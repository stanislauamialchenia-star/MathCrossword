# v22 — Smooth difficulty

## Why this version exists

The previous public 1–5 scale compressed a large behavioral transition into one jump: old Logic 3 could often feel almost automatic while old Logic 4 could require candidates, pauses and explicit hypothesis testing. With hundreds or thousands of Path levels, that jump wastes useful progression space.

## Model

The player now sees **Logic 1–10** and **Calculation 1–10**. Internally the generator also keeps continuous `logicScore` / `calcScore` values from 1.0 to 10.0. Existing five mature generator capability tiers remain in place as anchors so proven constructors, evaluators and exact-solver invariants do not need a risky rewrite.

Current public-Logic mapping to mature internal tiers:

- 1–2 → tier 1
- 3 → tier 2
- 4–5 → tier 3
- 6–8 → tier 4
- 9–10 → tier 5

Calculation uses a more even 2-public-levels-per-tier mapping.

## Path curve

The first 100 levels intentionally spend much of their resolution around public Logic 4–6. Approximate anchors are:

- level 1: score 1.0
- level 40: score 3.8
- level 55: score 4.5
- level 70: score 5.1
- level 85: score 5.8
- level 100: score 6.4

Levels 101–300 continue toward ~8.5, levels 301–600 toward ~9.5. Beyond that the curve stays in an expert range with a deterministic gentle wave instead of monotonically increasing forever. This lets a 1000-level Path alternate demanding structures rather than becoming a wall of maximum difficulty.

## Continuous anti-collapse

There is no longer a special hard switch at level 70. `antiCollapseStrength(logicScore)` interpolates the acceptable opening forced fraction and maximum HumanSolver cascade. The old failure mode — one entry point causing almost the entire board to fall automatically — is therefore suppressed gradually as the score rises.

A small fixed regression sample after current tuning produced:

| Path | public Logic | score | hidden | opening forced | max forced cascade |
|---:|---:|---:|---:|---:|---:|
| 40 | 4 | ~3.80 | 6 | 1 | 4/6 |
| 50 | 4 | ~4.27 | 6 | 1 | 4/6 |
| 60 | 5 | ~4.68 | 7 | 2 | 4/7 |
| 70 | 5 | ~5.10 | 6 | 2 | 3/6 |
| 80 | 6 | ~5.57 | 8 | 1 | 4/8 |

These are deterministic regression points, not universal claims about subjective human difficulty.

## Calculation 1–10

The arithmetic scale is not cosmetic. v22 expands numeric caps and multiplication/division/quotient bounds across ten public levels and raises exponent limits at the top. Path operation availability also grows gradually from addition through subtraction, multiplication, division and powers.

This keeps Logic and Calculation independent: a structurally difficult puzzle can still use modest arithmetic, and vice versa.

## Free Play

Free Play now accepts 1–10 for both dimensions. The selector is displayed as two rows (1–5 / 6–10) per dimension to preserve touch target size. Public levels are converted to mature internal tiers only at the generator boundary.

## Telemetry and calibration

Play traces preserve public bands plus continuous scores. `DifficultyCalibrator` now emits ten observed-cost bands. This lets later analysis ask not only whether public Logic 6 was over/under-estimated, but whether tasks around score 5.2 behave differently from 5.8 inside the same visible band.

## Known frontier

Public Logic 9–10 still rely on the existing hardest internal tier. v22 gives MIXED tier-5 a still-bounded eight-attempt budget and keeps medium Free Logic 10 around twelve unknowns, where the mature hidden-mask constructor is materially more stable. A five-seed smoke sample generated 3/5 Logic-10 requests in a single generator call; the Android UI independently retries up to three seeds. This is improved but still not a claim of perfect expert-level reliability. A dedicated tier-5 constructor remains a future frontier.
