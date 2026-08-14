package com.offline.mathcrossword;

/**
 * v22 difficulty model.
 *
 * The UI exposes Logic / Calculation 1..10. The existing mature generators
 * still use five internal capability tiers; a continuous score and the public
 * 1..10 level let Path vary the amount of ambiguity, board size and arithmetic
 * gradually inside a tier instead of jumping at a single level boundary.
 */
final class DifficultyScale {
    private DifficultyScale() { }

    static int clamp10(int level) { return Math.max(1, Math.min(10, level)); }

    /** Public Logic -> mature engine tier. The old L3->L4 gap is intentionally widened. */
    static int logicTier(int displayLevel) {
        int l = clamp10(displayLevel);
        if (l <= 2) return 1;
        if (l == 3) return 2;
        if (l <= 5) return 3;
        if (l <= 8) return 4;
        return 5;
    }

    /** Calculation is split more evenly because its old five bands were already monotone. */
    static int calcTier(int displayLevel) {
        int l = clamp10(displayLevel);
        if (l <= 2) return 1;
        if (l <= 4) return 2;
        if (l <= 6) return 3;
        if (l <= 8) return 4;
        return 5;
    }

    static int displayLogicForTier(int tier) {
        switch (Math.max(1, Math.min(5, tier))) {
            case 1: return 1;
            case 2: return 3;
            case 3: return 5;
            case 4: return 7;
            default: return 9;
        }
    }

    static int displayCalcForTier(int tier) {
        switch (Math.max(1, Math.min(5, tier))) {
            case 1: return 1;
            case 2: return 3;
            case 3: return 5;
            case 4: return 7;
            default: return 9;
        }
    }

    static int displayLevel(double score) {
        return clamp10((int)Math.round(Math.max(1.0, Math.min(10.0, score))));
    }

    /**
     * Continuous Path curve. The first 100 levels deliberately spend a lot of
     * resolution around Logic 4..6, where play changes from automatic cleanup to
     * candidates / multiple fronts. Later levels keep growing without requiring
     * an infinite ladder beyond 10.
     */
    static double pathLogicScore(int level) {
        level = Math.max(1, level);
        if (level <= 100) {
            return interpolate(level,
                    new int[]{1, 10, 25, 40, 55, 69, 70, 85, 100},
                    new double[]{1.0, 1.8, 2.8, 3.8, 4.5, 5.0, 5.1, 5.8, 6.4});
        }
        if (level <= 300) return lerp(6.4, 8.5, (level - 100) / 200.0);
        if (level <= 600) return lerp(8.5, 9.5, (level - 300) / 300.0);
        // Beyond 600, difficulty no longer climbs forever. Use a gentle wave
        // through the expert range so long paths can stay varied rather than
        // becoming a monotone wall of maximum-difficulty boards.
        double base = Math.min(9.65, 9.5 + (level - 600) / 2000.0);
        double wave = 0.32 * Math.sin((level - 600) * Math.PI / 90.0);
        return clamp(base + wave, 8.9, 10.0);
    }

    static double pathCalcScore(int level) {
        level = Math.max(1, level);
        if (level <= 100) {
            return interpolate(level,
                    new int[]{1, 15, 30, 50, 70, 85, 100},
                    new double[]{1.0, 1.6, 2.4, 3.2, 4.0, 4.6, 5.2});
        }
        if (level <= 300) return lerp(5.2, 7.2, (level - 100) / 200.0);
        if (level <= 600) return lerp(7.2, 8.8, (level - 300) / 300.0);
        double base = Math.min(9.35, 8.8 + (level - 600) / 1500.0);
        double wave = 0.28 * Math.sin((level - 600) * Math.PI / 120.0 + 0.7);
        return clamp(base + wave, 8.4, 10.0);
    }

    static int pathEquationCount(double logicScore) {
        return clampInt((int)Math.round(3.0 + (logicScore - 1.0) * 0.90), 3, 14);
    }

    static int pathHiddenTarget(double logicScore) {
        return clampInt((int)Math.round(3.0 + (logicScore - 1.0) * 1.15), 3, 18);
    }

    static int pathMaxNumber(double calcScore) {
        double[] caps = {20, 32, 50, 80, 125, 200, 320, 500, 720, 1000};
        double s = clamp(calcScore, 1.0, 10.0);
        int lo = (int)Math.floor(s) - 1;
        int hi = Math.min(9, lo + 1);
        double f = s - Math.floor(s);
        return (int)Math.round(lerp(caps[lo], caps[hi], f));
    }

    static char[] pathOperations(double calcScore) {
        if (calcScore < 2.2) return new char[]{'+'};
        if (calcScore < 4.2) return new char[]{'+', '-'};
        if (calcScore < 5.8) return new char[]{'+', '-', '×'};
        if (calcScore < 7.4) return new char[]{'+', '-', '×', '÷'};
        return new char[]{'+', '-', '×', '÷', '^'};
    }

    /** Strength 0..1 used by PATH anti-collapse gates inside old tier 4. */
    static double antiCollapseStrength(double logicScore) {
        return clamp((logicScore - 3.6) / 3.4, 0.0, 1.0);
    }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    static int clampInt(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    static double lerp(double a, double b, double t) { return a + (b - a) * clamp(t, 0.0, 1.0); }

    private static double interpolate(int x, int[] xs, double[] ys) {
        if (x <= xs[0]) return ys[0];
        for (int i = 1; i < xs.length; i++) {
            if (x <= xs[i]) {
                double t = (x - xs[i - 1]) / (double)(xs[i] - xs[i - 1]);
                return lerp(ys[i - 1], ys[i], t);
            }
        }
        return ys[ys.length - 1];
    }
}
