# F001 visual session validation

Scope: issue #3, fixed visual 2-back session. Specification:
[F001](../features/F001-visual-session.md). Delivery/revision-specific CI is linked
from the implementation PR; this record does not claim release readiness.

## Implementation boundary

The pure Kotlin engine owns generated sequences, the injected monotonic time
origin, response attribution, closed-trial classification and final results.
The Android ViewModel keeps this state in memory through configuration recreation.
A main-thread Handler wakes it at phase boundaries. Compose renders current state;
there is no timer, match classification or scoring calculation in rendering code.
No dependency, network permission, persistence or signing change is included.

## Acceptance evidence

| Criterion | Evidence |
| --- | --- |
| AC-01 classification | `VisualSessionTest.workedExampleHasAllFourExpectedOutcomes`: exact F001 sequence produces 4 hits, 2 misses, 3 false alarms, 11 correct rejections and 75% |
| AC-02 input boundaries | Engine checks around highlight, warm-up/scored, trial and final deadlines; duplicate/late input; Compose held press/release across a boundary |
| AC-03 completion | No-input, all-Match and perfect engine sessions; completion identity stable after repeated events; emulator real 66-second no-input session |
| AC-04 lifecycle | Deadline/interruption engine tests; actual orientation change, recreation in highlight/blank phases, pause without stop, Back and Restart instrumentation; external background/lock/process-loss QA |
| AC-05 accessibility | 48 dp minimum Match target checked; grid bounds regression at 200% font; rendered portrait/landscape inspection, contrast calculation and TalkBack navigation |
| AC-06 generation | 1,000 seeded sequences reproduce exactly, use valid cells and contain exactly six matches; first/last/adjacent matches covered; sampling and exclusion algorithm reviewed |
| AC-07 instructions and feedback | Start is explicit; warm-up disabled; neutral acknowledgement; complete Compose navigation and progress checks |
| AC-08 elapsed time | Fake-clock multi-trial jumps, original nonzero time origin, repeated state reads and defensive backwards-clock handling; retained engine across recreation |
| AC-09 transient results | Two full worked-example sessions through Compose with reset; actual results survive recreation/background; Home and process-loss reset |

## Execution record

- Environment doctor: JBR 25.0.3, Python 3.13.3, SDK 37.0/build tools 36.0.0.
- `scripts/verify.sh`: passed build, lint, structural checks, three harness script
  regression tests and eleven engine tests. The intentional harness failure probe
  is skipped during ordinary runs; it is not counted as gameplay coverage.
- Emulator: Pixel9a, ARM64, API 36, explicitly selected as `emulator-5554`.
- `scripts/emulator-test.sh`: all ten instrumentation tests passed, including the
  real 66-second no-input session; no instrumentation tests were skipped.
- Manual QA passed for portrait and landscape at 100% and 200% font size,
  background interruption, confirmed screen sleep/lock and fresh-process startup.
  After process termination, a different process ID launched into Home.
- With TalkBack 16 enabled and bound, keyboard focus and activation reached
  instructions, Start, Match, results and Home. Match showed neutral acknowledgement;
  the completed session showed the expected counts. This verifies control/navigation
  behavior, not nonvisual gameplay or recorded speech output.
- The final emulator crash buffer was empty. Temporary font, rotation and
  accessibility settings were restored; only the emulator started for this QA was stopped.
- Color contrast ratios: main text/paper 11.05:1; secondary text/paper 6.64:1;
  white button text/dark button 12.07:1; disabled text/control 5.73:1;
  active/inactive cells 5.30:1; white outline/active cell 6.72:1.
- Local screenshots, UI trees, contrast calculations and regression failure
  evidence live in ignored `artifacts/session-qa/`; CI publishes its own reports.

## Review and harness learning

Independent read-only review found a changing response message could resize or
recenter the portrait grid at large font sizes. A new emulator regression failed
before the fix: the grid container bottom moved from 1848 to 1959 pixels after
Match. The response area now reserves the largest feedback layout for the current
width/font size; invisible measuring text has no accessibility semantics.

The first full emulator run also exposed an immediate-Back race: input could
arrive after Start but before Compose updated BackHandler's enabled state. Back
handling now consults current session state synchronously in the Activity.
The existing failing scenario remains a regression test. Independent rereview of
both fixes reported no remaining findings, with runtime validation left to the lead.

Instruction discovery worked from AGENTS, README, F001 and ADR-002. One writer
implemented the change, a read-only planner derived edge cases independently,
and a separate reviewer inspected the actual engine/UI/test diff. Their reports
were checked against test results rather than treated as automatic acceptance.
No governance or CI rules were weakened. The repository did not have a
`status:in-progress` label; implementation progress was recorded in the issue body
using the existing workflow, without adding a new status system.

No claims of nonvisual-equivalent gameplay, medical benefit, saved history or
release readiness. The feature remains fixed 2-back with fixed timing.
