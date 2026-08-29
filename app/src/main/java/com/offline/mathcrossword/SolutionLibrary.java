package com.offline.mathcrossword;

import java.util.Arrays;
import java.util.List;

final class SolutionLibrary {
    static final class Entry {
        static final int STANDARD = 0;
        static final int INTRO_FIRST_MOVE = 1;
        static final int INTRO_UNCERTAINTY = 2;
        static final int INTRO_FIELD = 3;
        static final int STRATEGY_DIRECT = 4;
        static final int STRATEGY_INTERSECTION = 5;
        static final int STRATEGY_CANDIDATES = 6;
        static final int STRATEGY_CHAIN = 7;
        static final int STRATEGY_HYPOTHESIS = 8;
        static final int STRATEGY_NETWORK = 9;
        static final int STRATEGY_DIAGONAL = 10;
        static final int STRATEGY_REVERSE = 11;
        static final int STRATEGY_STUCK = 12;

        final int introType;
        final String title;
        final String idea;
        final String example;
        final String steps;

        Entry(String title, String idea, String example, String steps) {
            this(STANDARD, title, idea, example, steps);
        }

        Entry(int introType, String title, String idea, String example, String steps) {
            this.introType = introType;
            this.title = title;
            this.idea = idea;
            this.example = example;
            this.steps = steps;
        }
    }

    static List<Entry> ENTRIES = buildEntries();

    private static List<Entry> buildEntries() {
        return Arrays.asList(
            new Entry(
                    Entry.INTRO_FIRST_MOVE,
                    UiText.tr("0.1 First move", "0.1 Первый ход", "0.1 První tah"),
                    UiText.tr("Cell → number", "Клетка → число", "Políčko → číslo"),
                    UiText.tr(
                            "You can also start with the number.",
                            "Можно и наоборот: число → клетка.",
                            "Můžeš začít i číslem."),
                    ""),
            new Entry(
                    Entry.INTRO_UNCERTAINTY,
                    UiText.tr("0.2 If you are not sure", "0.2 Если не уверен", "0.2 Když si nejsi jistý"),
                    UiText.tr(
                            "Mark possible values — this is not an answer yet.",
                            "Отметь возможные значения — это ещё не ответ.",
                            "Označ možné hodnoty — ještě to není odpověď."),
                    UiText.tr(
                            "Move the reasoning out of your head.",
                            "Вынеси рассуждение из головы.",
                            "Přenes uvažování z hlavy ven."),
                    ""),
            new Entry(
                    Entry.INTRO_FIELD,
                    UiText.tr("0.3 Explore the field", "0.3 Исследуй поле", "0.3 Prozkoumej pole"),
                    UiText.tr(
                            "Zoom in. Focus. Return to the whole.",
                            "Приблизь. Сфокусируйся. Верни общий вид.",
                            "Přibliž. Zaměř se. Vrať se k celku."),
                    "",
                    ""),
            new Entry(
                    Entry.STRATEGY_DIRECT,
                    UiText.tr("1. Direct deduction", "1. Прямой вывод", "1. Přímý závěr"),
                    UiText.tr(
                            "Two numbers are known. Find the third.",
                            "Два числа известны. Найди третье.",
                            "Dvě čísla znáš. Najdi třetí."),
                    UiText.tr(
                            "After the move, check the crossings.",
                            "После хода проверь пересечения.",
                            "Po tahu zkontroluj křížení."),
                    ""),
            new Entry(
                    Entry.STRATEGY_INTERSECTION,
                    UiText.tr("2. Intersecting constraints", "2. Пересечение ограничений", "2. Průnik omezení"),
                    UiText.tr(
                            "One connection can determine another.",
                            "Одна связь может определить другую.",
                            "Jedna vazba může určit druhou."),
                    UiText.tr(
                            "Look for cells that belong to several equations.",
                            "Ищи клетки, которые участвуют сразу в нескольких уравнениях.",
                            "Hledej políčka, která patří do více rovnic."),
                    ""),
            new Entry(
                    Entry.STRATEGY_CANDIDATES,
                    UiText.tr("3. Candidates", "3. Кандидаты", "3. Kandidáti"),
                    UiText.tr(
                            "Do not guess. Narrow the possibilities.",
                            "Не угадывай. Сужай возможности.",
                            "Nehádej. Zužuj možnosti."),
                    UiText.tr(
                            "A smaller set is already progress.",
                            "Меньшее множество — уже прогресс.",
                            "Menší množina už je pokrok."),
                    ""),
            new Entry(
                    Entry.STRATEGY_CHAIN,
                    UiText.tr("4. Chain", "4. Цепочка", "4. Řetězec"),
                    UiText.tr(
                            "One deduction opens the next.",
                            "Один вывод открывает следующий.",
                            "Jeden závěr otevře další."),
                    UiText.tr(
                            "Do not recalculate the whole field — follow the newly opened connection.",
                            "Не пересчитывай всё поле — иди по открывшейся связи.",
                            "Nepočítej znovu celé pole — pokračuj po nově otevřené vazbě."),
                    ""),
            new Entry(
                    Entry.STRATEGY_HYPOTHESIS,
                    UiText.tr("5. Hypothesis and contradiction", "5. Гипотеза и противоречие", "5. Hypotéza a rozpor"),
                    UiText.tr(
                            "Test one option until a concrete contradiction appears.",
                            "Проверь один вариант до конкретного противоречия.",
                            "Otestuj jednu možnost až ke konkrétnímu rozporu."),
                    UiText.tr(
                            "A hypothesis is a temporary move, not an answer.",
                            "Гипотеза — временный ход, а не ответ.",
                            "Hypotéza je dočasný tah, ne odpověď."),
                    ""),
            new Entry(
                    Entry.STRATEGY_NETWORK,
                    UiText.tr("6. Network and cycle", "6. Сеть и цикл", "6. Síť a cyklus"),
                    UiText.tr(
                            "Think in connections, not rows.",
                            "Смотри на связи, а не на строки.",
                            "Dívej se na vazby, ne na řádky."),
                    UiText.tr(
                            "Two paths can constrain the same cell.",
                            "Два пути могут ограничить одну клетку.",
                            "Dvě cesty mohou omezovat stejné políčko."),
                    ""),
            new Entry(
                    Entry.STRATEGY_DIAGONAL,
                    UiText.tr("7. Diagonal connection", "7. Диагональная связь", "7. Diagonální vazba"),
                    UiText.tr(
                            "A diagonal is a full constraint.",
                            "Диагональ — полноценное ограничение.",
                            "Diagonála je plnohodnotné omezení."),
                    UiText.tr(
                            "It can bridge distant parts of the field.",
                            "Она может связывать далёкие части поля.",
                            "Může propojit vzdálené části pole."),
                    ""),
            new Entry(
                    Entry.STRATEGY_REVERSE,
                    UiText.tr("8. Reverse operations and powers", "8. Обратные операции и степени", "8. Obrácené operace a mocniny"),
                    UiText.tr(
                            "Sometimes go backwards from the result.",
                            "Иногда иди назад от результата.",
                            "Někdy jdi od výsledku zpět."),
                    UiText.tr(
                            "Look for the inverse operation.",
                            "Ищи обратную операцию.",
                            "Hledej opačnou operaci."),
                    ""),
            new Entry(
                    Entry.STRATEGY_STUCK,
                    UiText.tr("9. When you are stuck", "9. Если застрял", "9. Když se zasekneš"),
                    UiText.tr(
                            "If nothing moves, change the question.",
                            "Если ничего не движется — смени вопрос.",
                            "Když se nic nehýbe, změň otázku."),
                    UiText.tr(
                            "A dead end is a signal to change strategy, not to push harder.",
                            "Тупик — сигнал сменить стратегию, а не давить сильнее.",
                            "Slepá ulička je signál změnit strategii, ne tlačit víc."),
                    ""));
    }

    static void refreshLocalizedEntries() {
        ENTRIES = buildEntries();
    }

    private SolutionLibrary() { }
}
