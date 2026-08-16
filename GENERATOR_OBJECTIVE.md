# Generator objective — reasoning first

## What the generator produces

The generator does not primarily produce a board. It produces a **reasoning situation** carried by a board.

A good puzzle is therefore not defined by one scalar difficulty score or one canonical solution route. It should create a controlled space of constraints in which one or more meaningful reasoning traversals are available to a player.

## Objective hierarchy

The generator should optimize in this order:

1. **Mathematical validity** — the board is valid, solvable and has exactly one final solution.
2. **Reasoning topology** — the intended structural family survives realization (chain, network, hypothesis, deduction, mixed).
3. **Entry quality** — hard puzzles should not expose too much free information at the opening.
4. **Reasoning affordances** — the structure may support forward, backward, bidirectional or internal-entry exploration. A long cascade is allowed when it is earned by non-trivial reasoning.
5. **Human calibration** — observed player cost is measured separately from structural difficulty and used to improve future scoring.
6. **Generation efficiency** — attempts, rejects and generation time are first-class quality signals. A criterion that is expensive for the generator but weakly related to human difficulty should be questioned.

## Three different difficulties

Do not collapse these into one `logicScore`:

- **Structural difficulty**: properties of the puzzle/reasoning graph.
- **Human difficulty**: observed effort for a particular player/run.
- **Generation difficulty**: computational cost of constructing an acceptable puzzle.

They may correlate, but they are not interchangeable.

## Cascade policy

A cascade is not inherently good or bad.

- **Opening collapse**: much of the board becomes forced before meaningful reasoning. Reject for hard PATH.
- **Systemic fragility**: many arbitrary correct single-cell revelations collapse most of the board. Reject for hard PATH.
- **Productive dependency cascade**: the opening remains uncertain, the player must discover/validate an anchor or hypothesis, and only then a long dependency chain becomes forced. Preserve; this can be the intended mechanism.

## Route model

`HumanRouteComparator` should evolve from comparison against one canonical route toward comparison against a **space of valid traversals of the reasoning graph**.

Future observed traversal labels:

- forward
- backward
- bidirectional
- internal-entry
- branch-probing

The same structural chain can therefore be solved in a different order without being classified as a strong divergence.

## Research principle

Telemetry is not only a scorecard for the player. It is feedback about assumptions encoded in the generator. When real play repeatedly violates an assumption (for example, entering a chain from D/E and reasoning backward), the model should be revised before adding larger retry budgets.
