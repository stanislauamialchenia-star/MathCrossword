# Prior-art adaptation map

MathCrossword should borrow **mechanisms**, not inherit another project's assumptions. The current research target is not merely puzzle completion; it is to create controlled reasoning situations and measure how players traverse them.

## 1. Quality-Diversity / MAP-Elites -> Reasoning Space Archive

**Source idea:** MAP-Elites / pyribs stores the best solution in regions of a multidimensional behaviour/measure space instead of collapsing everything into one global score.

**MathCrossword adaptation:** treat each generated puzzle as a point in a reasoning space.

Candidate dimensions:

- dependency depth
- branch width
- meaningful entry regions
- route multiplicity
- opening forced-information fraction
- hypothesis depth
- independent reasoning fronts
- cascade shape
- calculation load (kept separate from reasoning topology)

The archive stores one or several high-quality puzzle representatives per region. `generatorScore` becomes a local quality value, not the definition of difficulty.

**Do not put pyribs in Android.** Start with an offline Python research tool fed by deterministic generator exports.

References:
- https://docs.pyribs.org/en/stable/api/ribs.archives.html
- https://docs.pyribs.org/en/latest/api/ribs.archives.GridArchive.html
- https://docs.pyribs.org/en/stable/supported-algorithms.html

## 2. Constraint solvers -> Mathematical Oracle

**Source ideas:** Google OR-Tools CP-SAT, Z3, MiniZinc.

**MathCrossword adaptation:** use an external solver as an independent oracle for research and CI, not as the human reasoning model.

Questions the oracle should answer:

- Is the puzzle satisfiable?
- Is the solution unique?
- How many solutions remain after a partial assignment?
- How much does a candidate action reduce the mathematical state space?
- Which constraints are responsible for infeasibility after a hypothesis?

This gives a clean separation:

- `OracleSolver`: mathematical truth / state-space queries
- `HumanSolver`: interpretable reasoning moves
- `PlayTrace`: what the human actually did

OR-Tools has Java support, but a Python research oracle is sufficient initially. MiniZinc is attractive for model experimentation because one model can be tested against several solving backends.

References:
- https://developers.google.com/optimization/cp
- https://developers.google.com/optimization/cp/cp_solver
- https://www.minizinc.org/
- https://microsoft.github.io/z3guide/

## 3. Process-mining conformance -> Reasoning Trace Alignment

**Source idea:** conformance checking compares an observed event trace with a model of valid behaviour. Alignment methods search for the closest valid model run instead of demanding an identical event order.

**MathCrossword adaptation:** compare a player's semantic move trace with the *space of valid traversals* over `ReasoningGraph`, not one canonical route.

Desired output:

- structurally matched moves
- order-only divergence
- internal entry
- reverse traversal
- branch probing
- model-only steps
- player-only steps
- alignment cost / structural coverage

This directly supports issue #3.

We should implement our own small graph-alignment layer rather than import PM4Py into the app. PM4Py/process-mining literature is prior art for the model and scoring semantics.

References:
- https://www.processmining.org/conformance.html
- https://pm4py-source.readthedocs.io/en/latest/pm4py.algo.conformance.alignments.petri_net.html

## 4. Successor representation -> Dependency-region overlap

**Source idea:** successor representations encode predictions about future states reachable from a current state. We do **not** claim the player literally uses this representation; it is useful as a computational analogy.

**MathCrossword adaptation:** for every probe / candidate cell, record the downstream set of cells or constraints that become resolved/restricted.

Example:

- B -> {B,C,D,E}
- C -> {C,D,E}
- D -> {D,E}

These are likely entries into one dependency region, not three independent weaknesses.

Use set overlap / containment / graph reachability to cluster `vulnerableSingleCells` into:

- `vulnerableRegions`
- `independentCollapseFronts`
- `regionOverlap`
- `largestRegionReach`

This directly supports issue #6.

Research anchor:
- Momennejad et al., *The successor representation in human reinforcement learning*, Nature Human Behaviour (2017), DOI 10.1038/s41562-017-0180-8.

## 5. Active information gathering -> Information Gain telemetry

**Source idea:** behavioural research studies how people acquire information before committing under uncertainty.

**MathCrossword adaptation:** estimate how much each semantic action reduces the puzzle's feasible state space.

Potential metrics:

- `solutionsBefore / solutionsAfter`
- `candidateMassBefore / candidateMassAfter`
- `informationGainBits = log2(before / after)` when state counting is tractable
- local vs global information gain
- information gained per committed placement
- probe cost before commitment

This lets us distinguish players/routes that:

- seek high-information pivots
- extend a local chain
- test hypotheses
- maintain several fronts
- commit early under uncertainty

Research anchor:
- Attaallah et al., *The role of the human hippocampus in decision-making under uncertainty*, Nature Human Behaviour (2024), DOI 10.1038/s41562-024-01855-2.

## 6. Strategy arbitration -> Behavioural state model (later)

**Source idea:** cognitive neuroscience models concurrent evaluation of an ongoing strategy and alternatives, including switching/exploration.

**MathCrossword adaptation:** only after graph-alignment is reliable, infer behavioural states such as:

- exploit current chain
- probe alternative
- switch front
- recover from contradiction
- return to previous anchor

Do not infer brain states or personality. These are game-level behavioural labels.

Research anchors:
- Donoso, Collins & Koechlin, *Foundations of human reasoning in the prefrontal cortex*, Science (2014), DOI 10.1126/science.1252254.
- Boorman et al., *How green is the grass on the other side?*, Neuron (2009), DOI 10.1016/j.neuron.2009.05.014.

## What to implement first

### PoC A — Reasoning Space Archive

Export 500-5000 deterministic generated candidates with structural metrics and place them into a small offline archive. Start with only 3 axes:

1. reasoning depth
2. branch width
3. entry-region count / cascade-region count

For each cell keep the best mathematically valid puzzle by a quality objective that penalizes generation cost and trivial opening collapse but does not reward a single notion of "hardness".

Questions this PoC answers:

- Does the current generator actually cover a broad reasoning space?
- Which regions are empty?
- Which constructors fill which regions?
- Where does generation cost explode?
- Are CHAIN / NETWORK / MIXED genuinely separated structurally?

### PoC B — Dependency-region clustering

For each hidden-cell probe, store the set of cells resolved after propagation, then cluster probes by overlap/containment. Replace raw vulnerable-cell count with region-level diagnostics only after deterministic PATH availability is proven.

### PoC C — Trace alignment

Once `ReasoningGraph` maps cleanly to concrete cells, align semantic player moves against valid graph traversals. Forward and reverse traversal of the same dependency chain should be structurally close even when order agreement is low.

## Explicit non-goals

- Do not put a heavyweight solver in the production path merely because it exists.
- Do not replace `HumanSolver` with SAT/CP search.
- Do not treat neuroscience findings as direct measurements of the player's brain.
- Do not optimize one scalar "difficulty" score again under another name.
- Do not copy GPL/AGPL code into the Android app without an explicit licensing decision.

## Working architecture

```text
Puzzle constructors
      |
      v
Structural analyzers ----> Reasoning Space Archive
      |                           |
      v                           v
Oracle Solver              coverage / empty regions
      |
      v
mathematical truth

Player -> semantic PlayTrace -> Reasoning Trace Alignment
                                |
                                v
                  observed solving strategy
```

The central research comparison becomes:

`intended reasoning situation -> structural realization -> human traversal`.
