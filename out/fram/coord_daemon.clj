(ns fram.coord-daemon
  (:require [fram.types :as t]
            [fram.cnf :as c]
            [fram.schema :as s]
            [fram.kernel :as ck]
            [fram.query :as q]
            [clojure.string :as str]))

(defrecord Idx [triples by-pr by-lp])

(defn idx-triples [r] (:triples r))

(defn idx-by-pr [r] (:by-pr r))

(defn idx-by-lp [r] (:by-lp r))

(def schema-preds #{"name" "cardinality" "value_kind" "cnf-supersedes"})

(def resolve-preds #{"refers_to" "keep_spelling" "qualifier" "ctor_prefix" "accessor_field" "supersedes"})

(def read-hidden-preds #{"bound_to"})

(defn module-of-name [nm]
  (if (string? nm) (let [s nm
   m (re-matches #"@([^#]+)#\d+" s)]
  (if (some? m) (nth m 1) nil)) nil))

(defn ^Boolean ref-shape? [^String s]
  (and (> (count s) 1) (str/starts-with? s "@") (nil? (re-find #"\s" s))))

(defn kind-of [r]
  (if (string? r) (let [s r]
  (if (ref-shape? s) :link :assert)) :assert))

(defn ^Boolean ast-pred-str? [p]
  (if (string? p) (let [s p]
  (or (contains? #{"kind" "v" "child" "tail" "style" "placement"} s) (some? (re-matches #"f\d+(?:\.\d+)*~(?:\d+|PENDING)" s)) (some? (re-matches #"(?:f|seg|comment)\d+" s)))) false))

(defn allocate-positions [asserts]
  (mapv (fn [op] (let [te (nth op 0)
   p (nth op 1)
   r (nth op 2)]
  (if (and (string? p) (str/ends-with? (let [ps p]
  ps) "~PENDING")) (let [ps p
   rs (str r)
   mm (re-matches #"@[^#]+#(\d+)" rs)
   tie (if (some? mm) (nth mm 1) "0")]
  [te (str (subs ps 0 (- (count ps) (count "PENDING"))) tie) r]) op))) asserts))

(defn claim->triple [st cid]
  (let [cl (c/claim-of st cid)]
  (if (some? cl) (let [pid (:p cl)
   pstr (c/literal st pid)]
  (if (or (contains? schema-preds pstr) (contains? resolve-preds pstr) (contains? read-hidden-preds pstr)) nil (let [lid (:l cl)
   rid (:r cl)]
  [(s/name-of st lid) pstr (if (c/value-object? st rid) (c/literal st rid) (s/name-of st rid))]))) nil)))

(defn reified->claims [c0]
  (let [st (:store c0)]
  (vec (keep (fn [cid] (let [tr (claim->triple st cid)]
  (if (some? tr) (let [a (nth tr 0)
   b (nth tr 1)
   d (nth tr 2)]
  (ck/->Claim a b d)) nil))) (c/current-claims st)))))

(defn lp-live-triples [c0 ^String te ^String p]
  (let [st (:store c0)
   lid (s/resolve-name st te)
   pid (c/value-id st p)]
  (if (and (some? lid) (some? pid) (not (contains? schema-preds p))) (let [l lid
   pp pid]
  (set (keep (fn [cid] (claim->triple st cid)) (c/by-lp st l pp)))) #{})))

(defn ^Idx idx-build [claims]
  (reduce (fn [acc cl] (let [l (:l cl)
   p (:p cl)
   r (:r cl)
   tr [l p r]]
  (->Idx (conj (:triples acc) tr) (update (:by-pr acc) [p r] (fn [o] (conj (or o #{}) tr))) (update (:by-lp acc) [l p] (fn [o] (conj (or o #{}) tr)))))) (->Idx #{} {} {}) claims))

(defn bucket-update [m k v]
  (let [nb (disj (get m k #{}) v)]
  (if (empty? nb) (dissoc m k) (assoc m k nb))))

(defn ^Idx idx-add [^Idx idx tr]
  (let [l (nth tr 0)
   p (nth tr 1)
   r (nth tr 2)]
  (->Idx (conj (:triples idx) tr) (update (:by-pr idx) [p r] (fn [o] (conj (or o #{}) tr))) (update (:by-lp idx) [l p] (fn [o] (conj (or o #{}) tr))))))

(defn ^Idx idx-del [^Idx idx tr]
  (let [l (nth tr 0)
   p (nth tr 1)
   r (nth tr 2)]
  (->Idx (disj (:triples idx) tr) (bucket-update (:by-pr idx) [p r] tr) (bucket-update (:by-lp idx) [l p] tr))))

(defn ^Boolean var-term? [t]
  (if (map? t) (contains? (let [m t]
  m) :var) false))

(defn unify1 [arg val s]
  (if (var-term? arg) (let [k (:var (let [m arg]
  m))
   b (get s k :coord-daemon/none)]
  (if (= b :coord-daemon/none) (assoc s k val) (if (= b val) s nil))) (if (= arg val) s nil)))

(defn unify-tuple [args tup s]
  (if (not= (count args) (count tup)) nil (loop [a (seq args)
   tt (seq tup)
   acc s]
  (cond
  (nil? acc) nil
  (empty? a) acc
  :else (recur (rest a) (rest tt) (unify1 (first a) (first tt) (let [m acc]
  m)))))))

(defn resolve-arg [arg s]
  (if (var-term? arg) (get s (:var (let [m arg]
  m)) :coord-daemon/unbound) arg))

(defn lit-candidates [^Idx idx litt s]
  (let [args (:args (let [m litt]
  m))
   p (resolve-arg (nth args 1) s)
   r (resolve-arg (nth args 2) s)]
  (if (and (not= p :coord-daemon/unbound) (not= r :coord-daemon/unbound)) (get (:by-pr idx) [p r] []) (:triples idx))))

(defn eval-body-idx [^Idx idx body]
  (reduce (fn [substs litt] (reduce (fn [acc s] (reduce (fn [a tup] (let [args (:args (let [m litt]
  m))
   s2 (unify-tuple args (let [tv tup]
  tv) s)]
  (if (some? s2) (conj a s2) a))) acc (lit-candidates idx litt s))) [] substs)) [{}] body))

(defn ground-head [args s]
  (mapv (fn [t] (if (var-term? t) (get s (:var (let [m t]
  m))) t)) args))

(defn ^Boolean simple-query? [q]
  (if (map? q) (let [qm q]
  (if (and (not (contains? qm :strata)) (vector? (:rules qm)) (= 1 (count (let [rs (:rules qm)]
  rs)))) (let [rule (first (let [rs (:rules qm)]
  rs))]
  (if (map? rule) (let [rm rule
   body (:body rm)
   head (:head rm)]
  (and (vector? body) (not (empty? (let [bv body]
  bv))) (if (map? head) (not= (:rel (let [hm head]
  hm)) "triple") false) (every? (fn [l] (if (map? l) (let [lm l]
  (and (= "triple" (:rel lm)) (not= (:neg lm) true) (vector? (:args lm)) (= 3 (count (let [av (:args lm)]
  av))))) false)) (let [bv body]
  bv)))) false)) false)) false))

(defn idx-run [^Idx idx q]
  (let [errs (q/validate q)]
  (if (not (empty? errs)) {:error errs} (let [qm q
   rule (first (let [rs (:rules qm)]
  rs))
   substs (eval-body-idx idx (let [b (:body rule)]
  b))
   head (:head rule)
   hargs (:args head)
   tuples (reduce (fn [acc sb] (conj acc (ground-head hargs sb))) #{} substs)]
  {:ok (vec tuples)}))))

(defn all-violations [idx]
  (vec (mapcat (fn [te] (mapv (fn [v] (str (subs te 1) ": " v)) (ck/violations-i idx te))) (ck/thread-ids-i idx))))

(defn next-module-int [st ^String module]
  (let [NAME (c/value-id st "name")
   pfx (str "@" module "#")
   mx (if (some? NAME) (let [nameid NAME]
  (reduce (fn [acc cid] (let [rid (c/claim-r st cid)
   nm (if (some? rid) (c/literal st rid) nil)]
  (if (string? nm) (let [snm nm]
  (if (str/starts-with? snm pfx) (let [mm (re-matches #"@[^#]+#(\d+)" snm)]
  (if (some? mm) (max acc (let [d (or (parse-long (nth mm 1)) 0)]
  d)) acc)) acc)) acc))) 0 (c/by-p st nameid))) 0)]
  (inc mx)))

(defn global-max-name-int [st]
  (let [NAME (c/value-id st "name")]
  (if (some? NAME) (let [nameid NAME]
  (reduce (fn [acc cid] (let [rid (c/claim-r st cid)
   nm (if (some? rid) (c/literal st rid) nil)]
  (if (string? nm) (let [snm nm
   mm (re-matches #"@[^#]+#(\d+)" snm)]
  (if (some? mm) (max acc (let [d (or (parse-long (nth mm 1)) 0)]
  d)) acc)) acc))) 0 (c/by-p st nameid))) 0)))

(defn module-node-ids [st mods]
  (let [NAME (c/value-id st "name")]
  (if (some? NAME) (let [nameid NAME]
  (set (keep (fn [cid] (let [rid (c/claim-r st cid)
   lid (c/claim-l st cid)
   nm (if (some? rid) (c/literal st rid) nil)
   m (module-of-name nm)]
  (if (and (some? m) (some? lid) (contains? mods (let [ms m]
  ms))) lid nil))) (c/by-p st nameid)))) nil)))

(defn node-path [psi node]
  (loop [n node
   acc []]
  (let [pr (get psi n)]
  (if (some? pr) (let [prv pr
   par (nth prv 0)
   slot (nth prv 1)]
  (recur par (conj acc slot))) (vec (reverse acc))))))
