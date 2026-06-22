;; ============================================================================
;; cnf_read_after_write_test.clj — read-after-write soundness on the warm store.
;; ============================================================================
;; Guards the exact property the #44/#50 propagation gate measures and that a P0
;; alarm once suspected was broken: after an upsert-form COMMITS a def, is that def
;; IMMEDIATELY visible to a reader? The daemon's :seen signal IS (c/value-id store v)
;; (cnf_coord_daemon.clj), so this asserts it in-process (no socket, no #14 flakiness).
;;
;; It also pins the failure mode that produced the 46s never-visible busy-loop: an
;; upsert into a module ABSENT from the corpus is REJECTED ("scope matches 0 files"),
;; NOT silently lost — so a never-:seen def means it was NEVER WRITTEN, never an unread
;; write. (That is why the gate's graph arm must target a module present in the fixture.)
;;   bb -cp out cnf_read_after_write_test.clj
;; ============================================================================
(require '[fram.cnf :as c] '[clojure.java.io :as io])
(def root (System/getProperty "user.dir"))
(def code-log (str root "/.fram/code.log"))
(when-not (.exists (io/file code-log)) (println "SKIP — no code.log") (System/exit 0))
(def flat (str (System/getProperty "java.io.tmpdir") "/raw-" (System/nanoTime) ".code.log"))
(io/copy (io/file code-log) (io/file flat))
(binding [*command-line-args* []] (load-file "cnf_coord_daemon.clj"))
(boot-flat! flat)
(def st (:store @co))

(def fails (atom 0))
(defn chk [name ok] (if ok (println "  [PASS]" name) (do (swap! fails inc) (println "  [FAIL]" name))))
;; the daemon's read-after-write signal, verbatim (cnf_coord_daemon :seen handler).
(defn seen? [nm] (boolean (c/value-id st nm)))
(defn try-upsert [mod nm]
  (try (let [r (do-edit-min {:op "upsert-form" :module mod :datum (list 'def (symbol nm) 7)})]
         {:ok (:ok r) :reject (:reject r)})
       (catch Throwable t {:threw (.getMessage t)})))

;; the present module in the committed fixture (ingested --module schema).
(def MOD (or (System/getenv "RAW_MODULE") "schema"))

;; --- read-after-write SOUND: a committed def is immediately reader-visible ----
(def nm1 "rawvisprobezzq1")
(chk "pre: probe def not yet seen" (not (seen? nm1)))
(def r1 (try-upsert MOD nm1))
(chk "upsert into present module commits (:ok)" (:ok r1))
(chk "read-after-write: committed def is IMMEDIATELY :seen" (seen? nm1))

;; a SECOND distinct def, same module — still immediately visible (no staleness) ----
(def nm2 "rawvisprobezzq2")
(def r2 (try-upsert MOD nm2))
(chk "second upsert commits (:ok)" (:ok r2))
(chk "second def immediately :seen" (seen? nm2))
(chk "first def still :seen after the second write" (seen? nm1))

;; --- the 46s failure mode: an ABSENT-module write is REJECTED, not lost --------
(def nm3 "rawvisprobezzq3")
(def r3 (try-upsert "nosuchmodulezzq" nm3))
(chk "absent-module upsert is REJECTED/threw (not :ok)" (not (:ok r3)))
(chk "rejected write left NO def behind (never-:seen = never-WRITTEN, not unread)" (not (seen? nm3)))

(.delete (io/file flat))
(println (str "\n---- read-after-write: " (if (zero? @fails) "ALL PASS" (str @fails " FAIL")) " ----"))
(System/exit (if (zero? @fails) 0 1))
