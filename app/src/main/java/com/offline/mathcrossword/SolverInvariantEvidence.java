package com.offline.mathcrossword;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Derives compact evidence for the pre-registered solver-invariant experiment.
 *
 * This class intentionally does not decide whether H1 is true. It only reshapes
 * recorded interaction telemetry into a review-friendly form. Final labels such
 * as INVARIANT_FITS or INVARIANT_BREAK_CANDIDATE remain an offline/manual step.
 */
final class SolverInvariantEvidence {
    static final int VERSION = 1;

    private SolverInvariantEvidence() { }

    static JSONObject forVisit(JSONObject row) {
        JSONObject out = new JSONObject();
        try {
            out.put("version", VERSION);
            out.put("measurementBoundary", "interaction_trace_not_private_reasoning");
            out.put("autoTheoryVerdict", "disabled");

            copyString(row, out, "sessionId");
            copyString(row, out, "runId");
            copyString(row, out, "puzzleId");
            copyInt(row, out, "visitIndex");
            copyString(row, out, "mode");
            copyInt(row, out, "level");
            copyString(row, out, "strategy");
            copyLong(row, out, "seed");
            copyInt(row, out, "logic");
            copyInt(row, out, "calc");
            copyInt(row, out, "generatorVersion");

            String outcome = rowOutcome(row);
            out.put("runOutcome", outcome);
            out.put("solved", RunLifecycle.SOLVED.equals(outcome));
            out.put("activeMs", Math.max(0L, row.optLong("activeMs", 0L)));
            out.put("eventCount", Math.max(0, row.optInt("eventCount", 0)));

            JSONObject candidates = new JSONObject();
            candidates.put("edits", countCandidateEdits(row.optJSONArray("events")));
            candidates.put("distinctCells", row.optInt("distinctCandidateCells",
                    row.optInt("candidateSequenceDistinctCells", 0)));
            candidates.put("cellSwitches", row.optInt("candidateCellSwitches", 0));
            candidates.put("cellRevisits", row.optInt("candidateCellRevisits", 0));
            candidates.put("maxObservedWidth", row.optInt("maxCandidatesInOneCell", 0));
            candidates.put("commitments", row.optInt("candidateCommitments", 0));
            candidates.put("avgCommitmentMs", row.optLong("avgCandidateCommitmentMs", 0L));
            out.put("candidates", candidates);

            JSONObject behavior = new JSONObject();
            behavior.put("hypothesisEpisodes", row.optInt("hypothesisEpisodes", 0));
            behavior.put("rapidCascades", row.optInt("rapidCascades", 0));
            behavior.put("productivePauses", row.optInt("productivePauses", 0));
            behavior.put("deadEndPauses", row.optInt("deadEndPauses", 0));
            behavior.put("recoveryEpisodes", row.optInt("recoveryEpisodes", 0));
            behavior.put("fullIncorrectCount", row.optInt("fullIncorrectCount", 0));
            behavior.put("undoCount", countType(row.optJSONArray("events"), "undo"));
            out.put("behavior", behavior);

            JSONObject generator = new JSONObject();
            generator.put("goodPivots", row.optInt("branchGoodPivotCount", 0));
            generator.put("seriousFalseBranches", row.optInt("branchSeriousFalseBranches", 0));
            generator.put("maxBranchWidth", row.optInt("branchMaxWidth", 0));
            generator.put("maxBranchInformationGain", row.optInt("branchMaxInformationGain", 0));
            generator.put("resourceConflictDecoys", row.optInt("resourceConflictDecoyCount", 0));
            generator.put("reasoningFronts", row.optInt("reasoningFronts", 0));
            generator.put("contradictionKernel", row.optBoolean("contradictionKernel", false));
            generator.put("contradictionKernelDepth", row.optInt("contradictionKernelDepth", 0));
            generator.put("contradictionKernelFamily", row.optString("contradictionKernelFamily", "none"));
            out.put("generatorSignals", generator);

            JSONObject route = row.optJSONObject("routeComparison");
            JSONObject routeEvidence = new JSONObject();
            if (route != null) {
                routeEvidence.put("available", route.optBoolean("available", false));
                routeEvidence.put("agreementPct", route.optDouble("agreementPct", 0.0));
                routeEvidence.put("earlyAgreementPct", route.optDouble("earlyAgreementPct", 0.0));
                routeEvidence.put("orderAgreementPct", route.optDouble("orderAgreementPct", 0.0));
                routeEvidence.put("alternateEntry", route.optBoolean("alternateEntry", false));
                routeEvidence.put("strongDivergence", route.optBoolean("strongDivergence", false));
            } else {
                routeEvidence.put("available", false);
            }
            out.put("humanRouteComparison", routeEvidence);

            JSONObject graph = graphTraversal(row);
            JSONObject graphEvidence = new JSONObject();
            boolean graphAvailable = graph != null && graph.optBoolean("available", false);
            graphEvidence.put("available", graphAvailable);
            if (graph != null) {
                graphEvidence.put("direction", graph.optString("direction", "unknown"));
                graphEvidence.put("internalEntry", graph.optBoolean("internalEntry", false));
                graphEvidence.put("branchProbing", graph.optBoolean("branchProbing", false));
                graphEvidence.put("anchorReached", graph.optBoolean("anchorReached", false));
                graphEvidence.put("structuralDivergence", graph.optBoolean("structuralDivergence", false));
                graphEvidence.put("entryDepth", graph.optInt("entryDepth", -1));
                graphEvidence.put("maxDepth", graph.optInt("maxDepth", -1));
                graphEvidence.put("mappedPct", graph.optDouble("mappedPct", 0.0));
                graphEvidence.put("adjacencyContinuityPct", graph.optDouble("adjacencyContinuityPct", 0.0));
                graphEvidence.put("confidencePct", graph.optDouble("confidencePct", 0.0));
                graphEvidence.put("anchorConfidencePct", graph.optDouble("anchorConfidencePct", 0.0));
                JSONArray observed = graph.optJSONArray("observedGraphRoute");
                if (observed != null) graphEvidence.put("observedRoute", observed);
            }
            out.put("realizedGraphTraversal", graphEvidence);

            int candidateEdits = candidates.optInt("edits", 0);
            int hypotheses = behavior.optInt("hypothesisEpisodes", 0);
            int cascades = behavior.optInt("rapidCascades", 0);
            boolean graphBranch = graph != null && graph.optBoolean("branchProbing", false);
            boolean graphDivergence = graph != null && graph.optBoolean("structuralDivergence", false);
            String direction = graph == null ? "unknown" : graph.optString("direction", "unknown");
            boolean alternateTraversal = graphAvailable && (graph.optBoolean("internalEntry", false)
                    || "backward".equals(direction) || "bidirectional".equals(direction));

            JSONObject signals = new JSONObject();
            signals.put("candidateSearchObserved", candidateEdits > 0);
            signals.put("propagationSignalObserved", cascades > 0);
            signals.put("branchSignalObserved", hypotheses > 0 || graphBranch);
            signals.put("coherentGraphTraversal", graphAvailable && !graphDivergence);
            signals.put("alternateTraversalObserved", alternateTraversal);
            signals.put("resourceConstraintOpportunity", row.optInt("resourceConflictDecoyCount", 0) > 0);
            boolean review = RunLifecycle.SOLVED.equals(outcome) && graphAvailable && graphDivergence
                    && route != null && route.optBoolean("strongDivergence", false);
            signals.put("manualReviewSuggested", review);
            out.put("evidenceSignals", signals);

            String quality;
            if (graphAvailable && candidateEdits > 0) quality = "strong";
            else if (graphAvailable || candidateEdits > 0) quality = "medium";
            else quality = "low";
            out.put("evidenceQuality", quality);
        } catch (Exception ignored) { }
        return out;
    }

    static JSONObject aggregate(List<JSONObject> rows) {
        JSONObject out = new JSONObject();
        try {
            out.put("version", VERSION);
            out.put("measurementBoundary", "interaction_trace_not_private_reasoning");
            out.put("autoTheoryVerdict", "disabled");
            out.put("visitRows", rows == null ? 0 : rows.size());

            int graphVisits = 0;
            int candidateVisits = 0;
            int propagationVisits = 0;
            int branchVisits = 0;
            int coherentGraphVisits = 0;
            int alternateTraversalVisits = 0;
            int reviewVisits = 0;
            int strongEvidenceVisits = 0;
            int mediumEvidenceVisits = 0;
            long entryDepthTotal = 0L;
            int entryDepthCount = 0;
            long maxDepthTotal = 0L;
            int maxDepthCount = 0;
            double confidenceTotal = 0.0;

            Map<String, Integer> directions = new LinkedHashMap<>();
            Map<String, StrategyCoverage> byStrategy = new LinkedHashMap<>();
            Set<String> runIds = new LinkedHashSet<>();
            Set<String> runsWithGraph = new LinkedHashSet<>();
            int legacyRun = 0;

            if (rows != null) for (JSONObject row : rows) {
                String runId = row.optString("runId", "");
                if (runId.isEmpty()) {
                    String sessionId = row.optString("sessionId", "");
                    runId = sessionId.isEmpty() ? ("legacy-" + legacyRun++) : ("legacy-session-" + sessionId);
                }
                runIds.add(runId);

                String strategy = row.optString("strategy", row.optString("style", "UNKNOWN"));
                StrategyCoverage coverage = byStrategy.get(strategy);
                if (coverage == null) {
                    coverage = new StrategyCoverage();
                    byStrategy.put(strategy, coverage);
                }
                coverage.visits++;

                int candidateEdits = countCandidateEdits(row.optJSONArray("events"));
                if (candidateEdits > 0) {
                    candidateVisits++;
                    coverage.candidateVisits++;
                }
                if (row.optInt("rapidCascades", 0) > 0) {
                    propagationVisits++;
                    coverage.propagationVisits++;
                }

                JSONObject graph = graphTraversal(row);
                boolean graphAvailable = graph != null && graph.optBoolean("available", false);
                boolean graphBranch = graphAvailable && graph.optBoolean("branchProbing", false);
                if (row.optInt("hypothesisEpisodes", 0) > 0 || graphBranch) {
                    branchVisits++;
                    coverage.branchVisits++;
                }

                if (graphAvailable) {
                    graphVisits++;
                    coverage.graphVisits++;
                    runsWithGraph.add(runId);
                    String direction = graph.optString("direction", "unknown");
                    directions.put(direction, directions.getOrDefault(direction, 0) + 1);
                    int entryDepth = graph.optInt("entryDepth", -1);
                    if (entryDepth >= 0) { entryDepthTotal += entryDepth; entryDepthCount++; }
                    int maxDepth = graph.optInt("maxDepth", -1);
                    if (maxDepth >= 0) { maxDepthTotal += maxDepth; maxDepthCount++; }
                    confidenceTotal += graph.optDouble("confidencePct", 0.0);
                    boolean coherent = !graph.optBoolean("structuralDivergence", false);
                    if (coherent) {
                        coherentGraphVisits++;
                        coverage.coherentGraphVisits++;
                    }
                    boolean alternate = graph.optBoolean("internalEntry", false)
                            || "backward".equals(direction) || "bidirectional".equals(direction);
                    if (alternate) {
                        alternateTraversalVisits++;
                        coverage.alternateTraversalVisits++;
                    }

                    JSONObject route = row.optJSONObject("routeComparison");
                    boolean review = RunLifecycle.SOLVED.equals(rowOutcome(row))
                            && graph.optBoolean("structuralDivergence", false)
                            && route != null && route.optBoolean("strongDivergence", false);
                    if (review) {
                        reviewVisits++;
                        coverage.reviewSuggestedVisits++;
                    }
                }

                if (graphAvailable && candidateEdits > 0) strongEvidenceVisits++;
                else if (graphAvailable || candidateEdits > 0) mediumEvidenceVisits++;
            }

            out.put("puzzleRunsRepresented", runIds.size());
            out.put("runsWithRealizedGraphTrace", runsWithGraph.size());
            out.put("visitsWithRealizedGraphTrace", graphVisits);
            out.put("visitsWithCandidateWork", candidateVisits);
            out.put("visitsWithPropagationSignal", propagationVisits);
            out.put("visitsWithBranchSignal", branchVisits);
            out.put("coherentGraphVisits", coherentGraphVisits);
            out.put("alternateTraversalVisits", alternateTraversalVisits);
            out.put("manualReviewSuggestedVisits", reviewVisits);
            out.put("strongEvidenceVisits", strongEvidenceVisits);
            out.put("mediumEvidenceVisits", mediumEvidenceVisits);
            out.put("avgEntryDepth", entryDepthCount == 0 ? 0.0 : entryDepthTotal / (double) entryDepthCount);
            out.put("avgMaxDepth", maxDepthCount == 0 ? 0.0 : maxDepthTotal / (double) maxDepthCount);
            out.put("avgTraversalConfidencePct", graphVisits == 0 ? 0.0 : confidenceTotal / graphVisits);
            out.put("directionCounts", intMap(directions));

            JSONObject strategyJson = new JSONObject();
            boolean allRequestedCovered = true;
            String[] requested = {"CHAIN", "NETWORK", "DEDUCTION", "HYPOTHESIS", "MIXED"};
            for (Map.Entry<String, StrategyCoverage> entry : byStrategy.entrySet()) {
                strategyJson.put(entry.getKey(), entry.getValue().toJson());
            }
            for (String strategy : requested) {
                StrategyCoverage coverage = byStrategy.get(strategy);
                if (coverage == null || coverage.graphVisits == 0) allRequestedCovered = false;
            }
            out.put("byStrategy", strategyJson);
            out.put("allRequestedStrategiesHaveGraphEvidence", allRequestedCovered);
            out.put("readyForFirstCrossStrategyReview", allRequestedCovered && graphVisits >= 10);
            out.put("note", "These are evidence counters only. Final invariant labels are intentionally not assigned automatically.");
        } catch (Exception ignored) { }
        return out;
    }

    static JSONObject graphTraversal(JSONObject row) {
        if (row == null) return null;
        JSONObject route = row.optJSONObject("routeComparison");
        JSONObject nested = route == null ? null : route.optJSONObject("graphTraversal");
        if (nested != null) return nested;
        return row.optJSONObject("graphTraversal");
    }

    private static int countCandidateEdits(JSONArray events) {
        if (events == null) return 0;
        int count = 0;
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null) continue;
            String type = event.optString("type", "");
            if ("candidate_add".equals(type) || "candidate_remove".equals(type)) count++;
        }
        return count;
    }

    private static int countType(JSONArray events, String wanted) {
        if (events == null) return 0;
        int count = 0;
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event != null && wanted.equals(event.optString("type", ""))) count++;
        }
        return count;
    }

    private static String rowOutcome(JSONObject row) {
        String explicit = row.optString("runOutcome", "");
        if (!explicit.isEmpty()) return explicit;
        return RunLifecycle.outcome(row.optBoolean("solved", false), row.optString("finishReason", "unknown"));
    }

    private static void copyString(JSONObject from, JSONObject to, String key) throws Exception {
        if (from.has(key)) to.put(key, from.optString(key, ""));
    }

    private static void copyInt(JSONObject from, JSONObject to, String key) throws Exception {
        if (from.has(key)) to.put(key, from.optInt(key, 0));
    }

    private static void copyLong(JSONObject from, JSONObject to, String key) throws Exception {
        if (from.has(key)) to.put(key, from.optLong(key, 0L));
    }

    private static JSONObject intMap(Map<String, Integer> values) throws Exception {
        JSONObject out = new JSONObject();
        for (Map.Entry<String, Integer> entry : values.entrySet()) out.put(entry.getKey(), entry.getValue());
        return out;
    }

    private static final class StrategyCoverage {
        int visits;
        int graphVisits;
        int candidateVisits;
        int propagationVisits;
        int branchVisits;
        int coherentGraphVisits;
        int alternateTraversalVisits;
        int reviewSuggestedVisits;

        JSONObject toJson() throws Exception {
            JSONObject out = new JSONObject();
            out.put("visits", visits);
            out.put("graphVisits", graphVisits);
            out.put("candidateVisits", candidateVisits);
            out.put("propagationVisits", propagationVisits);
            out.put("branchVisits", branchVisits);
            out.put("coherentGraphVisits", coherentGraphVisits);
            out.put("alternateTraversalVisits", alternateTraversalVisits);
            out.put("reviewSuggestedVisits", reviewSuggestedVisits);
            return out;
        }
    }
}
