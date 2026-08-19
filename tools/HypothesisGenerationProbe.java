package com.offline.mathcrossword;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Focused diagnostic probe for HYPOTHESIS Logic 8 generation availability and provenance. */
public final class HypothesisGenerationProbe {
    private HypothesisGenerationProbe() { }

    public static void main(String[] args) {
        Locale.setDefault(Locale.US);
        Set<Character> ops = new LinkedHashSet<>();
        for (char c : new char[]{'+', '-', '×', '÷', '^'}) ops.add(c);

        int logic = 8;
        int displayCalc = 9;
        int samples = args.length > 0 ? Integer.parseInt(args[0]) : 12;
        int tier = DifficultyScale.logicTier(logic);
        int calcTier = DifficultyScale.calcTier(displayCalc);
        int eq = PuzzleGenerator.clamp(
                DifficultyScale.pathEquationCount(logic) + GeneratorPolicy.equationDelta(SolutionStrategy.HYPOTHESIS, tier),
                3, 14);
        int hidden = Math.min(18,
                DifficultyScale.pathHiddenTarget(logic) + GeneratorPolicy.hiddenDelta(SolutionStrategy.HYPOTHESIS, tier));
        char[] enabled = PuzzleGenerator.toOps(ops);

        System.out.println("=== RAW HYPOTHESIS L8 tier=" + tier + " eq=" + eq + " hidden=" + hidden + " ===");
        for (int sample = 0; sample < samples; sample++) {
            long seed = PuzzleGenerator.mix64(0x5241574859504F54L
                    ^ ((long) logic << 32)
                    ^ sample * 0x9E3779B97F4A7C15L);
            int shape = GeneratorPolicy.shapeStyle(seed, 0, SolutionStrategy.HYPOTHESIS);
            GameConfig cfg = new GameConfig(eq, 1000, enabled, hidden, shape,
                    tier, calcTier, logic, displayCalc, logic, displayCalc,
                    SolutionStrategy.HYPOTHESIS, false);
            GenerationDiagnostics diag = new GenerationDiagnostics(SolutionStrategy.HYPOTHESIS, tier);
            long t0 = System.nanoTime();
            PuzzleModel.Puzzle p = PuzzleGenerator.generateCandidate(cfg, seed, diag);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            if (p == null) {
                System.out.println("RAW_NULL sample=" + sample + " ms=" + ms
                        + " rejects=" + diag.compactSummary() + " timings=" + diag.stageSummary());
                continue;
            }
            LogicAnalyzer.Metrics lm = LogicAnalyzer.analyze(p);
            HumanSolver.Metrics hm = HumanSolver.analyze(p);
            boolean difficulty = GeneratorPolicy.acceptsDifficulty(SolutionStrategy.HYPOTHESIS, lm, hm, tier);
            boolean signature = GeneratorPolicy.accepts(SolutionStrategy.HYPOTHESIS, lm, hm, tier);
            System.out.println("RAW sample=" + sample
                    + " ms=" + ms
                    + " family=" + safe(p.generatorFamily)
                    + " ctor=" + safe(p.generatorConstructor)
                    + " diff=" + difficulty
                    + " sig=" + signature
                    + " kernel=" + p.contradictionKernel
                    + " hidden=" + lm.hidden
                    + " ambiguous=" + lm.ambiguousEquations
                    + " cross=" + lm.crossHidden
                    + " singletons=" + lm.singletons
                    + " initSingletons=" + hm.initialSingletons
                    + " initBranches=" + hm.initialBranchCells
                    + " avgDomain=" + fmt(hm.initialAverageDomain)
                    + " branchWidth=" + hm.maxBranchWidth
                    + " basicForced=" + hm.basicForced
                    + " basicRemaining=" + hm.basicRemaining
                    + " cascade=" + hm.maxForcedCascade
                    + " lookahead=" + hm.lookaheadDeductions
                    + " depth2=" + hm.depth2Deductions
                    + " reasoning=" + hm.reasoningSteps
                    + " depth=" + hm.maxReasoningDepth
                    + " stuck=" + hm.stuckRemaining
                    + " rejects=" + diag.compactSummary());
        }

        int calls = 0;
        int success = 0;
        int trueHypothesis = 0;
        int matched = 0;
        int fallback = 0;
        int baseMiss = 0;
        long totalMs = 0L;

        System.out.println("=== PRODUCTION HYPOTHESIS L8 ===");
        for (int sample = 0; sample < samples; sample++) {
            long baseSeed = PuzzleGenerator.mix64(0x4859504F54484538L
                    ^ ((long) logic << 32)
                    ^ sample * 0x9E3779B97F4A7C15L);
            boolean accepted = false;
            for (int retry = 0; retry < 3 && !accepted; retry++) {
                calls++;
                long seed = PuzzleGenerator.mix64(baseSeed + retry * 0x9E3779B97F4A7C15L);
                long t0 = System.nanoTime();
                try {
                    PuzzleModel.Puzzle p = PuzzleGenerator.generateFree(
                            logic, displayCalc, 1, 1000, ops, seed, SolutionStrategy.HYPOTHESIS);
                    long ms = (System.nanoTime() - t0) / 1_000_000L;
                    totalMs += ms;
                    success++;
                    if (p.generationStrategy == SolutionStrategy.HYPOTHESIS) trueHypothesis++;
                    if (p.strategyTargetMatched) matched++;
                    if (p.generationStage >= 3) fallback++;
                    GenerationDiagnostics d = PuzzleGenerator.lastDiagnostics();
                    LogicAnalyzer.Metrics lm = LogicAnalyzer.analyze(p);
                    HumanSolver.Metrics hm = HumanSolver.analyze(p);
                    System.out.println("OK sample=" + sample
                            + " retry=" + retry
                            + " ms=" + ms
                            + " stage=" + p.generationStage
                            + " matched=" + p.strategyTargetMatched
                            + " generationStrategy=" + p.generationStrategy
                            + " family=" + safe(p.generatorFamily)
                            + " ctor=" + safe(p.generatorConstructor)
                            + " kernel=" + p.contradictionKernel
                            + " rated=" + p.ratedLogic
                            + " hidden=" + lm.hidden
                            + " initBranches=" + hm.initialBranchCells
                            + " avgDomain=" + fmt(hm.initialAverageDomain)
                            + " branchWidth=" + hm.maxBranchWidth
                            + " basicForced=" + hm.basicForced
                            + " basicRemaining=" + hm.basicRemaining
                            + " cascade=" + hm.maxForcedCascade
                            + " lookahead=" + hm.lookaheadDeductions
                            + " depth2=" + hm.depth2Deductions
                            + " depth=" + hm.maxReasoningDepth
                            + " stuck=" + hm.stuckRemaining
                            + " attempts=" + (d == null ? -1 : d.candidateAttempts)
                            + " rejects=" + (d == null ? "" : d.compactSummary())
                            + " timings=" + (d == null ? "" : d.stageSummary()));
                    accepted = true;
                } catch (RuntimeException ex) {
                    long ms = (System.nanoTime() - t0) / 1_000_000L;
                    totalMs += ms;
                    GenerationDiagnostics d = PuzzleGenerator.lastDiagnostics();
                    System.out.println("FAIL sample=" + sample
                            + " retry=" + retry
                            + " ms=" + ms
                            + " ex=" + ex.getClass().getSimpleName()
                            + " attempts=" + (d == null ? -1 : d.candidateAttempts)
                            + " rejects=" + (d == null ? "" : d.compactSummary())
                            + " timings=" + (d == null ? "" : d.stageSummary()));
                }
            }
            if (!accepted) {
                baseMiss++;
                System.out.println("BASE_MISS sample=" + sample);
            }
        }

        System.out.printf(Locale.US,
                "SUMMARY calls=%d success=%d true_hypothesis=%d matched=%d fallback=%d base_miss=%d avg_call_ms=%.1f%n",
                calls, success, trueHypothesis, matched, fallback, baseMiss,
                calls == 0 ? 0.0 : totalMs / (double) calls);
    }

    private static String fmt(double v) { return String.format(Locale.US, "%.2f", v); }
    private static String safe(String s) { return s == null ? "" : s; }
}
