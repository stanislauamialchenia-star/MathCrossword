package com.offline.mathcrossword;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure data model for generated puzzles. */
final class PuzzleModel {
    static final class Pos {
        final int x, y;
        Pos(int x, int y) { this.x = x; this.y = y; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Pos)) return false;
            Pos p = (Pos) o;
            return x == p.x && y == p.y;
        }
        @Override public int hashCode() { return 31 * x + y; }
    }

    enum Kind { NUMBER, OP, EQUAL }

    enum Orientation {
        H(1, 0, "H"),
        V(0, 1, "V"),
        DIAG_DOWN(1, 1, "D\\"),
        DIAG_UP(1, -1, "D/");

        final int dx, dy;
        final String key;
        Orientation(int dx, int dy, String key) { this.dx = dx; this.dy = dy; this.key = key; }

        static Orientation fromDelta(int dx, int dy) {
            if (dy == 0) return H;
            if (dx == 0) return V;
            return dx * dy > 0 ? DIAG_DOWN : DIAG_UP;
        }
    }

    static final class Cell {
        final Kind kind;
        int number;
        char symbol;
        Cell(int number) { this.kind = Kind.NUMBER; this.number = number; }
        Cell(Kind kind, char symbol) { this.kind = kind; this.symbol = symbol; }
    }

    static final class Slot {
        final Pos[] p = new Pos[5];
        final boolean horizontal; // compatibility with the original H/V builders
        final int sharedIndex;
        final int dx, dy;
        final Orientation orientation;

        Slot(int sx, int sy, boolean horizontal, int sharedIndex) {
            this(sx, sy, horizontal ? 1 : 0, horizontal ? 0 : 1, sharedIndex);
        }

        Slot(int sx, int sy, Orientation orientation, int sharedIndex) {
            this(sx, sy, orientation.dx, orientation.dy, sharedIndex);
        }

        Slot(int sx, int sy, int dx, int dy, int sharedIndex) {
            if (dx == 0 && dy == 0) throw new IllegalArgumentException("Zero direction");
            this.dx = Integer.signum(dx);
            this.dy = Integer.signum(dy);
            this.horizontal = this.dy == 0;
            this.orientation = Orientation.fromDelta(this.dx, this.dy);
            this.sharedIndex = sharedIndex;
            for (int i = 0; i < 5; i++) p[i] = new Pos(sx + this.dx * i, sy + this.dy * i);
        }
    }

    static final class Equation {
        final Pos a, op, b, eq, c;
        final char operator;
        Equation(Slot s, char operator) {
            a = s.p[0]; op = s.p[1]; b = s.p[2]; eq = s.p[3]; c = s.p[4];
            this.operator = operator;
        }
    }

    static final class Tile {
        final int id;
        final int value;
        boolean used;
        Tile(int id, int value) { this.id = id; this.value = value; }
    }

    static final class Puzzle {
        final Map<Pos, Cell> cells = new LinkedHashMap<>();
        final List<Equation> equations = new ArrayList<>();
        final Set<Pos> hidden = new HashSet<>();
        final List<Tile> tiles = new ArrayList<>();
        final Map<Pos, Integer> placedTile = new HashMap<>();
        int minX, maxX, minY, maxY;
        int shapeStyle;
        long seed;
        int logicLevel = 1; // internal 1..5 engine tier
        int calcLevel = 1;  // internal 1..5 engine tier
        int displayLogicLevel = 1; // public 1..10
        int displayCalcLevel = 1;  // public 1..10
        double logicScore = 1.0;   // continuous 1..10
        double calcScore = 1.0;    // continuous 1..10
        int decoyCount = 0;
        int deceptiveDecoyCount = 0;
        int deceptiveDecoySupportMax = 0;
        int contextualDecoyCount = 0;
        int resourceConflictDecoyCount = 0;
        int contextualDecoyConstraintSupportMax = 0;
        int contextualDecoyDepthMax = 0;
        int contextualDecoyInformationGainMax = 0;
        int generatorScore = 0;
        int ratedLogic = 1; // internal 1..5 estimate
        int ratedDisplayLogic = 1; // public 1..10 estimate
        int basicForced = 0;
        int basicRemaining = 0;
        int maxForcedCascade = 0;
        int lookaheadDeductions = 0;
        int depth2Deductions = 0;
        int reasoningDepth = 0;
        int reasoningSteps = 0;
        int branchPivotCount = 0;
        int branchGoodPivotCount = 0;
        int branchSeriousFalseBranches = 0;
        int branchDepth2RefutableBranches = 0;
        int branchDepth2SurvivingBranches = 0;
        int branchMaxWidth = 0;
        int branchMaxInformationGain = 0;
        int reasoningFronts = 0;
        double reasoningFrontBalance = 0.0;
        double reasoningLargestFrontFraction = 0.0;
        int reasoningFrontBottleneckDegree = 0;
        int maxResolvedAfterOneCell = 0;
        int maxAdditionalForcedAfterOneCell = 0;
        double maxResolvedFractionAfterOneCell = 0.0;
        int vulnerableSingleCells = 0;
        int vulnerableRegions = 0;
        int largestVulnerableRegionSize = 0;
        int largestVulnerableRegionEntries = 0;
        int independentCollapseFronts = 0;
        double vulnerabilityOverlap = 0.0;
        int maxResolvedAfterOneEquation = 0;
        double maxResolvedFractionAfterOneEquation = 0.0;
        boolean contradictionKernel = false;
        boolean contradictionKernelAddedDecoy = false;
        int contradictionKernelDepth = 0;
        int contradictionKernelBranchWidth = 0;
        int contradictionKernelPivotDegree = 0;
        int contradictionKernelBranches = 0;
        int contradictionKernelPivots = 0;
        int contradictionKernelDepth2Branches = 0;
        int contradictionKernelDepth3Branches = 0;
        int contradictionKernelDeepBranches = 0;
        int contradictionKernelMaxRemaining = 0;
        String contradictionKernelFamily = "none";
        SolutionStrategy solutionStrategy = SolutionStrategy.MIXED;
        SolutionStrategy generationStrategy = SolutionStrategy.MIXED;
        boolean strategyTargetMatched = true;
        int generatorVersion = PuzzleGenerator.GENERATOR_VERSION;
        int generationStage = 0; // 1 primary, 2 expanded, 3 logic-safe style fallback, 4 rated emergency
        String generatorConstructor = "generic";
        String generatorFamily = "generic";
        String generationStageTimings = "";
        long generationMillis = 0L;
        int generationAttempts = 0;
        int generationRejects = 0;
        String generationRejectSummary = "";
    }

}
