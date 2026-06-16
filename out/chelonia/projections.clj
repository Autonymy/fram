(ns chelonia.projections
  (:require [chelonia.kernel :as k]))

(defn incomplete-deps [idx ^String te]
  (filterv (fn [d] (and (some? (k/one-i idx d "title")) (not (k/terminal-i? idx d)))) (k/many-i idx te "depends_on")))

(defn ^Boolean blocked? [idx ^String te]
  (not (empty? (incomplete-deps idx te))))

(defn ready [idx]
  (filterv (fn [te] (and (not (k/terminal-i? idx te)) (not (blocked? idx te)))) (k/work-thread-ids-i idx)))

(defn blocked [idx]
  (filterv (fn [te] (and (not (k/terminal-i? idx te)) (blocked? idx te))) (k/work-thread-ids-i idx)))

(defn ^String condition-i [idx ^String te]
  (cond
  (some? (k/one-i idx te "driver")) "active"
  (some? (k/one-i idx te "committed")) (if (blocked? idx te) "blocked" "ready")
  :else "draft"))

(defn transitive-dependents [idx ^String te]
  (loop [frontier (k/dependents-i idx te)
   seen []]
  (if (empty? frontier) seen (let [x (first frontier)
   rest-f (vec (rest frontier))]
  (if (k/vec-contains? seen x) (recur rest-f seen) (recur (vec (concat rest-f (k/dependents-i idx x))) (conj seen x)))))))

(defn leverage-score [idx ^String te]
  (count (filterv (fn [d] (and (not (= d te)) (not (k/terminal-i? idx d)))) (transitive-dependents idx te))))
