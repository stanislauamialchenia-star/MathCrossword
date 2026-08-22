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
'''    void prepareScratchpadForPuzzle(long seed, boolean clear) {
        scratchpadPuzzleSeed = seed;''',
'''    void prepareScratchpadForPuzzle(long seed, boolean clear) {
        scratchpadPuzzleSeed = seed;
        if (clear && gameView != null) gameView.clearScratchpadCellLabels();''',
'clear labels with new scratchpad')

once(
'''        String ref = gameView.selectedScratchpadCellReference();''',
'''        String ref = gameView.ensureSelectedScratchpadCellLabel();''',
'use human cell label')

once(
'''        final Map<Pos, LinkedHashSet<Integer>> candidateNotes = new HashMap<>();
        final List<GameSnapshot> undoStack = new ArrayList<>();''',
'''        final Map<Pos, LinkedHashSet<Integer>> candidateNotes = new HashMap<>();
        final LinkedHashMap<Pos, String> scratchpadCellLabels = new LinkedHashMap<>();
        int scratchpadNextLabel = 0;
        final List<GameSnapshot> undoStack = new ArrayList<>();''',
'scratchpad label state')

once(
'''            drawAllCandidateNotesOverlay(canvas);
            drawLocalFocusOverlay(canvas);''',
'''            drawAllCandidateNotesOverlay(canvas);
            drawScratchpadCellLabelsOverlay(canvas);
            drawLocalFocusOverlay(canvas);''',
'draw label overlay')

once(
'''        void drawCandidateNotes(Canvas c, RectF r, Set<Integer> notes, boolean focused) {''',
'''        void drawScratchpadCellLabelsOverlay(Canvas c) {
            if (!scratchpadOverlayOpen || puzzle == null || scratchpadCellLabels.isEmpty()) return;
            for (Map.Entry<Pos, String> entry : scratchpadCellLabels.entrySet()) {
                Pos pos = entry.getKey();
                if (!puzzle.hidden.contains(pos)) continue;
                float left = originX + pos.x * cellSize;
                float top = originY + pos.y * cellSize;
                float inset = Math.max(dp(1.2f), cellSize * 0.035f);
                float badgeSize = Math.min(dp(17f), Math.max(dp(11f), cellSize * 0.28f));
                RectF badge = new RectF(left + inset, top + inset, left + inset + badgeSize, top + inset + badgeSize);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(238, Color.red(selected), Color.green(selected), Color.blue(selected)));
                c.drawRoundRect(badge, dp(4), dp(4), paint);
                paint.setColor(accent);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                paint.setTextSize(Math.min(dp(10.5f), badgeSize * 0.72f));
                Paint.FontMetrics fm = paint.getFontMetrics();
                c.drawText(entry.getValue(), badge.centerX(), badge.centerY() - (fm.ascent + fm.descent) / 2f, paint);
            }
        }

        void drawCandidateNotes(Canvas c, RectF r, Set<Integer> notes, boolean focused) {''',
'label overlay method')

once(
'''        String selectedScratchpadCellReference() {
            if (puzzle == null || selectedCell == null || !puzzle.hidden.contains(selectedCell)) return null;
            int row = selectedCell.y - puzzle.minY + 1;
            int col = selectedCell.x - puzzle.minX + 1;
            return "R" + row + "C" + col;
        }''',
'''        String ensureSelectedScratchpadCellLabel() {
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
        }''',
'replace coordinate references')

p.write_text(s)
