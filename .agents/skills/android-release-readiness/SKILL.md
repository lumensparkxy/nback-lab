---
name: android-release-readiness
description: Check Android release readiness, prepare a new version and publishing materials, or publish and verify an authorized Google Play release in this repository. Readiness and preparation requests do not authorize publication.
---

# Android Release Readiness

Read `AGENTS.md`, `docs/development.md` and `docs/android-readiness.md` from the
repository root. Keep this skill name for existing callers. Select the mode from
the user's request and existing authorization; ask only for missing decisions
that affect the next action.

| Mode | Scope and stopping point |
| --- | --- |
| Check readiness | Inspect and validate the candidate; report evidence and blockers. Do not sign, upload or edit Play Console. |
| Prepare publication | Include readiness, version/release-note preparation and the publishing-material review. Sign or prepare a Console draft only when those actions and the signing identity are authorized. Stop before submission/rollout. |
| Publish and verify | Complete preparation, then submit or roll out the specified release within explicit owner authorization and verify the resulting state. |

## Shared release checks

1. Inspect branch, working tree, issue/PR status, release scope and relevant feature
   specs. Preserve unrelated work. Record candidate revision, configuration and
   baseline release; a local tag alone does not establish what users received.
2. Run `./scripts/doctor.sh`, `./scripts/verify.sh` and
   `./scripts/release-check.sh`. A release also requires the complete Android
   suite: `ANDROID_SERIAL=<selected-emulator> python3 scripts/ci_android_tests.py full`
   or the equivalent full CI workflow on the candidate revision. Routine critical
   CI is insufficient. Reuse existing evidence only after verifying its revision,
   configuration, coverage and artifact identity; report missing checks as such.
3. Inspect the actual unsigned APK/AAB, merged manifest, R8 mapping and report.
   Install optimized `.qa` releaseSmoke only on an explicitly selected emulator;
   exercise current Home, Settings, How to Play/practice, completed results,
   history/progress and relevant upgrade paths. Inspect rendered screens.
   Debug-only tests do not certify optimized builds. A separate `.qa` installation
   does not prove an upgrade of the Play-installed app preserves data.
4. When shipping ads, follow the ads/privacy workflow in `docs/android-readiness.md`,
   F007 and ADR-005, including optimized `.adsqa` test-ad evidence and actual release
   disclosures. Do not enable live ads just to publish an otherwise ads-disabled
   release. Never upload smoke variants or use live-ad clicks as QA.
5. Bind evidence to the final candidate. The unsigned release report does not
   certify the signed/uploaded artifact. Source, version, signing or configuration
   changes require rechecking that artifact and rerunning affected validation.

For preparation and publishing, follow [the Play procedure](references/play-release.md)
and review [publishing materials](references/publishing-materials.md).
Use the [release record](references/release-record.md) for checkpoints and handoff.
A readiness-only request can use the same record without inspecting the Console.

## Authorization and completion

Prepare a concrete summary before requesting any missing approval. Retain existing
explicit authorization for the same version, material, track and rollout scope;
do not ask again for routine steps it already covers. Skill invocation alone never
permits merging, signing-identity changes, public disclosure or publication.
Do not change global tool configuration or retain credentials in files or logs.

Report the actual stage reached, evidence, blockers and next action. Distinguish
local validation, uploaded draft, submission, review, publication and installed
verification. If live access is unavailable, finish local preparation and report
Console verification as blocked. Do not imply that monitoring continues after the
turn unless an authorized follow-up has actually been configured.
