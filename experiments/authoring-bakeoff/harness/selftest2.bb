#!/usr/bin/env bb
(load-file "m2_adapter.bb")
(require '[babashka.process :as p] '[clojure.string :as str])
(def scratch "/home/tom/code/fram/experiments/authoring-bakeoff/scratch/adapter-test")
(p/sh "mkdir" "-p" scratch)
(defn gate [dump-lines tag]
  (let [f (str scratch "/" tag ".edn")]
    (spit f (str (str/join "\n" dump-lines) "\n"))
    (let [r (p/sh "/home/tom/code/beagle/bin/beagle-build-all" "--build-edn" f "--out" (str scratch "/out-" tag))]
      {:exit (:exit r) :out (str/trim (str (:out r) (:err r)))
       :clj (let [d (clojure.java.io/file (str scratch "/out-" tag "/beagle"))]
              (when (.exists d) (slurp (str scratch "/out-" tag "/beagle/user.clj"))))})))

;; Current program: inc (engine ids 1-wrapper, define-target, then inc defn).
;; Build it for real from text so the name->id map is grounded in the engine dump.
(spit (str scratch "/inc.bclj") "#lang beagle/clj\n(defn inc [x :- Int] :- Int (+ x 1))\n")
(def inc-dump (str/split-lines
  (:out (p/sh "racket" "/home/tom/code/beagle/beagle-lib/private/claims-roundtrip.rkt" "--emit-edn" (str scratch "/inc.bclj")))))
;; figure out the engine id of inc's defn root + its binding leaf for name mapping.
;; For 0B (pure additive) we don't need to map @ids — dec is all-fresh.
(def inc-current {:lines inc-dump :name->id {}})

;; --- 0B additive: model adds dec (all fresh ids), inc untouched ---
(def m2-0B-good
  (str/join "\n"
    ["[\"@m#21\" :kind \"list\"]"
     "[\"@m#22\" :kind \"symbol\"] [\"@m#22\" :v \"defn\"] [\"@m#21\" :f0 \"@m#22\"]"
     "[\"@m#23\" :kind \"symbol\"] [\"@m#23\" :v \"dec\"] [\"@m#21\" :f1 \"@m#23\"]"
     "[\"@m#24\" :kind \"list\"] [\"@m#21\" :f2 \"@m#24\"]"
     "[\"@m#25\" :kind \"symbol\"] [\"@m#25\" :v \"#%brackets\"] [\"@m#24\" :f0 \"@m#25\"]"
     "[\"@m#26\" :kind \"symbol\"] [\"@m#26\" :v \"x\"] [\"@m#24\" :f1 \"@m#26\"]"
     "[\"@m#27\" :kind \"symbol\"] [\"@m#27\" :v \":-\"] [\"@m#24\" :f2 \"@m#27\"]"
     "[\"@m#28\" :kind \"symbol\"] [\"@m#28\" :v \"Int\"] [\"@m#24\" :f3 \"@m#28\"]"
     "[\"@m#29\" :kind \"symbol\"] [\"@m#29\" :v \":-\"] [\"@m#21\" :f3 \"@m#29\"]"
     "[\"@m#30\" :kind \"symbol\"] [\"@m#30\" :v \"Int\"] [\"@m#21\" :f4 \"@m#30\"]"
     "[\"@m#31\" :kind \"list\"] [\"@m#21\" :f5 \"@m#31\"]"
     "[\"@m#32\" :kind \"symbol\"] [\"@m#32\" :v \"-\"] [\"@m#31\" :f0 \"@m#32\"]"
     "[\"@m#33\" :kind \"symbol\"] [\"@m#33\" :v \"x\"] [\"@m#31\" :f1 \"@m#33\"]"
     "[\"@m#34\" :kind \"number\"] [\"@m#34\" :v \"1\"] [\"@m#31\" :f2 \"@m#34\"]"]))

(let [res (m2-adapter/apply-m2 inc-current m2-0B-good "mod.bclj")]
  (if (:error res) (println "0B-good ADAPTER ERROR:" (:error res))
    (let [g (gate (:lines res) "0Bgood")]
      (println "=== 0B-good (additive dec, inc preserved) ===")
      (println "gate exit" (:exit g) "|" (:out g))
      (println "CLJ:\n" (:clj g)))))

;; --- 0B BAD: model adds dec with a type-wrong body (returns Bool not Int) ---
;; (- x 1) replaced with (> x 1) → :- Int declared, body Bool. Gate MUST reject.
(def m2-0B-bad
  (str/join "\n"
    ["[\"@m#21\" :kind \"list\"]"
     "[\"@m#22\" :kind \"symbol\"] [\"@m#22\" :v \"defn\"] [\"@m#21\" :f0 \"@m#22\"]"
     "[\"@m#23\" :kind \"symbol\"] [\"@m#23\" :v \"dec\"] [\"@m#21\" :f1 \"@m#23\"]"
     "[\"@m#24\" :kind \"list\"] [\"@m#21\" :f2 \"@m#24\"]"
     "[\"@m#25\" :kind \"symbol\"] [\"@m#25\" :v \"#%brackets\"] [\"@m#24\" :f0 \"@m#25\"]"
     "[\"@m#26\" :kind \"symbol\"] [\"@m#26\" :v \"x\"] [\"@m#24\" :f1 \"@m#26\"]"
     "[\"@m#27\" :kind \"symbol\"] [\"@m#27\" :v \":-\"] [\"@m#24\" :f2 \"@m#27\"]"
     "[\"@m#28\" :kind \"symbol\"] [\"@m#28\" :v \"Int\"] [\"@m#24\" :f3 \"@m#28\"]"
     "[\"@m#29\" :kind \"symbol\"] [\"@m#29\" :v \":-\"] [\"@m#21\" :f3 \"@m#29\"]"
     "[\"@m#30\" :kind \"symbol\"] [\"@m#30\" :v \"Int\"] [\"@m#21\" :f4 \"@m#30\"]"
     "[\"@m#31\" :kind \"list\"] [\"@m#21\" :f5 \"@m#31\"]"
     "[\"@m#32\" :kind \"symbol\"] [\"@m#32\" :v \">\"] [\"@m#31\" :f0 \"@m#32\"]"
     "[\"@m#33\" :kind \"symbol\"] [\"@m#33\" :v \"x\"] [\"@m#31\" :f1 \"@m#33\"]"
     "[\"@m#34\" :kind \"number\"] [\"@m#34\" :v \"1\"] [\"@m#31\" :f2 \"@m#34\"]"]))
(let [res (m2-adapter/apply-m2 inc-current m2-0B-bad "mod.bclj")]
  (if (:error res) (println "0B-bad ADAPTER ERROR:" (:error res))
    (let [g (gate (:lines res) "0Bbad")]
      (println "\n=== 0B-BAD (type-wrong dec) — gate MUST reject ===")
      (println "gate exit" (:exit g) "(expect non-zero) |" (:out g)))))

;; --- reference error: model refers to @m#999 not in state ---
(let [res (m2-adapter/apply-m2 inc-current
            "[\"@m#999\" :v \"oops\"]" "mod.bclj")]
  (println "\n=== reference error case ===")
  (println "adapter result:" (if (:error res) (:error res) "(no error — wired)")))
