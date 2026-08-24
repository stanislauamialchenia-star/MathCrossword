package com.offline.mathcrossword;

import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Small interaction layer for issue #56.
 *
 * The canvas game already supports clearing a placed value by tapping the same
 * cell again. That gesture remains available, but a first-time player should
 * not have to discover it by accident. When a filled cell is selected, this
 * activity places an explicit clear action over the Candidates tool slot.
 */
public class DiscoverableMainActivity extends MainActivity {
    private TextView clearAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        clearAction = buildClearAction();
        rootView.addView(clearAction, new FrameLayout.LayoutParams(1, 1));
        clearAction.setVisibility(View.GONE);

        // Observe normal game touches without consuming them. The Canvas view
        // still owns all game gestures; we only refresh the contextual action
        // after its state may have changed.
        gameView.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_MOVE
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                gameView.post(this::syncClearAction);
            }
            return false;
        });
        gameView.post(this::syncClearAction);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameView != null) gameView.post(this::syncClearAction);
    }

    private TextView buildClearAction() {
        TextView view = new TextView(this);
        view.setText(UiText.tr("× Clear", "× Удалить", "× Smazat"));
        view.setTextSize(14f);
        view.setTextColor(Color.rgb(39, 42, 40));
        view.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(255, 255, 253));
        bg.setCornerRadius(dpActivity(12));
        bg.setStroke(dpActivity(1), Color.rgb(174, 176, 171));
        view.setBackground(bg);

        view.setOnClickListener(v -> clearSelectedPlacedValue());
        return view;
    }

    private boolean canClearSelectedCell() {
        return gameView != null
                && gameView.screen == Screen.GAME
                && gameView.puzzle != null
                && !gameView.solved
                && !gameView.focusMode
                && !gameView.scratchpadOverlayOpen
                && !gameView.candidateMode
                && gameView.selectedCell != null
                && gameView.puzzle.placedTile.containsKey(gameView.selectedCell)
                && !gameView.candidateRect.isEmpty();
    }

    private void syncClearAction() {
        if (clearAction == null || !canClearSelectedCell()) {
            if (clearAction != null) clearAction.setVisibility(View.GONE);
            return;
        }

        RectF r = gameView.candidateRect;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) clearAction.getLayoutParams();
        lp.width = Math.max(1, Math.round(r.width()));
        lp.height = Math.max(1, Math.round(r.height()));
        lp.leftMargin = Math.round(r.left);
        lp.topMargin = Math.round(r.top);
        clearAction.setLayoutParams(lp);
        clearAction.setText(UiText.tr("× Clear", "× Удалить", "× Smazat"));
        clearAction.setVisibility(View.VISIBLE);
        clearAction.bringToFront();
    }

    private void clearSelectedPlacedValue() {
        if (!canClearSelectedCell()) {
            syncClearAction();
            return;
        }

        PuzzleModel.Pos pos = gameView.selectedCell;
        Integer existing = gameView.puzzle.placedTile.get(pos);
        if (existing == null) {
            syncClearAction();
            return;
        }

        gameView.saveUndoState();
        PuzzleModel.Tile old = gameView.tileById(existing);
        if (old != null) old.used = false;
        gameView.puzzle.placedTile.remove(pos);
        gameView.resetHintDepth();
        gameView.tracker.event("remove", pos.x, pos.y, old == null ? 0 : old.value, "explicit_clear");
        gameView.selectedCell = null;
        gameView.selectedTileId = -1;
        gameView.checkSolved();
        gameView.invalidate();
        syncClearAction();
    }
}
