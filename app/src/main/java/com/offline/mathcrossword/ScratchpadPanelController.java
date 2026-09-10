package com.offline.mathcrossword;

import android.content.Context;
import android.text.Editable;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Boundary object for the scratchpad/workbench surface.
 *
 * Phase 1 intent:
 * - keep MainActivity/GameView behavior unchanged;
 * - define the seam where scratchpad UI stops reaching into GameView internals;
 * - move concrete widget construction here in the next small refactor step.
 *
 * The controller talks to the game only through Host. That lets the scratchpad
 * remain a UI component instead of becoming another owner of game rules.
 */
final class ScratchpadPanelController {
    interface Host {
        boolean canUndo();
        void undoLastActionFromScratchpad();

        boolean isCandidateMode();
        void toggleCandidateModeFromScratchpad();

        List<CandidateTile> visibleCandidateTiles();
        void onCandidateTileTappedFromScratchpad(int tileId);

        String selectedExistingCellLabel();
        String selectedPreviewCellLabel();
        void insertOrRemoveSelectedCellReference();

        void showHintFromScratchpad();
        void onScratchpadVisibilityChanged(boolean visible);
        void onScratchpadTextChanged(String text);
    }

    static final class CandidateTile {
        final int tileId;
        final int value;
        final boolean selected;
        final boolean noted;

        CandidateTile(int tileId, int value, boolean selected, boolean noted) {
            this.tileId = tileId;
            this.value = value;
            this.selected = selected;
            this.noted = noted;
        }
    }

    private final Context context;
    private final Host host;

    LinearLayout panel;
    LinearLayout candidateShelf;
    EditText editor;
    TextView candidateModeAction;
    TextView undoAction;
    TextView insertAction;

    long puzzleSeed = Long.MIN_VALUE;
    int panelHeightPx = -1;
    float dragStartY = 0f;
    int dragStartHeight = 0;
    boolean binding = false;

    ScratchpadPanelController(Context context, Host host) {
        this.context = context;
        this.host = host;
    }

    /**
     * This method is intentionally narrow for phase 1. The current MainActivity
     * still owns the actual Android View tree until the next commit moves it here.
     */
    void attachExistingViews(LinearLayout panel, LinearLayout candidateShelf, EditText editor,
                             TextView candidateModeAction, TextView undoAction, TextView insertAction) {
        this.panel = panel;
        this.candidateShelf = candidateShelf;
        this.editor = editor;
        this.candidateModeAction = candidateModeAction;
        this.undoAction = undoAction;
        this.insertAction = insertAction;
    }

    void prepareForPuzzle(long seed, boolean clearText) {
        puzzleSeed = seed;
        panelHeightPx = -1;
        if (editor == null) return;
        binding = true;
        if (clearText) editor.setText("");
        Editable text = editor.getText();
        if (text != null) editor.setSelection(text.length());
        binding = false;
    }

    void notifyTextChanged(String text) {
        if (binding || puzzleSeed == Long.MIN_VALUE) return;
        host.onScratchpadTextChanged(text == null ? "" : text);
    }

    void refreshActions() {
        // Phase 1: no styling is moved yet. This exists so the next refactor can
        // redirect MainActivity.refreshScratchpadWorkbenchState() here without
        // changing game behavior.
        if (host == null) return;
        host.canUndo();
        host.isCandidateMode();
        host.selectedExistingCellLabel();
        host.selectedPreviewCellLabel();
    }

    List<CandidateTile> visibleCandidateTiles() {
        if (host == null) return Collections.emptyList();
        List<CandidateTile> tiles = host.visibleCandidateTiles();
        return tiles == null ? Collections.emptyList() : new ArrayList<>(tiles);
    }

    void hideKeyboard() {
        if (editor == null) return;
        editor.clearFocus();
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
    }
}
