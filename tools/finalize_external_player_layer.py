#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/offline/mathcrossword/MainActivity.java"
TRACKER = ROOT / "app/src/main/java/com/offline/mathcrossword/SessionTracker.java"


def replace_exact(source: str, old: str, new: str, label: str) -> tuple[str, int]:
    if new in source:
        return source, 0
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one occurrence, found {count}")
    return source.replace(old, new, 1), 1


def main() -> None:
    main_source = MAIN.read_text(encoding="utf-8")
    tracker_source = TRACKER.read_text(encoding="utf-8")
    changed = 0

    main_replacements = [
        (
            '                    + " · возвратов " + last.candidateCellRevisits\n',
            '                    + UiText.tr(" · revisits ", " · возвратов ", " · návratů ") + last.candidateCellRevisits\n',
            "analysis revisits label",
        ),
        (
            '                return info.versionName == null ? "1.36" : info.versionName;\n',
            '                return info.versionName == null ? "1.37" : info.versionName;\n',
            "version-name null fallback",
        ),
        (
            '                return "1.36";\n',
            '                return "1.37";\n',
            "version-name exception fallback",
        ),
        (
            '                return 36L;\n',
            '                return 37L;\n',
            "version-code exception fallback",
        ),
    ]

    for old, new, label in main_replacements:
        main_source, n = replace_exact(main_source, old, new, label)
        changed += n

    tracker_source, n = replace_exact(
        tracker_source,
        '            if (moves.length() > limit) out.append("\\n… ещё ").append(moves.length() - limit).append(UiText.tr(" more moves", " ходов", " tahů"));\n',
        '            if (moves.length() > limit) out.append(UiText.tr("\\n… ", "\\n… ещё ", "\\n… ještě ")).append(moves.length() - limit).append(UiText.tr(" more moves", " ходов", " tahů"));\n',
        "trajectory overflow label",
    )
    changed += n

    MAIN.write_text(main_source, encoding="utf-8")
    TRACKER.write_text(tracker_source, encoding="utf-8")
    print(f"final external-player cleanup changes: {changed}")


if __name__ == "__main__":
    main()
