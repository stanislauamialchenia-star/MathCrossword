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

        // First inspect one raw requested-strategy candidate per base seed. This tells
        // us which NETWORK gate misses before production retry/fallback hides it.
        for (int logic : logicLevels) {
            int displayCalc = Math.min(10, logic + 1);
            int tier = DifficultyScale.logicTier(logic);
            int calcTier = DifficultyScale.calcTier(displayCalc);
            int eq = PuzzleGenerator.clamp(
                    DifficultyScale.pathEquationCount(logic) + GeneratorPolicy.equationDelta(SolutionStrategy.NETWORK, tier),
                    3, 14);
            int baseHidden = DifficultyScale.pathHiddenTarget(logic);
            if (logic == 10) baseHidden = Math.min(baseHidden, 12);
            int hidden = Math.min(18, baseHidden + GeneratorPolicy.hiddenDelta(SolutionStrategy.NETWORK, tier));
            char[] enabled = PuzzleGenerator.toOps(ops);

            System.out.println("=== RAW NETWORK L" + logic + " tier=" + tier + " eq=" + eq + " hidden=" + hidden + " ===");
            for (int sample = 0; sample < samples; sample++) {
                long seed = PuzzleGenerator.mix64(0x5241574E4554574FL
                        ^ ((long) logic << 32)
                        ^ sample * 0x9E3779B97F4A7C15L);
                int shape = GeneratorPolicy.shapeStyle(seed, 0, SolutionStrategy.NETWORK);
                GameConfig cfg = new GameConfig(eq, 1000, enabled, hidden, shape,
                        tier, calcTier, logic, displayCalc, logic, displayCalc,
                        SolutionStrategy.NETWORK, false);
                GenerationDiagnostics diag = new GenerationDiagnostics(SolutionStrategy.NETWORK, tier);
                long t0 = System.nanoTime();
                PuzzleModel.Puzzle p = PuzzleGenerator.generateCandidate(cfg, seed, diag);
                long ms = (System.nanoTime() - t0) / 1_000_000L;
                if (p == null) {
                    System.out.println("RAW_NULL logic=" + logic + " sample=" + sample + " ms=" + ms
                            + " rejects=" + diag.compactSummary() + " timings=" + diag.stageSummary());
                    continue;
                }
                LogicAnalyzer.Metrics lm = LogicAnalyzer.analyze(p);
                HumanSolver.Metrics hm = HumanSolver.analyze(p);
                boolean difficulty = GeneratorPolicy.acceptsDifficulty(SolutionStrategy.NETWORK, lm, hm, tier);
                boolean signature = GeneratorPolicy.accepts(SolutionStrategy.NETWORK, lm, hm, tier);
                System.out.println("RAW logic=" + logic
                        + " sample=" + sample
                        + " ms=" + ms
                        + " family=" + safe(p.generatorFamily)
                        + " ctor=" + safe(p.generatorConstructor)
                        + " diff=" + difficulty
                        + " sig=" + signature
                        + " hidden=" + lm.hidden
                        + " cycles=" + lm.cycleRank
                        + " cross=" + lm.crossHidden
                        + " ambiguous=" + lm.ambiguousEquations
                        + " singletons=" + lm.singletons
                        + " initBranches=" + hm.initialBranchCells
                        + " avgDomain=" + fmt(hm.initialAverageDomain)
                        + " basicForced=" + hm.basicForced
                        + " basicRemaining=" + hm.basicRemaining
                        + " cascade=" + hm.maxForcedCascade
                        + " reasoning=" + hm.reasoningSteps
                        + " depth=" + hm.maxReasoningDepth
                        + " stuck=" + hm.stuckRemaining
                        + " rejects=" + diag.compactSummary());
            }
        }

        int total = 0;
        int success = 0;
        int trueNetwork = 0;
        int matched = 0;
        int fallback = 0;
        long totalMs = 0L;

        for (int logic : logicLevels) {
            System.out.println("=== PRODUCTION NETWORK L" + logic + " ===");
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

    private static String fmt(double v) { return String.format(Locale.US, "%.2f", v); }
    private static String safe(String s) { return s == null ? "" : s; }
}
