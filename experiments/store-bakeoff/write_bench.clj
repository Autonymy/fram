;; write_bench.clj — AXIS 1: write throughput @ N concurrent writer PROCESSES.
;; Spawns N real OS processes (each its own bb/JVM-less SCI runtime, own fds). Measures
;; WALL-CLOCK from first spawn to last join => aggregate writes/sec across the swarm.
;; This is the anti-scaling test: does adding writers raise or LOWER aggregate throughput?
;;
;; usage: bb write_bench.clj <candidate> <store-root> [iters]
;;   sweeps N = 1,8,32,128 ; each writer writes PER_WRITER claims.
;;   reports aggregate writes/sec per N (median of `iters` runs).
(require '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[clojure.edn :as edn]
         '[babashka.process :as p]
         '[babashka.pods :as pods])

(def candidate (or (first *command-line-args*) "per-log"))
(def root-base (or (second *command-line-args*) "/tmp/bakeoff-write"))
(def iters     (parse-long (or (nth *command-line-args* 2 nil) "3")))
(def PER_WRITER (parse-long (or (System/getenv "BAKEOFF_PER_WRITER") "4000")))
(def NSUBJ     500)
(def worker (str (System/getProperty "user.dir") "/experiments/store-bakeoff/worker.clj"))

(defn now [] (System/nanoTime))
(defn ms [t0] (/ (double (- (now) t0)) 1e6))
(defn rmdir! [d] (when (.exists (io/file d)) (run! io/delete-file (reverse (file-seq (io/file d))))))
(defn fresh! [d] (rmdir! d) (.mkdirs (io/file d)) d)

(defn init-store! [root]
  (fresh! root)
  (when (= candidate "sqlite")
    (pods/load-pod 'org.babashka/go-sqlite3 "0.1.0")
    (require '[pod.babashka.go-sqlite3 :as sql])
    (let [db (str root "/store.db")]
      ((resolve 'pod.babashka.go-sqlite3/execute!) db ["PRAGMA journal_mode=WAL;"])
      ((resolve 'pod.babashka.go-sqlite3/execute!) db ["PRAGMA synchronous=NORMAL;"])
      ((resolve 'pod.babashka.go-sqlite3/execute!)
       db ["CREATE TABLE IF NOT EXISTS claims (id TEXT PRIMARY KEY, l TEXT, p TEXT, r TEXT, op TEXT, by TEXT);"]))))

(defn one-run [n]
  (let [root (str root-base "-" candidate "-N" n)]
    (init-store! root)
    (let [t0 (now)
          procs (mapv (fn [w]
                        (p/process ["bb" worker candidate (str w) (str PER_WRITER) (str NSUBJ) root]
                                   {:out :string :err :string}))
                      (range n))
          _ (run! (fn [pr] @pr) procs)               ; join all
          wall (ms t0)
          total (* n PER_WRITER)]
      ;; sanity: surface any worker stderr
      (doseq [pr procs] (when (seq (:err @pr)) (binding [*out* *err*] (println "WORKER ERR:" (:err @pr)))))
      {:n n :wall-ms wall :total total :wps (/ total (/ wall 1000.0))})))

(defn median [xs] (let [s (sort xs)] (nth s (quot (count s) 2))))

(println (str "## WRITE throughput — candidate=" candidate " per-writer=" PER_WRITER
              " store=" root-base " fsync=" (or (System/getenv "BAKEOFF_FSYNC") "0")))
(println "N\twall_ms\ttotal\twrites/sec")
(doseq [n [1 8 32 128]]
  (let [runs (repeatedly iters #(one-run n))
        wps  (long (median (map :wps runs)))
        wall (long (median (map :wall-ms runs)))
        total (:total (first runs))]
    (println (str n "\t" wall "\t" total "\t" wps))
    (flush)))
(rmdir! (str root-base "-" candidate "-N1"))
(rmdir! (str root-base "-" candidate "-N8"))
(rmdir! (str root-base "-" candidate "-N32"))
(rmdir! (str root-base "-" candidate "-N128"))
