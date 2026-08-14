package com.offline.mathcrossword;

final class GameConfig {
    final int equationCount;
    final int maxNumber;
    final char[] operations;
    final int hiddenTarget;
    final int shapeStyle;

    /** Mature internal 1..5 capability tiers used by existing generators. */
    final int logicLevel;
    final int calcLevel;

    /** Public 1..10 bands plus continuous scores introduced in v22. */
    final int displayLogicLevel;
    final int displayCalcLevel;
    final double logicScore;
    final double calcScore;

    final SolutionStrategy solutionStrategy;
    final boolean pathMode;

    GameConfig(int equationCount, int maxNumber, char[] operations, int hiddenTarget, int shapeStyle, int logicLevel, int calcLevel) {
        this(equationCount, maxNumber, operations, hiddenTarget, shapeStyle, logicLevel, calcLevel, SolutionStrategy.MIXED, false);
    }

    GameConfig(int equationCount, int maxNumber, char[] operations, int hiddenTarget, int shapeStyle,
               int logicLevel, int calcLevel, SolutionStrategy solutionStrategy) {
        this(equationCount, maxNumber, operations, hiddenTarget, shapeStyle, logicLevel, calcLevel, solutionStrategy, false);
    }

    GameConfig(int equationCount, int maxNumber, char[] operations, int hiddenTarget, int shapeStyle,
               int logicLevel, int calcLevel, SolutionStrategy solutionStrategy, boolean pathMode) {
        this(equationCount, maxNumber, operations, hiddenTarget, shapeStyle,
                logicLevel, calcLevel,
                DifficultyScale.displayLogicForTier(logicLevel), DifficultyScale.displayCalcForTier(calcLevel),
                DifficultyScale.displayLogicForTier(logicLevel), DifficultyScale.displayCalcForTier(calcLevel),
                solutionStrategy, pathMode);
    }

    GameConfig(int equationCount, int maxNumber, char[] operations, int hiddenTarget, int shapeStyle,
               int logicLevel, int calcLevel, int displayLogicLevel, int displayCalcLevel,
               double logicScore, double calcScore,
               SolutionStrategy solutionStrategy, boolean pathMode) {
        this.equationCount = equationCount;
        this.maxNumber = maxNumber;
        this.operations = operations;
        this.hiddenTarget = hiddenTarget;
        this.shapeStyle = shapeStyle;
        this.logicLevel = Math.max(1, Math.min(5, logicLevel));
        this.calcLevel = Math.max(1, Math.min(5, calcLevel));
        this.displayLogicLevel = DifficultyScale.clamp10(displayLogicLevel);
        this.displayCalcLevel = DifficultyScale.clamp10(displayCalcLevel);
        this.logicScore = DifficultyScale.clamp(logicScore, 1.0, 10.0);
        this.calcScore = DifficultyScale.clamp(calcScore, 1.0, 10.0);
        this.solutionStrategy = solutionStrategy == null ? SolutionStrategy.MIXED : solutionStrategy;
        this.pathMode = pathMode;
    }

    static GameConfig scaled(int equationCount, int maxNumber, char[] operations, int hiddenTarget, int shapeStyle,
                             int displayLogicLevel, int displayCalcLevel,
                             double logicScore, double calcScore,
                             SolutionStrategy solutionStrategy, boolean pathMode) {
        return new GameConfig(equationCount, maxNumber, operations, hiddenTarget, shapeStyle,
                DifficultyScale.logicTier(displayLogicLevel), DifficultyScale.calcTier(displayCalcLevel),
                displayLogicLevel, displayCalcLevel, logicScore, calcScore, solutionStrategy, pathMode);
    }

    GameConfig withInternalLogic(int newLogicTier, int newHiddenTarget, int newShapeStyle) {
        return new GameConfig(equationCount, maxNumber, operations, newHiddenTarget, newShapeStyle,
                newLogicTier, calcLevel, displayLogicLevel, displayCalcLevel,
                logicScore, calcScore, solutionStrategy, pathMode);
    }
}
