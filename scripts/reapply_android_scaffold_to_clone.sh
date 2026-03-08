#!/usr/bin/env bash
set -euo pipefail

# Re-apply the Android scaffold/conflict-tooling files into another clone folder.
# Usage:
#   ./scripts/reapply_android_scaffold_to_clone.sh /path/to/other/openaicodex

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /path/to/other/openaicodex" >&2
  exit 1
fi

TARGET="$1"
SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [[ ! -d "$TARGET/.git" ]]; then
  echo "Target must be a git clone with .git directory: $TARGET" >&2
  exit 1
fi

files=(
  ".gitattributes"
  ".github/workflows/android-apk.yml"
  "README.md"
  "android/README.md"
  "android/build.gradle.kts"
  "android/gradle.properties"
  "android/settings.gradle.kts"
  "android/app/build.gradle.kts"
  "android/app/proguard-rules.pro"
  "android/app/src/main/AndroidManifest.xml"
  "android/app/src/main/res/values/themes.xml"
  "android/app/src/main/java/com/example/codexmobile/MainActivity.kt"
  "android/app/src/main/java/com/example/codexmobile/data/CodexDatabase.kt"
  "android/app/src/main/java/com/example/codexmobile/data/PatchEntity.kt"
  "android/app/src/main/java/com/example/codexmobile/data/RoomSessionRepository.kt"
  "android/app/src/main/java/com/example/codexmobile/data/SessionDao.kt"
  "android/app/src/main/java/com/example/codexmobile/data/SessionEntity.kt"
  "android/app/src/main/java/com/example/codexmobile/domain/Session.kt"
  "android/app/src/main/java/com/example/codexmobile/domain/SessionRepository.kt"
  "android/app/src/main/java/com/example/codexmobile/domain/SessionState.kt"
  "android/app/src/main/java/com/example/codexmobile/runtime/CommandGate.kt"
  "android/app/src/main/java/com/example/codexmobile/runtime/RuntimeManager.kt"
  "android/app/src/main/java/com/example/codexmobile/runtime/TerminalSessionManager.kt"
  "android/app/src/main/java/com/example/codexmobile/ui/session/SessionSummary.kt"
  "docs/git-conflict-resolution.md"
  "scripts/resolve_android_conflicts.sh"
)

for rel in "${files[@]}"; do
  src="$SOURCE_ROOT/$rel"
  dst="$TARGET/$rel"

  if [[ ! -f "$src" ]]; then
    echo "Missing source file: $src" >&2
    exit 2
  fi

  mkdir -p "$(dirname "$dst")"
  cp "$src" "$dst"
  echo "updated: $rel"
done

echo

echo "Done. Next steps in target clone:"
echo "  cd '$TARGET'"
echo "  git status --short"
echo "  git add ."
echo "  git commit -m 'Reapply Android scaffold and merge tooling'"
