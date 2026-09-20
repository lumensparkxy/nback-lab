#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$NBACK_ROOT"
: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the intended emulator}"
if [[ "$(adb -s "$ANDROID_SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" != "1" ]]; then
    echo "Choose an emulator for development installation."
    exit 1
fi
./gradlew --no-daemon --console=plain :app:assembleDebug
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" shell am start -W -n com.example.nback/.MainActivity
