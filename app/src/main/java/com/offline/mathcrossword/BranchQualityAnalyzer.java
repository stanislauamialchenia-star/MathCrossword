package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Describes whether ambiguity in a puzzle creates useful hypothesis testing or
 * merely a wide brute-force search.
 *
 * The analyzer is deliberately bounded. It does not try to solve the puzzle a
 * second time; it inspects a handful of the tightest unresolved domains after
 * opening singleton propagation and asks whether false alternatives are locally
 * believable, informative, and refutable with short human-style lookahead.
 */
final class BranchQualityAnalyzer {
    private BranchQualityAnalyzer() { }

    static final class Profile {
        int hidden;
        int unresolvedAfterOpening;
        int pivotCount;                 // unresolved cells with 2..5 candidates
        int contextualPivotCount;       // pivot participates in >=2 equations
        int goodPivotCount;             // compact domain + believable false branch
        int productivePivotCount;       // assumption yields consequences or bounded refutation
        int bruteForcePivotCount;       // wide cells (>=6 candidates)
        int seriousFalseBranches;       // false branch survives immediate local propagation
        int immediateFalseBranches;     // looks possible in domain, collapses immediately after propagation
        int depth2RefutableBranches;     // bounded depth-2 probe finds contradiction
        int depth2SurvivingBranches;     // remains viable after bounded depth-2 probe
        int maxBranchWidth;
        int minGoodBranchWidth;
        int maxForcedAfterAssumption;
        int maxInformationGain;          // assignment + immediate forced consequences
        int maxPivotDegree;
        int testedPivots;

        boolean hasUsefulHypothesis() {
            return goodPivotCount > 0 && seriousFalseBranches > 0;
        }
    }

    static Profile analyze(Puzzle p) {
        Profile out = new Profile();
        if (p == null || p.hidden.isEmpty()) return out;
        out.hidden = p.hidden.size();

        HumanSolver.State base = HumanSolver.initialState(p);
        HumanSolver.Propagation opening = HumanSolver.propagateSingles(p, base);
        if (opening.contradiction || !HumanSolver.allLocallyPossible(p, base)) return out;

        Map<Pos, Set<Integer>> domains = HumanSolver.allDomains(p, base);
        Map<Pos, Integer> degrees = PuzzleGenerator.numberDegrees(p);
        out.unresolvedAfterOpening = domains.size();

        List<Pos> ordered = new ArrayList<>(domains.keySet());
        ordered.sort((a, b) -> {
            int sa = domains.getOrDefault(a, Collections.emptySet()).size();
            int sb = domains.getOrDefault(b, Collections.emptySet()).size();
            if (sa != sb) return Integer.compare(sa, sb);
            return Integer.compare(degrees.getOrDefault(b, 1), degrees.getOrDefault(a, 1));
        });

        // Hard puzzles are small enough that eight pivots are representative, while
        // this cap prevents profiling from becoming a second expensive solver.
        int limit = Math.min(8, ordered.size());
        int totalFalseChecks = 0;
        for (int i = 0; i < limit && totalFalseChecks < 16; i++) {
            Pos pos = ordered.get(i);
            Set<Integer> domain = domains.getOrDefault(pos, Collections.emptySet());
            int width = domain.size();
            out.maxBranchWidth = Math.max(out.maxBranchWidth, width);
            if (width >= 6) out.bruteForcePivotCount++;
            if (width < 2 || width > 5) continue;

            out.testedPivots++;
            out.pivotCount++;
            int degree = degrees.getOrDefault(pos, 1);
            out.maxPivotDegree = Math.max(out.maxPivotDegree, degree);
            if (degree >= 2) out.contextualPivotCount++;

            Cell truthCell = p.cells.get(pos);
            if (truthCell == null) continue;
            int truth = truthCell.number;
            int seriousHere = 0;
            int productiveHere = 0;
            int checked = 0;

            for (int candidate : domain) {
                if (candidate == truth) continue;
                if (checked++ >= 3 || totalFalseChecks++ >= 16) break;

                HumanSolver.State probe = new HumanSolver.State(base);
                if (!HumanSolver.assign(probe, pos, candidate)) continue;
                HumanSolver.Propagation propagation = HumanSolver.propagateSingles(p, probe);
                if (propagation.contradiction || !HumanSolver.allLocallyPossible(p, probe)) {
                    out.immediateFalseBranches++;
                    continue;
                }

                seriousHere++;
                out.seriousFalseBranches++;
                int informationGain = 1 + Math.max(0, propagation.forced);
                out.maxForcedAfterAssumption = Math.max(out.maxForcedAfterAssumption, propagation.forced);
                out.maxInformationGain = Math.max(out.maxInformationGain, informationGain);

                boolean viableDepth2 = HumanSolver.candidateViable(
                        p, base, pos, candidate, 2, new HumanSolver.ProbeBudget(180));
                if (viableDepth2) {
                    out.depth2SurvivingBranches++;
                } else {
                    out.depth2RefutableBranches++;
                    productiveHere++;
                }
                if (propagation.forced > 0) productiveHere++;
            }

            // A useful hypothesis pivot should present a small number of real choices,
            // not a lottery. Degree >=2 means the false value is simultaneously
            // compatible with multiple local constraints rather than one isolated sum.
            if (seriousHere > 0 && width <= 4 && degree >= 2) {
                out.goodPivotCount++;
                if (out.minGoodBranchWidth == 0 || width < out.minGoodBranchWidth) {
                    out.minGoodBranchWidth = width;
                }
            }
            if (productiveHere > 0 && seriousHere > 0) out.productivePivotCount++;
        }
        return out;
    }

    static int qualityBonus(Profile p, int logicLevel) {
        if (p == null) return 0;
        int score = p.goodPivotCount * 70
                + p.productivePivotCount * 45
                + p.contextualPivotCount * 18
                + p.depth2RefutableBranches * 20
                + Math.min(4, p.depth2SurvivingBranches) * 12;
        score -= p.bruteForcePivotCount * (logicLevel >= 5 ? 30 : 45);
        if (p.maxBranchWidth > (logicLevel >= 5 ? 7 : 6)) score -= 100;
        return Math.max(-180, Math.min(320, score));
    }
}
