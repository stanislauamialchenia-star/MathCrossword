#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "app/src/main/java/com/offline/mathcrossword/MainActivity.java"


def replace_exact(source: str, old: str, new: str, label: str) -> tuple[str, int]:
    if new in source:
        return source, 0
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one occurrence, found {count}")
    return source.replace(old, new, 1), 1


def main() -> None:
    source = TARGET.read_text(encoding="utf-8")
    changed = 0

    replacements = [
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

    for old, new, label in replacements:
        source, n = replace_exact(source, old, new, label)
        changed += n

    TARGET.write_text(source, encoding="utf-8")
    print(f"final external-player cleanup changes: {changed}")


if __name__ == "__main__":
    main()
