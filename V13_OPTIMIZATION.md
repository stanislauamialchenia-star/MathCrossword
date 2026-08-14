# MathCrossword v13 — TileBankBuilder and strategy-specific calibration

v13 continues the "minimal outside, rich inside" direction. The visible game is intentionally almost unchanged; the work is inside the generator.

## 1. TileBankBuilder

The v12 profiler showed that `TILE_BANK` dominated Network generation. The expensive path repeatedly:

1. added one decoy;
2. recomputed HumanSolver domains;
3. searched another decoy;
4. repeated.

v13 replaces this with a separate Android-independent `TileBankBuilder`:

- compute the base domain picture once;
- derive a bounded external-value pool algebraically from equations touching hidden cells;
- test each candidate locally once;
- score which singleton/crossing cells it makes ambiguous;
- greedily choose a small decoy set;
- run the normal exact/HumanSolver validation afterwards.

This keeps the final solver checks authoritative while reducing repeated work inside tile-bank construction.

## 2. Extra timing stages

`TILE_BANK` is now split into:

- `TILE_POOL` — derive and validate candidate decoys;
- `TILE_SELECT` — greedily select the bank;

and hidden-mask generation adds:

- `HIDDEN_PREFILTER` — a cheap topology-only check before building a tile bank.

The prefilter is deliberately conservative: it may reject obviously unsuitable masks, but the full StrategyEvaluator remains the source of truth.

## 3. Deduction / Hypothesis evaluators

CHAIN and NETWORK already had separate definitions of a good puzzle. v13 extends the same architecture to:

- `DeductionEvaluator` — rewards intersection tightening and shallow deductions; penalizes depth-2 hypothesis reasoning;
- `HypothesisEvaluator` — rewards branching, viable alternatives and contradiction/lookahead reasoning.

This avoids forcing these strategies through one generic Logic-4/5 definition.

## 4. Early-stop calibration

Because Deduction/Hypothesis now have explicit evaluators, their hidden-search minimum sample budget is reduced once an actually on-target puzzle is found. The generator does not lower the requested difficulty; it simply stops spending samples after the target has already been verified.

## 5. Benchmark snapshot

The same deterministic Network benchmark seeds were run on v12 and v13 in this environment.

### Network Logic 4, 10 seeds

- v12 average generation: ~548 ms
- v13 average generation: ~208 ms
- v12 `TILE_BANK`: ~422 ms
- v13 `TILE_BANK`: ~108 ms

This is roughly a 2.6x end-to-end improvement and ~3.9x reduction in tile-bank time on that batch.

### Network Logic 5, 10 seeds

- v12 average generation: ~1061 ms
- v13 average generation: ~700 ms range in repeated runs

Logic 5 remains more variable. The profiler now shows that `TILE_POOL` and repeated hard hidden-set sampling dominate the remaining cost.

Bench outputs are stored under `tools/benchmark_notes_v13_*.csv`.

## 6. New bottleneck map

The current priority is no longer "optimize the whole generator". It is:

1. reduce repeated candidate-pool construction across neighbouring hidden masks;
2. improve Logic-5 constructive reliability, especially Hypothesis and Chain;
3. keep Network L5 from exhausting retries on seeds whose hidden topology is structurally unsuitable;
4. only then consider more arithmetic/UI features.

The important rule remains: do not make the generator faster by silently lowering puzzle quality.
