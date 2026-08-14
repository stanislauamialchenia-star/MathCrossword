# MathCrossword v19 — level replay, anti-collapse from 70, believable false candidates

v19 was driven by two gameplay observations/requests:

1. the automatic-collapse problem was not isolated to level 80; it had become common from roughly level 70 onward;
2. replaying exact Path levels is useful both as a player feature and as a controlled test tool;
3. extra wrong tiles are useful only when they are mathematically believable, not random visual noise.

## 1. Path hard band now starts at level 70

The old boundary `<= 70` kept level 70 in Logic 3. v19 moves the boundary by one:

- 1–69: previous progression up to Logic 3;
- 70–85: Logic 4;
- 86–100: Logic 4 with the larger board;
- later bands continue as before.

For level 70+ Path generation now has an explicit anti-collapse acceptance gate based on **reachable HumanSolver behaviour**:

- small basic opening only;
- bounded `maxForcedCascade` after an actual reasoning deduction;
- Logic band still has to pass `LogicAnalyzer.acceptForLevel`.

Important correction from experimentation: arbitrary injection of a true hidden value (`maxResolvedAfterOneCell`) is still recorded, but is not used as a hard rejection condition. In many connected unique boards an arbitrary truth reveal can collapse the whole graph even when that truth was not available to the player. Rejecting on that metric would throw away legitimate puzzles.

The fallback path was also tightened: level 70+ no longer silently falls back to Logic 3 merely because a hard seed is inconvenient to generate.

Reference run: `tools/benchmark_notes_v19_path_70_100.csv`.

The selected probes 70/75/80/85/90/95/100 all remain Logic 4 and pass the reachable-cascade gate. Generation time is still uneven on several seeds; this is now a performance target rather than a reason to weaken puzzle structure.

## 2. Exact level selection / replay

Home now has **Выбрать уровень**.

The selector is arranged in pages of 100:

- page 1: levels 1–100, always available;
- page 2: 101–200 opens after the sequential frontier reaches 101;
- page 3: 201–300 opens after it reaches 201;
- and so on.

The important implementation detail is that **selected level and progression frontier are now separate state**.

Examples with progression at level 81:

- selecting level 75 = `PATH_REPLAY`;
- selecting level 81 = normal `PATH`;
- selecting level 94 from the already-open 1–100 test page = `PATH_TEST`.

Solving a replay/test level does not move the sequential frontier. Only solving the current frontier advances progress.

This prevents a level-replay feature from accidentally rolling progress backward or allowing an out-of-order test to skip the sequential Path.

`LevelAccess` contains these rules as Android-independent logic and has a small harness test.

## 3. Replay/test traces are marked separately

Replay is valuable research data, but it is not the same stimulus as a first encounter. Familiarity can make an identical puzzle much cheaper on the second pass.

Therefore raw history keeps all three modes:

- `PATH`;
- `PATH_REPLAY`;
- `PATH_TEST`.

The default `DifficultyCalibrator` excludes replay/test sessions from first-pass calibration. They remain in the raw local history for later dedicated comparisons such as learning/retention and repeated-puzzle effects.

## 4. Deceptive decoys

v19 adds `DeceptiveDecoyBuilder` for hard Path levels.

The goal is explicitly **not** “more random numbers”. A new deceptive tile must satisfy all of the following:

1. its numeric value is not the true value of any hidden solution cell;
2. it is mathematically plausible for at least one hidden cell under the visible constraints;
3. a tentative placement survives immediate HumanSolver singleton propagation (no instant local contradiction);
4. after adding the tile, `SolutionCounter` still proves exactly one global solution.

Logic 4 Path asks for up to 2 extra deceptive decoys; Logic 5 can ask for 3. If a board does not contain enough honest false candidates, the builder adds fewer rather than padding the bank with noise.

Puzzle metadata records:

- `deceptiveDecoyCount`;
- `deceptiveDecoySupportMax` — maximum number of hidden cells for which one added false value was locally plausible.

The same fields are written into the local play trace. This makes later analysis possible: do these decoys create productive candidate work, wrong branches, more revisions, or merely extra scanning?

A small invariant harness checked selected hard Path levels: every added deceptive tile was false relative to the hidden truth set and every final puzzle remained exactly unique.

## 5. What to analyze next

The new selector + decoy metadata creates several clean experiments without changing the puzzle rules:

- first pass vs replay of the exact same seed/level;
- before/after familiarity: does active time drop while reasoning structure stays the same?;
- deceptive-decoy count/support vs wrong placements / candidate notes / Undo;
- predicted `maxForcedCascade` vs real rapid-action cascades;
- Path families that still produce long automatic clean-up despite passing Logic 4.

This is especially useful for separating two different phenomena:

- **bad generator collapse**: one discovery objectively forces almost everything;
- **learned compression**: the player has learned to recognize a structure and executes several logical steps almost automatically.

Those should not be “fixed” in the same way.

## Validation in this environment

- all app Java sources syntax-compiled with `javac` against minimal Android/JSON stubs;
- Android-independent core compiles normally;
- `LevelAccessHarness` passed page/unlock/session-mode invariants;
- `DeceptiveDecoyHarness` passed false-value + exact-uniqueness invariants on selected hard Path levels;
- Path probes 70/75/80/85/90/95/100 all returned Logic-4 accepted boards in the reference run.

A full Android APK is still not built here because this snapshot has no Android SDK/Gradle wrapper in the execution environment. Phone/UI smoke testing remains necessary.
