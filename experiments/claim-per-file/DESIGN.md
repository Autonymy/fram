# Claim-per-file store — the theoretically-sound design

**Status:** design brief for the principled store (`cpf2.clj`), built on the spike
(`cpf.clj`, `WRITEUP.md`). Where the spike used raw UUIDv7 and deferred the hard
parts, this fixes each at the root. Built by @substrate-architect (2026-06-22) on
Tom's directive: *"build the primitive in the best theoretical way, period."*

The spike proved the shape (4 behaviors green, fold-equivalent to the log at
900/900, 2236/2236, 2476/2476). Its own WRITEUP named six costs/hazards. This
design resolves the two that are **correctness** (not engineering): cross-writer
ordering (HLC, hazard #5) and cardinality merge-precedence (hazard #4), and
specifies the engineering path for the rest (packing, GC, subject-index).

---

## 0. The thesis in one line

On an append-only claim graph **writes never conflict**; the *only* forced
convergence is **identity allocation** (`VIEWS_AND_BRANCHES.md` §4). So the store is
a **coordinator-free CRDT**: a grow-only set of immutable claim-objects, each named
by a totally-ordered id, merged by set-union, read by a deterministic fold. Every
"conflict" other systems resolve at write time is, here, a **read-time selection
obligation cast by a cardinality axiom** (`VIEWS_AND_BRANCHES.md` §3). The store
must therefore make that read-time fold *deterministic and arrival-order-independent*
— which is exactly what the two correctness fixes below guarantee.

---

## 1. Ordering = Hybrid Logical Clock (HLC), baked into the id

### Why raw wall-ms is wrong (the spike's genuine open problem)

UUIDv7's 48-bit ms prefix orders by **each writer's wall clock**. Across federated
writers with clock skew, last-write-wins picks the writer with the *faster clock*,
not the *causally-later* write. `rt.clj:129` already records the engine's reason the
log uses `:tx` not wall-time: *"wall-clock is non-monotonic under NTP/skew."* The
CRDT must restore a trustworthy total order **without** a central `:tx` counter. That
is precisely what an HLC delivers: a clock that is *time-sortable* (stays close to
physical time, so ids remain roughly chronological and tail-by-id keeps working) yet
*monotonic across the federation* (respects causality given that replicas exchange
claims, i.e. merge), and **never goes backwards** even when the wall clock does.

### The HLC tuple and its three rules (Kulkarni et al. 2014, adapted)

An HLC timestamp is a triple `[pt, lc, node]`:

| field | bits | meaning |
|-------|------|---------|
| `pt`  | 48   | physical time — the max wall-ms this node has *observed* (own clock or any merged-in claim). Monotonic non-decreasing. |
| `lc`  | 16   | logical counter — disambiguates events within the same `pt` (same ms, or a stalled/regressed wall clock). |
| `node`| 48   | stable node-id (random-per-process, persisted) — the **final, deterministic tiebreak** that makes the order *total* across writers who legitimately share `[pt, lc]`. |

The clock has exactly three transitions. `wall()` = `System/currentTimeMillis`.

**(a) On a local write (`send`/local event):**
```
pt'  = max(pt, wall())
lc'  = (pt' == pt) ? lc + 1 : 0
emit [pt', lc', node]
```

**(b) On observing a remote HLC `[m_pt, m_lc, _]` (`receive` — happens at MERGE/LOAD,
not at write, because a CRDT has no write-time message):**
```
pt'  = max(pt, m_pt, wall())
lc'  = pt'==pt && pt'==m_pt ? max(lc, m_lc) + 1
       : pt'==pt           ? lc + 1
       : pt'==m_pt         ? m_lc + 1
       :                     0
```

**(c) Total order** over two HLC ids: compare `pt`, then `lc`, then `node`. `node`
guarantees **antisymmetry** — two distinct writers can never mint equal ids, so the
order is a true total order and the fold is deterministic. (UUIDv7's random `rand_b`
*could* collide structurally; a stable `node` cannot, and it is *meaningful* —
two ids ordered only by `node` are genuinely concurrent, and the order is an
arbitrary-but-stable choice, which is all a CRDT needs.)

### Encoding into the filename / id (still time-sortable)

`pt(48) | lc(16) | node(48)` → 112 bits, rendered as a fixed-width lowercase-hex
string `"{pt:012x}-{lc:04x}-{node:012x}.edn"`. Big-endian field order ⇒ **lexical
sort == HLC total order** (the spike's "filename IS the position" property is
preserved, now causally sound). Tail-by-id ("claims with id > cursor") stays a string
comparison. The id is *self-describing*: `pt` is a real-ish wall time for humans,
`node` identifies the origin replica for provenance.

### Persistence & merge of the clock

- Each replica persists its HLC state (`pt`, `lc`) in `<store>/.hlc` so a restart
  cannot regress below ids it already minted.
- **Loading a store advances the clock past every id it reads** (rule (b) applied to
  the max id seen). This is the no-message receive: after `load-store`, the next
  local write is guaranteed `>` everything merged in. Merge → load → write therefore
  threads causality through the file union with no protocol on the wire.

> Theory note (non-obvious choice): putting `node` *after* `lc` in the sort key — not
> before — is what keeps the id chronological *and* total. Sorting `node` first would
> partition by writer (a Lamport-per-node vector), losing the global time order that
> makes tail-by-id and human-readable ordering work. `node` is the **last** key: it
> only breaks ties physics left genuinely concurrent.

---

## 2. Store = grow-only G-Set of immutable claim-objects

Each object is an immutable EDN map:
```clojure
{:id   "{hlc}"            ; HLC id, §1 — the object's name and sort position
 :l :p :r                 ; the triple (subject predicate object)
 :op  :assert | :retract  ; (commit objects are :op :commit, §3)
 :supersedes [id ...]     ; causal edges — kill targets regardless of fold order
 :retracts   [id ...]
 :by   "<actor>"          ; provenance
 :hash "<sha256>"}        ; content hash of the body sans :id (dedup/integrity)
```

**CRDT structure.** The store is a **G-Set** (grow-only set) of these objects; the
graph state is `fold(sort-by-HLC(gate-commits(set)))`. The merge operator is **set
union** (keyed by `:id`; equal id ⇒ identical object ⇒ idempotent). This is a CvRDT:

- **Idempotent:** `S ∪ S = S` (same id ⇒ same file ⇒ no-op). ✓
- **Commutative:** `A ∪ B = B ∪ A` — union ignores arrival order. ✓
- **Associative:** `(A ∪ B) ∪ C = A ∪ (B ∪ C)`. ✓
- **Convergent:** the fold is a *pure deterministic function of the set* (HLC total
  order is total and arrival-order-independent), so any two replicas with the same set
  compute the same graph. ✓

These four are the principled obligation; §6 makes each a property test (not a demo).

> Why a G-Set and not an OR-Set / 2P-Set: retraction here is **not** element removal
> from the CRDT set — the retract is *itself an immutable element* (`:op :retract` /
> `:retracts` edge). The set only ever grows; "deletion" is a fold-time interpretation
> of added retract-objects. This keeps the join a plain union (the simplest possible
> CvRDT) and makes retract fully commutative with the assert it kills, in any order.

---

## 3. Atomic multi-claim tx = commit-claim (git dangling-object model)

A transaction over N claims is a **commit-claim** `{:id :op :commit :members [id…]
:by}`. Members are written `:pending true`; a pending member is **invisible** until
*some* commit-claim lists its id. The commit's own atomic rename is the **single
linearization point** — all members appear together or not at all.

- **All-or-nothing on crash:** a writer that dies before the commit lands leaves
  staged members as inert GC-able garbage, never partially visible.
- **Merge-safe both directions:** members-without-commit → stay pending → invisible;
  commit-without-members → references absent ids → contributes nothing. Transactions
  compose with federation with **zero extra protocol**.
- **HLC interaction:** a member carries its own birth-HLC (its real causal position);
  the commit carries a *later* HLC. The fold orders **members by their own ids**, not
  the commit's — so a tx's internal claims keep their true relative order while the
  commit only gates *visibility*. (Non-obvious: visibility and ordering are separate
  axes; conflating them — ordering members at the commit's time — would corrupt
  causality between a tx's members and concurrent non-tx writes.)

---

## 4. Schema / cardinality merge-precedence (the second correctness fix)

### The hazard, restated precisely

Cardinality is itself a claim: `(P "cardinality" "single")`. The spike's hazard #4:
a **late-arriving** schema claim from a merge could *retroactively re-collapse* a
predicate's history from multi to single — i.e. the meaning of past data claims would
depend on the arrival time of a schema claim. That violates convergence (two replicas
that merged the same set in different orders could disagree mid-stream).

### The principled resolution (grounded in VIEWS_AND_BRANCHES §3)

The framing fix: **cardinality is not a write-time conflict; it is a read-time
selection obligation** (§3 of the theory doc: "conflict is the shadow of a cardinality
axiom… an obligation of the reader, deferred to use-time"). So the rule is about
making the **fold deterministic and arrival-order-independent**, not about pinning a
timestamp. Three sub-rules:

1. **Cardinality of a predicate is single-valued *on `(P, "cardinality")`* and
   resolved by HLC-LWW.** Among all live `(P "cardinality" v)` schema claims, the one
   with the **maximum HLC id wins**. Because the HLC order is total and a pure function
   of the set, every replica selects the *same* axiom regardless of merge order.
   (Schema claims obey the very cardinality machinery they configure: `cardinality`
   itself is a `single` predicate — a fixed bootstrap axiom.)

2. **The selected cardinality is applied uniformly to the predicate's *entire*
   history — there is no "before/after the schema claim" time-slicing.** Cardinality
   is a property *of the predicate*, not of a time interval. So whether the schema
   claim arrived first or last, the final fold is identical: `single` ⇒ the
   max-HLC data claim for each `[l p]` wins; `multi` ⇒ all members accumulate. This is
   what kills the "retroactive re-collapse changes the *outcome*" hazard: the outcome
   was *always* going to be the max-HLC value once `single` is selected; arrival order
   of the schema claim changes *nothing* about the converged state. Convergence holds.

3. **Narrowing (multi→single) is monotone and total-order-safe; widening
   (single→multi) likewise resolves by HLC-LWW on the axiom.** There is no
   intermediate state that depends on partial knowledge: the fold reads the *whole*
   set, picks the winning axiom, then folds data once under it. A replica mid-merge
   that has only some files computes a *prefix-consistent* state (correct for the set
   it has), and converges to the same final state once it has the full set — the
   definition of strong eventual consistency.

> Two-pass fold (the implementation of the rule): **pass 1** scans the set for the
> winning cardinality axiom per predicate (HLC-LWW); **pass 2** folds data claims
> under those axioms. Both passes are pure functions of the set ⇒ deterministic ⇒
> convergent. This is strictly more correct than the spike's single-pass `single?`
> set (which read cardinality from *whatever order it happened to fold*, and treated
> conflicting axioms as "any one present").

> Non-obvious choice the theory forced: the temptation is to **pin** schema claims
> "early" (give them artificially-small ids so they always sort first). That is
> wrong — it fabricates causality and breaks the HLC's meaning. The correct fix is
> not to reorder schema but to make the fold *not care* about schema order, which the
> two-pass + uniform-application rule achieves. Order-independence by construction
> beats order-manipulation by hack.

---

## 5. Storage tiering = git's object model (the engineering path)

Three tiers, mirroring git loose-objects + packfiles + the warm index:

1. **Warm in-memory index (hot read path).** fram's existing derived graph stays the
   interactive read path. Per-file layout is **never** on the critical read path; it
   is the *durable + syncable + federation* format. (Resolves spike costs #1 load-O(N)
   and #6 subject-scoped-reads for the interactive case.)

2. **Loose objects (hot/recent writes).** New claims land as individual `{id}.edn`
   files — coordinator-free, atomically renamed, immediately mergeable. This is the
   write tier and the small-delta sync tier.

3. **Packfiles (cold objects).** A **pack** operation coalesces a contiguous
   HLC-range of cold loose objects into one read-optimized segment: `pack-{lo}-{hi}.edn`
   (a sorted vector of claim-objects) + a tiny index. Cold load reads *one* file per
   pack instead of N opens (resolves cost #1 at scale and #2 inode pressure). Loose
   objects newer than the pack frontier stay per-file. **Equivalence invariant:**
   `fold(loose ∪ unpack(packs)) == fold(all-loose)` — packing is a pure
   re-encoding, byte-for-byte fold-identical (a property test, §6).

4. **GC / compaction (discretionary, per VIEWS_AND_BRANCHES §5).** Convergence does
   *not* require it; it is hygiene. A reachability sweep from live state drops
   superseded/retracted/abandoned-pending objects into the unreachable set, packed
   away or pruned. Grow-only is correct-but-unbounded; GC is the git-gc analogue, run
   on demand, never on the correctness path.

---

## 6. The principled test suite (properties, not just a demo)

The spike has 4 *example* behaviors. The principled store adds **property tests** that
witness the CRDT obligations and the two correctness fixes:

- **P1 HLC monotonicity:** a single replica's successive ids strictly increase even
  under a *regressing* wall clock (inject `wall` going backwards).
- **P2 HLC causality across merge:** after `load(merge(A,B))`, a fresh local write's
  id `>` every id in `A ∪ B` (the no-message receive advanced the clock).
- **P3 merge commutativity/associativity/idempotence:** for random claim sets,
  `fold(A∪B) == fold(B∪A)`, `fold((A∪B)∪C) == fold(A∪(B∪C))`, `fold(A∪A)==fold(A)`.
- **P4 convergence under shuffle:** fold is invariant to file *arrival* order (shuffle
  the directory listing N times ⇒ identical state).
- **P5 cardinality order-independence:** inject the `(P "cardinality" "single")`
  schema claim **first** vs **last** in arrival order ⇒ identical converged state
  (the §4 fix; the spike's named hazard, now a passing test).
- **P6 commit atomicity under partial arrival:** members-without-commit and
  commit-without-members both yield empty visible state; full set yields all members.
- **P7 pack fold-equivalence:** `fold(packed) == fold(loose)` (the §5 invariant).
- **R8 fold-equivalence regression (the cross-substrate gate):** the same claim
  sequence applied to the append-log fold and the cpf2 fold yields **identical
  state** — the spike's 900/900-style equivalence, kept as the migration gate.

---

## 7. Migration — dual-write → pack → pin → promote → HLC-live

Behavior-preserving, never breaks the running `:7977` system. The lease primitive
(`cnf_lease_test.clj` 10/10 on this branch) is the correctness floor — it closes the
lost-update-≠-mutex hole that any transition must respect.

| phase | move | gate |
|-------|------|------|
| **0. dual-write** | every coordinator commit ALSO writes a claim-object, mirroring the log append (the named integration point: `cnf_coord/commit!` → `append-tx!` → after `rt/append-records-fsync!` succeeds, call `cpf2/put-claim` into a shadow store). | R8: the two substrates fold to identical state. |
| **1. pack** | add the loose→pack coalescer + cold-load path over the shadow store. | P7: pack fold-equivalence. |
| **2. pin schema** | ensure cardinality axioms are written as claim-objects and resolved by §4 rule in the shadow fold. | P5: order-independence. |
| **3. promote** | make per-file the canonical durable format; the coordinator becomes *optional* (single-node fast-write convenience, not a correctness requirement). | R8 still green on canonical. |
| **4. HLC live** | enable cross-machine: replicas exchange loose objects, HLC threads causality through merges; multi-writer with no coordinator. | P2 across real replicas. |

**Dual-write integration point (named in code):** the coordinator is now authored in
Beagle (`src/fram/cnf_coord.bclj` → `out/fram/cnf_coord.clj`, ns `fram.cnf-coord`).
`commit!` (line 119) persists via the private `append-tx!` (line 50), whose sole
durable write is `rt/append-records-fsync!`. Phase 0 adds, **immediately after that
fsync'd append returns**, a mirrored `cpf2/put-claim` (per record) into
`<log-dir>/.cpf-shadow/`, and a CI check folds both substrates and asserts equality
(R8). This is the smallest reversible first wire-in: it touches **only** `append-tx!`'s
tail, never the read path, and a single feature flag disables the mirror.

---

## 8. What the theory forced (the non-obvious choices, collected)

1. **`node` is the *last* sort key, not the first** — total order that stays
   chronological (§1). A node-first sort is a per-writer Lamport vector and loses
   global time order.
2. **Cardinality fixed by order-independent fold, never by pinning schema ids** (§4) —
   fabricating causality to force schema-first is the wrong fix; making the fold not
   care is the right one.
3. **G-Set, not OR-Set** — retraction is an *added immutable object*, so the join
   stays plain union and retract commutes with its assert in any order (§2).
4. **Visibility and ordering are orthogonal axes in a tx** — members order by their
   own HLC, the commit only gates visibility (§3); conflating them corrupts causality.
5. **The receive-rule fires at merge/load, not at write** — a CRDT has no write-time
   message, so the clock must advance when reading a peer's files (§1). This is what
   makes "merge then write" causally sound with zero wire protocol.
