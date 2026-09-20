#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$NBACK_ROOT"
if [[ -z "${ANDROID_SERIAL:-}" ]]; then
    echo "Set ANDROID_SERIAL to one emulator from adb devices; physical devices are not selected automatically."
    exit 1
fi
if [[ "$(adb -s "$ANDROID_SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" != "1" ]]; then
    echo "Target is not an emulator. Refusing to install test APKs."
    exit 1
fi
./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest "$@"
