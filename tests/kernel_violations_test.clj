;; kernel_violations_test.clj — the dangling-person-ref integrity check
;; (the-model §2/§7). lead/driver/proposed_by must point at a node carrying a
;; `display_name` claim (the person/agent kind; `name` is a RESERVED engine/schema
;; predicate); a ref to a node WITHOUT a display_name surfaces
;; as 'references unknown person', greppable apart from the thread-ref
;; 'references missing entity'. Mirrors over BOTH the indexed (violations-i) and
;; flat (violations) paths.
;;   bb -cp out tests/kernel_violations_test.clj      (from the repo ROOT)
(require '[fram.kernel :as k])

(defn idx-of [claims] (k/build-index claims))
(defn has? [v sub] (some #(clojure.string/includes? % sub) v))

;; @p is a person (has a name). @w1 lead @p resolves cleanly.
(def ok-claims
  [(k/->Claim "@p" "display_name" "Tom")
   (k/->Claim "@w1" "title" "W1")
   (k/->Claim "@w1" "lead" "@p")])

;; @w2 driver @ghost — @ghost has NO name node => dangling person ref.
(def ghost-claims
  [(k/->Claim "@p" "display_name" "Tom")
   (k/->Claim "@w2" "title" "W2")
   (k/->Claim "@w2" "driver" "@ghost")])

;; @w3 proposed_by @p (ok) + proposed_by @ghost (dangling) — only @ghost flags.
(def proposed-claims
  [(k/->Claim "@p" "display_name" "Tom")
   (k/->Claim "@w3" "title" "W3")
   (k/->Claim "@w3" "proposed_by" "@p")
   (k/->Claim "@w3" "proposed_by" "@ghost")])

(def ix-ok (idx-of ok-claims))
(def ix-ghost (idx-of ghost-claims))
(def ix-prop (idx-of proposed-claims))

(def v-ok-i    (k/violations-i ix-ok "@w1"))
(def v-ghost-i (k/violations-i ix-ghost "@w2"))
(def v-prop-i  (k/violations-i ix-prop "@w3"))

;; flat path (over the Claim vec)
(def v-ok-f    (k/violations ok-claims "@w1"))
(def v-ghost-f (k/violations ghost-claims "@w2"))
(def v-prop-f  (k/violations proposed-claims "@w3"))

(def checks
  [;; indexed path
   ["(i) lead -> named person => no person violation" (not (has? v-ok-i "references unknown person"))]
   ["(i) driver -> ghost => 'driver references unknown person @ghost'"
    (has? v-ghost-i "driver references unknown person @ghost")]
   ["(i) proposed_by -> named is clean, ghost flags"
    (and (has? v-prop-i "proposed_by references unknown person @ghost")
         (not (has? v-prop-i "references unknown person @p")))]
   ;; flat path mirror
   ["(f) lead -> named person => no person violation" (not (has? v-ok-f "references unknown person"))]
   ["(f) driver -> ghost => 'driver references unknown person @ghost'"
    (has? v-ghost-f "driver references unknown person @ghost")]
   ["(f) proposed_by -> named is clean, ghost flags"
    (and (has? v-prop-f "proposed_by references unknown person @ghost")
         (not (has? v-prop-f "references unknown person @p")))]])

(let [fails (remove second checks)]
  (doseq [[nm ok] checks] (println (if ok "  [PASS] " "  [FAIL] ") nm))
  (if (empty? fails)
    (println "\nkernel_violations:" (count checks) "/" (count checks) "PASS")
    (do (println "\nkernel_violations:" (count fails) "FAILED") (System/exit 1))))
