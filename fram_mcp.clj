;; fram_mcp.clj — the AI-facing edge of a Fram instance.
;; ============================================================================
;; Speaks MCP (JSON-RPC 2.0, newline-delimited, over stdio) and serves the SAME
;; catalog + dispatch as the CLI, IN-PROCESS: it folds the current log, generates
;; the claim-named tool catalog (fram.tools), and routes each tools/call to it —
;; reads off the fold, writes through the coordinator (serialized, rule-checked).
;; cheshire keywordizes the JSON arguments into exactly the EDN shape fram.tools /
;; fram.query expect, so a model fills typed params (or, for `query`, emits a
;; structured Datalog-shaped object) and can't author broken syntax.
;;
;;   bb -cp out fram_mcp.clj        (usually via bin/fram-mcp)
;; Diagnostics go to STDERR; stdout is the JSON-RPC channel only.
;; ============================================================================
(require '[cheshire.core :as json]
         '[clojure.string :as str]
         '[fram.kernel :as k]
         '[fram.fold :as fold]
         '[fram.tools :as tl]
         '[fram.rt])

(defn- log! [& xs] (binding [*out* *err*] (apply println xs)))

(def instructions
  (str
   "Fram is a claim engine: every fact is a triple (subject predicate object); a "
   "thread is any @id with a `title`. Lifecycle is DERIVED from facts (committed / "
   "outcome / abandoned / driver / depends_on), never a stored status.\n\n"
   "These tools are GENERATED from the claim vocabulary, so they are named after "
   "your claims. For each predicate P: `P-of`/`P-list` read it, `set-P`/`add-P`/"
   "`remove-P` write it, and for reference predicates `P-from` walks the reverse "
   "edge. Structural tools: threads, show, dependents-of, validate. Prefer a named "
   "tool — you fill typed params, you can't emit a broken query.\n\n"
   "For a multi-hop question no named tool covers, use `query`: a structured "
   "Datalog-shaped object {:find <rel> :rules [{:head {:rel R :args [terms]} "
   ":body [{:rel r :args [terms] :neg <bool>}]}]}. A term is {\"var\":\"x\"} or a "
   "constant; base relations are triple(l,p,r) and claim(cid,l,p,r). Recursion and "
   "stratified negation are supported; the query is validated before it runs."))

;; --- per-request state: fold the current log fresh (sees others' writes) -----
(defn load-state []
  (let [log (fram.rt/log-path)
        claims (:claims (fold/fold (fram.rt/read-log log)))]
    {:claims claims :idx (k/build-index claims) :cat (tl/catalog claims)}))

;; --- catalog spec -> MCP tool descriptor -------------------------------------
(defn- input-schema [params]
  {:type "object"
   :properties (reduce (fn [m p] (assoc m (:name p) {:type (:type p) :description (str (:name p))})) {} params)
   :required (vec (keep (fn [p] (when (:required p) (:name p))) params))})

(defn- ->tool [spec]
  {:name (:name spec) :description (:desc spec) :inputSchema (input-schema (:params spec))})

;; --- writes -> through the coordinator (mirrors the CLI's route-write) -------
(defn- route-write [w]
  (let [port (fram.rt/coord-port)]
    (if (neg? (fram.rt/coord-version port))
      {:isError true :text "no coordinator on 127.0.0.1 — start it with bin/fram-up"}
      (loop [tries 5]
        (let [v (fram.rt/coord-version port)
              resp (if (= (:op w) "assert")
                     (fram.rt/coord-assert port (:l w) (:p w) (:r w) v)
                     (fram.rt/coord-retract port (:l w) (:p w) (:r w) v))]
          (cond
            (and (= resp "conflict") (pos? tries)) (recur (dec tries))
            (str/starts-with? (str resp) "ok:") {:text (str "committed: " (:l w) " " (:p w) " = " (:r w) " [" (:op w) "]")}
            :else {:isError true :text (str "rejected by coordinator: " resp)}))))))

;; --- dispatch one tools/call -------------------------------------------------
(defn handle-call [name args]
  (let [{:keys [claims idx cat]} (load-state)
        res (tl/call claims idx cat name (or args {}))]
    (cond
      (:error res) {:isError true :text (str/join "\n" (:error res))}
      (:write res) (route-write (:write res))
      (contains? res :ok) {:text (json/generate-string (:ok res))}
      :else {:text (json/generate-string (:rows res))})))

;; wall-clock budget on the AI-facing path: validation makes a query STRUCTURALLY
;; safe, but evaluation is naive, so a deeply recursive query can be slow. Bound it
;; here. 10s is generous for the corpus sizes Fram targets; the CLI path runs
;; unbounded (a human can Ctrl-C).
;;
;; IMPORTANT: future-cancel only sets the worker's interrupt flag — it does NOT
;; stop CPU-bound work that never checks Thread.interrupted()/blocks. The naive
;; datalog fixpoint (fram.datalog/fixpoint) is exactly that: tight reduce/loop
;; forms with no interruption checks, so a cancelled runaway query would keep
;; pinning a core indefinitely, and "repeat a few times" pins every core. We can't
;; make the engine cooperatively interruptible from here (that per-iteration bound
;; belongs in fram.datalog, owned elsewhere — see FOLLOW-UP below), so we bound the
;; BLAST RADIUS two ways the budget can actually enforce:
;;   (1) run on a DAEMON thread we set the interrupt flag on and then abandon, so a
;;       runaway never keeps the JVM alive and is reaped on process exit; and
;;   (2) a hard CAP on how many query workers may be alive at once — an orphaned
;;       runaway holds its slot until it (eventually) finishes, so a client cannot
;;       pile up unbounded CPU-pegged threads by repeating an expensive query. Once
;;       the cap is hit, further queries are refused FAST (within budget) instead of
;;       spawning yet another never-dying core-pinning thread.
;; FOLLOW-UP (fram.datalog): add a cooperative deadline / max-iterations /
;; max-derived-facts bound inside fixpoint so a runaway actually STOPS at the
;; budget rather than running to completion on its abandoned daemon thread.
(def ^:private max-live-queries
  (max 1 (quot (.. Runtime getRuntime availableProcessors) 2)))
(def ^:private live-queries (atom 0))

(defn- with-timeout [ms thunk]
  ;; reserve a worker slot; refuse fast if too many (possibly orphaned) are alive.
  (if (> (swap! live-queries inc) max-live-queries)
    (do (swap! live-queries dec)
        {:isError true :text (str "query budget: too many concurrent/abandoned queries in flight (>" max-live-queries ") — a prior expensive query is still running; retry later or narrow it")})
    (let [result (promise)
          worker (doto (Thread.
                        (fn []
                          (try (deliver result (thunk))
                               (catch InterruptedException _ (deliver result ::timeout))
                               (catch Throwable t (deliver result {:isError true :text (str "query failed: " (.getMessage t))}))
                               (finally (swap! live-queries dec)))))
                   (.setDaemon true)         ; never blocks JVM shutdown
                   (.setName "fram-mcp-query")
                   (.start))
          r (deref result ms ::timeout)]
      (if (= r ::timeout)
        (do (.interrupt worker)              ; best-effort; abandoned if it ignores us
            {:isError true :text (str "query exceeded the " (quot ms 1000) "s time budget — narrow it (fewer rules / more constants)")})
        r))))

;; --- JSON-RPC plumbing -------------------------------------------------------
(defn- reply [id result] (println (json/generate-string {:jsonrpc "2.0" :id id :result result})) (flush))
(defn- reply-err [id code msg] (println (json/generate-string {:jsonrpc "2.0" :id id :error {:code code :message msg}})) (flush))

(defn handle [req]
  (let [has-id (contains? req :id)      ; a request WITHOUT an :id key is a notification
        id (:id req) method (:method req) params (:params req)]
    (cond
      ;; notification: never answer, whatever the method (and an explicit "id":null
      ;; below is still a request, so it DOES get answered — the contains? check
      ;; distinguishes "no id key" from "id is null").
      (not has-id) nil

      (= method "initialize")
      (reply id {:protocolVersion "2024-11-05"
                 :capabilities {:tools {}}
                 :serverInfo {:name "fram" :version "0.1"}
                 :instructions instructions})

      (= method "tools/list")
      (reply id {:tools (mapv ->tool (:cat (load-state)))})

      (= method "tools/call")
      (let [r (with-timeout 10000 (fn [] (handle-call (:name params) (:arguments params))))]
        (reply id {:content [{:type "text" :text (:text r)}] :isError (boolean (:isError r))}))

      :else (reply-err id -32601 (str "method not found: " method)))))

(log! "fram-mcp: ready on stdio (tools generated from the current log fold)")
(loop []
  (let [line (read-line)]
    (when (some? line)
      (when (seq (str/trim line))
        (let [req (try (json/parse-string line true) (catch Exception e (log! "parse error:" (.getMessage e)) nil))]
          (cond
            (nil? req) nil
            ;; a valid request is a JSON object (map). Anything else — a top-level
            ;; array (JSON-RPC batch, which cheshire yields as a seq, removed in MCP
            ;; 2025-06-18), or a scalar — is rejected loudly so a client doesn't hang
            ;; on a missing response.
            (not (map? req))
            (do (println (json/generate-string {:jsonrpc "2.0" :id nil :error {:code -32600 :message "Invalid Request: expected a single JSON object (batches not supported)"}})) (flush))
            :else
            (try (handle req)
                 (catch Exception e (log! "handler error:" (.getMessage e))
                   (when (contains? req :id) (reply-err (:id req) -32603 (str (.getMessage e)))))))))
      (recur))))
