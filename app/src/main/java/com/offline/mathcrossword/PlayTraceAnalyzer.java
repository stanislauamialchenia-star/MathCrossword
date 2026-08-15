package com.offline.mathcrossword;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Conservative behavioral signals derived from the local event trace.
 * These are descriptions of interaction inside the puzzle, not psychological labels.
 */
final class PlayTraceAnalyzer {
    private PlayTraceAnalyzer() { }

    static final class Stats {
        int productivePauses;
        int deadEndPauses;
        int hypothesisEpisodes;
        int candidateCommitments;
        long candidateCommitmentTotalMs;
        int recoveryEpisodes;
        int recoveryActionTotal;
        int rapidCascades;
    }

    static Stats analyze(JSONArray events) {
        Stats out = new Stats();
        if (events == null || events.length() == 0) return out;

        // Candidate notation -> actual placement in the same cell.
        Map<String, Long> firstCandidateAt = new HashMap<>();
        int lastBranchStart = -1;
        long lastBranchStartT = -1L;
        boolean branchHadExploration = false;
        int recoveryStart = -1;
        int rapidMeaningfulRun = 0;
        long prevMeaningfulT = -1L;
        long prevAnalysisT = -1L;

        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            String type = e.optString("type", "");
            long t = e.optLong("tMs", -1L);

            boolean meaningful = isMeaningful(type);
            boolean analysisRelevant = meaningful || "hint".equals(type) || "undo".equals(type)
                    || "reset".equals(type) || "full_incorrect".equals(type);
            // UI-only actions (drawer resize, focus mode, candidate-mode toggle, etc.) must not
            // split a thinking pause. The raw trace keeps them; behavioral analysis ignores them.
            if (analysisRelevant && prevAnalysisT >= 0 && t >= prevAnalysisT && t - prevAnalysisT >= 3000L) {
                int nextSignal = classifyAfterPause(events, i);
                if (nextSignal > 0) out.productivePauses++;
                else if (nextSignal < 0) out.deadEndPauses++;
            }
            if (analysisRelevant && t >= 0) prevAnalysisT = t;
            if (meaningful && prevMeaningfulT >= 0 && t >= prevMeaningfulT && t - prevMeaningfulT <= 900L) {
                rapidMeaningfulRun++;
                if (rapidMeaningfulRun == 3) out.rapidCascades++;
            } else if (meaningful) {
                rapidMeaningfulRun = 1;
            }
            if (meaningful && t >= 0) prevMeaningfulT = t;

            String cell = cellKey(e);
            if ("candidate_add".equals(type) && cell != null) {
                firstCandidateAt.putIfAbsent(cell, t);
            } else if ("candidate_remove".equals(type) && cell != null) {
                // Keep the timestamp: removing one candidate is often part of the same deliberation.
            } else if ("place".equals(type) && cell != null) {
                Long started = firstCandidateAt.remove(cell);
                if (started != null && t >= started) {
                    out.candidateCommitments++;
                    out.candidateCommitmentTotalMs += t - started;
                }
                // A placement starts a tentative branch. It only becomes a hypothesis signal if
                // subsequent exploration occurs and the player later backs out/overwrites.
                lastBranchStart = i;
                lastBranchStartT = t;
                branchHadExploration = false;
                if ("replace".equals(e.optString("detail", ""))) {
                    out.hypothesisEpisodes++;
                    recoveryStart = i;
                }
            } else if (meaningful && lastBranchStart >= 0 && i > lastBranchStart) {
                if ("candidate_add".equals(type) || "select_cell".equals(type) || "place".equals(type)) {
                    branchHadExploration = true;
                }
            }

            if ("undo".equals(type) || "remove".equals(type) || "full_incorrect".equals(type)) {
                if (lastBranchStart >= 0 && (branchHadExploration
                        || (t >= 0 && lastBranchStartT >= 0 && t - lastBranchStartT >= 2000L))) {
                    out.hypothesisEpisodes++;
                }
                lastBranchStart = -1;
                lastBranchStartT = -1L;
                branchHadExploration = false;
                recoveryStart = i;
            } else if ("reset".equals(type) || "hint".equals(type)) {
                recoveryStart = i;
            } else if (recoveryStart >= 0 && "place".equals(type)) {
                int actions = countMeaningful(events, recoveryStart + 1, i + 1);
                if (actions >= 2) {
                    out.recoveryEpisodes++;
                    out.recoveryActionTotal += actions;
                    recoveryStart = -1;
                }
            }
        }
        return out;
    }

    private static int classifyAfterPause(JSONArray events, int start) {
        int positive = 0;
        int checked = 0;
        long startT = -1L;
        JSONObject first = events.optJSONObject(start);
        if (first != null) startT = first.optLong("tMs", -1L);
        for (int i = start; i < events.length() && checked < 5; i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            String type = e.optString("type", "");
            if (!isMeaningful(type) && !"hint".equals(type) && !"undo".equals(type)
                    && !"reset".equals(type) && !"full_incorrect".equals(type)) continue;
            checked++;
            long t = e.optLong("tMs", -1L);
            if (startT >= 0 && t >= 0 && t - startT > 15000L) break;
            if ("undo".equals(type) || "reset".equals(type) || "hint".equals(type)
                    || "full_incorrect".equals(type)) return -1;
            if ("place".equals(type) || "candidate_add".equals(type) || "candidate_remove".equals(type)) {
                positive++;
                if (positive >= 2) return 1;
            }
        }
        return 0;
    }

    private static boolean isMeaningful(String type) {
        return "place".equals(type) || "remove".equals(type) || "candidate_add".equals(type)
                || "candidate_remove".equals(type) || "select_cell".equals(type);
    }

    private static String cellKey(JSONObject e) {
        if (e == null || !e.has("x") || !e.has("y")) return null;
        return e.optInt("x") + ":" + e.optInt("y");
    }

    private static int countMeaningful(JSONArray events, int from, int to) {
        int n = 0;
        for (int i = Math.max(0, from); i < Math.min(events.length(), to); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e != null && isMeaningful(e.optString("type", ""))) n++;
        }
        return n;
    }
}
