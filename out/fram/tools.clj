(ns fram.tools
  (:require [fram.kernel :as k]
            [fram.query :as q]
            [clojure.string :as str]))

(defn- at [id]
  (if (string? id) (if (str/starts-with? id "@") id (str "@" id)) id))

(defn- distinct-preds [claims]
  (vec (sort (reduce (fn [acc c] (conj acc (:p c))) #{} claims))))

(defn- ^Boolean ref-pred? [claims ^String pred]
  (loop [cs claims]
  (if (empty? cs) false (let [c (first cs)]
  (if (and (= (:p c) pred) (string? (:r c)) (str/starts-with? (:r c) "@")) true (recur (rest cs)))))))

(defn- ^Boolean all-ref? [claims ^String pred]
  (loop [cs claims
   seen false]
  (if (empty? cs) seen (let [c (first cs)]
  (if (= (:p c) pred) (if (and (string? (:r c)) (str/starts-with? (:r c) "@")) (recur (rest cs) true) false) (recur (rest cs) seen))))))

(defn ref-value [claims ^String pred value]
  (if (all-ref? claims pred) (at value) value))

(defn- pred-tools [claims ^String pred]
  (let [single (k/single? pred)
   ref (ref-pred? claims pred)
   id-param [{:name "id" :type "string" :required true}]
   idv-param [{:name "id" :type "string" :required true} {:name "value" :type "string" :required true}]
   reads (if single [{:name (str pred "-of") :desc (str "Get the " pred " of <id> (single-valued).") :params id-param :op :one :pred pred}] [{:name (str pred "-list") :desc (str "List the " pred " values of <id>.") :params id-param :op :many :pred pred}])
   revs (if ref [{:name (str pred "-from") :desc (str "Entities whose " pred " points at <id> (reverse edge).") :params id-param :op :revfrom :pred pred}] [])
   writes (if single [{:name (str "set-" pred) :desc (str "Set the " pred " of <id> to <value> (replaces; single-valued).") :params idv-param :op :set :pred pred}] [{:name (str "add-" pred) :desc (str "Add <value> to the " pred " of <id>.") :params idv-param :op :add :pred pred} {:name (str "remove-" pred) :desc (str "Remove <value> from the " pred " of <id>.") :params idv-param :op :remove :pred pred}])]
  (vec (concat reads (concat revs writes)))))

(defn- dedupe-by-name [specs]
  (loop [ss specs
   seen #{}
   out []]
  (if (empty? ss) out (let [s (first ss)
   nm (:name s)]
  (if (contains? seen nm) (recur (rest ss) seen out) (recur (rest ss) (conj seen nm) (conj out s)))))))

(defn catalog [claims]
  (let [structural [{:name "threads" :desc "List all threads (entities with a title) as {id,title}." :params [] :op :threads :pred ""} {:name "show" :desc "All claims about <id>." :params [{:name "id" :type "string" :required true}] :op :show :pred ""} {:name "dependents-of" :desc "Threads that depend_on <id> (reverse depends_on)." :params [{:name "id" :type "string" :required true}] :op :dependents :pred ""} {:name "validate" :desc "Structural integrity violations (cycles, dangling refs) across all threads." :params [] :op :validate :pred ""} {:name "query" :desc (str "Ad-hoc recursive query for multi-hop questions no named tool covers. " "Pass a structured Datalog-shaped object: " "{:find <rel> :rules [{:head {:rel R :args [terms]} :body [{:rel r :args [terms] :neg <bool>}]}]}. " "A term is {:var \"x\"} or a constant; base relations are triple(l,p,r) and claim(cid,l,p,r).") :params [{:name "query" :type "object" :required true}] :op :query :pred ""}]
   per-pred (reduce (fn [acc pred] (vec (concat acc (pred-tools claims pred)))) [] (distinct-preds claims))]
  (dedupe-by-name (vec (concat structural per-pred)))))

(defn- spec-by-name [cat ^String name]
  (loop [cs cat]
  (if (empty? cs) nil (if (= (:name (first cs)) name) (first cs) (recur (rest cs))))))

(defn- missing-req [op args]
  (let [need-id (or (= op :one) (or (= op :many) (or (= op :revfrom) (or (= op :show) (or (= op :dependents) (or (= op :set) (or (= op :add) (= op :remove))))))))
   need-val (or (= op :set) (or (= op :add) (= op :remove)))
   e1 (if (and need-id (nil? (:id args))) ["missing required param 'id'"] [])
   e2 (if (and need-val (nil? (:value args))) ["missing required param 'value'"] [])
   e3 (if (and (= op :query) (nil? (:query args))) ["missing required param 'query'"] [])]
  (vec (concat e1 (concat e2 e3)))))

(defn call [claims idx cat ^String tool args]
  (let [spec (spec-by-name cat tool)]
  (if (nil? spec) {:error [(str "unknown tool '" tool "' — call `tools` for the catalog")]} (let [op (:op spec)
   pred (:pred spec)
   miss (missing-req op args)]
  (if (not (empty? miss)) {:error miss} (let [id (:id args)
   value (:value args)
   te (at id)
   rv (ref-value claims pred value)]
  (cond
  (= op :one) {:rows (let [v (k/one-i idx te pred)]
  (if (some? v) [v] []))}
  (= op :many) {:rows (k/many-i idx te pred)}
  (= op :revfrom) {:rows (reduce (fn [acc c] (if (and (= (:p c) pred) (= (:r c) te)) (conj acc (:l c)) acc)) [] claims)}
  (= op :threads) {:rows (mapv (fn [t] {:id t :title (k/one-i idx t "title")}) (k/thread-ids-i idx))}
  (= op :show) {:rows (mapv (fn [c] {:pred (:p c) :value (:r c)}) (k/q-by-l claims te))}
  (= op :dependents) {:rows (k/dependents-i idx te)}
  (= op :validate) {:rows (reduce (fn [acc t] (vec (concat acc (mapv (fn [v] {:thread t :violation v}) (k/violations-i idx t))))) [] (k/thread-ids-i idx))}
  (= op :query) (q/run claims (:query args))
  (= op :set) {:write {:op "assert" :l te :p pred :r rv}}
  (= op :add) {:write {:op "assert" :l te :p pred :r rv}}
  (= op :remove) {:write {:op "retract" :l te :p pred :r rv}}
  :else {:error [(str "unhandled op for tool '" tool "'")]})))))))
