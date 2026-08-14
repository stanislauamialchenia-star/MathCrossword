#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/tools/run_generator_harness.sh" "${1:-3}" 4 5 NETWORK structural
"$ROOT/tools/run_generator_harness.sh" "${1:-3}" 4 5 NETWORK orthogonal
"$ROOT/tools/run_generator_harness.sh" "${1:-3}" 5 5 CHAIN structural
"$ROOT/tools/run_generator_harness.sh" "${1:-3}" 5 5 CHAIN orthogonal
