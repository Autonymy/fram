(ns resolve
  (:require [fram.types :as t]
            [fram.cnf :as c]
            [clojure.string :as str]
            [fram.rt :as rt]))

^{:line 41 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ORD-STEP 65536)

^{:line 42 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def PARAM-FORMS ^{:line 42 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{"defn" "defn-" "fn" "defmacro" "fn*"})

^{:line 43 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def DEF-FORMS ^{:line 43 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{"def" "def-" "defonce"})

^{:line 44 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def VALUE-DEFS ^{:line 44 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into PARAM-FORMS DEF-FORMS))

^{:line 45 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def TYPE-DEFS ^{:line 45 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{"defrecord" "deftype" "defprotocol" "definterface" "defunion"})

^{:line 46 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def TYPE-COLON ^{:line 46 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{":-" ":"})

^{:line 47 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def LET-FORMS ^{:line 47 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{"let" "loop" "when-let" "if-let" "when-some" "if-some" "binding" "with-open" "with-local-vars" "dotimes" "with-redefs" "if-let*" "when-let*"})

^{:line 49 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def FOR-FORMS ^{:line 49 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{"doseq" "for"})

^{:line 50 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def MATCH-FORMS ^{:line 50 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{"match"})

^{:line 54 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic ctx nil)

^{:line 55 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic tx nil)

^{:line 56 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic SUP nil)

^{:line 58 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic *reject!* ^{:line 58 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [code] ^{:line 58 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/exit! code)))

^{:line 60 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic *resolve-walk?* true)

^{:line 61 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic *corpus-scope* nil)

^{:line 62 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic *corpus-cache* nil)

^{:line 63 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic *deleted-forms* ^{:line 63 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{})

^{:line 64 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic *deleted-subtree* ^{:line 64 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{})

^{:line 65 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic *resolve-out* nil)

^{:line 66 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic *project-srcs* nil)

^{:line 67 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic *capture-only?* false)

^{:line 69 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic file->ents ^{:line 69 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (atom ^{:line 69 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {}))

^{:line 71 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic Vp nil)

^{:line 71 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic KIND nil)

^{:line 71 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic REFERS nil)

^{:line 72 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic BOUND nil)

^{:line 72 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic FIXED nil)

^{:line 72 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic QUAL nil)

^{:line 73 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic CTOR nil)

^{:line 73 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic ACC nil)

^{:line 75 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic n-resolved ^{:line 75 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (atom 0))

^{:line 75 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic n-unresolved ^{:line 75 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (atom 0))

^{:line 76 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic n-xmod ^{:line 76 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (atom 0))

^{:line 76 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic n-type ^{:line 76 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (atom 0))

^{:line 77 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic n-comment ^{:line 77 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (atom 0))

^{:line 78 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic n-forms-walked ^{:line 78 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (atom 0))

^{:line 78 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic walked-modules ^{:line 78 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (atom ^{:line 78 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{}))

^{:line 80 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic *xresolve* ^{:line 80 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [nm] nil))

^{:line 81 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic *tresolve* ^{:line 81 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [nm] nil))

^{:line 82 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic *aresolve* ^{:line 82 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [nm] nil))

^{:line 84 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic srcs ^{:line 84 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])

^{:line 85 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic file-modframe ^{:line 85 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {})

^{:line 86 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic file-typeframe ^{:line 86 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {})

^{:line 87 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic file-accessors ^{:line 87 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {})

^{:line 88 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic global-exports ^{:line 88 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {})

^{:line 89 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic global-type-exports ^{:line 89 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {})

^{:line 90 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def ^:dynamic global-accessor-exports ^{:line 90 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {})

^{:line 96 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn ^String load-edn! [^String path]
  ^{:line 97 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [lines ^{:line 97 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/split-lines ^{:line 97 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/slurp path))
   src ^{:line 98 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (subs ^{:line 98 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [l ^{:line 98 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ^{:line 98 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filter ^{:line 98 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [l] ^{:line 98 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/starts-with? l "@file")) lines))]
  l) 6)
   local ^{:line 99 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (atom ^{:line 99 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {})
   ent ^{:line 100 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [lid] ^{:line 101 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [r ^{:line 101 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 101 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get ^{:line 101 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (deref local) lid) ^{:line 102 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [e ^{:line 102 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/entity! ctx)]
  ^{:line 103 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (swap! local ^{:line 103 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [m] ^{:line 103 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (assoc m lid e)))
  ^{:line 104 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (swap! file->ents ^{:line 104 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [m] ^{:line 105 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (update m src ^{:line 105 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [o] ^{:line 105 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (conj ^{:line 105 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or o ^{:line 105 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []) e)))))
  e))]
  r))]
  ^{:line 108 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [line lines]
  ^{:line 109 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 109 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/starts-with? line "[") ^{:line 109 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 110 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [[s p o] ^{:line 110 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/parse-edn line)]
  ^{:line 111 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx ^{:line 111 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ent s) ^{:line 111 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx p) ^{:line 111 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 111 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (integer? o) ^{:line 111 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ent o) ^{:line 111 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx o)) tx)))))
  src))

^{:line 123 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn select-main-1 [cids]
  ^{:line 123 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first cids))

^{:line 125 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn pred-val [e ^String pname]
  ^{:line 126 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [P ^{:line 126 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value-id ctx pname)]
  ^{:line 127 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if P ^{:line 127 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 127 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [cs ^{:line 127 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/by-lp ctx e P)]
  ^{:line 128 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [cid ^{:line 128 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (select-main-1 cs)]
  ^{:line 128 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if cid ^{:line 128 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 129 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/literal ctx ^{:line 129 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [r ^{:line 129 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 129 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim-r ctx cid) 0)]
  r))))))))))

^{:line 130 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn kind-of [e]
  ^{:line 130 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (pred-val e "kind"))

^{:line 131 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn sym-val [e]
  ^{:line 131 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 131 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "symbol" ^{:line 131 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of e)) ^{:line 131 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 131 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (pred-val e "v"))))

^{:line 139 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn ord-parse [p]
  ^{:line 140 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 140 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (string? p) ^{:line 140 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 141 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [m ^{:line 141 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (re-matches #"f(\d+(?:\.\d+)*)~(\d+)" p)]
  ^{:line 142 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 142 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? m) ^{:line 143 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [ps ^{:line 143 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth m 1)
   ts ^{:line 143 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth m 2)]
  ^{:line 144 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {:path ^{:line 144 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapv ^{:line 144 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 144 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 144 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (parse-long x) 0)) ^{:line 144 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/split ps #"\.")) :tie ^{:line 144 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 144 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (parse-long ts) 0)}) ^{:line 146 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [m2 ^{:line 146 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (re-matches #"f(\d+)" p)]
  ^{:line 147 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 147 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? m2) ^{:line 148 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {:path ^{:line 148 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 148 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (* ^{:line 148 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (inc ^{:line 148 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 148 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (parse-long ^{:line 148 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth m2 1)) 0)) ORD-STEP)] :tie 0} nil)))))))

^{:line 150 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn ^Boolean ord-pos? [p]
  ^{:line 150 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (boolean ^{:line 150 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ord-parse p)))

^{:line 151 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn ^String ord-str [path tie]
  ^{:line 151 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "f" ^{:line 151 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/join "." path) "~" tie))

^{:line 152 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn ord-veccmp [a b]
  ^{:line 153 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (loop [a ^{:line 153 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (seq a)
   b ^{:line 153 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (seq b)]
  ^{:line 154 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 154 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and ^{:line 154 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nil? a) ^{:line 154 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nil? b)) 0
  ^{:line 155 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nil? a) -1
  ^{:line 156 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nil? b) 1
  :else ^{:line 157 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [c ^{:line 157 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (compare ^{:line 157 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first a) ^{:line 157 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first b))]
  ^{:line 158 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 158 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (zero? c) ^{:line 158 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 158 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (next a) ^{:line 158 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (next b)) c)))))

^{:line 159 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn ord-cmp [x y]
  ^{:line 160 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [c ^{:line 160 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ord-veccmp ^{:line 160 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:path x) ^{:line 160 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:path y))]
  ^{:line 161 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 161 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (zero? c) ^{:line 161 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (compare ^{:line 161 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:tie x) ^{:line 161 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:tie y)) c)))

^{:line 162 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn ord-append [last-path]
  ^{:line 163 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 163 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (empty? last-path) ^{:line 164 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [ORD-STEP] ^{:line 165 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (conj ^{:line 165 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 165 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (butlast last-path)) ^{:line 165 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [n ^{:line 165 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (+ ^{:line 165 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [l ^{:line 165 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 165 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (last last-path) 0)]
  l) ORD-STEP)]
  n))))

^{:line 166 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn ord-between [lo hi]
  ^{:line 167 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 167 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and ^{:line 167 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nil? lo) ^{:line 167 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nil? hi)) ^{:line 167 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [ORD-STEP]
  ^{:line 168 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nil? hi) ^{:line 168 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ord-append ^{:line 168 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [l ^{:line 168 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or lo ^{:line 168 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])]
  l))
  :else ^{:line 169 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [lo ^{:line 169 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or lo ^{:line 169 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [0])]
  ^{:line 170 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (loop [i 0
   acc ^{:line 170 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []]
  ^{:line 171 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [a ^{:line 171 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get lo i 0)
   b ^{:line 172 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get hi i ^{:line 172 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (+ a ^{:line 172 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (* 2 ORD-STEP)))]
  ^{:line 173 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 173 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (> ^{:line 173 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [d ^{:line 173 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (- b a)]
  d) 1) ^{:line 174 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (conj acc ^{:line 174 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (quot ^{:line 174 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [s ^{:line 174 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (+ a b)]
  s) 2)) ^{:line 175 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 175 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (inc i) ^{:line 175 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (conj acc a))))))))

^{:line 177 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn ordered-children [e]
  ^{:line 178 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (->> ^{:line 178 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/by-l ctx e) ^{:line 179 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keep ^{:line 179 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [cid] ^{:line 180 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [k ^{:line 180 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ord-parse ^{:line 180 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/literal ctx ^{:line 180 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [p ^{:line 180 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 180 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim-p ctx cid) 0)]
  p)))]
  ^{:line 180 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if k ^{:line 180 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 181 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [k ^{:line 181 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [r ^{:line 181 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 181 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim-r ctx cid) 0)]
  r)]))))) ^{:line 182 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sort-by first ord-cmp) ^{:line 183 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapv second)))

^{:line 184 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn ordered-segs [e]
  ^{:line 185 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (->> ^{:line 185 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/by-l ctx e) ^{:line 186 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keep ^{:line 186 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [cid] ^{:line 187 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [p ^{:line 187 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/literal ctx ^{:line 187 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [pp ^{:line 187 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 187 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim-p ctx cid) 0)]
  pp))]
  ^{:line 188 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 188 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and ^{:line 188 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (string? p) ^{:line 188 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (re-matches #"seg\d+" p)) ^{:line 188 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 189 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 189 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (parse-long ^{:line 189 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (subs p 3)) ^{:line 189 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [r ^{:line 189 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 189 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim-r ctx cid) 0)]
  r)]))))) ^{:line 190 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sort-by first) ^{:line 191 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapv second)))

^{:line 192 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn head-sym [e]
  ^{:line 193 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 193 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "list" ^{:line 193 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of e)) ^{:line 193 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 194 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [cs ^{:line 194 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children e)]
  ^{:line 195 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 195 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not ^{:line 195 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (empty? cs)) ^{:line 195 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 195 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val ^{:line 195 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [c ^{:line 195 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first cs)]
  c))))))))

^{:line 196 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn bound-target
  "The DURABLE identity target of reference `L` — the bound_to edge points at the binding's\n   stable @mod#int node-id, not its spelling. nil if L carries no durable edge (legacy/unedged\n   refs fall back to the spelling-derived refers_to)." [L]
  ^{:line 201 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if BOUND ^{:line 201 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 202 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [cs ^{:line 202 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/by-lp ctx L BOUND)]
  ^{:line 203 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [cid ^{:line 203 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (select-main-1 cs)]
  ^{:line 203 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if cid ^{:line 203 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 203 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim-r ctx cid))))))))

^{:line 204 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn refers-target
  "The binding node reference `L` resolves to (default-main view). Prefers the DURABLE bound_to\n   identity edge; falls back to the derived refers_to (spelling-walk) for unedged/legacy refs.\n   Not a uniqueness proof (select-main-1)." [L]
  ^{:line 209 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 209 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (bound-target L) ^{:line 210 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [cs ^{:line 210 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/by-lp ctx L REFERS)]
  ^{:line 211 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [cid ^{:line 211 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (select-main-1 cs)]
  ^{:line 211 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if cid ^{:line 211 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 211 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim-r ctx cid)))))))

^{:line 212 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn live-node? [e]
  ^{:line 212 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (seq ^{:line 212 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/by-lp ctx e KIND)))

^{:line 215 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn ^Boolean brackets? [e]
  ^{:line 215 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "#%brackets" ^{:line 215 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (head-sym e)))

^{:line 216 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn ^Boolean map-node? [e]
  ^{:line 216 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "#%map" ^{:line 216 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (head-sym e)))

^{:line 219 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn collect-bind-syms [node]
  ^{:line 220 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 221 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val node) ^{:line 221 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [v ^{:line 221 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val node)]
  ^{:line 222 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 222 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 222 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{"&" "_"} v) ^{:line 222 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [] ^{:line 222 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [node]))
  ^{:line 223 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? node) ^{:line 223 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat collect-bind-syms ^{:line 223 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 223 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children node)))
  ^{:line 224 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (map-node? node) ^{:line 225 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (loop [ks ^{:line 225 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 225 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children node))
   acc ^{:line 225 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []]
  ^{:line 226 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 226 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (empty? ks) acc ^{:line 227 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [k ^{:line 227 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ks)
   kv ^{:line 227 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val k)
   v ^{:line 227 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second ks)]
  ^{:line 228 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 229 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 229 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{":keys" ":strs" ":syms"} kv) ^{:line 230 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 230 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 ks) ^{:line 230 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into acc ^{:line 230 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 230 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and v ^{:line 230 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? v)) ^{:line 230 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 231 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filterv sym-val ^{:line 231 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 231 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children v)))))))
  ^{:line 232 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= ":as" kv) ^{:line 232 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 232 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 ks) ^{:line 232 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into acc ^{:line 232 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (collect-bind-syms v)))
  ^{:line 233 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= ":or" kv) ^{:line 233 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 233 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 ks) acc)
  ^{:line 234 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val k) ^{:line 234 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 234 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 ks) ^{:line 234 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (conj acc k))
  :else ^{:line 235 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 235 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 ks) ^{:line 235 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into acc ^{:line 235 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (collect-bind-syms k)))))))
  ^{:line 240 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "list" ^{:line 240 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of node)) ^{:line 241 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat collect-bind-syms ^{:line 242 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (take-while ^{:line 242 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 242 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not ^{:line 242 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? TYPE-COLON ^{:line 242 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val x)))) ^{:line 243 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children node)))
  :else ^{:line 244 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []))

^{:line 246 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn collect-or-vals [node]
  ^{:line 247 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 248 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (map-node? node) ^{:line 249 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (loop [ks ^{:line 249 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 249 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children node))
   acc ^{:line 249 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []]
  ^{:line 250 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 250 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (empty? ks) acc ^{:line 251 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [k ^{:line 251 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ks)
   kv ^{:line 251 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val k)
   v ^{:line 251 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second ks)]
  ^{:line 252 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 253 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= ":or" kv) ^{:line 253 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 253 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 ks) ^{:line 253 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into acc ^{:line 253 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 253 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and v ^{:line 253 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (map-node? v)) ^{:line 253 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 254 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keep-indexed ^{:line 254 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [i c] ^{:line 255 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 255 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (odd? i) ^{:line 255 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  c))) ^{:line 256 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 256 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children v)))))))
  ^{:line 257 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 257 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{":keys" ":strs" ":syms" ":as"} kv) ^{:line 257 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 257 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 ks) acc)
  ^{:line 258 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val k) ^{:line 258 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 258 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 ks) acc)
  :else ^{:line 259 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 259 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 ks) ^{:line 259 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into acc ^{:line 259 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (collect-or-vals k)))))))
  ^{:line 260 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? node) ^{:line 260 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat collect-or-vals ^{:line 260 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 260 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children node)))
  :else ^{:line 261 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []))

^{:line 263 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn param-binds [bracket]
  ^{:line 264 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (loop [ks ^{:line 264 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 264 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children bracket))
   binds ^{:line 264 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []
   skip false]
  ^{:line 265 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 265 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (empty? ks) binds ^{:line 266 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [k ^{:line 266 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ks)
   v ^{:line 266 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val k)]
  ^{:line 267 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  skip ^{:line 267 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 267 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ks) binds false)
  ^{:line 268 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? TYPE-COLON v) ^{:line 268 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 268 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ks) binds true)
  :else ^{:line 269 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 269 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ks) ^{:line 269 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into binds ^{:line 269 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (collect-bind-syms k)) false))))))

^{:line 275 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn let-bind-pairs [bracket]
  ^{:line 276 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (loop [ks ^{:line 276 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 276 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children bracket))
   acc ^{:line 276 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []]
  ^{:line 277 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 277 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (empty? ks) acc ^{:line 278 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [pat ^{:line 278 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ks)
   after ^{:line 279 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 279 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? TYPE-COLON ^{:line 279 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val ^{:line 279 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second ks))) ^{:line 279 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 3 ks) ^{:line 279 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ks))
   val ^{:line 280 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first after)]
  ^{:line 281 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 281 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest after) ^{:line 281 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (conj acc ^{:line 281 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 281 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (collect-bind-syms pat) val ^{:line 281 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (collect-or-vals pat)]))))))

^{:line 283 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn for-bind-pairs [bracket]
  ^{:line 284 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (loop [ks ^{:line 284 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 284 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children bracket))
   acc ^{:line 284 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []]
  ^{:line 285 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 285 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (empty? ks) acc ^{:line 286 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [k ^{:line 286 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ks)
   kv ^{:line 286 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val k)
   v ^{:line 286 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second ks)]
  ^{:line 287 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 288 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 288 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{":when" ":while"} kv) ^{:line 288 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 288 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 ks) ^{:line 288 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (conj acc ^{:line 288 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [:expr v]))
  ^{:line 289 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= ":let" kv) ^{:line 289 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 289 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 ks) ^{:line 290 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into acc ^{:line 290 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 290 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and v ^{:line 290 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? v)) ^{:line 290 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 291 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapv ^{:line 291 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [p] ^{:line 292 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [s ^{:line 292 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first p)
   vn ^{:line 292 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second p)
   ov ^{:line 292 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth p 2)]
  ^{:line 293 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [:bind s vn ov])) ^{:line 294 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let-bind-pairs v))))))
  ^{:line 295 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? TYPE-COLON ^{:line 295 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val v)) ^{:line 296 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 296 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 4 ks) ^{:line 296 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (conj acc ^{:line 296 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [:bind ^{:line 296 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (collect-bind-syms k) ^{:line 296 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth ks 3 nil) ^{:line 296 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (collect-or-vals k)]))
  :else ^{:line 298 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 298 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 ks) ^{:line 298 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (conj acc ^{:line 298 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [:bind ^{:line 298 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (collect-bind-syms k) v ^{:line 298 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (collect-or-vals k)])))))))

^{:line 299 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn frame-of [bsyms]
  ^{:line 300 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into ^{:line 300 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {} ^{:line 300 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapv ^{:line 300 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [b] ^{:line 300 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 300 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val b) b]) bsyms)))

^{:line 303 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn match-pat-binds [pat]
  ^{:line 304 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 305 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val pat) ^{:line 305 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [v ^{:line 305 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val pat)]
  ^{:line 305 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 305 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 305 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{"_"} v) ^{:line 305 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [] ^{:line 305 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [pat]))
  ^{:line 306 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "list" ^{:line 306 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of pat)) ^{:line 306 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat match-pat-binds ^{:line 306 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 306 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children pat)))
  ^{:line 307 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? pat) ^{:line 307 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat match-pat-binds ^{:line 307 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 307 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children pat)))
  :else ^{:line 308 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []))

^{:line 314 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (declare walk! walk-type! walk-all! walk-fn-arity! walk-pat-heads! walk-quasi! walk-quasi-seq!)

^{:line 316 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn bind! [L target]
  ^{:line 316 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx L REFERS target tx)
  ^{:line 316 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (swap! n-resolved ^{:line 316 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 316 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (+ x 1))))

^{:line 317 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn bind-xmod! [node x]
  ^{:line 318 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 318 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and x ^{:line 318 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:target x)) ^{:line 318 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 319 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (bind! node ^{:line 319 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:target x))
  ^{:line 320 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 321 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= ^{:line 321 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:mode x) :fixed) ^{:line 321 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx node FIXED ^{:line 321 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx "1") tx)
  ^{:line 322 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= ^{:line 322 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:mode x) :qual) ^{:line 322 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx node QUAL ^{:line 322 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx ^{:line 322 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:alias x)) tx)
  :else nil)
  ^{:line 324 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 324 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:accessor x) ^{:line 324 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 325 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx node ACC ^{:line 325 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx ^{:line 325 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:accessor x)) tx)))
  ^{:line 326 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (swap! n-xmod ^{:line 326 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [n] ^{:line 326 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (+ n 1)))
  true)))

^{:line 328 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn walk-type! [node]
  ^{:line 329 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 330 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val node) ^{:line 330 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [nm ^{:line 330 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val node)]
  ^{:line 331 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 331 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [b ^{:line 331 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*tresolve* nm)]
  ^{:line 331 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if b ^{:line 331 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 331 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (bind! node b)
  ^{:line 331 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (swap! n-type ^{:line 331 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [n] ^{:line 331 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (+ n 1)))
  true))) ^{:line 332 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (bind-xmod! node ^{:line 332 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*xresolve* nm))))
  ^{:line 333 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "list" ^{:line 333 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of node)) ^{:line 333 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [c ^{:line 333 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children node)]
  ^{:line 333 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-type! c))
  ^{:line 334 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? node) ^{:line 334 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [c ^{:line 334 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 334 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children node))]
  ^{:line 334 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-type! c))
  :else nil))

^{:line 336 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn resolve-type-after-colon! [nodes]
  ^{:line 337 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (loop [xs nodes]
  ^{:line 338 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 338 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (seq xs) ^{:line 338 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 339 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 339 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? TYPE-COLON ^{:line 339 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val ^{:line 339 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first xs))) ^{:line 340 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 340 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second xs) ^{:line 340 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 340 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-type! ^{:line 340 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second xs)))) ^{:line 341 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 341 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest xs)))))))

^{:line 342 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn resolve-types-in-bracket! [bracket]
  ^{:line 343 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (loop [ks ^{:line 343 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 343 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children bracket))]
  ^{:line 344 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 344 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (seq ks) ^{:line 344 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 345 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [k ^{:line 345 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ks)]
  ^{:line 346 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 347 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? TYPE-COLON ^{:line 347 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val k)) ^{:line 347 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 347 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 347 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second ks) ^{:line 347 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 347 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-type! ^{:line 347 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second ks))))
  ^{:line 347 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 347 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 ks)))
  ^{:line 348 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "list" ^{:line 348 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of k)) ^{:line 348 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 348 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (resolve-type-after-colon! ^{:line 348 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children k))
  ^{:line 349 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 349 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ks)))
  :else ^{:line 350 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 350 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ks))))))))

^{:line 351 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn walk-all! [nodes scope]
  ^{:line 351 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [n nodes]
  ^{:line 351 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk! n scope)))

^{:line 352 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn walk-fn-arity! [forms scope]
  ^{:line 353 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [pv ^{:line 353 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ^{:line 353 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filter ^{:line 353 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [f] ^{:line 353 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? f)) forms))
   binds ^{:line 354 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if pv ^{:line 354 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (param-binds pv) ^{:line 354 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])
   _ ^{:line 355 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if pv ^{:line 355 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 355 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (resolve-types-in-bracket! pv)))
   or-vals ^{:line 356 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if pv ^{:line 356 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat collect-or-vals ^{:line 356 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 356 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children pv))) ^{:line 356 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])
   frame ^{:line 357 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (frame-of binds)
   body ^{:line 358 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (loop [xs ^{:line 358 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 358 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop-while ^{:line 358 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [f] ^{:line 358 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not ^{:line 358 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? f))) forms))]
  ^{:line 359 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 359 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 359 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{":-" ":" ":raises"} ^{:line 359 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val ^{:line 359 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first xs))) ^{:line 360 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 360 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 360 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second xs) ^{:line 360 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 360 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-type! ^{:line 360 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second xs))))
  ^{:line 360 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 360 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 xs))) xs))]
  ^{:line 362 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-all! or-vals scope)
  ^{:line 363 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-all! body ^{:line 363 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cons frame scope))))

^{:line 364 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn walk-pat-heads! [pat scope]
  ^{:line 365 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 365 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "list" ^{:line 365 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of pat)) ^{:line 365 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 366 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk! ^{:line 366 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ^{:line 366 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children pat)) scope)
  ^{:line 367 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [c ^{:line 367 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 367 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children pat))]
  ^{:line 367 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-pat-heads! c scope)))))

^{:line 368 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn walk! [node scope]
  ^{:line 369 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 370 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "symbol" ^{:line 370 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of node)) ^{:line 371 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [nm ^{:line 371 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val node)
   bt ^{:line 372 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (bound-target node)
   local ^{:line 373 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some ^{:line 373 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [m] ^{:line 373 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get m nm)) scope)]
  ^{:line 374 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  bt ^{:line 378 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (bind! node bt)
  local ^{:line 379 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (bind! node local)
  ^{:line 382 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (bind-xmod! node ^{:line 382 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*xresolve* nm)) nil
  ^{:line 383 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [b ^{:line 383 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*tresolve* nm)]
  ^{:line 383 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if b ^{:line 383 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 383 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (bind! node b)
  ^{:line 383 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (swap! n-type ^{:line 383 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [n] ^{:line 383 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (+ n 1)))
  true))) nil
  ^{:line 386 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [pfx ^{:line 386 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 386 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 386 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/starts-with? ^{:line 386 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or nm "") "map->") ^{:line 386 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/includes? ^{:line 386 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or nm "") "/map->")) "map->"
  ^{:line 387 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 387 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/starts-with? ^{:line 387 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or nm "") "->") ^{:line 387 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/includes? ^{:line 387 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or nm "") "/->")) "->"
  :else nil)]
  ^{:line 386 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if pfx ^{:line 386 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 389 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [stripped ^{:line 389 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/replace ^{:line 389 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or nm "") pfx "")]
  ^{:line 390 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 390 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [b ^{:line 390 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*tresolve* stripped)]
  ^{:line 390 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if b ^{:line 390 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 391 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (bind! node b)
  ^{:line 391 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx node CTOR ^{:line 391 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx pfx) tx)
  ^{:line 391 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (swap! n-type ^{:line 391 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [n] ^{:line 391 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (+ n 1)))
  true))) ^{:line 392 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 392 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (bind-xmod! node ^{:line 392 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*xresolve* stripped)) ^{:line 392 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 393 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx node CTOR ^{:line 393 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx pfx) tx)
  true))))))) nil
  ^{:line 396 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [a ^{:line 396 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*aresolve* nm)]
  ^{:line 396 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if a ^{:line 396 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 397 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (bind! node ^{:line 397 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first a))
  ^{:line 397 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx node ACC ^{:line 397 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx ^{:line 397 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second a)) tx)
  ^{:line 397 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (swap! n-type ^{:line 397 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [n] ^{:line 397 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (+ n 1)))
  true))) nil
  :else ^{:line 398 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (swap! n-unresolved ^{:line 398 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [n] ^{:line 398 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (+ n 1)))))
  ^{:line 399 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "list" ^{:line 399 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of node)) ^{:line 400 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [kids ^{:line 400 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children node)
   h ^{:line 400 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (head-sym node)]
  ^{:line 401 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 402 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 402 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{"quote"} h) nil
  ^{:line 403 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 403 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{"quasiquote"} h) ^{:line 403 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-quasi! node scope false)
  ^{:line 404 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? TYPE-DEFS h) ^{:line 405 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [c ^{:line 405 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 kids)]
  ^{:line 406 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 407 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? c) ^{:line 407 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (resolve-types-in-bracket! c)
  ^{:line 408 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "list" ^{:line 408 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of c)) ^{:line 409 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 409 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [b ^{:line 409 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filter ^{:line 409 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 409 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? x)) ^{:line 409 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children c))]
  ^{:line 409 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (resolve-types-in-bracket! b))
  ^{:line 411 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (resolve-type-after-colon! ^{:line 411 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 411 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop-while ^{:line 411 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 411 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not ^{:line 411 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? x))) ^{:line 411 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children c)))))
  ^{:line 414 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val c) ^{:line 414 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [b ^{:line 414 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*tresolve* ^{:line 414 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val c))]
  ^{:line 414 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 414 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and b ^{:line 414 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not= b c)) ^{:line 414 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 414 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (bind! c b)
  ^{:line 414 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (swap! n-type ^{:line 414 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [n] ^{:line 414 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (+ n 1))))))
  :else nil))
  ^{:line 416 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? DEF-FORMS h) ^{:line 417 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [after-name ^{:line 417 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 kids)]
  ^{:line 418 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 418 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= ":-" ^{:line 418 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val ^{:line 418 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first after-name))) ^{:line 419 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 419 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 419 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second after-name) ^{:line 419 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 419 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-type! ^{:line 419 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second after-name))))
  ^{:line 420 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-all! ^{:line 420 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 after-name) scope)) ^{:line 421 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-all! after-name scope)))
  ^{:line 422 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? PARAM-FORMS h) ^{:line 423 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [after-name ^{:line 423 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 423 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 423 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{"defn" "defn-" "defmacro"} h) ^{:line 423 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 kids) ^{:line 423 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest kids))]
  ^{:line 424 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 424 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some ^{:line 424 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [a] ^{:line 424 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? a)) after-name) ^{:line 425 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-fn-arity! after-name scope) ^{:line 426 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [a after-name]
  ^{:line 427 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 427 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and ^{:line 427 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "list" ^{:line 427 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of a)) ^{:line 427 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? ^{:line 427 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ^{:line 427 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children a)))) ^{:line 427 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 428 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-fn-arity! ^{:line 428 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children a) scope))))))
  ^{:line 429 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? LET-FORMS h) ^{:line 430 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [bracket ^{:line 430 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second kids)
   _ ^{:line 431 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 431 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and bracket ^{:line 431 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? bracket)) ^{:line 431 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 431 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (resolve-types-in-bracket! bracket)))
   pairs ^{:line 432 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 432 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and bracket ^{:line 432 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? bracket)) ^{:line 432 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let-bind-pairs bracket) ^{:line 432 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])
   final ^{:line 435 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (reduce ^{:line 435 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [sc pr] ^{:line 436 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [[bsyms vnode orvals] pr]
  ^{:line 437 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-all! orvals sc)
  ^{:line 437 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if vnode ^{:line 437 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 437 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk! vnode sc)))
  ^{:line 438 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cons ^{:line 438 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (frame-of bsyms) sc))) scope pairs)]
  ^{:line 440 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-all! ^{:line 440 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 kids) final))
  ^{:line 441 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? FOR-FORMS h) ^{:line 442 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [bracket ^{:line 442 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second kids)
   _ ^{:line 443 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 443 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and bracket ^{:line 443 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? bracket)) ^{:line 443 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 443 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (resolve-types-in-bracket! bracket)))
   entries ^{:line 444 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 444 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and bracket ^{:line 444 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? bracket)) ^{:line 444 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (for-bind-pairs bracket) ^{:line 444 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])
   final ^{:line 446 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (reduce ^{:line 446 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [sc e] ^{:line 447 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 447 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= :expr ^{:line 447 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first e)) ^{:line 448 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 448 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk! ^{:line 448 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second e) sc)
  sc) ^{:line 449 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [[_ bsyms vnode orvals] e]
  ^{:line 450 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-all! orvals sc)
  ^{:line 450 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if vnode ^{:line 450 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 450 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk! vnode sc)))
  ^{:line 451 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cons ^{:line 451 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (frame-of bsyms) sc)))) scope entries)]
  ^{:line 453 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-all! ^{:line 453 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 kids) final))
  ^{:line 454 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? MATCH-FORMS h) ^{:line 455 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [kids ^{:line 455 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children node)]
  ^{:line 456 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk! ^{:line 456 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second kids) scope)
  ^{:line 457 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [clause ^{:line 457 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 kids)]
  ^{:line 458 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 458 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? clause) ^{:line 458 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 459 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [cc ^{:line 459 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 459 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children clause))
   pat ^{:line 459 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first cc)
   body ^{:line 459 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest cc)]
  ^{:line 460 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-pat-heads! pat scope)
  ^{:line 461 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-all! body ^{:line 461 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cons ^{:line 461 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (frame-of ^{:line 461 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (match-pat-binds pat)) scope)))))))
  ^{:line 462 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= h "letfn") ^{:line 463 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [bracket ^{:line 463 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second kids)
   fnlists ^{:line 464 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 464 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and bracket ^{:line 464 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? bracket)) ^{:line 464 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filter ^{:line 464 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 464 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "list" ^{:line 464 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of x))) ^{:line 464 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 464 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children bracket))) ^{:line 464 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])
   frame ^{:line 465 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (frame-of ^{:line 465 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keep ^{:line 465 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [fl] ^{:line 465 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ^{:line 465 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children fl))) fnlists))
   bodyscope ^{:line 466 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cons frame scope)]
  ^{:line 467 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [fl fnlists]
  ^{:line 467 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-fn-arity! ^{:line 467 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 467 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children fl)) bodyscope))
  ^{:line 468 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-all! ^{:line 468 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 kids) bodyscope))
  ^{:line 469 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 469 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{"extend-type" "extend-protocol"} h) ^{:line 470 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [c ^{:line 470 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest kids)]
  ^{:line 471 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 472 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val c) ^{:line 472 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk! c scope)
  ^{:line 473 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "list" ^{:line 473 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of c)) ^{:line 473 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [ic ^{:line 473 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children c)]
  ^{:line 474 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk! ^{:line 474 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ic) scope)
  ^{:line 475 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-fn-arity! ^{:line 475 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ic) scope))
  :else nil))
  ^{:line 477 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= h "as->") ^{:line 478 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [init ^{:line 478 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 478 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (> ^{:line 478 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (count kids) 1) ^{:line 478 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second kids) nil)
   nm ^{:line 479 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 479 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (> ^{:line 479 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (count kids) 2) ^{:line 479 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth kids 2) nil)
   frame ^{:line 480 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (frame-of ^{:line 480 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 480 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and nm ^{:line 480 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val nm)) ^{:line 480 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [nm] ^{:line 480 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []))]
  ^{:line 481 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if init ^{:line 481 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 481 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk! init scope)))
  ^{:line 482 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-all! ^{:line 482 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 3 kids) ^{:line 482 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cons frame scope)))
  :else ^{:line 483 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-all! kids scope)))
  :else nil))

^{:line 493 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn walk-quasi! [node scope ^Boolean quoted?]
  ^{:line 494 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 495 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val node) ^{:line 496 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 496 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not quoted?) ^{:line 496 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 497 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [nm ^{:line 497 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val node)]
  ^{:line 498 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 499 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some ^{:line 499 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [m] ^{:line 499 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get m nm)) ^{:line 499 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (butlast scope)) nil
  ^{:line 500 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get ^{:line 500 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (last scope) nm) ^{:line 500 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (bind! node ^{:line 500 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get ^{:line 500 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (last scope) nm))
  ^{:line 501 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (bind-xmod! node ^{:line 501 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*xresolve* nm)) nil
  :else nil))))
  ^{:line 503 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "list" ^{:line 503 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of node)) ^{:line 504 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [h ^{:line 504 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (head-sym node)]
  ^{:line 505 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 506 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 506 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{"unquote" "unquote-splicing"} h) ^{:line 506 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-all! ^{:line 506 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 506 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children node)) scope)
  ^{:line 507 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 507 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{"quote"} h) ^{:line 507 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-quasi-seq! ^{:line 507 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children node) scope true)
  :else ^{:line 508 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-quasi-seq! ^{:line 508 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children node) scope quoted?)))
  :else nil))

^{:line 512 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn walk-quasi-seq! [children scope ^Boolean quoted?]
  ^{:line 513 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (loop [cs children]
  ^{:line 514 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 514 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (seq cs) ^{:line 514 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 515 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 515 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 515 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{"~" "," "~@" ",@"} ^{:line 515 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val ^{:line 515 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first cs))) ^{:line 516 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 516 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 516 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second cs) ^{:line 516 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 516 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk! ^{:line 516 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second cs) scope)))
  ^{:line 516 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 516 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 cs))) ^{:line 517 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 517 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-quasi! ^{:line 517 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first cs) scope quoted?)
  ^{:line 517 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 517 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest cs))))))))

^{:line 520 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn unwrap-def [form]
  ^{:line 521 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 521 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "js/export" ^{:line 521 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (head-sym form)) ^{:line 521 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second ^{:line 521 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children form)) form))

^{:line 522 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn module-defs [^String src]
  ^{:line 523 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [wrapper ^{:line 523 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some ^{:line 523 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [e] ^{:line 523 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 523 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "beagle-file" ^{:line 523 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (head-sym e)) ^{:line 523 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  e))) ^{:line 524 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [fe ^{:line 524 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (deref file->ents)]
  ^{:line 524 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get fe src)))
   forms ^{:line 525 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 525 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children wrapper))]
  ^{:line 526 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into ^{:line 526 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {} ^{:line 526 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat ^{:line 526 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [f] ^{:line 527 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [d ^{:line 527 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (unwrap-def f)]
  ^{:line 528 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 529 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? VALUE-DEFS ^{:line 529 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (head-sym d)) ^{:line 530 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [nl ^{:line 530 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second ^{:line 530 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children d))]
  ^{:line 530 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 530 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val nl) ^{:line 530 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 530 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 530 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 530 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val nl) nl]])))
  ^{:line 533 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 533 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{"defprotocol" "definterface"} ^{:line 533 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (head-sym d)) ^{:line 534 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keep ^{:line 534 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [m] ^{:line 535 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 535 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "list" ^{:line 535 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of m)) ^{:line 535 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 536 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [nl ^{:line 536 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ^{:line 536 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children m))]
  ^{:line 537 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 537 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val nl) ^{:line 537 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 537 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 537 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val nl) nl])))))) ^{:line 538 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 ^{:line 538 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children d)))
  :else nil))) forms))))

^{:line 542 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn forms-of [^String src]
  ^{:line 543 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 543 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children ^{:line 543 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some ^{:line 543 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [e] ^{:line 543 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 543 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "beagle-file" ^{:line 543 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (head-sym e)) ^{:line 543 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  e))) ^{:line 544 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [fe ^{:line 544 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (deref file->ents)]
  ^{:line 544 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get fe src))))))

^{:line 545 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn ns-form [^String src]
  ^{:line 546 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some ^{:line 546 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [f] ^{:line 546 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 546 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "ns" ^{:line 546 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (head-sym f)) ^{:line 546 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  f))) ^{:line 546 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (forms-of src)))

^{:line 547 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn module-name [^String src]
  ^{:line 548 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [nf ^{:line 548 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ns-form src)]
  ^{:line 548 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if nf ^{:line 548 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 548 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val ^{:line 548 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second ^{:line 548 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children nf)))))))

^{:line 549 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn merge-import-opts [acc modn kids]
  ^{:line 550 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [idx ^{:line 550 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [kw] ^{:line 550 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ^{:line 550 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keep-indexed ^{:line 550 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [i k] ^{:line 550 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 550 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= kw ^{:line 550 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val k)) ^{:line 550 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  i))) kids)))
   ri ^{:line 551 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (idx ":refer")
   ai ^{:line 551 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (idx ":as")
   rri ^{:line 551 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (idx ":rename")
   nb ^{:line 552 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ri ^{:line 552 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 552 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth kids ^{:line 552 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (inc ri) nil)))
   refers ^{:line 553 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 553 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and nb ^{:line 553 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? nb)) ^{:line 553 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 553 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keep sym-val ^{:line 553 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 553 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children nb)))))
   alias ^{:line 554 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ai ^{:line 554 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 554 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val ^{:line 554 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth kids ^{:line 554 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (inc ai) nil))))
   rmap ^{:line 555 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if rri ^{:line 555 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 555 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [mb ^{:line 555 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth kids ^{:line 555 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (inc rri) nil)]
  ^{:line 556 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 556 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and mb ^{:line 556 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (map-node? mb)) ^{:line 556 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 557 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (loop [cs ^{:line 557 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 557 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children mb))
   m ^{:line 557 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {}]
  ^{:line 558 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 558 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (< ^{:line 558 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (count cs) 2) m ^{:line 559 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 559 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 cs) ^{:line 559 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (assoc m ^{:line 559 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val ^{:line 559 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first cs)) ^{:line 559 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val ^{:line 559 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second cs)))))))))))
   acc1 ^{:line 560 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 560 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (seq refers) ^{:line 561 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (update acc :refer ^{:line 561 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [m] ^{:line 561 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into m ^{:line 561 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapv ^{:line 561 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [n] ^{:line 561 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [n modn]) refers)))) acc)
   acc2 ^{:line 563 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if alias ^{:line 564 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (update acc1 :as ^{:line 564 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [m] ^{:line 564 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (assoc m alias modn))) acc1)
   acc3 ^{:line 566 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 566 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (seq rmap) ^{:line 567 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (update acc2 :rename ^{:line 567 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [m] ^{:line 567 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into m ^{:line 567 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapv ^{:line 567 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [pr] ^{:line 567 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [sn ^{:line 567 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first pr)
   ln ^{:line 567 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth pr 1)]
  ^{:line 567 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [ln ^{:line 567 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [modn sn]])) ^{:line 567 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec rmap))))) acc2)]
  acc3))

^{:line 570 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn parse-require [^String src]
  ^{:line 571 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [empty ^{:line 571 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {:refer ^{:line 571 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {} :as ^{:line 571 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {} :rename ^{:line 571 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {}}
   bare ^{:line 573 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (reduce ^{:line 573 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [acc f] ^{:line 574 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 574 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "require" ^{:line 574 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (head-sym f)) ^{:line 575 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [kids ^{:line 575 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children f)]
  ^{:line 576 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (merge-import-opts acc ^{:line 576 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val ^{:line 576 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth kids 1 nil)) ^{:line 576 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 kids))) acc)) empty ^{:line 578 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (forms-of src))]
  ^{:line 579 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [nf ^{:line 579 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ns-form src)]
  ^{:line 579 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if nf ^{:line 580 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [reqs ^{:line 580 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some ^{:line 580 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [c] ^{:line 580 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 580 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and ^{:line 580 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "list" ^{:line 580 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of c)) ^{:line 581 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= ":require" ^{:line 581 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val ^{:line 581 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ^{:line 581 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children c))))) ^{:line 580 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  c))) ^{:line 582 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children nf))]
  ^{:line 580 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if reqs ^{:line 583 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (reduce ^{:line 583 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [acc spec] ^{:line 584 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 584 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not ^{:line 584 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? spec)) acc ^{:line 585 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [kids ^{:line 585 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 585 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children spec))]
  ^{:line 586 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (merge-import-opts acc ^{:line 586 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val ^{:line 586 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first kids)) ^{:line 586 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest kids))))) bare ^{:line 587 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 587 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children reqs))) bare)) bare))))

^{:line 590 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn module-exports [^String src]
  ^{:line 591 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into ^{:line 591 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {} ^{:line 591 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keep ^{:line 591 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [f] ^{:line 592 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 592 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "js/export" ^{:line 592 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (head-sym f)) ^{:line 592 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 593 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [d ^{:line 593 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second ^{:line 593 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children f))]
  ^{:line 594 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 595 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? VALUE-DEFS ^{:line 595 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (head-sym d)) ^{:line 595 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [nl ^{:line 595 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second ^{:line 595 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children d))]
  ^{:line 595 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 595 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val nl) nl])
  ^{:line 596 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val d) ^{:line 596 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 596 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val d) d]
  :else nil))))) ^{:line 598 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (forms-of src))))

^{:line 599 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn type-name-leaf [d]
  ^{:line 600 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [nl0 ^{:line 600 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second ^{:line 600 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children d))]
  ^{:line 601 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 601 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "list" ^{:line 601 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of nl0)) ^{:line 601 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ^{:line 601 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children nl0)) nl0)))

^{:line 602 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn module-types [^String src]
  ^{:line 603 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [defs ^{:line 603 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filterv ^{:line 603 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [f] ^{:line 603 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? TYPE-DEFS ^{:line 603 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (head-sym ^{:line 603 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (unwrap-def f)))) ^{:line 603 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (forms-of src))
   names ^{:line 604 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into ^{:line 604 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {} ^{:line 604 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keep ^{:line 604 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [f] ^{:line 604 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [nl ^{:line 604 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (type-name-leaf ^{:line 604 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (unwrap-def f))]
  ^{:line 605 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 605 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val nl) ^{:line 605 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 605 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 605 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val nl) nl])))) defs))
   variants ^{:line 610 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into ^{:line 610 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {} ^{:line 610 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat ^{:line 610 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [f] ^{:line 611 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [d ^{:line 611 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (unwrap-def f)]
  ^{:line 612 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 612 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "defunion" ^{:line 612 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (head-sym d)) ^{:line 612 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 613 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keep ^{:line 613 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [v] ^{:line 614 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 615 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "list" ^{:line 615 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of v)) ^{:line 616 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [vn ^{:line 616 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ^{:line 616 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children v))]
  ^{:line 617 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 617 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val vn) ^{:line 617 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 617 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 617 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val vn) vn])))
  ^{:line 618 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val v) ^{:line 618 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 618 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val v) v]
  :else nil)) ^{:line 620 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 ^{:line 620 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children d))))))) defs))]
  ^{:line 622 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (merge variants names)))

^{:line 625 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn module-accessors [^String src]
  ^{:line 626 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into ^{:line 626 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {} ^{:line 626 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat ^{:line 626 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [f] ^{:line 627 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [d ^{:line 627 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (unwrap-def f)]
  ^{:line 628 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 628 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 628 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{"defrecord" "deftype"} ^{:line 628 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (head-sym d)) ^{:line 628 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 629 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [nl ^{:line 629 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (type-name-leaf d)
   fb ^{:line 630 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ^{:line 630 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filterv ^{:line 630 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [c] ^{:line 630 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? c)) ^{:line 630 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 ^{:line 630 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children d))))]
  ^{:line 631 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 631 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and ^{:line 631 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val nl) fb) ^{:line 631 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 632 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [pfx ^{:line 632 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/lower-case ^{:line 632 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val nl))]
  ^{:line 633 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapv ^{:line 633 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [fld] ^{:line 633 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 633 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str pfx "-" fld) ^{:line 633 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [nl fld]]) ^{:line 634 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keep sym-val ^{:line 634 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (param-binds fb))))))))))) ^{:line 635 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (forms-of src))))

^{:line 636 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn def-binding [^String src ^String nm]
  ^{:line 637 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 637 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get ^{:line 637 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (file-modframe src) nm) ^{:line 637 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get ^{:line 637 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (file-typeframe src) nm)))

^{:line 638 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn make-xresolve [^String src]
  ^{:line 639 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [parsed ^{:line 639 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (parse-require src)
   refer ^{:line 640 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:refer parsed)
   as ^{:line 641 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:as parsed)
   rename ^{:line 642 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:rename parsed)
   xport ^{:line 644 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [m n] ^{:line 644 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 644 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get-in global-exports ^{:line 644 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [m n]) ^{:line 644 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get-in global-type-exports ^{:line 644 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [m n])))
   xacc ^{:line 645 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [m n] ^{:line 645 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get-in global-accessor-exports ^{:line 645 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [m n]))]
  ^{:line 646 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [nm] ^{:line 647 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 648 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get refer nm) ^{:line 648 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [m ^{:line 648 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get refer nm)]
  ^{:line 649 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [t ^{:line 649 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (xport m nm)]
  ^{:line 649 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if t ^{:line 649 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {:target t :mode :tracking} ^{:line 650 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [a ^{:line 650 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (xacc m nm)]
  ^{:line 650 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if a ^{:line 650 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 650 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {:target ^{:line 650 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first a) :mode :tracking :accessor ^{:line 650 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth a 1)}))))))
  ^{:line 651 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get rename nm) ^{:line 651 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [rr ^{:line 651 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get rename nm)
   m ^{:line 651 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first rr)
   sn ^{:line 651 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth rr 1)]
  ^{:line 651 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {:target ^{:line 651 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (xport m sn) :mode :fixed})
  ^{:line 652 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/includes? nm "/") ^{:line 654 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [parts ^{:line 654 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/split nm #"/")
   al ^{:line 655 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first parts)
   pn ^{:line 656 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/join "/" ^{:line 656 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest parts))
   m ^{:line 657 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 657 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get as al) ^{:line 658 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 658 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some ^{:line 658 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [tab] ^{:line 658 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? tab al)) ^{:line 659 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [global-exports global-type-exports global-accessor-exports]) ^{:line 658 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  al)))]
  ^{:line 660 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if m ^{:line 660 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 661 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [t ^{:line 661 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (xport m pn)]
  ^{:line 661 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if t ^{:line 661 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {:target t :mode :qual :alias al} ^{:line 662 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [a ^{:line 662 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (xacc m pn)]
  ^{:line 662 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if a ^{:line 662 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 662 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {:target ^{:line 662 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first a) :mode :qual :alias al :accessor ^{:line 662 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth a 1)}))))))))
  :else nil))))

^{:line 675 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn cbind! [L target]
  ^{:line 676 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx L REFERS target tx)
  ^{:line 677 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (swap! n-comment ^{:line 677 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 677 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (+ x 1))))

^{:line 678 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn resolve-comment! [e ^String src]
  ^{:line 679 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [seg ^{:line 679 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-segs e)
   :when ^{:line 679 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "symbol" ^{:line 679 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of seg))]
  ^{:line 680 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [nm ^{:line 680 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val seg)]
  ^{:line 681 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 681 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? nm) ^{:line 681 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 682 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [b ^{:line 682 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 682 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def-binding src nm) ^{:line 683 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:target ^{:line 683 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*xresolve* nm)))]
  ^{:line 684 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if b ^{:line 684 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 684 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cbind! seg b)))))))))

^{:line 685 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn walk-comments! [^String src]
  ^{:line 686 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [es ^{:line 686 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (deref file->ents)]
  ^{:line 687 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [e ^{:line 687 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get es src)
   :when ^{:line 687 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "comment" ^{:line 687 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of e))]
  ^{:line 688 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (resolve-comment! e src))))

^{:line 690 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn run-resolution-over! [walk-srcs]
  ^{:line 696 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [src walk-srcs]
  ^{:line 697 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (binding [*xresolve* ^{:line 697 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (make-xresolve src)
   *tresolve* ^{:line 698 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [nm] ^{:line 699 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [tf file-typeframe]
  ^{:line 700 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get ^{:line 700 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get tf src) nm)))
   *aresolve* ^{:line 701 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [nm] ^{:line 702 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [af file-accessors]
  ^{:line 703 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get ^{:line 703 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get af src) nm)))]
  ^{:line 704 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [forms ^{:line 704 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (forms-of src)
   fmf file-modframe]
  ^{:line 706 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (swap! walked-modules ^{:line 706 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [s] ^{:line 706 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (conj s src)))
  ^{:line 707 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (swap! n-forms-walked ^{:line 707 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 707 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (+ x ^{:line 707 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (count forms))))
  ^{:line 708 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-all! forms ^{:line 708 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 708 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get fmf src)]))
  ^{:line 709 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-comments! src))))

^{:line 710 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn run-resolution! []
  ^{:line 711 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [s srcs]
  ^{:line 712 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (run-resolution-over! s)))

^{:line 724 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn resolve-edn!
  ([edn-paths]
    (resolve-edn! edn-paths (fn [] nil)))
  ([edn-paths body]
    (let [store (c/new-store)
   t (c/begin-tx! store "resolve")
   sup (c/value! store "supersedes")]
  (c/set-supersedes-pred! store sup)
  (binding [ctx store
   tx t
   SUP sup
   file->ents (atom {})
   Vp (c/value! store "v")
   KIND (c/value! store "kind")
   REFERS (c/value! store "refers_to")
   BOUND (c/value! store "bound_to")
   FIXED (c/value! store "keep_spelling")
   QUAL (c/value! store "qualifier")
   CTOR (c/value! store "ctor_prefix")
   ACC (c/value! store "accessor_field")
   n-resolved (atom 0)
   n-unresolved (atom 0)
   n-xmod (atom 0)
   n-type (atom 0)
   n-comment (atom 0)
   n-forms-walked (atom 0)
   walked-modules (atom #{})
   srcs []
   file-modframe {}
   file-typeframe {}
   file-accessors {}
   global-exports {}
   global-type-exports {}
   global-accessor-exports {}]
  (set! srcs (mapv (fn [p] (load-edn! p)) edn-paths))
  (set! file-modframe (into {} (map (fn [s] [s (module-defs s)]) srcs)))
  (set! file-typeframe (into {} (map (fn [s] [s (module-types s)]) srcs)))
  (set! file-accessors (into {} (map (fn [s] [s (module-accessors s)]) srcs)))
  (set! global-exports (into {} (map (fn [s] [(module-name s) (let [e (module-exports s)]
  (if (seq e) e (module-defs s)))]) (filter (fn [s] (module-name s)) srcs))))
  (set! global-type-exports (into {} (map (fn [s] [(module-name s) (module-types s)]) (filter (fn [s] (module-name s)) srcs))))
  (set! global-accessor-exports (into {} (map (fn [s] [(module-name s) (module-accessors s)]) (filter (fn [s] (module-name s)) srcs))))
  (run-resolution!)
  (body)))))

^{:line 762 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn name->module [nm]
  ^{:line 763 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 763 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (string? nm) ^{:line 763 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 764 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [g ^{:line 764 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (re-matches #"@([^#]+)#\d+" nm)]
  ^{:line 764 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if g ^{:line 764 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 765 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth g 1)))))))

^{:line 772 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn corpus-from-store! []
  ^{:line 773 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [t0 ^{:line 773 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/nano-time)
   NAME ^{:line 774 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value-id ctx "name")
   groups ^{:line 776 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 779 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? *corpus-cache*) *corpus-cache*
  ^{:line 780 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? NAME) ^{:line 780 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (reduce ^{:line 780 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [acc cid] ^{:line 781 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [nm ^{:line 781 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/literal ctx ^{:line 781 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:r ^{:line 781 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim-of ctx cid)))
   m ^{:line 782 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (name->module nm)]
  ^{:line 783 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 783 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? m) ^{:line 784 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (update acc m ^{:line 784 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [o] ^{:line 785 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (conj ^{:line 785 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or o ^{:line 785 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []) ^{:line 785 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:l ^{:line 785 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim-of ctx cid))))) acc))) ^{:line 787 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {} ^{:line 787 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/by-p ctx NAME))
  :else ^{:line 788 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {})
   t-groups ^{:line 789 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/nano-time)]
  ^{:line 790 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (reset! file->ents groups)
  ^{:line 791 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (set! srcs ^{:line 791 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 791 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keys groups)))
  ^{:line 797 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [frame-srcs ^{:line 798 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 798 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? *corpus-scope*) ^{:line 799 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filterv ^{:line 799 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [s] ^{:line 799 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (boolean ^{:line 799 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*corpus-scope* s))) srcs) srcs)]
  ^{:line 801 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (set! file-modframe ^{:line 801 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into ^{:line 801 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {} ^{:line 801 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (map ^{:line 801 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [s] ^{:line 801 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [s ^{:line 801 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-defs s)]) frame-srcs)))
  ^{:line 802 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (set! file-typeframe ^{:line 802 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into ^{:line 802 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {} ^{:line 802 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (map ^{:line 802 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [s] ^{:line 802 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [s ^{:line 802 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-types s)]) frame-srcs)))
  ^{:line 803 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (set! file-accessors ^{:line 803 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into ^{:line 803 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {} ^{:line 803 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (map ^{:line 803 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [s] ^{:line 803 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [s ^{:line 803 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-accessors s)]) frame-srcs))))
  ^{:line 804 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 804 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not ^{:line 804 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? *corpus-scope*)) ^{:line 804 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 805 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (set! global-exports ^{:line 806 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into ^{:line 806 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {} ^{:line 806 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (map ^{:line 806 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [s] ^{:line 806 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 806 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-name s) ^{:line 807 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [e ^{:line 807 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-exports s)]
  ^{:line 807 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 807 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (seq e) e ^{:line 807 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-defs s)))]) ^{:line 808 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filterv ^{:line 808 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [s] ^{:line 808 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? ^{:line 808 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-name s))) srcs))))
  ^{:line 809 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (set! global-type-exports ^{:line 810 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into ^{:line 810 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {} ^{:line 810 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (map ^{:line 810 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [s] ^{:line 810 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 810 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-name s) ^{:line 810 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-types s)]) ^{:line 811 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filterv ^{:line 811 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [s] ^{:line 811 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? ^{:line 811 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-name s))) srcs))))
  ^{:line 812 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (set! global-accessor-exports ^{:line 813 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into ^{:line 813 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {} ^{:line 813 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (map ^{:line 813 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [s] ^{:line 813 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 813 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-name s) ^{:line 813 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-accessors s)]) ^{:line 814 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filterv ^{:line 814 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [s] ^{:line 814 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? ^{:line 814 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-name s))) srcs))))))
  ^{:line 815 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 815 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "1" ^{:line 815 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/getenv "FRAM_PROF")) ^{:line 815 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 816 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [s srcs]
  ^{:line 817 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 818 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/format-str "  corpus-from-store!: groups=%.1fms frames+exports=%.1fms cached=%s nsrcs=%d scoped=%s" ^{:line 819 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 819 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (/ ^{:line 819 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (- t-groups t0) 1000000.0) ^{:line 819 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (/ ^{:line 819 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (- ^{:line 819 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/nano-time) t-groups) 1000000.0) ^{:line 820 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? *corpus-cache*) ^{:line 820 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (count s) ^{:line 820 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (boolean *corpus-scope*)])))))))

^{:line 826 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn module-export-set [^String src]
  ^{:line 827 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [v ^{:line 827 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-exports src)
   vexp ^{:line 828 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 828 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (seq v) v ^{:line 828 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-defs src))]
  ^{:line 829 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into ^{:line 829 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{} ^{:line 829 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (concat ^{:line 829 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keys vexp) ^{:line 830 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keys ^{:line 830 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-types src)) ^{:line 831 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keys ^{:line 831 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-accessors src))))))

^{:line 836 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn module-imports [^String src]
  ^{:line 837 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [pr ^{:line 837 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (parse-require src)
   refer ^{:line 838 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:refer pr)
   as ^{:line 839 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:as pr)
   rename ^{:line 840 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:rename pr)]
  ^{:line 841 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into ^{:line 841 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{} ^{:line 841 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (concat ^{:line 841 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vals refer) ^{:line 841 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vals as) ^{:line 841 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (map ^{:line 841 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 841 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first x)) ^{:line 841 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vals rename))))))

^{:line 842 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn import-graph []
  ^{:line 843 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into ^{:line 843 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {} ^{:line 843 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (map ^{:line 843 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [s] ^{:line 843 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 843 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-name s) ^{:line 843 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-imports s)]) ^{:line 844 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filterv ^{:line 844 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [s] ^{:line 844 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? ^{:line 844 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-name s))) srcs))))

^{:line 849 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn ^Boolean module-has-macro? [^String src]
  ^{:line 850 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (boolean ^{:line 850 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some ^{:line 850 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [f] ^{:line 850 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "defmacro" ^{:line 850 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (head-sym ^{:line 850 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (unwrap-def f)))) ^{:line 850 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (forms-of src))))

^{:line 858 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn resolve-warm-store!
  ([store]
    (resolve-warm-store! store (fn [] nil)))
  ([store body]
    (let [t (c/begin-tx! store "resolve-warm")
   sup (or (c/value-id store "supersedes") (c/value! store "supersedes"))]
  (c/set-supersedes-pred! store sup)
  (binding [ctx store
   tx t
   SUP sup
   file->ents (atom {})
   Vp (c/value! store "v")
   KIND (c/value! store "kind")
   REFERS (c/value! store "refers_to")
   BOUND (c/value! store "bound_to")
   FIXED (c/value! store "keep_spelling")
   QUAL (c/value! store "qualifier")
   CTOR (c/value! store "ctor_prefix")
   ACC (c/value! store "accessor_field")
   n-resolved (atom 0)
   n-unresolved (atom 0)
   n-xmod (atom 0)
   n-type (atom 0)
   n-comment (atom 0)
   n-forms-walked (atom 0)
   walked-modules (atom #{})
   srcs []
   file-modframe {}
   file-typeframe {}
   file-accessors {}
   global-exports {}
   global-type-exports {}
   global-accessor-exports {}]
  (corpus-from-store!)
  (if *resolve-walk?* (do
  (run-resolution!)))
  (body)))))

^{:line 882 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn resolve-modules!
  ([store module-set]
    (resolve-modules! store module-set (fn [] nil)))
  ([store module-set body]
    (let [t (c/begin-tx! store "resolve-scoped")
   sup (or (c/value-id store "supersedes") (c/value! store "supersedes"))]
  (c/set-supersedes-pred! store sup)
  (binding [ctx store
   tx t
   SUP sup
   file->ents (atom {})
   Vp (c/value! store "v")
   KIND (c/value! store "kind")
   REFERS (c/value! store "refers_to")
   BOUND (c/value! store "bound_to")
   FIXED (c/value! store "keep_spelling")
   QUAL (c/value! store "qualifier")
   CTOR (c/value! store "ctor_prefix")
   ACC (c/value! store "accessor_field")
   n-resolved (atom 0)
   n-unresolved (atom 0)
   n-xmod (atom 0)
   n-type (atom 0)
   n-comment (atom 0)
   n-forms-walked (atom 0)
   walked-modules (atom #{})
   srcs []
   file-modframe {}
   file-typeframe {}
   file-accessors {}
   global-exports {}
   global-type-exports {}
   global-accessor-exports {}]
  (corpus-from-store!)
  (run-resolution-over! (filterv (fn [s] (contains? module-set s)) srcs))
  (body)))))

^{:line 903 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn ultimate [B]
  ^{:line 904 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (loop [b B
   n 0]
  ^{:line 905 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [t ^{:line 905 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (refers-target b)]
  ^{:line 906 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 906 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and ^{:line 906 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? t) ^{:line 906 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (< n 64)) ^{:line 906 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur t ^{:line 906 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (+ n 1)) b))))

^{:line 907 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn binding-name [B]
  ^{:line 907 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val ^{:line 907 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ultimate B)))

^{:line 922 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn register! [^String src e]
  ^{:line 923 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (swap! file->ents update src ^{:line 923 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [o] ^{:line 923 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (conj ^{:line 923 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or o ^{:line 923 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []) e)))
  e)

^{:line 929 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn mint-leaf! [^String src ^String kind ^String v]
  ^{:line 930 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [e ^{:line 930 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (register! src ^{:line 930 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/entity! ctx))]
  ^{:line 931 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx e KIND ^{:line 931 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx kind) tx)
  ^{:line 932 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx e Vp ^{:line 932 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx v) tx)
  e))

^{:line 934 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn mint-datum! [^String src d]
  ^{:line 935 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 936 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nil? d) ^{:line 936 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [e ^{:line 936 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (register! src ^{:line 936 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/entity! ctx))]
  ^{:line 936 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx e KIND ^{:line 936 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx "nil") tx)
  e)
  ^{:line 937 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (symbol? d) ^{:line 937 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mint-leaf! src "symbol" ^{:line 937 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str d))
  ^{:line 938 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keyword? d) ^{:line 938 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mint-leaf! src "keyword" ^{:line 938 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (subs ^{:line 938 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str d) 1))
  ^{:line 939 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (string? d) ^{:line 939 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mint-leaf! src "string" d)
  ^{:line 940 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (boolean? d) ^{:line 940 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mint-leaf! src "bool" ^{:line 940 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if d "true" "false"))
  ^{:line 941 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (char? d) ^{:line 941 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mint-leaf! src "char" ^{:line 941 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str d))
  ^{:line 942 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (number? d) ^{:line 942 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mint-leaf! src "number" ^{:line 942 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str d))
  ^{:line 943 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 943 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (list? d) ^{:line 943 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (seq? d) ^{:line 943 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vector? d) ^{:line 943 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (map? d)) ^{:line 946 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [head ^{:line 946 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 946 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vector? d) ^{:line 946 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 946 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (symbol "#%brackets")]
  ^{:line 946 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (map? d) ^{:line 946 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 946 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (symbol "#%map")]
  :else ^{:line 946 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])
   elems ^{:line 947 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (concat head ^{:line 947 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 947 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (map? d) ^{:line 947 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (apply concat ^{:line 947 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (seq d)) ^{:line 947 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (seq d)))
   e ^{:line 948 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (register! src ^{:line 948 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/entity! ctx))]
  ^{:line 949 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx e KIND ^{:line 949 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx "list") tx)
  ^{:line 950 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [[i x] ^{:line 950 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (map-indexed vector elems)]
  ^{:line 951 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx e ^{:line 951 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx ^{:line 951 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "f" i)) ^{:line 951 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mint-datum! src x) tx))
  e)
  :else ^{:line 953 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mint-leaf! src "other" ^{:line 953 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (pr-str d))))

^{:line 956 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn fN-claims [parent]
  ^{:line 957 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (->> ^{:line 957 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/by-l ctx parent) ^{:line 958 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keep ^{:line 958 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [cid] ^{:line 959 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [p ^{:line 959 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/literal ctx ^{:line 959 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim-p ctx cid))]
  ^{:line 960 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 960 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and ^{:line 960 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (string? p) ^{:line 960 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? ^{:line 960 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (re-matches #"f\d+" p))) ^{:line 960 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 961 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 961 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (parse-long ^{:line 961 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (subs p 1)) cid ^{:line 961 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim-r ctx cid)]))))) ^{:line 962 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sort-by first)))

^{:line 966 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn retire-claim! [oldc]
  ^{:line 966 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx ^{:line 966 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/entity! ctx) SUP oldc tx))

^{:line 973 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn wrapper-of [^String src]
  ^{:line 974 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [m ^{:line 974 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (deref file->ents)]
  ^{:line 975 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some ^{:line 975 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [e] ^{:line 975 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 975 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "beagle-file" ^{:line 975 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (head-sym e)) ^{:line 975 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  e))) ^{:line 975 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get m src))))

^{:line 976 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn structural-kids [n]
  ^{:line 977 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (->> ^{:line 977 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/by-l ctx n) ^{:line 978 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keep ^{:line 978 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [cid] ^{:line 979 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [p ^{:line 979 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/literal ctx ^{:line 979 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim-p ctx cid))
   r ^{:line 980 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim-r ctx cid)]
  ^{:line 981 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 981 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and ^{:line 981 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (integer? r) ^{:line 981 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (string? p) ^{:line 982 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 982 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ord-pos? p) ^{:line 982 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? ^{:line 982 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (re-matches #"seg\d+" p)) ^{:line 983 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? ^{:line 983 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (re-matches #"comment\d+" p)) ^{:line 983 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= p "tail"))) ^{:line 981 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  r)))))))

^{:line 985 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn descendants [root]
  ^{:line 986 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (loop [seen ^{:line 986 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{}
   stack ^{:line 986 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [root]]
  ^{:line 987 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 987 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (empty? stack) seen ^{:line 988 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [n ^{:line 988 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (peek stack)]
  ^{:line 989 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 989 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? seen n) ^{:line 989 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur seen ^{:line 989 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 989 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (pop stack))) ^{:line 990 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 990 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (conj seen n) ^{:line 990 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into ^{:line 990 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 990 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (pop stack)) ^{:line 990 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (structural-kids n))))))))

^{:line 991 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn form-for-victim [^String src victim]
  ^{:line 992 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [w ^{:line 992 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (wrapper-of src)]
  ^{:line 993 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 993 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? w) ^{:line 994 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some ^{:line 994 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [f] ^{:line 995 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [nl0 ^{:line 995 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second ^{:line 995 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children ^{:line 995 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (unwrap-def f)))]
  ^{:line 996 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 996 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? nl0) ^{:line 997 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [nl ^{:line 997 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 997 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "list" ^{:line 997 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of nl0)) ^{:line 998 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [g0 ^{:line 998 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ^{:line 998 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children nl0))]
  ^{:line 998 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 998 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? g0) g0 nl0)) nl0)]
  ^{:line 1000 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1000 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= victim nl) f nil)) nil))) ^{:line 1002 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 1002 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children w))) nil)))

^{:line 1012 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn extract-file! [^String src ^String out-path]
  ^{:line 1013 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [ents ^{:line 1013 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1013 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get ^{:line 1013 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (deref file->ents) src) ^{:line 1013 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])
   ds *deleted-subtree*
   wrap ^{:line 1015 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (wrapper-of src)
   root ^{:line 1016 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1016 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (empty? *deleted-forms*) ^{:line 1016 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1016 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (wrapper-of src)))
   live ^{:line 1017 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if root ^{:line 1017 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1017 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (descendants ^{:line 1017 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or root 0))))
   keep? ^{:line 1018 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [e] ^{:line 1018 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1018 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nil? live) ^{:line 1018 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 1018 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or live ^{:line 1018 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{}) e)))
   emit-line ^{:line 1020 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [e cid] ^{:line 1021 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [p ^{:line 1021 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1021 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim-p ctx cid) 0)
   r ^{:line 1022 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1022 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim-r ctx cid) 0)
   ps ^{:line 1023 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/literal ctx p)]
  ^{:line 1024 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 1025 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and ^{:line 1025 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= e ^{:line 1025 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or wrap 0)) ^{:line 1025 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (string? ps) ^{:line 1025 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ord-pos? ps) ^{:line 1025 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not= ps "f0")) nil
  ^{:line 1026 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 1026 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{"supersedes" "refers_to" "keep_spelling" "qualifier" "ctor_prefix" "accessor_field"} ps) nil
  ^{:line 1027 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and ^{:line 1027 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= ps "v") ^{:line 1027 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (refers-target e)) ^{:line 1028 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [D ^{:line 1028 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1028 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (refers-target e) 0)
   fixed? ^{:line 1029 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (seq ^{:line 1029 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/by-lp ctx e FIXED))
   qual ^{:line 1030 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (pred-val e "qualifier")
   cpfx ^{:line 1031 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (pred-val e "ctor_prefix")
   afield ^{:line 1032 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (pred-val e "accessor_field")
   nm0 ^{:line 1033 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (binding-name D)
   nm ^{:line 1034 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  cpfx ^{:line 1034 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str cpfx nm0)
  afield ^{:line 1035 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str ^{:line 1035 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/lower-case ^{:line 1035 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str nm0)) "-" afield)
  :else ^{:line 1036 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str nm0))]
  ^{:line 1037 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "[" e " \"v\" " ^{:line 1038 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (pr-str ^{:line 1038 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  fixed? ^{:line 1038 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/literal ctx r)
  qual ^{:line 1039 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str qual "/" nm)
  :else nm)) "]"))
  ^{:line 1042 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value-object? ctx r) ^{:line 1043 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "[" e " " ^{:line 1043 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (pr-str ps) " " ^{:line 1043 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (pr-str ^{:line 1043 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/literal ctx r)) "]")
  :else ^{:line 1044 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "[" e " " ^{:line 1044 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (pr-str ps) " " r "]"))))
   node-lines ^{:line 1046 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (reduce ^{:line 1047 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [acc e] ^{:line 1048 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1048 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and ^{:line 1048 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not ^{:line 1048 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ds e)) ^{:line 1048 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keep? e)) ^{:line 1049 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (reduce ^{:line 1049 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [a cid] ^{:line 1050 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [ln ^{:line 1050 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (emit-line e cid)]
  ^{:line 1051 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ln ^{:line 1051 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (conj a ln) a))) acc ^{:line 1052 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/by-l ctx e)) acc)) ^{:line 1054 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [] ents)
   form-lines ^{:line 1056 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if wrap ^{:line 1057 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1057 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keep-indexed ^{:line 1058 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [i f] ^{:line 1059 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "[" ^{:line 1059 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or wrap 0) " \"f" ^{:line 1059 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (+ i 1) "\" " f "]")) ^{:line 1060 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filterv ^{:line 1060 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [f] ^{:line 1060 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not ^{:line 1060 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? *deleted-forms* f))) ^{:line 1061 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 1061 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children ^{:line 1061 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or wrap 0)))))) ^{:line 1062 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])
   all-lines ^{:line 1063 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1063 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (concat ^{:line 1063 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cons ^{:line 1063 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "@file " src) node-lines) form-lines))]
  ^{:line 1064 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/spit-file out-path ^{:line 1064 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str ^{:line 1064 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/join "\n" all-lines) "\n"))))

^{:line 1068 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn ^String out-path [^String src]
  ^{:line 1069 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str ^{:line 1069 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or *resolve-out* ^{:line 1069 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/getenv "RESOLVE_OUT") "/tmp") "/resolved-" ^{:line 1070 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (last ^{:line 1070 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/split src #"/")) ".edn"))

^{:line 1078 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn ^Boolean renders-as-tracked-name? [node]
  ^{:line 1079 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and ^{:line 1079 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not ^{:line 1079 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (seq ^{:line 1079 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/by-lp ctx node FIXED))) ^{:line 1080 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not ^{:line 1080 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (pred-val node "qualifier"))))

^{:line 1081 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn capture-refs [node scope B ^String newnm]
  ^{:line 1082 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 1083 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= ^{:line 1083 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of node) "symbol") ^{:line 1084 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [t ^{:line 1084 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (refers-target node)]
  ^{:line 1085 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1085 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and t ^{:line 1085 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= B ^{:line 1085 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ultimate ^{:line 1085 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or t 0))) ^{:line 1086 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (renders-as-tracked-name? node) ^{:line 1087 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some ^{:line 1087 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [m] ^{:line 1087 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get m newnm)) scope)) ^{:line 1088 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [node] ^{:line 1088 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []))
  ^{:line 1089 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= ^{:line 1089 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of node) "list") ^{:line 1090 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [kids ^{:line 1090 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children node)
   h ^{:line 1091 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (head-sym node)]
  ^{:line 1092 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 1093 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? PARAM-FORMS ^{:line 1093 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or h "")) ^{:line 1094 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [after-name ^{:line 1094 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1094 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 1094 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{"defn" "defn-" "defmacro"} ^{:line 1094 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or h "")) ^{:line 1094 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1094 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 kids)) ^{:line 1094 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1094 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest kids)))
   cap-arity ^{:line 1095 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [forms] ^{:line 1096 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [pv ^{:line 1096 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ^{:line 1096 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filterv ^{:line 1096 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1096 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? x)) forms))
   frame ^{:line 1097 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (frame-of ^{:line 1097 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if pv ^{:line 1097 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (param-binds ^{:line 1097 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or pv 0)) ^{:line 1097 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []))
   or-vals ^{:line 1098 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if pv ^{:line 1098 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1098 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat ^{:line 1098 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1098 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (collect-or-vals x)) ^{:line 1098 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 1098 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children ^{:line 1098 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or pv 0))))) ^{:line 1098 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])
   body ^{:line 1099 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (loop [xs ^{:line 1099 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1099 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 1099 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop-while ^{:line 1099 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1099 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not ^{:line 1099 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? x))) forms)))]
  ^{:line 1100 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1100 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 1100 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{":-" ":" ":raises"} ^{:line 1100 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1100 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val ^{:line 1100 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first xs)) "")) ^{:line 1100 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 1100 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1100 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 xs))) xs))]
  ^{:line 1101 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1101 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (concat ^{:line 1101 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat ^{:line 1101 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1101 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (capture-refs x scope B newnm)) or-vals) ^{:line 1102 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat ^{:line 1102 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1102 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (capture-refs x ^{:line 1102 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cons frame scope) B newnm)) body)))))]
  ^{:line 1103 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1103 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some ^{:line 1103 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1103 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? x)) after-name) ^{:line 1104 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cap-arity after-name) ^{:line 1105 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1105 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat ^{:line 1105 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [a] ^{:line 1106 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1106 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and ^{:line 1106 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "list" ^{:line 1106 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of a)) ^{:line 1106 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? ^{:line 1106 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1106 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ^{:line 1106 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children a)) 0))) ^{:line 1107 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cap-arity ^{:line 1107 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children a)) ^{:line 1107 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])) after-name))))
  ^{:line 1109 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? LET-FORMS ^{:line 1109 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or h "")) ^{:line 1110 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [bracket ^{:line 1110 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second kids)
   pairs ^{:line 1111 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1111 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and bracket ^{:line 1111 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? ^{:line 1111 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or bracket 0))) ^{:line 1111 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let-bind-pairs ^{:line 1111 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or bracket 0)) ^{:line 1111 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])
   acc ^{:line 1112 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (reduce ^{:line 1112 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [a pr] ^{:line 1113 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [sc ^{:line 1113 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first a)
   caps ^{:line 1114 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1114 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second a) ^{:line 1114 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])
   bsyms ^{:line 1115 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1115 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first pr) ^{:line 1115 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])
   vnode ^{:line 1116 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second pr)
   orvals ^{:line 1117 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1117 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth pr 2 nil) ^{:line 1117 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])]
  ^{:line 1118 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 1118 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cons ^{:line 1118 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (frame-of bsyms) sc) ^{:line 1119 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into caps ^{:line 1119 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1119 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (concat ^{:line 1119 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat ^{:line 1119 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1119 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (capture-refs x sc B newnm)) orvals) ^{:line 1120 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if vnode ^{:line 1120 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (capture-refs ^{:line 1120 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or vnode 0) sc B newnm) ^{:line 1120 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []))))])) ^{:line 1121 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [scope ^{:line 1121 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []] pairs)
   final ^{:line 1122 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first acc)
   vcaps ^{:line 1123 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1123 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second acc) ^{:line 1123 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])]
  ^{:line 1124 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1124 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (concat vcaps ^{:line 1124 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat ^{:line 1124 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1124 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (capture-refs x final B newnm)) ^{:line 1124 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 kids)))))
  ^{:line 1125 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? FOR-FORMS ^{:line 1125 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or h "")) ^{:line 1126 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [bracket ^{:line 1126 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second kids)
   entries ^{:line 1127 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1127 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and bracket ^{:line 1127 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? ^{:line 1127 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or bracket 0))) ^{:line 1127 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (for-bind-pairs ^{:line 1127 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or bracket 0)) ^{:line 1127 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])
   acc ^{:line 1128 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (reduce ^{:line 1128 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [a e] ^{:line 1129 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [sc ^{:line 1129 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first a)
   caps ^{:line 1130 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1130 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second a) ^{:line 1130 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])]
  ^{:line 1131 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1131 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= :expr ^{:line 1131 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first e)) ^{:line 1132 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [sc ^{:line 1132 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into caps ^{:line 1132 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (capture-refs ^{:line 1132 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1132 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second e) 0) sc B newnm))] ^{:line 1133 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [bsyms ^{:line 1133 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1133 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth e 1 nil) ^{:line 1133 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])
   vnode ^{:line 1134 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth e 2 nil)
   orvals ^{:line 1135 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1135 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth e 3 nil) ^{:line 1135 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])]
  ^{:line 1136 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 1136 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cons ^{:line 1136 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (frame-of bsyms) sc) ^{:line 1137 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into caps ^{:line 1137 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1137 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (concat ^{:line 1137 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat ^{:line 1137 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1137 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (capture-refs x sc B newnm)) orvals) ^{:line 1138 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if vnode ^{:line 1138 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (capture-refs ^{:line 1138 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or vnode 0) sc B newnm) ^{:line 1138 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []))))])))) ^{:line 1139 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [scope ^{:line 1139 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []] entries)
   final ^{:line 1140 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first acc)
   vcaps ^{:line 1141 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1141 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second acc) ^{:line 1141 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])]
  ^{:line 1142 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1142 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (concat vcaps ^{:line 1142 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat ^{:line 1142 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1142 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (capture-refs x final B newnm)) ^{:line 1142 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 kids)))))
  ^{:line 1143 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? MATCH-FORMS ^{:line 1143 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or h "")) ^{:line 1144 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [kids ^{:line 1144 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children node)]
  ^{:line 1145 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1145 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (concat ^{:line 1145 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (capture-refs ^{:line 1145 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1145 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second kids) 0) scope B newnm) ^{:line 1146 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat ^{:line 1146 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [clause] ^{:line 1147 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1147 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? clause) ^{:line 1148 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [cc ^{:line 1148 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1148 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 1148 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children clause)))
   pat ^{:line 1149 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1149 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first cc) 0)
   body ^{:line 1150 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1150 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest cc))
   frame ^{:line 1151 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (frame-of ^{:line 1151 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (match-pat-binds pat))]
  ^{:line 1152 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1152 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (concat ^{:line 1152 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (capture-refs pat scope B newnm) ^{:line 1153 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat ^{:line 1153 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1153 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (capture-refs x ^{:line 1153 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cons frame scope) B newnm)) body)))) ^{:line 1154 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])) ^{:line 1155 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 kids)))))
  ^{:line 1156 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= h "letfn") ^{:line 1157 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [bracket ^{:line 1157 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second kids)
   fnlists ^{:line 1158 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1158 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and bracket ^{:line 1158 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? ^{:line 1158 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or bracket 0))) ^{:line 1159 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filterv ^{:line 1159 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1159 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "list" ^{:line 1159 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of x))) ^{:line 1159 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 1159 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children ^{:line 1159 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or bracket 0)))) ^{:line 1160 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])
   frame ^{:line 1161 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (frame-of ^{:line 1161 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1161 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keep ^{:line 1161 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1161 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ^{:line 1161 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children x))) fnlists)))
   bodyscope ^{:line 1162 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cons frame scope)
   cap-arity ^{:line 1163 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [forms] ^{:line 1164 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [pv ^{:line 1164 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ^{:line 1164 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filterv ^{:line 1164 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1164 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? x)) forms))
   pframe ^{:line 1165 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (frame-of ^{:line 1165 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if pv ^{:line 1165 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (param-binds ^{:line 1165 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or pv 0)) ^{:line 1165 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []))
   fbody ^{:line 1166 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (loop [xs ^{:line 1166 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1166 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 1166 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop-while ^{:line 1166 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1166 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not ^{:line 1166 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? x))) forms)))]
  ^{:line 1167 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1167 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 1167 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{":-" ":" ":raises"} ^{:line 1167 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1167 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val ^{:line 1167 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first xs)) "")) ^{:line 1167 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 1167 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1167 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 xs))) xs))]
  ^{:line 1168 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1168 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat ^{:line 1168 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1168 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (capture-refs x ^{:line 1168 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cons pframe bodyscope) B newnm)) fbody))))]
  ^{:line 1169 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1169 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (concat ^{:line 1169 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat ^{:line 1169 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [fl] ^{:line 1169 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cap-arity ^{:line 1169 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1169 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 1169 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children fl))))) fnlists) ^{:line 1170 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat ^{:line 1170 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1170 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (capture-refs x bodyscope B newnm)) ^{:line 1170 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 kids)))))
  ^{:line 1171 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 1171 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{"extend-type" "extend-protocol"} ^{:line 1171 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or h "")) ^{:line 1172 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1172 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat ^{:line 1172 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [c] ^{:line 1173 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1173 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= "list" ^{:line 1173 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (kind-of c)) ^{:line 1174 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [ic ^{:line 1174 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ordered-children c)
   pv ^{:line 1175 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ^{:line 1175 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filterv ^{:line 1175 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1175 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? x)) ^{:line 1175 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ic)))
   pframe ^{:line 1176 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (frame-of ^{:line 1176 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if pv ^{:line 1176 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (param-binds ^{:line 1176 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or pv 0)) ^{:line 1176 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []))
   fbody ^{:line 1177 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (loop [xs ^{:line 1177 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1177 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ^{:line 1177 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop-while ^{:line 1177 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1177 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not ^{:line 1177 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? x))) ^{:line 1177 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest ic))))]
  ^{:line 1178 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1178 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 1178 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{":-" ":" ":raises"} ^{:line 1178 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1178 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val ^{:line 1178 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first xs)) "")) ^{:line 1178 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (recur ^{:line 1178 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1178 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 2 xs))) xs))]
  ^{:line 1179 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1179 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (concat ^{:line 1179 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (capture-refs ^{:line 1179 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1179 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ic) 0) scope B newnm) ^{:line 1180 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat ^{:line 1180 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1180 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (capture-refs x ^{:line 1180 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cons pframe scope) B newnm)) fbody)))) ^{:line 1181 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (capture-refs c scope B newnm))) ^{:line 1182 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rest kids)))
  ^{:line 1183 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= h "as->") ^{:line 1184 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [init ^{:line 1184 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth kids 1 nil)
   nm ^{:line 1185 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth kids 2 nil)
   frame ^{:line 1186 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (frame-of ^{:line 1186 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1186 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val ^{:line 1186 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or nm 0)) ^{:line 1186 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [^{:line 1186 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or nm 0)] ^{:line 1186 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []))]
  ^{:line 1187 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1187 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (concat ^{:line 1187 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if init ^{:line 1187 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (capture-refs ^{:line 1187 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or init 0) scope B newnm) ^{:line 1187 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []) ^{:line 1188 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat ^{:line 1188 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1188 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (capture-refs x ^{:line 1188 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cons frame scope) B newnm)) ^{:line 1188 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (drop 3 kids)))))
  :else ^{:line 1189 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1189 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat ^{:line 1189 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1189 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (capture-refs x scope B newnm)) kids))))
  :else ^{:line 1190 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} []))

^{:line 1196 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn re-resolve! []
  ^{:line 1197 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [s srcs
   modframe ^{:line 1199 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into ^{:line 1199 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {} ^{:line 1199 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (map ^{:line 1199 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [src] ^{:line 1199 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [src ^{:line 1199 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-defs src)]) s))
   typeframe ^{:line 1201 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into ^{:line 1201 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {} ^{:line 1201 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (map ^{:line 1201 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [src] ^{:line 1201 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [src ^{:line 1201 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-types src)]) s))
   accessors ^{:line 1203 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into ^{:line 1203 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} {} ^{:line 1203 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (map ^{:line 1203 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [src] ^{:line 1203 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [src ^{:line 1203 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-accessors src)]) s))]
  ^{:line 1204 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [src s]
  ^{:line 1205 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (binding [*xresolve* ^{:line 1205 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (make-xresolve src)
   *tresolve* ^{:line 1206 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [nm] ^{:line 1206 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get ^{:line 1206 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get typeframe src) nm))
   *aresolve* ^{:line 1207 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [nm] ^{:line 1207 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get ^{:line 1207 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get accessors src) nm))]
  ^{:line 1208 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-all! ^{:line 1208 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (forms-of src) ^{:line 1208 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (list ^{:line 1208 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get modframe src)))
  ^{:line 1209 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (walk-comments! src)))))

^{:line 1211 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn author-emit! [^String op ^String detail]
  ^{:line 1212 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [s srcs]
  ^{:line 1213 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [src s]
  ^{:line 1213 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (extract-file! src ^{:line 1213 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (out-path src)))
  ^{:line 1214 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1214 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "================ authoring: " op " ================"))
  ^{:line 1215 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! detail)
  ^{:line 1216 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [src s]
  ^{:line 1216 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1216 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "projected -> " ^{:line 1216 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (out-path src) "   <- " src)))))

^{:line 1218 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn- emit-srcs []
  ^{:line 1218 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or *project-srcs* srcs))

^{:line 1221 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn author-emit-scoped! [^String op ^String detail]
  ^{:line 1222 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1222 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not *capture-only?*) ^{:line 1222 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1223 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [src ^{:line 1223 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (emit-srcs)]
  ^{:line 1223 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (extract-file! src ^{:line 1223 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (out-path src)))
  ^{:line 1224 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1224 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "================ authoring: " op " ================"))
  ^{:line 1225 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! detail)
  ^{:line 1226 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [src ^{:line 1226 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (emit-srcs)]
  ^{:line 1226 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1226 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "projected -> " ^{:line 1226 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (out-path src) "   <- " src))))))

^{:line 1229 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn verb-rename! [^String old ^String new ^String target]
  ^{:line 1230 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [target-srcs ^{:line 1230 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filterv ^{:line 1230 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [s] ^{:line 1230 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/includes? s target)) srcs)
   edits ^{:line 1231 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (atom 0)]
  ^{:line 1232 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [src target-srcs]
  ^{:line 1233 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1233 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and ^{:line 1233 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def-binding src old) ^{:line 1233 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def-binding src new)) ^{:line 1233 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1234 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1234 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "REJECTED — `" new "` already names a binding in " src " (rename-doesn't-collide; no claims mutated)."))
  ^{:line 1236 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*reject!* 3))))
  ^{:line 1237 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [src target-srcs]
  ^{:line 1238 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1238 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and ^{:line 1238 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? ^{:line 1238 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get ^{:line 1238 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (file-typeframe src) old)) ^{:line 1238 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nil? ^{:line 1238 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (re-find #"^[A-Z]" new))) ^{:line 1238 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1239 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1239 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "REJECTED — `" new "` is not a valid (Capitalized) type name " "(beagle type-name shape; no claims mutated)."))
  ^{:line 1241 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*reject!* 3))))
  ^{:line 1242 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [src target-srcs]
  ^{:line 1243 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [B ^{:line 1243 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def-binding src old)]
  ^{:line 1244 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1244 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? B) ^{:line 1244 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1245 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [caps ^{:line 1246 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1246 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat ^{:line 1246 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [s] ^{:line 1247 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mapcat ^{:line 1247 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [f] ^{:line 1248 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (capture-refs f ^{:line 1248 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (list ^{:line 1248 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get file-modframe s)) B new)) ^{:line 1249 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (forms-of s))) srcs))]
  ^{:line 1251 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1251 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (seq caps) ^{:line 1251 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1252 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1252 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "REJECTED — renaming `" old "` -> `" new "` would be CAPTURED by a local `" new "` in scope at " ^{:line 1253 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (count caps) " reference(s) (no-capture; no claims mutated)."))
  ^{:line 1254 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*reject!* 4))))))))
  ^{:line 1255 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [target-mods ^{:line 1256 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (into ^{:line 1256 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{} ^{:line 1256 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keep ^{:line 1256 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [src] ^{:line 1256 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (module-name src)) target-srcs))]
  ^{:line 1257 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [src srcs
   :when ^{:line 1257 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not ^{:line 1257 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some ^{:line 1257 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [s] ^{:line 1257 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= s src)) target-srcs))]
  ^{:line 1258 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [pr ^{:line 1258 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (parse-require src)
   refer ^{:line 1259 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:refer pr)
   rename ^{:line 1260 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:rename pr)]
  ^{:line 1261 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1261 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and ^{:line 1261 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? target-mods ^{:line 1261 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get refer old)) ^{:line 1262 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1262 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def-binding src new) ^{:line 1262 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get refer new) ^{:line 1262 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (get rename new))) ^{:line 1261 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1263 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1263 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "REJECTED — renaming `" old "` -> `" new "` would DUPLICATE a binding in consumer " src " (it already binds `" new "`; no-import-collision; no claims mutated)."))
  ^{:line 1265 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*reject!* 3))))))
  ^{:line 1266 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [src target-srcs]
  ^{:line 1267 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [B ^{:line 1267 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def-binding src old)]
  ^{:line 1268 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1268 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? B) ^{:line 1268 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1269 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [oldc ^{:line 1269 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ^{:line 1269 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filterv ^{:line 1269 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [cid] ^{:line 1269 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= Vp ^{:line 1269 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim-p ctx cid))) ^{:line 1269 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/by-l ctx B)))
   nc ^{:line 1270 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx B Vp ^{:line 1270 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx new) tx)]
  ^{:line 1271 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx nc SUP oldc tx)
  ^{:line 1272 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (swap! edits ^{:line 1272 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1272 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (+ x 1))))))))
  ^{:line 1273 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1273 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (zero? ^{:line 1273 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [e ^{:line 1273 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (deref edits)]
  e)) ^{:line 1273 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1274 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1274 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "REJECTED — no binding named `" old "` found in \"" target "\" (nothing to rename; no claims mutated)."))
  ^{:line 1276 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*reject!* 5)))
  ^{:line 1279 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1279 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not *capture-only?*) ^{:line 1279 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1280 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [src ^{:line 1280 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (emit-srcs)]
  ^{:line 1280 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (extract-file! src ^{:line 1280 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (out-path src)))
  ^{:line 1281 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! "================ Turtle #5 — O(1) shadow-correct rename ================")
  ^{:line 1282 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1282 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "edit: rename def `" old "` -> `" new "` in \"" target "\""))
  ^{:line 1283 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1283 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "CLAIMS EDITED: " ^{:line 1283 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [e ^{:line 1283 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (deref edits)]
  e) "  (just the definition's name; references follow refers_to)"))
  ^{:line 1285 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [src ^{:line 1285 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (emit-srcs)]
  ^{:line 1285 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1285 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "projected -> " ^{:line 1285 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (out-path src) "   <- " src)))))))

^{:line 1290 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn wrap-forms [parent]
  ^{:line 1291 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (vec ^{:line 1291 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sort-by ^{:line 1291 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [e] ^{:line 1291 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first e)) ord-cmp ^{:line 1292 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keep ^{:line 1292 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [cid] ^{:line 1293 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [p ^{:line 1293 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim-p ctx cid)
   k ^{:line 1294 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ord-parse ^{:line 1294 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/literal ctx p))]
  ^{:line 1295 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if k ^{:line 1295 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1295 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [k cid ^{:line 1295 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim-r ctx cid)])))) ^{:line 1296 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/by-l ctx parent)))))

^{:line 1301 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn- ^String ord-tie []
  ^{:line 1301 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if *capture-only?* "PENDING" "0"))

^{:line 1303 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn verb-upsert-form! [^String scope datum]
  ^{:line 1304 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [target-srcs ^{:line 1304 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filterv ^{:line 1304 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [s] ^{:line 1304 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/includes? s scope)) srcs)]
  ^{:line 1305 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1305 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not= 1 ^{:line 1305 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (count target-srcs)) ^{:line 1305 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1306 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1306 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "REJECTED — scope \"" scope "\" matches " ^{:line 1306 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (count target-srcs) " source files; upsert-form needs exactly one (no claims mutated)."))
  ^{:line 1308 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*reject!* 3)))
  ^{:line 1309 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1309 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and ^{:line 1309 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (seq? datum) ^{:line 1309 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not ^{:line 1309 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? VALUE-DEFS ^{:line 1309 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str ^{:line 1309 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first datum))))) ^{:line 1309 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1310 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1310 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "REJECTED — upsert-form spec head `" ^{:line 1310 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first datum) "` is not a value def (def/defn/...); no claims mutated."))
  ^{:line 1312 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*reject!* 3)))
  ^{:line 1313 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [src ^{:line 1313 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first target-srcs)
   wrap ^{:line 1314 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (wrapper-of src)
   forms ^{:line 1315 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (wrap-forms wrap)
   new-name ^{:line 1317 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1317 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and ^{:line 1317 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (seq? datum) ^{:line 1317 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? VALUE-DEFS ^{:line 1317 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str ^{:line 1317 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first datum)))) ^{:line 1317 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1317 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str ^{:line 1317 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (second datum))))
   existing ^{:line 1318 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if new-name ^{:line 1318 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1318 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def-binding src new-name)))
   victim-form ^{:line 1319 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if existing ^{:line 1319 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1319 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (form-for-victim src existing)))
   victim-entry ^{:line 1321 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if victim-form ^{:line 1321 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1322 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some ^{:line 1322 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [e] ^{:line 1323 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [r ^{:line 1323 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth e 2)]
  ^{:line 1323 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1323 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= r victim-form) ^{:line 1323 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  e)))) forms)))
   new-root ^{:line 1325 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mint-datum! src datum)]
  ^{:line 1326 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if victim-entry ^{:line 1328 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [k ^{:line 1328 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth victim-entry 0)
   cid ^{:line 1329 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth victim-entry 1)]
  ^{:line 1330 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (retire-claim! cid)
  ^{:line 1331 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx wrap ^{:line 1331 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx ^{:line 1331 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ord-str ^{:line 1331 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:path k) ^{:line 1331 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ord-tie))) new-root tx)) ^{:line 1334 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [last-path ^{:line 1335 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1335 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (seq forms) ^{:line 1335 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1335 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [pk ^{:line 1335 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ^{:line 1335 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (peek forms))]
  ^{:line 1335 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:path pk))))]
  ^{:line 1336 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx wrap ^{:line 1336 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx ^{:line 1336 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ord-str ^{:line 1336 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ord-append ^{:line 1336 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or last-path ^{:line 1336 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} [])) ^{:line 1336 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ord-tie))) new-root tx)))
  ^{:line 1337 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1337 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not *capture-only?*) ^{:line 1337 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1337 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (re-resolve!)))
  ^{:line 1338 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (author-emit-scoped! "upsert-form" ^{:line 1339 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str ^{:line 1339 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if victim-entry "replaced" "added") " top-level def `" new-name "` in \"" scope "\" (1 form minted as claims; refs resolved via refers_to)")))))

^{:line 1346 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn verb-insert-form! [^String scope ^String after-name datum]
  ^{:line 1347 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [target-srcs ^{:line 1347 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filterv ^{:line 1347 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [s] ^{:line 1347 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/includes? s scope)) srcs)]
  ^{:line 1348 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1348 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not= 1 ^{:line 1348 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (count target-srcs)) ^{:line 1348 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1349 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1349 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "REJECTED — insert-form scope \"" scope "\" matches " ^{:line 1350 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (count target-srcs) " files (need 1)."))
  ^{:line 1351 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*reject!* 3)))
  ^{:line 1352 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1352 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and ^{:line 1352 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (seq? datum) ^{:line 1352 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not ^{:line 1352 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? VALUE-DEFS ^{:line 1352 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str ^{:line 1352 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first datum))))) ^{:line 1352 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1353 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1353 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "REJECTED — insert-form head `" ^{:line 1353 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first datum) "` not a value def."))
  ^{:line 1354 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*reject!* 3)))
  ^{:line 1355 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [src ^{:line 1355 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first target-srcs)
   wrap ^{:line 1356 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (wrapper-of src)
   forms ^{:line 1357 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (wrap-forms wrap)
   anchor-bind ^{:line 1358 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def-binding src after-name)
   anchor-form ^{:line 1359 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if anchor-bind ^{:line 1359 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1359 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (form-for-victim src anchor-bind)))
   idx ^{:line 1361 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if anchor-form ^{:line 1361 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1362 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ^{:line 1362 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keep-indexed ^{:line 1362 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [i e] ^{:line 1363 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [r ^{:line 1363 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth e 2)]
  ^{:line 1363 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1363 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= r anchor-form) ^{:line 1363 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  i)))) forms))))]
  ^{:line 1365 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1365 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nil? idx) ^{:line 1365 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1366 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1366 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "REJECTED — insert-form anchor `" after-name "` not found in \"" scope "\"."))
  ^{:line 1367 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*reject!* 3)))
  ^{:line 1368 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1368 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? idx) ^{:line 1368 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1369 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [i idx
   anchor-path ^{:line 1370 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [k ^{:line 1370 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ^{:line 1370 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth forms i))]
  ^{:line 1370 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:path k))
   next-path ^{:line 1372 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1372 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (< ^{:line 1372 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (+ i 1) ^{:line 1372 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (count forms)) ^{:line 1372 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1372 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [k ^{:line 1372 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first ^{:line 1372 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth forms ^{:line 1372 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (+ i 1)))]
  ^{:line 1372 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:path k))))
   new-root ^{:line 1373 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mint-datum! src datum)]
  ^{:line 1374 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx wrap ^{:line 1374 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx ^{:line 1374 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ord-str ^{:line 1374 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ord-between anchor-path next-path) ^{:line 1374 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (ord-tie))) new-root tx)
  ^{:line 1375 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1375 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not *capture-only?*) ^{:line 1375 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1375 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (re-resolve!)))
  ^{:line 1376 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (author-emit-scoped! "insert-form" ^{:line 1377 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "inserted def after `" after-name "` in \"" scope "\" (CRDT mid-insert)"))))))))

^{:line 1387 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn- next-comment-idx [form]
  ^{:line 1388 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (+ 1 ^{:line 1388 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (reduce ^{:line 1388 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [acc n] ^{:line 1388 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (max acc n)) -1 ^{:line 1390 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (keep ^{:line 1390 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [cid] ^{:line 1391 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [p ^{:line 1391 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/literal ctx ^{:line 1391 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim-p ctx cid))]
  ^{:line 1392 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1392 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and ^{:line 1392 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (string? p) ^{:line 1392 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (re-matches #"comment\d+" p)) ^{:line 1392 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1393 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (parse-long ^{:line 1393 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (subs p 7)))))) ^{:line 1394 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/by-l ctx form)))))

^{:line 1396 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn verb-insert-comment! [^String scope ^String anchor-name ^String text ^String placement]
  ^{:line 1397 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [target-srcs ^{:line 1397 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filterv ^{:line 1397 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [s] ^{:line 1397 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/includes? s scope)) srcs)]
  ^{:line 1398 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1398 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not= 1 ^{:line 1398 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (count target-srcs)) ^{:line 1398 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1399 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1399 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "REJECTED — insert-comment scope \"" scope "\" matches " ^{:line 1400 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (count target-srcs) " files (need 1)."))
  ^{:line 1401 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*reject!* 3)))
  ^{:line 1402 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1402 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/blank? text) ^{:line 1402 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1403 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! "REJECTED — insert-comment needs non-empty --text; no claims mutated.")
  ^{:line 1404 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*reject!* 3)))
  ^{:line 1405 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [src ^{:line 1405 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first target-srcs)
   plc ^{:line 1406 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1406 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? ^{:line 1406 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} #{"leading" "trailing"} placement) placement "leading")
   lex ^{:line 1407 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1407 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/starts-with? ^{:line 1407 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/triml text) ";") text ^{:line 1407 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str ";; " text))
   anchor-bind ^{:line 1408 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def-binding src anchor-name)
   anchor-form ^{:line 1409 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if anchor-bind ^{:line 1409 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1409 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (form-for-victim src anchor-bind)))]
  ^{:line 1410 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1410 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nil? anchor-form) ^{:line 1410 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1411 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1411 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "REJECTED — insert-comment anchor `" anchor-name "` not found in \"" scope "\"."))
  ^{:line 1412 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*reject!* 3)))
  ^{:line 1413 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1413 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? anchor-form) ^{:line 1413 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1414 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [af anchor-form
   k ^{:line 1415 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (next-comment-idx af)
   cnode ^{:line 1416 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (register! src ^{:line 1416 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/entity! ctx))
   seg ^{:line 1417 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (register! src ^{:line 1417 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/entity! ctx))]
  ^{:line 1418 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx cnode KIND ^{:line 1418 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx "comment") tx)
  ^{:line 1419 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx cnode ^{:line 1419 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx "style") ^{:line 1419 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx "line") tx)
  ^{:line 1420 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx cnode ^{:line 1420 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx "placement") ^{:line 1420 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx plc) tx)
  ^{:line 1421 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx seg KIND ^{:line 1421 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx "text") tx)
  ^{:line 1422 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx seg Vp ^{:line 1422 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx lex) tx)
  ^{:line 1423 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx cnode ^{:line 1423 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx "seg0") seg tx)
  ^{:line 1424 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx af ^{:line 1424 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx ^{:line 1424 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "comment" k)) cnode tx)
  ^{:line 1425 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1425 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not *capture-only?*) ^{:line 1425 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1425 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (re-resolve!)))
  ^{:line 1426 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (author-emit-scoped! "insert-comment" ^{:line 1427 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "added " plc " comment on `" anchor-name "` in \"" scope "\" (comment" k "; 1 text seg minted)"))))))))

^{:line 1430 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn verb-set-body! [^String name ^String scope datum]
  ^{:line 1431 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [target-srcs ^{:line 1431 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filterv ^{:line 1431 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [s] ^{:line 1431 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/includes? s scope)) srcs)]
  ^{:line 1432 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1432 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not= 1 ^{:line 1432 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (count target-srcs)) ^{:line 1432 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1433 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1433 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "REJECTED — scope \"" scope "\" matches " ^{:line 1433 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (count target-srcs) " source files; set-body needs exactly one (no claims mutated)."))
  ^{:line 1435 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*reject!* 3)))
  ^{:line 1436 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [src ^{:line 1436 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (first target-srcs)
   B ^{:line 1437 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (def-binding src name)
   form ^{:line 1438 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1438 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? B) ^{:line 1438 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1438 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (form-for-victim src B)))
   d ^{:line 1439 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1439 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? form) ^{:line 1439 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1439 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (unwrap-def form)))
   dhead ^{:line 1440 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1440 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? d) ^{:line 1440 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1440 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (head-sym d)))]
  ^{:line 1441 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1441 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or ^{:line 1441 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nil? form) ^{:line 1441 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not ^{:line 1441 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? PARAM-FORMS dhead))) ^{:line 1441 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1442 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1442 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "REJECTED — `" name "` is not a defn with a body in \"" scope "\" (set-body needs a defn; no claims mutated)."))
  ^{:line 1444 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*reject!* 5)))
  ^{:line 1445 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1445 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some? d) ^{:line 1445 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1446 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [dd d
   kids ^{:line 1447 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fN-claims dd)
   bracket-n ^{:line 1449 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some ^{:line 1449 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [e] ^{:line 1450 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [n ^{:line 1450 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth e 0)
   r ^{:line 1450 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth e 2)]
  ^{:line 1450 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1450 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (brackets? r) ^{:line 1450 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  n)))) kids)
   bn ^{:line 1452 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (or bracket-n 0)
   ret? ^{:line 1454 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (some ^{:line 1454 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [e] ^{:line 1455 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [n ^{:line 1455 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth e 0)
   r ^{:line 1455 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth e 2)]
  ^{:line 1456 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1456 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (and ^{:line 1456 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= n ^{:line 1456 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (+ bn 1)) ^{:line 1456 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (contains? TYPE-COLON ^{:line 1456 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (sym-val r))) ^{:line 1456 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  n)))) kids)
   body-start ^{:line 1458 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (+ bn ^{:line 1458 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ret? 3 1))
   body-slots ^{:line 1460 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filterv ^{:line 1460 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [e] ^{:line 1460 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [n ^{:line 1460 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth e 0)]
  ^{:line 1460 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (>= n body-start))) kids)
   new-root ^{:line 1461 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (mint-datum! src datum)]
  ^{:line 1462 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1462 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (empty? body-slots) ^{:line 1462 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1463 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1463 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "REJECTED — `" name "` has no body fN edges to replace; no claims mutated."))
  ^{:line 1464 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (*reject!* 5)))
  ^{:line 1465 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (doseq [e body-slots]
  ^{:line 1465 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [cid ^{:line 1465 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (nth e 1)]
  ^{:line 1465 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (retire-claim! cid)))
  ^{:line 1466 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/claim! ctx dd ^{:line 1466 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (c/value! ctx ^{:line 1466 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "f" body-start)) new-root tx)
  ^{:line 1467 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if ^{:line 1467 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (not *capture-only?*) ^{:line 1467 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1467 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (re-resolve!)))
  ^{:line 1468 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (author-emit-scoped! "set-body" ^{:line 1469 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "replaced body of defn `" name "` in \"" scope "\" (" ^{:line 1470 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (count body-slots) " body slot(s) superseded; new body minted as claims)"))))))))

^{:line 1474 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (defn run-verb-warm! [store spec]
  ^{:line 1475 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [module ^{:line 1475 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:module spec)]
  ^{:line 1476 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (binding [*resolve-out* ^{:line 1476 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:resolve-out spec)]
  ^{:line 1477 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (resolve-warm-store! store ^{:line 1479 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [] ^{:line 1480 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (binding [*project-srcs* ^{:line 1480 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (if module ^{:line 1480 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1481 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (let [s srcs
   m module]
  ^{:line 1483 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (filter ^{:line 1483 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (fn [x] ^{:line 1483 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str/includes? x m)) s))))]
  ^{:line 1484 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (cond
  ^{:line 1485 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= ^{:line 1485 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:op spec) "rename") ^{:line 1485 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (verb-rename! ^{:line 1485 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:old spec) ^{:line 1485 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:new spec) module)
  ^{:line 1486 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= ^{:line 1486 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:op spec) "upsert-form") ^{:line 1486 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (verb-upsert-form! module ^{:line 1486 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:datum spec))
  ^{:line 1487 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= ^{:line 1487 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:op spec) "insert-form") ^{:line 1487 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (verb-insert-form! module ^{:line 1487 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:after spec) ^{:line 1487 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:datum spec))
  ^{:line 1488 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= ^{:line 1488 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:op spec) "insert-comment") ^{:line 1488 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (verb-insert-comment! module ^{:line 1488 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:after spec) ^{:line 1488 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:text spec) ^{:line 1488 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:placement spec))
  ^{:line 1489 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (= ^{:line 1489 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:op spec) "set-body") ^{:line 1489 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (verb-set-body! ^{:line 1489 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:name spec) module ^{:line 1489 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:datum spec))
  :else ^{:line 1490 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (do
  ^{:line 1490 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/println-err! ^{:line 1490 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (str "run-verb-warm!: unknown op " ^{:line 1490 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (:op spec)))
  ^{:line 1491 :file "/home/tom/code/fram-lease/chartroom/src/resolve.bclj"} (rt/exit! 2)))))))
  module))
;; resolve_cli.clj — the hand-Clojure CLI tail appended to the Beagle-emitted
;; resolver (chartroom/src/resolve.bclj) to form chartroom/src/resolve.clj (ns resolve).
;; The bb/CLI -main + the callgraph mode (datalog + cheshire) live here — no Beagle value.
;; Regenerate resolve.clj: bin/build-resolve.sh

;; ---- CLI driver (Clojure: bb/CLI + callgraph's datalog/cheshire edge) ----
(require (quote [clojure.edn :as edn]) (quote [fram.datalog :as d]) (quote [cheshire.core :as json]))
(def mode (first *command-line-args*))

(def MODES #{"resolve" "rename" "delete" "callgraph" "upsert-form" "set-body"})
(defn -main []
  (let [edn-paths (drop (case mode "resolve" 1 "rename" 4 "delete" 3 "callgraph" 1
                                   "upsert-form" 3 "set-body" 4)
                        *command-line-args*)]
    (resolve-edn!
     edn-paths
     (fn []
(case mode
  "resolve"
  (binding [*out* *err*]
    (println "================ Turtle #5 — lexical resolution pass ================")
    (println (str "references resolved (carry refers_to → a binding node): " @n-resolved
                  "  (" @n-xmod " cross-module, " @n-type " type references)"))
    (println (str "unresolved (builtins / native — correctly NO refers_to): " @n-unresolved))
    (println (str "comment identifier mentions resolved (rename-correct doc comments): " @n-comment))
    ;; write the resolved projection so identity can be checked: with NO rename,
    ;; projecting through refers_to must reproduce the original source exactly.
    (doseq [src srcs] (extract-file! src (out-path src)))
    (doseq [src srcs]
      (println (str "  " (-> src (str/split #"/") last) ": "
                    (count (filter #(and (= "symbol" (kind-of %)) (refers-target %)) (@file->ents src)))
                    " references carry refers_to; projected (identity) -> " (out-path src)))))

  "rename"
  (let [[old new target] (drop 1 *command-line-args*)]
    (verb-rename! old new target))

  "delete"
  (let [[name target] (drop 1 *command-line-args*)
        target-srcs (filter #(str/includes? % target) srcs)
        victims (keep #(def-binding % name) target-srcs)   ; value OR type binding occurrences to delete
        ;; the top-level forms to remove + their whole subtrees (incl. each form's own
        ;; doc-comment AND, for a defunion, its variant-constructor name-leaves). Computed
        ;; FIRST so the orphan check can both exclude refs INSIDE a deleted form and flag
        ;; surviving refs to ANY binding the deletion removes — the union name OR a variant.
        all-forms (set (mapcat (fn [src] (keep #(form-for-victim src %) victims)) srcs))
        subtree (reduce into #{} (map descendants all-forms))
        orphans (for [src srcs, e (@file->ents src)
                      :when (and (= "symbol" (kind-of e)) (refers-target e) (not (subtree e))
                                 (subtree (ultimate (refers-target e))))] e)]   ; ref to a deleted binding
    (when (zero? (count victims))
      (binding [*out* *err*]
        (println (str "REJECTED — no binding named `" name "` found in \"" target "\" (nothing to delete).")))
      (System/exit 5))
    ;; matched a binding but no independently-deletable top-level form (e.g. a defunion
    ;; variant lives nested inside its union) — refuse, don't report a no-op as success.
    (when (empty? all-forms)
      (binding [*out* *err*]
        (println (str "REJECTED — `" name "` is not an independently-deletable top-level form "
                      "(a defunion variant / nested binding); no claims mutated.")))
      (System/exit 5))
    ;; INVARIANT (no-orphaned-refs): refuse if any SURVIVING reference points at a victim.
    (when (pos? (count orphans))
      (binding [*out* *err*]
        (println "================ Turtle #5 — delete + orphaned-reference invariant ================")
        (println (str "REJECTED — " (count orphans) " reference(s) would be ORPHANED (no-orphaned-refs):"))
        (doseq [o (take 5 orphans)] (println (str "  orphan: reference node " o " (`" (sym-val o) "`)"))))
      (System/exit 6))
    ;; SAFE: project each src with the victim forms (and their subtrees) omitted, siblings renumbered.
    (binding [*deleted-forms* all-forms *deleted-subtree* subtree]
      (doseq [src srcs] (extract-file! src (out-path src))))
    (binding [*out* *err*]
      (println "================ Turtle #5 — delete (no-orphaned-refs satisfied) ================")
      (println (str "deleted def `" name "` in \"" target "\": " (count all-forms) " form(s); 0 orphaned refs"))
      (doseq [src srcs] (println (str "projected -> " (out-path src) "   <- " src)))))

  ;; ============================================================================
  ;; AUTHORING VERBS — the GAP closed: a claim operation for novel authoring.
  ;; upsert-form : add a NEW top-level def (append a wrapper fN edge) OR replace an
  ;;               existing top-level def by name (supersede its wrapper fN edge to
  ;;               point at a freshly-minted subtree). The form is given as an EDN
  ;;               datum (the structured edit spec), minted into the SAME store.
  ;; Both reuse extract-file! (the rename/delete render machine) and re-run the
  ;; lexical walk over the post-mint corpus, so a reference in the new code resolves
  ;; via refers_to (scope-correct) exactly like hand-written code — then the recompile
  ;; gate (authoring.sh) is the only acceptance criterion. fail-closed before that.
  ;; ============================================================================
  "upsert-form"
  (let [[scope spec-file] (drop 1 *command-line-args*)
        datum (edn/read-string (slurp spec-file))]
    (verb-upsert-form! scope datum))

  ;; set-body : replace a defn's BODY — supersede every post-params fN edge of the
  ;; named defn and re-wire to a freshly-minted body datum.
  "set-body"
  (let [[name scope body-file] (drop 1 *command-line-args*)
        datum (edn/read-string (slurp body-file))]
    (verb-set-body! name scope datum))

  ;; ============================================================================
  ;; callgraph — the scope-correct call graph + transitive blast radius, derived
  ;; from the SAME refers_to edges the rename/delete engine uses. A "call" is a
  ;; reference in list-HEAD position whose binding (followed transitively) is a
  ;; top-level defn; the caller is its enclosing top-level defn. Because refers_to
  ;; is the converged cross-module/multi-arity/collision-correct resolution, this
  ;; call graph is too — unlike a bare-callname index, it does NOT drop qualified
  ;; (a/f, m/f) cross-module calls. Emits the JSON beagle-cascade consumes.
  ;; ============================================================================
  "callgraph"
  (let [dkey      (fn [src leaf] (str src "#" leaf))
        defn-meta (into {} (for [src srcs, [nm leaf] (file-modframe src)]
                             [leaf {:key (dkey src leaf) :file src
                                    :module (or (module-name src)
                                                (-> src (str/split #"/") last (str/replace #"\.[^.]+$" "")))
                                    :name nm}]))
        defn-set  (set (keys defn-meta))
        ;; ALL resolved reference symbols in a subtree (any position — not just list-head).
        ;; For a BLAST RADIUS ("what must change if I change X"), every reference to a defn is
        ;; a dependency: a head call (f x), a value-pass (mapv f xs), a threaded step (-> x f),
        ;; a `:- T` annotation. Head-only silently under-reports (proven on shipped fram/src).
        call-refs (fn call-refs [node]
                    (if (refers-target node) [node]
                      (when (= "list" (kind-of node)) (mapcat call-refs (ordered-children node)))))
        ;; callers = [caller-defn-leaf, body-node] pairs. A top-level value defn is one caller;
        ;; an extend-type/extend-protocol attributes each impl method's body to that protocol
        ;; method (the impl method-name resolves to it via refers_to) — those bodies were skipped.
        callers (mapcat
                 (fn [form]
                   (let [d (unwrap-def form) h (head-sym d)]
                     (cond
                       (VALUE-DEFS h)
                       (let [cl (second (ordered-children d))] (when (defn-meta cl) [[cl d]]))
                       (#{"extend-type" "extend-protocol"} h)
                       (keep (fn [c] (when (= "list" (kind-of c))
                                       (let [mnode (first (ordered-children c))
                                             cl (when (sym-val mnode) (ultimate (refers-target mnode)))]
                                         (when (and cl (defn-meta cl)) [cl c]))))
                             (rest (ordered-children d))))))
                 (mapcat forms-of srcs))
        edges (vec (distinct
                    (for [[caller-leaf body] callers
                          r (call-refs body)
                          :let [callee (ultimate (refers-target r))]  ; follow refers_to to the bound defn
                          :when (and (defn-set callee) (not= callee caller-leaf))]
                      [(:key (defn-meta caller-leaf)) (:key (defn-meta callee))])))
        ;; transitive blast radius via Fram Datalog: blast(D) = {x | x transitively calls D}
        bctx (c/new-store) btx (c/begin-tx! bctx "code") EDGE (c/value! bctx "calls-defn")
        k->e (volatile! {})
        bent (fn [k] (or (get @k->e k) (let [e (c/entity! bctx)] (vswap! k->e assoc k e) e)))
        _ (doseq [[a b] edges] (c/claim! bctx (bent a) EDGE (bent b) btx))
        e->k (into {} (map (fn [[k v]] [v k]) @k->e))
        db (d/run-rules bctx
             [(d/rule "reaches" [(d/v :x) (d/v :y)] [(d/lit "triple" [(d/v :x) EDGE (d/v :y)])])
              (d/rule "reaches" [(d/v :x) (d/v :z)] [(d/lit "triple" [(d/v :x) EDGE (d/v :y)])
                                                     (d/lit "reaches" [(d/v :y) (d/v :z)])])])
        reaches (set (d/facts db "reaches"))
        blast (reduce (fn [m [xid yid]] (update m (e->k yid) (fnil conj #{}) (e->k xid))) {} reaches)]
    (binding [*out* *err*]
      (println (format "callgraph: %d defns, %d scope-correct edges, %d transitive reaches-pairs (refers_to + Fram Datalog)"
                       (count defn-meta) (count edges) (count reaches))))
    (println (json/generate-string
              {:defns (vec (vals defn-meta)) :edges edges
               :blast (into {} (map (fn [[k vs]] [k (vec vs)]) blast))}))))))))

;; GUARD: run the pipeline only when invoked as a CLI with a recognized mode.
;; Loaded as a library (no mode arg, or an unrecognized one), this is a no-op —
;; so a daemon can `require`/load this file and call `resolve-edn!` over its own
;; warm store without the old top-level load-edn crashing on mis-sliced args.
(when (MODES mode) (-main))
