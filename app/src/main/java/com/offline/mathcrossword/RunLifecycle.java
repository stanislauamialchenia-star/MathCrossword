package com.offline.mathcrossword;

import java.util.Locale;

/**
 * Android-independent lifecycle semantics for play telemetry.
 *
 * A Visit is one foreground interaction window. A PuzzleRun is the player's
 * continuing attempt at one puzzle until a terminal outcome is known.
 *
 * These labels are research/export identifiers and stay language-independent.
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
        String reason = normalizedReason(finishReason);
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

    static String visitOutcome(boolean solved, String finishReason) {
        if (solved) return "SOLVED";
        String reason = normalizedReason(finishReason);
        switch (reason) {
            case "home": return "HOME_EXIT";
            case "background":
            case "pause": return "APP_BACKGROUND";
            case "replaced":
            case "superseded": return "REPLACED";
            case "give_up":
            case "giveup": return "GIVE_UP";
            case "restart":
            case "restarted":
            case "reset": return "RESTARTED";
            case "skip":
            case "skipped": return "SKIPPED";
            default: return "UNKNOWN";
        }
    }

    static String lifecycleEvent(boolean solved, String finishReason) {
        if (solved) return "SOLVED";
        String reason = normalizedReason(finishReason);
        switch (reason) {
            case "home": return "HOME_EXIT";
            case "give_up":
            case "giveup": return "GIVE_UP";
            case "restart":
            case "restarted":
            case "reset": return "RESTARTED";
            case "skip":
            case "skipped": return "SKIPPED";
            case "replaced":
            case "superseded": return "RUN_SUPERSEDED";
            default: return "VISIT_FINISHED";
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
        // Home/background/in-progress visits must never become failed solves.
        // Replacement is also ambiguous: it can be simple navigation.
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

    private static String normalizedReason(String finishReason) {
        return finishReason == null ? "" : finishReason.toLowerCase(Locale.ROOT);
    }
}
