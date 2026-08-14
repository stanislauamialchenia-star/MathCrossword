#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/tools/.curve-classes"
rm -rf "$OUT"; mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT/app/src/main/java/com/offline/mathcrossword/SolutionStrategy.java" \
  "$ROOT/app/src/main/java/com/offline/mathcrossword/DifficultyScale.java" \
  "$ROOT/tools/DifficultyCurveHarness.java"
java -cp "$OUT" com.offline.mathcrossword.DifficultyCurveHarness "$@"
