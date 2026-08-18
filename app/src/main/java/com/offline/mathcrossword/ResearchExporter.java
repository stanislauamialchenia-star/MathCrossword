package com.offline.mathcrossword;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Manual, user-initiated export of local play telemetry.
 *
 * Nothing is uploaded here. The user explicitly chooses a destination document,
 * then Android can offer the resulting ZIP through the normal share sheet.
 */
final class ResearchExporter {
    static final int EXPORT_SCHEMA_VERSION = 2;
    private static final String PREFS = "research_export";
    private static final String PARTICIPANT_ID = "participant_id";

    private ResearchExporter() { }

    static final class Result {
        final int sessions; // backward-compatible raw stored visit rows
        final int runs;
        final int solved;
        final String participantId;

        Result(int sessions, int runs, int solved, String participantId) {
            this.sessions = sessions;
            this.runs = runs;
            this.solved = solved;
            this.participantId = participantId;
        }
    }

    static String suggestedFileName() {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        return "MathCrossword-research-" + stamp + ".zip";
    }

    static Result write(Context context, OutputStream output) throws Exception {
        if (context == null) throw new IllegalArgumentException("context == null");
        if (output == null) throw new IllegalArgumentException("output == null");

        List<String> lines = readHistory(context);
        List<JSONObject> rows = new ArrayList<>();
        for (String line : lines) {
            try { rows.add(new JSONObject(line)); }
            catch (Exception ignored) { }
        }

        String participantId = participantId(context);
        JSONObject metadata = metadata(context, participantId, lines.size(), rows.size());
        JSONObject summary = summary(rows);

        // The one-argument constructor exists since Android API 1. Entry names are ASCII,
        // while file contents are explicitly encoded as UTF-8 below. This keeps minSdk 23 safe.
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            writeEntry(zip, "metadata.json", metadata.toString(2) + "\n");

            StringBuilder sessions = new StringBuilder();
            for (String line : lines) sessions.append(line).append('\n');
            writeEntry(zip, "sessions.jsonl", sessions.toString());

            writeEntry(zip, "summary.json", summary.toString(2) + "\n");
            zip.finish();
        }

        return new Result(rows.size(), summary.optInt("puzzleRuns", 0),
                summary.optInt("solvedRuns", 0), participantId);
    }

    private static JSONObject metadata(Context context, String participantId,
                                       int rawLines, int parsedSessions) throws Exception {
        JSONObject out = new JSONObject();
        out.put("exportSchemaVersion", EXPORT_SCHEMA_VERSION);
        out.put("generatedAtEpochMs", System.currentTimeMillis());
        out.put("participantId", participantId);
        out.put("participantIdScope", "random-installation-id");
        out.put("manualUserInitiatedExport", true);
        out.put("automaticUpload", false);
        out.put("sourceStorage", "app-internal-play_history.jsonl");
        out.put("rawSessionLines", rawLines); // legacy name retained for compatibility
        out.put("parsedSessions", parsedSessions); // legacy name: each row is now treated as a Visit row
        out.put("parsedVisitRows", parsedSessions);
        out.put("generatorVersion", PuzzleGenerator.GENERATOR_VERSION);
        out.put("humanRouteModelVersion", HumanRouteComparator.VERSION);
        out.put("moveNotationVersion", MoveNotation.VERSION);

        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        out.put("applicationId", context.getPackageName());
        out.put("appVersionName", info.versionName == null ? "" : info.versionName);
        long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? info.getLongVersionCode() : info.versionCode;
        out.put("appVersionCode", versionCode);

        JSONObject privacy = new JSONObject();
        privacy.put("accountIdentifierCollected", false);
        privacy.put("advertisingIdCollected", false);
        privacy.put("locationCollected", false);
        privacy.put("contactsCollected", false);
        privacy.put("networkRequiredForPlay", false);
        privacy.put("note", "The archive contains local puzzle/visit telemetry, timestamps, random visit/run identifiers and a random installation participant ID. It is created only after an explicit export action.");
        out.put("privacy", privacy);
        return out;
    }

    private static JSONObject summary(List<JSONObject> rows) throws Exception {
        JSONObject out = new JSONObject();
        int legacySolvedVisitRows = 0;
        int routeCompared = 0;
        int graphSessions = 0;
        double routeAgreement = 0.0;
        long cycleRank = 0L;
        long bridges = 0L;
        long articulations = 0L;
        long hiddenArticulations = 0L;
        long branchNodes = 0L;
        long diameter = 0L;

        Map<String, Integer> strategies = new LinkedHashMap<>();
        Map<String, Integer> generatorVersions = new LinkedHashMap<>();
        Map<String, String> runOutcomes = new LinkedHashMap<>();
        Map<String, Long> runActiveMs = new LinkedHashMap<>();
        Map<String, String> runStrategies = new LinkedHashMap<>();
        int legacyIndex = 0;

        for (JSONObject row : rows) {
            if (row.optBoolean("solved", false)) legacySolvedVisitRows++;

            String runId = row.optString("runId", "");
            if (runId.isEmpty()) {
                String sessionId = row.optString("sessionId", "");
                runId = sessionId.isEmpty() ? ("legacy-run-" + legacyIndex++) : ("legacy-session-" + sessionId);
            }
            String outcome = rowOutcome(row);
            runOutcomes.put(runId, RunLifecycle.mergeOutcome(runOutcomes.get(runId), outcome));
            runActiveMs.put(runId, runActiveMs.getOrDefault(runId, 0L) + Math.max(0L, row.optLong("activeMs", 0L)));

            String strategy = row.optString("strategy", row.optString("style", "UNKNOWN"));
            strategies.put(strategy, strategies.getOrDefault(strategy, 0) + 1);
            if (!runStrategies.containsKey(runId)) runStrategies.put(runId, strategy);

            String generator = Integer.toString(row.optInt("generatorVersion", 0));
            generatorVersions.put(generator, generatorVersions.getOrDefault(generator, 0) + 1);

            JSONObject route = row.optJSONObject("routeComparison");
            if (route != null && route.optBoolean("available", false)) {
                routeCompared++;
                routeAgreement += route.optDouble("agreementPct", 0.0);
            }

            if (row.has("graphNodes")) {
                graphSessions++;
                cycleRank += row.optInt("graphCycleRank", 0);
                bridges += row.optInt("graphBridges", 0);
                articulations += row.optInt("graphArticulationPoints", 0);
                hiddenArticulations += row.optInt("graphHiddenArticulations", 0);
                branchNodes += row.optInt("graphBranchNodes", 0);
                diameter += row.optInt("graphDiameter", 0);
            }
        }

        int solvedRuns = 0;
        int inProgressRuns = 0;
        int giveUpRuns = 0;
        int restartedRuns = 0;
        int skippedRuns = 0;
        int abandonedRuns = 0;
        long solvedRunActiveMs = 0L;
        Map<String, Integer> strategyRuns = new LinkedHashMap<>();
        Map<String, Integer> outcomeCounts = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : runOutcomes.entrySet()) {
            String runId = entry.getKey();
            String outcome = entry.getValue();
            outcomeCounts.put(outcome, outcomeCounts.getOrDefault(outcome, 0) + 1);
            String strategy = runStrategies.getOrDefault(runId, "UNKNOWN");
            strategyRuns.put(strategy, strategyRuns.getOrDefault(strategy, 0) + 1);
            if (RunLifecycle.SOLVED.equals(outcome)) {
                solvedRuns++;
                solvedRunActiveMs += runActiveMs.getOrDefault(runId, 0L);
            } else if (RunLifecycle.IN_PROGRESS.equals(outcome)) inProgressRuns++;
            else if (RunLifecycle.GIVE_UP.equals(outcome)) giveUpRuns++;
            else if (RunLifecycle.RESTARTED.equals(outcome)) restartedRuns++;
            else if (RunLifecycle.SKIPPED.equals(outcome)) skippedRuns++;
            else if (RunLifecycle.ABANDONED.equals(outcome)) abandonedRuns++;
        }

        int explicitDifficultyOutcomes = solvedRuns + giveUpRuns;
        out.put("visits", rows.size());
        out.put("puzzleRuns", runOutcomes.size());
        out.put("solvedRuns", solvedRuns);
        out.put("inProgressRuns", inProgressRuns);
        out.put("giveUpRuns", giveUpRuns);
        out.put("restartedRuns", restartedRuns);
        out.put("skippedRuns", skippedRuns);
        out.put("abandonedRuns", abandonedRuns);
        out.put("runOutcomeCounts", mapJson(outcomeCounts));
        out.put("explicitDifficultyOutcomes", explicitDifficultyOutcomes);
        out.put("solveRateBasis", "SOLVED_or_GIVE_UP_only");
        out.put("solveRatePct", explicitDifficultyOutcomes == 0 ? 0.0
                : solvedRuns * 100.0 / explicitDifficultyOutcomes);
        out.put("avgSolvedRunActiveMs", solvedRuns == 0 ? 0L : solvedRunActiveMs / solvedRuns);

        // Legacy visit-row fields remain available for old analysis notebooks, but are
        // explicitly named so unfinished navigation is not mistaken for a loss.
        out.put("sessions", rows.size());
        out.put("legacySolvedVisitRows", legacySolvedVisitRows);
        out.put("legacyUnsolvedVisitRows", Math.max(0, rows.size() - legacySolvedVisitRows));
        out.put("legacyVisitSolveRatePct", rows.isEmpty() ? 0.0 : legacySolvedVisitRows * 100.0 / rows.size());

        out.put("routeComparedVisits", routeCompared);
        out.put("avgRouteAgreementPct", routeCompared == 0 ? 0.0 : routeAgreement / routeCompared);
        out.put("strategyVisitCounts", mapJson(strategies));
        out.put("strategyRunCounts", mapJson(strategyRuns));
        out.put("generatorVersionVisitCounts", mapJson(generatorVersions));

        JSONObject graph = new JSONObject();
        graph.put("visitsWithGraphMetrics", graphSessions);
        graph.put("avgCycleRank", avg(cycleRank, graphSessions));
        graph.put("avgBridges", avg(bridges, graphSessions));
        graph.put("avgArticulationPoints", avg(articulations, graphSessions));
        graph.put("avgHiddenArticulations", avg(hiddenArticulations, graphSessions));
        graph.put("avgBranchNodes", avg(branchNodes, graphSessions));
        graph.put("avgDiameter", avg(diameter, graphSessions));
        out.put("graph", graph);
        return out;
    }

    private static String rowOutcome(JSONObject row) {
        String explicit = row.optString("runOutcome", "");
        if (!explicit.isEmpty()) return explicit;
        return RunLifecycle.outcome(row.optBoolean("solved", false), row.optString("finishReason", "unknown"));
    }

    private static double avg(long total, int count) {
        return count == 0 ? 0.0 : total / (double) count;
    }

    private static JSONObject mapJson(Map<String, Integer> values) throws Exception {
        JSONObject out = new JSONObject();
        for (Map.Entry<String, Integer> e : values.entrySet()) out.put(e.getKey(), e.getValue());
        return out;
    }

    private static String participantId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String current = prefs.getString(PARTICIPANT_ID, null);
        if (current != null && !current.trim().isEmpty()) return current;
        String created = UUID.randomUUID().toString();
        prefs.edit().putString(PARTICIPANT_ID, created).apply();
        return created;
    }

    private static List<String> readHistory(Context context) {
        File file = new File(context.getFilesDir(), "play_history.jsonl");
        List<String> out = new ArrayList<>();
        if (!file.exists()) return out;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) out.add(line);
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static void writeEntry(ZipOutputStream zip, String name, String content) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        zip.write(bytes);
        zip.closeEntry();
    }
}
