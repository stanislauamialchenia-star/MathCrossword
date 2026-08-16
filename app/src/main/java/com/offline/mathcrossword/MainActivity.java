package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class MainActivity extends Activity {
    GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gameView = new GameView(this);
        setContentView(gameView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameView != null) gameView.onHostResume();
    }

    @Override
    protected void onPause() {
        if (gameView != null) gameView.onHostPause();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (gameView != null && gameView.goHomeIfNeeded()) return;
        super.onBackPressed();
    }

    static final class BankHit {
        final RectF rect;
        final int tileId;
        BankHit(RectF rect, int tileId) { this.rect = rect; this.tileId = tileId; }
    }

    enum Screen { HOME, LEVELS, FREE_SETUP, LIBRARY, ANALYSIS, GAME }
    enum GameMode { PATH, FREE }

    static final class GameSnapshot {
        final Map<Pos, Integer> placed = new HashMap<>();
        final Map<Pos, LinkedHashSet<Integer>> notes = new HashMap<>();

        GameSnapshot(Map<Pos, Integer> placedTile, Map<Pos, LinkedHashSet<Integer>> candidateNotes) {
            placed.putAll(placedTile);
            for (Map.Entry<Pos, LinkedHashSet<Integer>> e : candidateNotes.entrySet()) {
                notes.put(e.getKey(), new LinkedHashSet<>(e.getValue()));
            }
        }
    }

    static final class GameView extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        final SharedPreferences prefs;
        final SessionTracker tracker;
        final Random freeSeedRandom = new Random();

        Screen screen = Screen.HOME;
        GameMode mode = GameMode.PATH;
        Puzzle puzzle;
        int level;
        int progressLevel;
        int levelPage = 0;
        int selectedTileId = -1;
        Pos selectedCell = null;
        boolean solved = false;
        boolean candidateMode = false;
        int hintStage = 0;
        boolean focusMode = false;
        float candidateDrawerHeight = -1f;
        float lastExpandedDrawerHeight = -1f;
        boolean draggingCandidateDrawer = false;
        float drawerDragStartY = 0f;
        float drawerDragStartHeight = 0f;
        final Map<Pos, LinkedHashSet<Integer>> candidateNotes = new HashMap<>();
        final List<GameSnapshot> undoStack = new ArrayList<>();
        int topInset = 0;
        int bottomInset = 0;
        float cellSize, originX, originY;

        // Board viewport: fit-to-screen is the baseline; pinch/pan is only a temporary visual lens.
        float boardZoom = 1f, boardPanX = 0f, boardPanY = 0f;
        float boardViewportTop = 0f, boardViewportBottom = 0f;
        boolean boardPanGesture = false, boardGestureMoved = false, pinching = false;
        float boardDownX = 0f, boardDownY = 0f, boardPanStartX = 0f, boardPanStartY = 0f;
        float pinchStartDistance = 0f, pinchStartZoom = 1f;
        long lastBoardTapTime = 0L;
        float lastBoardTapX = 0f, lastBoardTapY = 0f;
        Pos longPressPos = null, localFocusCell = null;
        final Set<Pos> localFocusPositions = new HashSet<>();
        Runnable boardLongPressRunnable = null;
        boolean boardLongPressTriggered = false;

        // A downloaded APK stays pending while Android asks once for install-from-this-source permission.
        Uri pendingInstallUri = null;
        String pendingInstallVersion = null;

        int freeLogic = 5;
        int freeCalc = 4;
        int freeSize = 1;
        int freeMaxIndex = 1;
        int freeStrategyIndex = 4;
        int libraryEntryIndex = 0;
        final SolutionStrategy[] strategies = SolutionStrategy.values();
        final int[] freeMaxValues = {20, 100, 500, 1000};
        final LinkedHashSet<Character> freeOps = new LinkedHashSet<>();
        long lastFreeSeed = 0L;
        volatile boolean generating = false;
        long generationToken = 0L;
        volatile Puzzle prefetchedPathPuzzle = null;
        volatile int prefetchedPathLevel = -1;
        volatile boolean pathPrefetchRunning = false;
        volatile int pathPrefetchTarget = -1;

        final List<BankHit> bankHits = new ArrayList<>();
        final RectF homeContinueRect = new RectF();
        final RectF homeLevelsRect = new RectF();
        final RectF homeFreeRect = new RectF();
        final RectF homeLibraryRect = new RectF();
        final RectF homeAnalysisRect = new RectF();
        final RectF homeUpdateRect = new RectF();
        String updateStatus = "обновление не проверено";
        boolean updateChecking = false;
        final RectF topHomeRect = new RectF();
        final RectF resetRect = new RectF();
        final RectF menuRect = new RectF();
        final RectF focusMenuRect = new RectF();
        final RectF drawerHandleRect = new RectF();
        final RectF undoRect = new RectF();
        final RectF candidateRect = new RectF();
        final RectF hintRect = new RectF();
        final RectF nextLevelRect = new RectF();
        final RectF freeGenerateRect = new RectF();
        final RectF[] logicRects = {new RectF(), new RectF(), new RectF(), new RectF(), new RectF(),
                new RectF(), new RectF(), new RectF(), new RectF(), new RectF()};
        final RectF[] calcRects = {new RectF(), new RectF(), new RectF(), new RectF(), new RectF(),
                new RectF(), new RectF(), new RectF(), new RectF(), new RectF()};
        final RectF[] sizeRects = {new RectF(), new RectF(), new RectF()};
        final RectF[] maxRects = {new RectF(), new RectF(), new RectF(), new RectF()};
        final RectF[] opRects = {new RectF(), new RectF(), new RectF(), new RectF(), new RectF()};
        final RectF[] strategyRects = {new RectF(), new RectF(), new RectF(), new RectF(), new RectF()};
        final RectF[] levelRects = new RectF[100];
        final RectF levelsPrevPageRect = new RectF();
        final RectF levelsNextPageRect = new RectF();
        final RectF libraryPrevRect = new RectF();
        final RectF libraryNextRect = new RectF();

        final int bg = Color.rgb(238, 248, 235);
        final int board = Color.rgb(255, 255, 255);
        final int ink = Color.rgb(42, 45, 43);
        final int green = Color.rgb(211, 244, 186);
        final int red = Color.rgb(255, 171, 160);
        final int selected = Color.rgb(255, 244, 190);
        final int accent = Color.rgb(46, 136, 62);
        final int soft = Color.rgb(245, 251, 242);

        GameView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE);
            tracker = new SessionTracker(context);
            progressLevel = Math.max(1, prefs.getInt("currentLevel", 1));
            level = progressLevel;
            for (int i = 0; i < levelRects.length; i++) levelRects[i] = new RectF();
            freeOps.add('+'); freeOps.add('-');
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setOnApplyWindowInsetsListener((v, insets) -> {
                    topInset = insets.getSystemWindowInsetTop();
                    bottomInset = insets.getSystemWindowInsetBottom();
                    invalidate();
                    return insets;
                });
                requestApplyInsets();
            }
        }

        void onHostResume() {
            if (screen == Screen.GAME && tracker.hasOpenSession()) tracker.resume();
            if (pendingInstallUri != null) maybeInstallPendingUpdate();
        }

        void onHostPause() {
            tracker.pause();
        }

        boolean goHomeIfNeeded() {
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
            final int targetLevel = Math.max(1, newLevel);
            if (generating) return;

            Puzzle cached = null;
            if (prefetchedPathLevel == targetLevel && prefetchedPathPuzzle != null) {
                cached = prefetchedPathPuzzle;
                prefetchedPathPuzzle = null;
                prefetchedPathLevel = -1;
            }
            if (cached != null) {
                activatePathPuzzle(targetLevel, cached);
                return;
            }

            final long token = ++generationToken;
            generating = true;
            invalidate();
            new Thread(() -> {
                Puzzle ready = null;
                try { ready = PuzzleGenerator.generatePath(targetLevel); }
                catch (RuntimeException ignored) { }
                final Puzzle result = ready;
                post(() -> {
                    if (token != generationToken) return;
                    generating = false;
                    if (result == null) {
                        Toast.makeText(getContext(), "Не удалось собрать уровень — попробуй ещё раз", Toast.LENGTH_SHORT).show();
                        invalidate();
                        return;
                    }
                    activatePathPuzzle(targetLevel, result);
                });
            }, "mathcrossword-path-generator").start();
        }

        void activatePathPuzzle(int newLevel, Puzzle ready) {
            level = Math.max(1, newLevel);
            mode = GameMode.PATH;
            puzzle = ready;
            selectedTileId = -1;
            selectedCell = null;
            solved = false;
            candidateMode = false;
            hintStage = 0;
            candidateNotes.clear();
            undoStack.clear();
            resetBoardViewport();
            screen = Screen.GAME;
            String sessionMode = LevelAccess.sessionMode(level, progressLevel);
            tracker.start(sessionMode, level, puzzle.seed, puzzle.displayLogicLevel, puzzle.displayCalcLevel, puzzle.logicScore, puzzle.calcScore,
                    puzzle.solutionStrategy.name(), puzzle.hidden.size(), puzzle.equations.size(),
                    puzzle.ratedDisplayLogic, puzzle.reasoningSteps, puzzle.reasoningDepth,
                    puzzle.basicForced, puzzle.basicRemaining, puzzle.maxForcedCascade,
                    puzzle.maxResolvedAfterOneCell, puzzle.maxResolvedFractionAfterOneCell,
                    puzzle.vulnerableSingleCells, puzzle.maxResolvedAfterOneEquation, puzzle.maxResolvedFractionAfterOneEquation,
                    puzzle.generatorVersion,
                    puzzle.generationStage, puzzle.strategyTargetMatched, puzzle.generationStrategy.name(),
                    puzzle.generatorConstructor, puzzle.generatorFamily,
                    puzzle.deceptiveDecoyCount, puzzle.deceptiveDecoySupportMax,
                    puzzle.contextualDecoyCount, puzzle.resourceConflictDecoyCount, puzzle.contextualDecoyConstraintSupportMax,
                    puzzle.contextualDecoyDepthMax, puzzle.contextualDecoyInformationGainMax,
                    puzzle.branchPivotCount, puzzle.branchGoodPivotCount, puzzle.branchSeriousFalseBranches,
                    puzzle.branchDepth2RefutableBranches, puzzle.branchDepth2SurvivingBranches,
                    puzzle.branchMaxWidth, puzzle.branchMaxInformationGain,
                    puzzle.reasoningFronts, puzzle.reasoningFrontBalance, puzzle.reasoningLargestFrontFraction,
                    puzzle.reasoningFrontBottleneckDegree,
                    puzzle.contradictionKernel, puzzle.contradictionKernelAddedDecoy, puzzle.contradictionKernelDepth,
                    puzzle.contradictionKernelFamily, puzzle.contradictionKernelBranches, puzzle.contradictionKernelPivots,
                    puzzle.contradictionKernelDepth2Branches, puzzle.contradictionKernelDepth3Branches,
                    puzzle.contradictionKernelDeepBranches, puzzle.contradictionKernelMaxRemaining,
                    puzzle.generationStageTimings,
                    puzzle.generationMillis, puzzle.generationAttempts,
                    puzzle.generationRejects, puzzle.generationRejectSummary);
            invalidate();
            prefetchPathLevel(level + 1);
        }

        void prefetchPathLevel(int targetLevel) {
            targetLevel = Math.max(1, targetLevel);
            if (prefetchedPathLevel == targetLevel && prefetchedPathPuzzle != null) return;
            if (pathPrefetchRunning && pathPrefetchTarget == targetLevel) return;
            pathPrefetchRunning = true;
            pathPrefetchTarget = targetLevel;
            final int wanted = targetLevel;
            new Thread(() -> {
                Puzzle ready = null;
                try { ready = PuzzleGenerator.generatePath(wanted); }
                catch (RuntimeException ignored) { }
                final Puzzle result = ready;
                post(() -> {
                    if (pathPrefetchTarget == wanted) {
                        pathPrefetchRunning = false;
                        if (result != null) {
                            prefetchedPathPuzzle = result;
                            prefetchedPathLevel = wanted;
                        }
                    }
                });
            }, "mathcrossword-path-prefetch").start();
        }

        void loadFreePuzzle() {
            if (generating) return;
            mode = GameMode.FREE;

            final int logic = freeLogic;
            final int calc = freeCalc;
            final int size = freeSize;
            final int maxNumber = freeMaxValues[freeMaxIndex];
            final LinkedHashSet<Character> ops = new LinkedHashSet<>(freeOps);
            final SolutionStrategy solutionStrategy = strategies[freeStrategyIndex];
            final long baseSeed = System.nanoTime() ^ freeSeedRandom.nextLong();
            lastFreeSeed = baseSeed;
            final long token = ++generationToken;
            generating = true;
            invalidate();

            new Thread(() -> {
                Puzzle ready = null;
                long acceptedSeed = baseSeed;
                for (int retry = 0; retry < 3 && ready == null; retry++) {
                    long seed = PuzzleGenerator.mix64(baseSeed + retry * 0x9E3779B97F4A7C15L);
                    try {
                        ready = PuzzleGenerator.generateFree(logic, calc, size, maxNumber, ops, seed, solutionStrategy);
                        acceptedSeed = seed;
                    } catch (RuntimeException ignored) { }
                }

                final Puzzle result = ready;
                final long resultSeed = acceptedSeed;
                post(() -> {
                    if (token != generationToken) return;
                    generating = false;
                    if (result == null) {
                        Toast.makeText(getContext(), "Не нашёл достаточно сильную головоломку — нажми ещё раз", Toast.LENGTH_SHORT).show();
                        invalidate();
                        return;
                    }
                    lastFreeSeed = resultSeed;
                    puzzle = result;
                    selectedTileId = -1;
                    selectedCell = null;
                    solved = false;
                    candidateMode = false;
                    candidateNotes.clear();
                    undoStack.clear();
                    screen = Screen.GAME;
                    tracker.start("FREE", 0, puzzle.seed, puzzle.displayLogicLevel, puzzle.displayCalcLevel, puzzle.logicScore, puzzle.calcScore,
                            puzzle.solutionStrategy.name(), puzzle.hidden.size(), puzzle.equations.size(),
                            puzzle.ratedDisplayLogic, puzzle.reasoningSteps, puzzle.reasoningDepth,
                            puzzle.basicForced, puzzle.basicRemaining, puzzle.maxForcedCascade,
                    puzzle.maxResolvedAfterOneCell, puzzle.maxResolvedFractionAfterOneCell,
                    puzzle.vulnerableSingleCells, puzzle.maxResolvedAfterOneEquation, puzzle.maxResolvedFractionAfterOneEquation,
                    puzzle.generatorVersion,
                            puzzle.generationStage, puzzle.strategyTargetMatched, puzzle.generationStrategy.name(),
                            puzzle.generatorConstructor, puzzle.generatorFamily,
                    puzzle.deceptiveDecoyCount, puzzle.deceptiveDecoySupportMax,
                    puzzle.contextualDecoyCount, puzzle.resourceConflictDecoyCount, puzzle.contextualDecoyConstraintSupportMax,
                    puzzle.contextualDecoyDepthMax, puzzle.contextualDecoyInformationGainMax,
                    puzzle.branchPivotCount, puzzle.branchGoodPivotCount, puzzle.branchSeriousFalseBranches,
                    puzzle.branchDepth2RefutableBranches, puzzle.branchDepth2SurvivingBranches,
                    puzzle.branchMaxWidth, puzzle.branchMaxInformationGain,
                    puzzle.reasoningFronts, puzzle.reasoningFrontBalance, puzzle.reasoningLargestFrontFraction,
                    puzzle.reasoningFrontBottleneckDegree,
                    puzzle.contradictionKernel, puzzle.contradictionKernelAddedDecoy, puzzle.contradictionKernelDepth,
                    puzzle.contradictionKernelFamily, puzzle.contradictionKernelBranches, puzzle.contradictionKernelPivots,
                    puzzle.contradictionKernelDepth2Branches, puzzle.contradictionKernelDepth3Branches,
                    puzzle.contradictionKernelDeepBranches, puzzle.contradictionKernelMaxRemaining,
                    puzzle.generationStageTimings,
                            puzzle.generationMillis, puzzle.generationAttempts,
                            puzzle.generationRejects, puzzle.generationRejectSummary);
                    invalidate();
                });
            }, "mathcrossword-generator").start();
        }

        void resetCurrentPuzzle() {
            if (puzzle == null) return;
            puzzle.placedTile.clear();
            for (Tile t : puzzle.tiles) t.used = false;
            selectedTileId = -1;
            selectedCell = null;
            solved = false;
            candidateMode = false;
            hintStage = 0;
            candidateNotes.clear();
            undoStack.clear();
            resetBoardViewport();
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(bg);
            if (screen == Screen.HOME) drawHome(canvas);
            else if (screen == Screen.LEVELS) drawLevels(canvas);
            else if (screen == Screen.FREE_SETUP) drawFreeSetup(canvas);
            else if (screen == Screen.LIBRARY) drawLibrary(canvas);
            else if (screen == Screen.ANALYSIS) drawAnalysis(canvas);
            else drawGame(canvas);
        }

        void drawHome(Canvas c) {
            float w = getWidth(), h = getHeight();
            float y = topInset + dp(82);

            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(29));
            c.drawText("Математический", w / 2f, y, paint);
            paint.setTextSize(dp(27));
            c.drawText("кроссворд", w / 2f, y + dp(34), paint);

            // Keep the home screen quiet: product/update metadata lives in a small footer,
            // not as another primary action competing with play.
            float side = dp(26);
            float buttonH = dp(52);
            float gap = dp(9);
            float firstTop = Math.max(y + dp(76), h * 0.29f);
            homeContinueRect.set(side, firstTop, w - side, firstTop + buttonH);
            homeLevelsRect.set(side, homeContinueRect.bottom + gap, w - side, homeContinueRect.bottom + gap + buttonH);
            homeFreeRect.set(side, homeLevelsRect.bottom + gap, w - side, homeLevelsRect.bottom + gap + buttonH);
            homeLibraryRect.set(side, homeFreeRect.bottom + gap, w - side, homeFreeRect.bottom + gap + buttonH);
            homeAnalysisRect.set(side, homeLibraryRect.bottom + gap, w - side, homeLibraryRect.bottom + gap + buttonH);

            drawBigButton(c, homeContinueRect, generating ? "Генерирую уровень " + progressLevel + "…" : "Продолжить — уровень " + progressLevel, true);
            drawBigButton(c, homeLevelsRect, "Выбрать уровень", false);
            drawBigButton(c, homeFreeRect, "Свободная игра", false);
            drawBigButton(c, homeLibraryRect, "Библиотека решений", false);
            drawBigButton(c, homeAnalysisRect, "Анализ прохождений", false);

            float footerY = h - bottomInset - dp(34);
            homeUpdateRect.set(w - dp(62), footerY - dp(20), w - dp(18), footerY + dp(20));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(115, 255, 255, 255));
            c.drawRoundRect(homeUpdateRect, dp(12), dp(12), paint);
            paint.setColor(updateChecking ? Color.rgb(120, 130, 121) : ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(dp(23));
            Paint.FontMetrics updateFm = paint.getFontMetrics();
            c.drawText(updateChecking ? "…" : "↻", homeUpdateRect.centerX(),
                    homeUpdateRect.centerY() - (updateFm.ascent + updateFm.descent) / 2f, paint);

            paint.setColor(Color.rgb(108, 119, 110));
            paint.setTextSize(dp(11.8f));
            paint.setTextAlign(Paint.Align.CENTER);
            String versionLine = "v" + installedVersionName() + " (" + installedVersionCode() + ") · офлайн · без рекламы";
            if (!"обновление не проверено".equals(updateStatus)) versionLine += " · " + updateStatus;
            c.drawText(versionLine, w / 2f - dp(14), footerY - dp(7), paint);
            paint.setTextSize(dp(10.8f));
            c.drawText("данные и история решения хранятся локально", w / 2f - dp(14), footerY + dp(11), paint);
        }

        void drawLevels(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            float top = topInset + dp(14);
            topHomeRect.set(dp(12), top, dp(62), top + dp(48));
            drawIconButton(c, topHomeRect, "‹");

            int maxPage = maxUnlockedLevelPage();
            levelPage = Math.max(0, Math.min(levelPage, maxPage));
            int first = levelPage * 100 + 1;
            int last = first + 99;

            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(22));
            c.drawText("Уровни " + first + "–" + last, w / 2f, top + dp(32), paint);

            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(dp(12.5f));
            paint.setColor(Color.rgb(95, 106, 97));
            c.drawText("Прогресс: уровень " + progressLevel + " · переигрывание прогресс не сбрасывает",
                    w / 2f, top + dp(57), paint);

            float side = dp(18);
            float gap = dp(5);
            float gridTop = top + dp(82);
            float cell = (w - side * 2 - gap * 9) / 10f;
            cell = Math.min(cell, dp(37));
            float gridW = cell * 10 + gap * 9;
            float left = (w - gridW) / 2f;
            for (int i = 0; i < 100; i++) {
                int row = i / 10, col = i % 10;
                float l = left + col * (cell + gap);
                float t = gridTop + row * (cell + gap);
                RectF r = levelRects[i];
                r.set(l, t, l + cell, t + cell);
                int n = first + i;
                if (n < progressLevel) paint.setColor(green);
                else if (n == progressLevel) paint.setColor(selected);
                else paint.setColor(board);
                c.drawRoundRect(r, dp(7), dp(7), paint);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(n == progressLevel ? dp(2) : dp(1));
                stroke.setColor(n == progressLevel ? accent : Color.rgb(198, 210, 199));
                c.drawRoundRect(r, dp(7), dp(7), stroke);
                paint.setColor(ink);
                paint.setTextSize(dp(n >= 100 ? 11.5f : 12.5f));
                paint.setTypeface(n == progressLevel ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
                Paint.FontMetrics fm = paint.getFontMetrics();
                c.drawText(Integer.toString(n), r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, paint);
            }

            float navTop = Math.min(h - bottomInset - dp(58), gridTop + 10 * (cell + gap) + dp(13));
            levelsPrevPageRect.set(side, navTop, w / 2f - dp(6), navTop + dp(46));
            levelsNextPageRect.set(w / 2f + dp(6), navTop, w - side, navTop + dp(46));
            drawBigButton(c, levelsPrevPageRect, levelPage > 0 ? "← 100" : "—", false);
            drawBigButton(c, levelsNextPageRect, levelPage < maxPage ? "+100 →" : "Следующие закрыты", false);
        }

        int maxUnlockedLevelPage() {
            return LevelAccess.maxUnlockedPage(progressLevel);
        }

        void drawLibrary(Canvas c) {
            float w = getWidth();
            float top = topInset + dp(14);
            topHomeRect.set(dp(12), top, dp(62), top + dp(48));
            drawIconButton(c, topHomeRect, "‹");

            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(23));
            c.drawText("Библиотека решений", w / 2f, top + dp(33), paint);

            SolutionLibrary.Entry entry = SolutionLibrary.ENTRIES.get(libraryEntryIndex);
            float side = dp(22);
            float y = top + dp(82);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(dp(21));
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setColor(ink);
            y = drawWrappedText(c, entry.title, side, y, w - side * 2, dp(26));

            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(dp(15));
            paint.setColor(Color.rgb(65, 78, 68));
            y = drawWrappedText(c, entry.idea, side, y + dp(12), w - side * 2, dp(21));

            paint.setColor(ink);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(16));
            c.drawText("Пример", side, y + dp(22), paint);
            paint.setTypeface(android.graphics.Typeface.MONOSPACE);
            paint.setTextSize(dp(14));
            paint.setColor(Color.rgb(35, 48, 38));
            y = drawWrappedText(c, entry.example, side, y + dp(47), w - side * 2, dp(20));

            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(15.5f));
            paint.setColor(ink);
            c.drawText("Как действовать", side, y + dp(23), paint);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(dp(14));
            paint.setColor(Color.rgb(75, 84, 76));
            y = drawWrappedText(c, entry.steps, side, y + dp(47), w - side * 2, dp(19));

            float navTop = Math.min(getHeight() - bottomInset - dp(70), Math.max(y + dp(22), top + dp(610)));
            libraryPrevRect.set(side, navTop, w / 2f - dp(6), navTop + dp(50));
            libraryNextRect.set(w / 2f + dp(6), navTop, w - side, navTop + dp(50));
            drawBigButton(c, libraryPrevRect, "← Предыдущий", false);
            drawBigButton(c, libraryNextRect, "Следующий →", false);

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(12));
            paint.setColor(Color.rgb(105, 115, 106));
            c.drawText((libraryEntryIndex + 1) + " / " + SolutionLibrary.ENTRIES.size(),
                    w / 2f, navTop - dp(10), paint);
        }

        void drawAnalysis(Canvas c) {
            float w = getWidth();
            float top = topInset + dp(14);
            topHomeRect.set(dp(12), top, dp(62), top + dp(48));
            drawIconButton(c, topHomeRect, "‹");

            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(23));
            c.drawText("Анализ прохождений", w / 2f, top + dp(33), paint);

            SessionTracker.AnalysisSnapshot a = tracker.analyze();
            float side = dp(23);
            float y = top + dp(88);
            paint.setTextAlign(Paint.Align.LEFT);
            if (a.sessions == 0) {
                paint.setTypeface(android.graphics.Typeface.DEFAULT);
                paint.setTextSize(dp(16));
                paint.setColor(Color.rgb(75, 85, 77));
                drawWrappedText(c, "Пока данных нет. Заверши или покинь несколько головоломок — здесь появится краткий итог. Таймер учитывает только активное время: сворачивание приложения не считается.",
                        side, y, w - side * 2, dp(23));
                return;
            }

            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(17));
            c.drawText("Краткий итог", side, y, paint);
            y += dp(31);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(dp(15));
            paint.setColor(Color.rgb(55, 67, 58));
            c.drawText("Сессий: " + a.sessions + "   Решено: " + a.solved, side, y, paint); y += dp(24);
            c.drawText("Среднее время решённой: " + formatDuration(a.avgSolvedMs), side, y, paint); y += dp(24);
            c.drawText("Среднее событий: " + String.format(Locale.US, "%.1f", a.avgEvents), side, y, paint); y += dp(24);
            c.drawText("До первого действия: " + formatDuration(a.avgFirstActionMs), side, y, paint); y += dp(24);
            c.drawText("Средняя длинная пауза: " + formatDuration(a.avgLongestPauseMs), side, y, paint); y += dp(24);
            c.drawText("Ходы: " + a.placements + "   Undo: " + a.undoCount + "   Кандидаты: " + a.candidateEdits, side, y, paint); y += dp(24);
            c.drawText("Наводящие намёки: " + a.hintCount, side, y, paint); y += dp(24);
            c.drawText("Паузы: продуктивные " + a.productivePauses + " · тупиковые " + a.deadEndPauses, side, y, paint); y += dp(24);
            c.drawText("Сигналы проверки гипотез: " + a.hypothesisEpisodes, side, y, paint); y += dp(24);
            c.drawText("Быстрые каскады действий: " + a.rapidCascades, side, y, paint); y += dp(24);
            c.drawText("Кандидаты: переходы между клетками " + a.candidateCellSwitches
                    + " · возвраты " + a.candidateCellRevisits, side, y, paint); y += dp(24);

            paint.setColor(ink);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(15.5f));
            c.drawText("Калибровка сложности", side, y, paint); y += dp(23);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(dp(13.2f));
            paint.setColor(Color.rgb(68, 80, 70));
            if (!a.calibrationReady) {
                int need = Math.max(0, DifficultyCalibrator.MIN_SOLVED - a.calibrationSessions);
                c.drawText("Нужно ещё решённых прохождений: " + need, side, y, paint); y += dp(21);
            } else {
                String scope = a.calibrationGeneratorVersion > 0 ? (" · v" + a.calibrationGeneratorVersion) : " · история";
                c.drawText("Прогноз ±1: " + String.format(Locale.US, "%.0f%%", a.calibrationWithinOnePct) + scope, side, y, paint); y += dp(21);
                String tendency = a.calibrationMeanError > 0.35 ? "чаще недооценивает трудность"
                        : (a.calibrationMeanError < -0.35 ? "чаще переоценивает трудность" : "в среднем близок к прохождению");
                c.drawText("Модель: " + tendency, side, y, paint); y += dp(21);
                if (a.lastPredictedBand > 0 && a.lastObservedBand > 0) {
                    c.drawText("Последняя: прогноз L" + a.lastPredictedBand + " → стоимость " + a.lastObservedBand + "/10", side, y, paint); y += dp(21);
                }
                if (Math.abs(a.recentObservedCostChangePct) >= 8.0) {
                    String sign = a.recentObservedCostChangePct > 0 ? "+" : "";
                    c.drawText("Последние 10: " + sign + String.format(Locale.US, "%.0f%%", a.recentObservedCostChangePct) + " к предыдущим", side, y, paint); y += dp(21);
                }
            }
            y += dp(5);
            if (a.kernelSessions > 0) {
                c.drawText("Задачи с ядром гипотезы: " + a.kernelSessions + " · глубокие " + a.deepKernelSessions, side, y, paint); y += dp(24);
            }
            c.drawText("Сбросы: " + a.resetCount, side, y, paint); y += dp(24);
            if (a.strategyFallbacks > 0) {
                c.drawText("Fallback генератора: " + a.strategyFallbacks + " из " + a.sessions, side, y, paint); y += dp(24);
            }
            y += dp(10);

            if (!a.byStrategy.isEmpty()) {
                paint.setColor(ink);
                paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                paint.setTextSize(dp(17));
                c.drawText("По стратегиям решения", side, y, paint);
                y += dp(28);
                paint.setTypeface(android.graphics.Typeface.DEFAULT);
                paint.setTextSize(dp(13.2f));
                for (SessionTracker.StrategyStats st : a.byStrategy) {
                    if (y > getHeight() - bottomInset - dp(150)) break;
                    double undoPer = st.sessions == 0 ? 0.0 : st.undoCount / (double) st.sessions;
                    double candPer = st.sessions == 0 ? 0.0 : st.candidateEdits / (double) st.sessions;
                    double hypPer = st.sessions == 0 ? 0.0 : st.hypothesisEpisodes / (double) st.sessions;
                    String line = strategyLabel(st.strategy) + ": " + st.sessions + " сесс. · "
                            + formatDuration(st.avgSolvedMs) + " · U "
                            + String.format(Locale.US, "%.1f", undoPer) + " · Г "
                            + String.format(Locale.US, "%.1f", hypPer);
                    paint.setColor(Color.rgb(63, 77, 66));
                    c.drawText(line, side, y, paint);
                    y += dp(22);
                }
                y += dp(12);
            }

            if (!a.recent.isEmpty() && y < getHeight() - bottomInset - dp(170)) {
                SessionTracker.SessionSummary last = a.recent.get(0);
                paint.setColor(ink);
                paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                paint.setTextSize(dp(16));
                c.drawText("Последняя траектория", side, y, paint);
                y += dp(24);
                paint.setTypeface(android.graphics.Typeface.DEFAULT);
                paint.setTextSize(dp(12.8f));
                paint.setColor(Color.rgb(72, 84, 74));
                String traceLine = "паузы +" + last.productivePauses + "/−" + last.deadEndPauses
                        + " · проверки гипотез " + last.hypothesisEpisodes
                        + (last.hintStage > 0 ? (" · намёк " + last.hintStage) : " · без намёков");
                c.drawText(traceLine, side, y, paint);
                y += dp(21);
                if (last.candidateCellSwitches > 0 || last.candidateCellRevisits > 0) {
                    c.drawText("кандидаты: переходов " + last.candidateCellSwitches
                            + " · возвратов " + last.candidateCellRevisits
                            + " · максимум в клетке " + last.maxCandidatesInOneCell, side, y, paint);
                    y += dp(21);
                }
                if (last.hidden > 0 && last.maxForcedCascade > 0) {
                    c.drawText("модель каскада: до " + last.maxForcedCascade + " из " + last.hidden + " после ключевого вывода", side, y, paint);
                    y += dp(21);
                }
                if (last.kernelFamily != null && !"none".equals(last.kernelFamily)
                        && !"unprofiled".equals(last.kernelFamily)) {
                    c.drawText("ядро задачи: " + kernelFamilyLabel(last.kernelFamily), side, y, paint);
                    y += dp(21);
                }
                if (last.contextualDecoys > 0) {
                    String decoyLine = "контекстные ложные варианты: " + last.contextualDecoys;
                    if (last.resourceConflictDecoys > 0) decoyLine += " · конфликт плиток " + last.resourceConflictDecoys;
                    c.drawText(decoyLine, side, y, paint);
                    y += dp(21);
                }
                if (last.branchGoodPivots > 0) {
                    c.drawText("точки гипотезы: " + last.branchGoodPivots
                            + " · жизнеспособных ложных веток " + last.branchFalseBranches, side, y, paint);
                    y += dp(21);
                }
                if (last.reasoningFronts >= 2) {
                    c.drawText("структура: " + last.reasoningFronts + " рабочих фронта(ов)", side, y, paint);
                    y += dp(21);
                }
                y += dp(7);
            }

            paint.setColor(ink);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(17));
            c.drawText("Последние прохождения", side, y, paint);
            y += dp(29);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(dp(13.5f));
            for (SessionTracker.SessionSummary r : a.recent) {
                if (y > getHeight() - bottomInset - dp(35)) break;
                String where = "PATH_REPLAY".equals(r.mode) ? ("↺ ур." + r.level) : ("PATH_TEST".equals(r.mode) ? ("тест ур." + r.level) : ("PATH".equals(r.mode) ? ("ур." + r.level) : strategyLabel(r.strategy)));
                String signal = r.hypothesisEpisodes > 0 ? (" · Г" + r.hypothesisEpisodes) : "";
                if (r.hintStage > 0) signal += " · Н" + r.hintStage;
                String line = (r.solved ? "✓ " : "• ") + where
                        + "  Л" + r.logic + "→" + (r.ratedLogic > 0 ? r.ratedLogic : "?") + "/В" + r.calc
                        + "  " + formatDuration(r.activeMs)
                        + signal;
                paint.setColor(r.solved ? Color.rgb(45, 118, 59) : Color.rgb(105, 92, 72));
                c.drawText(line, side, y, paint);
                y += dp(23);
            }

            paint.setColor(Color.rgb(100, 111, 102));
            paint.setTextSize(dp(12));
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("Полная история ходов сохраняется локально для последних 500 сессий", w / 2f,
                    getHeight() - bottomInset - dp(18), paint);
        }

        String strategyLabel(String name) {
            try { return SolutionStrategy.valueOf(name).label; }
            catch (RuntimeException ex) { return "Свободная"; }
        }

        String kernelFamilyLabel(String name) {
            if ("single-pivot".equals(name)) return "одна опорная гипотеза";
            if ("two-stage".equals(name)) return "двухступенчатая гипотеза";
            if ("deep-branch".equals(name)) return "глубокая ложная ветка";
            if ("multi-pivot".equals(name)) return "несколько точек гипотезы";
            return name == null ? "—" : name;
        }

        String formatDuration(long ms) {
            if (ms <= 0) return "—";
            long total = ms / 1000L;
            long min = total / 60L;
            long sec = total % 60L;
            if (min >= 60) return (min / 60) + "ч " + (min % 60) + "м";
            return min + ":" + (sec < 10 ? "0" : "") + sec;
        }

        float drawWrappedText(Canvas c, String text, float x, float y, float maxWidth, float lineHeight) {
            String[] paragraphs = text.split("\\n", -1);
            float yy = y;
            for (String paragraph : paragraphs) {
                if (paragraph.isEmpty()) { yy += lineHeight; continue; }
                String[] words = paragraph.split(" ");
                StringBuilder line = new StringBuilder();
                for (String word : words) {
                    String test = line.length() == 0 ? word : line + " " + word;
                    if (paint.measureText(test) > maxWidth && line.length() > 0) {
                        c.drawText(line.toString(), x, yy, paint);
                        yy += lineHeight;
                        line.setLength(0);
                        line.append(word);
                    } else {
                        if (line.length() > 0) line.append(' ');
                        line.append(word);
                    }
                }
                if (line.length() > 0) {
                    c.drawText(line.toString(), x, yy, paint);
                    yy += lineHeight;
                }
            }
            return yy;
        }

        void drawBigButton(Canvas c, RectF r, String label, boolean filled) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(filled ? accent : board);
            c.drawRoundRect(r, dp(16), dp(16), paint);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(2));
            stroke.setColor(filled ? accent : Color.rgb(80, 105, 82));
            c.drawRoundRect(r, dp(16), dp(16), stroke);
            paint.setColor(filled ? Color.WHITE : ink);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(18));
            Paint.FontMetrics fm = paint.getFontMetrics();
            c.drawText(label, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, paint);
        }

        void drawFreeSetup(Canvas c) {
            float w = getWidth();
            float top = topInset + dp(16);
            topHomeRect.set(dp(12), top, dp(62), top + dp(48));
            drawIconButton(c, topHomeRect, "‹");

            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(24));
            c.drawText("Свободная игра", w / 2f, top + dp(33), paint);

            float y = top + dp(76);
            y = drawTenChoiceRow(c, "Логика", logicRects, freeLogic - 1, y);
            y = drawTenChoiceRow(c, "Вычисления", calcRects, freeCalc - 1, y + dp(4));
            y = drawChoiceRow(c, "Размер поля", new String[]{"S", "M", "L"}, sizeRects, freeSize, y + dp(6));
            y = drawChoiceRow(c, "Числа до", new String[]{"20", "100", "500", "1000"}, maxRects, freeMaxIndex, y + dp(6));
            y = drawChoiceRow(c, "Стратегия решения", new String[]{"Дед.", "Цепь", "Гип.", "Сеть", "Микс"},
                    strategyRects, freeStrategyIndex, y + dp(6));

            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(16));
            c.drawText("Операции", dp(22), y + dp(8), paint);
            float buttonY = y + dp(24);
            String[] opLabels = {"+", "−", "×", "÷", "^"};
            char[] opChars = {'+', '-', '×', '÷', '^'};
            float gap = dp(7);
            float side = dp(22);
            float bw = (w - side * 2 - gap * 4) / 5f;
            for (int i = 0; i < 5; i++) {
                RectF r = opRects[i];
                r.set(side + i * (bw + gap), buttonY, side + i * (bw + gap) + bw, buttonY + dp(48));
                drawPill(c, r, opLabels[i], freeOps.contains(opChars[i]));
            }

            freeGenerateRect.set(dp(28), buttonY + dp(73), w - dp(28), buttonY + dp(135));
            drawBigButton(c, freeGenerateRect, generating ? "Генерирую…" : "Сгенерировать", !generating);

            paint.setColor(Color.rgb(100, 112, 102));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(dp(12.5f));
            c.drawText("Стратегия меняет структуру рассуждения, а не только форму поля", w / 2f,
                    freeGenerateRect.bottom + dp(25), paint);
            c.drawText("Логика и вычисления остаются независимыми шкалами", w / 2f,
                    freeGenerateRect.bottom + dp(44), paint);
        }

        float drawTenChoiceRow(Canvas c, String title, RectF[] rects, int selectedIndex, float y) {
            float w = getWidth();
            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(16));
            c.drawText(title, dp(22), y + dp(18), paint);

            float side = dp(22);
            float gap = dp(7);
            float top = y + dp(28);
            float bw = (w - side * 2 - gap * 4) / 5f;
            for (int i = 0; i < 10; i++) {
                int row = i / 5;
                int col = i % 5;
                RectF r = rects[i];
                float yy = top + row * dp(48);
                r.set(side + col * (bw + gap), yy, side + col * (bw + gap) + bw, yy + dp(40));
                drawPill(c, r, Integer.toString(i + 1), i == selectedIndex);
            }
            return top + dp(92);
        }

        float drawChoiceRow(Canvas c, String title, String[] labels, RectF[] rects, int selectedIndex, float y) {
            float w = getWidth();
            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(16));
            c.drawText(title, dp(22), y, paint);

            float top = y + dp(16);
            float gap = dp(8);
            float side = dp(22);
            float bw = (w - side * 2 - gap * (labels.length - 1)) / labels.length;
            for (int i = 0; i < labels.length; i++) {
                RectF r = rects[i];
                r.set(side + i * (bw + gap), top, side + i * (bw + gap) + bw, top + dp(46));
                drawPill(c, r, labels[i], i == selectedIndex);
            }
            return top + dp(58);
        }

        void drawPill(Canvas c, RectF r, String label, boolean on) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(on ? green : board);
            c.drawRoundRect(r, dp(10), dp(10), paint);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(1.5f));
            stroke.setColor(on ? accent : Color.rgb(120, 130, 121));
            c.drawRoundRect(r, dp(10), dp(10), stroke);
            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(on ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            paint.setTextSize(dp(14));
            Paint.FontMetrics fm = paint.getFontMetrics();
            c.drawText(label, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, paint);
        }

        static final class BankMetrics {
            final float tileW, tileH, gap, textSize;
            BankMetrics(float tileW, float tileH, float gap, float textSize) {
                this.tileW = tileW;
                this.tileH = tileH;
                this.gap = gap;
                this.textSize = textSize;
            }
        }

        BankMetrics bankMetrics(int tileCount) {
            // Keep the board visually dominant. The bank gets denser as it grows,
            // but never becomes so small that it is awkward to tap on a phone.
            if (tileCount <= 10) return new BankMetrics(dp(58), dp(50), dp(8), dp(22));
            if (tileCount <= 18) return new BankMetrics(dp(54), dp(48), dp(7), dp(21));
            return new BankMetrics(dp(52), dp(48), dp(7), dp(20));
        }

        void drawGame(Canvas canvas) {
            if (puzzle == null) return;
            float w = getWidth(), h = getHeight();

            // The candidate bank is a bottom drawer. It can be resized continuously or
            // collapsed to a thin handle so the board can occupy almost the whole screen.
            float drawerMin = dp(28) + bottomInset;
            float drawerCompact = Math.min(h * 0.34f, dp(250) + bottomInset);
            float drawerMax = Math.min(h * 0.58f, dp(430) + bottomInset);
            if (candidateDrawerHeight < 0f) {
                candidateDrawerHeight = drawerCompact;
                lastExpandedDrawerHeight = drawerCompact;
            }
            candidateDrawerHeight = Math.max(drawerMin, Math.min(drawerMax, candidateDrawerHeight));
            if (candidateDrawerHeight > drawerMin + dp(10)) lastExpandedDrawerHeight = candidateDrawerHeight;
            // Completion gets its own reserved bottom sheet. Never overlay the board.
            float solvedDrawerHeight = dp(158) + bottomInset;
            float effectiveDrawerHeight = solved ? solvedDrawerHeight : (focusMode ? drawerMin : candidateDrawerHeight);

            float headerH = focusMode ? 0f : dp(46);
            float topH = topInset + headerH;
            float drawerTop = h - effectiveDrawerHeight;

            if (!focusMode) drawTopBar(canvas, w, topInset, headerH);
            else drawFocusHandle(canvas, w, topInset);

            int cols = puzzle.maxX - puzzle.minX + 1;
            int rows = puzzle.maxY - puzzle.minY + 1;
            float availW = Math.max(dp(40), w - dp(12));
            // Finished puzzles get a real breathing gap above the completion sheet.
            // This is reserved board space, not an overlay, so the last crossword tile
            // never visually sticks to the bottom controls.
            float boardBottomGap = solved ? dp(38) : dp(10);
            float availH = Math.max(dp(60), drawerTop - topH - boardBottomGap);
            boardViewportTop = topH;
            boardViewportBottom = drawerTop;

            float fitCell = Math.min(Math.min(availW / cols, availH / rows), dp(62));
            fitCell = Math.max(dp(1), fitCell);
            boardZoom = Math.max(1f, Math.min(2.65f, boardZoom));
            cellSize = fitCell * boardZoom;
            float gridW = cols * cellSize;
            float gridH = rows * cellSize;

            // Clamp pan so an enlarged board can move, but can never be lost off-screen.
            float maxPanX = Math.max(0f, (gridW - availW) / 2f + dp(18));
            float maxPanY = Math.max(0f, (gridH - availH) / 2f + dp(18));
            boardPanX = Math.max(-maxPanX, Math.min(maxPanX, boardPanX));
            boardPanY = Math.max(-maxPanY, Math.min(maxPanY, boardPanY));

            originX = (w - gridW) / 2f - puzzle.minX * cellSize + boardPanX;
            originY = topH + (availH - gridH) / 2f - puzzle.minY * cellSize + boardPanY;

            Map<Pos, Integer> status = equationStatus();
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Math.max(dp(1.0f), cellSize * 0.035f));
            stroke.setColor(ink);

            List<Map.Entry<Pos, Cell>> entries = new ArrayList<>(puzzle.cells.entrySet());
            entries.sort(Comparator.comparingInt((Map.Entry<Pos, Cell> e) -> e.getKey().y).thenComparingInt(e -> e.getKey().x));
            for (Map.Entry<Pos, Cell> e : entries) drawCell(canvas, e.getKey(), e.getValue(), status.getOrDefault(e.getKey(), 0));

            // Draw handwritten candidate notes in a dedicated top layer after the whole board.
            // This guarantees that notes from non-selected cells are never replaced by a
            // summary marker or covered by later board drawing.
            drawAllCandidateNotesOverlay(canvas);
            drawLocalFocusOverlay(canvas);

            if (solved) drawSolvedBanner(canvas, w, h);
            else drawCandidateDrawer(canvas, drawerTop, effectiveDrawerHeight, w, h, drawerMin, drawerMax);
        }

        void drawTopBar(Canvas c, float w, float insetTop, float headerH) {
            float top = insetTop;
            float centerY = top + headerH / 2f;
            topHomeRect.set(dp(8), top + dp(3), dp(48), top + headerH - dp(3));
            menuRect.set(w - dp(48), top + dp(3), w - dp(8), top + headerH - dp(3));
            resetRect.setEmpty();
            drawIconButton(c, topHomeRect, "⌂");
            drawIconButton(c, menuRect, "⋮");

            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(17));
            Paint.FontMetrics fm = paint.getFontMetrics();
            float ty = centerY - (fm.ascent + fm.descent) / 2f;
            c.drawText(mode == GameMode.PATH ? "Уровень " + level
                    : puzzle.solutionStrategy.label + " · Л" + puzzle.displayLogicLevel + "/В" + puzzle.displayCalcLevel,
                    w / 2f, ty, paint);

            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.argb(18, 0, 0, 0));
            c.drawLine(0, top + headerH - 1, w, top + headerH - 1, paint);
        }

        void drawFocusHandle(Canvas c, float w, float insetTop) {
            focusMenuRect.set(w - dp(43), insetTop + dp(4), w - dp(7), insetTop + dp(38));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(115, 255, 255, 255));
            c.drawRoundRect(focusMenuRect, dp(10), dp(10), paint);
            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(21));
            Paint.FontMetrics fm = paint.getFontMetrics();
            c.drawText("⋮", focusMenuRect.centerX(), focusMenuRect.centerY() - (fm.ascent + fm.descent) / 2f, paint);
        }

        void drawCandidateDrawer(Canvas c, float top, float height, float w, float h, float minH, float maxH) {
            drawerHandleRect.set(0, Math.max(0, top - dp(8)), w, Math.min(h - bottomInset, top + dp(44)));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(246, 248, 252, 246));
            c.drawRect(0, top, w, h, paint);
            paint.setColor(Color.argb(28, 0, 0, 0));
            c.drawRect(0, top, w, top + dp(1), paint);

            paint.setColor(Color.rgb(118, 128, 119));
            RectF grip = new RectF(w / 2f - dp(24), top + dp(8), w / 2f + dp(24), top + dp(12));
            c.drawRoundRect(grip, dp(3), dp(3), paint);

            bankHits.clear();
            undoRect.setEmpty(); candidateRect.setEmpty(); hintRect.setEmpty();
            if (height <= minH + dp(6) || focusMode) return;

            float contentTop = top + dp(25);
            drawGameTools(c, contentTop, w);
            float bankTop = contentTop + dp(52);
            float expansion = Math.max(0f, Math.min(1f, (height - minH) / Math.max(dp(1), maxH - minH)));
            drawBank(c, bankTop, w, h - bottomInset, expansion);
        }

        void showGameMenu() {
            if (puzzle == null) return;
            String info = String.format(Locale.US, "Логика %d (%.1f) · вычисления %d (%.1f)\n%s · скрыто клеток: %d\nВерсия %s (%d)",
                    puzzle.displayLogicLevel, puzzle.logicScore, puzzle.displayCalcLevel, puzzle.calcScore,
                    puzzle.solutionStrategy.label, puzzle.hidden.size(), installedVersionName(), installedVersionCode());
            String focusLabel = focusMode ? "Показать панели" : "Режим фокуса";
            boolean drawerHidden = candidateDrawerHeight <= dp(40) + bottomInset;
            String drawerLabel = drawerHidden ? "Показать кандидаты" : "Скрыть кандидаты";
            new AlertDialog.Builder(getContext())
                    .setTitle(mode == GameMode.PATH ? "Уровень " + level : "Головоломка")
                    .setMessage(info)
                    .setItems(new String[]{focusLabel, drawerLabel, "Перезапустить", "Закрыть"}, (dialog, which) -> {
                        if (which == 0) {
                            if (!focusMode && candidateDrawerHeight > dp(40) + bottomInset) lastExpandedDrawerHeight = candidateDrawerHeight;
                            focusMode = !focusMode;
                            if (!focusMode && candidateDrawerHeight <= dp(40) + bottomInset) {
                                candidateDrawerHeight = lastExpandedDrawerHeight > dp(40) + bottomInset
                                        ? lastExpandedDrawerHeight : dp(220) + bottomInset;
                            }
                            tracker.event("focus_mode", -1, -1, focusMode ? 1 : -1, null);
                            invalidate();
                        } else if (which == 1) {
                            if (!drawerHidden) lastExpandedDrawerHeight = candidateDrawerHeight;
                            candidateDrawerHeight = drawerHidden
                                    ? (lastExpandedDrawerHeight > dp(40) + bottomInset ? lastExpandedDrawerHeight : dp(220) + bottomInset)
                                    : dp(28) + bottomInset;
                            tracker.event("candidate_drawer", -1, -1, drawerHidden ? 1 : 0, "menu");
                            invalidate();
                        } else if (which == 2) {
                            restartCurrentGame();
                        }
                    }).show();
        }

        void restartCurrentGame() {
            if (puzzle == null) return;
            tracker.event("reset", -1, -1, 0, null);
            if (tracker.hasOpenSession()) tracker.finish(false, "reset");
            if (mode == GameMode.PATH) loadPathLevel(level);
            else {
                resetCurrentPuzzle();
                startTrackerForCurrentFreePuzzle();
            }
        }

        void startTrackerForCurrentFreePuzzle() {
            tracker.start("FREE", 0, puzzle.seed, puzzle.displayLogicLevel, puzzle.displayCalcLevel, puzzle.logicScore, puzzle.calcScore,
                    puzzle.solutionStrategy.name(), puzzle.hidden.size(), puzzle.equations.size(),
                    puzzle.ratedDisplayLogic, puzzle.reasoningSteps, puzzle.reasoningDepth,
                    puzzle.basicForced, puzzle.basicRemaining, puzzle.maxForcedCascade,
                    puzzle.maxResolvedAfterOneCell, puzzle.maxResolvedFractionAfterOneCell,
                    puzzle.vulnerableSingleCells, puzzle.maxResolvedAfterOneEquation, puzzle.maxResolvedFractionAfterOneEquation,
                    puzzle.generatorVersion, puzzle.generationStage, puzzle.strategyTargetMatched, puzzle.generationStrategy.name(),
                    puzzle.generatorConstructor, puzzle.generatorFamily, puzzle.deceptiveDecoyCount, puzzle.deceptiveDecoySupportMax,
                    puzzle.contextualDecoyCount, puzzle.resourceConflictDecoyCount, puzzle.contextualDecoyConstraintSupportMax,
                    puzzle.contextualDecoyDepthMax, puzzle.contextualDecoyInformationGainMax,
                    puzzle.branchPivotCount, puzzle.branchGoodPivotCount, puzzle.branchSeriousFalseBranches,
                    puzzle.branchDepth2RefutableBranches, puzzle.branchDepth2SurvivingBranches,
                    puzzle.branchMaxWidth, puzzle.branchMaxInformationGain,
                    puzzle.reasoningFronts, puzzle.reasoningFrontBalance, puzzle.reasoningLargestFrontFraction,
                    puzzle.reasoningFrontBottleneckDegree,
                    puzzle.contradictionKernel, puzzle.contradictionKernelAddedDecoy, puzzle.contradictionKernelDepth,
                    puzzle.contradictionKernelFamily, puzzle.contradictionKernelBranches, puzzle.contradictionKernelPivots,
                    puzzle.contradictionKernelDepth2Branches, puzzle.contradictionKernelDepth3Branches,
                    puzzle.contradictionKernelDeepBranches, puzzle.contradictionKernelMaxRemaining,
                    puzzle.generationStageTimings, puzzle.generationMillis, puzzle.generationAttempts,
                    puzzle.generationRejects, puzzle.generationRejectSummary);
        }

        void drawIconButton(Canvas c, RectF r, String text) {
            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextSize(dp(27));
            Paint.FontMetrics fm = paint.getFontMetrics();
            c.drawText(text, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, paint);
        }

        void drawGameTools(Canvas c, float y, float w) {
            float side = dp(16);
            float gap = dp(8);
            float h = dp(44);
            float totalW = w - side * 2 - gap * 2;
            float each = totalW / 3f;
            undoRect.set(side, y, side + each, y + h);
            candidateRect.set(undoRect.right + gap, y, undoRect.right + gap + each, y + h);
            hintRect.set(candidateRect.right + gap, y, w - side, y + h);
            drawToolButton(c, undoRect, "↶ Отмена", !undoStack.isEmpty(), false);
            drawToolButton(c, candidateRect, "✎ Канд.", true, candidateMode);
            drawToolButton(c, hintRect, "? Намёк", true, false);
        }

        void drawToolButton(Canvas c, RectF r, String label, boolean enabled, boolean active) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(active ? selected : (enabled ? board : Color.rgb(231, 237, 230)));
            c.drawRoundRect(r, dp(12), dp(12), paint);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(active ? 2.2f : 1.4f));
            stroke.setColor(active ? accent : (enabled ? Color.rgb(105, 117, 106) : Color.rgb(185, 194, 185)));
            c.drawRoundRect(r, dp(12), dp(12), stroke);
            paint.setColor(enabled ? ink : Color.rgb(160, 168, 160));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(active ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            paint.setTextSize(dp(14));
            Paint.FontMetrics fm = paint.getFontMetrics();
            c.drawText(label, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, paint);
        }

        void drawCell(Canvas c, Pos pos, Cell cell, int status) {
            float left = originX + pos.x * cellSize;
            float top = originY + pos.y * cellSize;
            RectF r = new RectF(left, top, left + cellSize, top + cellSize);

            int fill = board;
            if (status == 1) fill = green;
            if (status == 2) fill = red;
            if (selectedCell != null && selectedCell.equals(pos)) fill = selected;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fill);
            c.drawRect(r, paint);
            c.drawRect(r, stroke);

            String text = "";
            if (cell.kind == Kind.NUMBER) {
                if (!puzzle.hidden.contains(pos)) text = Integer.toString(cell.number);
                else {
                    Integer tileId = puzzle.placedTile.get(pos);
                    if (tileId != null) {
                        Tile t = tileById(tileId);
                        if (t != null) text = Integer.toString(t.value);
                    }
                }
            } else text = String.valueOf(cell.symbol);

            if (!text.isEmpty()) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ink);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTypeface(cell.kind == Kind.NUMBER ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
                paint.setTextSize(Math.min(cellSize * 0.46f, dp(25)));
                Paint.FontMetrics fm = paint.getFontMetrics();
                c.drawText(text, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, paint);
            }
        }

        void drawAllCandidateNotesOverlay(Canvas c) {
            if (puzzle == null || candidateNotes.isEmpty()) return;
            for (Map.Entry<Pos, LinkedHashSet<Integer>> e : candidateNotes.entrySet()) {
                Pos pos = e.getKey();
                LinkedHashSet<Integer> notes = e.getValue();
                if (notes == null || notes.isEmpty()) continue;
                if (!puzzle.hidden.contains(pos) || puzzle.placedTile.containsKey(pos)) continue;
                float left = originX + pos.x * cellSize;
                float top = originY + pos.y * cellSize;
                RectF r = new RectF(left, top, left + cellSize, top + cellSize);
                drawCandidateNotes(c, r, notes, selectedCell != null && selectedCell.equals(pos));
            }
        }

        void drawCandidateNotes(Canvas c, RectF r, Set<Integer> notes, boolean focused) {
            // Candidate notes are never intentionally hidden. The layout adapts to both
            // note count and digit width so three-digit values do not disappear under borders.
            List<Integer> vals = new ArrayList<>(notes);
            Collections.sort(vals);
            int n = vals.size();
            if (n == 0) return;
            int maxDigits = 1;
            for (Integer v : vals) maxDigits = Math.max(maxDigits, Integer.toString(Math.abs(v)).length());

            int cols;
            if (n == 1) cols = 1;
            else if (n <= 4) cols = 2;
            else if (maxDigits >= 3) cols = 2;
            else if (maxDigits >= 2 && n <= 6) cols = 2;
            else cols = 3;
            int rows = (int) Math.ceil(n / (double) cols);

            float pad = Math.max(dp(1.3f), cellSize * 0.045f);
            float usableW = Math.max(dp(2), r.width() - pad * 2);
            float usableH = Math.max(dp(2), r.height() - pad * 2);
            float colW = usableW / cols;
            float rowH = usableH / rows;

            float target = Math.min(dp(focused ? 13f : 11.5f), Math.min(cellSize * (focused ? 0.25f : 0.22f), rowH * 0.66f));
            target = Math.max(dp(4.7f), target);
            paint.setTypeface(focused ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(focused ? Color.rgb(51, 72, 56) : Color.rgb(73, 92, 77));

            // Find one font size that fits the widest note into every column.
            float textSize = target;
            paint.setTextSize(textSize);
            float widest = 0f;
            for (Integer v : vals) widest = Math.max(widest, paint.measureText(Integer.toString(v)));
            float maxAllowed = Math.max(dp(2), colW * 0.88f);
            if (widest > maxAllowed) textSize *= maxAllowed / widest;
            textSize = Math.max(dp(4.5f), textSize);
            paint.setTextSize(textSize);
            Paint.FontMetrics fm = paint.getFontMetrics();

            for (int i = 0; i < n; i++) {
                int row = i / cols, col = i % cols;
                float cx = r.left + pad + colW * (col + 0.5f);
                float cy = r.top + pad + rowH * (row + 0.5f) - (fm.ascent + fm.descent) / 2f;
                c.drawText(Integer.toString(vals.get(i)), cx, cy, paint);
            }
        }

        void resetBoardViewport() {
            boardZoom = 1f; boardPanX = 0f; boardPanY = 0f;
            clearLocalFocus();
            cancelBoardLongPress();
        }

        float pointerSpacing(MotionEvent event) {
            if (event.getPointerCount() < 2) return 0f;
            float dx = event.getX(0) - event.getX(1);
            float dy = event.getY(0) - event.getY(1);
            return (float) Math.hypot(dx, dy);
        }

        void scheduleBoardLongPress(final Pos pos) {
            cancelBoardLongPress();
            longPressPos = pos;
            boardLongPressRunnable = () -> {
                if (!boardGestureMoved && !pinching && longPressPos != null && longPressPos.equals(pos)) {
                    toggleLocalFocus(pos);
                    boardLongPressTriggered = true;
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    invalidate();
                }
            };
            postDelayed(boardLongPressRunnable, 430L);
        }

        void cancelBoardLongPress() {
            if (boardLongPressRunnable != null) removeCallbacks(boardLongPressRunnable);
            boardLongPressRunnable = null;
            longPressPos = null;
        }

        void toggleLocalFocus(Pos pos) {
            if (localFocusCell != null && localFocusCell.equals(pos)) {
                clearLocalFocus();
                tracker.event("local_focus", pos.x, pos.y, 0, "off");
                return;
            }
            localFocusCell = pos;
            localFocusPositions.clear();
            if (puzzle != null) {
                for (Equation e : puzzle.equations) {
                    Pos[] line = {e.a, e.op, e.b, e.eq, e.c};
                    boolean contains = false;
                    for (Pos q : line) if (q.equals(pos)) { contains = true; break; }
                    if (contains) Collections.addAll(localFocusPositions, line);
                }
            }
            if (localFocusPositions.isEmpty()) localFocusPositions.add(pos);
            tracker.event("local_focus", pos.x, pos.y, localFocusPositions.size(), "on");
        }

        void clearLocalFocus() {
            localFocusCell = null;
            localFocusPositions.clear();
        }

        void drawLocalFocusOverlay(Canvas c) {
            if (localFocusCell == null || puzzle == null) return;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(128, Color.red(bg), Color.green(bg), Color.blue(bg)));
            for (Pos pos : puzzle.cells.keySet()) {
                if (localFocusPositions.contains(pos)) continue;
                float left = originX + pos.x * cellSize;
                float top = originY + pos.y * cellSize;
                c.drawRect(left, top, left + cellSize, top + cellSize, paint);
            }
            float left = originX + localFocusCell.x * cellSize;
            float top = originY + localFocusCell.y * cellSize;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Math.max(dp(2f), cellSize * 0.045f));
            stroke.setColor(accent);
            c.drawRect(left, top, left + cellSize, top + cellSize, stroke);
        }

        Map<Pos, Integer> equationStatus() {
            Map<Pos, Integer> out = new HashMap<>();
            // On hard logic levels, instant red/green feedback becomes a brute-force
            // oracle. Wait until every crossword cell is filled before revealing it.
            if (puzzle.logicLevel >= 4 && puzzle.placedTile.size() < puzzle.hidden.size()) return out;
            for (Equation e : puzzle.equations) {
                Integer a = numberAt(e.a), b = numberAt(e.b), cc = numberAt(e.c);
                if (a == null || b == null || cc == null) continue;
                boolean ok = PuzzleGenerator.eval(a, e.operator, b) == cc;
                int s = ok ? 1 : 2;
                for (Pos q : new Pos[]{e.a, e.op, e.b, e.eq, e.c}) {
                    int old = out.getOrDefault(q, 0);
                    if (s == 2 || old == 0) out.put(q, s);
                }
            }
            return out;
        }

        Integer numberAt(Pos pos) {
            Cell cell = puzzle.cells.get(pos);
            if (cell == null || cell.kind != Kind.NUMBER) return null;
            if (!puzzle.hidden.contains(pos)) return cell.number;
            Integer tileId = puzzle.placedTile.get(pos);
            if (tileId == null) return null;
            Tile t = tileById(tileId);
            return t == null ? null : t.value;
        }

        Tile tileById(int id) {
            for (Tile t : puzzle.tiles) if (t.id == id) return t;
            return null;
        }

        void drawBank(Canvas c, float startY, float w, float bottomLimit, float expansion) {
            bankHits.clear();
            List<Tile> visible = new ArrayList<>();
            for (Tile t : puzzle.tiles) if (!t.used) visible.add(t);

            BankMetrics base = bankMetrics(visible.size());
            float tileW = base.tileW + dp(8) * expansion;
            float tileH = base.tileH + dp(7) * expansion;
            float gap = base.gap;
            float textSize = base.textSize + dp(2) * expansion;
            int perRow = Math.max(3, (int) ((w - dp(24) + gap) / (tileW + gap)));
            int rowCount = Math.max(1, (int) Math.ceil(visible.size() / (double) perRow));
            float totalH = rowCount * tileH + Math.max(0, rowCount - 1) * gap;
            float available = Math.max(dp(20), bottomLimit - startY - dp(8));
            if (totalH > available) {
                float scale = Math.max(0.72f, available / totalH);
                tileH *= scale;
                textSize *= Math.max(0.80f, scale);
            }

            Set<Integer> selectedNotes = Collections.emptySet();
            if (selectedCell != null) {
                LinkedHashSet<Integer> set = candidateNotes.get(selectedCell);
                if (set != null) selectedNotes = set;
            }

            int index = 0;
            for (int row = 0; row < rowCount; row++) {
                int remaining = visible.size() - index;
                int count = Math.min(perRow, remaining);
                float rowW = count * tileW + Math.max(0, count - 1) * gap;
                float x = (w - rowW) / 2f;
                for (int col = 0; col < count; col++) {
                    Tile t = visible.get(index++);
                    RectF r = new RectF(x, startY + row * (tileH + gap), x + tileW, startY + row * (tileH + gap) + tileH);
                    bankHits.add(new BankHit(r, t.id));
                    boolean noted = candidateMode && selectedCell != null && selectedNotes.contains(t.value);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor((t.id == selectedTileId || noted) ? selected : board);
                    c.drawRoundRect(r, dp(5), dp(5), paint);
                    stroke.setColor(noted ? accent : ink);
                    stroke.setStrokeWidth(dp(noted ? 2.4f : 1.8f));
                    c.drawRoundRect(r, dp(5), dp(5), stroke);

                    paint.setColor(Color.rgb(28, 121, 38));
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    paint.setTextSize(textSize);
                    String label = Integer.toString(t.value);
                    float maxW = r.width() - dp(8);
                    float measured = paint.measureText(label);
                    if (measured > maxW) paint.setTextSize(textSize * maxW / measured);
                    Paint.FontMetrics fm = paint.getFontMetrics();
                    c.drawText(label, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, paint);
                    x += tileW + gap;
                }
            }
        }

        void drawSolvedBanner(Canvas c, float w, float h) {
            // Replace the candidate drawer after completion instead of floating controls
            // over the crossword. The board has already reserved this exact area.
            float sheetTop = h - bottomInset - dp(158);
            drawerHandleRect.setEmpty();
            bankHits.clear();
            undoRect.setEmpty(); candidateRect.setEmpty(); hintRect.setEmpty();

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(246, 248, 252, 246));
            c.drawRect(0, sheetTop, w, h, paint);
            paint.setColor(Color.argb(22, 0, 0, 0));
            c.drawRect(0, sheetTop, w, sheetTop + dp(1), paint);

            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(16.5f));
            c.drawText("Готово ✓", w / 2f, sheetTop + dp(38), paint);

            // Keep completion action obvious but less visually dominant than the board.
            float side = dp(38);
            nextLevelRect.set(side, sheetTop + dp(76), w - side, h - bottomInset - dp(32));
            paint.setColor(accent);
            c.drawRoundRect(nextLevelRect, dp(13), dp(13), paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(dp(17));
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            String labelText = generating ? "Генерирую…"
                    : (mode == GameMode.PATH ? "Следующий уровень  →" : "Новая головоломка  →");
            Paint.FontMetrics fm = paint.getFontMetrics();
            c.drawText(labelText, w / 2f, nextLevelRect.centerY() - (fm.ascent + fm.descent) / 2f, paint);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX(), y = event.getY();
            if (screen == Screen.GAME) {
                // Drawer gesture has priority over board gestures.
                if (!solved && event.getAction() == MotionEvent.ACTION_DOWN && drawerHandleRect.contains(x, y)) {
                    cancelBoardLongPress();
                    draggingCandidateDrawer = true;
                    drawerDragStartY = y;
                    drawerDragStartHeight = candidateDrawerHeight;
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE && draggingCandidateDrawer) {
                    float minH = dp(28) + bottomInset;
                    float maxH = Math.min(getHeight() * 0.58f, dp(430) + bottomInset);
                    candidateDrawerHeight = Math.max(minH, Math.min(maxH, drawerDragStartHeight + (drawerDragStartY - y)));
                    focusMode = false;
                    invalidate();
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP && draggingCandidateDrawer) {
                    draggingCandidateDrawer = false;
                    float moved = Math.abs(y - drawerDragStartY);
                    float minH = dp(28) + bottomInset;
                    float compactH = Math.min(getHeight() * 0.34f, dp(250) + bottomInset);
                    float maxH = Math.min(getHeight() * 0.58f, dp(430) + bottomInset);
                    if (moved < dp(8)) {
                        if (candidateDrawerHeight <= minH + dp(8)) {
                            candidateDrawerHeight = compactH;
                        } else if (candidateDrawerHeight < maxH - dp(20)) {
                            lastExpandedDrawerHeight = maxH;
                            candidateDrawerHeight = maxH;
                        } else {
                            lastExpandedDrawerHeight = candidateDrawerHeight;
                            candidateDrawerHeight = minH;
                        }
                    } else {
                        if (candidateDrawerHeight <= minH + dp(24)) candidateDrawerHeight = minH;
                        else lastExpandedDrawerHeight = candidateDrawerHeight;
                    }
                    tracker.event("candidate_drawer", -1, -1, Math.round(candidateDrawerHeight), "drag");
                    invalidate();
                    return true;
                }

                if (!solved && event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2
                        && event.getY(0) < boardViewportBottom && event.getY(1) < boardViewportBottom) {
                    cancelBoardLongPress();
                    pinching = true;
                    boardGestureMoved = true;
                    pinchStartDistance = pointerSpacing(event);
                    pinchStartZoom = boardZoom;
                    return true;
                }
                if (pinching && event.getActionMasked() == MotionEvent.ACTION_MOVE && event.getPointerCount() >= 2) {
                    float d = pointerSpacing(event);
                    if (pinchStartDistance > dp(8)) {
                        boardZoom = Math.max(1f, Math.min(2.65f, pinchStartZoom * d / pinchStartDistance));
                        if (boardZoom <= 1.01f) { boardZoom = 1f; boardPanX = 0f; boardPanY = 0f; }
                        invalidate();
                    }
                    return true;
                }
                if (pinching && event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
                    pinching = false;
                    tracker.event("view_zoom", -1, -1, Math.round(boardZoom * 100f), null);
                    return true;
                }

                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    boardGestureMoved = false;
                    boardPanGesture = false;
                    boardLongPressTriggered = false;
                    boardDownX = x; boardDownY = y;
                    boardPanStartX = boardPanX; boardPanStartY = boardPanY;
                    if (!solved && y >= boardViewportTop && y <= boardViewportBottom) {
                        Pos hold = gridPosAt(x, y);
                        if (hold != null) scheduleBoardLongPress(hold);
                    }
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE && !pinching) {
                    float dx = x - boardDownX, dy = y - boardDownY;
                    if (Math.hypot(dx, dy) > dp(8)) {
                        boardGestureMoved = true;
                        cancelBoardLongPress();
                        if (boardZoom > 1.01f && boardDownY >= boardViewportTop && boardDownY <= boardViewportBottom) {
                            boardPanGesture = true;
                            boardPanX = boardPanStartX + dx;
                            boardPanY = boardPanStartY + dy;
                            invalidate();
                            return true;
                        }
                    }
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    cancelBoardLongPress();
                    if (boardLongPressTriggered) { boardLongPressTriggered = false; return true; }
                    if (boardPanGesture || boardGestureMoved) {
                        if (boardPanGesture) tracker.event("view_pan", -1, -1, Math.round(boardZoom * 100f), null);
                        boardPanGesture = false; boardGestureMoved = false;
                        return true;
                    }
                    if (!solved && y >= boardViewportTop && y <= boardViewportBottom) {
                        long now = System.currentTimeMillis();
                        if (now - lastBoardTapTime < 310L
                                && Math.hypot(x - lastBoardTapX, y - lastBoardTapY) < dp(36)) {
                            boardZoom = 1f; boardPanX = 0f; boardPanY = 0f;
                            selectedCell = null; selectedTileId = -1;
                            tracker.event("view_fit", -1, -1, 100, "double_tap");
                            lastBoardTapTime = 0L;
                            invalidate();
                            return true;
                        }
                        lastBoardTapTime = now; lastBoardTapX = x; lastBoardTapY = y;
                        if (localFocusCell != null && gridPosAt(x, y) == null) {
                            clearLocalFocus();
                            invalidate();
                            return true;
                        }
                    }
                }
            }
            if (event.getAction() != MotionEvent.ACTION_UP) return true;

            if (screen == Screen.HOME) {
                if (generating) return true;
                if (homeContinueRect.contains(x, y)) loadPathLevel(progressLevel);
                else if (homeLevelsRect.contains(x, y)) { levelPage = Math.min(maxUnlockedLevelPage(), Math.max(0, (Math.max(1, level) - 1) / 100)); screen = Screen.LEVELS; invalidate(); }
                else if (homeFreeRect.contains(x, y)) { screen = Screen.FREE_SETUP; invalidate(); }
                else if (homeLibraryRect.contains(x, y)) { screen = Screen.LIBRARY; invalidate(); }
                else if (homeAnalysisRect.contains(x, y)) { screen = Screen.ANALYSIS; invalidate(); }
                else if (homeUpdateRect.contains(x, y)) { checkForUpdate(); }
                return true;
            }

            if (screen == Screen.LEVELS) {
                if (topHomeRect.contains(x, y)) { screen = Screen.HOME; invalidate(); return true; }
                if (levelsPrevPageRect.contains(x, y) && levelPage > 0) { levelPage--; invalidate(); return true; }
                if (levelsNextPageRect.contains(x, y) && levelPage < maxUnlockedLevelPage()) { levelPage++; invalidate(); return true; }
                int first = levelPage * 100 + 1;
                for (int i = 0; i < levelRects.length; i++) {
                    if (levelRects[i].contains(x, y)) {
                        loadPathLevel(first + i);
                        return true;
                    }
                }
                return true;
            }

            if (screen == Screen.LIBRARY) {
                if (topHomeRect.contains(x, y)) { screen = Screen.HOME; invalidate(); return true; }
                if (libraryPrevRect.contains(x, y)) {
                    libraryEntryIndex = Math.floorMod(libraryEntryIndex - 1, SolutionLibrary.ENTRIES.size());
                    invalidate(); return true;
                }
                if (libraryNextRect.contains(x, y)) {
                    libraryEntryIndex = (libraryEntryIndex + 1) % SolutionLibrary.ENTRIES.size();
                    invalidate(); return true;
                }
                return true;
            }

            if (screen == Screen.ANALYSIS) {
                if (topHomeRect.contains(x, y)) { screen = Screen.HOME; invalidate(); }
                return true;
            }

            if (screen == Screen.FREE_SETUP) {
                if (topHomeRect.contains(x, y)) { generationToken++; generating = false; screen = Screen.HOME; invalidate(); return true; }
                if (generating) return true;
                for (int i = 0; i < logicRects.length; i++) if (logicRects[i].contains(x, y)) { freeLogic = i + 1; invalidate(); return true; }
                for (int i = 0; i < calcRects.length; i++) if (calcRects[i].contains(x, y)) { freeCalc = i + 1; invalidate(); return true; }
                for (int i = 0; i < sizeRects.length; i++) if (sizeRects[i].contains(x, y)) { freeSize = i; invalidate(); return true; }
                for (int i = 0; i < maxRects.length; i++) if (maxRects[i].contains(x, y)) { freeMaxIndex = i; invalidate(); return true; }
                for (int i = 0; i < strategyRects.length; i++) if (strategyRects[i].contains(x, y)) { freeStrategyIndex = i; invalidate(); return true; }
                char[] opChars = {'+', '-', '×', '÷', '^'};
                for (int i = 0; i < opRects.length; i++) {
                    if (opRects[i].contains(x, y)) {
                        char op = opChars[i];
                        if (freeOps.contains(op)) {
                            if (freeOps.size() == 1) {
                                Toast.makeText(getContext(), "Оставь хотя бы одну операцию", Toast.LENGTH_SHORT).show();
                            } else if (op != '^' && freeOps.contains('^') && freeOps.size() == 2) {
                                Toast.makeText(getContext(), "Степени пока работают в смешанном режиме — оставь ещё одну базовую операцию", Toast.LENGTH_SHORT).show();
                            } else {
                                freeOps.remove(op);
                            }
                        } else freeOps.add(op);
                        invalidate();
                        return true;
                    }
                }
                if (freeGenerateRect.contains(x, y)) loadFreePuzzle();
                return true;
            }

            if (solved && nextLevelRect.contains(x, y)) {
                if (generating) return true;
                if (mode == GameMode.PATH) loadPathLevel(level + 1);
                else loadFreePuzzle();
                return true;
            }
            if (!focusMode && topHomeRect.contains(x, y)) {
                if (tracker.hasOpenSession() && !solved) tracker.finish(false, "home");
                screen = Screen.HOME; invalidate(); return true;
            }
            if ((!focusMode && menuRect.contains(x, y)) || (focusMode && focusMenuRect.contains(x, y))) {
                showGameMenu();
                return true;
            }
            if (undoRect.contains(x, y)) {
                undoLastAction();
                return true;
            }
            if (candidateRect.contains(x, y)) {
                candidateMode = !candidateMode;
                tracker.event("candidate_mode", -1, -1, candidateMode ? 1 : -1, null);
                selectedTileId = -1;
                invalidate();
                return true;
            }
            if (hintRect.contains(x, y)) {
                showGuidedHint();
                return true;
            }

            for (BankHit hit : bankHits) {
                if (hit.rect.contains(x, y)) {
                    if (candidateMode) {
                        if (selectedCell != null) {
                            Tile t = tileById(hit.tileId);
                            if (t != null) toggleCandidate(selectedCell, t.value);
                            selectedTileId = -1;
                        } else {
                            selectedTileId = (selectedTileId == hit.tileId) ? -1 : hit.tileId;
                            Tile chosen = tileById(hit.tileId);
                            tracker.event(selectedTileId == -1 ? "deselect_tile" : "select_tile", -1, -1,
                                    chosen == null ? 0 : chosen.value, candidateMode ? "candidate" : null);
                        }
                    } else if (selectedCell != null) {
                        saveUndoState();
                        placeTileInCell(hit.tileId, selectedCell);
                        resetHintDepth();
                        candidateNotes.remove(selectedCell);
                        selectedCell = null;
                        selectedTileId = -1;
                        checkSolved();
                    } else {
                        selectedTileId = (selectedTileId == hit.tileId) ? -1 : hit.tileId;
                        Tile chosen = tileById(hit.tileId);
                        tracker.event(selectedTileId == -1 ? "deselect_tile" : "select_tile", -1, -1,
                                chosen == null ? 0 : chosen.value, null);
                    }
                    invalidate();
                    return true;
                }
            }

            Pos hitPos = gridPosAt(x, y);
            if (hitPos != null && puzzle.hidden.contains(hitPos)) {
                if (selectedTileId != -1) {
                    if (candidateMode) {
                        Tile t = tileById(selectedTileId);
                        if (t != null) toggleCandidate(hitPos, t.value);
                    } else {
                        saveUndoState();
                        placeTileInCell(selectedTileId, hitPos);
                        resetHintDepth();
                        candidateNotes.remove(hitPos);
                        checkSolved();
                    }
                    selectedTileId = -1;
                    selectedCell = null;
                } else if (selectedCell != null && selectedCell.equals(hitPos)) {
                    Integer existing = puzzle.placedTile.get(hitPos);
                    if (existing != null && !candidateMode) {
                        saveUndoState();
                        Tile old = tileById(existing);
                        if (old != null) old.used = false;
                        puzzle.placedTile.remove(hitPos);
                        resetHintDepth();
                        tracker.event("remove", hitPos.x, hitPos.y, old == null ? 0 : old.value, null);
                        checkSolved();
                    }
                    selectedCell = null;
                } else {
                    selectedCell = hitPos;
                    selectedTileId = -1;
                    tracker.event("select_cell", hitPos.x, hitPos.y, 0, null);
                }
                invalidate();
            }
            return true;
        }

        Map<Pos, Integer> currentAssignedValues() {
            Map<Pos, Integer> values = new HashMap<>();
            if (puzzle == null) return values;
            for (Map.Entry<Pos, Integer> e : puzzle.placedTile.entrySet()) {
                Tile t = tileById(e.getValue());
                if (t != null) values.put(e.getKey(), t.value);
            }
            return values;
        }

        void showGuidedHint() {
            if (puzzle == null || solved) return;
            final int shownStage = hintStage;
            HintEngine.Hint hint = HintEngine.suggest(puzzle, currentAssignedValues(), shownStage);
            if (hint.focus != null && puzzle.hidden.contains(hint.focus)) {
                selectedCell = hint.focus;
                selectedTileId = -1;
            }
            tracker.event("hint", hint.focus == null ? -1 : hint.focus.x,
                    hint.focus == null ? -1 : hint.focus.y, shownStage + 1, hint.kind);
            hintStage = Math.min(2, shownStage + 1);
            showHintDialog(hint, shownStage);
            invalidate();
        }

        void showHintDialog(HintEngine.Hint hint, int shownStage) {
            TextView text = new TextView(getContext());
            int pad = (int) dp(20);
            text.setPadding(pad, pad, pad, pad);
            text.setText(hint.text);
            text.setTextSize(17f);
            text.setTextColor(ink);
            text.setLineSpacing(0f, 1.16f);
            text.setTextIsSelectable(true);

            ScrollView scroll = new ScrollView(getContext());
            scroll.setFillViewport(true);
            scroll.addView(text);

            AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                    .setTitle("Намёк " + (shownStage + 1) + "/3")
                    .setView(scroll)
                    .setNegativeButton("Закрыть", null);
            if (shownStage < 2) {
                builder.setPositiveButton("Глубже →", (dialog, which) -> showGuidedHint());
            }
            builder.show();
        }

        String installedVersionName() {
            try {
                android.content.pm.PackageInfo info = getContext().getPackageManager()
                        .getPackageInfo(getContext().getPackageName(), 0);
                return info.versionName == null ? "1.30" : info.versionName;
            } catch (android.content.pm.PackageManager.NameNotFoundException ex) {
                return "1.30";
            }
        }

        long installedVersionCode() {
            try {
                android.content.pm.PackageInfo info = getContext().getPackageManager()
                        .getPackageInfo(getContext().getPackageName(), 0);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return info.getLongVersionCode();
                return info.versionCode;
            } catch (android.content.pm.PackageManager.NameNotFoundException ex) {
                return 30L;
            }
        }

        void startUpdateDownload(String version, String downloadUrl) {
            try {
                DownloadManager manager = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager == null) throw new IllegalStateException("DownloadManager недоступен");
                String fileName = "MathCrossword-v" + version + ".apk";
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
                request.setTitle("MathCrossword " + version);
                request.setDescription("Загрузка обновления");
                request.setMimeType("application/vnd.android.package-archive");
                request.setAllowedOverMetered(true);
                request.setAllowedOverRoaming(true);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalFilesDir(getContext(), Environment.DIRECTORY_DOWNLOADS, fileName);
                long id = manager.enqueue(request);
                updateStatus = "скачиваю " + version;
                invalidate();
                Toast.makeText(getContext(), "Обновление скачивается внутри приложения", Toast.LENGTH_SHORT).show();
                waitForUpdateDownload(manager, id, version);
            } catch (RuntimeException ex) {
                updateStatus = "ошибка загрузки";
                invalidate();
                Toast.makeText(getContext(), "Не удалось начать загрузку: " + ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        }

        void waitForUpdateDownload(DownloadManager manager, long id, String version) {
            new Thread(() -> {
                for (int attempt = 0; attempt < 900; attempt++) {
                    Cursor cursor = null;
                    try {
                        cursor = manager.query(new DownloadManager.Query().setFilterById(id));
                        if (cursor != null && cursor.moveToFirst()) {
                            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                Uri uri = manager.getUriForDownloadedFile(id);
                                post(() -> {
                                    pendingInstallUri = uri;
                                    pendingInstallVersion = version;
                                    updateStatus = "скачано " + version;
                                    invalidate();
                                    maybeInstallPendingUpdate();
                                });
                                return;
                            }
                            if (status == DownloadManager.STATUS_FAILED) {
                                post(() -> {
                                    updateStatus = "ошибка загрузки";
                                    invalidate();
                                    Toast.makeText(getContext(), "Android не смог скачать обновление", Toast.LENGTH_LONG).show();
                                });
                                return;
                            }
                        }
                    } catch (RuntimeException ignored) {
                    } finally {
                        if (cursor != null) cursor.close();
                    }
                    try { Thread.sleep(500L); } catch (InterruptedException ex) { return; }
                }
            }, "mathcrossword-update-download").start();
        }

        void maybeInstallPendingUpdate() {
            if (pendingInstallUri == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && !getContext().getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(getContext())
                        .setTitle("Разрешить обновления")
                        .setMessage("Android один раз попросит разрешить MathCrossword устанавливать скачанные обновления. После этого вернись в игру — установка продолжится сама.")
                        .setPositiveButton("Разрешить", (d, which) -> {
                            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + getContext().getPackageName()));
                            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            getContext().startActivity(settings);
                        })
                        .setNegativeButton("Позже", null)
                        .show();
                return;
            }
            try {
                Intent install = new Intent(Intent.ACTION_VIEW);
                install.setDataAndType(pendingInstallUri, "application/vnd.android.package-archive");
                install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getContext().startActivity(install);
            } catch (RuntimeException ex) {
                Toast.makeText(getContext(), "APK скачан, но установщик не открылся", Toast.LENGTH_LONG).show();
            }
        }

        void checkForUpdate() {
            if (updateChecking) return;
            updateChecking = true;
            updateStatus = "проверяю…";
            invalidate();
            UpdateChecker.check(installedVersionName(), new UpdateChecker.Callback() {
                @Override public void onResult(String latestVersion, String downloadUrl, boolean newer) {
                    post(() -> {
                        updateChecking = false;
                        updateStatus = newer ? "доступна " + latestVersion : "актуальная";
                        invalidate();
                        AlertDialog.Builder dialog = new AlertDialog.Builder(getContext())
                                .setTitle(newer ? "Есть обновление" : "Обновление не требуется")
                                .setMessage("Установлена: " + installedVersionName() + " (" + installedVersionCode() + ")\n"
                                        + "Последняя: " + latestVersion);
                        if (newer && downloadUrl != null) {
                            dialog.setPositiveButton("Скачать", (d, which) ->
                                    startUpdateDownload(latestVersion, downloadUrl));
                        }
                        dialog.setNegativeButton("Закрыть", null).show();
                    });
                }

                @Override public void onError(String message) {
                    post(() -> {
                        updateChecking = false;
                        updateStatus = "ошибка проверки";
                        invalidate();
                        Toast.makeText(getContext(), "Не удалось проверить обновление: " + message, Toast.LENGTH_LONG).show();
                    });
                }
            });
        }

        void resetHintDepth() {
            hintStage = 0;
        }

        void saveUndoState() {
            if (puzzle == null) return;
            undoStack.add(new GameSnapshot(puzzle.placedTile, candidateNotes));
            if (undoStack.size() > 80) undoStack.remove(0);
        }

        void undoLastAction() {
            if (puzzle == null || undoStack.isEmpty()) return;
            tracker.event("undo", -1, -1, 0, null);
            resetHintDepth();
            GameSnapshot snap = undoStack.remove(undoStack.size() - 1);
            puzzle.placedTile.clear();
            puzzle.placedTile.putAll(snap.placed);
            for (Tile t : puzzle.tiles) t.used = false;
            for (Integer id : puzzle.placedTile.values()) {
                Tile t = tileById(id);
                if (t != null) t.used = true;
            }
            candidateNotes.clear();
            for (Map.Entry<Pos, LinkedHashSet<Integer>> e : snap.notes.entrySet()) {
                candidateNotes.put(e.getKey(), new LinkedHashSet<>(e.getValue()));
            }
            selectedCell = null;
            selectedTileId = -1;
            checkSolved();
            invalidate();
        }

        void toggleCandidate(Pos pos, int value) {
            if (puzzle == null || !puzzle.hidden.contains(pos) || puzzle.placedTile.containsKey(pos)) return;
            saveUndoState();
            LinkedHashSet<Integer> notes = candidateNotes.computeIfAbsent(pos, k -> new LinkedHashSet<>());
            boolean added = notes.add(value);
            if (!added) notes.remove(value);
            tracker.event(added ? "candidate_add" : "candidate_remove", pos.x, pos.y, value, null);
            resetHintDepth();
            if (notes.isEmpty()) candidateNotes.remove(pos);
            invalidate();
        }

        void placeTileInCell(int tileId, Pos cellPos) {
            Tile incoming = tileById(tileId);
            if (incoming == null) return;
            Integer oldId = puzzle.placedTile.get(cellPos);
            if (oldId != null && oldId == tileId) return;

            Pos previousPos = null;
            for (Map.Entry<Pos, Integer> e : puzzle.placedTile.entrySet()) {
                if (e.getValue() == tileId) { previousPos = e.getKey(); break; }
            }
            if (previousPos != null) puzzle.placedTile.remove(previousPos);

            if (oldId != null) {
                Tile old = tileById(oldId);
                if (old != null) old.used = false;
            }
            incoming.used = true;
            puzzle.placedTile.put(cellPos, tileId);
            tracker.event("place", cellPos.x, cellPos.y, incoming.value,
                    oldId == null ? null : "replace");
        }

        Pos gridPosAt(float x, float y) {
            if (cellSize <= 0) return null;
            int gx = (int) Math.floor((x - originX) / cellSize);
            int gy = (int) Math.floor((y - originY) / cellSize);
            Pos p = new Pos(gx, gy);
            return puzzle.cells.containsKey(p) ? p : null;
        }

        void checkSolved() {
            boolean wasSolved = solved;
            if (puzzle.placedTile.size() != puzzle.hidden.size()) { solved = false; return; }
            for (Equation e : puzzle.equations) {
                Integer a = numberAt(e.a), b = numberAt(e.b), c = numberAt(e.c);
                if (a == null || b == null || c == null || PuzzleGenerator.eval(a, e.operator, b) != c) {
                    solved = false;
                    tracker.event("full_incorrect", -1, -1, 0, null);
                    return;
                }
            }
            solved = true;
            if (!wasSolved && mode == GameMode.PATH && level == progressLevel) {
                progressLevel = level + 1;
                prefs.edit().putInt("currentLevel", progressLevel).apply();
            }
            if (!wasSolved && tracker.hasOpenSession()) tracker.finish(true, "solved");
        }

        float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    }
}
