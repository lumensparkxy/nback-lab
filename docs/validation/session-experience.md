# F008–F010 session experience validation

Tracking: [issue #35](https://github.com/lumensparkxy/nback-lab/issues/35).
Scope: dedicated Settings, configurable scored length, Results/Progress and How to
Play. This record describes the reviewed implementation; merge and release remain
separate owner decisions.

## Acceptance evidence

| Criteria | Evidence |
| --- | --- |
| F008 AC-01–04, AC-07 | `SessionLengthTest` covers all four lengths, all n/mode combinations, pace boundaries, exact target counts, response patterns, deadlines and four-example practice. `SessionExperienceTest` checks a ten-turn completion, immutable replay/practice configuration and reopened 30/50-turn results. |
| F008 AC-05–06 | `LengthStorageTest`, `LevelSettingsTest` and existing storage/coordinator suites cover independent preference recovery, ordered/retryable writes, v1/v2/v3 migration, v4 round-trips and raw malformed-row rejection without destructive recovery. |
| F008 AC-08–09 | `GuidedDesignTest` checks Settings at narrow portrait/landscape and normal/200% text. Route tests retain the sole current-result Home ad callback. Existing consent/quota tests remain in the full suite. |
| F009 AC-01–03 | `SessionExperienceTest`, `HistoryUiTest` and `GuidedLifecycleTest` cover Results sections, old/new detail, timeline length, Back restoration, rotation and retained group/table state. |
| F009 AC-04–05 | `ProgressDataTest` checks the 75%/90% example, exact configuration groups, ordering, exclusions and legacy compatibility. UI tests retain selection through Home/Settings and expose a removed group explicitly. |
| F009 AC-06–07 | Read/save failures, retry/clear and missing-data fixtures preserve truthful counts. Both history and progress UI tests retain all 10,000 records with lazy rows. |
| F009 AC-08–09 | Graph legends use named types and distinct shapes/styles; exact values remain available in a lazy table. Large-text screenshots and lifecycle checks cover progress/table navigation. New Settings/Help/Back/Play again paths do not invoke result-Home ads. |
| F010 AC-01–06 | `GuidedDesignTest` exercises all seven type sets at all three n values; `SessionExperienceTest` verifies selected configuration, annotated match/non-match examples and practice separation. Existing practice/lifecycle tests cover feedback, duplicate input and interruption. |

## Verification record

- Environment doctor passed. Standard verification passed structural/link checks,
  19 Python harness tests, 29 active engine tests, 14 app unit tests, Android lint
  and debug/test APK builds. The engine's intentional failure probe is the sole
  ordinary skipped test.
- The initial full Android run executed exactly 85 expected methods on the
  explicitly selected Pixel9a Android 16 emulator: 84 passed, one test attempted
  a filter action before background list preparation completed. The test now
  waits for the rendered list; its filter, Back/scroll and lazy-row assertions
  are retained. Fresh affected-class results are recorded with the PR evidence.
- A fresh-app manual check reproduced Results remaining on Loading after an
  unchanged history reload. The new empty/populated equal-content regression
  failed before the fix. Prepared snapshots now use identity equality, allowing
  Compose to publish the new source identity while retaining stale-data guards.
  This adds one Android method, bringing the full expected inventory to 86.
- After the final source fix, affected Android classes passed with exact method
  inventories: HistoryUiTest 6/6, SessionExperienceTest 8/8, GuidedDesignTest 4/4
  and GuidedLifecycleTest 3/3. Across the full run and these reruns, all 86 expected
  methods have a passing latest result; this is not a claim of one fresh 86-test
  full run. Standard verification passed again on the final application code.
- The deliberate failure gate rejected its expected assertion and restored a
  passing ordinary engine run. Fresh-app manual Results/Progress navigation
  confirmed the empty-history loading regression is fixed.
- TalkBack service binding and touch exploration were verified. Settings controls,
  selected Results tabs and How to Play text/examples were inspected, with actions
  activated under TalkBack. This checks focus/navigation and semantics, not human
  spoken-output quality or an equivalent nonvisual gameplay experience. Original
  accessibility settings were restored.
- A real 10-turn, one-second session completed in 12 seconds including warm-up,
  saved once, and appeared as a 70% progress point with hits 0/3 and false alarms
  0/7. The chart and exact-data card were inspected at 200% text in landscape.
  Original font and rotation settings were restored and read back.
- Independent review found and resolved selected-group retention and an outdated
  Home-title assertion. Follow-up reviews cleared those fixes, the large-text
  corner change and the asynchronous-list test synchronization.
  Independent follow-up also confirmed the refresh mechanism and identity fix.
- Rendered inspection found clipping in tall progress-table buttons with the
  default stadium shape. Fixed 16 dp corners preserve the complete text area.
  Fresh screenshots verify the affected presentation.

## Evidence handling and limitations

Generated build files with duplicate class names reappeared in the primary
checkout. They were preserved for diagnosis; validation used an isolated checkout
with a SHA-256 source manifest checked against the implementation checkout.
No application workaround or build-gate suppression was added.

Ignored `artifacts/session-experience/` evidence includes source manifests,
individual JUnit reports, screenshots and review/command summaries. Hosted CI
must independently pass for the submitted PR head. Local success does not stand
in for a hosted check, a production release or an assessment of cognitive benefit.

Application source was committed as `d9bf0b4`; subsequent changes only complete this
validation record. Automatic approval review initially blocked publication pending
explicit owner approval. The owner subsequently authorized pushing
`codex/session-experience` and opening its prepared PR. Hosted CI results belong to
that PR head; issue #35 remains open until accepted delivery. Merge and release
require separate authorization.
