#!/usr/bin/env bash
# =============================================================================
# Generate the seed lesson audio (LES-08) with offline TTS inside Docker.
# =============================================================================
# Reads the seeded sentences from init-db.sql (lesson ids 1111...1111111111NN,
# columns: lesson_id, sentence_order, text, ipa, translation_vi, start_ms, end_ms)
# and writes, for every lesson N:
#   media/seed/lesson-N/s<order>.mp3   one sentence (what sentence.audio_url points at)
#   media/seed/lesson-N/full.mp3       whole lesson: each sentence padded/trimmed to
#                                      exactly its [start_ms, end_ms) slot, so the
#                                      seeded timings stay valid (lesson.audio_url)
# Sentences longer than their slot are sped up (ffmpeg atempo, max 2x) to fit.
#
# Requirements on the host: docker, python3 (only to parse init-db.sql).
# The TTS (espeak-ng) + ffmpeg run in a throw-away alpine container.
# Output is committed to git (each file is a few tens of KB); re-run only when the
# seeded sentences change:  scripts/gen-seed-audio.sh
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/media/seed"
IMAGE="${SEED_AUDIO_IMAGE:-alpine:3.20}"
VOICE="${SEED_AUDIO_VOICE:-en-us}"
SPEED="${SEED_AUDIO_SPEED:-150}"   # espeak-ng words per minute

# Work dir inside the repo (not /tmp): the docker daemon must be able to bind-mount it.
WORK="$(mktemp -d "$ROOT/.seed-audio-work.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

# --- 1. extract sentences -> one line per sentence, sorted by lesson then order:
#        lesson_no|order|slot_ms|text      (slot_ms = end_ms - start_ms)
python3 -I - "$ROOT/init-db.sql" > "$WORK/sentences.txt" <<'PY'
import re, sys
pat = re.compile(
    r"^\('11111111-1111-1111-1111-1111111111(\d\d)',\s*(\d+),\s*'((?:''|[^'])*)',"
    r".*,\s*(\d+),\s*(\d+)\)[,;]?\s*$")
rows = []
for line in open(sys.argv[1], encoding="utf-8"):
    m = pat.match(line.strip())
    if m:
        lesson, order, text, start, end = m.groups()
        text = text.replace("''", "'").replace("|", " ")
        rows.append((int(lesson), int(order), int(end) - int(start), text))
if not rows:
    sys.exit("no seeded sentences found in init-db.sql")
for r in sorted(rows):
    print("|".join(map(str, r)))
print(f"found {len(rows)} sentences", file=sys.stderr)
PY

# --- 2. synthesize inside docker (busybox sh)
cat > "$WORK/synth.sh" <<'SH'
#!/bin/sh
set -eu
apk add --no-cache -q espeak-ng ffmpeg >/dev/null
TMP=/tmp/seed
mkdir -p "$TMP"
ENC="-ac 1 -ar 22050 -c:a libmp3lame -b:a 48k -map_metadata -1 -fflags +bitexact -flags:a +bitexact"
dur_ms() { ffprobe -v error -show_entries format=duration -of csv=p=0 "$1" | awk '{printf "%d", $1 * 1000}'; }

while IFS='|' read -r n order slot text; do
  dir="/out/lesson-$n"
  if [ ! -f "$TMP/list-$n.txt" ]; then
    rm -rf "$dir"; mkdir -p "$dir"; : > "$TMP/list-$n.txt"
  fi
  raw="$TMP/raw-$n-$order.wav"
  espeak-ng -v "$VOICE" -s "$SPEED" -w "$raw" "$text" </dev/null
  d=$(dur_ms "$raw")
  # keep >= 150 ms of silence before the next sentence; speed up (max 2x) if needed
  filter=$(awk -v d="$d" -v s="$slot" 'BEGIN { f = s - 150; if (d <= f) { print "anull" } else { t = d / f; if (t > 2) t = 2; printf "atempo=%.4f", t } }')
  [ "$filter" = "anull" ] || echo "  lesson-$n s$order: ${d}ms > slot ${slot}ms -> $filter"
  sent="$TMP/sent-$n-$order.wav"
  ffmpeg -v error -y -i "$raw" -af "$filter" -ac 1 -ar 22050 "$sent" </dev/null
  # shellcheck disable=SC2086
  ffmpeg -v error -y -i "$sent" $ENC "$dir/s$order.mp3" </dev/null
  padded="$TMP/pad-$n-$order.wav"
  secs=$(awk -v s="$slot" 'BEGIN { printf "%.3f", s / 1000 }')
  ffmpeg -v error -y -i "$sent" -af "apad,atrim=0:$secs" -ac 1 -ar 22050 "$padded" </dev/null
  echo "file '$padded'" >> "$TMP/list-$n.txt"
done < /work/sentences.txt

for list in "$TMP"/list-*.txt; do
  n=$(basename "$list" .txt); n=${n#list-}
  dir="/out/lesson-$n"
  # shellcheck disable=SC2086
  ffmpeg -v error -y -f concat -safe 0 -i "$list" $ENC "$dir/full.mp3" </dev/null
  echo "lesson-$n: $(ls "$dir" | wc -l) files, full.mp3 = $(dur_ms "$dir/full.mp3") ms"
done
chown -R "$HOST_UID:$HOST_GID" /out
SH

mkdir -p "$OUT"
docker run --rm \
  -e VOICE="$VOICE" -e SPEED="$SPEED" -e HOST_UID="$(id -u)" -e HOST_GID="$(id -g)" \
  -v "$WORK":/work:ro -v "$OUT":/out \
  "$IMAGE" sh /work/synth.sh

echo "Done -> $OUT"
du -sh "$OUT"
