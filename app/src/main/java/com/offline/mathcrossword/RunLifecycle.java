package com.offline.mathcrossword;

import java.util.Locale;

/**
 * Android-independent lifecycle semantics for play telemetry.
 *
 * A Visit is one foreground interaction window. A PuzzleRun is the player's
 * continuing attempt at one puzzle until a terminal outcome is known.
 *
 * This helper deliberately keeps lifecycle labels language-independent because
 * they are research/export identifiers, not player-facing text.
 */
final class RunLifecycle {
    static final String SOLVED = "SOLVED";
    static final String IN_PROGRESS = "IN_PROGRESS";
    static final String GIVE_UP = "GIVE_UP";
    static final String RESTARTED = "RESTARTED";
    static final String SKIPPED = "SKIPPED";
    static final String ABANDONED = "ABANDONED";

    private RunLifecycle() { }

    static String outcome(boolean solved, String finishReason) {
        if (solved) return SOLVED;
        String reason = finishReason == null ? "" : finishReason.toLowerCase(Locale.ROOT);
        switch (reason) {
            case "give_up":
            case "giveup":
                return GIVE_UP;
            case "restart":
            case "restarted":
            case "reset":
                return RESTARTED;
            case "skip":
            case "skipped":
                return SKIPPED;
            case "replaced":
            case "superseded":
                return ABANDONED;
            case "home":
            case "background":
            case "pause":
            case "unknown":
            case "":
            default:
                return IN_PROGRESS;
        }
    }

    static boolean isSolved(String outcome) {
        return SOLVED.equals(outcome);
    }

    static boolean isTerminal(String outcome) {
        return SOLVED.equals(outcome)
                || GIVE_UP.equals(outcome)
                || RESTARTED.equals(outcome)
                || SKIPPED.equals(outcome)
                || ABANDONED.equals(outcome);
    }

    static boolean isExplicitDifficultyOutcome(String outcome) {
        // An in-progress/home-exit visit must never be interpreted as a failed solve.
        // ABANDONED is also ambiguous: replacement can be a product/navigation choice.
        return SOLVED.equals(outcome) || GIVE_UP.equals(outcome);
    }

    static int precedence(String outcome) {
        if (SOLVED.equals(outcome)) return 6;
        if (GIVE_UP.equals(outcome)) return 5;
        if (SKIPPED.equals(outcome)) return 4;
        if (RESTARTED.equals(outcome)) return 3;
        if (ABANDONED.equals(outcome)) return 2;
        return 1;
    }

    static String mergeOutcome(String current, String next) {
        if (current == null || current.isEmpty()) return next == null ? IN_PROGRESS : next;
        if (next == null || next.isEmpty()) return current;
        return precedence(next) > precedence(current) ? next : current;
    }

    static String puzzleId(String mode, int level, long seed, int generatorVersion) {
        String safeMode = mode == null ? "" : mode;
        return safeMode + ":" + level + ":" + seed + ":g" + generatorVersion;
    }
}
