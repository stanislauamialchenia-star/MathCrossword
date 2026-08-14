package com.offline.mathcrossword;

public enum SolutionStrategy {
    DEDUCTION("Дедукция", "Ответ появляется через пересечение ограничений"),
    CHAIN("Цепочка", "Нужно найти вход и провести длинную последовательность выводов"),
    HYPOTHESIS("Гипотеза", "Нужно временно допустить вариант и проверить последствия"),
    NETWORK("Сеть", "Несколько частей поля ограничивают друг друга через циклы"),
    MIXED("Микс", "Смешивает несколько стратегий решения");

    public final String label;
    public final String description;

    SolutionStrategy(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String shortLabel() {
        switch (this) {
            case DEDUCTION: return "Дедук.";
            case CHAIN: return "Цепь";
            case HYPOTHESIS: return "Гипот.";
            case NETWORK: return "Сеть";
            default: return "Микс";
        }
    }
}
