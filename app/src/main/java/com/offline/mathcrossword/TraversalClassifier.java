package com.offline.mathcrossword;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure classifier for observed node visits over a ConcreteReasoningGraph.
 * It describes interaction evidence only; it does not infer private thought.
 */
final class TraversalClassifier {
    enum Direction {
        FORWARD,
        BACKWARD,
        BIDIRECTIONAL,
        MIXED,
        DIVERGENT,
        UNKNOWN
    }

    static final class Result {
        Direction direction = Direction.UNKNOWN;
        boolean internalEntry;
        boolean branchProbing;
        boolean anchorReached;
        boolean structuralDivergence;
        int entryDepth = -1;
        int maxDepth = -1;
        int mappedNodes;
        int offGraphNodes;
        int forwardTransitions;
        int backwardTransitions;
        int lateralTransitions;
        int adjacentTransitions;
        int nonAdjacentTransitions;
        double mappedFraction;
        double adjacencyContinuity;
        double confidence;
    }

    private TraversalClassifier() { }

    static Result classify(ConcreteReasoningGraph graph, List<Integer> observed) {
        Result out = new Result();
        if (graph == null || observed == null || observed.isEmpty()) return out;

        List<Integer> mapped = new ArrayList<>();
        for (Integer node : observed) {
            if (node != null && graph.containsNode(node)) mapped.add(node);
            else out.offGraphNodes++;
        }
        out.mappedNodes = mapped.size();
        out.mappedFraction = observed.isEmpty() ? 0.0 : mapped.size() / (double) observed.size();
        if (mapped.isEmpty()) {
            out.direction = Direction.DIVERGENT;
            out.structuralDivergence = true;
            return out;
        }

        int firstDistance = graph.distanceToAnchor(mapped.get(0));
        out.entryDepth = firstDistance;
        out.internalEntry = firstDistance > 0;
        out.maxDepth = firstDistance;
        out.anchorReached = firstDistance == 0;

        Map<Integer, Integer> lastSeen = new HashMap<>();
        lastSeen.put(mapped.get(0), 0);
        int firstAnchorIndex = out.anchorReached ? 0 : -1;
        boolean backwardBeforeAnchor = false;
        boolean forwardAfterAnchor = false;

        for (int i = 1; i < mapped.size(); i++) {
            int prev = mapped.get(i - 1);
            int cur = mapped.get(i);
            int prevDistance = graph.distanceToAnchor(prev);
            int curDistance = graph.distanceToAnchor(cur);
            out.maxDepth = Math.max(out.maxDepth, curDistance);
            if (curDistance == 0) {
                out.anchorReached = true;
                if (firstAnchorIndex < 0) firstAnchorIndex = i;
            }

            if (prev == cur || graph.adjacent(prev, cur)) out.adjacentTransitions++;
            else out.nonAdjacentTransitions++;

            if (prevDistance >= 0 && curDistance >= 0) {
                if (curDistance > prevDistance) {
                    out.forwardTransitions++;
                    if (firstAnchorIndex >= 0 && i > firstAnchorIndex) forwardAfterAnchor = true;
                } else if (curDistance < prevDistance) {
                    out.backwardTransitions++;
                    if (firstAnchorIndex < 0 || i <= firstAnchorIndex) backwardBeforeAnchor = true;
                } else {
                    out.lateralTransitions++;
                }
            }

            Integer previousVisit = lastSeen.put(cur, i);
            if (previousVisit != null && i - previousVisit >= 2 && graph.degree(cur) >= 2) {
                out.branchProbing = true;
            }
        }

        int transitions = Math.max(0, mapped.size() - 1);
        out.adjacencyContinuity = transitions == 0 ? 1.0 : out.adjacentTransitions / (double) transitions;
        double offGraphFraction = observed.isEmpty() ? 0.0 : out.offGraphNodes / (double) observed.size();
        out.structuralDivergence = offGraphFraction > 0.34
                || (transitions >= 2 && out.adjacencyContinuity < 0.50);

        if (out.structuralDivergence) {
            out.direction = Direction.DIVERGENT;
        } else if (backwardBeforeAnchor && forwardAfterAnchor) {
            out.direction = Direction.BIDIRECTIONAL;
        } else if (out.backwardTransitions > 0 && out.forwardTransitions == 0) {
            out.direction = Direction.BACKWARD;
        } else if (out.forwardTransitions > 0 && out.backwardTransitions == 0) {
            out.direction = Direction.FORWARD;
        } else if (out.forwardTransitions == 0 && out.backwardTransitions == 0) {
            out.direction = Direction.UNKNOWN;
        } else {
            out.direction = Direction.MIXED;
        }

        double anchorFactor = graph.anchorConfidence <= 0.0 ? 0.45 : graph.anchorConfidence;
        double evidence = Math.min(1.0, mapped.size() / 5.0);
        out.confidence = clamp01(0.45 * out.mappedFraction
                + 0.30 * out.adjacencyContinuity
                + 0.15 * anchorFactor
                + 0.10 * evidence);
        if (out.direction == Direction.DIVERGENT) out.confidence = Math.max(out.confidence, 0.70);
        return out;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
