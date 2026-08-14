# Statistics / research signals

MathCrossword stores local-only play traces. The goal is not to diagnose a person, but to compare **task structure**, **solver prediction** and **observed solving behavior**.

## Already recorded or derived in v10

### Puzzle / generator
- seed;
- generator version;
- requested solution strategy;
- actual generation strategy;
- constructor name (`chain-snake-v1`, `network-lattice-v1`, generic, fallback);
- requested Logic and Calc bands;
- HumanSolver predicted steps/depth;
- rated Logic;
- hidden cells / equations;
- generation time;
- number of candidate-generation attempts;
- number and summary of generator rejections;
- generation stage and whether the target strategy was actually matched.

### Player process
- active solving time (background time excluded);
- time to first action;
- time to first placement;
- longest pause between actions;
- number of pauses over 3 s and 10 s;
- rapid transitions under 1 s;
- placements and replacements;
- number of distinct cells actually filled;
- candidate-note edits and number of cells where candidates were used;
- Undo / Reset;
- incorrect full-board completions;
- hints and maximum hint depth;
- full ordered event trace with timestamps.

## High-value metrics to add next

- **spatial return distance**: how often attention returns to a previously visited region;
- **local-to-global switching**: movement between distant components of the constraint graph;
- **candidate entropy over time**: whether uncertainty is shrinking smoothly or oscillating;
- **productive pause**: a long pause followed by several correct/consistent moves;
- **dead-end pause**: a long pause followed by undo/reset/hint;
- **strategy transition markers**: e.g. deduction -> candidate notation -> hypothesis test;
- **error recovery length**: how many actions pass between a wrong branch and its correction;
- **hint transfer**: whether the same hint type becomes unnecessary in later puzzles;
- **learning curve by strategy**: median active time/undo/hints across rolling windows;
- **calibration error**: predicted difficulty minus observed difficulty proxy;
- **generator rejection profile** by constructor and strategy.

## Research caution

These variables describe behavior inside MathCrossword. They should not be treated as personality tests or clinical/cognitive diagnoses. General claims about people require multiple participants, controlled procedures, consent and a proper study design.

## v11 additions

### Generator provenance
- `generatorFamily`: constructive family such as `chain-long`, `chain-branch`, `chain-converge`, `network-ring`, `network-hub`, `network-two-cluster`, `network-dense`.
- `generationStageTimings`: semicolon-separated stage timing summary (`GRAPH`, `ARITHMETIC`, `HIDDEN_UNIQUENESS`, `HUMAN_ANALYSIS`, `STRATEGY_EVALUATION`).

### Harness-only structural metrics
- `avg_diagonal`: average number of diagonal equations in accepted puzzles.
- `families`: observed family counts in a benchmark batch.
- stage timing columns: average milliseconds spent in each generation stage.

These fields make it possible to compare not only strategy/difficulty, but also the concrete generative grammar and the computational cost of each stage.


## v13 generator diagnostics

Harness timing now separates:

- `HIDDEN_PREFILTER` — topology-only rejection before the bank exists;
- `TILE_POOL` — derivation/local validation of plausible external values;
- `TILE_SELECT` — greedy decoy selection.

A new rejection reason `HIDDEN_TOPOLOGY_REJECTED` records masks rejected before expensive tile-bank/HumanSolver work. This is a generator diagnostic, not a player-behavior metric.


## v14 generator diagnostics

- `FINAL_UNIQUENESS` stage: exact check of the fully playable puzzle after decoys are present.
- `FINAL_UNIQUENESS_FAILED`: candidate rejected because the final bank admits more than one complete solution.
- `avg_diagonal` should now be interpreted as **structural diagonal usage**, not general layout variety: generic/path generation is orthogonal and only selected constructive bridge families may retain diagonals.
- Hypothesis families now include `hypothesis-fork` and `hypothesis-diamond` for constructive L4 experiments.

## v16 contradiction-kernel signals

For Hypothesis L5 the generator can now record a candidate-domain contradiction kernel:

- `contradictionKernel` — at least one false value is locally plausible, survives forced propagation, but cannot occur in any complete solution;
- `contradictionKernelAddedDecoy` — the kernel required one additional generated decoy rather than already existing in the normal tile bank;
- `contradictionKernelDepth` — `2` if the bounded HumanSolver explicitly detects a depth-2 refutation; `-1` means the branch is globally false but its exact contradiction depth was not measured during construction.

Harness-only metrics:

- `kernel_pct`;
- `kernel_added_pct`;
- `kernel_depth2_pct`;
- `stage_contradiction_kernel_ms`.

These should later be compared with real play-trace behavior: candidate-note usage, tentative placements, undo/replacement patterns, and pauses after entering a false branch.


## v17 implemented analysis signals

Several items that were previously only proposed are now derived locally:

- `productivePauses`;
- `deadEndPauses`;
- `hypothesisEpisodes`;
- candidate-to-placement commitment time;
- recovery length after revision/error signals;
- rapid action cascades.

Hypothesis L5 generator metadata now also describes the false-branch candidate space:

- branch count;
- pivot count;
- bounded depth-2 vs deeper-surviving branches;
- kernel family (`single-pivot`, `two-stage`, `deep-branch`, `multi-pivot`, `unprofiled`).

The in-game analysis surface intentionally exposes only compact summaries. Full traces and provenance remain available locally for later calibration and offline research.


## v18 cascade + calibration signals

### Puzzle / generator
- `maxForcedCascade`: largest forced HumanSolver cascade after a legitimate reasoning step; this is the main anti-automatic-collapse gate for non-Chain hard strategies.
- `maxResolvedAfterOneCell`, `maxAdditionalForcedAfterOneCell`, `maxResolvedFractionAfterOneCell`, `vulnerableSingleCells`: descriptive truth-injection profile. These are **not** treated as authoritative difficulty labels because revealing an arbitrary correct value gives more information than the player may have earned.
- equation-reveal counterparts describe the same structural fragility at equation granularity.
- harness columns `avg_onecell_collapse`, `avg_vulnerable_cells`, `stage_cascade_resilience_ms` expose the cost/profile of this analysis.

### Observed difficulty calibration
`DifficultyCalibrator` consumes solved local sessions and records/derives:
- predicted band (`ratedLogic`, falling back to requested Logic);
- transparent observed raw cost from active time per hidden cell, event density, revisions, candidates, dead-end pauses and hints;
- observed personal cost band 1–10, ranked against the player's own recent solved history;
- calibration mean error / mean absolute error;
- exact and within-±1 percentages;
- underestimation / overestimation counts;
- last predicted vs observed band;
- recent observed-cost trend.

Calibration requires at least 8 solved sessions. If the latest generator version has at least 8 solved sessions, only that version is used; otherwise the UI explicitly reports a historical/mixed scope. This avoids silently comparing changed generator stimuli as if they were identical.

## v19 additions

### Hard-Path decoy provenance
- `deceptiveDecoyCount`: number of extra false values added by `DeceptiveDecoyBuilder` after the normal bank was built.
- `deceptiveDecoySupportMax`: maximum number of hidden cells for which one such false value was locally plausible.

A deceptive decoy is required to be false relative to all hidden truth values, locally viable under visible arithmetic, and to preserve exact global uniqueness.

### Replay/test mode
Path history can now use `PATH`, `PATH_REPLAY`, or `PATH_TEST`. Raw traces keep all modes, but default first-pass difficulty calibration excludes replay/test sessions so familiarity does not silently bias the model.

## v20 additions

### PATH generation telemetry
`generatePath()` now owns a `GenerationDiagnostics` request just like Free Play. Therefore Path sessions can persist:

- `generationMillis`;
- `generationAttempts`;
- `generationRejects`;
- `generationRejectSummary`;
- `generationStageTimings`.

This makes it possible to correlate a slow level with its actual generator bottleneck instead of treating latency as one opaque number. The v20 path profiler additionally distinguishes deceptive-decoy cost and post-generation Path analysis.

### Safe deceptive decoys
A deceptive candidate is now enrichment only. The Path generator keeps it only if the resulting board still passes the same Logic and anti-cascade gates. If a decoy damages the reasoning shape, that tile is rolled back rather than discarding the already-good base puzzle.


## v21 additions — quality of uncertainty

v21 broadens the decoy model from the historical v19 definition. A false **placement** may now reuse an extra copy of a number that is correct somewhere else in the puzzle. This is recorded as a resource-conflict decoy rather than silently treated as a novel false value.

### Contextual decoys
- `contextualDecoyCount`: distinct surplus tile values that remain locally plausible at a compact unresolved target constrained by multiple equations while the final exact puzzle stays uniquely solvable.
- `resourceConflictDecoyCount`: contextual values that are extra copies of truth values needed elsewhere in the puzzle. They can look locally valid but create a global allocation conflict when placed in the wrong cell.
- `contextualDecoyConstraintSupportMax`: largest number of local equation constraints supporting one contextual false placement.
- `contextualDecoyDepthMax`: bounded contradiction-depth class reached by the strongest contextual decoy.
- `contextualDecoyInformationGainMax`: largest bounded forced-information gain after testing one contextual false placement.

`deceptiveDecoyCount` remains provenance for values explicitly inserted/refined by the decoy builder. `contextualDecoyCount` describes the **final tile bank**, including useful ambiguity that already existed before v21 enrichment.

### Branch quality
- `branchPivotCount`: compact unresolved candidate pivots after opening singleton propagation.
- `branchGoodPivotCount`: pivots with a compact domain, multiple constraints and at least one locally viable false branch.
- `branchSeriousFalseBranches`: bounded false branches that survive immediate local propagation.
- `branchDepth2RefutableBranches`: false branches whose contradiction becomes visible within the bounded depth-2 probe.
- `branchDepth2SurvivingBranches`: false branches still locally viable after that probe.
- `branchMaxWidth`: widest compact branch domain observed by the profiler.
- `branchMaxInformationGain`: largest bounded number of additional forced cells obtained after testing one assumption.

These are puzzle-structure signals. They do not claim that the player consciously used a hypothesis strategy.

### Multi-front resilience
- `reasoningFronts`: number of meaningful unresolved reasoning fronts/components after opening propagation.
- `reasoningFrontBalance`: relative size of the second useful front compared with the largest.
- `reasoningLargestFrontFraction`: share of unresolved work concentrated in the largest front.
- `reasoningFrontBottleneckDegree`: degree of the strongest articulation/bottleneck-like connector found by the bounded graph probe.

For `mixed-two-front`, v21 now checks that the accepted puzzle actually retains useful independent fronts instead of trusting the constructor-family name alone.

### Reproducibility note
`BranchQualityAnalyzer` is deliberately bounded by a fixed number of candidate probes rather than wall-clock time. Generator seed reproducibility must not depend on transient machine load.


## v22 smooth-difficulty provenance

The public difficulty model is now 1–10 while the mature generator still uses five internal capability tiers as implementation anchors. Do not confuse these fields:

- `logic` / `calc`: public 1–10 bands shown to the player;
- `logicScore` / `calcScore`: continuous 1.0–10.0 Path/Free difficulty coordinates;
- internal `logicLevel` / `calcLevel`: five engine capability tiers used by existing constructors/evaluators and not intended as the player-facing scale;
- `ratedLogic` in v22 play traces stores the public 1–10 heuristic rating for backward-compatible field naming.

`DifficultyCalibrator` now derives observed cost in ten personal-history percentile bands. It remains a transparent local calibration signal, not a population norm or psychological measurement.

The continuous scores are particularly important for generator experiments: adjacent Path levels can differ by fractions of a point even when they display the same rounded public level. This makes it possible to evaluate whether cascade resilience, branch quality and arithmetic load change smoothly inside a visible band.
