# UX decisions

## Wrong-number removal — issue #56

Decision after first external tester feedback:

- keep the existing repeat-tap gesture as a shortcut;
- do not rely on that gesture as the only way to correct a filled cell;
- when a filled cell is selected, expose an explicit contextual `× Clear / × Удалить / × Smazat` action;
- the first tap only selects the cell; deletion requires the explicit clear action or the existing repeat-tap shortcut, avoiding accidental single-tap deletion;
- do not add a tutorial modal; prefer a self-explanatory control in the normal game UI;
- preserve Undo, Scratchpad and Hint behavior;
- target this as the next small UX release (v1.51) after phone verification.

Implementation branch: `ux/discoverable-clear`.
