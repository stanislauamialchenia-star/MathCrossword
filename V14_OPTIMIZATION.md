# MathCrossword v14 — structural diagonals and Hypothesis frontier

v14 keeps the external game almost unchanged and works on generator discipline.

## 1. DiagonalPolicy

Diagonals are no longer a generic shape-variation option.

- generic/path geometry: orthogonal only;
- Chain Long/Branch: orthogonal only;
- Chain Converge: a diagonal is allowed only as a fallback bridge that connects at least two existing number nodes;
- Network Ring/Two-cluster: orthogonal only;
- Network Hub/Dense: diagonal fallback is allowed only for a real closure bridge between existing number nodes;
- Hypothesis: orthogonal only.

This implements the rule: keep a diagonal only when it buys constraint structure, not visual complexity.

## 2. Constructive Hypothesis — L4 first

A dedicated `ConstructiveHypothesisBuilder` now has two experimental families:

- `hypothesis-fork`
- `hypothesis-diamond`

The builder constructs branchable orthogonal topology first, then uses the shared arithmetic/hidden/solver pipeline. It is production-enabled for Logic 4, where the benchmark improved target-strategy matching. Logic 5 stays on the proven generic path for now: the constructive prototype does not yet reliably create depth-2/stuck hypothesis states without excessive retries.

This is deliberate: an experimental constructor is kept in the codebase without forcing a regression into the player path.

## 3. Hypothesis-specific filtering

Two generic assumptions were removed from Hypothesis:

- a hypothesis puzzle does not intrinsically require a graph cycle;
- hidden-mask prefiltering should judge branch/ambiguity prerequisites and leave actual contradiction depth to `HumanSolver` / `HypothesisEvaluator`.

Hidden-mask scoring now explicitly rewards ambiguity that survives basic propagation and penalizes one-singleton -> whole-board cascades.

## 4. Final uniqueness guard

v14 adds a final exact uniqueness check after the complete tile bank exists. This closes a subtle gap: hidden-mask uniqueness can be true before decoys are added, while the final bank can accidentally introduce an alternative full assignment.

New diagnostics:

- stage: `FINAL_UNIQUENESS`
- reject reason: `FINAL_UNIQUENESS_FAILED`

Accepted Free/Path puzzles therefore get one last exact check at the final playable state.

## 5. Benchmark snapshot

Small deterministic harness samples in this environment:

### Hypothesis L4, 5 seeds
- generated: 5/5
- exact unique: 5/5
- target strategy matched: 4/5
- constructive accepted: 60%
- average diagonal equations: 0

The comparable v13 L4 batch matched the target strategy only 1/5 on the stored reference batch. Timing is somewhat higher because the new constructive attempt does more deliberate work; this is a quality/reliability trade, not a pure speed optimization.

### Hypothesis L5, 5 seeds
- generated: 3/5
- exact unique: 3/3 generated
- target matched: 2/3 generated
- constructive L5 is intentionally not production-enabled yet.

This remains an active frontier.

### Network
Accepted samples became mostly orthogonal. In the L4 sample, the only diagonals came from structural Hub closure; L5 sample used none. This is the intended v14 policy.

### Chain
The tested L4/L5 accepted samples used no diagonals. Chain L5 remained strong in the small batch.

Bench files: `tools/benchmark_notes_v14_*_final.csv`.

## 6. Next frontier

1. constructive Hypothesis L5 based on deliberately designed contradiction depth rather than generic hidden-mask luck;
2. improve Network/Chain family acceptance without adding generic retries;
3. measure whether retained diagonals actually improve cycle/strategy metrics versus an orthogonal alternative;
4. keep the UI unchanged unless the player experience reveals a concrete need.
