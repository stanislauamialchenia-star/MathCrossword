# MathCrossword v1.31 — trajectory analysis front

- The compact Analysis screen stays visually quiet.
- `Последняя траектория` is now tappable and opens a scrollable detailed report.
- The report exposes the chess-like semantic action log already stored locally: candidate add/remove, placements, undo, hints, reset and incorrect completion signals with active timestamps.
- It surfaces candidate-to-commit time, recovery episodes, candidate-cell switching/revisits and rapid cascades.
- It adds conservative **signals for checking the model** when observed play disagrees with generator/analyzer expectations (cascade mismatch, hypothesis-pivot mismatch, repeated revisits).
- These signals describe interaction with the puzzle; they are not claims about personality or a literal recording of thought.
- No puzzle-generation rules changed in this version. Generator v22 remains the mathematical baseline.
