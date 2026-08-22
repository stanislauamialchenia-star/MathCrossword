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
- `+ клетка` assigns the selected hidden cell a stable human label (`A`, `B`, `C`...) and inserts that label into the note.
- While the scratchpad is open, A/B/C badges are clearly visible on their cells.
- After the scratchpad is closed, the same labels remain on the board in a smaller, muted state so the external-memory link is not lost.
- A label stays attached even after a number is placed in that hidden cell; it disappears only when the puzzle/scratchpad is reset for a new puzzle.
- Android Back closes the scratchpad first.

## Phone-test checklist
1. Does the initial quarter-screen size feel right?
2. Is the drag grip easy to find and resize naturally?
3. Does the keyboard leave enough useful space for both board and note?
4. Does text survive close/reopen and Home → Continue?
5. Do muted A/B/C labels remain useful after the scratchpad is closed without making the crossword visually noisy?
6. Are the small cell badges readable without covering candidate notes or main values?
7. Are four tool buttons too cramped in Russian?
8. Does this reduce the urge to switch to paper for branching puzzles?

The persistent muted-label revision compiled successfully for both GitHub and Play variants before being committed. A signed GitHub-distribution prototype APK was also built for phone testing.
