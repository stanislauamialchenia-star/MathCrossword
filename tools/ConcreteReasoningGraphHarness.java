package com.offline.mathcrossword;

import java.util.Arrays;
import java.util.List;

/** Deterministic Android-independent contracts for concrete graph traversal semantics. */
public final class ConcreteReasoningGraphHarness {
    public static void main(String[] args) {
        forwardChain();
        reverseChain();
        internalEntry();
        bidirectionalChain();
        branchProbe();
        offGraphDivergence();
        System.out.println("ConcreteReasoningGraphHarness OK");
    }

    private static ConcreteReasoningGraph chain5() {
        return ConcreteReasoningGraph.synthetic(5,
                new int[][]{{0,1},{1,2},{2,3},{3,4}}, 0);
    }

    private static void forwardChain() {
        TraversalClassifier.Result r = TraversalClassifier.classify(chain5(), seq(0,1,2,3,4));
        require(r.direction == TraversalClassifier.Direction.FORWARD, "forward chain");
        require(!r.internalEntry, "forward starts at anchor");
        require(!r.structuralDivergence, "forward is not divergence");
    }

    private static void reverseChain() {
        TraversalClassifier.Result r = TraversalClassifier.classify(chain5(), seq(4,3,2,1,0));
        require(r.direction == TraversalClassifier.Direction.BACKWARD, "reverse chain is backward");
        require(r.internalEntry, "reverse starts internally/deep");
        require(r.anchorReached, "reverse reaches anchor");
        require(!r.structuralDivergence, "reverse is a valid traversal");
    }

    private static void internalEntry() {
        TraversalClassifier.Result r = TraversalClassifier.classify(chain5(), seq(3,2,1,0));
        require(r.internalEntry, "internal entry is explicit");
        require(r.entryDepth == 3, "internal entry depth");
        require(r.direction == TraversalClassifier.Direction.BACKWARD, "internal entry may still be backward");
    }

    private static void bidirectionalChain() {
        TraversalClassifier.Result r = TraversalClassifier.classify(chain5(), seq(4,3,2,1,0,1,2,3,4));
        require(r.direction == TraversalClassifier.Direction.BIDIRECTIONAL, "reverse then confirmation is bidirectional");
        require(r.internalEntry, "bidirectional example starts internally");
        require(r.anchorReached, "bidirectional reaches anchor");
        require(!r.structuralDivergence, "bidirectional is valid");
    }

    private static void branchProbe() {
        ConcreteReasoningGraph g = ConcreteReasoningGraph.synthetic(4,
                new int[][]{{0,1},{1,2},{1,3}}, 0);
        TraversalClassifier.Result r = TraversalClassifier.classify(g, seq(0,1,3,1,2));
        require(r.branchProbing, "branch revisit is branch probing evidence");
        require(!r.structuralDivergence, "branch probing stays on graph");
    }

    private static void offGraphDivergence() {
        TraversalClassifier.Result r = TraversalClassifier.classify(chain5(), seq(4,99,98,0));
        require(r.structuralDivergence, "mostly off-graph route diverges");
        require(r.direction == TraversalClassifier.Direction.DIVERGENT, "divergent direction label");
    }

    private static List<Integer> seq(Integer... nodes) {
        return Arrays.asList(nodes);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
