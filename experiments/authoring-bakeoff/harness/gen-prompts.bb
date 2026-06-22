#!/usr/bin/env bb
;; gen-prompts.bb — assemble the NINE frozen prompts (M1 / M1.5 / M2 × d3/d6/d10)
;; from the engine-verified bank. Supersedes the six-prompt G0 generator:
;;   * adds M1.5 (text front-end, def-level apply; "emit only defs you add/change").
;;   * re-renders M2 to R3 flat EDN triples with KEYWORD predicates
;;     (`[ "@m#1" :kind "list"] [ "@m#1" :f0 "@m#2"]`), engine-faithful to list/fN/leaf,
;;     replacing the bespoke string-pred `[@id "attr" val]` form.
;; Shared instruction block byte-identical across all nine; the ONLY per-arm diff is
;; the output-contract paragraph. Writes to experiments/authoring-bakeoff/prompts/.
(require '[clojure.edn :as edn] '[clojure.string :as str])
(def bank (edn/read-string (slurp "/home/tom/code/fram/experiments/authoring-bakeoff/prompts/bank.edn")))
(def OUT "/home/tom/code/fram/experiments/authoring-bakeoff/prompts")

(def shared-block
  (str/join "\n"
   ["You are authoring code in beagle, a Clojure-subset language with explicit type"
    "annotations (`:- Type`)."
    ""
    "You will be given: (1) a **task**, (2) the **current program state** (empty, or the"
    "existing program). Produce output that satisfies the task and nothing more. A"
    "fail-closed engine will compile, type-check, and run your output against a hidden"
    "test vector. If it fails, you receive a structured error report"
    "(`<file>:<line>: beagle: <reason>`) and may revise. You have ‹R› revision rounds"
    "and a ‹TOKEN_BUDGET› ceiling, whichever binds first."
    ""
    "Rules: output **only** the artifact specified by your output contract — no prose,"
    "no fences. Do not invent operations, predicates, parameters, or types not part of"
    "beagle; if unsure an element exists, do not use it. For edit tasks, preserve the"
    "behavior of every definition the task does not ask you to change."]))

(def m1-contract
  (str/join "\n"
   ["## Output contract (M1 — text, whole module)"
    "Emit beagle source **text**. The compilation/grading unit is the **module**: when"
    "you modify an existing program, re-emit the ENTIRE file with your change applied —"
    "reproduce every unchanged form verbatim so it keeps compiling, and edit only the"
    "form the task names. (The text modality has no identity edges: a reference is a"
    "*spelling*, so a rename is a spelling change at the definition AND at every use"
    "site; miss one and you get an `unbound` error the gate catches.)"]))

(def m15-contract
  (str/join "\n"
   ["## Output contract (M1.5 — text, changed definitions only)"
    "Emit beagle source **text**, but **only the definitions you ADD or CHANGE** — do"
    "NOT re-emit unchanged definitions. The harness applies your output at"
    "definition granularity: each `defn`/`defrecord`/`def` you emit REPLACES the"
    "existing definition of that name (or is added if new); every definition you do"
    "NOT emit is preserved untouched. So for an additive task emit just the new"
    "definition; for an edit emit just the changed definition. (Like M1 this is text"
    "with spelling-based references — if you rename a definition, every use site that"
    "you DO emit must use the new spelling.)"]))

(def m2-contract
  (str/join "\n"
   ["## Output contract (M2 — claim changeset, EDN triples)"
    "Emit a flat changeset: EDN triples, one per line, `[\"@id\" :predicate value]`. A"
    "program is a graph of `(subject predicate object)` claims; you author it by"
    "stating these AST facts. The harness applies each triple as a claim-delta (mint"
    "new `\"@id\"`s / supersede existing ones) and **recompile-gates the batch"
    "fail-closed** (renders the edited graph and runs the SAME beagle compile+typecheck"
    "as the text arms; a batch that does not compile is refused whole, nothing"
    "written). Emit **only the delta** — never re-emit unchanged definitions."
    "Reference existing nodes by `\"@id\"` as shown in current state. To RETRACT a claim"
    "(supersede an edge/spelling) write `[RETRACT \"@id\" :predicate value]`; a fresh"
    "assertion is the bare triple (or `[ASSERT \"@id\" :predicate value]` for emphasis)."
    ""
    "The AST vocabulary is small and fixed (keyword predicates):"
    "- `[\"@id\" :kind K]` — every node's tag. `K ∈ {\"list\" \"symbol\" \"string\" \"number\" \"bool\" \"keyword\" \"char\" \"nil\"}`."
    "- `[\"@id\" :v V]` — a **leaf**'s literal value (a STRING). A name, an operator, a type"
    "  name, a keyword-accessor (`:x`), and the type marker `:-` are **all** `:kind \"symbol\"`"
    "  (the colon is retained in `:-` and in `:x`). Only string literals are `:kind \"string\"`;"
    "  numeric literals `:kind \"number\"` with `:v` the digits as a string."
    "- `[\"@list-id\" :fN \"@child-id\"]` — the N-th ordered child of a list (`:f0` is the head)."
    "  **Vectors are lists too**: `[..]` is a `:kind \"list\"` headed by the symbol `#%brackets`,"
    "  so a param/field vector is `(#%brackets a :- T ...)`."
    "There is no `:param`/`:type`/`:ret`/`:callee`/`:access`/`:field`/`:if`/`:cond`/`:record`"
    "predicate or kind — every form is the uniform list+`:fN` shape headed by its operator"
    "symbol (`if`, `defrecord`, `and`, `:x`, …). References are by identity: a use is a"
    "symbol leaf with the binding's spelling; the engine resolves it (and on rename"
    "materializes a durable `:bound_to` edge), so a rename re-spells ONE binding leaf and"
    "every use follows — you never edit the use sites."]))

;; render one M2 tuple in the R3 keyword form. Bank tuples are 3-vecs of strings
;; ["@m#1" "kind" "list"] or 4-vecs ["RETRACT" id pred obj]/["ASSERT" ...].
(defn render-m2-tuple [t]
  (let [h (first t)]
    (cond
      (#{"RETRACT" "ASSERT"} h)
      (let [[verb id pred obj] t]
        (format "[%s \"%s\" :%s %s]" verb id pred
                (if (str/starts-with? (str obj) "@") (str "\"" obj "\"") (str "\"" obj "\""))))
      :else
      (let [[id pred obj] t]
        (format "[\"%s\" :%s %s]" id pred
                ;; all objects render as strings (node-refs "@m#N" and literals alike);
                ;; the adapter distinguishes a node-ref by the leading @.
                (str "\"" obj "\""))))))

(defn example-block [arm {:keys [id task state m1 m2 note]}]
  (str/join "\n"
   (concat
    [(str "### " id)
     (str "TASK: " task)
     "STATE:" "```" state "```" "ANSWER:" "```"]
    (case arm
      :m1  [m1]
      ;; M1.5 example answer = the changed/added defs only. For from-scratch and
      ;; additive bank items, that is exactly the same single def as M1 (which already
      ;; emits only the new def in the additive E3 — see the bank's m1 value). For the
      ;; whole-program-rewrite M1 cases there are none in the bank (all single-def).
      :m15 [m1]
      :m2  (map render-m2-tuple m2))
    ["```" (str "(" note ")") ""])))

(def slices {"d3" 3 "d6" 6 "d10" 10})

(defn assemble [arm density]
  (let [n (slices density)
        exs (filter #(<= (:slice %) n) bank)
        contract (case arm :m1 m1-contract :m15 m15-contract :m2 m2-contract)
        arm-name (case arm :m1 "M1 (text, whole module)" :m15 "M1.5 (text, changed defs only)" :m2 "M2 (claim-changeset)")
        last-e (last (map #(subs (:id %) 1) exs))]
    (str/join "\n"
     (concat
      [(str "# Authoring bake-off prompt — " arm-name " — density " density " (E1–E" last-e ")")
       "" "## Shared instruction block" shared-block "" contract ""
       (str "## Examples (E1–E" last-e ") — verified [task/state/answer]") ""]
      (map #(example-block arm %) exs)))))

(doseq [arm [:m1 :m15 :m2] density ["d3" "d6" "d10"]]
  (let [content (assemble arm density)
        a (case arm :m1 "M1" :m15 "M1.5" :m2 "M2")
        fname (str OUT "/" a "-" density ".md")]
    (spit fname content)
    (println (str "wrote " fname " (" (count content) " bytes)"))))
