;; Differential verification: original daemon pure fns (user ns, loaded w/o boot)
;; vs the Beagle fram.coord-daemon module. Run: bb -cp out /tmp/cut1_verify.clj  (from fram-lease)
(require '[fram.cnf :as c] '[fram.schema :as s] '[fram.kernel :as ck] '[fram.query :as q]
         '[fram.coord-daemon :as cd] '[clojure.set])
(load-file "cnf_coord_daemon.clj")   ; defines user/* fns; tail case -> nil (NO boot)

(def O #(deref (resolve (symbol "user" %))))   ; fetch original fn var-value by name
(defn orig [nm & args] (apply (O nm) args))

;; ---- build a realistic store -----------------------------------------------
(def st (c/new-store))
(def tx (c/begin-tx! st "test"))
(s/setup! st tx)
(s/def-predicate! st "title" "single" "literal" tx)
(s/def-predicate! st "owner" "single" "literal" tx)
(s/def-predicate! st "part_of" "single" "ref" tx)
(s/def-predicate! st "kind" "multi" "literal" tx)
(s/def-predicate! st "refers_to" "multi" "ref" tx)        ; resolve-pred -> must be FILTERED
(defn ent! [nm] (let [e (c/entity! st)] (s/name! st e nm tx) e))
(def t1 (ent! "@T1")) (def t2 (ent! "@T2")) (def n1 (ent! "@mod#5")) (def n2 (ent! "@mod#9"))
(s/assert! st t1 "title" "Hello" tx)
(s/assert! st t1 "owner" "alice" tx)
(s/assert! st t2 "owner" "bob" tx)
(s/link!   st t2 "part_of" t1 tx)
(s/assert! st n1 "kind" "sym" tx)
(s/link!   st n1 "refers_to" t1 tx)                        ; filtered
;; a supersede on a single-valued cell (owner alice -> carol): exercises live-view filtering
(s/assert! st t1 "owner" "carol" tx)
(def co0 {:store st :log nil :lock (Object.)})

(def results (atom []))
(defn chk [label ok] (swap! results conj [label ok]) (println (if ok "  [PASS]" "  [FAIL]") label))

;; ---- reified->claims (set-eq; refers_to filtered) --------------------------
(def oc (set (orig "reified->claims" co0)))
(def bc (set (cd/reified->claims co0)))
(chk "reified->claims set-eq" (= oc bc))
(chk "refers_to filtered from read view" (not (some #(= "refers_to" (:p %)) bc)))
(chk "owner superseded: only carol live"
     (= #{"carol"} (set (keep #(when (= "owner" (:p %)) (:r %)) (filter #(= "@T1" (:l %)) bc)))))

;; ---- idx-build (field-wise; record vs map -> compare fields) ----------------
(def claims (vec bc))
(def oi (orig "idx-build" claims))
(def bi (cd/idx-build claims))
(chk "idx :triples eq" (= (:triples oi) (:triples bi)))
(chk "idx :by-pr eq"   (= (:by-pr oi) (:by-pr bi)))
(chk "idx :by-lp eq"   (= (:by-lp oi) (:by-lp bi)))

;; ---- idx-add / idx-del round-trip == build minus/plus ----------------------
(def some-tr (first (:triples bi)))
(chk "idx-del then idx-add == original (field-wise)"
     (let [d (cd/idx-del bi some-tr) a (cd/idx-add d some-tr)]
       (and (= (:triples a) (:triples bi)) (= (:by-pr a) (:by-pr bi)) (= (:by-lp a) (:by-lp bi)))))

;; ---- idx-run ≡ q/run (oracle) on a simple bound query ----------------------
(def qy {:rules [{:head {:rel "who" :args [{:var "t"} {:var "o"}]}
                  :body [{:rel "triple" :args [{:var "t"} "owner" {:var "o"}]}]}]})
(chk "idx-run ≡ q/run (oracle)"
     (= (set (:ok (q/run claims qy))) (set (:ok (cd/idx-run bi qy)))))
;; a 2-literal join (ground pred, var subject + object chained)
(def qy2 {:rules [{:head {:rel "j" :args [{:var "a"}]}
                   :body [{:rel "triple" :args [{:var "a"} "part_of" {:var "b"}]}
                          {:rel "triple" :args [{:var "b"} "title" {:var "ti"}]}]}]})
(chk "idx-run ≡ q/run (join)"
     (= (set (:ok (q/run claims qy2))) (set (:ok (cd/idx-run bi qy2)))))
(chk "simple-query? agrees" (and (= (orig "simple-query?" qy) (cd/simple-query? qy))
                                 (cd/simple-query? qy) (cd/simple-query? qy2)))

;; ---- helpers: differential on edge cases -----------------------------------
(chk "ref-shape?" (= (mapv #(orig "ref-shape?" %) ["@T1" "@" "@a b" "x" "@mod#5"])
                     (mapv cd/ref-shape? ["@T1" "@" "@a b" "x" "@mod#5"])))
(chk "kind-of" (= (mapv #(orig "kind-of" %) ["@T1" "hello" "@" "@a b"])
                  (mapv cd/kind-of ["@T1" "hello" "@" "@a b"])))
(chk "module-of-name" (= (mapv #(orig "module-of-name" %) ["@mod#5" "x" "@T1" "@a.b#3"])
                         (mapv cd/module-of-name ["@mod#5" "x" "@T1" "@a.b#3"])))
;; ast-pred-str?: original returns the set-element (truthy) on a hit; Beagle returns
;; a proper Bool (its :- Bool annotation). Used ONLY in boolean context (daemon 994/1004),
;; so the correct equivalence is TRUTHINESS, not exact value.
(let [cs ["kind" "v" "f3~5" "f1.2~PENDING" "seg2" "comment1" "title" "owner"]]
  (chk "ast-pred-str? (truthiness)" (= (mapv #(boolean (orig "ast-pred-str?" %)) cs)
                                       (mapv #(boolean (cd/ast-pred-str? %)) cs))))
(chk "allocate-positions"
     (= (orig "allocate-positions" [["@a" "f1~PENDING" "@mod#7"] ["@a" "kind" "sym"] ["@b" "f2.3~PENDING" "@z#42"]])
        (cd/allocate-positions  [["@a" "f1~PENDING" "@mod#7"] ["@a" "kind" "sym"] ["@b" "f2.3~PENDING" "@z#42"]])))
(chk "next-module-int" (= (orig "next-module-int" st "mod") (cd/next-module-int st "mod")))
(chk "global-max-name-int" (= (orig "global-max-name-int" st) (cd/global-max-name-int st)))
(chk "module-node-ids" (= (orig "module-node-ids" st #{"mod"}) (cd/module-node-ids st #{"mod"})))
(chk "lp-live-triples (group reconcile)"
     (= (orig "lp-live-triples" co0 "@T1" "owner") (cd/lp-live-triples co0 "@T1" "owner")))

(let [fails (remove second @results)]
  (println (format "\n=== cut1 differential: %d/%d PASS ===" (- (count @results) (count fails)) (count @results)))
  (when (seq fails) (println "FAILURES:" (mapv first fails)))
  (System/exit (if (seq fails) 1 0)))
