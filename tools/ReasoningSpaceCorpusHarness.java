package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Emits one CSV row per deterministic generated puzzle for offline reasoning-space analysis.
 *
 * Unlike GeneratorHarness, this intentionally does not aggregate samples. The research
 * question is whether individual puzzles occupy different reasoning regions, not only
 * whether a strategy has a good average score.
 */
public final class ReasoningSpaceCorpusHarness {
    private ReasoningSpaceCorpusHarness() { }

    public static void main(String[] args) {
        Locale.setDefault(Locale.US);
        int samples = args.length > 0 ? Math.max(1, Integer.parseInt(args[0])) : 20;
        int logicMin = args.length > 1 ? clamp(Integer.parseInt(args[1]), 1, 10) : 4;
        int logicMax = args.length > 2 ? clamp(Integer.parseInt(args[2]), logicMin, 10) : 10;
        String strategyFilter = args.length > 3 ? args[3].trim().toUpperCase(Locale.US) : "";

        Set<Character> ops = new LinkedHashSet<>();
        for (char c : new char[]{'+', '-', '×', '÷', '^'}) ops.add(c);

        System.out.println(String.join(",",
                "strategy",
                "logic",
                "sample",
                "seed",
                "generated",
                "unique",
                "target_matched",
                "generation_stage",
                "generator_version",
                "constructor",
                "family",
                "rated_logic",
                "hidden",
                "equations",
                "reasoning_steps",
                "reasoning_depth",
                "max_branch_width",
                "initial_branch_cells",
                "basic_forced",
                "basic_remaining",
                "max_forced_cascade",
                "alternative_fronts",
                "front_balance",
                "largest_front_fraction",
                "cycle_rank",
                "ambiguous_equations",
                "cross_hidden",
                "generation_ms",
                "generation_attempts",
                "generation_rejects",
                "reject_summary"));

        for (SolutionStrategy strategy : SolutionStrategy.values()) {
            if (!strategyFilter.isEmpty() && !strategy.name().equals(strategyFilter)) continue;
            for (int logic = logicMin; logic <= logicMax; logic++) {
                for (int sample = 0; sample < samples; sample++) {
                    long seed = PuzzleGenerator.mix64(0x51A7E5D9C0FFEE11L
                            ^ ((long) strategy.ordinal() << 48)
                            ^ ((long) logic << 32)
                            ^ sample);
                    emit(strategy, logic, sample, seed, ops);
                }
            }
        }
    }

    private static void emit(SolutionStrategy strategy, int logic, int sample,
                             long seed, Set<Character> ops) {
        try {
            Puzzle p = PuzzleGenerator.generateFree(logic, 9, 1, 1000, ops, seed, strategy);
            LogicAnalyzer.Metrics lm = LogicAnalyzer.analyze(p);
            HumanSolver.Metrics hm = HumanSolver.analyze(p);
            MultiFrontResilienceAnalyzer.Profile front = MultiFrontResilienceAnalyzer.analyze(p);
            boolean unique = SolutionCounter.countSolutions(p, 2) == 1;

            System.out.println(csv(
                    strategy.name(),
                    logic,
                    sample,
                    seed,
                    1,
                    unique ? 1 : 0,
                    p.strategyTargetMatched ? 1 : 0,
                    p.generationStage,
                    p.generatorVersion,
                    safe(p.generatorConstructor),
                    safe(p.generatorFamily),
                    p.ratedDisplayLogic,
                    p.hidden.size(),
                    p.equations.size(),
                    hm.reasoningSteps,
                    hm.maxReasoningDepth,
                    hm.maxBranchWidth,
                    hm.initialBranchCells,
                    hm.basicForced,
                    hm.basicRemaining,
                    hm.maxForcedCascade,
                    front.alternativeFronts,
                    front.balance,
                    front.largestFrontFraction,
                    lm.cycleRank,
                    lm.ambiguousEquations,
                    lm.crossHidden,
                    p.generationMillis,
                    p.generationAttempts,
                    p.generationRejects,
                    safe(p.generationRejectSummary)));
        } catch (RuntimeException ex) {
            GenerationDiagnostics d = PuzzleGenerator.lastDiagnostics();
            System.out.println(csv(
                    strategy.name(),
                    logic,
                    sample,
                    seed,
                    0,
                    0,
                    0,
                    "",
                    PuzzleGenerator.GENERATOR_VERSION,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    d == null ? "" : d.candidateAttempts,
                    d == null ? "" : d.totalRejects(),
                    d == null ? ex.getClass().getSimpleName() : d.compactSummary()));
        }
    }

    private static String csv(Object... values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append(',');
            String s = values[i] == null ? "" : String.valueOf(values[i]);
            if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
                out.append('"').append(s.replace("\"", "\"\"")).append('"');
            } else {
                out.append(s);
            }
        }
        return out.toString();
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
