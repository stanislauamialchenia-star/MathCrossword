# Scratchpad prototype phone test

PR: #55 · Issue: #54

Phone testing confirmed the base scratchpad interaction is useful enough to promote to production as v1.50.

## Production interaction
- `Черн.` / Scratchpad is the fourth in-game tool beside Undo, Candidates, and Hint.
- First open uses roughly one quarter of the screen.
- Drag the top grip to resize the panel between about 22% and 62% of screen height.
- The crossword reserves that space, so the scratchpad replaces the number-bank area rather than covering the board.
- Free-form note text stays local to the device and is not exported as research telemetry.
- Closing/reopening the panel keeps the current note.
- A new or restarted puzzle gets a clean note.
- `+ клетка` assigns stable human labels (`A`, `B`, `C`...) to selected hidden cells and inserts the matching `A:` reference into the note.
- The same cell keeps the same label when inserted again.
- Labels remain on the board after the scratchpad is closed in a smaller muted state, and stay attached if a number is placed in the cell.
- Android Back closes the scratchpad first.

## Phone-test result
- the free-form sheet feels useful as external working memory;
- A/B/C references are more natural than row/column coordinates;
- keeping muted labels visible after closing preserves the link between board and note;
- the base feature should remain simple until real use shows a need for richer notation.

## Deferred follow-ups
- tapping an A/B/C badge to reopen/jump to its note;
- quick symbols/arrows/branch controls;
- richer scratchpad-derived player metrics.

## Release
Promoted as v1.50 / code 50 after green CI.
