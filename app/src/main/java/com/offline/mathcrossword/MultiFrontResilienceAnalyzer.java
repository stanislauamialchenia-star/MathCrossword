package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Measures whether a hard board leaves more than one meaningful place to work.
 *
 * A puzzle can be globally cyclic yet still have a single logical bottleneck. This
 * analyzer looks at the unresolved hidden-cell graph after opening propagation and
 * asks whether it already contains separate fronts, or can split into balanced
 * fronts around one articulation cell / bridge edge. It is descriptive for generic
 * boards and a validation gate for the explicit mixed-two-front constructor.
 */
final class MultiFrontResilienceAnalyzer {
    private MultiFrontResilienceAnalyzer() { }

    static final class Profile {
        int unresolved;
        int componentCount;
        int meaningfulComponents;
        int articulationFronts;
        int bridgeFronts;
        int alternativeFronts;
        int largestFront;
        int secondFront;
        double largestFrontFraction;
        double balance;
        int bottleneckDegree;

        boolean hasAlternativeFronts() {
            return alternativeFronts >= 2 && secondFront >= 2;
        }
    }

    static Profile analyze(Puzzle p) {
        Profile out = new Profile();
        if (p == null || p.hidden.isEmpty()) return out;

        HumanSolver.State base = HumanSolver.initialState(p);
        HumanSolver.Propagation opening = HumanSolver.propagateSingles(p, base);
        if (opening.contradiction) return out;

        Set<Pos> unresolved = new LinkedHashSet<>();
        for (Pos pos : p.hidden) if (!base.assigned.containsKey(pos)) unresolved.add(pos);
        out.unresolved = unresolved.size();
        if (unresolved.isEmpty()) return out;

        Map<Pos, Set<Pos>> graph = buildGraph(p, unresolved);
        List<Integer> baseSizes = componentSizes(graph, null, null);
        applySizes(out, baseSizes);
        out.componentCount = baseSizes.size();
        out.meaningfulComponents = countMeaningful(baseSizes);
        out.alternativeFronts = out.meaningfulComponents;

        // If the graph is already split, no need to search for bottlenecks.
        // Otherwise inspect articulation cells and bridge-like edges. Boards are
        // tiny, and this bounded O(V*(V+E)) work happens only after a candidate
        // has survived the expensive generator gates.
        if (out.alternativeFronts < 2 && unresolved.size() >= 5) {
            for (Pos removed : unresolved) {
                List<Integer> sizes = componentSizes(graph, removed, null);
                int meaningful = countMeaningful(sizes);
                if (meaningful >= 2 && betterSplit(sizes, out.articulationFronts, out.balance)) {
                    out.articulationFronts = meaningful;
                    applyBestSplit(out, sizes);
                    out.bottleneckDegree = graph.getOrDefault(removed, Collections.emptySet()).size();
                }
            }

            Set<String> seen = new HashSet<>();
            for (Map.Entry<Pos, Set<Pos>> e : graph.entrySet()) {
                for (Pos b : e.getValue()) {
                    String key = edgeKey(e.getKey(), b);
                    if (!seen.add(key)) continue;
                    List<Integer> sizes = componentSizes(graph, null, new Edge(e.getKey(), b));
                    int meaningful = countMeaningful(sizes);
                    if (meaningful >= 2 && betterSplit(sizes, out.bridgeFronts, out.balance)) {
                        out.bridgeFronts = meaningful;
                        applyBestSplit(out, sizes);
                    }
                }
            }
            out.alternativeFronts = Math.max(out.alternativeFronts,
                    Math.max(out.articulationFronts, out.bridgeFronts));
        }
        return out;
    }

    private static Map<Pos, Set<Pos>> buildGraph(Puzzle p, Set<Pos> unresolved) {
        Map<Pos, Set<Pos>> g = new LinkedHashMap<>();
        for (Pos pos : unresolved) g.put(pos, new LinkedHashSet<>());
        for (Equation e : p.equations) {
            List<Pos> nodes = new ArrayList<>(3);
            if (unresolved.contains(e.a)) nodes.add(e.a);
            if (unresolved.contains(e.b)) nodes.add(e.b);
            if (unresolved.contains(e.c)) nodes.add(e.c);
            for (int i = 0; i < nodes.size(); i++) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    g.get(nodes.get(i)).add(nodes.get(j));
                    g.get(nodes.get(j)).add(nodes.get(i));
                }
            }
        }
        return g;
    }

    private static List<Integer> componentSizes(Map<Pos, Set<Pos>> g, Pos removed, Edge removedEdge) {
        Set<Pos> seen = new HashSet<>();
        List<Integer> sizes = new ArrayList<>();
        for (Pos start : g.keySet()) {
            if (start.equals(removed) || seen.contains(start)) continue;
            int size = 0;
            ArrayDeque<Pos> q = new ArrayDeque<>();
            q.add(start); seen.add(start);
            while (!q.isEmpty()) {
                Pos a = q.removeFirst();
                size++;
                for (Pos b : g.getOrDefault(a, Collections.emptySet())) {
                    if (b.equals(removed) || seen.contains(b)) continue;
                    if (removedEdge != null && removedEdge.matches(a, b)) continue;
                    seen.add(b); q.addLast(b);
                }
            }
            sizes.add(size);
        }
        sizes.sort(Collections.reverseOrder());
        return sizes;
    }

    private static int countMeaningful(List<Integer> sizes) {
        int n = 0;
        for (int s : sizes) if (s >= 2) n++;
        return n;
    }

    private static void applySizes(Profile out, List<Integer> sizes) {
        if (sizes.isEmpty()) return;
        out.largestFront = Math.max(out.largestFront, sizes.get(0));
        if (sizes.size() > 1) out.secondFront = Math.max(out.secondFront, sizes.get(1));
        if (out.unresolved > 0) out.largestFrontFraction = out.largestFront / (double) out.unresolved;
        if (out.largestFront > 0 && out.secondFront > 0) out.balance = out.secondFront / (double) out.largestFront;
    }

    private static void applyBestSplit(Profile out, List<Integer> sizes) {
        if (sizes.isEmpty()) return;
        int largest = sizes.get(0);
        int second = sizes.size() > 1 ? sizes.get(1) : 0;
        double balance = largest == 0 ? 0.0 : second / (double) largest;
        if (second > out.secondFront || (second == out.secondFront && balance > out.balance)) {
            out.largestFront = largest;
            out.secondFront = second;
            out.balance = balance;
            out.largestFrontFraction = out.unresolved == 0 ? 0.0 : largest / (double) out.unresolved;
        }
    }

    private static boolean betterSplit(List<Integer> sizes, int existingFronts, double existingBalance) {
        int meaningful = countMeaningful(sizes);
        int largest = sizes.isEmpty() ? 0 : sizes.get(0);
        int second = sizes.size() > 1 ? sizes.get(1) : 0;
        double balance = largest == 0 ? 0.0 : second / (double) largest;
        return meaningful > existingFronts || (meaningful == existingFronts && balance > existingBalance);
    }

    private static String edgeKey(Pos a, Pos b) {
        int ah = a.hashCode(), bh = b.hashCode();
        return ah <= bh ? ah + ":" + bh : bh + ":" + ah;
    }

    static int qualityBonus(Profile p) {
        if (p == null || p.unresolved == 0) return 0;
        int score = 0;
        if (p.hasAlternativeFronts()) score += 120 + (int)Math.round(p.balance * 100.0);
        if (p.alternativeFronts >= 3) score += 60;
        if (p.largestFrontFraction > 0.85 && p.alternativeFronts < 2) score -= 70;
        return Math.max(-100, Math.min(300, score));
    }

    private static final class Edge {
        final Pos a, b;
        Edge(Pos a, Pos b) { this.a = a; this.b = b; }
        boolean matches(Pos x, Pos y) {
            return (a.equals(x) && b.equals(y)) || (a.equals(y) && b.equals(x));
        }
    }
}
