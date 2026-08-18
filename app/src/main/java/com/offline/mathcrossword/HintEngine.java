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
        if (p == null) return new Hint(null,
                UiText.tr("The puzzle is not ready yet.", "Головоломка ещё не готова.", "Hádanka ještě není připravená."),
                "none");
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
                                ? UiText.tr(
                                        "Check the highlighted area: the current value conflicts with the available tiles.",
                                        "Проверь выделенную область: текущее значение конфликтует с доступными плитками.",
                                        "Zkontroluj zvýrazněnou oblast: současná hodnota je v konfliktu s dostupnými dílky.")
                                : UiText.tr(
                                        "The number of equal tiles is already violated here. Go back to the last placement.",
                                        "Здесь уже нарушено ограничение по количеству одинаковых чисел. Вернись к последней подстановке.",
                                        "Tady už je porušen počet stejných dílků. Vrať se k poslednímu vložení."),
                        "contradiction");
            }
        }

        if (!HumanSolver.allLocallyPossible(p, state)) {
            Pos focus = findConflictFocus(p, state, assignedValues);
            String text = stage == 0
                    ? UiText.tr(
                            "There is already a contradiction in the current placements. Look for it near the highlighted cell instead of continuing brute force.",
                            "В текущих подстановках уже есть противоречие. Ищи его около выделенной клетки, а не продолжай перебор.",
                            "V současných vloženích už je rozpor. Hledej ho u zvýrazněného políčka místo dalšího zkoušení naslepo.")
                    : UiText.tr(
                            "One equation near the highlighted cell can no longer be satisfied by any remaining tile. Check your latest moves in this area.",
                            "Одно из уравнений рядом с выделенной клеткой больше не может быть выполнено ни одной оставшейся плиткой. Проверь последние ходы в этой области.",
                            "Jednu z rovnic u zvýrazněného políčka už nelze splnit žádným zbývajícím dílkem. Zkontroluj poslední tahy v této oblasti.");
            return new Hint(focus, text, "contradiction");
        }

        Map<Pos, Set<Integer>> domains = HumanSolver.allDomains(p, state);
        if (domains.isEmpty()) return new Hint(null,
                UiText.tr(
                        "The board is filled — only the final check remains.",
                        "Поле уже заполнено — осталось проверить итог.",
                        "Pole je vyplněné — zbývá jen závěrečná kontrola."),
                "complete");

        Map.Entry<Pos, Set<Integer>> best = domains.entrySet().stream()
                .min(Comparator.comparingInt((Map.Entry<Pos, Set<Integer>> e) -> e.getValue().size())
                        .thenComparingInt(e -> -LogicAnalyzer.equationsFor(p, e.getKey()).size()))
                .orElse(null);
        if (best == null) return new Hint(null,
                UiText.tr(
                        "Try reviewing candidates in intersection cells.",
                        "Попробуй пересмотреть кандидатов в клетках-пересечениях.",
                        "Zkus znovu projít kandidáty v průsečíkových políčkách."),
                "general");

        Pos focus = best.getKey();
        Set<Integer> domain = best.getValue();

        if (domain.size() == 1) {
            int only = domain.iterator().next();
            if (stage == 0) {
                return new Hint(focus,
                        UiText.tr(
                                "There is a cell you can already deduce without a hypothesis. I highlighted it — compare every equation that passes through it.",
                                "Есть клетка, которую уже можно вывести без гипотезы. Я выделил её — сравни все уравнения, которые через неё проходят.",
                                "Jedno políčko už lze určit bez hypotézy. Zvýraznil jsem ho — porovnej všechny rovnice, které přes něj vedou."),
                        "forced");
            }
            if (stage == 1) {
                return new Hint(focus,
                        UiText.tr(
                                "After intersecting the constraints, only one candidate remains for the highlighted cell. Check the surrounding equations before opening the next hint.",
                                "У выделенной клетки после пересечения ограничений остаётся только один кандидат. Проверь строки вокруг неё, прежде чем смотреть следующий намёк.",
                                "Po průniku omezení zůstává pro zvýrazněné políčko jediný kandidát. Než otevřeš další nápovědu, zkontroluj okolní rovnice."),
                        "forced");
            }
            return new Hint(focus,
                    UiText.tr(
                            "Deep hint: the only valid candidate here is " + only + ". The game will not place it automatically — verify the deduction yourself.",
                            "Глубокий намёк: единственный допустимый кандидат здесь — " + only + ". Игра не ставит его автоматически — проверь вывод сам.",
                            "Hlubší nápověda: jediný platný kandidát je " + only + ". Hra ho nevloží automaticky — ověř si závěr sám."),
                    "forced_value");
        }

        HumanSolver.Metrics metrics = new HumanSolver.Metrics();
        HumanSolver.Deduction deduction = HumanSolver.findContradictionDeduction(p, state, domains, 1, metrics);
        if (deduction == null) deduction = HumanSolver.findContradictionDeduction(p, state, domains, 2, metrics);
        if (deduction != null) {
            Set<Integer> d = domains.get(deduction.pos);
            if (stage == 0) {
                return new Hint(deduction.pos,
                        UiText.tr(
                                "There is no direct move. Try a temporary hypothesis in the highlighted cell and follow its consequences without changing the whole board at once.",
                                "Прямого хода нет. Попробуй временную гипотезу в выделенной клетке и проследи её последствия, не меняя сразу всё поле.",
                                "Není tu přímý tah. Zkus dočasnou hypotézu ve zvýrazněném políčku a sleduj její důsledky, aniž bys hned měnil celé pole."),
                        "hypothesis");
            }
            if (stage == 1) {
                return new Hint(deduction.pos,
                        UiText.tr(
                                "Current candidates for the highlighted cell: " + formatSet(d) + ". Test them one at a time until you find a concrete contradiction.",
                                "Для выделенной клетки сейчас кандидаты: " + formatSet(d) + ". Проверь их по одному до первого конкретного противоречия.",
                                "Současní kandidáti pro zvýrazněné políčko: " + formatSet(d) + ". Ověřuj je po jednom až k prvnímu konkrétnímu rozporu."),
                        "hypothesis_candidates");
            }
            Integer bad = firstImpossibleCandidate(p, state, deduction.pos, d, deduction.depth);
            if (bad != null) {
                return new Hint(deduction.pos,
                        UiText.tr(
                                "Deep hint: start with the hypothesis " + bad + " in the highlighted cell. This branch should lead to a contradiction; find where.",
                                "Глубокий намёк: начни с гипотезы " + bad + " в выделенной клетке. Эта ветка должна привести к противоречию; найди где именно.",
                                "Hlubší nápověda: začni hypotézou " + bad + " ve zvýrazněném políčku. Tato větev má vést k rozporu; najdi kde."),
                        "hypothesis_probe");
            }
            return new Hint(deduction.pos,
                    UiText.tr(
                            "Deep hint: one branch of this cell collapses after " + deduction.depth + " level(s) of checking. Follow the chain until an equation becomes impossible.",
                            "Глубокий намёк: одна из веток этой клетки рушится после " + deduction.depth + " уровня проверки. Веди цепочку до невозможного уравнения.",
                            "Hlubší nápověda: jedna větev tohoto políčka se rozpadne po " + deduction.depth + " úrovni kontroly. Veď řetězec až k nemožné rovnici."),
                    "hypothesis_probe");
        }

        Pos structural = chooseStructuralFocus(p, domains);
        Set<Integer> structuralDomain = domains.get(structural);
        String strategyAdvice = strategyAdvice(p);
        if (stage == 0) {
            return new Hint(structural,
                    strategyAdvice + " " + UiText.tr(
                            "I highlighted a cell with good information value: it has "
                                    + structuralDomain.size() + " candidate(s) and "
                                    + LogicAnalyzer.equationsFor(p, structural).size() + " connected equation(s).",
                            "Я выделил клетку с хорошей информационной ценностью: у неё "
                                    + structuralDomain.size() + " кандидата(ов) и "
                                    + LogicAnalyzer.equationsFor(p, structural).size() + " связанных уравнения.",
                            "Zvýraznil jsem políčko s dobrou informační hodnotou: má "
                                    + structuralDomain.size() + " kandidátů a "
                                    + LogicAnalyzer.equationsFor(p, structural).size() + " propojených rovnic."),
                    "strategy");
        }
        if (stage == 1) {
            return new Hint(structural,
                    UiText.tr(
                            "Candidates for the highlighted cell: " + formatSet(structuralDomain)
                                    + ". Do not choose by feel — test which candidate constrains the neighboring equations most strongly.",
                            "Кандидаты выделенной клетки: " + formatSet(structuralDomain)
                                    + ". Не выбирай по ощущению — проверь, какой кандидат сильнее всего ограничивает соседние уравнения.",
                            "Kandidáti zvýrazněného políčka: " + formatSet(structuralDomain)
                                    + ". Nevybírej podle pocitu — ověř, který kandidát nejvíc omezuje sousední rovnice."),
                    "strategy_candidates");
        }
        return new Hint(structural,
                UiText.tr(
                        "If the board is still stuck: write down the candidates for this cell, choose one as a temporary hypothesis and follow at least two consequences. Look for the first contradiction, not the answer.",
                        "Если поле всё ещё стоит: запиши кандидатов этой клетки, выбери один как временную гипотезу и проследи не меньше двух следствий. Ищи не ответ, а первое противоречие.",
                        "Pokud se pole stále nehýbe: zapiš kandidáty tohoto políčka, vyber jeden jako dočasnou hypotézu a sleduj alespoň dva důsledky. Nehledej odpověď, ale první rozpor."),
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
                return UiText.tr(
                        "Look for intersecting constraints: one equation may not solve the cell, but two together can.",
                        "Ищи пересечение ограничений: одна строка может не решить клетку, но две вместе — могут.",
                        "Hledej průnik omezení: jedna rovnice nemusí políčko vyřešit, ale dvě dohromady ano.");
            case CHAIN:
                return UiText.tr(
                        "Do not try to solve the whole board. Find an entry point after which one deduction opens the next.",
                        "Не пытайся решить всё поле. Ищи точку входа, после которой один вывод откроет следующий.",
                        "Nesnaž se vyřešit celé pole. Hledej vstupní bod, po kterém jeden závěr otevře další.");
            case HYPOTHESIS:
                String family = p == null ? "none" : p.contradictionKernelFamily;
                if ("multi-pivot".equals(family))
                    return UiText.tr(
                            "There are several plausible hypothesis points here. Do not get stuck on one; compare which branch constrains the rest of the network more strongly.",
                            "Здесь несколько правдоподобных точек для гипотезы. Не упирайся в одну: сравни, какая ветка сильнее ограничивает остальную сеть.",
                            "Je tu několik rozumných bodů pro hypotézu. Neupínej se na jeden; porovnej, která větev silněji omezuje zbytek sítě.");
                if ("deep-branch".equals(family))
                    return UiText.tr(
                            "A false hypothesis can survive several local checks here. Do not expect an immediate error — follow the consequences farther.",
                            "Ложная гипотеза здесь может пережить несколько локальных проверок. Не жди мгновенной ошибки — проследи последствия дальше.",
                            "Chybná hypotéza tu může přežít několik lokálních kontrol. Nečekej okamžitou chybu — sleduj důsledky dál.");
                if ("two-stage".equals(family))
                    return UiText.tr(
                            "One consequence may not be enough after a hypothesis. Take one more step before looking for a contradiction.",
                            "После гипотезы одного следствия может быть мало. Проведи ещё один шаг и только потом ищи противоречие.",
                            "Po hypotéze nemusí jeden důsledek stačit. Udělej ještě jeden krok a teprve potom hledej rozpor.");
                if ("single-pivot".equals(family))
                    return UiText.tr(
                            "Look for an anchor cell with several candidates: one testable hypothesis should open the structure of the puzzle.",
                            "Ищи опорную клетку с несколькими кандидатами: одна проверяемая гипотеза должна открыть структуру задачи.",
                            "Hledej opěrné políčko s několika kandidáty: jedna ověřitelná hypotéza by měla otevřít strukturu úlohy.");
                return UiText.tr(
                        "A testable hypothesis is useful here: temporarily assume one option and look for a concrete contradiction.",
                        "Здесь полезна проверяемая гипотеза: временно допусти вариант и ищи конкретное противоречие.",
                        "Tady se hodí ověřitelná hypotéza: dočasně připusť jednu možnost a hledej konkrétní rozpor.");
            case NETWORK:
                return UiText.tr(
                        "Treat the board as a network: information can travel through several equations and return to the original node.",
                        "Смотри на поле как на сеть: информация может пройти по нескольким уравнениям и вернуться к исходному узлу.",
                        "Dívej se na pole jako na síť: informace může projít několika rovnicemi a vrátit se k původnímu uzlu.");
            default:
                if (p != null && p.resourceConflictDecoyCount > 0)
                    return UiText.tr(
                            "There are plausible false placements here: a number may fit this cell locally but be needed elsewhere. Check consequences, not only the current equation.",
                            "Здесь есть правдоподобные ложные размещения: число может подходить этой клетке локально, но оказаться нужным в другой части поля. Проверяй последствия, а не только само уравнение.",
                            "Jsou tu věrohodná chybná umístění: číslo může lokálně sedět do tohoto políčka, ale být potřeba jinde. Kontroluj důsledky, ne jen samotnou rovnici.");
                if (p != null && p.reasoningFronts >= 2)
                    return UiText.tr(
                            "The board has several active fronts. If one area is stuck in uncertainty, leave it, gain information elsewhere, then return.",
                            "У поля есть несколько рабочих фронтов. Если одна область упёрлась в неопределённость, оставь её и добудь информацию в другой, затем вернись.",
                            "Pole má několik pracovních front. Pokud jedna oblast uvízne v nejistotě, nech ji být, získej informaci jinde a pak se vrať.");
                if (p != null && p.contextualDecoyCount > 0)
                    return UiText.tr(
                            "Some extra numbers really do fit several local constraints. Narrow the options through consequences, not by how plausible a number looks.",
                            "Некоторые лишние числа здесь действительно совместимы с несколькими локальными ограничениями. Сужай варианты через последствия, а не по внешнему виду числа.",
                            "Některá nadbytečná čísla opravdu vyhovují několika lokálním omezením. Zužuj možnosti podle důsledků, ne podle toho, jak číslo vypadá věrohodně.");
                return UiText.tr(
                        "First narrow the candidates in key intersection cells; if no direct deduction exists, test a short hypothesis.",
                        "Сначала сузь кандидатов в узловых клетках; если прямого вывода нет, проверь короткую гипотезу.",
                        "Nejprve zúž kandidáty v klíčových průsečíkových políčkách; pokud není přímý závěr, otestuj krátkou hypotézu.");
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
