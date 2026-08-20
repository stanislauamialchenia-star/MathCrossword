package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Diagnosis-only frontier probe for issues #39 and #41. */
public final class ChainAdversarialFrontierProbe {
    private static final long SEED_BASE = 0x43A19D2E7B5C680FL;
    private static final long RETRY_STEP = 0x9E3779B97F4A7C15L;
    private static final Set<Character> OPS = new LinkedHashSet<>(
            Arrays.asList('+', '-', '×', '÷'));

    static final class Row {
        final int sample;
        final int retry;
        final long seed;
        final Puzzle puzzle;
        final AdversarialMrvBenchmark.Metrics metrics;
        final CascadeResilienceAnalyzer.Profile cascade;
        final boolean matched;

        Row(int sample, int retry, long seed, Puzzle puzzle,
            AdversarialMrvBenchmark.Metrics metrics,
            CascadeResilienceAnalyzer.Profile cascade,
            boolean matched) {
            this.sample = sample;
            this.retry = retry;
            this.seed = seed;
            this.puzzle = puzzle;
            this.metrics = metrics;
            this.cascade = cascade;
            this.matched = matched;
        }
    }

    public static void main(String[] args) {
        int samples = args.length > 0 ? Math.max(4, Integer.parseInt(args[0])) : 24;
        List<Row> rows = new ArrayList<>();
        int misses = 0;
        int fallbacks = 0;

        for (int sample = 0; sample < samples; sample++) {
            Puzzle p = null;
            int usedRetry = -1;
            long usedSeed = 0L;
            long baseSeed = mix64(SEED_BASE ^ ((long) sample * 0xD6E8FEB86659FD93L));

            for (int retry = 0; retry < 3 && p == null; retry++) {
                long seed = mix64(baseSeed + retry * RETRY_STEP);
                try {
                    p = PuzzleGenerator.generateFree(
                            10, 10, 1, 100, OPS, seed, SolutionStrategy.CHAIN);
                    usedRetry = retry;
                    usedSeed = seed;
                } catch (IllegalStateException ignored) {
                    // Preserve availability pressure rather than hiding it.
                }
            }

            if (p == null) {
                misses++;
                System.out.printf("MISS sample=%d retries=3%n", sample);
                continue;
            }

            AdversarialMrvBenchmark.Metrics m = AdversarialMrvBenchmark.analyze(p);
            CascadeResilienceAnalyzer.Profile cascade = CascadeResilienceAnalyzer.analyze(p);
            boolean matched = p.strategyTargetMatched
                    && p.generationStrategy == SolutionStrategy.CHAIN;
            if (!matched) fallbacks++;
            Row row = new Row(sample, usedRetry, usedSeed, p, m, cascade, matched);
            rows.add(row);

            System.out.printf(
                    "CASE sample=%d retry=%d seed=%d matched=%s family=%s ctor=%s stage=%d "
                            + "hidden=%d initialMin=%d initialAvg=%.2f rootForced=%d "
                            + "proofCost=%.2f decisions=%d branches=%d failed=%d depth=%d "
                            + "avgDecisionWidth=%.2f maxBranchWidth=%d avgPropagation=%.2f maxPropagation=%d "
                            + "cascadeFrac=%.2f cascadeAdditional=%d vulnerable=%d wholeCollapse=%s "
                            + "generationMs=%d attempts=%d rejects=%d%n",
                    sample, usedRetry, usedSeed, matched,
                    p.generatorFamily, p.generatorConstructor, p.generationStage,
                    m.hidden, m.initialMinDomain, m.initialAverageDomain, m.rootForced,
                    m.proofCostPerHidden(), m.decisionNodes, m.candidateBranches,
                    m.failedBranches, m.maxDepth, m.averageDecisionWidth(),
                    m.maxBranchWidth, m.averagePropagationGain(), m.maxPropagationGain,
                    cascade.maxResolvedFractionAfterOneCell,
                    cascade.maxAdditionalForcedAfterOneCell,
                    cascade.vulnerableSingleCells,
                    cascade.wholeBoardSingleCellCollapse(),
                    p.generationMillis, p.generationAttempts, p.generationRejects);
        }

        List<Row> matchedRows = new ArrayList<>();
        for (Row row : rows) if (row.matched && !row.metrics.truncated) matchedRows.add(row);
        if (matchedRows.isEmpty()) throw new AssertionError("No matched CHAIN L10 samples");

        matchedRows.sort(Comparator.comparingDouble(r -> r.metrics.proofCostPerHidden()));
        double min = matchedRows.get(0).metrics.proofCostPerHidden();
        double median = quantile(matchedRows, 0.50);
        double p90 = quantile(matchedRows, 0.90);
        double max = matchedRows.get(matchedRows.size() - 1).metrics.proofCostPerHidden();

        double avgCost = 0.0;
        double avgDecisions = 0.0;
        double avgDepth = 0.0;
        double avgPropagation = 0.0;
        double avgCascadeFraction = 0.0;
        int wholeCollapses = 0;
        int oneDecisionOrLess = 0;
        int depth3Plus = 0;
        for (Row row : matchedRows) {
            avgCost += row.metrics.proofCostPerHidden();
            avgDecisions += row.metrics.decisionNodes;
            avgDepth += row.metrics.maxDepth;
            avgPropagation += row.metrics.averagePropagationGain();
            avgCascadeFraction += row.cascade.maxResolvedFractionAfterOneCell;
            if (row.cascade.wholeBoardSingleCellCollapse()) wholeCollapses++;
            if (row.metrics.decisionNodes <= 1) oneDecisionOrLess++;
            if (row.metrics.maxDepth >= 3) depth3Plus++;
        }
        int n = matchedRows.size();
        avgCost /= n;
        avgDecisions /= n;
        avgDepth /= n;
        avgPropagation /= n;
        avgCascadeFraction /= n;

        int quartile = Math.max(1, n / 4);
        double easyCascade = averageCascadeFraction(matchedRows, 0, quartile);
        double hardCascade = averageCascadeFraction(matchedRows, n - quartile, n);
        double easyPropagation = averagePropagation(matchedRows, 0, quartile);
        double hardPropagation = averagePropagation(matchedRows, n - quartile, n);
        double cascadeCorrelation = pearsonCostVsCascade(matchedRows);
        double vulnerableCorrelation = pearsonCostVsVulnerable(matchedRows);

        List<Row> hardest = new ArrayList<>(matchedRows);
        hardest.sort(Comparator
                .comparingDouble((Row r) -> r.metrics.proofCostPerHidden()).reversed()
                .thenComparing(Comparator.comparingInt((Row r) -> r.metrics.maxDepth).reversed())
                .thenComparing(Comparator.comparingInt((Row r) -> r.metrics.failedBranches).reversed()));

        System.out.println("=== CHAIN L10 ADVERSARIAL FRONTIER ===");
        System.out.printf(
                "SUMMARY requested=%d generated=%d matched=%d fallbacks=%d misses=%d "
                        + "proofCost[min=%.2f median=%.2f p90=%.2f max=%.2f avg=%.2f] "
                        + "avgDecisions=%.2f avgDepth=%.2f avgPropagation=%.2f "
                        + "avgCascadeFrac=%.2f wholeCollapses=%d "
                        + "oneDecisionOrLess=%d depth3Plus=%d%n",
                samples, rows.size(), matchedRows.size(), fallbacks, misses,
                min, median, p90, max, avgCost,
                avgDecisions, avgDepth, avgPropagation,
                avgCascadeFraction, wholeCollapses,
                oneDecisionOrLess, depth3Plus);
        System.out.printf(
                "CORRELATION proofCost_vs_cascadeFrac=%.3f proofCost_vs_vulnerable=%.3f "
                        + "easyQuartile[cascade=%.2f propagation=%.2f] "
                        + "hardQuartile[cascade=%.2f propagation=%.2f]%n",
                cascadeCorrelation, vulnerableCorrelation,
                easyCascade, easyPropagation, hardCascade, hardPropagation);

        int top = Math.min(5, hardest.size());
        for (int i = 0; i < top; i++) {
            Row row = hardest.get(i);
            AdversarialMrvBenchmark.Metrics m = row.metrics;
            System.out.printf(
                    "TOP rank=%d sample=%d seed=%d retry=%d family=%s ctor=%s "
                            + "proofCost=%.2f decisions=%d branches=%d failed=%d depth=%d "
                            + "avgDecisionWidth=%.2f maxBranchWidth=%d avgPropagation=%.2f maxPropagation=%d "
                            + "cascadeFrac=%.2f cascadeAdditional=%d vulnerable=%d wholeCollapse=%s%n",
                    i + 1, row.sample, row.seed, row.retry,
                    row.puzzle.generatorFamily, row.puzzle.generatorConstructor,
                    m.proofCostPerHidden(), m.decisionNodes, m.candidateBranches,
                    m.failedBranches, m.maxDepth, m.averageDecisionWidth(),
                    m.maxBranchWidth, m.averagePropagationGain(), m.maxPropagationGain,
                    row.cascade.maxResolvedFractionAfterOneCell,
                    row.cascade.maxAdditionalForcedAfterOneCell,
                    row.cascade.vulnerableSingleCells,
                    row.cascade.wholeBoardSingleCellCollapse());
        }

        for (Row row : matchedRows) {
            if (row.metrics.truncated) throw new AssertionError("MRV frontier truncated");
            if (row.metrics.solutions != 1) throw new AssertionError("Unexpected solution count");
        }
    }

    static double averageCascadeFraction(List<Row> rows, int from, int to) {
        if (to <= from) return 0.0;
        double sum = 0.0;
        for (int i = from; i < to; i++) sum += rows.get(i).cascade.maxResolvedFractionAfterOneCell;
        return sum / (to - from);
    }

    static double averagePropagation(List<Row> rows, int from, int to) {
        if (to <= from) return 0.0;
        double sum = 0.0;
        for (int i = from; i < to; i++) sum += rows.get(i).metrics.averagePropagationGain();
        return sum / (to - from);
    }

    static double pearsonCostVsCascade(List<Row> rows) {
        double[] x = new double[rows.size()];
        double[] y = new double[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            x[i] = rows.get(i).metrics.proofCostPerHidden();
            y[i] = rows.get(i).cascade.maxResolvedFractionAfterOneCell;
        }
        return pearson(x, y);
    }

    static double pearsonCostVsVulnerable(List<Row> rows) {
        double[] x = new double[rows.size()];
        double[] y = new double[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            x[i] = rows.get(i).metrics.proofCostPerHidden();
            y[i] = rows.get(i).cascade.vulnerableSingleCells;
        }
        return pearson(x, y);
    }

    static double pearson(double[] x, double[] y) {
        if (x.length != y.length || x.length < 2) return 0.0;
        double mx = 0.0, my = 0.0;
        for (int i = 0; i < x.length; i++) {
            mx += x[i];
            my += y[i];
        }
        mx /= x.length;
        my /= y.length;
        double covariance = 0.0, vx = 0.0, vy = 0.0;
        for (int i = 0; i < x.length; i++) {
            double dx = x[i] - mx;
            double dy = y[i] - my;
            covariance += dx * dy;
            vx += dx * dx;
            vy += dy * dy;
        }
        if (vx == 0.0 || vy == 0.0) return 0.0;
        return covariance / Math.sqrt(vx * vy);
    }

    static double quantile(List<Row> sorted, double q) {
        if (sorted.isEmpty()) return 0.0;
        int index = (int) Math.ceil(q * sorted.size()) - 1;
        index = Math.max(0, Math.min(sorted.size() - 1, index));
        return sorted.get(index).metrics.proofCostPerHidden();
    }

    static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
