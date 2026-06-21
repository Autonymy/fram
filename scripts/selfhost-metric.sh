#!/usr/bin/env bash
# fram self-host scorecard — what fraction of the engine is Beagle-authored vs the
# irreducible hand-Clojure host seam. The honest cut (fram-2's reconcile):
#   - A .clj with a same-dir .bclj sibling is GENERATED (Beagle emit, e.g.
#     resolve.clj <- resolve.bclj) -> counts as Beagle, NOT hand-Clojure.
#   - Hand-Clojure = .clj with NO .bclj sibling (rt/json/daemon/shim/CLI/chartroom).
#   - Excludes tests/, experiments/, out/ (generated).
set -euo pipefail
cd "$(dirname "$0")/.."

FIND_CLJ=(find . -name '*.clj'
  -not -path '*/out/*' -not -path '*/node_modules/*' -not -path '*/.git/*'
  -not -path '*/.claude/*' -not -path '*/tests/*' -not -path '*/experiments/*')

hand=0; gen=0
while IFS= read -r clj; do
  n=$(wc -l < "$clj")
  if [ -f "${clj%.clj}.bclj" ]; then gen=$((gen+n)); else hand=$((hand+n)); fi
done < <("${FIND_CLJ[@]}")

bclj=$(find . -name '*.bclj' -not -path '*/out/*' -not -path '*/node_modules/*' \
  -not -path '*/.git/*' -not -path '*/.claude/*' -not -path '*/tests/*' \
  -not -path '*/experiments/*' -print0 2>/dev/null | xargs -0 wc -l 2>/dev/null | tail -1 | awk '{print $1}')
externs=$(grep -rhE 'declare-extern' --include='*.bclj' . 2>/dev/null | grep -v node_modules | wc -l | tr -d ' ')
distinct=$(grep -rhoE 'fram\.rt/[a-z-]+' --include='*.bclj' . 2>/dev/null | grep -v node_modules | sort -u | wc -l | tr -d ' ')

echo "=== fram self-host scorecard ==="
echo "hand-Clojure (non-Beagle):        ${hand} lines"
echo "Beagle-authored (.bclj):          ${bclj} lines  (+ ${gen} generated .clj it emits)"
awk -v b="$bclj" -v c="$hand" 'BEGIN{printf "  => %.0f%% Beagle-authored\n", 100*b/(b+c)}'
echo "host seam: ${distinct} distinct fns / ${externs} declare-extern sites"
echo "--- biggest HAND-Clojure files (port targets) ---"
while IFS= read -r clj; do
  [ -f "${clj%.clj}.bclj" ] && continue
  printf '%6s  %s\n' "$(wc -l < "$clj")" "$clj"
done < <("${FIND_CLJ[@]}") | sort -rn | head -6
