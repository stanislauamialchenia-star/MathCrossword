package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

/** Deterministic graph-theory contracts for abstract and realized puzzle topology. */
public final class GraphTheoryHarness {
    private static int checks = 0;

    private GraphTheoryHarness() { }

    public static void main(String[] args) {
        checkReasoningFamilies();
        checkPuzzleFactorGraph();
        System.out.println("PASS · graph checks=" + checks);
    }

    private static void checkReasoningFamilies() {
        GraphAnalyzer.Metrics chainLong = GraphAnalyzer.analyze(
                ReasoningGraph.chain(ReasoningGraph.Family.CHAIN_LONG, 7));
        require(chainLong.connected(), "CHAIN_LONG must be connected");
        require(chainLong.cycleRank == 0, "CHAIN_LONG must be acyclic");
        require(chainLong.maxDegree <= 2, "CHAIN_LONG should remain path-like");
        require(chainLong.bridges == chainLong.edges, "every CHAIN_LONG edge should be a bridge");

        GraphAnalyzer.Metrics chainBranch = GraphAnalyzer.analyze(
                ReasoningGraph.chain(ReasoningGraph.Family.CHAIN_BRANCH, 8));
        require(chainBranch.connected(), "CHAIN_BRANCH must be connected");
        require(chainBranch.branchNodes >= 1, "CHAIN_BRANCH needs a branching vertex");

        GraphAnalyzer.Metrics chainConverge = GraphAnalyzer.analyze(
                ReasoningGraph.chain(ReasoningGraph.Family.CHAIN_CONVERGE, 8));
        require(chainConverge.connected(), "CHAIN_CONVERGE must be connected");
        require(chainConverge.cycleRank >= 1, "CHAIN_CONVERGE should contain reconvergence");

        GraphAnalyzer.Metrics fork = GraphAnalyzer.analyze(
                ReasoningGraph.hypothesis(ReasoningGraph.Family.HYPOTHESIS_FORK));
        require(fork.connected(), "HYPOTHESIS_FORK must be connected");
        require(fork.branchNodes >= 1, "HYPOTHESIS_FORK needs a pivot branch");

        GraphAnalyzer.Metrics diamond = GraphAnalyzer.analyze(
                ReasoningGraph.hypothesis(ReasoningGraph.Family.HYPOTHESIS_DIAMOND));
        require(diamond.cycleRank >= 1, "HYPOTHESIS_DIAMOND should branch and reconverge");

        GraphAnalyzer.Metrics ring = GraphAnalyzer.analyze(
                ReasoningGraph.network(ReasoningGraph.Family.NETWORK_RING));
        require(ring.connected(), "NETWORK_RING must be connected");
        require(ring.cycleRank == 1, "NETWORK_RING must contain exactly one independent cycle");
        require(ring.bridges == 0, "NETWORK_RING should have no bridges");
        require(ring.articulationPoints == 0, "NETWORK_RING should have no articulation points");

        GraphAnalyzer.Metrics twoCluster = GraphAnalyzer.analyze(
                ReasoningGraph.network(ReasoningGraph.Family.NETWORK_TWO_CLUSTER));
        require(twoCluster.connected(), "NETWORK_TWO_CLUSTER must be connected");
        require(twoCluster.cycleRank >= 2, "NETWORK_TWO_CLUSTER needs multiple independent cycles");
        require(twoCluster.bridges >= 1, "NETWORK_TWO_CLUSTER should expose a cluster bridge");
        require(twoCluster.articulationPoints >= 2,
                "NETWORK_TWO_CLUSTER should expose articulation endpoints around the bridge");

        GraphAnalyzer.Metrics dense = GraphAnalyzer.analyze(
                ReasoningGraph.network(ReasoningGraph.Family.NETWORK_DENSE));
        require(dense.connected(), "NETWORK_DENSE must be connected");
        require(dense.cycleRank > twoCluster.cycleRank,
                "NETWORK_DENSE should have more independent cycles than two-cluster");
        require(dense.maxDegree >= 4, "NETWORK_DENSE needs a high-degree core");
    }

    private static void checkPuzzleFactorGraph() {
        Puzzle p = new Puzzle();
        Slot first = new Slot(0, 0, true, -1);
        PuzzleGenerator.putEquation(p, first, 2, '+', 3, 5);

        // Share only the result value position with the second equation.
        Slot second = new Slot(4, 0, true, 0);
        PuzzleGenerator.putEquation(p, second, 5, '+', 7, 12);
        p.hidden.add(first.p[4]);

        GraphAnalyzer.Metrics factor = GraphAnalyzer.analyze(p);
        require(factor.variableNodes == 5, "two equations sharing one number should have five variable nodes");
        require(factor.factorNodes == 2, "factor graph should have one factor node per equation");
        require(factor.nodes == 7, "factor graph node count mismatch");
        require(factor.edges == 6, "each equation should contribute three variable-factor edges");
        require(factor.connected(), "shared-number factor graph should be connected");
        require(factor.cycleRank == 0, "single shared number should form a factor-tree");
        require(factor.bridges == factor.edges, "factor-tree edges should all be bridges");
        require(factor.variableArticulations >= 1, "shared number should be an articulation variable");
        require(factor.hiddenVariableArticulations >= 1,
                "hidden shared number should be visible as a hidden articulation variable");
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new IllegalStateException("GRAPH FAILURE: " + message);
    }
}
