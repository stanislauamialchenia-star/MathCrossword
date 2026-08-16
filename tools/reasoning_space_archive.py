#!/usr/bin/env python3
"""Build a small MAP-Elites-style archive from ReasoningSpaceCorpusHarness CSV.

This intentionally uses only the Python standard library. The first PoC borrows the
Quality-Diversity idea (best representative per reasoning region) without introducing
pyribs as a project dependency.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
from collections import Counter, defaultdict
from pathlib import Path


def as_int(row: dict[str, str], key: str, default: int = 0) -> int:
    try:
        return int(float(row.get(key, "")))
    except (TypeError, ValueError):
        return default


def as_float(row: dict[str, str], key: str, default: float = 0.0) -> float:
    try:
        return float(row.get(key, ""))
    except (TypeError, ValueError):
        return default


def branch_bin(width: int) -> str:
    if width <= 1:
        return "1"
    if width == 2:
        return "2"
    if width == 3:
        return "3"
    if width == 4:
        return "4"
    return "5+"


def front_bin(fronts: int) -> str:
    if fronts <= 1:
        return "1"
    if fronts == 2:
        return "2"
    return "3+"


def region(row: dict[str, str]) -> tuple[str, str, str]:
    return (
        str(as_int(row, "reasoning_depth")),
        branch_bin(as_int(row, "max_branch_width")),
        front_bin(as_int(row, "alternative_fronts")),
    )


def elite_key(row: dict[str, str]) -> tuple[float, ...]:
    """Lexicographic quality; dimensions are deliberately not folded into hardness."""
    generated = as_int(row, "generated")
    unique = as_int(row, "unique")
    target = as_int(row, "target_matched")
    hidden = max(1, as_int(row, "hidden", 1))
    opening_fraction = as_int(row, "basic_forced") / hidden
    attempts = as_int(row, "generation_attempts")
    rejects = as_int(row, "generation_rejects")
    generation_ms = as_float(row, "generation_ms")

    # Higher tuple wins. Mathematical validity and target provenance dominate.
    # Cost is a tiebreaker, not a proxy for difficulty.
    return (
        generated,
        unique,
        target,
        -opening_fraction,
        -math.log1p(max(0, attempts)),
        -math.log1p(max(0, rejects)),
        -math.log1p(max(0.0, generation_ms)),
    )


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("input", type=Path, help="CSV emitted by ReasoningSpaceCorpusHarness")
    ap.add_argument("--out-dir", type=Path, default=Path("reasoning-space-output"))
    args = ap.parse_args()

    args.out_dir.mkdir(parents=True, exist_ok=True)

    with args.input.open(newline="", encoding="utf-8") as fh:
        rows = list(csv.DictReader(fh))

    generated = [r for r in rows if as_int(r, "generated") == 1]
    valid = [r for r in generated if as_int(r, "unique") == 1]

    elites: dict[tuple[str, str, str], dict[str, str]] = {}
    occupants: Counter[tuple[str, str, str]] = Counter()
    strategies_by_region: dict[tuple[str, str, str], Counter[str]] = defaultdict(Counter)
    constructors_by_region: dict[tuple[str, str, str], Counter[str]] = defaultdict(Counter)

    for row in valid:
        reg = region(row)
        occupants[reg] += 1
        strategies_by_region[reg][row.get("strategy", "")] += 1
        constructors_by_region[reg][row.get("constructor", "")] += 1
        if reg not in elites or elite_key(row) > elite_key(elites[reg]):
            elites[reg] = row

    elite_path = args.out_dir / "reasoning_space_elites.csv"
    if rows:
        fields = ["depth_bin", "branch_bin", "front_bin", "occupants"] + list(rows[0].keys())
        with elite_path.open("w", newline="", encoding="utf-8") as fh:
            writer = csv.DictWriter(fh, fieldnames=fields)
            writer.writeheader()
            for reg in sorted(elites):
                row = dict(elites[reg])
                writer.writerow({
                    "depth_bin": reg[0],
                    "branch_bin": reg[1],
                    "front_bin": reg[2],
                    "occupants": occupants[reg],
                    **row,
                })

    all_depth = sorted({str(as_int(r, "reasoning_depth")) for r in valid})
    all_branch = sorted({branch_bin(as_int(r, "max_branch_width")) for r in valid})
    all_front = sorted({front_bin(as_int(r, "alternative_fronts")) for r in valid})
    theoretical_cells = max(1, len(all_depth) * len(all_branch) * len(all_front))

    summary = {
        "input_rows": len(rows),
        "generated_rows": len(generated),
        "unique_rows": len(valid),
        "occupied_regions": len(elites),
        "observed_axis_values": {
            "reasoning_depth": all_depth,
            "max_branch_width_bin": all_branch,
            "alternative_fronts_bin": all_front,
        },
        "coverage_over_observed_axis_product": len(elites) / theoretical_cells,
        "regions": [],
    }

    for reg in sorted(elites):
        summary["regions"].append({
            "reasoning_depth": reg[0],
            "branch_bin": reg[1],
            "front_bin": reg[2],
            "occupants": occupants[reg],
            "strategies": dict(strategies_by_region[reg]),
            "constructors": dict(constructors_by_region[reg]),
            "elite_seed": elites[reg].get("seed", ""),
        })

    (args.out_dir / "reasoning_space_summary.json").write_text(
        json.dumps(summary, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )

    print(
        "reasoning-space archive: "
        f"rows={len(rows)} generated={len(generated)} unique={len(valid)} "
        f"occupied_regions={len(elites)} "
        f"observed_product_coverage={summary['coverage_over_observed_axis_product']:.3f}"
    )
    for reg in sorted(elites):
        print(
            f"  depth={reg[0]} branch={reg[1]} fronts={reg[2]} "
            f"n={occupants[reg]} strategies={dict(strategies_by_region[reg])}"
        )


if __name__ == "__main__":
    main()
