# MathCrossword v1.32 — HumanSolver route comparison

- Adds a deterministic route model built from the same domain propagation and depth-1/depth-2 contradiction checks used by HumanSolver.
- Forced cells are represented as waves rather than an arbitrary strict order.
- Finished local sessions compare the model route with the order of meaningful cells actually touched by the player.
- Metrics include early-entry agreement, order agreement, early arrival at probe/pivot cells, alternate entry/order and a conservative strong-divergence flag.
- The Analysis screen shows aggregate route agreement; the detailed latest trajectory shows model route vs observed route and model-check signals.
- This compares an algorithmic route with interaction data; it does not reconstruct thought or classify personality.
- Puzzle generation is unchanged; generator v22 remains the mathematical baseline.
