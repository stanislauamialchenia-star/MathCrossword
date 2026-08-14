# MathCrossword v17 — kernel shape + observed solving signals

v17 keeps the external game deliberately small. The main change is that MathCrossword now describes **both sides of a Hypothesis puzzle** more precisely:

1. the generator describes the shape of the false-but-plausible branches it created;
2. the local play-trace analyzer describes conservative signals in how the player actually worked through the board.

Neither layer is treated as a psychological diagnosis.

## 1. Contradiction-kernel shape

`ContradictionKernelAnalyzer` runs only after the winning Hypothesis L5 candidate has already been selected and exactly validated. It does not decide mathematical truth. It profiles the local candidate space around false branches.

Recorded fields:

- `contradictionKernelBranches` — how many locally viable false branches were found in the bounded profile;
- `contradictionKernelPivots` — how many different cells can serve as those branch points;
- `contradictionKernelDepth2Branches` — branches refuted by the bounded depth-2 human-style probe;
- `contradictionKernelDeepBranches` — branches that survive the depth-2 probe and therefore require deeper/other reasoning (exact distance is intentionally not claimed);
- `contradictionKernelMaxRemaining` — maximum unresolved board remaining after entering a false branch and propagating forced singles;
- `contradictionKernelFamily` — descriptive family:
  - `single-pivot`;
  - `two-stage`;
  - `deep-branch`;
  - `multi-pivot`;
  - `unprofiled` when a valid v16 kernel exists but the bounded v17 profile does not capture it.

The profiler is intentionally bounded. An early prototype that deeply probed every branch could make a few seeds pathologically expensive. v17 limits pivot/candidate count and only uses depth-2 probing. This keeps profiling descriptive rather than turning it into another exact solver.

## 2. Same-seed Hypothesis L5 benchmark

Reference v16 batch (`10` fixed seeds):

- generated 9/10;
- unique 9/9;
- target strategy 8/10;
- average wall time ~1518 ms/request.

v17 on the same batch:

- generated 9/10;
- unique 9/9;
- target strategy 8/10;
- average wall time ~1543 ms/request;
- contradiction kernel in 8/9 generated puzzles;
- average profiled branches per kernel: ~3.13;
- average profiled pivots per kernel: ~2.88;
- 37.5% of kernel puzzles contained at least one branch that survived the bounded depth-2 probe;
- observed profile families: `multi-pivot`, `deep-branch`, `two-stage`, plus one `unprofiled` kernel.

`KERNEL_PROFILE` cost was ~40 ms/request in this batch. The extra semantic description therefore costs only a small fraction of total generation time while leaving the original v16 success/uniqueness results unchanged on this regression set.

Benchmark: `tools/benchmark_notes_v17_hypothesis_l5.csv`.

## 3. Play-trace process signals

`PlayTraceAnalyzer` derives conservative interaction signals from the already-local JSONL event history:

- `productivePauses` — a >=3 s pause followed by a short sequence of constructive placements/candidate work without immediate undo/hint/reset;
- `deadEndPauses` — a >=3 s pause followed shortly by undo/reset/hint/full-board failure;
- `hypothesisEpisodes` — tentative-placement/revision patterns that look like branch testing;
- `candidateCommitments` and `avgCandidateCommitmentMs` — candidate notation followed by an actual placement in the same cell;
- `recoveryEpisodes` and `avgRecoveryActions` — amount of interaction between an error/revision signal and renewed forward placement;
- `rapidCascades` — clusters of several meaningful actions under ~0.9 s apart.

These are **behavioral signals**, not claims about hidden cognition. For example, an Undo may reflect a hypothesis test, a slip, or a UI correction. Repeated patterns across many puzzles are more informative than a single event.

## 4. What the in-game analyzer now exposes

The existing `Анализ прохождений` screen remains compact but adds:

- productive vs dead-end pause counts;
- hypothesis-testing signals;
- number of sessions containing contradiction kernels and how many contained deeper branches;
- per-strategy average hypothesis-signal rate;
- a compact `Последняя траектория` summary;
- the kernel family of the last relevant Hypothesis puzzle.

The full event trace remains local for deeper later analysis.

## 5. Hints now use task analysis

The hint planner can use `contradictionKernelFamily` without revealing the answer:

- `multi-pivot` -> suggests comparing several possible hypothesis entry points;
- `deep-branch` -> warns that a false branch may survive local checks and should be followed farther;
- `two-stage` -> explicitly suggests carrying the assumption through another consequence;
- `single-pivot` -> points toward one high-value branch point.

This is the first direct feedback loop from **generator analysis -> player support** while keeping hints non-automatic.

## 6. What this enables later

With enough sessions we can compare:

`task kernel shape -> HumanSolver prediction -> actual play trace`.

Examples of useful questions:

- Do multi-pivot kernels produce more candidate notation or only more time?
- Are productive pauses concentrated before a strategy transition?
- Does the player actually enter the false branch the generator intended?
- Which hint family leads to renewed independent progress rather than another hint?
- Does a strategy become faster while retaining the same structural difficulty?
- Where does predicted Logic systematically disagree with observed interaction cost?

The next high-value layer is not more UI. It is **calibration over repeated traces**: learning curves by strategy/family and a transparent observed-difficulty proxy before considering any ML.
