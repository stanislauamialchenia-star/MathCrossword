# v23 — Compact candidate tray

The candidate bank is now adaptive so the puzzle board remains the visual focus.

## UI change

Candidate tiles now use three density bands:

- up to 10 visible tiles: 58×50 dp, 22 sp-equivalent Canvas text;
- 11–18 tiles: 54×48 dp, 21;
- 19+ tiles: 52×48 dp, 20.

Gaps shrink from the former fixed 9 dp to 7–8 dp. On a typical ~393 dp phone width, a large bank can fit six tiles per row instead of five, often removing an entire row from the tray.

The same metrics are used both for board-space reservation and actual bank drawing, so shrinking the tray gives the recovered vertical space back to the puzzle instead of leaving an empty reserve.

No game logic, candidate semantics, generator behavior, play traces, or level progression changed in v23.
