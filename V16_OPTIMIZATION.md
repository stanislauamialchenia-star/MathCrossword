# MathCrossword v16 — candidate-domain contradiction kernel

v16 attacks the Hypothesis Logic 5 frontier without adding new player-facing UI.
The key change is to construct and measure a **false but locally viable branch** instead of hoping that a deep hypothesis appears accidentally after generic decoy generation.

## 1. What a contradiction kernel means

For a hidden pivot cell the final tile bank contains at least one false value that:

1. is locally legal in the current equations;
2. can actually be placed from the current bank;
3. survives normal forced-single propagation;
4. leaves a meaningful part of the puzzle unresolved;
5. cannot belong to any complete solution because the final puzzle remains exactly unique.

This is the intended Hypothesis pattern:

`plausible assumption -> no immediate failure -> explore consequences -> contradiction later`.

The generator no longer requires the contradiction to occur at exactly depth 2. Exact depth is a measured property, not a construction requirement. This matters because some of the most interesting false branches survive the bounded HumanSolver depth-2 probe but are still globally impossible by the exact solver.

## 2. Where the kernel is built

The first prototype ran kernel search inside every hidden-mask attempt and was too expensive.

v16 instead uses two levels:

- **cheap kernel signal during hidden-mask ranking** — only asks whether the already-unique candidate contains a false domain value that survives immediate propagation;
- **full reinforcement once, after the best hidden/tile candidate has been selected** — it reuses an existing branch when possible, otherwise it may add one equation-supported decoy and re-check exact uniqueness.

This keeps the expensive work out of the rejection loop.

## 3. Kernel-aware Hypothesis L5 evaluation

A kernel is now an explicit strategy signal. A board may qualify as Hypothesis L5 when it has:

- >=10 hidden cells;
- >=5 ambiguous equations;
- no initial singleton;
- at most one basic forced move;
- broad initial branching;
- enough unresolved cells after basic propagation;
- and an exact contradiction kernel.

This complements the existing HumanSolver criteria instead of replacing them.

## 4. Same-seed reference batch

Ten deterministic Hypothesis L5 seeds were compared.

### v15 production

- generated: **4/10**;
- unique: **4/4 generated**;
- target strategy matched: **2/10 requested**;
- fallbacks: **2**;
- average wall time: **1836.7 ms/request**;
- average generator attempts: **5.25 per generated puzzle**;
- average rejects: **14.0 per generated puzzle**;
- TILE_BANK: **934.7 ms/request**.

### v16

- generated: **9/10**;
- unique: **9/9 generated**;
- target strategy matched: **8/10 requested**;
- fallbacks: **1**;
- average wall time: **1518.3 ms/request**;
- average generator attempts: **2.67 per generated puzzle**;
- average rejects: **2.89 per generated puzzle**;
- contradiction kernel present in **8/9 generated puzzles**;
- kernel required an extra decoy in only **1/9 generated puzzles**;
- HumanSolver classified **1/8 kernels** as an explicit depth-2 contradiction; the others survive beyond that bounded probe;
- TILE_BANK: **513.9 ms/request**.

On this small fixed batch v16 is both more reliable and faster, despite doing additional semantic checking, because kernel-aware hidden ranking stops wasting attempts on boards that look hard but do not contain a useful hypothesis branch.

These ten seeds are a regression benchmark, not a general population claim.

Benchmark: `tools/benchmark_notes_v16_hypothesis_l5.csv`.

## 5. New diagnostics

`GenerationDiagnostics.Stage.CONTRADICTION_KERNEL` records the post-selection kernel cost.

The standalone harness adds:

- `kernel_pct`;
- `kernel_added_pct`;
- `kernel_depth2_pct`;
- `stage_contradiction_kernel_ms`.

The play trace now stores:

- `contradictionKernel`;
- `contradictionKernelAddedDecoy`;
- `contradictionKernelDepth` (`2` when the bounded HumanSolver explicitly detects depth-2 reasoning, otherwise `-1` = globally false but exact depth not measured during construction).

## 6. Important interpretation

A contradiction kernel is **not** proof that a human must use hypothesis testing. It is a property of the puzzle's candidate space: at least one tempting false branch survives immediate local reasoning and is globally impossible.

Observed human behavior remains a separate layer. The useful future comparison is:

`kernel exists -> did the player actually test a hypothesis? -> where did the branch get abandoned?`

## 7. Next frontier

The next useful work is now less about whether Hypothesis L5 can exist and more about its shape:

1. measure how many kernel branches each puzzle has;
2. estimate how long a false branch stays alive (contradiction distance);
3. distinguish productive hypothesis testing from blind trial-and-error in play traces;
4. build kernel families such as `single-pivot`, `two-stage`, and `reconverging`;
5. use the same constructor/evaluator separation for the remaining weak Logic-5 families, especially Network reliability.
