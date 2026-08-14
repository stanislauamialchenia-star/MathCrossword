package com.offline.mathcrossword;

import java.util.Locale;

/** Cheap profile-only regression for the public 1..10 + continuous Path curve. */
public final class DifficultyCurveHarness {
    public static void main(String[] args) {
        Locale.setDefault(Locale.US);
        int from = args.length > 0 ? Math.max(1, Integer.parseInt(args[0])) : 1;
        int to = args.length > 1 ? Math.max(from, Integer.parseInt(args[1])) : 100;
        int step = args.length > 2 ? Math.max(1, Integer.parseInt(args[2])) : 5;
        System.out.println("level,logic,logic_score,logic_tier,calc,calc_score,calc_tier,equations,hidden,max_number,operations,path_mode");
        for (int level = from; level <= to; level += step) {
            double ls = DifficultyScale.pathLogicScore(level);
            double cs = DifficultyScale.pathCalcScore(level);
            int l = DifficultyScale.displayLevel(ls);
            int c = DifficultyScale.displayLevel(cs);
            char[] ops = DifficultyScale.pathOperations(cs);
            System.out.printf(Locale.US, "%d,%d,%.3f,%d,%d,%.3f,%d,%d,%d,%d,%s,%s%n",
                    level, l, ls, DifficultyScale.logicTier(l), c, cs, DifficultyScale.calcTier(c),
                    DifficultyScale.pathEquationCount(ls), DifficultyScale.pathHiddenTarget(ls),
                    DifficultyScale.pathMaxNumber(cs), new String(ops),
                    DifficultyScale.logicTier(l) >= 4);
        }
    }
}
