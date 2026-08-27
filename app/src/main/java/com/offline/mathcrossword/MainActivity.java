package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class MainActivity extends Activity {
    GameView gameView;
    FrameLayout rootView;
    LinearLayout scratchpadPanel;
    EditText scratchpadEditor;
    LinearLayout scratchpadCandidateShelf;
    TextView scratchpadCandidateModeAction;
    TextView scratchpadUndoAction;
    TextView scratchpadInsertAction;
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
        panel.setPadding(dpActivity(10), 0, dpActivity(10), 0);
        panel.setElevation(dpActivity(10));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.rgb(250, 249, 246));
        panelBg.setCornerRadii(new float[]{dpActivity(18), dpActivity(18), dpActivity(18), dpActivity(18), 0, 0, 0, 0});
        panel.setBackground(panelBg);

        // Keep resize discoverable, but give the grip only a narrow centered touch target.
        FrameLayout gripRow = new FrameLayout(this);
        panel.addView(gripRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpActivity(18)));
        TextView grip = new TextView(this);
        grip.setText("━");
        grip.setTextColor(Color.rgb(164, 165, 161));
        grip.setTextSize(9f);
        grip.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams gripParams = new FrameLayout.LayoutParams(
                dpActivity(64), dpActivity(18), Gravity.CENTER);
        gripRow.addView(grip, gripParams);
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

        // One compact command strip: actions stay in one visual location while the
        // remaining surface is reserved for candidates and reasoning.
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, 0, 0, dpActivity(2));
        panel.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpActivity(38)));

        scratchpadUndoAction = scratchpadWorkbenchAction("↶", false, true, false);
        LinearLayout.LayoutParams undoParams = new LinearLayout.LayoutParams(dpActivity(38), dpActivity(34));
        header.addView(scratchpadUndoAction, undoParams);
        scratchpadUndoAction.setContentDescription(UiText.tr("Undo", "Отмена", "Zpět"));
        scratchpadUndoAction.setOnClickListener(v -> {
            if (gameView == null || gameView.undoStack.isEmpty()) return;
            gameView.undoLastAction();
            refreshScratchpadWorkbenchState();
        });

        scratchpadCandidateModeAction = scratchpadWorkbenchAction("✎", false, true, true);
        LinearLayout.LayoutParams candidateParams = new LinearLayout.LayoutParams(dpActivity(38), dpActivity(34));
        candidateParams.leftMargin = dpActivity(5);
        header.addView(scratchpadCandidateModeAction, candidateParams);
        scratchpadCandidateModeAction.setContentDescription(UiText.tr(
                "Candidate mode", "Режим кандидатов", "Režim kandidátů"));
        scratchpadCandidateModeAction.setOnClickListener(v -> {
            if (gameView == null) return;
            gameView.candidateMode = !gameView.candidateMode;
            gameView.selectedTileId = -1;
            gameView.tracker.event("candidate_mode", -1, -1, gameView.candidateMode ? 1 : -1, "workbench");
            refreshScratchpadWorkbenchState();
            gameView.invalidate();
        });

        scratchpadInsertAction = scratchpadWorkbenchAction("⊞", false, true, false);
        LinearLayout.LayoutParams insertParams = new LinearLayout.LayoutParams(dpActivity(38), dpActivity(34));
        insertParams.leftMargin = dpActivity(5);
        header.addView(scratchpadInsertAction, insertParams);
        scratchpadInsertAction.setContentDescription(UiText.tr(
                "Insert selected cell", "Вставить выбранную клетку", "Vložit vybranou buňku"));
        scratchpadInsertAction.setOnClickListener(v -> insertSelectedCellIntoScratchpad());

        TextView hintAction = scratchpadWorkbenchAction("?", false, true, true);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(dpActivity(38), dpActivity(34));
        hintParams.leftMargin = dpActivity(5);
        header.addView(hintAction, hintParams);
        hintAction.setContentDescription(UiText.tr("Hint", "Намёк", "Nápověda"));
        hintAction.setOnClickListener(v -> {
            if (gameView == null) return;
            gameView.showGuidedHint();
            refreshScratchpadWorkbenchState();
        });

        TextView close = scratchpadWorkbenchAction("⌄", false, true, true);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dpActivity(38), dpActivity(34));
        closeParams.leftMargin = dpActivity(5);
        header.addView(close, closeParams);
        close.setContentDescription(UiText.tr(
                "Collapse workbench", "Свернуть рабочую панель", "Sbalit pracovní panel"));
        close.setOnClickListener(v -> hideScratchpad(true));

        // Candidates are no longer a separate screen/tab. They form a permanent shelf
        // directly above the writing surface.
        scratchpadCandidateShelf = new LinearLayout(this);
        scratchpadCandidateShelf.setOrientation(LinearLayout.VERTICAL);
        scratchpadCandidateShelf.setGravity(Gravity.CENTER_HORIZONTAL);
        scratchpadCandidateShelf.setPadding(dpActivity(2), dpActivity(2), dpActivity(2), dpActivity(5));
        panel.addView(scratchpadCandidateShelf, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        scratchpadEditor = new EditText(this);
        scratchpadEditor.setGravity(Gravity.TOP | Gravity.START);
        scratchpadEditor.setTextSize(16f);
        scratchpadEditor.setTextColor(Color.rgb(39, 42, 40));
        scratchpadEditor.setHintTextColor(Color.rgb(178, 180, 175));
        scratchpadEditor.setHint("…");
        scratchpadEditor.setPadding(dpActivity(8), dpActivity(2), dpActivity(8), dpActivity(12));
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

    TextView scratchpadWorkbenchAction(String label, boolean active, boolean compact, boolean enabled) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setGravity(Gravity.CENTER);
        view.setPadding(compact ? 0 : dpActivity(8), 0, compact ? 0 : dpActivity(8), 0);
        view.setTextSize(compact ? 18f : 13f);
        styleScratchpadWorkbenchAction(view, active, enabled);
        return view;
    }

    void styleScratchpadWorkbenchAction(TextView view, boolean active, boolean enabled) {
        if (view == null) return;
        view.setEnabled(enabled);
        view.setTextColor(enabled ? Color.rgb(39, 42, 40) : Color.rgb(160, 168, 160));
        view.setTypeface(active ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(active ? Color.rgb(232, 238, 231)
                : (enabled ? Color.rgb(255, 255, 253) : Color.rgb(239, 239, 236)));
        bg.setCornerRadius(dpActivity(11));
        bg.setStroke(dpActivity(active ? 2 : 1), active ? Color.rgb(62, 100, 72)
                : (enabled ? Color.rgb(174, 176, 171) : Color.rgb(205, 206, 202)));
        view.setBackground(bg);
    }

    void refreshScratchpadWorkbenchState() {
        if (gameView == null) return;
        styleScratchpadWorkbenchAction(scratchpadUndoAction, false, !gameView.undoStack.isEmpty());
        styleScratchpadWorkbenchAction(scratchpadCandidateModeAction, gameView.candidateMode, true);
        boolean canInsert = gameView.selectedCell != null
                && gameView.puzzle != null
                && gameView.puzzle.hidden.contains(gameView.selectedCell);
        styleScratchpadWorkbenchAction(scratchpadInsertAction, false, canInsert);
        refreshScratchpadCandidateShelf();
    }

    void refreshScratchpadCandidateShelf() {
        if (scratchpadCandidateShelf == null) return;
        scratchpadCandidateShelf.removeAllViews();
        if (gameView == null || gameView.puzzle == null) return;

        List<Tile> visible = new ArrayList<>();
        for (Tile t : gameView.puzzle.tiles) if (!t.used) visible.add(t);
        if (visible.isEmpty()) return;

        int screenW = rootView == null ? 0 : rootView.getWidth();
        if (screenW <= 0) screenW = getResources().getDisplayMetrics().widthPixels;
        int contentW = Math.max(dpActivity(220), screenW - dpActivity(28));
        int gap = dpActivity(4);
        int targetW = dpActivity(38);
        int perRow = Math.max(4, Math.min(8, (contentW + gap) / (targetW + gap)));
        int tileW = Math.max(dpActivity(34),
                (contentW - gap * Math.max(0, perRow - 1)) / perRow);
        int tileH = dpActivity(32);

        Set<Integer> selectedNotes = Collections.emptySet();
        if (gameView.selectedCell != null) {
            LinkedHashSet<Integer> notes = gameView.candidateNotes.get(gameView.selectedCell);
            if (notes != null) selectedNotes = notes;
        }

        for (int start = 0; start < visible.size(); start += perRow) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, tileH);
            if (start > 0) rowParams.topMargin = gap;
            scratchpadCandidateShelf.addView(row, rowParams);

            int end = Math.min(visible.size(), start + perRow);
            for (int i = start; i < end; i++) {
                Tile t = visible.get(i);
                TextView tile = new TextView(this);
                tile.setText(Integer.toString(t.value));
                tile.setGravity(Gravity.CENTER);
                tile.setTextSize(15f);
                tile.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                tile.setTextColor(Color.rgb(39, 42, 40));
                boolean selected = t.id == gameView.selectedTileId;
                boolean noted = gameView.candidateMode
                        && gameView.selectedCell != null
                        && selectedNotes.contains(t.value);
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(selected ? Color.rgb(232, 238, 231)
                        : (noted ? Color.rgb(245, 248, 244) : Color.rgb(255, 255, 253)));
                bg.setCornerRadius(dpActivity(6));
                bg.setStroke(dpActivity(selected || noted ? 2 : 1),
                        selected || noted ? Color.rgb(62, 100, 72) : Color.rgb(74, 76, 73));
                tile.setBackground(bg);
                tile.setContentDescription(UiText.tr(
                        "Candidate " + t.value,
                        "Кандидат " + t.value,
                        "Kandidát " + t.value));
                final int tileId = t.id;
                tile.setOnClickListener(v -> onScratchpadCandidateTapped(tileId));

                LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(tileW, tileH);
                if (i > start) tileParams.leftMargin = gap;
                row.addView(tile, tileParams);
            }
        }
    }

    void onScratchpadCandidateTapped(int tileId) {
        if (gameView == null || gameView.puzzle == null || gameView.solved) return;
        Tile t = gameView.tileById(tileId);
        if (t == null || t.used) return;

        if (gameView.candidateMode) {
            if (gameView.selectedCell != null) {
                gameView.toggleCandidate(gameView.selectedCell, t.value);
                gameView.selectedTileId = -1;
            } else {
                gameView.selectedTileId = gameView.selectedTileId == tileId ? -1 : tileId;
                gameView.tracker.event(gameView.selectedTileId == -1 ? "deselect_tile" : "select_tile",
                        -1, -1, t.value, "candidate");
            }
        } else if (gameView.selectedCell != null) {
            gameView.saveUndoState();
            Pos target = gameView.selectedCell;
            gameView.placeTileInCell(tileId, target);
            gameView.resetHintDepth();
            gameView.candidateNotes.remove(target);
            gameView.selectedCell = null;
            gameView.selectedTileId = -1;
            gameView.checkSolved();
        } else {
            gameView.selectedTileId = gameView.selectedTileId == tileId ? -1 : tileId;
            gameView.tracker.event(gameView.selectedTileId == -1 ? "deselect_tile" : "select_tile",
                    -1, -1, t.value, null);
        }

        refreshScratchpadWorkbenchState();
        gameView.invalidate();
        if (gameView.solved) hideScratchpad(false);
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
        if (clear && gameView != null) gameView.clearScratchpadCellLabels();
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
        if (scratchpadPanelHeightPx <= 0) {
            // Candidates + writing surface share one compact workbench.
            scratchpadPanelHeightPx = Math.round(h * 0.30f) + gameView.bottomInset;
        }
        scratchpadPanel.setVisibility(View.VISIBLE);
        setScratchpadPanelHeight(scratchpadPanelHeightPx, false);
        gameView.focusMode = false;
        refreshScratchpadWorkbenchState();
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
        String ref = gameView.ensureSelectedScratchpadCellLabel();
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameView != null) gameView.onHostResume();
    }

    @Override
    protected void onPause() {
        if (gameView != null) gameView.onHostPause();
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (gameView != null) gameView.onHostActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (scratchpadPanel != null && scratchpadPanel.getVisibility() == View.VISIBLE) {
            hideScratchpad(true);
            return;
        }
        if (gameView != null && gameView.goHomeIfNeeded()) return;
        super.onBackPressed();
    }

    static final class BankHit {
        final RectF rect;
        final int tileId;
        BankHit(RectF rect, int tileId) { this.rect = rect; this.tileId = tileId; }
    }

    enum Screen { HOME, LEVELS, FREE_SETUP, LIBRARY, ANALYSIS, GAME }
    enum GameMode { PATH, FREE }

    static final class GameSnapshot {
        final Map<Pos, Integer> placed = new HashMap<>();
        final Map<Pos, LinkedHashSet<Integer>> notes = new HashMap<>();

        GameSnapshot(Map<Pos, Integer> placedTile, Map<Pos, LinkedHashSet<Integer>> candidateNotes) {
            placed.putAll(placedTile);
            for (Map.Entry<Pos, LinkedHashSet<Integer>> e : candidateNotes.entrySet()) {
                notes.put(e.getKey(), new LinkedHashSet<>(e.getValue()));
            }
        }
    }

    static final class GameView extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        final SharedPreferences prefs;
        final SessionTracker tracker;
        final Random freeSeedRandom = new Random();
        static final int REQUEST_RESEARCH_EXPORT = 7301;

        Screen screen = Screen.HOME;
        GameMode mode = GameMode.PATH;
        Puzzle puzzle;
        int level;
        int progressLevel;
        int levelPage = 0;
        int selectedTileId = -1;
        Pos selectedCell = null;
        boolean solved = false;
        // True only while an unfinished in-memory board is parked on Home.
        boolean resumablePuzzle = false;
        boolean candidateMode = false;
        int hintStage = 0;
        boolean focusMode = false;
        boolean scratchpadOverlayOpen = false;
        float scratchpadReservedHeight = 0f;
        float candidateDrawerHeight = -1f;
        float lastExpandedDrawerHeight = -1f;
        boolean draggingCandidateDrawer = false;
        float drawerDragStartY = 0f;
        float drawerDragStartHeight = 0f;
        final Map<Pos, LinkedHashSet<Integer>> candidateNotes = new HashMap<>();
        final LinkedHashMap<Pos, String> scratchpadCellLabels = new LinkedHashMap<>();
        int scratchpadNextLabel = 0;
        final List<GameSnapshot> undoStack = new ArrayList<>();
        int topInset = 0;
        int bottomInset = 0;
        float cellSize, originX, originY;

        // Board viewport: fit-to-screen is the baseline; pinch/pan is only a temporary visual lens.
        float boardZoom = 1f, boardPanX = 0f, boardPanY = 0f;
        float boardViewportTop = 0f, boardViewportBottom = 0f;
        boolean boardPanGesture = false, boardGestureMoved = false, pinching = false;
        float boardDownX = 0f, boardDownY = 0f, boardPanStartX = 0f, boardPanStartY = 0f;
        float pinchStartDistance = 0f, pinchStartZoom = 1f;
        long lastBoardTapTime = 0L;
        float lastBoardTapX = 0f, lastBoardTapY = 0f;
        Pos longPressPos = null, localFocusCell = null;
        final Set<Pos> localFocusPositions = new HashSet<>();
        Runnable boardLongPressRunnable = null;
        boolean boardLongPressTriggered = false;

        // A downloaded APK stays pending while Android asks once for install-from-this-source permission.
        Uri pendingInstallUri = null;
        String pendingInstallVersion = null;

        int freeLogic = 5;
        int freeCalc = 4;
        int freeSize = 1;
        int freeMaxIndex = 1;
        int freeStrategyIndex = 4;
        int libraryEntryIndex = 0;
        final SolutionStrategy[] strategies = SolutionStrategy.values();
        final int[] freeMaxValues = {20, 100, 500, 1000};
        final LinkedHashSet<Character> freeOps = new LinkedHashSet<>();
        long lastFreeSeed = 0L;
        volatile boolean generating = false;
        long generationToken = 0L;
        volatile Puzzle prefetchedPathPuzzle = null;
        volatile int prefetchedPathLevel = -1;
        volatile boolean pathPrefetchRunning = false;
        volatile int pathPrefetchTarget = -1;

        final List<BankHit> bankHits = new ArrayList<>();
        final RectF homeContinueRect = new RectF();
        final RectF homeLevelsRect = new RectF();
        final RectF homeFreeRect = new RectF();
        final RectF homeLibraryRect = new RectF();
        final RectF homeAnalysisRect = new RectF();
        final RectF homeUpdateRect = new RectF();
        final RectF homeLanguageRect = new RectF();
        final RectF homePrivacyRect = new RectF();
        final RectF analysisLastTraceRect = new RectF();
        final RectF analysisExportRect = new RectF();
        String updateStatus = UiText.tr("update not checked", "обновление не проверено", "aktualizace nezkontrolována");
        boolean updateChecking = false;
        final RectF topHomeRect = new RectF();
        final RectF resetRect = new RectF();
        final RectF menuRect = new RectF();
        final RectF focusMenuRect = new RectF();
        final RectF drawerHandleRect = new RectF();
        final RectF undoRect = new RectF();
        final RectF candidateRect = new RectF();
        final RectF scratchpadRect = new RectF();
        final RectF hintRect = new RectF();
        final RectF nextLevelRect = new RectF();
        final RectF solvedInsightRect = new RectF();
        final RectF freeGenerateRect = new RectF();
        final RectF[] logicRects = {new RectF(), new RectF(), new RectF(), new RectF(), new RectF(),
                new RectF(), new RectF(), new RectF(), new RectF(), new RectF()};
        final RectF[] calcRects = {new RectF(), new RectF(), new RectF(), new RectF(), new RectF(),
                new RectF(), new RectF(), new RectF(), new RectF(), new RectF()};
        final RectF[] sizeRects = {new RectF(), new RectF(), new RectF()};
        final RectF[] maxRects = {new RectF(), new RectF(), new RectF(), new RectF()};
        final RectF[] opRects = {new RectF(), new RectF(), new RectF(), new RectF(), new RectF()};
        final RectF[] strategyRects = {new RectF(), new RectF(), new RectF(), new RectF(), new RectF()};
        final RectF[] levelRects = new RectF[100];
        final RectF levelsPrevPageRect = new RectF();
        final RectF levelsNextPageRect = new RectF();
        final RectF libraryPrevRect = new RectF();
        final RectF libraryNextRect = new RectF();

        // Minimal visual system: warm neutral surfaces, one muted green accent.
        // Success/error colors stay semantic instead of acting as decoration.
        final int bg = Color.rgb(246, 245, 241);
        final int board = Color.rgb(255, 255, 253);
        final int ink = Color.rgb(39, 42, 40);
        final int green = Color.rgb(229, 239, 226);
        final int red = Color.rgb(249, 224, 221);
        final int selected = Color.rgb(232, 238, 231);
        final int accent = Color.rgb(62, 100, 72);
        final int soft = Color.rgb(250, 249, 246);

        GameView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE);
            UiText.setLanguageOverride(prefs.getString("language_override", "system"));
            SolutionStrategy.refreshAllLocalizedText();
            SolutionLibrary.refreshLocalizedEntries();
            updateStatus = UiText.tr("update not checked", "обновление не проверено", "aktualizace nezkontrolována");
            tracker = new SessionTracker(context);
            progressLevel = Math.max(1, prefs.getInt("currentLevel", 1));
            level = progressLevel;
            for (int i = 0; i < levelRects.length; i++) levelRects[i] = new RectF();
            freeOps.add('+'); freeOps.add('-');
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setOnApplyWindowInsetsListener((v, insets) -> {
                    topInset = insets.getSystemWindowInsetTop();
                    bottomInset = insets.getSystemWindowInsetBottom();
                    invalidate();
                    return insets;
                });
                requestApplyInsets();
            }
        }

        void onHostResume() {
            if (screen == Screen.GAME && tracker.hasOpenSession()) tracker.resume();
            if (DistributionConfig.selfUpdateEnabled() && pendingInstallUri != null) maybeInstallPendingUpdate();
        }

        void onHostPause() {
            tracker.pause();
        }

        boolean goHomeIfNeeded() {
            if (screen != Screen.HOME) {
                generationToken++;
                generating = false;
                if (screen == Screen.GAME && tracker.hasOpenSession() && !solved) {
                    tracker.finish(false, "home");
                    resumablePuzzle = puzzle != null;
                }
                ((MainActivity) getContext()).hideScratchpad(false);
                screen = Screen.HOME;
                selectedCell = null;
                selectedTileId = -1;
                candidateMode = false;
                invalidate();
                return true;
            }
            return false;
        }

        void resumeCurrentPuzzle() {
            if (!resumablePuzzle || puzzle == null || solved) {
                resumablePuzzle = false;
                loadPathLevel(progressLevel);
                return;
            }
            generationToken++;
            generating = false;
            selectedCell = null;
            selectedTileId = -1;
            candidateMode = false;
            screen = Screen.GAME;
            startTrackerForCurrentPuzzle();
            resumablePuzzle = false;
            invalidate();
        }

        void loadPathLevel(int newLevel) {
            final int targetLevel = Math.max(1, newLevel);
            if (generating) return;

            Puzzle cached = null;
            if (prefetchedPathLevel == targetLevel && prefetchedPathPuzzle != null) {
                cached = prefetchedPathPuzzle;
                prefetchedPathPuzzle = null;
                prefetchedPathLevel = -1;
            }
            if (cached != null) {
                activatePathPuzzle(targetLevel, cached);
                return;
            }

            final long token = ++generationToken;
            generating = true;
            invalidate();
            new Thread(() -> {
                Puzzle ready = null;
                try { ready = PuzzleGenerator.generatePath(targetLevel); }
                catch (RuntimeException ignored) { }
                final Puzzle result = ready;
                post(() -> {
                    if (token != generationToken) return;
                    generating = false;
                    if (result == null) {
                        Toast.makeText(getContext(), UiText.tr("Could not build this level — try again", "Не удалось собрать уровень — попробуй ещё раз", "Úroveň se nepodařilo vytvořit — zkus to znovu"), Toast.LENGTH_SHORT).show();
                        invalidate();
                        return;
                    }
                    activatePathPuzzle(targetLevel, result);
                });
            }, "mathcrossword-path-generator").start();
        }

        void activatePathPuzzle(int newLevel, Puzzle ready) {
            level = Math.max(1, newLevel);
            mode = GameMode.PATH;
            puzzle = ready;
            resumablePuzzle = false;
            selectedTileId = -1;
            selectedCell = null;
            solved = false;
            candidateMode = false;
            hintStage = 0;
            candidateNotes.clear();
            undoStack.clear();
            resetBoardViewport();
            screen = Screen.GAME;
            resumablePuzzle = false;
            ((MainActivity) getContext()).prepareScratchpadForPuzzle(puzzle.seed, true);
            startTrackerForCurrentPuzzle();
            invalidate();
            prefetchPathLevel(level + 1);
        }

        void prefetchPathLevel(int targetLevel) {
            targetLevel = Math.max(1, targetLevel);
            if (prefetchedPathLevel == targetLevel && prefetchedPathPuzzle != null) return;
            if (pathPrefetchRunning && pathPrefetchTarget == targetLevel) return;
            pathPrefetchRunning = true;
            pathPrefetchTarget = targetLevel;
            final int wanted = targetLevel;
            new Thread(() -> {
                Puzzle ready = null;
                try { ready = PuzzleGenerator.generatePath(wanted); }
                catch (RuntimeException ignored) { }
                final Puzzle result = ready;
                post(() -> {
                    if (pathPrefetchTarget == wanted) {
                        pathPrefetchRunning = false;
                        if (result != null) {
                            prefetchedPathPuzzle = result;
                            prefetchedPathLevel = wanted;
                        }
                    }
                });
            }, "mathcrossword-path-prefetch").start();
        }

        void loadFreePuzzle() {
            if (generating) return;
            mode = GameMode.FREE;

            final int logic = freeLogic;
            final int calc = freeCalc;
            final int size = freeSize;
            final int maxNumber = freeMaxValues[freeMaxIndex];
            final LinkedHashSet<Character> ops = new LinkedHashSet<>(freeOps);
            final SolutionStrategy solutionStrategy = strategies[freeStrategyIndex];
            final long baseSeed = System.nanoTime() ^ freeSeedRandom.nextLong();
            lastFreeSeed = baseSeed;
            final long token = ++generationToken;
            generating = true;
            invalidate();

            new Thread(() -> {
                Puzzle ready = null;
                long acceptedSeed = baseSeed;
                for (int retry = 0; retry < 3 && ready == null; retry++) {
                    long seed = PuzzleGenerator.mix64(baseSeed + retry * 0x9E3779B97F4A7C15L);
                    try {
                        ready = PuzzleGenerator.generateFree(logic, calc, size, maxNumber, ops, seed, solutionStrategy);
                        acceptedSeed = seed;
                    } catch (RuntimeException ignored) { }
                }

                final Puzzle result = ready;
                final long resultSeed = acceptedSeed;
                post(() -> {
                    if (token != generationToken) return;
                    generating = false;
                    if (result == null) {
                        Toast.makeText(getContext(), UiText.tr("Could not find a strong enough puzzle — try again", "Не нашёл достаточно сильную головоломку — нажми ещё раз", "Nepodařilo se najít dostatečně silný hlavolam — zkus to znovu"), Toast.LENGTH_SHORT).show();
                        invalidate();
                        return;
                    }
                    lastFreeSeed = resultSeed;
                    puzzle = result;
                    resumablePuzzle = false;
                    selectedTileId = -1;
                    selectedCell = null;
                    solved = false;
                    candidateMode = false;
                    candidateNotes.clear();
                    undoStack.clear();
                    screen = Screen.GAME;
                    resumablePuzzle = false;
                    ((MainActivity) getContext()).prepareScratchpadForPuzzle(puzzle.seed, true);
                    startTrackerForCurrentPuzzle();
                    invalidate();
                });
            }, "mathcrossword-generator").start();
        }

        void resetCurrentPuzzle() {
            if (puzzle == null) return;
            puzzle.placedTile.clear();
            for (Tile t : puzzle.tiles) t.used = false;
            selectedTileId = -1;
            selectedCell = null;
            solved = false;
            candidateMode = false;
            hintStage = 0;
            candidateNotes.clear();
            undoStack.clear();
            resetBoardViewport();
            ((MainActivity) getContext()).prepareScratchpadForPuzzle(puzzle.seed, true);
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(bg);
            if (screen == Screen.HOME) drawHome(canvas);
            else if (screen == Screen.LEVELS) drawLevels(canvas);
            else if (screen == Screen.FREE_SETUP) drawFreeSetup(canvas);
            else if (screen == Screen.LIBRARY) drawLibrary(canvas);
            else if (screen == Screen.ANALYSIS) drawAnalysis(canvas);
            else drawGame(canvas);
        }

        void drawHome(Canvas c) {
            float w = getWidth(), h = getHeight();
            float y = topInset + dp(82);

            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(29));
            c.drawText(UiText.tr("Math", "Математический", "Matematická"), w / 2f, y, paint);
            paint.setTextSize(dp(27));
            c.drawText(UiText.tr("Crossword", "кроссворд", "křížovka"), w / 2f, y + dp(34), paint);

            // Compact language badge. A/XX means automatic system language; tapping opens the chooser.
            homeLanguageRect.set(w - dp(82), topInset + dp(18), w - dp(18), topInset + dp(54));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(118, 255, 255, 255));
            c.drawRoundRect(homeLanguageRect, dp(12), dp(12), paint);
            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(12.5f));
            Paint.FontMetrics languageFm = paint.getFontMetrics();
            c.drawText(UiText.badge(), homeLanguageRect.centerX(),
                    homeLanguageRect.centerY() - (languageFm.ascent + languageFm.descent) / 2f, paint);

            // Keep the home screen quiet: product/update metadata lives in a small footer,
            // not as another primary action competing with play.
            float side = dp(26);
            float primaryH = dp(52);
            float secondaryH = dp(48);
            float gap = dp(8);
            float firstTop = Math.max(y + dp(68), h * 0.275f);
            homeContinueRect.set(side, firstTop, w - side, firstTop + primaryH);
            homeLevelsRect.set(side, homeContinueRect.bottom + gap, w - side, homeContinueRect.bottom + gap + secondaryH);
            homeFreeRect.set(side, homeLevelsRect.bottom + gap, w - side, homeLevelsRect.bottom + gap + secondaryH);
            homeLibraryRect.set(side, homeFreeRect.bottom + gap, w - side, homeFreeRect.bottom + gap + secondaryH);
            homeAnalysisRect.set(side, homeLibraryRect.bottom + gap, w - side, homeLibraryRect.bottom + gap + secondaryH);

            String continueLabel;
            if (generating) {
                continueLabel = UiText.tr("Generating level ", "Генерирую уровень ", "Generuji úroveň ") + progressLevel + "…";
            } else if (resumablePuzzle && puzzle != null && !solved) {
                continueLabel = mode == GameMode.PATH
                        ? UiText.tr("Continue puzzle — level ", "Продолжить задачу — уровень ", "Pokračovat v hlavolamu — úroveň ") + level
                        : UiText.tr("Continue Free Play", "Продолжить свободную игру", "Pokračovat ve volné hře");
            } else {
                continueLabel = UiText.tr("Continue — level ", "Продолжить — уровень ", "Pokračovat — úroveň ") + progressLevel;
            }
            drawBigButton(c, homeContinueRect, continueLabel, true);
            drawBigButton(c, homeLevelsRect, UiText.tr("Choose level", "Выбрать уровень", "Vybrat úroveň"), false);
            drawBigButton(c, homeFreeRect, UiText.tr("Free Play", "Свободная игра", "Volná hra"), false);
            drawBigButton(c, homeLibraryRect, UiText.tr("Solution Library", "Библиотека решений", "Knihovna řešení"), false);
            drawBigButton(c, homeAnalysisRect, UiText.tr("Play Analysis", "Анализ прохождений", "Analýza průchodů"), false);

            float footerY = h - bottomInset - dp(34);
            float footerCenterX = w / 2f;
            if (DistributionConfig.selfUpdateEnabled()) {
                homeUpdateRect.set(w - dp(62), footerY - dp(20), w - dp(18), footerY + dp(20));
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(115, 255, 255, 255));
                c.drawRoundRect(homeUpdateRect, dp(12), dp(12), paint);
                paint.setColor(updateChecking ? Color.rgb(120, 130, 121) : ink);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTypeface(android.graphics.Typeface.DEFAULT);
                paint.setTextSize(dp(23));
                Paint.FontMetrics updateFm = paint.getFontMetrics();
                c.drawText(updateChecking ? "…" : "↻", homeUpdateRect.centerX(),
                        homeUpdateRect.centerY() - (updateFm.ascent + updateFm.descent) / 2f, paint);
            } else {
                homeUpdateRect.setEmpty();
            }

            homePrivacyRect.set(dp(18), footerY - dp(42), w - dp(18), footerY - dp(16));
            paint.setColor(Color.rgb(104, 106, 102));
            paint.setTextSize(dp(11.2f));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            c.drawText(UiText.tr("Privacy", "Конфиденциальность", "Soukromí"), footerCenterX, footerY - dp(23), paint);

            paint.setColor(Color.rgb(132, 133, 129));
            paint.setTextSize(dp(11.4f));
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("v" + installedVersionName(), footerCenterX, footerY + dp(3), paint);
        }

        void drawLevels(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            float top = topInset + dp(14);
            topHomeRect.set(dp(12), top, dp(62), top + dp(48));
            drawIconButton(c, topHomeRect, "‹");

            int maxPage = maxUnlockedLevelPage();
            levelPage = Math.max(0, Math.min(levelPage, maxPage));
            int first = levelPage * 100 + 1;
            int last = first + 99;

            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(22));
            c.drawText(UiText.tr("Levels ", "Уровни ", "Úrovně ") + first + "–" + last, w / 2f, top + dp(32), paint);

            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(dp(12.5f));
            paint.setColor(Color.rgb(105, 107, 103));
            c.drawText(UiText.tr("Progress: level ", "Прогресс: уровень ", "Postup: úroveň ") + progressLevel + UiText.tr(" · replay does not reset progress", " · переигрывание прогресс не сбрасывает", " · opakování nesmaže postup"),
                    w / 2f, top + dp(57), paint);

            float side = dp(18);
            float gap = dp(5);
            float gridTop = top + dp(82);
            float cell = (w - side * 2 - gap * 9) / 10f;
            cell = Math.min(cell, dp(37));
            float gridW = cell * 10 + gap * 9;
            float left = (w - gridW) / 2f;
            for (int i = 0; i < 100; i++) {
                int row = i / 10, col = i % 10;
                float l = left + col * (cell + gap);
                float t = gridTop + row * (cell + gap);
                RectF r = levelRects[i];
                r.set(l, t, l + cell, t + cell);
                int n = first + i;
                if (n < progressLevel) paint.setColor(green);
                else if (n == progressLevel) paint.setColor(selected);
                else paint.setColor(board);
                c.drawRoundRect(r, dp(7), dp(7), paint);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(n == progressLevel ? dp(2) : dp(1));
                stroke.setColor(n == progressLevel ? accent : Color.rgb(198, 210, 199));
                c.drawRoundRect(r, dp(7), dp(7), stroke);
                paint.setColor(ink);
                paint.setTextSize(dp(n >= 100 ? 11.5f : 12.5f));
                paint.setTypeface(n == progressLevel ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
                Paint.FontMetrics fm = paint.getFontMetrics();
                c.drawText(Integer.toString(n), r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, paint);
            }

            float navTop = Math.min(h - bottomInset - dp(58), gridTop + 10 * (cell + gap) + dp(13));
            levelsPrevPageRect.set(side, navTop, w / 2f - dp(6), navTop + dp(46));
            levelsNextPageRect.set(w / 2f + dp(6), navTop, w - side, navTop + dp(46));
            drawBigButton(c, levelsPrevPageRect, levelPage > 0 ? "← 100" : "—", false);
            drawBigButton(c, levelsNextPageRect, levelPage < maxPage ? "+100 →" : UiText.tr("Next locked", "Следующие закрыты", "Další uzamčeny"), false);
        }

        int maxUnlockedLevelPage() {
            return LevelAccess.maxUnlockedPage(progressLevel);
        }

        void drawLibrary(Canvas c) {
            float w = getWidth();
            float top = topInset + dp(14);
            topHomeRect.set(dp(12), top, dp(62), top + dp(48));
            drawIconButton(c, topHomeRect, "‹");
            analysisLastTraceRect.setEmpty();

            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(23));
            c.drawText(UiText.tr("Solution Library", "Библиотека решений", "Knihovna řešení"), w / 2f, top + dp(33), paint);

            SolutionLibrary.Entry entry = SolutionLibrary.ENTRIES.get(libraryEntryIndex);
            float side = dp(22);
            float y = top + dp(82);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(dp(21));
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setColor(ink);
            y = drawWrappedText(c, entry.title, side, y, w - side * 2, dp(26));

            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(dp(15));
            paint.setColor(Color.rgb(65, 78, 68));
            y = drawWrappedText(c, entry.idea, side, y + dp(12), w - side * 2, dp(21));

            paint.setColor(ink);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(16));
            c.drawText(UiText.tr("Example", "Пример", "Příklad"), side, y + dp(22), paint);
            paint.setTypeface(android.graphics.Typeface.MONOSPACE);
            paint.setTextSize(dp(14));
            paint.setColor(Color.rgb(35, 48, 38));
            y = drawWrappedText(c, entry.example, side, y + dp(47), w - side * 2, dp(20));

            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(15.5f));
            paint.setColor(ink);
            c.drawText(UiText.tr("How to proceed", "Как действовать", "Jak postupovat"), side, y + dp(23), paint);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(dp(14));
            paint.setColor(Color.rgb(75, 84, 76));
            y = drawWrappedText(c, entry.steps, side, y + dp(47), w - side * 2, dp(19));

            float navTop = Math.min(getHeight() - bottomInset - dp(70), Math.max(y + dp(22), top + dp(610)));
            libraryPrevRect.set(side, navTop, w / 2f - dp(6), navTop + dp(50));
            libraryNextRect.set(w / 2f + dp(6), navTop, w - side, navTop + dp(50));
            drawBigButton(c, libraryPrevRect, UiText.tr("← Previous", "← Предыдущий", "← Předchozí"), false);
            drawBigButton(c, libraryNextRect, UiText.tr("Next →", "Следующий →", "Další →"), false);

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(12));
            paint.setColor(Color.rgb(105, 115, 106));
            c.drawText((libraryEntryIndex + 1) + " / " + SolutionLibrary.ENTRIES.size(),
                    w / 2f, navTop - dp(10), paint);
        }

        void drawAnalysis(Canvas c) {
            float w = getWidth();
            float top = topInset + dp(14);
            topHomeRect.set(dp(12), top, dp(62), top + dp(48));
            drawIconButton(c, topHomeRect, "‹");
            analysisExportRect.setEmpty();

            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(23));
            c.drawText(UiText.tr("Play Analysis", "Анализ прохождений", "Analýza průchodů"), w / 2f, top + dp(33), paint);

            SessionTracker.AnalysisSnapshot a = tracker.analyze();
            float side = dp(23);
            float y = top + dp(88);
            paint.setTextAlign(Paint.Align.LEFT);
            if (a.sessions == 0) {
                paint.setTypeface(android.graphics.Typeface.DEFAULT);
                paint.setTextSize(dp(16));
                paint.setColor(Color.rgb(75, 85, 77));
                drawWrappedText(c, UiText.tr("No data yet. Finish or leave a few puzzles and a short summary will appear here. The timer counts active play only; time with the app in the background is excluded.", "Пока данных нет. Заверши или покинь несколько головоломок — здесь появится краткий итог. Таймер учитывает только активное время: сворачивание приложения не считается.", "Zatím nejsou žádná data. Dokonči nebo opusť několik hlavolamů a objeví se zde stručný přehled. Časovač počítá jen aktivní hraní; čas na pozadí se nezapočítává."),
                        side, y, w - side * 2, dp(23));
                return;
            }

            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(17));
            c.drawText(UiText.tr("Summary", "Краткий итог", "Souhrn"), side, y, paint);
            y += dp(31);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(dp(15));
            paint.setColor(Color.rgb(55, 67, 58));
            c.drawText(UiText.tr("Visits: ", "Посещений: ", "Návštěv: ") + a.visits + UiText.tr("   Runs: ", "   Прохождений: ", "   Průchodů: ") + a.runs, side, y, paint); y += dp(24);
            c.drawText(UiText.tr("Solved runs: ", "Решено прохождений: ", "Vyřešených průchodů: ") + a.solvedRuns + UiText.tr("   In progress: ", "   В процессе: ", "   Probíhá: ") + a.inProgressRuns, side, y, paint); y += dp(24);
            c.drawText(UiText.tr("Average solved time: ", "Среднее время решённой: ", "Průměrný čas vyřešení: ") + formatDuration(a.avgSolvedMs), side, y, paint); y += dp(24);
            c.drawText(UiText.tr("Average events: ", "Среднее событий: ", "Průměr událostí: ") + String.format(Locale.US, "%.1f", a.avgEvents), side, y, paint); y += dp(24);
            c.drawText(UiText.tr("Time to first action: ", "До первого действия: ", "Do první akce: ") + formatDuration(a.avgFirstActionMs), side, y, paint); y += dp(24);
            c.drawText(UiText.tr("Average long pause: ", "Средняя длинная пауза: ", "Průměrná dlouhá pauza: ") + formatDuration(a.avgLongestPauseMs), side, y, paint); y += dp(24);
            c.drawText(UiText.tr("Moves: ", "Ходы: ", "Tahy: ") + a.placements + "   Undo: " + a.undoCount + UiText.tr("   Candidates: ", "   Кандидаты: ", "   Kandidáti: ") + a.candidateEdits, side, y, paint); y += dp(24);
            c.drawText(UiText.tr("Guided hints: ", "Наводящие намёки: ", "Naváděcí nápovědy: ") + a.hintCount, side, y, paint); y += dp(24);
            c.drawText(UiText.tr("Pauses: productive ", "Паузы: продуктивные ", "Pauzy: produktivní ") + a.productivePauses + UiText.tr(" · dead-end ", " · тупиковые ", " · slepé ") + a.deadEndPauses, side, y, paint); y += dp(24);
            c.drawText(UiText.tr("Hypothesis-check signals: ", "Сигналы проверки гипотез: ", "Signály ověřování hypotéz: ") + a.hypothesisEpisodes, side, y, paint); y += dp(24);
            c.drawText(UiText.tr("Rapid action cascades: ", "Быстрые каскады действий: ", "Rychlé kaskády akcí: ") + a.rapidCascades, side, y, paint); y += dp(24);
            String candidateFlowLine = UiText.tr("Candidates: cell switches ", "Кандидаты: переходы между клетками ", "Kandidáti: přechody mezi buňkami ") + a.candidateCellSwitches
                    + UiText.tr(" · revisits ", " · возвраты ", " · návraty ") + a.candidateCellRevisits;
            y = drawWrappedText(c, candidateFlowLine, side, y, w - side * 2, dp(20));
            y += dp(4);

            if (a.routeComparedSessions > 0) {
                String routeSummaryLine = UiText.tr("Routes: ", "Маршруты: ", "Trasy: ") + a.routeComparedSessions + UiText.tr(" compared · agreement ", " сравн. · согласование ", " porovn. · shoda ")
                        + String.format(Locale.US, "%.0f%%", a.avgRouteAgreementPct)
                        + UiText.tr(" · strong divergences ", " · сильных расхождений ", " · výrazné odchylky ") + a.routeStrongDivergences;
                y = drawWrappedText(c, routeSummaryLine, side, y, w - side * 2, dp(20));
                y += dp(4);
            }

            paint.setColor(ink);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(15.5f));
            c.drawText(UiText.tr("Difficulty calibration", "Калибровка сложности", "Kalibrace obtížnosti"), side, y, paint); y += dp(23);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(dp(13.2f));
            paint.setColor(Color.rgb(68, 80, 70));
            if (!a.calibrationReady) {
                int need = Math.max(0, DifficultyCalibrator.MIN_SOLVED - a.calibrationSessions);
                c.drawText(UiText.tr("More solved runs needed: ", "Нужно ещё решённых прохождений: ", "Je potřeba dalších vyřešených průchodů: ") + need, side, y, paint); y += dp(21);
            } else {
                String scope = a.calibrationGeneratorVersion > 0 ? (" · v" + a.calibrationGeneratorVersion) : UiText.tr(" · history", " · история", " · historie");
                c.drawText(UiText.tr("Prediction ±1: ", "Прогноз ±1: ", "Predikce ±1: ") + String.format(Locale.US, "%.0f%%", a.calibrationWithinOnePct) + scope, side, y, paint); y += dp(21);
                String tendency = a.calibrationMeanError > 0.35 ? UiText.tr("usually underestimates difficulty", "чаще недооценивает трудность", "častěji podhodnocuje obtížnost")
                        : (a.calibrationMeanError < -0.35 ? UiText.tr("usually overestimates difficulty", "чаще переоценивает трудность", "častěji nadhodnocuje obtížnost") : UiText.tr("usually close to observed play", "в среднем близок к прохождению", "v průměru odpovídá skutečnému průchodu"));
                c.drawText(UiText.tr("Model: ", "Модель: ", "Model: ") + tendency, side, y, paint); y += dp(21);
                if (a.lastPredictedBand > 0 && a.lastObservedBand > 0) {
                    c.drawText(UiText.tr("Latest: predicted L", "Последняя: прогноз L", "Poslední: predikce L") + a.lastPredictedBand + UiText.tr(" → observed cost ", " → стоимость ", " → pozorovaná náročnost ") + a.lastObservedBand + "/10", side, y, paint); y += dp(21);
                }
                if (Math.abs(a.recentObservedCostChangePct) >= 8.0) {
                    String sign = a.recentObservedCostChangePct > 0 ? "+" : "";
                    c.drawText(UiText.tr("Latest 10: ", "Последние 10: ", "Posledních 10: ") + sign + String.format(Locale.US, "%.0f%%", a.recentObservedCostChangePct) + UiText.tr(" vs previous", " к предыдущим", " oproti předchozím"), side, y, paint); y += dp(21);
                }
            }
            y += dp(5);
            if (a.kernelSessions > 0) {
                c.drawText(UiText.tr("Puzzles with a hypothesis kernel: ", "Задачи с ядром гипотезы: ", "Hlavolamy s jádrem hypotézy: ") + a.kernelSessions + UiText.tr(" · deep ", " · глубокие ", " · hluboké ") + a.deepKernelSessions, side, y, paint); y += dp(24);
            }
            c.drawText(UiText.tr("Resets: ", "Сбросы: ", "Restarty: ") + a.resetCount, side, y, paint); y += dp(24);
            if (a.strategyFallbacks > 0) {
                c.drawText(UiText.tr("Generator fallback: ", "Fallback генератора: ", "Fallback generátoru: ") + a.strategyFallbacks + UiText.tr(" of ", " из ", " z ") + a.sessions, side, y, paint); y += dp(24);
            }
            y += dp(10);

            if (!a.byStrategy.isEmpty()) {
                paint.setColor(ink);
                paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                paint.setTextSize(dp(17));
                c.drawText(UiText.tr("By solving structure", "По стратегиям решения", "Podle struktury řešení"), side, y, paint);
                y += dp(28);
                paint.setTypeface(android.graphics.Typeface.DEFAULT);
                paint.setTextSize(dp(13.2f));
                for (SessionTracker.StrategyStats st : a.byStrategy) {
                    if (y > getHeight() - bottomInset - dp(150)) break;
                    double undoPer = st.sessions == 0 ? 0.0 : st.undoCount / (double) st.sessions;
                    double candPer = st.sessions == 0 ? 0.0 : st.candidateEdits / (double) st.sessions;
                    double hypPer = st.sessions == 0 ? 0.0 : st.hypothesisEpisodes / (double) st.sessions;
                    String line = strategyLabel(st.strategy) + ": " + st.sessions + UiText.tr(" sessions · ", " сесс. · ", " relací · ")
                            + formatDuration(st.avgSolvedMs) + " · U "
                            + String.format(Locale.US, "%.1f", undoPer) + UiText.tr(" · H ", " · Г ", " · H ")
                            + String.format(Locale.US, "%.1f", hypPer);
                    paint.setColor(Color.rgb(63, 77, 66));
                    c.drawText(line, side, y, paint);
                    y += dp(22);
                }
                y += dp(12);
            }

            if (!a.recent.isEmpty() && y < getHeight() - bottomInset - dp(170)) {
                SessionTracker.SessionSummary last = a.recent.get(0);
                float traceTop = y - dp(18);
                paint.setColor(ink);
                paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                paint.setTextSize(dp(16));
                c.drawText(UiText.tr("Latest trajectory", "Последняя траектория", "Poslední trajektorie"), side, y, paint);
                paint.setTextAlign(Paint.Align.RIGHT);
                paint.setTypeface(android.graphics.Typeface.DEFAULT);
                paint.setTextSize(dp(12.5f));
                paint.setColor(Color.rgb(82, 100, 85));
                c.drawText(UiText.tr("details ›", "подробно ›", "podrobně ›"), w - side, y, paint);
                paint.setTextAlign(Paint.Align.LEFT);
                y += dp(24);
                paint.setTypeface(android.graphics.Typeface.DEFAULT);
                paint.setTextSize(dp(12.8f));
                paint.setColor(Color.rgb(72, 84, 74));
                String traceLine = UiText.tr("pauses +", "паузы +", "pauzy +") + last.productivePauses + "/−" + last.deadEndPauses
                        + UiText.tr(" · hypothesis checks ", " · проверки гипотез ", " · ověření hypotéz ") + last.hypothesisEpisodes
                        + (last.hintStage > 0 ? (UiText.tr(" · hint ", " · намёк ", " · nápověda ") + last.hintStage) : UiText.tr(" · no hints", " · без намёков", " · bez nápověd"));
                c.drawText(traceLine, side, y, paint);
                y += dp(21);
                if (last.candidateCellSwitches > 0 || last.candidateCellRevisits > 0) {
                    c.drawText(UiText.tr("candidates: switches ", "кандидаты: переходов ", "kandidáti: přechody ") + last.candidateCellSwitches
                            + UiText.tr(" · revisits ", " · возвратов ", " · návratů ") + last.candidateCellRevisits
                            + UiText.tr(" · max in one cell ", " · максимум в клетке ", " · maximum v buňce ") + last.maxCandidatesInOneCell, side, y, paint);
                    y += dp(21);
                }
                if (last.routeCompared) {
                    String routeLine = UiText.tr("route: agreement ", "маршрут: согласование ", "trasa: shoda ")
                            + String.format(Locale.US, "%.0f%%", last.routeAgreementPct)
                            + UiText.tr(" · opening ", " · начало ", " · začátek ") + String.format(Locale.US, "%.0f%%", last.routeEarlyAgreementPct)
                            + UiText.tr(" · order ", " · порядок ", " · pořadí ") + String.format(Locale.US, "%.0f%%", last.routeOrderAgreementPct);
                    c.drawText(routeLine, side, y, paint);
                    y += dp(21);
                }
                if (last.hidden > 0 && last.maxForcedCascade > 0) {
                    c.drawText(UiText.tr("cascade model: up to ", "модель каскада: до ", "model kaskády: až ") + last.maxForcedCascade + UiText.tr(" of ", " из ", " z ") + last.hidden + UiText.tr(" after the key deduction", " после ключевого вывода", " po klíčovém závěru"), side, y, paint);
                    y += dp(21);
                }
                if (last.kernelFamily != null && !"none".equals(last.kernelFamily)
                        && !"unprofiled".equals(last.kernelFamily)) {
                    c.drawText(UiText.tr("puzzle kernel: ", "ядро задачи: ", "jádro hlavolamu: ") + kernelFamilyLabel(last.kernelFamily), side, y, paint);
                    y += dp(21);
                }
                if (last.contextualDecoys > 0) {
                    String decoyLine = UiText.tr("contextual false candidates: ", "контекстные ложные варианты: ", "kontextové falešné možnosti: ") + last.contextualDecoys;
                    if (last.resourceConflictDecoys > 0) decoyLine += UiText.tr(" · tile conflict ", " · конфликт плиток ", " · konflikt dlaždic ") + last.resourceConflictDecoys;
                    c.drawText(decoyLine, side, y, paint);
                    y += dp(21);
                }
                if (last.branchGoodPivots > 0) {
                    c.drawText(UiText.tr("hypothesis pivots: ", "точки гипотезы: ", "body hypotézy: ") + last.branchGoodPivots
                            + UiText.tr(" · viable false branches ", " · жизнеспособных ложных веток ", " · životaschopné falešné větve ") + last.branchFalseBranches, side, y, paint);
                    y += dp(21);
                }
                if (last.reasoningFronts >= 2) {
                    c.drawText(UiText.tr("structure: ", "структура: ", "struktura: ") + last.reasoningFronts + UiText.tr(" active reasoning fronts", " рабочих фронта(ов)", " aktivních front uvažování"), side, y, paint);
                    y += dp(21);
                }
                y += dp(7);
                analysisLastTraceRect.set(side - dp(8), traceTop, w - side + dp(8), y + dp(5));
            }

            paint.setColor(ink);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(17));
            c.drawText(UiText.tr("Recent runs", "Последние прохождения", "Poslední průchody"), side, y, paint);
            y += dp(29);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(dp(13.5f));
            for (SessionTracker.SessionSummary r : a.recent) {
                if (y > getHeight() - bottomInset - dp(96)) break;
                String where = "PATH_REPLAY".equals(r.mode) ? (UiText.tr("↺ lvl ", "↺ ур.", "↺ úr. ") + r.level) : ("PATH_TEST".equals(r.mode) ? (UiText.tr("test lvl ", "тест ур.", "test úr. ") + r.level) : ("PATH".equals(r.mode) ? (UiText.tr("lvl ", "ур.", "úr. ") + r.level) : strategyLabel(r.strategy)));
                String signal = r.hypothesisEpisodes > 0 ? (UiText.tr(" · H", " · Г", " · H") + r.hypothesisEpisodes) : "";
                if (r.hintStage > 0) signal += UiText.tr(" · Ht", " · Н", " · N") + r.hintStage;
                String line = (r.solved ? "✓ " : "• ") + where
                        + UiText.tr("  L", "  Л", "  L") + r.logic + "→" + (r.ratedLogic > 0 ? r.ratedLogic : "?") + UiText.tr("/C", "/В", "/V") + r.calc
                        + "  " + formatDuration(r.activeMs)
                        + signal;
                paint.setColor(r.solved ? Color.rgb(45, 118, 59) : Color.rgb(105, 92, 72));
                c.drawText(line, side, y, paint);
                y += dp(23);
            }

            float exportBottom = getHeight() - bottomInset - dp(10);
            analysisExportRect.set(side, exportBottom - dp(44), w - side, exportBottom);
            drawToolButton(c, analysisExportRect, UiText.tr("Export research data", "Экспорт исследовательских данных", "Exportovat výzkumná data"), true, false);

            paint.setColor(Color.rgb(100, 111, 102));
            paint.setTextSize(dp(11.2f));
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText(UiText.tr("ZIP: metadata + sessions + summary · shared only after your action", "ZIP: metadata + sessions + summary · отправка только после твоего действия", "ZIP: metadata + sessions + summary · sdílení pouze po tvé akci"),
                    w / 2f, analysisExportRect.top - dp(8), paint);
        }

        void beginResearchExport() {
            SessionTracker.AnalysisSnapshot snapshot = tracker.analyze();
            if (snapshot.sessions <= 0) {
                Toast.makeText(getContext(), UiText.tr("Nothing to export yet", "Пока нечего экспортировать", "Zatím není co exportovat"), Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_TITLE, ResearchExporter.suggestedFileName());
            try {
                ((Activity) getContext()).startActivityForResult(intent, REQUEST_RESEARCH_EXPORT);
            } catch (RuntimeException ex) {
                Toast.makeText(getContext(), UiText.tr("Could not open file picker", "Не удалось открыть выбор файла", "Výběr souboru se nepodařilo otevřít"), Toast.LENGTH_SHORT).show();
            }
        }

        void onHostActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode != REQUEST_RESEARCH_EXPORT || resultCode != Activity.RESULT_OK || data == null) return;
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                ResearchExporter.Result result;
                try (java.io.OutputStream out = getContext().getContentResolver().openOutputStream(uri, "w")) {
                    if (out == null) throw new java.io.IOException("openOutputStream returned null");
                    result = ResearchExporter.write(getContext(), out);
                }
                Toast.makeText(getContext(), UiText.tr("Exported sessions: ", "Экспортировано сессий: ", "Exportované relace: ") + result.sessions, Toast.LENGTH_SHORT).show();

                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("application/zip");
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.setClipData(android.content.ClipData.newRawUri("MathCrossword research export", uri));
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                ((Activity) getContext()).startActivity(Intent.createChooser(share, UiText.tr("Share research data", "Поделиться исследовательскими данными", "Sdílet výzkumná data")));
            } catch (Exception ex) {
                Toast.makeText(getContext(), UiText.tr("Could not create research ZIP", "Не удалось собрать исследовательский ZIP", "Výzkumný ZIP se nepodařilo vytvořit"), Toast.LENGTH_LONG).show();
            }
        }

        String strategyLabel(String name) {
            try { return SolutionStrategy.valueOf(name).label; }
            catch (RuntimeException ex) { return UiText.tr("Free", "Свободная", "Volná"); }
        }

        String kernelFamilyLabel(String name) {
            if ("single-pivot".equals(name)) return UiText.tr("single pivot hypothesis", "одна опорная гипотеза", "jedna opěrná hypotéza");
            if ("two-stage".equals(name)) return UiText.tr("two-stage hypothesis", "двухступенчатая гипотеза", "dvoustupňová hypotéza");
            if ("deep-branch".equals(name)) return UiText.tr("deep false branch", "глубокая ложная ветка", "hluboká falešná větev");
            if ("multi-pivot".equals(name)) return UiText.tr("multiple hypothesis pivots", "несколько точек гипотезы", "více bodů hypotézy");
            return name == null ? "—" : name;
        }

        String formatDuration(long ms) {
            if (ms <= 0) return "—";
            long total = ms / 1000L;
            long min = total / 60L;
            long sec = total % 60L;
            if (min >= 60) return (min / 60) + UiText.tr("h ", "ч ", "h ") + (min % 60) + UiText.tr("m", "м", "m");
            return min + ":" + (sec < 10 ? "0" : "") + sec;
        }

        float drawWrappedText(Canvas c, String text, float x, float y, float maxWidth, float lineHeight) {
            String[] paragraphs = text.split("\\n", -1);
            float yy = y;
            for (String paragraph : paragraphs) {
                if (paragraph.isEmpty()) { yy += lineHeight; continue; }
                String[] words = paragraph.split(" ");
                StringBuilder line = new StringBuilder();
                for (String word : words) {
                    String test = line.length() == 0 ? word : line + " " + word;
                    if (paint.measureText(test) > maxWidth && line.length() > 0) {
                        c.drawText(line.toString(), x, yy, paint);
                        yy += lineHeight;
                        line.setLength(0);
                        line.append(word);
                    } else {
                        if (line.length() > 0) line.append(' ');
                        line.append(word);
                    }
                }
                if (line.length() > 0) {
                    c.drawText(line.toString(), x, yy, paint);
                    yy += lineHeight;
                }
            }
            return yy;
        }

        void drawBigButton(Canvas c, RectF r, String label, boolean filled) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(filled ? accent : board);
            c.drawRoundRect(r, dp(14), dp(14), paint);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(filled ? 1.6f : 1.1f));
            stroke.setColor(filled ? accent : Color.rgb(174, 176, 171));
            c.drawRoundRect(r, dp(14), dp(14), stroke);
            paint.setColor(filled ? Color.WHITE : ink);
            paint.setTypeface(filled ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(filled ? 18f : 17f));
            Paint.FontMetrics fm = paint.getFontMetrics();
            c.drawText(label, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, paint);
        }

        void drawFreeSetup(Canvas c) {
            float w = getWidth();
            float top = topInset + dp(16);
            topHomeRect.set(dp(12), top, dp(62), top + dp(48));
            drawIconButton(c, topHomeRect, "‹");

            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(24));
            c.drawText(UiText.tr("Free Play", "Свободная игра", "Volná hra"), w / 2f, top + dp(33), paint);

            float y = top + dp(76);
            y = drawTenChoiceRow(c, UiText.tr("Logic", "Логика", "Logika"), logicRects, freeLogic - 1, y);
            y = drawTenChoiceRow(c, UiText.tr("Calculation", "Вычисления", "Výpočty"), calcRects, freeCalc - 1, y + dp(4));
            y = drawChoiceRow(c, UiText.tr("Board size", "Размер поля", "Velikost pole"), new String[]{"S", "M", "L"}, sizeRects, freeSize, y + dp(6));
            y = drawChoiceRow(c, UiText.tr("Numbers up to", "Числа до", "Čísla do"), new String[]{"20", "100", "500", "1000"}, maxRects, freeMaxIndex, y + dp(6));
            y = drawChoiceRow(c, UiText.tr("Solving structure", "Стратегия решения", "Struktura řešení"), new String[]{UiText.tr("Ded.", "Дед.", "Ded."), UiText.tr("Chain", "Цепь", "Řetěz"), UiText.tr("Hyp.", "Гип.", "Hyp."), UiText.tr("Net", "Сеть", "Síť"), UiText.tr("Mixed", "Микс", "Mix")},
                    strategyRects, freeStrategyIndex, y + dp(6));

            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(16));
            c.drawText(UiText.tr("Operations", "Операции", "Operace"), dp(22), y + dp(8), paint);
            float buttonY = y + dp(24);
            String[] opLabels = {"+", "−", "×", "÷", "^"};
            char[] opChars = {'+', '-', '×', '÷', '^'};
            float gap = dp(7);
            float side = dp(22);
            float bw = (w - side * 2 - gap * 4) / 5f;
            for (int i = 0; i < 5; i++) {
                RectF r = opRects[i];
                r.set(side + i * (bw + gap), buttonY, side + i * (bw + gap) + bw, buttonY + dp(48));
                drawPill(c, r, opLabels[i], freeOps.contains(opChars[i]));
            }

            freeGenerateRect.set(dp(28), buttonY + dp(73), w - dp(28), buttonY + dp(135));
            drawBigButton(c, freeGenerateRect, generating ? UiText.tr("Generating…", "Генерирую…", "Generuji…") : UiText.tr("Generate", "Сгенерировать", "Vygenerovat"), !generating);

            paint.setColor(Color.rgb(111, 112, 108));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(dp(12.5f));
            c.drawText(UiText.tr("The solving structure changes the reasoning, not just the board shape", "Стратегия меняет структуру рассуждения, а не только форму поля", "Struktura řešení mění způsob uvažování, ne jen tvar pole"), w / 2f,
                    freeGenerateRect.bottom + dp(25), paint);
            c.drawText(UiText.tr("Logic and calculation remain independent scales", "Логика и вычисления остаются независимыми шкалами", "Logika a výpočty zůstávají nezávislé škály"), w / 2f,
                    freeGenerateRect.bottom + dp(44), paint);
        }

        float drawTenChoiceRow(Canvas c, String title, RectF[] rects, int selectedIndex, float y) {
            float w = getWidth();
            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(16));
            c.drawText(title, dp(22), y + dp(18), paint);

            float side = dp(22);
            float gap = dp(7);
            float top = y + dp(28);
            float bw = (w - side * 2 - gap * 4) / 5f;
            for (int i = 0; i < 10; i++) {
                int row = i / 5;
                int col = i % 5;
                RectF r = rects[i];
                float yy = top + row * dp(48);
                r.set(side + col * (bw + gap), yy, side + col * (bw + gap) + bw, yy + dp(40));
                drawPill(c, r, Integer.toString(i + 1), i == selectedIndex);
            }
            return top + dp(92);
        }

        float drawChoiceRow(Canvas c, String title, String[] labels, RectF[] rects, int selectedIndex, float y) {
            float w = getWidth();
            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(16));
            c.drawText(title, dp(22), y, paint);

            float top = y + dp(16);
            float gap = dp(8);
            float side = dp(22);
            float bw = (w - side * 2 - gap * (labels.length - 1)) / labels.length;
            for (int i = 0; i < labels.length; i++) {
                RectF r = rects[i];
                r.set(side + i * (bw + gap), top, side + i * (bw + gap) + bw, top + dp(46));
                drawPill(c, r, labels[i], i == selectedIndex);
            }
            return top + dp(58);
        }

        void drawPill(Canvas c, RectF r, String label, boolean on) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(on ? green : board);
            c.drawRoundRect(r, dp(10), dp(10), paint);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(on ? 1.6f : 1.0f));
            stroke.setColor(on ? accent : Color.rgb(174, 176, 171));
            c.drawRoundRect(r, dp(10), dp(10), stroke);
            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(on ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            paint.setTextSize(dp(14));
            Paint.FontMetrics fm = paint.getFontMetrics();
            c.drawText(label, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, paint);
        }

        static final class BankMetrics {
            final float tileW, tileH, gap, textSize;
            BankMetrics(float tileW, float tileH, float gap, float textSize) {
                this.tileW = tileW;
                this.tileH = tileH;
                this.gap = gap;
                this.textSize = textSize;
            }
        }

        BankMetrics bankMetrics(int tileCount) {
            // Keep the board visually dominant. The bank gets denser as it grows,
            // but never becomes so small that it is awkward to tap on a phone.
            if (tileCount <= 10) return new BankMetrics(dp(58), dp(50), dp(8), dp(22));
            if (tileCount <= 18) return new BankMetrics(dp(54), dp(48), dp(7), dp(21));
            return new BankMetrics(dp(52), dp(48), dp(7), dp(20));
        }

        void drawGame(Canvas canvas) {
            if (puzzle == null) return;
            float w = getWidth(), h = getHeight();

            // The solution workbench is a bottom drawer. It can be resized continuously or
            // collapsed to a thin handle so the board can occupy almost the whole screen.
            float drawerMin = dp(28) + bottomInset;
            float drawerCompact = Math.min(h * 0.34f, dp(250) + bottomInset);
            float drawerMax = Math.min(h * 0.58f, dp(430) + bottomInset);
            if (candidateDrawerHeight < 0f) {
                candidateDrawerHeight = drawerCompact;
                lastExpandedDrawerHeight = drawerCompact;
            }
            candidateDrawerHeight = Math.max(drawerMin, Math.min(drawerMax, candidateDrawerHeight));
            if (candidateDrawerHeight > drawerMin + dp(10)) lastExpandedDrawerHeight = candidateDrawerHeight;
            // Completion gets its own reserved bottom sheet. Never overlay the board.
            float solvedDrawerHeight = dp(178) + bottomInset;
            float scratchpadHeight = Math.max(drawerMin, scratchpadReservedHeight);
            float effectiveDrawerHeight = solved ? solvedDrawerHeight
                    : (scratchpadOverlayOpen ? scratchpadHeight : (focusMode ? drawerMin : candidateDrawerHeight));

            float headerH = focusMode ? 0f : dp(46);
            float topH = topInset + headerH;
            float drawerTop = h - effectiveDrawerHeight;

            if (!focusMode) drawTopBar(canvas, w, topInset, headerH);
            else drawFocusHandle(canvas, w, topInset);

            int cols = puzzle.maxX - puzzle.minX + 1;
            int rows = puzzle.maxY - puzzle.minY + 1;
            float availW = Math.max(dp(40), w - dp(12));
            // Finished puzzles get a real breathing gap above the completion sheet.
            // This is reserved board space, not an overlay, so the last crossword tile
            // never visually sticks to the bottom controls.
            float boardBottomGap = solved ? dp(38) : dp(10);
            float availH = Math.max(dp(60), drawerTop - topH - boardBottomGap);
            boardViewportTop = topH;
            boardViewportBottom = drawerTop;

            float fitCell = Math.min(Math.min(availW / cols, availH / rows), dp(62));
            fitCell = Math.max(dp(1), fitCell);
            boardZoom = Math.max(1f, Math.min(2.65f, boardZoom));
            cellSize = fitCell * boardZoom;
            float gridW = cols * cellSize;
            float gridH = rows * cellSize;

            // Clamp pan so an enlarged board can move, but can never be lost off-screen.
            float maxPanX = Math.max(0f, (gridW - availW) / 2f + dp(18));
            float maxPanY = Math.max(0f, (gridH - availH) / 2f + dp(18));
            boardPanX = Math.max(-maxPanX, Math.min(maxPanX, boardPanX));
            boardPanY = Math.max(-maxPanY, Math.min(maxPanY, boardPanY));

            originX = (w - gridW) / 2f - puzzle.minX * cellSize + boardPanX;
            originY = topH + (availH - gridH) / 2f - puzzle.minY * cellSize + boardPanY;

            Map<Pos, Integer> status = equationStatus();
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Math.max(dp(1.0f), cellSize * 0.035f));
            stroke.setColor(ink);

            List<Map.Entry<Pos, Cell>> entries = new ArrayList<>(puzzle.cells.entrySet());
            entries.sort(Comparator.comparingInt((Map.Entry<Pos, Cell> e) -> e.getKey().y).thenComparingInt(e -> e.getKey().x));
            for (Map.Entry<Pos, Cell> e : entries) drawCell(canvas, e.getKey(), e.getValue(), status.getOrDefault(e.getKey(), 0));

            // Draw handwritten candidate notes in a dedicated top layer after the whole board.
            // This guarantees that notes from non-selected cells are never replaced by a
            // summary marker or covered by later board drawing.
            drawAllCandidateNotesOverlay(canvas);
            drawScratchpadCellLabelsOverlay(canvas);
            drawLocalFocusOverlay(canvas);

            if (solved) drawSolvedBanner(canvas, w, h);
            else if (!scratchpadOverlayOpen) drawCandidateDrawer(canvas, drawerTop, effectiveDrawerHeight, w, h, drawerMin, drawerMax);
        }

        void drawTopBar(Canvas c, float w, float insetTop, float headerH) {
            float top = insetTop;
            float centerY = top + headerH / 2f;
            topHomeRect.set(dp(8), top + dp(3), dp(48), top + headerH - dp(3));
            menuRect.set(w - dp(48), top + dp(3), w - dp(8), top + headerH - dp(3));
            resetRect.setEmpty();
            drawIconButton(c, topHomeRect, "⌂");
            drawIconButton(c, menuRect, "⋮");

            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(17));
            Paint.FontMetrics fm = paint.getFontMetrics();
            float ty = centerY - (fm.ascent + fm.descent) / 2f;
            c.drawText(mode == GameMode.PATH ? UiText.tr("Level ", "Уровень ", "Úroveň ") + level
                    : puzzle.solutionStrategy.label + UiText.tr(" · L", " · Л", " · L") + puzzle.displayLogicLevel
                    + UiText.tr("/C", "/В", "/V") + puzzle.displayCalcLevel,
                    w / 2f, ty, paint);

            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.argb(18, 0, 0, 0));
            c.drawLine(0, top + headerH - 1, w, top + headerH - 1, paint);
        }

        void drawFocusHandle(Canvas c, float w, float insetTop) {
            focusMenuRect.set(w - dp(43), insetTop + dp(4), w - dp(7), insetTop + dp(38));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(115, 255, 255, 255));
            c.drawRoundRect(focusMenuRect, dp(10), dp(10), paint);
            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(21));
            Paint.FontMetrics fm = paint.getFontMetrics();
            c.drawText("⋮", focusMenuRect.centerX(), focusMenuRect.centerY() - (fm.ascent + fm.descent) / 2f, paint);
        }

        void drawCandidateDrawer(Canvas c, float top, float height, float w, float h, float minH, float maxH) {
            // A small centered hit target keeps resize discoverable without stealing taps
            // from Candidates/Scratchpad directly underneath it.
            drawerHandleRect.set(w / 2f - dp(36), Math.max(0, top),
                    w / 2f + dp(36), Math.min(h - bottomInset, top + dp(22)));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(250, Color.red(soft), Color.green(soft), Color.blue(soft)));
            RectF surface = new RectF(0, top, w, h + dp(24));
            c.drawRoundRect(surface, dp(18), dp(18), paint);

            paint.setColor(Color.rgb(158, 159, 155));
            RectF grip = new RectF(w / 2f - dp(18), top + dp(8), w / 2f + dp(18), top + dp(10));
            c.drawRoundRect(grip, dp(2), dp(2), paint);

            bankHits.clear();
            undoRect.setEmpty(); candidateRect.setEmpty(); scratchpadRect.setEmpty(); hintRect.setEmpty();
            if (height <= minH + dp(6) || focusMode) return;

            float contentTop = top + dp(24);
            drawGameTools(c, contentTop, w);
            float bankTop = contentTop + dp(44);
            float expansion = Math.max(0f, Math.min(1f, (height - minH) / Math.max(dp(1), maxH - minH)));
            drawBank(c, bankTop, w, h - bottomInset, expansion);
        }

        void showGameMenu() {
            if (puzzle == null) return;
            String info = UiText.format(
                    "Logic %d (%.1f) · calculation %d (%.1f)\n%s · hidden cells: %d\nVersion %s (%d)",
                    "Логика %d (%.1f) · вычисления %d (%.1f)\n%s · скрыто клеток: %d\nВерсия %s (%d)",
                    "Logika %d (%.1f) · výpočty %d (%.1f)\n%s · skrytých buněk: %d\nVerze %s (%d)",
                    puzzle.displayLogicLevel, puzzle.logicScore, puzzle.displayCalcLevel, puzzle.calcScore,
                    puzzle.solutionStrategy.label, puzzle.hidden.size(), installedVersionName(), installedVersionCode());
            String focusLabel = focusMode ? UiText.tr("Show panels", "Показать панели", "Zobrazit panely") : UiText.tr("Focus mode", "Режим фокуса", "Režim soustředění");
            boolean drawerHidden = candidateDrawerHeight <= dp(40) + bottomInset;
            String drawerLabel = drawerHidden ? UiText.tr("Show workbench", "Показать рабочую панель", "Zobrazit pracovní panel") : UiText.tr("Hide workbench", "Скрыть рабочую панель", "Skrýt pracovní panel");
            new AlertDialog.Builder(getContext())
                    .setTitle(mode == GameMode.PATH ? UiText.tr("Level ", "Уровень ", "Úroveň ") + level : UiText.tr("Puzzle", "Головоломка", "Hlavolam"))
                    .setMessage(info)
                    .setItems(new String[]{focusLabel, drawerLabel, UiText.tr("Restart", "Перезапустить", "Restartovat"), UiText.tr("Close", "Закрыть", "Zavřít")}, (dialog, which) -> {
                        if (which == 0) {
                            if (!focusMode && candidateDrawerHeight > dp(40) + bottomInset) lastExpandedDrawerHeight = candidateDrawerHeight;
                            focusMode = !focusMode;
                            if (!focusMode && candidateDrawerHeight <= dp(40) + bottomInset) {
                                candidateDrawerHeight = lastExpandedDrawerHeight > dp(40) + bottomInset
                                        ? lastExpandedDrawerHeight : dp(220) + bottomInset;
                            }
                            tracker.event("focus_mode", -1, -1, focusMode ? 1 : -1, null);
                            invalidate();
                        } else if (which == 1) {
                            if (!drawerHidden) lastExpandedDrawerHeight = candidateDrawerHeight;
                            candidateDrawerHeight = drawerHidden
                                    ? (lastExpandedDrawerHeight > dp(40) + bottomInset ? lastExpandedDrawerHeight : dp(220) + bottomInset)
                                    : dp(28) + bottomInset;
                            tracker.event("candidate_drawer", -1, -1, drawerHidden ? 1 : 0, "menu");
                            invalidate();
                        } else if (which == 2) {
                            restartCurrentGame();
                        }
                    }).show();
        }

        void restartCurrentGame() {
            if (puzzle == null) return;
            tracker.event("reset", -1, -1, 0, null);
            if (tracker.hasOpenSession()) tracker.finish(false, "reset");
            if (mode == GameMode.PATH) loadPathLevel(level);
            else {
                resetCurrentPuzzle();
                resumablePuzzle = false;
                startTrackerForCurrentPuzzle();
            }
        }

        void startTrackerForCurrentPuzzle() {
            String sessionMode = mode == GameMode.PATH ? LevelAccess.sessionMode(level, progressLevel) : "FREE";
            int sessionLevel = mode == GameMode.PATH ? level : 0;
            tracker.start(sessionMode, sessionLevel, puzzle.seed, puzzle.displayLogicLevel, puzzle.displayCalcLevel, puzzle.logicScore, puzzle.calcScore,
                    puzzle.solutionStrategy.name(), puzzle.hidden.size(), puzzle.equations.size(),
                    puzzle.ratedDisplayLogic, puzzle.reasoningSteps, puzzle.reasoningDepth,
                    puzzle.basicForced, puzzle.basicRemaining, puzzle.maxForcedCascade,
                    puzzle.maxResolvedAfterOneCell, puzzle.maxResolvedFractionAfterOneCell,
                    puzzle.vulnerableSingleCells, puzzle.maxResolvedAfterOneEquation, puzzle.maxResolvedFractionAfterOneEquation,
                    puzzle.generatorVersion, puzzle.generationStage, puzzle.strategyTargetMatched, puzzle.generationStrategy.name(),
                    puzzle.generatorConstructor, puzzle.generatorFamily, puzzle.deceptiveDecoyCount, puzzle.deceptiveDecoySupportMax,
                    puzzle.contextualDecoyCount, puzzle.resourceConflictDecoyCount, puzzle.contextualDecoyConstraintSupportMax,
                    puzzle.contextualDecoyDepthMax, puzzle.contextualDecoyInformationGainMax,
                    puzzle.branchPivotCount, puzzle.branchGoodPivotCount, puzzle.branchSeriousFalseBranches,
                    puzzle.branchDepth2RefutableBranches, puzzle.branchDepth2SurvivingBranches,
                    puzzle.branchMaxWidth, puzzle.branchMaxInformationGain,
                    puzzle.reasoningFronts, puzzle.reasoningFrontBalance, puzzle.reasoningLargestFrontFraction,
                    puzzle.reasoningFrontBottleneckDegree,
                    puzzle.contradictionKernel, puzzle.contradictionKernelAddedDecoy, puzzle.contradictionKernelDepth,
                    puzzle.contradictionKernelFamily, puzzle.contradictionKernelBranches, puzzle.contradictionKernelPivots,
                    puzzle.contradictionKernelDepth2Branches, puzzle.contradictionKernelDepth3Branches,
                    puzzle.contradictionKernelDeepBranches, puzzle.contradictionKernelMaxRemaining,
                    puzzle.generationStageTimings, puzzle.generationMillis, puzzle.generationAttempts,
                    puzzle.generationRejects, puzzle.generationRejectSummary, GraphAnalyzer.analyze(puzzle));
            tracker.setModelRoute(HumanRouteComparator.modelRoute(puzzle));
        }

        void drawIconButton(Canvas c, RectF r, String text) {
            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(dp(27));
            Paint.FontMetrics fm = paint.getFontMetrics();
            c.drawText(text, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, paint);
        }

        void drawGameTools(Canvas c, float y, float w) {
            float gap = dp(7);
            float h = dp(36);
            float each = dp(42);
            float total = each * 4 + gap * 3;
            float x = (w - total) / 2f;

            undoRect.set(x, y, x + each, y + h);
            candidateRect.set(undoRect.right + gap, y, undoRect.right + gap + each, y + h);
            scratchpadRect.set(candidateRect.right + gap, y, candidateRect.right + gap + each, y + h);
            hintRect.set(scratchpadRect.right + gap, y, scratchpadRect.right + gap + each, y + h);

            // Compact command strip; the bank remains the visual focus.
            drawToolButton(c, undoRect, "↶", !undoStack.isEmpty(), false);
            drawToolButton(c, candidateRect, "✎", true, candidateMode);
            drawToolButton(c, scratchpadRect, "▤", true, false);
            drawToolButton(c, hintRect, "?", true, false);
        }

        void drawToolButton(Canvas c, RectF r, String label, boolean enabled, boolean active) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(active ? selected : (enabled ? board : Color.rgb(239, 239, 236)));
            c.drawRoundRect(r, dp(12), dp(12), paint);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(active ? 1.8f : 1.0f));
            stroke.setColor(active ? accent : (enabled ? Color.rgb(174, 176, 171) : Color.rgb(205, 206, 202)));
            c.drawRoundRect(r, dp(12), dp(12), stroke);
            paint.setColor(enabled ? ink : Color.rgb(160, 168, 160));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(active ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            paint.setTextSize(dp(label.length() <= 2 ? 18f : 13.2f));
            Paint.FontMetrics fm = paint.getFontMetrics();
            c.drawText(label, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, paint);
        }

        void drawCell(Canvas c, Pos pos, Cell cell, int status) {
            float left = originX + pos.x * cellSize;
            float top = originY + pos.y * cellSize;
            RectF r = new RectF(left, top, left + cellSize, top + cellSize);

            int fill = board;
            if (status == 1) fill = green;
            if (status == 2) fill = red;
            boolean cellSelected = selectedCell != null && selectedCell.equals(pos);
            if (cellSelected) fill = selected;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fill);
            c.drawRect(r, paint);
            c.drawRect(r, stroke);
            if (cellSelected) {
                stroke.setColor(accent);
                stroke.setStrokeWidth(Math.max(dp(1.8f), cellSize * 0.045f));
                c.drawRect(r, stroke);
                stroke.setColor(ink);
                stroke.setStrokeWidth(Math.max(dp(1.0f), cellSize * 0.035f));
            }

            String text = "";
            if (cell.kind == Kind.NUMBER) {
                if (!puzzle.hidden.contains(pos)) text = Integer.toString(cell.number);
                else {
                    Integer tileId = puzzle.placedTile.get(pos);
                    if (tileId != null) {
                        Tile t = tileById(tileId);
                        if (t != null) text = Integer.toString(t.value);
                    }
                }
            } else text = String.valueOf(cell.symbol);

            if (!text.isEmpty()) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ink);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTypeface(cell.kind == Kind.NUMBER ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
                paint.setTextSize(Math.min(cellSize * 0.46f, dp(25)));
                Paint.FontMetrics fm = paint.getFontMetrics();
                c.drawText(text, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, paint);
            }
        }

        void drawAllCandidateNotesOverlay(Canvas c) {
            if (puzzle == null || candidateNotes.isEmpty()) return;
            for (Map.Entry<Pos, LinkedHashSet<Integer>> e : candidateNotes.entrySet()) {
                Pos pos = e.getKey();
                LinkedHashSet<Integer> notes = e.getValue();
                if (notes == null || notes.isEmpty()) continue;
                if (!puzzle.hidden.contains(pos) || puzzle.placedTile.containsKey(pos)) continue;
                float left = originX + pos.x * cellSize;
                float top = originY + pos.y * cellSize;
                RectF r = new RectF(left, top, left + cellSize, top + cellSize);
                drawCandidateNotes(c, r, notes, selectedCell != null && selectedCell.equals(pos));
            }
        }

        void drawScratchpadCellLabelsOverlay(Canvas c) {
            if (puzzle == null || scratchpadCellLabels.isEmpty()) return;
            boolean scratchpadActive = scratchpadOverlayOpen;
            for (Map.Entry<Pos, String> entry : scratchpadCellLabels.entrySet()) {
                Pos pos = entry.getKey();
                if (!puzzle.hidden.contains(pos)) continue;
                float left = originX + pos.x * cellSize;
                float top = originY + pos.y * cellSize;
                float inset = Math.max(dp(1.2f), cellSize * 0.035f);
                float badgeSize = scratchpadActive
                        ? Math.min(dp(17f), Math.max(dp(11f), cellSize * 0.28f))
                        : Math.min(dp(15f), Math.max(dp(10f), cellSize * 0.24f));
                RectF badge = new RectF(left + inset, top + inset, left + inset + badgeSize, top + inset + badgeSize);

                // Keep the cell-to-note link visible after the sheet is closed, but make it
                // deliberately quieter so the normal crossword remains visually dominant.
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(scratchpadActive ? 238 : 112,
                        Color.red(selected), Color.green(selected), Color.blue(selected)));
                c.drawRoundRect(badge, dp(4), dp(4), paint);
                paint.setColor(Color.argb(scratchpadActive ? 255 : 165,
                        Color.red(accent), Color.green(accent), Color.blue(accent)));
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                paint.setTextSize(Math.min(dp(scratchpadActive ? 10.5f : 9.5f), badgeSize * 0.72f));
                Paint.FontMetrics fm = paint.getFontMetrics();
                c.drawText(entry.getValue(), badge.centerX(), badge.centerY() - (fm.ascent + fm.descent) / 2f, paint);
            }
        }

        void drawCandidateNotes(Canvas c, RectF r, Set<Integer> notes, boolean focused) {
            // Candidate notes are never intentionally hidden. The layout adapts to both
            // note count and digit width so three-digit values do not disappear under borders.
            List<Integer> vals = new ArrayList<>(notes);
            Collections.sort(vals);
            int n = vals.size();
            if (n == 0) return;
            int maxDigits = 1;
            for (Integer v : vals) maxDigits = Math.max(maxDigits, Integer.toString(Math.abs(v)).length());

            int cols;
            if (n == 1) cols = 1;
            else if (n <= 4) cols = 2;
            else if (maxDigits >= 3) cols = 2;
            else if (maxDigits >= 2 && n <= 6) cols = 2;
            else cols = 3;
            int rows = (int) Math.ceil(n / (double) cols);

            float pad = Math.max(dp(1.3f), cellSize * 0.045f);
            float usableW = Math.max(dp(2), r.width() - pad * 2);
            float usableH = Math.max(dp(2), r.height() - pad * 2);
            float colW = usableW / cols;
            float rowH = usableH / rows;

            float target = Math.min(dp(focused ? 13f : 11.5f), Math.min(cellSize * (focused ? 0.25f : 0.22f), rowH * 0.66f));
            target = Math.max(dp(4.7f), target);
            paint.setTypeface(focused ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(focused ? Color.rgb(51, 72, 56) : Color.rgb(73, 92, 77));

            // Find one font size that fits the widest note into every column.
            float textSize = target;
            paint.setTextSize(textSize);
            float widest = 0f;
            for (Integer v : vals) widest = Math.max(widest, paint.measureText(Integer.toString(v)));
            float maxAllowed = Math.max(dp(2), colW * 0.88f);
            if (widest > maxAllowed) textSize *= maxAllowed / widest;
            textSize = Math.max(dp(4.5f), textSize);
            paint.setTextSize(textSize);
            Paint.FontMetrics fm = paint.getFontMetrics();

            for (int i = 0; i < n; i++) {
                int row = i / cols, col = i % cols;
                float cx = r.left + pad + colW * (col + 0.5f);
                float cy = r.top + pad + rowH * (row + 0.5f) - (fm.ascent + fm.descent) / 2f;
                c.drawText(Integer.toString(vals.get(i)), cx, cy, paint);
            }
        }

        void resetBoardViewport() {
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

        String ensureSelectedScratchpadCellLabel() {
            if (puzzle == null || selectedCell == null || !puzzle.hidden.contains(selectedCell)) return null;
            String label = scratchpadCellLabels.get(selectedCell);
            if (label == null) {
                int index = scratchpadNextLabel++;
                if (index < 26) label = Character.toString((char) ('A' + index));
                else label = Character.toString((char) ('A' + (index % 26))) + (index / 26 + 1);
                scratchpadCellLabels.put(selectedCell, label);
            }
            invalidate();
            return label;
        }

        void clearScratchpadCellLabels() {
            scratchpadCellLabels.clear();
            scratchpadNextLabel = 0;
            invalidate();
        }

        float pointerSpacing(MotionEvent event) {
            if (event.getPointerCount() < 2) return 0f;
            float dx = event.getX(0) - event.getX(1);
            float dy = event.getY(0) - event.getY(1);
            return (float) Math.hypot(dx, dy);
        }

        void scheduleBoardLongPress(final Pos pos) {
            cancelBoardLongPress();
            longPressPos = pos;
            boardLongPressRunnable = () -> {
                if (!boardGestureMoved && !pinching && longPressPos != null && longPressPos.equals(pos)) {
                    toggleLocalFocus(pos);
                    boardLongPressTriggered = true;
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    invalidate();
                }
            };
            postDelayed(boardLongPressRunnable, 430L);
        }

        void cancelBoardLongPress() {
            if (boardLongPressRunnable != null) removeCallbacks(boardLongPressRunnable);
            boardLongPressRunnable = null;
            longPressPos = null;
        }

        void toggleLocalFocus(Pos pos) {
            if (localFocusCell != null && localFocusCell.equals(pos)) {
                clearLocalFocus();
                tracker.event("local_focus", pos.x, pos.y, 0, "off");
                return;
            }
            localFocusCell = pos;
            localFocusPositions.clear();
            if (puzzle != null) {
                for (Equation e : puzzle.equations) {
                    Pos[] line = {e.a, e.op, e.b, e.eq, e.c};
                    boolean contains = false;
                    for (Pos q : line) if (q.equals(pos)) { contains = true; break; }
                    if (contains) Collections.addAll(localFocusPositions, line);
                }
            }
            if (localFocusPositions.isEmpty()) localFocusPositions.add(pos);
            tracker.event("local_focus", pos.x, pos.y, localFocusPositions.size(), "on");
        }

        void clearLocalFocus() {
            localFocusCell = null;
            localFocusPositions.clear();
        }

        void drawLocalFocusOverlay(Canvas c) {
            if (localFocusCell == null || puzzle == null) return;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(128, Color.red(bg), Color.green(bg), Color.blue(bg)));
            for (Pos pos : puzzle.cells.keySet()) {
                if (localFocusPositions.contains(pos)) continue;
                float left = originX + pos.x * cellSize;
                float top = originY + pos.y * cellSize;
                c.drawRect(left, top, left + cellSize, top + cellSize, paint);
            }
            float left = originX + localFocusCell.x * cellSize;
            float top = originY + localFocusCell.y * cellSize;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Math.max(dp(2f), cellSize * 0.045f));
            stroke.setColor(accent);
            c.drawRect(left, top, left + cellSize, top + cellSize, stroke);
        }

        Map<Pos, Integer> equationStatus() {
            Map<Pos, Integer> out = new HashMap<>();
            // On hard logic levels, instant red/green feedback becomes a brute-force
            // oracle. Wait until every crossword cell is filled before revealing it.
            if (puzzle.logicLevel >= 4 && puzzle.placedTile.size() < puzzle.hidden.size()) return out;
            for (Equation e : puzzle.equations) {
                Integer a = numberAt(e.a), b = numberAt(e.b), cc = numberAt(e.c);
                if (a == null || b == null || cc == null) continue;
                boolean ok = PuzzleGenerator.eval(a, e.operator, b) == cc;
                int s = ok ? 1 : 2;
                for (Pos q : new Pos[]{e.a, e.op, e.b, e.eq, e.c}) {
                    int old = out.getOrDefault(q, 0);
                    if (s == 2 || old == 0) out.put(q, s);
                }
            }
            return out;
        }

        Integer numberAt(Pos pos) {
            Cell cell = puzzle.cells.get(pos);
            if (cell == null || cell.kind != Kind.NUMBER) return null;
            if (!puzzle.hidden.contains(pos)) return cell.number;
            Integer tileId = puzzle.placedTile.get(pos);
            if (tileId == null) return null;
            Tile t = tileById(tileId);
            return t == null ? null : t.value;
        }

        Tile tileById(int id) {
            for (Tile t : puzzle.tiles) if (t.id == id) return t;
            return null;
        }

        void drawBank(Canvas c, float startY, float w, float bottomLimit, float expansion) {
            bankHits.clear();
            List<Tile> visible = new ArrayList<>();
            for (Tile t : puzzle.tiles) if (!t.used) visible.add(t);

            BankMetrics base = bankMetrics(visible.size());
            float tileW = base.tileW + dp(8) * expansion;
            float tileH = base.tileH + dp(7) * expansion;
            float gap = base.gap;
            float textSize = base.textSize + dp(2) * expansion;
            int perRow = Math.max(3, (int) ((w - dp(24) + gap) / (tileW + gap)));
            int rowCount = Math.max(1, (int) Math.ceil(visible.size() / (double) perRow));
            float totalH = rowCount * tileH + Math.max(0, rowCount - 1) * gap;
            float available = Math.max(dp(20), bottomLimit - startY - dp(8));
            if (totalH > available) {
                float scale = Math.max(0.72f, available / totalH);
                tileH *= scale;
                textSize *= Math.max(0.80f, scale);
            }

            Set<Integer> selectedNotes = Collections.emptySet();
            if (selectedCell != null) {
                LinkedHashSet<Integer> set = candidateNotes.get(selectedCell);
                if (set != null) selectedNotes = set;
            }

            int index = 0;
            for (int row = 0; row < rowCount; row++) {
                int remaining = visible.size() - index;
                int count = Math.min(perRow, remaining);
                float rowW = count * tileW + Math.max(0, count - 1) * gap;
                float x = (w - rowW) / 2f;
                for (int col = 0; col < count; col++) {
                    Tile t = visible.get(index++);
                    RectF r = new RectF(x, startY + row * (tileH + gap), x + tileW, startY + row * (tileH + gap) + tileH);
                    bankHits.add(new BankHit(r, t.id));
                    boolean noted = candidateMode && selectedCell != null && selectedNotes.contains(t.value);
                    paint.setStyle(Paint.Style.FILL);
                    if (t.id == selectedTileId) paint.setColor(selected);
                    else if (noted) paint.setColor(Color.rgb(245, 248, 244));
                    else paint.setColor(board);
                    c.drawRoundRect(r, dp(5), dp(5), paint);
                    stroke.setColor(noted ? Color.rgb(104, 130, 110) : ink);
                    stroke.setStrokeWidth(dp(noted ? 2.0f : 1.8f));
                    c.drawRoundRect(r, dp(5), dp(5), stroke);

                    paint.setColor(ink);
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    paint.setTextSize(textSize);
                    String label = Integer.toString(t.value);
                    float maxW = r.width() - dp(8);
                    float measured = paint.measureText(label);
                    if (measured > maxW) paint.setTextSize(textSize * maxW / measured);
                    Paint.FontMetrics fm = paint.getFontMetrics();
                    c.drawText(label, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, paint);
                    x += tileW + gap;
                }
            }
        }

        void drawSolvedBanner(Canvas c, float w, float h) {
            // Completion stays below the board; reflection is optional and secondary.
            float sheetTop = h - bottomInset - dp(178);
            drawerHandleRect.setEmpty();
            bankHits.clear();
            undoRect.setEmpty(); candidateRect.setEmpty(); hintRect.setEmpty();

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(250, Color.red(soft), Color.green(soft), Color.blue(soft)));
            c.drawRect(0, sheetTop, w, h, paint);
            paint.setColor(Color.argb(22, 0, 0, 0));
            c.drawRect(0, sheetTop, w, sheetTop + dp(1), paint);

            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(16.5f));
            c.drawText(UiText.tr("Solved ✓", "Готово ✓", "Hotovo ✓"), w / 2f, sheetTop + dp(34), paint);

            float side = dp(38);
            solvedInsightRect.set(side, sheetTop + dp(46), w - side, sheetTop + dp(88));
            paint.setColor(accent);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(dp(13.5f));
            c.drawText(UiText.tr("How you solved it  →", "Как ты решил  →", "Jak jsi řešil  →"),
                    w / 2f, sheetTop + dp(72), paint);

            nextLevelRect.set(side, sheetTop + dp(98), w - side, h - bottomInset - dp(22));
            paint.setColor(accent);
            c.drawRoundRect(nextLevelRect, dp(13), dp(13), paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(dp(17));
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            String labelText = generating ? UiText.tr("Generating…", "Генерирую…", "Generuji…")
                    : (mode == GameMode.PATH ? UiText.tr("Next level  →", "Следующий уровень  →", "Další úroveň  →") : UiText.tr("New puzzle  →", "Новая головоломка  →", "Nový hlavolam  →"));
            Paint.FontMetrics fm = paint.getFontMetrics();
            c.drawText(labelText, w / 2f, nextLevelRect.centerY() - (fm.ascent + fm.descent) / 2f, paint);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX(), y = event.getY();
            if (screen == Screen.GAME) {
                // Drawer gesture has priority over board gestures.
                if (!solved && event.getAction() == MotionEvent.ACTION_DOWN && drawerHandleRect.contains(x, y)) {
                    cancelBoardLongPress();
                    draggingCandidateDrawer = true;
                    drawerDragStartY = y;
                    drawerDragStartHeight = candidateDrawerHeight;
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE && draggingCandidateDrawer) {
                    float minH = dp(28) + bottomInset;
                    float maxH = Math.min(getHeight() * 0.58f, dp(430) + bottomInset);
                    candidateDrawerHeight = Math.max(minH, Math.min(maxH, drawerDragStartHeight + (drawerDragStartY - y)));
                    focusMode = false;
                    invalidate();
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP && draggingCandidateDrawer) {
                    draggingCandidateDrawer = false;
                    float moved = Math.abs(y - drawerDragStartY);
                    float minH = dp(28) + bottomInset;
                    float compactH = Math.min(getHeight() * 0.34f, dp(250) + bottomInset);
                    float maxH = Math.min(getHeight() * 0.58f, dp(430) + bottomInset);
                    if (moved < dp(8)) {
                        if (candidateDrawerHeight <= minH + dp(8)) {
                            candidateDrawerHeight = compactH;
                        } else if (candidateDrawerHeight < maxH - dp(20)) {
                            lastExpandedDrawerHeight = maxH;
                            candidateDrawerHeight = maxH;
                        } else {
                            lastExpandedDrawerHeight = candidateDrawerHeight;
                            candidateDrawerHeight = minH;
                        }
                    } else {
                        if (candidateDrawerHeight <= minH + dp(24)) candidateDrawerHeight = minH;
                        else lastExpandedDrawerHeight = candidateDrawerHeight;
                    }
                    tracker.event("candidate_drawer", -1, -1, Math.round(candidateDrawerHeight), "drag");
                    invalidate();
                    return true;
                }

                if (!solved && event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2
                        && event.getY(0) < boardViewportBottom && event.getY(1) < boardViewportBottom) {
                    cancelBoardLongPress();
                    pinching = true;
                    boardGestureMoved = true;
                    pinchStartDistance = pointerSpacing(event);
                    pinchStartZoom = boardZoom;
                    return true;
                }
                if (pinching && event.getActionMasked() == MotionEvent.ACTION_MOVE && event.getPointerCount() >= 2) {
                    float d = pointerSpacing(event);
                    if (pinchStartDistance > dp(8)) {
                        boardZoom = Math.max(1f, Math.min(2.65f, pinchStartZoom * d / pinchStartDistance));
                        if (boardZoom <= 1.01f) { boardZoom = 1f; boardPanX = 0f; boardPanY = 0f; }
                        invalidate();
                    }
                    return true;
                }
                if (pinching && event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
                    pinching = false;
                    tracker.event("view_zoom", -1, -1, Math.round(boardZoom * 100f), null);
                    return true;
                }

                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    boardGestureMoved = false;
                    boardPanGesture = false;
                    boardLongPressTriggered = false;
                    boardDownX = x; boardDownY = y;
                    boardPanStartX = boardPanX; boardPanStartY = boardPanY;
                    if (!solved && y >= boardViewportTop && y <= boardViewportBottom) {
                        Pos hold = gridPosAt(x, y);
                        if (hold != null) scheduleBoardLongPress(hold);
                    }
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE && !pinching) {
                    float dx = x - boardDownX, dy = y - boardDownY;
                    if (Math.hypot(dx, dy) > dp(8)) {
                        boardGestureMoved = true;
                        cancelBoardLongPress();
                        if (boardZoom > 1.01f && boardDownY >= boardViewportTop && boardDownY <= boardViewportBottom) {
                            boardPanGesture = true;
                            boardPanX = boardPanStartX + dx;
                            boardPanY = boardPanStartY + dy;
                            invalidate();
                            return true;
                        }
                    }
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    cancelBoardLongPress();
                    if (boardLongPressTriggered) { boardLongPressTriggered = false; return true; }
                    if (boardPanGesture || boardGestureMoved) {
                        if (boardPanGesture) tracker.event("view_pan", -1, -1, Math.round(boardZoom * 100f), null);
                        boardPanGesture = false; boardGestureMoved = false;
                        return true;
                    }
                    if (!solved && y >= boardViewportTop && y <= boardViewportBottom) {
                        long now = System.currentTimeMillis();
                        if (now - lastBoardTapTime < 310L
                                && Math.hypot(x - lastBoardTapX, y - lastBoardTapY) < dp(36)) {
                            boardZoom = 1f; boardPanX = 0f; boardPanY = 0f;
                            selectedCell = null; selectedTileId = -1;
                            tracker.event("view_fit", -1, -1, 100, "double_tap");
                            lastBoardTapTime = 0L;
                            invalidate();
                            return true;
                        }
                        lastBoardTapTime = now; lastBoardTapX = x; lastBoardTapY = y;
                        if (localFocusCell != null && gridPosAt(x, y) == null) {
                            clearLocalFocus();
                            invalidate();
                            return true;
                        }
                    }
                }
            }
            if (event.getAction() != MotionEvent.ACTION_UP) return true;

            if (screen == Screen.HOME) {
                if (generating) return true;
                if (homeContinueRect.contains(x, y)) {
                    if (resumablePuzzle && puzzle != null && !solved) resumeCurrentPuzzle();
                    else loadPathLevel(progressLevel);
                }
                else if (homeLevelsRect.contains(x, y)) { levelPage = Math.min(maxUnlockedLevelPage(), Math.max(0, (Math.max(1, level) - 1) / 100)); screen = Screen.LEVELS; invalidate(); }
                else if (homeFreeRect.contains(x, y)) { screen = Screen.FREE_SETUP; invalidate(); }
                else if (homeLibraryRect.contains(x, y)) { screen = Screen.LIBRARY; invalidate(); }
                else if (homeAnalysisRect.contains(x, y)) { screen = Screen.ANALYSIS; invalidate(); }
                else if (homeLanguageRect.contains(x, y)) { showLanguageDialog(); }
                else if (homePrivacyRect.contains(x, y)) { showPrivacyDialog(); }
                else if (DistributionConfig.selfUpdateEnabled() && homeUpdateRect.contains(x, y)) { checkForUpdate(); }
                return true;
            }

            if (screen == Screen.LEVELS) {
                if (topHomeRect.contains(x, y)) { screen = Screen.HOME; invalidate(); return true; }
                if (levelsPrevPageRect.contains(x, y) && levelPage > 0) { levelPage--; invalidate(); return true; }
                if (levelsNextPageRect.contains(x, y) && levelPage < maxUnlockedLevelPage()) { levelPage++; invalidate(); return true; }
                int first = levelPage * 100 + 1;
                for (int i = 0; i < levelRects.length; i++) {
                    if (levelRects[i].contains(x, y)) {
                        loadPathLevel(first + i);
                        return true;
                    }
                }
                return true;
            }

            if (screen == Screen.LIBRARY) {
                if (topHomeRect.contains(x, y)) { screen = Screen.HOME; invalidate(); return true; }
                if (libraryPrevRect.contains(x, y)) {
                    libraryEntryIndex = Math.floorMod(libraryEntryIndex - 1, SolutionLibrary.ENTRIES.size());
                    invalidate(); return true;
                }
                if (libraryNextRect.contains(x, y)) {
                    libraryEntryIndex = (libraryEntryIndex + 1) % SolutionLibrary.ENTRIES.size();
                    invalidate(); return true;
                }
                return true;
            }

            if (screen == Screen.ANALYSIS) {
                if (topHomeRect.contains(x, y)) { screen = Screen.HOME; invalidate(); return true; }
                if (analysisExportRect.contains(x, y)) { beginResearchExport(); return true; }
                if (analysisLastTraceRect.contains(x, y)) { showLastTrajectoryDialog(); return true; }
                return true;
            }

            if (screen == Screen.FREE_SETUP) {
                if (topHomeRect.contains(x, y)) { generationToken++; generating = false; screen = Screen.HOME; invalidate(); return true; }
                if (generating) return true;
                for (int i = 0; i < logicRects.length; i++) if (logicRects[i].contains(x, y)) { freeLogic = i + 1; invalidate(); return true; }
                for (int i = 0; i < calcRects.length; i++) if (calcRects[i].contains(x, y)) { freeCalc = i + 1; invalidate(); return true; }
                for (int i = 0; i < sizeRects.length; i++) if (sizeRects[i].contains(x, y)) { freeSize = i; invalidate(); return true; }
                for (int i = 0; i < maxRects.length; i++) if (maxRects[i].contains(x, y)) { freeMaxIndex = i; invalidate(); return true; }
                for (int i = 0; i < strategyRects.length; i++) if (strategyRects[i].contains(x, y)) { freeStrategyIndex = i; invalidate(); return true; }
                char[] opChars = {'+', '-', '×', '÷', '^'};
                for (int i = 0; i < opRects.length; i++) {
                    if (opRects[i].contains(x, y)) {
                        char op = opChars[i];
                        if (freeOps.contains(op)) {
                            if (freeOps.size() == 1) {
                                Toast.makeText(getContext(), UiText.tr("Keep at least one operation", "Оставь хотя бы одну операцию", "Ponech alespoň jednu operaci"), Toast.LENGTH_SHORT).show();
                            } else if (op != '^' && freeOps.contains('^') && freeOps.size() == 2) {
                                Toast.makeText(getContext(), UiText.tr("Powers currently work in mixed mode — keep one more basic operation", "Степени пока работают в смешанном режиме — оставь ещё одну базовую операцию", "Mocniny zatím fungují ve smíšeném režimu — ponech ještě jednu základní operaci"), Toast.LENGTH_SHORT).show();
                            } else {
                                freeOps.remove(op);
                            }
                        } else freeOps.add(op);
                        invalidate();
                        return true;
                    }
                }
                if (freeGenerateRect.contains(x, y)) loadFreePuzzle();
                return true;
            }

            if (solved && solvedInsightRect.contains(x, y)) {
                showPostSolveInsights();
                return true;
            }
            if (solved && nextLevelRect.contains(x, y)) {
                if (generating) return true;
                if (mode == GameMode.PATH) loadPathLevel(level + 1);
                else loadFreePuzzle();
                return true;
            }
            if (!focusMode && topHomeRect.contains(x, y)) {
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
            }
            if (undoRect.contains(x, y)) {
                undoLastAction();
                return true;
            }
            if (candidateRect.contains(x, y)) {
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
            if (hintRect.contains(x, y)) {
                showGuidedHint();
                return true;
            }

            for (BankHit hit : bankHits) {
                if (hit.rect.contains(x, y)) {
                    if (candidateMode) {
                        if (selectedCell != null) {
                            Tile t = tileById(hit.tileId);
                            if (t != null) toggleCandidate(selectedCell, t.value);
                            selectedTileId = -1;
                        } else {
                            selectedTileId = (selectedTileId == hit.tileId) ? -1 : hit.tileId;
                            Tile chosen = tileById(hit.tileId);
                            tracker.event(selectedTileId == -1 ? "deselect_tile" : "select_tile", -1, -1,
                                    chosen == null ? 0 : chosen.value, candidateMode ? "candidate" : null);
                        }
                    } else if (selectedCell != null) {
                        saveUndoState();
                        placeTileInCell(hit.tileId, selectedCell);
                        resetHintDepth();
                        candidateNotes.remove(selectedCell);
                        selectedCell = null;
                        selectedTileId = -1;
                        checkSolved();
                    } else {
                        selectedTileId = (selectedTileId == hit.tileId) ? -1 : hit.tileId;
                        Tile chosen = tileById(hit.tileId);
                        tracker.event(selectedTileId == -1 ? "deselect_tile" : "select_tile", -1, -1,
                                chosen == null ? 0 : chosen.value, null);
                    }
                    invalidate();
                    return true;
                }
            }

            Pos hitPos = gridPosAt(x, y);
            if (hitPos != null && puzzle.hidden.contains(hitPos)) {
                if (selectedTileId != -1) {
                    if (candidateMode) {
                        Tile t = tileById(selectedTileId);
                        if (t != null) toggleCandidate(hitPos, t.value);
                    } else {
                        saveUndoState();
                        placeTileInCell(selectedTileId, hitPos);
                        resetHintDepth();
                        candidateNotes.remove(hitPos);
                        checkSolved();
                    }
                    selectedTileId = -1;
                    selectedCell = null;
                } else if (selectedCell != null && selectedCell.equals(hitPos)) {
                    Integer existing = puzzle.placedTile.get(hitPos);
                    if (existing != null && !candidateMode) {
                        saveUndoState();
                        Tile old = tileById(existing);
                        if (old != null) old.used = false;
                        puzzle.placedTile.remove(hitPos);
                        resetHintDepth();
                        tracker.event("remove", hitPos.x, hitPos.y, old == null ? 0 : old.value, null);
                        checkSolved();
                    }
                    selectedCell = null;
                } else {
                    selectedCell = hitPos;
                    selectedTileId = -1;
                    tracker.event("select_cell", hitPos.x, hitPos.y, 0, null);
                }
                invalidate();
                MainActivity host = (MainActivity) getContext();
                if (host.scratchpadPanel != null && host.scratchpadPanel.getVisibility() == View.VISIBLE) {
                    host.refreshScratchpadWorkbenchState();
                }
            }
            return true;
        }

        Map<Pos, Integer> currentAssignedValues() {
            Map<Pos, Integer> values = new HashMap<>();
            if (puzzle == null) return values;
            for (Map.Entry<Pos, Integer> e : puzzle.placedTile.entrySet()) {
                Tile t = tileById(e.getValue());
                if (t != null) values.put(e.getKey(), t.value);
            }
            return values;
        }

        void showGuidedHint() {
            if (puzzle == null || solved) return;
            final int shownStage = hintStage;
            HintEngine.Hint hint = HintEngine.suggest(puzzle, currentAssignedValues(), shownStage);
            if (hint.focus != null && puzzle.hidden.contains(hint.focus)) {
                selectedCell = hint.focus;
                selectedTileId = -1;
            }
            tracker.event("hint", hint.focus == null ? -1 : hint.focus.x,
                    hint.focus == null ? -1 : hint.focus.y, shownStage + 1, hint.kind);
            hintStage = Math.min(2, shownStage + 1);
            showHintDialog(hint, shownStage);
            invalidate();
        }

        void showHintDialog(HintEngine.Hint hint, int shownStage) {
            TextView text = new TextView(getContext());
            int pad = (int) dp(20);
            text.setPadding(pad, pad, pad, pad);
            text.setText(hint.text);
            text.setTextSize(17f);
            text.setTextColor(ink);
            text.setLineSpacing(0f, 1.16f);
            text.setTextIsSelectable(true);

            ScrollView scroll = new ScrollView(getContext());
            scroll.setFillViewport(true);
            scroll.addView(text);

            AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                    .setTitle(UiText.tr("Hint ", "Намёк ", "Nápověda ") + (shownStage + 1) + "/3")
                    .setView(scroll)
                    .setNegativeButton(UiText.tr("Close", "Закрыть", "Zavřít"), null);
            if (shownStage < 2) {
                builder.setPositiveButton(UiText.tr("Deeper →", "Глубже →", "Hlouběji →"), (dialog, which) -> showGuidedHint());
            }
            builder.show();
        }

        void showLanguageDialog() {
            final String[] codes = {"system", "en", "ru", "cs"};
            final String[] labels = {
                    UiText.tr("System language", "Язык системы", "Jazyk systému"),
                    "English",
                    "Русский",
                    "Čeština"
            };
            int checked = 0;
            String current = UiText.languageOverride();
            for (int i = 0; i < codes.length; i++) if (codes[i].equals(current)) checked = i;

            new AlertDialog.Builder(getContext())
                    .setTitle(UiText.tr("Language", "Язык", "Jazyk"))
                    .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                        UiText.setLanguageOverride(codes[which]);
                        prefs.edit().putString("language_override", codes[which]).apply();
                        SolutionStrategy.refreshAllLocalizedText();
                        SolutionLibrary.refreshLocalizedEntries();
                        updateStatus = UiText.tr("update not checked", "обновление не проверено", "aktualizace nezkontrolována");
                        dialog.dismiss();
                        invalidate();
                    })
                    .setNegativeButton(UiText.tr("Close", "Закрыть", "Zavřít"), null)
                    .show();
        }

        void showPrivacyDialog() {
            TextView text = new TextView(getContext());
            int pad = (int) dp(20);
            text.setPadding(pad, pad, pad, pad);
            text.setText(PrivacyPolicy.text());
            text.setTextSize(15.5f);
            text.setTextColor(ink);
            text.setLineSpacing(0f, 1.14f);
            text.setTextIsSelectable(true);

            ScrollView scroll = new ScrollView(getContext());
            scroll.setFillViewport(true);
            scroll.addView(text);

            new AlertDialog.Builder(getContext())
                    .setTitle(UiText.tr("Privacy", "Конфиденциальность", "Soukromí"))
                    .setView(scroll)
                    .setNegativeButton(UiText.tr("Close", "Закрыть", "Zavřít"), null)
                    .show();
        }

        void showPostSolveInsights() {
            SessionTracker.AnalysisSnapshot snapshot = tracker.analyze();
            if (snapshot.recent.isEmpty() || !snapshot.recent.get(0).solved) {
                Toast.makeText(getContext(), UiText.tr("No solved trace is available yet", "Пока нет завершённого следа решения", "Zatím není dostupná dokončená stopa řešení"), Toast.LENGTH_SHORT).show();
                return;
            }
            PostSolveInsightBuilder.Result result = PostSolveInsightBuilder.build(snapshot.recent.get(0));
            StringBuilder body = new StringBuilder();
            for (String observation : result.observations) {
                if (body.length() > 0) body.append("\n\n");
                body.append("• ").append(observation);
            }
            body.append(UiText.tr(
                    "\n\nThis is an interpretation of your interaction trace, not a literal record of your thoughts.",
                    "\n\nЭто интерпретация следа взаимодействия с задачей, а не буквальная запись твоих мыслей.",
                    "\n\nJde o interpretaci stopy interakce s hlavolamem, ne o doslovný záznam tvých myšlenek."));

            TextView text = new TextView(getContext());
            int pad = (int) dp(20);
            text.setPadding(pad, pad, pad, pad);
            text.setText(body.toString());
            text.setTextSize(16f);
            text.setTextColor(ink);
            text.setLineSpacing(0f, 1.16f);
            text.setTextIsSelectable(true);

            ScrollView scroll = new ScrollView(getContext());
            scroll.setFillViewport(true);
            scroll.addView(text);

            new AlertDialog.Builder(getContext())
                    .setTitle(UiText.tr("How you solved it", "Как ты решил", "Jak jsi řešil"))
                    .setView(scroll)
                    .setPositiveButton(UiText.tr("Done", "Готово", "Hotovo"), null)
                    .show();
        }

        void showLastTrajectoryDialog() {
            String report = tracker.latestTrajectoryReport();
            if (report == null || report.isEmpty()) {
                Toast.makeText(getContext(), UiText.tr("No completed sessions yet", "Пока нет завершённых сессий", "Zatím nejsou žádné dokončené relace"), Toast.LENGTH_SHORT).show();
                return;
            }
            TextView text = new TextView(getContext());
            int pad = (int) dp(20);
            text.setPadding(pad, pad, pad, pad);
            text.setText(report);
            text.setTextSize(15.5f);
            text.setTextColor(ink);
            text.setLineSpacing(0f, 1.14f);
            text.setTextIsSelectable(true);

            ScrollView scroll = new ScrollView(getContext());
            scroll.setFillViewport(true);
            scroll.addView(text);

            new AlertDialog.Builder(getContext())
                    .setTitle(UiText.tr("Solve path", "Ход решения", "Průběh řešení"))
                    .setView(scroll)
                    .setNegativeButton(UiText.tr("Close", "Закрыть", "Zavřít"), null)
                    .show();
        }

        String installedVersionName() {
            try {
                android.content.pm.PackageInfo info = getContext().getPackageManager()
                        .getPackageInfo(getContext().getPackageName(), 0);
                return info.versionName == null ? "1.41" : info.versionName;
            } catch (android.content.pm.PackageManager.NameNotFoundException ex) {
                return "1.41";
            }
        }

        long installedVersionCode() {
            try {
                android.content.pm.PackageInfo info = getContext().getPackageManager()
                        .getPackageInfo(getContext().getPackageName(), 0);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return info.getLongVersionCode();
                return info.versionCode;
            } catch (android.content.pm.PackageManager.NameNotFoundException ex) {
                return 41L;
            }
        }

        void startUpdateDownload(String version, String downloadUrl) {
            if (!DistributionConfig.selfUpdateEnabled()) return;
            try {
                DownloadManager manager = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager == null) throw new IllegalStateException(UiText.tr("DownloadManager unavailable", "DownloadManager недоступен", "DownloadManager není dostupný"));
                String fileName = "MathCrossword-v" + version + ".apk";
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
                request.setTitle("MathCrossword " + version);
                request.setDescription(UiText.tr("Downloading update", "Загрузка обновления", "Stahování aktualizace"));
                request.setMimeType("application/vnd.android.package-archive");
                request.setAllowedOverMetered(true);
                request.setAllowedOverRoaming(true);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalFilesDir(getContext(), Environment.DIRECTORY_DOWNLOADS, fileName);
                long id = manager.enqueue(request);
                updateStatus = UiText.tr("downloading ", "скачиваю ", "stahuji ") + version;
                invalidate();
                Toast.makeText(getContext(), UiText.tr("The update is downloading inside the app", "Обновление скачивается внутри приложения", "Aktualizace se stahuje přímo v aplikaci"), Toast.LENGTH_SHORT).show();
                waitForUpdateDownload(manager, id, version);
            } catch (RuntimeException ex) {
                updateStatus = UiText.tr("download error", "ошибка загрузки", "chyba stahování");
                invalidate();
                Toast.makeText(getContext(), UiText.tr("Could not start download: ", "Не удалось начать загрузку: ", "Stahování se nepodařilo spustit: ") + ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        }

        void waitForUpdateDownload(DownloadManager manager, long id, String version) {
            new Thread(() -> {
                for (int attempt = 0; attempt < 900; attempt++) {
                    Cursor cursor = null;
                    try {
                        cursor = manager.query(new DownloadManager.Query().setFilterById(id));
                        if (cursor != null && cursor.moveToFirst()) {
                            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                Uri uri = manager.getUriForDownloadedFile(id);
                                post(() -> {
                                    pendingInstallUri = uri;
                                    pendingInstallVersion = version;
                                    updateStatus = UiText.tr("downloaded ", "скачано ", "staženo ") + version;
                                    invalidate();
                                    maybeInstallPendingUpdate();
                                });
                                return;
                            }
                            if (status == DownloadManager.STATUS_FAILED) {
                                post(() -> {
                                    updateStatus = UiText.tr("download error", "ошибка загрузки", "chyba stahování");
                                    invalidate();
                                    Toast.makeText(getContext(), UiText.tr("Android could not download the update", "Android не смог скачать обновление", "Android nemohl stáhnout aktualizaci"), Toast.LENGTH_LONG).show();
                                });
                                return;
                            }
                        }
                    } catch (RuntimeException ignored) {
                    } finally {
                        if (cursor != null) cursor.close();
                    }
                    try { Thread.sleep(500L); } catch (InterruptedException ex) { return; }
                }
            }, "mathcrossword-update-download").start();
        }

        void maybeInstallPendingUpdate() {
            if (!DistributionConfig.selfUpdateEnabled()) return;
            if (pendingInstallUri == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && !getContext().getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(getContext())
                        .setTitle(UiText.tr("Allow updates", "Разрешить обновления", "Povolit aktualizace"))
                        .setMessage(UiText.tr("Android will ask once to let MathCrossword install downloaded updates. Then return to the game and installation will continue automatically.", "Android один раз попросит разрешить MathCrossword устанавливать скачанные обновления. После этого вернись в игру — установка продолжится сама.", "Android jednou požádá o povolení, aby MathCrossword mohl instalovat stažené aktualizace. Potom se vrať do hry a instalace bude automaticky pokračovat."))
                        .setPositiveButton(UiText.tr("Allow", "Разрешить", "Povolit"), (d, which) -> {
                            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + getContext().getPackageName()));
                            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            getContext().startActivity(settings);
                        })
                        .setNegativeButton(UiText.tr("Later", "Позже", "Později"), null)
                        .show();
                return;
            }
            try {
                Intent install = new Intent(Intent.ACTION_VIEW);
                install.setDataAndType(pendingInstallUri, "application/vnd.android.package-archive");
                install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getContext().startActivity(install);
            } catch (RuntimeException ex) {
                Toast.makeText(getContext(), UiText.tr("APK downloaded, but the installer did not open", "APK скачан, но установщик не открылся", "APK je stažené, ale instalátor se neotevřel"), Toast.LENGTH_LONG).show();
            }
        }

        void checkForUpdate() {
            if (!DistributionConfig.selfUpdateEnabled()) return;
            if (updateChecking) return;
            updateChecking = true;
            updateStatus = UiText.tr("checking…", "проверяю…", "kontroluji…");
            invalidate();
            UpdateChecker.check(installedVersionName(), new UpdateChecker.Callback() {
                @Override public void onResult(String latestVersion, String downloadUrl, boolean newer) {
                    post(() -> {
                        updateChecking = false;
                        updateStatus = newer ? UiText.tr("available ", "доступна ", "dostupná ") + latestVersion : UiText.tr("up to date", "актуальная", "aktuální");
                        invalidate();
                        AlertDialog.Builder dialog = new AlertDialog.Builder(getContext())
                                .setTitle(newer ? UiText.tr("Update available", "Есть обновление", "Je dostupná aktualizace") : UiText.tr("No update needed", "Обновление не требуется", "Aktualizace není potřeba"))
                                .setMessage(UiText.tr("Installed: ", "Установлена: ", "Nainstalováno: ") + installedVersionName() + " (" + installedVersionCode() + ")\n"
                                        + UiText.tr("Latest: ", "Последняя: ", "Nejnovější: ") + latestVersion);
                        if (newer && downloadUrl != null) {
                            dialog.setPositiveButton(UiText.tr("Download", "Скачать", "Stáhnout"), (d, which) ->
                                    startUpdateDownload(latestVersion, downloadUrl));
                        }
                        dialog.setNegativeButton(UiText.tr("Close", "Закрыть", "Zavřít"), null).show();
                    });
                }

                @Override public void onError(String message) {
                    post(() -> {
                        updateChecking = false;
                        updateStatus = UiText.tr("check error", "ошибка проверки", "chyba kontroly");
                        invalidate();
                        Toast.makeText(getContext(), UiText.tr("Could not check for updates: ", "Не удалось проверить обновление: ", "Aktualizace se nepodařilo zkontrolovat: ") + message, Toast.LENGTH_LONG).show();
                    });
                }
            });
        }

        void resetHintDepth() {
            hintStage = 0;
        }

        void saveUndoState() {
            if (puzzle == null) return;
            undoStack.add(new GameSnapshot(puzzle.placedTile, candidateNotes));
            if (undoStack.size() > 80) undoStack.remove(0);
        }

        void undoLastAction() {
            if (puzzle == null || undoStack.isEmpty()) return;
            tracker.event("undo", -1, -1, 0, null);
            resetHintDepth();
            GameSnapshot snap = undoStack.remove(undoStack.size() - 1);
            puzzle.placedTile.clear();
            puzzle.placedTile.putAll(snap.placed);
            for (Tile t : puzzle.tiles) t.used = false;
            for (Integer id : puzzle.placedTile.values()) {
                Tile t = tileById(id);
                if (t != null) t.used = true;
            }
            candidateNotes.clear();
            for (Map.Entry<Pos, LinkedHashSet<Integer>> e : snap.notes.entrySet()) {
                candidateNotes.put(e.getKey(), new LinkedHashSet<>(e.getValue()));
            }
            selectedCell = null;
            selectedTileId = -1;
            checkSolved();
            invalidate();
        }

        void toggleCandidate(Pos pos, int value) {
            if (puzzle == null || !puzzle.hidden.contains(pos) || puzzle.placedTile.containsKey(pos)) return;
            saveUndoState();
            LinkedHashSet<Integer> notes = candidateNotes.computeIfAbsent(pos, k -> new LinkedHashSet<>());
            boolean added = notes.add(value);
            if (!added) notes.remove(value);
            tracker.event(added ? "candidate_add" : "candidate_remove", pos.x, pos.y, value, null);
            resetHintDepth();
            if (notes.isEmpty()) candidateNotes.remove(pos);
            invalidate();
        }

        void placeTileInCell(int tileId, Pos cellPos) {
            Tile incoming = tileById(tileId);
            if (incoming == null) return;
            Integer oldId = puzzle.placedTile.get(cellPos);
            if (oldId != null && oldId == tileId) return;

            Pos previousPos = null;
            for (Map.Entry<Pos, Integer> e : puzzle.placedTile.entrySet()) {
                if (e.getValue() == tileId) { previousPos = e.getKey(); break; }
            }
            if (previousPos != null) puzzle.placedTile.remove(previousPos);

            if (oldId != null) {
                Tile old = tileById(oldId);
                if (old != null) old.used = false;
            }
            incoming.used = true;
            puzzle.placedTile.put(cellPos, tileId);
            tracker.event("place", cellPos.x, cellPos.y, incoming.value,
                    oldId == null ? null : "replace");
        }

        Pos gridPosAt(float x, float y) {
            if (cellSize <= 0) return null;
            int gx = (int) Math.floor((x - originX) / cellSize);
            int gy = (int) Math.floor((y - originY) / cellSize);
            Pos p = new Pos(gx, gy);
            return puzzle.cells.containsKey(p) ? p : null;
        }

        void checkSolved() {
            boolean wasSolved = solved;
            if (puzzle.placedTile.size() != puzzle.hidden.size()) { solved = false; return; }
            for (Equation e : puzzle.equations) {
                Integer a = numberAt(e.a), b = numberAt(e.b), c = numberAt(e.c);
                if (a == null || b == null || c == null || PuzzleGenerator.eval(a, e.operator, b) != c) {
                    solved = false;
                    tracker.event("full_incorrect", -1, -1, 0, null);
                    return;
                }
            }
            solved = true;
            resumablePuzzle = false;
            if (!wasSolved && mode == GameMode.PATH && level == progressLevel) {
                progressLevel = level + 1;
                prefs.edit().putInt("currentLevel", progressLevel).apply();
            }
            if (!wasSolved && tracker.hasOpenSession()) tracker.finish(true, "solved");
        }

        float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    }
}
