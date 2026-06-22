# Prompt Set v1 — M1 (text) vs M2 (tuple), matched density — PRE-G0 DRAFT

**Status: NOT YET FREEZABLE.** Every tuple in the example bank is a `‹PLACEHOLDER›` using illustrative vocabulary, not engine-exact predicates. Do not pre-register or run until §5 G0-swap is done. After swap: round-trip every M2 example through the engine gate, confirm every M1 example compiles, then hash and freeze.

Six prompts = M1 × {3,6,10 examples} and M2 × {3,6,10 examples}, from one shared instruction block + one shared example bank. Density tiers are slices of the same bank (E1–E3 / E1–E6 / E1–E10) — identical content across arms, differing only in representation.

## 1. Fairness invariants (MUST stay matched)
1. Identical instruction block across arms (§2), differing only in the output-contract paragraph (§3).
2. Same example bank, same slice boundaries (E1–E3, E1–E6, E1–E10), same tasks/order both arms. M1 and M2 examples are the *same programs* in two representations.
3. Examples drawn from a DEV POOL disjoint from the scored Rung 0–3 tasks. None of `add`, `subtract`, the multi-callsite refactor, the Rung 2 nesting target, the Rung 3 module may appear here. (Leakage check before freeze.)
4. One inherent asymmetry, declared not hidden: M2's contract says "emit only the delta." Whether M1 may also do partial updates is an engine property — resolve in §4 D1, apply the same answer to both arms.

## 2. Shared instruction block (identical both arms)
> You are authoring code in ‹DSL-NAME›, a Clojure-subset language with explicit type annotations (`:- Type`).
> You will be given: (1) a **task**, (2) the **current program state** (empty, or the existing program).
> Produce output that satisfies the task and nothing more. A fail-closed engine will compile, typecheck, and run your output against a hidden test vector. If it fails, you receive a structured error report (location, kind, message) and may revise. You have ‹R› revision rounds and a ‹TOKEN_BUDGET› ceiling, whichever binds first.
> Rules: output **only** ‹the program / the changeset› — no prose, no fences. Do not invent operations, predicates, parameters, or types not part of ‹DSL-NAME›; if unsure an element exists, do not use it. For edit tasks, preserve the behavior of every definition the task does not ask you to change.

## 3. Output-contract paragraph (the ONE allowed difference)
**M1 (text):** Emit ‹DSL-NAME› source text. Express the whole definition you are adding or changing. ‹PARTIAL-UPDATE-CLAUSE — see §4 D1›
**M2 (tuple):** Emit a flat changeset: one tuple per line `[@id attr value]`. The harness applies each as a claim-delta (mint/supersede) and recompile-gates the batch fail-closed. Emit **only the delta** — never re-emit unchanged definitions. Reference existing nodes by `@id` as shown in current state.

## 4. Decision points to resolve before freeze
- **D1 — M1 partial-update parity.** Does the text workflow support redefining one function without re-emitting the file? YES → add to M1 contract "you may emit only the definitions you add/change; untouched persist." NO → leave out; whole-unit re-emission is a genuine property of text modality (and the COLLATERAL_DAMAGE exposure Rung 1 measures). Either way document the choice.
- **D2 — node-id surfacing for M2.** Confirm how current-program-state is rendered to M2 (must show the `@id`s to reference). The state renderer is part of the prompt; freeze it.
- **D3 — error-report parity (gate G3).** Confirm the structured error report is equally informative for a parsed-text failure as a rejected tuple delta. If the engine gives richer errors to one arm, C1 is uncontrolled — note it.

## 5. Matched example bank (E1–E10) — PLACEHOLDER vocab, swap at G0
Placeholder vocab = `kind/name/ret/param/N/type/call/op/arg/N/refers_to/body/form/N` + invented `if/cond/then/else/record/field/N/access/of/field/callee/lit/value`. ALL placeholder — replace with engine-exact predicates, re-validate. Density: d3=E1–E3, d6=E1–E6, d10=E1–E10.

**E1 — author `square`** (defn, unary, binary op). State: empty.
- M1: `(defn square [x :- Int] :- Int (* x x))`
- M2 ‹PH›: defn node + 1 param + `(* x x)` call with two `refers_to` to the param node + body + form/0.

**E2 — author `inc`** (defn, literal operand). State: empty.
- M1: `(defn inc [x :- Int] :- Int (+ x 1))`
- M2 ‹PH›: defn + param + `(+ x 1)` (one `refers_to` param, one `kind lit / value 1`) + body + form/0.

**E3 — additive edit: given `inc`, add `dec`** (no collateral). State: program with `inc` (`@dev#6`).
- M1 ‹D1›: `(defn dec [x :- Int] :- Int (- x 1))`
- M2 ‹PH›: pure additive delta (new `dec` subtree + form/1), `inc` untouched.

**E4 — author `max2`** (conditional + comparison). State: empty.
- M1: `(defn max2 [a :- Int b :- Int] :- Int (if (> a b) a b))`
- M2 ‹PH›: defn + 2 params + `(> a b)` call + `if`(cond/then/else) node → body. *(if/cond/then/else INVENTED — discover real repr.)*

**E5 — author record `Point`.** State: empty.
- M1: `(defrecord Point [x :- Int y :- Int])`
- M2 ‹PH›: `record` node + 2 `field` nodes + field/0,field/1. *(record/field INVENTED.)*

**E6 — edit: rename a param (single site) — mini Rung-1.** Task: in `negate`, rename param `n`→`x`, update its use. State: `(defn negate [n :- Int] :- Int (- 0 n))`, param node `@dev#29`, use `@dev#32`.
- M1 ‹D1›: `(defn negate [x :- Int] :- Int (- 0 x))`
- M2 ‹PH›: supersede ONLY the param's name claim (`[@dev#29 name "x"]`); the use refers_to the NODE so needs no edit. *(The structural-edit payoff; the contrast Rung 1 measures at scale.)*

**E7 — author `sum3`** (nested binary calls — reference depth). State: empty.
- M1: `(defn sum3 [a :- Int b :- Int c :- Int] :- Int (+ (+ a b) c))`
- M2 ‹PH›: nested call wiring (inner `(+ a b)` as an operand of outer `+`). *(M2's stress point.)*

**E8 — edit: change return type.** Task: `inc` returns `Nat` not `Int`. State: `inc` (`@dev#6`).
- M1 ‹D1›: `(defn inc [x :- Int] :- Nat (+ x 1))`
- M2 ‹PH›: supersede the single ret claim (`[@dev#6 ret Nat]`).

**E9 — author fn calling another fn** (cross-definition reference). Task: `double-inc` applies `inc` twice. State: `inc` (`@dev#6`).
- M1: `(defn double-inc [x :- Int] :- Int (inc (inc x)))`
- M2 ‹PH›: call nodes with `callee @dev#6` (call the inc DEFINITION by node), nested. *(cross-def `callee` — discover real repr.)*

**E10 — additive edit on a record program** (multi-element delta, no collateral). Task: given `Point`, add `origin?` true when both fields are 0. State: `Point` (`@dev#25`, fields `@dev#26/@dev#27`).
- M1 ‹D1›: `(defn origin? [p :- Point] :- Bool (and (= (:x p) 0) (= (:y p) 0)))`
- M2 ‹PH›: field-access (`access/of/field`) + two `=` calls + `and`. *(access INVENTED.)*

*(Full placeholder tuple listings are in Tom's source message / conversation; the G0 swap re-authors each via the real engine and DUMPS the exact claims, so the listings are regenerated, not copied.)*

## 6. The six assembled prompts
Each = §2 block + arm's §3 contract + the example slice (each example as TASK/STATE/ANSWER in that arm).

| ID | Arm | Examples |
|----|-----|----------|
| M1-d3/d6/d10 | text | E1–E3 / E1–E6 / E1–E10 |
| M2-d3/d6/d10 | tuple | E1–E3 / E1–E6 / E1–E10 |

d3/d6/d10 spread powers the H2 learnability slope. Pair across arms when reading: M1-d3 vs M2-d3, etc.

## 7. Freeze checklist (before pre-registration)
- [ ] G0 swap: every PLACEHOLDER tuple replaced with engine-exact predicates from grounding.
- [ ] Every M2 example round-trips through the engine gate (applies + compiles + rendered program matches the M1 example's semantics).
- [ ] Every M1 example compiles.
- [ ] Leakage check: no example equals/reveals any scored Rung 0–3 task.
- [ ] D1 resolved + same answer both arms; D2 state-renderer frozen; D3 error-parity confirmed/annotated.
- [ ] Fill ‹DSL-NAME›=beagle, ‹R›, ‹TOKEN_BUDGET› identically across all six.
- [ ] Hash all six assembled prompts; record in pre-registration alongside primary comparison, n, effect-size threshold, taxonomy.
