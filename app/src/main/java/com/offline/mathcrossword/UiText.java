package com.offline.mathcrossword;

import java.util.Locale;

/**
 * Small Android-independent localization helper for player-facing text used by
 * engine/research helpers that must stay free of android.* dependencies.
 *
 * English is the fallback for every unsupported locale.
 */
final class UiText {
    private UiText() { }

    static String language() {
        String language = Locale.getDefault().getLanguage();
        if ("ru".equals(language)) return "ru";
        if ("cs".equals(language) || "cz".equals(language)) return "cs";
        return "en";
    }

    static String tr(String english, String russian, String czech) {
        switch (language()) {
            case "ru": return russian;
            case "cs": return czech;
            default: return english;
        }
    }

    static String format(String english, String russian, String czech, Object... args) {
        return String.format(displayLocale(), tr(english, russian, czech), args);
    }

    static Locale displayLocale() {
        switch (language()) {
            case "ru": return new Locale("ru");
            case "cs": return new Locale("cs");
            default: return Locale.ENGLISH;
        }
    }
}
