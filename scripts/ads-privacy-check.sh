#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$NBACK_ROOT"
rm -f artifacts/android-audit/privacy.json
./gradlew --no-daemon --console=plain :app:processReleaseManifest :app:generateReleaseBuildConfig "$@"
python3 scripts/android_audit.py privacy
