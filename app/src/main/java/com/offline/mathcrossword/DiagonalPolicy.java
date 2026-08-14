package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.Set;

/**
 * Diagonals are a structural tool, not decoration.
 *
 * v15 policy:
 * - generic/path geometry stays orthogonal;
 * - Chain Converge may keep a true structural diagonal bridge;
 * - Network diagonals are OFF in the player path because the current A/B batch
 *   did not show a quality/reliability benefit; STRUCTURAL_ALL remains available
 *   in the standalone harness for future comparison;
 * - Hypothesis stays orthogonal so branching difficulty comes from candidate
 *   structure rather than visual noise.
 */
final class DiagonalPolicy {
    private DiagonalPolicy() { }

    enum Mode { TARGETED, STRUCTURAL_ALL, ORTHOGONAL_ONLY }

    static Mode mode() {
        String raw = System.getProperty("mathcrossword.diagonalMode", "targeted");
        if ("orthogonal".equalsIgnoreCase(raw) || "off".equalsIgnoreCase(raw))
            return Mode.ORTHOGONAL_ONLY;
        if ("structural".equalsIgnoreCase(raw) || "all".equalsIgnoreCase(raw))
            return Mode.STRUCTURAL_ALL;
        return Mode.TARGETED;
    }

    static boolean allowChainBridge(ReasoningGraph.Family family, int logicLevel) {
        if (mode() == Mode.ORTHOGONAL_ONLY) return false;
        return logicLevel >= 4 && family == ReasoningGraph.Family.CHAIN_CONVERGE;
    }

    static boolean allowNetworkBridge(ReasoningGraph.Family family, int logicLevel) {
        // Small v15 A/B batches showed no Network benefit from retained diagonals;
        // TARGETED therefore disables them in the player path. STRUCTURAL_ALL is
        // kept only for harness comparisons and future families.
        if (mode() != Mode.STRUCTURAL_ALL) return false;
        if (logicLevel < 4) return false;
        return family == ReasoningGraph.Family.NETWORK_HUB
                || family == ReasoningGraph.Family.NETWORK_DENSE;
    }

    static boolean isDiagonal(Slot s) {
        return s != null && s.dx != 0 && s.dy != 0;
    }

    /** A kept diagonal must actually connect at least two already existing number nodes. */
    static boolean isUsefulBridge(Slot s, Set<Pos> occupied, Set<Pos> numberPositions) {
        if (!isDiagonal(s)) return true;
        int sharedNumbers = 0;
        for (int i : new int[]{0, 2, 4}) {
            Pos q = s.p[i];
            if (occupied.contains(q) && numberPositions.contains(q)) sharedNumbers++;
        }
        return sharedNumbers >= 2;
    }
}
