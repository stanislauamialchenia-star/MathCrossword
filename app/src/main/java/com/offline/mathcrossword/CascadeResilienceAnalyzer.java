package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Measures how much of a puzzle collapses after a single correct local discovery.
 *
 * This is deliberately different from difficulty. A board may have no obvious first
 * move and still be structurally fragile: once one key value is found, every remaining
 * cell can become forced. For MIXED/DEDUCTION/NETWORK/HYPOTHESIS that is usually a poor
 * hard puzzle. CHAIN is the exception: a long cascade is part of that strategy's point.
 */
final class CascadeResilienceAnalyzer {
    private CascadeResilienceAnalyzer() { }

    static final class Profile {
        int hidden;
        int maxResolvedAfterOneCell;
        int maxAdditionalForcedAfterOneCell;
        double maxResolvedFractionAfterOneCell;
        int vulnerableSingleCells;
        int testedSingleCells;
        Pos worstSingleCell;

        int maxResolvedAfterOneEquation;
        int maxAdditionalForcedAfterOneEquation;
        double maxResolvedFractionAfterOneEquation;
        int testedEquations;

        boolean wholeBoardSingleCellCollapse() {
            return hidden > 0 && maxResolvedAfterOneCell >= hidden;
        }
    }

    static Profile analyze(Puzzle p) {
        Profile out = new Profile();
        if (p == null || p.hidden.isEmpty()) return out;
        out.hidden = p.hidden.size();

        HumanSolver.State base = HumanSolver.initialState(p);

        for (Pos pos : p.hidden) {
            Cell cell = p.cells.get(pos);
            if (cell == null || cell.kind != Kind.NUMBER) continue;
            HumanSolver.State probe = new HumanSolver.State(base);
            if (!HumanSolver.assign(probe, pos, cell.number)) continue;
            HumanSolver.Propagation propagation = HumanSolver.propagateSingles(p, probe);
            if (propagation.contradiction) continue;

            out.testedSingleCells++;
            int resolved = probe.assigned.size();
            int additional = Math.max(0, resolved - 1);
            if (resolved > out.maxResolvedAfterOneCell) {
                out.maxResolvedAfterOneCell = resolved;
                out.worstSingleCell = pos;
            }
            out.maxAdditionalForcedAfterOneCell = Math.max(out.maxAdditionalForcedAfterOneCell, additional);
            if (resolved >= Math.max(3, (out.hidden * 3 + 3) / 4)) out.vulnerableSingleCells++;
        }

        out.maxResolvedFractionAfterOneCell = out.hidden == 0 ? 0.0
                : out.maxResolvedAfterOneCell / (double) out.hidden;

        // Descriptive only: "one example solved" can reveal more than one hidden number
        // in some layouts. We profile that larger perturbation but do not use it as the
        // primary rejection gate because fully revealing a three-hidden equation is often
        // stronger than any real first move available to the player.
        for (Equation e : p.equations) {
            Set<Pos> hiddenInEquation = new LinkedHashSet<>();
            if (p.hidden.contains(e.a)) hiddenInEquation.add(e.a);
            if (p.hidden.contains(e.b)) hiddenInEquation.add(e.b);
            if (p.hidden.contains(e.c)) hiddenInEquation.add(e.c);
            if (hiddenInEquation.isEmpty()) continue;

            HumanSolver.State probe = new HumanSolver.State(base);
            boolean ok = true;
            for (Pos pos : hiddenInEquation) {
                Cell cell = p.cells.get(pos);
                if (cell == null || !HumanSolver.assign(probe, pos, cell.number)) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;
            HumanSolver.Propagation propagation = HumanSolver.propagateSingles(p, probe);
            if (propagation.contradiction) continue;

            out.testedEquations++;
            int resolved = probe.assigned.size();
            int additional = Math.max(0, resolved - hiddenInEquation.size());
            out.maxResolvedAfterOneEquation = Math.max(out.maxResolvedAfterOneEquation, resolved);
            out.maxAdditionalForcedAfterOneEquation = Math.max(out.maxAdditionalForcedAfterOneEquation, additional);
        }
        out.maxResolvedFractionAfterOneEquation = out.hidden == 0 ? 0.0
                : out.maxResolvedAfterOneEquation / (double) out.hidden;
        return out;
    }

    static boolean acceptable(Puzzle p, SolutionStrategy strategy, int logicLevel, Profile profile) {
        if (Boolean.getBoolean("mathcrossword.disableCascadeGate")) return true;
        if (profile == null || profile.hidden == 0 || logicLevel <= 2) return true;
        strategy = strategy == null ? SolutionStrategy.MIXED : strategy;

        // CHAIN intentionally trains "find the entry point, then follow the dependency".
        // Its cascade is measured and exposed, not rejected.
        if (strategy == SolutionStrategy.CHAIN) return true;

        double maxFraction;
        if (logicLevel >= 5) maxFraction = 0.65;
        else if (logicLevel >= 4) maxFraction = 0.75;
        else maxFraction = 0.85;

        // Small boards need one-cell slack because integer fractions are coarse.
        int allowedResolved = Math.max(2, (int) Math.ceil(profile.hidden * maxFraction));
        if (profile.maxResolvedAfterOneCell > allowedResolved) return false;

        // If several distinct cells can each unlock almost the whole board, the fragility
        // is systemic rather than a single accidental key cell.
        int vulnerableAllowance = logicLevel >= 5 ? 0 : 1;
        return profile.vulnerableSingleCells <= vulnerableAllowance;
    }

    static int qualityBonus(SolutionStrategy strategy, int logicLevel, Profile profile) {
        if (profile == null || profile.hidden == 0) return 0;
        if (strategy == SolutionStrategy.CHAIN) {
            // #41 showed that almost every L10 CHAIN can still collapse the whole board
            // once the *right* value is known, including the hardest MRV samples. The
            // discriminator is entry-point selectivity: cheap boards expose many cells
            // that can trigger that cascade, while the hard tail exposes only a few.
            // Preserve the productive-cascade reward and, only at tier 5, add a bounded
            // reward for making those useful entry points sparse. This is selection
            // pressure only; CHAIN acceptance and constructor behavior stay unchanged.
            if (logicLevel < 5) {
                return Math.min(220, profile.maxAdditionalForcedAfterOneCell * 18);
            }
            int productiveCascade = Math.min(190, profile.maxAdditionalForcedAfterOneCell * 17);
            if (profile.testedSingleCells <= 0) return productiveCascade;
            double vulnerableFraction = profile.vulnerableSingleCells / (double) profile.testedSingleCells;
            double selectivity = Math.max(0.0, Math.min(1.0, 1.0 - vulnerableFraction));
            int selectivityBonus = (int) Math.round(selectivity * 110.0);
            return Math.min(280, productiveCascade + selectivityBonus);
        }
        double resilience = 1.0 - profile.maxResolvedFractionAfterOneCell;
        int bonus = (int) Math.round(resilience * (logicLevel >= 5 ? 260.0 : 180.0));
        bonus -= profile.vulnerableSingleCells * 70;
        return bonus;
    }
}
