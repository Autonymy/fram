(ns chelonia.export
  (:require [chelonia.kernel :as k]
            [clojure.string :as str]
            [chelonia.rt :as rt]))

(def order ["title" "owner" "lead" "driver" "source" "proposed_by" "created_by" "created_at" "updated_at" "committed" "do_on" "valid_until" "estimate_hours" "repo" "part_of" "depends_on" "relates_to" "outcome" "abandoned"])

(defn- distinct-s [xs]
  (reduce (fn [acc x] (if (k/vec-contains? acc x) acc (conj acc x))) [] xs))

(defn- ^String render-obj [^String v]
  (cond
  (str/starts-with? v "@") v
  (or (str/blank? v) (str/includes? v " ") (str/includes? v "\t") (str/starts-with? v "\"")) (chelonia.rt/edn-quote v)
  :else v))

(defn ^String thread-md [claims ^String te]
  (let [present (distinct-s (mapv (fn [c] (:p c)) (k/q-by-l claims te)))
   ordered (filterv (fn [p] (k/vec-contains? present p)) order)
   extra (vec (sort (filterv (fn [p] (and (not (k/vec-contains? order p)) (not (= p "body")))) present)))
   preds (vec (concat ordered extra))
   lines (reduce (fn [acc p] (vec (concat acc (mapv (fn [v] (str p "  " (render-obj v))) (k/many claims te p))))) [] preds)
   b (k/one claims te "body")
   body (if (some? b) b "")]
  (str te "\n" (str/join "\n" lines) "\n---\n" body)))
