;; load_bench.clj — AXES 2,3,4,5: cold-load/fold, federation merge, subject-read, footprint.
;; Builds a store of M claims for each candidate, then measures the read-side access paths.
;; Each measurement runs in THIS process but the store was written by a prior phase / fresh
;; files, and for the real-disk cold variant we run on /tmp with a sync+drop-hint between.
;;
;; usage: bb load_bench.clj <M> <store-root>
(require '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[clojure.edn :as edn]
         '[babashka.pods :as pods])
(import '[java.io FileOutputStream RandomAccessFile])
(load-file (str (System/getProperty "user.dir") "/experiments/store-bakeoff/common.clj"))
(alias 'c 'common)

(def M (parse-long (or (first *command-line-args*) "100000")))
(def root (or (second *command-line-args*) "/tmp/bakeoff-load"))
(def NSUBJ 2000)
(def NWRITERS 8)                                       ; M claims spread over 8 writers

(defn now [] (System/nanoTime))
(defn ms [t0] (/ (double (- (now) t0)) 1e6))

;; ---- build phase: write M claims into each candidate's layout -----------------
(defn build-log [dir claims]            ; single log (baseline + cand B single-writer view)
  (c/fresh-dir! dir)
  (with-open [out (FileOutputStream. (str dir "/store.log") true)]
    (doseq [cl claims] (.write out (.getBytes (str (pr-str cl) "\n") "UTF-8")))
    (.flush out))
  dir)

(defn build-per-log [dir claims]        ; cand B: NWRITERS separate logs
  (c/fresh-dir! dir)
  (let [groups (group-by :by claims)]
    (doseq [[w cs] groups]
      (with-open [out (FileOutputStream. (str dir "/" w ".log") true)]
        (doseq [cl cs] (.write out (.getBytes (str (pr-str cl) "\n") "UTF-8")))
        (.flush out))))
  dir)

(defn build-per-file [dir claims]       ; cand A
  (c/fresh-dir! dir)
  (doseq [cl claims]
    (spit (io/file dir (str (:id cl) ".edn")) (pr-str cl)))   ; direct spit for build speed
  dir)

(pods/load-pod 'org.babashka/go-sqlite3 "0.1.0")
(require '[pod.babashka.go-sqlite3 :as sql])
;; The go-sqlite3 pod is one-shot per execute! (no persistent connection, so BEGIN/COMMIT
;; can't span calls). Bulk-insert via batched multi-row INSERT statements instead — each
;; execute! is then its own implicit transaction over a whole batch.
(defn build-sqlite [dir claims]         ; cand C
  (c/fresh-dir! dir)
  (let [db (str dir "/store.db")]
    (sql/execute! db ["PRAGMA journal_mode=WAL;"])
    (sql/execute! db ["PRAGMA synchronous=NORMAL;"])
    (sql/execute! db ["CREATE TABLE claims (id TEXT PRIMARY KEY, l TEXT, p TEXT, r TEXT, op TEXT, by TEXT);"])
    (doseq [batch (partition-all 500 claims)]
      (let [ph (str/join "," (repeat (count batch) "(?,?,?,?,?,?)"))
            args (mapcat (fn [cl] [(:id cl) (:l cl) (:p cl) (:r cl) (name (:op cl)) (:by cl)]) batch)]
        (sql/execute! db (into [(str "INSERT OR IGNORE INTO claims (id,l,p,r,op,by) VALUES " ph)] args))))
    db))

;; ---- cold-load + fold ---------------------------------------------------------
(defn load-log [dir]
  (let [t0 (now)
        claims (->> (slurp (str dir "/store.log")) str/split-lines (remove str/blank?) (mapv c/de))
        state (c/fold-state claims)]
    {:ms (ms t0) :n (count claims) :state (count state)}))

(defn load-per-log [dir]
  (let [t0 (now)
        logs (filter #(str/ends-with? (.getName %) ".log") (.listFiles (io/file dir)))
        claims (vec (mapcat (fn [f] (->> (slurp f) str/split-lines (remove str/blank?) (map c/de))) logs))
        state (c/fold-state claims)]
    {:ms (ms t0) :n (count claims) :state (count state)}))

(defn load-per-file [dir]
  (let [t0 (now)
        fs* (filter #(str/ends-with? (.getName %) ".edn") (.listFiles (io/file dir)))
        claims (->> fs* (mapv #(c/de (slurp %))))
        state (c/fold-state claims)]
    {:ms (ms t0) :n (count claims) :state (count state)}))

(defn load-sqlite [dir]                 ; fold in app (apples-to-apples with EDN folds)
  (let [t0 (now) db (str dir "/store.db")
        rows (sql/query db ["SELECT id,l,p,r,op,by FROM claims"])
        claims (mapv (fn [r] {:id (:id r) :l (:l r) :p (:p r) :r (:r r)
                              :op (keyword (:op r)) :by (:by r)}) rows)
        state (c/fold-state claims)]
    {:ms (ms t0) :n (count claims) :state (count state)}))

;; sqlite can ALSO fold in-engine via SQL (its native advantage); measure that too.
(defn load-sqlite-sql [dir]
  (let [t0 (now) db (str dir "/store.db")
        ;; single-valued LWW: max(id) per (l,p) over single preds; multi = distinct r.
        n (:count (first (sql/query db ["SELECT count(DISTINCT l||'|'||p) AS count FROM claims"])))]
    {:ms (ms t0) :state n :note "SQL-side count(distinct l,p)"}))

;; ---- federation merge: two divergent replicas of size M each ------------------
;; per-file/per-log: merge = file union (cp new files). sqlite: INSERT OR IGNORE the other.
(defn merge-per-file [a b]
  (let [t0 (now)
        bfs (filter #(str/ends-with? (.getName %) ".edn") (.listFiles (io/file b)))
        merged 0]
    (doseq [f bfs]
      (let [d (io/file a (.getName f))] (when-not (.exists d) (io/copy f d))))
    {:ms (ms t0) :merged (count bfs)}))

(defn merge-per-log [a b]               ; cand B: concat the other replica's logs in
  (let [t0 (now)
        bfs (filter #(str/ends-with? (.getName %) ".log") (.listFiles (io/file b)))]
    (doseq [f bfs]
      (let [d (io/file a (str "merged-" (.getName f)))] (io/copy f d)))
    {:ms (ms t0) :merged (count bfs)}))

(defn merge-sqlite [a b]                ; pull b's rows into a (batched)
  (let [t0 (now)
        rows (sql/query (str b "/store.db") ["SELECT id,l,p,r,op,by FROM claims"])]
    (doseq [batch (partition-all 500 rows)]
      (let [ph (str/join "," (repeat (count batch) "(?,?,?,?,?,?)"))
            args (mapcat (fn [r] [(:id r) (:l r) (:p r) (:r r) (:op r) (:by r)]) batch)]
        (sql/execute! (str a "/store.db")
                      (into [(str "INSERT OR IGNORE INTO claims (id,l,p,r,op,by) VALUES " ph)] args))))
    {:ms (ms t0) :merged (count rows)}))

;; ---- subject-scoped read: "all claims about @x" -------------------------------
(def probe-subj "@e7")
(defn read-log-scan [dir]               ; no index: full scan + filter
  (let [t0 (now)
        claims (->> (slurp (str dir "/store.log")) str/split-lines (remove str/blank?) (map c/de)
                    (filter #(= probe-subj (:l %))))]
    {:ms (ms t0) :hits (count claims)}))

(defn read-per-file-scan [dir]          ; no index: listdir + open all + filter
  (let [t0 (now)
        fs* (filter #(str/ends-with? (.getName %) ".edn") (.listFiles (io/file dir)))
        hits (->> fs* (map #(c/de (slurp %))) (filter #(= probe-subj (:l %))))]
    {:ms (ms t0) :hits (count hits)}))

(defn read-sqlite-idx [dir]             ; WITH index on l
  (let [db (str dir "/store.db")
        _ (sql/execute! db ["CREATE INDEX IF NOT EXISTS idx_l ON claims(l);"])
        t0 (now)
        rows (sql/query db ["SELECT id,l,p,r FROM claims WHERE l=?" probe-subj])]
    {:ms (ms t0) :hits (count rows)}))

;; warm index for log/per-file: build {l -> [claims]} once, then lookup is O(hits).
(defn read-log-warmidx [dir]
  (let [claims (->> (slurp (str dir "/store.log")) str/split-lines (remove str/blank?) (map c/de))
        idx (group-by :l claims)
        t0 (now)
        hits (get idx probe-subj)]
    {:ms (ms t0) :hits (count hits) :note "lookup only (index pre-built)"}))

;; ---- footprint ----------------------------------------------------------------
(defn footprint [dir]
  (let [files (filter #(.isFile %) (file-seq (io/file dir)))
        bytes (reduce + 0 (map #(.length %) files))]
    {:files (count files) :bytes bytes :mb (/ bytes 1e6)}))

;; ============================ DRIVER ===========================================
(println (str "## LOAD/MERGE/READ — M=" M " writers=" NWRITERS " subjects=" NSUBJ " root=" root))
(let [claims (vec (mapcat #(c/gen-claims % (quot M NWRITERS) NSUBJ) (range NWRITERS)))
      claims (vec (take M claims))]
  (println (str "generated " (count claims) " claims"))

  (let [dlog  (str root "/log")
        dplog (str root "/per-log")
        dpf   (str root "/per-file")
        dsq   (str root "/sqlite")]
    (println "\n-- building stores --")
    (build-log dlog claims) (build-per-log dplog claims)
    (build-per-file dpf claims) (build-sqlite dsq claims)

    (println "\n-- AXIS 2: cold-load + full-fold (ms) --")
    (println "baseline/log :" (load-log dlog))
    (println "B/per-log    :" (load-per-log dplog))
    (println "A/per-file   :" (load-per-file dpf))
    (println "C/sqlite(app):" (load-sqlite dsq))

    (println "\n-- AXIS 5: footprint --")
    (println "baseline/log :" (footprint dlog))
    (println "B/per-log    :" (footprint dplog))
    (println "A/per-file   :" (footprint dpf))
    (println "C/sqlite     :" (footprint dsq))

    (println "\n-- AXIS 4: subject-scoped read (\"all claims about " probe-subj "\") --")
    (println "log scan (no idx)   :" (read-log-scan dlog))
    (println "per-file scan(no idx):" (read-per-file-scan dpf))
    (println "log warm-idx (lookup):" (read-log-warmidx dlog))
    (println "sqlite +index       :" (read-sqlite-idx dsq))

    (println "\n-- AXIS 3: federation merge (two replicas of M each) --")
    ;; build a 2nd divergent replica B' for each file-based candidate
    (let [claims2 (vec (mapcat #(c/gen-claims (+ 100 %) (quot M NWRITERS) NSUBJ) (range NWRITERS)))
          dpf2 (str root "/per-file-2") dplog2 (str root "/per-log-2") dsq2 (str root "/sqlite-2")]
      (build-per-file dpf2 claims2) (build-per-log dplog2 claims2) (build-sqlite dsq2 claims2)
      (println "A/per-file union :" (merge-per-file dpf dpf2))
      (println "B/per-log concat :" (merge-per-log dplog dplog2))
      (println "C/sqlite ins-ign :" (merge-sqlite dsq dsq2))
      (println "baseline/log     : N/A — single-origin tx-seq collides; cannot union without rewrite"))))
