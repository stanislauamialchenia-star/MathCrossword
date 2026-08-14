package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

final class LogicAnalyzer {
    static final class Metrics {
        int hidden;
        int singletons;
        int directSingleCells;
        int crossHidden;
        int ambiguousEquations;
        int intersectionTightening;
        int smallDomains;
        int maxDomain;
        int cycleRank;
        double averageDomain;
    }

    static Metrics analyze(Puzzle p) {
        Metrics m = new Metrics();
        m.hidden = p.hidden.size();
        Map<Integer, Integer> bank = bankCounts(p);
        List<Integer> values = new ArrayList<>(bank.keySet());
        double sum = 0;

        for (Equation e : p.equations) {
            int hidden = 0;
            if (p.hidden.contains(e.a)) hidden++;
            if (p.hidden.contains(e.b)) hidden++;
            if (p.hidden.contains(e.c)) hidden++;
            if (hidden >= 2) m.ambiguousEquations++;
        }

        for (Pos pos : p.hidden) {
            List<Equation> touching = equationsFor(p, pos);
            if (touching.size() >= 2) m.crossHidden++;

            Set<Integer> combined = domainForCell(p, pos, values, bank, null);
            int size = combined.size();
            sum += size;
            m.maxDomain = Math.max(m.maxDomain, size);
            if (size == 1) m.singletons++;
            if (size <= 2) m.smallDomains++;

            boolean directSingle = false;
            int minIndividual = Integer.MAX_VALUE;
            if (touching.size() >= 2) {
                for (Equation e : touching) {
                    int one = domainForCell(p, pos, values, bank, e).size();
                    minIndividual = Math.min(minIndividual, one);
                    if (one == 1) directSingle = true;
                }
                if (size > 0 && minIndividual != Integer.MAX_VALUE && size < minIndividual) {
                    m.intersectionTightening++;
                }
            } else if (touching.size() == 1) {
                int one = domainForCell(p, pos, values, bank, touching.get(0)).size();
                if (one == 1) directSingle = true;
            }
            if (directSingle) m.directSingleCells++;
        }
        m.averageDomain = m.hidden == 0 ? 0 : sum / m.hidden;
        m.cycleRank = hiddenConstraintCycleRank(p);
        return m;
    }

    static int hiddenConstraintCycleRank(Puzzle p) {
        List<Pos> hidden = new ArrayList<>(p.hidden);
        Map<Pos, Integer> posIndex = new HashMap<>();
        for (int i = 0; i < hidden.size(); i++) posIndex.put(hidden.get(i), i);

        List<Equation> active = new ArrayList<>();
        for (Equation e : p.equations) {
            int n = 0;
            if (p.hidden.contains(e.a)) n++;
            if (p.hidden.contains(e.b)) n++;
            if (p.hidden.contains(e.c)) n++;
            if (n >= 2) active.add(e);
        }
        int nPos = hidden.size();
        int nEq = active.size();
        int nodes = nPos + nEq;
        if (nodes == 0) return 0;

        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < nodes; i++) adj.add(new ArrayList<>());
        int edges = 0;
        for (int i = 0; i < active.size(); i++) {
            Equation e = active.get(i);
            int eqNode = nPos + i;
            for (Pos q : new Pos[]{e.a, e.b, e.c}) {
                Integer pi = posIndex.get(q);
                if (pi == null) continue;
                adj.get(pi).add(eqNode);
                adj.get(eqNode).add(pi);
                edges++;
            }
        }

        boolean[] seen = new boolean[nodes];
        int components = 0;
        int usedNodes = 0;
        for (int i = 0; i < nodes; i++) {
            if (adj.get(i).isEmpty() || seen[i]) continue;
            components++;
            ArrayList<Integer> stack = new ArrayList<>();
            stack.add(i); seen[i] = true;
            while (!stack.isEmpty()) {
                int v = stack.remove(stack.size() - 1);
                usedNodes++;
                for (int to : adj.get(v)) if (!seen[to]) { seen[to] = true; stack.add(to); }
            }
        }
        if (usedNodes == 0) return 0;
        return Math.max(0, edges - usedNodes + components);
    }

    static boolean cheapStaticPrefilter(Metrics m, int logicLevel) {
        if (logicLevel <= 3) return true;
        if (logicLevel == 4) {
            return m.hidden >= 8
                    && m.ambiguousEquations >= 4
                    && m.crossHidden >= 3
                    && m.singletons <= 2
                    && m.directSingleCells <= 2
                    && m.averageDomain >= 1.85
                    && m.cycleRank >= 1;
        }
        return m.hidden >= 10
                && m.ambiguousEquations >= 5
                && m.crossHidden >= 4
                && m.singletons == 0
                && m.directSingleCells <= 1
                && m.averageDomain >= 2.20
                && m.cycleRank >= 2;
    }

    static boolean acceptForLevel(Metrics m, HumanSolver.Metrics h, int logicLevel) {
        logicLevel = PuzzleGenerator.clamp(logicLevel, 1, 5);
        if (logicLevel == 1) return true;
        if (logicLevel == 2) return m.ambiguousEquations >= 1;
        if (logicLevel == 3) {
            boolean resistsOpening = h == null || (h.basicSolvedFraction <= 0.55
                    && h.basicRemaining >= Math.max(2, m.hidden / 3));
            return m.hidden >= 5
                    && m.ambiguousEquations >= 2
                    && m.crossHidden >= 1
                    && m.singletons <= Math.max(3, (m.hidden + 1) / 2)
                    && resistsOpening;
        }
        if (logicLevel == 4) {
            boolean sustained = h.reasoningSteps >= 2 || h.stuckRemaining >= Math.max(4, m.hidden / 3);
            boolean noBigCollapse = h.maxForcedCascade <= Math.max(3, m.hidden / 2);
            return m.hidden >= 8
                    && m.ambiguousEquations >= 4
                    && m.crossHidden >= 3
                    && m.singletons <= 1
                    && m.directSingleCells <= 1
                    && m.averageDomain >= 2.00
                    && m.cycleRank >= 1
                    && h.initialSingletons <= 1
                    && h.basicSolvedFraction <= 0.22
                    && h.basicRemaining >= Math.max(6, (m.hidden * 3) / 4)
                    && h.initialAverageDomain >= 2.20
                    && h.initialBranchCells >= Math.max(5, (m.hidden * 2) / 3)
                    && sustained && noBigCollapse;
        }
        boolean sustained = h.reasoningSteps >= 3 || h.stuckRemaining >= Math.max(6, m.hidden / 2);
        boolean deepEnough = h.maxReasoningDepth >= 2 || h.reasoningSteps >= 3 || h.stuckRemaining >= Math.max(7, (m.hidden * 3) / 5);
        boolean noBigCollapse = h.maxForcedCascade <= Math.max(4, (m.hidden * 3) / 5);
        return m.hidden >= 10
                && m.ambiguousEquations >= 5
                && m.crossHidden >= 4
                && m.singletons == 0
                && m.directSingleCells == 0
                && m.averageDomain >= 2.45
                && m.cycleRank >= 3
                && h.initialSingletons == 0
                && h.basicForced == 0
                && h.basicRemaining >= Math.max(9, (m.hidden * 4) / 5)
                && h.initialAverageDomain >= 2.65
                && h.initialBranchCells >= Math.max(8, (m.hidden * 4) / 5)
                && h.maxBranchWidth >= 3
                && sustained && deepEnough && noBigCollapse;
    }

    static int qualityScore(Metrics m, HumanSolver.Metrics h, int logicLevel) {
        int score = 0;
        score += m.ambiguousEquations * 22;
        score += m.crossHidden * 20;
        score += m.intersectionTightening * 30;
        score += m.cycleRank * (logicLevel >= 5 ? 70 : 45);
        score += (int) Math.round(m.averageDomain * 28.0);
        score += h.basicRemaining * 22;
        score += h.lookaheadDeductions * 70;
        score += h.lookaheadEliminations * 9;
        score += h.depth2Deductions * 145;
        score += h.depth2Eliminations * 14;
        score += h.reasoningSteps * 80;
        score += h.maxReasoningDepth * 100;
        score += h.stuckRemaining * 10;
        score += h.initialBranchCells * 8;
        score += Math.min(8, h.maxBranchWidth) * 10;
        score += (int) Math.round(h.initialAverageDomain * 20.0);
        score -= h.initialSingletons * 75;
        score -= h.basicForced * (logicLevel >= 5 ? 80 : 45);
        score -= m.directSingleCells * 45;
        score -= h.maxForcedCascade * (logicLevel >= 5 ? 22 : 12);
        if (logicLevel >= 4 && h.basicSolvedFraction > 0.30) score -= 200;
        if (logicLevel >= 5 && h.initialSingletons == 0) score += 100;
        if (logicLevel >= 5 && h.basicForced == 0) score += 120;
        if (logicLevel >= 5 && h.maxReasoningDepth >= 2) score += 160;
        if (logicLevel >= 5 && h.reasoningSteps >= 4) score += 180;
        if (logicLevel >= 4 && h.reasoningSteps < 2 && h.stuckRemaining == 0) score -= 180;
        return score;
    }

    static int estimateLevel(Metrics m, HumanSolver.Metrics h) {
        if (m.hidden <= 3 || h.basicRemaining <= 1) return 1;
        if (h.basicSolvedFraction >= 0.70 || h.basicRemaining < Math.max(3, m.hidden / 3)) return 2;
        if (h.initialSingletons >= 2 || h.basicSolvedFraction > 0.25
                || h.basicRemaining < Math.max(5, (m.hidden * 2) / 3)) return 3;
        boolean l5 = m.hidden >= 10 && m.cycleRank >= 3
                && h.initialSingletons == 0 && h.basicForced == 0
                && h.initialAverageDomain >= 2.65
                && (h.reasoningSteps >= 3 || h.stuckRemaining >= Math.max(6, m.hidden / 2))
                && (h.maxReasoningDepth >= 2 || h.reasoningSteps >= 3 || h.stuckRemaining >= Math.max(7, (m.hidden * 3) / 5))
                && h.maxForcedCascade <= Math.max(4, (m.hidden * 3) / 5);
        if (l5) return 5;
        return 4;
    }

    static int plausibilityScoreForValue(Puzzle p, int value, Map<Integer, Integer> bank) {
        if (bank.getOrDefault(value, 0) <= 0) return 0;
        List<Integer> values = new ArrayList<>(bank.keySet());
        int score = 0;
        for (Pos pos : p.hidden) {
            Set<Integer> domain = domainForCell(p, pos, values, bank, null);
            if (!domain.contains(value)) continue;
            int degree = equationsFor(p, pos).size();
            score += degree >= 2 ? 3 : 1;
        }
        return score;
    }

    static Map<Integer, Integer> bankCounts(Puzzle p) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (Tile t : p.tiles) counts.put(t.value, counts.getOrDefault(t.value, 0) + 1);
        return counts;
    }

    static List<Equation> equationsFor(Puzzle p, Pos pos) {
        List<Equation> out = new ArrayList<>();
        for (Equation e : p.equations) {
            if (pos.equals(e.a) || pos.equals(e.b) || pos.equals(e.c)) out.add(e);
        }
        return out;
    }

    static Set<Integer> domainForCell(Puzzle p, Pos pos, List<Integer> values,
                                      Map<Integer, Integer> bank, Equation onlyEquation) {
        Set<Integer> out = new LinkedHashSet<>();
        List<Equation> equations = onlyEquation == null ? equationsFor(p, pos) : Collections.singletonList(onlyEquation);
        for (int value : values) {
            if (bank.getOrDefault(value, 0) <= 0) continue;
            boolean ok = true;
            for (Equation e : equations) {
                if (!equationHasSupport(p, e, pos, value, bank)) { ok = false; break; }
            }
            if (ok) out.add(value);
        }
        return out;
    }

    static boolean equationHasSupport(Puzzle p, Equation e, Pos candidatePos, int candidateValue,
                                      Map<Integer, Integer> bank) {
        Map<Integer, Integer> remaining = new LinkedHashMap<>(bank);
        int have = remaining.getOrDefault(candidateValue, 0);
        if (have <= 0) return false;
        remaining.put(candidateValue, have - 1);

        Integer a = analysisValue(p, e.a, candidatePos, candidateValue);
        Integer b = analysisValue(p, e.b, candidatePos, candidateValue);
        Integer c = analysisValue(p, e.c, candidatePos, candidateValue);

        int unknown = (a == null ? 1 : 0) + (b == null ? 1 : 0) + (c == null ? 1 : 0);
        if (unknown == 0) return PuzzleGenerator.eval(a, e.operator, b) == c;

        List<Integer> vals = new ArrayList<>(remaining.keySet());
        if (unknown == 1) {
            for (int x : vals) {
                if (remaining.getOrDefault(x, 0) <= 0) continue;
                int aa = a == null ? x : a;
                int bb = b == null ? x : b;
                int cc = c == null ? x : c;
                if (PuzzleGenerator.eval(aa, e.operator, bb) == cc) return true;
            }
            return false;
        }

        if (unknown == 2) {
            for (int x : vals) {
                int cx = remaining.getOrDefault(x, 0);
                if (cx <= 0) continue;
                remaining.put(x, cx - 1);
                for (int y : vals) {
                    if (remaining.getOrDefault(y, 0) <= 0) continue;
                    Integer aa = a, bb = b, cc = c;
                    if (aa == null) aa = x;
                    else if (bb == null) bb = x;
                    else cc = x;
                    if (a == null && b == null) bb = y;
                    else if (a == null && c == null) cc = y;
                    else if (b == null && c == null) cc = y;
                    if (PuzzleGenerator.eval(aa, e.operator, bb) == cc) {
                        remaining.put(x, cx);
                        return true;
                    }
                }
                remaining.put(x, cx);
            }
            return false;
        }

        for (int x : vals) {
            int cx = remaining.getOrDefault(x, 0);
            if (cx <= 0) continue;
            remaining.put(x, cx - 1);
            for (int y : vals) {
                int cy = remaining.getOrDefault(y, 0);
                if (cy <= 0) continue;
                remaining.put(y, cy - 1);
                for (int z : vals) {
                    if (remaining.getOrDefault(z, 0) <= 0) continue;
                    if (PuzzleGenerator.eval(x, e.operator, y) == z) {
                        remaining.put(y, cy);
                        remaining.put(x, cx);
                        return true;
                    }
                }
                remaining.put(y, cy);
            }
            remaining.put(x, cx);
        }
        return false;
    }

    static Integer analysisValue(Puzzle p, Pos q, Pos candidatePos, int candidateValue) {
        if (q.equals(candidatePos)) return candidateValue;
        if (!p.hidden.contains(q)) {
            Cell c = p.cells.get(q);
            return c == null ? null : c.number;
        }
        return null;
    }
}

// v6.2: a bounded human-like solver used as a difficulty instrument.
// It never reads the hidden answer. It first propagates forced candidates,
// then tries contradiction look-ahead at depth 1 and depth 2. The generator
// uses the trace (not only uniqueness) to reject puzzles that collapse by
// cheap elimination.
