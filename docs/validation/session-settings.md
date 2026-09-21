# F002 session settings and guided practice validation

Scope: [issue #4](https://github.com/lumensparkxy/nback-lab/issues/4), agreed
[F002](../features/F002-practice-and-difficulty.md) and accepted
[ADR-003](../decisions/ADR-003-session-preferences.md). PR checks certify their
reported revision; this record is not a signed-release claim.

## Acceptance mapping

| Criteria | Evidence |
| --- | --- |
| AC-01 selection | Home option/selected-state/loading tests; dynamic instructions/examples/duration; engine validates 1..3 |
| AC-02 persistence | Real isolated DataStore missing/value/type/range/corruption and reopened-file tests; suspended load/save fake tests for failures, ordered writes, Retry and invalid-value repair |
| AC-03 immutable configuration | Settings completions during practice; practice-to-normal/restart retains n; changes outside Home rejected |
| AC-04 generation/scoring | 1,000 seeds at each n, six matches, first/last/adjacent match coverage; no-input/all-Match/perfect; original exact F001 fixture retained |
| AC-05 timing/lifecycle | Engine boundaries at each n; real 63/66/69-second sessions, retained rotation/recreation and interruption tests; manual process loss/lock |
| AC-06 practice explanations | Exact scripts at every n, all four outcomes, no-input/all-Match fixtures; Compose explanations and no accuracy card |
| AC-07 feedback barriers | Giant clock jump stops at first explanation; duplicates and stale step/previous-run tokens rejected; held Match release across feedback |
| AC-08 navigation | Practice rotation, paused feedback, background/Back interruption, restart and Skip; engine final-deadline completion precedence |
| AC-09 separation | Practice/repeat/normal transitions and transient results; only selected_n is written to preferences |
| AC-10 presentation | Loading/Retry/radio semantics and 48 dp tests; large-font stable grid; manual portrait/landscape/200% and TalkBack QA |
| AC-11 scope | Engine stays Android-free; one focused preferences dependency, unchanged permissions/backup exclusions, no inactive mode controls |

## Verification record

- Environment doctor: passed with JBR 25.0.3, Python 3.13.3 and the pinned SDK.
- Standard verification: passed structural checks, three harness regressions,
  seventeen engine tests, lint and debug/test APK builds. The intentional failure
  probe is skipped in ordinary runs and excluded from the gameplay count.
- Android instrumentation: 20/20 passed, no failures or skips, on the explicitly
  selected Pixel9a ARM64 emulator, Android 16 / API 36. This includes real
  63/66/69-second sessions and the retained F001 regressions.
- Rendered/manual QA: portrait and landscape, normal and 200% font, practice
  comparisons/completion, process restart, screen-lock interruption, and actual
  write-failure/Retry recovery passed. A fresh process retained the saved level
  and returned Home without restoring practice. Temporary filesystem permissions,
  font, rotation and accessibility settings were restored after testing.
- TalkBack: service binding and touch exploration verified; difficulty selection,
  Retry, practice Next and completion actions exercised with visible focus.
  This checks navigation and semantics, not an equivalent nonvisual game or a
  human assessment of spoken-output quality.
- Independent source review: no blocking correctness or agreed-scope findings.
  The lead checked that the reviewed application/engine source hashes remained
  unchanged through final validation. Hosted CI evidence belongs to the PR head.

## Implementation and learning

The engine owns immutable session configuration, exact normal rules and scripted
practice. Feedback carries a monotonically increasing token that is never reset
within the engine lifetime; stale Next callbacks cannot cross steps or restarted
runs. Feedback is a clock barrier, so reading time cannot consume later examples.

The Android settings adapter owns the sole application-scoped DataStore.
The ViewModel loads once before allowing selection/start and serializes saves;
request versions prevent old completions changing the latest UI status. Active
session configuration is independent of preference completion. Only selected_n
is persisted; no session, response, result or practice record is written.

Initial lint identified English strings requiring quantity resources after adding
1-back. These were corrected with singular/plural resources without suppressions.
No implementation fixes were required by independent review or manual runtime QA.
At 200% font in landscape, secondary practice controls may require scrolling;
the timed grid, progress and Match remain usable. Feedback itself is scrollable.

Local ignored evidence is in `artifacts/f002-qa/`: verification logs, screenshots,
UI trees, process/lock checks and a source fingerprint manifest. Large generated
artifacts are not tracked; hosted CI retains its own test/build reports.

Remaining limitations: fixed visual position mode and fixed timing; no nonvisual
equivalent, saved history, adaptive progression, additional modes or release work.
