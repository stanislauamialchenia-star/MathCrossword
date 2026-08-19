package com.offline.mathcrossword;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Focused diagnostic probe for expert NETWORK generation availability and provenance. */
public final class NetworkGenerationProbe {
    private NetworkGenerationProbe() { }

    public static void main(String[] args) {
        Locale.setDefault(Locale.US);
        Set<Character> ops = new LinkedHashSet<>();
        for (char c : new char[]{'+', '-', '×', '÷', '^'}) ops.add(c);

        int[] logicLevels = {8, 10};
        int samples = args.length > 0 ? Integer.parseInt(args[0]) : 12;
        int total = 0;
        int success = 0;
        int trueNetwork = 0;
        int matched = 0;
        int fallback = 0;
        long totalMs = 0L;

        for (int logic : logicLevels) {
            System.out.println("=== NETWORK L" + logic + " ===");
            for (int sample = 0; sample < samples; sample++) {
                long baseSeed = PuzzleGenerator.mix64(0x4E4554574F524B4CL
                        ^ ((long) logic << 32)
                        ^ sample * 0x9E3779B97F4A7C15L);
                boolean accepted = false;
                for (int retry = 0; retry < 3 && !accepted; retry++) {
                    total++;
                    long seed = PuzzleGenerator.mix64(baseSeed + retry * 0x9E3779B97F4A7C15L);
                    long t0 = System.nanoTime();
                    try {
                        PuzzleModel.Puzzle p = PuzzleGenerator.generateFree(
                                logic, Math.min(10, logic + 1), 1, 1000, ops, seed, SolutionStrategy.NETWORK);
                        long ms = (System.nanoTime() - t0) / 1_000_000L;
                        totalMs += ms;
                        success++;
                        if (p.generationStrategy == SolutionStrategy.NETWORK) trueNetwork++;
                        if (p.strategyTargetMatched) matched++;
                        if (p.generationStage >= 3) fallback++;
                        GenerationDiagnostics d = PuzzleGenerator.lastDiagnostics();
                        System.out.println("OK logic=" + logic
                                + " sample=" + sample
                                + " retry=" + retry
                                + " ms=" + ms
                                + " stage=" + p.generationStage
                                + " matched=" + p.strategyTargetMatched
                                + " generationStrategy=" + p.generationStrategy
                                + " family=" + safe(p.generatorFamily)
                                + " ctor=" + safe(p.generatorConstructor)
                                + " rated=" + p.ratedLogic
                                + " hidden=" + p.hidden.size()
                                + " attempts=" + (d == null ? -1 : d.candidateAttempts)
                                + " rejects=" + (d == null ? "" : d.compactSummary())
                                + " timings=" + (d == null ? "" : d.stageSummary()));
                        accepted = true;
                    } catch (RuntimeException ex) {
                        long ms = (System.nanoTime() - t0) / 1_000_000L;
                        totalMs += ms;
                        GenerationDiagnostics d = PuzzleGenerator.lastDiagnostics();
                        System.out.println("FAIL logic=" + logic
                                + " sample=" + sample
                                + " retry=" + retry
                                + " ms=" + ms
                                + " ex=" + ex.getClass().getSimpleName()
                                + " attempts=" + (d == null ? -1 : d.candidateAttempts)
                                + " rejects=" + (d == null ? "" : d.compactSummary())
                                + " timings=" + (d == null ? "" : d.stageSummary()));
                    }
                }
                if (!accepted) System.out.println("BASE_MISS logic=" + logic + " sample=" + sample);
            }
        }

        System.out.printf(Locale.US,
                "SUMMARY calls=%d success=%d true_network=%d matched=%d fallback=%d avg_call_ms=%.1f%n",
                total, success, trueNetwork, matched, fallback,
                total == 0 ? 0.0 : totalMs / (double) total);
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
