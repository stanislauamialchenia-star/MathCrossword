# Constructive generators — v10

v9 used one generic generator plus strategy-specific scoring. This worked well for some strategies, but CHAIN and NETWORK often depended on luck: the generic geometry had to accidentally acquire the desired reasoning signature.

v10 begins separating **construction** from **evaluation**.

## CHAIN: `chain-snake-v1`

The constructor first creates a long one-intersection dependency path. Each new equation shares exactly one number with the previous equation. Arithmetic is then assigned along that path and hidden cells are selected with CHAIN-specific difficulty rules.

Important distinction: a long forced cascade is not automatically penalized in CHAIN. It can be the intended mechanism after the player discovers a non-obvious entry point.

## NETWORK: `network-lattice-v1`

The constructor begins with a 3x3 number lattice: three horizontal plus three vertical equations. This creates cycles by construction rather than hoping the generic geometry closes enough bridges. Extra equations preferentially connect existing number nodes before growing ordinary branches.

NETWORK has its own difficulty acceptance rules. It rewards multiple cycles, ambiguous equations, broad candidate domains and several simultaneously constrained cells.

## Fallback remains

The v9 fallback system is preserved. If a strategy-specific constructor cannot find an acceptable board inside a bounded budget, the generator may still use the marked fallback path. The resulting session records that the requested strategy was not matched.

## Why not make five fully separate generators immediately?

That would duplicate arithmetic, uniqueness checking, hidden-cell logic and validation. v10 instead separates only the pieces that truly need different construction principles, while keeping shared mathematical invariants in the common engine.


## v14: HYPOTHESIS and structural diagonals

`ConstructiveHypothesisBuilder` adds `hypothesis-fork` and `hypothesis-diamond`. It is currently production-enabled for Logic 4 only. Logic 5 remains a measured frontier rather than being hidden behind larger retry budgets.

Diagonal generation is now policy-driven. Chain/Network may keep a diagonal only when it closes a real existing-node bridge in an eligible family. Generic geometry and Hypothesis remain orthogonal.
