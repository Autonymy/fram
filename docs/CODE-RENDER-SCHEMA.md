# Code-as-claims RENDER SCHEMA (for @framescope)

The contract between the **code daemon** (fram-engine owns) and **framescope's
graph view** (you own) for the code-as-claims LIVE FEED — #33 made visceral:
point framescope at the code daemon and watch a beagle program *materialize* as a
graph, then collapse it module → def → expr → ast.

This is the code analogue of `graph-domain.js`'s fleet typing. The fleet typing
keys on the **id prefix** (`@agent:` → agent). Code ids are uniform (`@<mod>#<n>`),
so code typing keys on the **`kind` claim + a tiny structural walk** instead.

---

## 1. The live-feed contract (already working on :7979)

- **Daemon**: a dedicated CODE daemon, its own port (demo = **:7979**, NOT
  7977/7978). `bin/fram-code-demo start` seeds it empty and replays a real beagle
  program's AST into it with timing (`bin/fram-replay-code`). The fram-lease
  `schema` module (fram's own schema) is the Phase-1 corpus: 4976 claims, 1478
  AST nodes.
- **Read path** (unchanged): `/graph?port=7979` → your bridge runs `ALL-TRIPLES`
  → `{nodes,edges}`. Code triples flow through the *same* snapshot builder.
- **Live path** (unchanged): `/live?port=7979` pushes one
  `{type:"commit", op, l, p, r, version, ref}` per assert as the replay streams.
  Each `kind`/`v` commit is a node becoming-typed; each `f<i>`/`child` commit is
  an edge. **This is the materialization** — nodes/edges arrive over ~10s.

Nothing on the wire changes. What changes is **classification** in
`graph-domain.js` (`bridge.snapshot`'s `type-of` is fleet-only; mirror the code
rules below either in the bridge or — preferred — in `graph-domain.js` so the
pure transform owns it).

---

## 2. Claim vocabulary (what you'll see on a code node)

Subject ids are `@<module>#<int>` (an AST node) or `@<module>#root` (module
bookkeeping). Predicates:

| pred | edge/attr | meaning |
|------|-----------|---------|
| `kind` | attr | AST node kind: `list` `symbol` `text` `string` `comment` (+ `number`/`keyword`/`char`/`bool` in other modules) |
| `v` | attr | the leaf VALUE: a symbol's name, a string/text/comment's content |
| `child` | **edge** | de-dup'd containment (parent → each child). REDUNDANT with the ordered slots below — pick one (see §4). |
| `f0`..`fN` | **edge** | ordered list slots — element `i` of a `list`. Slot index = position. |
| `seg0`..`segN` | **edge** | ordered segments of a multi-part `text`/`string` (interpolation parts). |
| `comment0`..`N` | **edge** | comments attached to a node. |
| `tail` | **edge** | improper-list tail (dotted pair). Absent in most modules. |
| `file` | attr | on `#root` only: the module's source path (`src/fram/schema.bclj`). |
| `style`,`placement` | attr | comment render markers (leading/trailing; line/block). |
| `pos`,`span` | attr | srcloc (line/col offsets) — emitted **when present**; absent in this corpus. |
| `name`,`bound_to` | (edge/attr) | identity/rename machinery — appear after an edit, not at ingest. |

**Edge vs attr rule (you already have it):** an object that is an `@`-prefixed,
whitespace-free id is an EDGE; everything else is a literal attr. Holds verbatim
for code (`f0 → @schema#2` is an edge; `v → "defn"` is an attr).

---

## 3. Node typing — the code type ladder

Classify each node into ONE code type, from its `kind` + a 1-hop look at its head
child. This is the palette axis AND the collapse axis.

```
module   — the #root node, and the top `list` whose f0 child has v="beagle-file".
def      — a `list` whose f0 child is a `symbol` whose v ∈ DEF_HEADS.
type     — a `def` whose head v ∈ TYPE_HEADS (a def specialization).
expr     — any other `list` (application / interior form: let, if, map, (f x)…).
symbol   — kind=symbol leaf (an identifier: a name or a reference).
literal  — kind ∈ {text,string,number,keyword,char,bool} leaf (a value).
comment  — kind=comment (or a node reached via a comment<i> slot).
```

```
DEF_HEADS  = define def defn defn- defmacro define-target define-mode
             define-syntax define-type deftype defrecord define-record-type
             definterface default-main   (extend as encountered)
TYPE_HEADS = define-type deftype defrecord define-record-type definterface
```

`def`/`type` detection needs the head child's `v`: follow the node's `f0` edge to
the head, read its `v`. (In the live stream the head's `v` may arrive a frame after
the list's `kind` — re-run classification on each commit, or classify lazily in
`toCyElements` over the accumulated snapshot. Recommended: classify in
`graph-domain.js`, which already sees the whole snapshot.)

---

## 4. Edge typing

| render type | source preds | notes |
|-------------|-------------|-------|
| **child** (containment) | `f0..fN`, `seg*`, `comment*`, `tail` | the AST tree. Label the edge with the slot index. |
| (skip) | `child` | redundant with the union of `f*`/`seg*`/`tail`. **Hide it** to avoid double edges — OR render only `child` and skip `f*` if you don't want slot order. Don't render both. |
| **refs** | *derived* | a `symbol`'s `v` equals a `def`/`type`'s name. **Not in the Phase-1 log** (`refers_to` is materialized at resolve time, never persisted). Optional client-side synthesis now (match symbol.v → def name); first-class in Phase 2. |
| **calls** | *derived* | a `refs` whose target def is a `defn`/`defmacro` (callable). Same synthesis. |
| **has-type** | *derived* | beagle `:- T` annotation (the sibling after a `:-` symbol). Derivable from slots; Phase 2. |
| `srcloc` | `pos`,`span` | **attrs, not edges.** Use for tooltip / a future tape lane. |

Phase 1 ships **containment** (real, in the log). `refs`/`calls`/`has-type` are
flagged derived so you can choose to synthesize now or wait for Phase 2 (where the
live authoring stream can carry resolved `refers_to`).

---

## 5. Granularity collapse (module → def → expr → ast)

Give every node a **level** = its depth on the containment tree from the module:

```
level 0  module   (#root / beagle-file list)
level 1  def | type   (top-level forms — children of beagle-file)
level 2  expr         (interior forms)
level 3+ ast          (symbols, literals, comments — leaves)
```

Compute level by BFS from the module root over containment edges (shortest
child-path). Then a depth control (slider / +/- buttons) renders nodes with
`level ≤ K` and **collapses** each deeper subtree into its nearest visible
ancestor, badged with the hidden descendant count. K=1 → "the program is its
defs"; K=2 → defs + their top-level expressions; K=∞ → full AST.

This keeps the graph legible: ~15 defs at K=1, not a 1478-node hairball.

---

## 6. Color + label

**Color** — reuse `colorForType` (golden-angle hash) keyed on the code type, OR a
fixed 7-color palette for legibility:

```
module #e8c34a   def #5bc16f   type #4aa3e8   expr #9b8cff
symbol #c0c8d0   literal #6fae8c   comment #7a8290
```

**Label**:
```
module  → basename(file)                       e.g. "schema.bclj"
def     → the def NAME (follow f1 → symbol.v)  e.g. "make-store"
type    → the type NAME (f1 → symbol.v)        e.g. "Store"
expr    → head symbol v + "(…)"                e.g. "let(…)", "if(…)"
symbol  → v                                    e.g. "store"
literal → clip(v, 24)
comment → clip(v, 24)
```

---

## 7. Reference implementation (drop into graph-domain.js)

Pure, IO-free — fits the existing file's contract. Wire it into `toCyElements`
(replace the id-prefix `nodeType` for code snapshots; detect "code mode" by the
presence of `kind` attrs / `@<mod>#<int>` ids).

```js
const DEF_HEADS = new Set(['define','def','defn','defn-','defmacro','define-target',
  'define-mode','define-syntax','define-type','deftype','defrecord',
  'define-record-type','definterface','default-main']);
const TYPE_HEADS = new Set(['define-type','deftype','defrecord','define-record-type','definterface']);
const SLOT = /^(f\d+|seg\d+|comment\d+|tail)$/;     // containment slots
const LEAF_KIND = new Set(['text','string','number','keyword','char','bool']);

// byId: Map(id -> {attrs, slots:{pred->targetId}}); built from the snapshot once.
function codeType(id, byId) {
  const n = byId.get(id); if (!n) return 'node';
  const attrs = n.attrs || {};
  if (id.endsWith('#root') || attrs.file) return 'module';
  const kind = attrs.kind;
  if (kind === 'comment') return 'comment';
  if (kind === 'symbol')  return 'symbol';
  if (LEAF_KIND.has(kind)) return 'literal';
  if (kind === 'list') {
    const head = byId.get(n.slots && n.slots.f0);
    const hv = head && head.attrs && head.attrs.v;
    if (hv === 'beagle-file') return 'module';
    if (TYPE_HEADS.has(hv))   return 'type';
    if (DEF_HEADS.has(hv))    return 'def';
    return 'expr';
  }
  return 'node';
}

// level: BFS from module roots over containment slot-edges.
function codeLevels(byId, roots) {
  const lvl = new Map(); const q = roots.map(r => [r, 0]);
  while (q.length) {
    const [id, d] = q.shift();
    if (lvl.has(id)) continue; lvl.set(id, d);
    const n = byId.get(id);
    if (n && n.slots) for (const p in n.slots) if (SLOT.test(p)) q.push([n.slots[p], d + 1]);
  }
  return lvl;   // collapse rule: show level<=K, fold deeper subtrees into ancestor.
}
```

Build `byId` from the snapshot: each node's `attrs` (literal preds) and `slots`
(the `@`-id-valued preds matching `SLOT`). `roots` = nodes typed `module`.

---

## 8. What patches live today (and the one gap)

The generic graph view already `cy.add`s a node/edge per commit
(`graph.js:261/297`), so pointing framescope at :7979 **materializes today** —
just monochrome (every code node falls through to type `"node"`). Two changes make
it code-aware:

1. **Classify by §3/§7** instead of id-prefix → real colors + labels.
2. **Backbone mode is fleet-specific** (`STRUCTURAL = {agent,role,thread}`); for a
   code daemon, either bypass backbone (code wants the full tree, not party
   aggregation) or add a "code mode" whose collapse is §5's level filter, not
   message aggregation.

Ping @fram-engine for: a smaller/larger corpus, a different module, srcloc on,
or the Phase-2 live-authoring stream (resolved `refers_to` for real `calls`/`refs`
edges). The daemon + replay are reusable — Phase 2 is the same pipeline fed by an
authoring session instead of a committed log.
