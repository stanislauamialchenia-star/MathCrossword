package com.offline.mathcrossword;

import android.content.Context;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Local-only play telemetry. No network, account, advertising ID, or analytics SDK.
 * Stores the last 500 sessions as JSONL in app-internal storage.
 */
final class SessionTracker {
    private static final int MAX_SESSIONS = 500;
    private final File historyFile;
    private OpenSession open;

    SessionTracker(Context context) {
        historyFile = new File(context.getFilesDir(), "play_history.jsonl");
    }

    synchronized boolean hasOpenSession() {
        return open != null;
    }

    synchronized void start(String mode, int level, long seed, int logic, int calc,
                            double logicScore, double calcScore,
                            String strategy, int hidden, int equations,
                            int ratedLogic, int predictedSteps, int predictedDepth,
                            int basicForced, int basicRemaining, int maxForcedCascade,
                            int maxResolvedAfterOneCell, double maxResolvedFractionAfterOneCell,
                            int vulnerableSingleCells, int maxResolvedAfterOneEquation, double maxResolvedFractionAfterOneEquation,
                            int generatorVersion, int generationStage, boolean strategyTargetMatched,
                            String generationStrategy, String generatorConstructor, String generatorFamily,
                            int deceptiveDecoyCount, int deceptiveDecoySupportMax,
                            int contextualDecoyCount, int resourceConflictDecoyCount, int contextualDecoyConstraintSupportMax,
                            int contextualDecoyDepthMax, int contextualDecoyInformationGainMax,
                            int branchPivotCount, int branchGoodPivotCount, int branchSeriousFalseBranches,
                            int branchDepth2RefutableBranches, int branchDepth2SurvivingBranches,
                            int branchMaxWidth, int branchMaxInformationGain,
                            int reasoningFronts, double reasoningFrontBalance, double reasoningLargestFrontFraction,
                            int reasoningFrontBottleneckDegree,
                            boolean contradictionKernel, boolean contradictionKernelAddedDecoy, int contradictionKernelDepth,
                            String contradictionKernelFamily, int contradictionKernelBranches, int contradictionKernelPivots,
                            int contradictionKernelDepth2Branches, int contradictionKernelDepth3Branches, int contradictionKernelDeepBranches,
                            int contradictionKernelMaxRemaining,
                            String generationStageTimings, long generationMillis, int generationAttempts, int generationRejects,
                            String generationRejectSummary) {
        if (open != null) finish(false, "replaced");
        open = new OpenSession(mode, level, seed, logic, calc, logicScore, calcScore, strategy, hidden, equations,
                ratedLogic, predictedSteps, predictedDepth, basicForced, basicRemaining, maxForcedCascade,
                maxResolvedAfterOneCell, maxResolvedFractionAfterOneCell, vulnerableSingleCells,
                maxResolvedAfterOneEquation, maxResolvedFractionAfterOneEquation,
                generatorVersion, generationStage, strategyTargetMatched, generationStrategy,
                generatorConstructor, generatorFamily, deceptiveDecoyCount, deceptiveDecoySupportMax,
                contextualDecoyCount, resourceConflictDecoyCount, contextualDecoyConstraintSupportMax, contextualDecoyDepthMax, contextualDecoyInformationGainMax,
                branchPivotCount, branchGoodPivotCount, branchSeriousFalseBranches,
                branchDepth2RefutableBranches, branchDepth2SurvivingBranches, branchMaxWidth, branchMaxInformationGain,
                reasoningFronts, reasoningFrontBalance, reasoningLargestFrontFraction, reasoningFrontBottleneckDegree,
                contradictionKernel, contradictionKernelAddedDecoy, contradictionKernelDepth,
                contradictionKernelFamily, contradictionKernelBranches, contradictionKernelPivots,
                contradictionKernelDepth2Branches, contradictionKernelDepth3Branches, contradictionKernelDeepBranches,
                contradictionKernelMaxRemaining,
                generationStageTimings, generationMillis, generationAttempts, generationRejects,
                generationRejectSummary);
    }

    synchronized void resume() {
        if (open != null) open.resume();
    }

    synchronized void pause() {
        if (open != null) open.pause();
    }

    synchronized void event(String type, int x, int y, int value, String detail) {
        if (open != null) open.event(type, x, y, value, detail);
    }

    synchronized void finish(boolean solved, String reason) {
        if (open == null) return;
        JSONObject json = open.finish(solved, reason);
        open = null;
        append(json);
    }

    synchronized AnalysisSnapshot analyze() {
        List<JSONObject> rows = readObjects();
        AnalysisSnapshot out = new AnalysisSnapshot();
        out.sessions = rows.size();
        long solvedTime = 0L;
        int solvedCountForTime = 0;
        long totalEvents = 0L;
        long totalFirstAction = 0L;
        int firstActionCount = 0;
        long totalLongestPause = 0L;
        int pauseCount = 0;

        Map<String, StrategyStats> strategyMap = new LinkedHashMap<>();
        List<DifficultyCalibrator.Sample> calibrationSamples = new ArrayList<>();
        long calibrationOrder = 0L;

        for (JSONObject row : rows) {
            String strategyName = row.optString("strategy", row.optString("style", SolutionStrategy.MIXED.name()));
            StrategyStats strategyStats = strategyMap.get(strategyName);
            if (strategyStats == null) {
                strategyStats = new StrategyStats();
                strategyStats.strategy = strategyName;
                strategyMap.put(strategyName, strategyStats);
            }
            strategyStats.sessions++;

            DifficultyCalibrator.Sample calibration = new DifficultyCalibrator.Sample();
            calibration.order = calibrationOrder++;
            calibration.strategy = strategyName;
            calibration.mode = row.optString("mode", "");
            calibration.generatorVersion = row.optInt("generatorVersion", 0);
            calibration.solved = row.optBoolean("solved", false);
            calibration.hidden = Math.max(1, row.optInt("hidden", 1));
            calibration.activeMs = row.optLong("activeMs", 0L);
            calibration.eventCount = row.optInt("eventCount", 0);
            calibration.predictedBand = row.optInt("ratedLogic", row.optInt("logic", 1));
            if (calibration.predictedBand <= 0) calibration.predictedBand = Math.max(1, row.optInt("logic", 1));
            calibration.deadEndPauses = row.optInt("deadEndPauses", 0);
            calibration.fullIncorrectCount = row.optInt("fullIncorrectCount", 0);

            if (!row.optBoolean("strategyTargetMatched", true)) out.strategyFallbacks++;
            if (row.optInt("generationStage", 1) >= 2) out.expandedGenerations++;
            boolean solved = row.optBoolean("solved", false);
            if (solved) {
                out.solved++;
                solvedTime += row.optLong("activeMs", 0L);
                solvedCountForTime++;
                strategyStats.solved++;
                strategyStats.totalSolvedMs += row.optLong("activeMs", 0L);
            }
            int eventCount = row.optInt("eventCount", 0);
            totalEvents += eventCount;
            strategyStats.events += eventCount;
            long first = row.optLong("firstActionMs", -1L);
            if (first >= 0) { totalFirstAction += first; firstActionCount++; }
            long longestPause = row.optLong("longestPauseBetweenActionsMs", 0L);
            if (longestPause > 0) { totalLongestPause += longestPause; pauseCount++; }
            int productive = row.optInt("productivePauses", 0);
            int deadEnd = row.optInt("deadEndPauses", 0);
            int hypothesisSignals = row.optInt("hypothesisEpisodes", 0);
            int rapidCascades = row.optInt("rapidCascades", 0);
            out.productivePauses += productive;
            out.deadEndPauses += deadEnd;
            out.hypothesisEpisodes += hypothesisSignals;
            out.rapidCascades += rapidCascades;
            strategyStats.productivePauses += productive;
            strategyStats.deadEndPauses += deadEnd;
            strategyStats.hypothesisEpisodes += hypothesisSignals;
            if (row.optBoolean("contradictionKernel", false)) {
                out.kernelSessions++;
                if (row.optInt("contradictionKernelDeepBranches", 0) > 0
                        || row.optInt("contradictionKernelDepth", 0) >= 4) out.deepKernelSessions++;
            }
            JSONArray events = row.optJSONArray("events");
            if (events != null) {
                for (int i = 0; i < events.length(); i++) {
                    JSONObject e = events.optJSONObject(i);
                    if (e == null) continue;
                    String type = e.optString("type", "");
                    if ("place".equals(type)) { out.placements++; strategyStats.placements++; }
                    else if ("undo".equals(type)) { out.undoCount++; strategyStats.undoCount++; calibration.undoCount++; }
                    else if ("candidate_add".equals(type) || "candidate_remove".equals(type)) { out.candidateEdits++; strategyStats.candidateEdits++; calibration.candidateEdits++; }
                    else if ("reset".equals(type)) { out.resetCount++; strategyStats.resetCount++; }
                    else if ("hint".equals(type)) { out.hintCount++; strategyStats.hintCount++; calibration.hintCount++; }
                }
            }
            calibrationSamples.add(calibration);
        }

        DifficultyCalibrator.Result calibrationResult = DifficultyCalibrator.calibrate(calibrationSamples);
        out.calibrationReady = calibrationResult.ready;
        out.calibrationSessions = calibrationResult.usableSessions;
        out.calibrationMeanError = calibrationResult.meanError;
        out.calibrationMeanAbsError = calibrationResult.meanAbsError;
        out.calibrationWithinOnePct = calibrationResult.withinOnePct();
        out.calibrationExactPct = calibrationResult.exactPct();
        out.calibrationUnderestimated = calibrationResult.underestimated;
        out.calibrationOverestimated = calibrationResult.overestimated;
        out.lastPredictedBand = calibrationResult.lastPredictedBand;
        out.lastObservedBand = calibrationResult.lastObservedBand;
        out.recentObservedCostChangePct = calibrationResult.recentCostChangePct;
        out.calibrationGeneratorVersion = calibrationResult.scopeGeneratorVersion;

        out.avgSolvedMs = solvedCountForTime == 0 ? 0L : solvedTime / solvedCountForTime;
        out.avgEvents = rows.isEmpty() ? 0.0 : totalEvents / (double) rows.size();
        out.avgFirstActionMs = firstActionCount == 0 ? 0L : totalFirstAction / firstActionCount;
        out.avgLongestPauseMs = pauseCount == 0 ? 0L : totalLongestPause / pauseCount;

        for (SolutionStrategy strategy : SolutionStrategy.values()) {
            StrategyStats st = strategyMap.get(strategy.name());
            if (st == null) continue;
            st.avgSolvedMs = st.solved == 0 ? 0L : st.totalSolvedMs / st.solved;
            st.avgEvents = st.sessions == 0 ? 0.0 : st.events / (double) st.sessions;
            out.byStrategy.add(st);
        }
        for (Map.Entry<String, StrategyStats> e : strategyMap.entrySet()) {
            boolean known = false;
            for (StrategyStats st : out.byStrategy) if (st.strategy.equals(e.getKey())) { known = true; break; }
            if (!known) {
                StrategyStats st = e.getValue();
                st.avgSolvedMs = st.solved == 0 ? 0L : st.totalSolvedMs / st.solved;
                st.avgEvents = st.sessions == 0 ? 0.0 : st.events / (double) st.sessions;
                out.byStrategy.add(st);
            }
        }

        int from = Math.max(0, rows.size() - 12);
        for (int i = rows.size() - 1; i >= from; i--) {
            JSONObject row = rows.get(i);
            SessionSummary s = new SessionSummary();
            s.mode = row.optString("mode", "");
            s.level = row.optInt("level", 0);
            s.strategy = row.optString("strategy", row.optString("style", SolutionStrategy.MIXED.name()));
            s.logic = row.optInt("logic", 0);
            s.calc = row.optInt("calc", 0);
            s.solved = row.optBoolean("solved", false);
            s.activeMs = row.optLong("activeMs", 0L);
            s.eventCount = row.optInt("eventCount", 0);
            s.ratedLogic = row.optInt("ratedLogic", 0);
            s.predictedSteps = row.optInt("predictedSteps", 0);
            s.hidden = row.optInt("hidden", 0);
            s.maxForcedCascade = row.optInt("maxForcedCascade", 0);
            s.maxResolvedFractionAfterOneCell = row.optDouble("maxResolvedFractionAfterOneCell", 0.0);
            s.rapidCascades = row.optInt("rapidCascades", 0);
            s.productivePauses = row.optInt("productivePauses", 0);
            s.deadEndPauses = row.optInt("deadEndPauses", 0);
            s.hypothesisEpisodes = row.optInt("hypothesisEpisodes", 0);
            s.hintStage = row.optInt("maxHintStage", 0);
            s.kernelFamily = row.optString("contradictionKernelFamily", "none");
            s.contextualDecoys = row.optInt("contextualDecoyCount", 0);
            s.resourceConflictDecoys = row.optInt("resourceConflictDecoyCount", 0);
            s.branchGoodPivots = row.optInt("branchGoodPivotCount", 0);
            s.branchFalseBranches = row.optInt("branchSeriousFalseBranches", 0);
            s.reasoningFronts = row.optInt("reasoningFronts", 0);
            s.reasoningFrontBalance = row.optDouble("reasoningFrontBalance", 0.0);
            out.recent.add(s);
        }
        return out;
    }

    private void append(JSONObject json) {
        if (json == null) return;
        List<String> lines = readLines();
        lines.add(json.toString());
        if (lines.size() > MAX_SESSIONS) {
            lines = new ArrayList<>(lines.subList(lines.size() - MAX_SESSIONS, lines.size()));
        }
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(historyFile, false), StandardCharsets.UTF_8))) {
            for (String line : lines) {
                bw.write(line);
                bw.newLine();
            }
        } catch (Exception ignored) { }
    }

    private List<String> readLines() {
        if (!historyFile.exists()) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(historyFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) out.add(line);
            }
        } catch (Exception ignored) { }
        return out;
    }

    private List<JSONObject> readObjects() {
        List<JSONObject> out = new ArrayList<>();
        for (String line : readLines()) {
            try { out.add(new JSONObject(line)); }
            catch (Exception ignored) { }
        }
        return out;
    }

    static final class AnalysisSnapshot {
        int sessions;
        int solved;
        long avgSolvedMs;
        double avgEvents;
        int placements;
        int undoCount;
        int candidateEdits;
        int resetCount;
        int hintCount;
        int strategyFallbacks;
        int expandedGenerations;
        long avgFirstActionMs;
        long avgLongestPauseMs;
        int productivePauses;
        int deadEndPauses;
        int hypothesisEpisodes;
        int rapidCascades;
        int kernelSessions;
        int deepKernelSessions;
        boolean calibrationReady;
        int calibrationSessions;
        double calibrationMeanError;
        double calibrationMeanAbsError;
        double calibrationWithinOnePct;
        double calibrationExactPct;
        int calibrationUnderestimated;
        int calibrationOverestimated;
        int lastPredictedBand;
        int lastObservedBand;
        double recentObservedCostChangePct;
        int calibrationGeneratorVersion;
        final List<StrategyStats> byStrategy = new ArrayList<>();
        final List<SessionSummary> recent = new ArrayList<>();
    }

    static final class StrategyStats {
        String strategy;
        int sessions;
        int solved;
        long totalSolvedMs;
        long avgSolvedMs;
        long events;
        double avgEvents;
        int placements;
        int undoCount;
        int candidateEdits;
        int resetCount;
        int hintCount;
        int productivePauses;
        int deadEndPauses;
        int hypothesisEpisodes;
    }

    static final class SessionSummary {
        String mode;
        int level;
        String strategy;
        int logic;
        int calc;
        boolean solved;
        long activeMs;
        int eventCount;
        int ratedLogic;
        int predictedSteps;
        int hidden;
        int maxForcedCascade;
        double maxResolvedFractionAfterOneCell;
        int rapidCascades;
        int productivePauses;
        int deadEndPauses;
        int hypothesisEpisodes;
        int hintStage;
        String kernelFamily;
        int contextualDecoys;
        int resourceConflictDecoys;
        int branchGoodPivots;
        int branchFalseBranches;
        int reasoningFronts;
        double reasoningFrontBalance;
    }

    private static final class OpenSession {
        final String id = UUID.randomUUID().toString();
        final long startedAtEpochMs = System.currentTimeMillis();
        final String mode;
        final int level;
        final long seed;
        final int logic;
        final int calc;
        final double logicScore;
        final double calcScore;
        final String strategy;
        final int hidden;
        final int equations;
        final int ratedLogic;
        final int predictedSteps;
        final int predictedDepth;
        final int basicForced;
        final int basicRemaining;
        final int maxForcedCascade;
        final int maxResolvedAfterOneCell;
        final double maxResolvedFractionAfterOneCell;
        final int vulnerableSingleCells;
        final int maxResolvedAfterOneEquation;
        final double maxResolvedFractionAfterOneEquation;
        final int generatorVersion;
        final int generationStage;
        final boolean strategyTargetMatched;
        final String generationStrategy;
        final String generatorConstructor;
        final String generatorFamily;
        final int deceptiveDecoyCount;
        final int deceptiveDecoySupportMax;
        final int contextualDecoyCount;
        final int resourceConflictDecoyCount;
        final int contextualDecoyConstraintSupportMax;
        final int contextualDecoyDepthMax;
        final int contextualDecoyInformationGainMax;
        final int branchPivotCount;
        final int branchGoodPivotCount;
        final int branchSeriousFalseBranches;
        final int branchDepth2RefutableBranches;
        final int branchDepth2SurvivingBranches;
        final int branchMaxWidth;
        final int branchMaxInformationGain;
        final int reasoningFronts;
        final double reasoningFrontBalance;
        final double reasoningLargestFrontFraction;
        final int reasoningFrontBottleneckDegree;
        final boolean contradictionKernel;
        final boolean contradictionKernelAddedDecoy;
        final int contradictionKernelDepth;
        final String contradictionKernelFamily;
        final int contradictionKernelBranches;
        final int contradictionKernelPivots;
        final int contradictionKernelDepth2Branches;
        final int contradictionKernelDepth3Branches;
        final int contradictionKernelDeepBranches;
        final int contradictionKernelMaxRemaining;
        final String generationStageTimings;
        final long generationMillis;
        final int generationAttempts;
        final int generationRejects;
        final String generationRejectSummary;
        final JSONArray events = new JSONArray();

        long activeAccumulatedMs = 0L;
        long activeSegmentStart = 0L;
        long firstActionMs = -1L;
        long longestPauseBetweenActionsMs = 0L;
        long previousActionMs = -1L;
        boolean active = false;

        OpenSession(String mode, int level, long seed, int logic, int calc,
                    double logicScore, double calcScore,
                    String strategy, int hidden, int equations,
                    int ratedLogic, int predictedSteps, int predictedDepth,
                    int basicForced, int basicRemaining, int maxForcedCascade,
                    int maxResolvedAfterOneCell, double maxResolvedFractionAfterOneCell,
                    int vulnerableSingleCells, int maxResolvedAfterOneEquation, double maxResolvedFractionAfterOneEquation,
                    int generatorVersion,
                    int generationStage, boolean strategyTargetMatched, String generationStrategy,
                    String generatorConstructor, String generatorFamily,
                    int deceptiveDecoyCount, int deceptiveDecoySupportMax,
                    int contextualDecoyCount, int resourceConflictDecoyCount, int contextualDecoyConstraintSupportMax,
                    int contextualDecoyDepthMax, int contextualDecoyInformationGainMax,
                    int branchPivotCount, int branchGoodPivotCount, int branchSeriousFalseBranches,
                    int branchDepth2RefutableBranches, int branchDepth2SurvivingBranches,
                    int branchMaxWidth, int branchMaxInformationGain,
                    int reasoningFronts, double reasoningFrontBalance, double reasoningLargestFrontFraction,
                    int reasoningFrontBottleneckDegree,
                    boolean contradictionKernel, boolean contradictionKernelAddedDecoy, int contradictionKernelDepth,
                    String contradictionKernelFamily, int contradictionKernelBranches, int contradictionKernelPivots,
                    int contradictionKernelDepth2Branches, int contradictionKernelDepth3Branches, int contradictionKernelDeepBranches,
                    int contradictionKernelMaxRemaining,
                    String generationStageTimings, long generationMillis, int generationAttempts,
                    int generationRejects, String generationRejectSummary) {
            this.mode = mode;
            this.level = level;
            this.seed = seed;
            this.logic = logic;
            this.calc = calc;
            this.logicScore = logicScore;
            this.calcScore = calcScore;
            this.strategy = strategy == null ? SolutionStrategy.MIXED.name() : strategy;
            this.hidden = hidden;
            this.equations = equations;
            this.ratedLogic = ratedLogic;
            this.predictedSteps = predictedSteps;
            this.predictedDepth = predictedDepth;
            this.basicForced = basicForced;
            this.basicRemaining = basicRemaining;
            this.maxForcedCascade = maxForcedCascade;
            this.maxResolvedAfterOneCell = maxResolvedAfterOneCell;
            this.maxResolvedFractionAfterOneCell = maxResolvedFractionAfterOneCell;
            this.vulnerableSingleCells = vulnerableSingleCells;
            this.maxResolvedAfterOneEquation = maxResolvedAfterOneEquation;
            this.maxResolvedFractionAfterOneEquation = maxResolvedFractionAfterOneEquation;
            this.generatorVersion = generatorVersion;
            this.generationStage = generationStage;
            this.strategyTargetMatched = strategyTargetMatched;
            this.generationStrategy = generationStrategy == null ? this.strategy : generationStrategy;
            this.generatorConstructor = generatorConstructor == null ? "" : generatorConstructor;
            this.generatorFamily = generatorFamily == null ? "" : generatorFamily;
            this.deceptiveDecoyCount = deceptiveDecoyCount;
            this.deceptiveDecoySupportMax = deceptiveDecoySupportMax;
            this.contextualDecoyCount = contextualDecoyCount;
            this.resourceConflictDecoyCount = resourceConflictDecoyCount;
            this.contextualDecoyConstraintSupportMax = contextualDecoyConstraintSupportMax;
            this.contextualDecoyDepthMax = contextualDecoyDepthMax;
            this.contextualDecoyInformationGainMax = contextualDecoyInformationGainMax;
            this.branchPivotCount = branchPivotCount;
            this.branchGoodPivotCount = branchGoodPivotCount;
            this.branchSeriousFalseBranches = branchSeriousFalseBranches;
            this.branchDepth2RefutableBranches = branchDepth2RefutableBranches;
            this.branchDepth2SurvivingBranches = branchDepth2SurvivingBranches;
            this.branchMaxWidth = branchMaxWidth;
            this.branchMaxInformationGain = branchMaxInformationGain;
            this.reasoningFronts = reasoningFronts;
            this.reasoningFrontBalance = reasoningFrontBalance;
            this.reasoningLargestFrontFraction = reasoningLargestFrontFraction;
            this.reasoningFrontBottleneckDegree = reasoningFrontBottleneckDegree;
            this.contradictionKernel = contradictionKernel;
            this.contradictionKernelAddedDecoy = contradictionKernelAddedDecoy;
            this.contradictionKernelDepth = contradictionKernelDepth;
            this.contradictionKernelFamily = contradictionKernelFamily == null ? "none" : contradictionKernelFamily;
            this.contradictionKernelBranches = contradictionKernelBranches;
            this.contradictionKernelPivots = contradictionKernelPivots;
            this.contradictionKernelDepth2Branches = contradictionKernelDepth2Branches;
            this.contradictionKernelDepth3Branches = contradictionKernelDepth3Branches;
            this.contradictionKernelDeepBranches = contradictionKernelDeepBranches;
            this.contradictionKernelMaxRemaining = contradictionKernelMaxRemaining;
            this.generationStageTimings = generationStageTimings == null ? "" : generationStageTimings;
            this.generationMillis = generationMillis;
            this.generationAttempts = generationAttempts;
            this.generationRejects = generationRejects;
            this.generationRejectSummary = generationRejectSummary == null ? "" : generationRejectSummary;
            resume();
        }

        void resume() {
            if (active) return;
            active = true;
            activeSegmentStart = SystemClock.elapsedRealtime();
        }

        void pause() {
            if (!active) return;
            activeAccumulatedMs += Math.max(0L, SystemClock.elapsedRealtime() - activeSegmentStart);
            active = false;
        }

        long activeMs() {
            if (!active) return activeAccumulatedMs;
            return activeAccumulatedMs + Math.max(0L, SystemClock.elapsedRealtime() - activeSegmentStart);
        }

        void event(String type, int x, int y, int value, String detail) {
            long t = activeMs();
            if (firstActionMs < 0) firstActionMs = t;
            if (previousActionMs >= 0) longestPauseBetweenActionsMs = Math.max(longestPauseBetweenActionsMs, t - previousActionMs);
            previousActionMs = t;
            JSONObject e = new JSONObject();
            try {
                e.put("tMs", t);
                e.put("type", type);
                if (x >= 0) e.put("x", x);
                if (y >= 0) e.put("y", y);
                if (value != 0) e.put("value", value);
                if (detail != null) e.put("detail", detail);
            } catch (Exception ignored) { }
            events.put(e);
        }

        JSONObject finish(boolean solved, String reason) {
            pause();
            JSONObject root = new JSONObject();
            try {
                root.put("sessionId", id);
                root.put("startedAtEpochMs", startedAtEpochMs);
                root.put("finishedAtEpochMs", System.currentTimeMillis());
                root.put("mode", mode);
                root.put("level", level);
                root.put("seed", seed);
                root.put("logic", logic);
                root.put("calc", calc);
                root.put("logicScore", logicScore);
                root.put("calcScore", calcScore);
                root.put("strategy", strategy);
                root.put("hidden", hidden);
                root.put("equations", equations);
                root.put("ratedLogic", ratedLogic);
                root.put("predictedSteps", predictedSteps);
                root.put("predictedDepth", predictedDepth);
                root.put("basicForced", basicForced);
                root.put("basicRemaining", basicRemaining);
                root.put("maxForcedCascade", maxForcedCascade);
                root.put("maxResolvedAfterOneCell", maxResolvedAfterOneCell);
                root.put("maxResolvedFractionAfterOneCell", maxResolvedFractionAfterOneCell);
                root.put("vulnerableSingleCells", vulnerableSingleCells);
                root.put("maxResolvedAfterOneEquation", maxResolvedAfterOneEquation);
                root.put("maxResolvedFractionAfterOneEquation", maxResolvedFractionAfterOneEquation);
                root.put("generatorVersion", generatorVersion);
                root.put("generationStage", generationStage);
                root.put("strategyTargetMatched", strategyTargetMatched);
                root.put("generationStrategy", generationStrategy);
                root.put("generatorConstructor", generatorConstructor);
                root.put("generatorFamily", generatorFamily);
                root.put("deceptiveDecoyCount", deceptiveDecoyCount);
                root.put("deceptiveDecoySupportMax", deceptiveDecoySupportMax);
                root.put("contextualDecoyCount", contextualDecoyCount);
                root.put("resourceConflictDecoyCount", resourceConflictDecoyCount);
                root.put("contextualDecoyConstraintSupportMax", contextualDecoyConstraintSupportMax);
                root.put("contextualDecoyDepthMax", contextualDecoyDepthMax);
                root.put("contextualDecoyInformationGainMax", contextualDecoyInformationGainMax);
                root.put("branchPivotCount", branchPivotCount);
                root.put("branchGoodPivotCount", branchGoodPivotCount);
                root.put("branchSeriousFalseBranches", branchSeriousFalseBranches);
                root.put("branchDepth2RefutableBranches", branchDepth2RefutableBranches);
                root.put("branchDepth2SurvivingBranches", branchDepth2SurvivingBranches);
                root.put("branchMaxWidth", branchMaxWidth);
                root.put("branchMaxInformationGain", branchMaxInformationGain);
                root.put("reasoningFronts", reasoningFronts);
                root.put("reasoningFrontBalance", reasoningFrontBalance);
                root.put("reasoningLargestFrontFraction", reasoningLargestFrontFraction);
                root.put("reasoningFrontBottleneckDegree", reasoningFrontBottleneckDegree);
                root.put("contradictionKernel", contradictionKernel);
                root.put("contradictionKernelAddedDecoy", contradictionKernelAddedDecoy);
                root.put("contradictionKernelDepth", contradictionKernelDepth);
                root.put("contradictionKernelFamily", contradictionKernelFamily);
                root.put("contradictionKernelBranches", contradictionKernelBranches);
                root.put("contradictionKernelPivots", contradictionKernelPivots);
                root.put("contradictionKernelDepth2Branches", contradictionKernelDepth2Branches);
                root.put("contradictionKernelDepth3Branches", contradictionKernelDepth3Branches);
                root.put("contradictionKernelDeepBranches", contradictionKernelDeepBranches);
                root.put("contradictionKernelMaxRemaining", contradictionKernelMaxRemaining);
                root.put("generationStageTimings", generationStageTimings);
                root.put("generationMillis", generationMillis);
                root.put("generationAttempts", generationAttempts);
                root.put("generationRejects", generationRejects);
                root.put("generationRejectSummary", generationRejectSummary);
                root.put("solved", solved);
                root.put("finishReason", reason == null ? "unknown" : reason);
                root.put("activeMs", activeAccumulatedMs);
                root.put("firstActionMs", firstActionMs);
                root.put("longestPauseBetweenActionsMs", longestPauseBetweenActionsMs);
                root.put("eventCount", events.length());
                appendDerivedEventStats(root);
                root.put("events", events);
            } catch (Exception ignored) { }
            return root;
        }

        void appendDerivedEventStats(JSONObject root) {
            long firstPlacementMs = -1L;
            int longPauses3s = 0;
            int longPauses10s = 0;
            int rapidTransitionsUnder1s = 0;
            int replacements = 0;
            int fullIncorrectCount = 0;
            int maxHintStage = 0;
            java.util.HashSet<String> placedCells = new java.util.HashSet<>();
            java.util.HashSet<String> candidateCells = new java.util.HashSet<>();
            long prevT = -1L;
            for (int i = 0; i < events.length(); i++) {
                JSONObject e = events.optJSONObject(i);
                if (e == null) continue;
                long t = e.optLong("tMs", -1L);
                if (prevT >= 0 && t >= prevT) {
                    long gap = t - prevT;
                    if (gap >= 3000L) longPauses3s++;
                    if (gap >= 10000L) longPauses10s++;
                    if (gap <= 1000L) rapidTransitionsUnder1s++;
                }
                if (t >= 0) prevT = t;
                String type = e.optString("type", "");
                if ("place".equals(type)) {
                    if (firstPlacementMs < 0) firstPlacementMs = t;
                    if (e.has("x") && e.has("y")) placedCells.add(e.optInt("x") + ":" + e.optInt("y"));
                    if ("replace".equals(e.optString("detail", ""))) replacements++;
                } else if ("candidate_add".equals(type) || "candidate_remove".equals(type)) {
                    if (e.has("x") && e.has("y")) candidateCells.add(e.optInt("x") + ":" + e.optInt("y"));
                } else if ("full_incorrect".equals(type)) {
                    fullIncorrectCount++;
                } else if ("hint".equals(type)) {
                    maxHintStage = Math.max(maxHintStage, e.optInt("value", 0));
                }
            }
            try {
                root.put("firstPlacementMs", firstPlacementMs);
                root.put("pausesOver3s", longPauses3s);
                root.put("pausesOver10s", longPauses10s);
                root.put("rapidTransitionsUnder1s", rapidTransitionsUnder1s);
                root.put("replacementCount", replacements);
                root.put("fullIncorrectCount", fullIncorrectCount);
                root.put("maxHintStage", maxHintStage);
                root.put("distinctPlacedCells", placedCells.size());
                root.put("distinctCandidateCells", candidateCells.size());
                PlayTraceAnalyzer.Stats trace = PlayTraceAnalyzer.analyze(events);
                root.put("productivePauses", trace.productivePauses);
                root.put("deadEndPauses", trace.deadEndPauses);
                root.put("hypothesisEpisodes", trace.hypothesisEpisodes);
                root.put("candidateCommitments", trace.candidateCommitments);
                root.put("avgCandidateCommitmentMs", trace.candidateCommitments == 0 ? 0L
                        : trace.candidateCommitmentTotalMs / trace.candidateCommitments);
                root.put("recoveryEpisodes", trace.recoveryEpisodes);
                root.put("avgRecoveryActions", trace.recoveryEpisodes == 0 ? 0.0
                        : trace.recoveryActionTotal / (double) trace.recoveryEpisodes);
                root.put("rapidCascades", trace.rapidCascades);
            } catch (Exception ignored) { }
        }
    }
}
