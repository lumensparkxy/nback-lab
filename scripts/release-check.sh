#!/usr/bin/env bash
# Builds unsigned release artifacts; never signs with production keys or publishes.
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$NBACK_ROOT"
rm -f artifacts/android-audit/release.json
./gradlew --no-daemon --console=plain :app:lintRelease :app:assembleRelease :app:bundleRelease :app:assembleReleaseSmoke :app:assembleAdsSmoke "$@"
python3 scripts/android_audit.py release
