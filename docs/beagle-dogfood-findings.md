# Beagle dogfooding — findings (gap list)

A real artifact, not scar tissue. The discipline: when typing dynamic data forces a
contortion, write it down here as "Beagle lacks X" — don't silently absorb it as
`Any` in the code. (Policy lives in the global beagle-authoring skill, §4.)

The probe that matters is **not** "does interop compile?" (it does, trivially) — it's
**"can I replace this `Any` with a real type, or does Beagle force me back to `Any`?"**

## Findings so far (2026-06-17)

| # | Where | Tried to type | Outcome | Verdict |
|---|---|---|---|---|
| 1 | `kernel/reachable-from?` `succ` | `[String -> (Vec String)]` (fn type) | **0 errors** | **No gap.** The `Any` I first wrote was author laziness, not a language limit. Real type now in place; kernel `cycle?`/`cycle-i?` are checked against it. |
| 2 | `lodestar.gatepolicy` Tenant/Bucket | `defrecord` + `(Map String Tenant)` + `Float` | **0 errors** | **No gap.** Records, typed maps, numeric types, fn types all express cleanly. The security decisions are real-typed. |
| 3 | `gatepolicy/sha256-hex` | `MessageDigest/getInstance` (static interop) | 1 error → fixed | **Minor ergonomics.** Non-`java.lang` classes need an explicit `(import …)`; otherwise Beagle parses `Pkg.Class/method` as an unresolved *namespace alias*. The error even suggested `(require …)`, which is the wrong fix for a Java class. **Possible Beagle improvement:** detect the `Pkg.Class/method` shape and suggest `(import)` instead. |

## Justified `Any` (the one honest use)

`Any` is correct at the **untrusted-input boundary**, where you validate dynamic data
*into* a typed shape:
- `gatepolicy/parse-tenant`'s `cfg` — raw registry EDN off disk → validated to a typed `Tenant`.
- `gatepolicy/valid-op?`'s `parsed` — untrusted wire body → `Bool`.

Everywhere else, `Any` is a smell.

## The real conclusion

The honest gap this session surfaced was **not** in Beagle — it was **discipline**.
Beagle expressed every real type asked of it (fn types, records, typed maps, numerics);
the bail-to-`Any` was a human reflex when the data got dynamic, which defeats the whole
point of a typed language. Beagle's coverage is better than the habit suggested. The fix
was the policy (skill §4), not a language change. Keep this list open: the moment a real
"Beagle can't say X" shows up, it goes here and feeds the language — but so far, none have.
