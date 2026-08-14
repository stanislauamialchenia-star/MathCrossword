# v21 — Quality of uncertainty

## Why this version exists

The practical trigger was a replay of level 70. The old failure mode — solve one equation and watch the whole board collapse automatically — had largely disappeared. The new friction was different: one cell could require testing plausible candidates and following a branch before rejecting it.

That is useful only if it is **structured hypothesis testing**, not blind enumeration. v21 therefore shifts the optimization target from “more ambiguity” to **better ambiguity**.

## 1. BranchQualityAnalyzer

The bounded profiler runs after opening singleton propagation and measures:

- compact pivot cells;
- locally viable false branches;
- immediate vs delayed rejection;
- branch width;
- bounded information gain after an assumption;
- overly wide domains that look more like brute-force search.

The first implementation used a short wall-clock budget. That was rejected because machine load could change which branches were inspected for the same seed. The production profiler uses fixed candidate-probe limits instead, so repeated harness runs remain deterministic.

Branch quality is currently a scoring/diagnostic signal rather than a blanket Path hard gate. A strict gate was tested and caused large rejection/performance regressions while also overfitting every hard Path puzzle toward Hypothesis. Different Path levels are still allowed to be deduction-, chain- or network-like.

## 2. Contextual decoys

A random extra number is not interesting. A useful decoy should be plausible in context.

`ContextualDecoyAnalyzer` inspects the **final bank** and identifies surplus values that:

1. are false at a particular unresolved target;
2. satisfy the target's visible local arithmetic constraints;
3. target a compact candidate domain;
4. survive immediate local propagation;
5. still live inside a puzzle whose final exact solution is unique.

This distinction matters because useful ambiguity may already exist in the normal tile bank. v21 no longer equates “not inserted by DeceptiveDecoyBuilder” with “not useful”.

## 3. Resource-conflict decoys

The strongest discovery in this iteration was that the best false candidate is often **not a novel number**.

A duplicate of a value that is genuinely correct somewhere else can be locally plausible in the wrong cell. It may satisfy two local equations, yet consume a tile resource needed by another region. The contradiction is therefore global rather than immediately arithmetic.

This is recorded as a `resourceConflictDecoy`.

Example shape (conceptual):

```text
region A: value 40 is locally plausible here
region B: value 40 is actually required there

placing the extra 40 in A can survive local checks,
but the full tile allocation can no longer complete consistently.
```

Exact final uniqueness remains mandatory, so the decoy cannot create a second valid completed board.

## 4. Safe enrichment instead of tile inflation

v21 does not blindly add two or three more tiles.

The sequence is:

1. analyze contextual ambiguity already present in the winning bank;
2. if weak, locate generic surplus tiles;
3. replace a surplus tile with a stronger proven contextual/resource-conflict decoy when possible;
4. rerun Path acceptance and branch checks;
5. roll back if the change makes the board worse;
6. only if still useful, allow at most one additional tile.

This keeps visual scanning load under control and makes every added candidate justify its presence.

An experiment that pushed contextual construction into the initial `TileBankBuilder` was reverted: it poisoned the hidden-mask rejection loop and made several Path seeds much slower. Post-selection refinement is the safer architecture.

## 5. MultiFrontResilienceAnalyzer

The anti-collapse problem is not solved merely by making one pivot harder. A board can still become a single bottleneck: solve one special cell and everything follows.

The multi-front profiler measures unresolved components/fronts, their balance and bottlenecks after opening propagation. `mixed-two-front` is now accepted only when the resulting puzzle actually retains useful multiple fronts.

This creates the possibility of a deliberate move:

> “I cannot settle this branch yet; work another region, gain information, then return.”

## 6. Hint and analysis integration

MIXED hints can now react to task structure without revealing an answer:

- resource conflict: a locally fitting number may be needed elsewhere;
- multiple fronts: leave a stuck region and obtain information from another one;
- contextual decoy: test consequences rather than judging a candidate from one equation alone.

The analysis screen can summarize the last session with:

- contextual false options / resource conflicts;
- useful hypothesis pivots / viable false branches;
- number of reasoning fronts.

These are properties of the puzzle plus observable interaction. They are not claims about the player's private mental state.

## 7. Small deterministic harness sample

For levels 70–84 (15 fixed deterministic levels on the development machine):

- contextual decoys appeared in 10/15;
- at least one useful hypothesis pivot appeared in 10/15;
- at least two useful reasoning fronts appeared in 8/15;
- average `max_forced_fraction` was about 0.40;
- average wall time was about 1.0 s in this diagnostic run.

Level 70 in the saved batch is a useful example: `mixed-two-front`, 2 contextual/resource-conflict decoys, 3 good pivots, 4 serious false branches and 2 reasoning fronts.

These numbers are regression diagnostics, not universal gameplay claims. v20 remains the cleaner speed baseline; v21 deliberately pays a modest bounded analysis cost for richer structural information.

## 8. Next questions

The next useful work is no longer “add more candidates”. It is to compare predicted branch structure with actual play traces:

- Did the player test a pivot the engine considered useful?
- Was a resource-conflict decoy actually tempting or simply ignored?
- How many actions separated a false assumption from recovery?
- Did the player switch to a second front before returning?
- Which branch profiles feel like reasoning and which feel like tedious enumeration?

That feedback can eventually tune constructor/evaluator policy per family without turning the exact solver into a behavioral model.
