#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "app/src/main/java/com/offline/mathcrossword/MainActivity.java"
REPORT = ROOT / "V33_LOCALIZATION_REMAINING.md"

# Deliberate second/third-pass migration for the large Canvas Activity.
# Russian text is intentionally retained as the middle UiText.tr(...) argument.
# Every transform is idempotent: already-localized lines are skipped.

ANALYSIS_TRANSLATIONS = {
    "Пока данных нет. Заверши или покинь несколько головоломок — здесь появится краткий итог. Таймер учитывает только активное время: сворачивание приложения не считается.": (
        "No data yet. Finish or leave a few puzzles and a short summary will appear here. The timer counts active play only; time with the app in the background is excluded.",
        "Zatím nejsou žádná data. Dokonči nebo opusť několik hlavolamů a objeví se zde stručný přehled. Časovač počítá jen aktivní hraní; čas na pozadí se nezapočítává."),
    "Краткий итог": ("Summary", "Souhrn"),
    "Сессий: ": ("Sessions: ", "Relací: "),
    "   Решено: ": ("   Solved: ", "   Vyřešeno: "),
    "Среднее время решённой: ": ("Average solved time: ", "Průměrný čas vyřešení: "),
    "Среднее событий: ": ("Average events: ", "Průměr událostí: "),
    "До первого действия: ": ("Time to first action: ", "Do první akce: "),
    "Средняя длинная пауза: ": ("Average long pause: ", "Průměrná dlouhá pauza: "),
    "Ходы: ": ("Moves: ", "Tahy: "),
    "   Кандидаты: ": ("   Candidates: ", "   Kandidáti: "),
    "Наводящие намёки: ": ("Guided hints: ", "Naváděcí nápovědy: "),
    "Паузы: продуктивные ": ("Pauses: productive ", "Pauzy: produktivní "),
    " · тупиковые ": (" · dead-end ", " · slepé "),
    "Сигналы проверки гипотез: ": ("Hypothesis-check signals: ", "Signály ověřování hypotéz: "),
    "Быстрые каскады действий: ": ("Rapid action cascades: ", "Rychlé kaskády akcí: "),
    "Кандидаты: переходы между клетками ": ("Candidates: cell switches ", "Kandidáti: přechody mezi buňkami "),
    " · возвраты ": (" · revisits ", " · návraty "),
    "Маршруты: ": ("Routes: ", "Trasy: "),
    " сравн. · согласование ": (" compared · agreement ", " porovn. · shoda "),
    " · сильных расхождений ": (" · strong divergences ", " · výrazné odchylky "),
    "Калибровка сложности": ("Difficulty calibration", "Kalibrace obtížnosti"),
    "Нужно ещё решённых прохождений: ": ("More solved runs needed: ", "Je potřeba dalších vyřešených průchodů: "),
    " · история": (" · history", " · historie"),
    "Прогноз ±1: ": ("Prediction ±1: ", "Predikce ±1: "),
    "чаще недооценивает трудность": ("usually underestimates difficulty", "častěji podhodnocuje obtížnost"),
    "чаще переоценивает трудность": ("usually overestimates difficulty", "častěji nadhodnocuje obtížnost"),
    "в среднем близок к прохождению": ("usually close to observed play", "v průměru odpovídá skutečnému průchodu"),
    "Модель: ": ("Model: ", "Model: "),
    "Последняя: прогноз L": ("Latest: predicted L", "Poslední: predikce L"),
    " → стоимость ": (" → observed cost ", " → pozorovaná náročnost "),
    "Последние 10: ": ("Latest 10: ", "Posledních 10: "),
    " к предыдущим": (" vs previous", " oproti předchozím"),
    "Задачи с ядром гипотезы: ": ("Puzzles with a hypothesis kernel: ", "Hlavolamy s jádrem hypotézy: "),
    " · глубокие ": (" · deep ", " · hluboké "),
    "Сбросы: ": ("Resets: ", "Restarty: "),
    "Fallback генератора: ": ("Generator fallback: ", "Fallback generátoru: "),
    " из ": (" of ", " z "),
    "По стратегиям решения": ("By solving structure", "Podle struktury řešení"),
    " сесс. · ": (" sessions · ", " relací · "),
    " · Г ": (" · H ", " · H "),
    "Последняя траектория": ("Latest trajectory", "Poslední trajektorie"),
    "подробно ›": ("details ›", "podrobně ›"),
    "паузы +": ("pauses +", "pauzy +"),
    " · проверки гипотез ": (" · hypothesis checks ", " · ověření hypotéz "),
    " · намёк ": (" · hint ", " · nápověda "),
    " · без намёков": (" · no hints", " · bez nápověd"),
    "кандидаты: переходов ": ("candidates: switches ", "kandidáti: přechody "),
    " · максимум в клетке ": (" · max in one cell ", " · maximum v buňce "),
    "маршрут: согласование ": ("route: agreement ", "trasa: shoda "),
    " · начало ": (" · opening ", " · začátek "),
    " · порядок ": (" · order ", " · pořadí "),
    "модель каскада: до ": ("cascade model: up to ", "model kaskády: až "),
    " после ключевого вывода": (" after the key deduction", " po klíčovém závěru"),
    "ядро задачи: ": ("puzzle kernel: ", "jádro hlavolamu: "),
    "контекстные ложные варианты: ": ("contextual false candidates: ", "kontextové falešné možnosti: "),
    " · конфликт плиток ": (" · tile conflict ", " · konflikt dlaždic "),
    "точки гипотезы: ": ("hypothesis pivots: ", "body hypotézy: "),
    " · жизнеспособных ложных веток ": (" · viable false branches ", " · životaschopné falešné větve "),
    "структура: ": ("structure: ", "struktura: "),
    " рабочих фронта(ов)": (" active reasoning fronts", " aktivních front uvažování"),
    "Последние прохождения": ("Recent runs", "Poslední průchody"),
    "↺ ур.": ("↺ lvl ", "↺ úr. "),
    "тест ур.": ("test lvl ", "test úr. "),
    "ур.": ("lvl ", "úr. "),
    " · Г": (" · H", " · H"),
    " · Н": (" · Ht", " · N"),
    "  Л": ("  L", "  L"),
    "/В": ("/C", "/V"),
    "Экспорт исследовательских данных": ("Export research data", "Exportovat výzkumná data"),
    "ZIP: metadata + sessions + summary · отправка только после твоего действия": (
        "ZIP: metadata + sessions + summary · shared only after your action",
        "ZIP: metadata + sessions + summary · sdílení pouze po tvé akci"),
}


def java_quote(s: str) -> str:
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n') + '"'


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


def localize_unwrapped_literals(source: str) -> tuple[str, int]:
    changed = 0
    out = []
    for line in source.splitlines(keepends=True):
        # Do not touch Russian fallback arguments already inside localization calls.
        if "UiText.tr(" in line or "UiText.format(" in line:
            out.append(line)
            continue
        for russian, (english, czech) in ANALYSIS_TRANSLATIONS.items():
            old = java_quote(russian)
            if old not in line:
                continue
            new = f"UiText.tr({java_quote(english)}, {old}, {java_quote(czech)})"
            occurrences = line.count(old)
            line = line.replace(old, new)
            changed += occurrences
        out.append(line)
    return "".join(out), changed


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

    source, n = localize_unwrapped_literals(source)
    changed += n
    TARGET.write_text(source, encoding="utf-8")

    # Report only raw Cyrillic literals that are not already localization arguments.
    string_re = re.compile(r'"(?:\\.|[^"\\])*"')
    remaining = []
    localized_call_depth = 0
    for line_no, line in enumerate(source.splitlines(), 1):
        # Most calls are one-line; UiText.format spans several lines, so suppress its
        # argument block until the closing formatted statement.
        if "UiText.format(" in line:
            localized_call_depth = 1
        if localized_call_depth:
            if line.rstrip().endswith(");"):
                localized_call_depth = 0
            continue
        if "UiText.tr(" in line:
            continue
        for match in string_re.finditer(line):
            literal = match.group(0)
            if re.search(r'[А-Яа-яЁё]', literal):
                remaining.append((line_no, literal))

    lines = [
        "# V33 localization migration — remaining raw player-facing Russian",
        "",
        f"Safe localization pass changed **{changed}** code occurrences in `MainActivity.java`.",
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
    print(f"safe localization changes: {changed}; raw Cyrillic literals remaining: {len(remaining)}")


if __name__ == "__main__":
    main()
