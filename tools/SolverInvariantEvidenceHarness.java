package com.offline.mathcrossword;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Deterministic JVM checks for the pre-registered solver-invariant evidence layer. */
public final class SolverInvariantEvidenceHarness {
    public static void main(String[] args) throws Exception {
        visitEvidenceUsesRecordedCandidateFields();
        nestedGraphTraversalWins();
        legacyTopLevelGraphStillWorks();
        crossStrategyCoverageRequiresEnoughEvidence();
        System.out.println("SolverInvariantEvidenceHarness OK");
    }

    private static void visitEvidenceUsesRecordedCandidateFields() throws Exception {
        JSONObject row = baseRow("CHAIN", "run-a", "backward", true, true, true);
        row.put("distinctCandidateCells", 3);
        row.put("candidateSequenceDistinctCells", 9);
        row.put("candidateCommitments", 2);
        row.put("avgCandidateCommitmentMs", 1400L);

        JSONObject evidence = SolverInvariantEvidence.forVisit(row);
        JSONObject candidates = evidence.optJSONObject("candidates");
        JSONObject signals = evidence.optJSONObject("evidenceSignals");
        expect(candidates != null, "candidate evidence exists");
        expect(candidates.optInt("distinctCells", -1) == 3, "uses distinctCandidateCells");
        expect(candidates.optInt("commitments", -1) == 2, "candidate commitments preserved");
        expect(signals != null && signals.optBoolean("candidateSearchObserved", false), "candidate work signal");
        expect(signals.optBoolean("propagationSignalObserved", false), "propagation signal");
        expect(signals.optBoolean("branchSignalObserved", false), "branch signal");
        expect("disabled".equals(evidence.optString("autoTheoryVerdict")), "no automatic theory verdict");
    }

    private static void nestedGraphTraversalWins() throws Exception {
        JSONObject row = baseRow("NETWORK", "run-b", "bidirectional", true, false, true);
        JSONObject legacy = graph("divergent", false, false, true);
        row.put("graphTraversal", legacy);

        JSONObject evidence = SolverInvariantEvidence.forVisit(row);
        JSONObject realized = evidence.optJSONObject("realizedGraphTraversal");
        expect(realized != null && "bidirectional".equals(realized.optString("direction")),
                "nested routeComparison.graphTraversal has priority");
        expect(realized.optBoolean("internalEntry", false), "bidirectional sample preserves internal entry");
    }

    private static void legacyTopLevelGraphStillWorks() throws Exception {
        JSONObject row = baseRow("DEDUCTION", "run-c", null, true, false, false);
        row.put("graphTraversal", graph("forward", false, false, false));
        JSONObject evidence = SolverInvariantEvidence.forVisit(row);
        JSONObject realized = evidence.optJSONObject("realizedGraphTraversal");
        expect(realized != null && realized.optBoolean("available", false), "legacy top-level graph available");
        expect("forward".equals(realized.optString("direction")), "legacy direction preserved");
    }

    private static void crossStrategyCoverageRequiresEnoughEvidence() throws Exception {
        String[] strategies = {"CHAIN", "NETWORK", "DEDUCTION", "HYPOTHESIS", "MIXED"};
        List<JSONObject> first = new ArrayList<>();
        for (int i = 0; i < strategies.length; i++) {
            first.add(baseRow(strategies[i], "run-first-" + i, i % 2 == 0 ? "backward" : "forward",
                    true, i % 2 == 0, i % 3 == 0));
        }
        JSONObject five = SolverInvariantEvidence.aggregate(first);
        expect(five.optBoolean("allRequestedStrategiesHaveGraphEvidence", false), "all five strategies covered");
        expect(!five.optBoolean("readyForFirstCrossStrategyReview", true), "five graph visits are not enough");

        List<JSONObject> ten = new ArrayList<>(first);
        for (int i = 0; i < strategies.length; i++) {
            ten.add(baseRow(strategies[i], "run-second-" + i, i % 2 == 0 ? "bidirectional" : "forward",
                    true, true, true));
        }
        JSONObject full = SolverInvariantEvidence.aggregate(ten);
        expect(full.optInt("visitsWithRealizedGraphTrace", 0) == 10, "ten graph visits counted");
        expect(full.optBoolean("allRequestedStrategiesHaveGraphEvidence", false), "cross-strategy coverage remains complete");
        expect(full.optBoolean("readyForFirstCrossStrategyReview", false), "ten covered graph visits unlock first review");
    }

    private static JSONObject baseRow(String strategy, String runId, String direction,
                                      boolean candidateWork, boolean cascade, boolean hypothesis) throws Exception {
        JSONObject row = new JSONObject();
        row.put("sessionId", "visit-" + runId);
        row.put("runId", runId);
        row.put("puzzleId", "puzzle-" + runId);
        row.put("visitIndex", 1);
        row.put("mode", "FREE");
        row.put("level", 0);
        row.put("strategy", strategy);
        row.put("seed", runId.hashCode());
        row.put("logic", 10);
        row.put("calc", 10);
        row.put("generatorVersion", 1);
        row.put("runOutcome", RunLifecycle.SOLVED);
        row.put("solved", true);
        row.put("activeMs", 60000L);
        row.put("rapidCascades", cascade ? 2 : 0);
        row.put("hypothesisEpisodes", hypothesis ? 1 : 0);
        row.put("resourceConflictDecoyCount", 1);
        row.put("branchGoodPivotCount", hypothesis ? 1 : 0);
        row.put("branchSeriousFalseBranches", hypothesis ? 1 : 0);
        row.put("branchMaxWidth", hypothesis ? 2 : 1);
        row.put("branchMaxInformationGain", cascade ? 4 : 1);
        row.put("reasoningFronts", 2);
        row.put("events", candidateWork ? candidateEvents() : new JSONArray());
        row.put("eventCount", row.optJSONArray("events").length());

        JSONObject route = new JSONObject();
        route.put("available", true);
        route.put("agreementPct", 60.0);
        route.put("earlyAgreementPct", 40.0);
        route.put("orderAgreementPct", 70.0);
        route.put("alternateEntry", direction != null && ("backward".equals(direction) || "bidirectional".equals(direction)));
        route.put("strongDivergence", false);
        if (direction != null) route.put("graphTraversal", graph(direction,
                "backward".equals(direction) || "bidirectional".equals(direction), hypothesis, false));
        row.put("routeComparison", route);
        return row;
    }

    private static JSONObject graph(String direction, boolean internalEntry,
                                    boolean branchProbe, boolean divergent) throws Exception {
        JSONObject graph = new JSONObject();
        graph.put("available", true);
        graph.put("direction", direction);
        graph.put("internalEntry", internalEntry);
        graph.put("branchProbing", branchProbe);
        graph.put("anchorReached", true);
        graph.put("structuralDivergence", divergent);
        graph.put("entryDepth", internalEntry ? 3 : 0);
        graph.put("maxDepth", 4);
        graph.put("mappedPct", 100.0);
        graph.put("adjacencyContinuityPct", divergent ? 30.0 : 100.0);
        graph.put("confidencePct", 90.0);
        graph.put("anchorConfidencePct", 100.0);
        graph.put("observedGraphRoute", new JSONArray());
        return graph;
    }

    private static JSONArray candidateEvents() throws Exception {
        JSONArray events = new JSONArray();
        events.put(event("candidate_add", 1, 1, 17));
        events.put(event("candidate_add", 1, 1, 25));
        events.put(event("candidate_remove", 1, 1, 25));
        events.put(event("place", 1, 1, 17));
        return events;
    }

    private static JSONObject event(String type, int x, int y, int value) throws Exception {
        JSONObject event = new JSONObject();
        event.put("type", type);
        event.put("x", x);
        event.put("y", y);
        event.put("value", value);
        return event;
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
