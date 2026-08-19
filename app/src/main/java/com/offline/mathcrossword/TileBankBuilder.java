package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * v13 tile-bank constructor.
 *
 * Instead of repeatedly adding one decoy and re-running the whole HumanSolver,
 * this builder computes one base domain picture, derives a bounded pool of
 * equation-supported external values, then greedily chooses a small set that
 * increases ambiguity in the most useful cells.
 */
final class TileBankBuilder {
    private TileBankBuilder() { }

    static void build(Puzzle p, Random r, int maxNumber, int logicLevel,
                      GenerationDiagnostics diagnostics) {
        List<Integer> trueValues = new ArrayList<>();
        Map<Integer, Integer> trueBank = new LinkedHashMap<>();
        for (Pos pos : p.hidden) {
            int v = p.cells.get(pos).number;
            trueValues.add(v);
            trueBank.put(v, trueBank.getOrDefault(v, 0) + 1);
        }

        int baseDecoys = logicLevel >= 5 ? 7 : (logicLevel >= 4 ? 5 : (logicLevel >= 3 ? 3 : 0));
        int ambiguityExtras = logicLevel >= 5 ? 4 : (logicLevel >= 4 ? 3 : (logicLevel >= 3 ? 2 : 0));
        int maxDecoys = baseDecoys + ambiguityExtras;

        HumanSolver.State base = new HumanSolver.State();
        base.remaining.putAll(trueBank);

        long poolStarted = System.nanoTime();
        Map<Pos, Set<Integer>> baseDomains = HumanSolver.allDomains(p, base);
        Set<Pos> singletonCells = new LinkedHashSet<>();
        for (Map.Entry<Pos, Set<Integer>> e : baseDomains.entrySet()) {
            if (e.getValue().size() == 1) singletonCells.add(e.getKey());
        }

        List<Candidate> pool = buildCandidatePool(p, base, baseDomains, singletonCells,
                trueValues, trueBank, maxNumber, logicLevel, p.solutionStrategy, r);
        if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.TILE_POOL,
                System.nanoTime() - poolStarted);

        long selectStarted = System.nanoTime();
        List<Integer> selected = selectDecoys(pool, singletonCells, trueBank, maxDecoys, baseDecoys,
                p.solutionStrategy, logicLevel, r);

        // Robustness fallback: if an unusual arithmetic layout did not yield enough
        // external values, duplicates of real values are still legitimate decoys.
        while (selected.size() < baseDecoys && !trueValues.isEmpty()) {
            selected.add(trueValues.get(r.nextInt(trueValues.size())));
        }

        List<Integer> values = new ArrayList<>(trueValues.size() + selected.size());
        values.addAll(trueValues);
        values.addAll(selected);
        Collections.shuffle(values, r);

        p.tiles.clear();
        int id = 1;
        for (int v : values) p.tiles.add(new Tile(id++, v));
        p.decoyCount = Math.max(0, p.tiles.size() - p.hidden.size());

        if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.TILE_SELECT,
                System.nanoTime() - selectStarted);
    }

    private static List<Candidate> buildCandidatePool(Puzzle p,
                                                       HumanSolver.State base,
                                                       Map<Pos, Set<Integer>> baseDomains,
                                                       Set<Pos> singletonCells,
                                                       List<Integer> trueValues,
                                                       Map<Integer,Integer> trueBank,
                                                       int maxNumber,
                                                       int logicLevel,
                                                       SolutionStrategy strategy,
                                                       Random r) {
        Set<Integer> raw = new LinkedHashSet<>();
        // v20 keeps provenance for equation-derived values. In v13-v19 the
        // builder first formed one global value pool and then tested every value
        // against every hidden cell, even when the equations had already proved
        // that the value was relevant to only one or two positions. Keeping this
        // support index removes most of those redundant HumanSolver probes without
        // changing the accepted candidate set.
        Map<Integer, Set<Pos>> hintedSupports = new LinkedHashMap<>();

        // Primary source: values derived algebraically from the equations touching
        // each hidden cell. This is usually a much smaller search space than 1..1000.
        for (Pos pos : p.hidden) {
            Set<Integer> local = PuzzleGenerator.plausibleExternalValuesForCell(p, pos, base, maxNumber);
            for (int value : local) {
                raw.add(value);
                hintedSupports.computeIfAbsent(value, k -> new LinkedHashSet<>()).add(pos);
            }
        }

        // Secondary source: a bounded neighbourhood around real values. It helps
        // with layouts where exact equation-derived intersections are too sparse.
        int radius = logicLevel >= 5 ? 10 : (logicLevel >= 4 ? 6 : 3);
        for (int v : trueValues) {
            for (int d = 1; d <= radius; d += (d < 4 ? 1 : 2)) {
                if (v - d > 0) raw.add(v - d);
                if (v + d <= maxNumber) raw.add(v + d);
            }
        }

        // Keep the pool bounded and deterministic. We first prefer values close to
        // true values because they are harder to dismiss visually, then add a small
        // pseudo-random tail for diversity.
        List<Integer> rawList = new ArrayList<>(raw);
        rawList.removeIf(v -> v == null || v <= 0 || v > maxNumber);
        rawList.sort(Comparator.comparingInt(v -> nearestDistance(v, trueValues)));
        int cap = logicLevel >= 5
                ? (strategy == SolutionStrategy.HYPOTHESIS ? 56 : 64)
                : (logicLevel >= 4 ? 56 : 36);
        if (rawList.size() > cap) {
            List<Integer> head = new ArrayList<>(rawList.subList(0, Math.max(1, cap * 3 / 4)));
            List<Integer> tail = new ArrayList<>(rawList.subList(head.size(), rawList.size()));
            Collections.shuffle(tail, r);
            int need = cap - head.size();
            if (need > 0) head.addAll(tail.subList(0, Math.min(need, tail.size())));
            rawList = head;
        }

        Map<Pos, Integer> degree = PuzzleGenerator.numberDegrees(p);
        List<Candidate> out = new ArrayList<>();
        for (int value : rawList) {
            Set<Pos> supports = new LinkedHashSet<>();
            int crossWeight = 0;
            Set<Pos> hinted = hintedSupports.get(value);
            Iterable<Pos> positions = (hinted != null && !hinted.isEmpty()) ? hinted : p.hidden;
            for (Pos pos : positions) {
                Set<Integer> existing = baseDomains.get(pos);
                if (existing != null && existing.contains(value)) continue;
                if (!locallyFitsWhenAdded(p, base, pos, value)) continue;
                supports.add(pos);
                crossWeight += Math.max(1, degree.getOrDefault(pos, 1));
            }
            if (supports.isEmpty()) continue;

            int singletonCoverage = 0;
            for (Pos q : singletonCells) if (supports.contains(q)) singletonCoverage++;
            boolean novel = !trueBank.containsKey(value);
            int distance = nearestDistance(value, trueValues);
            out.add(new Candidate(value, supports, singletonCoverage, crossWeight, novel, distance));
        }

        out.sort((a, b) -> Integer.compare(baseScore(b), baseScore(a)));
        return out;
    }

    private static boolean locallyFitsWhenAdded(Puzzle p, HumanSolver.State base, Pos pos, int candidate) {
        HumanSolver.State probe = new HumanSolver.State(base);
        probe.remaining.put(candidate, probe.remaining.getOrDefault(candidate, 0) + 1);
        return HumanSolver.assign(probe, pos, candidate) && HumanSolver.allLocallyPossible(p, probe);
    }

    private static List<Integer> selectDecoys(List<Candidate> pool,
                                              Set<Pos> singletonCells,
                                              Map<Integer,Integer> trueBank,
                                              int maxDecoys,
                                              int baseDecoys,
                                              SolutionStrategy strategy,
                                              int logicLevel,
                                              Random r) {
        List<Integer> selected = new ArrayList<>();
        Set<Pos> coveredSingletons = new HashSet<>();
        Set<Integer> selectedDistinct = new HashSet<>();

        for (int slot = 0; slot < maxDecoys; slot++) {
            Candidate best = null;
            int bestScore = Integer.MIN_VALUE;
            for (Candidate c : pool) {
                // Prefer distinct decoys until the mandatory base count is reached.
                if (slot < baseDecoys && selectedDistinct.contains(c.value)) continue;
                int newSingletons = 0;
                for (Pos q : c.supports) {
                    if (singletonCells.contains(q) && !coveredSingletons.contains(q)) newSingletons++;
                }
                int score = baseScore(c)
                        + newSingletons * 220
                        - (selectedDistinct.contains(c.value) ? 35 : 0);

                // After all original singleton cells have alternatives, extra decoys
                // are useful only if they have broad/crossing support.
                if (coveredSingletons.containsAll(singletonCells) && slot >= baseDecoys) {
                    score += c.supports.size() * 14 + c.crossWeight * 5;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
            if (best == null) break;

            selected.add(best.value);
            selectedDistinct.add(best.value);
            for (Pos q : best.supports) if (singletonCells.contains(q)) coveredSingletons.add(q);

            // Most strategies stop once obvious singleton cells have alternatives.
            // Hypothesis deliberately keeps broad-support decoys: the false branch
            // has to remain locally plausible long enough for a lookahead test to
            // matter. At tier 4 use the full bounded ambiguity budget; this is one
            // extra tile versus v1.45, not a retry-budget increase. Exact uniqueness
            // is still checked after the bank is built.
            int hypothesisFloor = strategy == SolutionStrategy.HYPOTHESIS
                    ? (logicLevel == 4
                        ? maxDecoys
                        : Math.min(maxDecoys, baseDecoys + 3))
                    : baseDecoys;
            if (selected.size() >= hypothesisFloor && coveredSingletons.containsAll(singletonCells)) break;
        }

        // A tiny diversity shuffle prevents deterministic ranking from producing
        // visually identical bank order for structurally similar boards.
        if (selected.size() > 1 && r.nextBoolean()) Collections.swap(selected, 0, selected.size() - 1);
        return selected;
    }

    private static int baseScore(Candidate c) {
        int score = c.singletonCoverage * 120 + c.supports.size() * 24 + c.crossWeight * 7;
        if (c.novel) score += 18;
        score += Math.max(0, 18 - c.nearestDistance);
        return score;
    }

    private static int nearestDistance(int value, List<Integer> trueValues) {
        int best = Integer.MAX_VALUE;
        for (int v : trueValues) best = Math.min(best, Math.abs(value - v));
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    private static final class Candidate {
        final int value;
        final Set<Pos> supports;
        final int singletonCoverage;
        final int crossWeight;
        final boolean novel;
        final int nearestDistance;

        Candidate(int value, Set<Pos> supports, int singletonCoverage,
                  int crossWeight, boolean novel, int nearestDistance) {
            this.value = value;
            this.supports = supports;
            this.singletonCoverage = singletonCoverage;
            this.crossWeight = crossWeight;
            this.novel = novel;
            this.nearestDistance = nearestDistance;
        }
    }
}
