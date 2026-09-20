# Bootstrap validation

Date: 2026-09-20

Scope: initial bootstrap working tree. Generated reports/screenshots are stored
in ignored `artifacts/`, build outputs or linked CI artifacts. No gameplay
correctness or release readiness is claimed.

| Check | Result |
| --- | --- |
| Environment discovery | Passed: JBR 25.0.3, Python 3.13.3, SDK 37.0/build tools 36.0.0 |
| Structural/TOML checks | Passed: `python3 scripts/check_harness.py`; shell scripts also passed `bash -n` |
| Android build and lint | Passed: `scripts/verify.sh`; debug application and instrumentation APKs built |
| Failed-test negative control | Passed: `scripts/test-failure-gate.sh`; actual JUnit failure and nonzero exit, normal test run restored |
| Probe regression tests | Passed: three Python tests for fresh, stale and unrelated failure reports |
| Emulator launch/recreation | Passed: both `LaunchSmokeTest` tests on Pixel9a ARM64, API 36 |
| Independent review | Initial stale-evidence finding fixed and re-reviewed; final executable-change review reported no findings |
| Visual inspection | Settled launch screen inspected; text visible without clipping, crash buffer empty |
| Hosted CI | Initial run correctly failed when `sdkmanager` was absent from PATH; correction adds explicit SDK setup, with results tracked in issue #1 |
| GitHub rules | Configured and read back: PRs, strict Actions `gate` check (app 15368), administrator enforcement, resolved conversations, no force push/deletion, automatic merge disabled |
| Codex project configuration | Loaded by Codex CLI 0.145.0 configuration reader with strict config; project layer active and two-subagent limit effective |
| Custom-role execution | Not verified: automatic approval review blocked fresh model-session execution; separate owner authorization requested |

## Evidence and limits

- [Repository](https://github.com/lumensparkxy/nback-lab)
- [Harness tracking issue](https://github.com/lumensparkxy/nback-lab/issues/1)
- [Next specification issue](https://github.com/lumensparkxy/nback-lab/issues/2)
- Engine JUnit results intentionally contain one skipped probe in ordinary runs;
  this is not gameplay test coverage. The negative-control script enables it,
  saves `artifacts/expected-failure.xml`, and checks the expected assertion.
- Emulator results are under `app/build/outputs/androidTest-results/connected/`;
  local screenshot is `artifacts/bootstrap-screen.png`.
- During local validation, duplicated generated `* 2.class` files caused a D8
  failure. Cleaning generated Gradle outputs and rebuilding resolved it. No source
  duplication was found; the cause of the extra generated files was not established.
- The actual first feature/PR cycle remains issue #3 after session rules are agreed.
  Runtime role loading, hosted CI and branch protection are distinct checks.

Initial published revision: `8476a5e26643167b051407c5436dcfa2911acc90`.
The first [hosted run](https://github.com/lumensparkxy/nback-lab/actions/runs/35533173730)
demonstrated failure propagation from SDK setup through the aggregate gate.
Subsequent CI corrections use a PR under the newly active branch protection.
