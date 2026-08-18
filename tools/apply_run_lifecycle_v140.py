#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SESSION = ROOT / "app/src/main/java/com/offline/mathcrossword/SessionTracker.java"
MAIN = ROOT / "app/src/main/java/com/offline/mathcrossword/MainActivity.java"
GRADLE = ROOT / "app/build.gradle"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


s = SESSION.read_text(encoding="utf-8")

s = replace_once(s,
'''        AnalysisSnapshot out = new AnalysisSnapshot();
        out.sessions = rows.size();
        long solvedTime = 0L;''',
'''        AnalysisSnapshot out = new AnalysisSnapshot();
        out.sessions = rows.size(); // backward-compatible raw stored-row count
        out.visits = rows.size();
        Map<String, String> runOutcomes = new LinkedHashMap<>();
        int legacyRunIndex = 0;
        for (JSONObject row : rows) {
            String runId = row.optString("runId", "");
            if (runId.isEmpty()) {
                String sessionId = row.optString("sessionId", "");
                runId = sessionId.isEmpty() ? ("legacy-run-" + legacyRunIndex++) : ("legacy-session-" + sessionId);
            }
            String outcome = outcomeForRow(row);
            runOutcomes.put(runId, RunLifecycle.mergeOutcome(runOutcomes.get(runId), outcome));
        }
        out.runs = runOutcomes.size();
        for (String outcome : runOutcomes.values()) {
            if (RunLifecycle.SOLVED.equals(outcome)) out.solvedRuns++;
            else if (RunLifecycle.IN_PROGRESS.equals(outcome)) out.inProgressRuns++;
            else if (RunLifecycle.GIVE_UP.equals(outcome)) out.giveUpRuns++;
            else if (RunLifecycle.RESTARTED.equals(outcome)) out.restartedRuns++;
            else if (RunLifecycle.SKIPPED.equals(outcome)) out.skippedRuns++;
            else if (RunLifecycle.ABANDONED.equals(outcome)) out.abandonedRuns++;
        }
        out.solved = out.solvedRuns; // old field now reflects solved PuzzleRuns, not unfinished visits
        long solvedTime = 0L;''',
"analysis lifecycle index")

s = replace_once(s,
'''            strategyStats.sessions++;

            DifficultyCalibrator.Sample calibration = new DifficultyCalibrator.Sample();''',
'''            strategyStats.sessions++;
            String runOutcome = outcomeForRow(row);

            DifficultyCalibrator.Sample calibration = new DifficultyCalibrator.Sample();''',
"analysis row outcome")

s = replace_once(s,
'''            calibration.solved = row.optBoolean("solved", false);''',
'''            calibration.solved = RunLifecycle.isSolved(runOutcome);''',
"calibration solved semantics")

s = replace_once(s,
'''            boolean solved = row.optBoolean("solved", false);
            if (solved) {
                out.solved++;''',
'''            boolean solved = RunLifecycle.isSolved(runOutcome);
            if (solved) {''',
"solved count semantics")

s = replace_once(s,
'''            calibrationSamples.add(calibration);''',
'''            if (RunLifecycle.isExplicitDifficultyOutcome(runOutcome)) calibrationSamples.add(calibration);''',
"calibration sample filter")

s = replace_once(s,
'''            s.solved = row.optBoolean("solved", false);
            s.activeMs = row.optLong("activeMs", 0L);''',
'''            s.outcome = outcomeForRow(row);
            s.solved = RunLifecycle.isSolved(s.outcome);
            s.activeMs = row.optLong("activeMs", 0L);''',
"recent outcome")

s = replace_once(s,
'''    private static String reportTime(long ms) {''',
'''    private static String outcomeForRow(JSONObject row) {
        String explicit = row.optString("runOutcome", "");
        if (!explicit.isEmpty()) return explicit;
        return RunLifecycle.outcome(row.optBoolean("solved", false), row.optString("finishReason", "unknown"));
    }

    private static String reportTime(long ms) {''',
"legacy outcome reader")

s = replace_once(s,
'''    static final class AnalysisSnapshot {
        int sessions;
        int solved;''',
'''    static final class AnalysisSnapshot {
        int sessions;
        int visits;
        int runs;
        int solved;
        int solvedRuns;
        int inProgressRuns;
        int giveUpRuns;
        int restartedRuns;
        int skippedRuns;
        int abandonedRuns;''',
"analysis snapshot fields")

s = replace_once(s,
'''        boolean solved;
        long activeMs;''',
'''        boolean solved;
        String outcome;
        long activeMs;''',
"session summary outcome field")

s = replace_once(s,
'''    private static final class OpenSession {
        final String id = UUID.randomUUID().toString();
        final long startedAtEpochMs = System.currentTimeMillis();''',
'''    private static final class OpenSession {
        final String id = UUID.randomUUID().toString(); // legacy sessionId / stored visit row id
        final String runId = UUID.randomUUID().toString();
        final String visitId = UUID.randomUUID().toString();
        final long startedAtEpochMs = System.currentTimeMillis();
        final String puzzleId;''',
"open session identity")

s = replace_once(s,
'''        final JSONArray events = new JSONArray();
        JSONArray modelRoute = new JSONArray();''',
'''        final JSONArray events = new JSONArray();
        final JSONArray lifecycleEvents = new JSONArray();
        JSONArray modelRoute = new JSONArray();''',
"lifecycle event array")

s = replace_once(s,
'''            this.generatorVersion = generatorVersion;
            this.generationStage = generationStage;''',
'''            this.generatorVersion = generatorVersion;
            this.puzzleId = RunLifecycle.puzzleId(mode, level, seed, generatorVersion);
            this.generationStage = generationStage;''',
"puzzle identity assignment")

s = replace_once(s,
'''            this.graphAverageDegree = graph == null ? 0.0 : graph.averageDegree;
            resume();
        }

        void setModelRoute(JSONArray route) {''',
'''            this.graphAverageDegree = graph == null ? 0.0 : graph.averageDegree;
            lifecycleEvent("PUZZLE_OPENED", null);
            resume();
        }

        void setModelRoute(JSONArray route) {''',
"puzzle opened lifecycle")

s = replace_once(s,
'''        void resume() {
            if (active) return;
            active = true;
            activeSegmentStart = SystemClock.elapsedRealtime();
        }

        void pause() {
            if (!active) return;
            activeAccumulatedMs += Math.max(0L, SystemClock.elapsedRealtime() - activeSegmentStart);
            active = false;
        }

        long activeMs() {''',
'''        void resume() {
            if (active) return;
            active = true;
            activeSegmentStart = SystemClock.elapsedRealtime();
            lifecycleEvent("APP_FOREGROUND", null);
        }

        void pause() {
            if (!active) return;
            lifecycleEvent("APP_BACKGROUND", null);
            stopActiveSegment();
        }

        void stopActiveSegment() {
            if (!active) return;
            activeAccumulatedMs += Math.max(0L, SystemClock.elapsedRealtime() - activeSegmentStart);
            active = false;
        }

        void lifecycleEvent(String type, String detail) {
            JSONObject e = new JSONObject();
            try {
                e.put("seq", lifecycleEvents.length() + 1);
                e.put("tMs", activeMs());
                e.put("type", type);
                if (detail != null) e.put("detail", detail);
            } catch (Exception ignored) { }
            lifecycleEvents.put(e);
        }

        long activeMs() {''',
"foreground background lifecycle")

s = replace_once(s,
'''        JSONObject finish(boolean solved, String reason) {
            pause();
            JSONObject root = new JSONObject();
            try {
                root.put("sessionId", id);''',
'''        JSONObject finish(boolean solved, String reason) {
            String runOutcome = RunLifecycle.outcome(solved, reason);
            lifecycleEvent(RunLifecycle.lifecycleEvent(solved, reason), reason);
            stopActiveSegment();
            JSONObject root = new JSONObject();
            try {
                root.put("telemetrySchemaVersion", 2);
                root.put("sessionId", id);
                root.put("visitId", visitId);
                root.put("runId", runId);
                root.put("puzzleId", puzzleId);
                root.put("visitIndex", 1);
                root.put("visitOutcome", RunLifecycle.visitOutcome(solved, reason));
                root.put("runOutcome", runOutcome);''',
"finish lifecycle identity")

s = replace_once(s,
'''                root.put("events", events);''',
'''                root.put("lifecycleEvents", lifecycleEvents);
                root.put("events", events);''',
"write lifecycle events")

SESSION.write_text(s, encoding="utf-8")

m = MAIN.read_text(encoding="utf-8")
m = replace_once(m,
'''            c.drawText(UiText.tr("Sessions: ", "Сессий: ", "Relací: ") + a.sessions + UiText.tr("   Solved: ", "   Решено: ", "   Vyřešeno: ") + a.solved, side, y, paint); y += dp(24);''',
'''            c.drawText(UiText.tr("Visits: ", "Посещений: ", "Návštěv: ") + a.visits + UiText.tr("   Runs: ", "   Прохождений: ", "   Průchodů: ") + a.runs, side, y, paint); y += dp(24);
            c.drawText(UiText.tr("Solved runs: ", "Решено прохождений: ", "Vyřešených průchodů: ") + a.solvedRuns + UiText.tr("   In progress: ", "   В процессе: ", "   Probíhá: ") + a.inProgressRuns, side, y, paint); y += dp(24);''',
"analysis UI visits/runs")

m = replace_once(m,
'''                return info.versionName == null ? "1.38" : info.versionName;
            } catch (android.content.pm.PackageManager.NameNotFoundException ex) {
                return "1.38";''',
'''                return info.versionName == null ? "1.40" : info.versionName;
            } catch (android.content.pm.PackageManager.NameNotFoundException ex) {
                return "1.40";''',
"version name fallback")

m = replace_once(m,
'''                return 38L;''',
'''                return 40L;''',
"version code fallback")
MAIN.write_text(m, encoding="utf-8")

g = GRADLE.read_text(encoding="utf-8")
g = replace_once(g,
'''        versionCode 39
        versionName '1.39' ''',
'''        versionCode 40
        versionName '1.40' ''',
"version bump") if "versionCode 39\n        versionName '1.39' " in g else g
if "versionCode 39\n        versionName '1.39'" in g:
    g = g.replace("versionCode 39\n        versionName '1.39'", "versionCode 40\n        versionName '1.40'", 1)
elif "versionCode 40\n        versionName '1.40'" not in g:
    raise SystemExit("version bump: expected 1.39")
GRADLE.write_text(g, encoding="utf-8")

print("Applied PuzzleRun/Visit lifecycle migration for v1.40")
