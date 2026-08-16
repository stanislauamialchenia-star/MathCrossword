package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure graph-theory layer for MathCrossword.
 *
 * Two related graphs are supported:
 * 1) ReasoningGraph: the abstract topology requested by constructive builders.
 * 2) Puzzle constraint graph: a bipartite factor graph where number cells are
 *    variable vertices and equations are factor vertices connected to a/b/c.
 *
 * The factor representation is intentional: projecting every equation directly
 * to a triangle would create one artificial cycle per equation and would hide
 * the loops that actually come from interactions between equations.
 */
final class GraphAnalyzer {
    private GraphAnalyzer() { }

    static final class Metrics {
        final int nodes;
        final int edges;
        final int components;
        final int cycleRank;
        final int bridges;
        final int articulationPoints;
        final int maxDegree;
        final int degreeOneNodes;
        final int branchNodes;
        final int diameter;
        final double averageDegree;

        // Puzzle factor-graph metadata. Zero for abstract ReasoningGraph metrics.
        final int variableNodes;
        final int factorNodes;
        final int variableArticulations;
        final int factorArticulations;
        final int hiddenVariableArticulations;

        Metrics(int nodes, int edges, int components, int cycleRank,
                int bridges, int articulationPoints, int maxDegree,
                int degreeOneNodes, int branchNodes, int diameter,
                double averageDegree,
                int variableNodes, int factorNodes,
                int variableArticulations, int factorArticulations,
                int hiddenVariableArticulations) {
            this.nodes = nodes;
            this.edges = edges;
            this.components = components;
            this.cycleRank = cycleRank;
            this.bridges = bridges;
            this.articulationPoints = articulationPoints;
            this.maxDegree = maxDegree;
            this.degreeOneNodes = degreeOneNodes;
            this.branchNodes = branchNodes;
            this.diameter = diameter;
            this.averageDegree = averageDegree;
            this.variableNodes = variableNodes;
            this.factorNodes = factorNodes;
            this.variableArticulations = variableArticulations;
            this.factorArticulations = factorArticulations;
            this.hiddenVariableArticulations = hiddenVariableArticulations;
        }

        boolean connected() { return nodes == 0 || components == 1; }

        String compact() {
            return "V=" + nodes
                    + ",E=" + edges
                    + ",C=" + components
                    + ",mu=" + cycleRank
                    + ",bridges=" + bridges
                    + ",cuts=" + articulationPoints
                    + ",degMax=" + maxDegree
                    + ",diam=" + diameter;
        }
    }

    static Metrics analyze(ReasoningGraph graph) {
        if (graph == null) return empty();
        List<int[]> edges = new ArrayList<>();
        for (ReasoningGraph.Edge e : graph.edges) {
            if (e == null) continue;
            if (e.from < 0 || e.to < 0 || e.from >= graph.nodeCount || e.to >= graph.nodeCount) continue;
            if (e.from == e.to) continue;
            edges.add(new int[]{e.from, e.to});
        }
        return analyzeUndirected(graph.nodeCount, edges, 0, 0, null);
    }

    static Metrics analyze(Puzzle puzzle) {
        if (puzzle == null) return empty();

        Map<Pos, Integer> variableIndex = new LinkedHashMap<>();
        for (Map.Entry<Pos, Cell> e : puzzle.cells.entrySet()) {
            if (e.getValue() != null && e.getValue().kind == Kind.NUMBER) {
                variableIndex.put(e.getKey(), variableIndex.size());
            }
        }

        int variables = variableIndex.size();
        int factors = puzzle.equations.size();
        List<int[]> edges = new ArrayList<>(factors * 3);
        for (int i = 0; i < puzzle.equations.size(); i++) {
            Equation equation = puzzle.equations.get(i);
            int factor = variables + i;
            addFactorEdge(edges, variableIndex, equation.a, factor);
            addFactorEdge(edges, variableIndex, equation.b, factor);
            addFactorEdge(edges, variableIndex, equation.c, factor);
        }

        boolean[] hiddenVariables = new boolean[variables];
        for (Pos pos : puzzle.hidden) {
            Integer index = variableIndex.get(pos);
            if (index != null) hiddenVariables[index] = true;
        }

        return analyzeUndirected(variables + factors, edges, variables, factors, hiddenVariables);
    }

    private static void addFactorEdge(List<int[]> edges, Map<Pos, Integer> variableIndex,
                                      Pos variable, int factor) {
        Integer index = variableIndex.get(variable);
        if (index != null) edges.add(new int[]{index, factor});
    }

    private static Metrics analyzeUndirected(int nodeCount, List<int[]> rawEdges,
                                             int variableNodes, int factorNodes,
                                             boolean[] hiddenVariables) {
        if (nodeCount <= 0) return empty();

        List<List<Integer>> adjacency = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) adjacency.add(new ArrayList<>());

        Set<Long> unique = new HashSet<>();
        for (int[] edge : rawEdges) {
            if (edge == null || edge.length < 2) continue;
            int a = edge[0], b = edge[1];
            if (a < 0 || b < 0 || a >= nodeCount || b >= nodeCount || a == b) continue;
            int lo = Math.min(a, b), hi = Math.max(a, b);
            long key = (((long) lo) << 32) ^ (hi & 0xffffffffL);
            if (!unique.add(key)) continue;
            adjacency.get(a).add(b);
            adjacency.get(b).add(a);
        }

        int edges = unique.size();
        int components = countComponents(adjacency);
        int cycleRank = Math.max(0, edges - nodeCount + components);

        int maxDegree = 0;
        int degreeOne = 0;
        int branchNodes = 0;
        long degreeSum = 0L;
        for (List<Integer> neighbors : adjacency) {
            int degree = neighbors.size();
            degreeSum += degree;
            maxDegree = Math.max(maxDegree, degree);
            if (degree == 1) degreeOne++;
            if (degree >= 3) branchNodes++;
        }
        double averageDegree = nodeCount == 0 ? 0.0 : degreeSum / (double) nodeCount;

        TarjanResult tarjan = tarjan(adjacency);
        int variableCuts = 0;
        int factorCuts = 0;
        int hiddenCuts = 0;
        if (variableNodes > 0) {
            for (int i = 0; i < tarjan.articulation.length; i++) {
                if (!tarjan.articulation[i]) continue;
                if (i < variableNodes) {
                    variableCuts++;
                    if (hiddenVariables != null && i < hiddenVariables.length && hiddenVariables[i]) hiddenCuts++;
                } else {
                    factorCuts++;
                }
            }
        }

        return new Metrics(nodeCount, edges, components, cycleRank,
                tarjan.bridges, tarjan.articulationCount,
                maxDegree, degreeOne, branchNodes, diameter(adjacency), averageDegree,
                variableNodes, factorNodes, variableCuts, factorCuts, hiddenCuts);
    }

    private static int countComponents(List<List<Integer>> adjacency) {
        boolean[] seen = new boolean[adjacency.size()];
        int components = 0;
        for (int start = 0; start < adjacency.size(); start++) {
            if (seen[start]) continue;
            components++;
            ArrayDeque<Integer> stack = new ArrayDeque<>();
            stack.push(start);
            seen[start] = true;
            while (!stack.isEmpty()) {
                int v = stack.pop();
                for (int next : adjacency.get(v)) {
                    if (!seen[next]) {
                        seen[next] = true;
                        stack.push(next);
                    }
                }
            }
        }
        return components;
    }

    private static final class TarjanResult {
        final boolean[] articulation;
        int bridges;
        int articulationCount;
        TarjanResult(int n) { articulation = new boolean[n]; }
    }

    private static TarjanResult tarjan(List<List<Integer>> adjacency) {
        int n = adjacency.size();
        int[] discovery = new int[n];
        int[] low = new int[n];
        int[] parent = new int[n];
        Arrays.fill(discovery, -1);
        Arrays.fill(parent, -1);
        TarjanResult result = new TarjanResult(n);
        int[] time = {0};

        for (int i = 0; i < n; i++) {
            if (discovery[i] == -1) dfsTarjan(i, adjacency, discovery, low, parent, time, result);
        }
        for (boolean cut : result.articulation) if (cut) result.articulationCount++;
        return result;
    }

    private static void dfsTarjan(int u, List<List<Integer>> adjacency,
                                  int[] discovery, int[] low, int[] parent,
                                  int[] time, TarjanResult result) {
        discovery[u] = low[u] = time[0]++;
        int children = 0;
        for (int v : adjacency.get(u)) {
            if (discovery[v] == -1) {
                parent[v] = u;
                children++;
                dfsTarjan(v, adjacency, discovery, low, parent, time, result);
                low[u] = Math.min(low[u], low[v]);

                if (parent[u] == -1 && children > 1) result.articulation[u] = true;
                if (parent[u] != -1 && low[v] >= discovery[u]) result.articulation[u] = true;
                if (low[v] > discovery[u]) result.bridges++;
            } else if (v != parent[u]) {
                low[u] = Math.min(low[u], discovery[v]);
            }
        }
    }

    private static int diameter(List<List<Integer>> adjacency) {
        int best = 0;
        for (int start = 0; start < adjacency.size(); start++) {
            int[] distance = new int[adjacency.size()];
            Arrays.fill(distance, -1);
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            distance[start] = 0;
            queue.add(start);
            while (!queue.isEmpty()) {
                int v = queue.removeFirst();
                best = Math.max(best, distance[v]);
                for (int next : adjacency.get(v)) {
                    if (distance[next] >= 0) continue;
                    distance[next] = distance[v] + 1;
                    queue.addLast(next);
                }
            }
        }
        return best;
    }

    private static Metrics empty() {
        return new Metrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0,
                0, 0, 0, 0, 0);
    }
}
