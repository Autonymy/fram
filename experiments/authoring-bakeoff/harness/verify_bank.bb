#!/usr/bin/env bb
(load-file "m2_adapter.bb")
(require '[clojure.edn :as edn] '[clojure.string :as str] '[babashka.process :as p])
(def bank (edn/read-string (slurp "/home/tom/code/fram/experiments/authoring-bakeoff/prompts/bank.edn")))
(def scratch "/home/tom/code/fram/experiments/authoring-bakeoff/scratch/bank-verify")
(p/sh "rm" "-rf" scratch) (p/sh "mkdir" "-p" scratch)

(defn render-m2-tuple [t]
  (let [h (first t)]
    (if (#{"RETRACT" "ASSERT"} h)
      (let [[verb id pred obj] t] (format "[%s \"%s\" :%s \"%s\"]" verb id pred obj))
      (let [[id pred obj] t] (format "[\"%s\" :%s \"%s\"]" id pred obj)))))

(defn empty-current []
  (let [f (str scratch "/empty.bclj")]
    (spit f "#lang beagle/clj\n")
    {:lines (str/split-lines (:out (p/sh "racket" "/home/tom/code/beagle/beagle-lib/private/claims-roundtrip.rkt" "--emit-edn" f))) :name->id {}}))

(defn build [dump tag]
  (let [f (str scratch "/" tag ".edn")]
    (spit f (str (str/join "\n" dump) "\n"))
    (let [r (p/sh "/home/tom/code/beagle/bin/beagle-build-all" "--build-edn" f "--out" (str scratch "/o-" tag))]
      {:exit (:exit r) :out (str/trim (str (:out r) (:err r)))})))

;; Only the from-scratch / additive-on-empty-ish examples here (E1 E2 E4 E5 E7) +
;; E3/E9/E10 which need a 1-fn state we can build from the bank's STATE text.
(doseq [{:keys [id m2]} (filter #(#{"E1" "E2" "E4" "E5" "E7"} (:id %)) bank)]
  (let [text (str/join "\n" (map render-m2-tuple m2))
        res (m2-adapter/apply-m2 (empty-current) text "mod.bclj")]
    (if (:error res)
      (println (format "%-4s ADAPTER-ERROR %s" id (:error res)))
      (let [g (build (:lines res) id)]
        (println (format "%-4s gate-exit=%d %s" id (:exit g)
                         (if (str/includes? (:out g) "0 error") "OK" (str "FAIL: " (:out g)))))))))
