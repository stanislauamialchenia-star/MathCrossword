package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Measures how much of a puzzle collapses after a single correct local discovery.
 *
 * Raw single-cell fragility is kept for backward compatibility. Region metrics add a
 * second view: several entry cells whose forced consequences substantially overlap are
 * treated as one dependency/collapse region rather than several independent defects.
 */
final class CascadeResilienceAnalyzer {
    private static final double REGION_OVERLAP_THRESHOLD = 0.80;

    private CascadeResilienceAnalyzer() { }

    static final class Profile {
        int hidden;
        int maxResolvedAfterOneCell;
        int maxAdditionalForcedAfterOneCell;
        double maxResolvedFractionAfterOneCell;
        int vulnerableSingleCells;
        int testedSingleCells;
        Pos worstSingleCell;

        // Descriptive region-level view. These do not affect generation acceptance yet.
        int vulnerableRegions;
        int largestVulnerableRegionSize;
        int largestVulnerableRegionEntries;
        int independentCollapseFronts;
        double vulnerabilityOverlap;
        final Set<Pos> worstRegionEntryCells = new LinkedHashSet<>();

        int maxResolvedAfterOneEquation;
        int maxAdditionalForcedAfterOneEquation;
        double maxResolvedFractionAfterOneEquation;
        int testedEquations;

        boolean wholeBoardSingleCellCollapse() {
            return hidden > 0 && maxResolvedAfterOneCell >= hidden;
        }
    }

    static final class RegionStats {
        int regions;
        int largestResolvedRegionSize;
        int largestRegionEntries;
        double meanWithinRegionOverlap;
        final Set<Pos> worstRegionEntryCells = new LinkedHashSet<>();
    }

    static Profile analyze(Puzzle p) {
        Profile out = new Profile();
        if (p == null || p.hidden.isEmpty()) return out;
        out.hidden = p.hidden.size();

        // Preserve the historical raw metric exactly: probe each correct cell from the
        // untouched initial state and count how many hidden cells become assigned.
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

        analyzeVulnerabilityRegions(p, out);

        // Descriptive only: revealing one equation can expose more than one hidden number.
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

    private static void analyzeVulnerabilityRegions(Puzzle p, Profile out) {
        HumanSolver.State opening = HumanSolver.initialState(p);
        HumanSolver.Propagation openingPropagation = HumanSolver.propagateSingles(p, opening);
        if (openingPropagation.contradiction) return;

        Set<Pos> openingAssigned = new LinkedHashSet<>(opening.assigned.keySet());
        int unresolved = Math.max(0, p.hidden.size() - openingAssigned.size());
        if (unresolved < 3) return;
        int vulnerableThreshold = Math.max(3, (unresolved * 3 + 3) / 4);

        Map<Pos, Set<Pos>> effects = new LinkedHashMap<>();
        for (Pos pos : p.hidden) {
            if (openingAssigned.contains(pos)) continue;
            Cell cell = p.cells.get(pos);
            if (cell == null || cell.kind != Kind.NUMBER) continue;

            HumanSolver.State probe = new HumanSolver.State(opening);
            if (!HumanSolver.assign(probe, pos, cell.number)) continue;
            HumanSolver.Propagation propagation = HumanSolver.propagateSingles(p, probe);
            if (propagation.contradiction) continue;

            Set<Pos> newlyResolved = new LinkedHashSet<>(probe.assigned.keySet());
            newlyResolved.removeAll(openingAssigned);
            if (newlyResolved.size() >= vulnerableThreshold) effects.put(pos, newlyResolved);
        }

        RegionStats stats = summarizeVulnerabilitySets(effects);
        out.vulnerableRegions = stats.regions;
        out.independentCollapseFronts = stats.regions;
        out.largestVulnerableRegionSize = stats.largestResolvedRegionSize;
        out.largestVulnerableRegionEntries = stats.largestRegionEntries;
        out.vulnerabilityOverlap = stats.meanWithinRegionOverlap;
        out.worstRegionEntryCells.addAll(stats.worstRegionEntryCells);
    }

    /**
     * Clusters entry points by overlap of the hidden cells they subsequently resolve.
     * The overlap coefficient (intersection / smaller set) deliberately treats nested
     * consequences as one dependency region: A->{A,B,C,D} and B->{B,C,D} are the same
     * structural cascade even though their Jaccard score is smaller than 1.
     */
    static RegionStats summarizeVulnerabilitySets(Map<Pos, Set<Pos>> effects) {
        RegionStats out = new RegionStats();
        if (effects == null || effects.isEmpty()) return out;

        List<Pos> entries = new ArrayList<>(effects.keySet());
        int n = entries.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double overlap = overlapCoefficient(effects.get(entries.get(i)), effects.get(entries.get(j)));
                if (overlap >= REGION_OVERLAP_THRESHOLD) union(parent, i, j);
            }
        }

        Map<Integer, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            groups.computeIfAbsent(root, ignored -> new ArrayList<>()).add(i);
        }
        out.regions = groups.size();

        double overlapSum = 0.0;
        int overlapPairs = 0;
        int bestResolvedSize = -1;
        int bestEntries = -1;

        for (List<Integer> group : groups.values()) {
            Set<Pos> unionResolved = new LinkedHashSet<>();
            for (int idx : group) unionResolved.addAll(effects.get(entries.get(idx)));

            for (int a = 0; a < group.size(); a++) {
                for (int b = a + 1; b < group.size(); b++) {
                    overlapSum += overlapCoefficient(
                            effects.get(entries.get(group.get(a))),
                            effects.get(entries.get(group.get(b))));
                    overlapPairs++;
                }
            }

            int resolvedSize = unionResolved.size();
            if (resolvedSize > bestResolvedSize
                    || (resolvedSize == bestResolvedSize && group.size() > bestEntries)) {
                bestResolvedSize = resolvedSize;
                bestEntries = group.size();
                out.worstRegionEntryCells.clear();
                for (int idx : group) out.worstRegionEntryCells.add(entries.get(idx));
            }
            out.largestResolvedRegionSize = Math.max(out.largestResolvedRegionSize, resolvedSize);
            out.largestRegionEntries = Math.max(out.largestRegionEntries, group.size());
        }

        out.meanWithinRegionOverlap = overlapPairs == 0 ? 0.0 : overlapSum / overlapPairs;
        return out;
    }

    static double overlapCoefficient(Set<Pos> a, Set<Pos> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0.0;
        Set<Pos> smaller = a.size() <= b.size() ? a : b;
        Set<Pos> larger = a.size() <= b.size() ? b : a;
        int intersection = 0;
        for (Pos pos : smaller) if (larger.contains(pos)) intersection++;
        return intersection / (double) smaller.size();
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) parent[rb] = ra;
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

        // Backward-compatible gate. Region metrics stay descriptive until their behaviour
        // is calibrated on a larger corpus and the deterministic PATH anchors.
        int allowedResolved = Math.max(2, (int) Math.ceil(profile.hidden * maxFraction));
        if (profile.maxResolvedAfterOneCell > allowedResolved) return false;

        int vulnerableAllowance = logicLevel >= 5 ? 0 : 1;
        return profile.vulnerableSingleCells <= vulnerableAllowance;
    }

    static int qualityBonus(SolutionStrategy strategy, int logicLevel, Profile profile) {
        if (profile == null || profile.hidden == 0) return 0;
        if (strategy == SolutionStrategy.CHAIN) {
            return Math.min(220, profile.maxAdditionalForcedAfterOneCell * 18);
        }
        double resilience = 1.0 - profile.maxResolvedFractionAfterOneCell;
        int bonus = (int) Math.round(resilience * (logicLevel >= 5 ? 260.0 : 180.0));
        bonus -= profile.vulnerableSingleCells * 70;
        return bonus;
    }
}
