# F003 implementation evidence

Issue: [#5](https://github.com/lumensparkxy/nback-lab/issues/5).
Contract: [F003](../features/F003-results-and-history.md) and
[ADR-004](../decisions/ADR-004-session-history.md), approved 2026-09-21.

## Automated coverage

| Criteria | Evidence |
| --- | --- |
| AC-01 | `HistoryScoreContractTest` checks all four score fixtures at all three levels, preserving the original n=2 worked sequence; UI checks all counts and baseline copy. |
| AC-02–03 | `HistoryCoordinatorTest` exercises refresh/Match/Back/pause/Home at the final deadline at every level, rejected starts, restart/play again IDs, practice/partial exclusions, delayed saves surviving ViewModel clearing, failures, Retry, duplicate capture and lost acknowledgement. |
| AC-04 | Real Room close/reopen, committed WAL and fresh coordinator/navigation state; manual process restart below. |
| AC-05 | Empty/filtered/10,000-row lazy UI, detail/Back/scroll, rotation/background retention, read failure without stale rows, timestamp ties, device-clock ordering, locale/12–24 hour/DST offsets. |
| AC-06–07 | Global filtered clear and cancel, selected level retention, delayed save→clear→new save ordering, failed/lost-ack handles, failed clear, cancellation, suspended post-clear read, actual SQLite trigger-induced transaction rollback. |
| AC-08 | Exported v1 schema, duplicate-ID conflict, raw SQLite type/range validation, corrupt/truncated/empty/version/identity/column fixtures preserved through load/save/clear/retry, current-schema reopening, committed WAL, unchanged backup exclusions and permissions. |
| AC-09 | Compose semantics/control checks plus rendered checks below. |
| AC-10 | Existing engine, settings, lifecycle and touch tests remain. Only the old exact result labels now include the F003 denominators. |

`doctor.sh`, `verify.sh` and `test-failure-gate.sh` pass locally: 3 harness tests,
18 engine tests, Android lint and debug/test APK builds. The deliberate failure
probe is normally skipped and independently proven to produce a failing build.

First complete emulator run: 35/36 passed; the sole failure was the old exact
“Hits: 4” assertion. It now expects “Hits: 4 of 6”, with the corresponding false
alarm denominator. This is an agreed display change, not a relaxed assertion.

## Review

Independent read-only review found raw SQLite coercion and stale displayed rows
after committed clear. Both were fixed with regressions; rereview reported no
remaining actionable findings. Review did not substitute for runtime checks.

## Environment and rendered verification

API 36 ARM64. Installing this build over F002 with selected 3-back retained that
selection and opened empty history. The original Android Studio emulator later
hit a system-server watchdog deadlock in package cleanup after instrumentation;
it was left intact. Further checks use a separate isolated test AVD.

A completed normal 1-back session showed the four counts, baseline explanation
and committed save status. Process restart returned Home with its selected level
and the same saved row. Portrait/detail/history and 200% text checks passed;
landscape revealed a clipped confirmation explanation, now made scrollable with
a dedicated lifecycle regression. Rendered recheck reached the end of the full
explanation with both buttons visible. Independent rereview accepted that fix.

With TalkBack bound, keyboard focus/activation reached Home→History, difficulty
filters, saved detail and the clear confirmation; system Back cancelled it.
Screenshots captured accessibility focus. No UiAutomator dump ran while TalkBack
was enabled. This verifies control navigation and labels/semantics, not a human
spoken-output or nonvisual-gameplay audit. Font, rotation and accessibility
settings were restored. Force-stopping a partial normal session returned to Home
without adding a record; the earlier committed row remained.

The added coroutine clear regression initially inferred a Boolean return, which
JUnit rejected. Explicit `runBlocking<Unit>` fixes the runner signature with all
assertions unchanged; the test-only diff also received independent review.

Final current-head emulator results, rendered rechecks and hosted CI are recorded
in the implementation PR linked from issue #5. This record alone does not imply
that pending checks or owner merge approval have completed.

## Boundaries

No release signing, distribution or implementation merge is part of this evidence.
Uncommitted snapshots remain process-local by contract. Transaction rollback and
coordinator cancellation tests cover interrupted operations; they do not claim a
universal hardware power-loss test or byte-for-byte invariance of WAL sidecars.
