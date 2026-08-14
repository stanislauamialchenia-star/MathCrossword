# MathCrossword v12 — generator bottleneck pass

v12 deliberately adds almost nothing to the visible UI. The focus is the expensive hidden-cell / tile-bank stage discovered by the v11 harness.

## What changed

### 1. Known-solution uniqueness check
During generation the engine already knows one valid assignment: the values stored in the hidden cells. v11 used the generic solution counter, which rediscovered that known solution before it could prove uniqueness.

v12 adds `SolutionCounter.hasUniqueKnownSolution()` and searches directly for a *different* valid assignment. If none exists, the puzzle is unique.

### 2. Bounded mathematical decoy search
The old ambiguity reinforcement could scan almost every integer from 1 to `maxNumber` for each singleton cell. At `maxNumber=1000` this was expensive, especially on Network puzzles.

v12 derives a compact candidate pool from the actual equations touching the target cell. It solves those relations backwards (`a op b = c`) to obtain plausible external values, validates them against the full local constraint system, and only uses a small bounded random fallback for unusual exponent/division layouts.

### 3. Adaptive best-of-N hidden search
Hard puzzles still use best-of-N hidden-set generation, but once a candidate has reached the requested strategy-specific difficulty band the search may stop after a minimum number of quality samples. This preserves selection pressure without paying for all 24–28 hidden attempts every time.

### 4. Spend some speedup on reliability
CHAIN/NETWORK get one additional constructive outer attempt. The cheaper hidden stage makes this still faster than v11 in the tested batches, while reducing avoidable seed failures.

### 5. Better stage diagnostics
The harness now breaks the old `HIDDEN_UNIQUENESS` bucket into:

- `HIDDEN_SET`
- `TILE_BANK`
- `UNIQUENESS`
- `HIDDEN_HUMAN`

The aggregate stage is still recorded for backwards comparison.

## Benchmark note
Container JVM, same diagnostic seed family; these numbers are not phone performance guarantees.

A v11 NETWORK L4 batch (5 samples) was roughly **1.67 s/puzzle**, with about **1.65 s** attributed to the old combined hidden/uniqueness stage.

After the first v12 optimization pass, NETWORK L4 was roughly **0.43–0.62 s/puzzle** in repeated 5-sample batches, while all generated samples remained exact-unique and target-matched in those batches.

NETWORK L5 also dropped substantially in successful batches, while remaining the more variable case. The new substage timing shows that the largest remaining cost is now **TILE_BANK**, not geometry and not the exact uniqueness search.

This is the intended behavior of the profiler: after one bottleneck is reduced, the next one becomes visible.

## Next bottlenecks

1. `TILE_BANK`: build convincing decoys with fewer repeated HumanSolver recomputations.
2. Generic DEDUCTION / HYPOTHESIS construction: these strategies still rely more heavily on rejection sampling than CHAIN / NETWORK.
3. NETWORK L5 pathological seeds: distinguish structurally bad graphs from arithmetic/hidden-set failures earlier.

The next optimization should target these directly rather than increasing global retry counts.
