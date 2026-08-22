from pathlib import Path

p = Path('app/src/main/java/com/offline/mathcrossword/MainActivity.java')
s = p.read_text()

old = '''        void drawScratchpadCellLabelsOverlay(Canvas c) {
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
'''

new = '''        void drawScratchpadCellLabelsOverlay(Canvas c) {
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
'''

count = s.count(old)
if count != 1:
    raise SystemExit(f'expected one scratchpad label renderer, got {count}')

p.write_text(s.replace(old, new, 1))
