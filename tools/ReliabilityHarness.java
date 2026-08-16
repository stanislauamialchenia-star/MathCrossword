package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Release-blocking deterministic smoke/property checks for the puzzle engine.
 *
 * This is intentionally a plain JVM main class rather than an Android test:
 * the mathematical engine must remain testable without an emulator or device.
 */
public final class ReliabilityHarness {
    private static int checks = 0;
    private static int generated = 0;
    private static int rawFreeFailures = 0;
    private static long totalGenerationMs = 0L;

    private ReliabilityHarness() { }

    public static void main(String[] args) {
        Locale.setDefault(Locale.US);
        String mode = args.length == 0 ? "smoke" : args[0].trim().toLowerCase(Locale.US);
        boolean extended = "extended".equals(mode);

        System.out.println("MathCrossword reliability gate · generator v" + PuzzleGenerator.GENERATOR_VERSION
                + " · mode=" + (extended ? "extended" : "smoke"));

        Set<Character> basicOps = ops('+', '-', '×', '÷');
        Set<Character> allOps = ops('+', '-', '×', '÷', '^');

        // Stable PATH anchors across the public difficulty curve.
        int[] pathLevels = extended
                ? new int[]{1, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100}
                : new int[]{1, 40, 60, 80, 100};
        for (int level : pathLevels) {
            Puzzle p = timedPath(level);
            validatePuzzle("PATH L" + level, p);
        }

        // One deterministic representative of every strategy plus the public scale edges.
        FreeCase[] cases = new FreeCase[]{
                new FreeCase(SolutionStrategy.MIXED, 2, 4, 100, basicOps, 0x1001L),
                new FreeCase(SolutionStrategy.DEDUCTION, 4, 5, 100, basicOps, 0x2002L),
                new FreeCase(SolutionStrategy.CHAIN, 6, 6, 500, allOps, 0x3003L),
                new FreeCase(SolutionStrategy.NETWORK, 6, 6, 500, allOps, 0x4004L),
                new FreeCase(SolutionStrategy.HYPOTHESIS, 6, 6, 500, allOps, 0x5005L),
                new FreeCase(SolutionStrategy.MIXED, 8, 8, 1000, allOps, 0x6006L),
                new FreeCase(SolutionStrategy.MIXED, 10, 9, 1000, allOps, 0x7007L)
        };
        for (FreeCase c : cases) {
            Puzzle p = timedFree(c, c.seed);
            validatePuzzle("FREE " + c.strategy + " L" + c.logic, p);
            require(p.solutionStrategy == c.strategy,
                    "requested strategy provenance changed for " + c.strategy + ": " + p.solutionStrategy);
        }

        // Reproducibility is a contract: same base config + seed + bounded retries => same accepted puzzle.
        FreeCase[] deterministic = extended
                ? cases
                : new FreeCase[]{cases[0], cases[2], cases[3], cases[6]};
        for (FreeCase c : deterministic) {
            long base = c.seed ^ 0x5A5A5A5A5A5A5A5AL;
            Puzzle a = timedFree(c, base);
            Puzzle b = timedFree(c, base);
            validatePuzzle("DETERMINISM-A " + c.strategy + " L" + c.logic, a);
            validatePuzzle("DETERMINISM-B " + c.strategy + " L" + c.logic, b);
            require(fingerprint(a).equals(fingerprint(b)),
                    "same base seed produced a different accepted puzzle for " + c.strategy + " L" + c.logic);
        }

        if (extended) {
            // Small property sweep. Fixed base seeds make regressions reproducible in CI.
            for (SolutionStrategy strategy : SolutionStrategy.values()) {
                for (int logic : new int[]{4, 6, 8, 10}) {
                    for (int sample = 0; sample < 2; sample++) {
                        long seed = PuzzleGenerator.mix64(0x13579BDF2468ACE0L
                                ^ ((long) strategy.ordinal() << 44)
                                ^ ((long) logic << 24)
                                ^ sample);
                        FreeCase c = new FreeCase(strategy, logic, Math.min(9, logic + 1),
                                logic >= 7 ? 1000 : 500, allOps, seed);
                        Puzzle p = timedFree(c, seed);
                        validatePuzzle("SWEEP " + strategy + " L" + logic + " #" + sample, p);
                    }
                }
            }
        }

        double avgMs = generated == 0 ? 0.0 : totalGenerationMs / (double) generated;
        System.out.printf(Locale.US,
                "PASS · checks=%d · generated=%d · raw_free_failures=%d · avg_generation_ms=%.1f%n",
                checks, generated, rawFreeFailures, avgMs);
    }

    private static Puzzle timedPath(int level) {
        long t0 = System.nanoTime();
        Puzzle p = PuzzleGenerator.generatePath(level);
        recordGeneration(t0, "PATH L" + level, p, 0);
        return p;
    }

    /** Mirrors the bounded 3-seed retry contract used by the Android Free Play caller. */
    private static Puzzle timedFree(FreeCase c, long baseSeed) {
        long t0 = System.nanoTime();
        RuntimeException last = null;
        for (int retry = 0; retry < 3; retry++) {
            long seed = PuzzleGenerator.mix64(baseSeed + retry * 0x9E3779B97F4A7C15L);
            try {
                Puzzle p = PuzzleGenerator.generateFree(c.logic, c.calc, 1, c.maxNumber, c.operations, seed, c.strategy);
                recordGeneration(t0, "FREE " + c.strategy + " L" + c.logic, p, retry);
                return p;
            } catch (RuntimeException ex) {
                rawFreeFailures++;
                last = ex;
            }
        }
        throw new IllegalStateException("FREE " + c.strategy + " L" + c.logic
                + " failed all three production retries for base seed " + baseSeed, last);
    }

    private static void recordGeneration(long t0, String label, Puzzle p, int retriesUsed) {
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        generated++;
        totalGenerationMs += ms;
        System.out.println("generated " + label + " in " + ms + " ms"
                + (retriesUsed > 0 ? " · retries=" + retriesUsed : "")
                + " · stage=" + p.generationStage
                + " · matched=" + p.strategyTargetMatched
                + " · family=" + safe(p.generatorFamily));
    }

    private static void validatePuzzle(String label, Puzzle p) {
        require(p != null, label + ": generator returned null");
        require(!p.cells.isEmpty(), label + ": no cells");
        require(!p.equations.isEmpty(), label + ": no equations");
        require(!p.hidden.isEmpty(), label + ": no hidden cells");
        require(!p.tiles.isEmpty(), label + ": empty tile bank");
        require(p.generatorVersion == PuzzleGenerator.GENERATOR_VERSION,
                label + ": stale generator version in puzzle provenance");

        for (Equation e : p.equations) {
            Cell a = p.cells.get(e.a);
            Cell b = p.cells.get(e.b);
            Cell c = p.cells.get(e.c);
            require(a != null && b != null && c != null, label + ": equation references a missing number cell");
            require(a.kind == Kind.NUMBER && b.kind == Kind.NUMBER && c.kind == Kind.NUMBER,
                    label + ": equation endpoint is not numeric");
            require(equationTrue(a.number, e.operator, b.number, c.number),
                    label + ": false generated equation " + a.number + " " + e.operator + " " + b.number + " = " + c.number);
        }

        Map<Integer, Integer> needed = new HashMap<>();
        for (Pos pos : p.hidden) {
            Cell cell = p.cells.get(pos);
            require(cell != null, label + ": hidden position missing from cells");
            require(cell.kind == Kind.NUMBER, label + ": hidden non-number cell at " + pos.x + "," + pos.y);
            needed.put(cell.number, needed.getOrDefault(cell.number, 0) + 1);
        }
        Map<Integer, Integer> available = new HashMap<>();
        for (Tile tile : p.tiles) available.put(tile.value, available.getOrDefault(tile.value, 0) + 1);
        for (Map.Entry<Integer, Integer> e : needed.entrySet()) {
            require(available.getOrDefault(e.getKey(), 0) >= e.getValue(),
                    label + ": tile bank lacks solution value " + e.getKey()
                            + " (need " + e.getValue() + ", have " + available.getOrDefault(e.getKey(), 0) + ")");
        }

        int solutions = SolutionCounter.countSolutions(p, 2);
        require(solutions == 1, label + ": expected exactly one solution, got " + solutions);
    }

    private static boolean equationTrue(int a, char op, int b, int c) {
        switch (op) {
            case '+': return (long) a + b == c;
            case '-': return (long) a - b == c;
            case '×':
            case '*': return (long) a * b == c;
            case '÷':
            case '/': return b != 0 && a % b == 0 && a / b == c;
            case '^':
                if (b < 0 || b > 31) return false;
                long v = 1L;
                for (int i = 0; i < b; i++) {
                    v *= a;
                    if (v > Integer.MAX_VALUE || v < Integer.MIN_VALUE) return false;
                }
                return v == c;
            default: return false;
        }
    }

    private static String fingerprint(Puzzle p) {
        StringBuilder out = new StringBuilder();

        List<Pos> cells = new ArrayList<>(p.cells.keySet());
        cells.sort(posComparator());
        for (Pos pos : cells) {
            Cell c = p.cells.get(pos);
            out.append('C').append(pos.x).append(',').append(pos.y).append(':').append(c.kind).append(':');
            if (c.kind == Kind.NUMBER) out.append(c.number);
            else out.append(c.symbol);
            out.append(';');
        }

        List<Pos> hidden = new ArrayList<>(p.hidden);
        hidden.sort(posComparator());
        for (Pos pos : hidden) out.append('H').append(pos.x).append(',').append(pos.y).append(';');

        List<String> eq = new ArrayList<>();
        for (Equation e : p.equations) {
            eq.add(e.a.x + "," + e.a.y + ">" + e.c.x + "," + e.c.y + ":" + e.operator);
        }
        Collections.sort(eq);
        for (String s : eq) out.append('E').append(s).append(';');

        List<Integer> tiles = new ArrayList<>();
        for (Tile t : p.tiles) tiles.add(t.value);
        Collections.sort(tiles);
        out.append("T").append(tiles).append(';');
        out.append("S").append(p.solutionStrategy).append(';');
        out.append("G").append(safe(p.generatorConstructor)).append('/').append(safe(p.generatorFamily)).append(';');
        return out.toString();
    }

    private static Comparator<Pos> posComparator() {
        return (a, b) -> {
            int y = Integer.compare(a.y, b.y);
            return y != 0 ? y : Integer.compare(a.x, b.x);
        };
    }

    private static Set<Character> ops(char... values) {
        LinkedHashSet<Character> out = new LinkedHashSet<>();
        for (char c : values) out.add(c);
        return out;
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new IllegalStateException("RELIABILITY FAILURE: " + message);
    }

    private static final class FreeCase {
        final SolutionStrategy strategy;
        final int logic;
        final int calc;
        final int maxNumber;
        final Set<Character> operations;
        final long seed;

        FreeCase(SolutionStrategy strategy, int logic, int calc, int maxNumber,
                 Set<Character> operations, long seed) {
            this.strategy = strategy;
            this.logic = logic;
            this.calc = calc;
            this.maxNumber = maxNumber;
            this.operations = operations;
            this.seed = seed;
        }
    }
}
