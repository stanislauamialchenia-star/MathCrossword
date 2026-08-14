#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# Production path
"$ROOT/tools/run_generator_harness.sh" "${1:-5}" 5 5 HYPOTHESIS targeted
# Experimental contradiction constructor
"$ROOT/tools/run_generator_harness.sh" "${1:-3}" 5 5 HYPOTHESIS targeted experimental
