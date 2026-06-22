#!/usr/bin/env bb
;; prop_test — PROPERTY tests for the principled store (cpf2). Not example behaviors:
;; these witness the CRDT obligations (§2) and the two CORRECTNESS fixes (HLC §1,
;; cardinality merge-precedence §4) named in DESIGN.md §6.
;;
;;   P1  HLC monotonicity under a REGRESSING wall clock
;;   P2  HLC causality across merge (post-merge write > everything merged in)
;;   P3  merge idempotence / commutativity / associativity (CvRDT laws)
;;   P4  convergence under arrival-order shuffle
;;   P5  cardinality order-independence (schema claim first vs last ⇒ same state)
;;   P6  commit atomicity under partial arrival
;;   P7  pack fold-equivalence
;;   R8  fold-equivalence regression vs append-log (the cross-substrate migration gate)
;;
;;   bb prop_test.clj
(require '[babashka.fs :as fs]
         '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.string :as str])
(load-file (str (fs/parent *file*) "/cpf2.clj"))
(require '[cpf2 :as c])

(def pass (atom 0)) (def fail (atom 0))
(defn check [nm ok]
  (if ok (do (swap! pass inc) (println "  [PASS]" nm))
         (do (swap! fail inc) (println "  [FAIL]" nm))))
(defn fresh [n] (let [d (str "/tmp/cpf2-" n "-" (System/nanoTime))] (fs/delete-tree d) (c/ensure-store d) d))

;; =================================================================== P1
(println "\n## P1 — HLC monotonicity under a REGRESSING wall clock")
;; A wall clock that jumps BACKWARDS must not produce a smaller id. The HLC's lc
;; counter (and pt = max) absorbs the regression.
(let [t (atom 100000)
      ;; wall regresses every other call
      walls (atom [100000 100000 99999 99998 100000 99000 100001 100000])
      wall  (fn [] (let [[h & r] @walls] (when r (reset! walls r)) h))
      clk   (c/make-clock wall)
      ids   (vec (repeatedly 8 #(c/hlc-now! clk)))]
  (check "8 ids strictly increasing despite wall going backwards"
         (= ids (sort ids)))
  (check "all ids distinct" (= 8 (count (distinct ids)))))

;; =================================================================== P2
(println "\n## P2 — HLC causality across merge (no-message receive at load)")
(let [a (fresh "p2a") b (fresh "p2b") m (fresh "p2m")
      ;; two replicas with INDEPENDENT clocks; b's wall runs AHEAD
      ca (c/make-clock (constantly 5000))
      cb (c/make-clock (constantly 9000))]
  (c/put-claim ca a {:l "@x" :p "k" :r "from-a"})
  (c/put-claim cb b {:l "@x" :p "k" :r "from-b"})
  (c/merge-into! m a) (c/merge-into! m b)
  ;; a replica that MERGES both, then writes locally, must produce an id > all merged ids
  (let [cm (c/make-clock (constantly 1))           ; its OWN wall is far behind both
        merged-ids (mapv :id (:alive (c/load-store cm m)))   ; load advances cm past all
        next-id (c/hlc-now! cm)]
    (check "post-merge local write > every merged id (clock advanced at load)"
           (every? #(neg? (compare % next-id)) merged-ids))))

;; =================================================================== P3
(println "\n## P3 — merge idempotence / commutativity / associativity (CvRDT laws)")
;; build three independent stores of random claims, then check the fold of various
;; unions is invariant. Union is by file copy; fold is pure over the object set.
(defn rand-claims! [clk dir n]
  (dotimes [i n]
    (c/put-claim clk dir {:l (str "@e" (mod i 7))
                          :p (rand-nth ["note" "kind" "ref"])  ; all multi (no card claim)
                          :r (str "v" (rand-int 1000))})))
(let [A (fresh "p3a") B (fresh "p3b") C (fresh "p3c")]
  (rand-claims! (c/make-clock (constantly 1000)) A 30)
  (rand-claims! (c/make-clock (constantly 2000)) B 30)
  (rand-claims! (c/make-clock (constantly 3000)) C 30)
  (let [u (fn [& dirs] (let [d (fresh (str "p3u" (rand-int 99999)))]
                         (doseq [s dirs] (c/merge-into! d s)) (:state (c/load-store d))))]
    (check "idempotent: fold(A∪A) == fold(A)"        (= (u A) (u A A)))
    (check "commutative: fold(A∪B) == fold(B∪A)"     (= (u A B) (u B A)))
    (check "associative: fold((A∪B)∪C)==fold(A∪(B∪C))"
           (= (u (let [d (fresh "p3ab")] (c/merge-into! d A) (c/merge-into! d B) d) C)
              (u A (let [d (fresh "p3bc")] (c/merge-into! d B) (c/merge-into! d C) d))))))

;; =================================================================== P4
(println "\n## P4 — convergence under arrival-order shuffle")
;; The fold must be invariant to the ORDER files are listed/arrive. We fold the same
;; object set under 20 random shuffles and require one unique state.
(let [d (fresh "p4")
      clk (c/make-clock (constantly 7777))]
  (c/put-claim clk d {:l "title" :p "cardinality" :r "single"})
  (dotimes [i 25] (c/put-claim clk d {:l (str "@n" (mod i 5)) :p "title" :r (str "t" i)}))
  (let [objs (c/read-objects d)
        states (set (repeatedly 20 #(:state (c/fold (shuffle objs)))))]
    (check "fold invariant under 20 shuffles of the object set (1 unique state)"
           (= 1 (count states)))))

;; =================================================================== P5
(println "\n## P5 — cardinality order-independence (the §4 correctness fix)")
;; The spike's named hazard: a LATE-arriving (P "cardinality" "single") could
;; retroactively re-collapse history. Here we build the SAME data, then inject the
;; schema claim FIRST vs LAST in arrival order, and require an identical converged state.
(let [mk (fn [schema-first?]
           (let [d (fresh (str "p5-" (if schema-first? "sf" "sl")))
                 clk (c/make-clock (atom 0))]
             ;; we mint ids in a fixed causal order, but PHYSICALLY place files in two
             ;; different arrival orders by writing the schema claim's FILE early or late.
             ;; Easier + truer to the hazard: mint data first, schema last (so schema has
             ;; the MAX id), vs schema first (MIN id). Either way single? must hold.
             (let [w (c/make-clock (constantly 1000))]
               (if schema-first?
                 (do (c/put-claim w d {:l "owner" :p "cardinality" :r "single"})
                     (c/put-claim w d {:l "@doc" :p "owner" :r "alice"})
                     (c/put-claim w d {:l "@doc" :p "owner" :r "bob"}))
                 (do (c/put-claim w d {:l "@doc" :p "owner" :r "alice"})
                     (c/put-claim w d {:l "@doc" :p "owner" :r "bob"})
                     (c/put-claim w d {:l "owner" :p "cardinality" :r "single"}))))
             (:state (c/load-store d))))
      s-first (mk true)
      s-last  (mk false)]
  (check "schema-first state == schema-last state (no retroactive re-collapse hazard)"
         (= s-first s-last))
  (check "single? selected ⇒ exactly one owner value (max-HLC: bob)"
         (= "bob" (get s-last ["@doc" "owner"]))))

;; Stronger §4 witness: SHUFFLE the object set (incl. the schema claim) and require a
;; single converged state, no matter where the schema claim lands in the fold order.
(let [d (fresh "p5-shuf")
      w (c/make-clock (constantly 2000))]
  (c/put-claim w d {:l "@doc" :p "owner" :r "alice"})
  (c/put-claim w d {:l "@doc" :p "owner" :r "carol"})
  (c/put-claim w d {:l "owner" :p "cardinality" :r "single"})
  (let [objs (c/read-objects d)
        states (set (repeatedly 30 #(:state (c/fold (shuffle objs)))))]
    (check "fold invariant under schema-claim position shuffle (1 state, carol wins)"
           (and (= 1 (count states))
                (= "carol" (get (first states) ["@doc" "owner"]))))))

;; =================================================================== P6
(println "\n## P6 — commit atomicity under partial arrival")
(let [d (fresh "p6") clk (c/make-clock (constantly 3000))]
  (c/put-claim clk d {:l "item" :p "cardinality" :r "single"})
  (c/put-claim clk d {:l "qty"  :p "cardinality" :r "single"})
  (let [m1 (c/stage-member clk d {:l "@o" :p "item" :r "widget"})
        m2 (c/stage-member clk d {:l "@o" :p "qty"  :r "3"})]
    ;; members present, commit ABSENT ⇒ nothing visible
    (check "members-without-commit ⇒ empty visible (pending invisible)"
           (and (nil? (get (:state (c/load-store d)) ["@o" "item"]))
                (= 2 (count (:dropped (c/load-store d))))))
    ;; land the commit ⇒ both members appear together
    (c/put-commit clk d {:members [m1 m2] :by "agent"})
    (let [{:keys [state dropped]} (c/load-store d)]
      (check "after commit ⇒ all members visible, none dropped"
             (and (= "widget" (get state ["@o" "item"]))
                  (= "3" (get state ["@o" "qty"]))
                  (zero? (count dropped)))))))
;; commit-without-members (referencing absent ids) ⇒ contributes nothing
(let [d (fresh "p6b") clk (c/make-clock (constantly 3000))]
  (c/put-commit clk d {:members ["deadbeef-0000-deadbeef0000"] :by "agent"})
  (check "commit referencing absent member ids ⇒ empty state"
         (empty? (:state (c/load-store d)))))

;; =================================================================== P7
(println "\n## P7 — pack fold-equivalence (the §5 storage-tiering invariant)")
(let [d (fresh "p7") clk (c/make-clock (atom 0))]
  (let [w (c/make-clock (constantly 4000))]
    (c/put-claim w d {:l "title" :p "cardinality" :r "single"})
    (dotimes [i 40] (c/put-claim w d {:l (str "@p" (mod i 6)) :p "title" :r (str "t" i)})))
  (let [before (:state (c/load-store d))
        ;; pack everything up to a frontier past all ids
        frontier "ffffffffffff-ffff-ffffffffffff"
        _ (c/pack! d frontier)
        loose-remaining (count (filter #(and (str/ends-with? (.getName %) ".edn")
                                             (not (str/starts-with? (.getName %) "pack-")))
                                       (.listFiles (io/file d))))
        after (:state (c/load-store d))]
    (check "pack collapses loose objects (0 loose .edn remain)" (zero? loose-remaining))
    (check "fold(packed) == fold(loose) — pure re-encoding" (= before after))))

;; =================================================================== R8
(println "\n## R8 — fold-equivalence regression vs append-log (migration gate)")
;; Mirror the spike's 900/900 equivalence: the SAME claim sequence applied to a naive
;; append-log fold and to cpf2's fold must yield IDENTICAL state. This is the gate the
;; dual-write phase keeps green.
(defn log-fold
  "Reference append-log fold: assoc by [l p], single iff a (p cardinality single) seen
   (single overwrites, multi accumulates) — the same semantics as the engine kernel."
  [claims]
  (let [single (into #{} (comp (filter #(= "cardinality" (:p %)))
                               (filter #(= "single" (:r %)))
                               (map :l)) claims)]
    (reduce (fn [st c]
              (let [k [(:l c) (:p c)]]
                (if (single (:p c))
                  (assoc st k (:r c))
                  (update st k (fnil conj #{}) (:r c)))))
            {} claims)))
(let [d (fresh "r8") clk (c/make-clock (atom 0))
      w (c/make-clock (constantly 6000))
      ;; deterministic mixed sequence: schema claims + single + multi preds
      seq* (concat [{:l "title" :p "cardinality" :r "single"}
                    {:l "owner" :p "cardinality" :r "single"}]
                   (for [i (range 300)]
                     {:l (str "@e" (mod i 40))
                      :p (rand-nth ["title" "owner" "note" "kind"])
                      :r (str "v" i)}))
      seq* (vec seq*)]
  ;; write all to cpf2 in order (HLC ids monotone ⇒ same relative order as the log)
  (doseq [cl seq*] (c/put-claim w d cl))
  (let [log-state (log-fold seq*)
        cpf-state (:state (c/load-store d))]
    (check (str "append-log fold == cpf2 fold (" (count log-state) "/" (count cpf-state) " keys)")
           (= log-state cpf-state))))

;; =================================================================== summary
(println (str "\ncpf2 properties: " @pass " / " (+ @pass @fail) " PASS"))
(when (pos? @fail) (System/exit 1))
