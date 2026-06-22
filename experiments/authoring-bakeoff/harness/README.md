# Authoring bake-off harness

Implements the matched control-flow loop from `../MASTER_SPEC.md` (Part IV) under
the G0 freeze (`../FREEZE.md`). Three arms, one loop, arm-specific ONLY at parse/apply.

## Arms
- **M1** — text, whole-module re-emission (D1). `apply-m1`: model emits the whole
  module → replace.
- **M1.5** — text, changed-defs-only (R2). `apply-m15`: model emits only the defs it
  adds/changes → def-level upsert; untouched defs persist by construction.
- **M2** — claim changeset, EDN triples (R3). `apply-m2-arm` → `m2_adapter.bb`: model
  emits `["@m#1" :kind "list"] ["@m#1" :f0 "@m#2"] …` → engine integer-node dump →
  **recompile gate** `beagle-build-all --build-edn` (the typecheck-gated path; D3).

## The shared ENGINE GATE (C1 control)
All arms converge to a full-module representation and run the SAME
`beagle-build-all` (`build_module`): M1/M1.5 via the text path, M2 via `--build-edn`.
Same binary, same `0 error` check, same compiler/diagnostic. The error fed back to the
model is NORMALIZED (`normalize-diag`) so the `<file>:<line>:` prefix is byte-identical
across arms — only the load-bearing `beagle: <reason>` line varies (G3 verified
identical: see `g3_parity.bb`). Grading loads the emitted `.clj` in babashka and runs
the target hidden test + each preserved-fn test (collateral-damage probe).

## Files
| file | role |
|------|------|
| `run.bb` | the matched loop + driver. `bb run.bb --arms m1,m15,m2 --densities d3,d6,d10 --tasks 0A,0B --n 30 --label X` |
| `m2_adapter.bb` | M2 changeset → engine dump (R3 adapter; no fram change) |
| `classify.bb` | the 5-bucket taxonomy with fixed precedence (COLLATERAL_DAMAGE > KCH > REFERENCE_ERROR > SEMANTIC_WRONG > STRUCTURAL_INVALID) |
| `tasks.edn` | Rung-0 task defs (0A author `square`; 0B add `dec` given `inc`) |
| `gen-prompts.bb` | regenerates the 9 frozen prompts from `../prompts/bank.edn` |
| `analyze.bb` | results.jsonl → Rung-0 report (Wilson 95% CIs, slope, cost) |
| `selftest_adapter.bb`, `selftest2.bb`, `verify_bank.bb`, `g3_parity.bb` | harness regression checks |
| `PREDICTIONS.md` | pre-registered Rung-0 predictions |

## Pinned config (identical across all arms — C1)
- model: `claude-opus-4-5-20251101` (override `BAKEOFF_MODEL`)
- R (revision rounds): 4 (`BAKEOFF_R`)
- output token ceiling/attempt: 8000 (`BAKEOFF_TOKEN_CEIL`)
- decoding params / seed: Claude CLI defaults, no temperature/seed override (the CLI
  exposes none) — identical across arms, which is what C1 requires.
- system prompt: a fixed minimal `SYS-PROMPT` (held identical); the IV lives entirely
  in the user message (the frozen prompt file + live task/state).

## Known confound (declared)
Every call carries the Claude Code default system-prompt cache overhead (~25–28k
cache tokens). It is a FIXED additive constant shared by all arms, so it does not
break C1 parity, but it dominates raw cost — report differential (per-arm) costs, not
just absolutes. To eliminate it entirely one would need `--bare` + `ANTHROPIC_API_KEY`
(not available in this environment; `--bare` ignores the logged-in OAuth).

## No daemon / no live state
The recompile gate is a pure `beagle-build-all` subprocess and grading is babashka —
no fram coordinator/daemon is touched. Scratch ports/dirs only.
