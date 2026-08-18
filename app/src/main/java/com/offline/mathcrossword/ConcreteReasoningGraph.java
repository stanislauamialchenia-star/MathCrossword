package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Constraint graph reconstructed from the final realized puzzle.
 *
 * Unlike {@link ReasoningGraph}, this graph is not constructor intent. Its nodes
 * are actual hidden cells and an undirected edge means that two hidden cells
 * participate in at least one common realized equation. The graph intentionally
 * makes no claim about the player's private reasoning or causal solve direction.
 */
final class ConcreteReasoningGraph {
    static final class Node {
        final int id;
        final Pos pos;
        final Set<Integer> equationIds = new LinkedHashSet<>();
        int visibleSupport;
        int directConstraintEquations;

        Node(int id, Pos pos) {
            this.id = id;
            this.pos = pos;
        }
    }

    static final class Edge {
        final int a;
        final int b;
        final Set<Integer> equationIds = new LinkedHashSet<>();

        Edge(int a, int b) {
            this.a = Math.min(a, b);
            this.b = Math.max(a, b);
        }
    }

    final List<Node> nodes;
    final List<Edge> edges;
    final List<Integer> anchorCandidates;
    final double anchorConfidence;

    private final Map<Pos, Integer> nodeByPos;
    private final List<Set<Integer>> adjacency;
    private final int[] distanceFromAnchor;

    private ConcreteReasoningGraph(List<Node> nodes, List<Edge> edges,
                                   List<Integer> anchorCandidates, double anchorConfidence) {
        this.nodes = Collections.unmodifiableList(nodes);
        this.edges = Collections.unmodifiableList(edges);
        this.anchorCandidates = Collections.unmodifiableList(anchorCandidates);
        this.anchorConfidence = anchorConfidence;
        nodeByPos = new HashMap<>();
        adjacency = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            nodeByPos.put(nodes.get(i).pos, i);
            adjacency.add(new LinkedHashSet<>());
        }
        for (Edge edge : edges) {
            if (edge.a < 0 || edge.b < 0 || edge.a >= nodes.size() || edge.b >= nodes.size()) continue;
            adjacency.get(edge.a).add(edge.b);
            adjacency.get(edge.b).add(edge.a);
        }
        distanceFromAnchor = distances(anchorCandidates);
    }

    static ConcreteReasoningGraph fromPuzzle(Puzzle puzzle) {
        if (puzzle == null || puzzle.hidden.isEmpty()) {
            return new ConcreteReasoningGraph(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 0.0);
        }

        List<Pos> hidden = new ArrayList<>(puzzle.hidden);
        hidden.sort(Comparator.comparingInt((Pos p) -> p.y).thenComparingInt(p -> p.x));

        List<Node> nodes = new ArrayList<>(hidden.size());
        Map<Pos, Integer> nodeByPos = new LinkedHashMap<>();
        for (Pos pos : hidden) {
            int id = nodes.size();
            nodes.add(new Node(id, pos));
            nodeByPos.put(pos, id);
        }

        Map<Long, Edge> edges = new LinkedHashMap<>();
        for (int equationId = 0; equationId < puzzle.equations.size(); equationId++) {
            Equation equation = puzzle.equations.get(equationId);
            Pos[] vars = {equation.a, equation.b, equation.c};
            List<Integer> hiddenIds = new ArrayList<>(3);
            for (Pos pos : vars) {
                Integer id = nodeByPos.get(pos);
                if (id != null) hiddenIds.add(id);
            }
            int visiblePeers = Math.max(0, 3 - hiddenIds.size());
            for (int id : hiddenIds) {
                Node node = nodes.get(id);
                node.equationIds.add(equationId);
                node.visibleSupport += visiblePeers;
                if (hiddenIds.size() == 1) node.directConstraintEquations++;
            }
            for (int i = 0; i < hiddenIds.size(); i++) {
                for (int j = i + 1; j < hiddenIds.size(); j++) {
                    int a = hiddenIds.get(i), b = hiddenIds.get(j);
                    long key = edgeKey(a, b);
                    Edge edge = edges.get(key);
                    if (edge == null) {
                        edge = new Edge(a, b);
                        edges.put(key, edge);
                    }
                    edge.equationIds.add(equationId);
                }
            }
        }

        AnchorSelection anchors = chooseAnchors(nodes, edges.values());
        return new ConcreteReasoningGraph(nodes, new ArrayList<>(edges.values()),
                anchors.ids, anchors.confidence);
    }

    static ConcreteReasoningGraph synthetic(int nodeCount, int[][] rawEdges, int... anchors) {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) nodes.add(new Node(i, new Pos(i, 0)));
        Map<Long, Edge> unique = new LinkedHashMap<>();
        if (rawEdges != null) {
            for (int[] pair : rawEdges) {
                if (pair == null || pair.length < 2) continue;
                int a = pair[0], b = pair[1];
                if (a < 0 || b < 0 || a >= nodeCount || b >= nodeCount || a == b) continue;
                unique.put(edgeKey(a, b), new Edge(a, b));
            }
        }
        List<Integer> anchorIds = new ArrayList<>();
        if (anchors != null) for (int a : anchors) if (a >= 0 && a < nodeCount && !anchorIds.contains(a)) anchorIds.add(a);
        return new ConcreteReasoningGraph(nodes, new ArrayList<>(unique.values()), anchorIds,
                anchorIds.isEmpty() ? 0.0 : 1.0);
    }

    int nodeId(Pos pos) {
        Integer id = nodeByPos.get(pos);
        return id == null ? -1 : id;
    }

    boolean containsNode(int node) {
        return node >= 0 && node < nodes.size();
    }

    int degree(int node) {
        return containsNode(node) ? adjacency.get(node).size() : 0;
    }

    boolean adjacent(int a, int b) {
        return containsNode(a) && containsNode(b) && adjacency.get(a).contains(b);
    }

    int distanceToAnchor(int node) {
        return containsNode(node) ? distanceFromAnchor[node] : -1;
    }

    Set<Integer> neighbors(int node) {
        if (!containsNode(node)) return Collections.emptySet();
        return Collections.unmodifiableSet(adjacency.get(node));
    }

    private int[] distances(List<Integer> anchors) {
        int[] distance = new int[nodes.size()];
        java.util.Arrays.fill(distance, -1);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int anchor : anchors) {
            if (!containsNode(anchor) || distance[anchor] == 0) continue;
            distance[anchor] = 0;
            queue.add(anchor);
        }
        while (!queue.isEmpty()) {
            int v = queue.removeFirst();
            for (int next : adjacency.get(v)) {
                if (distance[next] >= 0) continue;
                distance[next] = distance[v] + 1;
                queue.addLast(next);
            }
        }
        return distance;
    }

    private static final class AnchorSelection {
        final List<Integer> ids;
        final double confidence;
        AnchorSelection(List<Integer> ids, double confidence) {
            this.ids = ids;
            this.confidence = confidence;
        }
    }

    private static AnchorSelection chooseAnchors(List<Node> nodes, Iterable<Edge> edges) {
        List<Integer> ids = new ArrayList<>();
        int bestDirect = 0;
        int bestVisible = 0;
        for (Node node : nodes) {
            bestDirect = Math.max(bestDirect, node.directConstraintEquations);
            bestVisible = Math.max(bestVisible, node.visibleSupport);
        }
        if (bestDirect > 0) {
            for (Node node : nodes) if (node.directConstraintEquations == bestDirect) ids.add(node.id);
            return new AnchorSelection(ids, 1.0);
        }
        if (bestVisible > 0) {
            for (Node node : nodes) if (node.visibleSupport == bestVisible) ids.add(node.id);
            return new AnchorSelection(ids, 0.65);
        }

        int[] degree = new int[nodes.size()];
        for (Edge edge : edges) {
            if (edge.a >= 0 && edge.a < degree.length) degree[edge.a]++;
            if (edge.b >= 0 && edge.b < degree.length) degree[edge.b]++;
        }
        for (Node node : nodes) if (degree[node.id] == 1) ids.add(node.id);
        if (!ids.isEmpty()) return new AnchorSelection(ids, 0.35);
        return new AnchorSelection(ids, 0.0);
    }

    private static long edgeKey(int a, int b) {
        int lo = Math.min(a, b), hi = Math.max(a, b);
        return (((long) lo) << 32) ^ (hi & 0xffffffffL);
    }
}
