package com.offline.mathcrossword;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Compact, chess-like semantic transcript derived from the full local event trace.
 * Raw events remain the source of truth; this class only creates a convenient view.
 */
final class MoveNotation {
    private MoveNotation() {}

    static final int VERSION = 1;

    static JSONArray semanticMoves(JSONArray events) {
        JSONArray out = new JSONArray();
        if (events == null) return out;
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            String type = e.optString("type", "");
            if (!isSemantic(type)) continue;
            JSONObject m = new JSONObject();
            try {
                m.put("seq", e.optInt("seq", i + 1));
                m.put("tMs", e.optLong("tMs", 0L));
                m.put("type", type);
                if (e.has("x")) m.put("x", e.optInt("x"));
                if (e.has("y")) m.put("y", e.optInt("y"));
                if (e.has("value")) m.put("value", e.optInt("value"));
                if (e.has("detail")) m.put("detail", e.optString("detail", ""));
                m.put("notation", notation(e));
            } catch (Exception ignored) { }
            out.put(m);
        }
        return out;
    }

    static JSONArray candidateTrail(JSONArray events) {
        JSONArray out = new JSONArray();
        if (events == null) return out;
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            String type = e.optString("type", "");
            if (!"candidate_add".equals(type) && !"candidate_remove".equals(type)) continue;
            JSONObject c = new JSONObject();
            try {
                c.put("seq", e.optInt("seq", i + 1));
                c.put("tMs", e.optLong("tMs", 0L));
                c.put("op", "candidate_add".equals(type) ? "+" : "-");
                c.put("x", e.optInt("x"));
                c.put("y", e.optInt("y"));
                c.put("value", e.optInt("value"));
            } catch (Exception ignored) { }
            out.put(c);
        }
        return out;
    }


    static JSONArray focusTrail(JSONArray events) {
        JSONArray out = new JSONArray();
        if (events == null) return out;
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null || !"select_cell".equals(e.optString("type", ""))) continue;
            JSONObject f = new JSONObject();
            try {
                f.put("seq", e.optInt("seq", i + 1));
                f.put("tMs", e.optLong("tMs", 0L));
                f.put("x", e.optInt("x"));
                f.put("y", e.optInt("y"));
            } catch (Exception ignored) { }
            out.put(f);
        }
        return out;
    }

    static Summary summarizeCandidates(JSONArray events) {
        Summary s = new Summary();
        if (events == null) return s;
        Map<String, Set<Integer>> active = new HashMap<>();
        Set<String> seenCells = new HashSet<>();
        Set<Integer> seenValues = new HashSet<>();
        String previousCandidateCell = null;
        Set<String> leftCells = new HashSet<>();

        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            String type = e.optString("type", "");
            if (!"candidate_add".equals(type) && !"candidate_remove".equals(type)) continue;
            s.events++;
            String cell = e.optInt("x") + ":" + e.optInt("y");
            int value = e.optInt("value");
            seenCells.add(cell);
            seenValues.add(value);
            if (previousCandidateCell != null && !previousCandidateCell.equals(cell)) {
                s.cellSwitches++;
                leftCells.add(previousCandidateCell);
                if (leftCells.contains(cell)) s.cellRevisits++;
            }
            previousCandidateCell = cell;

            Set<Integer> set = active.get(cell);
            if (set == null) { set = new HashSet<>(); active.put(cell, set); }
            if ("candidate_add".equals(type)) set.add(value); else set.remove(value);
            s.maxCandidatesInOneCell = Math.max(s.maxCandidatesInOneCell, set.size());
        }
        s.distinctCells = seenCells.size();
        s.distinctValues = seenValues.size();
        return s;
    }

    private static boolean isSemantic(String type) {
        return "candidate_add".equals(type) || "candidate_remove".equals(type)
                || "place".equals(type) || "remove".equals(type)
                || "undo".equals(type) || "hint".equals(type)
                || "reset".equals(type) || "full_incorrect".equals(type);
    }

    private static String notation(JSONObject e) {
        String type = e.optString("type", "");
        String cell = e.has("x") && e.has("y") ? "[" + e.optInt("x") + "," + e.optInt("y") + "]" : "";
        int value = e.optInt("value", 0);
        if ("candidate_add".equals(type)) return cell + " ?+" + value;
        if ("candidate_remove".equals(type)) return cell + " ?-" + value;
        if ("place".equals(type)) return cell + " =" + value;
        if ("remove".equals(type)) return cell + " ×" + value;
        if ("undo".equals(type)) return "↶";
        if ("hint".equals(type)) return "?" + Math.max(1, value) + (cell.isEmpty() ? "" : " " + cell);
        if ("reset".equals(type)) return "↻";
        if ("full_incorrect".equals(type)) return "! full";
        return type;
    }

    static final class Summary {
        int events;
        int distinctCells;
        int distinctValues;
        int cellSwitches;
        int cellRevisits;
        int maxCandidatesInOneCell;
    }
}
