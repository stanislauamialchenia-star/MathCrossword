package com.offline.mathcrossword;

public enum SolutionStrategy {
    DEDUCTION(
            "Deduction", "Дедукция", "Dedukce",
            "The answer appears through intersecting constraints",
            "Ответ появляется через пересечение ограничений",
            "Odpověď vzniká průnikem omezení"),
    CHAIN(
            "Chain", "Цепочка", "Řetězec",
            "Find an entry point and carry a long sequence of deductions",
            "Нужно найти вход и провести длинную последовательность выводов",
            "Najdi vstupní bod a proveď delší řetězec závěrů"),
    HYPOTHESIS(
            "Hypothesis", "Гипотеза", "Hypotéza",
            "Temporarily assume an option and test its consequences",
            "Нужно временно допустить вариант и проверить последствия",
            "Dočasně připusť jednu možnost a ověř její důsledky"),
    NETWORK(
            "Network", "Сеть", "Síť",
            "Several parts of the board constrain one another through cycles",
            "Несколько частей поля ограничивают друг друга через циклы",
            "Několik částí pole se navzájem omezuje přes cykly"),
    MIXED(
            "Mixed", "Микс", "Mix",
            "Combines several solving structures",
            "Смешивает несколько стратегий решения",
            "Kombinuje několik struktur řešení");

    private final String englishLabel;
    private final String russianLabel;
    private final String czechLabel;
    private final String englishDescription;
    private final String russianDescription;
    private final String czechDescription;

    public String label;
    public String description;

    SolutionStrategy(String englishLabel, String russianLabel, String czechLabel,
                     String englishDescription, String russianDescription, String czechDescription) {
        this.englishLabel = englishLabel;
        this.russianLabel = russianLabel;
        this.czechLabel = czechLabel;
        this.englishDescription = englishDescription;
        this.russianDescription = russianDescription;
        this.czechDescription = czechDescription;
        refreshLocalizedText();
    }

    private void refreshLocalizedText() {
        label = UiText.tr(englishLabel, russianLabel, czechLabel);
        description = UiText.tr(englishDescription, russianDescription, czechDescription);
    }

    static void refreshAllLocalizedText() {
        for (SolutionStrategy strategy : values()) strategy.refreshLocalizedText();
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
