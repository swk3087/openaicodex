#!/usr/bin/env bash
set -euo pipefail

# Keep only the known baseline Android scaffold Kotlin files.
# Useful after conflict-heavy merges where experimental/Hilt files leaked in.

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE="$ROOT/android/app/src/main/java/com/example/codexmobile"

if [[ ! -d "$BASE" ]]; then
  echo "Android source base not found: $BASE" >&2
  exit 1
fi

keep=(
  "MainActivity.kt"
  "data/CodexDatabase.kt"
  "data/PatchEntity.kt"
  "data/RoomSessionRepository.kt"
  "data/SessionDao.kt"
  "data/SessionEntity.kt"
  "domain/Session.kt"
  "domain/SessionRepository.kt"
  "domain/SessionState.kt"
  "runtime/CommandGate.kt"
  "runtime/RuntimeManager.kt"
  "runtime/TerminalSessionManager.kt"
  "ui/session/SessionSummary.kt"
)

declare -A keep_map
for rel in "${keep[@]}"; do
  keep_map["$BASE/$rel"]=1
done

while IFS= read -r -d '' file; do
  if [[ -z "${keep_map[$file]:-}" ]]; then
    rm -f "$file"
    echo "removed: ${file#$ROOT/}"
  fi
done < <(find "$BASE" -type f -name '*.kt' -print0)

echo "Scaffold normalization complete."
