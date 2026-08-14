package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Adds a very small number of believable-but-false tiles to hard PATH boards.
 *
 * v21 strengthens the definition of a useful decoy. A contextual decoy must be
 * simultaneously compatible with at least two local equation constraints at a
 * pivot cell, live inside a compact candidate domain, survive immediate
 * propagation, and preserve one exact global solution. A particularly useful
 * form is a resource-conflict decoy: an extra copy of a number that is correct
 * somewhere else but false at this pivot. It can look locally valid while using
 * a tile that the global solution needs in another place.
 */
final class DeceptiveDecoyBuilder {
    private DeceptiveDecoyBuilder() { }

    static int reinforce(Puzzle p, int maxNumber, int requested, Random r) {
        return reinforce(p, maxNumber, requested, r, Math.max(1, p == null ? 1 : p.logicLevel), 1);
    }

    static int reinforce(Puzzle p, int maxNumber, int requested, Random r,
                         int logicLevel, int minConstraintSupport) {
        if (p == null || requested <= 0 || p.hidden.isEmpty()) return 0;
        minConstraintSupport = Math.max(1, minConstraintSupport);

        Map<Integer, Integer> truthCount = new LinkedHashMap<>();
        for (Pos pos : p.hidden) {
            Cell c = p.cells.get(pos);
            if (c != null) truthCount.put(c.number, truthCount.getOrDefault(c.number, 0) + 1);
        }
        Map<Integer, Integer> tileCount = new LinkedHashMap<>();
        for (Tile t : p.tiles) tileCount.put(t.value, tileCount.getOrDefault(t.value, 0) + 1);

        Map<Integer, Candidate> byValue = new LinkedHashMap<>();
        HumanSolver.State base = HumanSolver.initialState(p);
        Map<Pos, Integer> degree = PuzzleGenerator.numberDegrees(p);

        for (Pos pos : p.hidden) {
            Cell real = p.cells.get(pos);
            if (real == null) continue;
            int constraints = degree.getOrDefault(pos, 1);
            Set<Integer> plausible = PuzzleGenerator.plausibleExternalValuesForCell(p, pos, base, maxNumber);
            for (int value : plausible) {
                if (value <= 0 || value > maxNumber || value == real.number) continue;
                int needed = truthCount.getOrDefault(value, 0);
                int have = tileCount.getOrDefault(value, 0);
                boolean resourceConflict = needed > 0;
                // Novel values get at most one copy. For a value used by the real
                // solution, add at most one surplus copy beyond the required
                // multiplicity; repeated duplicates quickly become noise.
                if ((!resourceConflict && have > 0) || (resourceConflict && have > needed)) continue;

                HumanSolver.State withTile = new HumanSolver.State(base);
                withTile.remaining.put(value, withTile.remaining.getOrDefault(value, 0) + 1);
                Set<Integer> domain = HumanSolver.domainFor(p, pos, withTile);
                if (!domain.contains(value)) continue;

                Candidate c = byValue.get(value);
                if (c == null) {
                    c = new Candidate(value, Math.abs(value - real.number), resourceConflict);
                    byValue.put(value, c);
                }
                c.supports.add(pos);
                c.degreeWeight += Math.max(1, constraints);
                c.maxConstraintSupport = Math.max(c.maxConstraintSupport, constraints);
                c.minDomainWidth = Math.min(c.minDomainWidth, domain.size());
                c.nearestTruthDistance = Math.min(c.nearestTruthDistance, Math.abs(value - real.number));
            }
        }

        List<Candidate> ranked = new ArrayList<>(byValue.values());
        ranked.sort(Comparator.comparingInt((Candidate c) -> score(c, logicLevel)).reversed());

        // Semantic validation is still bounded, but v21 validates the shape of the
        // ambiguity as well as simple local viability.
        List<Candidate> viable = new ArrayList<>();
        int validationFrontier = Math.min(ranked.size(), Math.max(16, requested * 8));
        for (int i = 0; i < validationFrontier; i++) {
            Candidate c = ranked.get(i);
            Set<Pos> validated = new LinkedHashSet<>();
            for (Pos pos : c.supports) {
                int constraints = degree.getOrDefault(pos, 1);
                if (constraints < minConstraintSupport) continue;

                HumanSolver.State withTile = new HumanSolver.State(base);
                withTile.remaining.put(c.value, withTile.remaining.getOrDefault(c.value, 0) + 1);
                Set<Integer> domain = HumanSolver.domainFor(p, pos, withTile);
                if (!domain.contains(c.value)) continue;
                // A candidate hidden among 8-10 values is technically false but not
                // an interesting hypothesis. Prefer compact, inspectable choices.
                int maxUsefulWidth = logicLevel >= 5 ? 5 : 4;
                if (domain.size() < 2 || domain.size() > maxUsefulWidth) continue;

                HumanSolver.State probe = new HumanSolver.State(withTile);
                if (!HumanSolver.assign(probe, pos, c.value)) continue;
                HumanSolver.Propagation propagation = HumanSolver.propagateSingles(p, probe);
                if (propagation.contradiction || !HumanSolver.allLocallyPossible(p, probe)) continue;

                // The false value has survived the first local check. A depth-2
                // probe distinguishes a short contradiction from a deeper branch;
                // both are useful, but Logic 4 prefers refutable branches while
                // Logic 5 gives extra weight to branches that remain plausible.
                boolean survivesDepth2 = HumanSolver.candidateViable(
                        p, withTile, pos, c.value, 2, new HumanSolver.ProbeBudget(180));

                validated.add(pos);
                c.maxConstraintSupport = Math.max(c.maxConstraintSupport, constraints);
                c.minDomainWidth = Math.min(c.minDomainWidth, domain.size());
                c.maxForcedAfterAssumption = Math.max(c.maxForcedAfterAssumption, propagation.forced);
                c.maxInformationGain = Math.max(c.maxInformationGain, 1 + propagation.forced);
                if (survivesDepth2) c.depth2SurvivingSupports++;
                else c.depth2RefutableSupports++;
            }
            if (validated.isEmpty()) continue;
            c.supports.clear();
            c.supports.addAll(validated);
            viable.add(c);
            if (viable.size() >= Math.max(requested * 4, 8)) break;
        }
        ranked = viable;
        ranked.sort(Comparator.comparingInt((Candidate c) -> score(c, logicLevel)).reversed());

        // A small deterministic perturbation among near-equal candidates avoids
        // identical banks without sacrificing semantic quality.
        for (int i = 0; i + 1 < ranked.size(); i++) {
            if (Math.abs(score(ranked.get(i), logicLevel) - score(ranked.get(i + 1), logicLevel)) <= 10
                    && r.nextBoolean()) {
                Candidate a = ranked.get(i); ranked.set(i, ranked.get(i + 1)); ranked.set(i + 1, a);
            }
        }

        int added = 0;
        int nextId = 1;
        for (Tile t : p.tiles) nextId = Math.max(nextId, t.id + 1);
        for (Candidate c : ranked) {
            if (added >= requested) break;
            Tile tile = new Tile(nextId++, c.value);
            p.tiles.add(tile);
            // Exact global uniqueness remains the hard mathematical safety gate.
            if (SolutionCounter.countSolutions(p, 2) != 1) {
                p.tiles.remove(p.tiles.size() - 1);
                continue;
            }
            added++;
            p.deceptiveDecoySupportMax = Math.max(p.deceptiveDecoySupportMax, c.supports.size());
            p.contextualDecoyCount++;
            if (c.resourceConflict) p.resourceConflictDecoyCount++;
            p.contextualDecoyConstraintSupportMax = Math.max(p.contextualDecoyConstraintSupportMax, c.maxConstraintSupport);
            p.contextualDecoyInformationGainMax = Math.max(p.contextualDecoyInformationGainMax, c.maxInformationGain);
            if (c.depth2SurvivingSupports > 0) p.contextualDecoyDepthMax = Math.max(p.contextualDecoyDepthMax, 3);
            else if (c.depth2RefutableSupports > 0) p.contextualDecoyDepthMax = Math.max(p.contextualDecoyDepthMax, 2);
            else p.contextualDecoyDepthMax = Math.max(p.contextualDecoyDepthMax, 1);
        }
        p.deceptiveDecoyCount += added;
        p.decoyCount = Math.max(0, p.tiles.size() - p.hidden.size());
        return added;
    }

    private static int score(Candidate c, int logicLevel) {
        int score = c.supports.size() * 110 + c.degreeWeight * 18;
        score += c.maxConstraintSupport * 85;
        if (c.resourceConflict) score += 110;
        if (c.minDomainWidth == 2) score += 95;
        else if (c.minDomainWidth == 3) score += 80;
        else if (c.minDomainWidth == 4) score += 50;
        else if (c.minDomainWidth == 5) score += 20;
        score += Math.min(4, c.maxForcedAfterAssumption) * 28;
        score += Math.max(0, 36 - c.nearestTruthDistance * 2);
        if (logicLevel >= 5) {
            score += c.depth2SurvivingSupports * 65 + c.depth2RefutableSupports * 35;
        } else {
            score += c.depth2RefutableSupports * 65 + c.depth2SurvivingSupports * 35;
        }
        return score;
    }

    private static final class Candidate {
        final int value;
        final boolean resourceConflict;
        final Set<Pos> supports = new LinkedHashSet<>();
        int degreeWeight;
        int nearestTruthDistance;
        int maxConstraintSupport;
        int minDomainWidth = Integer.MAX_VALUE;
        int maxForcedAfterAssumption;
        int maxInformationGain;
        int depth2RefutableSupports;
        int depth2SurvivingSupports;

        Candidate(int value, int distance, boolean resourceConflict) {
            this.value = value;
            this.resourceConflict = resourceConflict;
            this.nearestTruthDistance = distance;
        }
    }
}
