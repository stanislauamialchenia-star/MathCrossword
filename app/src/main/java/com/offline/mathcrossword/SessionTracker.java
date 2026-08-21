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
    // One in-memory resumable run is enough for the current Home -> Continue path.
    // Process-death persistence is intentionally a later stage.
    private String resumableRunId;
    private String resumablePuzzleId;
    private int resumableVisitIndex;

    SessionTracker(Context context) {
        historyFile = new File(context.getFilesDir(), "play_history.jsonl");
    }

    synchronized boolean hasOpenSession() {
        return open != null;
    }

    synchronized boolean hasResumableRun() {
        return resumableRunId != null && resumablePuzzleId != null;
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
                            String generationRejectSummary, GraphAnalyzer.Metrics graph) {
        if (open != null) finish(false, "replaced");
        String nextPuzzleId = RunLifecycle.puzzleId(mode, level, seed, generatorVersion);
        boolean reopened = resumableRunId != null && nextPuzzleId.equals(resumablePuzzleId);
        String existingRunId = reopened ? resumableRunId : null;
        int nextVisitIndex = reopened ? resumableVisitIndex + 1 : 1;
        if (!reopened) clearResumableRun();
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
                generationRejectSummary, graph, existingRunId, nextVisitIndex, reopened);
        if (reopened) clearResumableRun();
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

    synchronized void setModelRoute(JSONArray route) {
        if (open != null) open.setModelRoute(route);
    }

    synchronized void finish(boolean solved, String reason) {
        if (open == null) return;
        OpenSession closing = open;
        JSONObject json = closing.finish(solved, reason);
        if (!solved && "home".equals(reason)) {
            resumableRunId = closing.runId;
            resumablePuzzleId = closing.puzzleId;
            resumableVisitIndex = closing.visitIndex;
        } else if (closing.runId.equals(resumableRunId)) {
            clearResumableRun();
        }
        open = null;
        append(json);
    }

    synchronized void discardResumableRun() {
        clearResumableRun();
    }

    private void clearResumableRun() {
        resumableRunId = null;
        resumablePuzzleId = null;
        resumableVisitIndex = 0;
    }

    synchronized AnalysisSnapshot analyze() {
        List<JSONObject> rows = readObjects();
        AnalysisSnapshot out = new AnalysisSnapshot();
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
            String runOutcome = outcomeForRow(row);

            DifficultyCalibrator.Sample calibration = new DifficultyCalibrator.Sample();
            calibration.order = calibrationOrder++;
            calibration.strategy = strategyName;
            calibration.mode = row.optString("mode", "");
            calibration.generatorVersion = row.optInt("generatorVersion", 0);
            calibration.solved = RunLifecycle.isSolved(runOutcome);
            calibration.hidden = Math.max(1, row.optInt("hidden", 1));
            calibration.activeMs = row.optLong("activeMs", 0L);
            calibration.eventCount = row.optInt("eventCount", 0);
            calibration.predictedBand = row.optInt("ratedLogic", row.optInt("logic", 1));
            if (calibration.predictedBand <= 0) calibration.predictedBand = Math.max(1, row.optInt("logic", 1));
            calibration.deadEndPauses = row.optInt("deadEndPauses", 0);
            calibration.fullIncorrectCount = row.optInt("fullIncorrectCount", 0);

            if (!row.optBoolean("strategyTargetMatched", true)) out.strategyFallbacks++;
            if (row.optInt("generationStage", 1) >= 2) out.expandedGenerations++;
            boolean solved = RunLifecycle.isSolved(runOutcome);
            if (solved) {
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
            out.candidateCellSwitches += row.optInt("candidateCellSwitches", 0);
            out.candidateCellRevisits += row.optInt("candidateCellRevisits", 0);
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
            JSONObject routeComparison = row.optJSONObject("routeComparison");
            if (routeComparison != null && routeComparison.optBoolean("available", false)) {
                out.routeComparedSessions++;
                if (routeComparison.optBoolean("strongDivergence", false)) out.routeStrongDivergences++;
                if (routeComparison.optBoolean("alternateEntry", false)) out.routeAlternateEntries++;
                out.routeAgreementTotal += routeComparison.optDouble("agreementPct", 0.0);
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
            if (RunLifecycle.isExplicitDifficultyOutcome(runOutcome)) calibrationSamples.add(calibration);
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
        out.avgRouteAgreementPct = out.routeComparedSessions == 0 ? 0.0
                : out.routeAgreementTotal / out.routeComparedSessions;

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
            s.outcome = outcomeForRow(row);
            s.solved = RunLifecycle.isSolved(s.outcome);
            s.activeMs = row.optLong("activeMs", 0L);
            s.eventCount = row.optInt("eventCount", 0);
            s.firstActionMs = row.optLong("firstActionMs", -1L);
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
            s.candidateCellSwitches = row.optInt("candidateCellSwitches", 0);
            s.candidateCellRevisits = row.optInt("candidateCellRevisits", 0);
            s.maxCandidatesInOneCell = row.optInt("maxCandidatesInOneCell", 0);
            s.graphCycleRank = row.optInt("graphCycleRank", 0);
            s.graphBridges = row.optInt("graphBridges", 0);
            s.graphArticulationPoints = row.optInt("graphArticulationPoints", 0);
            s.graphHiddenArticulations = row.optInt("graphHiddenArticulations", 0);
            s.graphBranchNodes = row.optInt("graphBranchNodes", 0);
            s.graphDiameter = row.optInt("graphDiameter", 0);
            JSONObject routeComparison = row.optJSONObject("routeComparison");
            if (routeComparison != null && routeComparison.optBoolean("available", false)) {
                s.routeCompared = true;
                s.routeAgreementPct = routeComparison.optDouble("agreementPct", 0.0);
                s.routeEarlyAgreementPct = routeComparison.optDouble("earlyAgreementPct", 0.0);
                s.routeOrderAgreementPct = routeComparison.optDouble("orderAgreementPct", 0.0);
                s.routeAlternateEntry = routeComparison.optBoolean("alternateEntry", false);
                s.routeStrongDivergence = routeComparison.optBoolean("strongDivergence", false);
                JSONObject traversal = routeComparison.optJSONObject("graphTraversal");
                if (traversal != null && traversal.optBoolean("available", false)) {
                    s.traversalAvailable = true;
                    s.traversalDirection = traversal.optString("direction", "unknown");
                    s.traversalConfidencePct = traversal.optDouble("confidencePct", 0.0);
                    s.traversalInternalEntry = traversal.optBoolean("internalEntry", false);
                    s.traversalEntryDepth = traversal.optInt("entryDepth", -1);
                }
            }
            out.recent.add(s);
        }
        return out;
    }

    synchronized String latestTrajectoryReport() {
        List<JSONObject> rows = readObjects();
        if (rows.isEmpty()) return null;
        JSONObject row = rows.get(rows.size() - 1);
        StringBuilder out = new StringBuilder();

        String mode = row.optString("mode", "");
        int level = row.optInt("level", 0);
        String where = mode.startsWith("PATH") && level > 0 ? (UiText.tr("level ", "уровень ", "úroveň ") + level) : UiText.tr("free play", "свободная игра", "volná hra");
        String strategyName = row.optString("strategy", SolutionStrategy.MIXED.name());
        String strategy = strategyName;
        try { strategy = SolutionStrategy.valueOf(strategyName).label; }
        catch (RuntimeException ignored) { }

        out.append(where).append(" · ").append(strategy)
                .append(" · ").append(row.optBoolean("solved", false) ? UiText.tr("solved", "решено", "vyřešeno") : UiText.tr("unfinished", "не завершено", "nedokončeno"))
                .append(UiText.tr("\nActive time: ", "\nАктивное время: ", "\nAktivní čas: ")).append(reportTime(row.optLong("activeMs", 0L)))
                .append(UiText.tr(" · events: ", " · событий: ", " · událostí: ")).append(row.optInt("eventCount", 0));
        if (row.has("graphNodes")) {
            out.append(UiText.tr("\nGraph: μ=", "\nГраф: μ=", "\nGraf: μ=")).append(row.optInt("graphCycleRank", 0))
                    .append(UiText.tr(" · bridges ", " · мосты ", " · mosty ")).append(row.optInt("graphBridges", 0))
                    .append(UiText.tr(" · articulation points ", " · точки сочленения ", " · artikulační body ")).append(row.optInt("graphArticulationPoints", 0))
                    .append(UiText.tr(" · hidden articulations ", " · скрытые узлы-сочленения ", " · skryté artikulace ")).append(row.optInt("graphHiddenArticulations", 0))
                    .append(UiText.tr(" · branch nodes ", " · ветвления ", " · větvení ")).append(row.optInt("graphBranchNodes", 0))
                    .append(UiText.tr(" · diameter ", " · диаметр ", " · průměr ")).append(row.optInt("graphDiameter", 0));
        }

        out.append(UiText.tr("\n\nPlay signals", "\n\nСигналы прохождения", "\n\nSignály průchodu"))
                .append(UiText.tr("\nPauses: productive ", "\nПаузы: продуктивные ", "\nPauzy: produktivní ")).append(row.optInt("productivePauses", 0))
                .append(UiText.tr(" · dead-end ", " · тупиковые ", " · slepé ")).append(row.optInt("deadEndPauses", 0))
                .append(UiText.tr("\nHypothesis checks: ", "\nПроверки гипотез: ", "\nOvěření hypotéz: ")).append(row.optInt("hypothesisEpisodes", 0))
                .append(UiText.tr(" · rapid cascades: ", " · быстрые каскады: ", " · rychlé kaskády: ")).append(row.optInt("rapidCascades", 0));

        int commitments = row.optInt("candidateCommitments", 0);
        if (commitments > 0) {
            out.append(UiText.tr("\nCandidate → decision: ", "\nКандидат → решение: ", "\nKandidát → rozhodnutí: ")).append(commitments)
                    .append(UiText.tr(" · average ", " · в среднем ", " · průměr ")).append(reportTime(row.optLong("avgCandidateCommitmentMs", 0L)));
        }
        int recoveries = row.optInt("recoveryEpisodes", 0);
        if (recoveries > 0) {
            out.append(UiText.tr("\nRecoveries after undo/error/hint: ", "\nВосстановления после отмены/ошибки/намёка: ", "\nObnovení po zpět/chybě/nápovědě: ")).append(recoveries)
                    .append(UiText.tr(" · average ", " · в среднем ", " · průměr "))
                    .append(String.format(java.util.Locale.US, "%.1f", row.optDouble("avgRecoveryActions", 0.0)))
                    .append(UiText.tr(" actions", " действия", " akcí"));
        }
        int switches = row.optInt("candidateCellSwitches", 0);
        int revisits = row.optInt("candidateCellRevisits", 0);
        if (switches > 0 || revisits > 0) {
            out.append(UiText.tr("\nCandidates: cell switches ", "\nКандидаты: переходов между клетками ", "\nKandidáti: přechody mezi buňkami ")).append(switches)
                    .append(UiText.tr(" · revisits ", " · возвратов ", " · návratů ")).append(revisits)
                    .append(UiText.tr(" · max in one cell ", " · максимум в клетке ", " · maximum v buňce ")).append(row.optInt("maxCandidatesInOneCell", 0));
        }

        JSONObject routeComparison = row.optJSONObject("routeComparison");
        if (routeComparison != null && routeComparison.optBoolean("available", false)) {
            out.append(UiText.tr("\n\nHumanSolver route ↔ play", "\n\nМаршрут HumanSolver ↔ прохождение", "\n\nTrasa HumanSolver ↔ průchod"))
                    .append(UiText.tr("\nAgreement: ", "\nСогласование: ", "\nShoda: "))
                    .append(String.format(java.util.Locale.US, "%.0f%%", routeComparison.optDouble("agreementPct", 0.0)))
                    .append(UiText.tr(" · opening ", " · начало ", " · začátek "))
                    .append(String.format(java.util.Locale.US, "%.0f%%", routeComparison.optDouble("earlyAgreementPct", 0.0)))
                    .append(UiText.tr(" · order ", " · порядок ", " · pořadí "))
                    .append(String.format(java.util.Locale.US, "%.0f%%", routeComparison.optDouble("orderAgreementPct", 0.0)));
            double probePct = routeComparison.optDouble("probeReachedEarlyPct", -1.0);
            if (probePct >= 0.0) out.append(UiText.tr(" · pivot reached early ", " · pivot вовремя ", " · pivot dosažen včas "))
                    .append(String.format(java.util.Locale.US, "%.0f%%", probePct));
            out.append(UiText.tr("\nModel: ", "\nМодель: ", "\nModel: ")).append(HumanRouteComparator.describeModel(row.optJSONArray("modelRoute"), 10));
            out.append(UiText.tr("\nPlay: ", "\nПрохождение: ", "\nPrůchod: ")).append(HumanRouteComparator.describeActual(routeComparison, 14));
        }

        out.append(UiText.tr("\n\nSignals for model review", "\n\nСигналы для проверки модели", "\n\nSignály pro kontrolu modelu"));
        boolean signal = false;
        int hidden = Math.max(0, row.optInt("hidden", 0));
        int modelCascade = Math.max(0, row.optInt("maxForcedCascade", 0));
        double cascadeFraction = hidden == 0 ? 0.0 : modelCascade / (double) hidden;
        int rapid = row.optInt("rapidCascades", 0);
        if (cascadeFraction < 0.35 && rapid > 0) {
            out.append(UiText.tr("\n• Play accelerated more than the cascade model expected — inspect an alternative solve path.", "\n• Прохождение ускорилось сильнее, чем ожидала модель каскада — проверить альтернативный путь решения.", "\n• Průchod zrychlil více, než očekával model kaskády — prověř alternativní cestu řešení."));
            signal = true;
        } else if (cascadeFraction >= 0.55 && rapid == 0 && row.optBoolean("solved", false)) {
            out.append(UiText.tr("\n• The model expected a strong cascade, but real play did not accelerate — review the forced-cascade estimate.", "\n• Модель ожидала сильный каскад, но в реальном прохождении ускорения не видно — проверить оценку forced-cascade.", "\n• Model očekával silnou kaskádu, ale skutečný průchod nezrychlil — zkontroluj odhad forced-cascade."));
            signal = true;
        } else if (cascadeFraction >= 0.55 && rapid > 0) {
            out.append(UiText.tr("\n• Model and play agree: a rapid cascade followed the key step.", "\n• Модель и прохождение согласуются: после ключевого шага возник быстрый каскад.", "\n• Model a průchod souhlasí: po klíčovém kroku vznikla rychlá kaskáda."));
            signal = true;
        }

        int hypotheses = row.optInt("hypothesisEpisodes", 0);
        int goodPivots = row.optInt("branchGoodPivotCount", 0);
        if (hypotheses > 0 && goodPivots == 0) {
            out.append(UiText.tr("\n• The player tested hypotheses although the generator marked no strong pivots — possible BranchQualityAnalyzer blind spot.", "\n• Игрок проверял гипотезы, хотя генератор не отметил сильных pivot-точек — возможная слепая зона BranchQualityAnalyzer.", "\n• Hráč ověřoval hypotézy, přestože generátor neoznačil silné pivoty — možná slepá skvrna BranchQualityAnalyzer."));
            signal = true;
        } else if (row.optBoolean("solved", false) && hypotheses == 0 && goodPivots > 0) {
            out.append(UiText.tr("\n• The generator expected hypothesis pivots, but the puzzle was solved without a recorded hypothesis check — another route may exist.", "\n• Генератор ожидал точки гипотезы, но задача решилась без зафиксированной проверки гипотез — возможен другой маршрут.", "\n• Generátor očekával body hypotézy, ale hlavolam byl vyřešen bez zaznamenaného ověření hypotézy — může existovat jiná cesta."));
            signal = true;
        }
        if (revisits >= 3) {
            out.append(UiText.tr("\n• Frequent returns to explored cells — check whether this is a meaningful puzzle hub or a visual/interface ambiguity.", "\n• Частые возвраты к уже исследованным клеткам — проверить, это содержательный узел задачи или визуальная/интерфейсная неоднозначность.", "\n• Časté návraty k již prozkoumaným buňkám — ověř, zda jde o důležitý uzel hlavolamu, nebo vizuální/interface nejasnost."));
            signal = true;
        }
        if (routeComparison != null && routeComparison.optBoolean("available", false)) {
            double routeAgreement = routeComparison.optDouble("agreementPct", 100.0);
            int firstModelStep = routeComparison.optInt("firstModelStep", -1);
            boolean routeSolved = row.optBoolean("solved", false);
            if (routeSolved && routeComparison.optBoolean("strongDivergence", false)) {
                out.append(UiText.tr("\n• The puzzle was solved in an order unlike the HumanSolver route — strong candidate for an alternative solve path.", "\n• Задача решена по порядку, слабо похожему на маршрут HumanSolver — сильный кандидат на альтернативный путь решения.", "\n• Hlavolam byl vyřešen v pořadí málo podobném trase HumanSolver — silný kandidát na alternativní cestu řešení."));
                signal = true;
            }
            if (routeComparison.optBoolean("alternateEntry", false)) {
                out.append(UiText.tr("\n• The first meaningful actions entered the puzzle outside HumanSolver's early steps", "\n• Первые содержательные действия вошли в задачу не через ранние шаги HumanSolver", "\n• První smysluplné akce vstoupily do hlavolamu mimo rané kroky HumanSolver"))
                        .append(firstModelStep >= 0 ? (UiText.tr(" (first match: model step ", " (первое совпадение: шаг модели ", " (první shoda: krok modelu ") + (firstModelStep + 1) + ").") : ".");
                signal = true;
            }
            if (routeComparison.optBoolean("alternateOrder", false) && routeAgreement >= 45.0) {
                out.append(UiText.tr("\n• The player used familiar model nodes in a notably different order — inspect independent fronts and deduction order.", "\n• Игрок использовал знакомые модели узлы, но в заметно другом порядке — проверить независимые фронты и порядок дедукций.", "\n• Hráč použil známé uzly modelu v výrazně jiném pořadí — prověř nezávislé fronty a pořadí dedukcí."));
                signal = true;
            }
            if (routeAgreement >= 78.0 && !routeComparison.optBoolean("alternateEntry", false)) {
                out.append(UiText.tr("\n• Play order agrees well with the current HumanSolver route.", "\n• Порядок прохождения хорошо согласуется с текущим маршрутом HumanSolver.", "\n• Pořadí průchodu dobře odpovídá současné trase HumanSolver."));
                signal = true;
            }
        }
        if (!signal) out.append(UiText.tr("\n• No clear mismatch between current structural and behavioral signals was found.", "\n• Явного расхождения между текущими структурными и поведенческими сигналами не найдено.", "\n• Nebyl nalezen jasný rozpor mezi současnými strukturálními a behaviorálními signály."));

        JSONArray moves = row.optJSONArray("semanticMoves");
        out.append(UiText.tr("\n\nSolve trace", "\n\nХод решения", "\n\nPrůběh řešení"));
        if (moves == null || moves.length() == 0) {
            out.append(UiText.tr("\nNo semantic moves in this session.", "\nНет семантических ходов в этой сессии.", "\nV této relaci nejsou žádné sémantické tahy."));
        } else {
            int limit = Math.min(120, moves.length());
            for (int i = 0; i < limit; i++) {
                JSONObject m = moves.optJSONObject(i);
                if (m == null) continue;
                out.append("\n")
                        .append(m.optInt("seq", i + 1)).append(". ")
                        .append("[").append(reportTime(m.optLong("tMs", 0L))).append("] ")
                        .append(m.optString("notation", m.optString("type", "")));
            }
            if (moves.length() > limit) out.append(UiText.tr("\n… ", "\n… ещё ", "\n… ještě ")).append(moves.length() - limit).append(UiText.tr(" more moves", " ходов", " tahů"));
        }
        out.append(UiText.tr("\n\nThis is a trace of interaction with the puzzle, not a literal record of a person's thoughts.", "\n\nЭто след взаимодействия с задачей, а не буквальная запись мыслей человека.", "\n\nToto je stopa interakce s hlavolamem, nikoli doslovný záznam myšlenek člověka."));
        return out.toString();
    }

    private static String outcomeForRow(JSONObject row) {
        String explicit = row.optString("runOutcome", "");
        if (!explicit.isEmpty()) return explicit;
        return RunLifecycle.outcome(row.optBoolean("solved", false), row.optString("finishReason", "unknown"));
    }

    private static String reportTime(long ms) {
        if (ms < 0) return "—";
        long total = ms / 1000L;
        long min = total / 60L;
        long sec = total % 60L;
        return min + ":" + (sec < 10 ? "0" : "") + sec;
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
        int visits;
        int runs;
        int solved;
        int solvedRuns;
        int inProgressRuns;
        int giveUpRuns;
        int restartedRuns;
        int skippedRuns;
        int abandonedRuns;
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
        int candidateCellSwitches;
        int candidateCellRevisits;
        int kernelSessions;
        int deepKernelSessions;
        int routeComparedSessions;
        int routeStrongDivergences;
        int routeAlternateEntries;
        double routeAgreementTotal;
        double avgRouteAgreementPct;
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
        String outcome;
        long activeMs;
        int eventCount;
        long firstActionMs = -1L;
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
        int candidateCellSwitches;
        int candidateCellRevisits;
        int maxCandidatesInOneCell;
        int graphCycleRank;
        int graphBridges;
        int graphArticulationPoints;
        int graphHiddenArticulations;
        int graphBranchNodes;
        int graphDiameter;
        boolean routeCompared;
        double routeAgreementPct;
        double routeEarlyAgreementPct;
        double routeOrderAgreementPct;
        boolean routeAlternateEntry;
        boolean routeStrongDivergence;
        boolean traversalAvailable;
        String traversalDirection;
        double traversalConfidencePct;
        boolean traversalInternalEntry;
        int traversalEntryDepth = -1;
    }

    private static final class OpenSession {
        final String id = UUID.randomUUID().toString(); // legacy sessionId / stored visit row id
        final String runId;
        final String visitId = UUID.randomUUID().toString();
        final int visitIndex;
        final long startedAtEpochMs = System.currentTimeMillis();
        final String puzzleId;
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
        final int graphNodes;
        final int graphEdges;
        final int graphComponents;
        final int graphCycleRank;
        final int graphBridges;
        final int graphArticulationPoints;
        final int graphVariableArticulations;
        final int graphFactorArticulations;
        final int graphHiddenArticulations;
        final int graphBranchNodes;
        final int graphDiameter;
        final int graphMaxDegree;
        final double graphAverageDegree;
        final JSONArray events = new JSONArray();
        final JSONArray lifecycleEvents = new JSONArray();
        JSONArray modelRoute = new JSONArray();

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
                    int generationRejects, String generationRejectSummary, GraphAnalyzer.Metrics graph,
                    String existingRunId, int visitIndex, boolean reopened) {
            this.runId = existingRunId == null ? UUID.randomUUID().toString() : existingRunId;
            this.visitIndex = Math.max(1, visitIndex);
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
            this.puzzleId = RunLifecycle.puzzleId(mode, level, seed, generatorVersion);
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
            this.graphNodes = graph == null ? 0 : graph.nodes;
            this.graphEdges = graph == null ? 0 : graph.edges;
            this.graphComponents = graph == null ? 0 : graph.components;
            this.graphCycleRank = graph == null ? 0 : graph.cycleRank;
            this.graphBridges = graph == null ? 0 : graph.bridges;
            this.graphArticulationPoints = graph == null ? 0 : graph.articulationPoints;
            this.graphVariableArticulations = graph == null ? 0 : graph.variableArticulations;
            this.graphFactorArticulations = graph == null ? 0 : graph.factorArticulations;
            this.graphHiddenArticulations = graph == null ? 0 : graph.hiddenVariableArticulations;
            this.graphBranchNodes = graph == null ? 0 : graph.branchNodes;
            this.graphDiameter = graph == null ? 0 : graph.diameter;
            this.graphMaxDegree = graph == null ? 0 : graph.maxDegree;
            this.graphAverageDegree = graph == null ? 0.0 : graph.averageDegree;
            lifecycleEvent(reopened ? "PUZZLE_REOPENED" : "PUZZLE_OPENED", null);
            resume();
        }

        void setModelRoute(JSONArray route) {
            try { modelRoute = route == null ? new JSONArray() : new JSONArray(route.toString()); }
            catch (Exception ignored) { modelRoute = new JSONArray(); }
        }

        void resume() {
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
                e.put("seq", events.length() + 1);
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
                root.put("visitIndex", visitIndex);
                root.put("visitOutcome", RunLifecycle.visitOutcome(solved, reason));
                root.put("runOutcome", runOutcome);
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
                root.put("graphNodes", graphNodes);
                root.put("graphEdges", graphEdges);
                root.put("graphComponents", graphComponents);
                root.put("graphCycleRank", graphCycleRank);
                root.put("graphBridges", graphBridges);
                root.put("graphArticulationPoints", graphArticulationPoints);
                root.put("graphVariableArticulations", graphVariableArticulations);
                root.put("graphFactorArticulations", graphFactorArticulations);
                root.put("graphHiddenArticulations", graphHiddenArticulations);
                root.put("graphBranchNodes", graphBranchNodes);
                root.put("graphDiameter", graphDiameter);
                root.put("graphMaxDegree", graphMaxDegree);
                root.put("graphAverageDegree", graphAverageDegree);
                root.put("solved", solved);
                root.put("finishReason", reason == null ? "unknown" : reason);
                root.put("activeMs", activeAccumulatedMs);
                root.put("firstActionMs", firstActionMs);
                root.put("longestPauseBetweenActionsMs", longestPauseBetweenActionsMs);
                root.put("eventCount", events.length());
                appendDerivedEventStats(root);
                root.put("modelRouteVersion", HumanRouteComparator.VERSION);
                root.put("modelRoute", modelRoute);
                root.put("routeComparison", HumanRouteComparator.compare(modelRoute, events));
                root.put("moveNotationVersion", MoveNotation.VERSION);
                root.put("semanticMoves", MoveNotation.semanticMoves(events));
                root.put("candidateTrail", MoveNotation.candidateTrail(events));
                root.put("focusTrail", MoveNotation.focusTrail(events));
                MoveNotation.Summary candidateSummary = MoveNotation.summarizeCandidates(events);
                root.put("candidateSequenceEvents", candidateSummary.events);
                root.put("candidateSequenceDistinctCells", candidateSummary.distinctCells);
                root.put("candidateSequenceDistinctValues", candidateSummary.distinctValues);
                root.put("candidateCellSwitches", candidateSummary.cellSwitches);
                root.put("candidateCellRevisits", candidateSummary.cellRevisits);
                root.put("maxCandidatesInOneCell", candidateSummary.maxCandidatesInOneCell);
                root.put("lifecycleEvents", lifecycleEvents);
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
