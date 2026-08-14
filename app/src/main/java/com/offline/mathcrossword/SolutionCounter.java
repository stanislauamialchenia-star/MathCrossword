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

final class SolutionCounter {
    static int countSolutions(Puzzle p, int stopAt) {
        HumanSolver.State state = HumanSolver.initialState(p);
        return searchState(p, state, stopAt);
    }


    /**
     * The generator already knows one valid assignment: the numbers stored in the hidden cells.
     * During generation we therefore do not need to rediscover that solution. We only search for
     * a second, different assignment. This is noticeably cheaper on large Network boards.
     */
    static boolean hasUniqueKnownSolution(Puzzle p) {
        if (p == null) return false;
        HumanSolver.State state = HumanSolver.initialState(p);
        // The known solution must be representable by the current tile bank.
        Map<Integer, Integer> need = new HashMap<>();
        for (Pos pos : p.hidden) {
            Cell c = p.cells.get(pos);
            if (c == null || c.kind != Kind.NUMBER) return false;
            need.put(c.number, need.getOrDefault(c.number, 0) + 1);
        }
        for (Map.Entry<Integer, Integer> e : need.entrySet()) {
            if (state.remaining.getOrDefault(e.getKey(), 0) < e.getValue()) return false;
        }
        return !hasAlternative(p, state, false);
    }

    static boolean hasAlternative(Puzzle p, HumanSolver.State state, boolean differsFromKnown) {
        if (state.assigned.size() == p.hidden.size()) {
            return differsFromKnown && allEquationsValid(p, state.assigned);
        }

        Pos next = null;
        Set<Integer> bestDomain = null;
        for (Pos pos : p.hidden) {
            if (state.assigned.containsKey(pos)) continue;
            Set<Integer> d = HumanSolver.domainFor(p, pos, state);
            if (d.isEmpty()) return false;
            if (bestDomain == null || d.size() < bestDomain.size()) {
                next = pos;
                bestDomain = d;
                if (d.size() == 1) break;
            }
        }
        if (next == null || bestDomain == null) return false;

        Cell truthCell = p.cells.get(next);
        int truth = truthCell == null ? Integer.MIN_VALUE : truthCell.number;

        // Search non-truth branches first. If an alternative exists, we usually discover it early.
        List<Integer> ordered = new ArrayList<>(bestDomain);
        ordered.sort((a, b) -> {
            boolean at = a == truth, bt = b == truth;
            if (at == bt) return Integer.compare(a, b);
            return at ? 1 : -1;
        });

        for (int value : ordered) {
            HumanSolver.State child = new HumanSolver.State(state);
            if (!HumanSolver.assign(child, next, value)) continue;
            boolean different = differsFromKnown || value != truth;
            if (hasAlternative(p, child, different)) return true;
        }
        return false;
    }

    static int searchState(Puzzle p, HumanSolver.State state, int stopAt) {
        if (state.assigned.size() == p.hidden.size()) {
            return allEquationsValid(p, state.assigned) ? 1 : 0;
        }

        Pos next = null;
        Set<Integer> bestDomain = null;
        for (Pos pos : p.hidden) {
            if (state.assigned.containsKey(pos)) continue;
            Set<Integer> d = HumanSolver.domainFor(p, pos, state);
            if (d.isEmpty()) return 0;
            if (bestDomain == null || d.size() < bestDomain.size()) {
                next = pos;
                bestDomain = d;
                if (d.size() == 1) break;
            }
        }
        if (next == null || bestDomain == null) return 0;

        int total = 0;
        for (int value : bestDomain) {
            HumanSolver.State child = new HumanSolver.State(state);
            if (!HumanSolver.assign(child, next, value)) continue;
            total += searchState(p, child, stopAt - total);
            if (total >= stopAt) return total;
        }
        return total;
    }

    static int search(Puzzle p, List<Pos> hidden, Map<Pos, Integer> assigned,
                      Map<Integer, Integer> remaining, int stopAt) {
        if (assigned.size() == hidden.size()) return allEquationsValid(p, assigned) ? 1 : 0;

        Pos next = chooseNext(p, hidden, assigned);
        int total = 0;
        List<Integer> values = new ArrayList<>(remaining.keySet());
        for (int value : values) {
            int count = remaining.getOrDefault(value, 0);
            if (count <= 0) continue;
            assigned.put(next, value);
            remaining.put(value, count - 1);

            if (partialValid(p, assigned, remaining)) {
                total += search(p, hidden, assigned, remaining, stopAt - total);
            }

            assigned.remove(next);
            remaining.put(value, count);
            if (total >= stopAt) return total;
        }
        return total;
    }

    static Pos chooseNext(Puzzle p, List<Pos> hidden, Map<Pos, Integer> assigned) {
        Pos best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Pos q : hidden) {
            if (assigned.containsKey(q)) continue;
            int score = 0;
            for (Equation e : p.equations) {
                if (!q.equals(e.a) && !q.equals(e.b) && !q.equals(e.c)) continue;
                int known = 0;
                if (knownValue(p, e.a, assigned) != null) known++;
                if (knownValue(p, e.b, assigned) != null) known++;
                if (knownValue(p, e.c, assigned) != null) known++;
                score += known * known + 2;
            }
            if (score > bestScore) { best = q; bestScore = score; }
        }
        return best;
    }

    static Integer knownValue(Puzzle p, Pos q, Map<Pos, Integer> assigned) {
        if (!p.hidden.contains(q)) {
            Cell c = p.cells.get(q);
            return c == null ? null : c.number;
        }
        return assigned.get(q);
    }

    static boolean partialValid(Puzzle p, Map<Pos, Integer> assigned, Map<Integer, Integer> remaining) {
        for (Equation e : p.equations) {
            Integer a = knownValue(p, e.a, assigned);
            Integer b = knownValue(p, e.b, assigned);
            Integer c = knownValue(p, e.c, assigned);
            int known = (a != null ? 1 : 0) + (b != null ? 1 : 0) + (c != null ? 1 : 0);
            if (known == 3) {
                if (PuzzleGenerator.eval(a, e.operator, b) != c) return false;
            } else if (known == 2) {
                Integer required = requiredMissing(a, b, c, e.operator);
                if (required != null && required != Integer.MIN_VALUE) {
                    if (remaining.getOrDefault(required, 0) <= 0) return false;
                }
            }
        }
        return true;
    }

    static Integer requiredMissing(Integer a, Integer b, Integer c, char op) {
        if (a != null && b != null && c == null) return PuzzleGenerator.eval(a, op, b);
        if (a != null && b == null && c != null) {
            switch (op) {
                case '+': return c - a > 0 ? c - a : Integer.MIN_VALUE;
                case '-': return a - c > 0 ? a - c : Integer.MIN_VALUE;
                case '×': return a != 0 && c % a == 0 && c / a > 0 ? c / a : Integer.MIN_VALUE;
                case '÷': return c != 0 && a % c == 0 && a / c > 0 ? a / c : Integer.MIN_VALUE;
                case '^': return PuzzleGenerator.exactExponent(a, c);
            }
        }
        if (a == null && b != null && c != null) {
            switch (op) {
                case '+': return c - b > 0 ? c - b : Integer.MIN_VALUE;
                case '-': return b + c;
                case '×': return b != 0 && c % b == 0 && c / b > 0 ? c / b : Integer.MIN_VALUE;
                case '÷': return b * c;
                case '^': return PuzzleGenerator.exactRoot(c, b);
            }
        }
        return null;
    }

    static boolean allEquationsValid(Puzzle p, Map<Pos, Integer> assigned) {
        for (Equation e : p.equations) {
            Integer a = knownValue(p, e.a, assigned);
            Integer b = knownValue(p, e.b, assigned);
            Integer c = knownValue(p, e.c, assigned);
            if (a == null || b == null || c == null || PuzzleGenerator.eval(a, e.operator, b) != c) return false;
        }
        return true;
    }
}
