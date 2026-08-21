from pathlib import Path

path = Path('app/src/main/java/com/offline/mathcrossword/MainActivity.java')
text = path.read_text()


def once(old, new, label):
    global text
    n = text.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1 match, got {n}')
    text = text.replace(old, new, 1)


once(
'''import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;''',
'''import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;''',
'graphics imports')

once(
'''import android.os.Environment;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import android.widget.ScrollView;
import android.widget.TextView;''',
'''import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.ScrollView;
import android.widget.TextView;''',
'view imports')

once(
'''public class MainActivity extends Activity {
    GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gameView = new GameView(this);
        setContentView(gameView);
    }''',
'''public class MainActivity extends Activity {
    GameView gameView;
    FrameLayout rootView;
    LinearLayout scratchpadPanel;
    EditText scratchpadEditor;
    long scratchpadPuzzleSeed = Long.MIN_VALUE;
    int scratchpadPanelHeightPx = -1;
    float scratchpadDragStartY = 0f;
    int scratchpadDragStartHeight = 0;
    boolean scratchpadBinding = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rootView = new FrameLayout(this);
        gameView = new GameView(this);
        rootView.addView(gameView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        scratchpadPanel = buildScratchpadPanel();
        FrameLayout.LayoutParams scratchParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM);
        rootView.addView(scratchpadPanel, scratchParams);
        scratchpadPanel.setVisibility(View.GONE);
        setContentView(rootView);
    }

    LinearLayout buildScratchpadPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dpActivity(12), 0, dpActivity(12), 0);
        panel.setElevation(dpActivity(10));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.rgb(250, 249, 246));
        panelBg.setCornerRadii(new float[]{dpActivity(18), dpActivity(18), dpActivity(18), dpActivity(18), 0, 0, 0, 0});
        panel.setBackground(panelBg);

        TextView grip = new TextView(this);
        grip.setText("━━━━");
        grip.setTextColor(Color.rgb(150, 151, 147));
        grip.setTextSize(13f);
        grip.setGravity(Gravity.CENTER);
        panel.addView(grip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpActivity(30)));
        grip.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                scratchpadDragStartY = event.getRawY();
                scratchpadDragStartHeight = Math.max(1, scratchpadPanel.getHeight());
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                int delta = Math.round(scratchpadDragStartY - event.getRawY());
                setScratchpadPanelHeight(scratchpadDragStartHeight + delta, false);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                setScratchpadPanelHeight(scratchpadPanel.getHeight(), true);
                return true;
            }
            return false;
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dpActivity(4), 0, dpActivity(4), dpActivity(4));
        panel.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpActivity(38)));

        TextView title = new TextView(this);
        title.setText(UiText.tr("Scratchpad", "Черновик", "Poznámky"));
        title.setTextColor(Color.rgb(39, 42, 40));
        title.setTextSize(15f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        title.setGravity(Gravity.CENTER_VERTICAL);

        TextView insert = scratchpadAction(UiText.tr("+ cell", "+ клетка", "+ buňka"));
        header.addView(insert, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpActivity(34)));
        insert.setOnClickListener(v -> insertSelectedCellIntoScratchpad());

        TextView close = scratchpadAction("⌄");
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dpActivity(42), dpActivity(34));
        closeParams.leftMargin = dpActivity(6);
        header.addView(close, closeParams);
        close.setTextSize(20f);
        close.setOnClickListener(v -> hideScratchpad(true));

        scratchpadEditor = new EditText(this);
        scratchpadEditor.setGravity(Gravity.TOP | Gravity.START);
        scratchpadEditor.setTextSize(16f);
        scratchpadEditor.setTextColor(Color.rgb(39, 42, 40));
        scratchpadEditor.setHintTextColor(Color.rgb(145, 147, 142));
        scratchpadEditor.setHint(UiText.tr(
                "Write freely: branches, alternatives, contradictions…",
                "Пиши свободно: ветки, варианты, противоречия…",
                "Piš volně: větve, možnosti, rozpory…"));
        scratchpadEditor.setPadding(dpActivity(10), dpActivity(8), dpActivity(10), dpActivity(12));
        scratchpadEditor.setBackgroundColor(Color.TRANSPARENT);
        scratchpadEditor.setSingleLine(false);
        scratchpadEditor.setHorizontallyScrolling(false);
        scratchpadEditor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (scratchpadBinding || scratchpadPuzzleSeed == Long.MIN_VALUE) return;
                getSharedPreferences("scratchpad_local", MODE_PRIVATE).edit()
                        .putLong("seed", scratchpadPuzzleSeed)
                        .putString("text", s.toString())
                        .apply();
            }
        });
        panel.addView(scratchpadEditor, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return panel;
    }

    TextView scratchpadAction(String label) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(13f);
        view.setTextColor(Color.rgb(62, 100, 72));
        view.setGravity(Gravity.CENTER);
        view.setPadding(dpActivity(10), 0, dpActivity(10), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(255, 255, 253));
        bg.setCornerRadius(dpActivity(10));
        bg.setStroke(dpActivity(1), Color.rgb(188, 196, 188));
        view.setBackground(bg);
        return view;
    }

    int dpActivity(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    void prepareScratchpadForPuzzle(long seed, boolean clear) {
        scratchpadPuzzleSeed = seed;
        android.content.SharedPreferences sp = getSharedPreferences("scratchpad_local", MODE_PRIVATE);
        String value = (!clear && sp.getLong("seed", Long.MIN_VALUE) == seed)
                ? sp.getString("text", "") : "";
        if (clear || sp.getLong("seed", Long.MIN_VALUE) != seed) {
            sp.edit().putLong("seed", seed).putString("text", "").apply();
        }
        scratchpadBinding = true;
        scratchpadEditor.setText(value == null ? "" : value);
        scratchpadEditor.setSelection(scratchpadEditor.length());
        scratchpadBinding = false;
        scratchpadPanelHeightPx = -1;
        hideScratchpad(false);
    }

    void toggleScratchpad() {
        if (scratchpadPanel.getVisibility() == View.VISIBLE) hideScratchpad(true);
        else showScratchpad();
    }

    void showScratchpad() {
        if (gameView == null || gameView.puzzle == null || gameView.solved) return;
        int h = Math.max(rootView.getHeight(), gameView.getHeight());
        if (h <= 0) return;
        if (scratchpadPanelHeightPx <= 0) scratchpadPanelHeightPx = Math.round(h * 0.25f) + gameView.bottomInset;
        scratchpadPanel.setVisibility(View.VISIBLE);
        setScratchpadPanelHeight(scratchpadPanelHeightPx, false);
        gameView.focusMode = false;
        gameView.tracker.event("scratchpad_open", -1, -1, 1, null);
        scratchpadEditor.clearFocus();
    }

    void hideScratchpad(boolean track) {
        if (scratchpadPanel == null) return;
        if (scratchpadPanel.getVisibility() == View.VISIBLE) {
            scratchpadPanelHeightPx = scratchpadPanel.getHeight();
            if (track && gameView != null) gameView.tracker.event("scratchpad_open", -1, -1, 0, null);
        }
        scratchpadPanel.setVisibility(View.GONE);
        if (gameView != null) gameView.setScratchpadOverlay(false, 0);
        if (scratchpadEditor != null) scratchpadEditor.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && scratchpadEditor != null) imm.hideSoftInputFromWindow(scratchpadEditor.getWindowToken(), 0);
    }

    void setScratchpadPanelHeight(int requested, boolean track) {
        int h = Math.max(rootView.getHeight(), gameView == null ? 0 : gameView.getHeight());
        if (h <= 0) return;
        int min = Math.round(h * 0.22f) + (gameView == null ? 0 : gameView.bottomInset);
        int max = Math.round(h * 0.62f) + (gameView == null ? 0 : gameView.bottomInset);
        int value = Math.max(min, Math.min(max, requested));
        scratchpadPanelHeightPx = value;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) scratchpadPanel.getLayoutParams();
        lp.height = value;
        lp.gravity = Gravity.BOTTOM;
        scratchpadPanel.setLayoutParams(lp);
        if (gameView != null) {
            gameView.setScratchpadOverlay(true, value);
            if (track) {
                int pct = Math.round(value * 100f / Math.max(1, h));
                gameView.tracker.event("scratchpad_resize", -1, -1, pct, null);
            }
        }
    }

    void insertSelectedCellIntoScratchpad() {
        if (gameView == null) return;
        String ref = gameView.selectedScratchpadCellReference();
        if (ref == null) {
            Toast.makeText(this, UiText.tr(
                    "Select a hidden cell first",
                    "Сначала выбери скрытую клетку",
                    "Nejdřív vyber skrytou buňku"), Toast.LENGTH_SHORT).show();
            return;
        }
        Editable editable = scratchpadEditor.getText();
        if (editable.length() > 0 && editable.charAt(editable.length() - 1) != '\n') editable.append('\n');
        editable.append(ref).append(": ");
        scratchpadEditor.requestFocus();
        scratchpadEditor.setSelection(scratchpadEditor.length());
        gameView.tracker.event("scratchpad_insert_cell", gameView.selectedCell.x, gameView.selectedCell.y, 1, null);
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(scratchpadEditor, InputMethodManager.SHOW_IMPLICIT);
    }''',
'activity scratchpad shell')

once(
'''        final RectF candidateRect = new RectF();
        final RectF hintRect = new RectF();''',
'''        final RectF candidateRect = new RectF();
        final RectF scratchpadRect = new RectF();
        final RectF hintRect = new RectF();''',
'scratchpad rect')

once(
'''        boolean candidateMode = false;
        int hintStage = 0;
        boolean focusMode = false;''',
'''        boolean candidateMode = false;
        int hintStage = 0;
        boolean focusMode = false;
        boolean scratchpadOverlayOpen = false;
        float scratchpadReservedHeight = 0f;''',
'scratchpad game state')

once(
'''            float solvedDrawerHeight = dp(178) + bottomInset;
            float effectiveDrawerHeight = solved ? solvedDrawerHeight : (focusMode ? drawerMin : candidateDrawerHeight);''',
'''            float solvedDrawerHeight = dp(178) + bottomInset;
            float scratchpadHeight = Math.max(drawerMin, scratchpadReservedHeight);
            float effectiveDrawerHeight = solved ? solvedDrawerHeight
                    : (scratchpadOverlayOpen ? scratchpadHeight : (focusMode ? drawerMin : candidateDrawerHeight));''',
'reserve scratchpad height')

once(
'''            if (solved) drawSolvedBanner(canvas, w, h);
            else drawCandidateDrawer(canvas, drawerTop, effectiveDrawerHeight, w, h, drawerMin, drawerMax);''',
'''            if (solved) drawSolvedBanner(canvas, w, h);
            else if (!scratchpadOverlayOpen) drawCandidateDrawer(canvas, drawerTop, effectiveDrawerHeight, w, h, drawerMin, drawerMax);''',
'hide candidate drawer under scratchpad')

once(
'''            bankHits.clear();
            undoRect.setEmpty(); candidateRect.setEmpty(); hintRect.setEmpty();''',
'''            bankHits.clear();
            undoRect.setEmpty(); candidateRect.setEmpty(); scratchpadRect.setEmpty(); hintRect.setEmpty();''',
'clear scratchpad rect')

once(
'''        void drawGameTools(Canvas c, float y, float w) {
            float side = dp(16);
            float gap = dp(8);
            float h = dp(44);
            float totalW = w - side * 2 - gap * 2;
            float each = totalW / 3f;
            undoRect.set(side, y, side + each, y + h);
            candidateRect.set(undoRect.right + gap, y, undoRect.right + gap + each, y + h);
            hintRect.set(candidateRect.right + gap, y, w - side, y + h);
            drawToolButton(c, undoRect, UiText.tr("↶ Undo", "↶ Отмена", "↶ Zpět"), !undoStack.isEmpty(), false);
            drawToolButton(c, candidateRect, UiText.tr("✎ Cand.", "✎ Канд.", "✎ Kand."), true, candidateMode);
            drawToolButton(c, hintRect, UiText.tr("? Hint", "? Намёк", "? Nápověda"), true, false);
        }''',
'''        void drawGameTools(Canvas c, float y, float w) {
            float side = dp(12);
            float gap = dp(6);
            float h = dp(44);
            float totalW = w - side * 2 - gap * 3;
            float each = totalW / 4f;
            undoRect.set(side, y, side + each, y + h);
            candidateRect.set(undoRect.right + gap, y, undoRect.right + gap + each, y + h);
            scratchpadRect.set(candidateRect.right + gap, y, candidateRect.right + gap + each, y + h);
            hintRect.set(scratchpadRect.right + gap, y, w - side, y + h);
            drawToolButton(c, undoRect, UiText.tr("↶ Undo", "↶ Отмена", "↶ Zpět"), !undoStack.isEmpty(), false);
            drawToolButton(c, candidateRect, UiText.tr("✎ Cand.", "✎ Канд.", "✎ Kand."), true, candidateMode);
            drawToolButton(c, scratchpadRect, UiText.tr("▤ Draft", "▤ Черн.", "▤ Pozn."), true, false);
            drawToolButton(c, hintRect, UiText.tr("? Hint", "? Намёк", "? Nápověda"), true, false);
        }''',
'four game tools')

once(
'''            if (candidateRect.contains(x, y)) {
                candidateMode = !candidateMode;
                tracker.event("candidate_mode", -1, -1, candidateMode ? 1 : -1, null);
                selectedTileId = -1;
                invalidate();
                return true;
            }
            if (hintRect.contains(x, y)) {''',
'''            if (candidateRect.contains(x, y)) {
                candidateMode = !candidateMode;
                tracker.event("candidate_mode", -1, -1, candidateMode ? 1 : -1, null);
                selectedTileId = -1;
                invalidate();
                return true;
            }
            if (scratchpadRect.contains(x, y)) {
                ((MainActivity) getContext()).toggleScratchpad();
                return true;
            }
            if (hintRect.contains(x, y)) {''',
'scratchpad touch')

once(
'''        void resetBoardViewport() {
            boardZoom = 1f; boardPanX = 0f; boardPanY = 0f;
            clearLocalFocus();
            cancelBoardLongPress();
        }''',
'''        void resetBoardViewport() {
            boardZoom = 1f; boardPanX = 0f; boardPanY = 0f;
            clearLocalFocus();
            cancelBoardLongPress();
        }

        void setScratchpadOverlay(boolean open, float height) {
            scratchpadOverlayOpen = open;
            scratchpadReservedHeight = open ? Math.max(dp(1), height) : 0f;
            if (open) {
                focusMode = false;
                cancelBoardLongPress();
            }
            invalidate();
        }

        String selectedScratchpadCellReference() {
            if (puzzle == null || selectedCell == null || !puzzle.hidden.contains(selectedCell)) return null;
            int row = selectedCell.y - puzzle.minY + 1;
            int col = selectedCell.x - puzzle.minX + 1;
            return "R" + row + "C" + col;
        }''',
'scratchpad game helpers')

once(
'''            screen = Screen.GAME;
            resumablePuzzle = false;
            startTrackerForCurrentPuzzle();''',
'''            screen = Screen.GAME;
            resumablePuzzle = false;
            ((MainActivity) getContext()).prepareScratchpadForPuzzle(puzzle.seed, true);
            startTrackerForCurrentPuzzle();''',
'new path scratchpad')

once(
'''                    screen = Screen.GAME;
                    resumablePuzzle = false;
                    startTrackerForCurrentPuzzle();''',
'''                    screen = Screen.GAME;
                    resumablePuzzle = false;
                    ((MainActivity) getContext()).prepareScratchpadForPuzzle(puzzle.seed, true);
                    startTrackerForCurrentPuzzle();''',
'new free scratchpad')

once(
'''            candidateNotes.clear();
            undoStack.clear();
            resetBoardViewport();
            invalidate();''',
'''            candidateNotes.clear();
            undoStack.clear();
            resetBoardViewport();
            ((MainActivity) getContext()).prepareScratchpadForPuzzle(puzzle.seed, true);
            invalidate();''',
'reset scratchpad')

once(
'''                screen = Screen.HOME;
                selectedCell = null;
                selectedTileId = -1;
                candidateMode = false;''',
'''                ((MainActivity) getContext()).hideScratchpad(false);
                screen = Screen.HOME;
                selectedCell = null;
                selectedTileId = -1;
                candidateMode = false;''',
'close scratchpad on back/home')

path.write_text(text)
