#!/bin/bash
# ينشر نسخة جديدة من جيبك على GitHub Releases، والتطبيق على الموبايل بيلقطها لوحده.
# الاستخدام: زوّد versionCode و versionName في app/build.gradle.kts، اكتب الجديد في RELEASE_NOTES.md، وبعدين:
#   ./release.sh
set -euo pipefail
cd "$(dirname "$0")"
CODE=$(grep -E 'versionCode = ' app/build.gradle.kts | grep -oE '[0-9]+')
NAME=$(grep -E 'versionName = ' app/build.gradle.kts | grep -oE '"[^"]+"' | tr -d '"')
GRADLE="${GRADLE:-./gradlew}"
if gh release view "v$CODE" >/dev/null 2>&1; then
  echo "v$CODE منشورة قبل كده، زوّد versionCode الأول" >&2; exit 1
fi
"$GRADLE" assembleRelease testDebugUnitTest
mkdir -p build
cp app/build/outputs/apk/release/app-release.apk build/nabd.apk
git push -q origin HEAD
gh release create "v$CODE" build/nabd.apk --title "جيبك $NAME" --notes-file RELEASE_NOTES.md
echo "اتنشرت v$CODE ($NAME)"
