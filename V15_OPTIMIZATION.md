# MathCrossword v15 — targeted diagonals and Hypothesis L5 frontier

v15 keeps the external game unchanged and works on two measured questions: which diagonals actually buy generator quality, and how to make Hypothesis L5 cheaper without hiding its remaining structural weakness.

## 1. Diagonals are now evidence-targeted

`DiagonalPolicy` now has three harness modes:

- `targeted` (default/player path): Chain Converge can use a structural bridge; Network does not use diagonals;
- `structural`: old v14 structural Network/Chain bridges are enabled for comparison;
- `orthogonal`: all optional diagonals are disabled.

Small same-seed A/B batches showed:

### Network L4 (3 seeds)
- structural: 3/3 generated, 2/3 target matched, ~593 ms/sample, avg diagonal 0.67;
- orthogonal: 3/3 generated, 3/3 target matched, ~450 ms/sample, avg diagonal 0.

### Network L5 (3 seeds)
Both modes generated 1/3 and matched 1/3; structural gave no visible reliability gain.

### Chain L5 (3 seeds)
- structural: 3/3 generated, 3/3 target matched, ~350 ms/sample, avg diagonal 0.33;
- orthogonal: 2/3 generated, 2/3 target matched, ~691 ms/sample, avg diagonal 0.

The sample is intentionally small, so this is not a universal claim. It is enough to justify the current player policy: retain the Chain Converge bridge, remove Network diagonals until a future family demonstrates a measurable benefit.

Benchmark files:
- `tools/benchmark_notes_v15_network_structural.csv`
- `tools/benchmark_notes_v15_network_orthogonal.csv`
- `tools/benchmark_notes_v15_chain_structural.csv`
- `tools/benchmark_notes_v15_chain_orthogonal.csv`
- `tools/benchmark_notes_v15_network_targeted.csv`
- `tools/benchmark_notes_v15_chain_targeted.csv`

## 2. Hypothesis L5: safe speed improvement

The production L5 generator remains the generic proven path. The expensive candidate pool in `TileBankBuilder` is capped at 56 candidates for Hypothesis L5 instead of 72. This reduces local-fit work while retaining enough decoy variety.

Same 5-seed reference batch:

### v14 baseline
- generated 3/5;
- unique 3/3 generated;
- target matched 2/3 generated;
- fallback 1;
- ~2322 ms/sample;
- TILE_BANK ~1114 ms/sample.

### v15 production
- generated 3/5;
- unique 3/3 generated;
- target matched 2/3 generated;
- fallback 1;
- ~1918 ms/sample;
- TILE_BANK ~940 ms/sample.

This is a modest but safe improvement: roughly 17% lower wall-clock time on this batch with the same generated/unique/target-match counts.

Benchmark: `tools/benchmark_notes_v15_hypothesis_l5_production.csv`.

## 3. Experimental contradiction constructor

`ConstructiveHypothesisBuilder` now contains an opt-in L5 family `hypothesis-contradiction`. Its second prototype builds an exact arithmetic lattice first, then tries to make the hidden/tile pipeline create a delayed contradiction. This avoids the first prototype's equation-closure failures.

It is available only when the Java system property is set:

```text
-Dmathcrossword.experimentalHypothesisL5=true
```

The harness exposes this as a sixth argument: `experimental`.

Current result: the experimental constructor is still not accepted often enough and does not outperform the generic production path. Therefore v15 **does not enable it for players**. This is useful negative evidence: topology alone is not enough; the constructor must control the candidate/contradiction structure more directly.

Benchmark: `tools/benchmark_notes_v15_hypothesis_l5_experimental2.csv`.

## 4. Next frontier

The next useful work is not more retries. It is to construct a contradiction kernel at the candidate-domain level:

1. choose a pivot with at least two locally viable values;
2. explicitly build one false value that survives immediate propagation;
3. ensure it creates a contradiction only after one additional dependent choice;
4. map that domain structure back into arithmetic equations;
5. keep the exact solver as the final invariant guard.

This should reduce rejection sampling more than simply increasing the hidden-mask or candidate-bank budgets.
