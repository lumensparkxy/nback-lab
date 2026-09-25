# F006 — Session interval and accuracy timeline

Agreement: Agreed — owner approved on 2026-09-21 with exposure bands:
1–7 second turns show stimuli for 1 second; 8–15 for 2 seconds; 16–30 for 3.
The overlapping 7-second boundary in the request is assigned to the first band.

Delivery: [issue #28](https://github.com/lumensparkxy/nback-lab/issues/28).

## User outcome

Choose a comfortable pace before starting, then see how accuracy developed for
each active type during the completed session. Keep the Guided Play visual design.

## Behavior

### Time between turns

- Home adds a compact “Time per turn” control below difficulty: discrete slider,
  1–30 seconds in one-second steps, initial/default value 3 seconds. Show the
  selected value prominently and provide labeled decrease/increase buttons for
  precise adjustment and accessible actions; endpoints cannot exceed the range.
- This is the time from one stimulus appearing to the next. Use one-second exposure for intervals 1–7, two seconds for 8–15 and three
  seconds for 16–30; the remainder is blank, with responses accepted
  throughout the turn. At 1 second there is no blank phase, so identical consecutive stimuli may
  appear continuous; turn progress still advances. Explain this in Help.
- Remember interval alongside level and selected types. Missing interval means
  3 seconds; invalid interval visibly resets only that preference to 3. Preserve
  existing ordered saves, loading and retry/failure behavior.
- Snapshot interval on Start or Guided practice. Play again/Restart reuse the
  completed/interrupted session configuration; Home changes affect the next run.
- Normal sessions retain n warm-up turns plus 20 scored turns. Home updates total
  duration immediately: `(n + 20) × interval`. For 2-back this is 22 seconds at
  1 second, 66 seconds at 3 seconds, and 11 minutes at 30 seconds.
- Practice uses the chosen interval for stimulus/response windows, retaining its
  user-paced feedback and Next barrier. Do not label its duration as fixed.
- Keep elapsed monotonic timing, exact-boundary input attribution, delayed-event
  reconciliation, rotation and interruption policies. Changing pace does not
  change match frequency, response semantics or scoring. Update all fixed-timing
  copy and duration labels to reflect the actual configuration.
- Keep Home compact: condensed interval row and slider rather than another large
  settings card. Retain setup and Start visibility at the agreed normal reference
  viewport; smaller screens, landscape and 200% text may scroll.

### Accuracy during the session

- Completed-session Results adds “Accuracy over time” after the concise per-type
  results and primary Play again action. Retain the existing counts and accuracy
  caveat. Show the same chart when reopening a new saved session from History.
- X-axis: elapsed time from session Start, formatted in seconds or minutes:seconds.
  Show the warm-up region with no score line; warm-up is excluded from accuracy.
- Y-axis: accuracy, fixed 0–100%. One line per active type, named Position, Colour
  and/or Number. Inactive types have no line or legend entry.
- At the close of scored turn k (1–20), its point is:
  `x = (n + k) × interval`, `y = 100 × correct outcomes through k / k`.
  Use scheduled turn deadlines even if timer callbacks arrive late.
  Hits and correct rejections count as correct. Use cumulative accuracy, not a
  recent-turn average. Keep full precision for plotting and round displayed
  percentages consistently. The twentieth point equals the summary percentage.
- Use a step line and markers so the display reflects discrete closed turns.
  Do not invent a 0% starting score, smooth curves or values during warm-up.
  No live score/correctness chart during gameplay or practice.
- Use contrasting colours plus distinct line/marker styles, a text legend and
  optional per-line visibility controls so overlapping series can be inspected.
  These controls affect only the displayed chart, never the session's active types.
- Offer a labeled expandable data table with scored-turn number, elapsed time and
  each active type's running accuracy. It provides exact values and a screen-reader
  alternative; the chart also exposes a concise text summary. No precision tapping
  on tiny chart points is required. Preserve 48dp control targets and 200% text use.
- Explain: “Running accuracy includes correct waits. No responses can still give
  70% overall.” Keep target-hit and false-alarm information alongside the graph.

### Saved results and compatibility

- Save the selected interval and the ordered 20 outcomes for every active type
  with each newly completed normal session. Derive chart points from these facts;
  do not persist duplicate percentages, generated stimuli or raw input timestamps.
- Save summary and timeline atomically. Validate exact active-type coverage,
  outcome values/counts, range of interval and agreement with summary counts.
  Apply this validation before reads and mutations, including migration preflight,
  save and clear; malformed stored timelines must not be silently overwritten.
  Preserve identical-payload retry checks, save failures, global clear and filters.
- Preserve existing history with its original three-second interval and summaries.
  Old records have no recoverable order: show “Timeline unavailable for this older
  session.” Never reconstruct a curve from aggregate counts or fabricate points.
- Show interval in result/history detail metadata so sessions at different speeds
  are not presented as equivalent configurations. No new history filters or
  cross-session trend graph in this scope.
- This explicitly extends ADR-004's summary-only storage boundary. Update its
  accepted extension and preserving schema migration alongside implementation
  under this owner agreement. Keep all data offline and existing backup exclusions.

## Acceptance criteria

| ID | Observable outcome | Verification |
| --- | --- | --- |
| T01 | All integer intervals 1–30 selectable; default 3; value and normal duration accurate | Settings/unit and Compose checks; 1/3/30 at each n |
| T02 | Preference persists, invalid values recover independently, save failure remains honest | Settings round-trip/invalid/failure tests |
| T03 | Interval fixed per run; exact timing, first-response semantics, completion and lifecycle preserved | Fake-clock boundary/duplicate/delayed jumps at 1/3/7/8/15/16/30, all n/modes; emulator rotation/interruption |
| T04 | Practice uses selected pace and retains feedback barrier | Deterministic practice tests and emulator Next flow |
| T05 | Twenty cumulative points per type, correct elapsed times, no warm-up score, exact final summary agreement | Known mixed-outcome streams, all-correct/no-response, single/all types |
| T06 | Results and saved detail show usable chart, legend and equivalent data table | Normal/200% portrait/landscape screenshots, semantics and overlapping-line inspection |
| T07 | New timelines persist atomically; old summaries preserved without invented curves | All supported schema upgrades/reopen, malformed rows/timeline, retry/clear/failure and historical fixtures |
| T08 | Home remains compact and fixed-timing copy is replaced | Reference-viewport full Start visibility; layout/copy checks at every interval boundary |

## Scope and dependencies

Depends on F004 modes and F005 Guided Play presentation (PRs #25 and #27 are open
at drafting time). Keep engine rules/timeline derivation independent of Android;
Room owns persistence and Compose owns rendering. No sound, reaction-time score,
variable pace mid-session, pause/resume, adaptive difficulty or release work.

## Agreement record

The owner approved the draft and implementation with the variable exposure bands
above. Cumulative accuracy, saved new-session timelines, practice pace and the
preserving migration are included. Merge/publish require separate authorization.

## F008–F010 amendment — 2026-09-25

Owner-approved [F008](F008-settings-and-session-length.md) generalizes normal
length and count/timing denominators and moves controls into Settings.
[F009](F009-results-and-progress.md) defines the Results destination and comparable
cross-session progress. [F010](F010-how-to-play.md) moves full instructions to a
dedicated screen. These supersede only the explicitly revised behavior; preserve
all remaining scoring, practice, lifecycle, storage and accessibility contracts.
