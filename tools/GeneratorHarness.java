package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;

/** Standalone generator bench with rejection diagnostics. */
public final class GeneratorHarness {
    public static void main(String[] args) {
        Locale.setDefault(Locale.US);
        int samples = args.length > 0 ? Math.max(1, Integer.parseInt(args[0])) : 5;
        int logicMin = args.length > 1 ? clamp(Integer.parseInt(args[1]), 1, 10) : 6;
        int logicMax = args.length > 2 ? clamp(Integer.parseInt(args[2]), logicMin, 10) : 10;
        String strategyFilter = args.length > 3 ? args[3].trim().toUpperCase(Locale.US) : "";
        String diagonalMode = args.length > 4 ? args[4].trim().toLowerCase(Locale.US) : "targeted";
        System.setProperty("mathcrossword.diagonalMode", diagonalMode);
        boolean experimentalHypothesisL5 = args.length > 5
                && "experimental".equalsIgnoreCase(args[5].trim());
        System.setProperty("mathcrossword.experimentalHypothesisL5",
                Boolean.toString(experimentalHypothesisL5));

        Set<Character> ops = new LinkedHashSet<>();
        for (char c : new char[]{'+', '-', '×', '÷', '^'}) ops.add(c);

        System.out.println("diagonal_mode,strategy,logic,samples,generated,unique,target_matched,fallback,avg_ms,avg_attempts,avg_rejects,constructive_pct,avg_rated,avg_hidden,avg_cycle,avg_domain,avg_steps,avg_depth,avg_stuck,avg_diagonal,avg_onecell_collapse,avg_vulnerable_cells,kernel_pct,kernel_added_pct,kernel_depth2_pct,kernel_branches_avg,kernel_pivots_avg,kernel_deep_pct,kernel_families,stage_graph_ms,stage_arithmetic_ms,stage_hidden_total_ms,stage_hidden_set_ms,stage_hidden_prefilter_ms,stage_tile_bank_ms,stage_tile_pool_ms,stage_tile_select_ms,stage_contradiction_kernel_ms,stage_kernel_profile_ms,stage_cascade_resilience_ms,stage_uniqueness_ms,stage_final_uniqueness_ms,stage_hidden_human_ms,stage_human_ms,stage_eval_ms,families,reject_constructive,reject_geometry,reject_equation,reject_hidden_unique,reject_hidden_topology,reject_final_unique,reject_level,reject_strategy,reject_fallback,reject_none");

        for (SolutionStrategy strategy : SolutionStrategy.values()) {
            if (!strategyFilter.isEmpty() && !strategy.name().equals(strategyFilter)) continue;
            for (int logic = logicMin; logic <= logicMax; logic++) {
                long totalMs = 0;
                int generated = 0, unique = 0, targetMatched = 0, fallback = 0, constructive = 0;
                long attempts = 0, rejects = 0;
                long[] rejectionTotals = new long[GenerationDiagnostics.RejectReason.values().length];
                long[] stageTotals = new long[GenerationDiagnostics.Stage.values().length];
                Map<String,Integer> familyCounts = new LinkedHashMap<>();
                double rated = 0, hidden = 0, cycle = 0, domain = 0, steps = 0, depth = 0, stuck = 0, diagonal = 0;
                double oneCellCollapse = 0, vulnerableCells = 0;
                int kernels = 0, kernelsAdded = 0, kernelsDepth2 = 0, kernelsDeep = 0;
                double kernelBranches = 0, kernelPivots = 0;
                Map<String,Integer> kernelFamilyCounts = new LinkedHashMap<>();

                for (int i = 0; i < samples; i++) {
                    long seed = PuzzleGenerator.mix64(0xC0FFEE1234ABCDEFL
                            ^ ((long) strategy.ordinal() << 48)
                            ^ ((long) logic << 32)
                            ^ i);
                    long t0 = System.nanoTime();
                    try {
                        Puzzle p = PuzzleGenerator.generateFree(logic, 9, 1, 1000, ops, seed, strategy);
                        long ms = (System.nanoTime() - t0) / 1_000_000L;
                        totalMs += ms;
                        generated++;
                        if (SolutionCounter.countSolutions(p, 2) == 1) unique++;
                        if (p.strategyTargetMatched) targetMatched++;
                        if (p.generationStage >= 2) fallback++;
                        if (p.generatorConstructor != null && (p.generatorConstructor.startsWith("chain-")
                                || p.generatorConstructor.startsWith("network-")
                                || p.generatorConstructor.startsWith("hypothesis-"))) constructive++;
                        attempts += p.generationAttempts;
                        rejects += p.generationRejects;

                        LogicAnalyzer.Metrics lm = LogicAnalyzer.analyze(p);
                        HumanSolver.Metrics hm = HumanSolver.analyze(p);
                        rated += p.ratedDisplayLogic;
                        hidden += p.hidden.size();
                        cycle += lm.cycleRank;
                        domain += hm.initialAverageDomain;
                        steps += hm.reasoningSteps;
                        depth += hm.maxReasoningDepth;
                        stuck += hm.stuckRemaining;
                        int diagEq = 0;
                        for (Equation e : p.equations) if (e.a.x != e.c.x && e.a.y != e.c.y) diagEq++;
                        diagonal += diagEq;
                        CascadeResilienceAnalyzer.Profile cascadeProfile = CascadeResilienceAnalyzer.analyze(p);
                        oneCellCollapse += cascadeProfile.maxResolvedFractionAfterOneCell;
                        vulnerableCells += cascadeProfile.vulnerableSingleCells;
                        if (p.contradictionKernel) {
                            kernels++;
                            if (p.contradictionKernelDepth == 2) kernelsDepth2++;
                            if (p.contradictionKernelAddedDecoy) kernelsAdded++;
                            kernelBranches += p.contradictionKernelBranches;
                            kernelPivots += p.contradictionKernelPivots;
                            if (p.contradictionKernelDeepBranches > 0 || p.contradictionKernelDepth >= 4) kernelsDeep++;
                            String kf = p.contradictionKernelFamily == null ? "none" : p.contradictionKernelFamily;
                            kernelFamilyCounts.put(kf, kernelFamilyCounts.getOrDefault(kf, 0) + 1);
                        }
                        String fam = p.generatorFamily == null ? "" : p.generatorFamily;
                        familyCounts.put(fam, familyCounts.getOrDefault(fam, 0) + 1);
                    } catch (RuntimeException ex) {
                        totalMs += (System.nanoTime() - t0) / 1_000_000L;
                    }
                    GenerationDiagnostics d = PuzzleGenerator.lastDiagnostics();
                    if (d != null) {
                        for (GenerationDiagnostics.RejectReason r : GenerationDiagnostics.RejectReason.values()) {
                            rejectionTotals[r.ordinal()] += d.count(r);
                        }
                        for (GenerationDiagnostics.Stage st : GenerationDiagnostics.Stage.values()) {
                            stageTotals[st.ordinal()] += d.stageMillis(st);
                        }
                    }
                }

                double denom = Math.max(1, generated);
                StringBuilder fam = new StringBuilder();
                for (Map.Entry<String,Integer> e : familyCounts.entrySet()) {
                    if (fam.length() > 0) fam.append('|');
                    fam.append(e.getKey()).append(':').append(e.getValue());
                }
                StringBuilder kernelFam = new StringBuilder();
                for (Map.Entry<String,Integer> e : kernelFamilyCounts.entrySet()) {
                    if (kernelFam.length() > 0) kernelFam.append('|');
                    kernelFam.append(e.getKey()).append(':').append(e.getValue());
                }
                System.out.printf(Locale.US,
                        "%s,%s,%d,%d,%d,%d,%d,%d,%.1f,%.2f,%.2f,%.1f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.3f,%.2f,%.1f,%.1f,%.1f,%.2f,%.2f,%.1f,%s,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%s",
                        diagonalMode, strategy.name(), logic, samples, generated, unique, targetMatched, fallback,
                        totalMs / (double) samples, attempts / denom, rejects / denom,
                        100.0 * constructive / denom, rated / denom, hidden / denom, cycle / denom,
                        domain / denom, steps / denom, depth / denom, stuck / denom, diagonal / denom,
                        oneCellCollapse / denom, vulnerableCells / denom,
                        100.0 * kernels / denom, 100.0 * kernelsAdded / denom, 100.0 * kernelsDepth2 / Math.max(1, kernels),
                        kernelBranches / Math.max(1, kernels), kernelPivots / Math.max(1, kernels),
                        100.0 * kernelsDeep / Math.max(1, kernels), kernelFam.toString(),
                        stageTotals[GenerationDiagnostics.Stage.GRAPH.ordinal()] / (double) samples,
                        stageTotals[GenerationDiagnostics.Stage.ARITHMETIC.ordinal()] / (double) samples,
                        stageTotals[GenerationDiagnostics.Stage.HIDDEN_UNIQUENESS.ordinal()] / (double) samples,
                        stageTotals[GenerationDiagnostics.Stage.HIDDEN_SET.ordinal()] / (double) samples,
                        stageTotals[GenerationDiagnostics.Stage.HIDDEN_PREFILTER.ordinal()] / (double) samples,
                        stageTotals[GenerationDiagnostics.Stage.TILE_BANK.ordinal()] / (double) samples,
                        stageTotals[GenerationDiagnostics.Stage.TILE_POOL.ordinal()] / (double) samples,
                        stageTotals[GenerationDiagnostics.Stage.TILE_SELECT.ordinal()] / (double) samples,
                        stageTotals[GenerationDiagnostics.Stage.CONTRADICTION_KERNEL.ordinal()] / (double) samples,
                        stageTotals[GenerationDiagnostics.Stage.KERNEL_PROFILE.ordinal()] / (double) samples,
                        stageTotals[GenerationDiagnostics.Stage.CASCADE_RESILIENCE.ordinal()] / (double) samples,
                        stageTotals[GenerationDiagnostics.Stage.UNIQUENESS.ordinal()] / (double) samples,
                        stageTotals[GenerationDiagnostics.Stage.FINAL_UNIQUENESS.ordinal()] / (double) samples,
                        stageTotals[GenerationDiagnostics.Stage.HIDDEN_HUMAN.ordinal()] / (double) samples,
                        stageTotals[GenerationDiagnostics.Stage.HUMAN_ANALYSIS.ordinal()] / (double) samples,
                        stageTotals[GenerationDiagnostics.Stage.STRATEGY_EVALUATION.ordinal()] / (double) samples,
                        fam.toString());
                for (long v : rejectionTotals) System.out.print("," + v);
                System.out.println();
            }
        }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
