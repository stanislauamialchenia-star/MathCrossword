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

final class HumanSolver {
    static final class Metrics {
        int hidden;
        int initialSingletons;
        int initialMaxDomain;
        double initialAverageDomain;
        int initialBranchCells;
        int maxBranchWidth;
        int basicForced;
        int basicRounds;
        int basicRemaining;
        double basicSolvedFraction;
        int lookaheadTests;
        int lookaheadEliminations;
        int lookaheadDeductions;
        int depth2Tests;
        int depth2Eliminations;
        int depth2Deductions;
        int finalForced;
        int maxForcedCascade;
        int reasoningSteps;
        int maxReasoningDepth;
        int stuckRemaining;
        boolean contradiction;
    }

    static final class State {
        final Map<Pos, Integer> assigned = new HashMap<>();
        final Map<Integer, Integer> remaining = new LinkedHashMap<>();

        State() { }
        State(State other) {
            assigned.putAll(other.assigned);
            remaining.putAll(other.remaining);
        }
    }

    static final class Propagation {
        int forced;
        int rounds;
        boolean contradiction;
    }

    static final class ProbeBudget {
        int left;
        ProbeBudget(int left) { this.left = left; }
        boolean spend() { return --left >= 0; }
    }

    static Metrics analyze(Puzzle p) {
        Metrics m = new Metrics();
        m.hidden = p.hidden.size();
        State state = initialState(p);

        Map<Pos, Set<Integer>> initial = allDomains(p, state);
        double sum = 0;
        for (Set<Integer> d : initial.values()) {
            int size = d.size();
            if (size == 1) m.initialSingletons++;
            if (size > 1) m.initialBranchCells++;
            m.initialMaxDomain = Math.max(m.initialMaxDomain, size);
            m.maxBranchWidth = Math.max(m.maxBranchWidth, size);
            sum += size;
        }
        m.initialAverageDomain = initial.isEmpty() ? 0 : sum / initial.size();

        Propagation base = propagateSingles(p, state);
        m.basicForced = base.forced;
        m.basicRounds = base.rounds;
        m.basicRemaining = p.hidden.size() - state.assigned.size();
        m.basicSolvedFraction = p.hidden.isEmpty() ? 0.0 : (double) m.basicForced / p.hidden.size();
        if (base.contradiction) {
            m.contradiction = true;
            m.stuckRemaining = m.basicRemaining;
            return m;
        }

        int guard = 0;
        while (state.assigned.size() < p.hidden.size() && guard++ < p.hidden.size() * 6 + 30) {
            Map<Pos, Set<Integer>> domains = allDomains(p, state);
            boolean empty = false;
            for (Set<Integer> d : domains.values()) if (d.isEmpty()) { empty = true; break; }
            if (empty) {
                m.contradiction = true;
                m.stuckRemaining = p.hidden.size() - state.assigned.size();
                return m;
            }

            Deduction deduction = null;
            for (int depth = 1; depth <= 2 && deduction == null; depth++) {
                deduction = findContradictionDeduction(p, state, domains, depth, m);
            }
            if (deduction == null) {
                m.stuckRemaining = p.hidden.size() - state.assigned.size();
                return m;
            }

            if (!assign(state, deduction.pos, deduction.value)) {
                m.contradiction = true;
                break;
            }
            m.reasoningSteps++;
            m.maxReasoningDepth = Math.max(m.maxReasoningDepth, deduction.depth);
            if (deduction.depth == 1) m.lookaheadDeductions++;
            else m.depth2Deductions++;

            Propagation after = propagateSingles(p, state);
            m.finalForced += after.forced;
            m.maxForcedCascade = Math.max(m.maxForcedCascade, after.forced);
            if (after.contradiction) {
                m.contradiction = true;
                break;
            }
        }

        m.stuckRemaining = Math.max(0, p.hidden.size() - state.assigned.size());
        return m;
    }

    static final class Deduction {
        final Pos pos;
        final int value;
        final int depth;
        Deduction(Pos pos, int value, int depth) { this.pos = pos; this.value = value; this.depth = depth; }
    }

    static Deduction findContradictionDeduction(Puzzle p, State state,
                                                 Map<Pos, Set<Integer>> domains,
                                                 int depth, Metrics m) {
        List<Map.Entry<Pos, Set<Integer>>> ordered = new ArrayList<>(domains.entrySet());
        ordered.sort((a, b) -> Integer.compare(a.getValue().size(), b.getValue().size()));

        for (Map.Entry<Pos, Set<Integer>> entry : ordered) {
            Set<Integer> domain = entry.getValue();
            if (domain.size() <= 1 || domain.size() > 8) continue;
            m.maxBranchWidth = Math.max(m.maxBranchWidth, domain.size());

            List<Integer> survivors = new ArrayList<>();
            for (int candidate : domain) {
                if (depth == 1) m.lookaheadTests++; else m.depth2Tests++;
                ProbeBudget budget = new ProbeBudget(depth == 1 ? 70 : 240);
                boolean viable = candidateViable(p, state, entry.getKey(), candidate, depth, budget);
                if (!viable) {
                    if (depth == 1) m.lookaheadEliminations++; else m.depth2Eliminations++;
                } else {
                    survivors.add(candidate);
                    if (survivors.size() > 1 && depth == 2 && domain.size() > 5) {
                        // No need to spend the entire budget on a wide cell once
                        // it is already clear that this cell is not forced.
                        break;
                    }
                }
            }
            if (survivors.size() == 1) return new Deduction(entry.getKey(), survivors.get(0), depth);
        }
        return null;
    }

    static boolean candidateViable(Puzzle p, State base, Pos pos, int candidate,
                                   int depth, ProbeBudget budget) {
        if (!budget.spend()) return true; // conservative: budget exhaustion never creates a fake contradiction
        State probe = new State(base);
        if (!assign(probe, pos, candidate)) return false;
        Propagation propagation = propagateSingles(p, probe);
        if (propagation.contradiction || !allLocallyPossible(p, probe)) return false;
        if (probe.assigned.size() == p.hidden.size()) return true;
        if (depth <= 1) return true;

        Map<Pos, Set<Integer>> domains = allDomains(p, probe);
        Pos pivot = null;
        Set<Integer> pivotDomain = null;
        for (Map.Entry<Pos, Set<Integer>> e : domains.entrySet()) {
            if (e.getValue().isEmpty()) return false;
            if (e.getValue().size() <= 1) continue;
            if (pivotDomain == null || e.getValue().size() < pivotDomain.size()) {
                pivot = e.getKey();
                pivotDomain = e.getValue();
            }
        }
        if (pivot == null || pivotDomain == null) return true;
        if (pivotDomain.size() > 8) return true;

        for (int v : pivotDomain) {
            if (candidateViable(p, probe, pivot, v, depth - 1, budget)) return true;
        }
        return false;
    }

    static State initialState(Puzzle p) {
        State s = new State();
        for (Tile t : p.tiles) s.remaining.put(t.value, s.remaining.getOrDefault(t.value, 0) + 1);
        return s;
    }

    static boolean assign(State s, Pos pos, int value) {
        if (s.assigned.containsKey(pos)) return s.assigned.get(pos) == value;
        int have = s.remaining.getOrDefault(value, 0);
        if (have <= 0) return false;
        s.assigned.put(pos, value);
        s.remaining.put(value, have - 1);
        return true;
    }

    static Propagation propagateSingles(Puzzle p, State state) {
        Propagation out = new Propagation();
        int guard = 0;
        while (guard++ < p.hidden.size() * 3 + 10) {
            Map<Pos, Set<Integer>> domains = allDomains(p, state);
            List<Map.Entry<Pos, Set<Integer>>> singles = new ArrayList<>();
            for (Map.Entry<Pos, Set<Integer>> e : domains.entrySet()) {
                if (e.getValue().isEmpty()) {
                    out.contradiction = true;
                    return out;
                }
                if (e.getValue().size() == 1) singles.add(e);
            }
            if (singles.isEmpty()) break;
            out.rounds++;
            boolean changed = false;
            for (Map.Entry<Pos, Set<Integer>> e : singles) {
                if (state.assigned.containsKey(e.getKey())) continue;
                int v = e.getValue().iterator().next();
                if (!assign(state, e.getKey(), v)) {
                    out.contradiction = true;
                    return out;
                }
                out.forced++;
                changed = true;
            }
            if (!changed) break;
            if (!allLocallyPossible(p, state)) {
                out.contradiction = true;
                return out;
            }
        }
        return out;
    }

    static Map<Pos, Set<Integer>> allDomains(Puzzle p, State state) {
        Map<Pos, Set<Integer>> out = new LinkedHashMap<>();
        for (Pos pos : p.hidden) {
            if (!state.assigned.containsKey(pos)) out.put(pos, domainFor(p, pos, state));
        }
        return out;
    }

    static Set<Integer> domainFor(Puzzle p, Pos pos, State state) {
        Set<Integer> out = new LinkedHashSet<>();
        for (Map.Entry<Integer, Integer> e : state.remaining.entrySet()) {
            int value = e.getKey();
            if (e.getValue() <= 0) continue;
            State probe = new State(state);
            if (!assign(probe, pos, value)) continue;
            if (allLocallyPossible(p, probe)) out.add(value);
        }
        return out;
    }

    static boolean allLocallyPossible(Puzzle p, State state) {
        for (Equation e : p.equations) if (!equationPossible(p, e, state)) return false;
        return true;
    }

    static boolean equationPossible(Puzzle p, Equation e, State state) {
        Integer a = valueAt(p, e.a, state);
        Integer b = valueAt(p, e.b, state);
        Integer c = valueAt(p, e.c, state);
        int unknown = (a == null ? 1 : 0) + (b == null ? 1 : 0) + (c == null ? 1 : 0);
        if (unknown == 0) return PuzzleGenerator.eval(a, e.operator, b) == c;

        List<Integer> vals = new ArrayList<>();
        for (Map.Entry<Integer, Integer> x : state.remaining.entrySet()) if (x.getValue() > 0) vals.add(x.getKey());
        if (vals.isEmpty()) return false;

        if (unknown == 1) {
            Integer required = SolutionCounter.requiredMissing(a, b, c, e.operator);
            if (required == null) return true;
            return required != Integer.MIN_VALUE && state.remaining.getOrDefault(required, 0) > 0;
        }

        if (unknown == 2) {
            for (int x : vals) {
                int cx = state.remaining.getOrDefault(x, 0);
                if (cx <= 0) continue;
                state.remaining.put(x, cx - 1);
                for (int y : vals) {
                    if (state.remaining.getOrDefault(y, 0) <= 0) continue;
                    Integer aa = a, bb = b, cc = c;
                    if (aa == null) aa = x;
                    else if (bb == null) bb = x;
                    else cc = x;
                    if (a == null && b == null) bb = y;
                    else if (a == null && c == null) cc = y;
                    else if (b == null && c == null) cc = y;
                    if (PuzzleGenerator.eval(aa, e.operator, bb) == cc) {
                        state.remaining.put(x, cx);
                        return true;
                    }
                }
                state.remaining.put(x, cx);
            }
            return false;
        }

        for (int x : vals) {
            int cx = state.remaining.getOrDefault(x, 0);
            if (cx <= 0) continue;
            state.remaining.put(x, cx - 1);
            for (int y : vals) {
                int cy = state.remaining.getOrDefault(y, 0);
                if (cy <= 0) continue;
                state.remaining.put(y, cy - 1);
                for (int z : vals) {
                    if (state.remaining.getOrDefault(z, 0) <= 0) continue;
                    if (PuzzleGenerator.eval(x, e.operator, y) == z) {
                        state.remaining.put(y, cy);
                        state.remaining.put(x, cx);
                        return true;
                    }
                }
                state.remaining.put(y, cy);
            }
            state.remaining.put(x, cx);
        }
        return false;
    }

    static Integer valueAt(Puzzle p, Pos pos, State state) {
        if (!p.hidden.contains(pos)) {
            Cell c = p.cells.get(pos);
            return c == null ? null : c.number;
        }
        return state.assigned.get(pos);
    }
}

