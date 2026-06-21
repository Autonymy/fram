(ns fram.cnf
  (:require [fram.types :as t]))

(defn new-store []
  (atom (t/->Store 0 0 nil {} {} {} {} {} {} {} {} {} {} {} {})))

(defn fresh-id! [ctx]
  (:next-id (swap! ctx update :next-id inc)))

(defn entity! [ctx]
  (let [id (fresh-id! ctx)]
  (swap! ctx update :objects assoc id true)
  id))

(defn value! [ctx v]
  (let [vi (let [s (deref ctx)]
  (:val-intern s))]
  (if (contains? vi v) (get vi v) (let [id (fresh-id! ctx)]
  (swap! ctx update :objects assoc id true)
  (swap! ctx update :values assoc id v)
  (swap! ctx update :val-intern assoc v id)
  id))))

(defn ^Boolean value-object? [ctx id]
  (let [s (deref ctx)]
  (contains? (:values s) id)))

(defn literal [ctx id]
  (let [s (deref ctx)]
  (get (:values s) id)))

(defn value-id [ctx v]
  (let [s (deref ctx)]
  (get (:val-intern s) v)))

(defn begin-tx! [ctx agent]
  (let [tx (fresh-id! ctx)
   seq (:next-seq (swap! ctx update :next-seq inc))]
  (swap! ctx update :txs assoc tx {:seq seq :agent agent})
  tx))

(defn tx-seq [ctx tx]
  (let [s (deref ctx)
   m (get (:txs s) tx)]
  (if (some? m) (:seq m) 0)))

(defn set-supersedes-pred! [ctx pid]
  (swap! ctx assoc :supersedes-pred pid))

(defn- index-claim! [ctx cid l p r]
  (swap! ctx update :idx-by-l update l (fn [o] (conj (or o []) cid)))
  (swap! ctx update :idx-by-p update p (fn [o] (conj (or o []) cid)))
  (swap! ctx update :idx-by-r update r (fn [o] (conj (or o []) cid)))
  (swap! ctx update :idx-by-lp update [l p] (fn [o] (conj (or o []) cid)))
  (swap! ctx update :idx-by-pr update [p r] (fn [o] (conj (or o []) cid))))

(defn claim! [ctx l p r tx]
  (let [cid (fresh-id! ctx)]
  (swap! ctx update :objects assoc cid true)
  (swap! ctx update :claims assoc cid {:l l :p p :r r})
  (swap! ctx update :tx-of assoc cid tx)
  (index-claim! ctx cid l p r)
  (if (= p (let [s (deref ctx)]
  (:supersedes-pred s))) (do
  (swap! ctx update :superseded assoc r true)))
  cid))

(defn claim-of [ctx cid]
  (let [s (deref ctx)]
  (get (:claims s) cid)))

(defn claim-tx [ctx cid]
  (let [s (deref ctx)]
  (get (:tx-of s) cid)))

(defn ^Boolean live? [ctx cid]
  (let [s (deref ctx)]
  (not (contains? (:superseded s) cid))))

(defn- live-only [ctx cids]
  (filterv (fn [c] (live? ctx c)) cids))

(defn by-l [ctx l]
  (live-only ctx (let [s (deref ctx)]
  (get (:idx-by-l s) l []))))

(defn by-p [ctx p]
  (live-only ctx (let [s (deref ctx)]
  (get (:idx-by-p s) p []))))

(defn by-r [ctx r]
  (live-only ctx (let [s (deref ctx)]
  (get (:idx-by-r s) r []))))

(defn by-lp [ctx l p]
  (live-only ctx (let [s (deref ctx)]
  (get (:idx-by-lp s) [l p] []))))

(defn by-pr [ctx p r]
  (live-only ctx (let [s (deref ctx)]
  (get (:idx-by-pr s) [p r] []))))

(defn current-claims [ctx]
  (live-only ctx (let [s (deref ctx)]
  (vec (keys (:claims s))))))

(defn dump-store [ctx]
  (let [s (deref ctx)]
  {:next-id (:next-id s) :next-seq (:next-seq s) :supersedes-pred (:supersedes-pred s) :objects (vec (keys (:objects s))) :values (vec (:values s)) :claims (vec (:claims s)) :tx-of (vec (:tx-of s)) :txs (vec (:txs s)) :superseded (vec (keys (:superseded s)))}))

(defn load-store! [ctx data]
  (swap! ctx (fn [old] (t/->Store (:next-id data) (:next-seq data) (:supersedes-pred data) {} {} {} {} {} {} {} {} {} {} {} {})))
  (doseq [id (:objects data)]
  (swap! ctx update :objects assoc id true))
  (doseq [e (:values data)]
  (let [id (first e)
   v (nth e 1)]
  (swap! ctx update :values assoc id v)
  (swap! ctx update :val-intern assoc v id)))
  (doseq [e (:claims data)]
  (let [cid (first e)
   m (nth e 1)]
  (swap! ctx update :claims assoc cid m)
  (index-claim! ctx cid (:l m) (:p m) (:r m))))
  (doseq [e (:tx-of data)]
  (swap! ctx update :tx-of assoc (first e) (nth e 1)))
  (doseq [e (:txs data)]
  (swap! ctx update :txs assoc (first e) (nth e 1)))
  (doseq [cid (:superseded data)]
  (swap! ctx update :superseded assoc cid true))
  ctx)

(defn current-seq [ctx]
  (let [s (deref ctx)]
  (:next-seq s)))

(defn next-id [ctx]
  (let [s (deref ctx)]
  (:next-id s)))

(defn claim-l [ctx cid]
  (let [m (claim-of ctx cid)]
  (if (some? m) (:l m) nil)))

(defn claim-p [ctx cid]
  (let [m (claim-of ctx cid)]
  (if (some? m) (:p m) nil)))

(defn claim-r [ctx cid]
  (let [m (claim-of ctx cid)]
  (if (some? m) (:r m) nil)))

(defn claim-lpr [ctx cid]
  (let [m (claim-of ctx cid)]
  (if (some? m) [(:l m) (:p m) (:r m)] [])))

(defn records-since [ctx since txid]
  (let [s (deref ctx)
   valss (vec (:values s))
   clmss (vec (:claims s))
   objss (vec (keys (:objects s)))
   vals (reduce (fn [acc e] (let [id (first e)]
  (if (>= id since) (conj acc {:k :value :id id :v (nth e 1)}) acc))) [] valss)
   ents (reduce (fn [acc id] (if (and (>= id since) (not (contains? (:values s) id)) (not (contains? (:claims s) id))) (conj acc {:k :entity :id id}) acc)) [] objss)
   clms (reduce (fn [acc e] (let [cid (first e)
   mm (nth e 1)]
  (if (>= cid since) (conj acc {:k :claim :cid cid :l (:l mm) :p (:p mm) :r (:r mm) :tx (get (:tx-of s) cid)}) acc))) [] clmss)
   txm (get (:txs s) txid)
   txrec {:k :tx :tx txid :seq (if (some? txm) (:seq txm) 0) :agent (if (some? txm) (:agent txm) nil)}]
  (vec (concat vals ents clms [txrec {:k :commit :tx txid}]))))

(defn all-records [ctx]
  (let [s (deref ctx)
   valss (vec (:values s))
   clmss (vec (:claims s))
   txss (vec (:txs s))
   objss (vec (keys (:objects s)))
   vals (mapv (fn [e] {:k :value :id (first e) :v (nth e 1)}) valss)
   ents (reduce (fn [acc id] (if (and (not (contains? (:values s) id)) (not (contains? (:claims s) id))) (conj acc {:k :entity :id id}) acc)) [] objss)
   clms (mapv (fn [e] (let [cid (first e)
   mm (nth e 1)]
  {:k :claim :cid cid :l (:l mm) :p (:p mm) :r (:r mm) :tx (get (:tx-of s) cid)})) clmss)
   txs (mapv (fn [e] (let [tx (first e)
   tm (nth e 1)]
  {:k :tx :tx tx :seq (:seq tm) :agent (:agent tm)})) txss)]
  (vec (concat vals ents clms txs))))
