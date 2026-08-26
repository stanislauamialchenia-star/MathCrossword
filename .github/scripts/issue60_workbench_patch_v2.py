from pathlib import Path

base = Path(".github/scripts/issue60_workbench_patch.py")
source = base.read_text(encoding="utf-8")

old = '''replace_once(
    "            paint.setTextSize(dp(14));\\n            Paint.FontMetrics fm = paint.getFontMetrics();\\n",
    "            paint.setTextSize(dp(label.length() <= 2 ? 18f : 13.2f));\\n            Paint.FontMetrics fm = paint.getFontMetrics();\\n",
    "adaptive workbench button text size",
)'''

new = '''replace_once(
    "            paint.setTypeface(active ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);\\n"
    "            paint.setTextSize(dp(14));\\n"
    "            Paint.FontMetrics fm = paint.getFontMetrics();\\n",
    "            paint.setTypeface(active ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);\\n"
    "            paint.setTextSize(dp(label.length() <= 2 ? 18f : 13.2f));\\n"
    "            Paint.FontMetrics fm = paint.getFontMetrics();\\n",
    "adaptive workbench button text size",
)'''

count = source.count(old)
if count != 1:
    raise SystemExit(f"v2 patch bootstrap: expected one matcher block, found {count}")

source = source.replace(old, new, 1)
exec(compile(source, str(base), "exec"), {"__name__": "__main__"})
