#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/offline/mathcrossword/MainActivity.java"
LIB = ROOT / "app/src/main/java/com/offline/mathcrossword/SolutionLibrary.java"
GRADLE = ROOT / "app/build.gradle"


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 occurrence, found {count}")
    return text.replace(old, new, 1)


def patch_library():
    text = LIB.read_text(encoding="utf-8")
    text = replace_once(
        text,
        "    static final List<Entry> ENTRIES = Arrays.asList(\n",
        "    static List<Entry> ENTRIES = buildEntries();\n\n    private static List<Entry> buildEntries() {\n        return Arrays.asList(\n",
        "library builder start",
    )
    text = replace_once(
        text,
        ")));\n\n    private SolutionLibrary() { }\n}",
        ")));\n    }\n\n    static void refreshLocalizedEntries() {\n        ENTRIES = buildEntries();\n    }\n\n    private SolutionLibrary() { }\n}",
        "library builder end",
    )
    LIB.write_text(text, encoding="utf-8")


def patch_main():
    text = MAIN.read_text(encoding="utf-8")

    text = replace_once(
        text,
        "        final RectF homeUpdateRect = new RectF();\n        final RectF homePrivacyRect = new RectF();\n",
        "        final RectF homeUpdateRect = new RectF();\n        final RectF homeLanguageRect = new RectF();\n        final RectF homePrivacyRect = new RectF();\n",
        "language rect field",
    )

    text = replace_once(
        text,
        "            prefs = context.getSharedPreferences(\"progress\", Context.MODE_PRIVATE);\n            tracker = new SessionTracker(context);\n",
        "            prefs = context.getSharedPreferences(\"progress\", Context.MODE_PRIVATE);\n            UiText.setLanguageOverride(prefs.getString(\"language_override\", \"system\"));\n            SolutionStrategy.refreshAllLocalizedText();\n            SolutionLibrary.refreshLocalizedEntries();\n            updateStatus = UiText.tr(\"update not checked\", \"обновление не проверено\", \"aktualizace nezkontrolována\");\n            tracker = new SessionTracker(context);\n",
        "load saved language",
    )

    anchor = "            c.drawText(UiText.tr(\"Crossword\", \"кроссворд\", \"křížovka\"), w / 2f, y + dp(34), paint);\n\n"
    language_ui = '''            c.drawText(UiText.tr("Crossword", "кроссворд", "křížovka"), w / 2f, y + dp(34), paint);\n\n            // Compact language badge. A/XX means automatic system language; tapping opens the chooser.\n            homeLanguageRect.set(w - dp(82), topInset + dp(18), w - dp(18), topInset + dp(54));\n            paint.setStyle(Paint.Style.FILL);\n            paint.setColor(Color.argb(118, 255, 255, 255));\n            c.drawRoundRect(homeLanguageRect, dp(12), dp(12), paint);\n            paint.setColor(ink);\n            paint.setTextAlign(Paint.Align.CENTER);\n            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);\n            paint.setTextSize(dp(12.5f));\n            Paint.FontMetrics languageFm = paint.getFontMetrics();\n            c.drawText(UiText.badge(), homeLanguageRect.centerX(),\n                    homeLanguageRect.centerY() - (languageFm.ascent + languageFm.descent) / 2f, paint);\n\n'''
    text = replace_once(text, anchor, language_ui, "draw language badge")

    text = replace_once(
        text,
        "                else if (homeAnalysisRect.contains(x, y)) { screen = Screen.ANALYSIS; invalidate(); }\n                else if (homePrivacyRect.contains(x, y)) { showPrivacyDialog(); }\n",
        "                else if (homeAnalysisRect.contains(x, y)) { screen = Screen.ANALYSIS; invalidate(); }\n                else if (homeLanguageRect.contains(x, y)) { showLanguageDialog(); }\n                else if (homePrivacyRect.contains(x, y)) { showPrivacyDialog(); }\n",
        "language touch",
    )

    dialog_anchor = "        void showPrivacyDialog() {\n"
    dialog_method = '''        void showLanguageDialog() {\n            final String[] codes = {"system", "en", "ru", "cs"};\n            final String[] labels = {\n                    UiText.tr("System language", "Язык системы", "Jazyk systému"),\n                    "English",\n                    "Русский",\n                    "Čeština"\n            };\n            int checked = 0;\n            String current = UiText.languageOverride();\n            for (int i = 0; i < codes.length; i++) if (codes[i].equals(current)) checked = i;\n\n            new AlertDialog.Builder(getContext())\n                    .setTitle(UiText.tr("Language", "Язык", "Jazyk"))\n                    .setSingleChoiceItems(labels, checked, (dialog, which) -> {\n                        UiText.setLanguageOverride(codes[which]);\n                        prefs.edit().putString("language_override", codes[which]).apply();\n                        SolutionStrategy.refreshAllLocalizedText();\n                        SolutionLibrary.refreshLocalizedEntries();\n                        updateStatus = UiText.tr("update not checked", "обновление не проверено", "aktualizace nezkontrolována");\n                        dialog.dismiss();\n                        invalidate();\n                    })\n                    .setNegativeButton(UiText.tr("Close", "Закрыть", "Zavřít"), null)\n                    .show();\n        }\n\n        void showPrivacyDialog() {\n'''
    text = replace_once(text, dialog_anchor, dialog_method, "language dialog")

    text = text.replace('return info.versionName == null ? "1.37" : info.versionName;', 'return info.versionName == null ? "1.38" : info.versionName;')
    text = text.replace('return "1.37";', 'return "1.38";')
    text = text.replace('return 37L;', 'return 38L;')

    MAIN.write_text(text, encoding="utf-8")


def patch_version():
    text = GRADLE.read_text(encoding="utf-8")
    text = replace_once(text, "        versionCode 37\n        versionName '1.37'\n", "        versionCode 38\n        versionName '1.38'\n", "version bump")
    GRADLE.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    patch_library()
    patch_main()
    patch_version()
    print("language switcher migration applied")
