---
name: android-release-readiness
description: Prepare and review an Android release build in this repository; use for release readiness, optimized build validation or release evidence, without implicit signing or publication.
---

# Android Release Readiness

Read AGENTS.md and docs/android-readiness.md. Run scripts/doctor.sh and
scripts/release-check.sh; inspect the actual unsigned APK/AAB, merged manifest,
R8 mapping and reported revision. Install the .qa releaseSmoke variant only on
an explicitly selected emulator and exercise the relevant flows. Do not use a
debug-only pass as release proof. Record versionCode, signing arrangement,
artifact hashes, mapping retention and any missing store/internal-track evidence.
Keep signing identity, credentials and publishing under the owner's authorization.
The release script cannot certify the final signed/uploaded artifact. Recheck it
when signing or source changes. Do not modify global tool configuration.
