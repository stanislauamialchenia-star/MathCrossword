package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Offline benchmark for issue #37.
 *
 * This is deliberately not a player model. It is a deterministic reference CSP
 * search that uses the same finite domains and exact tile inventory as the game:
 * propagate singletons, choose an MRV pivot, branch, and prune contradictions.
 * Production generator behavior is not changed by this tool.
 */
public final class AdversarialMrvBenchmark {
    private static final long SEED_BASE = 0x5A17C9E3D4B26F81L;
    private static final long RETRY_STEP = 0x9E3779B97F4A7C15L;
    private static final int NODE_BUDGET = 200_000;

    private static final Set<Character> OPS = new LinkedHashSet<>(
            Arrays.asList('+', '-', '×', '÷'));

    static final class Metrics {
        int hidden;
        int initialMinDomain;
        int initialMaxDomain;
        double initialAverageDomain;
        int rootForced;

        int decisionNodes;
        int candidateBranches;
        int failedBranches;
        int maxDepth;
        int maxBranchWidth;
        int minDecisionDomain = Integer.MAX_VALUE;
        long decisionDomainTotal;

        int propagationBranches;
        int totalPropagationGain;
        int maxPropagationGain;

        int solutions;
        boolean truncated;

        double averageDecisionWidth() {
            return decisionNodes == 0 ? 0.0 : (double) decisionDomainTotal / decisionNodes;
        }

        double averagePropagationGain() {
            return propagationBranches == 0 ? 0.0 : (double) totalPropagationGain / propagationBranches;
        }

        double proofCostPerHidden() {
            int denom = Math.max(1, hidden);
            return (double) (decisionNodes + candidateBranches + failedBranches) / denom;
        }

        boolean solvedWithoutBranching() {
            return solutions == 1 && decisionNodes == 0 && !truncated;
        }
    }

    static final class SearchContext {
        int nodesLeft = NODE_BUDGET;
    }

    static final class Summary {
        int generated;
        int matched;
        int misses;
        int truncated;
        double allCost;
        double matchedCost;
        double matchedDecisions;
        double matchedDepth;
        double matchedPropagation;

        void add(Puzzle p, Metrics m, boolean isMatched) {
            generated++;
            if (m.truncated) truncated++;
            allCost += m.proofCostPerHidden();
            if (isMatched) {
                matched++;
                matchedCost += m.proofCostPerHidden();
                matchedDecisions += m.decisionNodes;
                matchedDepth += m.maxDepth;
                matchedPropagation += m.averagePropagationGain();
            }
        }
    }

    public static void main(String[] args) {
        int samples = args.length > 0 ? Math.max(1, Integer.parseInt(args[0])) : 2;
        int[] logicLevels = {8, 10};
        SolutionStrategy[] strategies = {
                SolutionStrategy.CHAIN,
                SolutionStrategy.NETWORK,
                SolutionStrategy.DEDUCTION,
                SolutionStrategy.HYPOTHESIS,
                SolutionStrategy.MIXED
        };

        Map<String, Summary> summaries = new LinkedHashMap<>();
        int totalGenerated = 0;
        int totalMisses = 0;
        int totalTruncated = 0;

        for (int logic : logicLevels) {
            for (SolutionStrategy strategy : strategies) {
                String key = strategy.name() + "-L" + logic;
                Summary summary = new Summary();
                summaries.put(key, summary);

                for (int sample = 0; sample < samples; sample++) {
                    Puzzle p = null;
                    int usedRetry = -1;
                    long usedSeed = 0L;
                    long baseSeed = mix64(SEED_BASE
                            ^ ((long) logic << 40)
                            ^ ((long) strategy.ordinal() << 28)
                            ^ (long) sample * 0xD6E8FEB86659FD93L);

                    for (int retry = 0; retry < 3 && p == null; retry++) {
                        long seed = mix64(baseSeed + retry * RETRY_STEP);
                        try {
                            p = PuzzleGenerator.generateFree(logic, 10, 1, 100, OPS, seed, strategy);
                            usedRetry = retry;
                            usedSeed = seed;
                        } catch (IllegalStateException ignored) {
                            // Availability is part of the benchmark. Keep the miss visible.
                        }
                    }

                    if (p == null) {
                        summary.misses++;
                        totalMisses++;
                        System.out.printf(
                                "MISS strategy=%s logic=%d sample=%d retries=3%n",
                                strategy, logic, sample);
                        continue;
                    }

                    Metrics metrics = analyze(p);
                    boolean matched = p.strategyTargetMatched && p.generationStrategy == strategy;
                    summary.add(p, metrics, matched);
                    totalGenerated++;
                    if (metrics.truncated) totalTruncated++;

                    System.out.printf(
                            "CASE strategy=%s logic=%d sample=%d retry=%d seed=%d "
                                    + "matched=%s generationStrategy=%s stage=%d family=%s ctor=%s "
                                    + "hidden=%d initialMin=%d initialAvg=%.2f initialMax=%d rootForced=%d "
                                    + "decisions=%d candidateBranches=%d failedBranches=%d maxDepth=%d "
                                    + "minDecisionDomain=%d avgDecisionWidth=%.2f maxBranchWidth=%d "
                                    + "propagationBranches=%d avgPropagationGain=%.2f maxPropagationGain=%d "
                                    + "proofCostPerHidden=%.2f solutions=%d branchless=%s truncated=%s "
                                    + "generationMs=%d attempts=%d rejects=%d%n",
                            strategy, logic, sample, usedRetry, usedSeed,
                            matched, p.generationStrategy, p.generationStage, p.generatorFamily, p.generatorConstructor,
                            metrics.hidden, metrics.initialMinDomain, metrics.initialAverageDomain,
                            metrics.initialMaxDomain, metrics.rootForced,
                            metrics.decisionNodes, metrics.candidateBranches, metrics.failedBranches, metrics.maxDepth,
                            metrics.minDecisionDomain == Integer.MAX_VALUE ? 0 : metrics.minDecisionDomain,
                            metrics.averageDecisionWidth(), metrics.maxBranchWidth,
                            metrics.propagationBranches, metrics.averagePropagationGain(), metrics.maxPropagationGain,
                            metrics.proofCostPerHidden(), metrics.solutions,
                            metrics.solvedWithoutBranching(), metrics.truncated,
                            p.generationMillis, p.generationAttempts, p.generationRejects);
                }
            }
        }

        System.out.println("=== MRV ADVERSARIAL SUMMARY ===");
        for (Map.Entry<String, Summary> e : summaries.entrySet()) {
            Summary s = e.getValue();
            double avgAll = s.generated == 0 ? 0.0 : s.allCost / s.generated;
            double avgMatched = s.matched == 0 ? 0.0 : s.matchedCost / s.matched;
            double avgDecisions = s.matched == 0 ? 0.0 : s.matchedDecisions / s.matched;
            double avgDepth = s.matched == 0 ? 0.0 : s.matchedDepth / s.matched;
            double avgPropagation = s.matched == 0 ? 0.0 : s.matchedPropagation / s.matched;
            System.out.printf(
                    "SUMMARY bucket=%s generated=%d matched=%d misses=%d truncated=%d "
                            + "avgAllProofCost=%.2f avgMatchedProofCost=%.2f "
                            + "avgMatchedDecisions=%.2f avgMatchedDepth=%.2f avgMatchedPropagationGain=%.2f%n",
                    e.getKey(), s.generated, s.matched, s.misses, s.truncated,
                    avgAll, avgMatched, avgDecisions, avgDepth, avgPropagation);
        }

        System.out.printf(
                "TOTAL generated=%d misses=%d truncated=%d samplesPerBucket=%d%n",
                totalGenerated, totalMisses, totalTruncated, samples);

        if (totalGenerated == 0) throw new AssertionError("No puzzles generated for MRV benchmark");
        if (totalTruncated > 0) throw new AssertionError("MRV benchmark exhausted node budget");
    }

    static Metrics analyze(Puzzle p) {
        Metrics m = new Metrics();
        m.hidden = p.hidden.size();

        HumanSolver.State initialState = HumanSolver.initialState(p);
        Map<Pos, Set<Integer>> initialDomains = HumanSolver.allDomains(p, initialState);
        int min = Integer.MAX_VALUE;
        int max = 0;
        long sum = 0;
        for (Set<Integer> domain : initialDomains.values()) {
            int size = domain.size();
            min = Math.min(min, size);
            max = Math.max(max, size);
            sum += size;
        }
        m.initialMinDomain = min == Integer.MAX_VALUE ? 0 : min;
        m.initialMaxDomain = max;
        m.initialAverageDomain = initialDomains.isEmpty()
                ? 0.0 : (double) sum / initialDomains.size();

        HumanSolver.State root = new HumanSolver.State(initialState);
        HumanSolver.Propagation rootPropagation = HumanSolver.propagateSingles(p, root);
        m.rootForced = rootPropagation.forced;
        if (rootPropagation.contradiction || !HumanSolver.allLocallyPossible(p, root)) {
            m.solutions = 0;
            return m;
        }

        SearchContext context = new SearchContext();
        m.solutions = explore(p, root, 0, m, context);
        return m;
    }

    static int explore(Puzzle p, HumanSolver.State state, int depth,
                       Metrics metrics, SearchContext context) {
        if (context.nodesLeft-- <= 0) {
            metrics.truncated = true;
            return 0;
        }

        if (state.assigned.size() == p.hidden.size()) {
            return HumanSolver.allLocallyPossible(p, state) ? 1 : 0;
        }

        Map<Pos, Set<Integer>> domains = HumanSolver.allDomains(p, state);
        Pos pivot = null;
        Set<Integer> pivotDomain = null;

        List<Map.Entry<Pos, Set<Integer>>> ordered = new ArrayList<>(domains.entrySet());
        ordered.sort(Comparator
                .comparingInt((Map.Entry<Pos, Set<Integer>> e) -> e.getValue().size())
                .thenComparingInt(e -> e.getKey().x)
                .thenComparingInt(e -> e.getKey().y));

        for (Map.Entry<Pos, Set<Integer>> entry : ordered) {
            if (entry.getValue().isEmpty()) return 0;
            if (pivot == null) {
                pivot = entry.getKey();
                pivotDomain = entry.getValue();
            }
        }
        if (pivot == null || pivotDomain == null || pivotDomain.isEmpty()) return 0;

        metrics.decisionNodes++;
        metrics.maxDepth = Math.max(metrics.maxDepth, depth + 1);
        metrics.maxBranchWidth = Math.max(metrics.maxBranchWidth, pivotDomain.size());
        metrics.minDecisionDomain = Math.min(metrics.minDecisionDomain, pivotDomain.size());
        metrics.decisionDomainTotal += pivotDomain.size();

        List<Integer> candidates = new ArrayList<>(pivotDomain);
        candidates.sort(Integer::compareTo);

        int solutions = 0;
        for (int value : candidates) {
            if (metrics.truncated) break;
            metrics.candidateBranches++;

            HumanSolver.State child = new HumanSolver.State(state);
            if (!HumanSolver.assign(child, pivot, value)) {
                metrics.failedBranches++;
                continue;
            }

            HumanSolver.Propagation propagation = HumanSolver.propagateSingles(p, child);
            metrics.propagationBranches++;
            metrics.totalPropagationGain += propagation.forced;
            metrics.maxPropagationGain = Math.max(metrics.maxPropagationGain, propagation.forced);

            if (propagation.contradiction || !HumanSolver.allLocallyPossible(p, child)) {
                metrics.failedBranches++;
                continue;
            }

            int before = solutions;
            int found = explore(p, child, depth + 1, metrics, context);
            solutions = Math.min(2, solutions + found);
            if (found == 0 && !metrics.truncated) metrics.failedBranches++;
            if (solutions >= 2) break;
            if (solutions == before && metrics.truncated) break;
        }
        return solutions;
    }

    static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
