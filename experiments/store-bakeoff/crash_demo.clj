#!/usr/bin/env bb
;; crash_demo.clj — AXIS 6 made empirical, not just reasoned.
;; Shows: (B/per-log) a torn TRAILING line on power-loss mid-append is dropped by the fold
;; while all prior records survive; (A/per-file) an in-flight atomic write is a .tmp that is
;; invisible to readers, so a torn claim can NEVER be read. The two safety stories of the
;; two file-based candidates, demonstrated.
(require '[clojure.string :as str] '[clojure.edn :as edn] '[babashka.fs :as fs]
         '[clojure.java.io :as io])

;; --- B / per-log: torn trailing line ---------------------------------------
(def d "/dev/shm/torn-b") (fs/delete-tree d) (fs/create-dirs d)
(def logp (str d "/w.log"))
(spit logp (str (pr-str {:id "0001" :l "@a" :p "title" :r "v1"}) "\n"
                (pr-str {:id "0002" :l "@a" :p "title" :r "v2"}) "\n"
                "{:id \"0003\" :l \"@a\" :p \"titl"))         ; power-loss mid-append => torn line
(defn safe-load [p]
  (->> (slurp p) str/split-lines (remove str/blank?)
       (keep (fn [ln] (try (edn/read-string ln) (catch Exception _ nil))))))  ; drop torn partial
(let [claims (safe-load logp)]
  (assert (= 2 (count claims)))
  (println "B per-log: parsed" (count claims) "of 3; trailing partial DROPPED, prior intact =>" (mapv :r claims)))

;; --- A / per-file: in-flight temp is invisible -----------------------------
(def a "/dev/shm/torn-a") (fs/delete-tree a) (fs/create-dirs a)
(spit (str a "/.0003.edn.tmp") "{:id \"0003\" :l \"@a\" :p \"titl")           ; in-flight, never renamed
(spit (str a "/0001.edn") (pr-str {:id "0001" :l "@a" :p "title" :r "v1"}))
(let [edns (->> (.listFiles (io/file a))
                (filter #(str/ends-with? (.getName %) ".edn"))
                (remove #(str/starts-with? (.getName %) ".")))]
  (assert (= 1 (count edns)))
  (println "A per-file: visible .edn =" (count edns) "(the .tmp in-flight write is invisible; zero torn reads ever)"))
(println "crash_demo: both assertions green")
