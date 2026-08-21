package com.offline.mathcrossword;

public final class PostSolveInsightHarness {
    public static void main(String[] args) {
        UiText.setLanguageOverride("ru");
        SessionTracker.SessionSummary divergent = new SessionTracker.SessionSummary();
        divergent.solved = true;
        divergent.traversalAvailable = true;
        divergent.traversalDirection = "mixed";
        divergent.traversalConfidencePct = 88.0;
        divergent.routeCompared = true;
        divergent.routeStrongDivergence = true;
        PostSolveInsightBuilder.Result a = PostSolveInsightBuilder.build(divergent);
        require(!a.observations.isEmpty(), "divergent story missing");
        require(a.observations.size() <= 4, "too many observations");
        require(join(a).contains("маршрут"), "route story missing");

        SessionTracker.SessionSummary cascade = new SessionTracker.SessionSummary();
        cascade.solved = true;
        cascade.hidden = 10;
        cascade.maxForcedCascade = 6;
        cascade.rapidCascades = 1;
        cascade.hypothesisEpisodes = 2;
        cascade.candidateCellRevisits = 3;
        cascade.firstActionMs = 18000L;
        PostSolveInsightBuilder.Result b = PostSolveInsightBuilder.build(cascade);
        require(b.observations.size() >= 2 && b.observations.size() <= 4, "signal selection bounds");
        require(join(b).contains("каскад"), "cascade story missing");
        require(join(b).contains("альтернатив"), "hypothesis uncertainty wording missing");

        SessionTracker.SessionSummary quiet = new SessionTracker.SessionSummary();
        quiet.solved = true;
        require(PostSolveInsightBuilder.build(quiet).observations.size() == 1, "quiet solve fallback missing");

        SessionTracker.SessionSummary unfinished = new SessionTracker.SessionSummary();
        require(PostSolveInsightBuilder.build(unfinished).observations.isEmpty(), "unfinished card must be empty");
        System.out.println("PASS post-solve insight contracts");
    }

    private static String join(PostSolveInsightBuilder.Result r) {
        StringBuilder out = new StringBuilder();
        for (String s : r.observations) out.append(s).append('\n');
        return out.toString().toLowerCase(java.util.Locale.ROOT);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
