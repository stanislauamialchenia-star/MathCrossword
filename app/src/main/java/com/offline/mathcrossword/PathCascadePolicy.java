package com.offline.mathcrossword;

/**
 * Classifies the shape of a hard PATH cascade without treating every long
 * dependency chain as a defect.
 *
 * The key distinction is temporal/structural:
 * - opening collapse: too much of the puzzle is forced before meaningful reasoning;
 * - productive dependency cascade: the opening stays uncertain, then a later
 *   reasoning step unlocks a long chain;
 * - systemic fragility: many different single-cell revelations can each collapse
 *   most of the board.
 *
 * This class is Android-independent so the policy can be regression-tested in CI.
 */
final class PathCascadePolicy {
    private PathCascadePolicy() { }

    enum Shape {
        RESILIENT,
        OPENING_COLLAPSE,
        SYSTEMIC_FRAGILITY,
        PRODUCTIVE_DEPENDENCY_CASCADE
    }

    static final class Assessment {
        final Shape shape;
        final int openingForcedLimit;
        final int productiveCascadeFloor;
        final int systemicResolvedLimit;
        final int vulnerableCellAllowance;

        Assessment(Shape shape,
                   int openingForcedLimit,
                   int productiveCascadeFloor,
                   int systemicResolvedLimit,
                   int vulnerableCellAllowance) {
            this.shape = shape;
            this.openingForcedLimit = openingForcedLimit;
            this.productiveCascadeFloor = productiveCascadeFloor;
            this.systemicResolvedLimit = systemicResolvedLimit;
            this.vulnerableCellAllowance = vulnerableCellAllowance;
        }

        boolean reject() {
            return shape == Shape.OPENING_COLLAPSE || shape == Shape.SYSTEMIC_FRAGILITY;
        }

        boolean productiveCascade() {
            return shape == Shape.PRODUCTIVE_DEPENDENCY_CASCADE;
        }
    }

    static Assessment assess(int hidden,
                             int basicForced,
                             int basicRemaining,
                             int maxForcedCascade,
                             int reasoningSteps,
                             int maxReasoningDepth,
                             int maxResolvedAfterOneCell,
                             int vulnerableSingleCells,
                             double antiCollapseStrength) {
        hidden = Math.max(1, hidden);
        double strength = clamp(antiCollapseStrength, 0.0, 1.0);

        // Keep the existing intent: harder PATH boards should expose less free
        // information at the opening. This is the part of anti-collapse worth
        // preserving.
        int openingForcedLimit = Math.max(1,
                (int) Math.ceil(hidden * lerp(0.38, 0.16, strength)));

        // A long later cascade is evidence of a dependency chain, not necessarily
        // fragility. The floor becomes stricter as PATH difficulty rises.
        int productiveCascadeFloor = Math.max(3,
                (int) Math.ceil(hidden * lerp(0.42, 0.55, strength)));

        // Systemic fragility is about how much can be revealed by arbitrary single
        // correct cells, not about the longest solver cascade in isolation.
        int systemicResolvedLimit = Math.max(3,
                (int) Math.ceil(hidden * lerp(0.82, 0.68, strength)));
        int vulnerableCellAllowance = strength >= 0.65 ? 1 : 2;

        if (basicForced > openingForcedLimit) {
            return new Assessment(Shape.OPENING_COLLAPSE, openingForcedLimit,
                    productiveCascadeFloor, systemicResolvedLimit, vulnerableCellAllowance);
        }

        // Systemic fragility is a stronger defect than a nice-looking chain. If
        // several unrelated correct cells can each unlock most of the board, the
        // puzzle is fragile even when the solver also observes reasoning steps.
        boolean largeSingleCellReveal = maxResolvedAfterOneCell > systemicResolvedLimit;
        boolean repeatedFragility = vulnerableSingleCells > vulnerableCellAllowance;
        if (largeSingleCellReveal && repeatedFragility) {
            return new Assessment(Shape.SYSTEMIC_FRAGILITY, openingForcedLimit,
                    productiveCascadeFloor, systemicResolvedLimit, vulnerableCellAllowance);
        }

        boolean openingStillUncertain = basicRemaining >= Math.max(3, (hidden * 3) / 5);
        boolean reasoningBeforeCascade = reasoningSteps > 0 || maxReasoningDepth > 0;
        boolean longLaterCascade = maxForcedCascade >= productiveCascadeFloor;

        if (openingStillUncertain && reasoningBeforeCascade && longLaterCascade) {
            return new Assessment(Shape.PRODUCTIVE_DEPENDENCY_CASCADE, openingForcedLimit,
                    productiveCascadeFloor, systemicResolvedLimit, vulnerableCellAllowance);
        }

        return new Assessment(Shape.RESILIENT, openingForcedLimit,
                productiveCascadeFloor, systemicResolvedLimit, vulnerableCellAllowance);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * clamp(t, 0.0, 1.0);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
