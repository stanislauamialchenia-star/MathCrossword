package com.offline.mathcrossword;

/** Small deterministic contract tests for PathCascadePolicy. */
public final class PathCascadePolicyHarness {
    private PathCascadePolicyHarness() { }

    public static void main(String[] args) {
        openingCollapseIsRejected();
        productiveLaterCascadeIsAccepted();
        systemicFragilityIsRejected();
        reasoningChainIsNotMistakenForIndependentFragility();
        ordinaryResilientBoardIsAccepted();
        System.out.println("PathCascadePolicyHarness: OK");
    }

    private static void openingCollapseIsRejected() {
        PathCascadePolicy.Assessment a = PathCascadePolicy.assess(
                10, 4, 6, 5, 0, 0, 6, 1, 0.80);
        require(a.shape == PathCascadePolicy.Shape.OPENING_COLLAPSE,
                "expected OPENING_COLLAPSE, got " + a.shape);
        require(a.reject(), "opening collapse must be rejected");
    }

    private static void productiveLaterCascadeIsAccepted() {
        PathCascadePolicy.Assessment a = PathCascadePolicy.assess(
                12, 1, 10, 8, 2, 1, 9, 1, 0.80);
        require(a.shape == PathCascadePolicy.Shape.PRODUCTIVE_DEPENDENCY_CASCADE,
                "expected PRODUCTIVE_DEPENDENCY_CASCADE, got " + a.shape);
        require(!a.reject(), "productive later cascade must not be rejected");
        require(a.productiveCascade(), "productive cascade flag must be true");
    }

    private static void systemicFragilityIsRejected() {
        PathCascadePolicy.Assessment a = PathCascadePolicy.assess(
                12, 1, 10, 3, 0, 0, 10, 3, 0.80);
        require(a.shape == PathCascadePolicy.Shape.SYSTEMIC_FRAGILITY,
                "expected SYSTEMIC_FRAGILITY, got " + a.shape);
        require(a.reject(), "systemic fragility must be rejected");
    }

    private static void reasoningChainIsNotMistakenForIndependentFragility() {
        PathCascadePolicy.Assessment a = PathCascadePolicy.assess(
                12, 1, 10, 8, 2, 1, 10, 3, 0.80);
        require(a.shape == PathCascadePolicy.Shape.PRODUCTIVE_DEPENDENCY_CASCADE,
                "reasoning-chain vulnerability must remain productive until graph clustering exists, got " + a.shape);
        require(!a.reject(), "cells on one reasoning chain must not be treated as independent fragility");
    }

    private static void ordinaryResilientBoardIsAccepted() {
        PathCascadePolicy.Assessment a = PathCascadePolicy.assess(
                12, 1, 9, 3, 0, 0, 7, 1, 0.80);
        require(a.shape == PathCascadePolicy.Shape.RESILIENT,
                "expected RESILIENT, got " + a.shape);
        require(!a.reject(), "resilient board must be accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
