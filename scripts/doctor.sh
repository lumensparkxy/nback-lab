#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$NBACK_ROOT"
failed=0
check() {
    if "$@"; then return 0; else failed=1; fi
}
check python3 -c 'import sys; assert sys.version_info >= (3, 11), "Python 3.11+ required"; print("Python:", sys.version.split()[0])'
check bash -c 'command -v java >/dev/null && java -version'
check bash -c 'java -version 2>&1 | head -1 | grep -Eq "\"25[.+\"]" || { echo "Use JDK 25 for the pinned build/CI baseline."; exit 1; }'
check test -f "${ANDROID_HOME:-/missing}/platforms/android-37.0/android.jar"
check test -d "${ANDROID_HOME:-/missing}/build-tools/36.0.0"
check test -x "${ANDROID_HOME:-/missing}/platform-tools/adb"
check test -x ./gradlew
check test -f gradle/wrapper/gradle-wrapper.jar
printf 'Android SDK: %s\n' "${ANDROID_HOME:-NOT FOUND}"
if [[ "$failed" -ne 0 ]]; then
    echo "Missing prerequisite. See docs/toolchain.md. No tools were installed."
    exit 1
fi
echo "Environment prerequisites found. This does not prove build or emulator success."
