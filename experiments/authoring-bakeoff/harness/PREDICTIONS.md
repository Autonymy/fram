# Rung-0 predictions (pre-registered, before any full run)

Model: claude-opus-4-5-20251101 (pinned). Recorded 2026-06-22.

## Headline expectations (the report verdicts)
- **0A (author `square` from empty):** all three arms should CEILING (correctness@1 ≈ 1.0).
  0A is pure from-scratch authoring; H3 says M2≈M1 once syntax is verifier-gated.
  If M2 cannot ceiling on 0A, the INTERFACE is broken (not the experiment) — that is
  the make-or-break sanity verdict.
- **0B (additive edit: add `dec` given `inc`, `inc` must stay unchanged):** all arms
  should mostly succeed on the new-fn correctness; the interesting signal is
  COLLATERAL_DAMAGE on `inc`. Prediction: M1 (whole-module re-emission) introduces a
  measurable, NON-ZERO collateral-damage rate (must re-type `inc` verbatim → a chance
  to perturb it); M1.5 and M2 (def-level / delta application) should be at or near
  ZERO collateral damage because untouched defs persist by construction.
  At n=30 the d-variant effect on 0B should be small (it's a trivial task); the
  arm effect (M1 collateral > {M1.5,M2}) is the predicted signal, though it may be
  small in absolute terms because `inc` is a 1-line fn (low perturbation surface).

## Distribution / secondary
- KCH (off-surface ops/symbols): near-zero on Rung 0 (square/dec are trivial,
  in-distribution). M2 may show a few STRUCTURAL_INVALID (malformed triples / wrong fN
  wiring) that M1 cannot (text can't be structurally-invalid-but-parseable the same way).
- SEMANTIC_WRONG (negative control): near-zero on Rung 0 (tasks are unambiguous);
  no arm effect expected (R4).
- H2 slope (correctness vs example-count d3<d6<d10): near-FLAT for all arms on Rung 0
  (tasks are easy enough to ceiling regardless of shot count). The slope test is a
  Rung-1 instrument; Rung 0 is a floor/sanity check.
- Cost: M2 tokens/attempt >> M1 (the triple serialization is ~3-4x more output tokens
  for the same program). This is the EXPECTED authoring-token penalty (R5); the
  crossover (M2 cheaper on edits) is a Rung-1+ phenomenon, not visible at Rung 0.

## Shakedown must prove (before full n)
1. M2 recompile gate REJECTS a deliberately type-wrong delta (typecheck fires, not just
   structural verb checks) — the single most important control (D3/C1).
2. The EDN adapter round-trips (model triples -> engine dump -> build -> grade).
3. Error-schema parity across arms (G3): same `<file>:<line>: beagle: <reason>` shape.
