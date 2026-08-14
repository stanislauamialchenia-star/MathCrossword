package com.offline.mathcrossword;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Abstract reasoning topology. It contains no arithmetic and no Android types.
 * A constructive builder chooses one of these families first, then maps that
 * topology into crossword slots and finally into equations.
 */
final class ReasoningGraph {
    enum Family {
        CHAIN_LONG("chain-long"),
        CHAIN_BRANCH("chain-branch"),
        CHAIN_CONVERGE("chain-converge"),
        HYPOTHESIS_FORK("hypothesis-fork"),
        HYPOTHESIS_DIAMOND("hypothesis-diamond"),
        HYPOTHESIS_CONTRADICTION("hypothesis-contradiction"),
        NETWORK_RING("network-ring"),
        NETWORK_HUB("network-hub"),
        NETWORK_TWO_CLUSTER("network-two-cluster"),
        NETWORK_DENSE("network-dense");

        final String id;
        Family(String id) { this.id = id; }
    }

    static final class Edge {
        final int from, to;
        Edge(int from, int to) { this.from = from; this.to = to; }
    }

    final Family family;
    final int nodeCount;
    final List<Edge> edges = new ArrayList<>();

    ReasoningGraph(Family family, int nodeCount) {
        this.family = family;
        this.nodeCount = nodeCount;
    }

    ReasoningGraph edge(int a, int b) {
        edges.add(new Edge(a, b));
        return this;
    }

    int degree(int node) {
        int d = 0;
        for (Edge e : edges) if (e.from == node || e.to == node) d++;
        return d;
    }

    int maxDegree() {
        int m = 0;
        for (int i = 0; i < nodeCount; i++) m = Math.max(m, degree(i));
        return m;
    }

    int cycleRank() {
        if (nodeCount == 0) return 0;
        Map<Integer, List<Integer>> adj = new HashMap<>();
        for (int i = 0; i < nodeCount; i++) adj.put(i, new ArrayList<>());
        for (Edge e : edges) {
            adj.get(e.from).add(e.to);
            adj.get(e.to).add(e.from);
        }
        Set<Integer> seen = new HashSet<>();
        int components = 0;
        for (int i = 0; i < nodeCount; i++) {
            if (seen.contains(i)) continue;
            components++;
            ArrayList<Integer> stack = new ArrayList<>();
            stack.add(i); seen.add(i);
            while (!stack.isEmpty()) {
                int v = stack.remove(stack.size() - 1);
                for (int n : adj.get(v)) if (seen.add(n)) stack.add(n);
            }
        }
        return Math.max(0, edges.size() - nodeCount + components);
    }

    static ReasoningGraph chain(Family family, int equations) {
        int n = Math.max(2, equations + 1);
        ReasoningGraph g = new ReasoningGraph(family, n);
        switch (family) {
            case CHAIN_BRANCH: {
                int trunk = Math.max(2, n / 2);
                for (int i = 0; i < trunk - 1; i++) g.edge(i, i + 1);
                int next = trunk;
                int root = Math.max(1, trunk - 2);
                while (next < n) {
                    g.edge(root, next);
                    if (next + 1 < n) g.edge(next, next + 1);
                    next += 2;
                    root = Math.min(trunk - 1, root + 1);
                }
                break;
            }
            case CHAIN_CONVERGE: {
                if (n < 6) return chain(Family.CHAIN_LONG, equations);
                // Two short branches join at one late node, then continue.
                g.edge(0, 1).edge(1, 2);
                g.edge(0, 3).edge(3, 4);
                g.edge(2, 5).edge(4, 5);
                for (int i = 5; i < n - 1; i++) g.edge(i, i + 1);
                break;
            }
            case CHAIN_LONG:
            default:
                for (int i = 0; i < n - 1; i++) g.edge(i, i + 1);
        }
        return g;
    }

    static ReasoningGraph hypothesis(Family family) {
        switch (family) {
            case HYPOTHESIS_CONTRADICTION: {
                // Experimental L5 family. The graph contains two branch layers
                // before reconvergence so a false local hypothesis has room to
                // survive one inference and fail later.
                ReasoningGraph g = new ReasoningGraph(family, 8);
                g.edge(0,1).edge(0,2)
                        .edge(1,3).edge(2,4)
                        .edge(3,5).edge(4,5)
                        .edge(3,6).edge(4,7).edge(6,7);
                return g;
            }
            case HYPOTHESIS_DIAMOND: {
                // Two plausible routes leave the same starting region and meet again.
                // The human-facing difficulty is created later by hidden/tile selection;
                // the graph simply guarantees a branch-and-reconverge skeleton.
                ReasoningGraph g = new ReasoningGraph(family, 6);
                g.edge(0, 1).edge(0, 2)
                        .edge(1, 3).edge(2, 4)
                        .edge(3, 5).edge(4, 5);
                return g;
            }
            case HYPOTHESIS_FORK:
            default: {
                ReasoningGraph g = new ReasoningGraph(family, 7);
                g.edge(0, 1).edge(0, 2).edge(0, 3)
                        .edge(1, 4).edge(2, 5).edge(3, 6);
                return g;
            }
        }
    }

    static ReasoningGraph network(Family family) {
        switch (family) {
            case NETWORK_HUB: {
                ReasoningGraph g = new ReasoningGraph(family, 7);
                for (int i = 1; i <= 5; i++) g.edge(0, i);
                g.edge(2, 6).edge(4, 6);
                return g;
            }
            case NETWORK_TWO_CLUSTER: {
                ReasoningGraph g = new ReasoningGraph(family, 8);
                g.edge(0,1).edge(1,2).edge(2,3).edge(3,0).edge(0,2);
                g.edge(4,5).edge(5,6).edge(6,7).edge(7,4).edge(4,6);
                g.edge(2,4);
                return g;
            }
            case NETWORK_DENSE: {
                ReasoningGraph g = new ReasoningGraph(family, 9);
                int[][] e = {{0,1},{1,2},{3,4},{4,5},{6,7},{7,8},
                        {0,3},{3,6},{1,4},{4,7},{2,5},{5,8},
                        {0,4},{2,4},{4,6},{4,8}};
                for (int[] x : e) g.edge(x[0], x[1]);
                return g;
            }
            case NETWORK_RING:
            default: {
                ReasoningGraph g = new ReasoningGraph(family, 6);
                for (int i = 0; i < 6; i++) g.edge(i, (i + 1) % 6);
                return g;
            }
        }
    }
}
