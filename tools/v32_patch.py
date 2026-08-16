from pathlib import Path

# SessionTracker: persist model route and compare it at session finish.
path = Path('app/src/main/java/com/offline/mathcrossword/SessionTracker.java')
s = path.read_text(encoding='utf-8')

anchor = '''    synchronized void event(String type, int x, int y, int value, String detail) {\n        if (open != null) open.event(type, x, y, value, detail);\n    }\n\n'''
insert = anchor + '''    synchronized void setModelRoute(JSONArray route) {\n        if (open != null) open.setModelRoute(route);\n    }\n\n'''
if anchor not in s:
    raise SystemExit('SessionTracker event anchor not found')
s = s.replace(anchor, insert, 1)

anchor = '''            if (row.optBoolean("contradictionKernel", false)) {\n                out.kernelSessions++;\n                if (row.optInt("contradictionKernelDeepBranches", 0) > 0\n                        || row.optInt("contradictionKernelDepth", 0) >= 4) out.deepKernelSessions++;\n            }\n'''
insert = anchor + '''            JSONObject routeComparison = row.optJSONObject("routeComparison");\n            if (routeComparison != null && routeComparison.optBoolean("available", false)) {\n                out.routeComparedSessions++;\n                if (routeComparison.optBoolean("strongDivergence", false)) out.routeStrongDivergences++;\n                if (routeComparison.optBoolean("alternateEntry", false)) out.routeAlternateEntries++;\n                out.routeAgreementTotal += routeComparison.optDouble("agreementPct", 0.0);\n            }\n'''
if anchor not in s:
    raise SystemExit('SessionTracker analyze route anchor not found')
s = s.replace(anchor, insert, 1)

anchor = '''        out.avgLongestPauseMs = pauseCount == 0 ? 0L : totalLongestPause / pauseCount;\n\n'''
insert = '''        out.avgLongestPauseMs = pauseCount == 0 ? 0L : totalLongestPause / pauseCount;\n        out.avgRouteAgreementPct = out.routeComparedSessions == 0 ? 0.0\n                : out.routeAgreementTotal / out.routeComparedSessions;\n\n'''
if anchor not in s:
    raise SystemExit('SessionTracker average anchor not found')
s = s.replace(anchor, insert, 1)

anchor = '''            s.maxCandidatesInOneCell = row.optInt("maxCandidatesInOneCell", 0);\n            out.recent.add(s);\n'''
insert = '''            s.maxCandidatesInOneCell = row.optInt("maxCandidatesInOneCell", 0);\n            JSONObject routeComparison = row.optJSONObject("routeComparison");\n            if (routeComparison != null && routeComparison.optBoolean("available", false)) {\n                s.routeCompared = true;\n                s.routeAgreementPct = routeComparison.optDouble("agreementPct", 0.0);\n                s.routeEarlyAgreementPct = routeComparison.optDouble("earlyAgreementPct", 0.0);\n                s.routeOrderAgreementPct = routeComparison.optDouble("orderAgreementPct", 0.0);\n                s.routeAlternateEntry = routeComparison.optBoolean("alternateEntry", false);\n                s.routeStrongDivergence = routeComparison.optBoolean("strongDivergence", false);\n            }\n            out.recent.add(s);\n'''
if anchor not in s:
    raise SystemExit('SessionTracker recent route anchor not found')
s = s.replace(anchor, insert, 1)

anchor = '''        out.append("\\n\\nСигналы для проверки модели");\n'''
route_report = '''        JSONObject routeComparison = row.optJSONObject("routeComparison");\n        if (routeComparison != null && routeComparison.optBoolean("available", false)) {\n            out.append("\\n\\nМаршрут HumanSolver ↔ прохождение")\n                    .append("\\nСогласование: ")\n                    .append(String.format(java.util.Locale.US, "%.0f%%", routeComparison.optDouble("agreementPct", 0.0)))\n                    .append(" · начало ")\n                    .append(String.format(java.util.Locale.US, "%.0f%%", routeComparison.optDouble("earlyAgreementPct", 0.0)))\n                    .append(" · порядок ")\n                    .append(String.format(java.util.Locale.US, "%.0f%%", routeComparison.optDouble("orderAgreementPct", 0.0)));\n            double probePct = routeComparison.optDouble("probeReachedEarlyPct", -1.0);\n            if (probePct >= 0.0) out.append(" · pivot вовремя ")\n                    .append(String.format(java.util.Locale.US, "%.0f%%", probePct));\n            out.append("\\nМодель: ").append(HumanRouteComparator.describeModel(row.optJSONArray("modelRoute"), 10));\n            out.append("\\nПрохождение: ").append(HumanRouteComparator.describeActual(routeComparison, 14));\n        }\n\n'''
if anchor not in s:
    raise SystemExit('SessionTracker report signal anchor not found')
s = s.replace(anchor, route_report + anchor, 1)

anchor = '''        if (revisits >= 3) {\n            out.append("\\n• Частые возвраты к уже исследованным клеткам — проверить, это содержательный узел задачи или визуальная/интерфейсная неоднозначность.");\n            signal = true;\n        }\n'''
insert = anchor + '''        if (routeComparison != null && routeComparison.optBoolean("available", false)) {\n            double routeAgreement = routeComparison.optDouble("agreementPct", 100.0);\n            int firstModelStep = routeComparison.optInt("firstModelStep", -1);\n            boolean routeSolved = row.optBoolean("solved", false);\n            if (routeSolved && routeComparison.optBoolean("strongDivergence", false)) {\n                out.append("\\n• Задача решена по порядку, слабо похожему на маршрут HumanSolver — сильный кандидат на альтернативный путь решения.");\n                signal = true;\n            }\n            if (routeComparison.optBoolean("alternateEntry", false)) {\n                out.append("\\n• Первые содержательные действия вошли в задачу не через ранние шаги HumanSolver")\n                        .append(firstModelStep >= 0 ? (" (первое совпадение: шаг модели " + (firstModelStep + 1) + ").") : ".");\n                signal = true;\n            }\n            if (routeComparison.optBoolean("alternateOrder", false) && routeAgreement >= 45.0) {\n                out.append("\\n• Игрок использовал знакомые модели узлы, но в заметно другом порядке — проверить независимые фронты и порядок дедукций.");\n                signal = true;\n            }\n            if (routeAgreement >= 78.0 && !routeComparison.optBoolean("alternateEntry", false)) {\n                out.append("\\n• Порядок прохождения хорошо согласуется с текущим маршрутом HumanSolver.");\n                signal = true;\n            }\n        }\n'''
if anchor not in s:
    raise SystemExit('SessionTracker revisits anchor not found')
s = s.replace(anchor, insert, 1)

anchor = '''        int deepKernelSessions;\n        boolean calibrationReady;\n'''
insert = '''        int deepKernelSessions;\n        int routeComparedSessions;\n        int routeStrongDivergences;\n        int routeAlternateEntries;\n        double routeAgreementTotal;\n        double avgRouteAgreementPct;\n        boolean calibrationReady;\n'''
if anchor not in s:
    raise SystemExit('AnalysisSnapshot route fields anchor not found')
s = s.replace(anchor, insert, 1)

anchor = '''        int maxCandidatesInOneCell;\n    }\n\n    private static final class OpenSession {\n'''
insert = '''        int maxCandidatesInOneCell;\n        boolean routeCompared;\n        double routeAgreementPct;\n        double routeEarlyAgreementPct;\n        double routeOrderAgreementPct;\n        boolean routeAlternateEntry;\n        boolean routeStrongDivergence;\n    }\n\n    private static final class OpenSession {\n'''
if anchor not in s:
    raise SystemExit('SessionSummary route fields anchor not found')
s = s.replace(anchor, insert, 1)

anchor = '''        final JSONArray events = new JSONArray();\n\n        long activeAccumulatedMs = 0L;\n'''
insert = '''        final JSONArray events = new JSONArray();\n        JSONArray modelRoute = new JSONArray();\n\n        long activeAccumulatedMs = 0L;\n'''
if anchor not in s:
    raise SystemExit('OpenSession modelRoute field anchor not found')
s = s.replace(anchor, insert, 1)

anchor = '''        void resume() {\n'''
method = '''        void setModelRoute(JSONArray route) {\n            try { modelRoute = route == null ? new JSONArray() : new JSONArray(route.toString()); }\n            catch (Exception ignored) { modelRoute = new JSONArray(); }\n        }\n\n'''
if anchor not in s:
    raise SystemExit('OpenSession resume anchor not found')
s = s.replace(anchor, method + anchor, 1)

anchor = '''                appendDerivedEventStats(root);\n                root.put("moveNotationVersion", MoveNotation.VERSION);\n'''
insert = '''                appendDerivedEventStats(root);\n                root.put("modelRouteVersion", HumanRouteComparator.VERSION);\n                root.put("modelRoute", modelRoute);\n                root.put("routeComparison", HumanRouteComparator.compare(modelRoute, events));\n                root.put("moveNotationVersion", MoveNotation.VERSION);\n'''
if anchor not in s:
    raise SystemExit('OpenSession finish route anchor not found')
s = s.replace(anchor, insert, 1)
path.write_text(s, encoding='utf-8')

# MainActivity: attach model route and surface compact route stats.
path = Path('app/src/main/java/com/offline/mathcrossword/MainActivity.java')
s = path.read_text(encoding='utf-8')
end = 'puzzle.generationRejects, puzzle.generationRejectSummary);'
if s.count(end) != 2:
    raise SystemExit(f'Expected 2 tracker.start endings, got {s.count(end)}')
s = s.replace(end, end + '\n            tracker.setModelRoute(HumanRouteComparator.modelRoute(puzzle));', 1)
s = s.replace(end, end + '\n                    tracker.setModelRoute(HumanRouteComparator.modelRoute(puzzle));', 1)

anchor = '''            c.drawText("Кандидаты: переходы между клетками " + a.candidateCellSwitches\n                    + " · возвраты " + a.candidateCellRevisits, side, y, paint); y += dp(24);\n\n'''
insert = anchor + '''            if (a.routeComparedSessions > 0) {\n                c.drawText("Маршруты: " + a.routeComparedSessions + " сравн. · согласование "\n                        + String.format(Locale.US, "%.0f%%", a.avgRouteAgreementPct)\n                        + " · сильных расхождений " + a.routeStrongDivergences, side, y, paint); y += dp(24);\n            }\n\n'''
if anchor not in s:
    raise SystemExit('MainActivity analysis route summary anchor not found')
s = s.replace(anchor, insert, 1)

anchor = '''                if (last.candidateCellSwitches > 0 || last.candidateCellRevisits > 0) {\n                    c.drawText("кандидаты: переходов " + last.candidateCellSwitches\n                            + " · возвратов " + last.candidateCellRevisits\n                            + " · максимум в клетке " + last.maxCandidatesInOneCell, side, y, paint);\n                    y += dp(21);\n                }\n'''
insert = anchor + '''                if (last.routeCompared) {\n                    String routeLine = "маршрут: согласование "\n                            + String.format(Locale.US, "%.0f%%", last.routeAgreementPct)\n                            + " · начало " + String.format(Locale.US, "%.0f%%", last.routeEarlyAgreementPct)\n                            + " · порядок " + String.format(Locale.US, "%.0f%%", last.routeOrderAgreementPct);\n                    c.drawText(routeLine, side, y, paint);\n                    y += dp(21);\n                }\n'''
if anchor not in s:
    raise SystemExit('MainActivity last trajectory route anchor not found')
s = s.replace(anchor, insert, 1)

s = s.replace('return info.versionName == null ? "1.31" : info.versionName;', 'return info.versionName == null ? "1.32" : info.versionName;', 1)
s = s.replace('return "1.31";', 'return "1.32";', 1)
s = s.replace('return 31L;', 'return 32L;', 1)
path.write_text(s, encoding='utf-8')

# App version and documentation.
path = Path('app/build.gradle')
s = path.read_text(encoding='utf-8')
if "versionCode 31" not in s or "versionName '1.31'" not in s:
    raise SystemExit('Expected v1.31 build.gradle not found')
s = s.replace('versionCode 31', 'versionCode 32', 1)
s = s.replace("versionName '1.31'", "versionName '1.32'", 1)
path.write_text(s, encoding='utf-8')

Path('V32_ROUTE_COMPARISON.md').write_text('''# MathCrossword v1.32 — HumanSolver route comparison\n\n- Adds a deterministic route model built from the same domain propagation and depth-1/depth-2 contradiction checks used by HumanSolver.\n- Forced cells are represented as waves rather than an arbitrary strict order.\n- Finished local sessions compare the model route with the order of meaningful cells actually touched by the player.\n- Metrics include early-entry agreement, order agreement, early arrival at probe/pivot cells, alternate entry/order and a conservative strong-divergence flag.\n- The Analysis screen shows aggregate route agreement; the detailed latest trajectory shows model route vs observed route and model-check signals.\n- This compares an algorithmic route with interaction data; it does not reconstruct thought or classify personality.\n- Puzzle generation is unchanged; generator v22 remains the mathematical baseline.\n''', encoding='utf-8')
