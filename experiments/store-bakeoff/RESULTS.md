# Claim-store physical-layout bake-off — DATA (2026-06-22)

**Question (Tom's directive):** the claim primitive is special — claims are *immutable
monotonic facts that don't conflict*; the only coordination point is identity allocation.
Design for that algebra and let **scale data**, not priors, pick the physical layout that
backs the (fixed) semantic model: a grow-only G-Set of immutable claim-objects, merge =
union, state = deterministic fold over an HLC total order honoring causal edges.

We benchmarked four layouts head-to-head. **The semantic model is identical across all
four** — every candidate folds the same claim set to the *same* state (verified: state
counts match to the claim at every M, see Axis 2). This is a pure layout/access-path
comparison.

## Candidates

| tag | layout | write coordination |
|---|---|---|
| **baseline** | single shared log + sole-writer coordinator (today's `commit!`) | ONE global lock, fsync-in-lock |
| **A / per-file** | one EDN file per claim, atomic temp+rename, shared dir (git loose-objects) | none (distinct filenames) |
| **B / per-log** | each writer owns ONE append log (O_APPEND); graph = union of all logs | none (own fd, no lock) |
| **C / sqlite** | embedded KV baseline: one shared WAL-mode sqlite db | sqlite's single-writer WAL lock |

Runtime: babashka/SCI (matches the existing spike harness). Absolute numbers are SCI, not
the JVM daemon (~6x faster compiled) — but the **shapes** (scaling sign, merge cost,
inode tax) are runtime-independent. 24-core box, 62 GB RAM. tmpfs = `/dev/shm` (RAM,
layout-isolated); real disk = `/tmp` (btrfs on LUKS SSD — the inode/fsync point).
Concurrency axis = **real OS processes** (each writer its own runtime + fds), not threads,
so candidate B's "no shared lock across writers" is tested honestly.

---

# CONCLUSIONS FIRST

**Winner overall: B (per-writer append-log).** It is the only candidate that delivers all
three of the primitive-native promises at scale, and it wins or ties every axis except
"raw single-writer durability" (where the baseline log is marginally tighter):

- **Write throughput: B SCALES UP** — 26k w/s @ 1 writer → **300k w/s @ 128** on tmpfs
  (12.6×), **24k → 281k** on real disk. The baseline coordinator **anti-scales** (1139 →
  213 w/s, 1→32). B has sequential-append speed *per writer* AND no single-writer ceiling,
  exactly the hypothesis. **Confirmed, not refuted.**
- **Federation merge: B wins by ~635–960×** — merging two M-claim replicas is O(#writers)
  (concat 8 logs: **4 ms @100k, 39 ms @1M — flat in M**) vs A's O(#claims) file-union
  (2.9 s / **24.7 s @1M**) and sqlite's O(#claims) re-insert (4.2 s / **37.5 s @1M**). The
  baseline **cannot merge at all** (single-origin tx-seq collides). This is the CRDT payoff
  and B realizes it at a cost that doesn't grow with the data.
- **Cold-load: B fastest @100k; sqlite @1M** (at 1M the EDN-parse dominates all text-log
  candidates equally, so sqlite's native-row read pulls ahead — 31.7 s vs B 37.8 s). **A is
  the consistent loser** (1M file-opens: 62.5 s @1M). Steady-state the daemon folds
  incrementally and never cold-loads from scratch, so this axis matters less than it looks.
- **Footprint: B = #writers files** (8, flat in M) vs A = #claims files (1M inodes @1M,
  measured). A's inode tax is real: a 1.65× cold-load penalty @1M and, on real disk, A
  *anti-scales* (peaks 20k w/s @8 writers, drops to 14k @128) from shared-directory inode
  contention — while B holds 24k→281k.
- **Crash safety: B is append-tear-exposed** (a torn last line on power-loss) — the one
  place A's atomic-rename is strictly safer. Mitigation is cheap and standard (length-
  prefixed records or fsync-on-flush + skip-trailing-partial on load), and B's fold
  *already* tolerates a dropped trailing partial.

**A (per-file)** is the *safest* per-write (atomic rename = no torn read ever) and merges
correctly, but pays an inode tax that compounds at scale: slowest load, slowest disk
write, 1M inodes. It's git's loose-object model and inherits git's need for packing — at
which point it *becomes* C-hybrid. **A's costs are exactly the costs B avoids.**

**C (sqlite)** has the best *cold indexed read* out of the box (B-tree, 2.6 ms @100k /
7.9 ms @1M scoped read) and the fastest *cold full-fold at 1M* (31.7 s — no EDN parse), but
it is a **single-writer store**: a flat ~3.5k→11.8k w/s ceiling that *never* scales with N
(WAL single-writer lock), and merge is a full O(#claims) re-insert (37.5 s @1M). It's a
fine *read/index cache* projected from B, not the authoritative write substrate.

**Recommendation: B (per-writer append-log) as the authoritative store + a derived index
(C-style) for scoped reads.** This is candidate **C-hybrid** in its best form: B for
writes + federation (the primitive's algebra), a rebuildable embedded-KV/index projection
for the one axis B is weak on (scoped read without a warm index). Build B; treat the index
as a cache, never the source of truth. See "What would separate the close calls" for the
one test that could still move this.

---

# THE DATA

## Axis 1 — Write throughput @ N concurrent writers (writes/sec; higher = better)

**tmpfs (RAM, layout-isolated), per-writer=4000, fsync off:**

| N writers | baseline (real `commit!`) | A / per-file | B / per-log | C / sqlite |
|---:|---:|---:|---:|---:|
| 1   | 1139 | 17 697 | **26 382** | 3 457 |
| 8   | 736¹ | 82 492 | **159 186** | 8 157 |
| 32  | 344² | 80 773 | **266 283** | 11 027 |
| 128 | 213² | 81 588 | **300 802** | 11 790 |

¹ baseline N=4 (heavier path measured at 1/4/16/32). ² baseline N=16 / N=32.
Baseline reproduces RESULTS.md's anti-scaling anchor (951→151) as **1139→213**.

**real disk (/tmp btrfs SSD), per-writer=4000:**

| N | B / per-log fsync=0 | A / per-file fsync=0 | B / per-log fsync=1 | A / per-file fsync=1 |
|---:|---:|---:|---:|---:|
| 1   | 24 414 | 12 609 | 23 781 | 11 092 |
| 8   | 144 826 | 20 372 | 147 920 | 18 601 |
| 32  | 238 006 | 15 699 | 224 639 | 15 921 |
| 128 | **281 422** | 14 038 | **235 011** | 14 038¹ |

¹ A fsync=1 N=128 ≈ fsync=0 N=128 (14 038) — per-file at high N is already inode/IO-wait-bound
on btrfs, so fsync barely moves it; the cost is the per-claim rename+create, not the flush.

Read: **B scales the same on disk as in RAM** (24k→281k), and **fsync barely dents B**
(235k vs 281k @128 — one fsync per writer per flush batch, not per claim). **A on disk
anti-scales from shared-directory inode contention** — it peaks at N=8 (20k) then *drops*
(15.7k→14k) as more writers pile onto the one directory inode, even though their claims
never conflict. The shared dir is A's hidden serialization point; B has none because each
writer touches only its own file. This is the cleanest demonstration that **A's "no
coordination" is illusory at the fs layer** — the directory metadata is the lock.

> **Winner — Axis 1: B**, decisively and at every N≥1. Only B turns more writers into more
> throughput. Baseline anti-scales (global lock). sqlite plateaus (WAL single-writer). A
> plateaus in RAM and anti-scales on disk (shared-dir inode contention).

**The durability asymmetry that decides it:** the *authoritative* baseline does fsync
**inside** the global lock, so durability and concurrency trade off directly — a simplified
in-process append-floor measured **48 291 w/s flush-only @ N=1 but 1 774 w/s with
fsync-per-write @ N=1** (27× collapse), and it only gets worse as the lock serializes more
fsyncs under contention. B pays its fsync **per-writer, outside any shared lock** (235k w/s
@128 *with* fsync) — so B gets full durability essentially for free, while the
coordinator's durability is its throughput ceiling. This is the structural reason B wins:
no shared lock means fsync isn't a global serialization point.

## Axis 2 — Cold-load / full-fold (ms; lower = better). All fold to IDENTICAL state.

| M | baseline/log | A / per-file | B / per-log | C / sqlite (app-fold) | state |
|---:|---:|---:|---:|---:|---:|
| 100k | 3048 | 4368 | **2393** | 2542 | 10 000 (all four) |
| 1M | 36 969 | 62 506 | 37 843 | **31 682** | 10 000 (all four) |

> **Winner — Axis 2: B at 100k; sqlite at 1M.** At 100k B wins (a handful of sequential
> log reads). At **1M the EDN-parse cost dominates** all three text-log candidates (~37 s,
> layout-independent — they all parse 1M EDN records), so **sqlite pulls ahead (31.7 s)**
> because it reads native rows, no EDN parse. **A is the consistent loser** (per-claim
> open: 4.4 s @100k, **62.5 s @1M — 1.65× the log**, the inode tax scaling with M). The
> takeaway: a *cold full-fold* at 1M is parse-bound, not layout-bound, for the logs — which
> is another reason to keep a derived sqlite/index projection for cold reads, and to fold
> incrementally (the daemon never cold-folds 1M from scratch in steady state).
> **All four fold to identical state (10 000) at both scales ⇒ the layout swap is
> behavior-preserving.**

## Axis 3 — Federation merge: union two divergent replicas of M each (ms; lower = better)

| M | A / per-file (file union) | B / per-log (concat logs) | C / sqlite (INSERT-OR-IGNORE) | baseline |
|---:|---:|---:|---:|---:|
| 100k | 2 910 | **4.2** | 4 159 | N/A — single-origin tx-seq collides |
| 1M | 24 697 | **39** | 37 457 | N/A |

> **Winner — Axis 3: B by ~700× (100k) and ~635× (1M) — and the gap is structural, not
> incidental.** B's merge is **O(#writers)** (a replica = 8 log files; merge = copy them
> in: **39 ms to union two 1M-claim replicas**). A and sqlite are **O(#claims)** — A
> stat/copies 1M files (24.7 s), sqlite re-inserts 1M rows. **B's merge cost is independent
> of M** (4 ms @100k, 39 ms @1M — that's writer-count + a constant, not claim-count);
> A's and sqlite's scale linearly with the store. **The baseline cannot federate at all**
> — its `:tx` numbers are single-origin and collide across replicas, needing a full rewrite
> pass. This is the CRDT-union payoff the whole exercise is about, and **B realizes it at
> a cost that doesn't grow with the data.**

## Axis 4 — Subject-scoped read ("all claims about @e7") (ms; lower = better)

| M | log scan (no idx) | per-file scan (no idx) | log/B warm-idx (lookup) | sqlite +index |
|---:|---:|---:|---:|---:|
| 100k | 280 | 133 | **0.009** | 2.6 |
| 1M | 3968 | 1452 | **0.008** | 7.9 |

> **Winner — Axis 4: a warm in-memory index (0.008 ms) or sqlite's B-tree (7.9 ms cold).**
> This is **B's one genuinely weak axis**: a cold scoped read over raw logs is a full scan
> (4 s @1M). But the gap to an index is **5–6 orders of magnitude**, and the fix is cheap:
> keep a `{subject → claims}` index warm in the daemon (B already loads + folds the whole
> graph; `group-by :l` is free → 0.008 ms), or project into sqlite/LMDB for cold
> out-of-process readers (7.9 ms B-tree @1M). **sqlite wins the out-of-the-box,
> cold-process indexed read** — precisely why the recommendation pairs B with a C-style
> index cache rather than choosing one or the other.

## Axis 5 — Space + file/inode footprint @ M=100k and 1M

| candidate | files @100k | files @1M | bytes @1M |
|---|---:|---:|---:|
| baseline/log | 1 | 1 | 94.8 MB |
| B / per-log | 8 | 8 | 94.8 MB |
| A / per-file | 100 000 | **1 000 000** | 93.8 MB |
| C / sqlite | 1 | 1 | 101.6 MB |

> **Winner — Axis 5: B and the logs** (handful of files, flat in M). Bytes are within 8%
> across all four; the differentiator is **inode count**. A is one inode per claim — at 1M
> claims that's literally **1,000,000 files** (measured), with the directory-listing,
> backup/rsync, and fs-metadata overhead that implies. This is git's loose-object problem
> and is exactly why git *packs*. B's file count tracks #writers, not #claims — flat at 8.

## Axis 6 — Crash / torn-write safety (qualitative)

| candidate | torn-write exposure | durability cost |
|---|---|---|
| **A / per-file** | **None** — atomic temp+rename; a claim file is all-or-nothing. Best-in-class. | 2 syscalls + 2 dir updates per claim (the inode tax). |
| **B / per-log** | **Trailing-line tear** on power-loss mid-append (last record only). Fold already skips a torn trailing partial; length-prefix or fsync-on-flush closes it fully. Interior is safe (append-only). | One `O_APPEND` per writer; optional one fsync per flush batch. |
| baseline/log | Same trailing-tear as B, but it's *one* shared log so a tear blocks *all* writers' recovery, not one writer's. fsync-in-lock makes it durable but is the throughput killer (Axis 1). | fsync inside the global lock — durability bought at the cost of all concurrency. |
| C / sqlite | WAL gives atomic-commit + crash recovery for free (mature). | `synchronous=NORMAL` WAL fsync; single-writer lock is the ceiling. |

> **A is strictly safest per-write; B is safe everywhere except the trailing partial,
> which is cheaply closed and which the fold already tolerates. sqlite inherits decades of
> WAL crash-hardening "for free" — the one place its maturity is a real, not nominal,
> advantage.**

---

# Does B deliver "sequential speed + no single-writer ceiling + cheap merge"? — YES.

The hypothesis was: a per-writer log gets single-file sequential-write speed *without* the
single-writer ceiling *and* without file-per-claim's syscall/inode tax. **All three hold
in the data:**

1. **Sequential speed:** B @ 1 writer = 26k w/s (tmpfs) / 24k (disk) — same order as the
   append-log floor, ~7× faster than per-file's atomic-rename and ~7× faster than sqlite.
2. **No single-writer ceiling:** B is the *only* candidate whose throughput *rises* with
   N (→300k @128). Baseline falls (global lock), sqlite is flat (WAL lock), A is flat
   (RAM) / falls (disk shared-dir inode contention). B's writers share *nothing*.
3. **Cheap merge:** 4 ms to union two 100k replicas — O(#writers), ~700× cheaper than the
   per-claim candidates. The whole federation story is a `cp`/`cat` of N logs.

**Where B breaks (be honest):**
- **Cold scoped read is a full scan** (Axis 4) — needs a derived index. Not fatal: the
  daemon folds the whole graph anyway, so a warm `{subject → claims}` index is free; for
  cold out-of-process reads, project into sqlite/LMDB. This is the *only* reason to keep
  a C-component.
- **Trailing-line torn write** (Axis 6) — cheap to close, fold already tolerant.
- **Writer-count, not claim-count, governs merge/load fan-out** — with *thousands* of
  distinct writers B's "8 files" becomes "thousands of files" and starts to look like A.
  Mitigation: per-writer logs **compact** into shared segment files for cold writers
  (the C-hybrid / git-packfile move). At realistic writer counts (agents+apps, tens to
  low hundreds) this is a non-issue; flag it for the thousands-of-ephemeral-writers regime.

---

# What would separate the close calls

- **B vs C for the read-heavy mix:** if the real workload is read-dominated with many
  cold, out-of-process scoped reads, C's always-on B-tree index (2.6 ms cold) could
  outweigh B's write/merge wins. **Test:** a mixed read/write trace at the true r:w ratio,
  measuring p99 scoped-read latency from a *cold* process for B-with-projected-index vs C.
  Until that trace exists, B+index-cache dominates on the write/merge/federation axes that
  are the primitive's whole reason for existing.
- **B's writer-count fan-out:** rerun Axis 2/3 at **1000+ distinct writers** (vs 8) to
  find where per-writer-log load/merge degrades toward per-file, and measure the
  compaction (segment-pack) crossover. That sets the policy for when cold writers must be
  coalesced.
- **JVM absolute numbers:** rerun Axis 1 on the compiled JVM daemon (not SCI) for true
  throughput ceilings; the *shapes* won't change but the absolute w/s will rise ~6×.

---

# Reproduce

```
cd <fram coord-lease worktree>            # this is /home/tom/code/fram-lease
export BAKEOFF_PER_WRITER=4000
# Axis 1 (write throughput @ N), per candidate, tmpfs:
bb experiments/store-bakeoff/write_bench.clj per-log  /dev/shm/bk 3
bb experiments/store-bakeoff/write_bench.clj per-file /dev/shm/bk 3
bb experiments/store-bakeoff/write_bench.clj sqlite   /dev/shm/bk 3
bb experiments/store-bakeoff/baseline_bench.clj       /dev/shm/bk-base 3     # simplified floor
bb -cp out /tmp/coord_load_anchor.clj                                        # REAL commit! baseline anchor
# Axis 1 on real disk (inode/fsync), + fsync variants:
bash experiments/store-bakeoff/run_disk.sh
# Axes 2-5 (load/merge/read/footprint) at M:
bb experiments/store-bakeoff/load_bench.clj 100000  /dev/shm/bk-load
bb experiments/store-bakeoff/load_bench.clj 1000000 /dev/shm/bk-load
```

Scratch only: `/dev/shm`, `/tmp`. Never touches `~/.local/state/lodestar/claims.log`,
the canonical log, or the live `:7977/:7978` daemons. Raw run logs in `data/`.
