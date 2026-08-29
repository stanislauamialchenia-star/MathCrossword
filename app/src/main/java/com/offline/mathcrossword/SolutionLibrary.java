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
                    UiText.tr("5. Hypothesis and contradiction", "5. Гипотеза и противоречие", "5. Hypotéza a rozpor"),
                    UiText.tr(
                            "When two options remain equally plausible, test one temporarily. This is not blind guessing: the branch must produce consequences you can verify.",
                            "Когда два варианта остаются равноправными, временно допустимо проверить один из них. Это не слепое угадывание: ветка должна приводить к проверяемым последствиям.",
                            "Když zůstanou dvě stejně pravděpodobné možnosti, jednu dočasně otestuj. Není to slepé hádání: větev musí vést k ověřitelným důsledkům."),
                    UiText.tr("A ∈ {6, 9}. Assume A = 6…", "A ∈ {6, 9}. Допустим A = 6…", "A ∈ {6, 9}. Předpokládej A = 6…"),
                    UiText.tr(
                            "Follow several forced consequences. If you get a non-integer division, an impossible negative result or a missing tile, the branch is impossible, so A = 9.",
                            "Проведи несколько обязательных следствий. Если получаешь деление с остатком, отрицательный результат или отсутствие плитки — ветка невозможна, значит A = 9.",
                            "Proveď několik nutných důsledků. Pokud dostaneš dělení se zbytkem, nemožný záporný výsledek nebo chybějící dílek, větev je nemožná, takže A = 9.")),
            new Entry(
                    UiText.tr("6. Network and cycle", "6. Сеть и цикл", "6. Síť a cyklus"),
                    UiText.tr(
                            "A network puzzle has no single main row. A constraint can travel through several equations and return to the starting area with new information.",
                            "В сетевой задаче нет одной главной строки. Ограничение может пройти по нескольким уравнениям и вернуться к исходной области с новой информацией.",
                            "V síťové úloze není jeden hlavní řádek. Omezení může projít několika rovnicemi a vrátit se do výchozí oblasti s novou informací."),
                    "A — B — C\n|       |\nD — E — F",
                    UiText.tr(
                            "Do not force one row to completion. Track candidates at several nodes and look for places where two independent paths constrain the same cell.",
                            "Не пытайся закончить одну строку любой ценой. Отмечай кандидатов в нескольких узлах и ищи, где два независимых пути ограничивают одну и ту же клетку.",
                            "Nesnaž se za každou cenu dokončit jeden řádek. Sleduj kandidáty v několika uzlech a hledej místo, kde dvě nezávislé cesty omezují stejné políčko.")),
            new Entry(
                    UiText.tr("7. Diagonal connection", "7. Диагональная связь", "7. Diagonální vazba"),
                    UiText.tr(
                            "A diagonal equation is a full constraint just like a horizontal or vertical one. It often connects areas that look independent.",
                            "Диагональное уравнение — такая же полноценная связь, как горизонтальная или вертикальная. Оно часто соединяет области поля, которые визуально кажутся независимыми.",
                            "Diagonální rovnice je plnohodnotné omezení stejně jako vodorovná nebo svislá. Často propojuje oblasti, které vypadají nezávisle."),
                    "A  ·  ·\n · B ·\n  ·  · C",
                    UiText.tr(
                            "Trace the whole diagonal from the first number to the result. Then check which number cells also belong to other equations. On harder networks the diagonal is often a bridge or closes a cycle.",
                            "Сначала проследи всю диагональ от первого числа до результата. Затем проверь, какие её числовые клетки одновременно принадлежат другим уравнениям. На сложных сетях диагональ часто является мостом или замыкает цикл.",
                            "Nejprve projdi celou diagonálu od prvního čísla k výsledku. Potom zkontroluj, která číselná políčka zároveň patří do jiných rovnic. V těžších sítích diagonála často tvoří most nebo uzavírá cyklus.")),
            new Entry(
                    UiText.tr("8. Reverse operations and powers", "8. Обратные операции и степени", "8. Obrácené operace a mocniny"),
                    UiText.tr(
                            "A complicated expression is often easier to solve backwards from the result.",
                            "Сложное выражение часто проще решать назад от результата.",
                            "Složitější výraz se často řeší snáz pozpátku od výsledku."),
                    "?³ = 125",
                    UiText.tr(
                            "Find the number whose cube is 125: 5³ = 125. Likewise for division: if ? ÷ 6 = 12, then ? = 72.",
                            "Ищи число, куб которого равен 125: 5³ = 125. Аналогично для деления: если ? ÷ 6 = 12, то ? = 72.",
                            "Najdi číslo, jehož třetí mocnina je 125: 5³ = 125. Stejně u dělení: pokud ? ÷ 6 = 12, pak ? = 72.")),
            new Entry(
                    UiText.tr("9. When you are stuck", "9. Если застрял", "9. Když se zasekneš"),
                    UiText.tr(
                            "The goal is not to push through friction by brute force, but to identify what information is missing.",
                            "Цель — не научиться обходить трение перебором, а понять, какой информации тебе не хватает.",
                            "Cílem není překonat tření hrubou silou, ale zjistit, jaká informace ti chybí."),
                    UiText.tr("The board has not moved for several minutes", "Поле стоит уже несколько минут", "Pole se několik minut nehýbe"),
                    UiText.tr(
                            "1) stop brute force; 2) mark candidates; 3) find cells shared by two or more equations; 4) look for a short chain; 5) if none exists, choose one hypothesis and seek a concrete contradiction. If none of these moves the puzzle, the obstacle may be a new solving structure rather than lack of attention.",
                            "1) останови перебор; 2) отметь кандидатов; 3) найди клетки с двумя и более уравнениями; 4) проверь, есть ли короткая цепочка; 5) если нет — выбери одну гипотезу и ищи конкретное противоречие. Если ни один способ не двигает задачу, возможно, препятствие — новая стратегия решения, а не нехватка внимания.",
                            "1) zastav hrubý pokus-omyl; 2) označ kandidáty; 3) najdi políčka sdílená dvěma nebo více rovnicemi; 4) hledej krátký řetězec; 5) pokud není, zvol jednu hypotézu a hledej konkrétní rozpor. Pokud nic z toho úlohu neposune, překážkou může být nový typ řešení, ne nedostatek pozornosti.")));
    }

    static void refreshLocalizedEntries() {
        ENTRIES = buildEntries();
    }

    private SolutionLibrary() { }
}
