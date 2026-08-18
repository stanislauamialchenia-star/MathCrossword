# MathCrossword

**MathCrossword** is an offline Android math-logic puzzle and a research playground for studying how generated reasoning structures are solved.

## Download for Android

**[Download MathCrossword.apk](https://github.com/stanislauamialchenia-star/MathCrossword/releases/latest/download/MathCrossword.apk)**

That link always points to the APK attached to the newest GitHub release. You do not need a GitHub account and you do not need to understand the repository.

If the direct download does not open, use the [latest release page](https://github.com/stanislauamialchenia-star/MathCrossword/releases/latest) and choose **MathCrossword.apk** under the release assets.

### First install on Android

Android may warn you because MathCrossword is installed directly instead of through Google Play. On the first install you may need to allow your browser or file manager to **install unknown apps / install from this source**.

1. Download `MathCrossword.apk`.
2. Open the downloaded file.
3. If Android asks, allow installation from the app that opened the APK (for example Chrome or Files).
4. Install MathCrossword.
5. You can disable that source permission again afterwards if you want.

GitHub builds are signed with a persistent project key, so future GitHub APK releases can update an existing GitHub installation without uninstalling the game.

## What the game is

MathCrossword generates arithmetic crossword-style boards where some values are hidden and must be reconstructed from intersecting equations and the available number tiles.

The project is designed to work:

- offline;
- without ads;
- without an account;
- without a required server;
- with deterministic seeds and reproducible generator behavior.

## Game modes

### Path

A long progression of generated levels. The first 100 levels gradually move from simple logical reconstruction toward puzzles with several reasoning fronts, candidate management and hypothesis testing.

After the early progression, the Path continues into a wider expert range instead of becoming an endless monotonic difficulty ladder.

### Free Play

Free Play lets the player choose two independent dimensions:

- **Logic: 1–10**
- **Calculation: 1–10**

This makes it possible to separate structural reasoning difficulty from arithmetic difficulty.

## Reasoning modes

Generated puzzles can target different reasoning structures:

- **Deduction** — local constraints progressively force values;
- **Chain** — discovering a useful entry point unlocks a dependency chain;
- **Hypothesis** — locally plausible alternatives must be tested and rejected;
- **Network** — several interconnected constraints interact at once;
- **Mixed** — combines multiple structures and reasoning fronts.

These names describe puzzle structure and solving affordances, not psychological player types.

## Difficulty model

The visible Logic and Calculation scales run from **1–10** while the generator also keeps continuous internal scores (`logicScore` / `calcScore`) from 1.0 to 10.0.

The current model includes:

- smooth progression between neighboring difficulty levels;
- continuous anti-collapse tuning rather than one hard switch;
- independent arithmetic growth across ten Calculation levels;
- a long expert-range Path curve;
- bounded retry budgets at the highest difficulty levels instead of hiding generator weakness behind unlimited search;
- play traces that preserve both public difficulty bands and continuous scores for later calibration.

See [`V22_SMOOTH_DIFFICULTY.md`](V22_SMOOTH_DIFFICULTY.md) for the original 1–10 difficulty-model milestone. Later Android versions continue to build on that generator line.

## Generator philosophy

MathCrossword does not rely only on random geometry plus a final difficulty score. The project increasingly separates:

1. **construction** of a desired reasoning structure;
2. **mathematical validation** and uniqueness checking;
3. **difficulty / reasoning evaluation**;
4. **human play telemetry and calibration**.

CHAIN, NETWORK and HYPOTHESIS already have dedicated constructive families in addition to generic fallback generation.

The generator intentionally uses bounded search. If a requested structure cannot be produced reliably inside its budget, that failure is treated as useful engineering and research information rather than hidden behind unbounded retries.

## Solving and analysis

The project contains both exact mathematical validation and human-oriented analysis tools.

Current research areas include:

- opening collapse vs. productive reasoning cascades;
- candidate-domain width and low-uncertainty entry points;
- branching quality and false-but-plausible candidates;
- multiple simultaneous reasoning fronts;
- concrete reasoning graphs and player traversal;
- replay / visit lifecycle semantics;
- difficulty calibration from real play traces;
- coverage of a multidimensional reasoning space rather than one scalar difficulty number.

Open GitHub issues are used as the active research backlog.

## Play traces and privacy

Play telemetry is intended for local research and generator calibration. Raw `play_history.jsonl` data is kept local/private by default and should not be committed to the repository.

The goal is to record observable interaction behavior — placements, candidate edits, focus changes, pauses, retries and structural traversal — without claiming to reconstruct a player's private thoughts.

## Languages

The external-player localization target is:

- English — fallback;
- Russian;
- Czech.

The implementation is tracked in [issue #16](https://github.com/stanislauamialchenia-star/MathCrossword/issues/16). Internal telemetry identifiers, generator family IDs and research field names remain language-independent.

## Repository map

Useful technical and research documents:

- [`CONSTRUCTIVE_GENERATORS.md`](CONSTRUCTIVE_GENERATORS.md) — constructive generator families;
- [`GENERATOR_BASELINES.md`](GENERATOR_BASELINES.md) — generator baselines and reproducibility;
- [`GITHUB_SETUP.md`](GITHUB_SETUP.md) — repository/versioning workflow;
- [`PLAYTRACE_FORMAT.md`](PLAYTRACE_FORMAT.md) — play-trace format;
- [`RESEARCH_FRAMEWORK.md`](RESEARCH_FRAMEWORK.md) — research framing;
- [`STATISTICS_SCHEMA.md`](STATISTICS_SCHEMA.md) — statistics schema;
- [`TECHNICAL_AUDIT.md`](TECHNICAL_AUDIT.md) — technical audit;
- [`V11_ARCHITECTURE.md`](V11_ARCHITECTURE.md) — reasoning graph and constructor architecture;
- [`V14_OPTIMIZATION.md`](V14_OPTIMIZATION.md) — structural diagonals and hypothesis frontier;
- [`V17_ANALYSIS.md`](V17_ANALYSIS.md) — play-trace analysis;
- [`V18_CALIBRATION_AND_CASCADE.md`](V18_CALIBRATION_AND_CASCADE.md) — calibration and cascade resilience;
- [`V19_LEVELS_AND_DECOYS.md`](V19_LEVELS_AND_DECOYS.md) — level replay and deceptive decoys;
- [`V20_JUBILEE.md`](V20_JUBILEE.md) — hard-Path performance baseline;
- [`V22_SMOOTH_DIFFICULTY.md`](V22_SMOOTH_DIFFICULTY.md) — 1–10 difficulty-model milestone.

## Project status

MathCrossword is an actively evolving experimental project. Generator behavior, telemetry and research models may change between versions, so important generator states are preserved as versioned baselines instead of silently overwritten.

The repository is public so the code, experiments and reasoning behind generator changes can be inspected and reproduced.
