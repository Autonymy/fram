#!/usr/bin/env bb
(require '[babashka.process :as p] '[clojure.string :as str])
(load-file "m2_adapter.bb")
(def BEAGLE "/home/tom/code/beagle/bin/beagle-build-all")
(def ROUNDTRIP "/home/tom/code/beagle/beagle-lib/private/claims-roundtrip.rkt")
(def W "/home/tom/code/fram/experiments/authoring-bakeoff/scratch/g3")
(p/sh "rm" "-rf" W) (p/sh "mkdir" "-p" W)
(defn normalize-diag [out]
  (-> out
      (str/replace #"(?m)^\s*\S+?(?::\d+)?: beagle:" "  module: beagle:")
      (str/replace #"(?m)^\s*\S+?: type errors" "  module: type errors")
      (str/replace #"/[^\s:]+\.bclj" "module.bclj")
      str/trim))

;; M1/M1.5 TEXT path: a type-wrong module
(def bad-text "#lang beagle/clj\n(defn inc [x :- Int] :- Int (+ x 1))\n(defn dec [x :- Int] :- Int (> x 1))\n")
(spit (str W "/bad.bclj") bad-text)
(def text-gate (p/sh BEAGLE (str W "/bad.bclj") "--out" (str W "/o-text")))
(def text-diag (normalize-diag (str (:out text-gate) (:err text-gate))))

;; M2 path: same logical failure as a claim delta on the inc graph
(spit (str W "/inc.bclj") "#lang beagle/clj\n(defn inc [x :- Int] :- Int (+ x 1))\n")
(def inc-current {:lines (str/split-lines (:out (p/sh "racket" ROUNDTRIP "--emit-edn" (str W "/inc.bclj")))) :name->id {}})
(def bad-m2 (str/join "\n"
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
(def m2-res (m2-adapter/apply-m2 inc-current bad-m2 "mod.bclj"))
(spit (str W "/bad.edn") (str (str/join "\n" (:lines m2-res)) "\n"))
(def m2-gate (p/sh BEAGLE "--build-edn" (str W "/bad.edn") "--out" (str W "/o-m2")))
(def m2-diag (normalize-diag (str (:out m2-gate) (:err m2-gate))))

(println "=== M1/M1.5 (text) normalized diag ===")
(println text-diag)
(println "\n=== M2 (claim-EDN) normalized diag ===")
(println m2-diag)
(println "\n=== G3 PARITY:" (if (= text-diag m2-diag) "IDENTICAL ✓" "DIVERGENT ✗") "===")
