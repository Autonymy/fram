#!/usr/bin/env bb
;; G0 prompt generator — assemble the six frozen prompts (M1/M2 × d3/d6/d10) from
;; the engine-verified bank. Guarantees M1/M2 content parity (same bank, same slice
;; boundaries). Writes to experiments/authoring-bakeoff/prompts/.
(require '[clojure.edn :as edn] '[clojure.string :as str])
(def bank (edn/read-string (slurp "bank.edn")))
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
   ["## Output contract (M1 — text)"
    "Emit beagle source **text**. Express the whole definition you are adding or"
    "changing. The compilation/grading unit is the **module**: when you modify an"
    "existing program, re-emit the file with your change applied — reproduce every"
    "unchanged form verbatim so it keeps compiling, and edit only the form the task"
    "names. (The text modality has no identity edges: a reference is a *spelling*, so a"
    "rename is a spelling change at the definition AND at every use site; miss one and"
    "you get an `unbound` error the gate catches.)"]))

(def m2-contract
  (str/join "\n"
   ["## Output contract (M2 — claim-tuples)"
    "Emit a flat changeset: one tuple per line `[@id attr value]`. A program is a graph"
    "of `(subject predicate object)` claims; you author it by stating these AST facts."
    "The harness applies each tuple as a claim-delta (mint new `@id`s / supersede"
    "existing ones) and **recompile-gates the batch fail-closed** (renders the edited"
    "graph and runs the SAME beagle compile+typecheck as M1; a batch that does not"
    "compile is refused whole, nothing written). Emit **only the delta** — never"
    "re-emit unchanged definitions. Reference existing nodes by `@id` as shown in"
    "current state. To RETRACT a claim (supersede an edge/spelling) write it as"
    "`RETRACT [@id attr value]`; a fresh assertion is just the bare tuple (or"
    "`ASSERT [@id attr value]` for emphasis)."
    ""
    "The AST vocabulary is small and fixed:"
    "- `[id \"kind\" K]` — every node's tag. `K ∈ {list, symbol, string, number, bool, keyword, char, nil}`."
    "- `[id \"v\" V]` — a **leaf**'s literal value. A name, an operator, a type name, a keyword-accessor (`:x`), and the type marker `:-` are **all `kind=symbol`** (the colon is retained in `:-` and in `:x`). Only string literals are `kind=string`; numeric literals `kind=number`."
    "- `[list-id \"fN\" child-id]` — the N-th ordered child of a list (`f0` is the head). **Vectors are lists too**: `[..]` is a `list` headed by the symbol `#%brackets`, so a param/field vector is `(#%brackets a :- T ...)`."
    "There is no `param`/`type`/`ret`/`callee`/`access`/`field`/`if`/`cond`/`record`"
    "predicate or kind — every form is the uniform list+`fN` shape headed by its"
    "operator symbol (`if`, `defrecord`, `and`, `:x`, …). References are by identity:"
    "a use is a symbol leaf with the binding's spelling; the engine resolves it (and"
    "on rename materializes a durable `bound_to` edge), so a rename re-spells ONE"
    "binding leaf and every use follows — you never edit the use sites."]))

(defn m2-line [[a p v]]
  (cond
    (= a "RETRACT") (str "RETRACT [" (nth [a p v] 1) " \"" (nth [a p v] 2) "\" " (let [r (nth [a p v] 3 nil)] r) "]")
    :else (str "[" a " \"" p "\" " (if (str/starts-with? (str v) "@") v (str "\"" v "\"")) "]")))

;; RETRACT/ASSERT tuples are stored as 4-vectors ["RETRACT" id pred obj]; render specially.
(defn render-m2-tuple [t]
  (let [[h & _] t]
    (cond
      (#{"RETRACT" "ASSERT"} h)
      (let [[verb id pred obj] t]
        (str verb " [" id " \"" pred "\" " (if (str/starts-with? (str obj) "@") obj (str "\"" obj "\"")) "]"))
      :else
      (let [[id pred obj] t]
        (str "[" id " \"" pred "\" " (if (str/starts-with? (str obj) "@") obj (str "\"" obj "\"")) "]")))))

(defn example-block [arm {:keys [id task state m1 m2 note]}]
  (str/join "\n"
   (concat
    [(str "### " id)
     (str "TASK: " task)
     (str "STATE:")
     (str "```")
     state
     (str "```")
     (str "ANSWER:")
     "```"]
    (if (= arm :m1)
      [m1]
      (map render-m2-tuple m2))
    ["```"
     (str "(" note ")")
     ""])))

(def slices {"d3" 3 "d6" 6 "d10" 10})

(defn assemble [arm density]
  (let [n (slices density)
        exs (filter #(<= (:slice %) n) bank)
        contract (if (= arm :m1) m1-contract m2-contract)
        arm-name (if (= arm :m1) "M1 (text)" "M2 (claim-tuples)")]
    (str/join "\n"
     (concat
      [(str "# Authoring bake-off prompt — " arm-name " — density " density " (E1–E" (last (map #(subs (:id %) 1) exs)) ")")
       ""
       "## Shared instruction block"
       shared-block
       ""
       contract
       ""
       (str "## Examples (E1–E" (last (map #(subs (:id %) 1) exs)) ") — verified [task/state/answer]")
       ""]
      (map #(example-block arm %) exs)))))

(doseq [arm [:m1 :m2] density ["d3" "d6" "d10"]]
  (let [content (assemble arm density)
        fname (str OUT "/" (if (= arm :m1) "M1" "M2") "-" density ".md")]
    (spit fname content)
    (println (str "wrote " fname " (" (count content) " bytes)"))))
