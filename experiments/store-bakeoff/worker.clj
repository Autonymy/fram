;; worker.clj — ONE writer process. Spawned N times for the concurrency axis.
;; argv: <candidate> <writer-id> <nclaims> <nsubj> <store-root>
;; prints one EDN line: {:w <id> :ms <wall> :n <claims>}  (write-only timing)
;;
;; candidates:
;;   baseline : flock a shared lock-file, append to ONE shared log, fsync in lock.
;;              (models the sole-writer coordinator's global-lock + fsync-in-lock shape.)
;;   per-log  : O_APPEND to THIS writer's OWN log. No lock, no coordination. (cand B)
;;   per-file : atomic temp+rename, one file per claim, into a SHARED dir. No lock. (cand A)
;;   sqlite   : INSERT each claim into a shared WAL-mode sqlite db. (cand C)
(require '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[babashka.pods :as pods])
(import '[java.nio.file Files StandardCopyOption]
        '[java.io RandomAccessFile FileOutputStream])
;; java.nio.channels.{FileChannel,FileLock} aren't importable in this SCI build;
;; reference via the RandomAccessFile/.getChannel handle without a type hint.

(def argv *command-line-args*)
(def candidate (nth argv 0))
(def w         (parse-long (nth argv 1)))
(def nclaims   (parse-long (nth argv 2)))
(def nsubj     (parse-long (nth argv 3)))
(def root      (nth argv 4))
(def do-fsync  (= "1" (System/getenv "BAKEOFF_FSYNC")))   ; default off (matches live log)

(defn now [] (System/nanoTime))

;; --- HLC id (writer-disjoint) ----------------------------------------------
(def ctr (atom 0))
(defn hlc []
  (format "%012x-%03x-%07x" (System/currentTimeMillis)
          (bit-and w 0xfff) (bit-and (swap! ctr inc) 0xfffffff)))

(def preds ["title" "note" "kind" "owner" "ref"])
(defn gen []
  (mapv (fn [i] {:id (hlc) :op :assert
                 :l (str "@e" (mod (+ i (* w 7)) nsubj))
                 :p (nth preds (mod i 5)) :r (str "v" w "-" i) :by (str "w" w)})
        (range nclaims)))

;; baseline lives in baseline_bench.clj (in-process single-coordinator model — the
;; cross-process FileLock path is blocked by SCI's method allowlist, and the real
;; coordinator is one process holding an in-memory lock anyway, so in-process is the
;; faithful model — see coord_load.clj). worker.clj handles A/B/C only.

;; --- per-log (cand B): own log, O_APPEND, no lock ---------------------------
(defn run-per-log [claims]
  (let [logp (str root "/w" w ".log")
        t0 (now)]
    (with-open [out (FileOutputStream. logp true)]
      (let [fc (.getChannel out)]
        (doseq [c claims]
          (.write out (.getBytes (str (pr-str c) "\n") "UTF-8")))
        (.flush out)
        (when do-fsync (.force fc true))))           ; one fsync at end (batched durability)
    (- (now) t0)))

;; --- per-file (cand A): atomic rename, shared dir ---------------------------
(defn run-per-file [claims]
  (let [t0 (now)]
    (doseq [c claims]
      (let [id (:id c)
            tmp (io/file root (str "." id ".tmp"))
            dst (io/file root (str id ".edn"))]
        (spit tmp (pr-str c))
        (Files/move (.toPath tmp) (.toPath dst)
                    (into-array java.nio.file.CopyOption [StandardCopyOption/ATOMIC_MOVE]))))
    (- (now) t0)))

;; --- sqlite (cand C): shared WAL db -----------------------------------------
(pods/load-pod 'org.babashka/go-sqlite3 "0.1.0")
(require '[pod.babashka.go-sqlite3 :as sql])
(defn run-sqlite [claims]
  (let [db (str root "/store.db")
        t0 (now)]
    ;; busy_timeout so concurrent writers serialize at sqlite's single-writer WAL lock
    ;; rather than erroring; this is sqlite's intrinsic write ceiling, the honest number.
    (sql/execute! db ["PRAGMA busy_timeout=60000;"])
    (doseq [c claims]
      (sql/execute! db ["INSERT OR IGNORE INTO claims (id,l,p,r,op,by) VALUES (?,?,?,?,?,?)"
                        (:id c) (:l c) (:p c) (:r c) (name (:op c)) (:by c)])
      )
    (- (now) t0)))

(let [claims (gen)
      nanos (case candidate
              "per-log"  (run-per-log claims)
              "per-file" (run-per-file claims)
              "sqlite"   (run-sqlite claims))]
  (println (pr-str {:w w :n nclaims :ms (/ (double nanos) 1e6)})))
