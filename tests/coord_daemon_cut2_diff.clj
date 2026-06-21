;; Cut-2 differential: original daemon do-assert/do-retract/warm orchestration (user ns)
;; vs the Beagle fram.coord-daemon do-assert!/do-retract!/warm!. Run: bb -cp out /tmp/cut2_verify.clj (from fram-lease)
(require '[fram.coord-daemon :as cd] '[fram.cnf-coord :as cc] '[fram.cnf :as c]
         '[fram.schema :as s] '[fram.kernel :as ck] '[clojure.edn :as edn] '[clojure.string :as str])
(load-file "cnf_coord_daemon.clj")          ; original user/* (tail case -> nil, NO boot)
(def UCO (resolve 'user/co))
(defn ucall [nm & a] (apply (deref (resolve (symbol "user" nm))) a))

(def results (atom []))
(defn chk [label ok] (swap! results conj [label ok]) (println (if ok "  [PASS]" "  [FAIL]") label))

;; ---- boot twin coordinators (same fresh state) -----------------------------
(doseq [f ["/tmp/c2o-v2.log" "/tmp/c2o-flat.log" "/tmp/c2b-v2.log" "/tmp/c2b-flat.log"]] (spit f ""))
;; ORIGINAL: user/boot! sets user/co + flat-log + cache + index
(ucall "boot!" "/tmp/c2o-v2.log" "/tmp/c2o-flat.log")
;; BEAGLE: set the atoms by hand (boot! is cut 3, not yet ported)
(reset! cd/co (cc/new-coord! "/tmp/c2b-v2.log"))
(reset! cd/flat-log "/tmp/c2b-flat.log")
(reset! cd/cache {:index nil :version -1})
(cd/reset-refers-state!)
(cd/index!)
;; register the SAME domain preds on both coordinators
(doseq [co [@@UCO @cd/co]]
  (cc/register-pred! co "owner" "single" "literal")
  (cc/register-pred! co "title" "single" "literal")
  (cc/register-pred! co "part_of" "single" "ref")
  (cc/register-pred! co "kind" "multi" "literal"))

;; ---- run the SAME op sequence through both; compare return values -----------
;; (assert; assert-same=idempotent? single supersede; multi; link; retract)
(def ops [[:a "@T1" "owner" "alice"]
          [:a "@T1" "title" "Hello"]
          [:a "@T1" "owner" "carol"]      ; single-valued supersede (alice -> carol)
          [:a "@N1" "kind" "sym"]
          [:a "@N1" "kind" "kw"]          ; multi-valued (both live)
          [:l "@T2" "part_of" "@T1"]
          [:r "@T1" "title" "Hello"]])    ; retract
;; apply to ORIG (base = current version per op, so single-valued supersede/retract pass OCC)
(def orig-rets
  (mapv (fn [[op te p r]]
          (let [base (cc/current-seq @@UCO)]
            (if (= op :r) (ucall "do-retract" te p r base)
                (ucall "do-assert" te p r base)))) ops))
;; apply to BEAGLE
(def bgl-rets
  (mapv (fn [[op te p r]]
          (let [base (cd/cur-seq)]
            (if (= op :r) (cd/do-retract! te p r base)
                (cd/do-assert! te p r base)))) ops))
;; :ok seqs should match op-by-op (same cc/commit! underneath, same order)
(chk "do-assert!/do-retract! :ok seqs match orig op-by-op"
     (= (mapv #(:ok %) orig-rets) (mapv #(:ok %) bgl-rets)))

;; ---- store live-view identical (the ground truth) --------------------------
(chk "store live-triples identical (orig == beagle)"
     (= (cc/live-triples (:store @@UCO)) (cc/live-triples (:store @cd/co))))

;; ---- WARM-CHECK INVARIANT: incremental cache == fresh rebuild --------------
;; (the apply-commit-delta! correctness gate — the e668dfe divergence bug class)
(let [w (cd/warm!)
      fresh (cd/reified->claims @cd/co)
      fidx (cd/idx-build fresh)]
  (chk "warm :idx :triples == fresh (incremental==rebuild)" (= (:triples (:idx w)) (:triples fidx)))
  (chk "warm :idx :by-pr == fresh" (= (:by-pr (:idx w)) (:by-pr fidx)))
  (chk "warm :idx :by-lp == fresh" (= (:by-lp (:idx w)) (:by-lp fidx)))
  (chk "warm :claims == set(fresh)" (= (:claims w) (set fresh))))
;; and beagle warm == orig warm
(chk "beagle warm :claims == orig warm :claims"
     (= (:claims (cd/warm!)) (:claims (ucall "warm!"))))
(chk "supersede applied: owner=carol only (single live)"
     (= #{["@T1" "owner" "carol"]}
        (set (filter #(and (= "@T1" (nth % 0)) (= "owner" (nth % 1))) (:triples (:idx (cd/warm!)))))))
(chk "multi-valued both live: kind sym+kw"
     (= #{["@N1" "kind" "sym"] ["@N1" "kind" "kw"]}
        (set (filter #(and (= "@N1" (nth % 0)) (= "kind" (nth % 1))) (:triples (:idx (cd/warm!)))))))
(chk "retract applied: title gone"
     (empty? (filter #(= "title" (nth % 1)) (:triples (:idx (cd/warm!))))))

;; ---- flat-log: (op l p r) tuples identical (ignore :ts) --------------------
(defn flat-tuples [path]
  (->> (str/split-lines (slurp path)) (remove str/blank?)
       (map edn/read-string) (mapv #(select-keys % [:op :l :p :r]))))
(chk "flat-log (op l p r) tuples identical" (= (flat-tuples "/tmp/c2o-flat.log") (flat-tuples "/tmp/c2b-flat.log")))

;; ---- dirty-modules: mark-dirty! parity (AST names only) --------------------
(chk "dirty-modules parity (orig == beagle)"
     (= @@(resolve 'user/dirty-modules) @cd/dirty-modules))
;; reserve-name-ints! returns a disjoint range
(cd/seed-name-seq! (:store @cd/co))
(let [r1 (cd/reserve-name-ints! 3) r2 (cd/reserve-name-ints! 2)]
  (chk "reserve-name-ints! disjoint consecutive ranges"
       (and (= 3 (count r1)) (= 2 (count r2)) (empty? (clojure.set/intersection (set r1) (set r2)))
            (= (inc (last r1)) (first r2)))))

(let [fails (remove second @results)]
  (println (format "\n=== cut2 differential: %d/%d PASS ===" (- (count @results) (count fails)) (count @results)))
  (when (seq fails) (println "FAILURES:" (mapv first fails)))
  (System/exit (if (seq fails) 1 0)))
