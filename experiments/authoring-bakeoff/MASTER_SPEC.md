# Agent-Authored Code as Claims — Master Spec (v1, canonical)

Supersedes PROTOCOL.md + PROMPTSET_v1.md (kept for history). Status: implementation-ready except G0 (engine-exact surface, Part V). Run gates not optional.

**Thesis (revised, defensible):** the leverage is NOT tuple syntax and NOT "reduce hallucination" broadly (syntactic hallucination is ~solved by constrained decoding; residual is semantic, which representation barely touches). The real, unclaimed leverage is a **content-addressed, fail-closed substrate for agent EDITS** — authorship unit = a small independently-checkable delta over a typed graph, so modifications never collaterally damage untouched code. Position as **Unison-for-agents (a systems substrate)**, with the bake-off as edit-integrity evidence. (R7)

## Part 0 — The seven recommendations
- **R1.** Reframe the IV: not "tuples vs text" but (a) explicit-delta vs implicit-delta × (b) application granularity (whole-unit → per-def → per-claim). Tuples = one operationalization of "explicit, fine."
- **R2.** Add **M1.5** to the FIRST run: text front-end + **definition-level delta application** (Unison's actual model — kept text, got edit-integrity from the backend). Highest info value: if **M1.5 ≈ M2 → scoped application wins, tuple notation unnecessary, skip the front-end**; if **M2 ≫ M1.5 → explicit-delta expression earns its place**. The old design could not discover this.
- **R3.** Don't invent tuple syntax — emit the most **in-distribution serialization** the engine can ingest: **EDN / Datomic-style tx-data** (model has seen it at scale; ~0 exposure to bespoke `[@id attr val]`). Attacks the OOD confound at root. Two layers stay explicit: rendered program (Clojure-subset) vs claim serialization (what the agent emits). **For us: a HARNESS ADAPTER maps EDN→fram claims — no fram-engine change required.**
- **R4.** **SEMANTIC_WRONG is a NEGATIVE CONTROL.** No mechanism by which representation fixes wrong-algorithm errors. If M2 appears to reduce it → confound/leak, not a win. Pre-register no-effect.
- **R5.** **Edit token-cost crossover** as a first-class predicted curve: tuples lose on authoring tokens, win on edit tokens; report where the lines cross (edit-scope ÷ function-size). Backbone of the "safer AND cheaper for edits" claim.
- **R6.** Two fairness gates non-negotiable: **C1** identical verify-and-retry loop on every arm; **C2** equal-effort, frozen, held-out, pre-registered prompts per arm.
- **R7.** Contribution = a systems substrate (Unison-for-agents), evidenced by the bake-off. Differentiate from Berkeley graph-rep paper by **edit focus + matched verification**. Drop "reduce hallucination generally."

## Part I — Two-layer model
1. **Rendered program** — Clojure-subset source w/ `:- Type`. What the code IS, the shared compile target.
2. **Claim serialization** — what the agent emits (text for M1/M1.5; EDN claim-changeset for M2/M3).
All arms target the SAME typed AST → four front-ends to one substrate (what makes the comparison fair + M1.5 possible).

## Part II — Prior art (you're not alone, corner is real)
Unison (1.0 late 2025) = the representation (content-addressed, additive edits, non-breaking rename) — but a HUMAN language w/ text front-end; nobody built it as an AGENT substrate w/ deltas as emission unit = the gap (and Unison's text-front-end choice is the evidence behind R2). Constrained decoding (XGrammar/llguidance, default in vLLM/SGLang 2026) = M3 commoditized — but "Let Me Speak Freely?" shows format restriction can DEGRADE reasoning (M3 may win STRUCTURAL_INVALID, lose SEMANTIC_WRONG — pre-register). Berkeley "A Matter of Representation" (Oct 2025) = representation-moves-accuracy already shown → sharpen our Q to edit-integrity-under-matched-verification. Tarau (Feb 2025) = programs-as-triples lineage. AlphaProof/Lean wave = the fail-closed-gate cousin → (1) the VERIFIER does the work (C1 is cardinal), (2) measured performance tax on formal reps (OOD evidence → R3 + slope). FORGE 2026 = syntactic hallucination ~solved, residual is semantic ("invented parameter is syntactically perfect") → aim at edit + reference integrity, not semantic correctness.

## Part III — Reframed model
IV decomposition: **delta-expression** (explicit | implicit) × **application-granularity** (coarse | medium-def | fine-claim).

| Arm | Delta expr | Granularity | Front-end | OOD |
|-----|-----------|-------------|-----------|-----|
| M1 | implicit (re-emit whole) | coarse (replace unit) | text | none |
| **M1.5** | implicit (re-emit whole) | **medium (def-level diff+apply)** | text | none |
| M2 | **explicit (changeset)** | **fine (per-claim mint/supersede)** | EDN claims | high |
| M3 | explicit | fine | EDN claims, grammar-constrained | high |

Hypothesis it exposes: collateral damage driven by (implicit × coarse). M1.5 isolates "fine application alone, familiar text." **M1.5≈M2 → granularity is the active ingredient, notation unjustified; M2≫M1.5 → explicit expression has value beyond granularity.** Serialization (minimal-OOD vs familiar-verbose) = a SECOND variable; DO NOT vary in v1 — pick EDN, hold fixed.

## Part IV — Protocol
**Hypotheses:** H1 (edit-integrity headline) M2 < M1 on collateral-damage + reference errors during MODIFICATION, matched verify + effort, stated token-cost. **H1′ (decisive, R2)** M2 vs M1.5. H2 (learnability) M2 slope steeper per few-shot than near-saturated M1. H3 (authoring control, ~null) M2≈M1 from-scratch once syntax verifier-gated. **Negative control (R4)** SEMANTIC_WRONG shows no rep effect.

**Confounds → controls:** C1 verifier-attribution → §IV.4 identical loop all arms. C2 prompt-effort → frozen/held-out/pre-registered/equal-effort prompts. C3 OOD/verbosity → R3 EDN + slope + token axes. C4 fantasy-interface → Part V contract + G0.

**Matched loop (C1):** PROMPT → MODEL emits → [PARSE/APPLY: M1 replace-whole-unit | M1.5 parse-text→diff-vs-AST→def-level-delta | M2 parse-EDN→per-claim mint/supersede | M3 = M2 w/ constrained emission] → ENGINE GATE (fail-closed, IDENTICAL: compile+typecheck+run hidden test) → PASS record | FAIL structured error (SAME schema all arms) → retry R=4 ∧ token-ceiling. **M1 errors must be as rich as M2 (G3).**

**Taxonomy (primary bucket by precedence):** COLLATERAL_DAMAGE > KCH > REFERENCE_ERROR > SEMANTIC_WRONG > STRUCTURAL_INVALID. (KCH = uses op/predicate/param/type NOT in the Part V surface — the residual constrained decoding misses. SEMANTIC_WRONG = compiles/runs/wrong-output = negative control.)

**Metrics:** primary = correctness@1/@k, collateral-damage rate (H1), reference-error rate. secondary = rejected-delta/parse, retry-rounds, KCH, SEMANTIC_WRONG(control). learnability = correctness-vs-examples SLOPE+CI. cost = tokens/attempt, tokens/SUCCESS, wall-time, unit-count, per-unit-error AND units-per-task. **crossover (R5)** = edit-token-cost vs edit-scope/fn-size, report crossover point.

**Stats:** n≥30 per cell (±~14pts Wilson @80%); Wilson CIs proportions, bootstrap slopes/ratios; primary = M2-vs-M1 AND M2-vs-M1.5 collateral-damage on Rung1, pre-registered two-proportion; effect ≥10pts + non-overlapping CI = real; BH on secondary. First-run budget (Rungs 0–1, 3 arms × 3 density × 4 tasks × 30 = **1,080 gens** ≤4 retries) tractable; M3+Rungs2–3 scale after.

**Task ladder:** R0 warmup (0A author `square`; 0B additive-edit add `dec` given `inc`). **R1 punish-text headline (1A multi-callsite param rename N≥6 sites — center of gravity; 1B return-type change w/ downstream).** R2 punish-claims gated (2A deep reference-heavy nesting; 2B from scratch). R3 integration gated.

**Gates:** G0 engine-exact (Part V filled+validated, blocks all). G1 Rung0-first full-n reported. G2 Rung1 before 2/3 (no advantage = a finding, stop+report). G3 error-parity. G4 leakage (no bank example = a scored task; note `square` is a TASK not a bank item).

## Part V — Engine-surface contract (G0 unblocker; grounding agent fills; doubles as KCH ground-truth — off-catalog = KCH)
- **V.1 serialization:** pick (b) Datomic tx-data `[{:db/id "add" :node/kind :defn ...}]` OR (c) flat EDN triples `[["add" :node/kind :defn] ...]`. Lean (b)/(c). DECISION: ‹FILL›.
- **V.2 node-kind catalog:** for each kind (defn/param/call/ref/lit/if/record/field/access/+others) → required attrs, optional, ordered edge slots, notes. Every supported kind listed; anything else = KCH.
- **V.3 operator surface:** exhaustive builtin `op` list (arith/cmp/logical/other) + arity + types. The list IS the valid-op definition.
- **V.4 type surface:** primitives, composites, subtyping/coercion (e.g. Nat<:Int?).
- **V.5 edge-predicate catalog:** edge → from-kind, to, cardinality, ordered?.
- **V.6 well-formedness rules the gate enforces** (boundary STRUCTURAL/REFERENCE caught vs SEMANTIC passes-gate).
- **V.7 delta semantics:** mint (fresh @id), supersede (per-attribute? — the edit examples rely on this), form/param/arg reorder+remove, idempotency.
- **V.8 acceptance test:** the `add` example round-trips gate→`(defn add [a :- Int b :- Int] :- Int (+ a b))`, passes add(2,3)=5; every Part VI example likewise. Until then G0 not cleared.

## Part VI — Prompt set (9 prompts: {M1,M1.5,M2}×{d3,d6,d10})
Shared instruction block (identical) + arm output-contract (the only diff: M1 whole-def text; M1.5 changed-defs-only text + harness applies def-level; M2 EDN changeset emit-only-delta). Example bank E1–E10 (dev pool DISJOINT from scored tasks): inc/negate/dec/max2/Point/negate-rename/sum3/inc-rettype/double-inc/origin?. E6/E8 teach the structural-edit payoff (rename=1 superseded claim, rettype=1 claim); E7/E9/E10 teach reference-heavy wiring (M2's weak point) so the bank doesn't over-train M2's strengths. Decision points: D1 (M1 partial-update parity — if yes, M1 vs M1.5 differ ONLY in harness granularity = cleanest R2 isolation), D2 (node-id surfacing to M2), D3 (error parity = G3). Freeze: re-render to chosen serialization, round-trip every example, leakage check, hash 9 prompts, pre-register.

## Part VII — Result types
Strong H1/H1′: R1 collateral+reference rates materially lower M2<M1 (≥10pts, non-overlap CI), stated token-cost, AND M2<M1.5. **Decisive negative (still great): M1.5≈M2 → build the backend + def-level apply, SKIP the tuple front-end** (saves building the unnecessary). Strong H2: M2 slope steep, M1 flat. Honest boundary: R2 M2 reference-error climbs w/ depth until crossing M1 = the idea's limit. Control intact: SEMANTIC_WRONG no arm-effect. Void: unmatched loops (C1) / naive M1 (C2) / no cost+slope axes (C3) / non-engine-exact claims (C4).

## Part VIII — Runbook
1. Pick serialization (EDN b/c), freeze. 2. Grounding agent fills Part V + V.8 acceptance test (clears C4/G0). 3. Re-render bank to serialization, round-trip, G4 leakage. 4. Resolve D1/D2/D3 (G3 parity). 5. Freeze+hash 9 prompts, write pre-registration. 6. Build matched loop (arm-specific only at parse/apply). 7. Run Rung0 all 3 arms full-n, report (G1). 8. Run Rung1, report w/ CIs (G2) — headline incl M2-vs-M1.5. 9. Decision: no advantage → stop+report; advantage → proceed. 10. Scale R2/3 + build M3 (pre-register: may win STRUCTURAL, lose SEMANTIC). 11. Full table + slopes + cost + crossover curve + qualitative log + threats register.

## Appendix — where this might be wrong
M1.5 feasibility depends on semantic def-level text↔AST diff (Unison structural-hash trick) — spike it; **for us: parse emitted def-text → upsert-form (existing verb) is the likely buildable path.** R3 in-distribution is an assumption for OUR model — a tiny 2-serialization pilot (1 task, n=20) de-risks. SEMANTIC_WRONG-as-pure-control may leak via reclassification (unexpressible wrong-ref → REFERENCE_ERROR) — define the boundary. Edit-integrity win may shrink as base models improve — timestamp the result. None of this addresses semantic correctness (specs/tests/retrieval) — this substrate is necessary-infra, not the main correctness lever.

## Our-stack reconciliation (Tom's judgment-call note)
Claude-online doesn't know beagle/fram syntax → its Part V/example tuples are ILLUSTRATIVE; the engine (via the grounding agent) is ground truth (the C4/G0 discipline). EDN serialization (R3) = a harness adapter (EDN→fram claims), NOT a fram change. M1.5 = parse-def-text→upsert-form, buildable on existing verbs. **Judgment: HOLD beagle/fram surface stable during measurement; let the data isolate a specific surface gap (e.g. a missing claim-form, or a real OOD bottleneck) before changing the substrate — don't rebuild for the experiment.**
