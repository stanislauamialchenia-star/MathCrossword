package com.offline.mathcrossword;

/** Strategy-specific quality definition. Exact math remains shared. */
interface StrategyEvaluator {
    boolean acceptsSignature(LogicAnalyzer.Metrics m, HumanSolver.Metrics h, int requestedLogic);
    boolean acceptsDifficulty(LogicAnalyzer.Metrics m, HumanSolver.Metrics h, int logicLevel);
    int bonus(LogicAnalyzer.Metrics m, HumanSolver.Metrics h);
    int estimateLevel(LogicAnalyzer.Metrics m, HumanSolver.Metrics h);
}

final class StrategyEvaluators {
    private StrategyEvaluators() { }

    static StrategyEvaluator forStrategy(SolutionStrategy s) {
        if (s == SolutionStrategy.DEDUCTION) return DeductionEvaluator.INSTANCE;
        if (s == SolutionStrategy.CHAIN) return ChainEvaluator.INSTANCE;
        if (s == SolutionStrategy.HYPOTHESIS) return HypothesisEvaluator.INSTANCE;
        if (s == SolutionStrategy.NETWORK) return NetworkEvaluator.INSTANCE;
        return null;
    }

    private static final class DeductionEvaluator implements StrategyEvaluator {
        static final DeductionEvaluator INSTANCE = new DeductionEvaluator();
        public boolean acceptsSignature(LogicAnalyzer.Metrics m, HumanSolver.Metrics h, int l) {
            if (l <= 2) return true;
            if (l == 3) return m.crossHidden >= 1 || m.intersectionTightening >= 1;
            if (l == 4) {
                return m.intersectionTightening >= 1 && m.crossHidden >= 3
                        && h.initialAverageDomain >= 2.0
                        && h.maxReasoningDepth <= 1 && h.depth2Deductions == 0;
            }
            return m.intersectionTightening >= 2 && m.crossHidden >= 4
                    && h.initialAverageDomain >= 2.35
                    && h.maxReasoningDepth <= 1 && h.depth2Deductions == 0;
        }
        public boolean acceptsDifficulty(LogicAnalyzer.Metrics m, HumanSolver.Metrics h, int l) {
            if (l <= 3) return LogicAnalyzer.acceptForLevel(m, h, l);
            if (l == 4) {
                return m.hidden >= 8 && m.ambiguousEquations >= 4 && m.crossHidden >= 3
                        && m.intersectionTightening >= 1 && m.singletons <= 1
                        && h.initialSingletons <= 1 && h.initialAverageDomain >= 2.10
                        && h.basicRemaining >= Math.max(6, (m.hidden * 2) / 3)
                        && h.maxReasoningDepth <= 1
                        && h.maxForcedCascade <= Math.max(4, m.hidden / 2)
                        && (h.lookaheadDeductions >= 1 || h.reasoningSteps >= 2
                            || h.stuckRemaining >= Math.max(4, m.hidden / 3));
            }
            return m.hidden >= 10 && m.ambiguousEquations >= 5 && m.crossHidden >= 4
                    && m.intersectionTightening >= 2 && m.singletons == 0
                    && h.initialSingletons == 0 && h.basicForced <= 1
                    && h.initialAverageDomain >= 2.45
                    && h.basicRemaining >= Math.max(8, (m.hidden * 3) / 4)
                    && h.maxReasoningDepth <= 1 && h.depth2Deductions == 0
                    && h.maxForcedCascade <= Math.max(5, (m.hidden * 3) / 5)
                    && (h.lookaheadDeductions >= 2 || h.reasoningSteps >= 3
                        || h.stuckRemaining >= Math.max(6, m.hidden / 2));
        }
        public int bonus(LogicAnalyzer.Metrics m, HumanSolver.Metrics h) {
            return m.intersectionTightening * 90 + m.crossHidden * 28
                    + h.lookaheadDeductions * 55 + h.lookaheadEliminations * 7
                    - h.depth2Deductions * 160 - h.maxReasoningDepth * 20;
        }
        public int estimateLevel(LogicAnalyzer.Metrics m, HumanSolver.Metrics h) {
            if (acceptsDifficulty(m, h, 5)) return 5;
            if (acceptsDifficulty(m, h, 4)) return 4;
            if (LogicAnalyzer.acceptForLevel(m, h, 3)) return 3;
            if (LogicAnalyzer.acceptForLevel(m, h, 2)) return 2;
            return 1;
        }
    }

    private static final class ChainEvaluator implements StrategyEvaluator {
        static final ChainEvaluator INSTANCE = new ChainEvaluator();
        public boolean acceptsSignature(LogicAnalyzer.Metrics m, HumanSolver.Metrics h, int l) {
            if (l <= 2) return true;
            if (l == 3) return h.basicForced >= 1 || h.reasoningSteps >= 1;
            int cascade = Math.max(4, m.hidden / 2);
            return (h.maxForcedCascade >= cascade || h.reasoningSteps >= (l >= 5 ? 2 : 1))
                    && h.basicRemaining >= Math.max(4, m.hidden / 3)
                    && m.cycleRank <= (l >= 5 ? 5 : 4);
        }
        public boolean acceptsDifficulty(LogicAnalyzer.Metrics m, HumanSolver.Metrics h, int l) {
            if (l <= 3) return LogicAnalyzer.acceptForLevel(m, h, l);
            int cascadeTarget = l >= 5 ? Math.max(6, (m.hidden * 3) / 5) : Math.max(4, m.hidden / 2);
            int remainingTarget = l >= 5 ? Math.max(7, (m.hidden * 2) / 3) : Math.max(5, m.hidden / 2);
            boolean entryNotTrivial = h.initialBranchCells >= (l >= 5 ? 5 : 3)
                    && h.initialAverageDomain >= (l >= 5 ? 2.0 : 1.75);
            boolean longDependency = h.maxForcedCascade >= cascadeTarget
                    || h.reasoningSteps >= (l >= 5 ? 2 : 1);
            return m.hidden >= (l >= 5 ? 10 : 8)
                    && m.ambiguousEquations >= (l >= 5 ? 4 : 3)
                    && h.basicRemaining >= remainingTarget && entryNotTrivial && longDependency;
        }
        public int bonus(LogicAnalyzer.Metrics m, HumanSolver.Metrics h) {
            return h.reasoningSteps * 35 + h.maxForcedCascade * 28 - m.cycleRank * 8;
        }
        public int estimateLevel(LogicAnalyzer.Metrics m, HumanSolver.Metrics h) {
            if (acceptsDifficulty(m,h,5)) return 5;
            if (acceptsDifficulty(m,h,4)) return 4;
            if (LogicAnalyzer.acceptForLevel(m,h,3)) return 3;
            if (LogicAnalyzer.acceptForLevel(m,h,2)) return 2;
            return 1;
        }
    }

    private static final class HypothesisEvaluator implements StrategyEvaluator {
        static final HypothesisEvaluator INSTANCE = new HypothesisEvaluator();
        public boolean acceptsSignature(LogicAnalyzer.Metrics m, HumanSolver.Metrics h, int l) {
            if (l <= 2) return true;
            if (l == 3) return h.initialBranchCells >= 2 && h.maxBranchWidth >= 2;
            if (l == 4) {
                return h.initialBranchCells >= Math.max(4, m.hidden / 3)
                        && h.maxBranchWidth >= 2
                        && (h.lookaheadDeductions > 0 || h.maxReasoningDepth >= 1
                            || h.stuckRemaining >= Math.max(4, m.hidden / 3));
            }
            return h.initialBranchCells >= Math.max(6, m.hidden / 2)
                    && h.maxBranchWidth >= 3
                    && (h.depth2Deductions > 0 || h.maxReasoningDepth >= 2
                        || h.stuckRemaining >= Math.max(6, m.hidden / 2));
        }
        public boolean acceptsDifficulty(LogicAnalyzer.Metrics m, HumanSolver.Metrics h, int l) {
            if (l <= 3) return LogicAnalyzer.acceptForLevel(m, h, l);
            if (l == 4) {
                return m.hidden >= 8 && m.ambiguousEquations >= 4
                        && h.initialSingletons <= 1
                        && h.initialBranchCells >= Math.max(5, m.hidden / 2)
                        && h.initialAverageDomain >= 2.25
                        && h.basicRemaining >= Math.max(6, (m.hidden * 2) / 3)
                        && h.maxBranchWidth >= 2
                        && h.maxForcedCascade <= Math.max(4, m.hidden / 2)
                        && (h.lookaheadDeductions >= 1 || h.maxReasoningDepth >= 1
                            || h.stuckRemaining >= Math.max(4, m.hidden / 3));
            }
            return m.hidden >= 10 && m.ambiguousEquations >= 5
                    && h.initialSingletons == 0 && h.basicForced <= 1
                    && h.initialBranchCells >= Math.max(8, (m.hidden * 2) / 3)
                    && h.initialAverageDomain >= 2.65
                    && h.basicRemaining >= Math.max(8, (m.hidden * 3) / 4)
                    && h.maxBranchWidth >= 3
                    && h.maxForcedCascade <= Math.max(5, (m.hidden * 3) / 5)
                    && (h.depth2Deductions >= 1 || h.maxReasoningDepth >= 2
                        || h.stuckRemaining >= Math.max(7, (m.hidden * 3) / 5));
        }
        public int bonus(LogicAnalyzer.Metrics m, HumanSolver.Metrics h) {
            return h.depth2Deductions * 150 + h.maxReasoningDepth * 105
                    + h.lookaheadDeductions * 45 + h.stuckRemaining * 12
                    + h.maxBranchWidth * 14;
        }
        public int estimateLevel(LogicAnalyzer.Metrics m, HumanSolver.Metrics h) {
            if (acceptsDifficulty(m, h, 5)) return 5;
            if (acceptsDifficulty(m, h, 4)) return 4;
            if (LogicAnalyzer.acceptForLevel(m, h, 3)) return 3;
            return LogicAnalyzer.estimateLevel(m, h);
        }
    }

    private static final class NetworkEvaluator implements StrategyEvaluator {
        static final NetworkEvaluator INSTANCE = new NetworkEvaluator();
        public boolean acceptsSignature(LogicAnalyzer.Metrics m, HumanSolver.Metrics h, int l) {
            if (l <= 2) return true;
            if (l == 3) return m.cycleRank >= 1 || m.crossHidden >= 2;
            return m.cycleRank >= (l >= 5 ? 3 : 1)
                    && m.crossHidden >= (l >= 5 ? 4 : 3)
                    && m.ambiguousEquations >= (l >= 5 ? 5 : 4)
                    && h.initialBranchCells >= Math.max(4, m.hidden / 3);
        }
        public boolean acceptsDifficulty(LogicAnalyzer.Metrics m, HumanSolver.Metrics h, int l) {
            if (l <= 3) return LogicAnalyzer.acceptForLevel(m,h,l);
            if (l == 4) {
                return m.hidden >= 8 && m.cycleRank >= 2 && m.crossHidden >= 4
                        && m.ambiguousEquations >= 5
                        && h.initialBranchCells >= Math.max(5, m.hidden / 2)
                        && h.initialAverageDomain >= 2.30
                        && h.basicRemaining >= Math.max(6, m.hidden / 2)
                        && h.maxForcedCascade <= Math.max(4, m.hidden / 2)
                        && (h.reasoningSteps >= 1 || h.stuckRemaining >= Math.max(4, m.hidden / 3));
            }
            return m.hidden >= 10 && m.cycleRank >= 3 && m.crossHidden >= 5
                    && m.ambiguousEquations >= 6
                    && h.initialBranchCells >= Math.max(8, (m.hidden * 2) / 3)
                    && h.initialAverageDomain >= 2.75 && h.basicForced <= 1
                    && h.basicRemaining >= Math.max(8, (m.hidden * 3) / 4)
                    && h.maxForcedCascade <= Math.max(5, (m.hidden * 3) / 5)
                    && (h.reasoningSteps >= 2 || h.maxReasoningDepth >= 2
                        || h.stuckRemaining >= Math.max(6, m.hidden / 2));
        }
        public int bonus(LogicAnalyzer.Metrics m, HumanSolver.Metrics h) {
            return m.cycleRank * 95 + m.crossHidden * 30 + m.ambiguousEquations * 18;
        }
        public int estimateLevel(LogicAnalyzer.Metrics m, HumanSolver.Metrics h) {
            if (acceptsDifficulty(m,h,5)) return 5;
            if (acceptsDifficulty(m,h,4)) return 4;
            if (LogicAnalyzer.acceptForLevel(m,h,3)) return 3;
            return LogicAnalyzer.estimateLevel(m,h);
        }
    }
}
