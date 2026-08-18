#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SESSION = ROOT / "app/src/main/java/com/offline/mathcrossword/SessionTracker.java"
MAIN = ROOT / "app/src/main/java/com/offline/mathcrossword/MainActivity.java"
GRADLE = ROOT / "app/build.gradle"


def once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


def regex_once(text, pattern, repl, label):
    out, count = re.subn(pattern, repl, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"{label}: expected one regex match, found {count}")
    return out


# ---------------- SessionTracker ----------------
s = SESSION.read_text(encoding="utf-8")
s = once(s,
'''    private final File historyFile;
    private OpenSession open;
''',
'''    private final File historyFile;
    private OpenSession open;
    // One in-memory resumable run is enough for the current Home -> Continue path.
    // Process-death persistence is intentionally a later stage.
    private String resumableRunId;
    private String resumablePuzzleId;
    private int resumableVisitIndex;
''',
"resumable fields")

s = once(s,
'''    synchronized boolean hasOpenSession() {
        return open != null;
    }
''',
'''    synchronized boolean hasOpenSession() {
        return open != null;
    }

    synchronized boolean hasResumableRun() {
        return resumableRunId != null && resumablePuzzleId != null;
    }
''',
"resumable getter")

old_start_head = '''        if (open != null) finish(false, "replaced");
        open = new OpenSession(mode, level, seed, logic, calc, logicScore, calcScore, strategy, hidden, equations,
'''
new_start_head = '''        if (open != null) finish(false, "replaced");
        String nextPuzzleId = RunLifecycle.puzzleId(mode, level, seed, generatorVersion);
        boolean reopened = resumableRunId != null && nextPuzzleId.equals(resumablePuzzleId);
        String existingRunId = reopened ? resumableRunId : null;
        int nextVisitIndex = reopened ? resumableVisitIndex + 1 : 1;
        if (!reopened) clearResumableRun();
        open = new OpenSession(mode, level, seed, logic, calc, logicScore, calcScore, strategy, hidden, equations,
'''
s = once(s, old_start_head, new_start_head, "start reopen detection")

s = once(s,
'''                generationStageTimings, generationMillis, generationAttempts, generationRejects,
                generationRejectSummary, graph);
    }
''',
'''                generationStageTimings, generationMillis, generationAttempts, generationRejects,
                generationRejectSummary, graph, existingRunId, nextVisitIndex, reopened);
        if (reopened) clearResumableRun();
    }
''',
"open session continuation args")

s = once(s,
'''    synchronized void finish(boolean solved, String reason) {
        if (open == null) return;
        JSONObject json = open.finish(solved, reason);
        open = null;
        append(json);
    }
''',
'''    synchronized void finish(boolean solved, String reason) {
        if (open == null) return;
        OpenSession closing = open;
        JSONObject json = closing.finish(solved, reason);
        if (!solved && "home".equals(reason)) {
            resumableRunId = closing.runId;
            resumablePuzzleId = closing.puzzleId;
            resumableVisitIndex = closing.visitIndex;
        } else if (closing.runId.equals(resumableRunId)) {
            clearResumableRun();
        }
        open = null;
        append(json);
    }

    synchronized void discardResumableRun() {
        clearResumableRun();
    }

    private void clearResumableRun() {
        resumableRunId = null;
        resumablePuzzleId = null;
        resumableVisitIndex = 0;
    }
''',
"finish continuation state")

s = once(s,
'''        final String id = UUID.randomUUID().toString(); // legacy sessionId / stored visit row id
        final String runId = UUID.randomUUID().toString();
        final String visitId = UUID.randomUUID().toString();
        final long startedAtEpochMs = System.currentTimeMillis();
        final String puzzleId;
''',
'''        final String id = UUID.randomUUID().toString(); // legacy sessionId / stored visit row id
        final String runId;
        final String visitId = UUID.randomUUID().toString();
        final int visitIndex;
        final long startedAtEpochMs = System.currentTimeMillis();
        final String puzzleId;
''',
"open identity fields")

s = once(s,
'''                    String generationStageTimings, long generationMillis, int generationAttempts,
                    int generationRejects, String generationRejectSummary, GraphAnalyzer.Metrics graph) {
            this.mode = mode;
''',
'''                    String generationStageTimings, long generationMillis, int generationAttempts,
                    int generationRejects, String generationRejectSummary, GraphAnalyzer.Metrics graph,
                    String existingRunId, int visitIndex, boolean reopened) {
            this.runId = existingRunId == null ? UUID.randomUUID().toString() : existingRunId;
            this.visitIndex = Math.max(1, visitIndex);
            this.mode = mode;
''',
"constructor continuation signature")

s = once(s,
'''            this.graphAverageDegree = graph == null ? 0.0 : graph.averageDegree;
            lifecycleEvent("PUZZLE_OPENED", null);
            resume();
''',
'''            this.graphAverageDegree = graph == null ? 0.0 : graph.averageDegree;
            lifecycleEvent(reopened ? "PUZZLE_REOPENED" : "PUZZLE_OPENED", null);
            resume();
''',
"reopened lifecycle event")

s = once(s,
'''                root.put("visitIndex", 1);
''',
'''                root.put("visitIndex", visitIndex);
''',
"visit index output")
SESSION.write_text(s, encoding="utf-8")


# ---------------- MainActivity ----------------
m = MAIN.read_text(encoding="utf-8")
m = once(m,
'''        boolean solved = false;
        boolean candidateMode = false;
''',
'''        boolean solved = false;
        // True only while an unfinished in-memory board is parked on Home.
        boolean resumablePuzzle = false;
        boolean candidateMode = false;
''',
"resumable puzzle field")

m = once(m,
'''        boolean goHomeIfNeeded() {
            if (screen != Screen.HOME) {
                generationToken++;
                generating = false;
                if (screen == Screen.GAME && tracker.hasOpenSession() && !solved) tracker.finish(false, "home");
                screen = Screen.HOME;
                selectedCell = null;
                selectedTileId = -1;
                candidateMode = false;
                invalidate();
                return true;
            }
            return false;
        }

        void loadPathLevel(int newLevel) {
''',
'''        boolean goHomeIfNeeded() {
            if (screen != Screen.HOME) {
                generationToken++;
                generating = false;
                if (screen == Screen.GAME && tracker.hasOpenSession() && !solved) {
                    tracker.finish(false, "home");
                    resumablePuzzle = puzzle != null;
                }
                screen = Screen.HOME;
                selectedCell = null;
                selectedTileId = -1;
                candidateMode = false;
                invalidate();
                return true;
            }
            return false;
        }

        void resumeCurrentPuzzle() {
            if (!resumablePuzzle || puzzle == null || solved) {
                resumablePuzzle = false;
                loadPathLevel(progressLevel);
                return;
            }
            generationToken++;
            generating = false;
            selectedCell = null;
            selectedTileId = -1;
            candidateMode = false;
            screen = Screen.GAME;
            startTrackerForCurrentPuzzle();
            resumablePuzzle = false;
            invalidate();
        }

        void loadPathLevel(int newLevel) {
''',
"home resume method")

# Replace PATH tracker block with shared helper.
m = regex_once(m,
    r'''            String sessionMode = LevelAccess\.sessionMode\(level, progressLevel\);\n            tracker\.start\(sessionMode, level, puzzle\.seed,.*?            tracker\.setModelRoute\(HumanRouteComparator\.modelRoute\(puzzle\)\);\n''',
    '''            resumablePuzzle = false;\n            startTrackerForCurrentPuzzle();\n''',
    "path tracker helper")

# Replace the inline FREE tracker block inside loadFreePuzzle only.
free_anchor = '                    screen = Screen.GAME;\n                    tracker.start("FREE", 0, puzzle.seed,'
if free_anchor not in m:
    raise SystemExit("free inline tracker anchor missing")
start = m.index('                    tracker.start("FREE", 0, puzzle.seed,', m.index('                    screen = Screen.GAME;'))
end_marker = '            tracker.setModelRoute(HumanRouteComparator.modelRoute(puzzle));\n'
end = m.index(end_marker, start) + len(end_marker)
m = m[:start] + '                    resumablePuzzle = false;\n                    startTrackerForCurrentPuzzle();\n' + m[end:]

# Generalize the existing FREE-only helper.
m = once(m,
'''                startTrackerForCurrentFreePuzzle();
            }
        }

        void startTrackerForCurrentFreePuzzle() {
            tracker.start("FREE", 0, puzzle.seed, puzzle.displayLogicLevel, puzzle.displayCalcLevel, puzzle.logicScore, puzzle.calcScore,
''',
'''                resumablePuzzle = false;
                startTrackerForCurrentPuzzle();
            }
        }

        void startTrackerForCurrentPuzzle() {
            String sessionMode = mode == GameMode.PATH ? LevelAccess.sessionMode(level, progressLevel) : "FREE";
            int sessionLevel = mode == GameMode.PATH ? level : 0;
            tracker.start(sessionMode, sessionLevel, puzzle.seed, puzzle.displayLogicLevel, puzzle.displayCalcLevel, puzzle.logicScore, puzzle.calcScore,
''',
"general tracker helper")

# Home primary button reflects parked puzzle and works for FREE too.
m = once(m,
'''            drawBigButton(c, homeContinueRect, generating ? UiText.tr("Generating level ", "Генерирую уровень ", "Generuji úroveň ") + progressLevel + "…" : UiText.tr("Continue — level ", "Продолжить — уровень ", "Pokračovat — úroveň ") + progressLevel, true);
''',
'''            String continueLabel;
            if (generating) {
                continueLabel = UiText.tr("Generating level ", "Генерирую уровень ", "Generuji úroveň ") + progressLevel + "…";
            } else if (resumablePuzzle && puzzle != null && !solved) {
                continueLabel = mode == GameMode.PATH
                        ? UiText.tr("Continue puzzle — level ", "Продолжить задачу — уровень ", "Pokračovat v hlavolamu — úroveň ") + level
                        : UiText.tr("Continue Free Play", "Продолжить свободную игру", "Pokračovat ve volné hře");
            } else {
                continueLabel = UiText.tr("Continue — level ", "Продолжить — уровень ", "Pokračovat — úroveň ") + progressLevel;
            }
            drawBigButton(c, homeContinueRect, continueLabel, true);
''',
"home continue label")

m = once(m,
'''                if (homeContinueRect.contains(x, y)) loadPathLevel(progressLevel);
''',
'''                if (homeContinueRect.contains(x, y)) {
                    if (resumablePuzzle && puzzle != null && !solved) resumeCurrentPuzzle();
                    else loadPathLevel(progressLevel);
                }
''',
"home continue action")

m = once(m,
'''            if (!focusMode && topHomeRect.contains(x, y)) {
                if (tracker.hasOpenSession() && !solved) tracker.finish(false, "home");
                screen = Screen.HOME; invalidate(); return true;
            }
''',
'''            if (!focusMode && topHomeRect.contains(x, y)) {
                if (tracker.hasOpenSession() && !solved) {
                    tracker.finish(false, "home");
                    resumablePuzzle = puzzle != null;
                }
                screen = Screen.HOME; invalidate(); return true;
            }
''',
"game home button parks puzzle")

# New puzzle activation must stop advertising an older parked board.
m = once(m,
'''            puzzle = ready;
            selectedTileId = -1;
''',
'''            puzzle = ready;
            resumablePuzzle = false;
            selectedTileId = -1;
''',
"path new puzzle clears resume")

# The FREE generation assignment occurs once with this exact shape after result success.
m = once(m,
'''                    puzzle = result;
                    selectedTileId = -1;
''',
'''                    puzzle = result;
                    resumablePuzzle = false;
                    selectedTileId = -1;
''',
"free new puzzle clears resume")

# A solved run cannot remain parked as resumable.
m = once(m,
'''            solved = true;
            if (!wasSolved && mode == GameMode.PATH && level == progressLevel) {
''',
'''            solved = true;
            resumablePuzzle = false;
            if (!wasSolved && mode == GameMode.PATH && level == progressLevel) {
''',
"solve clears resume")

m = once(m,
'''                return info.versionName == null ? "1.40" : info.versionName;
            } catch (android.content.pm.PackageManager.NameNotFoundException ex) {
                return "1.40";
''',
'''                return info.versionName == null ? "1.41" : info.versionName;
            } catch (android.content.pm.PackageManager.NameNotFoundException ex) {
                return "1.41";
''',
"version name fallback")
m = once(m, '                return 40L;\n', '                return 41L;\n', "version code fallback")
MAIN.write_text(m, encoding="utf-8")

# ---------------- Version ----------------
g = GRADLE.read_text(encoding="utf-8")
g = once(g,
'''        versionCode 40
        versionName '1.40'
''',
'''        versionCode 41
        versionName '1.41'
''',
"version bump")
GRADLE.write_text(g, encoding="utf-8")

print("Applied in-memory PuzzleRun resume migration for v1.41")
