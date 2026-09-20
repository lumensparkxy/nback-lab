# F001 — Fixed-level visual n-back session

Agreement: Draft. Visual n-back and offline operation are agreed; detailed rules are not.

## User outcome

Complete a visual position-matching session and receive an understandable outcome.

## Scope and exclusions

First vertical slice: one fixed difficulty, a visual grid, response input, session
completion and basic result. No adaptive level, audio task, account or history UI.

## Behavior

At trial i, compare the current position with the position n trials earlier when
that earlier trial exists. The final spec must include a worked deterministic
sequence and expected classifications. Time and randomness must be controllable
in tests. Screen recomposition must not advance a trial or reset session state.

## Acceptance criteria and verification

Draft criteria to complete after owner decisions:

| ID | Criterion | Verification |
| --- | --- | --- |
| AC-01 | Given an agreed fixed sequence and n, each eligible trial is classified correctly | Pure Kotlin sequence tests with boundary cases |
| AC-02 | Input is attributed to the correct trial within the agreed response window | Fake-clock tests, boundary instants, duplicate taps |
| AC-03 | The session completes exactly once and produces the agreed result | State-machine tests and complete emulator flow |
| AC-04 | Backgrounding, rotation and process loss follow the agreed policy | Lifecycle/emulator scenarios |
| AC-05 | Controls and text remain usable with large font and supported accessibility settings | Compose checks plus visual/device inspection |

## Dependencies

[F000](F000-harness.md), [architecture](../decisions/ADR-002-android-foundation.md).

## Open questions

1. Grid size and eligible positions, fixed n and number of trials.
2. Stimulus duration, inter-trial interval and response window boundaries.
3. Treatment of the first n trials; match frequency and accidental/lure matches.
4. Hits, misses, false alarms and correct rejections; result formula/denominator.
5. Duplicate taps, response feedback and late responses.
6. Pause/background behavior, rotation, back navigation and process death.
7. Accessibility approach for a spatial visual task; motion/contrast requirements.

## Agreement record

Pending. The next specification issue resolves these questions before implementation.
