package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapts recorded interaction events onto the realized concrete constraint graph.
 * Existing HumanRouteComparator output is intentionally left untouched.
 */
final class GraphTraversalTelemetry {
    static final int VERSION = 1;

    private GraphTraversalTelemetry() { }

    static JSONObject analyze(ConcreteReasoningGraph graph, JSONArray events) {
        JSONObject out = new JSONObject();
        try {
            out.put("version", VERSION);
            if (graph == null) {
                out.put("available", false);
                return out;
            }

            JSONArray route = new JSONArray();
            List<Integer> observed = observedNodes(graph, events, route);
            out.put("available", !graph.nodes.isEmpty() && !observed.isEmpty());
            out.put("graphNodes", graph.nodes.size());
            out.put("graphEdges", graph.edges.size());
            out.put("anchorCandidates", graph.anchorCandidates.size());
            out.put("anchorConfidencePct", 100.0 * graph.anchorConfidence);
            out.put("observedGraphRoute", route);
            if (observed.isEmpty()) return out;

            TraversalClassifier.Result result = TraversalClassifier.classify(graph, observed);
            out.put("direction", result.direction.name().toLowerCase(Locale.US));
            out.put("internalEntry", result.internalEntry);
            out.put("branchProbing", result.branchProbing);
            out.put("anchorReached", result.anchorReached);
            out.put("structuralDivergence", result.structuralDivergence);
            out.put("entryDepth", result.entryDepth);
            out.put("maxDepth", result.maxDepth);
            out.put("mappedNodes", result.mappedNodes);
            out.put("offGraphNodes", result.offGraphNodes);
            out.put("forwardTransitions", result.forwardTransitions);
            out.put("backwardTransitions", result.backwardTransitions);
            out.put("lateralTransitions", result.lateralTransitions);
            out.put("adjacentTransitions", result.adjacentTransitions);
            out.put("nonAdjacentTransitions", result.nonAdjacentTransitions);
            out.put("mappedPct", 100.0 * result.mappedFraction);
            out.put("adjacencyContinuityPct", 100.0 * result.adjacencyContinuity);
            out.put("confidencePct", 100.0 * result.confidence);
        } catch (Exception ignored) { }
        return out;
    }

    private static List<Integer> observedNodes(ConcreteReasoningGraph graph, JSONArray events, JSONArray route) {
        List<Integer> out = new ArrayList<>();
        if (events == null) return out;
        Integer previous = null;
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null || !meaningful(event.optString("type", ""))) continue;
            if (!event.has("x") || !event.has("y")) continue;

            int x = event.optInt("x"), y = event.optInt("y");
            int node = graph.nodeId(new Pos(x, y));
            if (previous != null && previous == node) continue; // compress repeated edits in the same cell
            previous = node;
            out.add(node);

            JSONObject visit = new JSONObject();
            try {
                visit.put("node", node);
                visit.put("x", x);
                visit.put("y", y);
                visit.put("tMs", event.optLong("tMs", 0L));
                visit.put("firstType", event.optString("type", ""));
                visit.put("anchorDistance", graph.distanceToAnchor(node));
            } catch (Exception ignored) { }
            route.put(visit);
        }
        return out;
    }

    private static boolean meaningful(String type) {
        return "candidate_add".equals(type)
                || "candidate_remove".equals(type)
                || "place".equals(type)
                || "remove".equals(type);
    }
}
