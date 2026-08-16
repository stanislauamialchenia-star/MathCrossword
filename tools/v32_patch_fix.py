from pathlib import Path
p = Path('/tmp/v32_patch.py')
s = p.read_text(encoding='utf-8')
old = """if s.count(end) != 2:\n    raise SystemExit(f'Expected 2 tracker.start endings, got {s.count(end)}')\ns = s.replace(end, end + '\\n            tracker.setModelRoute(HumanRouteComparator.modelRoute(puzzle));', 1)\ns = s.replace(end, end + '\\n                    tracker.setModelRoute(HumanRouteComparator.modelRoute(puzzle));', 1)\n"""
new = """if s.count(end) != 3:\n    raise SystemExit(f'Expected 3 tracker.start endings, got {s.count(end)}')\ns = s.replace(end, end + '\\n            tracker.setModelRoute(HumanRouteComparator.modelRoute(puzzle));')\n"""
if old not in s:
    raise SystemExit('v32 tracker-start patch block not found')
p.write_text(s.replace(old, new, 1), encoding='utf-8')
