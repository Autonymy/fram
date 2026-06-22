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

(defn- persist-bound-for-rename! [spec]
  (ensure-refers!)
  (let [co0 (deref cd/co)
   st (:store co0)
   REFp (c/value-id st "refers_to")
   B (target-node {:module (:module spec) :name (:old spec)})]
  (if (some? B) (if (some? REFp) (let [Bn B
   REFpn REFp
   B-name0 (s/name-of st Bn)]
  (if (some? B-name0) (let [B-name B-name0
   BND (or (c/value-id st "bound_to") (c/value! st "bound_to"))
   v0 (cd/cur-seq)
   already (set (keep (fn [cid] (let [c0 (c/claim-of st cid)]
  (if (some? c0) (let [l (:l c0)]
  l) nil))) (c/by-p st BND)))
   ref-leaves (vec (distinct (keep (fn [cid] (let [c0 (c/claim-of st cid)]
  (if (some? c0) (if (= Bn (:r c0)) (let [l (:l c0)]
  l) nil) nil))) (c/by-p st REFpn))))]
  (doseq [leaf ref-leaves]
  (let [lnm (s/name-of st leaf)]
  (if (and (not (contains? already leaf)) (some? lnm)) (cd/do-assert! lnm "bound_to" B-name v0) nil)))) nil)) nil) nil)))

(defn do-edit-min! [spec]
  (let [module (:module spec)]
  (if (str/blank? (let [ms module]
  ms)) (throw (ex-info "edit-min: :module required" {})) nil)
  (let [op (:op spec)]
  (if (not (contains? #{"set-body" "upsert-form" "insert-form" "insert-comment" "rename"} op)) (throw (ex-info (str "edit-min: unknown verb '" op "' (known: set-body, upsert-form, insert-form, insert-comment, rename)") {:reject :unknown-verb})) nil))
  (let [mod module
   op (:op spec)
   co0 (deref cd/co)
   real (:store co0)
   clone (atom (deref real))
   since (:next-id (let [c0 (deref clone)]
  c0))
   scope? (contains? #{"set-body" "upsert-form" "insert-form"} op)
   scope (if scope? (fn [str0] (str/includes? str0 mod)) (let [x nil]
  x))
   _res (binding [r/*reject!* (fn [code] (throw (ex-info (str "verb rejected the edit (code " code ")") {:reject :verb :code code})))
   r/*capture-only?* true
   r/*resolve-walk?* false
   r/*corpus-scope* scope
   r/*corpus-cache* (if scope? (ensure-corpus-groups! mod) (let [x nil]
  x))]
  (r/run-verb-warm! clone spec))
   m (deref clone)
   sup-pid (:supersedes-pred m)
   since-ids (vec (range (inc since) (inc (:next-id m))))
   name-of* (fn [eid] (s/name-of clone eid))
   new-eids (vec (filter (fn [id] (and (contains? (:objects m) id) (not (contains? (:values m) id)) (not (contains? (:claims m) id)) (nil? (name-of* id)))) since-ids))
   name-ints (cd/reserve-name-ints! (count new-eids))
   eid->name (into (let [e {}]
  e) (map-indexed (fn [i eid] [eid (str "@" mod "#" (nth name-ints i))]) new-eids))
   wire-name (fn [eid] (let [nm (get eid->name eid)]
  (if (some? nm) nm (name-of* eid))))
   ->wire (fn [cl] (let [l (:l cl)
   p (c/literal clone (let [pid (:p cl)]
  pid))
   r (:r cl)
   te (wire-name l)
   rs (if (c/value-object? clone r) (c/literal clone r) (wire-name r))]
  (if (and (some? te) (some? rs)) [te p rs] (let [x nil]
  x))))
   new-cid-claims (vec (keep (fn [cid] (let [cl (get (:claims m) cid)]
  (if (some? cl) [cid cl] (let [x nil]
  x)))) since-ids))
   new-claims (vec (filter (fn [cl] (cd/ast-pred-str? (c/literal clone (let [pid (:p cl)]
  pid)))) (map (fn [pr] (let [cl (nth pr 1)]
  cl)) new-cid-claims)))
   asserts (vec (keep ->wire new-claims))
   victim-cids (vec (keep (fn [pr] (let [cl (nth pr 1)]
  (if (= (:p cl) sup-pid) (let [r0 (:r cl)]
  r0) (let [x nil]
  x)))) new-cid-claims))
   retracts (vec (keep (fn [vcid] (let [vcl (get (:claims m) vcid)]
  (if (some? vcl) (if (cd/ast-pred-str? (c/literal clone (let [pid (:p vcl)]
  pid))) (->wire vcl) (let [x nil]
  x)) (let [y nil]
  y)))) victim-cids))]
  (cd/with-dlock! (fn [] (if (= "rename" op) (persist-bound-for-rename! spec) nil)
  (let [v0 (cd/cur-seq)
   asserts2 (cd/allocate-positions asserts)
   rej (atom (let [x nil]
  x))]
  (binding [cd/*flat-batch* (atom (let [v []]
  v))]
  (reduce (fn [_acc trip] (if (some? (deref rej)) (reduced _acc) (let [te (nth trip 0)
   p (nth trip 1)
   r (nth trip 2)
   res (cd/do-retract! te p r v0)]
  (if (some? (:reject res)) (reset! rej {:op :retract :te te :p p :r r :res res}) nil)))) (let [n0 nil]
  n0) retracts)
  (let [leaf? (fn [trip] (contains? #{"kind" "v"} (nth trip 1)))
   ordered (vec (concat (filter leaf? asserts2) (remove leaf? asserts2)))]
  (reduce (fn [_acc trip] (if (some? (deref rej)) (reduced _acc) (let [te (nth trip 0)
   p (nth trip 1)
   r (nth trip 2)
   res (cd/do-assert! te p r v0)]
  (if (some? (:reject res)) (reset! rej {:op :assert :te te :p p :r r :res res}) nil)))) (let [n0 nil]
  n0) ordered))
  (cd/flush-flat-batch!))
  (let [rj (deref rej)]
  (if (some? rj) {:reject (:res rj) :failed-op rj :module mod} {:ok true :module mod :asserts (count asserts2) :retracts (count retracts) :ops (+ (count asserts2) (count retracts)) :new-nodes (count new-eids) :name-ints name-ints :version (cd/cur-seq)}))))))))
