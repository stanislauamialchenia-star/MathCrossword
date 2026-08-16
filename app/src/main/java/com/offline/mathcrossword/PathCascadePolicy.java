package com.offline.mathcrossword;

/**
 * Classifies the shape of a hard PATH cascade without treating every long
 * dependency chain as a defect.
 *
 * The key distinction is temporal/structural:
 * - opening collapse: too much of the puzzle is forced before meaningful reasoning;
 * - productive dependency cascade: the opening stays uncertain, then a later
 *   reasoning step unlocks a long chain;
 * - systemic fragility: many single-cell revelations can collapse most of the
 *   board when there is no evidence of a non-trivial reasoning dependency.
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

        // Preserve the useful part of anti-collapse: a hard board should not give
        // away too much information before the player has done anything meaningful.
        int openingForcedLimit = Math.max(1,
                (int) Math.ceil(hidden * lerp(0.38, 0.16, strength)));

        // A long later cascade can be the dependency structure we intentionally
        // want to train. Higher PATH difficulty asks for a more substantial chain
        // before we label it productive.
        int productiveCascadeFloor = Math.max(3,
                (int) Math.ceil(hidden * lerp(0.42, 0.55, strength)));

        int systemicResolvedLimit = Math.max(3,
                (int) Math.ceil(hidden * lerp(0.82, 0.68, strength)));
        int vulnerableCellAllowance = strength >= 0.65 ? 1 : 2;

        if (basicForced > openingForcedLimit) {
            return new Assessment(Shape.OPENING_COLLAPSE, openingForcedLimit,
                    productiveCascadeFloor, systemicResolvedLimit, vulnerableCellAllowance);
        }

        boolean openingStillUncertain = basicRemaining >= Math.max(3, (hidden * 3) / 5);
        boolean hasNonTrivialReasoning = reasoningSteps > 0 || maxReasoningDepth > 0;
        boolean longLaterCascade = maxForcedCascade >= productiveCascadeFloor;

        // Aggregate solver metrics do not yet prove temporal order, so this is a
        // conservative proxy: the board has a non-trivial reasoning dependency and
        // a long cascade while its opening remains uncertain. Step-level traversal
        // telemetry will refine this in the reasoning-graph work.
        if (openingStillUncertain && hasNonTrivialReasoning && longLaterCascade) {
            return new Assessment(Shape.PRODUCTIVE_DEPENDENCY_CASCADE, openingForcedLimit,
                    productiveCascadeFloor, systemicResolvedLimit, vulnerableCellAllowance);
        }

        boolean largeSingleCellReveal = maxResolvedAfterOneCell > systemicResolvedLimit;
        boolean repeatedFragility = vulnerableSingleCells > vulnerableCellAllowance;

        // `vulnerableSingleCells` currently counts cells, not independent regions.
        // Several cells on one dependency chain can therefore look like several
        // weaknesses. Until vulnerability is graph-clustered, only use this as a
        // hard reject when the solver sees no non-trivial reasoning dependency.
        if (largeSingleCellReveal && repeatedFragility && !hasNonTrivialReasoning) {
            return new Assessment(Shape.SYSTEMIC_FRAGILITY, openingForcedLimit,
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
