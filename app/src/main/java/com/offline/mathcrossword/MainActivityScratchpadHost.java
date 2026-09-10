package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter between the existing MainActivity/GameView implementation and the new
 * ScratchpadPanelController boundary.
 *
 * This class is deliberately thin: it does not introduce new rules and does not
 * change scratchpad behavior. It centralizes the current GameView calls behind
 * ScratchpadPanelController.Host so MainActivity can be rewired in small steps.
 */
final class MainActivityScratchpadHost implements ScratchpadPanelController.Host {
    private final MainActivity activity;

    MainActivityScratchpadHost(MainActivity activity) {
        this.activity = activity;
    }

    private MainActivity.GameView gameView() {
        return activity == null ? null : activity.gameView;
    }

    @Override
    public boolean canUndo() {
        MainActivity.GameView gameView = gameView();
        return gameView != null && !gameView.undoStack.isEmpty();
    }

    @Override
    public void undoLastActionFromScratchpad() {
        MainActivity.GameView gameView = gameView();
        if (gameView == null || gameView.undoStack.isEmpty()) return;
        gameView.undoLastAction();
        activity.refreshScratchpadWorkbenchState();
    }

    @Override
    public boolean isCandidateMode() {
        MainActivity.GameView gameView = gameView();
        return gameView != null && gameView.candidateMode;
    }

    @Override
    public void toggleCandidateModeFromScratchpad() {
        MainActivity.GameView gameView = gameView();
        if (gameView == null) return;
        gameView.candidateMode = !gameView.candidateMode;
        gameView.selectedTileId = -1;
        gameView.tracker.event("candidate_mode", -1, -1,
                gameView.candidateMode ? 1 : -1, "workbench");
        activity.refreshScratchpadWorkbenchState();
        gameView.invalidate();
    }

    @Override
    public List<ScratchpadPanelController.CandidateTile> visibleCandidateTiles() {
        MainActivity.GameView gameView = gameView();
        if (gameView == null || gameView.puzzle == null) return Collections.emptyList();

        Set<Integer> selectedNotes = Collections.emptySet();
        if (gameView.selectedCell != null) {
            LinkedHashSet<Integer> notes = gameView.candidateNotes.get(gameView.selectedCell);
            if (notes != null) selectedNotes = notes;
        }

        List<ScratchpadPanelController.CandidateTile> result = new ArrayList<>();
        for (Tile tile : gameView.puzzle.tiles) {
            if (tile.used) continue;
            boolean selected = tile.id == gameView.selectedTileId;
            boolean noted = gameView.candidateMode
                    && gameView.selectedCell != null
                    && selectedNotes.contains(tile.value);
            result.add(new ScratchpadPanelController.CandidateTile(
                    tile.id, tile.value, selected, noted));
        }
        return result;
    }

    @Override
    public void onCandidateTileTappedFromScratchpad(int tileId) {
        if (activity == null) return;
        activity.onScratchpadCandidateTapped(tileId);
    }

    @Override
    public String selectedExistingCellLabel() {
        MainActivity.GameView gameView = gameView();
        return gameView == null ? null : gameView.selectedScratchpadCellLabel();
    }

    @Override
    public String selectedPreviewCellLabel() {
        MainActivity.GameView gameView = gameView();
        return gameView == null ? null : gameView.previewSelectedScratchpadCellLabel();
    }

    @Override
    public void insertOrRemoveSelectedCellReference() {
        if (activity == null) return;
        activity.insertSelectedCellIntoScratchpad();
    }

    @Override
    public void showHintFromScratchpad() {
        MainActivity.GameView gameView = gameView();
        if (gameView == null) return;
        gameView.showGuidedHint();
        activity.refreshScratchpadWorkbenchState();
    }

    @Override
    public void onScratchpadVisibilityChanged(boolean visible) {
        MainActivity.GameView gameView = gameView();
        if (gameView == null) return;
        gameView.tracker.event("scratchpad_open", -1, -1, visible ? 1 : 0, null);
    }

    @Override
    public void onScratchpadTextChanged(String text) {
        if (activity == null || activity.scratchpadPuzzleSeed == Long.MIN_VALUE) return;
        activity.getSharedPreferences("scratchpad_local", Context.MODE_PRIVATE).edit()
                .putLong("seed", activity.scratchpadPuzzleSeed)
                .putString("text", text == null ? "" : text)
                .apply();
    }
}
