(ns chelonia.time
  (:require [clojure.string :as str]
            [chelonia.rt :as rt]
            [chelonia.json :as json]))

(defn- ^String log-path [^String dir]
  (str dir "/log.jsonl"))

(defn- ^String current-path [^String dir]
  (str dir "/current.json"))

(defn- ^String projects-path [^String dir]
  (str dir "/projects.json"))

(defn- ^String today-prefix []
  (subs (chelonia.rt/now-iso) 0 10))

(defn- duration-seconds [^String start ^String end]
  (- (chelonia.rt/iso-to-seconds end) (chelonia.rt/iso-to-seconds start)))

(defn- ^String zero-pad-2 [n]
  (if (< n 10) (str "0" n) (str n)))

(defn- ^String fmt-duration [secs]
  (let [h (quot secs 3600)
   m (quot (mod secs 3600) 60)]
  (if (zero? h) (str m "m") (str h "h " (zero-pad-2 m) "m"))))

(defn- ^String normalize-iso [^String s]
  (let [t (str/replace s " " "T")]
  (cond
  (chelonia.rt/is-iso-datetime-19 t) t
  (chelonia.rt/is-iso-datetime-16 t) (str t ":00")
  :else (chelonia.rt/error-exit (str "bad timestamp \"" s "\" (expected YYYY-MM-DD HH:MM)")))))

(defn- ^String entry-id [^String start ^String task]
  (let [prefix (chelonia.rt/filter-digits (subs start 0 16))]
  (str prefix "-" task)))

(defn- ^String pad-right [^String s width]
  (let [n (count s)]
  (if (>= n width) s (str s (chelonia.rt/repeat-str " " (- width n))))))

(defn- ^String entry-to-json [^String id ^String task ^String start end ^String notes ^String source synced-to]
  (chelonia.json/to-string (chelonia.json/put-raw (chelonia.json/put (chelonia.json/put (chelonia.json/put (chelonia.json/put (chelonia.json/put (chelonia.json/put (chelonia.json/empty) "id" id) "task" task) "start" start) "end" end) "notes" notes) "source" source) "synced_to" synced-to)))

(def EMPTY-ENTRIES [])

(defrecord TimeEntry [id task start end notes source synced-to])

(defn timeentry-id [r] (:id r))

(defn timeentry-task [r] (:task r))

(defn timeentry-start [r] (:start r))

(defn timeentry-end [r] (:end r))

(defn timeentry-notes [r] (:notes r))

(defn timeentry-source [r] (:source r))

(defn timeentry-synced-to [r] (:synced-to r))

(defn- ^TimeEntry parse-entry [^String line]
  (let [j (chelonia.json/parse line)
   n (chelonia.json/get j "notes")
   s (chelonia.json/get j "source")
   raw-sync (chelonia.json/get-raw j "synced_to")]
  (->TimeEntry (let [v (chelonia.json/get j "id")]
  (if (nil? v) "" v)) (let [v (chelonia.json/get j "task")]
  (if (nil? v) "" v)) (let [v (chelonia.json/get j "start")]
  (if (nil? v) "" v)) (chelonia.json/get j "end") (if (nil? n) "" n) (if (nil? s) "manual" s) raw-sync)))

(defn- ^String entry-json-line [^TimeEntry e]
  (str (entry-to-json (:id e) (:task e) (:start e) (:end e) (:notes e) (:source e) (:synced-to e)) "\n"))

(defn- load-entries [^String dir]
  (chelonia.rt/create-dirs dir)
  (let [p (log-path dir)]
  (if (not (chelonia.rt/file-exists p)) [] (let [lines (filterv (fn [line] (not (= line ""))) (chelonia.rt/split-on (slurp p) "\n"))]
  (mapv (fn [line] (parse-entry line)) lines)))))

(defn- write-entries [^String dir entries]
  (chelonia.rt/create-dirs dir)
  (spit (log-path dir) (reduce (fn [acc e] (str acc (entry-json-line e))) "" entries)))

(defn- append-entry [^String dir ^TimeEntry e]
  (chelonia.rt/create-dirs dir)
  (chelonia.rt/spit-append (log-path dir) (entry-json-line e)))

(defn- load-current [^String dir]
  (let [p (current-path dir)]
  (if (chelonia.rt/file-exists p) (parse-entry (slurp p)) nil)))

(defn- save-current [^String dir ^TimeEntry e]
  (chelonia.rt/create-dirs dir)
  (spit (current-path dir) (entry-to-json (:id e) (:task e) (:start e) (:end e) (:notes e) (:source e) (:synced-to e))))

(defn- clear-current [^String dir]
  (let [p (current-path dir)]
  (if (chelonia.rt/file-exists p) (do
  (chelonia.rt/delete-file p)))))

(defn- load-projects [^String dir]
  (let [p (projects-path dir)]
  (if (chelonia.rt/file-exists p) (chelonia.json/parse (slurp p)) (chelonia.json/empty))))

(defn- save-projects [^String dir m]
  (chelonia.rt/create-dirs dir)
  (spit (projects-path dir) (chelonia.json/to-string (chelonia.json/sort-keys m))))

(defn cmd-on [^String dir ^String task ^String notes]
  (let [cur (load-current dir)]
  (if (some? cur) (do
  (let [now (chelonia.rt/now-iso)
   elapsed (duration-seconds (:start cur) now)]
  (append-entry dir (->TimeEntry (:id cur) (:task cur) (:start cur) now (:notes cur) (:source cur) (:synced-to cur)))
  (println (str "(clocked out: " (:task cur) ", " (fmt-duration elapsed) ")")))))
  (let [start (chelonia.rt/now-iso)
   entry (->TimeEntry (entry-id start task) task start nil notes "clock-in" nil)]
  (save-current dir entry)
  (if (= notes "") (println (str "clocked in: " task " @ " start)) (println (str "clocked in: " task " @ " start " — " notes))))))

(defn cmd-off [^String dir ^String notes]
  (let [cur (load-current dir)]
  (if (nil? cur) (do
  (chelonia.rt/error-exit "not clocked in")))
  (let [end (chelonia.rt/now-iso)
   final-entry (if (= notes "") (->TimeEntry (:id cur) (:task cur) (:start cur) end (:notes cur) (:source cur) (:synced-to cur)) (->TimeEntry (:id cur) (:task cur) (:start cur) end notes (:source cur) (:synced-to cur)))]
  (append-entry dir final-entry)
  (clear-current dir)
  (println (str "clocked out: " (:task cur) " (" (fmt-duration (duration-seconds (:start cur) end)) ")")))))

(defn cmd-log [^String dir ^String task ^String start ^String end ^String notes]
  (let [start-n (normalize-iso start)
   end-n (normalize-iso end)]
  (if (>= (chelonia.rt/iso-to-seconds start-n) (chelonia.rt/iso-to-seconds end-n)) (do
  (chelonia.rt/error-exit (str "start must be before end (" start-n " >= " end-n ")"))))
  (append-entry dir (->TimeEntry (entry-id start-n task) task start-n end-n notes "manual" nil))
  (println (str "logged: " task "  " start-n " → " end-n "  (" (fmt-duration (duration-seconds start-n end-n)) ")"))))

(defn cmd-status [^String dir]
  (let [cur (load-current dir)]
  (if (some? cur) (println (str "ON: " (:task cur) " — started " (:start cur) " (" (fmt-duration (duration-seconds (:start cur) (chelonia.rt/now-iso))) " elapsed)")) (println "(not clocked in)"))
  (let [prefix (today-prefix)
   entries (load-entries dir)
   today-secs (reduce (fn [acc e] (if (and (some? (:end e)) (str/starts-with? (:start e) prefix)) (+ acc (duration-seconds (:start e) (:end e))) acc)) 0 entries)]
  (println (str "today: " (fmt-duration today-secs))))))

(defn- ^Boolean any-prefix-match [^String s prefixes]
  (loop [ps prefixes]
  (if (empty? ps) false (if (str/starts-with? s (first ps)) true (recur (rest ps))))))

(defn- summarize-prefixes [entries prefixes ^String label]
  (let [matches (filterv (fn [e] (and (some? (:end e)) (any-prefix-match (:start e) prefixes))) entries)]
  (if (empty? matches) (println (str "(no entries for " label ")")) (let [total (reduce (fn [acc e] (+ acc (duration-seconds (:start e) (:end e)))) 0 matches)
   task-keys (sort (distinct (mapv (fn [e] (:task e)) matches)))]
  (println (str label ": " (count matches) " entries, " (fmt-duration total) " total"))
  (doseq [tk task-keys]
  (let [task-secs (reduce (fn [acc e] (if (= (:task e) tk) (+ acc (duration-seconds (:start e) (:end e))) acc)) 0 matches)]
  (println (str "  " (pad-right tk 16) "  " (fmt-duration task-secs)))))
  (doseq [e (sort-by (fn [e] (:start e)) matches)]
  (let [secs (duration-seconds (:start e) (:end e))
   note-suffix (if (= "" (:notes e)) "" (str "  — " (:notes e)))]
  (println (str "  " (subs (:start e) 0 16) " → " (subs (:end e) 0 16) "  " (pad-right (:task e) 16) "  " (fmt-duration secs) note-suffix))))))))

(defn cmd-today [^String dir]
  (let [prefixes (conj EMPTY-ENTRIES (today-prefix))]
  (summarize-prefixes (load-entries dir) prefixes "today")))

(defn cmd-week [^String dir]
  (summarize-prefixes (load-entries dir) (chelonia.rt/this-week-dates) "this week"))

(defn cmd-import-org [^String dir file ^String task]
  (let [org-file (if (nil? file) (chelonia.rt/error-exit "usage: time import-org <org-file> <task>") file)]
  (if (not (chelonia.rt/file-exists org-file)) (do
  (chelonia.rt/error-exit (str "no such file: " org-file))))
  (let [content (slurp org-file)
   entries (load-entries dir)
   existing-ids (mapv (fn [e] (:id e)) entries)
   lines (chelonia.rt/split-on content "\n")]
  (loop [rest-lines lines
   added 0
   skipped 0]
  (if (empty? rest-lines) (println (str "import: " added " added, " skipped " already present (from " org-file ")")) (let [line (first rest-lines)]
  (if (not (str/starts-with? (str/trim line) "CLOCK: [")) (recur (rest rest-lines) added skipped) (let [trimmed (str/trim line)
   start (str (subs trimmed 8 12) "-" (subs trimmed 13 15) "-" (subs trimmed 16 18) "T" (subs trimmed 23 25) ":" (subs trimmed 26 28) ":00")
   end (str (subs trimmed 31 35) "-" (subs trimmed 36 38) "-" (subs trimmed 39 41) "T" (subs trimmed 46 48) ":" (subs trimmed 49 51) ":00")
   id (entry-id start task)
   already (loop [ids existing-ids]
  (if (empty? ids) false (if (= (first ids) id) true (recur (rest ids)))))]
  (if already (recur (rest rest-lines) added (+ skipped 1)) (do
  (append-entry dir (->TimeEntry id task start end "" "org-import" nil))
  (recur (rest rest-lines) (+ added 1) skipped)))))))))))

(defn- ^String clockify-key []
  (let [env-key (chelonia.rt/getenv "CLOCKIFY_API_KEY")
   secret-file (chelonia.rt/getenv "CLOCKIFY_SECRET_FILE")]
  (cond
  (some? env-key) env-key
  (and (some? secret-file) (chelonia.rt/file-exists secret-file)) (str/trim (slurp secret-file))
  :else (chelonia.rt/error-exit "no clockify key — set CLOCKIFY_API_KEY, or CLOCKIFY_SECRET_FILE to a key file"))))

(defn- clockify-get [^String path]
  (let [body (chelonia.rt/http-get (str "https://api.clockify.me/api/v1" path) (clockify-key))]
  (if (= body "") (chelonia.json/empty) (chelonia.json/parse body))))

(defn- clockify-post [^String path body-data]
  (let [body (chelonia.rt/http-post (str "https://api.clockify.me/api/v1" path) (clockify-key) (chelonia.json/to-string body-data))]
  (if (= body "") (chelonia.json/empty) (chelonia.json/parse body))))

(defn- ^String default-workspace []
  (let [env-ws (chelonia.rt/getenv "CLOCKIFY_WORKSPACE_ID")]
  (if (some? env-ws) env-ws (let [user (clockify-get "/user")
   ws (chelonia.json/get user "defaultWorkspace")]
  (if (nil? ws) (chelonia.rt/error-exit "no defaultWorkspace in /user response") ws)))))

(defn cmd-workspaces []
  (doseq [w (cheshire.core/parse-string (chelonia.rt/http-get "https://api.clockify.me/api/v1/workspaces" (clockify-key)) false)]
  (println (str (get w "id" "") "  " (get w "name" "")))))

(defn cmd-projects []
  (let [ws (default-workspace)]
  (doseq [p (cheshire.core/parse-string (chelonia.rt/http-get (str "https://api.clockify.me/api/v1/workspaces/" ws "/projects?page-size=100") (clockify-key)) false)]
  (println (str (get p "id" "") "  " (get p "name" ""))))))

(defn cmd-map [^String dir ^String task ^String project-id]
  (let [mapping (load-projects dir)]
  (save-projects dir (chelonia.json/put mapping task project-id))
  (println (str "mapped: " task " → " project-id))))

(defn- ^String project-id-for-task [^String dir ^String task]
  (let [mapping (load-projects dir)
   proj (chelonia.json/get mapping task)]
  (if (nil? proj) (chelonia.rt/error-exit (str "no clockify project mapped for '" task "'. run: chelonia time map " task " <project-id>")) proj)))

(defn cmd-sync [^String dir]
  (let [ws (default-workspace)
   entries (load-entries dir)
   unsynced (filterv (fn [e] (and (some? (:end e)) (nil? (:synced-to e)))) entries)]
  (if (empty? unsynced) (println "nothing to sync.") (let [updated (mapv (fn [e] (if (or (nil? (:end e)) (some? (:synced-to e))) e (let [proj (project-id-for-task dir (:task e))
   resp (clockify-post (str "/workspaces/" ws "/time-entries") (-> (chelonia.json/empty) (chelonia.json/put "start" (str (:start e) "Z")) (chelonia.json/put "end" (str (:end e) "Z")) (chelonia.json/put "description" (:notes e)) (chelonia.json/put "projectId" proj)))
   clockify-id (let [cid (chelonia.json/get resp "id")]
  (if (nil? cid) "" cid))]
  (println (str "  ✓ " (:task e) "  " (:start e) "  (clockify id " clockify-id ")"))
  (->TimeEntry (:id e) (:task e) (:start e) (:end e) (:notes e) (:source e) (str "{\"clockify\":\"" clockify-id "\"}"))))) entries)]
  (write-entries dir updated)
  (println "done.")))))
