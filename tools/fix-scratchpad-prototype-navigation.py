from pathlib import Path

p = Path('app/src/main/java/com/offline/mathcrossword/MainActivity.java')
s = p.read_text()

def once(old, new, label):
    global s
    n = s.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1 match, got {n}')
    s = s.replace(old, new, 1)

once(
'''    @Override
    public void onBackPressed() {
        if (gameView != null && gameView.goHomeIfNeeded()) return;
        super.onBackPressed();
    }''',
'''    @Override
    public void onBackPressed() {
        if (scratchpadPanel != null && scratchpadPanel.getVisibility() == View.VISIBLE) {
            hideScratchpad(true);
            return;
        }
        if (gameView != null && gameView.goHomeIfNeeded()) return;
        super.onBackPressed();
    }''',
'back closes scratchpad')

once(
'''            if (!focusMode && topHomeRect.contains(x, y)) {
                if (tracker.hasOpenSession() && !solved) {
                    tracker.finish(false, "home");
                    resumablePuzzle = puzzle != null;
                }
                screen = Screen.HOME; invalidate(); return true;
            }
            if ((!focusMode && menuRect.contains(x, y)) || (focusMode && focusMenuRect.contains(x, y))) {
                showGameMenu();
                return true;
            }''',
'''            if (!focusMode && topHomeRect.contains(x, y)) {
                ((MainActivity) getContext()).hideScratchpad(false);
                if (tracker.hasOpenSession() && !solved) {
                    tracker.finish(false, "home");
                    resumablePuzzle = puzzle != null;
                }
                screen = Screen.HOME; invalidate(); return true;
            }
            if ((!focusMode && menuRect.contains(x, y)) || (focusMode && focusMenuRect.contains(x, y))) {
                ((MainActivity) getContext()).hideScratchpad(false);
                showGameMenu();
                return true;
            }''',
'home and menu close scratchpad')

p.write_text(s)
