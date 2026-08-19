package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Constructive NETWORK families. Diagonals are retained only as useful closure bridges. */
final class ConstructiveNetworkBuilder {
    private ConstructiveNetworkBuilder() { }

    static Puzzle tryGenerate(GameConfig cfg, long seed, int workMax, GenerationDiagnostics diag) {
        if (cfg.equationCount < 6 || !PuzzleGenerator.hasLatticeOperation(cfg.operations)) return null;
        Random r = new Random(seed ^ 0x9E6C63D0676A9A99L);

        long t = System.nanoTime();
        ReasoningGraph.Family family = chooseFamily(cfg, seed);
        if (diag != null) diag.addStageTime(GenerationDiagnostics.Stage.GRAPH, System.nanoTime() - t);

        t = System.nanoTime();
        List<Character> coreOps = new ArrayList<>();
        for (char op : new char[]{'+', '-', '×', '÷'}) if (PuzzleGenerator.contains(cfg.operations, op)) coreOps.add(op);
        if (coreOps.isEmpty()) return null;
        char coreOp = coreOps.get(r.nextInt(coreOps.size()));
        int[][] values = PuzzleGenerator.buildLatticeValues(coreOp, workMax, cfg.displayCalcLevel, r);
        if (values == null) return null;

        Puzzle p = new Puzzle();
        p.shapeStyle = 400 + Math.floorMod(cfg.shapeStyle, 18);
        p.seed = seed;
        p.logicLevel = cfg.logicLevel;
        p.calcLevel = cfg.calcLevel;
        p.solutionStrategy = cfg.solutionStrategy;
        p.generationStrategy = SolutionStrategy.NETWORK;
        p.generatorVersion = PuzzleGenerator.GENERATOR_VERSION;
        p.generatorFamily = family.id;
        p.generatorConstructor = family.id + "-v2";

        for (int row = 0; row < 3; row++) {
            Slot slot = new Slot(0, row * 2, true, -1);
            PuzzleGenerator.putEquation(p, slot, values[row][0], coreOp, values[row][1], values[row][2]);
        }
        for (int col = 0; col < 3; col++) {
            Slot slot = new Slot(col * 2, 0, false, -1);
            PuzzleGenerator.putEquation(p, slot, values[0][col], coreOp, values[1][col], values[2][col]);
        }

        Set<String> keys = new HashSet<>();
        for (Equation e : p.equations) {
            keys.add(PuzzleGenerator.eqKey(p.cells.get(e.a).number, e.operator,
                    p.cells.get(e.b).number, p.cells.get(e.c).number));
        }

        int remaining = cfg.equationCount - 6;
        int guard = 0;
        while (remaining > 0 && guard++ < 2200) {
            Set<Pos> occupied = new HashSet<>(p.cells.keySet());
            Set<Pos> numberPositions = new HashSet<>();
            for (Map.Entry<Pos, Cell> ce : p.cells.entrySet()) {
                if (ce.getValue().kind == Kind.NUMBER) numberPositions.add(ce.getKey());
            }

            boolean allowDiagonalBridge = DiagonalPolicy.allowNetworkBridge(family, cfg.logicLevel);
            Slot slot = null;

            if (family == ReasoningGraph.Family.NETWORK_HUB && guard % 3 != 0) {
                Pos hub = new Pos(2, 2);
                int[][] dirs = new int[][]{{1,0},{0,1},{-1,0},{0,-1}};
                int[] d = dirs[r.nextInt(dirs.length)];
                slot = new Slot(hub.x, hub.y, d[0], d[1], 0);
                if (!PuzzleGenerator.geometrySlotFits(slot, occupied, numberPositions, 1)) slot = null;
            }

            // Prefer an orthogonal closure. Only if no such closure exists do
            // HUB/DENSE families get a diagonal fallback, and that diagonal must
            // connect at least two existing number nodes.
            if (slot == null) {
                slot = PuzzleGenerator.findBridgeSlot(occupied, numberPositions, r, false);
            }
            if (slot == null && allowDiagonalBridge) {
                Slot bridge = PuzzleGenerator.findBridgeSlot(occupied, numberPositions, r, true);
                if (DiagonalPolicy.isUsefulBridge(bridge, occupied, numberPositions)) slot = bridge;
            }

            if (slot == null || !PuzzleGenerator.slotFitsPuzzle(slot, p)) {
                // Ring and two-cluster families tolerate sparse outward growth,
                // while dense/hub keep preferring closures.
                int branchEvery = family == ReasoningGraph.Family.NETWORK_DENSE ? 7
                        : (family == ReasoningGraph.Family.NETWORK_HUB ? 6 : 4);
                if ((guard % branchEvery) != 0) continue;
                List<Pos> nums = new ArrayList<>(numberPositions);
                if (nums.isEmpty()) break;
                Pos anchor = nums.get(r.nextInt(nums.size()));
                int[][] dirs = new int[][]{{1,0},{0,1},{-1,0},{0,-1}};
                int[] d = dirs[r.nextInt(dirs.length)];
                int childIndex = new int[]{0, 2, 4}[r.nextInt(3)];
                int sx = anchor.x - d[0] * childIndex;
                int sy = anchor.y - d[1] * childIndex;
                slot = new Slot(sx, sy, d[0], d[1], childIndex);
                if (!PuzzleGenerator.slotFitsPuzzle(slot, p)) continue;
            }

            int[] e = PuzzleGenerator.equationForSlot(p, slot, workMax, cfg.operations, r,
                    cfg.displayCalcLevel, keys);
            if (e == null) continue;
            keys.add(PuzzleGenerator.eqKey(e[0], (char) e[1], e[2], e[3]));
            PuzzleGenerator.putEquation(p, slot, e[0], (char) e[1], e[2], e[3]);
            remaining--;
        }
        if (diag != null) diag.addStageTime(GenerationDiagnostics.Stage.ARITHMETIC, System.nanoTime() - t);
        if (remaining != 0) {
            if (diag != null) diag.reject(GenerationDiagnostics.RejectReason.CONSTRUCTIVE_BUILDER_FAILED);
            return null;
        }

        t = System.nanoTime();
        if (!PuzzleGenerator.chooseHiddenWithUniqueSolution(p, cfg.hiddenTarget, workMax, cfg.logicLevel, r, cfg.solutionStrategy, cfg.pathMode, diag)) {
            if (diag != null) {
                diag.addStageTime(GenerationDiagnostics.Stage.HIDDEN_UNIQUENESS, System.nanoTime() - t);
                diag.reject(GenerationDiagnostics.RejectReason.HIDDEN_OR_UNIQUENESS_FAILED);
            }
            return null;
        }
        if (diag != null) diag.addStageTime(GenerationDiagnostics.Stage.HIDDEN_UNIQUENESS, System.nanoTime() - t);
        PuzzleGenerator.computeBounds(p);

        // Keep the constructive gate aligned with the real Network evaluator.
        // Returning a tier-5 board with only two cycles merely guarantees that
        // generateFree will reject it later after paying the full HumanSolver cost.
        t = System.nanoTime();
        LogicAnalyzer.Metrics lm = LogicAnalyzer.analyze(p);
        if (diag != null) diag.addStageTime(GenerationDiagnostics.Stage.HUMAN_ANALYSIS, System.nanoTime() - t);
        int minCycles = cfg.logicLevel >= 5 ? 3 : 2;
        if (lm.cycleRank < minCycles) {
            if (diag != null) diag.reject(GenerationDiagnostics.RejectReason.STRATEGY_MISMATCH);
            return null;
        }
        return p;
    }

    private static ReasoningGraph.Family chooseFamily(GameConfig cfg, long seed) {
        int families = cfg.logicLevel >= 5 ? 4 : 3;
        int n = Math.floorMod((int)(seed ^ (seed >>> 32)), families);
        if (n == 1) return ReasoningGraph.Family.NETWORK_HUB;
        if (n == 2) return ReasoningGraph.Family.NETWORK_TWO_CLUSTER;
        if (n == 3) return ReasoningGraph.Family.NETWORK_DENSE;
        return ReasoningGraph.Family.NETWORK_RING;
    }
}
