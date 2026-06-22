# G0 FREEZE — Authoring Bake-Off prompt set (engine-exact)

**Status: FROZEN, with ONE harness precondition (D3 — see below).** Every example
in the six prompts is engine-exact (M2 tuples dumped from the real fram engine; M1
text compiled by `beagle build`) and round-trips through the engine gate. The
PROTOCOL §9 G0 precondition (no fantasy interface, C4) is satisfied: the placeholder
vocabulary in PROMPTSET_v1 (`param/N`, `type`, `ret`, `refers_to`-authored,
`if/cond/then/else`, `record/field`, `access/of/field`, `callee`) was **all
invented** and has been replaced with the real uniform `kind`/`v`/`fN` shape.

Engine pin: beagle `~/code/beagle` @ working tree (parse→check→emit); fram
`~/code/fram` branch `authoring-bakeoff-phase1`, vocabulary commit `b2dc257`
(`prompts/claim-algebra-authoring.md`). Toolchain: `bin/fram-code-author` →
`bin/fram-edit-code :edit-min` (warm authoring verbs) + `claims-roundtrip.rkt
--emit-edn/--render` + `beagle-build-all --build-edn`. Scratch daemon ran on **port
7993** over an isolated `.../scratch/g0/.fram/author-devbank-code.log` (NEVER
7977–7989).

---

## The NINE frozen prompts (SHA-256) — RE-FROZEN at harness build (M1.5 + R3 M2)

Supersedes the original six. Two changes folded in at harness time (both mandated by
the master spec, both validated against the engine):
1. **M1.5 added** (R2): text front-end, def-level apply — contract "emit ONLY the
   definitions you add or change"; same TEXT example bank as M1 (the bank's `:m1`
   values are already minimal changed-def sets, so M1.5's example answers = M1's).
2. **M2 re-rendered to R3 flat EDN triples with KEYWORD predicates**
   (`["@m#1" :kind "list"] ["@m#1" :f0 "@m#2"]`), engine-faithful to list/fN/leaf,
   replacing the bespoke string-pred `[@id "attr" val]` form. The harness adapter
   (`harness/m2_adapter.bb`) maps these → the engine integer-node `--emit-edn` dump →
   `beagle-build-all --build-edn` (the recompile gate). Every from-scratch M2 bank
   example (E1/E2/E4/E5/E7) verified to round-trip through the adapter and build
   `0 error` in the new format.

| file | arm | density | examples | sha256 |
|------|-----|---------|----------|--------|
| `prompts/M1-d3.md`    | M1 text          | d3  | E1–E3  | `d8c171481d6007d907020baa5be594bf910b13b95be4171e5bbef510b1ef1462` |
| `prompts/M1-d6.md`    | M1 text          | d6  | E1–E6  | `3f47d5ed36a406cc0cc24ab3890f53228e01fad9977aa90a5758192398debd97` |
| `prompts/M1-d10.md`   | M1 text          | d10 | E1–E10 | `90998fe57cae8cf0add39f27fd030d463c824514786865eb92aab49d9415a395` |
| `prompts/M1.5-d3.md`  | M1.5 text/def    | d3  | E1–E3  | `83f03bd254c9690cf90b83ac4f75fe91b17d3e3cafc4268b245e3c12a94df259` |
| `prompts/M1.5-d6.md`  | M1.5 text/def    | d6  | E1–E6  | `99b314f6154fda613c0ca54f7b54fa1bc6bfc6252c2db8d5ef03ce75cb8d6ae1` |
| `prompts/M1.5-d10.md` | M1.5 text/def    | d10 | E1–E10 | `50d5e9f969c361524a5b3b51383dd1d629502da2753699dd80ce4a4bfa2908b9` |
| `prompts/M2-d3.md`    | M2 claim-EDN     | d3  | E1–E3  | `dde39a4dc05d2fcde18a29e87419508dc3e544991f8360ef992efff8092d1605` |
| `prompts/M2-d6.md`    | M2 claim-EDN     | d6  | E1–E6  | `4e08507b89409678381828db8cfd8576825edd40cf712f802532c50b7a25a047` |
| `prompts/M2-d10.md`   | M2 claim-EDN     | d10 | E1–E10 | `2eb7a116a220e1534ecb986329e54fe40fd3fe82abdaebb7360c60bba53558cd` |

`‹DSL-NAME›` filled to **beagle**. `‹R›` and `‹TOKEN_BUDGET›` left as named
placeholders (the harness fills them IDENTICALLY across all nine — they are not
example content, so hashing them now would couple the freeze to a harness knob).
Regenerate with `harness/gen-prompts.bb` (deterministic from `prompts/bank.edn`).

**Fairness invariants verified:**
- Shared instruction block byte-identical across all NINE (md5 `7948ea07…`); the ONLY
  per-arm difference is the output-contract paragraph (§3). ✔
- Same bank, same slice boundaries all arms (d3=E1–E3, d6=E1–E6, d10=E1–E10); each
  example is the SAME program in three representations (whole-text / changed-def-text /
  claim-EDN). ✔

### `:head` shorthand — judgment call (logged)
The master-spec R3 directive illustrated the form as `["@m#1" :head "defn"] …`. The
binding constraint there is "engine-faithful to list/fN/leaf", and the engine has NO
`:head` predicate (the head is `:f0` → a symbol leaf). So the examples use the
explicit engine-faithful `:f0`→symbol-leaf form (consistency between examples and
contract is load-bearing — the model imitates the examples). The adapter ALSO accepts
a `:head "op"` convenience tuple (desugared to `:f0`+leaf) so a model that abbreviates
still round-trips, but no example teaches it.

---

## The example bank — engine status (all 10 verified)

| E | form | failure mode targeted | M1 build | M2 round-trip | status |
|---|------|----------------------|----------|---------------|--------|
| E1 | `square` (defn, `*`) | basic leaf+list | 0 err | `--build-edn` ≡ M1 clj | exact |
| E2 | `inc` (`+ x 1`, literal) | number leaf | 0 err | `--build-edn` ≡ M1 | exact |
| E3 | add `dec` to `inc` | additive edit (no collateral) | 0 err | `--build-edn` (inc+dec) | exact |
| E4 | `max2` (`if`) | **conditional** | 0 err | `--build-edn` | exact |
| E5 | `Point` (`defrecord`) | **record** | 0 err | `--build-edn` | exact |
| E6 | rename `inc`→`bump` | ref-follows-identity (rename payoff) | 0 err | warm `rename` verb, 2 ops, ENGINE-DUMPED | **substituted** (see below) |
| E7 | `sum3` (`(+ (+ a b) c)`) | reference/nesting depth | 0 err | `--build-edn` | exact |
| E8 | set-body of `negate` | surgical localized edit | 0 err | warm `set-body` verb, 12 ops, ENGINE-DUMPED | **substituted** (see below) |
| E9 | `double-inc` calls `inc` | **cross-def call** | 0 err | `--build-edn` (inc+double-inc) | exact |
| E10 | add `origin?` over `Point` | additive + **field-access** | 0 err | `--build-edn` (Point+origin?) | exact |

"M2 round-trip" for from-scratch/additive examples = the renumbered `@m#N` tuple
block was reassembled into the `--emit-edn` dump format and `beagle-build-all
--build-edn` produced **byte-identical Clojure** to the M1 text. For the two
edit-delta examples (E6/E8) the verb was driven through the live warm daemon and
**landed recompile-able** (op-receipts recorded below), and the delta tuples are
dumped from the resulting `code.log`.

### Discovered engine representations (the placeholders were wrong)
- **`if`** (E4): a plain `list` headed by symbol `if` — `f0=if f1=cond f2=then f3=else`. No `if`-kind, no `then/else` predicates.
- **`defrecord`** (E5): a plain `list` headed by symbol `defrecord`; the field vector is the SAME `#%brackets` list as a defn param vector (`field :- Type` slots). No `record`-kind, no `field/N`.
- **cross-def call** (E9): the callee is just a **symbol leaf** (`v="inc"`); resolution binds it via `bound_to`. No `callee` predicate.
- **field-access `(:x p)`** (E10): a plain `list` whose head is the accessor leaf `:x`, encoded **`kind=symbol, v=":x"`** (the colon retained in the spelling, exactly the `:-` rule). No `access/of/field`.
- **literals** (E2/E10): `kind=number, v="1"` / `v="0"`; `kind=string` only for string literals. As T1.

---

## Substitutions (logged) — two examples re-aimed at engine-supported edits

Both preserve the ORIGINAL failure mode; neither invents a representation.

**E6 — param-rename → def-rename.** Original: "rename param `n`→`x` in `negate`".
The engine's `rename` verb (`resolve.clj verb-rename!`) operates on a **top-level
def binding only** — there is **no param-rename verb** (`rename` rejects "no binding
named X" if the target isn't a def). Substituted to **rename the def `inc`→`bump`**,
which `double-inc` references — exercising the IDENTICAL failure mode (reference-
follows-identity, shadow-correct, O(1)). Engine-dumped delta (2 ops):
```
[@m#17 "bound_to" @m#3]   [@m#19 "bound_to" @m#3]   ; durable edges for the 2 uses
RETRACT [@m#3 "v" "inc"]  ASSERT [@m#3 "v" "bump"]   ; one binding-leaf re-spell
```

**E8 — return-type supersede → set-body.** Original: "`inc` returns `Nat`, supersede
`[@id ret Nat]`". This was a FANTASY on two counts: (a) there is **no `ret`/signature
predicate** — a return type is an ordinary `fN` child slot, changeable only by
re-minting the whole form (`upsert-form` replace, ~40 ops) or a body edit; (b)
**`Nat` is not a valid beagle type** (the checker build-rejects it — verified).
Substituted to a **`set-body` edit** (`negate` body `(- 0 n)`→`(* -1 n)`) — the
engine's genuine surgical edit, which leaves name/params/return untouched. Engine-
dumped delta (12 ops): retire the body edge `[@m#17 f5 @m#27]`, mint the new `(* -1
n)` subtree, wire it as `f5`.

---

## Form-support boundary — what the engine does NOT represent as an authored claim edit

The real surface limit found at G0 (a valid, important finding, NOT a blocker):

1. **No surgical signature/return-type edit.** Params, return type, and record
   fields are `fN` SLOTS of a form list, not field-addressable claims. Changing a
   signature means re-minting the form (`upsert-form` replace) — there is no
   "supersede one type claim" verb. (Body is the exception: `set-body` is surgical.)
2. **No param/local rename verb.** `rename` is def-scoped only. Renaming a bound
   local/param requires re-minting the enclosing form.
3. Everything else the bank needed IS representable as authored claims: `def`,
   `defn`, `defrecord`, `if`, nested calls, cross-def calls, keyword field-access,
   `and`/`=`/arithmetic, literals — all the uniform `list`+`fN`+leaf shape, all
   gate-checked. Conditionals and records, flagged "INVENTED" in PROMPTSET, are
   fully supported (just not as bespoke shapes).

---

## D1 — M1 partial-update parity

**Resolved: NO partial update for M1 — whole-module re-emission, applied as
symmetric INTENT to both arms.**

Test against the engine: `beagle build`'s compilation+grading unit is the **file/
module** (a hidden test vector exercises the whole program). A single def can be
*compiled* in isolation, but the modification that the task grades is over the whole
module, so the M1 modality must submit a coherent module — i.e. the changed def(s)
PLUS the unchanged forms reproduced verbatim. This whole-unit re-emission is a
genuine property of text and is exactly the COLLATERAL_DAMAGE exposure Rung 1
measures. M2 emits only the delta (the harness applies it to the existing graph).

The asymmetry is **declared, not hidden** (fairness invariant 4). Same INTENT both
arms: "change only what the task requires." M1 expresses it by reproducing unchanged
forms; M2 by the delta. The M1 contract (`prompts/M1-*.md` §"Output contract")
states this explicitly.

---

## D2 — node-id surfacing for M2 (the state renderer, frozen)

**Resolved: the M2 current-program-state renderer is the AST-only `@<mod>#<int>`
projection — `kind`/`v`/`fN` triples, srcloc and `child`-mirror edges DROPPED.**

- Node ids are the warm-store **`@<mod>#<int>`** names (stable across rename/reorder).
- The renderer emits, per node in module order: `[id "kind" K]`, `[id "v" V]`
  (leaves), and `[id "fN" child]` ordered-child edges — including the wrapper's
  top-level form edges (`fN` for ingested forms, CRDT `f<path>~<tie>` for
  warm-appended forms). It **drops** the per-node srcloc claims (`line`/`col`/`pos`/
  `span`) and the `child` mirror edge — these are bookkeeping the canonical prompt
  already omits "for clarity", and in the warm log srcloc objects are re-keyed to
  node-refs (noise that would mislead the model). The exact projector is in
  `scratch/g0/` (`extract.bb` / `subtree.bb`) and is reproduced from the live
  `code.log` via the assert/retract supersession fold.
- In the prompts, each example's STATE shows the relevant `@id`s inline (e.g. E6
  state names the binding leaf `@m#3` and the two use leaves `@m#17/@m#19`) so the
  model can reference existing nodes. (Per-example ids are renumbered `@m#N` to read
  standalone; the engine SHAPE is exact, the absolute int is per-module and not
  load-bearing.)

---

## D3 — error-report parity (gate G3) — THE HEADLINE FINDING

**Resolved, with a hard harness precondition. Error parity is ACHIEVABLE and
empirically IDENTICAL — but ONLY if the M2 arm is gated through the recompile path,
which the named demo tool is NOT.**

Empirical comparison on the same failure (`defn` returning unknown type `Nat`):

- **M1 (text, `beagle build`):**
  `…:2: beagle: defn badret: expected return Nat, got Int` + `type errors` + `0 built, 1 error(s)`.
- **M2 (claims, GATED):** render the edited graph from the log → `beagle-build-all`
  → identical report:
  `…:20: beagle: defn badret: expected return Nat, got Int` + `type errors` + `0 built, 1 error(s)`.

They are the SAME report (same `<file>:<line>: beagle: defn <name>: <reason>`
schema) because the M2 gate **is** `beagle-build-all` over the rendered corpus —
same compiler, same diagnostic. **So G3/C1 is satisfiable.**

**BUT — the confound, flagged for the harness:** the warm authoring tool the brief
names (`fram-code-author` / `fram-edit-code :edit-min`) does **NOT** run the
recompile/typecheck gate. It enforces only the structural verb checks (name
collision, orphaned-ref) and returns a bare `REJECTED — verb rejected the edit (code
3)` with NO location/kind/message. A type-wrong upsert (`badret` returning `Nat`)
**LANDED unchecked** through `:edit-min` (verified: it committed at v651 and rendered
into the module). The actual recompile-gate (`beagle-build-all '0 error'`, fail-
closed, with the rich diagnostic) lives in a DIFFERENT caller —
`tests/fram_mcp.clj` `route-edit` / `flip-graph-edit!` (renders the whole corpus
from the log, drops in the edit, builds, commits only on `0 error`, else
`REJECTED — edited corpus does not recompile:\n<the beagle-build-all output>`).

**Harness requirement (C1 control):** the M2 arm MUST apply each changeset and then
run the recompile gate explicitly (render-from-log → `beagle-build-all`, i.e. the
`route-edit`/`flip-graph-edit!` loop), NOT the bare `:edit-min` path — otherwise M2
gets a free pass on type/semantic errors that M1's `beagle build` catches, and BOTH
the gate-matching (C1) and the error-richness (G3) are broken. With the gate wired,
the two arms' error reports are identical. This is the single precondition on the
freeze.

---

## Leakage check (PROMPTSET invariant 3) — PASS

No bank example equals or trivially reveals a scored Rung 0–3 task:
- **Rung 0** is `add`/`subtract` (BINARY two-arg). The bank's `inc`/`dec` (E2/E3)
  are **UNARY ±1** — distinct fn name, arity, and op-shape. Confirmed distinct. E1
  `square`, E7 `sum3`, E9 `double-inc` are none of add/subtract.
- **Rung 1** is a multi-callsite PARAM rename of `f` (N≥6 sites) + a downstream-type
  signature change. E6 is a single-caller DEF rename of `inc` (2 refs in one callee)
  — same mechanism, strictly smaller and differently named. E8 is a body swap, not a
  signature change. No leak.
- **Rung 2** is deep reference-heavy nesting (conditional + record + many cross-refs).
  E10 `origin?` is the **nearest neighbor** (record + 2 field-accesses) but is
  shallow (no nested conditional, no multi-fn wiring) and differently named —
  **flagged as the closest, not a leak.**
- **Rung 3** (multi-fn refactor-over-nesting integration) has no bank analog. No leak.

---

## Verdict

**FREEZABLE.** All ten examples are engine-exact and round-trip (8 via `--build-edn`
byte-identical to M1; 2 edit-deltas engine-dumped through the recompile-able warm
gate). D1/D2 resolved and baked into the prompts; leakage clean. **The one gating
precondition is D3:** the harness must route the M2 arm through the recompile gate
(`route-edit`/`flip-graph-edit!`, i.e. render-from-log + `beagle-build-all`), NOT the
bare `fram-edit-code :edit-min` demo path — else C1/G3 are uncontrolled and the run
is void. With that wired, error reports are byte-for-byte the same schema across
arms. Two examples (E6, E8) were substituted to engine-supported edits exercising
the same failure modes (logged); the real surface boundary — no surgical
signature/param edit — is documented above.
