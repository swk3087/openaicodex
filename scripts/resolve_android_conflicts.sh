#!/usr/bin/env bash
set -euo pipefail

# Resolve known Android scaffold conflict set by preferring current branch content.
# Use only while a merge/rebase conflict is in progress.

if [[ ! -d .git ]]; then
  echo "Run from repository root." >&2
  exit 1
fi

if [[ ! -f .git/MERGE_HEAD && ! -d .git/rebase-merge && ! -d .git/rebase-apply ]]; then
  echo "No merge/rebase conflict in progress." >&2
  exit 1
fi

files=(
  ".github/workflows/android-apk.yml"
  "README.md"
  "android/README.md"
  "android/app/build.gradle.kts"
  "android/app/src/main/AndroidManifest.xml"
  "android/app/src/main/java/com/example/codexmobile/MainActivity.kt"
  "android/app/src/main/java/com/example/codexmobile/data/CodexDatabase.kt"
  "android/app/src/main/java/com/example/codexmobile/data/RoomSessionRepository.kt"
  "android/app/src/main/java/com/example/codexmobile/data/SessionDao.kt"
  "android/app/src/main/java/com/example/codexmobile/data/SessionEntity.kt"
  "android/app/src/main/java/com/example/codexmobile/domain/Session.kt"
  "android/app/src/main/java/com/example/codexmobile/domain/SessionRepository.kt"
  "android/app/src/main/java/com/example/codexmobile/runtime/CommandGate.kt"
  "android/app/src/main/java/com/example/codexmobile/runtime/RuntimeManager.kt"
  "android/app/src/main/java/com/example/codexmobile/runtime/TerminalSessionManager.kt"
  "android/app/src/main/java/com/example/codexmobile/ui/session/SessionSummary.kt"
  "android/build.gradle.kts"
)

for f in "${files[@]}"; do
  # only resolve files that are actually unmerged
  if git ls-files -u -- "$f" | grep -q .; then
    git checkout --ours -- "$f"
    git add "$f"
    echo "resolved(ours): $f"
  fi
done

# fail if any conflict markers remain
if rg -n "^(<<<<<<<|=======|>>>>>>>)" . -g '!android/.gradle/**' -g '!android/build/**' >/dev/null 2>&1; then
  echo "Conflict markers still present. Resolve manually." >&2
  exit 2
fi

echo "Conflict set resolved. Review with: git status"
