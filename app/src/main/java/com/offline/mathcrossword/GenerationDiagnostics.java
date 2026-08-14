package com.offline.mathcrossword;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Android-independent diagnostics for one generator request. */
final class GenerationDiagnostics {
    enum Stage {
        GRAPH,
        ARITHMETIC,
        HIDDEN_UNIQUENESS,
        HIDDEN_SET,
        HIDDEN_PREFILTER,
        TILE_BANK,
        TILE_POOL,
        TILE_SELECT,
        CONTRADICTION_KERNEL,
        KERNEL_PROFILE,
        CASCADE_RESILIENCE,
        UNIQUENESS,
        FINAL_UNIQUENESS,
        HIDDEN_HUMAN,
        HUMAN_ANALYSIS,
        STRATEGY_EVALUATION,
        DECEPTIVE_DECOYS,
        BRANCH_QUALITY,
        FRONT_RESILIENCE,
        PATH_POST_ANALYSIS
    }

    enum RejectReason {
        CONSTRUCTIVE_BUILDER_FAILED,
        GEOMETRY_FAILED,
        EQUATION_ASSIGNMENT_FAILED,
        HIDDEN_OR_UNIQUENESS_FAILED,
        HIDDEN_TOPOLOGY_REJECTED,
        FINAL_UNIQUENESS_FAILED,
        LEVEL_MISMATCH,
        STRATEGY_MISMATCH,
        FALLBACK_CANDIDATE_FAILED,
        NO_ACCEPTABLE_PUZZLE,
        PATH_GEOMETRY_REPEAT,
        PATH_DIFFICULTY_REJECTED,
        PATH_CASCADE_REJECTED
    }

    final SolutionStrategy requestedStrategy;
    final int requestedLogic;
    final Map<RejectReason, Integer> rejects = new EnumMap<>(RejectReason.class);
    final Map<Stage, Long> stageNanos = new EnumMap<>(Stage.class);
    int candidateAttempts;
    int strictAttempts;
    int fallbackAttempts;
    long elapsedNanos;
    String acceptedConstructor = "";
    String acceptedFamily = "";
    int acceptedStage;
    boolean targetMatched;

    GenerationDiagnostics(SolutionStrategy requestedStrategy, int requestedLogic) {
        this.requestedStrategy = requestedStrategy == null ? SolutionStrategy.MIXED : requestedStrategy;
        this.requestedLogic = requestedLogic;
    }

    void attempt(boolean fallback) {
        candidateAttempts++;
        if (fallback) fallbackAttempts++;
        else strictAttempts++;
    }

    void reject(RejectReason reason) {
        rejects.put(reason, rejects.getOrDefault(reason, 0) + 1);
    }

    void addStageTime(Stage stage, long nanos) {
        if (stage == null || nanos <= 0) return;
        stageNanos.put(stage, stageNanos.getOrDefault(stage, 0L) + nanos);
    }

    long stageMillis(Stage stage) {
        return stageNanos.getOrDefault(stage, 0L) / 1_000_000L;
    }

    String stageSummary() {
        StringBuilder sb = new StringBuilder();
        for (Stage stage : Stage.values()) {
            long ms = stageMillis(stage);
            if (ms == 0L) continue;
            if (sb.length() > 0) sb.append(';');
            sb.append(stage.name()).append('=').append(ms).append("ms");
        }
        return sb.toString();
    }

    void accept(PuzzleModel.Puzzle p) {
        acceptedConstructor = p.generatorConstructor == null ? "" : p.generatorConstructor;
        acceptedFamily = p.generatorFamily == null ? "" : p.generatorFamily;
        acceptedStage = p.generationStage;
        targetMatched = p.strategyTargetMatched;
    }

    int totalRejects() {
        int n = 0;
        for (int v : rejects.values()) n += v;
        return n;
    }

    int count(RejectReason reason) {
        return rejects.getOrDefault(reason, 0);
    }

    String compactSummary() {
        StringBuilder sb = new StringBuilder();
        for (RejectReason r : RejectReason.values()) {
            int n = count(r);
            if (n == 0) continue;
            if (sb.length() > 0) sb.append(';');
            sb.append(r.name()).append('=').append(n);
        }
        return sb.toString();
    }

    String csvRejectColumns() {
        return String.format(Locale.US, "%d,%d,%d,%d,%d,%d,%d,%d,%d,%d",
                count(RejectReason.CONSTRUCTIVE_BUILDER_FAILED),
                count(RejectReason.GEOMETRY_FAILED),
                count(RejectReason.EQUATION_ASSIGNMENT_FAILED),
                count(RejectReason.HIDDEN_OR_UNIQUENESS_FAILED),
                count(RejectReason.HIDDEN_TOPOLOGY_REJECTED),
                count(RejectReason.FINAL_UNIQUENESS_FAILED),
                count(RejectReason.LEVEL_MISMATCH),
                count(RejectReason.STRATEGY_MISMATCH),
                count(RejectReason.FALLBACK_CANDIDATE_FAILED),
                count(RejectReason.NO_ACCEPTABLE_PUZZLE));
    }

    static final class Result {
        final PuzzleModel.Puzzle puzzle;
        final GenerationDiagnostics diagnostics;
        Result(PuzzleModel.Puzzle puzzle, GenerationDiagnostics diagnostics) {
            this.puzzle = puzzle;
            this.diagnostics = diagnostics;
        }
    }
}
