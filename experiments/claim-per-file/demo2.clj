#!/usr/bin/env bb
;; demo2 — the spike's four load-bearing behaviors, now on the PRINCIPLED store (cpf2).
;; Same four behaviors as demo.clj, proving the HLC G-Set is a behavior-preserving
;; upgrade of the spike (not a semantic change): write+fold, supersede/retract,
;; atomic commit-claim tx, coordinator-free federation merge. Property coverage of the
;; deeper CRDT/HLC/cardinality guarantees lives in prop_test.clj.
(require '[babashka.fs :as fs])
(load-file (str (fs/parent *file*) "/cpf2.clj"))
(require '[cpf2 :as c])

(defn fresh [n] (let [d (str "/tmp/cpf2-demo-" n)] (fs/delete-tree d) (c/ensure-store d) d))
(defn line [s] (println (str "\n=== " s " ===")))

;; --- 1. write + single-valued last-write-wins (HLC order) -------------------
(line "1. write + last-write-wins (single-valued title, HLC-ordered)")
(let [d (fresh "lww") clk (c/make-clock)]
  (c/put-claim clk d {:l "title" :p "cardinality" :r "single"})
  (c/put-claim clk d {:l "@site" :p "title" :r "Old name"})
  (c/put-claim clk d {:l "@site" :p "title" :r "New name"})
  (let [{:keys [state]} (c/load-store clk d)]
    (println "  @site title =>" (get state ["@site" "title"]))
    (assert (= "New name" (get state ["@site" "title"])) "last write must win")))

;; --- 2. supersede + retract via causal edges --------------------------------
(line "2. supersede / retract edges (order-independent kill-set)")
(let [d (fresh "edges") clk (c/make-clock)]
  (c/put-claim clk d {:l "tag" :p "cardinality" :r "single"})
  (let [a (c/put-claim clk d {:l "@x" :p "note" :r "draft"})]
    (c/put-claim clk d {:l "@x" :p "note" :r "revised"})
    (c/put-claim clk d {:l "@x" :p "note" :r "final" :supersedes [a]})
    (let [{:keys [state]} (c/load-store clk d)]
      (println "  @x note =>" (get state ["@x" "note"]))
      (assert (= #{"revised" "final"} (get state ["@x" "note"])) "draft superseded out"))))

;; --- 3. ATOMIC multi-claim tx via commit-claim ------------------------------
(line "3. atomic tx — members invisible until the commit-claim lands")
(let [d (fresh "tx") clk (c/make-clock)]
  (c/put-claim clk d {:l "item" :p "cardinality" :r "single"})
  (c/put-claim clk d {:l "qty"  :p "cardinality" :r "single"})
  (let [m1 (c/stage-member clk d {:l "@order" :p "item" :r "widget"})
        m2 (c/stage-member clk d {:l "@order" :p "qty"  :r "3"})]
    (let [{:keys [state dropped]} (c/load-store clk d)]
      (println "  pre-commit @order =>" (get state ["@order" "item"]) " (pending:" (count dropped) ")")
      (assert (nil? (get state ["@order" "item"])) "staged members invisible pre-commit"))
    (c/put-commit clk d {:members [m1 m2] :by "agent-a"})
    (let [{:keys [state]} (c/load-store clk d)]
      (println "  post-commit @order item =>" (get state ["@order" "item"]) " qty =>" (get state ["@order" "qty"]))
      (assert (= "widget" (get state ["@order" "item"])) "member visible after commit")
      (assert (= "3" (get state ["@order" "qty"])) "all-or-nothing"))))

;; --- 4. federation: two coordinator-less stores merge by union --------------
(line "4. federation — union of files, no coordinator, HLC resolves the conflict")
(let [a (fresh "fed-a") b (fresh "fed-b") m (fresh "fed-merged")
      ;; INDEPENDENT clocks — alice's wall slow, bob's fast (the skew the spike feared)
      ca (c/make-clock (constantly 1000))
      cb (c/make-clock (constantly 5000))]
  (c/put-claim ca a {:l "owner" :p "cardinality" :r "single"})
  (c/put-claim ca a {:l "@doc" :p "owner" :r "alice"})
  (c/put-claim cb b {:l "@doc" :p "owner" :r "bob"})        ; bob's HLC is later
  (c/merge-into! m a) (c/merge-into! m b)
  (let [{:keys [state claims alive]} (c/load-store m)]
    (println "  merged @doc owner =>" (get state ["@doc" "owner"]) "(later HLC wins: bob)")
    (println "  merged claim count =>" (count alive))
    (assert (= "bob" (get state ["@doc" "owner"])) "HLC time-order resolves the conflict deterministically")))

(println "\nALL DEMO2 ASSERTIONS PASSED")
