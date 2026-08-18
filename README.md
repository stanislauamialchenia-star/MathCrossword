# MathCrossword

**MathCrossword** is an offline Android math-logic puzzle and a research playground for studying how generated reasoning structures are solved.

Current development line: **v22**.

[Download the latest release](https://github.com/stanislauamialchenia-star/MathCrossword/releases/latest)

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

## v22 — smooth difficulty scale

v22 expands the visible Logic and Calculation scales from 1–5 to **1–10** while keeping continuous internal scores (`logicScore` / `calcScore`) from 1.0 to 10.0.

Key changes:

- smoother progression between neighboring difficulty levels;
- continuous anti-collapse tuning instead of a hard switch at one Path level;
- independent arithmetic growth across ten Calculation levels;
- a longer expert-range Path curve for hundreds or thousands of generated levels;
- bounded retry budgets at the highest difficulty levels instead of hiding generator weakness behind unlimited search;
- play traces preserve both public difficulty bands and continuous scores for later calibration.

See [`V22_SMOOTH_DIFFICULTY.md`](V22_SMOOTH_DIFFICULTY.md) for the detailed model and regression points.

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

## Install on Android

For testers, the easiest path is the Releases page:

1. Open the [latest release](https://github.com/stanislauamialchenia-star/MathCrossword/releases/latest).
2. Download the `.apk` file.
3. Open it on the Android device.
4. If Android asks, allow installation from that source.
5. Install the app.

Future APK updates can be distributed through the same Releases page.

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
- [`V22_SMOOTH_DIFFICULTY.md`](V22_SMOOTH_DIFFICULTY.md) — current 1–10 difficulty model.

## Project status

MathCrossword is an actively evolving experimental project. Generator behavior, telemetry and research models may change between versions, so important generator states are preserved as versioned baselines instead of silently overwritten.

The repository is public so the code, experiments and reasoning behind generator changes can be inspected and reproduced.
