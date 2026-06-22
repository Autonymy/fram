#!/usr/bin/env bash
# Real-disk write sweep (btrfs on SSD, /tmp). The inode/fsync behavior is the point here:
# per-file pays a real inode-create per claim; per-log pays one O_APPEND per writer.
# Runs both fsync-off (matches the live log, flush-only) AND fsync-on (durable each write).
set -euo pipefail
cd /home/tom/code/fram-lease
export BAKEOFF_PER_WRITER="${BAKEOFF_PER_WRITER:-4000}"
OUT=experiments/store-bakeoff/data/write-disk.txt
DISK=/tmp/bakeoff-disk
mkdir -p "$DISK"
: > "$OUT"

echo "############## REAL DISK (/tmp btrfs/SSD) ##############" | tee -a "$OUT"
echo "" | tee -a "$OUT"
echo "===== fsync=0 (flush only, matches live log) =====" | tee -a "$OUT"
for cand in per-log per-file; do
  echo "" | tee -a "$OUT"
  BAKEOFF_FSYNC=0 bb experiments/store-bakeoff/write_bench.clj "$cand" "$DISK/w" 3 2>/dev/null | tee -a "$OUT"
done
echo "" | tee -a "$OUT"
echo "===== fsync=1 (durable each write/batch) =====" | tee -a "$OUT"
for cand in per-log per-file; do
  echo "" | tee -a "$OUT"
  BAKEOFF_FSYNC=1 bb experiments/store-bakeoff/write_bench.clj "$cand" "$DISK/w" 3 2>/dev/null | tee -a "$OUT"
done
echo "" | tee -a "$OUT"
echo "===== baseline (in-process coordinator), fsync=0 then fsync=1 =====" | tee -a "$OUT"
BAKEOFF_FSYNC=0 bb experiments/store-bakeoff/baseline_bench.clj "$DISK/base" 3 2>/dev/null | tee -a "$OUT"
echo "" | tee -a "$OUT"
BAKEOFF_FSYNC=1 bb experiments/store-bakeoff/baseline_bench.clj "$DISK/base" 3 2>/dev/null | tee -a "$OUT"
echo "DISK-SWEEP-COMPLETE" | tee -a "$OUT"
