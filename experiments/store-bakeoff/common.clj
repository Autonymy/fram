;; common.clj — shared primitives for the store bake-off.
;;
;; The SEMANTIC MODEL is fixed (a grow-only G-Set of immutable claim-objects; merge =
;; union; state = deterministic fold over an HLC total order). We are ONLY measuring the
;; PHYSICAL LAYOUT + access path. Every candidate below stores the SAME claim shape and
;; folds to the SAME state — only the durability format differs.
;;
;; Claim shape: {:id <hlc-sortable> :l <subject> :p <pred> :r <obj> :op :assert|:retract
;;               :by <writer> :sup [ids] :ret [ids]}.  Single-valued preds LWW by id-order.
(ns common
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.nio.file Files StandardCopyOption]
           [java.util.concurrent.atomic AtomicLong]))

(defn now [] (System/nanoTime))
(defn ms [t0 t1] (/ (double (- t1 t0)) 1e6))
(defn ms-since [t0] (ms t0 (now)))

;; --- HLC-ish sortable id ----------------------------------------------------
;; 48-bit ms timestamp | 16-bit per-writer counter, hex => lexicographic == chronological.
;; A per-writer counter (not a global one) is the whole point: no shared sequence, ids are
;; collision-free across writers because each carries its writer id in a disjoint band.
;; For the bench we make it (ms, writer, ctr) so two writers never collide and order is total.
(defn mk-hlc-fn
  "Returns a 0-arg fn minting monotone sortable ids for writer `w`."
  [w]
  (let [ctr (AtomicLong. 0)]
    (fn []
      (let [t (System/currentTimeMillis)
            c (.getAndIncrement ctr)]
        ;; 12 hex ms | 3 hex writer | 7 hex ctr  => total order, writer-disjoint
        (format "%012x-%03x-%07x" t (bit-and w 0xfff) (bit-and c 0xfffffff))))))

;; --- claim generation -------------------------------------------------------
;; SUBJECT FANOUT controls read-scoping + LWW collapse. mod `nsubj` => realistic
;; "many claims about @x". preds rotate over a small vocabulary.
(def preds ["title" "note" "kind" "owner" "ref"])

(defn gen-claims
  "n claims for writer w, subjects spread over nsubj entities."
  [w n nsubj]
  (let [hlc (mk-hlc-fn w)]
    (mapv (fn [i]
            {:id (hlc)
             :op :assert
             :l (str "@e" (mod (+ i (* w 7)) nsubj))
             :p (nth preds (mod i 5))
             :r (str "v" w "-" i)
             :by (str "w" w)})
          (range n))))

;; --- serialize/parse one claim ----------------------------------------------
(defn ser [c] (pr-str c))
(defn de  [s] (edn/read-string s))

;; --- the FOLD (identical across candidates) ---------------------------------
;; deterministic fold over id-sorted claims; single-valued LWW, multi accumulates,
;; causal :sup/:ret kill targets. We fold to {[l p] -> r|#{r}} and return state size.
(def single-preds #{"title" "kind" "owner"})   ; fixed cardinality for the bench

(defn fold-state
  "claims (any order) -> state map. Sorts by :id (HLC total order)."
  [claims]
  (let [sorted (sort-by :id claims)
        killed (into #{} (mapcat (fn [c] (concat (:sup c) (:ret c))) sorted))
        alive  (remove #(contains? killed (:id %)) sorted)]
    (reduce (fn [st c]
              (let [k [(:l c) (:p c)]]
                (cond
                  (= :retract (:op c)) (update st k (fn [v] (if (set? v) (disj v (:r c)) nil)))
                  (single-preds (:p c)) (assoc st k (:r c))
                  :else (update st k (fnil conj #{}) (:r c)))))
            {} alive)))

;; --- atomic file write (for candidate A) ------------------------------------
(defn atomic-spit [dir fname content]
  (let [tmp (io/file dir (str "." fname ".tmp"))
        dst (io/file dir fname)]
    (spit tmp content)
    (Files/move (.toPath tmp) (.toPath dst)
                (into-array java.nio.file.CopyOption [StandardCopyOption/ATOMIC_MOVE]))))

(defn rmdir! [d] (when (.exists (io/file d)) (run! io/delete-file (reverse (file-seq (io/file d))))) )
(defn fresh-dir! [d] (rmdir! d) (.mkdirs (io/file d)) d)

;; --- drop OS page cache for a path (best-effort; needs root for full drop) ---
;; We can't drop the global cache without root, so the cold-load story is handled by
;; running on real disk + a freshly-written store the bench never read back, plus a
;; separate process per load (no warm JVM heap). Documented in RESULTS.
