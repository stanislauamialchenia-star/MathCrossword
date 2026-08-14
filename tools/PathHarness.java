package com.offline.mathcrossword;

import java.util.Locale;

/** Regression harness for the real PATH progression, including cascade/autopilot risk. */
public final class PathHarness {
    public static void main(String[] args) {
        Locale.setDefault(Locale.US);
        int from = args.length > 0 ? Math.max(1, Integer.parseInt(args[0])) : 70;
        int to = args.length > 1 ? Math.max(from, Integer.parseInt(args[1])) : 100;
        int step = args.length > 2 ? Math.max(1, Integer.parseInt(args[2])) : 5;
        System.out.println("level,logic,logic_score,calc,calc_score,rated,family,hidden,basic_forced,basic_remaining,reasoning_steps,max_forced_cascade,max_forced_fraction,truth_reveal_fraction,total_decoys,deceptive_decoys,deceptive_support,contextual_decoys,resource_conflict_decoys,context_constraints,context_depth,context_gain,branch_pivots,good_pivots,false_branches,depth2_refutable,depth2_survive,max_branch_width,branch_info_gain,fronts,front_balance,largest_front_fraction,accepted,wall_ms,attempts,rejects,stage_ms,reject_summary");
        for (int level = from; level <= to; level += step) {
            long t0 = System.nanoTime();
            try {
                PuzzleModel.Puzzle p = PuzzleGenerator.generatePath(level);
                HumanSolver.Metrics h = HumanSolver.analyze(p);
                LogicAnalyzer.Metrics lm = LogicAnalyzer.analyze(p);
                CascadeResilienceAnalyzer.Profile c = CascadeResilienceAnalyzer.analyze(p);
                double forcedFraction = p.hidden.isEmpty() ? 0.0 : h.maxForcedCascade / (double)p.hidden.size();
                BranchQualityAnalyzer.Profile b = BranchQualityAnalyzer.analyze(p);
                MultiFrontResilienceAnalyzer.Profile f = MultiFrontResilienceAnalyzer.analyze(p);
                GenerationDiagnostics d = PuzzleGenerator.lastDiagnostics();
                System.out.printf(Locale.US, "%d,%d,%.2f,%d,%.2f,%d,%s,%d,%d,%d,%d,%d,%.3f,%.3f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.3f,%.3f,%s,%d,%d,%d,%s,%s%n",
                        level, p.displayLogicLevel, p.logicScore, p.displayCalcLevel, p.calcScore, p.ratedDisplayLogic,
                        p.generatorFamily == null ? "" : p.generatorFamily,
                        p.hidden.size(), h.basicForced, h.basicRemaining, h.reasoningSteps,
                        h.maxForcedCascade, forcedFraction, c.maxResolvedFractionAfterOneCell,
                        p.decoyCount, p.deceptiveDecoyCount, p.deceptiveDecoySupportMax,
                        p.contextualDecoyCount, p.resourceConflictDecoyCount, p.contextualDecoyConstraintSupportMax,
                        p.contextualDecoyDepthMax, p.contextualDecoyInformationGainMax,
                        b.pivotCount, b.goodPivotCount, b.seriousFalseBranches,
                        b.depth2RefutableBranches, b.depth2SurvivingBranches,
                        b.maxBranchWidth, b.maxInformationGain,
                        f.alternativeFronts, f.balance, f.largestFrontFraction,
                        LogicAnalyzer.acceptForLevel(lm, h, p.logicLevel)
                                && PuzzleGenerator.pathCascadeAcceptable(level, p, h, c)
                                && PuzzleGenerator.pathFrontAcceptable(p, f),
                        (System.nanoTime() - t0) / 1_000_000L,
                        d == null ? 0 : d.candidateAttempts, d == null ? 0 : d.totalRejects(),
                        d == null ? "" : d.stageSummary(), d == null ? "" : d.compactSummary());
            } catch (RuntimeException ex) {
                System.out.printf(Locale.US, "%d,0,0,0,0,0,ERROR%n", level);
            }
        }
    }
}
