package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * v16 candidate-domain contradiction kernel.
 *
 * The production invariant is deliberately simpler than the earlier prototype:
 * a false value must be locally plausible and survive forced propagation, while
 * the complete puzzle still has one exact solution. Therefore the false branch
 * is guaranteed to die globally, but not immediately. That is the useful human
 * hypothesis pattern; the exact distance to the contradiction is measured later
 * by HumanSolver rather than required during construction.
 */
final class ContradictionKernelBuilder {
    private ContradictionKernelBuilder() { }

    static boolean reinforce(Puzzle p, int maxNumber, Random r, GenerationDiagnostics diagnostics) {
        if (p == null || p.solutionStrategy != SolutionStrategy.HYPOTHESIS || p.logicLevel < 5) return false;
        long started = System.nanoTime();
        try {
            clearKernelMetrics(p);
            HumanSolver.State base = HumanSolver.initialState(p);
            Map<Pos, Set<Integer>> domains = HumanSolver.allDomains(p, base);
            Map<Pos, Integer> degree = PuzzleGenerator.numberDegrees(p);

            // The selected hidden/tile candidate has already passed the known-
            // solution uniqueness test. Thus any non-truth value in a domain is
            // globally false. We only need to prove that it is not immediately
            // self-defeating after forced propagation.
            Kernel existing = bestExistingKernel(p, base, domains, degree);
            if (existing != null) {
                apply(p, existing, false);
                return true;
            }

            // If the bank has no suitable false branch, add one carefully chosen
            // equation-supported decoy. This path is bounded and much rarer.
            List<Pos> pivots = new ArrayList<>(p.hidden);
            pivots.sort((a, b) -> Integer.compare(degree.getOrDefault(b, 1), degree.getOrDefault(a, 1)));
            if (pivots.size() > 5) pivots = new ArrayList<>(pivots.subList(0, 5));

            for (Pos pivot : pivots) {
                int truth = p.cells.get(pivot).number;
                Set<Integer> current = domains.getOrDefault(pivot, Collections.emptySet());
                if (current.size() > 4) continue;

                Set<Integer> raw = new LinkedHashSet<>(PuzzleGenerator.plausibleExternalValuesForCell(
                        p, pivot, base, maxNumber));
                for (Tile t : p.tiles) raw.add(t.value);
                for (int d = 1; d <= 4; d++) {
                    if (truth - d > 0) raw.add(truth - d);
                    if (truth + d <= maxNumber) raw.add(truth + d);
                }
                raw.remove(truth);
                raw.removeAll(current);
                raw.removeIf(v -> v == null || v <= 0 || v > maxNumber);

                List<Integer> ordered = new ArrayList<>(raw);
                Collections.shuffle(ordered, r);
                ordered.sort(Comparator.comparingInt(v -> Math.abs(v - truth)));
                if (ordered.size() > 10) ordered = new ArrayList<>(ordered.subList(0, 10));

                for (int falseValue : ordered) {
                    p.tiles.add(new Tile(p.tiles.size() + 1000, falseValue));
                    HumanSolver.State withDecoy = HumanSolver.initialState(p);
                    Survival survival = survivesImmediate(p, withDecoy, pivot, falseValue);
                    if (survival == null || survival.remainingAfter < Math.max(2, p.hidden.size() / 4)) {
                        p.tiles.remove(p.tiles.size() - 1);
                        continue;
                    }

                    // The added decoy must not create any complete second solution.
                    if (!SolutionCounter.hasUniqueKnownSolution(p)) {
                        p.tiles.remove(p.tiles.size() - 1);
                        continue;
                    }

                    Kernel k = new Kernel(pivot, falseValue,
                            Math.max(2, current.size() + 1), degree.getOrDefault(pivot, 1),
                            survival.remainingAfter);
                    p.tiles.remove(p.tiles.size() - 1);

                    p.tiles.add(new Tile(p.tiles.size() + 1, falseValue));
                    p.decoyCount = Math.max(0, p.tiles.size() - p.hidden.size());
                    apply(p, k, true);
                    return true;
                }
            }
            return false;
        } finally {
            if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.CONTRADICTION_KERNEL,
                    System.nanoTime() - started);
        }
    }


    /** Cheap signal used while ranking hidden masks. The puzzle has already
     * passed known-solution uniqueness, so a non-truth domain value that
     * survives propagation is already a valid globally-false hypothesis. */
    static boolean hasExistingKernelQuick(Puzzle p) {
        if (p == null || p.solutionStrategy != SolutionStrategy.HYPOTHESIS || p.logicLevel < 5) return false;
        HumanSolver.State base = HumanSolver.initialState(p);
        Map<Pos, Set<Integer>> domains = HumanSolver.allDomains(p, base);
        Map<Pos, Integer> degree = PuzzleGenerator.numberDegrees(p);
        List<Pos> positions = new ArrayList<>(p.hidden);
        positions.sort((a, b) -> {
            int da = degree.getOrDefault(a, 1), db = degree.getOrDefault(b, 1);
            if (da != db) return Integer.compare(db, da);
            return Integer.compare(domains.getOrDefault(a, Collections.emptySet()).size(),
                    domains.getOrDefault(b, Collections.emptySet()).size());
        });
        int posLimit = Math.min(6, positions.size());
        for (int i = 0; i < posLimit; i++) {
            Pos pos = positions.get(i);
            Set<Integer> d = domains.getOrDefault(pos, Collections.emptySet());
            int truth = p.cells.get(pos).number;
            if (!d.contains(truth) || d.size() < 2 || d.size() > 6) continue;
            int checked = 0;
            for (int v : d) {
                if (v == truth) continue;
                if (checked++ >= 3) break;
                Survival survival = survivesImmediate(p, base, pos, v);
                if (survival != null && survival.remainingAfter >= Math.max(2, p.hidden.size() / 4)) return true;
            }
        }
        return false;
    }
    private static Kernel bestExistingKernel(Puzzle p, HumanSolver.State base,
                                             Map<Pos, Set<Integer>> domains,
                                             Map<Pos, Integer> degree) {
        Kernel best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Map.Entry<Pos, Set<Integer>> e : domains.entrySet()) {
            Pos pos = e.getKey();
            int truth = p.cells.get(pos).number;
            if (!e.getValue().contains(truth) || e.getValue().size() < 2 || e.getValue().size() > 6) continue;
            for (int candidate : e.getValue()) {
                if (candidate == truth) continue;
                Survival survival = survivesImmediate(p, base, pos, candidate);
                if (survival == null || survival.remainingAfter < Math.max(2, p.hidden.size() / 4)) continue;
                int score = degree.getOrDefault(pos, 1) * 70
                        + Math.max(0, 7 - e.getValue().size()) * 30
                        + survival.remainingAfter * 8;
                if (score > bestScore) {
                    bestScore = score;
                    best = new Kernel(pos, candidate, e.getValue().size(),
                            degree.getOrDefault(pos, 1), survival.remainingAfter);
                }
            }
        }
        return best;
    }

    /** Local viability test: the false value can be written, all forced singles
     * are propagated, and the board still has no local contradiction. */
    private static Survival survivesImmediate(Puzzle p, HumanSolver.State base, Pos pivot, int falseValue) {
        HumanSolver.State probe = new HumanSolver.State(base);
        if (!HumanSolver.assign(probe, pivot, falseValue)) return null;
        HumanSolver.Propagation propagation = HumanSolver.propagateSingles(p, probe);
        if (propagation.contradiction || !HumanSolver.allLocallyPossible(p, probe)) return null;
        return new Survival(Math.max(0, p.hidden.size() - probe.assigned.size()), propagation.forced);
    }

    private static void clearKernelMetrics(Puzzle p) {
        p.contradictionKernel = false;
        p.contradictionKernelAddedDecoy = false;
        p.contradictionKernelDepth = 0;
        p.contradictionKernelBranchWidth = 0;
        p.contradictionKernelPivotDegree = 0;
        p.contradictionKernelBranches = 0;
        p.contradictionKernelPivots = 0;
        p.contradictionKernelDepth2Branches = 0;
        p.contradictionKernelDepth3Branches = 0;
        p.contradictionKernelDeepBranches = 0;
        p.contradictionKernelMaxRemaining = 0;
        p.contradictionKernelFamily = "none";
    }

    private static void apply(Puzzle p, Kernel k, boolean added) {
        p.contradictionKernel = true;
        p.contradictionKernelAddedDecoy = added;
        // Exact contradiction depth is intentionally not measured during
        // construction; doing so is expensive. The final HumanSolver pass may
        // classify it as depth-2 later. -1 means "globally false, depth unknown".
        p.contradictionKernelDepth = -1;
        p.contradictionKernelBranchWidth = k.branchWidth;
        p.contradictionKernelPivotDegree = k.pivotDegree;
    }

    private static final class Survival {
        final int remainingAfter;
        final int forced;
        Survival(int remainingAfter, int forced) { this.remainingAfter = remainingAfter; this.forced = forced; }
    }

    private static final class Kernel {
        final Pos pivot;
        final int falseValue;
        final int branchWidth;
        final int pivotDegree;
        final int remainingAfter;
        Kernel(Pos pivot, int falseValue, int branchWidth, int pivotDegree, int remainingAfter) {
            this.pivot = pivot;
            this.falseValue = falseValue;
            this.branchWidth = branchWidth;
            this.pivotDegree = pivotDegree;
            this.remainingAfter = remainingAfter;
        }
    }
}
