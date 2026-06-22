#!/usr/bin/env bb
;; cpf2 — the THEORETICALLY-SOUND claim-per-file store (#foundational-store-direction).
;;
;; Successor to cpf.clj: where the spike used raw UUIDv7 wall-ms (misorders under
;; cross-writer skew) and a single-pass cardinality read (order-dependent), this fixes
;; both at the root. See DESIGN.md for the full brief. The store is a coordinator-free
;; CvRDT: a grow-only G-Set of immutable claim-objects, merge = file union, read =
;; deterministic fold over the HLC total order with causal edges.
;;
;; What's principled here (the whole point):
;;   1. ORDER = Hybrid Logical Clock baked into the id  (§1) — total order respecting
;;      causality without trusting wall clocks; never regresses; node-id breaks genuine
;;      concurrency ties so the order is TOTAL and the fold deterministic.
;;   2. STORE = G-Set of immutable objects (§2) — merge = set union; convergence
;;      (idempotent/commutative/associative) is by construction.
;;   3. TX = commit-claim (§3) — members invisible until a commit lists them; git's
;;      dangling-object model; all-or-nothing on crash; merge-safe both directions.
;;   4. CARDINALITY merge-precedence (§4) — TWO-PASS fold: pass 1 picks the winning
;;      cardinality axiom per predicate by HLC-LWW; pass 2 folds data under it. A
;;      late-arriving schema claim CANNOT change the converged state. Order-independent.
;;   5. TIERING = git object model (§5) — loose objects (hot) + packfiles (cold);
;;      pack is a pure re-encoding, fold-identical (`pack-equivalent?`).
(ns cpf2
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.security MessageDigest]
           [java.nio.file Files StandardCopyOption]))

;; ===========================================================================
;; §1  HYBRID LOGICAL CLOCK
;; ===========================================================================
;; HLC tuple = [pt lc node]:
;;   pt   48-bit  physical time — max wall-ms this node has OBSERVED (own clock or any
;;                merged-in claim). Monotonic non-decreasing.
;;   lc   16-bit  logical counter — disambiguates events in the same pt (same ms, or a
;;                stalled/regressed wall clock).
;;   node 48-bit  stable per-process id — the FINAL deterministic tiebreak making the
;;                order TOTAL across writers who legitimately share [pt lc].
;;
;; The id string is "{pt:012x}-{lc:04x}-{node:012x}" — big-endian field order ⇒
;; LEXICAL SORT == HLC TOTAL ORDER. So tail-by-id and rough-chronological filenames
;; survive, now causally sound.

(def ^:private NODE
  ;; stable node-id for this process. In production this persists per-replica; here a
  ;; random 48-bit value per process is enough to make the order total across writers.
  (bit-and (long (* (rand) 0xFFFFFFFFFFFF)) 0xFFFFFFFFFFFF))

(defn make-clock
  "A fresh HLC state. `wall` is an injectable now-ms fn (tests regress it on purpose)."
  ([] (make-clock #(System/currentTimeMillis)))
  ([wall] (atom {:pt 0 :lc 0 :node NODE :wall wall})))

(defn- hlc-id-str [pt lc node]
  (format "%012x-%04x-%012x" (bit-and pt 0xFFFFFFFFFFFF) (bit-and lc 0xFFFF) node))

(defn hlc-now!
  "Rule (a): local event. Advances the clock and returns a fresh, strictly-greater id.
   pt' = max(pt, wall());  lc' = (pt'==pt) ? lc+1 : 0."
  [clock]
  (let [{:keys [wall]} @clock
        w (wall)
        s (swap! clock
                 (fn [{:keys [pt lc node] :as st}]
                   (let [pt' (max pt w)
                         lc' (if (= pt' pt) (inc lc) 0)]
                     (assoc st :pt pt' :lc lc'))))]
    (hlc-id-str (:pt s) (:lc s) (:node s))))

(defn- parse-id [id]
  ;; "{pt}-{lc}-{node}" -> [pt lc node]; tolerant of malformed/foreign ids (returns nil).
  (when (string? id)
    (let [[a b c] (str/split id #"-")]
      (try [(Long/parseLong a 16) (Long/parseLong b 16) (Long/parseLong c 16)]
           (catch Exception _ nil)))))

(defn hlc-observe!
  "Rule (b): receive. Advances the clock PAST a remote id (fires at MERGE/LOAD — a CRDT
   has no write-time message, so the clock advances when we READ a peer's files). After
   observing the max id in a merged set, the next local write is guaranteed greater."
  [clock remote-id]
  (when-let [[m-pt m-lc _] (parse-id remote-id)]
    (let [{:keys [wall]} @clock
          w (wall)]
      (swap! clock
             (fn [{:keys [pt lc node] :as st}]
               (let [pt' (max pt m-pt w)
                     lc' (cond
                           (and (= pt' pt) (= pt' m-pt)) (inc (max lc m-lc))
                           (= pt' pt)                     (inc lc)
                           (= pt' m-pt)                   (inc m-lc)
                           :else                          0)]
                 (assoc st :pt pt' :lc lc'))))))
  clock)

;; ===========================================================================
;; content address (dedup / integrity) — body sans :id, canonical (sorted)
;; ===========================================================================
(defn content-hash [m]
  (let [canon (pr-str (into (sorted-map) (dissoc m :id :hash)))
        dig   (.digest (MessageDigest/getInstance "SHA-256") (.getBytes canon "UTF-8"))]
    (apply str (map #(format "%02x" %) dig))))

;; ===========================================================================
;; §2/§3  WRITE PATH — one claim, one file; atomic temp-write + rename
;; ===========================================================================
(defn ensure-store [dir] (.mkdirs (io/file dir)) dir)

(defn- atomic-spit [dir fname content]
  (let [tmp (io/file dir (str "." fname ".tmp"))
        dst (io/file dir fname)]
    (spit tmp content)
    (Files/move (.toPath tmp) (.toPath dst)
                (into-array java.nio.file.CopyOption [StandardCopyOption/ATOMIC_MOVE]))))

(defn put-claim
  "Write one immutable claim-object; returns its HLC id. opts may carry
   :supersedes/:retracts (vectors of ids), :pending, :by."
  [clock dir {:keys [l p r] :as claim}]
  (let [id (hlc-now! clock)
        m0 (merge {:op :assert} claim {:id id})
        m  (assoc m0 :hash (content-hash m0))]
    (atomic-spit dir (str id ".edn") (pr-str m))
    id))

(defn stage-member
  "Write a PENDING member (git dangling object): real birth-HLC now, but invisible
   until some commit-claim lists it. Returns the id for the caller to collect."
  [clock dir claim]
  (put-claim clock dir (assoc claim :pending true)))

(defn put-commit
  "Write a COMMIT-CLAIM listing member ids. Members go live only once THIS file lands.
   The commit's own atomic rename is the single linearization point. Returns commit id."
  [clock dir {:keys [members by]}]
  (let [id (hlc-now! clock)
        m  {:id id :op :commit :members (vec members) :by by}]
    (atomic-spit dir (str id ".edn") (pr-str m))
    id))

;; ===========================================================================
;; READ PATH — list, parse, observe-clock, gate commits, two-pass cardinality fold
;; ===========================================================================
(defn- read-loose [dir]
  (->> (.listFiles (io/file dir))
       (filter #(let [n (.getName %)]
                  (and (str/ends-with? n ".edn")
                       (not (str/starts-with? n "."))         ; skip *.tmp
                       (not (str/starts-with? n "pack-")))))  ; packs read separately
       (keep (fn [f] (try (edn/read-string (slurp f)) (catch Exception _ nil))))
       (vec)))

(defn- read-packs [dir]
  ;; §5: a pack file is a sorted vector of claim-objects (pure re-encoding of loose).
  (->> (.listFiles (io/file dir))
       (filter #(str/starts-with? (.getName %) "pack-"))
       (mapcat (fn [f] (try (edn/read-string (slurp f)) (catch Exception _ nil))))
       (vec)))

(defn read-objects
  "All claim-objects in the store (loose ∪ unpacked), deduped by id. The G-Set."
  [dir]
  (->> (concat (read-packs dir) (read-loose dir))
       (reduce (fn [m o] (assoc m (:id o) o)) {})   ; dedup by id (idempotent union)
       (vals)
       (vec)))

;; ---- the FOLD (a pure function of the object set — this is the CRDT read) ----

(def ^:private BOOTSTRAP-CARD
  ;; `cardinality` itself is single-valued: a predicate has ONE cardinality axiom.
  ;; This is the fixed bootstrap axiom that §4's two-pass machinery configures from.
  "cardinality")

(defn fold
  "Deterministic fold of a claim-object SET to graph state. Pure function of the set ⇒
   convergent (any replica with the same set computes the same state). Returns
   {:state {[l p] -> r | #{r}} :alive [objs] :commits #{ids} :dropped [ids]}.

   §3 commit-gate → §1 HLC sort → causal kill-set → §4 TWO-PASS cardinality."
  [objects]
  (let [commits  (filterv #(= :commit (:op %)) objects)
        claims   (filterv #(not= :commit (:op %)) objects)
        landed   (into #{} (mapcat :members commits))
        ;; §3 commit-gate: a :pending member is invisible until a commit lists it
        {visible true dropped false}
        (group-by (fn [c] (if (:pending c) (contains? landed (:id c)) true)) claims)
        ;; §1 HLC total order (lexical sort of the id string == HLC order)
        sorted   (sort-by :id (or visible []))
        ;; causal kill-set (order-independent): explicit retract/supersede edges
        killed   (into #{} (mapcat (fn [c] (concat (:retracts c) (:supersedes c))) sorted))
        alive    (filterv #(not (contains? killed (:id %))) sorted)

        ;; §4 PASS 1 — winning cardinality axiom per predicate, by HLC-LWW.
        ;; Among all live (P "cardinality" v) claims, max-HLC id wins. Order-independent:
        ;; same set ⇒ same winner regardless of arrival/merge order.
        card-axioms (->> alive
                         (filter #(= BOOTSTRAP-CARD (:p %)))   ; (P "cardinality" v)
                         (group-by :l)                          ; per predicate P (= :l)
                         (reduce-kv (fn [m pred cs]
                                      (assoc m pred (:r (apply max-key :id cs))))
                                    {}))
        single?  (fn [p] (= "single" (get card-axioms p)))

        ;; §4 PASS 2 — fold data claims under the selected axioms. single ⇒ max-HLC
        ;; value wins (LWW); multi ⇒ accumulate. Because the axiom is fixed before this
        ;; pass and applied uniformly to the WHOLE history, a late schema claim cannot
        ;; change the outcome — the converged state was always the max-HLC value.
        state    (reduce
                  (fn [st c]
                    (let [k [(:l c) (:p c)]]
                      (cond
                        (= :retract (:op c)) (update st k (fn [v] (if (set? v) (disj v (:r c)) nil)))
                        (single? (:p c))     (assoc st k (:r c))     ; LWW: sorted ⇒ last wins
                        :else                (update st k (fnil conj #{}) (:r c)))))
                  {} alive)]
    {:state   (into {} (remove (comp nil? val) state))
     :alive   alive
     :commits (into #{} (map :id commits))
     :dropped (mapv :id (or dropped []))
     :card    card-axioms}))

(defn load-store
  "Read the store AND advance `clock` past every id seen (§1 rule-b: the no-message
   receive). After load, the next local write is guaranteed > everything merged in.
   Pass clock=nil for a pure read (e.g. equivalence checks). Returns the fold result."
  ([dir] (load-store nil dir))
  ([clock dir]
   (let [objs (read-objects dir)]
     (when clock
       (doseq [o objs] (hlc-observe! clock (:id o))))
     (fold objs))))

;; ===========================================================================
;; MERGE = union of files (federation pull). Idempotent (same id ⇒ same file).
;; ===========================================================================
(defn merge-into! [dst-dir src-dir]
  (ensure-store dst-dir)
  (doseq [f (.listFiles (io/file src-dir))
          :when (let [n (.getName f)]
                  (and (str/ends-with? n ".edn") (not (str/starts-with? n "."))))]
    (let [d (io/file dst-dir (.getName f))]
      (when-not (.exists d) (io/copy f d))))
  dst-dir)

;; ===========================================================================
;; §5  PACKING — loose→packfile coalescing (git object model). Pure re-encoding.
;; ===========================================================================
(defn pack!
  "Coalesce all LOOSE objects with id <= `frontier` into one sorted packfile and delete
   the loose originals. `frontier` is an HLC id string (exclusive of newer writes).
   Returns the pack filename, or nil if nothing to pack. Fold-equivalent by construction
   (the pack holds the same objects, just re-encoded as one sorted vector)."
  [dir frontier]
  (let [loose-files (->> (.listFiles (io/file dir))
                         (filter #(let [n (.getName %)]
                                    (and (str/ends-with? n ".edn")
                                         (not (str/starts-with? n "."))
                                         (not (str/starts-with? n "pack-")))))
                         (filter #(<= (compare (str/replace (.getName %) ".edn" "") frontier) 0)))
        objs (->> loose-files
                  (keep (fn [f] (try (edn/read-string (slurp f)) (catch Exception _ nil))))
                  (sort-by :id)
                  (vec))]
    (when (seq objs)
      (let [lo (:id (first objs)) hi (:id (last objs))
            fname (str "pack-" lo "--" hi ".edn")]
        (atomic-spit dir fname (pr-str objs))
        (doseq [f loose-files] (.delete f))                ; loose originals now in pack
        fname))))

;; ===========================================================================
;; tiny CLI marker
;; ===========================================================================
(defn -main [& _] (println "cpf2: principled HLC G-Set store — see DESIGN.md / demo2.clj / prop_test.clj"))
