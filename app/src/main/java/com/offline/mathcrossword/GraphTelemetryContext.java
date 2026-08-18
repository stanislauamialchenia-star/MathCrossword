package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Small session bridge for v1.41.
 *
 * HumanRouteComparator.modelRoute(puzzle) is called when the tracker is started
 * for the concrete puzzle. We capture the realized graph there and consume it
 * when the same tracker visit is finished and compare(...) receives the event
 * trace. This avoids touching PuzzleRun / Visit lifecycle code while keeping the
 * graph telemetry tied to the active puzzle.
 */
final class GraphTelemetryContext {
    private static ConcreteReasoningGraph currentGraph;

    private GraphTelemetryContext() { }

    static synchronized void capture(Puzzle puzzle) {
        currentGraph = ConcreteReasoningGraph.fromPuzzle(puzzle);
    }

    static synchronized JSONObject consume(JSONArray events) {
        ConcreteReasoningGraph graph = currentGraph;
        currentGraph = null;
        return GraphTraversalTelemetry.analyze(graph, events);
    }

    static synchronized void clear() {
        currentGraph = null;
    }
}
