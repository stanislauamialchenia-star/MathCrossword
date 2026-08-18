package com.offline.mathcrossword;

/** Deterministic contracts for PuzzleRun / Visit outcome semantics. */
public final class RunLifecycleHarness {
    private RunLifecycleHarness() { }

    public static void main(String[] args) {
        expect(RunLifecycle.IN_PROGRESS, RunLifecycle.outcome(false, "home"), "home is not a loss");
        expect("HOME_EXIT", RunLifecycle.visitOutcome(false, "home"), "home visit outcome");
        check(!RunLifecycle.isTerminal(RunLifecycle.outcome(false, "home")), "home remains resumable/in-progress");
        check(!RunLifecycle.isExplicitDifficultyOutcome(RunLifecycle.outcome(false, "home")), "home excluded from difficulty outcomes");

        expect(RunLifecycle.IN_PROGRESS, RunLifecycle.outcome(false, "background"), "background is not a loss");
        expect("APP_BACKGROUND", RunLifecycle.visitOutcome(false, "background"), "background visit outcome");

        expect(RunLifecycle.ABANDONED, RunLifecycle.outcome(false, "replaced"), "replacement supersedes old run");
        check(!RunLifecycle.isExplicitDifficultyOutcome(RunLifecycle.ABANDONED), "replacement is not evidence of puzzle difficulty");

        expect(RunLifecycle.GIVE_UP, RunLifecycle.outcome(false, "give_up"), "explicit give-up");
        check(RunLifecycle.isExplicitDifficultyOutcome(RunLifecycle.GIVE_UP), "give-up is an explicit difficulty outcome");

        expect(RunLifecycle.RESTARTED, RunLifecycle.outcome(false, "reset"), "reset/restart outcome");
        expect(RunLifecycle.SKIPPED, RunLifecycle.outcome(false, "skip"), "skip outcome");

        expect(RunLifecycle.SOLVED, RunLifecycle.outcome(true, "home"), "solved overrides finish reason");
        expect(RunLifecycle.SOLVED, RunLifecycle.mergeOutcome(RunLifecycle.IN_PROGRESS, RunLifecycle.SOLVED), "solved dominates earlier visit");
        expect(RunLifecycle.GIVE_UP, RunLifecycle.mergeOutcome(RunLifecycle.IN_PROGRESS, RunLifecycle.GIVE_UP), "give-up terminates run");

        String a = RunLifecycle.puzzleId("PATH", 42, 123L, 9);
        String b = RunLifecycle.puzzleId("PATH", 42, 123L, 9);
        String c = RunLifecycle.puzzleId("PATH", 42, 124L, 9);
        expect(a, b, "puzzle identity deterministic");
        check(!a.equals(c), "different seed changes puzzle identity");

        System.out.println("RunLifecycleHarness: OK");
    }

    private static void expect(String expected, String actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
