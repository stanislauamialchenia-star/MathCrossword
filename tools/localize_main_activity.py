#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "app/src/main/java/com/offline/mathcrossword/MainActivity.java"
REPORT = ROOT / "V33_LOCALIZATION_REMAINING.md"

# Exact Russian Java string literals -> English / Czech.
# We keep the Russian source text as the middle UiText.tr argument so behavior on ru devices
# is unchanged while en/cs devices get localized player-facing copy.
TRANSLATIONS = {
    "обновление не проверено": ("update not checked", "aktualizace nezkontrolována"),
    "Не удалось собрать уровень — попробуй ещё раз": ("Could not build this level — try again", "Úroveň se nepodařilo vytvořit — zkus to znovu"),
    "Не нашёл достаточно сильную головоломку — нажми ещё раз": ("Could not find a strong enough puzzle — try again", "Nepodařilo se najít dostatečně silný hlavolam — zkus to znovu"),
    "Математический": ("Math", "Matematická"),
    "кроссворд": ("Crossword", "křížovka"),
    "Генерирую уровень ": ("Generating level ", "Generuji úroveň "),
    "Продолжить — уровень ": ("Continue — level ", "Pokračovat — úroveň "),
    "Выбрать уровень": ("Choose level", "Vybrat úroveň"),
    "Свободная игра": ("Free Play", "Volná hra"),
    "Библиотека решений": ("Solution Library", "Knihovna řešení"),
    "Анализ прохождений": ("Play Analysis", "Analýza průchodů"),
    "Конфиденциальность": ("Privacy", "Soukromí"),
    " · офлайн · без рекламы": (" · offline · no ads", " · offline · bez reklam"),
    "данные и история решения хранятся локально": ("data and solve history stay on this device", "data a historie řešení zůstávají v zařízení"),
    "Уровни ": ("Levels ", "Úrovně "),
    "Прогресс: уровень ": ("Progress: level ", "Postup: úroveň "),
    " · переигрывание прогресс не сбрасывает": (" · replay does not reset progress", " · opakování nesmaže postup"),
    "Следующие закрыты": ("Next locked", "Další uzamčeny"),
    "Пример": ("Example", "Příklad"),
    "Как действовать": ("How to proceed", "Jak postupovat"),
    "← Предыдущий": ("← Previous", "← Předchozí"),
    "Следующий →": ("Next →", "Další →"),
    "Логика": ("Logic", "Logika"),
    "Вычисления": ("Calculation", "Výpočty"),
    "Размер поля": ("Board size", "Velikost pole"),
    "Числа до": ("Numbers up to", "Čísla do"),
    "Стратегия решения": ("Solving structure", "Struktura řešení"),
    "Дед.": ("Ded.", "Ded."),
    "Цепь": ("Chain", "Řetěz"),
    "Гип.": ("Hyp.", "Hyp."),
    "Сеть": ("Net", "Síť"),
    "Микс": ("Mixed", "Mix"),
    "Операции": ("Operations", "Operace"),
    "Генерирую…": ("Generating…", "Generuji…"),
    "Сгенерировать": ("Generate", "Vygenerovat"),
    "Стратегия меняет структуру рассуждения, а не только форму поля": ("The solving structure changes the reasoning, not just the board shape", "Struktura řešení mění způsob uvažování, ne jen tvar pole"),
    "Логика и вычисления остаются независимыми шкалами": ("Logic and calculation remain independent scales", "Logika a výpočty zůstávají nezávislé škály"),
    "Уровень ": ("Level ", "Úroveň "),
    "Показать панели": ("Show panels", "Zobrazit panely"),
    "Режим фокуса": ("Focus mode", "Režim soustředění"),
    "Показать кандидаты": ("Show candidates", "Zobrazit kandidáty"),
    "Скрыть кандидаты": ("Hide candidates", "Skrýt kandidáty"),
    "Головоломка": ("Puzzle", "Hlavolam"),
    "Перезапустить": ("Restart", "Restartovat"),
    "Закрыть": ("Close", "Zavřít"),
    "↶ Отмена": ("↶ Undo", "↶ Zpět"),
    "✎ Канд.": ("✎ Cand.", "✎ Kand."),
    "? Намёк": ("? Hint", "? Nápověda"),
    "Готово ✓": ("Solved ✓", "Hotovo ✓"),
    "Следующий уровень  →": ("Next level  →", "Další úroveň  →"),
    "Новая головоломка  →": ("New puzzle  →", "Nový hlavolam  →"),
    "Оставь хотя бы одну операцию": ("Keep at least one operation", "Ponech alespoň jednu operaci"),
    "Степени пока работают в смешанном режиме — оставь ещё одну базовую операцию": ("Powers currently work in mixed mode — keep one more basic operation", "Mocniny zatím fungují ve smíšeném režimu — ponech ještě jednu základní operaci"),
    "Намёк ": ("Hint ", "Nápověda "),
    "Глубже →": ("Deeper →", "Hlouběji →"),
    "Пока нет завершённых сессий": ("No completed sessions yet", "Zatím nejsou žádné dokončené relace"),
    "Ход решения": ("Solve path", "Průběh řešení"),
    "Загрузка обновления": ("Downloading update", "Stahování aktualizace"),
    "скачиваю ": ("downloading ", "stahuji "),
    "Обновление скачивается внутри приложения": ("The update is downloading inside the app", "Aktualizace se stahuje přímo v aplikaci"),
    "ошибка загрузки": ("download error", "chyba stahování"),
    "Не удалось начать загрузку: ": ("Could not start download: ", "Stahování se nepodařilo spustit: "),
    "скачано ": ("downloaded ", "staženo "),
    "Android не смог скачать обновление": ("Android could not download the update", "Android nemohl stáhnout aktualizaci"),
    "Разрешить обновления": ("Allow updates", "Povolit aktualizace"),
    "Android один раз попросит разрешить MathCrossword устанавливать скачанные обновления. После этого вернись в игру — установка продолжится сама.": ("Android will ask once to let MathCrossword install downloaded updates. Then return to the game and installation will continue automatically.", "Android jednou požádá o povolení, aby MathCrossword mohl instalovat stažené aktualizace. Potom se vrať do hry a instalace bude automaticky pokračovat."),
    "Разрешить": ("Allow", "Povolit"),
    "Позже": ("Later", "Později"),
    "APK скачан, но установщик не открылся": ("APK downloaded, but the installer did not open", "APK je stažené, ale instalátor se neotevřel"),
    "проверяю…": ("checking…", "kontroluji…"),
    "доступна ": ("available ", "dostupná "),
    "актуальная": ("up to date", "aktuální"),
    "Есть обновление": ("Update available", "Je dostupná aktualizace"),
    "Обновление не требуется": ("No update needed", "Aktualizace není potřeba"),
    "Установлена: ": ("Installed: ", "Nainstalováno: "),
    "Последняя: ": ("Latest: ", "Nejnovější: "),
    "Скачать": ("Download", "Stáhnout"),
    "ошибка проверки": ("check error", "chyba kontroly"),
    "Не удалось проверить обновление: ": ("Could not check for updates: ", "Aktualizace se nepodařilo zkontrolovat: "),
    "Пока нечего экспортировать": ("Nothing to export yet", "Zatím není co exportovat"),
    "Не удалось открыть выбор файла": ("Could not open file picker", "Výběr souboru se nepodařilo otevřít"),
    "Экспортировано сессий: ": ("Exported sessions: ", "Exportované relace: "),
    "Поделиться исследовательскими данными": ("Share research data", "Sdílet výzkumná data"),
    "Не удалось собрать исследовательский ZIP": ("Could not create research ZIP", "Výzkumný ZIP se nepodařilo vytvořit"),
    "Свободная": ("Free", "Volná"),
    "одна опорная гипотеза": ("single pivot hypothesis", "jedna opěrná hypotéza"),
    "двухступенчатая гипотеза": ("two-stage hypothesis", "dvoustupňová hypotéza"),
    "глубокая ложная ветка": ("deep false branch", "hluboká falešná větev"),
    "несколько точек гипотезы": ("multiple hypothesis pivots", "více bodů hypotézy"),
}


def java_quote(s: str) -> str:
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n') + '"'


def main() -> None:
    source = TARGET.read_text(encoding="utf-8")
    changed = 0
    for russian, (english, czech) in TRANSLATIONS.items():
        old = java_quote(russian)
        new = f"UiText.tr({java_quote(english)}, {old}, {java_quote(czech)})"
        count = source.count(old)
        if count:
            source = source.replace(old, new)
            changed += count

    TARGET.write_text(source, encoding="utf-8")

    # Report remaining Cyrillic literals. This is intentionally a report rather than a build
    # failure: the migration can land in small reviewable slices while the draft PR stays open.
    string_re = re.compile(r'"(?:\\.|[^"\\])*"')
    remaining = []
    for line_no, line in enumerate(source.splitlines(), 1):
        for match in string_re.finditer(line):
            literal = match.group(0)
            if re.search(r'[А-Яа-яЁё]', literal):
                remaining.append((line_no, literal))

    lines = [
        "# V33 localization migration — remaining player-facing Russian",
        "",
        f"Automatic pass replaced **{changed}** exact string occurrences in `MainActivity.java`.",
        "",
        "The entries below still contain Cyrillic and need a deliberate translation or a decision to keep them as internal/research text.",
        "",
    ]
    if not remaining:
        lines.append("No Cyrillic Java string literals remain in `MainActivity.java`.")
    else:
        for line_no, literal in remaining:
            lines.append(f"- line {line_no}: `{literal}`")
    REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"localized occurrences: {changed}; remaining Cyrillic literals: {len(remaining)}")


if __name__ == "__main__":
    main()
