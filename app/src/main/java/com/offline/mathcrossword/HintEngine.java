package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Android-independent hint planner. It never places a tile for the player.
 * Hints are progressive: direction -> candidates -> concrete reasoning probe.
 */
final class HintEngine {
    static final class Hint {
        final Pos focus;
        final String text;
        final String kind;

        Hint(Pos focus, String text, String kind) {
            this.focus = focus;
            this.text = text;
            this.kind = kind;
        }
    }

    private HintEngine() { }

    static Hint suggest(Puzzle p, Map<Pos, Integer> assignedValues, int stage) {
        if (p == null) return new Hint(null, "Головоломка ещё не готова.", "none");
        stage = Math.max(0, Math.min(2, stage));

        HumanSolver.State state = HumanSolver.initialState(p);
        List<Map.Entry<Pos, Integer>> placed = new ArrayList<>(assignedValues.entrySet());
        placed.sort(Comparator.comparingInt((Map.Entry<Pos, Integer> e) -> e.getKey().y)
                .thenComparingInt(e -> e.getKey().x));

        for (Map.Entry<Pos, Integer> e : placed) {
            if (!p.hidden.contains(e.getKey())) continue;
            if (!HumanSolver.assign(state, e.getKey(), e.getValue())) {
                return new Hint(e.getKey(),
                        stage == 0
                                ? "Проверь выделенную область: текущее значение конфликтует с доступными плитками."
                                : "Здесь уже нарушено ограничение по количеству одинаковых чисел. Вернись к последней подстановке.",
                        "contradiction");
            }
        }

        if (!HumanSolver.allLocallyPossible(p, state)) {
            Pos focus = findConflictFocus(p, state, assignedValues);
            String text = stage == 0
                    ? "В текущих подстановках уже есть противоречие. Ищи его около выделенной клетки, а не продолжай перебор."
                    : "Одно из уравнений рядом с выделенной клеткой больше не может быть выполнено ни одной оставшейся плиткой. Проверь последние ходы в этой области.";
            return new Hint(focus, text, "contradiction");
        }

        Map<Pos, Set<Integer>> domains = HumanSolver.allDomains(p, state);
        if (domains.isEmpty()) return new Hint(null, "Поле уже заполнено — осталось проверить итог.", "complete");

        Map.Entry<Pos, Set<Integer>> best = domains.entrySet().stream()
                .min(Comparator.comparingInt((Map.Entry<Pos, Set<Integer>> e) -> e.getValue().size())
                        .thenComparingInt(e -> -LogicAnalyzer.equationsFor(p, e.getKey()).size()))
                .orElse(null);
        if (best == null) return new Hint(null, "Попробуй пересмотреть кандидатов в клетках-пересечениях.", "general");

        Pos focus = best.getKey();
        Set<Integer> domain = best.getValue();
        int touching = LogicAnalyzer.equationsFor(p, focus).size();

        if (domain.size() == 1) {
            int only = domain.iterator().next();
            if (stage == 0) {
                return new Hint(focus,
                        "Есть клетка, которую уже можно вывести без гипотезы. Я выделил её — сравни все уравнения, которые через неё проходят.",
                        "forced");
            }
            if (stage == 1) {
                return new Hint(focus,
                        "У выделенной клетки после пересечения ограничений остаётся только один кандидат. Проверь строки вокруг неё, прежде чем смотреть следующий намёк.",
                        "forced");
            }
            return new Hint(focus,
                    "Глубокий намёк: единственный допустимый кандидат здесь — " + only + ". Игра не ставит его автоматически — проверь вывод сам.",
                    "forced_value");
        }

        HumanSolver.Metrics metrics = new HumanSolver.Metrics();
        HumanSolver.Deduction deduction = HumanSolver.findContradictionDeduction(p, state, domains, 1, metrics);
        if (deduction == null) deduction = HumanSolver.findContradictionDeduction(p, state, domains, 2, metrics);
        if (deduction != null) {
            Set<Integer> d = domains.get(deduction.pos);
            if (stage == 0) {
                return new Hint(deduction.pos,
                        "Прямого хода нет. Попробуй временную гипотезу в выделенной клетке и проследи её последствия, не меняя сразу всё поле.",
                        "hypothesis");
            }
            if (stage == 1) {
                return new Hint(deduction.pos,
                        "Для выделенной клетки сейчас кандидаты: " + formatSet(d) + ". Проверь их по одному до первого конкретного противоречия.",
                        "hypothesis_candidates");
            }
            Integer bad = firstImpossibleCandidate(p, state, deduction.pos, d, deduction.depth);
            if (bad != null) {
                return new Hint(deduction.pos,
                        "Глубокий намёк: начни с гипотезы " + bad + " в выделенной клетке. Эта ветка должна привести к противоречию; найди где именно.",
                        "hypothesis_probe");
            }
            return new Hint(deduction.pos,
                    "Глубокий намёк: одна из веток этой клетки рушится после " + deduction.depth + " уровня проверки. Веди цепочку до невозможного уравнения.",
                    "hypothesis_probe");
        }

        // No forced or look-ahead deduction found within the bounded human model.
        // Point the player at a structurally useful cell rather than reveal an answer.
        Pos structural = chooseStructuralFocus(p, domains);
        Set<Integer> structuralDomain = domains.get(structural);
        String strategyAdvice = strategyAdvice(p);
        if (stage == 0) {
            return new Hint(structural,
                    strategyAdvice + " Я выделил клетку с хорошей информационной ценностью: у неё "
                            + structuralDomain.size() + " кандидата(ов) и "
                            + LogicAnalyzer.equationsFor(p, structural).size() + " связанных уравнения.",
                    "strategy");
        }
        if (stage == 1) {
            return new Hint(structural,
                    "Кандидаты выделенной клетки: " + formatSet(structuralDomain)
                            + ". Не выбирай по ощущению — проверь, какой кандидат сильнее всего ограничивает соседние уравнения.",
                    "strategy_candidates");
        }
        return new Hint(structural,
                "Если поле всё ещё стоит: запиши кандидатов этой клетки, выбери один как временную гипотезу и проследи не меньше двух следствий. Ищи не ответ, а первое противоречие.",
                "strategy_probe");
    }

    static Pos findConflictFocus(Puzzle p, HumanSolver.State state, Map<Pos, Integer> assignedValues) {
        for (Equation e : p.equations) {
            if (HumanSolver.equationPossible(p, e, state)) continue;
            for (Pos pos : new Pos[]{e.a, e.b, e.c}) {
                if (assignedValues.containsKey(pos)) return pos;
            }
            return e.a;
        }
        return assignedValues.isEmpty() ? null : assignedValues.keySet().iterator().next();
    }

    static Pos chooseStructuralFocus(Puzzle p, Map<Pos, Set<Integer>> domains) {
        Pos best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Map.Entry<Pos, Set<Integer>> e : domains.entrySet()) {
            int links = LogicAnalyzer.equationsFor(p, e.getKey()).size();
            int size = Math.max(1, e.getValue().size());
            double score = links * 3.0 - size * 0.55;
            if (score > bestScore) {
                bestScore = score;
                best = e.getKey();
            }
        }
        return best == null ? domains.keySet().iterator().next() : best;
    }

    static Integer firstImpossibleCandidate(Puzzle p, HumanSolver.State state, Pos pos,
                                            Set<Integer> domain, int depth) {
        if (domain == null) return null;
        HumanSolver.ProbeBudget budget = new HumanSolver.ProbeBudget(depth <= 1 ? 100 : 320);
        for (int v : domain) {
            if (!HumanSolver.candidateViable(p, state, pos, v, Math.max(1, depth), budget)) return v;
        }
        return null;
    }

    static String strategyAdvice(Puzzle p) {
        SolutionStrategy strategy = p == null || p.solutionStrategy == null
                ? SolutionStrategy.MIXED : p.solutionStrategy;
        switch (strategy) {
            case DEDUCTION:
                return "Ищи пересечение ограничений: одна строка может не решить клетку, но две вместе — могут.";
            case CHAIN:
                return "Не пытайся решить всё поле. Ищи точку входа, после которой один вывод откроет следующий.";
            case HYPOTHESIS:
                String family = p == null ? "none" : p.contradictionKernelFamily;
                if ("multi-pivot".equals(family))
                    return "Здесь несколько правдоподобных точек для гипотезы. Не упирайся в одну: сравни, какая ветка сильнее ограничивает остальную сеть.";
                if ("deep-branch".equals(family))
                    return "Ложная гипотеза здесь может пережить несколько локальных проверок. Не жди мгновенной ошибки — проследи последствия дальше.";
                if ("two-stage".equals(family))
                    return "После гипотезы одного следствия может быть мало. Проведи ещё один шаг и только потом ищи противоречие.";
                if ("single-pivot".equals(family))
                    return "Ищи опорную клетку с несколькими кандидатами: одна проверяемая гипотеза должна открыть структуру задачи.";
                return "Здесь полезна проверяемая гипотеза: временно допусти вариант и ищи конкретное противоречие.";
            case NETWORK:
                return "Смотри на поле как на сеть: информация может пройти по нескольким уравнениям и вернуться к исходному узлу.";
            default:
                if (p != null && p.resourceConflictDecoyCount > 0)
                    return "Здесь есть правдоподобные ложные размещения: число может подходить этой клетке локально, но оказаться нужным в другой части поля. Проверяй последствия, а не только само уравнение.";
                if (p != null && p.reasoningFronts >= 2)
                    return "У поля есть несколько рабочих фронтов. Если одна область упёрлась в неопределённость, оставь её и добудь информацию в другой, затем вернись.";
                if (p != null && p.contextualDecoyCount > 0)
                    return "Некоторые лишние числа здесь действительно совместимы с несколькими локальными ограничениями. Сужай варианты через последствия, а не по внешнему виду числа.";
                return "Сначала сузь кандидатов в узловых клетках; если прямого вывода нет, проверь короткую гипотезу.";
        }
    }

    static String formatSet(Set<Integer> values) {
        if (values == null || values.isEmpty()) return "∅";
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Integer::compareTo);
        StringBuilder sb = new StringBuilder("{");
        int max = Math.min(sorted.size(), 8);
        for (int i = 0; i < max; i++) {
            if (i > 0) sb.append(", ");
            sb.append(sorted.get(i));
        }
        if (sorted.size() > max) sb.append(", …");
        return sb.append('}').toString();
    }
}
