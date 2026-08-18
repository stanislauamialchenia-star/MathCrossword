package com.offline.mathcrossword;

public enum SolutionStrategy {
    DEDUCTION(
            UiText.tr("Deduction", "Дедукция", "Dedukce"),
            UiText.tr(
                    "The answer appears through intersecting constraints",
                    "Ответ появляется через пересечение ограничений",
                    "Odpověď vzniká průnikem omezení")),
    CHAIN(
            UiText.tr("Chain", "Цепочка", "Řetězec"),
            UiText.tr(
                    "Find an entry point and carry a long sequence of deductions",
                    "Нужно найти вход и провести длинную последовательность выводов",
                    "Najdi vstupní bod a proveď delší řetězec závěrů")),
    HYPOTHESIS(
            UiText.tr("Hypothesis", "Гипотеза", "Hypotéza"),
            UiText.tr(
                    "Temporarily assume an option and test its consequences",
                    "Нужно временно допустить вариант и проверить последствия",
                    "Dočasně připusť jednu možnost a ověř její důsledky")),
    NETWORK(
            UiText.tr("Network", "Сеть", "Síť"),
            UiText.tr(
                    "Several parts of the board constrain one another through cycles",
                    "Несколько частей поля ограничивают друг друга через циклы",
                    "Několik částí pole se navzájem omezuje přes cykly")),
    MIXED(
            UiText.tr("Mixed", "Микс", "Mix"),
            UiText.tr(
                    "Combines several solving structures",
                    "Смешивает несколько стратегий решения",
                    "Kombinuje několik struktur řešení"));

    public final String label;
    public final String description;

    SolutionStrategy(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String shortLabel() {
        switch (this) {
            case DEDUCTION: return UiText.tr("Ded.", "Дедук.", "Ded.");
            case CHAIN: return UiText.tr("Chain", "Цепь", "Řet.");
            case HYPOTHESIS: return UiText.tr("Hyp.", "Гипот.", "Hyp.");
            case NETWORK: return UiText.tr("Net", "Сеть", "Síť");
            default: return UiText.tr("Mix", "Микс", "Mix");
        }
    }
}
