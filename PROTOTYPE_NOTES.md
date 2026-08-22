# Scratchpad prototype phone test

PR: #55 · Issue: #54

This branch is intentionally a prototype and should not be merged before phone testing.

## Current interaction
- `Черн.` / Scratchpad is the fourth in-game tool beside Undo, Candidates, and Hint.
- First open uses roughly one quarter of the screen.
- Drag the top grip to resize the panel between about 22% and 62% of screen height.
- The crossword reserves that space, so the scratchpad replaces the number-bank area rather than covering the board.
- Free-form note text stays local to the device and is not exported as research telemetry.
- Closing/reopening the panel keeps the current note.
- A new or restarted puzzle gets a clean note.
- `+ клетка` now assigns the selected hidden cell a human label (`A`, `B`, `C`...) and inserts `A:` into the note.
- The same cell keeps the same label when inserted again.
- Letter badges are shown on the corresponding puzzle cells only while the scratchpad is open, so the normal board stays clean when it is closed.
- Android Back closes the scratchpad first.

## Phone-test checklist
1. Does the initial quarter-screen size feel right?
2. Is the drag grip easy to find and resize naturally?
3. Does the keyboard leave enough useful space for both board and note?
4. Does text survive close/reopen and Home → Continue?
5. Do `A/B/C` labels make it immediately obvious which scratchpad line belongs to which cell?
6. Are the small cell badges readable without covering candidate notes or main values?
7. Are four tool buttons too cramped in Russian?
8. Does this reduce the urge to switch to paper for branching puzzles?

The A/B/C label revision was compiled successfully for both GitHub and Play variants before being committed to the prototype branch.
