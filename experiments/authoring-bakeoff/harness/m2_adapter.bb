#!/usr/bin/env bb
;; m2_adapter.bb — the M2 claim-changeset adapter (R3).
;; ============================================================================
;; Maps a model-emitted flat EDN changeset over the program graph to the engine's
;; integer-node `--emit-edn` dump, then the SHARED gate (beagle-build-all
;; --build-edn) recompiles+typechecks it. NO fram-engine change: this is a pure
;; harness translation, exactly the "HARNESS ADAPTER maps EDN→fram claims" of R3.
;;
;; MODEL EMISSION FORMAT (R3, in-distribution flat EDN triples):
;;   one tuple per line, EDN vector [subject predicate object]
;;     ["@m#1" :kind "list"]        node tag        K ∈ {list symbol string number bool keyword char nil}
;;     ["@m#2" :v "defn"]           leaf value
;;     ["@m#1" :f0 "@m#2"]          ordered child edge (f0 = head)
;;   retract (supersede an edge/spelling): a 4-vector headed by RETRACT
;;     [RETRACT "@m#3" :v "inc"]
;;   a bare 3-vector is an assert; [ASSERT ...] is the same, allowed for emphasis.
;;   bound_to (rename): ["@u" :bound_to "@def"] materializes the durable ref edge.
;;
;; engine-faithful to list/fN/leaf (G0 finding 1): no node-kind catalog —
;; `if`/`defrecord`/`:x` are plain lists headed by their operator symbol. Predicates
;; are keywords here (:kind/:v/:fN), the most in-distribution EDN spelling, mapping
;; 1:1 to the engine's string preds ("kind"/"v"/"fN").
;;
;; APPLY SEMANTICS (delta over current graph, fail-closed):
;;   current state arrives as engine integer-node dump lines + a name->id map (which
;;   @m#K the prompt STATE showed maps to which engine id). Model @m#N ids that are
;;   NOT in that map are minted fresh (ids above current max → no collision). A model
;;   tuple naming an EXISTING node supersedes/extends it in place. New top-level forms
;;   are appended as wrapper children. Result = full-module engine dump for the gate.
;; ============================================================================
(ns m2-adapter
  (:require [clojure.string :as str]
            [clojure.edn :as edn]))

(defn- read-all-forms
  "read EVERY EDN form on a line (the model may pack several tuples per line, or one
   per line — both legal). Returns [forms] or ::bad if any garbage is hit."
  [line]
  (let [rdr (java.io.PushbackReader. (java.io.StringReader. line))]
    (loop [acc []]
      (let [form (try (edn/read {:eof ::eof} rdr) (catch Exception _ ::bad))]
        (cond
          (= form ::eof) acc
          (= form ::bad) ::bad
          :else (recur (conj acc form)))))))

(defn- classify-tuple
  "one EDN form -> [:assert triple] | [:retract triple] | [:error msg]."
  [form]
  (cond
    (not (vector? form))
    [:error (str "STRUCTURAL_INVALID: changeset element is not a tuple: " (pr-str form))]
    (and (= 4 (count form)) (#{'RETRACT 'ASSERT} (first form)))
    (let [[verb s p o] form
          triple [(str s) (keyword p) (if (symbol? o) (str o) o)]]
      [(if (= verb 'RETRACT) :retract :assert) triple])
    (= 3 (count form))
    (let [[s p o] form]
      [:assert [(str s) (keyword p) (if (symbol? o) (str o) o)]])
    :else
    [:error (str "STRUCTURAL_INVALID: tuple wrong arity (" (count form) "): " (pr-str form))]))

(defn parse-changeset
  "model text -> {:asserts [[s pred o]...] :retracts [...]} | {:error str}.
   Robust to multiple tuples per physical line OR one per line."
  [text]
  (let [lines (->> (str/split-lines text)
                   (map str/trim)
                   (remove str/blank?)
                   (remove #(str/starts-with? % ";")))
        all-forms (reduce (fn [acc ln]
                            (let [fs (read-all-forms ln)]
                              (if (= fs ::bad)
                                (reduced ::bad)
                                (into acc fs))))
                          [] lines)]
    (if (= all-forms ::bad)
      {:error "STRUCTURAL_INVALID: unparseable changeset line"}
      ;; desugar a `:head` convenience tuple [id :head "op"] into the engine-faithful
      ;; f0→symbol-leaf form: mint a head leaf id <subj>/h, kind=symbol, v=op, f0=that.
      ;; (the bank teaches the explicit form; this only catches a model that abbreviates.)
      (let [all-forms
            (mapcat (fn [form]
                      (if (and (vector? form) (= 3 (count form)) (= :head (second form)))
                        (let [[s _ op] form
                              hid (str s "/h")]
                          [[hid :kind "symbol"] [hid :v (str op)] [s :f0 hid]])
                        [form]))
                    all-forms)]
      (loop [fs all-forms asserts [] retracts []]
        (if (empty? fs)
          (if (and (empty? asserts) (empty? retracts))
            {:error "STRUCTURAL_INVALID: empty changeset (no tuples emitted)"}
            {:asserts asserts :retracts retracts})
          (let [[kind v] (classify-tuple (first fs))]
            (case kind
              :error   {:error v}
              :assert  (recur (rest fs) (conj asserts v) retracts)
              :retract (recur (rest fs) asserts (conj retracts v))))))))))

(defn read-dump-triples
  "engine --emit-edn lines -> {:file path :triples [[id pred obj]...]} (string preds)."
  [lines]
  (let [file (some (fn [l] (when (str/starts-with? l "@file ") (str/trim (subs l 6)))) lines)
        triples (vec (for [l lines :when (str/starts-with? l "[")] (edn/read-string l)))]
    {:file file :triples triples}))

(defn- props-from-triples
  "[[id pred obj]...] (string or kw preds, possibly multiple per line) -> {id {pred obj}}.
   Engine dumps can pack several tuples per physical line; read-dump-triples already
   split per `[`-prefixed line, but a line like `[a ..] [b ..]` reads only the FIRST
   form via edn/read-string. So we re-read each line for ALL forms."
  [lines]
  (reduce
   (fn [m line]
     (if-not (str/starts-with? line "[")
       m
       (let [rdr (java.io.PushbackReader. (java.io.StringReader. line))]
         (loop [m m]
           (let [form (try (edn/read {:eof ::eof} rdr) (catch Exception _ ::eof))]
             (if (or (= form ::eof) (not (vector? form)) (not= 3 (count form)))
               m
               (let [[id pred obj] form
                     pk (if (keyword? pred) (name pred) (str pred))]
                 (recur (update m id (fnil assoc {}) pk obj)))))))))
   {} lines))

(defn render-engine-dump
  "current = {:lines [...] :name->id {...}} (or nil for empty). changeset = parse output.
   -> {:lines [str...] :file path} | {:error str}."
  [current changeset module-file]
  (let [cur-lines (or (:lines current) [])
        cur-props (props-from-triples cur-lines)
        cur-name->id (or (:name->id current) {})
        cur-max (reduce (fn [mx [id h]]
                          (reduce (fn [mx2 [_ o]] (max mx2 (if (int? o) o 0)))
                                  (max mx (if (int? id) id 0)) h))
                        0 cur-props)
        all-tuples (concat (:asserts changeset) (:retracts changeset))
        model-ids (->> all-tuples
                       (mapcat (fn [[s _ o]] [s (when (and (string? o) (str/starts-with? o "@")) o)]))
                       (remove nil?) distinct)
        next-id (atom cur-max)
        name->eng (reduce (fn [m mid]
                            (if-let [eid (get cur-name->id mid)]
                              (assoc m mid eid)
                              (assoc m mid (swap! next-id inc))))
                          {} model-ids)
        resolve (fn [x] (if (and (string? x) (str/starts-with? x "@"))
                          (or (name->eng x)
                              (throw (ex-info (str "REFERENCE_ERROR: @id not in current state or changeset: " x) {})))
                          x))]
    (try
      (let [props (atom cur-props)]
        (doseq [[s p o] (:retracts changeset)]
          (let [eid (resolve s) pk (name p)
                ov (if (and (string? o) (str/starts-with? o "@")) (resolve o) o)]
            (when (= (get-in @props [eid pk]) ov)
              (swap! props update eid dissoc pk))))
        (doseq [[s p o] (:asserts changeset)]
          (let [eid (resolve s) pk (name p)
                ov (if (and (string? o) (str/starts-with? o "@")) (resolve o) o)]
            (swap! props update eid (fnil assoc {}) pk ov)))
        (let [p @props
              wrapper (some (fn [[id h]]
                              (when (and (= "list" (get h "kind"))
                                         (= "beagle-file" (get-in p [(get h "f0") "v"])))
                                id))
                            p)
              child-ids (set (for [[_ h] p [pred o] h :when (re-matches #"f\d+.*" pred)] o))
              wrapper-h (get p wrapper)
              used-idxs (->> (keys wrapper-h)
                             (keep #(when-let [mm (re-matches #"f(\d+)" %)] (parse-long (second mm))))
                             (into #{}))
              next-slot (atom (if (empty? used-idxs) 0 (inc (apply max used-idxs))))
              new-forms (for [[id h] p
                              :when (and (= "list" (get h "kind"))
                                         (not= id wrapper)
                                         (not (child-ids id)))]
                          id)]
          (doseq [fid (sort new-forms)]
            (swap! props assoc-in [wrapper (str "f" @next-slot)] fid)
            (swap! next-slot inc))
          (let [final @props
                lines (concat
                       [(str "@file " (or module-file "mod.bclj"))]
                       (for [id (sort (keys final))
                             [pred o] (sort-by key (get final id))
                             :let [line (cond
                                          (= pred "kind") (format "[%s \"kind\" %s]" id (pr-str (str o)))
                                          (= pred "v") (format "[%s \"v\" %s]" id (pr-str (str o)))
                                          (re-matches #"f\d+.*" pred) (format "[%s %s %s]" id (pr-str pred) o)
                                          (= pred "child") nil
                                          (= pred "bound_to") nil
                                          (#{"line" "col" "pos" "span"} pred) nil
                                          :else (format "[%s %s %s]" id (pr-str pred) o))]
                             :when line]
                         line))]
            {:lines (vec lines) :file (or module-file "mod.bclj")})))
      (catch clojure.lang.ExceptionInfo e
        {:error (.getMessage e)}))))

(defn apply-m2 [current model-text module-file]
  (let [cs (parse-changeset model-text)]
    (if (:error cs) cs (render-engine-dump current cs module-file))))
