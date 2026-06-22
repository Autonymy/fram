;; baseline_bench.clj — AXIS 1 for the BASELINE (sole-writer coordinator model).
;; In-process: N threads contend ONE monitor; the critical section = serialize + append +
;; flush (+fsync if BAKEOFF_FSYNC=1). This mirrors coord_load.clj / the live commit! shape
;; (one global lock, fsync-in-lock) and anchors against RESULTS.md (1w 951 -> 32w 151 w/s on
;; the SCI runtime). Different subjects => no OCC conflict, pure serialization (the kindest
;; case for the lock; it still anti-scales from the convoy).
(require '[clojure.java.io :as io])
(import '[java.io FileOutputStream])

(def root (or (first *command-line-args*) "/tmp/bakeoff-baseline"))
(def iters (parse-long (or (second *command-line-args*) "3")))
(def PER_WRITER (parse-long (or (System/getenv "BAKEOFF_PER_WRITER") "4000")))
(def do-fsync (= "1" (System/getenv "BAKEOFF_FSYNC")))
(def NSUBJ 500)

(defn now [] (System/nanoTime))
(defn ms [t0] (/ (double (- (now) t0)) 1e6))
(defn rmf [f] (io/delete-file f true))

(def preds ["title" "note" "kind" "owner" "ref"])

(defn run-once [n]
  (let [logp (str root "/shared-N" n ".log")]
    (rmf logp)
    (.mkdirs (io/file root))
    (with-open [out (FileOutputStream. logp true)]
      (let [fc (.getChannel out)
            lock (Object.)
            ctr (atom 0)                                   ; shared tx counter (the contended resource)
            t0 (now)
            fs (mapv (fn [w]
                       (future
                         (dotimes [i PER_WRITER]
                           (let [c {:l (str "@e" (mod (+ i (* w 7)) NSUBJ))
                                    :p (nth preds (mod i 5)) :r (str "v" w "-" i) :op :assert :by (str "w" w)}]
                             (locking lock                  ; GLOBAL write lock (the coordinator)
                               (let [tx (swap! ctr inc)     ; central tx-seq (single-origin counter)
                                     bs (.getBytes (str (pr-str (assoc c :id (format "%012d" tx))) "\n") "UTF-8")]
                                 (.write out bs)
                                 (.flush out)
                                 (when do-fsync (.force fc true))))))))
                     (range n))]
        (run! deref fs)
        (let [wall (ms t0) total (* n PER_WRITER)]
          {:n n :wall-ms wall :total total :wps (/ total (/ wall 1000.0))})))))

(defn median [xs] (nth (sort xs) (quot (count xs) 2)))

(println (str "## WRITE throughput — candidate=baseline (in-process coordinator) per-writer="
              PER_WRITER " fsync=" (if do-fsync "1" "0")))
(println "N\twall_ms\ttotal\twrites/sec")
(doseq [n [1 8 32 128]]
  (let [runs (repeatedly iters #(run-once n))]
    (println (str n "\t" (long (median (map :wall-ms runs))) "\t" (:total (first runs))
                  "\t" (long (median (map :wps runs)))))
    (flush)))
