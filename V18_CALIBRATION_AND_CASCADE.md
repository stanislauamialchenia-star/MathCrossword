# MathCrossword v18 — anti-cascade path + transparent difficulty calibration

v18 was triggered by a real gameplay report after roughly 80 Path levels: **once one example was solved, the rest of the board often filled almost automatically**.

The important finding was not merely that some boards felt easy. The Path progression and acceptance rules allowed a structural failure mode: high Path levels could still be generated as Logic 3 and accepted even when the basic HumanSolver could force essentially every hidden cell.

## 1. Path progression fix

The Path profile now moves into Logic 4 earlier:

- 1–70: existing progression up to Logic 3;
- 71–85: Logic 4, 9 equations, 11 target hidden cells;
- 86–100: Logic 4, 10 equations, 12 target hidden cells;
- 101–150: Logic 4 / Calc 4;
- 151+: Logic 5 bands.

For Logic >= 3, Path no longer accepts a board only because it has a rated label. It recomputes `LogicAnalyzer` + `HumanSolver` metrics and requires the board to pass the current level gate.

A fixed Path probe illustrates the change around the reported point:

| Probe | Basic forced at opening | Remaining after basic opening | Max forced cascade after a reasoning step |
|---|---:|---:|---:|
| v17 level 80 | 8 / 8 | 0 | effectively whole board |
| v18 level 80 | 2 / 10 | 8 | 3 / 10 |

The v18 row is a deterministic regression sample, not a population-level performance claim. See `tools/benchmark_notes_v18_path_70_100.csv`.

## 2. Cascade is now a first-class structural signal

`CascadeResilienceAnalyzer` profiles how fragile a board is to information injection. Puzzle metadata now records:

- `maxForcedCascade` — the largest HumanSolver forced cascade after a legitimate reasoning step;
- `maxResolvedAfterOneCell`;
- `maxAdditionalForcedAfterOneCell`;
- `maxResolvedFractionAfterOneCell`;
- `vulnerableSingleCells`;
- equation-reveal counterparts.

### Important distinction

The one-cell truth-reveal test is **descriptive**, not a hard difficulty gate. Injecting an arbitrary correct value gives the solver information the player may not have earned; in a connected unique puzzle that can legitimately collapse a large portion of the board. Therefore v18 uses the HumanSolver's **post-reasoning `maxForcedCascade`** as the main anti-automatic gate.

Generic Logic 4/5 and Deduction/Hypothesis/Network evaluators now cap oversized forced cascades. Chain intentionally keeps a different rule because a long dependency cascade is part of the target structure for that strategy.

## 3. `mixed-two-front`

Hard MIXED/Path generation can now try a physical `mixed-two-front` family: two separated cyclic reasoning regions are constructed before extra equations are added.

The goal is not just "more equations". It is to reduce the chance that discovering one entry point turns the entire board into one monotonically forced tree. A player should sometimes have to leave one front partially unresolved and find information elsewhere.

## 4. Path regression harness

New tools:

- `tools/PathHarness.java`
- `tools/run_path_harness.sh`
- `tools/benchmark_notes_v18_path_70_100.csv`

The current fixed probes for levels 75–100 all pass the strengthened acceptance gate. At level 80 the basic opener resolves 2/10 hidden cells rather than the full board.

Some seeds still take several seconds to construct (notably the 85/95 probes). Correctness/structure is being kept ahead of raw generation speed; Path prefetch hides much of this in normal use. Those slow seeds are the next performance target rather than a reason to weaken the gate.

## 5. Network L5 under the stricter gate

Reference 10-seed run: `tools/benchmark_notes_v18_network_l5.csv`.

- generated: 8/10;
- unique: 8/8;
- target matched: 7/10;
- fallback: 1;
- average request time: ~2.01 s.

This is better reliability/target matching than the earlier v17 control (6/10 generated, 5/10 matched), at the cost of additional profiling and stricter acceptance. Current expensive stages remain TileBank construction and cascade profiling.

## 6. Hypothesis L5 regression

Reference: `tools/benchmark_notes_v18_hypothesis_l5.csv`.

- generated: 8/10;
- unique: 8/8;
- target matched: 8/10;
- fallback: 0;
- contradiction kernel present in all accepted puzzles;
- observed kernel families in this sample: `two-stage:5`, `multi-pivot:3`.

## 7. Transparent `DifficultyCalibrator`

v18 adds an Android-independent, local-only calibrator before any ML is considered.

It deliberately separates two things:

- **predicted difficulty**: the engine's `ratedLogic` / requested Logic band;
- **observed personal solving cost**: a transparent score derived from solved play traces.

The raw observed-cost score uses:

- active seconds per hidden cell;
- events per hidden cell;
- Undo / full-incorrect revisions;
- candidate edits;
- dead-end pauses;
- hints.

Solved sessions are ranked relative to the player's own recent history and mapped to five observed-cost bands. This is not a universal claim that a certain number of minutes equals Logic 4. It is a calibration tool for answering questions such as:

> "The engine calls this L5, but does this structure still cost this player L5-like effort?"

The calibrator needs at least 8 solved sessions. When the newest generator version has 8+ solved sessions it calibrates only within that version; before that it labels the result as historical/mixed so generator changes are not silently treated as the same stimulus.

The existing Analysis screen can show:

- predicted ±1-band accuracy;
- whether the model tends to under- or overestimate observed cost;
- latest predicted Logic vs observed cost band;
- recent cost trend;
- rapid cascades of player actions.

No ML and no network service are involved.

## 8. What this enables next

The generator and analyzer can now be compared directly:

`task structure -> HumanSolver prediction -> generated cascade profile -> real play trace -> observed cost -> calibration error`

That opens the next work in order:

1. collect v18 play traces from the repaired 70–100 Path band;
2. compare predicted `maxForcedCascade` with actual rapid-action cascades;
3. find structures where calibration error is systematic;
4. use those errors to improve constructors/evaluators rather than merely changing global difficulty coefficients;
5. only after enough clean data consider a learned ranker.

## Validation in this development environment

- Android-independent generator/harness compiles and runs with `javac`;
- fixed Path regression harness runs;
- Network/Hypothesis reference CSVs were produced;
- `SessionTracker`, `PlayTraceAnalyzer` and `DifficultyCalibrator` were syntax-compiled against minimal Android/JSON stubs;
- a synthetic `DifficultyCalibrator` smoke test passed.

A full Android APK was not built in this environment because the project snapshot does not include a Gradle wrapper/Android SDK here. Final phone/UI smoke testing should still be done from Android Studio.
