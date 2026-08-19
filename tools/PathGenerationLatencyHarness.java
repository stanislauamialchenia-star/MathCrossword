package com.offline.mathcrossword;

/** Focused regression probe for the PATH level that stalled on-device. */
public final class PathGenerationLatencyHarness {
    public static void main(String[] args) {
        int level = args.length > 0 ? Math.max(1, Integer.parseInt(args[0])) : 78;
        long started = System.nanoTime();
        PuzzleModel.Puzzle puzzle = PuzzleGenerator.generatePath(level);
        long wallMs = (System.nanoTime() - started) / 1_000_000L;
        GenerationDiagnostics d = PuzzleGenerator.lastDiagnostics();
        System.out.println("PATH level=" + level
                + " wall_ms=" + wallMs
                + " attempts=" + (d == null ? -1 : d.candidateAttempts)
                + " rejects=" + (d == null ? -1 : d.totalRejects())
                + " stage=" + (d == null ? "" : d.stageSummary())
                + " reject_summary=" + (d == null ? "" : d.compactSummary())
                + " hidden=" + puzzle.hidden.size()
                + " rated=" + puzzle.ratedDisplayLogic);
    }
}
