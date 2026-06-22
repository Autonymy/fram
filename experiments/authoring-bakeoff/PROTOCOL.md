# Claim-Algebra vs. Text: Code-Authoring Bake-Off Protocol (v1)

**Status:** spec for implementation. Run gates are hard — do not skip.
**Owner:** Tom · **Harness author:** agent · **Grounding agent:** supplies engine-exact tuples + reference programs.

> Two implementation flags (from review, wired in as hard controls):
> - **G3 error-report parity:** confirm M1-text errors are as rich as M2-tuple errors before scoring. If the engine reports better on applied deltas than parsed source, that asymmetry is the C1 confound — flag/equalize or annotate the run void.
> - **Budget degradation order:** if token spend binds, drop the 6-example prompt variant (keep 3 and 10) BEFORE reducing n. n protects the CIs; the sweep is secondary.

---

## 0. Reframed hypothesis (read this first)

The experiment does **not** test "tuples reduce hallucination." That framing is too broad and largely tests a problem (syntax) that constrained decoding already solves. The pre-registered hypothesis is narrower and falsifiable:

> **H1 (edit integrity):** Under a *matched* verify-and-retry loop and *comparable* prompting effort, structured additive claim-deltas (M2) produce fewer collateral-damage errors and fewer reference errors during *modification* of an existing program than text re-emission (M1), at a measured token-cost ratio.

> **H2 (learnability):** M2's correctness climbs faster per few-shot example than M1's (which is near-saturated), indicating the representation is learnable even if absolute correctness at low example counts is lower.

Secondary, exploratory:

> **H3 (authoring):** For *from-scratch authoring*, M2 ≈ M1 on correctness once syntax is verifier-gated on both arms (the authoring advantage is expected small/null; this is a control, not the headline).

---

## 1. The four confounds this protocol exists to neutralize

| # | Confound | If uncontrolled | Control mechanism |
|---|----------|-----------------|------------------------------|
| C1 | **Verifier attribution** | M2 wins because it has a compile-gate-retry loop and M1 doesn't → win belongs to the verifier | §4 — *identical* verify-and-retry loop on both arms |
| C2 | **Prompt-effort asymmetry** | M2 lovingly tuned, M1 naive → fake win | §5 — frozen, equal-effort, held-out prompt variants, pre-registered |
| C3 | **OOD/verbosity tax** | M2 loses because ~8× tokens + ~0 pretraining → "bad representation" is wrong conclusion | §6 — measure *slope* (H2) + token-cost axis, not just absolute |
| C4 | **Fantasy interface** | Tuple examples "illustrative" not engine-exact → measuring a notation that doesn't exist | §9 gate G0 — engine-exact precondition blocks the run |

If any control is dropped, the corresponding result is void. Note it in the run log.

---

## 2. Arms

- **M1 — text.** Model emits Clojure-subset source text, e.g. `(defn add [a :- Int b :- Int] :- Int (+ a b))`.
- **M2 — free-tuple (claim algebra).** Model emits a flat changeset of engine-exact tuples; harness applies as claim-deltas (mint/supersede); engine recompile-gates the batch fail-closed.
- **M3 — constrained-tuple.** *Deferred.* Only if M2 shows promise on Rung 0–1. First run is M1 vs M2 only.

---

## 3. Held-constant (fairness frame)

Identical across arms, per task, per trial: model+version (pinned), decoding params (temp/top-p/max-tokens/seed — recorded), token budget per attempt, the verify-and-retry loop (§4), task set + order randomization + grading harness, few-shot example *count* within a cell. The ONLY within-cell difference between M1/M2 is the representation + the parse/apply step.

---

## 4. The matched verify-and-retry loop (C1 control)

```
PROMPT (task + current program state, in the arm's representation)
        │ ▼
   MODEL emits an artifact
        │ ▼
 ┌─────────────────────────────────────────────┐
 │ PARSE/APPLY  (the ONLY arm-specific step)    │
 │  M1: parse source text → candidate program   │
 │  M2: parse tuple changeset → apply as         │
 │       claim-delta (mint/supersede)            │
 └─────────────────────────────────────────────┘
        │ ▼
   ENGINE GATE (fail-closed, IDENTICAL both arms):
     compile + typecheck + run test vector
   ┌────┴─────┐
   ▼          ▼
  PASS       FAIL → structured error report (same schema both arms)
   │          │ AGENT retries with errors if budget remains
 record       └──► loop until PASS or budget/round limit
```

**Critical (C1):** M1 text is NOT "emit once and eval" — same compile→error→retry affordance as M2. Error schema (location, kind, message) must be as informative for text as tuples (**G3**). Retry budget: fixed `R=4` rounds AND a token ceiling, whichever binds, same both arms.

---

## 5. Prompt fairness (C2 control)

1. Equal variant count `P=3` per modality (vary example density 3/6/10 + framing — same axes both arms).
2. Equal-effort authoring (time-boxed / fixed iteration cycles; log effort; no arm unbounded polish).
3. Held-out dev set, disjoint from scored tasks. Freeze prompts before touching scored tasks.
4. Pre-register frozen prompt hashes + the primary comparison (§7) before first scored generation. Post-hoc changes = exploratory.
5. (Optional, stronger) different agents author M1 vs M2 prompts; record who/what.

---

## 6. Learnability sweep & cost axis (C3 control)

Within each (modality × task): sweep few-shot count {3, 6, 10} (= the three variants).
- **H2 slope:** fit correctness vs example-count; report slope + CI per arm. Steep M2 vs flat (saturated) M1 = learnability signal even if M2 trails absolute at count=3.
- **Cost axis (always):** tokens/attempt (sum over retries), tokens/successful-program, wall-time, op/tuple/line count.
- **Decisive question:** `per-unit error rate × unit count`. Report both per-unit error AND units-per-task; a representation can be safer per-unit and worse per-task. Don't collapse.

---

## 7. Pre-registered metrics & statistics

**Primary:** correctness@1 / @k; **collateral-damage rate** (edit tasks — the H1 headline: previously-correct def's behavior changed unintentionally); **reference-error rate** (dangling/wrong-target ref).
**Secondary:** rejected-delta/parse rate; retry-rounds-to-success; KCH rate; semantic-wrong rate.

**Pre-registered hallucination taxonomy** (one PRIMARY bucket per failure, by precedence **COLLATERAL_DAMAGE > KCH > REFERENCE_ERROR > SEMANTIC_WRONG > STRUCTURAL_INVALID**; freeze before run):
1. **STRUCTURAL_INVALID** — rejected by parser (M1) / tuple-shape-or-grammar check (M2) before the engine gate. (unbalanced parens / misplaced `:-`; malformed tuple / bad arity index.)
2. **REFERENCE_ERROR** — well-formed but points wrong: dangling/wrong node-ref, mis-indexed `param/N`/`arg/N` (M1: out-of-scope var / wrong identifier).
3. **KNOWLEDGE_CONFLICTING (KCH)** — references an op/predicate/param/type not in the engine surface. *The "invented API parameter" class constrained decoding does NOT catch.*
4. **SEMANTIC_WRONG** — compiles/typechecks/runs, wrong output (subtract implemented as add).
5. **COLLATERAL_DAMAGE** — an edit changed a previously-correct, out-of-scope definition (edit tasks; takes precedence).

**Statistics:** n ≥ **30** per (modality × prompt-variant × task) (±~14pts Wilson @80%); Wilson 95% CIs for proportions, bootstrap for slopes/ratios. Primary comparison: M2 vs M1 collateral-damage on Rung 1, pooled across variants, one pre-registered two-proportion test. Effect-size threshold: ≥10 absolute points + non-overlapping CIs = "real"; smaller-significant = suggestive. Everything beyond the primary = exploratory (Benjamini-Hochberg on the secondary family).

---

## 8. Task ladder (gated; each rung provokes a specific failure mode)

**Rung 0 — ceiling/warmup:** 0A author `add` from empty (`add(2,3)=5`); 0B additive edit — add `subtract` without touching `add` (`subtract(5,2)=3` AND add unchanged → introduces COLLATERAL_DAMAGE in easiest form).
**Rung 1 — punish text (H1 headline):** 1A multi-callsite param rename (`f` called from N≥6 sites; rename param / change arity → every site must update; targets COLLATERAL_DAMAGE + REFERENCE_ERROR; text must re-emit & risks perturbing untouched code — **center of gravity**). 1B signature change with downstream type impact (blast-radius localization).
**Rung 2 — punish tuples (gated):** 2A deep reference-heavy nesting (conditional + small record, many cross-refs → REFERENCE_ERROR, M2's weakest point). 2B same target from scratch (does wiring-error rate explode with depth?).
**Rung 3 — integration (gated):** small multi-fn module combining Rung-1 refactor over a Rung-2 reference-heavy structure; find the crossover.

---

## 9. Run gates (hard stops)

- **G0 — engine-exact precondition (blocks everything):** grounding agent delivers (a) engine-exact tuple vocabulary, (b) verified M2 examples that compile through the engine, (c) M1 reference programs. Round-trip every example through the gate before generation.
- **G1 — Rung 0 first:** run 0A/0B fully (both arms, all variants, full n) + report before any Rung 1 generation.
- **G2 — Rung 1 before Rung 2/3:** no Rung 2–3 until Rung 1 has real numbers + CIs. If Rung 1 shows no M2 edit-integrity advantage, that's a finding — stop and report, don't climb hoping for a win.
- **G3 — matched-loop check:** before scoring, confirm both arms run the identical retry loop AND produce comparably rich error reports. If not, C1 uncontrolled — fix or annotate.

---

## 10. Output artifacts

1. Primary results table (modality × variant × task × example-count → all §7 metrics + CIs; machine-readable + rendered).
2. Slope plots (correctness vs example-count per arm×task, CIs).
3. Cost table (tokens/attempt, tokens/success, wall-time, unit-count, per-unit-error, both arms).
4. Qualitative hallucination log (every failure: task, arm, primary+secondary buckets, offending excerpt, engine error).
5. Threats-to-validity register (the §1 table, filled with controlled-vs-annotated).
6. Pre-registration record (frozen prompt hashes, primary comparison, n, effect-size threshold, taxonomy — committed before first scored generation).

---

## 11. What a clean result looks like

- **H1 support:** Rung 1 collateral-damage + reference-error materially lower for M2 (≥10pts, non-overlapping CIs), matched loop, stated token-cost ratio.
- **H2 support:** M2 correctness-vs-examples slope steep+positive while M1 flat (saturated), even if M2 trails absolute at count=3.
- **Honest null/boundary:** Rung 2 M2 reference-error climbs with depth until it crosses M1 — that's the *boundary of the idea*, exactly what you want to know.
- **Void (don't ship):** M2 "wins" but loops unmatched (C1), or M1 got a naive prompt (C2), or only absolute correctness reported with no cost/slope axis (C3). → rerun.

*Harness one-liner:* one control-flow loop, parametrize only the parse/apply step by arm, gate fail-closed identically, sweep example-count, log every failure into the §7 buckets, never run Rung 2+ before Rung 0–1 has CIs.
