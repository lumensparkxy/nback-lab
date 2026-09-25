# Session experience proposal

Agreement: Agreed — owner approved the recommended specifications and implementation
on 2026-09-25. Tracking: [issue #35](https://github.com/lumensparkxy/nback-lab/issues/35).

## Intended experience

Home is a quick starting point. Settings has the controls for the next session.
Results brings saved sessions and progress together. How to Play teaches the task
on a dedicated screen with optional guided practice.

```text
Home
  Start session → Play → Session result → Play again / Results / Home
  Settings → Session settings → Back to Home
  Results → Sessions / Progress → Saved session detail → Back to Results
  How to Play → Instructions → Guided practice → Practice complete
  Guided practice → Practice complete → Start session / Practice again / Home
```

Use labeled Home links rather than a permanent navigation bar during play.
Keep the existing warm off-white, navy and apricot Guided Play design. Settings
and instructions are reachable in one action from Home; neither starts a session.

## Current baseline and proposed changes

Inspected on 2026-09-25 at local `main`, revision `c20ae6f`. The working tree was
clean before these drafts. Findings are from specs and source inspection, not a
new emulator run.

| Area | Existing behavior | Proposed addition/change |
| --- | --- | --- |
| Setup | Home has types, 1-/2-/3-back and 1–30 seconds per turn | [F008: Dedicated Settings and session length](F008-settings-and-session-length.md); Home keeps a read-only summary |
| Length | 20 scored turns plus n warm-up turns | Select 10, 20, 30 or 50 scored turns; default 20 |
| Types | Position, Colour, Number 1–9; all seven nonempty combinations | Move existing choices to Settings; Symbol remains an explicit product decision |
| Session result | Dedicated completion view, per-type summary and running-accuracy graph; saved detail available | [F009: Results and progress](F009-results-and-progress.md) unifies entry and generalizes graphs to selected length |
| History | Local completed sessions with level/mode filters | Results → Sessions retains history; Results → Progress adds comparable-session graphs |
| Instructions | Collapsed Home disclosure plus guided practice | [F010: Dedicated How to Play](F010-how-to-play.md), contextual examples and practice entry |

GitHub inspection found issues #4, #5, #23, #24, #26 and #28 closed. Issue #31
remains open for remaining monetization/release evidence after PR #33 merged.
PR #34 is open CI maintenance; these drafts do not depend on its merge. This is
a dated dependency observation, not a replacement for live GitHub task status.
The previously pending [post-merge main CI run](https://github.com/lumensparkxy/nback-lab/actions/runs/36065368513)
was rechecked and completed successfully for `c20ae6f`; this does not certify any
of the proposed features.

## Agreed decisions

| Decision | Recommendation | Reason |
| --- | --- | --- |
| Where settings live | Dedicated Settings screen from Home; compact current setup on Home | Keeps Start prominent and configuration easy to find |
| Meaning of length | Scored turns: 10 / 20 / 30 / 50, default 20; show warm-up and estimated duration separately | Predictable scoring; each option supports exactly 30% matches per type |
| Memory distance | Retain 1-, 2- and 3-back | Existing supported levels; higher levels need a separate scope decision |
| Number or symbol | Keep Number 1–9 in this delivery | Already supported; a symbol mode needs its own vocabulary, accessibility and compatibility rules |
| Results structure | Sessions and Progress sections in one Results destination, plus session detail | Both an individual run and long-term records have a clear home |
| Progress comparisons | Compare the same types, n, pace and length; all saved sessions remain browsable | Different tasks should not imply directly comparable scores |

The owner approved these recommendations and implementation. Number remains the
third type; Symbol is deferred. A future Symbol proposal must decide replacement
versus additional type. Merging and publishing remain separate owner decisions.

## Suggested implementation issue boundaries

Issue #35 tracks all five sequenced work packages below and their acceptance IDs.
They are implemented together so variable-length controls and saved results stay
compatible throughout delivery.

| Work package | Scope and acceptance coverage | Dependencies |
| --- | --- | --- |
| A: Variable-length rules and preserving storage | F008 AC-02–06; engine counts/timing, preferences, history migration, variable timelines and all affected existing labels/results | Approved F008; accepted ADR-003/004 extensions recorded with implementation |
| B: Dedicated Settings and Home navigation | F008 AC-01, AC-07–09; expose length only after A supports it end to end | A; coordinated route names with F009/F010 |
| C: Dedicated How to Play | F010 AC-01–06; move help and preserve practice | Approved F010 and B; use A's actual configuration |
| D: Results destination and session detail | F009 AC-01–03, AC-06–09; reuse existing result/history components | Approved F009 and A/B |
| E: Cross-session progress | F009 AC-04–09; comparison groups, graph/table and all history states | D and preserving metadata from A |

A may be delivered before new controls if it preserves 20-turn defaults and has
internal variable-length coverage. Do not ship selectable length while progress
labels, score denominators, timeline validation or historical reads still assume 20.
C and D can be separately scheduled after shared navigation is established.

For implementation, run doctor/verify, failure-gate checks for affected failure
handling, and emulator tests/rendered inspection at normal and 200% text in both
orientations. Independently review meaningful code changes. Required scenarios
are specified below each feature; documentation checks do not certify them.

## Boundaries and compatibility

These agreed specifications make targeted amendments to F002/F005/F006 setup/help placement,
F001–F004 fixed scoring denominators, and F003/F006 history presentation. These amendments supersede only the explicitly changed behavior of earlier specs. Extend accepted
ADR-003/004 during implementation; keep settings in DataStore, history in Room and
rules/calculations in the pure Kotlin engine. No new backend or chart dependency
is presumed necessary.

Preserve F007's sole possible ad trigger: explicit Home from the current completed
normal result. Results tabs, saved details, Settings, Help, Back and Play again add
no ad opportunities. Production ads and release work are separate from this plan.
