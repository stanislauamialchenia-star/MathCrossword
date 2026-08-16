package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compares one deterministic HumanSolver route with the order of meaningful cells
 * actually touched by the player. It does not claim to reconstruct thought: the
 * output is only a model-vs-interaction comparison used to find blind spots.
 */
final class HumanRouteComparator {
    static final int VERSION = 1;
    private static final int MAX_MODEL_STEPS = 48;

    private HumanRouteComparator() { }

    static JSONArray modelRoute(Puzzle p) {
        JSONArray out = new JSONArray();
        if (p == null || p.hidden.isEmpty()) return out;

        HumanSolver.State state = HumanSolver.initialState(p);
        HumanSolver.Metrics scratch = new HumanSolver.Metrics();
        int guard = 0;

        while (state.assigned.size() < p.hidden.size() && guard++ < MAX_MODEL_STEPS) {
            Map<Pos, Set<Integer>> domains = HumanSolver.allDomains(p, state);
            if (domains.isEmpty()) break;
            boolean impossible = false;
            for (Set<Integer> d : domains.values()) if (d.isEmpty()) { impossible = true; break; }
            if (impossible) break;

            List<Map.Entry<Pos, Set<Integer>>> singles = new ArrayList<>();
            for (Map.Entry<Pos, Set<Integer>> e : domains.entrySet()) {
                if (e.getValue().size() == 1) singles.add(e);
            }
            singles.sort((a, b) -> {
                int c = Integer.compare(a.getKey().y, b.getKey().y);
                return c != 0 ? c : Integer.compare(a.getKey().x, b.getKey().x);
            });

            if (!singles.isEmpty()) {
                JSONArray cells = new JSONArray();
                boolean ok = true;
                for (Map.Entry<Pos, Set<Integer>> e : singles) {
                    int value = e.getValue().iterator().next();
                    cells.put(cell(e.getKey(), value));
                    if (!HumanSolver.assign(state, e.getKey(), value)) { ok = false; break; }
                }
                putStep(out, "forced-wave", 0, cells, state.assigned.size());
                if (!ok || !HumanSolver.allLocallyPossible(p, state)) break;
                continue;
            }

            HumanSolver.Deduction d = null;
            for (int depth = 1; depth <= 2 && d == null; depth++) {
                d = HumanSolver.findContradictionDeduction(p, state, domains, depth, scratch);
            }
            if (d == null) break;
            JSONArray cells = new JSONArray();
            cells.put(cell(d.pos, d.value));
            putStep(out, "probe", d.depth, cells, state.assigned.size() + 1);
            if (!HumanSolver.assign(state, d.pos, d.value) || !HumanSolver.allLocallyPossible(p, state)) break;
        }
        return out;
    }

    static JSONObject compare(JSONArray modelRoute, JSONArray events) {
        JSONObject out = new JSONObject();
        try {
            out.put("version", VERSION);
            int modelSteps = modelRoute == null ? 0 : modelRoute.length();
            JSONArray actual = actualDecisionRoute(events);
            out.put("modelSteps", modelSteps);
            out.put("actualCells", actual.length());
            out.put("actualDecisionRoute", actual);

            Map<String, Integer> stepByCell = new LinkedHashMap<>();
            List<String> probeCells = new ArrayList<>();
            if (modelRoute != null) {
                for (int i = 0; i < modelRoute.length(); i++) {
                    JSONObject step = modelRoute.optJSONObject(i);
                    if (step == null) continue;
                    boolean probe = "probe".equals(step.optString("kind", ""));
                    JSONArray cells = step.optJSONArray("cells");
                    if (cells == null) continue;
                    for (int j = 0; j < cells.length(); j++) {
                        JSONObject c = cells.optJSONObject(j);
                        if (c == null) continue;
                        String key = key(c.optInt("x"), c.optInt("y"));
                        if (!stepByCell.containsKey(key)) stepByCell.put(key, i);
                        if (probe && !probeCells.contains(key)) probeCells.add(key);
                    }
                }
            }

            boolean available = modelSteps > 0 && actual.length() > 0 && !stepByCell.isEmpty();
            out.put("available", available);
            out.put("modelDistinctCells", stepByCell.size());
            out.put("modelProbeCells", probeCells.size());
            if (!available) return out;

            int earlyN = Math.min(5, actual.length());
            int earlyLimitStep = Math.min(2, modelSteps - 1);
            int earlyHits = 0;
            int mapped = 0;
            int firstStep = -1;
            int previousStep = -1;
            int comparableTransitions = 0;
            int orderedTransitions = 0;
            int backwardTransitions = 0;
            int maxJumpAhead = 0;
            Map<String, Integer> actualIndex = new HashMap<>();

            for (int i = 0; i < actual.length(); i++) {
                JSONObject a = actual.optJSONObject(i);
                if (a == null) continue;
                String k = key(a.optInt("x"), a.optInt("y"));
                actualIndex.put(k, i);
                Integer step = stepByCell.get(k);
                if (step == null) continue;
                a.put("modelStep", step);
                mapped++;
                if (firstStep < 0) firstStep = step;
                if (i < earlyN && step <= earlyLimitStep) earlyHits++;
                if (i < earlyN) maxJumpAhead = Math.max(maxJumpAhead, step);
                if (previousStep >= 0) {
                    comparableTransitions++;
                    if (step >= previousStep) orderedTransitions++;
                    else backwardTransitions++;
                }
                previousStep = step;
            }

            double earlyPct = earlyN == 0 ? 0.0 : 100.0 * earlyHits / earlyN;
            double orderPct = comparableTransitions == 0 ? 100.0 : 100.0 * orderedTransitions / comparableTransitions;
            int probeEarly = 0;
            int probeSeen = 0;
            int earlyCutoff = Math.max(1, (int)Math.ceil(actual.length() * 0.60));
            for (String probe : probeCells) {
                Integer idx = actualIndex.get(probe);
                if (idx == null) continue;
                probeSeen++;
                if (idx < earlyCutoff) probeEarly++;
            }
            double probeEarlyPct = probeCells.isEmpty() ? -1.0 : 100.0 * probeEarly / probeCells.size();
            double mappedPct = actual.length() == 0 ? 0.0 : 100.0 * mapped / actual.length();

            double agreement;
            if (probeCells.isEmpty()) agreement = 0.48 * earlyPct + 0.52 * orderPct;
            else agreement = 0.38 * earlyPct + 0.42 * orderPct + 0.20 * Math.max(0.0, probeEarlyPct);
            agreement = Math.max(0.0, Math.min(100.0, agreement));

            out.put("mappedActualPct", mappedPct);
            out.put("firstModelStep", firstStep);
            out.put("earlyAgreementPct", earlyPct);
            out.put("orderAgreementPct", orderPct);
            out.put("probeReachedEarlyPct", probeEarlyPct);
            out.put("probeSeen", probeSeen);
            out.put("backwardTransitions", backwardTransitions);
            out.put("maxEarlyStepJump", maxJumpAhead);
            out.put("agreementPct", agreement);
            out.put("divergencePct", 100.0 - agreement);
            out.put("alternateEntry", firstStep < 0 || firstStep > 2);
            out.put("alternateOrder", comparableTransitions >= 2 && orderPct < 65.0);
            out.put("strongDivergence", actual.length() >= 3 && agreement < 55.0);
        } catch (Exception ignored) { }
        return out;
    }

    static String describeModel(JSONArray route, int maxSteps) {
        if (route == null || route.length() == 0) return "—";
        StringBuilder s = new StringBuilder();
        int limit = Math.min(Math.max(1, maxSteps), route.length());
        for (int i = 0; i < limit; i++) {
            JSONObject step = route.optJSONObject(i);
            if (step == null) continue;
            if (s.length() > 0) s.append(" → ");
            String kind = step.optString("kind", "");
            int depth = step.optInt("depth", 0);
            s.append("probe".equals(kind) ? ("H" + depth) : "F");
            JSONArray cells = step.optJSONArray("cells");
            s.append("{");
            if (cells != null) {
                int cellLimit = Math.min(4, cells.length());
                for (int j = 0; j < cellLimit; j++) {
                    if (j > 0) s.append(",");
                    JSONObject c = cells.optJSONObject(j);
                    if (c != null) s.append("[").append(c.optInt("x")).append(",").append(c.optInt("y")).append("]");
                }
                if (cells.length() > cellLimit) s.append("…+").append(cells.length() - cellLimit);
            }
            s.append("}");
        }
        if (route.length() > limit) s.append(" → …+").append(route.length() - limit);
        return s.toString();
    }

    static String describeActual(JSONObject comparison, int maxCells) {
        if (comparison == null) return "—";
        JSONArray route = comparison.optJSONArray("actualDecisionRoute");
        if (route == null || route.length() == 0) return "—";
        StringBuilder s = new StringBuilder();
        int limit = Math.min(Math.max(1, maxCells), route.length());
        for (int i = 0; i < limit; i++) {
            JSONObject c = route.optJSONObject(i);
            if (c == null) continue;
            if (s.length() > 0) s.append(" → ");
            s.append("[").append(c.optInt("x")).append(",").append(c.optInt("y")).append("]");
        }
        if (route.length() > limit) s.append(" → …+").append(route.length() - limit);
        return s.toString();
    }

    private static JSONArray actualDecisionRoute(JSONArray events) {
        JSONArray out = new JSONArray();
        if (events == null) return out;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            String type = e.optString("type", "");
            if (!"candidate_add".equals(type) && !"candidate_remove".equals(type)
                    && !"place".equals(type) && !"remove".equals(type)) continue;
            if (!e.has("x") || !e.has("y")) continue;
            int x = e.optInt("x"), y = e.optInt("y");
            String key = key(x, y);
            if (!seen.add(key)) continue;
            JSONObject a = new JSONObject();
            try {
                a.put("x", x);
                a.put("y", y);
                a.put("tMs", e.optLong("tMs", 0L));
                a.put("firstType", type);
            } catch (Exception ignored) { }
            out.put(a);
        }
        return out;
    }

    private static JSONObject cell(Pos pos, int value) {
        JSONObject c = new JSONObject();
        try {
            c.put("x", pos.x);
            c.put("y", pos.y);
            c.put("value", value);
        } catch (Exception ignored) { }
        return c;
    }

    private static void putStep(JSONArray out, String kind, int depth, JSONArray cells, int assignedAfter) {
        JSONObject step = new JSONObject();
        try {
            step.put("index", out.length());
            step.put("kind", kind);
            step.put("depth", depth);
            step.put("cells", cells);
            step.put("assignedAfter", assignedAfter);
        } catch (Exception ignored) { }
        out.put(step);
    }

    private static String key(int x, int y) { return x + ":" + y; }
}
