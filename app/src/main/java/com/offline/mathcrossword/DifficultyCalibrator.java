package com.offline.mathcrossword;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Transparent, local calibration between engine-predicted Logic and observed play cost.
 *
 * Observed cost is deliberately personal and relative: solved sessions are ranked against
 * the player's own recent history, then split into ten bands. This avoids pretending that
 * "8 minutes" has the same meaning for every person or every board size.
 */
final class DifficultyCalibrator {
    private DifficultyCalibrator() { }

    static final int MIN_SOLVED = 8;
    static final int MAX_WINDOW = 80;

    static final class Sample {
        int predictedBand;
        int hidden;
        long activeMs;
        int eventCount;
        int undoCount;
        int candidateEdits;
        int hintCount;
        int deadEndPauses;
        int fullIncorrectCount;
        boolean solved;
        String mode = "";
        String strategy = SolutionStrategy.MIXED.name();
        int generatorVersion;
        long order;
    }

    static final class ScoredSample {
        Sample sample;
        double rawCost;
        int observedBand;
        int error;
    }

    static final class Result {
        int usableSessions;
        boolean ready;
        double meanError;
        double meanAbsError;
        int withinOne;
        int exact;
        int underestimated;
        int overestimated;
        int lastPredictedBand;
        int lastObservedBand;
        double lastRawCost;
        double recentCostChangePct;
        int scopeGeneratorVersion; // 0 = mixed historical fallback
        final List<ScoredSample> scored = new ArrayList<>();

        double withinOnePct() {
            return usableSessions == 0 ? 0.0 : withinOne * 100.0 / usableSessions;
        }
        double exactPct() {
            return usableSessions == 0 ? 0.0 : exact * 100.0 / usableSessions;
        }
    }

    static Result calibrate(List<Sample> input) {
        Result out = new Result();
        if (input == null || input.isEmpty()) return out;

        int latestVersion = 0;
        int latestSolved = 0;
        for (Sample s : input) {
            if (!eligible(s)) continue;
            latestVersion = Math.max(latestVersion, s.generatorVersion);
        }
        if (latestVersion > 0) {
            for (Sample s : input) {
                if (eligible(s) && s.generatorVersion == latestVersion) latestSolved++;
            }
        }
        boolean latestOnly = latestSolved >= MIN_SOLVED;
        out.scopeGeneratorVersion = latestOnly ? latestVersion : 0;

        List<ScoredSample> usable = new ArrayList<>();
        for (Sample s : input) {
            if (!eligible(s)) continue;
            if (latestOnly && s.generatorVersion != latestVersion) continue;
            ScoredSample x = new ScoredSample();
            x.sample = s;
            x.rawCost = observedRawCost(s);
            usable.add(x);
        }
        if (usable.size() > MAX_WINDOW) {
            usable.sort(Comparator.comparingLong(a -> a.sample.order));
            usable = new ArrayList<>(usable.subList(usable.size() - MAX_WINDOW, usable.size()));
        }
        out.usableSessions = usable.size();
        if (usable.size() < MIN_SOLVED) return out;
        out.ready = true;

        List<Double> costs = new ArrayList<>();
        for (ScoredSample x : usable) costs.add(x.rawCost);
        Collections.sort(costs);

        usable.sort(Comparator.comparingLong(a -> a.sample.order));
        double errorSum = 0.0;
        double absSum = 0.0;
        for (ScoredSample x : usable) {
            x.observedBand = percentileBand(costs, x.rawCost);
            int predicted = clamp(x.sample.predictedBand, 1, 10);
            x.error = x.observedBand - predicted;
            errorSum += x.error;
            absSum += Math.abs(x.error);
            if (x.error == 0) out.exact++;
            if (Math.abs(x.error) <= 1) out.withinOne++;
            if (x.error >= 2) out.underestimated++;
            if (x.error <= -2) out.overestimated++;
            out.scored.add(x);
        }
        out.meanError = errorSum / usable.size();
        out.meanAbsError = absSum / usable.size();

        ScoredSample last = usable.get(usable.size() - 1);
        out.lastPredictedBand = clamp(last.sample.predictedBand, 1, 10);
        out.lastObservedBand = last.observedBand;
        out.lastRawCost = last.rawCost;
        out.recentCostChangePct = recentCostChange(usable);
        return out;
    }

    private static boolean eligible(Sample s) {
        if (s == null || !s.solved || s.hidden <= 0 || s.activeMs <= 0) return false;
        // Replays and deliberately jumped-ahead test levels are kept in the raw
        // history, but they do not calibrate first-pass difficulty: familiarity
        // would otherwise make the generator look easier than it really was.
        return !("PATH_REPLAY".equals(s.mode) || "PATH_TEST".equals(s.mode));
    }

    /** Visible formula components are intentionally simple and documented. */
    static double observedRawCost(Sample s) {
        double hidden = Math.max(1.0, s.hidden);
        double secondsPerHidden = (s.activeMs / 1000.0) / hidden;
        double eventsPerHidden = s.eventCount / hidden;
        double revisionsPerHidden = (s.undoCount + s.fullIncorrectCount * 2.0) / hidden;
        double candidatesPerHidden = s.candidateEdits / hidden;

        return 1.55 * Math.log1p(secondsPerHidden / 3.0)
                + 0.70 * Math.log1p(eventsPerHidden)
                + 1.10 * revisionsPerHidden
                + 0.30 * candidatesPerHidden
                + 0.35 * s.deadEndPauses
                + 0.85 * s.hintCount;
    }

    private static int percentileBand(List<Double> sorted, double value) {
        if (sorted.isEmpty()) return 1;
        int lessOrEqual = 0;
        for (double v : sorted) if (v <= value) lessOrEqual++;
        double percentile = lessOrEqual / (double) sorted.size();
        int band = (int)Math.ceil(percentile * 10.0);
        return clamp(band, 1, 10);
    }

    private static double recentCostChange(List<ScoredSample> ordered) {
        if (ordered.size() < 12) return 0.0;
        int window = Math.min(10, ordered.size() / 2);
        List<Double> previous = new ArrayList<>();
        List<Double> recent = new ArrayList<>();
        int split = ordered.size() - window;
        int prevStart = Math.max(0, split - window);
        for (int i = prevStart; i < split; i++) previous.add(ordered.get(i).rawCost);
        for (int i = split; i < ordered.size(); i++) recent.add(ordered.get(i).rawCost);
        double a = median(previous);
        double b = median(recent);
        if (a <= 1e-9) return 0.0;
        return (b - a) * 100.0 / a;
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        List<Double> c = new ArrayList<>(values);
        Collections.sort(c);
        int n = c.size();
        if ((n & 1) == 1) return c.get(n / 2);
        return (c.get(n / 2 - 1) + c.get(n / 2)) / 2.0;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
