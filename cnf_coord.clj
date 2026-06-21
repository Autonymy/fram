;; cnf_coord.clj — SHIM.  The coordinator is now AUTHORED IN BEAGLE:
;;   src/fram/cnf_coord.bclj  ->  out/fram/cnf_coord.clj  (ns fram.cnf-coord)
;;
;; The ~311 lines of hand-written Clojure that lived here are gone — the logic is
;; typed Beagle. This thin re-export keeps every load-file consumer (the daemon and
;; ~40 tests/experiments that `(load-file "cnf_coord.clj")`) working UNCHANGED: it
;; refers the public coordinator API into the loading namespace and bridges the two
;; Beagle !-suffixed names (new-coord!/replay!) back to their historical spellings.
;;
;;   bb -cp out cnf_coord.clj   ; (loaded as a library, never run standalone)
(require '[fram.cnf-coord :as cc])

(defn store [co] (:store co))          ; the bare store accessor some tests use directly
(def new-coord      cc/new-coord!)     ; Beagle requires the ! (it mints a store); consumers say new-coord
(def replay         cc/replay!)        ; "
(def register-pred! cc/register-pred!)
(def commit!        cc/commit!)
(def retract!       cc/retract!)
(def current-seq    cc/current-seq)
(def base-version   cc/base-version)
(def live-cids-lp   cc/live-cids-lp)
(def dump-log!      cc/dump-log!)
(def live-triples   cc/live-triples)
(def acquire-lease! cc/acquire-lease!)
(def release-lease! cc/release-lease!)
(def fence-ok?      cc/fence-ok?)
