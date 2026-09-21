# Toolchain and local setup

## Pinned baseline

| Component | Version |
| --- | --- |
| JDK used to run Gradle | 25 (CI: Temurin; Android Studio JBR 25 works locally) |
| Java/Kotlin bytecode target | 17 |
| Gradle wrapper | 9.7.1, distribution SHA-256 pinned |
| Android Gradle Plugin | 9.4.1, built-in Kotlin |
| Kotlin JVM/Compose plugins | 2.4.20 |
| Compose BOM | 2026.09.00 |
| Compile / target SDK | 37 / 37 |
| Build tools | 36.0.0 |
| Minimum Android API | 26, provisional development baseline |
| Python for harness checks | 3.11+; CI 3.13 |

The [version catalog](../gradle/libs.versions.toml) pins direct dependencies.
AGP 9 uses built-in Kotlin, so `org.jetbrains.kotlin.android` is not applied.
The engine uses the Kotlin JVM plugin. Kotlin/Java bytecode stays at 17 even when
the build JVM is 25. Review toolchain upgrades together as a compatibility change.
Dependency locking/checksum verification beyond the Gradle distribution can be
added as a dedicated issue; it is not claimed by this baseline.

Lint fails on correctness warnings/errors. Its three dependency-freshness detectors
(`NewerVersionAvailable`, `GradleDependency`, `AndroidGradlePluginVersion`) are
excluded because a newly published version must not break an unchanged build.
Dependabot proposes upgrades separately; this is not a claim that every pinned
dependency is the newest patch.

## Setup

Install Android Studio or command-line SDK tools, JDK 25 and Python 3.11+.
Through SDK Manager install Android SDK Platform 37 (`platforms;android-37.0`), Build Tools 36.0.0,
Platform Tools, Android Emulator and a system image for the host architecture.
Use an ARM64 image on Apple Silicon; CI uses an x86_64 API 36 emulator on Linux.
Accept SDK licenses through the SDK tools locally. Local setup scripts do not
accept licenses for you; the CI Android setup action accepts them on its disposable
runner as part of SDK provisioning.

Set `JAVA_HOME` and `ANDROID_HOME` if they are not in the common locations detected
by `scripts/env.sh`. Run `./scripts/doctor.sh` and `./scripts/verify.sh`.
Open this directory in Android Studio and sync. The checked-in
`gradle/gradle-daemon-jvm.properties` selects JDK 25 for the Gradle daemon and
provides generated download URLs for supported platforms. Parallel IDE sync is
enabled in `gradle.properties`; the application bytecode target remains 17.
Gradle downloads pinned dependencies on the first run; the application itself
does not require network access. Global Gradle caches remain outside the repo.

## Commands and outputs

| Command | Checks/output |
| --- | --- |
| `python3 scripts/check_harness.py` | Required files, local Markdown link targets, TOML syntax |
| `./scripts/verify.sh` | Structural checks, JVM tests, app unit tests, Android lint, debug/test APK builds |
| `./scripts/test-failure-gate.sh` | Actual failed JUnit assertion must produce a nonzero build; normal run restored afterward |
| `ANDROID_SERIAL=... ./scripts/emulator-test.sh` | Instrumented tests on an explicitly selected emulator |
| `ANDROID_SERIAL=... ./scripts/run-app.sh` | Build, install and launch development APK on that emulator |

Reports: `engine/build/reports/tests/test/`, `app/build/reports/lint-results-debug.html`,
`app/build/reports/androidTests/connected/`. APK: `app/build/outputs/apk/debug/app-debug.apk`.
Temporary logs/screenshots belong in ignored `artifacts/` or CI artifacts.

### Emulator deadlines and diagnostics

The two emulator commands require Python 3.11+ and an explicit `ANDROID_SERIAL`.
They check device state, emulator identity and ActivityManager responsiveness
before building or installing. No physical-device fallback is allowed.

| Environment setting | Default seconds | Applies to |
| --- | --- | --- |
| `NBACK_ADB_TIMEOUT_SECONDS` | 10 | Each of the three health probes |
| `NBACK_GRADLE_TIMEOUT_SECONDS` | 1200 | Build or connected test command |
| `NBACK_INSTALL_TIMEOUT_SECONDS` | 120 | Debug APK installation |
| `NBACK_LAUNCH_TIMEOUT_SECONDS` | 30 | Activity launch |

Overrides must be positive finite numbers; invalid settings fail before device
actions. Defaults leave headroom inside CI's existing 30-minute job deadline.
Extra arguments to `emulator-test.sh` still reach Gradle as separate arguments.
Build/install/launch output streams normally. Exit 124 means timeout; ordinary
command failures remain nonzero; SIGINT/SIGTERM return 130/143 respectively.

Each invocation writes a unique JSON summary under `artifacts/emulator-operations/`
with serial, Git revision (or unavailable), fixed operation names, deadlines,
elapsed times and outcomes. It excludes raw extra arguments, environment values,
logcat, UI dumps and app data. These summaries are included in CI Android evidence.
A failed summary write warns without hiding an operation failure.

On timeout or interruption, the wrapper terminates only the client process it
started, allows five seconds to exit, then kills that client if needed (up to two
seconds more to reap it). It never signals process groups, stops shared ADB/Gradle
services, or reboots, wipes or shuts down an emulator. A detached daemon or
device-side instrumentation may outlive client cancellation. Inspect the record
and target health before retrying; do not assume timeout cancelled all device work.
Local Git revision lookup is separately bounded to three seconds.

The engine includes gameplay tests and a deliberately skipped harness failure probe.
Its negative-control script enables a real assertion failure and verifies the
JUnit report, so dependency/network/build errors cannot masquerade as success.

## Sandbox troubleshooting

Gradle requires cache writes and local process communication; dependency downloads
require network access. ADB/emulators require local sockets and virtualization.
If an agent sandbox blocks these, request the narrow execution permission instead
of changing global configuration or disabling checks. Never report the blocked
operation as tested. Device tests may require a less restricted local session.

Sources: [AGP compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes),
[built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin),
[Compose BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping).
