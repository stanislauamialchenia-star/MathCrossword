# MathCrossword v11 — deeper core, quiet UI

## What changed

### Full hint card
`? Намёк` no longer uses a short Toast for the actual hint text. Hints open in a scrollable multi-line dialog with `Закрыть` and, for stages 1–2, `Глубже →`. The field stays unchanged; the hint still never inserts an answer automatically.

### Diagonal equations
The engine now supports four equation orientations:
- horizontal;
- vertical;
- diagonal down-right / up-left;
- diagonal up-right / down-left.

Diagonal constraints are first used mainly by constructive CHAIN/NETWORK families, where they can connect distant-looking areas or close a network cycle. Symbols remain upright; only their cells follow a diagonal path.

### Abstract reasoning graph
`ReasoningGraph` is an Android-independent intermediate representation. It describes the desired topology before arithmetic is assigned.

Current families:
- `chain-long`
- `chain-branch`
- `chain-converge`
- `network-ring`
- `network-hub`
- `network-two-cluster`
- `network-dense`

The graph has no numbers or operations. Constructive builders first choose a family, then map it to crossword geometry, then assign arithmetic, hidden cells and decoys, and finally run exact/human-like validation.

### Strategy-specific evaluators
`StrategyEvaluator` separates the definition of “good CHAIN” from “good NETWORK”. Exact mathematical correctness remains shared in `SolutionCounter` and arithmetic rules.

### Stage diagnostics
`GenerationDiagnostics` now accumulates time by stage:
- `GRAPH`
- `ARITHMETIC`
- `HIDDEN_UNIQUENESS`
- `HUMAN_ANALYSIS`
- `STRATEGY_EVALUATION`

The accepted puzzle stores `generatorFamily` and `generationStageTimings`; both are also written into the local play trace.

### Generator harness
The standalone harness now reports:
- average diagonal-equation count;
- family distribution;
- average time by generation stage;
- previous rejection/quality statistics.

This makes performance work causal: if NETWORK is slow because hidden-cell uniqueness consumes 90% of the time, we optimize that stage instead of blindly raising retry counts.

## Design boundary
The UI remains intentionally small. Most new complexity lives behind the field in the generator, solver, diagnostics and strategy model.
