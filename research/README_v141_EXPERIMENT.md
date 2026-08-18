# v1.41 solver-invariant experiment branch

This branch is intentionally based directly on `main` v1.41.

It adds realized hidden-cell graph traversal telemetry without modifying `SessionTracker` PuzzleRun / Visit lifecycle code or generator acceptance/scoring.

For compatibility with the existing v1.41 session schema, the new report is stored inside `routeComparison.graphTraversal` and versioned by `routeComparison.graphTraversalVersion`.

The pre-registered analysis protocol is in `research/SOLVER_INVARIANT_PROTOCOL.md`.
