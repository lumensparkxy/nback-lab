#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$NBACK_ROOT"
python3 scripts/check_harness.py
python3 -m unittest discover -s scripts/tests -v
./gradlew --no-daemon --console=plain :engine:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest "$@"
