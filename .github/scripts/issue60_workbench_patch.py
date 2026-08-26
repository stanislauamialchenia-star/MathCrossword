from pathlib import Path
import re

PATH = Path("app/src/main/java/com/offline/mathcrossword/MainActivity.java")
text = PATH.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)


def replace_regex_once(pattern: str, replacement: str, label: str) -> None:
    global text
    text, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one regex match, found {count}")


replace_once(
    "    LinearLayout scratchpadPanel;\n    EditText scratchpadEditor;\n",
    "    LinearLayout scratchpadPanel;\n"
    "    EditText scratchpadEditor;\n"
    "    TextView scratchpadCandidateTab;\n"
    "    TextView scratchpadDraftTab;\n"
    "    TextView scratchpadUndoAction;\n",
    "scratchpad workbench fields",
)

new_scratchpad_block = r'''    LinearLayout buildScratchpadPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dpActivity(12), 0, dpActivity(12), 0);
        panel.setElevation(dpActivity(10));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.rgb(250, 249, 246));
        panelBg.setCornerRadii(new float[]{dpActivity(18), dpActivity(18), dpActivity(18), dpActivity(18), 0, 0, 0, 0});
        panel.setBackground(panelBg);

        // The grip is intentionally visible but owns only a small centered touch target.
        // It must never steal taps from the workbench controls below it.
        FrameLayout gripRow = new FrameLayout(this);
        panel.addView(gripRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpActivity(24)));
        TextView grip = new TextView(this);
        grip.setText("━━");
        grip.setTextColor(Color.rgb(158, 159, 155));
        grip.setTextSize(10f);
        grip.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams gripParams = new FrameLayout.LayoutParams(
                dpActivity(72), dpActivity(22), Gravity.CENTER);
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

        // Candidates and Scratchpad are presented as two tools in one solution workbench.
        // Undo/Hint are secondary actions, so they get compact icon buttons.
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dpActivity(2), 0, dpActivity(2), dpActivity(2));
        panel.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpActivity(42)));

        scratchpadUndoAction = scratchpadWorkbenchAction("↶", false, true, false);
        LinearLayout.LayoutParams undoParams = new LinearLayout.LayoutParams(dpActivity(40), dpActivity(38));
        header.addView(scratchpadUndoAction, undoParams);
        scratchpadUndoAction.setOnClickListener(v -> {
            if (gameView == null || gameView.undoStack.isEmpty()) return;
            gameView.undoLastAction();
            refreshScratchpadWorkbenchState();
        });

        scratchpadCandidateTab = scratchpadWorkbenchAction(
                UiText.tr("Candidates", "Кандидаты", "Kandidáti"), false, false, true);
        LinearLayout.LayoutParams candidateParams = new LinearLayout.LayoutParams(0, dpActivity(38), 1f);
        candidateParams.leftMargin = dpActivity(6);
        header.addView(scratchpadCandidateTab, candidateParams);
        scratchpadCandidateTab.setOnClickListener(v -> showCandidatesFromScratchpad());

        scratchpadDraftTab = scratchpadWorkbenchAction(
                UiText.tr("Scratchpad", "Черновик", "Poznámky"), true, false, true);
        LinearLayout.LayoutParams draftParams = new LinearLayout.LayoutParams(0, dpActivity(38), 1f);
        draftParams.leftMargin = dpActivity(6);
        header.addView(scratchpadDraftTab, draftParams);

        TextView hintAction = scratchpadWorkbenchAction("?", false, true, true);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(dpActivity(40), dpActivity(38));
        hintParams.leftMargin = dpActivity(6);
        header.addView(hintAction, hintParams);
        hintAction.setOnClickListener(v -> {
            if (gameView != null) gameView.showGuidedHint();
        });

        LinearLayout contextRow = new LinearLayout(this);
        contextRow.setGravity(Gravity.CENTER_VERTICAL);
        contextRow.setPadding(dpActivity(6), 0, dpActivity(2), dpActivity(2));
        panel.addView(contextRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpActivity(34)));

        TextView context = new TextView(this);
        context.setText(UiText.tr(
                "branches · options · contradictions",
                "ветки · варианты · противоречия",
                "větve · možnosti · rozpory"));
        context.setTextColor(Color.rgb(118, 121, 116));
        context.setTextSize(11.5f);
        context.setSingleLine(true);
        context.setGravity(Gravity.CENTER_VERTICAL);
        contextRow.addView(context, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        TextView insert = scratchpadAction(UiText.tr("+ cell", "+ клетка", "+ buňka"));
        contextRow.addView(insert, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpActivity(30)));
        insert.setOnClickListener(v -> insertSelectedCellIntoScratchpad());

        TextView close = scratchpadAction("⌄");
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dpActivity(38), dpActivity(30));
        closeParams.leftMargin = dpActivity(6);
        contextRow.addView(close, closeParams);
        close.setTextSize(18f);
        close.setOnClickListener(v -> hideScratchpad(true));

        scratchpadEditor = new EditText(this);
        scratchpadEditor.setGravity(Gravity.TOP | Gravity.START);
        scratchpadEditor.setTextSize(16f);
        scratchpadEditor.setTextColor(Color.rgb(39, 42, 40));
        scratchpadEditor.setHintTextColor(Color.rgb(145, 147, 142));
        scratchpadEditor.setHint(UiText.tr(
                "Write freely…",
                "Пиши свободно…",
                "Piš volně…"));
        scratchpadEditor.setPadding(dpActivity(10), dpActivity(6), dpActivity(10), dpActivity(12));
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
        styleScratchpadWorkbenchAction(scratchpadCandidateTab, false, gameView != null);
        styleScratchpadWorkbenchAction(scratchpadDraftTab, true, true);
        styleScratchpadWorkbenchAction(scratchpadUndoAction, false,
                gameView != null && !gameView.undoStack.isEmpty());
    }

    void showCandidatesFromScratchpad() {
        if (gameView == null) {
            hideScratchpad(true);
            return;
        }
        hideScratchpad(true);
        if (!gameView.candidateMode) {
            gameView.candidateMode = true;
            gameView.tracker.event("candidate_mode", -1, -1, 1, "workbench_tab");
        }
        gameView.selectedTileId = -1;
        gameView.invalidate();
    }

    TextView scratchpadAction(String label) {'''

replace_regex_once(
    r"    LinearLayout buildScratchpadPanel\(\) \{.*?\n    \}\n\n    TextView scratchpadAction\(String label\) \{",
    new_scratchpad_block,
    "scratchpad panel block",
)

replace_once(
    "        if (scratchpadPanelHeightPx <= 0) scratchpadPanelHeightPx = Math.round(h * 0.25f) + gameView.bottomInset;\n"
    "        scratchpadPanel.setVisibility(View.VISIBLE);\n"
    "        setScratchpadPanelHeight(scratchpadPanelHeightPx, false);\n"
    "        gameView.focusMode = false;\n"
    "        gameView.tracker.event(\"scratchpad_open\", -1, -1, 1, null);\n"
    "        scratchpadEditor.clearFocus();\n",
    "        if (scratchpadPanelHeightPx <= 0) {\n"
    "            // Start compact: enough room to reason without turning the scratchpad into a second screen.\n"
    "            scratchpadPanelHeightPx = Math.round(h * 0.28f) + gameView.bottomInset;\n"
    "        }\n"
    "        scratchpadPanel.setVisibility(View.VISIBLE);\n"
    "        setScratchpadPanelHeight(scratchpadPanelHeightPx, false);\n"
    "        gameView.focusMode = false;\n"
    "        refreshScratchpadWorkbenchState();\n"
    "        gameView.tracker.event(\"scratchpad_open\", -1, -1, 1, null);\n"
    "        scratchpadEditor.clearFocus();\n",
    "scratchpad default height and state refresh",
)

old_drawer = r'''        void drawCandidateDrawer(Canvas c, float top, float height, float w, float h, float minH, float maxH) {
            drawerHandleRect.set(0, Math.max(0, top - dp(8)), w, Math.min(h - bottomInset, top + dp(44)));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(250, Color.red(soft), Color.green(soft), Color.blue(soft)));
            c.drawRect(0, top, w, h, paint);
            paint.setColor(Color.argb(28, 0, 0, 0));
            c.drawRect(0, top, w, top + dp(1), paint);

            paint.setColor(Color.rgb(137, 138, 134));
            RectF grip = new RectF(w / 2f - dp(24), top + dp(8), w / 2f + dp(24), top + dp(12));
            c.drawRoundRect(grip, dp(3), dp(3), paint);

            bankHits.clear();
            undoRect.setEmpty(); candidateRect.setEmpty(); scratchpadRect.setEmpty(); hintRect.setEmpty();
            if (height <= minH + dp(6) || focusMode) return;

            float contentTop = top + dp(25);
            drawGameTools(c, contentTop, w);
            float bankTop = contentTop + dp(52);
            float expansion = Math.max(0f, Math.min(1f, (height - minH) / Math.max(dp(1), maxH - minH)));
            drawBank(c, bankTop, w, h - bottomInset, expansion);
        }'''

new_drawer = r'''        void drawCandidateDrawer(Canvas c, float top, float height, float w, float h, float minH, float maxH) {
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
            float bankTop = contentTop + dp(48);
            float expansion = Math.max(0f, Math.min(1f, (height - minH) / Math.max(dp(1), maxH - minH)));
            drawBank(c, bankTop, w, h - bottomInset, expansion);
        }'''

replace_once(old_drawer, new_drawer, "candidate drawer")

old_tools = r'''        void drawGameTools(Canvas c, float y, float w) {
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
        }'''

new_tools = r'''        void drawGameTools(Canvas c, float y, float w) {
            float side = dp(10);
            float gap = dp(6);
            float h = dp(40);
            float compact = dp(40);
            float primary = (w - side * 2 - gap * 3 - compact * 2) / 2f;

            undoRect.set(side, y, side + compact, y + h);
            candidateRect.set(undoRect.right + gap, y, undoRect.right + gap + primary, y + h);
            scratchpadRect.set(candidateRect.right + gap, y, candidateRect.right + gap + primary, y + h);
            hintRect.set(scratchpadRect.right + gap, y, w - side, y + h);

            // The center pair is the solution workbench; Undo/Hint are secondary actions.
            drawToolButton(c, undoRect, "↶", !undoStack.isEmpty(), false);
            drawToolButton(c, candidateRect, UiText.tr("Candidates", "Кандидаты", "Kandidáti"), true, candidateMode);
            drawToolButton(c, scratchpadRect, UiText.tr("Scratchpad", "Черновик", "Poznámky"), true, false);
            drawToolButton(c, hintRect, "?", true, false);
        }'''

replace_once(old_tools, new_tools, "workbench tool row")

replace_once(
    "            paint.setTextSize(dp(14));\n            Paint.FontMetrics fm = paint.getFontMetrics();\n",
    "            paint.setTextSize(dp(label.length() <= 2 ? 18f : 13.2f));\n            Paint.FontMetrics fm = paint.getFontMetrics();\n",
    "adaptive workbench button text size",
)

replace_once(
    "            String drawerLabel = drawerHidden ? UiText.tr(\"Show candidates\", \"Показать кандидаты\", \"Zobrazit kandidáty\") : UiText.tr(\"Hide candidates\", \"Скрыть кандидаты\", \"Skrýt kandidáty\");\n",
    "            String drawerLabel = drawerHidden ? UiText.tr(\"Show workbench\", \"Показать рабочую панель\", \"Zobrazit pracovní panel\") : UiText.tr(\"Hide workbench\", \"Скрыть рабочую панель\", \"Skrýt pracovní panel\");\n",
    "workbench menu label",
)

replace_once(
    "            // The candidate bank is a bottom drawer. It can be resized continuously or\n"
    "            // collapsed to a thin handle so the board can occupy almost the whole screen.\n",
    "            // The solution workbench is a bottom drawer. It can be resized continuously or\n"
    "            // collapsed to a thin handle so the board can occupy almost the whole screen.\n",
    "workbench drawer comment",
)

PATH.write_text(text, encoding="utf-8")
print("Applied issue #60 solution workbench patch")
