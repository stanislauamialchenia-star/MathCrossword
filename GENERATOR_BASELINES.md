# Generator baselines

## Why keep baselines
Puzzle quality can regress even when code becomes more sophisticated. A generator version is therefore part of the experiment, not just an implementation detail.

## Current markers
- v8: first architecture-separated generator + strategies + hint engine. Keep the original `MathCrossword_v8.zip` as the historical source snapshot.
- v9: resilient deterministic search. Play traces record `generatorVersion`, `generationStage`, and `strategyTargetMatched`.

## v9 generation stages
1. Strict requested-strategy search.
2. Requested Logic passed, but the requested strategy signature was only the closest available.
3. Stable MIXED-policy airbag: requested Logic/Calc preserved, requested strategy not claimed as matched.
4. Emergency same-rated candidate from the strict phase. Correctness and unique solution are still required.

## Rule
Never silently lower arithmetic correctness or unique-solution requirements. For research comparisons, do not mix results from different `generatorVersion` values without grouping them.

## Rollback
The safest rollback is a source snapshot, not hidden compatibility code. Keep the v8 archive unchanged. If v9 produces worse puzzles, use the v8 project while the v9 generator is repaired; play traces make the versions distinguishable.

## v10 — constructive baseline

v10 is the first baseline where CHAIN and NETWORK can use strategy-specific constructors rather than only strategy-weighted generic geometry.

Keep v9 and v10 as separate tags. They answer different questions:

- v9: how far can one generic constructor be pushed by scoring/policies?
- v10: what changes when construction itself becomes strategy-specific?

This distinction is important for comparing generation speed, rejection profiles and player traces.

## v16 — candidate-domain hypothesis baseline

v16 adds an explicit Hypothesis L5 contradiction-kernel signal. The generator prefers hidden masks containing a false value that survives immediate local propagation while exact uniqueness proves it cannot belong to a complete solution. Kernel reinforcement is post-selection and bounded; it is not run inside every hidden-mask attempt.

Reference benchmark: `tools/benchmark_notes_v16_hypothesis_l5.csv`.


## v18 — anti-cascade / calibration baseline

v18 is the first baseline created explicitly in response to a real Path failure mode: around level 80 a Logic-3 board could be accepted even when the basic solver forced the whole hidden set.

Keep v17 and v18 separate when comparing play traces. v18 changes both the Path stimulus (71–100 now Logic 4 with stronger acceptance) and the recorded structural metadata. It also introduces `mixed-two-front` and the first transparent personal difficulty calibrator.

Reference files:
- `tools/benchmark_notes_v18_path_70_100.csv`;
- `tools/benchmark_notes_v18_network_l5.csv`;
- `tools/benchmark_notes_v18_hypothesis_l5.csv`.

## v20 — hard-Path performance baseline

v20 keeps the v19 level/replay/decoy semantics but changes where expensive work is spent. Hard PATH candidates use a dedicated search intent, deferred cascade profiling and safe decoy rollback. The mathematical gates remain exact and the anti-collapse acceptance remains active.

Use v20 as the reference when comparing generation latency for levels 70–100. Do not compare raw wall time across devices as if it were absolute; compare the same harness/seeds on the same machine.

Reference file: `tools/benchmark_notes_v20_path_70_100.csv`.


## v21 — quality-of-uncertainty baseline

v21 keeps the v20 hard-Path search architecture but adds bounded post-selection analysis of **how** uncertainty is structured. It profiles compact hypothesis pivots, locally viable false branches, contextual/resource-conflict decoys and multiple unresolved reasoning fronts.

A key negative experiment is part of this baseline: forcing contextual decoys inside the initial `TileBankBuilder` made hidden-mask rejection and uniqueness checks substantially more expensive. That change was reverted. v21 instead analyzes the winning bank first, replaces generic surplus tiles when a stronger contextual decoy can be proven safe, and adds at most one extra tile only when it improves the ambiguity profile without damaging Path acceptance.

The baseline also removes wall-clock stopping conditions from branch profiling; bounded deterministic probe counts preserve seed reproducibility across runs.

Reference files:
- `tools/benchmark_notes_v21_path_70_84.csv`;
- `tools/benchmark_notes_v21_path_70_100.csv`.

Use v20 for pure hard-Path latency comparisons and v21 for quality-of-uncertainty comparisons. Do not interpret one small deterministic benchmark batch as a population-level gameplay result.


## v22 — smooth-difficulty baseline

v22 changes the public difficulty coordinate from five bands to **ten bands backed by continuous scores**. The five mature engine tiers are deliberately retained underneath as constructor/evaluator capability anchors. This isolates a progression-model change from a wholesale generator rewrite.

The first Path page spends extra resolution in the former Logic-3/Logic-4 transition. Anti-collapse thresholds are interpolated from continuous `logicScore` rather than switching at a fixed level number. Calculation difficulty also becomes genuinely ten-step through arithmetic caps and operation availability.

Reference files:
- `tools/benchmark_notes_v22_difficulty_curve.csv`;
- `tools/benchmark_notes_v22_long_curve.csv`;
- `tools/benchmark_notes_v22_path_curve.csv`.

Use v21 when comparing quality-of-uncertainty mechanics and v22 when comparing progression smoothness. The current `ratedDisplayLogic` heuristic is not a human-ground-truth score; the local calibrator is expected to expose family-specific over/under-rating once enough v22 first-pass traces exist.
