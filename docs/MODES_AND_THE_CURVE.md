# Modes and the Curve — the corrected thesis shape for concurrent authoring

**Status:** Corrected record + experiment plan. Supersedes the framing scattered
across the #11 / #11b / propagation receipts. Companion to
[VIEWS_AND_BRANCHES.md](VIEWS_AND_BRANCHES.md) (the view model) and
[WHY_FRAM_EXISTS.md](WHY_FRAM_EXISTS.md) (the atom). This doc is about the
*concurrent-authoring* claim only.

---

## 0. The correction (read this first)

Three **clean stories** were told about Fram-vs-git on concurrent authoring. All
three are wrong, in different directions:

1. *"Git wins validation compute"* (a separate efficiency axis) — the agent's clean story.
2. *"Git wins nothing"* — the overcorrection.
3. *"Fram accepts incoherence; git is safe"* — the lazy trade story.

The right shape:

> **Git is one fixed operating point. Fram is the curve through that point and beyond.**

Git hard-codes a single tradeoff: *validate the joint state of a change set
before exposing it*, with a *coarse* (whole-file/tree) gate, because text has no
smaller unit of meaning. Fram makes that tradeoff **programmable**: it can occupy
git's point (with a *finer, scoped* gate) **and** slide to points text cannot
express (eager visibility, per-claim failure isolation, commuting edits).

**One-line thesis:** *Git gives you one hard-coded tradeoff; Fram makes the
tradeoff programmable.* Not "Fram is always better." The honest claim is
**superset**: git's guarantees included (at a cheaper gate), plus modes git's
substrate forbids.

---

## 1. What git's "win" actually is — collapse R1 + #11b into ONE

Git's lone substrate property is: **validate the JOINT state of a change set
before that state is visible to anyone.** It was logged as *two* git wins. It is
**one win seen twice**:

| Face | Receipt | What it shows |
|---|---|---|
| **Compute** | #11 R1 | one joint validation amortized over K edits vs K individual ones |
| **Coherence** | #11b | rejects the individually-valid-but-jointly-incoherent combination |

- The **compute count** (K vs 1) is a **harness artifact**, not a substrate win.
  Fram can batch-validate *behind* eager landing and erase the pure compute
  delta. Log it as a benchmark *configuration asymmetry*, not a git property.
- The **coherence** property is **real and non-erasable**: to validate-before-expose
  you must hold a barrier — and the barrier is exactly what costs propagation.

So git's column is **one entry**: *joint pre-exposure validation, priced in
barrier latency.* Not two. R1 is its compute face; #11b is its coherence face.

---

## 2. Where Fram beats git on git's OWN turf (and the caveat)

Git's gate is **coarse**: it validates whole files/trees. The claim graph **knows
the dependency structure** (which claim references which), so Fram can gate on the
**affected sub-graph** — validate the binding-set that actually changed, not 184
modules because one line moved. *Same guarantee* (no incoherent state exposed),
*cheaper enforcement.*

> ⚠️ **MEASURED, AND THE FIRST PROXY DID NOT HOLD — this is not a free theorem.**
> The propagation receipt (`cnf_propagation.clj`) measured the closest existing
> proxy — S3.3 *scoped re-resolve* — on the real corpus (K=11 modules). Result:
> **scoped re-resolve did NOT beat whole-corpus.** Per-module it is *flat* at
> ~2.9–3.4 s regardless of which module is dirtied (hub `cnf` ≈ leaf `fold`),
> essentially equal to the COLD whole-corpus walk (~2.9–3.1 s). Cause, visible in
> the code: `materialize-refers-scoped!` still runs `corpus-from-store!` over **all
> K** (a fixed whole-corpus cost) before walking only the affected modules — so at
> this K the fixed scan dominates and scoping the *walk* saves nothing measurable.
>
> This does **not** refute the cheaper-**gate** claim — re-resolve ≠ a coherence
> gate — but it raises a hard flag: **Fram's current scoping machinery carries a
> whole-corpus fixed cost.** For "Fram's gate is cheaper than git's coarse gate" to
> stand, Leg 1's gate must scope the **fixed** cost too (check coherence over the
> affected subgraph *without* re-deriving all binding tables), and be tested at
> larger K. Until then the "better" half of the superset claim is **unproven, with
> a measured headwind**, not assumed.

---

## 3. What Fram can do that git's substrate forbids

- **Per-edit immediate visibility** (propagation / swarm-mode): git can't — the
  barrier is load-bearing for git's coherence.
- **Per-edit failure isolation**: one bad edit doesn't poison a batch.
- **Commuting concurrent edits** (C-mode): same-point structural edits converge
  via deterministic ordering keys instead of conflicting (git) / corrupting
  (Fram-today).

**The structural caveat — say it or get caught:** you cannot have *both* extremes
on the *same* edit. Immediate-visibility and validate-before-expose are
genuinely exclusive for a given commit. So Fram does **not** dominate git at a
single operating point. Fram dominates the **space**: it can sit at git's point
(cheaper gate) or move off it, and *per-edit* it can choose. **Git is a point;
Fram is the curve, and the curve passes through git's point.**

---

## 4. The four modes (the map)

| Mode | Visibility | Validation | Proves | Status |
|---|---|---|---|---|
| **Git** | after merge/gate | joint, whole text/tree | coherent but queued | external baseline (banked: `git_append_baseline.sh`) |
| **Fram git-mode** | after graph gate (staging view → main) | joint, **affected subgraph** | same guarantee, hopefully cheaper | **PRIMITIVE DESIGNED (views), MODE NOT BUILT**; cheaper = **MUST MEASURE** |
| **Fram swarm-mode** | before full validation (eager to main) | per-claim / deferred / scoped | fast visibility, failure isolation, concurrency | **partially measured** (propagation receipt = this point) |
| **Fram C-mode** | deterministic ordering keys | structural commutation | same-point edits converge vs conflict/corrupt | hazard + git-baseline **BANKED**; fix = build-and-confirm demo, **DEFERRED** |

---

## 5. Record correction — what each BANKED receipt actually proves

| Receipt | Old label | Corrected label |
|---|---|---|
| #11 R1 | "git wins validation throughput" | git's fixed point, **compute face**; the K-vs-1 count is a config artifact (Fram can batch). Not a standalone git win. |
| #11 R2/R3 | "Fram wins landing latency / isolation" | Fram **swarm-mode** points — what you buy by *leaving* git's corner. One point on the curve git can't reach, not an abstract "Fram wins." |
| #11b | "git wins coupled coherence" | git's fixed point, **coherence face** — the *same* win as R1, other face; the honest cost of swarm-mode (expose-before-validate). |
| #31 locality (`4c6a0bf`) | "Fram structural-position bug" | the **edit-safety** axis at the same insertion point: Fram-today *silently corrupts* (multi-valued fN, no commit-time conflict detection — LIVE). |
| git baseline (`812fc50`) | — | the **edit-safety** axis from git's side: git *conflicts* at the same point (raw AND merge-queue); auto-merges different points. |

#31 + the baseline together are **C-mode's motivation**: at the same insertion
point git conflicts, Fram-today corrupts, C would converge — carrying the #11b
**decoupling tax** (machine-chosen order; see #32).

---

## 6. The honesty ledger (banked vs plausible vs must-measure)

- **BANKED:** swarm-mode propagation split (raw synchronous / derived lazy-on-read);
  the edit-safety same-point hazard on *both* systems; git's joint-coherence win
  (one, real, priced in latency).
- **PLAUSIBLE, NOT BANKED:** Fram **git-mode**. The substrate has a *view model*
  (VIEWS_AND_BRANCHES.md: main = privileged published view) — staging-view vs
  published-view is the natural primitive for stage→gate→publish. But the
  *daemon mode* that stages a batch, joint-validates it, and publishes to main
  only on pass — **with a real view boundary so speculative claims are NOT visible
  to ordinary readers** — is **not built**. Until it is, "Fram can do git's
  guarantee" is *architectural*, not demonstrated.
- **MEASURED, HEADWIND FOUND:** that Fram's git-mode gate is **cheaper** than git's.
  The first proxy (S3.3 scoped re-resolve) was measured (`cnf_propagation.clj`) and
  did **not** beat whole-corpus at K=11 — flat ~2.9–3.4 s across all modules,
  because `corpus-from-store!` runs over all K every time. So the "cheaper" half of
  the superset claim has a *measured headwind*: Fram's current scoping reduces the
  walk but not the fixed corpus scan. Leg 1 must beat this, not assume past it.

---

## 7. Sequenced experiment plan (one rig: the multi-client daemon harness)

**Leg 2 — swarm-mode point (BANKED): propagation receipt.** `cnf_propagation.clj`,
isolated daemon on a `/tmp` copy of `.fram/code.log` (K=11). *Not "Fram wins" — one
point on the curve, the eager-expose corner git cannot reach.* Results:
- **RAW `:query`: SYNCHRONOUS, ~0.1 ms, 12/12 reflected on the first read** (no
  polling). Eager visibility is real.
- **DERIVED `:callers`/refers_to: LAZY-ON-READ.** First reader after a write pays
  the resolve walk; NO-OP repeats ~0; no background re-resolve.
- **SURPRISE: scoped re-resolve did NOT beat whole-corpus** — flat ~2.9–3.4 s
  across all 11 modules (see §2). The measure-first win of the session: the
  "scoped is cheaper" assumption failed its first proxy test.

**Leg 1 — git-mode (next build; the "better, not just different" proof).** The
unbuilt, unknown-magnitude one:
1. Stage a batch of edits in a non-main view (NOT visible to ordinary readers).
2. Joint-validate the resulting staged graph.
3. Publish to main only if the joint graph passes.
4. Compare against git / merge-queue on the same workload.
5. **Measure validation SCOPE and time: whole project/module (git) vs affected
   subgraph (Fram).** This is the substrate win, and it is a *measurement* — with a
   known headwind: the Leg-2 result shows Fram's current scoping carries a fixed
   whole-corpus cost (`corpus-from-store!`). So Leg 1's gate must scope the **fixed**
   cost too — check coherence over the affected subgraph *without* re-deriving all
   binding tables — and be tested at **larger K**, or the "cheaper" claim fails the
   same way re-resolve did. Requires the view boundary to be real (§6).

**Leg 3 — the curve (synthesis).** Same workload, sweep Fram from git-mode to
swarm-mode; plot git as a single fixed dot on the coherence↔visibility frontier.
The talk slide: *"Here is git. Here is the curve Fram can occupy. Git is one
point on it; Fram chose to leave."*

**Leg 4 — C-mode (deferred demo).** Tiebroken/actor-id ordering key; same-point
edits converge. Sign is known (build-and-confirm); carries the #11b decoupling
tax (machine-chosen order → could be wrong if `fooB` reads `fooA`). Build only as
a *live demo* after Legs 1–3 (#32).

**Do NOT** build any experiment whose purpose is to *deny git the joint-coherence
win.* It is real and conceded; such an experiment would be a strawman against our
own honest result.

---

## 8. Meta — why "I step away and this happens"

The failure mode: agents and advisors generate a **clean story** when the truth
is a messier trade, and the clean story is seductive in whatever direction you're
already leaning. Three clean stories appeared here, all wrong (§0).

The discipline, applied whenever the substrate looks like it **loses OR wins
cleanly** — ask which of three it is, and refuse any framing that collapses them:

1. a **harness artifact** (erasable — like the K-vs-1 compute count), or
2. a **fixed point of git** (real, priced — like joint-coherence), or
3. a **programmable choice for Fram** (a point on the curve — like eager visibility).

"Git wins compute" collapsed (1) into (2). "Git wins nothing" denied (2). "Fram
accepts incoherence" mistook a *choice* (3) for a *constraint*. The correct claim
keeps all three distinct: git is a point; Fram is the curve; the curve passes
through git's point at a cheaper gate.
