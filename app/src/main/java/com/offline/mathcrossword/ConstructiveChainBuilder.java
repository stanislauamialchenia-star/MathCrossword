package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Constructive CHAIN families: long, branch and converge. */
final class ConstructiveChainBuilder {
    private ConstructiveChainBuilder() { }

    static Puzzle tryGenerate(GameConfig cfg, long seed, int workMax, GenerationDiagnostics diag) {
        if (cfg.equationCount < 4) return null;
        Random r = new Random(seed ^ 0xC6BC279692B5CC83L);

        long t = System.nanoTime();
        ReasoningGraph.Family family = chooseFamily(cfg, seed);
        ReasoningGraph abstractGraph = ReasoningGraph.chain(family, cfg.equationCount);
        List<Slot> slots = buildFamilyGeometry(cfg.equationCount, family, cfg.logicLevel, r);
        if (diag != null) diag.addStageTime(GenerationDiagnostics.Stage.GRAPH, System.nanoTime() - t);
        if (slots == null || slots.size() != cfg.equationCount) {
            if (diag != null) diag.reject(GenerationDiagnostics.RejectReason.GEOMETRY_FAILED);
            return null;
        }

        Puzzle p = new Puzzle();
        p.shapeStyle = 300 + Math.floorMod(cfg.shapeStyle, 18);
        p.seed = seed;
        p.logicLevel = cfg.logicLevel;
        p.calcLevel = cfg.calcLevel;
        p.solutionStrategy = cfg.solutionStrategy;
        p.generationStrategy = SolutionStrategy.CHAIN;
        p.generatorVersion = PuzzleGenerator.GENERATOR_VERSION;
        p.generatorFamily = family.id;
        p.generatorConstructor = family.id + "-v2";

        t = System.nanoTime();
        Set<String> keys = new HashSet<>();
        int[] root = PuzzleGenerator.randomEquation(workMax, cfg.operations, r,
                (int) Math.floorMod(seed, 97), cfg.displayCalcLevel);
        PuzzleGenerator.putEquation(p, slots.get(0), root[0], (char) root[1], root[2], root[3]);
        keys.add(PuzzleGenerator.eqKey(root[0], (char) root[1], root[2], root[3]));

        for (int i = 1; i < slots.size(); i++) {
            int[] e = PuzzleGenerator.equationForSlot(p, slots.get(i), workMax,
                    cfg.operations, r, cfg.displayCalcLevel, keys);
            if (e == null) {
                if (diag != null) {
                    diag.addStageTime(GenerationDiagnostics.Stage.ARITHMETIC, System.nanoTime() - t);
                    diag.reject(GenerationDiagnostics.RejectReason.EQUATION_ASSIGNMENT_FAILED);
                }
                return null;
            }
            keys.add(PuzzleGenerator.eqKey(e[0], (char) e[1], e[2], e[3]));
            PuzzleGenerator.putEquation(p, slots.get(i), e[0], (char) e[1], e[2], e[3]);
        }
        if (diag != null) diag.addStageTime(GenerationDiagnostics.Stage.ARITHMETIC, System.nanoTime() - t);

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

        // Abstract topology is intentionally independent from arithmetic. These
        // metrics are cheap sanity checks that the requested family survived mapping.
        if (family == ReasoningGraph.Family.CHAIN_LONG && abstractGraph.maxDegree() > 2) return null;
        if (family == ReasoningGraph.Family.CHAIN_CONVERGE && abstractGraph.maxDegree() < 3) return null;
        return p;
    }

    private static ReasoningGraph.Family chooseFamily(GameConfig cfg, long seed) {
        if (cfg.logicLevel <= 2) return ReasoningGraph.Family.CHAIN_LONG;
        int n = Math.floorMod((int) (seed ^ (seed >>> 32)), cfg.logicLevel >= 4 ? 3 : 2);
        if (n == 1) return ReasoningGraph.Family.CHAIN_BRANCH;
        if (n == 2) return ReasoningGraph.Family.CHAIN_CONVERGE;
        return ReasoningGraph.Family.CHAIN_LONG;
    }

    private static List<Slot> buildFamilyGeometry(int count, ReasoningGraph.Family family,
                                                   int logicLevel, Random r) {
        List<Slot> out = new ArrayList<>();
        Set<Pos> occupied = new HashSet<>();
        Set<Pos> numbers = new HashSet<>();
        List<Pos> endpoints = new ArrayList<>();

        // v14: ordinary Chain growth is intentionally orthogonal. A diagonal is
        // permitted only as a real convergence bridge when orthogonal closure
        // is impossible; this keeps the field readable without removing a useful
        // structural tool from the generator.
        int[][] dirs = new int[][]{{1,0},{0,1},{-1,0},{0,-1}};

        int[] d0 = dirs[r.nextInt(dirs.length)];
        Slot root = new Slot(0, 0, d0[0], d0[1], -1);
        addSlot(root, out, occupied, numbers, endpoints);

        int guard = 0;
        while (out.size() < count && guard++ < 1600) {
            Slot candidate = null;

            if (family == ReasoningGraph.Family.CHAIN_CONVERGE && out.size() >= 4 && out.size() % 3 == 1) {
                candidate = PuzzleGenerator.findBridgeSlot(occupied, numbers, r, false);
                if (candidate == null && DiagonalPolicy.allowChainBridge(family, logicLevel)) {
                    Slot diagonalFallback = PuzzleGenerator.findBridgeSlot(occupied, numbers, r, true);
                    if (DiagonalPolicy.isUsefulBridge(diagonalFallback, occupied, numbers)) candidate = diagonalFallback;
                }
                if (candidate != null && !PuzzleGenerator.geometrySlotFits(candidate, occupied, numbers, 2)) candidate = null;
            }

            if (candidate == null) {
                Pos anchor;
                if (family == ReasoningGraph.Family.CHAIN_LONG) {
                    anchor = endpoints.get(endpoints.size() - 1);
                } else if (family == ReasoningGraph.Family.CHAIN_BRANCH) {
                    int bound = Math.max(1, endpoints.size() - 1);
                    anchor = endpoints.get(r.nextInt(bound));
                } else {
                    anchor = endpoints.get(r.nextInt(endpoints.size()));
                }

                int[] d = dirs[r.nextInt(dirs.length)];
                candidate = new Slot(anchor.x, anchor.y, d[0], d[1], 0);
                if (!PuzzleGenerator.geometrySlotFits(candidate, occupied, numbers, 1)) continue;
            }
            addSlot(candidate, out, occupied, numbers, endpoints);
        }
        return out.size() == count ? out : null;
    }

    private static void addSlot(Slot s, List<Slot> out, Set<Pos> occupied,
                                Set<Pos> numbers, List<Pos> endpoints) {
        out.add(s);
        for (int i = 0; i < 5; i++) {
            occupied.add(s.p[i]);
            if (i == 0 || i == 2 || i == 4) numbers.add(s.p[i]);
        }
        endpoints.add(s.p[4]);
        if (endpoints.size() > 24) endpoints.remove(0);
    }
}
