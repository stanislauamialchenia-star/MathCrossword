package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Constructive HYPOTHESIS families.
 *
 * The old hypothesis mode relied on generic rejection sampling. L4 first
 * creates a branchable topology (fork or branch-and-reconverge diamond), then
 * lets the normal hidden/tile/solver pipeline judge the result. v15 also keeps
 * an opt-in L5 contradiction-lattice experiment behind a system property. It is
 * intentionally NOT production-enabled yet: the benchmark still favors the
 * proven generic L5 path. All Hypothesis geometry remains orthogonal.
 */
final class ConstructiveHypothesisBuilder {
    private ConstructiveHypothesisBuilder() { }

    static Puzzle tryGenerate(GameConfig cfg, long seed, int workMax, GenerationDiagnostics diag) {
        if (cfg.logicLevel < 4 || cfg.logicLevel > 5 || cfg.equationCount < 6) return null;
        Random r = new Random(seed ^ 0xD1342543DE82EF95L);
        if (cfg.logicLevel >= 5) return tryGenerateL5Contradiction(cfg, seed, workMax, diag, r);

        long t = System.nanoTime();
        ReasoningGraph.Family family = chooseFamily(cfg, seed);
        ReasoningGraph graph = ReasoningGraph.hypothesis(family);
        List<Slot> slots = buildGeometry(cfg.equationCount, family, r);
        if (diag != null) diag.addStageTime(GenerationDiagnostics.Stage.GRAPH, System.nanoTime() - t);
        if (slots == null || slots.size() != cfg.equationCount) {
            if (diag != null) diag.reject(GenerationDiagnostics.RejectReason.GEOMETRY_FAILED);
            return null;
        }

        Puzzle p = new Puzzle();
        p.shapeStyle = 500 + Math.floorMod(cfg.shapeStyle, 18);
        p.seed = seed;
        p.logicLevel = cfg.logicLevel;
        p.calcLevel = cfg.calcLevel;
        p.solutionStrategy = cfg.solutionStrategy;
        p.generationStrategy = SolutionStrategy.HYPOTHESIS;
        p.generatorVersion = PuzzleGenerator.GENERATOR_VERSION;
        p.generatorFamily = family.id;
        p.generatorConstructor = family.id + "-v1";

        t = System.nanoTime();
        Set<String> keys = new HashSet<>();
        int[] root = PuzzleGenerator.randomEquation(workMax, cfg.operations, r,
                (int)Math.floorMod(seed, 113), cfg.displayCalcLevel);
        PuzzleGenerator.putEquation(p, slots.get(0), root[0], (char)root[1], root[2], root[3]);
        keys.add(PuzzleGenerator.eqKey(root[0], (char)root[1], root[2], root[3]));

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
            keys.add(PuzzleGenerator.eqKey(e[0], (char)e[1], e[2], e[3]));
            PuzzleGenerator.putEquation(p, slots.get(i), e[0], (char)e[1], e[2], e[3]);
        }
        if (diag != null) diag.addStageTime(GenerationDiagnostics.Stage.ARITHMETIC, System.nanoTime() - t);

        t = System.nanoTime();
        if (!PuzzleGenerator.chooseHiddenWithUniqueSolution(p, cfg.hiddenTarget, workMax,
                cfg.logicLevel, r, cfg.solutionStrategy, cfg.pathMode, diag)) {
            if (diag != null) {
                diag.addStageTime(GenerationDiagnostics.Stage.HIDDEN_UNIQUENESS, System.nanoTime() - t);
                diag.reject(GenerationDiagnostics.RejectReason.HIDDEN_OR_UNIQUENESS_FAILED);
            }
            return null;
        }
        if (diag != null) diag.addStageTime(GenerationDiagnostics.Stage.HIDDEN_UNIQUENESS,
                System.nanoTime() - t);
        PuzzleGenerator.computeBounds(p);

        // Sanity check the abstract intent survived the family choice. The exact
        // hypothesis signature is still judged by HypothesisEvaluator later.
        if (family == ReasoningGraph.Family.HYPOTHESIS_FORK && graph.maxDegree() < 3) return null;
        if ((family == ReasoningGraph.Family.HYPOTHESIS_DIAMOND
                || family == ReasoningGraph.Family.HYPOTHESIS_CONTRADICTION)
                && graph.cycleRank() < 1) return null;
        return p;
    }

    /** Experimental L5: arithmetic-first contradiction scaffold.
     *
     * The first prototype tried to close an already-numbered diamond and spent
     * most failures in equation assignment. v15 instead builds a small exact
     * arithmetic lattice first, then asks the HYPOTHESIS hidden/tile pipeline to
     * create the delayed false branch. Extra equations grow outward only, so the
     * core remains stable and cheap to construct. */
    private static Puzzle tryGenerateL5Contradiction(GameConfig cfg, long seed, int workMax,
                                                      GenerationDiagnostics diag, Random r) {
        if (!PuzzleGenerator.hasLatticeOperation(cfg.operations)) return null;
        long t = System.nanoTime();
        ReasoningGraph graph = ReasoningGraph.hypothesis(ReasoningGraph.Family.HYPOTHESIS_CONTRADICTION);
        if (diag != null) diag.addStageTime(GenerationDiagnostics.Stage.GRAPH, System.nanoTime() - t);

        t = System.nanoTime();
        List<Character> coreOps = new ArrayList<>();
        for (char op : new char[]{'+','-','×','÷'}) if (PuzzleGenerator.contains(cfg.operations, op)) coreOps.add(op);
        if (coreOps.isEmpty()) return null;
        char coreOp = coreOps.get(r.nextInt(coreOps.size()));
        int[][] values = PuzzleGenerator.buildLatticeValues(coreOp, workMax, cfg.displayCalcLevel, r);
        if (values == null) return null;

        Puzzle p = new Puzzle();
        p.shapeStyle = 550 + Math.floorMod(cfg.shapeStyle, 18);
        p.seed = seed;
        p.logicLevel = cfg.logicLevel;
        p.calcLevel = cfg.calcLevel;
        p.solutionStrategy = cfg.solutionStrategy;
        p.generationStrategy = SolutionStrategy.HYPOTHESIS;
        p.generatorVersion = PuzzleGenerator.GENERATOR_VERSION;
        p.generatorFamily = ReasoningGraph.Family.HYPOTHESIS_CONTRADICTION.id;
        p.generatorConstructor = "hypothesis-contradiction-lattice-v2";

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
        while (remaining > 0 && guard++ < 1500) {
            Set<Pos> occupied = new HashSet<>(p.cells.keySet());
            Set<Pos> numberPositions = new HashSet<>();
            for (java.util.Map.Entry<Pos, Cell> ce : p.cells.entrySet()) {
                if (ce.getValue().kind == Kind.NUMBER) numberPositions.add(ce.getKey());
            }
            List<Pos> nums = new ArrayList<>(numberPositions);
            if (nums.isEmpty()) break;
            Pos anchor = nums.get(r.nextInt(nums.size()));
            int[][] dirs = {{1,0},{0,1},{-1,0},{0,-1}};
            int[] d = dirs[r.nextInt(dirs.length)];
            int childIndex = new int[]{0,2,4}[r.nextInt(3)];
            Slot slot = new Slot(anchor.x - d[0]*childIndex, anchor.y - d[1]*childIndex,
                    d[0], d[1], childIndex);
            if (!PuzzleGenerator.geometrySlotFits(slot, occupied, numberPositions, 1)
                    || !PuzzleGenerator.slotFitsPuzzle(slot, p)) continue;
            int[] e = PuzzleGenerator.equationForSlot(p, slot, workMax, cfg.operations, r, cfg.displayCalcLevel, keys);
            if (e == null) continue;
            keys.add(PuzzleGenerator.eqKey(e[0], (char)e[1], e[2], e[3]));
            PuzzleGenerator.putEquation(p, slot, e[0], (char)e[1], e[2], e[3]);
            remaining--;
        }
        if (diag != null) diag.addStageTime(GenerationDiagnostics.Stage.ARITHMETIC, System.nanoTime() - t);
        if (remaining != 0) return null;

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

        LogicAnalyzer.Metrics lm = LogicAnalyzer.analyze(p);
        if (lm.cycleRank < Math.min(2, Math.max(1, graph.cycleRank()))) return null;
        return p;
    }

    private static ReasoningGraph.Family chooseFamily(GameConfig cfg, long seed) {
        if (cfg.logicLevel >= 5) {
            return Math.floorMod((int)(seed ^ (seed >>> 32)), 4) == 0
                    ? ReasoningGraph.Family.HYPOTHESIS_DIAMOND
                    : ReasoningGraph.Family.HYPOTHESIS_CONTRADICTION;
        }
        return Math.floorMod((int)(seed ^ (seed >>> 32)), 3) == 0
                ? ReasoningGraph.Family.HYPOTHESIS_DIAMOND
                : ReasoningGraph.Family.HYPOTHESIS_FORK;
    }

    private static List<Slot> buildGeometry(int count, ReasoningGraph.Family family, Random r) {
        List<Slot> out = new ArrayList<>();
        Set<Pos> occupied = new HashSet<>();
        Set<Pos> numbers = new HashSet<>();
        List<Pos> frontier = new ArrayList<>();

        // Shared root.
        add(new Slot(0, 0, true, -1), out, occupied, numbers, frontier);
        // Two independent branches from the root endpoints.
        addIfFits(new Slot(0, 0, false, 0), out, occupied, numbers, frontier, 1);
        addIfFits(new Slot(4, 0, false, 0), out, occupied, numbers, frontier, 1);

        if (family == ReasoningGraph.Family.HYPOTHESIS_CONTRADICTION) {
            // Orthogonal two-layer branch/reconvergence core. No diagonal is
            // used: the difficulty must come from delayed candidate viability.
            addIfFits(new Slot(0, 0, false, 0), out, occupied, numbers, frontier, 1);
            addIfFits(new Slot(4, 0, false, 0), out, occupied, numbers, frontier, 1);
            addIfFits(new Slot(0, 4, true, 0), out, occupied, numbers, frontier, 2);
            addIfFits(new Slot(0, 2, true, 0), out, occupied, numbers, frontier, 2);
            addIfFits(new Slot(2, 0, false, 0), out, occupied, numbers, frontier, 2);
        } else if (family == ReasoningGraph.Family.HYPOTHESIS_DIAMOND) {
            // Reconverge the two branches. This is deliberately orthogonal.
            addIfFits(new Slot(0, 4, true, 0), out, occupied, numbers, frontier, 2);
        } else {
            // Fork: extend the two branch ends away from one another.
            addIfFits(new Slot(-4, 4, true, 4), out, occupied, numbers, frontier, 1);
            addIfFits(new Slot(4, 4, true, 0), out, occupied, numbers, frontier, 1);
        }

        int guard = 0;
        while (out.size() < count && guard++ < 1800) {
            if (frontier.isEmpty()) break;
            Pos anchor = frontier.get(r.nextInt(frontier.size()));
            int[][] dirs = {{1,0},{0,1},{-1,0},{0,-1}};
            int[] d = dirs[r.nextInt(dirs.length)];
            int childIndex = new int[]{0,4,2}[r.nextInt(3)];
            Slot s = new Slot(anchor.x - d[0] * childIndex,
                    anchor.y - d[1] * childIndex, d[0], d[1], childIndex);
            if (!PuzzleGenerator.geometrySlotFits(s, occupied, numbers, 1)) continue;
            add(s, out, occupied, numbers, frontier);
        }
        return out.size() == count ? out : null;
    }

    private static void addIfFits(Slot s, List<Slot> out, Set<Pos> occupied,
                                  Set<Pos> numbers, List<Pos> frontier, int minShared) {
        if (PuzzleGenerator.geometrySlotFits(s, occupied, numbers, minShared)) {
            add(s, out, occupied, numbers, frontier);
        }
    }

    private static void add(Slot s, List<Slot> out, Set<Pos> occupied,
                            Set<Pos> numbers, List<Pos> frontier) {
        out.add(s);
        for (int i = 0; i < 5; i++) {
            occupied.add(s.p[i]);
            if (i == 0 || i == 2 || i == 4) numbers.add(s.p[i]);
        }
        frontier.add(s.p[0]);
        frontier.add(s.p[4]);
        if (frontier.size() > 40) frontier.remove(0);
    }
}
