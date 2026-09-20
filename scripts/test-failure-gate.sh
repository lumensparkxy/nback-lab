#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$NBACK_ROOT"
mkdir -p artifacts
# A pre-test build failure must not reuse evidence from an interrupted older run.
rm -f engine/build/test-results/test/TEST-com.example.nback.engine.HarnessFailureProbeTest.xml
# --rerun-tasks prevents a cached success from standing in for this experiment.
if ./gradlew --no-daemon --console=plain :engine:test -PharnessFailureProbe=true --rerun-tasks > artifacts/failure-probe.log 2>&1; then
    echo "ERROR: deliberately failing test was accepted."
    exit 1
fi
python3 - <<'PY'
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
report = Path('engine/build/test-results/test/TEST-com.example.nback.engine.HarnessFailureProbeTest.xml')
root = ET.parse(report).getroot()
failures = root.findall('.//failure')
assert any('HARNESS_EXPECTED_FAILURE' in f.get('message', '') for f in failures), 'Build failed for another reason; negative control NOT proven'
shutil.copyfile(report, 'artifacts/expected-failure.xml')
print('Expected JUnit assertion failure detected and Gradle returned nonzero.')
PY
# Restore ordinary reports and prove the deliberate failure has not poisoned normal runs.
./gradlew --no-daemon --console=plain :engine:test --rerun-tasks
