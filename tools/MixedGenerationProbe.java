package com.offline.mathcrossword;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Focused diagnostic probe for MIXED Logic 10 generation availability. */
public final class MixedGenerationProbe {
    private MixedGenerationProbe() { }

    public static void main(String[] args) {
        Locale.setDefault(Locale.US);
        Set<Character> ops = new LinkedHashSet<>();
        for (char c : new char[]{'+', '-', '×', '÷', '^'}) ops.add(c);

        int logic = 10;
        int displayCalc = 9;
        int samples = args.length > 0 ? Integer.parseInt(args[0]) : 12;
        int tier = DifficultyScale.logicTier(logic);
        int calcTier = DifficultyScale.calcTier(displayCalc);
        int eq = PuzzleGenerator.clamp(
                DifficultyScale.pathEquationCount(logic) + GeneratorPolicy.equationDelta(SolutionStrategy.MIXED, tier),
                3, 14);
        int hidden = DifficultyScale.pathHiddenTarget(logic);
        // Mirror generateFree medium-size Logic 10 stability cap.
        hidden = Math.min(hidden, 12);
        hidden = Math.min(18, hidden + GeneratorPolicy.hiddenDelta(SolutionStrategy.MIXED, tier));
        char[] enabled = PuzzleGenerator.toOps(ops);

        System.out.println("=== RAW MIXED L10 tier=" + tier + " eq=" + eq + " hidden=" + hidden + " ===");
        for (int sample = 0; sample < samples; sample++) {
            long seed = PuzzleGenerator.mix64(0x5241574D49584544L
                    ^ ((long) logic << 32)
                    ^ sample * 0x9E3779B97F4A7C15L);
            int shape = GeneratorPolicy.shapeStyle(seed, 0, SolutionStrategy.MIXED);
            GameConfig cfg = new GameConfig(eq, 1000, enabled, hidden, shape,
                    tier, calcTier, logic, displayCalc, logic, displayCalc,
                    SolutionStrategy.MIXED, false);
            GenerationDiagnostics diag = new GenerationDiagnostics(SolutionStrategy.MIXED, tier);
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
            MultiFrontResilienceAnalyzer.Profile front = MultiFrontResilienceAnalyzer.analyze(p);
            boolean prefilter = GeneratorPolicy.staticPrefilter(SolutionStrategy.MIXED, lm, tier);
            boolean difficulty = GeneratorPolicy.acceptsDifficulty(SolutionStrategy.MIXED, lm, hm, tier);
            System.out.println("RAW sample=" + sample
                    + " ms=" + ms
                    + " family=" + safe(p.generatorFamily)
                    + " ctor=" + safe(p.generatorConstructor)
                    + " pre=" + prefilter
                    + " diff=" + difficulty
                    + " rated=" + p.ratedLogic
                    + " hidden=" + lm.hidden
                    + " ambiguous=" + lm.ambiguousEquations
                    + " cross=" + lm.crossHidden
                    + " singletons=" + lm.singletons
                    + " direct=" + lm.directSingleCells
                    + " cycles=" + lm.cycleRank
                    + " staticAvg=" + fmt(lm.averageDomain)
                    + " initSingletons=" + hm.initialSingletons
                    + " initBranches=" + hm.initialBranchCells
                    + " avgDomain=" + fmt(hm.initialAverageDomain)
                    + " branchWidth=" + hm.maxBranchWidth
                    + " basicForced=" + hm.basicForced
                    + " basicRemaining=" + hm.basicRemaining
                    + " cascade=" + hm.maxForcedCascade
                    + " reasoning=" + hm.reasoningSteps
                    + " depth=" + hm.maxReasoningDepth
                    + " stuck=" + hm.stuckRemaining
                    + " fronts=" + front.alternativeFronts
                    + " front2=" + front.secondFront
                    + " balance=" + fmt(front.balance)
                    + " rejects=" + diag.compactSummary());
        }

        int calls = 0;
        int success = 0;
        int baseMiss = 0;
        int twoFront = 0;
        int tier5 = 0;
        long totalMs = 0L;

        System.out.println("=== PRODUCTION MIXED L10 ===");
        for (int sample = 0; sample < samples; sample++) {
            long baseSeed = PuzzleGenerator.mix64(0x4D495845444C3130L
                    ^ ((long) logic << 32)
                    ^ sample * 0x9E3779B97F4A7C15L);
            boolean accepted = false;
            for (int retry = 0; retry < 3 && !accepted; retry++) {
                calls++;
                long seed = PuzzleGenerator.mix64(baseSeed + retry * 0x9E3779B97F4A7C15L);
                long t0 = System.nanoTime();
                try {
                    PuzzleModel.Puzzle p = PuzzleGenerator.generateFree(
                            logic, displayCalc, 1, 1000, ops, seed, SolutionStrategy.MIXED);
                    long ms = (System.nanoTime() - t0) / 1_000_000L;
                    totalMs += ms;
                    success++;
                    if ("mixed-two-front".equals(p.generatorFamily)) twoFront++;
                    LogicAnalyzer.Metrics lm = LogicAnalyzer.analyze(p);
                    HumanSolver.Metrics hm = HumanSolver.analyze(p);
                    if (GeneratorPolicy.acceptsDifficulty(SolutionStrategy.MIXED, lm, hm, tier)) tier5++;
                    MultiFrontResilienceAnalyzer.Profile front = MultiFrontResilienceAnalyzer.analyze(p);
                    GenerationDiagnostics d = PuzzleGenerator.lastDiagnostics();
                    System.out.println("OK sample=" + sample
                            + " retry=" + retry
                            + " ms=" + ms
                            + " stage=" + p.generationStage
                            + " family=" + safe(p.generatorFamily)
                            + " ctor=" + safe(p.generatorConstructor)
                            + " rated=" + p.ratedLogic
                            + " hidden=" + lm.hidden
                            + " ambiguous=" + lm.ambiguousEquations
                            + " cross=" + lm.crossHidden
                            + " cycles=" + lm.cycleRank
                            + " initBranches=" + hm.initialBranchCells
                            + " avgDomain=" + fmt(hm.initialAverageDomain)
                            + " basicForced=" + hm.basicForced
                            + " basicRemaining=" + hm.basicRemaining
                            + " cascade=" + hm.maxForcedCascade
                            + " reasoning=" + hm.reasoningSteps
                            + " depth=" + hm.maxReasoningDepth
                            + " stuck=" + hm.stuckRemaining
                            + " fronts=" + front.alternativeFronts
                            + " front2=" + front.secondFront
                            + " balance=" + fmt(front.balance)
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
                "SUMMARY calls=%d success=%d base_miss=%d tier5=%d two_front=%d avg_call_ms=%.1f%n",
                calls, success, baseMiss, tier5, twoFront,
                calls == 0 ? 0.0 : totalMs / (double) calls);
    }

    private static String fmt(double v) { return String.format(Locale.US, "%.2f", v); }
    private static String safe(String s) { return s == null ? "" : s; }
}
