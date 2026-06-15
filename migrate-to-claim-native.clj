;; migrate-to-claim-native.clj — one-off corpus migration:
;;   old YAML-frontmatter threads  ->  new claim-triple files.
;;
;; Transforms, in one pass:
;;   - id reformat            20260101090000 -> 2026-01-01-090000  (+ all cross-refs)
;;   - YAML frontmatter       -> @subject + `predicate  object` triple lines
;;   - entity prefixes        -> @refs (created_by/lead/driver/proposed_by/part_of/depends_on)
;;   - state enum             -> lifecycle facts (committed / outcome / abandoned)
;;   - tags                   -> relates_to a minted topic thread (@topic-<slug>)
;;   - body                   -> kept verbatim after `---`
;;
;; Usage:  bb migrate-to-claim-native.clj <dir>     (rewrites *.md in place; mints topic threads)
(require '[clojure.string :as str] '[clojure.java.io :as io])

(def tdir (or (first *command-line-args*) "threads"))
(def today (str (java.time.LocalDate/now)))

(defn unq [v]
  (let [n (count v)]
    (cond
      (and (>= n 2) (str/starts-with? v "\"") (str/ends-with? v "\"")) (str/replace (subs v 1 (dec n)) "\\\"" "\"")
      (and (>= n 2) (str/starts-with? v "'") (str/ends-with? v "'")) (str/replace (subs v 1 (dec n)) "''" "'")
      :else v)))

(defn parse-fm [fm]
  (loop [ls (str/split-lines fm), acc {}]
    (if (empty? ls)
      acc
      (let [line (first ls)]
        (cond
          (str/blank? line) (recur (rest ls) acc)
          (str/starts-with? line "  - ") (recur (rest ls) acc)
          :else
          (let [ci (str/index-of line ":")]
            (if (nil? ci)
              (recur (rest ls) acc)
              (let [k (str/trim (subs line 0 ci))
                    v (str/trim (subs line (inc ci)))]
                (if (= v "")
                  (let [items (->> (rest ls) (take-while #(str/starts-with? % "  - "))
                                   (map #(unq (str/trim (subs % 4)))) vec)]
                    (recur (drop (inc (count items)) ls) (assoc acc k items)))
                  (recur (rest ls) (assoc acc k (unq v))))))))))))

(defn split-doc [content]
  (let [lines (vec (str/split-lines content))]
    (if (or (empty? lines) (not= "---" (str/trim (first lines))))
      [nil content]
      (let [j (first (for [i (range 1 (count lines)) :when (= "---" (str/trim (nth lines i)))] i))]
        (if j
          [(str/join "\n" (subvec lines 1 j)) (str/join "\n" (subvec lines (inc j) (count lines)))]
          [nil content])))))

(defn new-id [old] (if (re-matches #"\d{14}" (str old))
                     (let [s (str old)] (str (subs s 0 4) "-" (subs s 4 6) "-" (subs s 6 8) "-" (subs s 8 14)))
                     (str old)))
(defn as-list [v] (cond (nil? v) [] (sequential? v) (vec v) :else [v]))
(defn fslug [s] (-> (str/lower-case (str s)) (str/replace #"[^a-z0-9]+" "_") (str/replace #"^_+|_+$" "")))
(defn tslug [s] (-> (str/lower-case (str s)) (str/replace #"[^a-z0-9]+" "-") (str/replace #"^-+|-+$" "")))
(defn render-obj [v]
  (let [v (str v)]
    (cond
      (str/starts-with? v "@") v
      (or (str/blank? v) (re-find #"\s" v) (str/starts-with? v "\"")) (pr-str v)
      :else v)))

(defn thread-lines [m]
  (let [te (str "@" (new-id (m "id")))
        L (atom [])
        add (fn [p v] (when (and v (not (str/blank? (str v)))) (swap! L conj (str p "  " (render-obj v)))))
        ref (fn [v] (str "@" v))]
    (add "title" (m "title"))
    (add "owner" (m "owner"))
    (when (m "lead") (add "lead" (ref (m "lead"))))
    (add "source" (m "source"))
    (doseq [p (as-list (m "proposed_by"))] (add "proposed_by" (ref p)))
    (when (m "created_by") (add "created_by" (ref (m "created_by"))))
    (add "created_at" (m "created_at"))
    (add "updated_at" (m "updated_at"))
    ;; driver = currently being worked, so only state=active keeps one.
    (let [st (m "state") at (or (m "created_at") today)]
      (cond
        (= st "ready")    (add "committed" at)
        (= st "active")   (do (add "committed" at) (when (m "driver") (add "driver" (ref (m "driver")))))
        (= st "done")     (do (add "committed" at) (add "outcome" "migrated: completed"))
        (= st "canceled") (add "abandoned" (or (m "canceled_reason") "migrated: canceled"))
        :else nil))
    (add "do_on" (m "do_on"))
    (add "valid_until" (m "valid_until"))
    (add "estimate_hours" (m "estimate_hours"))
    (doseq [r (as-list (m "repo"))] (add "repo" r))
    (when (m "part_of") (add "part_of" (str "@" (new-id (m "part_of")))))
    (doseq [d (as-list (m "depends_on"))] (add "depends_on" (str "@" (new-id d))))
    (when (m "superseded_by") (add "superseded_by" (str "@" (new-id (m "superseded_by")))))
    ;; clarifies / amends are thread-ref edges too (these were silently dropped
    ;; in the first migration run — see the cutover-verify findings).
    (doseq [r (as-list (m "clarifies"))] (add "clarifies" (str "@" (new-id r))))
    (doseq [r (as-list (m "amends"))] (add "amends" (str "@" (new-id r))))
    (doseq [t (as-list (m "tags"))] (add "relates_to" (str "@topic-" (tslug t))))
    ;; fall-through: emit ANY remaining frontmatter key as a literal so a future
    ;; corpus can never lose a field we didn't explicitly anticipate.
    (let [handled #{"id" "title" "owner" "lead" "driver" "source" "proposed_by"
                    "created_by" "created_at" "updated_at" "state" "do_on" "valid_until"
                    "estimate_hours" "repo" "part_of" "depends_on" "superseded_by"
                    "clarifies" "amends" "tags" "canceled_reason"}]
      (doseq [k (keys m)]
        (when-not (handled k)
          (doseq [v (as-list (m k))] (add k v)))))
    @L))

(defn thread-file [m body]
  (str "@" (new-id (m "id")) "\n" (str/join "\n" (thread-lines m)) "\n---\n" body))

;; --- run ---
(def files (->> (.listFiles (io/file tdir)) (map #(.getPath %))
                (filter #(str/ends-with? % ".md")) (remove #(str/ends-with? % "CLAUDE.md")) sort))
(def parsed (for [f files :let [[fm body] (split-doc (slurp f))] :when fm] {:f f :m (parse-fm fm) :body body}))
(def tags (->> parsed (mapcat #(as-list (get (:m %) "tags"))) distinct sort))

(println "migrating" (count parsed) "threads;" (count tags) "distinct tags -> topic threads")
;; rewrite each thread in place under its new id-based filename; delete the old file
(doseq [{:keys [f m body]} parsed]
  (let [id (new-id (m "id"))
        nf (str tdir "/" id "-" (fslug (m "title")) ".md")]
    (spit nf (thread-file m body))
    (when (not= nf f) (io/delete-file f true))))
;; mint a topic thread per distinct tag
(doseq [t tags]
  (let [id (str "topic-" (tslug t))]
    (spit (str tdir "/" id ".md")
          (str "@" id "\ntitle  " (render-obj t) "\nowner  personal\nsource  migrated\ncommitted  " today "\n---\n"
               "Topic thread (migrated from tag `" t "`). Threads relate_to this.\n"))))
(println "done.")
