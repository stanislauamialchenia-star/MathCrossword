#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "app/src/main/java/com/offline/mathcrossword/SessionTracker.java"
REPORT = ROOT / "V33_TRAJECTORY_LOCALIZATION_REMAINING.md"

# Player-facing prose only. JSON keys, event names, enum values, telemetry fields and
# research identifiers are intentionally untouched and remain language-independent.
# Strings beginning with \\n are written here exactly as Java string contents, so java_quote()
# intentionally preserves backslashes instead of escaping them a second time.
TRANSLATIONS = {
    "уровень ": ("level ", "úroveň "),
    "свободная игра": ("free play", "volná hra"),
    "решено": ("solved", "vyřešeno"),
    "не завершено": ("unfinished", "nedokončeno"),
    "\\nАктивное время: ": ("\\nActive time: ", "\\nAktivní čas: "),
    " · событий: ": (" · events: ", " · událostí: "),
    "\\nГраф: μ=": ("\\nGraph: μ=", "\\nGraf: μ="),
    " · мосты ": (" · bridges ", " · mosty "),
    " · точки сочленения ": (" · articulation points ", " · artikulační body "),
    " · скрытые узлы-сочленения ": (" · hidden articulations ", " · skryté artikulace "),
    " · ветвления ": (" · branch nodes ", " · větvení "),
    " · диаметр ": (" · diameter ", " · průměr "),
    "\\n\\nСигналы прохождения": ("\\n\\nPlay signals", "\\n\\nSignály průchodu"),
    "\\nПаузы: продуктивные ": ("\\nPauses: productive ", "\\nPauzy: produktivní "),
    " · тупиковые ": (" · dead-end ", " · slepé "),
    "\\nПроверки гипотез: ": ("\\nHypothesis checks: ", "\\nOvěření hypotéz: "),
    " · быстрые каскады: ": (" · rapid cascades: ", " · rychlé kaskády: "),
    "\\nКандидат → решение: ": ("\\nCandidate → decision: ", "\\nKandidát → rozhodnutí: "),
    " · в среднем ": (" · average ", " · průměr "),
    "\\nВосстановления после отмены/ошибки/намёка: ": ("\\nRecoveries after undo/error/hint: ", "\\nObnovení po zpět/chybě/nápovědě: "),
    " действия": (" actions", " akcí"),
    "\\nКандидаты: переходов между клетками ": ("\\nCandidates: cell switches ", "\\nKandidáti: přechody mezi buňkami "),
    " · возвратов ": (" · revisits ", " · návratů "),
    " · максимум в клетке ": (" · max in one cell ", " · maximum v buňce "),
    "\\n\\nМаршрут HumanSolver ↔ прохождение": ("\\n\\nHumanSolver route ↔ play", "\\n\\nTrasa HumanSolver ↔ průchod"),
    "\\nСогласование: ": ("\\nAgreement: ", "\\nShoda: "),
    " · начало ": (" · opening ", " · začátek "),
    " · порядок ": (" · order ", " · pořadí "),
    " · pivot вовремя ": (" · pivot reached early ", " · pivot dosažen včas "),
    "\\nМодель: ": ("\\nModel: ", "\\nModel: "),
    "\\nПрохождение: ": ("\\nPlay: ", "\\nPrůchod: "),
    "\\n\\nСигналы для проверки модели": ("\\n\\nSignals for model review", "\\n\\nSignály pro kontrolu modelu"),
    "\\n• Прохождение ускорилось сильнее, чем ожидала модель каскада — проверить альтернативный путь решения.": ("\\n• Play accelerated more than the cascade model expected — inspect an alternative solve path.", "\\n• Průchod zrychlil více, než očekával model kaskády — prověř alternativní cestu řešení."),
    "\\n• Модель ожидала сильный каскад, но в реальном прохождении ускорения не видно — проверить оценку forced-cascade.": ("\\n• The model expected a strong cascade, but real play did not accelerate — review the forced-cascade estimate.", "\\n• Model očekával silnou kaskádu, ale skutečný průchod nezrychlil — zkontroluj odhad forced-cascade."),
    "\\n• Модель и прохождение согласуются: после ключевого шага возник быстрый каскад.": ("\\n• Model and play agree: a rapid cascade followed the key step.", "\\n• Model a průchod souhlasí: po klíčovém kroku vznikla rychlá kaskáda."),
    "\\n• Игрок проверял гипотезы, хотя генератор не отметил сильных pivot-точек — возможная слепая зона BranchQualityAnalyzer.": ("\\n• The player tested hypotheses although the generator marked no strong pivots — possible BranchQualityAnalyzer blind spot.", "\\n• Hráč ověřoval hypotézy, přestože generátor neoznačil silné pivoty — možná slepá skvrna BranchQualityAnalyzer."),
    "\\n• Генератор ожидал точки гипотезы, но задача решилась без зафиксированной проверки гипотез — возможен другой маршрут.": ("\\n• The generator expected hypothesis pivots, but the puzzle was solved without a recorded hypothesis check — another route may exist.", "\\n• Generátor očekával body hypotézy, ale hlavolam byl vyřešen bez zaznamenaného ověření hypotézy — může existovat jiná cesta."),
    "\\n• Частые возвраты к уже исследованным клеткам — проверить, это содержательный узел задачи или визуальная/интерфейсная неоднозначность.": ("\\n• Frequent returns to explored cells — check whether this is a meaningful puzzle hub or a visual/interface ambiguity.", "\\n• Časté návraty k již prozkoumaným buňkám — ověř, zda jde o důležitý uzel hlavolamu, nebo vizuální/interface nejasnost."),
    "\\n• Задача решена по порядку, слабо похожему на маршрут HumanSolver — сильный кандидат на альтернативный путь решения.": ("\\n• The puzzle was solved in an order unlike the HumanSolver route — strong candidate for an alternative solve path.", "\\n• Hlavolam byl vyřešen v pořadí málo podobném trase HumanSolver — silný kandidát na alternativní cestu řešení."),
    "\\n• Первые содержательные действия вошли в задачу не через ранние шаги HumanSolver": ("\\n• The first meaningful actions entered the puzzle outside HumanSolver's early steps", "\\n• První smysluplné akce vstoupily do hlavolamu mimo rané kroky HumanSolver"),
    " (первое совпадение: шаг модели ": (" (first match: model step ", " (první shoda: krok modelu "),
    "\\n• Игрок использовал знакомые модели узлы, но в заметно другом порядке — проверить независимые фронты и порядок дедукций.": ("\\n• The player used familiar model nodes in a notably different order — inspect independent fronts and deduction order.", "\\n• Hráč použil známé uzly modelu v výrazně jiném pořadí — prověř nezávislé fronty a pořadí dedukcí."),
    "\\n• Порядок прохождения хорошо согласуется с текущим маршрутом HumanSolver.": ("\\n• Play order agrees well with the current HumanSolver route.", "\\n• Pořadí průchodu dobře odpovídá současné trase HumanSolver."),
    "\\n• Явного расхождения между текущими структурными и поведенческими сигналами не найдено.": ("\\n• No clear mismatch between current structural and behavioral signals was found.", "\\n• Nebyl nalezen jasný rozpor mezi současnými strukturálními a behaviorálními signály."),
    "\\n\\nХод решения": ("\\n\\nSolve trace", "\\n\\nPrůběh řešení"),
    "\\nНет семантических ходов в этой сессии.": ("\\nNo semantic moves in this session.", "\\nV této relaci nejsou žádné sémantické tahy."),
    "\\n… ещё ": ("\\n… ", "\\n… ještě "),
    " ходов": (" more moves", " tahů"),
    "\\n\\nЭто след взаимодействия с задачей, а не буквальная запись мыслей человека.": ("\\n\\nThis is a trace of interaction with the puzzle, not a literal record of a person's thoughts.", "\\n\\nToto je stopa interakce s hlavolamem, nikoli doslovný záznam myšlenek člověka."),
}


def java_quote(s: str) -> str:
    # Entries are Java string contents (including literal \\n escapes), so preserve
    # backslashes exactly and escape only quotes for source-code insertion.
    return '"' + s.replace('"', '\\"') + '"'


def main() -> None:
    source = TARGET.read_text(encoding="utf-8")
    changed = 0
    out = []
    for line in source.splitlines(keepends=True):
        if "UiText.tr(" in line or "UiText.format(" in line:
            out.append(line)
            continue
        for russian, (english, czech) in TRANSLATIONS.items():
            old = java_quote(russian)
            if old not in line:
                continue
            new = f"UiText.tr({java_quote(english)}, {old}, {java_quote(czech)})"
            occurrences = line.count(old)
            line = line.replace(old, new)
            changed += occurrences
        out.append(line)
    source = "".join(out)
    TARGET.write_text(source, encoding="utf-8")

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
        "# V33 trajectory localization — remaining raw Russian",
        "",
        f"Safe pass changed **{changed}** player-facing literals in `SessionTracker.java`.",
        "",
        "Internal telemetry identifiers and JSON keys are intentionally language-independent.",
        "Russian fallback arguments inside `UiText.tr(...)` are intentional.",
        "",
    ]
    if not remaining:
        lines.append("No unwrapped Cyrillic Java string literals remain in `SessionTracker.java`.")
    else:
        for line_no, literal in remaining:
            lines.append(f"- line {line_no}: `{literal}`")
    REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"trajectory localization changes: {changed}; raw Cyrillic literals remaining: {len(remaining)}")


if __name__ == "__main__":
    main()
