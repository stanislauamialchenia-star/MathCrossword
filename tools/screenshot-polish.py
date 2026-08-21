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
    '            float side = dp(26);\n            float buttonH = dp(52);\n            float gap = dp(9);\n            float firstTop = Math.max(y + dp(76), h * 0.29f);\n            homeContinueRect.set(side, firstTop, w - side, firstTop + buttonH);\n            homeLevelsRect.set(side, homeContinueRect.bottom + gap, w - side, homeContinueRect.bottom + gap + buttonH);\n            homeFreeRect.set(side, homeLevelsRect.bottom + gap, w - side, homeLevelsRect.bottom + gap + buttonH);\n            homeLibraryRect.set(side, homeFreeRect.bottom + gap, w - side, homeFreeRect.bottom + gap + buttonH);\n            homeAnalysisRect.set(side, homeLibraryRect.bottom + gap, w - side, homeLibraryRect.bottom + gap + buttonH);',
    '            float side = dp(26);\n            float primaryH = dp(52);\n            float secondaryH = dp(48);\n            float gap = dp(8);\n            float firstTop = Math.max(y + dp(68), h * 0.275f);\n            homeContinueRect.set(side, firstTop, w - side, firstTop + primaryH);\n            homeLevelsRect.set(side, homeContinueRect.bottom + gap, w - side, homeContinueRect.bottom + gap + secondaryH);\n            homeFreeRect.set(side, homeLevelsRect.bottom + gap, w - side, homeLevelsRect.bottom + gap + secondaryH);\n            homeLibraryRect.set(side, homeFreeRect.bottom + gap, w - side, homeFreeRect.bottom + gap + secondaryH);\n            homeAnalysisRect.set(side, homeLibraryRect.bottom + gap, w - side, homeLibraryRect.bottom + gap + secondaryH);',
    'home geometry')

once('                footerCenterX -= dp(14);\n', '', 'footer centering')
footer_start = text.index('            homePrivacyRect.set(dp(18), footerY - dp(50)')
footer_end = text.index('        }\n\n        void drawLevels', footer_start)
new_footer = '''            homePrivacyRect.set(dp(18), footerY - dp(42), w - dp(18), footerY - dp(16));
            paint.setColor(Color.rgb(104, 106, 102));
            paint.setTextSize(dp(11.2f));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            c.drawText(UiText.tr("Privacy", "Конфиденциальность", "Soukromí"), footerCenterX, footerY - dp(23), paint);

            paint.setColor(Color.rgb(132, 133, 129));
            paint.setTextSize(dp(11.4f));
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("v" + installedVersionName(), footerCenterX, footerY + dp(3), paint);
'''
text = text[:footer_start] + new_footer + text[footer_end:]

once(
    '                    boolean noted = candidateMode && selectedCell != null && selectedNotes.contains(t.value);\n                    paint.setStyle(Paint.Style.FILL);\n                    paint.setColor((t.id == selectedTileId || noted) ? selected : board);\n                    c.drawRoundRect(r, dp(5), dp(5), paint);\n                    stroke.setColor(noted ? accent : ink);\n                    stroke.setStrokeWidth(dp(noted ? 2.4f : 1.8f));\n                    c.drawRoundRect(r, dp(5), dp(5), stroke);',
    '                    boolean noted = candidateMode && selectedCell != null && selectedNotes.contains(t.value);\n                    paint.setStyle(Paint.Style.FILL);\n                    if (t.id == selectedTileId) paint.setColor(selected);\n                    else if (noted) paint.setColor(Color.rgb(245, 248, 244));\n                    else paint.setColor(board);\n                    c.drawRoundRect(r, dp(5), dp(5), paint);\n                    stroke.setColor(noted ? Color.rgb(104, 130, 110) : ink);\n                    stroke.setStrokeWidth(dp(noted ? 2.0f : 1.8f));\n                    c.drawRoundRect(r, dp(5), dp(5), stroke);',
    'candidate bank emphasis')

once('            float solvedDrawerHeight = dp(214) + bottomInset;',
     '            float solvedDrawerHeight = dp(178) + bottomInset;',
     'solved reserved height')
once('            float sheetTop = h - bottomInset - dp(214);',
     '            float sheetTop = h - bottomInset - dp(178);',
     'solved sheet height')
once(
    '            float side = dp(38);\n            solvedInsightRect.set(side, sheetTop + dp(55), w - side, sheetTop + dp(99));\n            drawToolButton(c, solvedInsightRect,\n                    UiText.tr("How you solved it", "Как ты решил", "Jak jsi řešil"), true, false);\n\n            nextLevelRect.set(side, sheetTop + dp(119), w - side, h - bottomInset - dp(24));',
    '            float side = dp(38);\n            solvedInsightRect.set(side, sheetTop + dp(46), w - side, sheetTop + dp(88));\n            paint.setColor(accent);\n            paint.setTextAlign(Paint.Align.CENTER);\n            paint.setTypeface(android.graphics.Typeface.DEFAULT);\n            paint.setTextSize(dp(13.5f));\n            c.drawText(UiText.tr("How you solved it  →", "Как ты решил  →", "Jak jsi řešil  →"),\n                    w / 2f, sheetTop + dp(72), paint);\n\n            nextLevelRect.set(side, sheetTop + dp(98), w - side, h - bottomInset - dp(22));',
    'solved secondary action')

path.write_text(text)
