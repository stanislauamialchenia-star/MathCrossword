package com.offline.mathcrossword;

final class GeneratorPolicy {
    private GeneratorPolicy() { }

    static int equationDelta(SolutionStrategy strategy, int logic) {
        if (strategy == null) return 0;
        switch (strategy) {
            case NETWORK: return logic >= 5 ? 1 : 0;
            case HYPOTHESIS: return logic >= 4 ? 1 : 0;
            case CHAIN: return 1;
            default: return 0;
        }
    }

    static int hiddenDelta(SolutionStrategy strategy, int logic) {
        if (strategy == null) return 0;
        switch (strategy) {
            case NETWORK: return 0;
            case HYPOTHESIS: return logic >= 4 ? 2 : 1;
            case DEDUCTION: return logic >= 4 ? 1 : 0;
            case CHAIN: return 0;
            default: return 0;
        }
    }

    static int shapeStyle(long seed, int attempt, SolutionStrategy strategy) {
        int base = (int) Math.floorMod(PuzzleGenerator.mix64(seed + attempt * 0x9E3779B97F4A7C15L), 18);
        if (strategy == null) return base;
        switch (strategy) {
            case CHAIN:
                // Families 0..8 are more often branch/tree-like in the current geometry builder.
                return Math.floorMod(base + (attempt % 3), 9);
            case NETWORK:
                return 9 + Math.floorMod(base + attempt * 2, 9);
            case HYPOTHESIS:
                return 9 + Math.floorMod(base + attempt * 5, 9);
            case DEDUCTION:
                return Math.floorMod(base + attempt * 7, 18);
            default:
                return base;
        }
    }

    static boolean useLattice(SolutionStrategy strategy, int logicLevel, long seed) {
        if (logicLevel < 4) return false;
        if (strategy == null || strategy == SolutionStrategy.MIXED) return true;
        switch (strategy) {
            case CHAIN: return logicLevel >= 4 && (seed & 1L) == 0L;
            case NETWORK: return true;
            case HYPOTHESIS: return true;
            case DEDUCTION: return true;
            default: return true;
        }
    }

    // Small deterministic budgets by design. Frequent fallback is treated as a
    // generator-quality signal, not something to hide with hundreds of retries.
    static int strictAttempts(SolutionStrategy strategy, int logicLevel) {
        if (logicLevel <= 3) return 60;
        // v10: constructive builders make a small retry increase affordable.
        // We still keep the budget deliberately bounded so weaknesses stay visible.
        // v12 spends part of the hidden/uniqueness speedup on reliability: one extra
        // constructive attempt is still cheaper than v11's old hidden search.
        if (strategy == SolutionStrategy.NETWORK) return logicLevel >= 5 ? 5 : 4;
        if (strategy == SolutionStrategy.CHAIN) return logicLevel >= 5 ? 5 : 4;
        if (strategy == SolutionStrategy.HYPOTHESIS) return logicLevel >= 5 ? 5 : 4;
        if (strategy == SolutionStrategy.MIXED && logicLevel >= 5) return 8;
        return 4;
    }

    static int stableFallbackAttempts(SolutionStrategy strategy, int logicLevel) {
        if (logicLevel <= 3 || strategy == SolutionStrategy.MIXED) return 0;
        if (strategy == SolutionStrategy.NETWORK) return 2;
        if (strategy == SolutionStrategy.CHAIN) return 2;
        return 2;
    }

    static boolean accepts(SolutionStrategy strategy,
                           LogicAnalyzer.Metrics logic,
                           HumanSolver.Metrics human,
                           int requestedLogic) {
        if (strategy == null || strategy == SolutionStrategy.MIXED || requestedLogic <= 2) return true;
        if (logic == null || human == null) return false;
        StrategyEvaluator evaluator = StrategyEvaluators.forStrategy(strategy);
        if (evaluator != null) return evaluator.acceptsSignature(logic, human, requestedLogic);

        if (requestedLogic == 3) {
            switch (strategy) {
                case DEDUCTION:
                    return logic.crossHidden >= 1 || logic.intersectionTightening >= 1;
                case CHAIN:
                    return human.basicForced >= 1 || human.reasoningSteps >= 1;
                case HYPOTHESIS:
                    return human.initialBranchCells >= 2 && human.maxBranchWidth >= 2;
                case NETWORK:
                    return logic.cycleRank >= 1 || logic.crossHidden >= 2;
                default:
                    return true;
            }
        }

        switch (strategy) {
            case DEDUCTION:
                if (requestedLogic >= 5) {
                    return logic.intersectionTightening >= 2
                            && logic.crossHidden >= 4
                            && human.initialAverageDomain >= 2.4
                            && human.depth2Deductions == 0
                            && human.maxReasoningDepth <= 1;
                }
                return logic.intersectionTightening >= 1
                        && logic.crossHidden >= 2
                        && human.initialAverageDomain >= 1.9
                        && human.depth2Deductions <= 1;

            case CHAIN:
                // Chain mode is intentionally different from the other hard modes:
                // it may contain a long forced cascade after the correct entry point
                // is found. That is the mechanism being trained here, not a defect.
                int strongCascade = Math.max(4, logic.hidden / 2);
                boolean chainSignature = human.maxForcedCascade >= strongCascade
                        || human.reasoningSteps >= (requestedLogic >= 5 ? 2 : 1);
                return chainSignature
                        && human.basicRemaining >= Math.max(4, logic.hidden / 3)
                        && logic.cycleRank <= (requestedLogic >= 5 ? 5 : 4);

            case HYPOTHESIS:
                if (requestedLogic >= 5) {
                    return (human.depth2Deductions > 0 || human.maxReasoningDepth >= 2
                            || human.stuckRemaining >= Math.max(6, logic.hidden / 2))
                            && human.initialBranchCells >= Math.max(5, logic.hidden / 2)
                            && human.maxBranchWidth >= 3;
                }
                return (human.lookaheadDeductions > 0 || human.maxReasoningDepth >= 1
                        || human.stuckRemaining >= Math.max(4, logic.hidden / 3))
                        && human.initialBranchCells >= Math.max(3, logic.hidden / 3)
                        && human.maxBranchWidth >= 2;

            case NETWORK:
                return logic.cycleRank >= (requestedLogic >= 5 ? 3 : 1)
                        && logic.crossHidden >= (requestedLogic >= 5 ? 4 : 3)
                        && logic.ambiguousEquations >= (requestedLogic >= 5 ? 5 : 4)
                        && human.initialBranchCells >= Math.max(4, logic.hidden / 3);

            default:
                return true;
        }
    }

    static int bonus(SolutionStrategy strategy,
                     LogicAnalyzer.Metrics logic,
                     HumanSolver.Metrics human) {
        if (strategy == null || strategy == SolutionStrategy.MIXED || logic == null || human == null) return 0;
        StrategyEvaluator evaluator = StrategyEvaluators.forStrategy(strategy);
        if (evaluator != null) return evaluator.bonus(logic, human);
        switch (strategy) {
            case DEDUCTION:
                return logic.intersectionTightening * 55 + logic.crossHidden * 20
                        + human.lookaheadDeductions * 18 - human.depth2Deductions * 8;
            case CHAIN:
                return human.reasoningSteps * 35 + human.maxForcedCascade * 28
                        - logic.cycleRank * 8;
            case HYPOTHESIS:
                return human.depth2Deductions * 130 + human.maxReasoningDepth * 90
                        + human.stuckRemaining * 10 + human.maxBranchWidth * 12;
            case NETWORK:
                return logic.cycleRank * 95 + logic.crossHidden * 30
                        + logic.ambiguousEquations * 18;
            default:
                return 0;
        }
    }

    static boolean staticPrefilter(SolutionStrategy strategy, LogicAnalyzer.Metrics m, int logicLevel) {
        if (logicLevel <= 3) return true;
        if (strategy == SolutionStrategy.CHAIN) {
            if (logicLevel == 4) {
                return m.hidden >= 8
                        && m.ambiguousEquations >= 3
                        && m.crossHidden >= 1
                        && m.singletons <= 3
                        && m.directSingleCells <= 2
                        && m.averageDomain >= 1.65;
            }
            return m.hidden >= 10
                    && m.ambiguousEquations >= 4
                    && m.crossHidden >= 1
                    && m.singletons <= 2
                    && m.directSingleCells <= 2
                    && m.averageDomain >= 1.85;
        }
        if (strategy == SolutionStrategy.HYPOTHESIS) {
            // A hypothesis is about branch viability, not graph cycles. Keep only
            // the cheap ambiguity prerequisites here and let HumanSolver measure
            // whether contradiction/lookahead is actually required.
            if (logicLevel == 4) {
                return m.hidden >= 8 && m.ambiguousEquations >= 4
                        && m.crossHidden >= 2 && m.singletons <= 2
                        && m.directSingleCells <= 2 && m.averageDomain >= 2.0;
            }
            return m.hidden >= 10 && m.ambiguousEquations >= 5
                    && m.crossHidden >= 2 && m.singletons <= 1
                    && m.directSingleCells <= 1 && m.averageDomain >= 2.30;
        }
        return LogicAnalyzer.cheapStaticPrefilter(m, logicLevel);
    }

    static boolean acceptsDifficulty(SolutionStrategy strategy,
                                     LogicAnalyzer.Metrics m,
                                     HumanSolver.Metrics h,
                                     int logicLevel) {
        if (logicLevel <= 3) return LogicAnalyzer.acceptForLevel(m, h, logicLevel);
        StrategyEvaluator evaluator = StrategyEvaluators.forStrategy(strategy);
        if (evaluator != null) return evaluator.acceptsDifficulty(m, h, logicLevel);
        if (strategy == SolutionStrategy.NETWORK) {
            if (logicLevel == 4) {
                return m.hidden >= 8
                        && m.cycleRank >= 2
                        && m.crossHidden >= 4
                        && m.ambiguousEquations >= 5
                        && h.initialBranchCells >= Math.max(5, m.hidden / 2)
                        && h.initialAverageDomain >= 2.30
                        && h.basicRemaining >= Math.max(6, m.hidden / 2)
                        && (h.reasoningSteps >= 1 || h.stuckRemaining >= Math.max(4, m.hidden / 3));
            }
            return m.hidden >= 10
                    && m.cycleRank >= 3
                    && m.crossHidden >= 5
                    && m.ambiguousEquations >= 6
                    && h.initialBranchCells >= Math.max(8, (m.hidden * 2) / 3)
                    && h.initialAverageDomain >= 2.75
                    && h.basicForced <= 1
                    && h.basicRemaining >= Math.max(8, (m.hidden * 3) / 4)
                    && (h.reasoningSteps >= 2 || h.maxReasoningDepth >= 2
                        || h.stuckRemaining >= Math.max(6, m.hidden / 2));
        }
        if (strategy != SolutionStrategy.CHAIN) return LogicAnalyzer.acceptForLevel(m, h, logicLevel);
        int cascadeTarget = logicLevel >= 5 ? Math.max(6, (m.hidden * 3) / 5) : Math.max(4, m.hidden / 2);
        int remainingTarget = logicLevel >= 5 ? Math.max(7, (m.hidden * 2) / 3) : Math.max(5, m.hidden / 2);
        boolean entryNotTrivial = h.initialBranchCells >= (logicLevel >= 5 ? 5 : 3)
                && h.initialAverageDomain >= (logicLevel >= 5 ? 2.0 : 1.75);
        boolean longDependency = h.maxForcedCascade >= cascadeTarget
                || h.reasoningSteps >= (logicLevel >= 5 ? 2 : 1);
        return m.hidden >= (logicLevel >= 5 ? 10 : 8)
                && m.ambiguousEquations >= (logicLevel >= 5 ? 4 : 3)
                && h.basicRemaining >= remainingTarget
                && entryNotTrivial
                && longDependency;
    }

    static boolean acceptsHypothesisKernel(LogicAnalyzer.Metrics m, HumanSolver.Metrics h, int logicLevel) {
        if (logicLevel < 5 || m == null || h == null) return false;
        return m.hidden >= 10
                && m.ambiguousEquations >= 5
                && h.initialSingletons == 0
                && h.basicForced <= 1
                && h.initialBranchCells >= Math.max(6, m.hidden / 2)
                && h.initialAverageDomain >= 2.20
                && h.basicRemaining >= Math.max(7, (m.hidden * 2) / 3);
    }

    static int hiddenQualityBonus(SolutionStrategy strategy,
                                  LogicAnalyzer.Metrics m,
                                  HumanSolver.Metrics h,
                                  int logicLevel) {
        if (strategy == SolutionStrategy.NETWORK) {
            // A network is not just a cyclic picture. The uncertainty must survive
            // basic propagation after the first useful entry. v22 over-rewarded
            // graph density and repeatedly selected masks that were structurally
            // network-like but collapsed in one long forced cascade. Keep the
            // structural rewards, but make the score reflect the same anti-collapse
            // properties that the Network difficulty evaluator ultimately requires.
            int score = m.cycleRank * 75 + m.crossHidden * 24 + m.ambiguousEquations * 16
                    + h.initialBranchCells * 8 + h.reasoningSteps * 35;
            score += Math.min(h.basicRemaining, m.hidden) * 30;
            score -= h.basicForced * 70;
            score -= h.maxForcedCascade * 55;
            // Selection is best-of-N. Once a sampled mask actually satisfies the
            // requested NETWORK tier, never let a prettier-but-collapsing mask
            // replace it merely because its structural score is numerically larger.
            if (acceptsDifficulty(SolutionStrategy.NETWORK, m, h, logicLevel)) score += 10_000;
            return score;
        }
        if (strategy == SolutionStrategy.HYPOTHESIS) {
            // Hidden-mask selection must prefer uncertainty that survives basic
            // propagation. Otherwise a board may look ambiguous at t=0 but one
            // singleton opens the entire puzzle, which is a Chain-like cascade,
            // not a hypothesis task.
            return h.depth2Deductions * 220 + h.maxReasoningDepth * 150
                    + h.lookaheadDeductions * 95 + h.lookaheadEliminations * 12
                    + h.initialBranchCells * 10 + h.maxBranchWidth * 10
                    + Math.min(h.basicRemaining, m.hidden) * 18
                    - h.basicForced * 28 - h.maxForcedCascade * 24;
        }
        if (strategy == SolutionStrategy.DEDUCTION) {
            return m.intersectionTightening * 60 + m.crossHidden * 20
                    + h.lookaheadDeductions * 45 - h.depth2Deductions * 100;
        }
        if (strategy != SolutionStrategy.CHAIN) return 0;
        int bonus = h.maxForcedCascade * 55 + h.reasoningSteps * 45;
        bonus += Math.min(h.basicRemaining, m.hidden) * 12;
        bonus += h.initialBranchCells * 8;
        bonus -= m.cycleRank * 20;
        return bonus;
    }

    /** Cheap bank-independent rejection of hidden masks that cannot possibly express
     * the requested strategy. Runs before TileBankBuilder/HumanSolver. */
    static boolean hiddenTopologyPrefilter(PuzzleModel.Puzzle p, SolutionStrategy strategy, int logicLevel) {
        if (p == null || logicLevel <= 3) return true;
        strategy = strategy == null ? SolutionStrategy.MIXED : strategy;

        int ambiguous = 0;
        int direct = 0;
        for (PuzzleModel.Equation e : p.equations) {
            int n = 0;
            if (p.hidden.contains(e.a)) n++;
            if (p.hidden.contains(e.b)) n++;
            if (p.hidden.contains(e.c)) n++;
            if (n >= 2) ambiguous++;
            else if (n == 1) direct++;
        }
        java.util.Map<PuzzleModel.Pos,Integer> degree = PuzzleGenerator.numberDegrees(p);
        int cross = 0;
        for (PuzzleModel.Pos q : p.hidden) if (degree.getOrDefault(q, 1) >= 2) cross++;
        int cycles = LogicAnalyzer.hiddenConstraintCycleRank(p);

        if (strategy == SolutionStrategy.CHAIN) {
            return p.hidden.size() >= (logicLevel >= 5 ? 10 : 8)
                    && ambiguous >= (logicLevel >= 5 ? 4 : 3)
                    && cross >= 1 && direct <= (logicLevel >= 5 ? 4 : 5);
        }
        if (strategy == SolutionStrategy.NETWORK) {
            // Match the eventual Network evaluator before paying for tile-bank and
            // HumanSolver work. A mask with too few hidden cycles cannot recover
            // later no matter how good its arithmetic values are.
            return p.hidden.size() >= (logicLevel >= 5 ? 10 : 8)
                    && ambiguous >= (logicLevel >= 5 ? 5 : 4)
                    && cross >= (logicLevel >= 5 ? 4 : 3)
                    && cycles >= (logicLevel >= 5 ? 3 : 2)
                    && direct <= (logicLevel >= 5 ? 4 : 3);
        }
        if (strategy == SolutionStrategy.DEDUCTION) {
            return p.hidden.size() >= (logicLevel >= 5 ? 10 : 8)
                    && ambiguous >= (logicLevel >= 5 ? 4 : 4)
                    && cross >= (logicLevel >= 5 ? 3 : 3)
                    && cycles >= 1
                    && direct <= (logicLevel >= 5 ? 3 : 2);
        }
        if (strategy == SolutionStrategy.HYPOTHESIS) {
            // Hypothesis does not intrinsically require a cycle. Requiring one
            // here was an accidental Network bias and discarded many legitimate
            // branch-and-contradiction masks before HumanSolver could judge them.
            // The strategy evaluator remains strict about branch width/depth.
            return p.hidden.size() >= (logicLevel >= 5 ? 10 : 8)
                    && ambiguous >= 4
                    && cross >= 2
                    && direct <= 3;
        }
        return p.hidden.size() >= (logicLevel >= 5 ? 10 : 8)
                && ambiguous >= (logicLevel >= 5 ? 5 : 4)
                && cross >= (logicLevel >= 5 ? 4 : 3)
                && cycles >= (logicLevel >= 5 ? 2 : 1)
                && direct <= (logicLevel >= 5 ? 2 : 3);
    }

    /** Minimum number of hidden-set samples before an already on-target candidate may stop the search.
     * Network gets a smaller budget because hidden/uniqueness is its dominant cost; quality is still
     * guarded by the strategy-specific evaluator and exact uniqueness check. */
    static int hiddenSearchMinimumSamples(SolutionStrategy strategy, int logicLevel) {
        if (logicLevel <= 2) return 1;
        if (strategy == SolutionStrategy.NETWORK) return logicLevel >= 5 ? 8 : 6;
        if (strategy == SolutionStrategy.CHAIN) return logicLevel >= 5 ? 10 : 8;
        // v13: Deduction/Hypothesis now have their own evaluators, so once a
        // candidate actually satisfies the requested strategy and band we no
        // longer need the old generic 10-12 sample safety margin.
        if (strategy == SolutionStrategy.DEDUCTION) return logicLevel >= 5 ? 7 : 5;
        if (strategy == SolutionStrategy.HYPOTHESIS) return logicLevel >= 5 ? 7 : 5;
        return logicLevel >= 5 ? 10 : 8;
    }

    static boolean mayStopHiddenSearch(SolutionStrategy strategy, int logicLevel, int samplesSeen,
                                       boolean targetAccepted, LogicAnalyzer.Metrics m, HumanSolver.Metrics h) {
        if (!targetAccepted || m == null || h == null) return false;
        if (samplesSeen < hiddenSearchMinimumSamples(strategy, logicLevel)) return false;
        // Require the candidate to be rated at least at the requested band. This keeps the early stop
        // from trading generation speed for mislabeled difficulty.
        return estimateLevel(strategy, m, h) >= logicLevel;
    }

    static int estimateLevel(SolutionStrategy strategy, LogicAnalyzer.Metrics m, HumanSolver.Metrics h) {
        StrategyEvaluator evaluator = StrategyEvaluators.forStrategy(strategy);
        if (evaluator != null) return evaluator.estimateLevel(m, h);
        if (strategy == SolutionStrategy.NETWORK) {
            if (acceptsDifficulty(strategy, m, h, 5)) return 5;
            if (acceptsDifficulty(strategy, m, h, 4)) return 4;
            if (LogicAnalyzer.acceptForLevel(m, h, 3)) return 3;
            return LogicAnalyzer.estimateLevel(m, h);
        }
        if (strategy != SolutionStrategy.CHAIN) return LogicAnalyzer.estimateLevel(m, h);
        if (acceptsDifficulty(strategy, m, h, 5)) return 5;
        if (acceptsDifficulty(strategy, m, h, 4)) return 4;
        if (LogicAnalyzer.acceptForLevel(m, h, 3)) return 3;
        if (LogicAnalyzer.acceptForLevel(m, h, 2)) return 2;
        return 1;
    }
}
