# Solver-invariant experiment protocol

Status: pre-registered before the next larger play batch. Measurement pipeline completed before the clean cross-strategy sample.

Tracking issue: #13

## Question

Does the current generator create genuinely different solving strategies, or mostly different geometries / search costs that can still be solved by the same higher-level algorithm?

## H1 — solver invariant

Across CHAIN, NETWORK, DEDUCTION, HYPOTHESIS and MIXED, including high Logic / Calculation settings, successful play can be explained by the same loop:

1. inspect candidate domains and local constraints;
2. choose a low-uncertainty entry point (MRV-like / fail-first);
3. propagate consequences through the realized graph;
4. branch only when forced;
5. reject branches by contradiction or global resource constraints;
6. repeat from the new lowest-uncertainty frontier.

A puzzle becoming slower, deeper, wider, or harder to enter does **not** by itself falsify H1.

## Falsification target

A useful counterexample must require a qualitatively different reasoning mechanism, not merely more applications of the loop above.

Potential counterexample signature:

- the player solves the puzzle without a trace explainable as low-domain entry + propagation + branching/rejection;
- or the trace repeatedly stalls despite the MRV-like frontier and succeeds only after a different information source / relation is used;
- or a new strategy is required that cannot be represented as another ordering of the same constraint-search loop.

## Measurement boundary

Telemetry is treated as an interaction trace, not as a literal recording of thought.

We may infer only from recorded actions and puzzle structure, including:

- candidate add/remove events;
- placements / replacements / undo / reset;
- focus and semantic move trails;
- active time and action gaps;
- realized reasoning-graph traversal;
- puzzle strategy, Logic / Calculation, seed and generator provenance;
- branch / decoy / contradiction-kernel metrics;
- tile/resource-conflict evidence when available.

Generator acceptance rules must not be changed from the results until the first full batch is reviewed.

## Current telemetry bridge

The experiment instrumentation is based directly on the current PuzzleRun / Visit lifecycle so Home -> Continue does not turn one puzzle into false independent losses.

The concrete graph is captured when `HumanRouteComparator.modelRoute(puzzle)` starts the current visit and consumed when that visit is finalized. The resulting report is stored under:

`routeComparison.graphTraversal`

with `routeComparison.graphTraversalVersion` beside it.

This keeps the existing session/run-resume behavior stable while exporting realized-graph evidence. Analysis reads this nested field first; older research branches may expose the same payload as a top-level `graphTraversal` object.

## Export bundle

Starting with export schema v3 the research ZIP contains the raw source of truth plus a derived evidence layer:

- `sessions.jsonl` — untouched raw visit rows;
- `summary.json` — run-aware general summary plus a nested solver-invariant summary;
- `solver_invariant_summary.json` — cross-strategy coverage and aggregate evidence counters;
- `solver_invariant_evidence.jsonl` — one compact evidence record per visit.

`SolverInvariantEvidence` deliberately does **not** assign the final theory labels automatically. It only exposes candidate work, cascade/propagation signals, branch signals, realized graph traversal, generator opportunities and review flags. Final labels remain an offline/manual analysis step.

The in-app latest trajectory text also shows the realized graph direction, entry depth, branch-probing/internal-entry flags, confidence and the observed anchor-depth sequence.

## Derived metrics per session

### Entry / MRV-like behavior

- explored candidate-domain width per cell;
- minimum observed candidate-domain width at decision frontiers;
- number of entries / re-entries into low-domain cells;
- time and actions spent before a low-domain entry is found.

### Propagation

- candidate removals after a commitment;
- forced / rapid placements after a commitment;
- number of affected cells;
- propagation gain per commitment;
- distance travelled through the realized graph.

### Branching / rejection

- branch/probe episodes;
- maximum observed branch width;
- branch survival depth when observable;
- recovery / backtrack actions;
- rejection signature: arithmetic, crossing/graph, candidate exhaustion, tile/resource conflict, replacement/undo.

### Search cost

- active solve time;
- event count;
- candidate edits;
- candidate-cell switches and revisits;
- productive / dead-end pauses;
- recovery episodes;
- graph traversal direction and internal-entry depth.

### Strategy-change evidence

- whether one solving loop explains the full session;
- whether a distinct mechanism is needed in the middle of the solve;
- whether the requested generator strategy predicts the observed interaction style.

## Session labels

After offline analysis, label each session as one of:

- `INVARIANT_FITS` — the same loop explains the trace;
- `INVARIANT_FITS_HIGH_COST` — same loop, materially higher search/branch/depth cost;
- `AMBIGUOUS_TRACE` — insufficient evidence;
- `INVARIANT_BREAK_CANDIDATE` — qualitatively different mechanism appears necessary; manual review required.

A single break candidate is not enough to redesign the generator. Prefer repeated, interpretable counterexamples.

## Sampling

1. Continue repeated high-difficulty Free Play sessions, including L10/V10.
2. Include all five requested strategies, not MIXED alone.
3. Keep normal, difficult, abandoned, reset and solved sessions. Do not export only interesting examples.
4. Export the whole play-history batch after the checkpoint.
5. Analyze the batch before changing generator scoring or acceptance.

The first checkpoint should contain multiple sessions from each requested strategy. Exact sample size is secondary to coverage and keeping non-cherry-picked sessions.

For the clean sample, use the first released app version that contains export schema v3 / `SolverInvariantEvidence.VERSION = 1` or later. Older visits remain useful background data, but should be separated from the clean cross-strategy sample.

## Already observed motivating case

A MIXED L10/V10 puzzle was solved by first finding a region with fewer viable candidates. Candidate reduction then cascaded through the board. Large alternative computation trees made the entry search harder, but the successful algorithm did not qualitatively change.

This observation supports testing H1; it is not counted as proof.

## Related work

- #3 / PR #11 / PR #12 — earlier realized reasoning-graph telemetry work
- #6 / PR #10 — dependency-region vulnerability
- #7 / PR #9 — reasoning-space coverage archive
- #13 — this experiment
- PR #23 — realized-graph telemetry ported onto the current PuzzleRun / Visit lifecycle
