#!/usr/bin/env bb
(load-file "m2_adapter.bb")
(require '[babashka.process :as p] '[clojure.string :as str])
(def scratch "/home/tom/code/fram/experiments/authoring-bakeoff/scratch/adapter-test")
(p/sh "rm" "-rf" scratch) (p/sh "mkdir" "-p" scratch)

(defn gate [dump-lines tag]
  (let [f (str scratch "/" tag ".edn")]
    (spit f (str (str/join "\n" dump-lines) "\n"))
    (let [r (p/sh "/home/tom/code/beagle/bin/beagle-build-all" "--build-edn" f "--out" (str scratch "/out-" tag))]
      {:exit (:exit r) :out (str (:out r) (:err r))})))

;; --- 0A: square from empty (model emits R3 flat EDN, :head-less keyword form) ---
(def m2-0A
  (str/join "\n"
    ["[\"@m#1\" :kind \"list\"]"
     "[\"@m#2\" :kind \"symbol\"] [\"@m#2\" :v \"defn\"] [\"@m#1\" :f0 \"@m#2\"]"
     "[\"@m#3\" :kind \"symbol\"] [\"@m#3\" :v \"square\"] [\"@m#1\" :f1 \"@m#3\"]"
     "[\"@m#4\" :kind \"list\"] [\"@m#1\" :f2 \"@m#4\"]"
     "[\"@m#5\" :kind \"symbol\"] [\"@m#5\" :v \"#%brackets\"] [\"@m#4\" :f0 \"@m#5\"]"
     "[\"@m#6\" :kind \"symbol\"] [\"@m#6\" :v \"x\"] [\"@m#4\" :f1 \"@m#6\"]"
     "[\"@m#7\" :kind \"symbol\"] [\"@m#7\" :v \":-\"] [\"@m#4\" :f2 \"@m#7\"]"
     "[\"@m#8\" :kind \"symbol\"] [\"@m#8\" :v \"Int\"] [\"@m#4\" :f3 \"@m#8\"]"
     "[\"@m#9\" :kind \"symbol\"] [\"@m#9\" :v \":-\"] [\"@m#1\" :f3 \"@m#9\"]"
     "[\"@m#10\" :kind \"symbol\"] [\"@m#10\" :v \"Int\"] [\"@m#1\" :f4 \"@m#10\"]"
     "[\"@m#11\" :kind \"list\"] [\"@m#1\" :f5 \"@m#11\"]"
     "[\"@m#12\" :kind \"symbol\"] [\"@m#12\" :v \"*\"] [\"@m#11\" :f0 \"@m#12\"]"
     "[\"@m#13\" :kind \"symbol\"] [\"@m#13\" :v \"x\"] [\"@m#11\" :f1 \"@m#13\"]"
     "[\"@m#14\" :kind \"symbol\"] [\"@m#14\" :v \"x\"] [\"@m#11\" :f2 \"@m#14\"]"]))

;; Need a current graph that already has the wrapper + define-target, even for empty.
;; The empty program = wrapper(beagle-file) + (define-target clj), no forms.
(def empty-current
  {:lines ["@file mod.bclj"
           "[1 \"kind\" \"list\"]"
           "[2 \"kind\" \"symbol\"] [2 \"v\" \"beagle-file\"] [1 \"f0\" 2]"
           "[3 \"kind\" \"list\"] [1 \"f1\" 3]"
           "[4 \"kind\" \"symbol\"] [4 \"v\" \"define-target\"] [3 \"f0\" 4]"
           "[5 \"kind\" \"symbol\"] [5 \"v\" \"clj\"] [3 \"f1\" 5]"]
   :name->id {}})

(let [res (m2-adapter/apply-m2 empty-current m2-0A "mod.bclj")]
  (if (:error res)
    (println "0A ADAPTER ERROR:" (:error res))
    (let [g (gate (:lines res) "0A")]
      (println "0A gate exit" (:exit g))
      (println (:out g)))))
