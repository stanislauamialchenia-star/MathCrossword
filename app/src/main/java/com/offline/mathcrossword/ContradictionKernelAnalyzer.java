package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Post-generation analysis of the *shape* of false-but-locally-viable branches.
 *
 * This never decides whether the puzzle is mathematically valid; ExactSolver / SolutionCounter
 * already own that invariant. The analyzer only describes the candidate space that a human can
 * encounter in a Hypothesis puzzle.
 */
final class ContradictionKernelAnalyzer {
    private ContradictionKernelAnalyzer() { }

    static final class Profile {
        int branchCount;
        int pivotCount;
        int depth2Branches;
        int depth3Branches;
        int deepBranches;          // survives bounded depth-3 probe; exact contradiction is deeper/unknown
        int maxBranchWidth;
        int maxPivotDegree;
        int maxRemainingAfterPropagation;
        int maxForcedAfterAssumption;
        String family = "none";
    }

    static Profile analyze(Puzzle p) {
        Profile out = new Profile();
        if (p == null || p.solutionStrategy != SolutionStrategy.HYPOTHESIS || p.logicLevel < 5) return out;

        HumanSolver.State base = HumanSolver.initialState(p);
        Map<Pos, Set<Integer>> domains = HumanSolver.allDomains(p, base);
        Map<Pos, Integer> degrees = PuzzleGenerator.numberDegrees(p);
        Set<Pos> pivots = new HashSet<>();

        List<Pos> ordered = new ArrayList<>(p.hidden);
        ordered.sort((a, b) -> {
            int da = degrees.getOrDefault(a, 1), db = degrees.getOrDefault(b, 1);
            if (da != db) return Integer.compare(db, da);
            return Integer.compare(domains.getOrDefault(a, Collections.emptySet()).size(),
                    domains.getOrDefault(b, Collections.emptySet()).size());
        });

        // This is a post-selection descriptor, not a rejection-loop metric. Keep it strictly
        // bounded: profile shape, do not turn analysis into a second exact solver.
        int posLimit = Math.min(8, ordered.size());
        for (int i = 0; i < posLimit; i++) {
            Pos pos = ordered.get(i);
            Set<Integer> domain = domains.getOrDefault(pos, Collections.emptySet());
            if (domain.size() < 2 || domain.size() > 8) continue;
            Cell truthCell = p.cells.get(pos);
            if (truthCell == null) continue;
            int truth = truthCell.number;
            if (!domain.contains(truth)) continue;

            int checked = 0;
            for (int candidate : domain) {
                if (candidate == truth) continue;
                if (checked++ >= 3) break;

                HumanSolver.State probe = new HumanSolver.State(base);
                if (!HumanSolver.assign(probe, pos, candidate)) continue;
                HumanSolver.Propagation propagation = HumanSolver.propagateSingles(p, probe);
                if (propagation.contradiction || !HumanSolver.allLocallyPossible(p, probe)) continue;

                // The puzzle is exactly unique and candidate != truth, so this branch is globally
                // false. What matters here is how long bounded human-style probing keeps it alive.
                boolean viableDepth2 = HumanSolver.candidateViable(
                        p, base, pos, candidate, 2, new HumanSolver.ProbeBudget(260));

                out.branchCount++;
                pivots.add(pos);
                out.maxBranchWidth = Math.max(out.maxBranchWidth, domain.size());
                out.maxPivotDegree = Math.max(out.maxPivotDegree, degrees.getOrDefault(pos, 1));
                out.maxRemainingAfterPropagation = Math.max(out.maxRemainingAfterPropagation,
                        Math.max(0, p.hidden.size() - probe.assigned.size()));
                out.maxForcedAfterAssumption = Math.max(out.maxForcedAfterAssumption, propagation.forced);

                if (!viableDepth2) out.depth2Branches++;
                else out.deepBranches++; // survives depth-2; exact contradiction is deeper/unknown
            }
        }

        out.pivotCount = pivots.size();
        if (out.branchCount == 0) out.family = p.contradictionKernel ? "unprofiled" : "none";
        else if (out.pivotCount >= 2 && out.branchCount >= 3) out.family = "multi-pivot";
        else if (out.deepBranches > 0) out.family = "deep-branch";
        else if (out.depth2Branches > 0 && out.maxForcedAfterAssumption > 0) out.family = "two-stage";
        else out.family = "single-pivot";
        return out;
    }

    static void apply(Puzzle p, Profile profile) {
        if (p == null || profile == null) return;
        p.contradictionKernelBranches = profile.branchCount;
        p.contradictionKernelPivots = profile.pivotCount;
        p.contradictionKernelDepth2Branches = profile.depth2Branches;
        p.contradictionKernelDepth3Branches = profile.depth3Branches;
        p.contradictionKernelDeepBranches = profile.deepBranches;
        p.contradictionKernelFamily = profile.family == null ? "none" : profile.family;
        p.contradictionKernelMaxRemaining = profile.maxRemainingAfterPropagation;
        if (profile.branchCount > 0) p.contradictionKernel = true;
        if (profile.deepBranches > 0) p.contradictionKernelDepth = Math.max(p.contradictionKernelDepth, 3);
        else if (profile.depth2Branches > 0) p.contradictionKernelDepth = Math.max(p.contradictionKernelDepth, 2);
    }
}
