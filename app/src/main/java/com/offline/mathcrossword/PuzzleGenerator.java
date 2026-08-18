package com.offline.mathcrossword;

import static com.offline.mathcrossword.PuzzleModel.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

final class PuzzleGenerator {
    static final int GENERATOR_VERSION = 23;
    private static final ThreadLocal<GenerationDiagnostics> LAST_DIAGNOSTICS = new ThreadLocal<>();

    static GenerationDiagnostics lastDiagnostics() { return LAST_DIAGNOSTICS.get(); }
    private static final long PATH_SEED = 0x6A09E667F3BCC909L;

    static Puzzle generatePath(int level) {
        level = Math.max(1, level);
        GameConfig base = profileForLevel(level);
        boolean hardPath = base.logicScore >= 4.8;
        GenerationDiagnostics diagnostics = new GenerationDiagnostics(SolutionStrategy.MIXED, base.logicLevel);
        LAST_DIAGNOSTICS.set(diagnostics);
        long generationStarted = System.nanoTime();
        Puzzle best = null;
        int bestScore = Integer.MIN_VALUE;
        int targetRating = base.logicLevel >= 5 ? 5 : (base.logicLevel >= 4 ? 4 : 0);
        int primaryAttempts = base.logicLevel >= 4 ? (hardPath ? 36 : 18) : 140;

        // The level number is a stable recipe. Hard bands no longer take the
        // first valid board: they keep sampling until the human-like rating
        // actually reaches the requested band, with a deterministic best-board
        // fallback if none of the bounded attempts does.
        for (int attempt = 0; attempt < primaryAttempts; attempt++) {
            diagnostics.attempt(false);
            long seed = mix64(PATH_SEED ^ ((long) level * 0x9E3779B97F4A7C15L) ^ ((long) attempt * 0xD1B54A32D192ED03L));
            GameConfig cfg = new GameConfig(
                    base.equationCount,
                    base.maxNumber,
                    base.operations,
                    base.hiddenTarget,
                    Math.floorMod(base.shapeStyle + attempt * 5, 18),
                    base.logicLevel, base.calcLevel,
                    base.displayLogicLevel, base.displayCalcLevel, base.logicScore, base.calcScore,
                    SolutionStrategy.MIXED, base.pathMode);
            Puzzle p = generateCandidate(cfg, seed, diagnostics);
            if (p == null) continue;

            // v20: reject structurally wrong PATH candidates before spending time
            // on deceptive tiles or a second full solver pass. generateCandidate has
            // already measured the base bank; this gate is intentionally cheap.
            if (tooSimilarToRecentGeometry(p, level)) { diagnostics.reject(GenerationDiagnostics.RejectReason.PATH_GEOMETRY_REPEAT); continue; }
            if (p.ratedLogic < base.logicLevel) {
                diagnostics.reject(GenerationDiagnostics.RejectReason.PATH_DIFFICULTY_REJECTED);
                continue;
            }
            PathCascadePolicy.Assessment quickCascade = pathQuickAssessment(p, base.logicLevel);
            if (quickCascade.reject()) {
                recordPathCascadeReject(diagnostics, quickCascade);
                continue;
            }

            PathEvaluation pathEval = evaluatePath(level, p, base.logicLevel, diagnostics);
            if (!pathEval.accepted) {
                diagnostics.reject(GenerationDiagnostics.RejectReason.PATH_DIFFICULTY_REJECTED);
                continue;
            }

            // v20: decoys are allowed to make a puzzle more ambiguous, but never
            // to destroy its reasoning shape. Add them one at a time and keep only
            // those that preserve the same PATH difficulty/cascade gates.
            if (hardPath) {
                int wanted = base.displayLogicLevel >= 9 ? 3 : (base.displayLogicLevel >= 6 ? 2 : 1);
                pathEval = reinforcePathDecoysSafely(level, p, base.maxNumber, wanted,
                        seed ^ 0xA0761D6478BD642FL, base.logicLevel, pathEval, diagnostics);
            }
            if (SolutionCounter.countSolutions(p, 2) != 1) {
                diagnostics.reject(GenerationDiagnostics.RejectReason.FINAL_UNIQUENESS_FAILED);
                continue;
            }
            p.generationStage = 1;
            applyGeneratorMetrics(p, pathEval.logic, pathEval.human,
                    p.generatorScore
                            + CascadeResilienceAnalyzer.qualityBonus(p.solutionStrategy, base.logicLevel, pathEval.cascade)
                            + BranchQualityAnalyzer.qualityBonus(pathEval.branch, base.logicLevel)
                            + MultiFrontResilienceAnalyzer.qualityBonus(pathEval.front),
                    pathEval.cascade);
            applyPathProfiles(p, pathEval);

            if (base.logicLevel < 3) { finalizeDiagnostics(p, diagnostics, generationStarted); return p; }
            if (p.generatorScore > bestScore) { best = p; bestScore = p.generatorScore; }
            if (base.logicLevel == 3 || p.ratedLogic >= targetRating) { finalizeDiagnostics(p, diagnostics, generationStarted); return p; }
        }
        if (best != null) { finalizeDiagnostics(best, diagnostics, generationStarted); return best; }

        // Deterministic airbag. v22 no longer keys this protection to level 70;
        // the continuous Logic score decides when the anti-collapse regime begins.
        for (int soften = 1; soften <= 2; soften++) {
            int floorLogic = hardPath ? Math.min(4, base.logicLevel) : 1;
            int fallbackLogic = Math.max(floorLogic, base.logicLevel - soften);
            int fallbackHidden = Math.max(hardPath ? Math.max(7, base.hiddenTarget - 2) : 3, base.hiddenTarget - Math.max(0, soften - 1));
            for (int attempt = 0; attempt < (hardPath ? 90 : 60); attempt++) {
                diagnostics.attempt(true);
                long seed = mix64(PATH_SEED
                        ^ ((long) level * 0x94D049BB133111EBL)
                        ^ ((long) soften * 0xBF58476D1CE4E5B9L)
                        ^ ((long) attempt * 0xD6E8FEB86659FD93L));
                GameConfig fallback = new GameConfig(base.equationCount, base.maxNumber, base.operations,
                        fallbackHidden, Math.floorMod(base.shapeStyle + attempt * 7, 18),
                        fallbackLogic, base.calcLevel, base.displayLogicLevel, base.displayCalcLevel,
                        base.logicScore, base.calcScore, SolutionStrategy.MIXED, base.pathMode);
                Puzzle p = generateCandidate(fallback, seed, diagnostics);
                if (p == null) continue;
                if (hardPath && p.ratedLogic < fallbackLogic) {
                    diagnostics.reject(GenerationDiagnostics.RejectReason.PATH_DIFFICULTY_REJECTED);
                    continue;
                }
                if (hardPath) {
                    PathCascadePolicy.Assessment quickCascade = pathQuickAssessment(p, fallbackLogic);
                    if (quickCascade.reject()) {
                        recordPathCascadeReject(diagnostics, quickCascade);
                        continue;
                    }
                }
                PathEvaluation pathEval = evaluatePath(level, p, fallbackLogic, diagnostics);
                if (!pathEval.accepted) {
                    diagnostics.reject(GenerationDiagnostics.RejectReason.PATH_DIFFICULTY_REJECTED);
                    continue;
                }
                if (hardPath) {
                    int wantedFallback = base.displayLogicLevel >= 9 ? 3 : (base.displayLogicLevel >= 6 ? 2 : 1);
                    pathEval = reinforcePathDecoysSafely(level, p, base.maxNumber, wantedFallback,
                            seed ^ 0xE7037ED1A0B428DBL, fallbackLogic, pathEval, diagnostics);
                }
                if (SolutionCounter.countSolutions(p, 2) != 1) {
                    diagnostics.reject(GenerationDiagnostics.RejectReason.FINAL_UNIQUENESS_FAILED);
                    continue;
                }
                applyGeneratorMetrics(p, pathEval.logic, pathEval.human,
                        p.generatorScore
                                + BranchQualityAnalyzer.qualityBonus(pathEval.branch, fallbackLogic)
                                + MultiFrontResilienceAnalyzer.qualityBonus(pathEval.front),
                        pathEval.cascade);
                applyPathProfiles(p, pathEval);
                p.generationStage = 4;
                p.strategyTargetMatched = false;
                finalizeDiagnostics(p, diagnostics, generationStarted);
                return p;
            }
        }
        throw new IllegalStateException("Could not generate path puzzle");
    }

    static Puzzle generateFree(int logicLevel, int calcLevel, int size, int maxNumber,
                               Set<Character> enabledOps, long seed, SolutionStrategy strategy) {
        int displayLogic = DifficultyScale.clamp10(logicLevel);
        int displayCalc = DifficultyScale.clamp10(calcLevel);
        double logicScore = displayLogic;
        double calcScore = displayCalc;
        logicLevel = DifficultyScale.logicTier(displayLogic);
        calcLevel = DifficultyScale.calcTier(displayCalc);
        strategy = strategy == null ? SolutionStrategy.MIXED : strategy;
        GenerationDiagnostics diagnostics = new GenerationDiagnostics(strategy, logicLevel);
        LAST_DIAGNOSTICS.set(diagnostics);
        long generationStarted = System.nanoTime();

        int sizeDelta = size == 0 ? -2 : (size == 2 ? 2 : 0);
        int baseEq = clamp(DifficultyScale.pathEquationCount(logicScore) + sizeDelta, 3, 14);
        int eq = clamp(baseEq + GeneratorPolicy.equationDelta(strategy, logicLevel), 3, 14);

        int baseHidden = clamp(DifficultyScale.pathHiddenTarget(logicScore) + sizeDelta, 3, 18);
        // The mature tier-5 hidden-mask constructor is currently much more stable
        // around twelve unknowns. Public Logic 10 should increase reasoning depth,
        // not fail simply because one extra hidden cell pushes the old topology
        // search over a cliff. Keep medium Free L10 at this stable frontier until
        // a dedicated tier-5 constructor replaces the rejection-heavy mask search.
        if (displayLogic == 10 && size == 1) baseHidden = Math.min(baseHidden, 12);
        int hidden = Math.min(18, baseHidden + GeneratorPolicy.hiddenDelta(strategy, logicLevel));

        char[] ops = toOps(enabledOps);
        Puzzle best = null;
        int bestScore = Integer.MIN_VALUE;
        Puzzle ratedFallback = null;
        int ratedFallbackScore = Integer.MIN_VALUE;

        // Phase 1: a small strict budget for the requested strategy. We do not
        // hide structural weaknesses behind huge retry counts: the harness should
        // make weak strategy generators visible.
        int strictAttempts = GeneratorPolicy.strictAttempts(strategy, logicLevel);
        for (int attempt = 0; attempt < strictAttempts; attempt++) {
            diagnostics.attempt(false);
            long s = mix64(seed + attempt * 0x9E3779B97F4A7C15L);
            int shape = GeneratorPolicy.shapeStyle(s, attempt, strategy);
            GameConfig cfg = new GameConfig(eq, maxNumber, ops, hidden, shape, logicLevel, calcLevel,
                    displayLogic, displayCalc, logicScore, calcScore, strategy, false);
            Puzzle p = generateCandidate(cfg, s, diagnostics);
            if (p == null) continue;
            if (!finalUnique(p, diagnostics)) continue;

            long analysisStarted = System.nanoTime();
            LogicAnalyzer.Metrics lm = LogicAnalyzer.analyze(p);
            HumanSolver.Metrics hm = HumanSolver.analyze(p);
            if (p.contradictionKernel && (hm.depth2Deductions > 0 || hm.maxReasoningDepth >= 2))
                p.contradictionKernelDepth = 2;
            diagnostics.addStageTime(GenerationDiagnostics.Stage.HUMAN_ANALYSIS, System.nanoTime() - analysisStarted);
            long evalStarted = System.nanoTime();
            boolean kernelAccepted = strategy == SolutionStrategy.HYPOTHESIS && logicLevel >= 5
                    && p.contradictionKernel && GeneratorPolicy.acceptsHypothesisKernel(lm, hm, logicLevel);
            boolean levelAccepted = GeneratorPolicy.acceptsDifficulty(strategy, lm, hm, logicLevel) || kernelAccepted;
            boolean styleAccepted = GeneratorPolicy.accepts(strategy, lm, hm, logicLevel) || kernelAccepted;
            if (kernelAccepted) p.ratedLogic = Math.max(p.ratedLogic, logicLevel);
            diagnostics.addStageTime(GenerationDiagnostics.Stage.STRATEGY_EVALUATION, System.nanoTime() - evalStarted);
            if (!styleAccepted) diagnostics.reject(GenerationDiagnostics.RejectReason.STRATEGY_MISMATCH);
            int styledScore = p.generatorScore + GeneratorPolicy.bonus(strategy, lm, hm)
                    + (styleAccepted ? 220 : 0);
            p.generatorScore = styledScore;
            p.solutionStrategy = strategy;
            p.generationStrategy = strategy;
            p.generatorVersion = GENERATOR_VERSION;
            p.generationStage = 1;
            p.strategyTargetMatched = styleAccepted;

            if (!levelAccepted && logicLevel >= 4) {
                diagnostics.reject(GenerationDiagnostics.RejectReason.LEVEL_MISMATCH);
                if (p.ratedLogic >= logicLevel && styledScore > ratedFallbackScore) {
                    ratedFallback = p;
                    ratedFallbackScore = styledScore;
                }
                continue;
            }
            if (styledScore > bestScore) {
                best = p;
                bestScore = styledScore;
            }
            if (styleAccepted) { finalizeDiagnostics(p, diagnostics, generationStarted); return p; }
        }

        // If the strict strategy produced a Logic-correct board but missed only
        // the strategy signature, prefer it over any fallback.
        if (best != null) {
            best.strategyTargetMatched = false;
            best.generationStage = 2;
            diagnostics.reject(GenerationDiagnostics.RejectReason.STRATEGY_MISMATCH);
            finalizeDiagnostics(best, diagnostics, generationStarted);
            return best;
        }

        // Phase 2: stable airbag. This intentionally falls back to the proven
        // MIXED geometry policy while keeping the same requested Logic/Calc band
        // and exact unique-solution checks. It is marked in the play trace, so it
        // can never be mistaken for a true Chain/Network sample in research.
        if (strategy != SolutionStrategy.MIXED && logicLevel >= 4) {
            int stableAttempts = GeneratorPolicy.stableFallbackAttempts(strategy, logicLevel);
            Puzzle stableRated = null;
            int stableRatedScore = Integer.MIN_VALUE;
            for (int attempt = 0; attempt < stableAttempts; attempt++) {
                diagnostics.attempt(true);
                long s = attempt == 0
                        ? seed
                        : mix64(seed ^ 0xA0761D6478BD642FL
                        ^ ((long) attempt * 0xE7037ED1A0B428DBL));
                int shape = GeneratorPolicy.shapeStyle(s, attempt, SolutionStrategy.MIXED);
                int stableHidden = (strategy == SolutionStrategy.CHAIN && baseHidden > 9) ? baseHidden - 1 : baseHidden;
                GameConfig cfg = new GameConfig(baseEq, maxNumber, ops, stableHidden,
                        shape, logicLevel, calcLevel, displayLogic, displayCalc, logicScore, calcScore,
                        SolutionStrategy.MIXED, false);
                Puzzle p = generateCandidate(cfg, s, diagnostics);
                if (p == null) { diagnostics.reject(GenerationDiagnostics.RejectReason.FALLBACK_CANDIDATE_FAILED); continue; }
                if (!finalUnique(p, diagnostics)) continue;
                long analysisStarted = System.nanoTime();
                LogicAnalyzer.Metrics lm = LogicAnalyzer.analyze(p);
                HumanSolver.Metrics hm = HumanSolver.analyze(p);
                diagnostics.addStageTime(GenerationDiagnostics.Stage.HUMAN_ANALYSIS, System.nanoTime() - analysisStarted);
                int fallbackScore = p.generatorScore + LogicAnalyzer.qualityScore(lm, hm, logicLevel);
                if (LogicAnalyzer.acceptForLevel(lm, hm, logicLevel)) {
                    p.solutionStrategy = strategy;
                    p.generationStrategy = SolutionStrategy.MIXED;
                    p.strategyTargetMatched = false;
                    p.generationStage = 3;
                    p.generatorVersion = GENERATOR_VERSION;
                    finalizeDiagnostics(p, diagnostics, generationStarted);
                    return p;
                }
                // Compatibility airbag: this mirrors the older, looser "rated"
                // behavior. It is deliberately provenance-marked and must be
                // excluded from strict strategy/difficulty research samples.
                if (p.ratedLogic >= logicLevel && fallbackScore > stableRatedScore) {
                    stableRated = p;
                    stableRatedScore = fallbackScore;
                }
            }
            if (stableRated != null) {
                stableRated.solutionStrategy = strategy;
                stableRated.generationStrategy = SolutionStrategy.MIXED;
                stableRated.strategyTargetMatched = false;
                stableRated.generationStage = 4;
                stableRated.generatorVersion = GENERATOR_VERSION;
                finalizeDiagnostics(stableRated, diagnostics, generationStarted);
                return stableRated;
            }
        }

        // Last resort is still same-rated and uniquely solvable. No difficulty
        // is silently softened. The UI may ask for another seed if even this is absent.
        if (ratedFallback != null) {
            ratedFallback.strategyTargetMatched = false;
            ratedFallback.generationStage = 4;
            ratedFallback.generatorVersion = GENERATOR_VERSION;
            finalizeDiagnostics(ratedFallback, diagnostics, generationStarted);
            return ratedFallback;
        }
        diagnostics.reject(GenerationDiagnostics.RejectReason.NO_ACCEPTABLE_PUZZLE);
        diagnostics.elapsedNanos = System.nanoTime() - generationStarted;
        throw new IllegalStateException("Could not generate requested difficulty");
    }

    static boolean finalUnique(Puzzle p, GenerationDiagnostics diagnostics) {
        long t = System.nanoTime();
        boolean unique = p != null && SolutionCounter.countSolutions(p, 2) == 1;
        if (diagnostics != null) {
            diagnostics.addStageTime(GenerationDiagnostics.Stage.FINAL_UNIQUENESS, System.nanoTime() - t);
            if (!unique) diagnostics.reject(GenerationDiagnostics.RejectReason.FINAL_UNIQUENESS_FAILED);
        }
        return unique;
    }

    static void finalizeDiagnostics(Puzzle p, GenerationDiagnostics diagnostics, long startedNanos) {
        if (p == null || diagnostics == null) return;
        diagnostics.elapsedNanos = System.nanoTime() - startedNanos;
        diagnostics.accept(p);
        p.generationMillis = diagnostics.elapsedNanos / 1_000_000L;
        p.generationAttempts = diagnostics.candidateAttempts;
        p.generationRejects = diagnostics.totalRejects();
        p.generationRejectSummary = diagnostics.compactSummary();
        p.generationStageTimings = diagnostics.stageSummary();
    }

    static boolean pathCascadeAcceptable(int level, Puzzle p, HumanSolver.Metrics hm,
                                         CascadeResilienceAnalyzer.Profile cascade) {
        if (p == null || hm == null || cascade == null || p.hidden.isEmpty()) return true;
        double strength = pathPolicyStrength(p, p.logicLevel);
        if (strength <= 0.0) return true;
        PathCascadePolicy.Assessment assessment = PathCascadePolicy.assess(
                p.hidden.size(), hm.basicForced, hm.basicRemaining, hm.maxForcedCascade,
                hm.reasoningSteps, hm.maxReasoningDepth,
                cascade.maxResolvedAfterOneCell, cascade.vulnerableSingleCells, strength);
        return !assessment.reject();
    }

    static double pathPolicyStrength(Puzzle p, int logicLevel) {
        if (p != null && p.logicScore > 0.0) return DifficultyScale.antiCollapseStrength(p.logicScore);
        if (logicLevel >= 5) return 0.82;
        if (logicLevel >= 4) return 0.48;
        return 0.0;
    }

    static PathCascadePolicy.Assessment pathQuickAssessment(Puzzle p, int requestedLogic) {
        if (p == null || p.hidden.isEmpty()) {
            return PathCascadePolicy.assess(1, 0, 1, 0, 0, 0, 0, 0, 0.0);
        }
        return PathCascadePolicy.assess(
                p.hidden.size(), p.basicForced, p.basicRemaining, p.maxForcedCascade,
                p.reasoningSteps, p.reasoningDepth,
                p.maxResolvedAfterOneCell, p.vulnerableSingleCells,
                pathPolicyStrength(p, requestedLogic));
    }

    static void recordPathCascadeReject(GenerationDiagnostics diagnostics,
                                        PathCascadePolicy.Assessment assessment) {
        if (diagnostics == null || assessment == null) return;
        if (assessment.shape == PathCascadePolicy.Shape.OPENING_COLLAPSE) {
            diagnostics.reject(GenerationDiagnostics.RejectReason.PATH_OPENING_COLLAPSE);
        } else if (assessment.shape == PathCascadePolicy.Shape.SYSTEMIC_FRAGILITY) {
            diagnostics.reject(GenerationDiagnostics.RejectReason.PATH_SYSTEMIC_FRAGILITY);
        } else {
            diagnostics.reject(GenerationDiagnostics.RejectReason.PATH_CASCADE_REJECTED);
        }
    }

    static GameConfig profileForLevel(int level) {
        level = Math.max(1, level);
        double logicScore = DifficultyScale.pathLogicScore(level);
        double calcScore = DifficultyScale.pathCalcScore(level);
        int displayLogic = DifficultyScale.displayLevel(logicScore);
        int displayCalc = DifficultyScale.displayLevel(calcScore);
        int eq = DifficultyScale.pathEquationCount(logicScore);
        int hidden = DifficultyScale.pathHiddenTarget(logicScore);
        int max = DifficultyScale.pathMaxNumber(calcScore);
        char[] ops = DifficultyScale.pathOperations(calcScore);
        int shape = Math.floorMod(level * 23 + (int)Math.round(logicScore * 17.0 + calcScore * 11.0), 18);
        boolean pathMode = DifficultyScale.logicTier(displayLogic) >= 4;
        return GameConfig.scaled(eq, max, ops, hidden, shape, displayLogic, displayCalc,
                logicScore, calcScore, SolutionStrategy.MIXED, pathMode);
    }

    static char[] toOps(Set<Character> enabled) {
        if (enabled == null || enabled.isEmpty()) return new char[]{'+'};
        List<Character> out = new ArrayList<>();
        for (char c : new char[]{'+', '-', '×', '÷', '^'}) if (enabled.contains(c)) out.add(c);
        if (out.isEmpty()) out.add('+');
        char[] a = new char[out.size()];
        for (int i = 0; i < out.size(); i++) a[i] = out.get(i);
        return a;
    }

    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    static Puzzle generateCandidate(GameConfig cfg, long seed) {
        return generateCandidate(cfg, seed, null);
    }

    static Puzzle generateCandidate(GameConfig cfg, long seed, GenerationDiagnostics diagnostics) {
        Random r = new Random(seed);
        int workMax = Math.min(cfg.maxNumber, calculationNumberCap(cfg.displayCalcLevel));

        if (cfg.logicLevel >= 4 && cfg.solutionStrategy == SolutionStrategy.CHAIN) {
            Puzzle chain = ConstructiveChainBuilder.tryGenerate(cfg, seed, workMax, diagnostics);
            if (chain != null) return stampDifficulty(chain, cfg);
            if (diagnostics != null) diagnostics.reject(GenerationDiagnostics.RejectReason.CONSTRUCTIVE_BUILDER_FAILED);
        }
        if (cfg.logicLevel >= 4 && cfg.solutionStrategy == SolutionStrategy.NETWORK) {
            Puzzle network = ConstructiveNetworkBuilder.tryGenerate(cfg, seed, workMax, diagnostics);
            if (network != null) return stampDifficulty(network, cfg);
            if (diagnostics != null) diagnostics.reject(GenerationDiagnostics.RejectReason.CONSTRUCTIVE_BUILDER_FAILED);
        }
        boolean experimentalHypothesisL5 = cfg.logicLevel == 5
                && Boolean.getBoolean("mathcrossword.experimentalHypothesisL5");
        if ((cfg.logicLevel == 4 || experimentalHypothesisL5)
                && cfg.solutionStrategy == SolutionStrategy.HYPOTHESIS) {
            // L4 is production. L5 remains an opt-in research frontier until
            // benchmark data show that the contradiction constructor beats the
            // proven generic path on reliability as well as strategy signature.
            Puzzle hypothesis = ConstructiveHypothesisBuilder.tryGenerate(cfg, seed, workMax, diagnostics);
            if (hypothesis != null) return stampDifficulty(hypothesis, cfg);
            if (diagnostics != null) diagnostics.reject(GenerationDiagnostics.RejectReason.CONSTRUCTIVE_BUILDER_FAILED);
        }

        // v18: hard MIXED/path boards first try two independent cyclic fronts.
        // This directly targets the observed failure mode where finding one key
        // example turns the rest of a connected tree into automatic cleanup.
        if (cfg.logicLevel >= 4 && cfg.solutionStrategy == SolutionStrategy.MIXED
                && cfg.equationCount >= 8 && hasLatticeOperation(cfg.operations)) {
            Puzzle multiFront = generateTwoFrontCandidate(cfg, seed ^ 0x7F4A7C159E3779B9L, workMax, diagnostics);
            if (multiFront != null) return stampDifficulty(multiFront, cfg);
        }

        // Hard modes first try a deliberately cyclic arithmetic lattice. The
        // previous generator mainly grew trees; even a unique tree often falls
        // apart after the first forced value. A lattice gives several routes
        // back to the same numbers, so local choices constrain one another.
        if (GeneratorPolicy.useLattice(cfg.solutionStrategy, cfg.logicLevel, seed) && hasLatticeOperation(cfg.operations)) {
            Puzzle lattice = generateLatticeCandidate(cfg, seed ^ 0xA24BAED4963EE407L, workMax, diagnostics);
            if (lattice != null) { lattice.generatorConstructor = "generic-lattice"; return stampDifficulty(lattice, cfg); }
        }
        List<Slot> slots;
        try {
            slots = buildGeometry(cfg.equationCount, r, cfg.shapeStyle);
        } catch (RuntimeException ex) {
            if (diagnostics != null) diagnostics.reject(GenerationDiagnostics.RejectReason.GEOMETRY_FAILED);
            return null;
        }

        Puzzle p = new Puzzle();
        p.shapeStyle = cfg.shapeStyle;
        p.seed = seed;
        p.logicLevel = cfg.logicLevel;
        p.calcLevel = cfg.calcLevel;
        p.solutionStrategy = cfg.solutionStrategy;
        p.generationStrategy = cfg.solutionStrategy;
        p.generatorVersion = GENERATOR_VERSION;
        p.generatorConstructor = "generic-geometry";
        p.decoyCount = Math.max(0, cfg.logicLevel - 2);

        Set<String> equationKeys = new HashSet<>();
        int[] root = randomEquation(workMax, cfg.operations, r, (int) Math.floorMod(seed, 97), cfg.displayCalcLevel);
        putEquation(p, slots.get(0), root[0], (char) root[1], root[2], root[3]);
        equationKeys.add(eqKey(root[0], (char) root[1], root[2], root[3]));

        for (int i = 1; i < slots.size(); i++) {
            Slot s = slots.get(i);
            int[] e = equationForSlot(p, s, workMax, cfg.operations, r, cfg.displayCalcLevel, equationKeys);
            if (e == null) {
                if (diagnostics != null) diagnostics.reject(GenerationDiagnostics.RejectReason.EQUATION_ASSIGNMENT_FAILED);
                return null;
            }
            equationKeys.add(eqKey(e[0], (char) e[1], e[2], e[3]));
            putEquation(p, s, e[0], (char) e[1], e[2], e[3]);
        }

        long hiddenStarted = System.nanoTime();
        if (!chooseHiddenWithUniqueSolution(p, cfg.hiddenTarget, workMax, cfg.logicLevel, r, cfg.solutionStrategy, cfg.pathMode, diagnostics)) {
            if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.HIDDEN_UNIQUENESS,
                    System.nanoTime() - hiddenStarted);
            if (diagnostics != null) diagnostics.reject(GenerationDiagnostics.RejectReason.HIDDEN_OR_UNIQUENESS_FAILED);
            return null;
        }
        if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.HIDDEN_UNIQUENESS,
                System.nanoTime() - hiddenStarted);
        computeBounds(p);
        return stampDifficulty(p, cfg);
    }

    static Puzzle stampDifficulty(Puzzle p, GameConfig cfg) {
        if (p == null || cfg == null) return p;
        p.logicLevel = cfg.logicLevel;
        p.calcLevel = cfg.calcLevel;
        p.displayLogicLevel = cfg.displayLogicLevel;
        p.displayCalcLevel = cfg.displayCalcLevel;
        p.logicScore = cfg.logicScore;
        p.calcScore = cfg.calcScore;
        return p;
    }

    /**
     * Two physically separate cyclic fronts for hard MIXED/path play.
     * A correct discovery in one component must not automatically determine the
     * other component; the shared tile bank still couples them weakly, while exact
     * uniqueness remains the final mathematical gate.
     */
    static Puzzle generateTwoFrontCandidate(GameConfig cfg, long seed, int workMax,
                                            GenerationDiagnostics diagnostics) {
        if (cfg.equationCount < 8) return null;
        Random r = new Random(seed ^ 0xD6E8FEB86659FD93L);
        long graphStarted = System.nanoTime();

        List<Character> ops = new ArrayList<>();
        for (char op : new char[]{'+', '-', '×', '÷'}) if (contains(cfg.operations, op)) ops.add(op);
        if (ops.isEmpty()) return null;
        char opA = ops.get(r.nextInt(ops.size()));
        char opB = ops.get(r.nextInt(ops.size()));
        int[][] a = buildLatticeValues(opA, workMax, cfg.displayCalcLevel, r);
        int[][] b = buildLatticeValues(opB, workMax, cfg.displayCalcLevel, r);
        if (a == null || b == null) return null;

        Puzzle p = new Puzzle();
        p.shapeStyle = 700 + Math.floorMod(cfg.shapeStyle, 18);
        p.seed = seed;
        p.logicLevel = cfg.logicLevel;
        p.calcLevel = cfg.calcLevel;
        p.solutionStrategy = cfg.solutionStrategy;
        p.generationStrategy = SolutionStrategy.MIXED;
        p.generatorVersion = GENERATOR_VERSION;
        p.generatorFamily = "mixed-two-front";
        p.generatorConstructor = "mixed-two-front-v1";

        // Each core is a four-equation loop. The 8-column separation prevents
        // accidental arithmetic sharing while keeping both fronts on one board.
        putTwoFrontCore(p, 0, 0, a, opA);
        putTwoFrontCore(p, 8, 0, b, opB);
        if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.GRAPH,
                System.nanoTime() - graphStarted);

        Set<String> keys = new HashSet<>();
        for (Equation e : p.equations) {
            keys.add(eqKey(p.cells.get(e.a).number, e.operator,
                    p.cells.get(e.b).number, p.cells.get(e.c).number));
        }

        long arithmeticStarted = System.nanoTime();
        int remaining = cfg.equationCount - 8;
        int guard = 0;
        int side = 0;
        while (remaining > 0 && guard++ < 1200) {
            boolean left = (side++ & 1) == 0;
            List<Pos> anchors = new ArrayList<>();
            for (Map.Entry<Pos, Cell> e : p.cells.entrySet()) {
                if (e.getValue().kind != Kind.NUMBER) continue;
                if (left ? e.getKey().x <= 4 : e.getKey().x >= 8) anchors.add(e.getKey());
            }
            if (anchors.isEmpty()) continue;
            Pos anchor = anchors.get(r.nextInt(anchors.size()));
            int[][] dirs = left
                    ? new int[][]{{-1,0},{0,1},{0,-1}}
                    : new int[][]{{1,0},{0,1},{0,-1}};
            int[] d = dirs[r.nextInt(dirs.length)];
            int childIndex = new int[]{0,2,4}[r.nextInt(3)];
            Slot slot = new Slot(anchor.x - d[0] * childIndex,
                    anchor.y - d[1] * childIndex, d[0], d[1], childIndex);
            if (!slotFitsPuzzle(slot, p)) continue;

            // Do not accidentally turn the gap between the fronts into a bridge.
            boolean crossesMid = false;
            for (Pos q : slot.p) {
                if (left && q.x > 5) crossesMid = true;
                if (!left && q.x < 7) crossesMid = true;
            }
            if (crossesMid) continue;

            int[] e = equationForSlot(p, slot, workMax, cfg.operations, r, cfg.displayCalcLevel, keys);
            if (e == null) continue;
            keys.add(eqKey(e[0], (char)e[1], e[2], e[3]));
            putEquation(p, slot, e[0], (char)e[1], e[2], e[3]);
            remaining--;
        }
        if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.ARITHMETIC,
                System.nanoTime() - arithmeticStarted);
        if (remaining != 0) return null;

        long hiddenStarted = System.nanoTime();
        if (!chooseHiddenWithUniqueSolution(p, cfg.hiddenTarget, workMax, cfg.logicLevel, r, cfg.solutionStrategy, cfg.pathMode, diagnostics)) {
            if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.HIDDEN_UNIQUENESS,
                    System.nanoTime() - hiddenStarted);
            return null;
        }
        if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.HIDDEN_UNIQUENESS,
                System.nanoTime() - hiddenStarted);
        computeBounds(p);
        return p;
    }

    private static void putTwoFrontCore(Puzzle p, int ox, int oy, int[][] v, char op) {
        putEquation(p, new Slot(ox, oy, true, -1), v[0][0], op, v[0][1], v[0][2]);
        putEquation(p, new Slot(ox, oy + 2, true, -1), v[1][0], op, v[1][1], v[1][2]);
        putEquation(p, new Slot(ox, oy, false, -1), v[0][0], op, v[1][0], v[2][0]);
        putEquation(p, new Slot(ox + 2, oy, false, -1), v[0][1], op, v[1][1], v[2][1]);
    }

    static boolean hasLatticeOperation(char[] ops) {
        return contains(ops, '+') || contains(ops, '-') || contains(ops, '×') || contains(ops, '÷');
    }

    static Puzzle generateLatticeCandidate(GameConfig cfg, long seed, int workMax, GenerationDiagnostics diagnostics) {
        if (cfg.equationCount < 5) return null;
        Random r = new Random(seed);
        int coreVariant = cfg.equationCount == 5
                ? 1 + Math.floorMod(cfg.shapeStyle, 2)
                : Math.floorMod(cfg.shapeStyle + (int) (seed & 3), 3);
        List<Character> coreOps = new ArrayList<>();
        for (char op : new char[]{'+', '-', '×', '÷'}) if (contains(cfg.operations, op)) coreOps.add(op);
        if (coreOps.isEmpty()) return null;
        char coreOp = coreOps.get(r.nextInt(coreOps.size()));

        int[][] v = buildLatticeValues(coreOp, workMax, cfg.displayCalcLevel, r);
        if (v == null) return null;

        Puzzle p = new Puzzle();
        p.shapeStyle = 100 + coreVariant * 20 + Math.floorMod(cfg.shapeStyle, 18);
        p.seed = seed;
        p.logicLevel = cfg.logicLevel;
        p.calcLevel = cfg.calcLevel;
        p.solutionStrategy = cfg.solutionStrategy;
        p.generationStrategy = cfg.solutionStrategy;
        p.generatorVersion = GENERATOR_VERSION;

        List<Slot> core = new ArrayList<>();
        int horizontalCount = coreVariant == 1 ? 2 : 3;
        int verticalCount = coreVariant == 2 ? 2 : 3;
        for (int row = 0; row < horizontalCount; row++) {
            Slot slot = new Slot(0, row * 2, true, -1);
            core.add(slot);
            putEquation(p, slot, v[row][0], coreOp, v[row][1], v[row][2]);
        }
        for (int col = 0; col < verticalCount; col++) {
            Slot slot = new Slot(col * 2, 0, false, -1);
            core.add(slot);
            putEquation(p, slot, v[0][col], coreOp, v[1][col], v[2][col]);
        }

        Set<String> keys = new HashSet<>();
        for (Equation e : p.equations) {
            keys.add(eqKey(p.cells.get(e.a).number, e.operator, p.cells.get(e.b).number, p.cells.get(e.c).number));
        }

        int need = cfg.equationCount - core.size();
        int guard = 0;
        while (need > 0 && guard++ < 2500) {
            List<Pos> nums = new ArrayList<>();
            for (Map.Entry<Pos, Cell> e : p.cells.entrySet()) if (e.getValue().kind == Kind.NUMBER) nums.add(e.getKey());
            if (nums.isEmpty()) return null;
            Slot s = null;

            // Expert boards preferentially spend their extra equations on
            // bridges between two existing number cells. That raises the
            // cycle rank of the constraint graph instead of attaching another
            // tree branch that can collapse after one deduction.
            if (cfg.logicLevel >= 5 && r.nextDouble() < 0.72) {
                Set<Pos> occupied = new HashSet<>(p.cells.keySet());
                Set<Pos> numberPositions = new HashSet<>();
                for (Map.Entry<Pos, Cell> ce : p.cells.entrySet()) {
                    if (ce.getValue().kind == Kind.NUMBER) numberPositions.add(ce.getKey());
                }
                Slot bridge = findBridgeSlot(occupied, numberPositions, r);
                if (bridge != null && slotFitsPuzzle(bridge, p)) s = bridge;
            }

            if (s == null) {
                Pos anchor = nums.get(r.nextInt(nums.size()));
                boolean horizontal = r.nextBoolean();
                int childIndex = new int[]{0, 2, 4}[r.nextInt(3)];
                int sx = horizontal ? anchor.x - childIndex : anchor.x;
                int sy = horizontal ? anchor.y : anchor.y - childIndex;
                s = new Slot(sx, sy, horizontal, childIndex);
                if (!slotFitsPuzzle(s, p)) continue;
            }
            int[] e = equationForSlot(p, s, workMax, cfg.operations, r, cfg.displayCalcLevel, keys);
            if (e == null) continue;
            keys.add(eqKey(e[0], (char) e[1], e[2], e[3]));
            putEquation(p, s, e[0], (char) e[1], e[2], e[3]);
            need--;
        }
        if (need != 0) return null;

        long hiddenStarted = System.nanoTime();
        if (!chooseHiddenWithUniqueSolution(p, cfg.hiddenTarget, workMax, cfg.logicLevel, r, cfg.solutionStrategy, cfg.pathMode, diagnostics)) {
            if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.HIDDEN_UNIQUENESS,
                    System.nanoTime() - hiddenStarted);
            return null;
        }
        if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.HIDDEN_UNIQUENESS,
                System.nanoTime() - hiddenStarted);
        computeBounds(p);
        return p;
    }

    static int[][] buildLatticeValues(char op, int max, int calcLevel, Random r) {
        for (int attempt = 0; attempt < 500; attempt++) {
            if (op == '+') {
                int cap = Math.max(2, Math.min(max / 4, calculationNumberCap(calcLevel) / 4));
                if (cap < 2) return null;
                int a = 1 + r.nextInt(cap);
                int b = 1 + r.nextInt(cap);
                int c = 1 + r.nextInt(cap);
                int d = 1 + r.nextInt(cap);
                if (a == b || a == c || a == d || b == c || b == d || c == d) continue;
                long total = (long) a + b + c + d;
                if (total > max) continue;
                return new int[][]{
                        {a, b, a + b},
                        {c, d, c + d},
                        {a + c, b + d, (int) total}
                };
            }

            if (op == '-') {
                int cap = Math.max(2, Math.min(max / 7, calculationNumberCap(calcLevel) / 7));
                if (cap < 2) return null;
                int d = 1 + r.nextInt(cap);
                int f = 1 + r.nextInt(cap);
                int g = 1 + r.nextInt(cap);
                int t = 1 + r.nextInt(cap);
                int b = d + f;
                int c = d + g;
                int a = d + f + g + t;
                int r0 = g + t;
                int r1 = g;
                int c0 = f + t;
                int c1 = f;
                if (a > max || b > max || c > max || r0 > max || c0 > max) continue;
                return new int[][]{
                        {a, b, r0},
                        {c, d, r1},
                        {c0, c1, t}
                };
            }

            if (op == '×') {
                int factorCap = Math.min(7, multiplicationCap(calcLevel, max));
                if (factorCap < 3) return null;
                int a = 2 + r.nextInt(Math.max(1, factorCap - 1));
                int b = 2 + r.nextInt(Math.max(1, factorCap - 1));
                int c = 2 + r.nextInt(Math.max(1, factorCap - 1));
                int d = 2 + r.nextInt(Math.max(1, factorCap - 1));
                if (a == b || a == c || a == d || b == c || b == d || c == d) continue;
                long ab = (long) a * b;
                long cd = (long) c * d;
                long ac = (long) a * c;
                long bd = (long) b * d;
                long all = ab * cd;
                if (ab > max || cd > max || ac > max || bd > max || all > max) continue;
                return new int[][]{
                        {a, b, (int) ab},
                        {c, d, (int) cd},
                        {(int) ac, (int) bd, (int) all}
                };
            }

            if (op == '÷') {
                int factorCap = Math.min(7, divisionCap(calcLevel, max));
                if (factorCap < 3) return null;
                int d = 1 + r.nextInt(Math.min(3, Math.max(1, max)));
                int f = 2 + r.nextInt(Math.max(1, factorCap - 1));
                int g = 2 + r.nextInt(Math.max(1, factorCap - 1));
                int t = 2 + r.nextInt(Math.max(1, factorCap - 1));
                long b = (long) d * f;
                long c = (long) d * g;
                long a = (long) d * f * g * t;
                long r0 = (long) g * t;
                long c0 = (long) f * t;
                if (a > max || b > max || c > max || r0 > max || c0 > max) continue;
                return new int[][]{
                        {(int) a, (int) b, (int) r0},
                        {(int) c, d, g},
                        {(int) c0, f, t}
                };
            }
        }
        return null;
    }

    static boolean slotFitsPuzzle(Slot s, Puzzle p) {
        int shared = 0;
        for (int i = 0; i < 5; i++) {
            Cell old = p.cells.get(s.p[i]);
            if (old == null) continue;
            boolean numberIndex = i == 0 || i == 2 || i == 4;
            if (!numberIndex || old.kind != Kind.NUMBER) return false;
            shared++;
        }
        return shared >= 1 && shared <= 2;
    }

    static boolean tooSimilarToRecentGeometry(Puzzle current, int level) {
        if (level <= 2) return false;
        String currentShape = geometryFingerprint(current);
        int start = Math.max(1, level - 5);
        for (int old = start; old < level; old++) {
            GameConfig oc = profileForLevel(old);
            long seed = mix64(PATH_SEED ^ ((long) old * 0x9E3779B97F4A7C15L));
            try {
                Random rr = new Random(seed);
                List<Slot> slots = buildGeometry(oc.equationCount, rr, oc.shapeStyle);
                String ref = geometryFingerprint(slots);
                if (currentShape.equals(ref)) return true;
            } catch (RuntimeException ignored) { }
        }
        return false;
    }

    static List<Slot> buildGeometry(int count, Random r, int strategy) {
        for (int restart = 0; restart < 60; restart++) {
            List<Slot> slots = new ArrayList<>();
            Set<Pos> occupied = new HashSet<>();
            Set<Pos> numberPositions = new HashSet<>();
            // v14: generic geometry remains orthogonal. Diagonals are no longer
            // a visual-variation knob; specialized constructive builders may use
            // one only when it closes a structurally useful bridge.
            boolean rootHorizontal = ((strategy + restart) & 1) == 0;
            Slot root = new Slot(0, 0, rootHorizontal, -1);
            slots.add(root);
            for (int i = 0; i < 5; i++) {
                occupied.add(root.p[i]);
                if (i == 0 || i == 2 || i == 4) numberPositions.add(root.p[i]);
            }

            int attempts = 0;
            while (slots.size() < count && attempts++ < 7000) {
                Slot candidate = null;

                // From medium-size boards onward, periodically try to bridge two
                // existing number cells. A successful bridge closes a real loop
                // in the constraint graph instead of growing another tree branch.
                if (slots.size() >= 4 && (Math.floorMod(strategy, 3) == 2 || r.nextDouble() < 0.34)) {
                    candidate = findBridgeSlot(occupied, numberPositions, r);
                }

                if (candidate == null) {
                    Slot parent;
                    int family = Math.floorMod(strategy, 3);
                    if (family == 0) {
                        int fromEnd = Math.min(slots.size() - 1, r.nextInt(Math.min(3, slots.size())));
                        parent = slots.get(slots.size() - 1 - fromEnd);
                    } else if (family == 1) {
                        parent = slots.get(r.nextInt(slots.size()));
                    } else {
                        int bound = Math.max(1, (slots.size() + 1) / 2);
                        parent = slots.get(r.nextInt(bound));
                    }

                    int[] numberIndices;
                    int mode = Math.floorMod(strategy / 3, 3);
                    if (mode == 0) numberIndices = new int[]{0, 4, 2};
                    else if (mode == 1) numberIndices = new int[]{2, 0, 4};
                    else numberIndices = new int[]{4, 2, 0};

                    int parentNumberIndex = numberIndices[r.nextInt(3)];
                    Pos cross = parent.p[parentNumberIndex];

                    int childIndex;
                    int childMode = Math.floorMod(strategy / 9, 2);
                    if (childMode == 0) childIndex = new int[]{0, 4, 2}[r.nextInt(3)];
                    else childIndex = new int[]{2, 0, 4}[r.nextInt(3)];

                    int[][] dirs = new int[][]{{1,0},{0,1}};
                    int[] d = dirs[r.nextInt(dirs.length)];
                    // Avoid extending in exactly the same orientation as the parent;
                    // a change of direction is what creates a real crossing/turn.
                    if (Orientation.fromDelta(d[0], d[1]) == parent.orientation && dirs.length > 1) {
                        d = dirs[(r.nextInt(dirs.length - 1) + 1) % dirs.length];
                    }
                    int sx = cross.x - d[0] * childIndex;
                    int sy = cross.y - d[1] * childIndex;
                    candidate = new Slot(sx, sy, d[0], d[1], childIndex);
                    if (!geometrySlotFits(candidate, occupied, numberPositions, 1)) continue;
                }

                int sideTouches = 0;
                for (int i = 0; i < 5; i++) {
                    Pos q = candidate.p[i];
                    if (occupied.contains(q)) continue;
                    Pos[] n = {
                            new Pos(q.x + 1, q.y), new Pos(q.x - 1, q.y),
                            new Pos(q.x, q.y + 1), new Pos(q.x, q.y - 1),
                            new Pos(q.x + 1, q.y + 1), new Pos(q.x - 1, q.y - 1),
                            new Pos(q.x + 1, q.y - 1), new Pos(q.x - 1, q.y + 1)};
                    for (Pos z : n) if (occupied.contains(z)) sideTouches++;
                }
                if (sideTouches > 5 && r.nextDouble() < 0.76) continue;

                slots.add(candidate);
                for (int i = 0; i < 5; i++) {
                    Pos q = candidate.p[i];
                    occupied.add(q);
                    if (i == 0 || i == 2 || i == 4) numberPositions.add(q);
                }
            }
            if (slots.size() == count) return slots;
        }
        throw new IllegalStateException("Could not generate crossword layout");
    }

    static Slot findBridgeSlot(Set<Pos> occupied, Set<Pos> numberPositions, Random r) {
        return findBridgeSlot(occupied, numberPositions, r, false);
    }

    static Slot findBridgeSlot(Set<Pos> occupied, Set<Pos> numberPositions, Random r, boolean allowDiagonal) {
        List<Pos> anchors = new ArrayList<>(numberPositions);
        if (anchors.size() < 2) return null;
        Collections.shuffle(anchors, r);
        int tries = Math.min(360, anchors.size() * (allowDiagonal ? 36 : 24));
        int[] numberIndices = {0, 2, 4};
        int[][] dirs = allowDiagonal
                ? new int[][]{{1,0},{0,1},{1,1},{1,-1}}
                : new int[][]{{1,0},{0,1}};
        for (int t = 0; t < tries; t++) {
            Pos anchor = anchors.get(t % anchors.size());
            int[] d = dirs[r.nextInt(dirs.length)];
            int anchorIndex = numberIndices[r.nextInt(numberIndices.length)];
            int sx = anchor.x - d[0] * anchorIndex;
            int sy = anchor.y - d[1] * anchorIndex;
            Slot s = new Slot(sx, sy, d[0], d[1], anchorIndex);
            if (geometrySlotFits(s, occupied, numberPositions, 2)) return s;
        }
        return null;
    }

    static boolean geometrySlotFits(Slot candidate, Set<Pos> occupied,
                                    Set<Pos> numberPositions, int minSharedNumbers) {
        int shared = 0;
        for (int i = 0; i < 5; i++) {
            Pos q = candidate.p[i];
            if (!occupied.contains(q)) continue;
            boolean isNumberIndex = i == 0 || i == 2 || i == 4;
            if (!isNumberIndex || !numberPositions.contains(q)) return false;
            shared++;
        }
        return shared >= minSharedNumbers && shared <= 3;
    }

    static void putEquation(Puzzle p, Slot s, int a, char op, int b, int c) {
        putNumber(p, s.p[0], a);
        p.cells.put(s.p[1], new Cell(Kind.OP, op));
        putNumber(p, s.p[2], b);
        p.cells.put(s.p[3], new Cell(Kind.EQUAL, '='));
        putNumber(p, s.p[4], c);
        p.equations.add(new Equation(s, op));
    }

    static void putNumber(Puzzle p, Pos pos, int value) {
        Cell old = p.cells.get(pos);
        if (old != null && old.kind == Kind.NUMBER && old.number != value) {
            throw new IllegalStateException("Conflicting number at crossing");
        }
        if (old == null) p.cells.put(pos, new Cell(value));
    }

    static int[] equationForSlot(Puzzle p, Slot s, int max, char[] ops, Random r,
                                         int calcLevel, Set<String> used) {
        Integer a0 = existingNumber(p, s.p[0]);
        Integer b0 = existingNumber(p, s.p[2]);
        Integer c0 = existingNumber(p, s.p[4]);
        int known = (a0 != null ? 1 : 0) + (b0 != null ? 1 : 0) + (c0 != null ? 1 : 0);

        if (known == 1) {
            int index = a0 != null ? 0 : (b0 != null ? 2 : 4);
            int shared = a0 != null ? a0 : (b0 != null ? b0 : c0);
            for (int tries = 0; tries < 100; tries++) {
                int[] e = equationWithShared(shared, index, max, ops, r, calcLevel);
                if (e[0] <= 0 || e[2] <= 0 || e[3] <= 0 || e[1] == '?') continue;
                if (!used.contains(eqKey(e[0], (char) e[1], e[2], e[3]))) return e;
            }
            return null;
        }

        // A slot may cross two existing number cells. That closes a loop in
        // the equation graph. We solve the missing third number instead of
        // destroying the second crossing; this is the main structural change
        // that lets expert boards resist simple tree-like cascades.
        List<Character> order = new ArrayList<>();
        for (char op : ops) order.add(op);
        Collections.shuffle(order, r);
        for (char op : order) {
            Integer a = a0, b = b0, c = c0;
            if (known == 2) {
                Integer missing = SolutionCounter.requiredMissing(a, b, c, op);
                if (missing == null || missing == Integer.MIN_VALUE) continue;
                if (a == null) a = missing;
                else if (b == null) b = missing;
                else c = missing;
            }
            if (a == null || b == null || c == null) continue;
            if (!equationFitsBand(a, op, b, c, max, calcLevel)) continue;
            String key = eqKey(a, op, b, c);
            if (!used.contains(key)) return pack(a, op, b, c);
        }
        return null;
    }

    static Integer existingNumber(Puzzle p, Pos pos) {
        Cell c = p.cells.get(pos);
        return c != null && c.kind == Kind.NUMBER ? c.number : null;
    }

    static boolean equationFitsBand(int a, char op, int b, int c, int max, int calcLevel) {
        if (a <= 0 || b <= 0 || c <= 0 || a > max || b > max || c > max) return false;
        if (PuzzleGenerator.eval(a, op, b) != c) return false;
        if (op == '^' && (b < 2 || b > maxExponent(calcLevel))) return false;
        return true;
    }

    static int[] randomEquation(int max, char[] ops, Random r, int salt, int calcLevel) {
        max = Math.max(3, max);
        for (int tries = 0; tries < 1400; tries++) {
            char op = ops[r.nextInt(ops.length)];
            int a, b, c;
            switch (op) {
                case '+':
                    a = 1 + Math.floorMod(salt + r.nextInt(max), Math.max(1, max - 1));
                    if (a >= max) continue;
                    b = 1 + r.nextInt(max - a);
                    c = a + b;
                    return pack(a, op, b, c);
                case '-':
                    a = 2 + r.nextInt(Math.max(1, max - 1));
                    b = 1 + r.nextInt(Math.max(1, a - 1));
                    c = a - b;
                    return pack(a, op, b, c);
                case '×': {
                    int factorCap = multiplicationCap(calcLevel, max);
                    a = 2 + r.nextInt(Math.max(1, factorCap - 1));
                    int mb = Math.min(factorCap, max / Math.max(1, a));
                    if (mb < 2) continue;
                    b = 2 + r.nextInt(mb - 1);
                    c = a * b;
                    if (c <= max) return pack(a, op, b, c);
                    break;
                }
                case '÷': {
                    int divisorCap = divisionCap(calcLevel, max);
                    b = 2 + r.nextInt(Math.max(1, divisorCap - 1));
                    int qCap = Math.min(divisionQuotientCap(calcLevel), max / b);
                    if (qCap < 1) continue;
                    c = 1 + r.nextInt(qCap);
                    a = b * c;
                    if (a <= max) return pack(a, op, b, c);
                    break;
                }
                case '^': {
                    int maxExp = maxExponent(calcLevel);
                    int exp = 2 + r.nextInt(Math.max(1, maxExp - 1));
                    int baseCap = maxBaseForPower(max, exp);
                    if (baseCap < 2) continue;
                    a = 2 + r.nextInt(baseCap - 1);
                    b = exp;
                    c = safePow(a, b, max);
                    if (c > 0) return pack(a, op, b, c);
                    break;
                }
            }
        }
        return pack(2, '+', 1, 3);
    }

    static int[] equationWithShared(int shared, int index, int max, char[] ops, Random r, int calcLevel) {
        for (int tries = 0; tries < 2200; tries++) {
            char op = ops[r.nextInt(ops.length)];
            int a = 0, b = 0, c = 0;
            boolean ok = true;
            if (index == 0) {
                a = shared;
                switch (op) {
                    case '+':
                        if (a >= max) ok = false;
                        else { b = 1 + r.nextInt(max - a); c = a + b; }
                        break;
                    case '-':
                        if (a <= 1) ok = false;
                        else { b = 1 + r.nextInt(a - 1); c = a - b; }
                        break;
                    case '×': {
                        if (a <= 0 || a > max / 2) { ok = false; break; }
                        int lim = Math.min(multiplicationCap(calcLevel, max), max / a);
                        if (lim < 2) { ok = false; break; }
                        b = 2 + r.nextInt(lim - 1); c = a * b;
                        break;
                    }
                    case '÷': {
                        List<Integer> ds = divisors(a, divisionCap(calcLevel, max));
                        if (ds.isEmpty()) ok = false;
                        else { b = ds.get(r.nextInt(ds.size())); c = a / b; }
                        break;
                    }
                    case '^': {
                        int maxExp = maxExponent(calcLevel);
                        b = 2 + r.nextInt(Math.max(1, maxExp - 1));
                        c = safePow(a, b, max);
                        if (c <= 0) ok = false;
                        break;
                    }
                }
            } else if (index == 2) {
                b = shared;
                switch (op) {
                    case '+':
                        if (b >= max) ok = false;
                        else { a = 1 + r.nextInt(max - b); c = a + b; }
                        break;
                    case '-':
                        if (b >= max) ok = false;
                        else { c = 1 + r.nextInt(max - b); a = b + c; }
                        break;
                    case '×': {
                        if (b <= 0 || b > max / 2) { ok = false; break; }
                        int lim = Math.min(multiplicationCap(calcLevel, max), max / b);
                        if (lim < 2) { ok = false; break; }
                        a = 2 + r.nextInt(lim - 1); c = a * b;
                        break;
                    }
                    case '÷': {
                        if (b <= 0 || b > max) { ok = false; break; }
                        int lim = Math.min(divisionQuotientCap(calcLevel), max / b);
                        if (lim < 1) { ok = false; break; }
                        c = 1 + r.nextInt(lim); a = b * c;
                        break;
                    }
                    case '^': {
                        int maxExp = maxExponent(calcLevel);
                        if (b < 2 || b > maxExp) { ok = false; break; }
                        int baseCap = maxBaseForPower(max, b);
                        if (baseCap < 2) { ok = false; break; }
                        a = 2 + r.nextInt(baseCap - 1);
                        c = safePow(a, b, max);
                        if (c <= 0) ok = false;
                        break;
                    }
                }
            } else {
                c = shared;
                switch (op) {
                    case '+':
                        if (c < 2) ok = false;
                        else { a = 1 + r.nextInt(c - 1); b = c - a; }
                        break;
                    case '-':
                        if (c >= max) ok = false;
                        else { b = 1 + r.nextInt(max - c); a = c + b; }
                        break;
                    case '×': {
                        List<Integer> fs = factorPairsLeft(c, multiplicationCap(calcLevel, max));
                        if (fs.isEmpty()) ok = false;
                        else { a = fs.get(r.nextInt(fs.size())); b = c / a; }
                        break;
                    }
                    case '÷':
                        if (c <= 0 || c > max) ok = false;
                        else {
                            int lim = Math.min(divisionCap(calcLevel, max), max / c);
                            if (lim < 1) ok = false;
                            else { b = 1 + r.nextInt(lim); a = c * b; }
                        }
                        break;
                    case '^': {
                        List<int[]> reps = powerRepresentations(c, maxExponent(calcLevel));
                        if (reps.isEmpty()) ok = false;
                        else {
                            int[] rep = reps.get(r.nextInt(reps.size()));
                            a = rep[0]; b = rep[1];
                        }
                        break;
                    }
                }
            }
            if (ok && a > 0 && b > 0 && c > 0 && a <= max && b <= max && c <= max && eval(a, op, b) == c) {
                return pack(a, op, b, c);
            }
        }
        if (contains(ops, '+')) {
            if (index == 0 && shared < max) return pack(shared, '+', 1, shared + 1);
            if (index == 2 && shared < max) return pack(1, '+', shared, shared + 1);
            if (index == 4 && shared > 1) return pack(1, '+', shared - 1, shared);
        }
        return pack(-1, '?', -1, -1);
    }

    static int calculationNumberCap(int calcLevel) {
        int[] caps = {32, 45, 65, 95, 140, 220, 340, 500, 720, 1000};
        return caps[clamp(calcLevel, 1, 10) - 1];
    }

    static int multiplicationCap(int calcLevel, int max) {
        int[] caps = {10, 12, 15, 18, 22, 28, 34, 40, 50, 60};
        return Math.max(3, Math.min(max, caps[clamp(calcLevel, 1, 10) - 1]));
    }

    static int divisionCap(int calcLevel, int max) {
        int[] caps = {8, 10, 12, 16, 20, 24, 30, 36, 43, 50};
        return Math.max(3, Math.min(max, caps[clamp(calcLevel, 1, 10) - 1]));
    }

    static int divisionQuotientCap(int calcLevel) {
        int[] caps = {10, 12, 15, 20, 27, 35, 45, 60, 80, 100};
        return caps[clamp(calcLevel, 1, 10) - 1];
    }

    static int maxExponent(int calcLevel) {
        calcLevel = clamp(calcLevel, 1, 10);
        if (calcLevel >= 9) return 5;
        if (calcLevel >= 7) return 4;
        return 3;
    }

    static int maxBaseForPower(int max, int exp) {
        int base = 1;
        while (base + 1 <= max && safePow(base + 1, exp, max) > 0) base++;
        return base;
    }

    static int safePow(int base, int exp, int max) {
        if (base <= 0 || exp < 0) return Integer.MIN_VALUE;
        long v = 1;
        for (int i = 0; i < exp; i++) {
            if (base != 0 && v > Math.min((long) max, (long) Integer.MAX_VALUE) / base) return Integer.MIN_VALUE;
            v *= base;
            if (v > max || v > Integer.MAX_VALUE) return Integer.MIN_VALUE;
        }
        return (int) v;
    }

    static List<int[]> powerRepresentations(int result, int maxExp) {
        List<int[]> out = new ArrayList<>();
        if (result <= 0) return out;
        for (int exp = 2; exp <= maxExp; exp++) {
            int baseCap = maxBaseForPower(result, exp);
            for (int base = 2; base <= baseCap; base++) {
                if (safePow(base, exp, result) == result) out.add(new int[]{base, exp});
            }
        }
        return out;
    }

    static int exactExponent(int base, int result) {
        if (base <= 1 || result <= 0) return Integer.MIN_VALUE;
        long v = 1;
        for (int exp = 1; exp <= 12; exp++) {
            if (v > (long) Integer.MAX_VALUE / base) break;
            v *= base;
            if (v == result) return exp;
            if (v > result || v > Integer.MAX_VALUE) break;
        }
        return Integer.MIN_VALUE;
    }

    static int exactRoot(int result, int exp) {
        if (result <= 0 || exp <= 0) return Integer.MIN_VALUE;
        int cap = maxBaseForPower(result, exp);
        for (int base = 1; base <= cap; base++) {
            if (safePow(base, exp, result) == result) return base;
        }
        return Integer.MIN_VALUE;
    }

    static boolean contains(char[] a, char target) {
        for (char c : a) if (c == target) return true;
        return false;
    }

    static List<Integer> divisors(int n, int cap) {
        List<Integer> out = new ArrayList<>();
        for (int d = 2; d <= Math.min(cap, n); d++) if (n % d == 0) out.add(d);
        if (out.isEmpty() && n >= 1) out.add(1);
        return out;
    }

    static List<Integer> factorPairsLeft(int n, int cap) {
        List<Integer> out = new ArrayList<>();
        for (int d = 2; d <= Math.min(cap, n); d++) if (n % d == 0 && n / d <= cap) out.add(d);
        if (out.isEmpty() && n >= 1) out.add(1);
        return out;
    }

    static int[] pack(int a, char op, int b, int c) { return new int[]{a, op, b, c}; }

    static int eval(int a, char op, int b) {
        switch (op) {
            case '+': return a + b;
            case '-': return a - b;
            case '×': return a * b;
            case '÷': return b != 0 && a % b == 0 ? a / b : Integer.MIN_VALUE;
            case '^': return safePow(a, b, Integer.MAX_VALUE);
            default: return Integer.MIN_VALUE;
        }
    }

    static String eqKey(int a, char op, int b, int c) {
        return a + ":" + op + ":" + b + ":" + c;
    }

    static boolean chooseHiddenWithUniqueSolution(Puzzle p, int requested, int maxNumber, int logicLevel, Random r) {
        return chooseHiddenWithUniqueSolution(p, requested, maxNumber, logicLevel, r,
                p == null ? SolutionStrategy.MIXED : p.solutionStrategy, false, null);
    }

    static boolean chooseHiddenWithUniqueSolution(Puzzle p, int requested, int maxNumber, int logicLevel,
                                                   Random r, GenerationDiagnostics diagnostics) {
        return chooseHiddenWithUniqueSolution(p, requested, maxNumber, logicLevel, r,
                p == null ? SolutionStrategy.MIXED : p.solutionStrategy, false, diagnostics);
    }

    static boolean chooseHiddenWithUniqueSolution(Puzzle p, int requested, int maxNumber, int logicLevel,
                                                   Random r, SolutionStrategy strategy) {
        return chooseHiddenWithUniqueSolution(p, requested, maxNumber, logicLevel, r, strategy, false, null);
    }

    static boolean chooseHiddenWithUniqueSolution(Puzzle p, int requested, int maxNumber, int logicLevel,
                                                   Random r, SolutionStrategy strategy,
                                                   GenerationDiagnostics diagnostics) {
        return chooseHiddenWithUniqueSolution(p, requested, maxNumber, logicLevel, r, strategy, false, diagnostics);
    }

    static boolean chooseHiddenWithUniqueSolution(Puzzle p, int requested, int maxNumber, int logicLevel,
                                                   Random r, SolutionStrategy strategy, boolean pathMode,
                                                   GenerationDiagnostics diagnostics) {
        strategy = strategy == null ? SolutionStrategy.MIXED : strategy;
        List<Pos> numbers = new ArrayList<>();
        final Map<Pos, Integer> degree = numberDegrees(p);
        for (Map.Entry<Pos, Cell> e : p.cells.entrySet()) {
            if (e.getValue().kind == Kind.NUMBER) numbers.add(e.getKey());
        }

        int maxTarget = Math.min(requested, Math.max(3, numbers.size() - 1));
        int minTarget = Math.max(3, maxTarget - (logicLevel >= 4 ? 1 : 2));

        Set<Pos> bestHidden = null;
        List<Integer> bestTiles = null;
        int bestScore = Integer.MIN_VALUE;
        LogicAnalyzer.Metrics bestLogic = null;
        HumanSolver.Metrics bestHuman = null;
        int hiddenSamplesSeen = 0;
        boolean stopHiddenSearch = false;

        for (int target = maxTarget; target >= minTarget && !stopHiddenSearch; target--) {
            int hiddenAttempts = pathMode
                    ? (logicLevel >= 5 ? 18 : (logicLevel >= 4 ? 14 : 16))
                    : (logicLevel >= 5 ? 28 : (logicLevel >= 4 ? 24 : (logicLevel >= 3 ? 16 : 32)));
            for (int attempt = 0; attempt < hiddenAttempts; attempt++) {
                long hiddenSetStarted = System.nanoTime();
                p.hidden.clear();
                p.tiles.clear();
                p.placedTile.clear();

                if (logicLevel >= 4) {
                    if (!buildHardHiddenSet(p, numbers, degree, target, logicLevel, r)) continue;
                } else {
                    List<Pos> order = new ArrayList<>(numbers);
                    Collections.shuffle(order, r);
                    if (logicLevel >= 3) {
                        order.sort((a, b) -> {
                            int da = degree.getOrDefault(a, 1);
                            int db = degree.getOrDefault(b, 1);
                            if (da != db) return Integer.compare(db, da);
                            return Integer.compare(a.hashCode(), b.hashCode());
                        });
                        int wobble = Math.min(order.size(), Math.max(1, 2 + attempt % 5));
                        if (wobble > 1) Collections.rotate(order.subList(0, wobble), r.nextInt(wobble));
                    } else if (!order.isEmpty()) {
                        Collections.rotate(order, r.nextInt(order.size()));
                    }

                    for (Pos pos : order) {
                        if (p.hidden.size() >= target) break;
                        p.hidden.add(pos);
                        if (wouldBlankWholeEquation(p)) p.hidden.remove(pos);
                    }
                    if (p.hidden.size() < target) continue;
                }
                if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.HIDDEN_SET,
                        System.nanoTime() - hiddenSetStarted);

                long prefilterStarted = System.nanoTime();
                boolean topologyOk = GeneratorPolicy.hiddenTopologyPrefilter(p, strategy, logicLevel);
                if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.HIDDEN_PREFILTER,
                        System.nanoTime() - prefilterStarted);
                if (!topologyOk) {
                    if (diagnostics != null) diagnostics.reject(GenerationDiagnostics.RejectReason.HIDDEN_TOPOLOGY_REJECTED);
                    continue;
                }

                long tileStarted = System.nanoTime();
                makeTiles(p, r, maxNumber, logicLevel, diagnostics);
                if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.TILE_BANK,
                        System.nanoTime() - tileStarted);
                LogicAnalyzer.Metrics lm = LogicAnalyzer.analyze(p);
                if (logicLevel >= 4 && !GeneratorPolicy.staticPrefilter(strategy, lm, logicLevel)) continue;

                long uniquenessStarted = System.nanoTime();
                boolean uniqueKnown = SolutionCounter.hasUniqueKnownSolution(p);
                if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.UNIQUENESS,
                        System.nanoTime() - uniquenessStarted);
                if (!uniqueKnown) continue;

                if (logicLevel <= 2) {
                    if (!LogicAnalyzer.acceptForLevel(lm, null, logicLevel)) continue;
                    long hiddenHumanStarted = System.nanoTime();
                    HumanSolver.Metrics hm = HumanSolver.analyze(p); // diagnostics only
                    if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.HIDDEN_HUMAN,
                            System.nanoTime() - hiddenHumanStarted);
                    int score = LogicAnalyzer.qualityScore(lm, hm, logicLevel);
                    applyGeneratorMetrics(p, lm, hm, score);
                    return true;
                }

                long hiddenHumanStarted = System.nanoTime();
                HumanSolver.Metrics hm = HumanSolver.analyze(p);
                if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.HIDDEN_HUMAN,
                        System.nanoTime() - hiddenHumanStarted);

                CascadeResilienceAnalyzer.Profile cascade = null;
                if (logicLevel >= 3 && !pathMode) {
                    long cascadeStarted = System.nanoTime();
                    cascade = CascadeResilienceAnalyzer.analyze(p);
                    if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.CASCADE_RESILIENCE,
                            System.nanoTime() - cascadeStarted);
                }

                hiddenSamplesSeen++;
                boolean kernelSignal = strategy == SolutionStrategy.HYPOTHESIS && logicLevel >= 5
                        && ContradictionKernelBuilder.hasExistingKernelQuick(p);
                boolean targetAccepted = GeneratorPolicy.acceptsDifficulty(strategy, lm, hm, logicLevel)
                        || (kernelSignal && GeneratorPolicy.acceptsHypothesisKernel(lm, hm, logicLevel));
                boolean pathQualified = !pathMode || pathHiddenCandidateAcceptable(p, lm, hm, logicLevel);
                int score = LogicAnalyzer.qualityScore(lm, hm, logicLevel)
                        + GeneratorPolicy.hiddenQualityBonus(strategy, lm, hm, logicLevel)
                        + (pathMode ? pathHiddenQualityBonus(p, lm, hm, logicLevel)
                                    : CascadeResilienceAnalyzer.qualityBonus(strategy, logicLevel, cascade))
                        + (kernelSignal ? 300 : 0)
                        + (targetAccepted ? 140 : 0)
                        + (pathQualified ? 120 : 0);

                // v6 uses best-of-N generation. The first valid crossword no
                // longer wins automatically: the strongest reasoning trace in
                // this sample is retained. Hitting the requested band earns a
                // bonus, but a rare seed cannot stall generation forever.
                if ((!pathMode || (pathQualified && targetAccepted)) && score > bestScore) {
                    bestScore = score;
                    bestHidden = new HashSet<>(p.hidden);
                    bestTiles = new ArrayList<>();
                    for (Tile t : p.tiles) bestTiles.add(t.value);
                    bestLogic = lm;
                    bestHuman = hm;
                }

                boolean mayStop = pathMode
                        ? pathQualified && targetAccepted && hiddenSamplesSeen >= (logicLevel >= 5 ? 5 : 4)
                        : GeneratorPolicy.mayStopHiddenSearch(strategy, logicLevel, hiddenSamplesSeen, targetAccepted, lm, hm);
                if (mayStop) {
                    stopHiddenSearch = true;
                    break;
                }
            }
        }

        if (bestHidden != null && bestTiles != null) {
            p.hidden.clear();
            p.hidden.addAll(bestHidden);
            p.tiles.clear();
            p.placedTile.clear();
            int id = 1;
            for (int v : bestTiles) p.tiles.add(new Tile(id++, v));
            p.decoyCount = Math.max(0, p.tiles.size() - p.hidden.size());

            // v16: refine only the winning HYPOTHESIS L5 hidden/tile candidate.
            // Running the contradiction-kernel search inside every hidden sample
            // was correct but wasteful; post-selection makes it a single bounded
            // operation per generated candidate.
            if (strategy == SolutionStrategy.HYPOTHESIS && logicLevel >= 5) {
                boolean kernel = ContradictionKernelBuilder.reinforce(p, maxNumber, r, diagnostics);
                if (kernel) {
                    long kernelProfileStarted = System.nanoTime();
                    ContradictionKernelAnalyzer.Profile profile = ContradictionKernelAnalyzer.analyze(p);
                    ContradictionKernelAnalyzer.apply(p, profile);
                    if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.KERNEL_PROFILE,
                            System.nanoTime() - kernelProfileStarted);
                    bestLogic = LogicAnalyzer.analyze(p);
                    bestHuman = HumanSolver.analyze(p);
                    boolean kernelDifficulty = GeneratorPolicy.acceptsHypothesisKernel(bestLogic, bestHuman, logicLevel);
                    int shapeBonus = profile.deepBranches * 70
                            + profile.depth3Branches * 45
                            + Math.max(0, profile.pivotCount - 1) * 55;
                    bestScore = LogicAnalyzer.qualityScore(bestLogic, bestHuman, logicLevel)
                            + GeneratorPolicy.hiddenQualityBonus(strategy, bestLogic, bestHuman, logicLevel)
                            + 300 + shapeBonus + (kernelDifficulty ? 140 : 0);
                }
            }
            applyGeneratorMetrics(p, bestLogic, bestHuman, bestScore);
            if (strategy == SolutionStrategy.HYPOTHESIS && logicLevel >= 5
                    && p.contradictionKernel
                    && GeneratorPolicy.acceptsHypothesisKernel(bestLogic, bestHuman, logicLevel)) {
                p.ratedLogic = Math.max(p.ratedLogic, 5);
            }
            return true;
        }
        return false;
    }

    static final class PathEvaluation {
        final LogicAnalyzer.Metrics logic;
        final HumanSolver.Metrics human;
        final CascadeResilienceAnalyzer.Profile cascade;
        final BranchQualityAnalyzer.Profile branch;
        final MultiFrontResilienceAnalyzer.Profile front;
        final boolean accepted;
        PathEvaluation(LogicAnalyzer.Metrics logic, HumanSolver.Metrics human,
                       CascadeResilienceAnalyzer.Profile cascade,
                       BranchQualityAnalyzer.Profile branch,
                       MultiFrontResilienceAnalyzer.Profile front,
                       boolean accepted) {
            this.logic = logic; this.human = human; this.cascade = cascade;
            this.branch = branch; this.front = front; this.accepted = accepted;
        }
    }

    static PathEvaluation evaluatePath(int level, Puzzle p, int logicLevel, GenerationDiagnostics diagnostics) {
        long started = System.nanoTime();
        LogicAnalyzer.Metrics lm = LogicAnalyzer.analyze(p);
        HumanSolver.Metrics hm = HumanSolver.analyze(p);
        CascadeResilienceAnalyzer.Profile cascade = CascadeResilienceAnalyzer.analyze(p);
        if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.PATH_POST_ANALYSIS,
                System.nanoTime() - started);

        long branchStarted = System.nanoTime();
        BranchQualityAnalyzer.Profile branch = BranchQualityAnalyzer.analyze(p);
        if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.BRANCH_QUALITY,
                System.nanoTime() - branchStarted);

        long frontStarted = System.nanoTime();
        MultiFrontResilienceAnalyzer.Profile front = MultiFrontResilienceAnalyzer.analyze(p);
        if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.FRONT_RESILIENCE,
                System.nanoTime() - frontStarted);

        boolean accepted = LogicAnalyzer.acceptForLevel(lm, hm, logicLevel)
                && pathCascadeAcceptable(level, p, hm, cascade)
                && pathFrontAcceptable(p, front);
        return new PathEvaluation(lm, hm, cascade, branch, front, accepted);
    }

    static boolean pathBranchAcceptable(HumanSolver.Metrics human,
                                        BranchQualityAnalyzer.Profile branch,
                                        int logicLevel) {
        if (human == null || branch == null || logicLevel < 4) return true;
        // A hard PATH board may be a network or deduction puzzle and therefore
        // does not have to contain a hypothesis pivot. But if HumanSolver has no
        // forward reasoning step at all, the remaining uncertainty must at least
        // contain a compact, contextual hypothesis. Otherwise difficulty is just
        // an unstructured search through a wide domain.
        if (human.reasoningSteps == 0 && !branch.hasUsefulHypothesis()) return false;
        if (branch.goodPivotCount == 0 && branch.maxBranchWidth > (logicLevel >= 5 ? 7 : 6)) return false;
        return branch.goodPivotCount > 0 || branch.bruteForcePivotCount <= (logicLevel >= 5 ? 3 : 2);
    }

    static boolean pathFrontAcceptable(Puzzle p, MultiFrontResilienceAnalyzer.Profile front) {
        if (p == null || front == null) return true;
        // Only the constructor that explicitly promises two workable regions is
        // gated by this metric. Generic MIXED puzzles can legitimately be a
        // hypothesis or network problem with one connected front.
        if (!"mixed-two-front".equals(p.generatorFamily)) return true;
        return front.hasAlternativeFronts() && front.balance >= 0.20;
    }

    static void applyPathProfiles(Puzzle p, PathEvaluation e) {
        if (p == null || e == null) return;
        if (e.branch != null) {
            p.branchPivotCount = e.branch.pivotCount;
            p.branchGoodPivotCount = e.branch.goodPivotCount;
            p.branchSeriousFalseBranches = e.branch.seriousFalseBranches;
            p.branchDepth2RefutableBranches = e.branch.depth2RefutableBranches;
            p.branchDepth2SurvivingBranches = e.branch.depth2SurvivingBranches;
            p.branchMaxWidth = e.branch.maxBranchWidth;
            p.branchMaxInformationGain = e.branch.maxInformationGain;
        }
        if (e.front != null) {
            p.reasoningFronts = e.front.alternativeFronts;
            p.reasoningFrontBalance = e.front.balance;
            p.reasoningLargestFrontFraction = e.front.largestFrontFraction;
            p.reasoningFrontBottleneckDegree = e.front.bottleneckDegree;
        }
    }

    static PathEvaluation reinforcePathDecoysSafely(int level, Puzzle p, int maxNumber, int wanted,
                                                     long seed, int logicLevel, PathEvaluation baseEval,
                                                     GenerationDiagnostics diagnostics) {
        PathEvaluation current = baseEval;
        Random r = new Random(seed);

        ContextualDecoyAnalyzer.Profile context = ContextualDecoyAnalyzer.analyze(p, logicLevel);
        ContextualDecoyAnalyzer.apply(p, context);
        int targetContextual = Math.max(0, wanted);
        int replacementsNeeded = Math.max(0, targetContextual - context.contextualValues);

        // First improve surplus tiles that already exist. Replacing a generic
        // decoy keeps the bank size stable and therefore avoids turning useful
        // ambiguity into visual clutter.
        for (int pass = 0; pass < replacementsNeeded; pass++) {
            List<Integer> surplus = surplusTileIndexes(p);
            if (surplus.isEmpty()) break;
            Collections.shuffle(surplus, r);
            boolean replaced = false;
            int frontier = Math.min(4, surplus.size());
            for (int k = 0; k < frontier && !replaced; k++) {
                TileSnapshot snapshot = new TileSnapshot(p);
                int index = surplus.get(k);
                if (index < 0 || index >= p.tiles.size()) continue;
                p.tiles.remove(index);
                p.decoyCount = Math.max(0, p.tiles.size() - p.hidden.size());

                long decoyStarted = System.nanoTime();
                int added = DeceptiveDecoyBuilder.reinforce(p, maxNumber, 1, r, logicLevel, 2);
                if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.DECEPTIVE_DECOYS,
                        System.nanoTime() - decoyStarted);
                if (added <= 0) {
                    snapshot.restore(p);
                    continue;
                }

                PathEvaluation enriched = evaluatePath(level, p, logicLevel, diagnostics);
                ContextualDecoyAnalyzer.Profile enrichedContext = ContextualDecoyAnalyzer.analyze(p, logicLevel);
                boolean contextualGain = enrichedContext.contextualValues > context.contextualValues;
                boolean bruteForceWorse = enriched.branch.bruteForcePivotCount > current.branch.bruteForcePivotCount;
                boolean branchShapePreserved = enriched.branch.goodPivotCount >= current.branch.goodPivotCount
                        || enriched.branch.seriousFalseBranches >= current.branch.seriousFalseBranches;
                if (!enriched.accepted || !contextualGain || bruteForceWorse || !branchShapePreserved) {
                    snapshot.restore(p);
                    continue;
                }
                current = enriched;
                context = enrichedContext;
                ContextualDecoyAnalyzer.apply(p, context);
                replaced = true;
            }
            if (!replaced) break;
        }

        // If the existing bank still has fewer than the target number of useful
        // contextual alternatives, one extra tile may be appended. It must create
        // a new contextual value and improve the branch picture; otherwise it is
        // discarded. This makes "more candidates" conditional on actual value.
        if (context.contextualValues < targetContextual) {
            TileSnapshot snapshot = new TileSnapshot(p);
            long decoyStarted = System.nanoTime();
            int added = DeceptiveDecoyBuilder.reinforce(p, maxNumber, 1, r, logicLevel, 2);
            if (diagnostics != null) diagnostics.addStageTime(GenerationDiagnostics.Stage.DECEPTIVE_DECOYS,
                    System.nanoTime() - decoyStarted);
            if (added > 0) {
                PathEvaluation enriched = evaluatePath(level, p, logicLevel, diagnostics);
                ContextualDecoyAnalyzer.Profile enrichedContext = ContextualDecoyAnalyzer.analyze(p, logicLevel);
                boolean contextualGain = enrichedContext.contextualValues > context.contextualValues;
                boolean branchImproved = enriched.branch.goodPivotCount > current.branch.goodPivotCount
                        || enriched.branch.seriousFalseBranches > current.branch.seriousFalseBranches
                        || enriched.branch.maxInformationGain > current.branch.maxInformationGain;
                boolean bruteForceWorse = enriched.branch.bruteForcePivotCount > current.branch.bruteForcePivotCount;
                if (enriched.accepted && contextualGain && branchImproved && !bruteForceWorse) {
                    current = enriched;
                    context = enrichedContext;
                } else {
                    snapshot.restore(p);
                }
            } else {
                snapshot.restoreMetricsOnly(p);
            }
        }

        // Always derive the final counters from the actual tile bank. This keeps
        // telemetry correct even when a good contextual decoy was already present
        // in the v13 TileBankBuilder and no v21 modification was necessary.
        context = ContextualDecoyAnalyzer.analyze(p, logicLevel);
        ContextualDecoyAnalyzer.apply(p, context);
        return current;
    }

    static List<Integer> surplusTileIndexes(Puzzle p) {
        List<Integer> out = new ArrayList<>();
        if (p == null) return out;
        Map<Integer, Integer> need = new LinkedHashMap<>();
        for (Pos pos : p.hidden) {
            Cell c = p.cells.get(pos);
            if (c != null) need.put(c.number, need.getOrDefault(c.number, 0) + 1);
        }
        for (int i = 0; i < p.tiles.size(); i++) {
            int v = p.tiles.get(i).value;
            int n = need.getOrDefault(v, 0);
            if (n > 0) need.put(v, n - 1);
            else out.add(i);
        }
        return out;
    }

    static final class TileSnapshot {
        final List<Integer> values = new ArrayList<>();
        final int decoyCount;
        final int deceptiveDecoyCount;
        final int deceptiveDecoySupportMax;
        final int contextualDecoyCount;
        final int resourceConflictDecoyCount;
        final int contextualDecoyConstraintSupportMax;
        final int contextualDecoyDepthMax;
        final int contextualDecoyInformationGainMax;

        TileSnapshot(Puzzle p) {
            for (Tile t : p.tiles) values.add(t.value);
            decoyCount = p.decoyCount;
            deceptiveDecoyCount = p.deceptiveDecoyCount;
            deceptiveDecoySupportMax = p.deceptiveDecoySupportMax;
            contextualDecoyCount = p.contextualDecoyCount;
            resourceConflictDecoyCount = p.resourceConflictDecoyCount;
            contextualDecoyConstraintSupportMax = p.contextualDecoyConstraintSupportMax;
            contextualDecoyDepthMax = p.contextualDecoyDepthMax;
            contextualDecoyInformationGainMax = p.contextualDecoyInformationGainMax;
        }

        void restore(Puzzle p) {
            p.tiles.clear();
            int id = 1;
            for (int v : values) p.tiles.add(new Tile(id++, v));
            restoreMetricsOnly(p);
        }

        void restoreMetricsOnly(Puzzle p) {
            p.decoyCount = decoyCount;
            p.deceptiveDecoyCount = deceptiveDecoyCount;
            p.deceptiveDecoySupportMax = deceptiveDecoySupportMax;
            p.contextualDecoyCount = contextualDecoyCount;
            p.resourceConflictDecoyCount = resourceConflictDecoyCount;
            p.contextualDecoyConstraintSupportMax = contextualDecoyConstraintSupportMax;
            p.contextualDecoyDepthMax = contextualDecoyDepthMax;
            p.contextualDecoyInformationGainMax = contextualDecoyInformationGainMax;
        }
    }

    static boolean pathHiddenCandidateAcceptable(Puzzle p, LogicAnalyzer.Metrics lm, HumanSolver.Metrics hm, int logicLevel) {
        if (p == null || lm == null || hm == null || p.hidden.isEmpty()) return false;
        int hidden = p.hidden.size();
        PathCascadePolicy.Assessment cascadeShape = PathCascadePolicy.assess(
                hidden, hm.basicForced, hm.basicRemaining, hm.maxForcedCascade,
                hm.reasoningSteps, hm.maxReasoningDepth, 0, 0,
                pathPolicyStrength(p, logicLevel));
        return !cascadeShape.reject()
                && hm.basicRemaining >= Math.max(6, (hidden * 3) / 4)
                && lm.ambiguousEquations >= (logicLevel >= 5 ? 5 : 4)
                && lm.crossHidden >= (logicLevel >= 5 ? 4 : 3)
                && lm.cycleRank >= (logicLevel >= 5 ? 2 : 1);
    }

    static int pathHiddenQualityBonus(Puzzle p, LogicAnalyzer.Metrics lm, HumanSolver.Metrics hm, int logicLevel) {
        if (p == null || hm == null) return 0;
        int hidden = Math.max(1, p.hidden.size());
        PathCascadePolicy.Assessment cascadeShape = PathCascadePolicy.assess(
                hidden, hm.basicForced, hm.basicRemaining, hm.maxForcedCascade,
                hm.reasoningSteps, hm.maxReasoningDepth, 0, 0,
                pathPolicyStrength(p, logicLevel));
        int score = hm.basicRemaining * 24 + hm.initialBranchCells * 12 + hm.reasoningSteps * 55;
        score -= hm.basicForced * 110;
        if (hm.basicForced == 0) score += 90;
        if (cascadeShape.productiveCascade()) score += Math.min(180, hm.maxForcedCascade * 18);
        else if (hm.maxForcedCascade <= Math.max(3, hidden / 3)) score += 40;
        return score;
    }

    static boolean pathQuickPrecheck(int level, Puzzle p, int requestedLogic) {
        if (p == null || p.hidden.isEmpty()) return true;
        if (p.ratedLogic < requestedLogic) return false;
        return !pathQuickAssessment(p, requestedLogic).reject();
    }

    static void applyGeneratorMetrics(Puzzle p, LogicAnalyzer.Metrics lm, HumanSolver.Metrics hm, int score) {
        applyGeneratorMetrics(p, lm, hm, score, null);
    }

    static void applyGeneratorMetrics(Puzzle p, LogicAnalyzer.Metrics lm, HumanSolver.Metrics hm, int score,
                                      CascadeResilienceAnalyzer.Profile knownCascade) {
        p.generatorScore = score;
        p.ratedLogic = GeneratorPolicy.estimateLevel(p.solutionStrategy, lm, hm);
        p.ratedDisplayLogic = estimateDisplayLogic(p.ratedLogic, lm, hm);
        p.basicForced = hm.basicForced;
        p.basicRemaining = hm.basicRemaining;
        p.maxForcedCascade = hm.maxForcedCascade;
        p.lookaheadDeductions = hm.lookaheadDeductions;
        p.depth2Deductions = hm.depth2Deductions;
        p.reasoningDepth = hm.maxReasoningDepth;
        p.reasoningSteps = hm.reasoningSteps;
        CascadeResilienceAnalyzer.Profile cascade = knownCascade != null
                ? knownCascade : CascadeResilienceAnalyzer.analyze(p);
        p.maxResolvedAfterOneCell = cascade.maxResolvedAfterOneCell;
        p.maxAdditionalForcedAfterOneCell = cascade.maxAdditionalForcedAfterOneCell;
        p.maxResolvedFractionAfterOneCell = cascade.maxResolvedFractionAfterOneCell;
        p.vulnerableSingleCells = cascade.vulnerableSingleCells;
        p.maxResolvedAfterOneEquation = cascade.maxResolvedAfterOneEquation;
        p.maxResolvedFractionAfterOneEquation = cascade.maxResolvedFractionAfterOneEquation;
    }

    static int estimateDisplayLogic(int legacyTier, LogicAnalyzer.Metrics m, HumanSolver.Metrics h) {
        legacyTier = clamp(legacyTier, 1, 5);
        if (legacyTier == 1) return (m.ambiguousEquations > 0 || h.basicRemaining > 1) ? 2 : 1;
        if (legacyTier == 2) return (m.crossHidden >= 1 && h.basicRemaining >= Math.max(3, m.hidden / 2)) ? 4 : 3;
        if (legacyTier == 3) return 5;
        if (legacyTier == 4) {
            int score = 6;
            if (h.initialAverageDomain >= 2.35 && h.initialBranchCells >= Math.max(5, m.hidden / 2)) score++;
            if (h.reasoningSteps >= 2 || h.maxReasoningDepth >= 1 || m.cycleRank >= 2) score++;
            return Math.min(8, score);
        }
        int score = 9;
        if (h.maxReasoningDepth >= 2 && h.depth2Deductions > 0 && h.initialAverageDomain >= 2.8) score = 10;
        return score;
    }

    static boolean buildHardHiddenSet(Puzzle p, List<Pos> numbers, Map<Pos, Integer> degree,
                                          int target, int logicLevel, Random r) {
        p.hidden.clear();
        List<Pos> crossings = new ArrayList<>();
        for (Pos q : numbers) if (degree.getOrDefault(q, 1) >= 2) crossings.add(q);
        Collections.shuffle(crossings, r);
        crossings.sort((a, b) -> Integer.compare(degree.getOrDefault(b, 1), degree.getOrDefault(a, 1)));
        if (crossings.isEmpty()) return false;

        int crossIndex = 0;
        int guard = 0;
        while (p.hidden.size() < target && guard++ < 120) {
            // First repair equations that currently expose exactly one blank.
            // A second blank removes the direct one-line calculation and forces
            // the player to combine it with the rest of the graph.
            Equation single = null;
            List<Equation> shuffled = new ArrayList<>(p.equations);
            Collections.shuffle(shuffled, r);
            for (Equation e : shuffled) {
                if (hiddenCount(p, e) == 1) { single = e; break; }
            }

            if (single != null) {
                List<Pos> candidates = new ArrayList<>();
                for (Pos q : new Pos[]{single.a, single.b, single.c}) {
                    if (!p.hidden.contains(q)) candidates.add(q);
                }
                Collections.shuffle(candidates, r);
                // Prefer a degree-1 partner: it closes this equation without
                // accidentally creating a new one-blank equation elsewhere.
                candidates.sort(Comparator.comparingInt(q -> degree.getOrDefault(q, 1)));
                boolean added = false;
                for (Pos q : candidates) {
                    p.hidden.add(q);
                    if (hardHiddenAllowed(p, degree, logicLevel)) { added = true; break; }
                    p.hidden.remove(q);
                }
                if (added) continue;
            }

            // Once an equation already has two hidden numbers, hard modes
            // may hide the third as well when that equation is tied back into
            // the graph through at least two crossings. This creates a local
            // ambiguity pocket that cannot be opened by one-line arithmetic.
            if (logicLevel >= 4 && p.hidden.size() < target) {
                List<Equation> fullCandidates = new ArrayList<>(p.equations);
                Collections.shuffle(fullCandidates, r);
                boolean madeFull = false;
                for (Equation e : fullCandidates) {
                    if (hiddenCount(p, e) != 2) continue;
                    for (Pos q : new Pos[]{e.a, e.b, e.c}) {
                        if (p.hidden.contains(q)) continue;
                        p.hidden.add(q);
                        if (hardHiddenAllowed(p, degree, logicLevel)) {
                            madeFull = true;
                            break;
                        }
                        p.hidden.remove(q);
                    }
                    if (madeFull) break;
                }
                if (madeFull) continue;
            }

            // Start another ambiguous cluster from a crossing.
            boolean added = false;
            while (crossIndex < crossings.size()) {
                Pos q = crossings.get(crossIndex++);
                if (p.hidden.contains(q)) continue;
                p.hidden.add(q);
                if (hardHiddenAllowed(p, degree, logicLevel)) { added = true; break; }
                p.hidden.remove(q);
            }
            if (added) continue;

            // Fallback to any legal number if every crossing is already used.
            List<Pos> rest = new ArrayList<>(numbers);
            Collections.shuffle(rest, r);
            for (Pos q : rest) {
                if (p.hidden.contains(q)) continue;
                p.hidden.add(q);
                if (hardHiddenAllowed(p, degree, logicLevel)) { added = true; break; }
                p.hidden.remove(q);
            }
            if (!added) break;
        }

        if (p.hidden.size() < target) return false;
        int singles = 0;
        for (Equation e : p.equations) if (hiddenCount(p, e) == 1) singles++;
        int allowedSingles = logicLevel >= 5 ? 0 : 1;
        return singles <= allowedSingles;
    }

    static boolean hardHiddenAllowed(Puzzle p, Map<Pos, Integer> degree, int logicLevel) {
        int full = 0;
        int maxFull = p.shapeStyle >= 100 ? (logicLevel >= 5 ? 6 : 4) : 2;
        for (Equation e : p.equations) {
            if (hiddenCount(p, e) != 3) continue;
            full++;
            int linked = 0;
            for (Pos q : new Pos[]{e.a, e.b, e.c}) {
                if (degree.getOrDefault(q, 1) >= 2) linked++;
            }
            if (linked < 2 || full > maxFull) return false;
        }
        return true;
    }

    static int fullHiddenEquationCount(Puzzle p) {
        int n = 0;
        for (Equation e : p.equations) if (hiddenCount(p, e) == 3) n++;
        return n;
    }

    static int hiddenCount(Puzzle p, Equation e) {
        int n = 0;
        if (p.hidden.contains(e.a)) n++;
        if (p.hidden.contains(e.b)) n++;
        if (p.hidden.contains(e.c)) n++;
        return n;
    }

    static Map<Pos, Integer> numberDegrees(Puzzle p) {
        Map<Pos, Integer> degree = new HashMap<>();
        for (Equation e : p.equations) {
            for (Pos q : new Pos[]{e.a, e.b, e.c}) degree.put(q, degree.getOrDefault(q, 0) + 1);
        }
        return degree;
    }

    static void makeTiles(Puzzle p, Random r, int maxNumber, int logicLevel) {
        makeTiles(p, r, maxNumber, logicLevel, null);
    }

    static void makeTiles(Puzzle p, Random r, int maxNumber, int logicLevel,
                          GenerationDiagnostics diagnostics) {
        TileBankBuilder.build(p, r, maxNumber, logicLevel, diagnostics);
    }

    static void reinforceHumanAmbiguity(Puzzle p, int maxNumber, int logicLevel, Random r) {
        int targetSingletons = 0;
        int extraLimit = logicLevel >= 5 ? 3 : 2;

        for (int extra = 0; extra < extraLimit; extra++) {
            HumanSolver.State base = HumanSolver.initialState(p);
            Map<Pos, Set<Integer>> domains = HumanSolver.allDomains(p, base);
            List<Pos> singles = new ArrayList<>();
            for (Map.Entry<Pos, Set<Integer>> e : domains.entrySet()) {
                if (e.getValue().size() == 1) singles.add(e.getKey());
            }
            if (singles.size() <= targetSingletons) return;
            Collections.shuffle(singles, r);

            int decoy = -1;
            for (Pos pos : singles) {
                decoy = findHumanDecoyForCell(p, pos, maxNumber, r);
                if (decoy > 0) break;
            }
            if (decoy <= 0) return;
            p.tiles.add(new Tile(p.tiles.size() + 1, decoy));
        }
    }

    static int findHumanDecoyForCell(Puzzle p, Pos pos, int maxNumber, Random r) {
        HumanSolver.State base = HumanSolver.initialState(p);
        Set<Integer> before = HumanSolver.domainFor(p, pos, base);

        // v12: derive a compact pool of mathematically plausible alternatives from
        // the equations touching this cell. The old implementation scanned every
        // integer up to maxNumber (often 1000) and ran a full local-consistency
        // check for each one. Network puzzles paid most of their generation time here.
        Set<Integer> pool = plausibleExternalValuesForCell(p, pos, base, maxNumber);
        List<Integer> ordered = new ArrayList<>(pool);
        Collections.shuffle(ordered, r);
        ordered.sort((a, b) -> {
            int da = Math.abs(a - p.cells.get(pos).number);
            int db = Math.abs(b - p.cells.get(pos).number);
            return Integer.compare(da, db);
        });

        for (int candidate : ordered) {
            if (candidate <= 0 || candidate > maxNumber || before.contains(candidate)) continue;
            HumanSolver.State probe = new HumanSolver.State(base);
            probe.remaining.put(candidate, probe.remaining.getOrDefault(candidate, 0) + 1);
            if (HumanSolver.assign(probe, pos, candidate) && HumanSolver.allLocallyPossible(p, probe)) return candidate;
        }

        // Tiny bounded fallback for unusual exponent/division layouts. This keeps
        // robustness without returning to an O(maxNumber) scan.
        int fallbackTrials = Math.min(48, Math.max(12, maxNumber / 20));
        int start = r.nextInt(Math.max(1, maxNumber));
        for (int k = 0; k < fallbackTrials; k++) {
            int candidate = 1 + Math.floorMod(start + k * 37, maxNumber);
            if (before.contains(candidate)) continue;
            HumanSolver.State probe = new HumanSolver.State(base);
            probe.remaining.put(candidate, probe.remaining.getOrDefault(candidate, 0) + 1);
            if (HumanSolver.assign(probe, pos, candidate) && HumanSolver.allLocallyPossible(p, probe)) return candidate;
        }
        return -1;
    }

    static Set<Integer> plausibleExternalValuesForCell(Puzzle p, Pos pos, HumanSolver.State base, int maxNumber) {
        Set<Integer> intersection = null;
        for (Equation e : p.equations) {
            if (!pos.equals(e.a) && !pos.equals(e.b) && !pos.equals(e.c)) continue;
            Set<Integer> local = plausibleValuesFromEquation(p, e, pos, base, maxNumber);
            if (local.isEmpty()) continue;
            if (intersection == null) intersection = new LinkedHashSet<>(local);
            else intersection.retainAll(local);
        }
        if (intersection != null && !intersection.isEmpty()) return intersection;

        // If exact intersection is empty (often because another hidden cell needs
        // a value that is not currently in the bank), use the union as candidates
        // and let allLocallyPossible perform the final exact validation.
        Set<Integer> union = new LinkedHashSet<>();
        for (Equation e : p.equations) {
            if (!pos.equals(e.a) && !pos.equals(e.b) && !pos.equals(e.c)) continue;
            union.addAll(plausibleValuesFromEquation(p, e, pos, base, maxNumber));
        }
        return union;
    }

    static Set<Integer> plausibleValuesFromEquation(Puzzle p, Equation e, Pos target,
                                                     HumanSolver.State base, int maxNumber) {
        Set<Integer> out = new LinkedHashSet<>();
        List<Integer> av = possibleValuesAtForDecoy(p, e.a, target, base);
        List<Integer> bv = possibleValuesAtForDecoy(p, e.b, target, base);
        List<Integer> cv = possibleValuesAtForDecoy(p, e.c, target, base);

        if (target.equals(e.a)) {
            for (int b : bv) for (int c : cv) {
                int v = solveA(e.operator, b, c);
                if (v > 0 && v <= maxNumber) out.add(v);
            }
        } else if (target.equals(e.b)) {
            for (int a : av) for (int c : cv) {
                int v = solveB(a, e.operator, c);
                if (v > 0 && v <= maxNumber) out.add(v);
            }
        } else {
            for (int a : av) for (int b : bv) {
                int v = eval(a, e.operator, b);
                if (v > 0 && v != Integer.MIN_VALUE && v <= maxNumber) out.add(v);
            }
        }
        return out;
    }

    static List<Integer> possibleValuesAtForDecoy(Puzzle p, Pos q, Pos target, HumanSolver.State base) {
        if (q.equals(target)) return Collections.emptyList();
        Integer known = HumanSolver.valueAt(p, q, base);
        if (known != null) return Collections.singletonList(known);
        List<Integer> out = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : base.remaining.entrySet()) {
            if (e.getValue() > 0) out.add(e.getKey());
        }
        return out;
    }

    static int solveA(char op, int b, int c) {
        switch (op) {
            case '+': return c - b;
            case '-': return b + c;
            case '×': return b != 0 && c % b == 0 ? c / b : Integer.MIN_VALUE;
            case '÷': return b > 0 ? b * c : Integer.MIN_VALUE;
            case '^': return exactRoot(c, b);
            default: return Integer.MIN_VALUE;
        }
    }

    static int solveB(int a, char op, int c) {
        switch (op) {
            case '+': return c - a;
            case '-': return a - c;
            case '×': return a != 0 && c % a == 0 ? c / a : Integer.MIN_VALUE;
            case '÷': return c != 0 && a % c == 0 ? a / c : Integer.MIN_VALUE;
            case '^': return exactExponent(a, c);
            default: return Integer.MIN_VALUE;
        }
    }

    static boolean wouldBlankWholeEquation(Puzzle p) {
        for (Equation e : p.equations) {
            if (p.hidden.contains(e.a) && p.hidden.contains(e.b) && p.hidden.contains(e.c)) return true;
        }
        return false;
    }

    static void computeBounds(Puzzle p) {
        p.minX = Integer.MAX_VALUE; p.minY = Integer.MAX_VALUE;
        p.maxX = Integer.MIN_VALUE; p.maxY = Integer.MIN_VALUE;
        for (Pos q : p.cells.keySet()) {
            p.minX = Math.min(p.minX, q.x); p.maxX = Math.max(p.maxX, q.x);
            p.minY = Math.min(p.minY, q.y); p.maxY = Math.max(p.maxY, q.y);
        }
    }

    static String geometryFingerprint(Puzzle p) {
        List<Slot> slots = new ArrayList<>();
        for (Equation e : p.equations) {
            int dx = Integer.signum(e.c.x - e.a.x);
            int dy = Integer.signum(e.c.y - e.a.y);
            Slot s = new Slot(e.a.x, e.a.y, dx, dy, -1);
            slots.add(s);
        }
        return geometryFingerprint(slots);
    }

    static String geometryFingerprint(List<Slot> slots) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        for (Slot s : slots) for (Pos p : s.p) { minX = Math.min(minX, p.x); minY = Math.min(minY, p.y); }
        List<String> rows = new ArrayList<>();
        for (Slot s : slots) {
            Pos a = s.p[0];
            rows.add(s.orientation.key + (a.x - minX) + "," + (a.y - minY));
        }
        Collections.sort(rows);
        StringBuilder b = new StringBuilder();
        for (String x : rows) b.append(x).append('|');
        return b.toString();
    }

    static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}

