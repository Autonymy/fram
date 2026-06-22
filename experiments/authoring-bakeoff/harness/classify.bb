#!/usr/bin/env bb
;; classify.bb — the failure taxonomy (5 buckets, fixed precedence).
;; ============================================================================
;; Precedence (MASTER_SPEC Part IV): a failure is labeled by the FIRST that applies.
;;   COLLATERAL_DAMAGE > KCH > REFERENCE_ERROR > SEMANTIC_WRONG > STRUCTURAL_INVALID
;; A PASS (compiles + typechecks + hidden test passes + no collateral) is :PASS.
;;
;;   COLLATERAL_DAMAGE  — an UNTOUCHED definition's behavior changed (the edit
;;                        perturbed code the task did not name). Detected by running
;;                        the preserved-fn hidden assertions: any that now fail = CD.
;;                        This OUTRANKS everything: even a correct new fn is a failure
;;                        if it broke a bystander. (H1 headline metric.)
;;   KCH                — knowledge-cutoff hallucination: the artifact used an op /
;;                        symbol / predicate / type / kind NOT on the engine surface.
;;                        Surfaces as a build error that is NOT a plain type mismatch:
;;                        unbound/unknown-op/unknown-type/unsupported-form.
;;   REFERENCE_ERROR    — a reference doesn't resolve (unbound symbol / dangling @id /
;;                        wrong arity at a call). Wiring is wrong but ops are real.
;;   SEMANTIC_WRONG     — compiles + typechecks + RUNS, but the hidden test of the
;;                        TARGET fn fails (wrong algorithm/output). Negative control (R4).
;;   STRUCTURAL_INVALID — won't even form a valid AST: parse error, malformed triples,
;;                        empty/garbage output, type mismatch with no missing-name.
;;
;; The build-gate error text + the test outcomes feed this. KCH vs REFERENCE_ERROR vs
;; STRUCTURAL_INVALID is decided by matching the engine diagnostic against known shapes;
;; the engine surface (Part V / FREEZE) is the ground truth for "off-catalog".
;; ============================================================================
(ns classify
  (:require [clojure.string :as str]))

;; --- engine-surface ground truth (FREEZE Part V; KCH = off this set) ---------
;; The builtin op/type surface the bank exercises. A symbol used as an operator or
;; type that is NOT here AND triggers an unbound/unknown error = KCH. (We detect KCH
;; primarily by the engine's own diagnostic shape; this set documents the boundary.)
(def known-types #{"Int" "Bool" "String" "Float" "Nat" "Char" "Keyword" "Any"})
;; Nat intentionally listed: the FREEZE notes Nat is REJECTED by beagle — so a Nat
;; return is build-rejected; we leave reason-shape detection to do the real work.

(defn- diag-shape
  "Classify a build-gate diagnostic line into :kch | :reference | :type | :structural.
   Matches the beagle error vocabulary (parse.rkt / check.rkt reasons)."
  [out]
  (let [o (str/lower-case out)]
    (cond
      ;; unbound / unknown name → could be reference OR a hallucinated op. A use of a
      ;; name that doesn't exist as a binding: if it's in an OPERATOR position and is
      ;; not a real builtin, that's KCH; otherwise an unresolved reference. The engine
      ;; says "unbound" / "cannot resolve" for both; we split on the hallucination cue.
      (or (str/includes? o "unknown op")
          (str/includes? o "unsupported expression")
          (str/includes? o "unsupported form")
          (str/includes? o "not a valid")
          (str/includes? o "unknown type")
          (str/includes? o "no such builtin")
          (str/includes? o "invalid type")) :kch
      (or (str/includes? o "unbound")
          (str/includes? o "cannot resolve")
          (str/includes? o "not bound")
          (str/includes? o "arity")
          (str/includes? o "@id not in")) :reference
      (or (str/includes? o "expected return")
          (str/includes? o "expected ")
          (str/includes? o "type error")
          (str/includes? o "got ")) :type
      (or (str/includes? o "parse")
          (str/includes? o "read")
          (str/includes? o "unexpected")
          (str/includes? o "structural_invalid")
          (str/includes? o "malformed")) :structural
      :else :structural)))

(defn classify
  "Inputs:
     :adapter-error  str|nil  — M2 adapter refused to even form a graph (STRUCTURAL_INVALID)
     :built?         bool     — gate produced '0 error' (compiled+typechecked)
     :gate-out       str      — the build-gate diagnostic (when not built)
     :target-pass?   bool     — the TARGET fn's hidden assertion passed (only meaningful if built)
     :collateral?    bool     — an UNTOUCHED fn's preserved-assertion FAILED (only if built)
   -> {:bucket KEYWORD :pass? bool}"
  [{:keys [adapter-error built? gate-out target-pass? collateral?]}]
  (cond
    ;; adapter couldn't parse the changeset → structural before any gate
    adapter-error {:bucket :STRUCTURAL_INVALID :pass? false :detail adapter-error}
    ;; built (compiled+typechecked+emitted): collateral OUTRANKS target correctness
    built?
    (cond
      collateral?            {:bucket :COLLATERAL_DAMAGE :pass? false}
      target-pass?           {:bucket :PASS :pass? true}
      :else                  {:bucket :SEMANTIC_WRONG :pass? false})  ; runs, wrong output
    ;; did NOT build → classify the diagnostic by precedence (KCH > REF > STRUCT)
    :else
    (case (diag-shape gate-out)
      :kch        {:bucket :KCH :pass? false}
      :reference  {:bucket :REFERENCE_ERROR :pass? false}
      ;; a pure type mismatch with no missing-name = STRUCTURAL_INVALID (malformed
      ;; relative to types) — NOT SEMANTIC_WRONG (that bucket requires the program to
      ;; RUN). The negative control only fires on programs that build.
      :type       {:bucket :STRUCTURAL_INVALID :pass? false :detail "type-mismatch-build-reject"}
      :structural {:bucket :STRUCTURAL_INVALID :pass? false})))
