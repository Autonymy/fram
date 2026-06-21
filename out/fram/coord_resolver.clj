(ns fram.coord-resolver
  (:require [fram.types :as t]
            [fram.cnf :as c]
            [fram.schema :as s]
            [clojure.string :as str]
            [clojure.set :as set]
            [fram.coord-daemon :as cd]
            [resolve :as r]))

(defn- drop-victims-int [m victims]
  (reduce-kv (fn [acc k cids] (let [kept (vec (remove (fn [c] (contains? victims c)) cids))]
  (if (empty? kept) acc (assoc acc k kept)))) (let [e {}]
  e) m))

(defn- drop-victims-vec [m victims]
  (reduce-kv (fn [acc k cids] (let [kept (vec (remove (fn [c] (contains? victims c)) cids))]
  (if (empty? kept) acc (assoc acc k kept)))) (let [e {}]
  e) m))

(defn strip-resolve-claims! [ctx subj-keep?]
  (let [s (deref ctx)
   vint (:val-intern s)
   rp-ids (set (keep (fn [nm] (get vint nm)) cd/resolve-preds))
   entries (vec (:claims s))
   victims (set (keep (fn [e] (let [cid (first e)
   cl (nth e 1)
   pid (:p cl)
   lid (:l cl)]
  (if (and (contains? rp-ids pid) (if (nil? subj-keep?) true (let [f subj-keep?]
  (f lid)))) cid nil))) entries))]
  (if (empty? victims) 0 (do
  (swap! ctx (fn [st] (-> st (update :claims (fn [m] (reduce (fn [mm v] (dissoc mm v)) m victims))) (update :tx-of (fn [m] (reduce (fn [mm v] (dissoc mm v)) m victims))) (update :objects (fn [m] (reduce (fn [mm v] (dissoc mm v)) m victims))) (update :superseded (fn [m] (reduce (fn [mm v] (dissoc mm v)) m victims))) (update :idx-by-l (fn [m] (drop-victims-int m victims))) (update :idx-by-p (fn [m] (drop-victims-int m victims))) (update :idx-by-r (fn [m] (drop-victims-int m victims))) (update :idx-by-lp (fn [m] (drop-victims-vec m victims))) (update :idx-by-pr (fn [m] (drop-victims-vec m victims))))))
  (count victims)))))

(defn restore-seq-space! [ctx before]
  (swap! ctx (fn [s] (assoc s :next-seq (:next-seq before) :supersedes-pred (:supersedes-pred before) :txs (:txs before)))))

(defn with-resolve-read* [store thunk]
  (binding [r/ctx store
   r/Vp (c/value-id store "v")
   r/KIND (c/value-id store "kind")
   r/REFERS (c/value-id store "refers_to")
   r/FIXED (c/value-id store "keep_spelling")
   r/QUAL (c/value-id store "qualifier")
   r/CTOR (c/value-id store "ctor_prefix")
   r/ACC (c/value-id store "accessor_field")
   r/file->ents (atom {})
   r/srcs []
   r/file-modframe {}
   r/file-typeframe {}
   r/file-accessors {}
   r/global-exports {}
   r/global-type-exports {}
   r/global-accessor-exports {}]
  (r/corpus-from-store!)
  (thunk)))

(defn materialize-refers-whole! []
  (let [co0 (deref cd/co)
   st (:store co0)
   before (deref st)
   stripped (strip-resolve-claims! st nil)
   walk-info (atom (let [x nil]
  x))]
  (r/resolve-warm-store! st (fn [] (reset! walk-info {:forms-walked (deref r/n-forms-walked) :modules-walked (deref r/walked-modules)})))
  (restore-seq-space! st before)
  (let [wi (deref walk-info)]
  {:stripped stripped :forms-walked (:forms-walked wi) :modules-walked (:modules-walked wi)})))

(defn snapshot-exports! [st]
  (reset! cd/export-snapshot (let [snap (with-resolve-read* st (fn [] (let [ss r/srcs]
  (into {} (mapv (fn [m] [(let [ms m]
  ms) (r/module-export-set m)]) (filterv (fn [m] (some? m)) ss))))))]
  snap)))

(defn classify-affected [dirty snapshot]
  (let [ig (r/import-graph)
   consumers (fn [m] (set (keep (fn [e] (let [s (first e)
   imps (nth e 1)]
  (if (contains? imps m) s nil))) (vec ig))))
   macro? (boolean (some (fn [m] (r/module-has-macro? m)) dirty))
   changed (set (filterv (fn [m] (not= (r/module-export-set m) (get snapshot m :coord-resolver/absent))) (vec dirty)))
   affected (reduce (fn [acc m] (into acc (consumers m))) dirty (vec changed))]
  {:affected affected :macro? macro? :export-changed changed}))

(defn materialize-refers-scoped! []
  (let [co0 (deref cd/co)
   st (:store co0)
   before (deref st)
   dirty (deref cd/dirty-modules)
   cls (with-resolve-read* st (fn [] (classify-affected dirty (deref cd/export-snapshot))))
   affected (:affected cls)
   macro? (:macro? cls)
   export-changed (:export-changed cls)]
  (if macro? (let [whole (materialize-refers-whole!)]
  (snapshot-exports! st)
  (reset! cd/dirty-modules (let [e #{}]
  e))
  {:mode :whole-macro-fallback :walked :all :stripped (:stripped whole) :export-changed export-changed}) (let [keep-ids (cd/module-node-ids st affected)
   scope-set (if (some? keep-ids) keep-ids (let [e #{}]
  e))
   stripped (strip-resolve-claims! st (fn [lid] (contains? scope-set lid)))
   walk-info (atom (let [x nil]
  x))]
  (r/resolve-modules! st affected (fn [] (reset! walk-info {:forms-walked (deref r/n-forms-walked) :modules-walked (deref r/walked-modules)})))
  (restore-seq-space! st before)
  (snapshot-exports! st)
  (reset! cd/dirty-modules (let [e #{}]
  e))
  (let [wi (deref walk-info)]
  {:mode :scoped :walked affected :stripped stripped :export-changed export-changed :forms-walked (:forms-walked wi) :modules-walked (:modules-walked wi)})))))

(defn ensure-refers! []
  (cond
  (not (deref cd/materialized?)) (let [rr (materialize-refers-whole!)
   co0 (deref cd/co)]
  (snapshot-exports! (:store co0))
  (reset! cd/dirty-modules (let [e #{}]
  e))
  (reset! cd/materialized? true)
  (reset! cd/refers-version (cd/cur-seq))
  (reset! cd/last-materialize (let [x (assoc rr :mode :whole-cold :walked :all)]
  x)))
  (not (empty? (deref cd/dirty-modules))) (let [rr (materialize-refers-scoped!)]
  (reset! cd/refers-version (cd/cur-seq))
  (reset! cd/last-materialize (let [x rr]
  x)))
  :else nil))

(defn ensure-corpus-groups! [^String module]
  (let [cg0 (deref cd/corpus-groups)]
  (if (or (nil? cg0) (not (contains? (let [m cg0]
  m) module))) (with-resolve-read* (:store (let [c (deref cd/co)]
  c)) (fn [] (reset! cd/corpus-groups (let [fe (deref r/file->ents)]
  fe)))) nil))
  (deref cd/corpus-groups))

(defn invalidate-corpus-groups! []
  (reset! cd/corpus-groups (let [x nil]
  x)))

(defn- render-ref-name [L]
  (let [ctx r/ctx
   FIXED r/FIXED
   nm (r/binding-name (or (r/refers-target L) 0))
   cpfx (r/pred-val L "ctor_prefix")
   afield (r/pred-val L "accessor_field")
   qual (r/pred-val L "qualifier")
   fixed? (not (empty? (c/by-lp ctx L FIXED)))]
  (cond
  fixed? (r/sym-val L)
  (some? cpfx) (str cpfx nm)
  (some? afield) (str (str/lower-case (let [ns nm]
  ns)) "-" afield)
  (some? qual) (str qual "/" nm)
  :else nm)))

(defn target-node [req]
  (let [co0 (deref cd/co)
   st (:store co0)]
  (cond
  (some? (:te req)) (let [n (s/resolve-name st (let [te (:te req)]
  te))]
  (if (some? n) (let [u (with-resolve-read* st (fn [] (r/ultimate n)))]
  (let [ui u]
  ui)) nil))
  (and (some? (:module req)) (some? (:name req))) (let [b (with-resolve-read* st (fn [] (r/def-binding (:module req) (:name req))))]
  (let [bi b]
  bi))
  :else nil)))

(defn callers-of-in-store [st B]
  (if (some? B) (let [bb B]
  (with-resolve-read* st (fn [] (let [ctx r/ctx
   REFERS r/REFERS]
  (set (keep (fn [cid] (let [cl (c/claim-of ctx cid)
   L (:l cl)]
  (if (= bb (r/ultimate (:r cl))) [(r/name->module (s/name-of ctx L)) (render-ref-name L)] nil))) (c/by-p ctx REFERS))))))) (let [e #{}]
  e)))

(defn- parent-slot-index [st]
  (let [m (deref st)]
  (reduce (fn [acc e] (let [cl (nth e 1)
   rr (:r cl)
   pstr (c/literal st (let [pid (:p cl)]
  pid))]
  (if (and (integer? rr) (string? pstr) (let [ps pstr]
  (or (boolean (r/ord-pos? ps)) (some? (re-matches #"seg\d+" ps)) (some? (re-matches #"comment\d+" ps)) (= ps "tail")))) (assoc acc (let [r2 rr]
  r2) [(:l cl) pstr]) acc))) (let [e0 {}]
  e0) (vec (let [cm (:claims m)]
  cm)))))

(defn refers-keyset [st]
  (with-resolve-read* st (fn [] (let [ctx r/ctx
   REFERS r/REFERS
   psi (parent-slot-index st)]
  (set (keep (fn [cid] (let [cl (c/claim-of ctx cid)
   L (:l cl)
   D (r/ultimate (:r cl))]
  [(r/name->module (s/name-of ctx L)) (render-ref-name L) (cd/node-path psi L) (r/name->module (s/name-of ctx (let [dd D]
  dd))) (r/binding-name (let [d2 D]
  d2))])) (c/by-p ctx REFERS)))))))

(defn refers-keyset-resp! []
  (let [co0 (deref cd/co)
   st (:store co0)
   scoped (refers-keyset st)
   clone (atom (deref st))
   _strip (strip-resolve-claims! clone nil)
   _walk (r/resolve-warm-store! clone)
   ground (refers-keyset clone)
   symdiff (set/union (set/difference scoped ground) (set/difference ground scoped))]
  {:scoped-size (count scoped) :ground-size (count ground) :symdiff-size (count symdiff) :symdiff (vec (take 40 symdiff)) :version (cd/cur-seq)}))
