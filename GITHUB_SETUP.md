# GitHub baseline

MathCrossword is intentionally versioned as an experiment as well as an Android app.
Do not overwrite old generator baselines: generator changes alter the stimulus shown to the player.

## First push

From the project root:

```bash
git init
git add .
git commit -m "MathCrossword v10 constructive generators baseline"
git branch -M main
git remote add origin <YOUR_REPOSITORY_URL>
git push -u origin main
```

Then freeze the first reproducible baseline:

```bash
git tag v10-baseline
git push origin v10-baseline
```

For future generator experiments, prefer branches such as:

- `experiment/chain-v2`
- `experiment/network-v2`
- `experiment/difficulty-calibration`

When an experiment becomes clearly better, merge it into `main` and create another tag.

## What should be committed

Commit source code, generator harnesses, research notes and documentation.
Do not commit Android Studio caches, Gradle build output or `local.properties`; `.gitignore` already excludes them.

## v11 checkpoint

After testing v11 on the phone, a useful checkpoint is:

```bash
git add .
git commit -m "v11: diagonal constraints and reasoning graph"
git tag v11-baseline
git push
git push origin v11-baseline
```

For risky generator experiments, prefer branches such as `experiment/network-hidden-selector` instead of overwriting the baseline.


## v12 checkpoint

After phone testing and harness comparison:

```bash
git add .
git commit -m "v12: optimize hidden and uniqueness generation"
git tag v12-generator-optimization
git push
git push origin v12-generator-optimization
```

Keep `v11-baseline` as the pre-optimization control for benchmark comparisons.


## v13 checkpoint

After phone testing:

```bash
git add .
git commit -m "v13: optimized tile-bank builder and strategy evaluators"
git tag v13-baseline
git push
git push origin v13-baseline
```

Keep risky next steps in branches such as `experiment/hypothesis-constructor` and `experiment/tile-pool-cache`.


## v14 checkpoint

After phone testing and the v14 harness pass:

```bash
git add .
git commit -m "v14: structural diagonal policy and hypothesis frontier"
git tag v14-baseline
git push
git push origin v14-baseline
```

Keep the unfinished Logic-5 hypothesis work in a branch such as `experiment/hypothesis-l5-contradiction-core` until it beats the v14 baseline on both quality and generation cost.


## v15 checkpoint

After phone smoke-testing v15:

```bash
git add .
git commit -m "v15: targeted diagonals and hypothesis frontier"
git tag v15-baseline
git push
git push origin v15-baseline
```

Keep risky L5 experiments on a branch such as `experiment/hypothesis-contradiction-v2`.

## v16 checkpoint

After phone testing, freeze the candidate-domain contradiction-kernel baseline:

```bash
git add .
git commit -m "v16: candidate-domain contradiction kernel"
git tag v16-baseline
git push
git push origin v16-baseline
```

Keep deeper contradiction-distance experiments in a branch such as `experiment/hypothesis-kernel-depth` so the v16 behavior remains reproducible.


## v18 checkpoint

After phone-testing the repaired Path band and analysis screen:

```bash
git add .
git commit -m "v18: anti-cascade path and difficulty calibration"
git tag v18-baseline
git push
git push origin v18-baseline
```

Keep future calibration/ranker changes in branches such as `experiment/difficulty-calibration-v2`. Do not mix raw `play_history.jsonl` into the repository; it remains local/private by default.

## v19 checkpoint

After phone-testing the level selector and hard Path band:

```bash
git add .
git commit -m "v19: level replay and deceptive decoys"
git tag v19-baseline
git push
git push origin v19-baseline
```

Replay/test traces remain local and should not be committed.

## v20 checkpoint

After phone-testing the optimized hard Path band:

```bash
git add .
git commit -m "v20: hard path performance baseline"
git tag v20-baseline
git push
git push origin v20-baseline
```

Keep future Path search experiments on branches such as `experiment/path-hidden-selector-v2`. The v20 tag is useful because it freezes both the v19 replay/decoy behavior and the first dedicated Path-performance architecture.


## v21 checkpoint

After phone-testing the quality-of-uncertainty changes:

```bash
git add .
git commit -m "v21: quality of uncertainty and contextual decoys"
git tag v21-baseline
git push
git push origin v21-baseline
```

Use `v20-baseline` as the hard-Path performance reference. v21 intentionally spends a modest bounded amount of extra analysis on branch quality, contextual decoys and multi-front structure. Keep stronger ambiguity experiments on branches such as `experiment/contextual-decoy-v2` or `experiment/branch-quality-gate` rather than overwriting either baseline.


## v22 checkpoint

After phone-testing the 1–10 selectors and the smooth Path curve:

```bash
git add .
git commit -m "v22: smooth 1-10 difficulty scale"
git tag v22-baseline
git push
git push origin v22-baseline
```

Keep experiments that change the continuous curve on branches such as `experiment/path-difficulty-curve-v2`. Preserve `v21-baseline` for quality-of-uncertainty comparisons; v22 changes the stimulus progression itself, so first-pass play traces from v21 and v22 should be grouped by generator version.
