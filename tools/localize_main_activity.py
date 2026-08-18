#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "app/src/main/java/com/offline/mathcrossword/MainActivity.java"
REPORT = ROOT / "V33_LOCALIZATION_REMAINING.md"

# The first migration already localized the main player path. Do not blindly replace
# Russian literals again: the Russian text is intentionally retained as the middle
# UiText.tr(...) argument and a second blind pass would nest UiText.tr calls.


def replace_once(source: str, old: str, new: str, label: str) -> tuple[str, int]:
    if new in source:
        return source, 0
    count = source.count(old)
    if count != 1:
        if count == 0:
            print(f"skip {label}: source pattern not found")
            return source, 0
        raise RuntimeError(f"{label}: expected one source occurrence, found {count}")
    return source.replace(old, new, 1), 1


def main() -> None:
    source = TARGET.read_text(encoding="utf-8")
    changed = 0

    # Free-play header abbreviations are visible in the ordinary game path.
    old = '                    : puzzle.solutionStrategy.label + " · Л" + puzzle.displayLogicLevel + "/В" + puzzle.displayCalcLevel,\n'
    new = '                    : puzzle.solutionStrategy.label + UiText.tr(" · L", " · Л", " · L") + puzzle.displayLogicLevel\n' \
          '                    + UiText.tr("/C", "/В", "/V") + puzzle.displayCalcLevel,\n'
    source, n = replace_once(source, old, new, "free-play header abbreviations")
    changed += n

    # Game information dialog is part of the normal player path and was still Russian.
    old = ('            String info = String.format(Locale.US, "Логика %d (%.1f) · вычисления %d (%.1f)\\n%s · скрыто клеток: %d\\nВерсия %s (%d)",\n'
           '                    puzzle.displayLogicLevel, puzzle.logicScore, puzzle.displayCalcLevel, puzzle.calcScore,\n'
           '                    puzzle.solutionStrategy.label, puzzle.hidden.size(), installedVersionName(), installedVersionCode());\n')
    new = ('            String info = UiText.format(\n'
           '                    "Logic %d (%.1f) · calculation %d (%.1f)\\n%s · hidden cells: %d\\nVersion %s (%d)",\n'
           '                    "Логика %d (%.1f) · вычисления %d (%.1f)\\n%s · скрыто клеток: %d\\nВерсия %s (%d)",\n'
           '                    "Logika %d (%.1f) · výpočty %d (%.1f)\\n%s · skrytých buněk: %d\\nVerze %s (%d)",\n'
           '                    puzzle.displayLogicLevel, puzzle.logicScore, puzzle.displayCalcLevel, puzzle.calcScore,\n'
           '                    puzzle.solutionStrategy.label, puzzle.hidden.size(), installedVersionName(), installedVersionCode());\n')
    source, n = replace_once(source, old, new, "game information dialog")
    changed += n

    # Duration units appear throughout player-facing analysis and dialogs.
    old = '            if (min >= 60) return (min / 60) + "ч " + (min % 60) + "м";\n'
    new = '            if (min >= 60) return (min / 60) + UiText.tr("h ", "ч ", "h ") + (min % 60) + UiText.tr("m", "м", "m");\n'
    source, n = replace_once(source, old, new, "duration units")
    changed += n

    # This exception message is surfaced by the surrounding download-error Toast.
    old = '                if (manager == null) throw new IllegalStateException("DownloadManager недоступен");\n'
    new = '                if (manager == null) throw new IllegalStateException(UiText.tr("DownloadManager unavailable", "DownloadManager недоступен", "DownloadManager není dostupný"));\n'
    source, n = replace_once(source, old, new, "download manager error")
    changed += n

    TARGET.write_text(source, encoding="utf-8")

    # Report only raw Cyrillic literals on lines that are not already localized.
    # Russian middle arguments inside UiText.tr/UiText.format are intentional and must
    # not be reported as unfinished localization.
    string_re = re.compile(r'"(?:\\.|[^"\\])*"')
    remaining = []
    for line_no, line in enumerate(source.splitlines(), 1):
        if "UiText.tr(" in line or "UiText.format(" in line:
            continue
        for match in string_re.finditer(line):
            literal = match.group(0)
            if re.search(r'[А-Яа-яЁё]', literal):
                remaining.append((line_no, literal))

    lines = [
        "# V33 localization migration — remaining raw player-facing Russian",
        "",
        f"Second safe pass changed **{changed}** ordinary-path code blocks in `MainActivity.java`.",
        "",
        "Russian text inside `UiText.tr(...)` / `UiText.format(...)` is intentional and is not listed below.",
        "The remaining raw literals should be translated deliberately or kept only if they are internal research text.",
        "",
    ]
    if not remaining:
        lines.append("No unwrapped Cyrillic Java string literals remain in `MainActivity.java`.")
    else:
        for line_no, literal in remaining:
            lines.append(f"- line {line_no}: `{literal}`")
    REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"second-pass changes: {changed}; raw Cyrillic literals remaining: {len(remaining)}")


if __name__ == "__main__":
    main()
