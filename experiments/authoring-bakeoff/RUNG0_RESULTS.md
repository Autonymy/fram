# Rung-0 results — Authoring bake-off (gate G1)

Run 2026-06-22/23. Harness `harness/`, frozen 9-prompt set (`FREEZE.md`),
model `claude-opus-4-5-20251101` (pinned), R=4, output-token ceiling 8000,
Claude-CLI default decoding (no temperature/seed override — identical all arms).
**540 generations** = 3 arms × 3 density-variants × 2 tasks × n=30.

Tasks (G4 leakage: declared overlap with bank E1/E3 — both are Rung-0 *warmups*
mirroring the bank to establish the from-scratch/additive FLOOR before Rung 1):
- **0A** author `square` from empty (hidden test `square(3)=9`).
- **0B** additive edit — given `inc`, add `dec` (`dec(5)=4` AND `inc` unchanged →
  COLLATERAL_DAMAGE probe `inc(5)=6`).

## Headline table (per arm × task, pooled over density; Wilson 95% CI)

| arm | task | n | correct@1 | correct@k | collateral | ref-err | KCH | SEM_WRONG | STRUCT_INV | rounds |
|-----|------|---|-----------|-----------|------------|---------|-----|-----------|------------|--------|
| M1   | 0A | 90 | 100% [96–100] | 100% [96–100] | 0% [0–4] | 0% [0–4] | 0 | 0 | 0 | 1.0 |
| M1   | 0B | 90 | 100% [96–100] | 100% [96–100] | 0% [0–4] | 0% [0–4] | 0 | 0 | 0 | 1.0 |
| M1.5 | 0A | 90 | 100% [96–100] | 100% [96–100] | 0% [0–4] | 0% [0–4] | 0 | 0 | 0 | 1.0 |
| M1.5 | 0B | 90 | 100% [96–100] | 100% [96–100] | 0% [0–4] | 0% [0–4] | 0 | 0 | 0 | 1.0 |
| M2   | 0A | 90 | 100% [96–100] | 100% [96–100] | 0% [0–4] | 0% [0–4] | 0 | 0 | 0 | 1.0 |
| M2   | 0B | 90 | 100% [96–100] | 100% [96–100] | 0% [0–4] | 0% [0–4] | 0 | 0 | 0 | 1.0 |

All 18 cells (arm×density×task, n=30): **100% correct@1, zero collateral, zero
errors of any bucket, every trial passed on attempt 1** (no retries fired in the live
run). Per-cell Wilson CI [89%,100%].

## H2 learnability slope (correct@1 vs example-count) per arm
| arm | d3 | d6 | d10 | slope |
|-----|----|----|-----|-------|
| M1   | 100% | 100% | 100% | flat |
| M1.5 | 100% | 100% | 100% | flat |
| M2   | 100% | 100% | 100% | flat |

Flat at ceiling for all — Rung-0 tasks are too easy to expose a few-shot learnability
gradient. (Slope is a Rung-1 instrument; here it is a floor check, as predicted.)

## Cost
| arm | out-tok/attempt | out-tok/success | $/attempt | units/task | per-unit-error |
|-----|-----------------|-----------------|-----------|------------|----------------|
| M1   | 129 | 129 | $0.019 | 1.5 defs    | 0 |
| M1.5 | 139 | 139 | $0.019 | 1.0 def     | 0 |
| M2   | 868 | 868 | $0.040 | ~38 triples | 0 |

M2 spends **~6.7× the output tokens** of the text arms for the same program — the
explicit-delta authoring-token penalty (R5), quantified. (Each call also carries a
fixed ~26k cache-token Claude-Code system-prompt floor, shared identically by all
arms; it dominates absolute $ but is not differential — see harness/README confound
note.) The edit-token *crossover* (M2 cheaper on edits) is a Rung-1+ phenomenon, not
visible on a 1-line additive task. Total run: 540 gens, **$14.11**, 204k output tokens.

## G3 error-parity (verified, `harness/g3_parity.bb`)
For the SAME logical failure (a `defn` declaring `:- Int` whose body returns `Bool`),
the normalized error fed back to the model is **BYTE-IDENTICAL** across the text arms
and the claim-EDN arm:
```
module: beagle: defn dec: expected return Int, got Bool
  module: type errors
0 built, 1 error(s)
```
Because the M2 gate IS `beagle-build-all --build-edn` (same compiler, same diagnostic
as M1's `beagle-build-all`). The `<file>:<line>:` prefix is normalized away (M2's dump
drops srcloc per D2); the load-bearing `beagle: <reason>` line is identical. C1/G3 met.

## D3 / C1 make-or-break — the M2 recompile gate (the most important control)
Verified (`harness/selftest2.bb`): a deliberately type-wrong M2 delta (a `dec` whose
body `(> x 1)` returns `Bool` under a `:- Int` signature) is **REJECTED** by the M2
path with `expected return Int, got Bool` + exit 1. The M2 arm routes through the
recompile gate (render full-module triples → `beagle-build-all --build-edn`, the
typecheck-gated path), NOT the bare `:edit-min` structural-only path the brief warns
about. **M2 is recompile-gated identically to M1/M1.5 — the run is VALID, not VOID.**

## Qualitative (verbatim, representative — all attempts passed, so these are the
## per-arm emission STYLES, the diagnostic content)
- **M1 / 0B** (whole-module re-emission — re-types the bystander `inc` every time):
  `(defn inc [x :- Int] :- Int (+ x 1))` / `(defn dec [x :- Int] :- Int (- x 1))`
- **M1.5 / 0B** (changed-defs-only — emits ONLY `dec`; harness preserves `inc`):
  `(defn dec [x :- Int] :- Int (- x 1))`
- **M2 / 0B** (claim delta — 14 fresh triples for the `dec` subtree; **0 of 90
  emissions touch `inc`**): `["@m#21" :kind "list"] … ["@m#23" :v "dec"] …`
- **M2 / 0A** (from scratch — the full `square` AST as 38 `list/fN/leaf` triples,
  faithful to the engine shape): `["@m#1" :kind "list"] ["@m#2" :v "defn"] …`

**Delta-purity datum:** all 90 M2/0B emissions emitted ONLY the `dec` delta — *none*
mentioned `inc`. The structural edit-integrity property is real and observable: M2
*cannot* collaterally damage `inc` because it never re-emits it. M1 must reproduce
`inc` verbatim every time — the exposure Rung 1 will stress with N≥6 bystander sites.

## Verdict against the two Rung-0 expectations
1. **Do all three arms ceiling on 0A?** — YES, all 100% (incl. M2 at 90/90). The M2
   interface is NOT broken: the EDN-triples → claim-delta → recompile-gate path
   authors from scratch as reliably as text.
2. **Does 0B introduce a measurable collateral-damage signal?** — NO at this scale:
   0% collateral for every arm. Rung-0's 1-line bystander (`inc`) is too small a
   perturbation surface for whole-module re-emission to corrupt. This is the PREDICTED
   floor result (PREDICTIONS.md) and is exactly why the collateral-damage headline is
   Rung 1 (multi-callsite rename, N≥6 sites), not Rung 0.

## Gate G1 — STOP
Rung 0 reported at full n with CIs. Per the brief this stops at G1 (before Rung 1).
Negative controls intact (SEMANTIC_WRONG = 0, no arm effect). C1 (matched loop), C2
(frozen equal-effort prompts), G3 (error parity), D3 (M2 recompile-gated) all satisfied.
Data: `scratch/run/rung0-combined.jsonl` (540 rows; scratch is gitignored).
