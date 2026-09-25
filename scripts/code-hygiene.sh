#!/usr/bin/env bash
# Evidence only. No source, resource or dependency deletion.
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$NBACK_ROOT"
rm -f artifacts/android-audit/hygiene.json
./scripts/release-check.sh "$@"
mkdir -p artifacts/android-audit
./gradlew --no-daemon --console=plain :app:dependencies --configuration releaseRuntimeClasspath > artifacts/android-audit/dependencies.txt
python3 scripts/android_audit.py hygiene
