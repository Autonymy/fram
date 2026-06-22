#!/usr/bin/env bb
;; run.bb — the matched control-flow bake-off loop (C1).
;; ============================================================================
;; ONE loop, arm-specific ONLY at parse/apply (MASTER_SPEC §IV.4):
;;   PROMPT(arm,task,state) -> MODEL emits -> [PARSE/APPLY by arm]
;;     M1   : parse text -> REPLACE whole module
;;     M1.5 : parse text -> per-def UPSERT (untouched defs persist)
;;     M2   : parse EDN changeset -> mint/supersede -> full-module engine dump
;;   -> ENGINE GATE (fail-closed, IDENTICAL all arms: beagle-build-all -> '0 error')
;;   -> GRADE (bb load-file: target hidden test + preserved-fn collateral tests)
;;   -> CLASSIFY (taxonomy) -> PASS record | retry R rounds with the SAME structured
;;      error fed back (NORMALIZED so the file/line prefix is byte-identical across arms).
;;
;; The gate binary, the "0 error" check, the grader, the retry budget, the token
;; ceiling, and the fed-back error schema are ALL shared — the only difference is the
;; arm-specific apply step above. That is the C1 control.
;; ============================================================================
(ns run
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.java.io :as io]))

(load-file (str (System/getProperty "user.dir") "/m2_adapter.bb"))
(load-file (str (System/getProperty "user.dir") "/classify.bb"))

;; --- config (IDENTICAL across arms — record it) ------------------------------
(def MODEL (or (System/getenv "BAKEOFF_MODEL") "claude-opus-4-5-20251101"))
(def R (parse-long (or (System/getenv "BAKEOFF_R") "4")))         ; revision rounds
(def TOKEN-CEIL (parse-long (or (System/getenv "BAKEOFF_TOKEN_CEIL") "8000"))) ; output token ceiling/attempt
(def BEAGLE "/home/tom/code/beagle/bin/beagle-build-all")
(def ROUNDTRIP "/home/tom/code/beagle/beagle-lib/private/claims-roundtrip.rkt")
(def SCRATCH (or (System/getenv "BAKEOFF_SCRATCH")
                 "/home/tom/code/fram/experiments/authoring-bakeoff/scratch/run"))
(def PROMPTS-DIR "/home/tom/code/fram/experiments/authoring-bakeoff/prompts")

;; system prompt held IDENTICAL across arms (the IV lives in the user message only).
(def SYS-PROMPT
  "You are a precise code-emission engine. Follow the output contract exactly. Output ONLY the requested artifact — no prose, no explanation, no markdown fences.")

;; --- model call (pinned, recorded) -------------------------------------------
;; Returns {:text str :in-tokens n :out-tokens n :cache-tokens n :cost n :error str?}
(defn call-model [user-prompt]
  (let [r (p/sh {:in user-prompt}
                "claude" "-p"
                "--model" MODEL
                "--output-format" "json"
                "--disable-slash-commands"
                "--allowedTools" ""
                "--system-prompt" SYS-PROMPT)]
    (if (not (zero? (:exit r)))
      {:error (str "claude CLI exit " (:exit r) ": " (str/trim (:err r)))}
      (let [j (try (json/parse-string (:out r) true) (catch Exception e nil))]
        (if (or (nil? j) (:is_error j))
          {:error (str "model error: " (or (:result j) (str/trim (:err r))))}
          {:text (:result j)
           :in-tokens (get-in j [:usage :input_tokens] 0)
           :out-tokens (get-in j [:usage :output_tokens] 0)
           :cache-tokens (+ (get-in j [:usage :cache_creation_input_tokens] 0)
                            (get-in j [:usage :cache_read_input_tokens] 0))
           :cost (get j :total_cost_usd 0.0)})))))

;; --- strip accidental markdown fences (defensive; contract says none) --------
(defn unfence [t]
  (let [t (str/trim t)]
    (if (str/starts-with? t "```")
      (-> t (str/replace #"(?s)^```[a-zA-Z]*\n?" "") (str/replace #"```\s*$" "") str/trim)
      t)))

;; --- normalize a build diagnostic so the FILE/LINE prefix is byte-identical ---
;; across arms (G3/C1): the load-bearing reason is `beagle: <reason>`; the path/line
;; prefix is an artifact (M2 dump drops srcloc). Normalize `<anything>: beagle:` ->
;; `module: beagle:` and strip line numbers, so M1/M1.5/M2 feed back the same text.
(defn normalize-diag [out]
  (-> out
      (str/replace #"(?m)^\s*\S+?(?::\d+)?: beagle:" "  module: beagle:")
      (str/replace #"(?m)^\s*\S+?: type errors" "  module: type errors")
      (str/replace #"/[^\s:]+\.bclj" "module.bclj")
      str/trim))

;; ============================================================================
;; ENGINE GATE — shared, fail-closed. Input: a full-module representation as either
;; {:text "..."} (M1/M1.5) or {:dump-lines [...]} (M2). Builds, requires '0 error',
;; then grades target + preserved tests by loading the emitted clj in bb.
;; ============================================================================
(defn build-module [rep work tag]
  (let [outdir (str work "/out-" tag)]
    (cond
      (:text rep)
      (let [f (str work "/" tag ".bclj")]
        (spit f (:text rep))
        (let [r (p/sh BEAGLE f "--out" outdir)]
          {:exit (:exit r) :out (str (:out r) (:err r)) :outdir outdir}))
      (:dump-lines rep)
      (let [f (str work "/" tag ".edn")]
        (spit f (str (str/join "\n" (:dump-lines rep)) "\n"))
        (let [r (p/sh BEAGLE "--build-edn" f "--out" outdir)]
          {:exit (:exit r) :out (str (:out r) (:err r)) :outdir outdir})))))

(defn built? [gate] (str/includes? (:out gate) "0 error"))

;; grade: load the emitted clj, run the target + preserved hidden tests in bb.
;; returns {:target-pass? bool :collateral? bool :grade-err str?}
(defn grade [outdir task]
  (let [clj (str outdir "/beagle/user.clj")]
    (if-not (.exists (io/file clj))
      {:grade-err (str "no emitted clj at " clj)}
      ;; Grade via RUNTIME eval: SCI resolves fully-qualified symbols (beagle.user/fn)
      ;; at ANALYSIS time, before load-file runs — so the test expression must be
      ;; eval'd from a string AFTER the namespace is loaded. We pr-str each test as a
      ;; data string and (eval (read-string ...)) it at runtime.
      (let [n-pres (count (:preserved task))
            target-prints (format "(println (str \"TARGET=\" (eval (read-string %s))))"
                                  (pr-str (:target-test task)))
            pres-prints (str/join "\n  "
                          (map-indexed (fn [i pv]
                                         (format "(println (str \"PRES%d=\" (eval (read-string %s))))"
                                                 i (pr-str (:test pv))))
                                       (:preserved task)))
            grader (format
                    "(try (load-file \"%s\")\n  %s\n  %s\n  (catch Throwable e (println (str \"GRADEERR=\" (.getMessage e)))))"
                    clj target-prints pres-prints)
            gf (str outdir "/grade.clj")]
        (spit gf grader)
        (let [r (p/sh "bb" gf)
              out (str (:out r) (:err r))]
          (cond
            (str/includes? out "GRADEERR=")
            {:grade-err (-> out (str/split #"GRADEERR=") second str/trim)}
            :else
            {:target-pass? (str/includes? out "TARGET=true")
             :collateral? (boolean (some (fn [i] (str/includes? out (format "PRES%d=false" i)))
                                         (range n-pres)))}))))))

;; ============================================================================
;; ARM-SPECIFIC APPLY — the ONLY place arms differ.
;; ============================================================================
;; current-state for an arm: M1/M1.5 carry the program TEXT; M2 carries the engine
;; dump lines (+ name->id). The harness holds BOTH projections of the same program.

;; M1: replace the whole module with the model's text.
(defn apply-m1 [_current model-text task]
  ;; the model re-emits the WHOLE module (incl. #lang + unchanged forms). Use verbatim,
  ;; but guarantee the #lang header (the contract implies a compilable module).
  (let [t (unfence model-text)
        t (if (str/includes? t "#lang") t (str "#lang beagle/clj\n" t))]
    {:text t}))

;; M1.5: parse the model's emitted defs; UPSERT each into the current module by name;
;; untouched defs persist. The model emits ONLY changed/added defs (its contract).
(defn def-name [form]
  (when (and (seq? form) (#{'defn 'defn- 'defrecord 'def 'defonce} (first form)))
    (second form)))

(defn read-forms [text]
  ;; read top-level forms, skipping a leading #lang line.
  (let [body (str/replace text #"(?m)^#lang[^\n]*\n?" "")
        rdr (java.io.PushbackReader. (java.io.StringReader. body))]
    (loop [acc []]
      (let [f (try (edn/read {:eof ::eof} rdr) (catch Exception _ ::eof))]
        (if (= f ::eof) acc (recur (conj acc f)))))))

(defn apply-m15 [current model-text task]
  ;; current = {:text "..."} ; parse current forms, parse emitted forms, upsert by name.
  (let [cur-forms (read-forms (:text current))
        new-forms (read-forms (unfence model-text))
        new-by-name (into {} (keep (fn [f] (when-let [n (def-name f)] [n f])) new-forms))
        ;; replace existing defs in place; keep order; append any net-new defs.
        replaced (map (fn [f] (if-let [n (def-name f)] (get new-by-name n f) f)) cur-forms)
        existing-names (set (keep def-name cur-forms))
        added (for [f new-forms :let [n (def-name f)] :when (and n (not (existing-names n)))] f)
        all (concat replaced added)
        text (str "#lang beagle/clj\n"
                  (str/join "\n" (map pr-str all)) "\n")]
    {:text text}))

;; M2: parse the EDN changeset, apply over the current engine graph (recompile-gated).
(defn apply-m2-arm [current model-text task]
  (let [res (m2-adapter/apply-m2 current (unfence model-text) (str (:module task) ".bclj"))]
    (if (:error res)
      {:adapter-error (:error res)}
      {:dump-lines (:lines res)})))

;; current-state projection for each arm, built from the task's initial-text.
(defn initial-current [arm task work]
  (case arm
    (:m1 :m15) {:text (:initial-text task)}
    :m2 (let [f (str work "/init.bclj")]
          (spit f (:initial-text task))
          ;; project the initial program to an engine dump (the M2 'current state')
          (let [r (p/sh "racket" ROUNDTRIP "--emit-edn" f)]
            {:lines (str/split-lines (:out r)) :name->id {}}))))

;; ============================================================================
;; PROMPT ASSEMBLY — shared instruction + arm contract + examples (from frozen
;; prompt files) + the TASK + the current STATE. The frozen prompt file already
;; carries the shared block + contract + examples; we append the live TASK/STATE.
;; ============================================================================
(defn arm-prompt-file [arm density]
  (let [a (case arm :m1 "M1" :m15 "M1.5" :m2 "M2")]
    (str PROMPTS-DIR "/" a "-" density ".md")))

(defn state-text [arm task current]
  (case arm
    (:m1 :m15) (let [t (str/trim (str/replace (:text current) #"(?m)^#lang[^\n]*\n?" ""))]
                 (if (str/blank? t) "(empty program)" t))
    :m2 (let [t (str/trim (str/replace (:initial-text task) #"(?m)^#lang[^\n]*\n?" ""))]
          ;; show the current program as its STATE (text form) PLUS a note that nodes
          ;; are addressable; for additive Rung-0 the model mints fresh ids, so the
          ;; text rendering of state is sufficient (matches bank E3).
          (if (str/blank? t) "(empty program)"
              (str t "\n; (existing nodes are addressable by @id; mint fresh @ids for new nodes)")))))

(defn build-prompt [arm density task current error-feedback]
  (let [base (slurp (arm-prompt-file arm density))]
    (str base
         "\n\n## YOUR TASK (scored)\n"
         "TASK: " (:task task) "\n"
         "STATE:\n```\n" (state-text arm task current) "\n```\n"
         (when error-feedback
           (str "\n## PREVIOUS ATTEMPT FAILED — revise\n"
                "Your prior output produced this engine error. Fix it and re-emit per your output contract:\n```\n"
                error-feedback "\n```\n"))
         "\nEmit your output now (only the artifact, no prose):")))

;; ============================================================================
;; ONE ATTEMPT = model call + apply + gate + grade + classify.
;; ============================================================================
(defn apply-arm [arm current model-text task]
  (case arm
    :m1  (apply-m1 current model-text task)
    :m15 (apply-m15 current model-text task)
    :m2  (apply-m2-arm current model-text task)))

(defn run-attempt [arm density task current work attempt-idx error-feedback]
  (let [prompt (build-prompt arm density task current error-feedback)
        m (call-model prompt)]
    (if (:error m)
      {:fatal (:error m) :tokens 0}
      (let [over-ceiling? (> (:out-tokens m) TOKEN-CEIL)
            applied (apply-arm arm current (:text m) task)
            tag (format "%s-%s-%s-a%d" (name arm) density (:id task) attempt-idx)]
        (if (:adapter-error applied)
          {:model m :applied applied
           :class (classify/classify {:adapter-error (:adapter-error applied)})
           :gate-out (:adapter-error applied)
           :over-ceiling? over-ceiling?}
          (let [gate (build-module applied work tag)
                b? (built? gate)
                g (when b? (grade (:outdir gate) task))
                cls (classify/classify
                     {:built? b?
                      :gate-out (:out gate)
                      :target-pass? (:target-pass? g)
                      :collateral? (:collateral? g)})]
            {:model m :applied applied :gate gate :grade g :class cls
             :gate-out (:out gate) :over-ceiling? over-ceiling?}))))))

;; full trial = up to R+1 attempts with feedback; stop on PASS or budget.
(defn run-trial [arm density task work trial-idx]
  (let [current (initial-current arm task work)]
    (loop [attempt 0 feedback nil rounds [] tot-in 0 tot-out 0 tot-cache 0 tot-cost 0.0]
      (let [r (run-attempt arm density task current work attempt feedback)]
        (if (:fatal r)
          {:arm arm :density density :task (:id task) :trial trial-idx
           :bucket :HARNESS_ERROR :pass? false :fatal (:fatal r)
           :attempts (inc attempt) :rounds rounds
           :in-tokens tot-in :out-tokens tot-out :cache-tokens tot-cache :cost tot-cost}
          (let [m (:model r)
                ti (+ tot-in (:in-tokens m)) to (+ tot-out (:out-tokens m))
                tc (+ tot-cache (:cache-tokens m)) cost (+ tot-cost (:cost m))
                cls (:class r)
                round {:attempt attempt :bucket (:bucket cls)
                       :out-tokens (:out-tokens m)
                       :diag (when-not (:pass? cls) (normalize-diag (or (:gate-out r) "")))
                       :raw (:text m)}
                rounds' (conj rounds round)]
            (cond
              (:pass? cls)
              {:arm arm :density density :task (:id task) :trial trial-idx
               :bucket :PASS :pass? true :attempts (inc attempt) :first-pass-attempt attempt
               :rounds rounds' :in-tokens ti :out-tokens to :cache-tokens tc :cost cost}
              (or (>= attempt R) (:over-ceiling? r))
              {:arm arm :density density :task (:id task) :trial trial-idx
               :bucket (:bucket cls) :pass? false :attempts (inc attempt)
               :over-ceiling? (:over-ceiling? r)
               :rounds rounds' :in-tokens ti :out-tokens to :cache-tokens tc :cost cost}
              :else
              (recur (inc attempt) (normalize-diag (or (:gate-out r) "")) rounds' ti to tc cost))))))))

;; ============================================================================
;; DRIVER
;; ============================================================================
(defn load-tasks []
  (:tasks (edn/read-string (slurp (str (System/getProperty "user.dir") "/tasks.edn")))))

(defn -main [& args]
  (let [opts (apply hash-map args)
        arms (if-let [a (get opts "--arms")] (map keyword (str/split a #",")) [:m1 :m15 :m2])
        densities (if-let [d (get opts "--densities")] (str/split d #",") ["d3" "d6" "d10"])
        task-ids (when-let [t (get opts "--tasks")] (set (str/split t #",")))
        n (parse-long (or (get opts "--n") "2"))
        label (or (get opts "--label") "run")
        all-tasks (load-tasks)
        tasks (if task-ids (filter #(task-ids (:id %)) all-tasks) all-tasks)
        work (str SCRATCH "/" label)
        _ (do (p/sh "rm" "-rf" work) (.mkdirs (io/file work)))
        results-file (str work "/results.jsonl")]
    (println (format "BAKEOFF %s | model=%s R=%d ceil=%d | arms=%s densities=%s tasks=%s n=%d"
                     label MODEL R TOKEN-CEIL (str/join "," (map name arms))
                     (str/join "," densities) (str/join "," (map :id tasks)) n))
    (println (str "results -> " results-file))
    (with-open [w (io/writer results-file)]
      (doseq [arm arms density densities task tasks trial (range n)]
        (let [res (run-trial arm density task work trial)]
          (.write w (str (json/generate-string res) "\n"))
          (.flush w)
          (println (format "  %-4s %-3s %-2s t%-2d -> %-18s attempts=%d out=%d"
                           (name arm) density (:id task) trial
                           (name (:bucket res)) (:attempts res) (:out-tokens res))))))
    (println "DONE.")))

(apply -main *command-line-args*)
