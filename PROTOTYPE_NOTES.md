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
- `+ клетка` inserts a lightweight `R#C#:` reference for the selected hidden cell.
- Android Back closes the scratchpad first.

## Phone-test checklist
1. Does the initial quarter-screen size feel right?
2. Is the drag grip easy to find and resize naturally?
3. Does the keyboard leave enough useful space for both board and note?
4. Does text survive close/reopen and Home → Continue?
5. Is `+ клетка` useful or confusing without row/column labels on the board?
6. Are four tool buttons too cramped in Russian?
7. Does this reduce the urge to switch to paper for branching puzzles?
