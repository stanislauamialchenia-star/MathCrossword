package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Map;

/**
 * Thin interaction layer for a discoverable correction action.
 *
 * A filled selected cell temporarily turns the Candidates slot into a clear action.
 * Candidates are not useful for a cell that already contains a tile, so the contextual
 * replacement avoids adding a fifth permanent tool while keeping Undo/Scratchpad/Hint intact.
 */
public class ClearableMainActivity extends MainActivity {
    private static final String CLEAR_HINT_KEY = "clear_action_hint_v151_shown";

    private TextView clearCellButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        clearCellButton = buildClearCellButton();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1, 1, Gravity.TOP | Gravity.START);
        rootView.addView(clearCellButton, lp);
        clearCellButton.setVisibility(View.GONE);
        rootView.post(this::syncClearCellButton);
    }

    private TextView buildClearCellButton() {
        TextView button = new TextView(this);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(14f);
        button.setTextColor(Color.rgb(39, 42, 40));
        button.setTypeface(android.graphics.Typeface.DEFAULT);
        button.setIncludeFontPadding(false);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(255, 255, 253));
        background.setCornerRadius(dpActivity(12));
        background.setStroke(dpActivity(1), Color.rgb(174, 176, 171));
        button.setBackground(background);
        button.setOnClickListener(v -> clearSelectedCell());
        return button;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        boolean handled = super.dispatchTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            rootView.post(this::syncClearCellButton);
        } else if (action == MotionEvent.ACTION_MOVE) {
            // Keep the contextual action attached to the tool row while the drawer is resized.
            rootView.postOnAnimation(this::syncClearCellButton);
        }
        return handled;
    }

    private void syncClearCellButton() {
        if (clearCellButton == null || gameView == null || gameView.puzzle == null) return;

        Pos pos = gameView.selectedCell;
        boolean drawerVisible = gameView.candidateDrawerHeight > dpActivity(40) + gameView.bottomInset;
        boolean scratchpadClosed = scratchpadPanel == null || scratchpadPanel.getVisibility() != View.VISIBLE;
        boolean canClear = gameView.screen == Screen.GAME
                && !gameView.solved
                && !gameView.focusMode
                && !gameView.candidateMode
                && drawerVisible
                && scratchpadClosed
                && pos != null
                && gameView.puzzle.placedTile.containsKey(pos)
                && gameView.candidateRect.width() > dpActivity(20)
                && gameView.candidateRect.height() > dpActivity(20);

        if (!canClear) {
            clearCellButton.setVisibility(View.GONE);
            return;
        }

        clearCellButton.setText(UiText.tr("⌫ Clear", "⌫ Убрать", "⌫ Smazat"));
        placeOverCandidateTool(gameView.candidateRect);
        clearCellButton.setVisibility(View.VISIBLE);
        maybeShowWrongNumberHint(pos);
    }

    private void placeOverCandidateTool(RectF target) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) clearCellButton.getLayoutParams();
        int width = Math.max(1, Math.round(target.width()));
        int height = Math.max(1, Math.round(target.height()));
        int left = Math.round(target.left);
        int top = Math.round(target.top);
        boolean changed = lp.width != width || lp.height != height
                || lp.leftMargin != left || lp.topMargin != top;
        if (!changed) return;
        lp.width = width;
        lp.height = height;
        lp.leftMargin = left;
        lp.topMargin = top;
        lp.gravity = Gravity.TOP | Gravity.START;
        clearCellButton.setLayoutParams(lp);
    }

    private void clearSelectedCell() {
        if (gameView == null || gameView.puzzle == null || gameView.candidateMode) return;
        Pos pos = gameView.selectedCell;
        if (pos == null) return;
        Integer existingId = gameView.puzzle.placedTile.get(pos);
        if (existingId == null) {
            syncClearCellButton();
            return;
        }

        gameView.saveUndoState();
        Tile old = gameView.tileById(existingId);
        if (old != null) old.used = false;
        gameView.puzzle.placedTile.remove(pos);
        gameView.resetHintDepth();
        gameView.selectedTileId = -1;
        gameView.tracker.event("remove", pos.x, pos.y, old == null ? 0 : old.value, "clear_button");
        gameView.checkSolved();

        // Keep the now-empty cell selected so the player can immediately choose a replacement.
        gameView.invalidate();
        syncClearCellButton();
    }

    private void maybeShowWrongNumberHint(Pos pos) {
        if (gameView.prefs.getBoolean(CLEAR_HINT_KEY, false)) return;
        Map<Pos, Integer> status = gameView.equationStatus();
        if (status.getOrDefault(pos, 0) != 2) return;

        gameView.prefs.edit().putBoolean(CLEAR_HINT_KEY, true).apply();
        Toast.makeText(this, UiText.tr(
                "Wrong number · tap “⌫ Clear” to remove it",
                "Неверное число · нажми «⌫ Убрать», чтобы удалить",
                "Chybné číslo · klepni na „⌫ Smazat“"), Toast.LENGTH_SHORT).show();
    }
}
