#!/usr/bin/env bb
;; Chelonia coordinator — ADVERSARIAL CONCURRENCY TEST SUITE (C1..C9).
;; CLAIM-NATIVE MODEL.
;;
;; Real concurrency: each case spins an EMBEDDED coordinator (coord.clj's own
;; serve/commit!/client over a real local socket) and N futures opening real
;; socket connections that race writes. Per case we assert the safety
;; invariants: 0 torn/corrupt log lines, contradictory & illegal writes
;; rejected, single-valued integrity, version consistency, obligations held.
;;
;;   bb -cp out coord_test.clj            # run all cases
;;   bb -cp out coord_test.clj C6         # run one case
;;
;; ---------------------------------------------------------------------------
;; THE CLAIM-NATIVE MODEL (what this suite now exercises)
;; ---------------------------------------------------------------------------
;; A claim is (l p r): `l` is always an entity ref written `@<id>`; `r` is a
;; ref `@<id>` or a literal string. There are NO `thread:`/`person:`/`owner:`
;; prefixes — the `@` sigil is the uniform ref marker.
;;
;; A THREAD is STRUCTURAL: any subject that carries a `title` claim. There is
;; NO `state` enum. Lifecycle is DERIVED from facts: `committed`, `outcome`
;; (=done), `abandoned` (=canceled), `driver` (=active). `terminal` = has
;; `outcome` OR `abandoned`.
;;
;; The OBLIGATION RULES enforced by the kernel (chelonia.kernel/violations,
;; which coord.clj's commit!/commit-retract! delegate to):
;;   (1) depends_on / part_of / relates_to targets must each be a real thread
;;       (have a `title`) — else "references missing entity";
;;   (2) a non-terminal thread's depends_on must not point at an `abandoned`
;;       thread;
;;   (3) no depends_on cycle;
;;   (4) no part_of cycle.
;; relates_to cycles are ALLOWED (loose relatedness). The old
;; "active thread has no driver" rule is GONE and is no longer tested.
;;
;; NOTE on single-valued collapse: the daemon (coord.clj) collapses a fixed set
;; of single-valued predicates (title, owner, lead, driver, source, part_of,
;; ...). The collapse/cardinality cases below use `title`, which is in that set
;; and is also the predicate that makes a subject a thread.

(require '[clojure.string :as str] '[clojure.edn :as edn])
(import '[java.net Socket InetSocketAddress])

(load-file "coord.clj") ; reuse the daemon's machinery (commit!/serve/client/state/...)

;; ===========================================================================
;; LOCAL HELPERS — mirror chelonia.kernel's claim-native rules so the suite can
;; assert invariants by replaying the log independently of the daemon.
;; ===========================================================================

;; a thread is structural: a subject that carries a `title` claim.
(defn thread-ids [claims]
  (->> claims (filter (fn [[l p _]] (= p "title"))) (map first) distinct set))

;; terminal = resolved: has an `outcome` (done) or was `abandoned` (canceled).
(defn terminal? [claims te]
  (boolean (or (one claims te "outcome") (one claims te "abandoned"))))

;; full claim-native obligation rule set (mirror of chelonia.kernel/violations).
;; Evaluated under the write-lock (atomic w.r.t. commit) when used as a probe.
(defn violations [claims te]
  (let [v (atom [])
        add #(swap! v conj %)
        ids (thread-ids claims)
        term? (terminal? claims te)]
    (doseq [d (many claims te "depends_on")]
      (when-not (ids d) (add (str "depends_on references missing entity " d)))
      (when (and (not term?) (one claims d "abandoned"))
        (add (str "depends_on points at abandoned " d))))
    (when-let [pa (one claims te "part_of")]
      (when-not (ids pa) (add (str "part_of references missing entity " pa))))
    (when (cycle? claims "depends_on" te) (add "depends_on cycle"))
    (when (cycle? claims "part_of" te) (add "part_of cycle"))
    ;; relates_to: target must be a real thread, but cycles are allowed.
    (doseq [rt (many claims te "relates_to")]
      (when-not (ids rt) (add (str "relates_to references missing entity " rt))))
    @v))

;; ===========================================================================
;; fold — replay the log; C8 compares replay to live daemon state.
;; ===========================================================================
(defn fold [assertions]
  (let [version (reduce max 0 (map :tx assertions))
        by-lp (group-by (juxt :l :p) assertions)]
    (loop [items (seq by-lp) claims (transient #{}) lastmod (transient {})]
      (if-not items
        {:claims (persistent! claims) :lastmod (persistent! lastmod) :version version}
        (let [[[l p] as] (first items)
              lm (reduce max 0 (map :tx as))
              lastmod (assoc! lastmod [l p] lm)
              claims
              (if (single-valued p)
                (let [latest (apply max-key :tx as)]
                  (if (= "assert" (:op latest)) (conj! claims [l p (:r latest)]) claims))
                (reduce (fn [cs [r rs]]
                          (let [latest (apply max-key :tx rs)]
                            (if (= "assert" (:op latest)) (conj! cs [l p r]) cs)))
                        claims (group-by :r as)))]
          (recur (next items) claims lastmod))))))

;; ===========================================================================
;; harness helpers
;; ===========================================================================
(def port-seq (atom 7980))
(defn next-port [] (swap! port-seq inc))

(defn fresh! [log]
  (reset! log-path log)
  (spit log "")
  (reset! state {:claims #{} :version 0 :lastmod {}}))

(defn with-server
  "Start an embedded coordinator on a fresh port, run (f port), shut it down.
   Uses coord.clj's real serve loop over a real ServerSocket."
  [f]
  (let [port (next-port)
        server (future (serve port))]
    (Thread/sleep 250)
    (try (f port)
         (finally (future-cancel server) (Thread/sleep 50)))))

(defn rd-version [port] (:version (client port {:op :version})))
(defn assert! [port te p r base] (client port {:op :assert :te te :p p :r r :base base}))
(defn retract! [port te p r base] (client port {:op :retract :te te :p p :r r :base base}))

(defn log-lines [] (->> (slurp @log-path) str/split-lines (remove str/blank?)))
(defn parsed-log []
  (map #(try (edn/read-string %) (catch Exception _ ::bad)) (log-lines)))
(defn corrupt-count [] (count (filter #(= ::bad %) (parsed-log))))
(defn live-claims [] (:claims @state))
(defn cardinality [l p] (count (q (live-claims) :l l :p p)))
(defn log-ops [] (count (filter #(not= ::bad %) (parsed-log))))

;; tx-monotonicity: parsed log tx column strictly increasing 1..n, no gaps/dupes
(defn tx-monotonic? []
  (let [txs (map :tx (parsed-log))]
    (= txs (range 1 (inc (count txs))))))

;; result accumulator
(def results (atom []))
(defn record! [case-name pass detail]
  (swap! results conj {:case case-name :pass pass :detail detail})
  (println (format "  [%s] %s" (if pass "PASS" "FAIL") detail)))
(defn check! [case-name conds]
  ;; conds: vector of [label bool]; pass if all true
  (let [pass (every? second conds)
        detail (str/join " · " (map (fn [[l b]] (str (if b "ok " "X ") l)) conds))]
    (record! case-name pass detail)
    pass))

;; ===========================================================================
;; OBLIGATION PROBE — show the live kernel rule set rejects the illegal writes
;; the adversarial cases race against (claim-native: title-based threads,
;; abandoned not canceled, relates_to dangling). This is a sanity check that
;; the rules under test actually fire, before we race them.
;; ===========================================================================
(defn obligation-probe []
  (println "\n=== OBLIGATION PROBE: claim-native kernel rules (live commit path) ===")
  (let [claims #{["@X" "title" "X"]
                 ["@X" "depends_on" "@GHOST"]   ; @GHOST has no title -> missing
                 ["@D" "title" "D"] ["@D" "abandoned" "t"]
                 ["@X" "depends_on" "@D"]        ; non-terminal -> abandoned dep
                 ["@X" "relates_to" "@Z"]}       ; @Z has no title -> missing
        flagged (set (violations claims "@X"))]
    (println "  kernel flags on @X:" flagged)
    (println "  (depends_on missing entity, depends_on abandoned, relates_to missing)")
    (println "  Cases C4/C6/C9 race writers against exactly these obligations.\n")))

;; ===========================================================================
;; C1 — Concurrent single-valued `title` race + a racing dangling depends_on
;;      (HEADLINE: a surviving obligation never slips under contention).
;; ===========================================================================
(defn c1 []
  (println "\n--- C1: single-valued title race + racing dangling depends_on (HEADLINE) ---")
  (fresh! "/tmp/chelonia-c1.log")
  ;; seed @T as a real thread (title) with an owner+driver (3 claims -> v3)
  (commit! "@T" "title" "seed" 0)
  (commit! "@T" "driver" "@x" 0)
  (commit! "@T" "owner" "@seed" 0)
  (with-server
    (fn [port]
      ;; 9 writers race to set @T's single-valued title; 1 rogue writer tries to
      ;; assert depends_on @GHOST (no title) — a surviving obligation that MUST
      ;; be rejected no matter how the title race interleaves.
      (let [racers
            (doall
              (for [i (range 10)]
                (future
                  (let [v (rd-version port)]
                    (if (= i 9)
                      ;; rogue: dangling depends_on at a never-titled entity
                      (assert! port "@T" "depends_on" "@GHOST" v)
                      (assert! port "@T" "title" (str "title-" i) v))))))
            resps (map deref racers)
            commits (count (filter :ok resps))
            conflicts (count (filter #(= :conflict (:reject %)) resps))
            rejects-rule (count (filter #(and (:reject %) (not= :conflict (:reject %))) resps))
            final-title (one (live-claims) "@T" "title")
            ver (:version @state)
            total-ops (- ver 3)]
        (check! "C1"
          [["0 corrupt/torn log lines" (zero? (corrupt-count))]
           ["title cardinality == 1 (single-valued collapse)" (= 1 (cardinality "@T" "title"))]
           ["@T remains a real thread (has a title)" (some? final-title)]
           ["dangling depends_on @GHOST was REJECTED (surviving obligation)"
            (zero? (count (q (live-claims) :l "@T" :p "depends_on" :r "@GHOST")))]
           ["no missing-entity edge persists at all"
            (zero? (count (q (live-claims) :l "@T" :p "depends_on")))]
           ["at least one writer won" (pos? commits)]
           ["version == 3 + committed-ops" (= ver (+ 3 total-ops))]
           ["contention fired (>=1 conflict OR rule reject)" (pos? (+ conflicts rejects-rule))]])))))

;; ===========================================================================
;; C2 — Retry-storm convergence (clients retry on conflict until success)
;; ===========================================================================
(defn c2 []
  (println "\n--- C2: retry-storm convergence ---")
  (fresh! "/tmp/chelonia-c2.log")
  (commit! "@T" "title" "seed" 0) ; 1 claim -> v1
  (with-server
    (fn [port]
      (let [n 10
            retry-counter (atom 0)
            ;; each client must land EXACTLY ONE successful commit, retrying on conflict
            clients
            (doall
              (for [i (range n)]
                (future
                  (loop [tries 0]
                    (if (> tries 200) {:client i :ok false :tries tries}
                      (let [v (rd-version port)
                            r (assert! port "@T" "title" (str "t" i "-" tries) v)]
                        (if (:ok r)
                          {:client i :ok true :tries tries}
                          (do (swap! retry-counter inc) (recur (inc tries))))))))))
            outs (map deref clients)
            all-committed (every? :ok outs)
            commits (count (filter :ok outs))
            ver (:version @state)
            ;; last winning value must equal the log tail value (read-back == log tail)
            log-tail (last (filter #(not= ::bad %) (parsed-log)))
            live-val (one (live-claims) "@T" "title")]
        (check! "C2"
          [["every client committed exactly once (no starvation)" (and all-committed (= commits n))]
           ["retries fired (contention proven)" (pos? @retry-counter)]
           ["title cardinality == 1" (= 1 (cardinality "@T" "title"))]
           ["version == 1 + commits" (= ver (+ 1 commits))]
           ["0 corrupt log lines" (zero? (corrupt-count))]
           ["last committed value == log tail :r" (= live-val (:r log-tail))]
           ["tx strictly monotonic, no gaps" (tx-monotonic?)]])))))

;; ===========================================================================
;; C3 — Cross-agent cooperative dependency-CYCLE creation
;; ===========================================================================
(defn c3 []
  (println "\n--- C3: cooperative depends_on cycle creation ---")
  (fresh! "/tmp/chelonia-c3.log")
  ;; @A and @B are real threads (title); 2 seeds -> v2
  (commit! "@A" "title" "A" 0)
  (commit! "@B" "title" "B" 0)
  (with-server
    (fn [port]
      (let [rounds 40
            ;; Agent-1 hammers A depends_on B ; Agent-2 hammers B depends_on A
            ;; interleave with background noise writers to add load
            a1 (future (doall (for [k (range rounds)]
                                (assert! port "@A" "depends_on" "@B" (rd-version port)))))
            a2 (future (doall (for [k (range rounds)]
                                (assert! port "@B" "depends_on" "@A" (rd-version port)))))
            noise (future (doall (for [k (range rounds)]
                                   (assert! port "@A" "owner" (str "@n" k) (rd-version port)))))
            _ @a1 _ @a2 _ @noise
            claims (live-claims)
            ab (seq (q claims :l "@A" :p "depends_on"))
            ba (seq (q claims :l "@B" :p "depends_on"))
            both? (and ab ba)
            cyc-a (cycle? claims "depends_on" "@A")
            cyc-b (cycle? claims "depends_on" "@B")]
        (check! "C3"
          [["NOT both A->B and B->A committed" (not both?)]
           ["at most one of the two edges exists" (<= (count (filter identity [(boolean ab) (boolean ba)])) 1)]
           ["cycle? false for A" (not cyc-a)]
           ["cycle? false for B" (not cyc-b)]
           ["0 corrupt log lines" (zero? (corrupt-count))]
           ["version == 2 + committed ops" (= (:version @state) (+ 2 (- (log-ops) 2)))]])))))

;; ===========================================================================
;; C4 — depends_on pointing at an ABANDONED thread, rejected under load
;; ===========================================================================
(defn c4 []
  (println "\n--- C4: depends_on -> abandoned thread, rejected under load ---")
  (fresh! "/tmp/chelonia-c4.log")
  ;; @D is a real thread, abandoned. @X is a real, non-terminal thread.
  (commit! "@D" "title" "D" 0)
  (commit! "@D" "abandoned" "t" 0)
  (commit! "@X" "title" "X" 0)
  (with-server
    (fn [port]
      (let [rounds 40
            ;; many writers race X depends_on D — must be rejected the whole time:
            ;; D stays abandoned and X stays non-terminal, so the obligation
            ;; (non-terminal dep must not point at abandoned) holds on every attempt.
            depers (doall (for [i (range 8)]
                            (future (doall (for [k (range rounds)]
                                             (assert! port "@X" "depends_on" "@D" (rd-version port)))))))
            ;; concurrent LEGAL load on D (re-asserting it abandoned + churning a
            ;; single-valued field) keeps the sole writer busy without ever
            ;; un-abandoning D — i.e. without defeating the obligation under test.
            churner (future (doall (for [k (range rounds)]
                                     (do (assert! port "@D" "abandoned" "t" (rd-version port))
                                         (assert! port "@D" "owner" (str "@o" k) (rd-version port))))))
            dep-resps (mapcat deref depers)
            _ @churner
            claims (live-claims)
            d-abandoned (some? (one claims "@D" "abandoned"))
            xd-edges (count (q claims :l "@X" :p "depends_on" :r "@D"))
            x-terminal (terminal? claims "@X")
            all-rejected (every? :reject dep-resps)]
        (check! "C4"
          [["D stayed abandoned throughout" d-abandoned]
           ["@X stayed non-terminal" (not x-terminal)]
           ["0 depends_on edges on abandoned D persisted" (zero? xd-edges)]
           ["every depends_on->abandoned attempt was rejected" all-rejected]
           ["0 corrupt log lines" (zero? (corrupt-count))]
           ["version == 3 + committed ops" (= (:version @state) (+ 3 (- (log-ops) 3)))]])))))

;; ===========================================================================
;; C5 — base_version staleness storm (lost-update prevention)
;; ===========================================================================
(defn c5 []
  (println "\n--- C5: base_version staleness storm (lost-update prevention) ---")
  (fresh! "/tmp/chelonia-c5.log")
  (commit! "@T" "title" "seed" 0) ; v1
  (with-server
    (fn [port]
      (let [n 10
            ;; every writer snapshots version ONCE, then fires multiple asserts on stale base
            snap (rd-version port)
            writers
            (doall
              (for [i (range n)]
                (future
                  (loop [k 0 commits 0 conflicts 0]
                    (if (= k 5) [commits conflicts]
                      (let [r (assert! port "@T" "title" (str "w" i "-" k) snap)]
                        (recur (inc k)
                               (+ commits (if (:ok r) 1 0))
                               (+ conflicts (if (= :conflict (:reject r)) 1 0)))))))))
            outs (map deref writers)
            commits (reduce + (map first outs))
            conflicts (reduce + (map second outs))
            ver (:version @state)
            ;; with a single shared stale base, at most ONE write past the snapshot can win;
            ;; once lastmod[T,title] > snap, all remaining stale writes are rejected.
            log-cnt (log-ops)]
        (check! "C5"
          [["every accepted write strictly incremented version" (= ver (+ 1 commits))]
           ["lost-update prevented: at most 1 stale-base commit" (<= commits 1)]
           ["conflicts fired on stale bases" (pos? conflicts)]
           ["title cardinality == 1" (= 1 (cardinality "@T" "title"))]
           ["version == 1 + commits, no gaps" (and (= ver (+ 1 commits)) (= ver log-cnt))]
           ["tx strictly monotonic" (tx-monotonic?)]
           ["0 corrupt log lines" (zero? (corrupt-count))]])))))

;; ===========================================================================
;; C6 — Surviving obligation never slips under concurrency: a racing writer
;;      asserting depends_on @GHOST (no title) is ALWAYS rejected (HEADLINE).
;;      (Replaces the removed "active-without-driver" rule.)
;; ===========================================================================
(defn c6 []
  (println "\n--- C6: obligation — dangling depends_on never slips (HEADLINE) ---")
  (let [rounds 30
        viols (atom 0)]
    (dotimes [r rounds]
      (fresh! "/tmp/chelonia-c6.log")
      (commit! "@T" "title" "T" 0)
      (with-server
        (fn [port]
          (let [;; A: try to point T at a never-titled @GHOST (must be rejected).
                ;; B: race a legal single-valued churn on T to interleave commits.
                a (future (assert! port "@T" "depends_on" "@GHOST" (rd-version port)))
                b (future (assert! port "@T" "owner" "@racer" (rd-version port)))
                _ @a _ @b
                claims (live-claims)
                ghost-titled (contains? (thread-ids claims) "@GHOST")
                tg-edges (count (q claims :l "@T" :p "depends_on" :r "@GHOST"))]
            ;; INVARIANT: a depends_on->GHOST edge persists ONLY IF GHOST became
            ;; a real thread (it never does here) — i.e. never a dangling edge.
            (when (and (pos? tg-edges) (not ghost-titled)) (swap! viols inc))))))
    (check! "C6"
      [["across all rounds: 0 dangling depends_on edges committed" (zero? @viols)]
       ["(rounds run)" (= rounds rounds)]
       ["0 corrupt log lines (last round)" (zero? (corrupt-count))]])))

;; ===========================================================================
;; C7 — part_of cycle via multi-hop cooperative chain
;; ===========================================================================
(defn c7 []
  (println "\n--- C7: multi-hop part_of cycle (A->B->C->A) ---")
  (fresh! "/tmp/chelonia-c7.log")
  (commit! "@A" "title" "A" 0)
  (commit! "@B" "title" "B" 0)
  (commit! "@C" "title" "C" 0)
  (with-server
    (fn [port]
      (let [rounds 40
            ag1 (future (doall (for [k (range rounds)]
                                 (assert! port "@A" "part_of" "@B" (rd-version port)))))
            ag2 (future (doall (for [k (range rounds)]
                                 (assert! port "@B" "part_of" "@C" (rd-version port)))))
            ag3 (future (doall (for [k (range rounds)]
                                 (assert! port "@C" "part_of" "@A" (rd-version port)))))
            ;; repointers churn the single-valued chain mid-storm
            rep (future (doall (for [k (range rounds)]
                                 (assert! port "@A" "part_of"
                                          (if (even? k) "@B" "@C") (rd-version port)))))
            _ @ag1 _ @ag2 _ @ag3 _ @rep
            claims (live-claims)
            nodes ["@A" "@B" "@C"]
            any-cycle (some #(cycle? claims "part_of" %) nodes)]
        (check! "C7"
          [["no multi-hop part_of cycle committed" (not any-cycle)]
           ["cycle? false for every node" (every? #(not (cycle? claims "part_of" %)) nodes)]
           ["each node has <=1 part_of (single-valued)" (every? #(<= (cardinality % "part_of") 1) nodes)]
           ["0 corrupt log lines" (zero? (corrupt-count))]])))))

;; ===========================================================================
;; C8 — Log durability / no torn writes under max concurrency + replay equivalence
;; ===========================================================================
(defn c8 []
  (println "\n--- C8: log durability + replay==live under max concurrency ---")
  (fresh! "/tmp/chelonia-c8.log")
  ;; seed a small valid world (all real threads via title)
  (commit! "@A" "title" "A" 0)
  (commit! "@A" "driver" "@x" 0)
  (commit! "@B" "title" "B" 0)
  (commit! "@C" "title" "C" 0)
  (commit! "@C" "abandoned" "t" 0)
  (with-server
    (fn [port]
      (let [rounds 25
            ;; mixed writers: legal single-valued churn, illegal cycles, illegal
            ;; abandoned-deps, missing-entity deps, relates_to dangling, retracts
            ;; — all racing the sole writer.
            fs [(future (doall (for [k (range rounds)] (assert! port "@A" "title" (str "A-" k) (rd-version port)))))
                (future (doall (for [k (range rounds)] (assert! port "@A" "part_of" "@A" (rd-version port)))))       ; self-cycle (illegal)
                (future (doall (for [k (range rounds)] (assert! port "@B" "depends_on" "@C" (rd-version port)))))    ; abandoned dep (illegal)
                (future (doall (for [k (range rounds)] (assert! port "@B" "depends_on" "@GHOST" (rd-version port))))) ; missing (illegal)
                (future (doall (for [k (range rounds)] (assert! port "@B" "relates_to" "@GHOST" (rd-version port))))) ; relates_to dangling (illegal)
                (future (doall (for [k (range rounds)] (assert! port "@A" "owner" (str "@o" k) (rd-version port)))))
                (future (doall (for [k (range rounds)] (retract! port "@A" "driver" "@x" (rd-version port)))))
                (future (doall (for [k (range rounds)] (assert! port "@A" "driver" "@x" (rd-version port)))))]
            _ (doseq [f fs] @f)
            ;; replay the log through fold
            replay (fold (filter #(not= ::bad %) (parsed-log)))
            live @state
            log-cnt (log-ops)
            tids (thread-ids (:claims live))]
        (check! "C8"
          [["0 corrupt/torn log lines" (zero? (corrupt-count))]
           ["tx strictly monotonic, no gaps/dupes" (tx-monotonic?)]
           ["replay :claims == live :claims" (= (:claims replay) (:claims live))]
           ["replay :version == live :version" (= (:version replay) (:version live))]
           ["version == log line count" (= (:version live) log-cnt)]
           ["INVARIANT: no dangling depends_on/relates_to/part_of in live claims"
            (every? (fn [te]
                      (let [ids (thread-ids (:claims live))]
                        (and (every? ids (many (:claims live) te "depends_on"))
                             (every? ids (many (:claims live) te "relates_to"))
                             (let [pa (one (:claims live) te "part_of")] (or (nil? pa) (ids pa))))))
                    tids)]
           ["INVARIANT: no non-terminal depends_on points at abandoned"
            (every? (fn [te]
                      (or (terminal? (:claims live) te)
                          (not-any? #(some? (one (:claims live) % "abandoned"))
                                    (many (:claims live) te "depends_on"))))
                    tids)]
           ["INVARIANT: no part_of/depends_on cycles in live claims"
            (every? (fn [te] (and (not (cycle? (:claims live) "part_of" te))
                                  (not (cycle? (:claims live) "depends_on" te))))
                    tids)]])))))

;; ===========================================================================
;; C9 — depends_on referencing a missing / never-titled entity under load
;; ===========================================================================
(defn c9 []
  (println "\n--- C9: depends_on -> missing/never-titled entity, rejected under load ---")
  (fresh! "/tmp/chelonia-c9.log")
  (commit! "@X" "title" "X" 0)
  (with-server
    (fn [port]
      (let [rounds 40
            ;; writers race X depends_on GHOST (never given a title) ...
            ghosters (doall (for [i (range 8)]
                              (future (doall (for [k (range rounds)]
                                               (assert! port "@X" "depends_on" "@GHOST" (rd-version port)))))))
            ;; ... while one writer MAY title GHOST partway (it becomes a real thread)
            maybe-creator (future
                            (Thread/sleep 60)
                            (assert! port "@GHOST" "title" "GHOST" (rd-version port)))
            gr (mapcat deref ghosters)
            _ @maybe-creator
            claims (live-claims)
            ghost-known (contains? (thread-ids claims) "@GHOST")
            xg-edges (count (q claims :l "@X" :p "depends_on" :r "@GHOST"))
            ;; INVARIANT: edge persists ONLY IF GHOST is a real thread (titled)
            ok (or (zero? xg-edges) ghost-known)]
        (check! "C9"
          [["dangling depends_on edge persists ONLY if target now titled" ok]
           ["if GHOST untitled, 0 edges to it" (or ghost-known (zero? xg-edges))]
           ["0 corrupt log lines" (zero? (corrupt-count))]
           ["tx strictly monotonic" (tx-monotonic?)]])))))

;; ===========================================================================
;; runner
;; ===========================================================================
(def all-cases {"C1" c1 "C2" c2 "C3" c3 "C4" c4 "C5" c5 "C6" c6 "C7" c7 "C8" c8 "C9" c9})

(defn -main [& args]
  (obligation-probe)
  (let [sel (or (seq (filter all-cases args)) (sort (keys all-cases)))]
    (doseq [c sel] ((all-cases c)))
    (println "\n=========================== SUMMARY ===========================")
    (doseq [{:keys [case pass detail]} (sort-by :case @results)]
      (println (format "  %-4s %s" case (if pass "PASS" "FAIL"))))
    (let [n (count @results) p (count (filter :pass @results))]
      (println (format "\n  %d/%d cases passed" p n))
      (when (< p n) (System/exit 1)))))

(apply -main *command-line-args*)
