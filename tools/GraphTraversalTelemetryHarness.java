package com.offline.mathcrossword;

import org.json.JSONArray;
import org.json.JSONObject;

/** Deterministic JVM smoke checks for realized traversal classification. */
public final class GraphTraversalTelemetryHarness {
    public static void main(String[] args) throws Exception {
        backwardInternalEntry();
        bidirectionalReturn();
        divergentJump();
        System.out.println("GraphTraversalTelemetryHarness OK");
    }

    private static void backwardInternalEntry() throws Exception {
        ConcreteReasoningGraph graph = ConcreteReasoningGraph.synthetic(
                5, new int[][]{{0,1},{1,2},{2,3},{3,4}}, 0);
        JSONObject out = GraphTraversalTelemetry.analyze(graph, events(4, 3, 2, 1, 0));
        expect(out.optBoolean("available", false), "backward available");
        expect("backward".equals(out.optString("direction")), "backward direction");
        expect(out.optBoolean("internalEntry", false), "backward internal entry");
        expect(out.optBoolean("anchorReached", false), "backward reaches anchor");
        expect(out.optInt("entryDepth", -1) == 4, "backward entry depth");
    }

    private static void bidirectionalReturn() throws Exception {
        ConcreteReasoningGraph graph = ConcreteReasoningGraph.synthetic(
                5, new int[][]{{0,1},{1,2},{2,3},{3,4}}, 0);
        JSONObject out = GraphTraversalTelemetry.analyze(graph, events(3, 2, 1, 0, 1, 2, 3));
        expect("bidirectional".equals(out.optString("direction")), "bidirectional direction");
        expect(out.optBoolean("anchorReached", false), "bidirectional reaches anchor");
    }

    private static void divergentJump() throws Exception {
        ConcreteReasoningGraph graph = ConcreteReasoningGraph.synthetic(
                6, new int[][]{{0,1},{1,2},{2,3},{3,4},{4,5}}, 0);
        JSONObject out = GraphTraversalTelemetry.analyze(graph, events(5, 1, 4, 0));
        expect(out.optBoolean("structuralDivergence", false), "divergent structural flag");
        expect("divergent".equals(out.optString("direction")), "divergent direction");
    }

    private static JSONArray events(int... nodes) throws Exception {
        JSONArray out = new JSONArray();
        long t = 0L;
        for (int node : nodes) {
            JSONObject e = new JSONObject();
            e.put("type", "candidate_add");
            e.put("x", node);
            e.put("y", 0);
            e.put("value", node + 1);
            e.put("tMs", t);
            t += 1000L;
            out.put(e);
        }
        return out;
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
