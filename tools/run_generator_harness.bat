@echo off
set ROOT=%~dp0..\
set SRC=%ROOT%app\src\main\java
set OUT=%ROOT%tools\.harness-classes
if exist "%OUT%" rmdir /S /Q "%OUT%"
mkdir "%OUT%"
javac -encoding UTF-8 -d "%OUT%" ^
  "%SRC%\com\offline\mathcrossword\SolutionStrategy.java" ^
  "%SRC%\com\offline\mathcrossword\PuzzleModel.java" ^
  "%SRC%\com\offline\mathcrossword\GameConfig.java" ^
  "%SRC%\com\offline\mathcrossword\DifficultyScale.java" ^
  "%SRC%\com\offline\mathcrossword\ReasoningGraph.java" ^
  "%SRC%\com\offline\mathcrossword\StrategyEvaluator.java" ^
  "%SRC%\com\offline\mathcrossword\GeneratorPolicy.java" ^
  "%SRC%\com\offline\mathcrossword\GenerationDiagnostics.java" ^
  "%SRC%\com\offline\mathcrossword\DiagonalPolicy.java" ^
  "%SRC%\com\offline\mathcrossword\ConstructiveChainBuilder.java" ^
  "%SRC%\com\offline\mathcrossword\ConstructiveNetworkBuilder.java" ^
  "%SRC%\com\offline\mathcrossword\ConstructiveHypothesisBuilder.java" ^
  "%SRC%\com\offline\mathcrossword\ContradictionKernelBuilder.java" ^
  "%SRC%\com\offline\mathcrossword\ContradictionKernelAnalyzer.java" ^
  "%SRC%\com\offline\mathcrossword\TileBankBuilder.java" ^
  "%SRC%\com\offline\mathcrossword\DeceptiveDecoyBuilder.java" ^
  "%SRC%\com\offline\mathcrossword\ContextualDecoyAnalyzer.java" ^
  "%SRC%\com\offline\mathcrossword\BranchQualityAnalyzer.java" ^
  "%SRC%\com\offline\mathcrossword\MultiFrontResilienceAnalyzer.java" ^
  "%SRC%\com\offline\mathcrossword\PuzzleGenerator.java" ^
  "%SRC%\com\offline\mathcrossword\LogicAnalyzer.java" ^
  "%SRC%\com\offline\mathcrossword\HumanSolver.java" ^
  "%SRC%\com\offline\mathcrossword\CascadeResilienceAnalyzer.java" ^
  "%SRC%\com\offline\mathcrossword\SolutionCounter.java" ^
  "%SRC%\com\offline\mathcrossword\HintEngine.java" ^
  "%~dp0GeneratorHarness.java"
if errorlevel 1 exit /b 1
java -cp "%OUT%" com.offline.mathcrossword.GeneratorHarness %*
