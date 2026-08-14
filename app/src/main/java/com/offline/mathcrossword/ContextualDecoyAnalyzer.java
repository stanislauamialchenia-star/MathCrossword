package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Classifies decoys already present in the final tile bank.
 *
 * A contextual decoy is a surplus tile value that is false at a particular
 * unresolved cell, yet remains locally compatible with at least two equations
 * there and lives in a compact candidate domain. Because the final puzzle has an
 * exact unique solution, such a placement is guaranteed to fail globally even
 * when it survives local checks.
 */
final class ContextualDecoyAnalyzer {
    private ContextualDecoyAnalyzer() { }

    static final class Profile {
        int distinctDecoyValues;
        int contextualValues;
        int resourceConflictValues;
        int maxConstraintSupport;
        int maxSupportCells;
        int depth2RefutableValues;
        int depth2SurvivingValues;
        int maxInformationGain;

        int depthMax() {
            if (depth2SurvivingValues > 0) return 3;
            if (depth2RefutableValues > 0) return 2;
            return contextualValues > 0 ? 1 : 0;
        }
    }

    static Profile analyze(Puzzle p, int logicLevel) {
        Profile out = new Profile();
        if (p == null || p.hidden.isEmpty()) return out;

        Map<Integer, Integer> truth = new LinkedHashMap<>();
        for (Pos pos : p.hidden) {
            Cell c = p.cells.get(pos);
            if (c != null) truth.put(c.number, truth.getOrDefault(c.number, 0) + 1);
        }
        Map<Integer, Integer> tiles = new LinkedHashMap<>();
        for (Tile t : p.tiles) tiles.put(t.value, tiles.getOrDefault(t.value, 0) + 1);

        Map<Integer, Integer> surplus = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> e : tiles.entrySet()) {
            int extra = e.getValue() - truth.getOrDefault(e.getKey(), 0);
            if (extra > 0) surplus.put(e.getKey(), extra);
        }
        out.distinctDecoyValues = surplus.size();
        if (surplus.isEmpty()) return out;

        HumanSolver.State base = HumanSolver.initialState(p);
        HumanSolver.Propagation opening = HumanSolver.propagateSingles(p, base);
        if (opening.contradiction || !HumanSolver.allLocallyPossible(p, base)) return out;
        Map<Pos, Integer> degree = PuzzleGenerator.numberDegrees(p);
        int maxWidth = logicLevel >= 5 ? 5 : 4;

        for (int value : surplus.keySet()) {
            boolean contextual = false;
            boolean refutable2 = false;
            boolean survives2 = false;
            int supportCells = 0;
            int valueMaxConstraints = 0;
            int valueMaxGain = 0;

            for (Pos pos : p.hidden) {
                if (base.assigned.containsKey(pos)) continue;
                Cell real = p.cells.get(pos);
                if (real == null || real.number == value) continue;
                int constraints = degree.getOrDefault(pos, 1);
                if (constraints < 2) continue;

                Set<Integer> domain = HumanSolver.domainFor(p, pos, base);
                if (!domain.contains(value) || domain.size() < 2 || domain.size() > maxWidth) continue;

                HumanSolver.State probe = new HumanSolver.State(base);
                if (!HumanSolver.assign(probe, pos, value)) continue;
                HumanSolver.Propagation propagation = HumanSolver.propagateSingles(p, probe);
                if (propagation.contradiction || !HumanSolver.allLocallyPossible(p, probe)) continue;

                contextual = true;
                supportCells++;
                valueMaxConstraints = Math.max(valueMaxConstraints, constraints);
                valueMaxGain = Math.max(valueMaxGain, 1 + propagation.forced);
                boolean viable2 = HumanSolver.candidateViable(
                        p, base, pos, value, 2, new HumanSolver.ProbeBudget(180));
                if (viable2) survives2 = true;
                else refutable2 = true;
            }

            if (!contextual) continue;
            out.contextualValues++;
            if (truth.containsKey(value)) out.resourceConflictValues++;
            if (refutable2) out.depth2RefutableValues++;
            if (survives2) out.depth2SurvivingValues++;
            out.maxSupportCells = Math.max(out.maxSupportCells, supportCells);
            out.maxConstraintSupport = Math.max(out.maxConstraintSupport, valueMaxConstraints);
            out.maxInformationGain = Math.max(out.maxInformationGain, valueMaxGain);
        }
        return out;
    }

    static void apply(Puzzle p, Profile profile) {
        if (p == null || profile == null) return;
        p.contextualDecoyCount = profile.contextualValues;
        p.resourceConflictDecoyCount = profile.resourceConflictValues;
        p.contextualDecoyConstraintSupportMax = profile.maxConstraintSupport;
        p.contextualDecoyDepthMax = profile.depthMax();
        p.contextualDecoyInformationGainMax = profile.maxInformationGain;
        // Keep deceptiveDecoySupportMax as provenance for decoys explicitly
        // inserted/refined by the v19-v21 builder. Existing contextual decoys are
        // counted independently above.
    }
}
