# Play trace format (v9)

`SessionTracker` хранит последние 500 сессий во внутреннем файле приложения `play_history.jsonl`. Каждая строка — один JSON-объект. Данные не отправляются по сети.

Основные поля сессии:

- `sessionId`
- `startedAtEpochMs`, `finishedAtEpochMs`
- `mode` (`PATH` / `FREE`)
- `level`
- `seed`
- `logic`, `calc`
- `strategy`
- `hidden`, `equations`
- `ratedLogic`
- `predictedSteps`, `predictedDepth`
- `basicForced`, `basicRemaining`
- `solved`
- `finishReason`
- `activeMs` — не включает время, когда Activity поставлена на паузу
- `firstActionMs`
- `longestPauseBetweenActionsMs`
- `eventCount`
- `events[]`

Поля события:

- `tMs` — активное время от появления головоломки;
- `type`;
- опциональные `x`, `y`;
- опциональный `value`;
- опциональный `detail`.

Типы событий включают:

- `select_cell`
- `select_tile`, `deselect_tile`
- `place`, `remove`
- `candidate_mode`
- `candidate_add`, `candidate_remove`
- `undo`
- `reset`
- `hint` — `value` содержит глубину намёка (1..3), `detail` — класс подсказки
- `full_incorrect`

Назначение trace — калибровка генератора, сравнение прогнозируемой и фактической сложности и последующий анализ стратегии решения. Это не инструмент психологической диагностики личности.


## Generator provenance (v9+)
- `generatorVersion` — версия генератора.
- `generationStage` — 1 primary, 2 expanded, 3 logic-safe style fallback, 4 rated emergency fallback.
- `strategyTargetMatched` — была ли строго достигнута выбранная стратегия решения.
- `generationStrategy` — какой политикой реально создано поле; при страховочном fallback может быть `MIXED`.

Для исследований эти поля обязательны: сравнивать времена и поведение между версиями генератора нужно отдельно или с явным учётом версии.

## Hypothesis kernel provenance (v16+)

For generated Hypothesis L5 puzzles the session metadata may include:

- `contradictionKernel`;
- `contradictionKernelAddedDecoy`;
- `contradictionKernelDepth`.

This describes the **task**, not the player. A kernel means the puzzle contains a false branch that survives immediate local reasoning while the exact final solution remains unique. Later analysis can compare this property with whether the player actually used candidates, tentative placements, undo, or a hypothesis-like trajectory.


## v17 task-shape + trace-analysis fields

Hypothesis L5 task metadata can additionally include:

- `contradictionKernelFamily`;
- `contradictionKernelBranches`;
- `contradictionKernelPivots`;
- `contradictionKernelDepth2Branches`;
- `contradictionKernelDepth3Branches` (reserved; current bounded profiler normally leaves it 0);
- `contradictionKernelDeepBranches`;
- `contradictionKernelMaxRemaining`.

Derived interaction fields now include:

- `productivePauses`;
- `deadEndPauses`;
- `hypothesisEpisodes`;
- `candidateCommitments`;
- `avgCandidateCommitmentMs`;
- `recoveryEpisodes`;
- `avgRecoveryActions`;
- `rapidCascades`.

These are deliberately conservative **interaction signals**, not labels of mental state. They become useful when compared across repeated puzzles and known generator structures.


## v18 cascade provenance

Session puzzle metadata now additionally includes:
- `maxForcedCascade`;
- `maxResolvedAfterOneCell`;
- `maxResolvedFractionAfterOneCell`;
- `vulnerableSingleCells`;
- `maxResolvedAfterOneEquation`;
- `maxResolvedFractionAfterOneEquation`.

`maxForcedCascade` is the gameplay-relevant prediction: it comes from HumanSolver reasoning. The one-cell/equation reveal fields are structural probes and should not be interpreted as "the player would solve this much after one move" because the probe injects correct information directly.

The local analysis layer can combine these task fields with `rapidCascades`, productive/dead-end pauses, candidate/Undo/hint behavior and `DifficultyCalibrator` output. This supports comparisons between predicted structure and observed play without claiming to infer private mental states.

## v21 quality-of-uncertainty provenance

Puzzle/session metadata can now additionally include:

- `contextualDecoyCount`;
- `resourceConflictDecoyCount`;
- `contextualDecoyConstraintSupportMax`;
- `contextualDecoyDepthMax`;
- `contextualDecoyInformationGainMax`;
- `branchPivotCount`;
- `branchGoodPivotCount`;
- `branchSeriousFalseBranches`;
- `branchDepth2RefutableBranches`;
- `branchDepth2SurvivingBranches`;
- `branchMaxWidth`;
- `branchMaxInformationGain`;
- `reasoningFronts`;
- `reasoningFrontBalance`;
- `reasoningLargestFrontFraction`;
- `reasoningFrontBottleneckDegree`.

A `resourceConflictDecoy` can be an extra copy of a value that is genuinely required elsewhere. The tile is false **at the tested target**, can satisfy several local constraints there, but cannot belong to a second full solution because exact final uniqueness is still a hard gate.

The branch/front fields describe opportunities and topology in the task. They are not evidence that a player had a particular private thought, mental state or personality trait. They become useful only when compared with observable interaction events such as candidate edits, tentative placements, undo, pauses, hints and later recovery.


## v22 continuous difficulty fields

Sessions additionally record:

- `logicScore`: continuous 1.0–10.0 logic coordinate used to shape the puzzle;
- `calcScore`: continuous 1.0–10.0 calculation coordinate;
- `logic` / `calc`: rounded public 1–10 bands shown in the UI;
- `ratedLogic`: public 1–10 heuristic rating in generator v22 (the historical JSON key is retained for compatibility).

Replay/test separation remains unchanged. First-pass Path sessions are the main input for difficulty calibration; `PATH_REPLAY` and `PATH_TEST` should be analyzed separately when studying learning or repeated exposure.
