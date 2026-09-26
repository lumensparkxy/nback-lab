# F000 — Portable Android development harness

Agreement: Agreed, 2026-09-20, owner approved the project blueprint in the setup conversation.

## User outcome

An owner and a fresh coding agent can understand a scoped issue, build the app,
make a reviewable change, verify its behavior and hand off reliable evidence.

## Scope and exclusions

Portable instructions, specs/decision records, role workflow, Codex adapter,
Android shell, local/CI commands, issue/PR templates, and a failure-path check.
Gameplay implementation, publishing and release signing are excluded. GitHub
activation requires a selected owner/visibility and an actual remote repository.

## Behavior

Follow [development](../development.md) and [agent roles](../agents.md). Ordinary
checks return nonzero on failure. Device verification requires an explicitly
selected emulator. Reports distinguish local checks, hosted CI and branch rules.

## Acceptance criteria and verification

| ID | Criterion | Verification |
| --- | --- | --- |
| AC-01 | A fresh agent finds scope, architecture, commands and workflow from README/AGENTS | Independent fresh-context review and link checker |
| AC-02 | Android shell builds using the pinned wrapper and toolchain | `scripts/verify.sh`, debug APK |
| AC-03 | App launches and survives activity recreation | `LaunchSmokeTest` on emulator; inspect rendered screen |
| AC-04 | A real failed test cannot be mistaken for a passing build | `scripts/test-failure-gate.sh` verifies exit status and exact JUnit failure |
| AC-05 | Portable role boundaries and Codex role files agree | Independent review, TOML checks; fresh Codex role-loading check separately recorded |
| AC-06 | CI runs build/lint/JVM checks and emulator smoke tests | Hosted `quality` and `android-ui` job results; routine CI uses the critical selection in [development](../development.md#ci-coverage-and-speed), with full Android validation before releases |
| AC-07 | Main branch requires successful checks and owner-controlled merge | Inspect GitHub branch/ruleset settings after activation |
| AC-08 | Initial work is represented by linked GitHub issues | Create issues from reviewed local drafts after remote activation |

## Bootstrap questions and later resolutions

Publication licensing and an owned app identifier were open during bootstrap.
The original project is now [MIT licensed](../../LICENSE), with
[separate third-party terms](../third-party-assets.md). The owner selected
`com.maswadkar.nback` before distribution, recorded in
[ADR-002](../decisions/ADR-002-android-foundation.md).
The owner approved public `lumensparkxy/nback-lab`. Hosted gates cannot be marked
complete from local evidence.
