# MathCrossword v25 — always-visible candidate notes

Candidate notes are now rendered in a dedicated overlay pass after the board is drawn.
This makes manually entered candidates visible in every unresolved cell, not only in the
currently selected cell. There is no ellipsis/summary fallback in the board renderer.

Readability changes:
- 2–3 digit candidates prefer two columns for up to six notes;
- font size is fit to the widest value;
- the selected cell uses slightly stronger contrast;
- all saved candidates remain visible on the board while the bottom drawer can still be
  collapsed or hidden for focus mode.

The GitHub release is built from this v25 source on `main`.
