# F008 — Dedicated Settings and configurable session length

Agreement: Agreed — owner approved the specification and implementation on 2026-09-25.
Tracking: [issue #35](https://github.com/lumensparkxy/nback-lab/issues/35).
See the [combined proposal and decisions](session-experience-plan.md).

## User outcome

Configure what to remember, how far back to compare and how long to play on a
dedicated screen, then start quickly from a clear Home screen.

## Scope and exclusions

Move existing setup controls to Settings; add selectable normal-session length.
Retain Position, Colour and Number, manual 1-/2-/3-back and current pace controls.
No mid-session editing, automatic difficulty, pause/resume, audio, new symbol
vocabulary, arbitrary duration timer or higher n in this proposal.

## Behavior

### Home and Settings

- Home shows Start session, a compact setup summary, Settings, Results, How to
  Play and Guided practice. Privacy remains accessible under F007.
- Example summary: “Position + Colour · 2-back · 20 scored turns · 3 s per turn”,
  followed by “2 warm-up turns · 1 min 6 s total”. All values reflect the same
  configuration Start will use. The summary opens Settings as an additional shortcut.
- Home no longer contains editable setup controls or the full help disclosure.
  Settings has a clear title and Back action; Android Back returns Home.
- Settings controls appear in this order:

| Label | Proposed choices | First-use default |
| --- | --- | --- |
| Session length | 10, 20, 30, 50 scored turns | 20 |
| Types to match | Independent Position, Colour and Number toggles | Position |
| How far back? | 1-back, 2-back, 3-back with plain-language explanation | 2-back |
| Time per turn | Existing integer 1–30 second control | 3 seconds |

- Keep at least one type selected; all seven existing combinations work. Number
  means digits 1–9, not a hidden symbol option. No unavailable Symbol control.
- Changes apply immediately to the next-session setup and save asynchronously in
  order. Back keeps the displayed selection; there is no separate Save/Cancel form.
  Explain “Changes apply to your next session.” Preserve visible Saving/failure/Retry
  states and loading guards on Settings and relevant Home status.
- Missing length defaults to 20. Invalid length resets only that field to 20 with
  notice; valid level/types/pace survive. General I/O failure uses existing handled
  defaults, permits play and honestly reports that settings could not load/save.
  Late recovery must not overwrite newer selections or active sessions.

### Length, generation and scoring

Length means scored turns, excluding the n warm-up turns. For length L and interval
t seconds, total normal duration is `(n + L) × t`. Update the estimate immediately;
number of selected types does not multiply duration. At 2-back and 3 seconds:

| Scored turns L | Total turns | Total time | Targets per active type |
| --- | --- | --- | --- |
| 10 | 12 | 36 seconds | 3 |
| 20 | 22 | 1 min 6 s | 6 |
| 30 | 32 | 1 min 36 s | 9 |
| 50 | 52 | 2 min 36 s | 15 |

Keep target frequency exactly 30%: select `3 × L / 10` distinct scored turns
uniformly per active type, independently across types. Preserve F004 target and
non-target generation, with no accidental extra n-back matches. Presets avoid
rounding an arbitrary target count.

Per type: hits + misses = `3L/10`; false alarms + correct rejections = `7L/10`;
all four counts sum to L. Accuracy is `100 × (hits + correct rejections) / L`.
Compute with full precision and display percentages rounded to the nearest whole
percent consistently in summaries, chart labels and tables. Existing 20-turn final
percentages are whole numbers, so their displayed values remain unchanged. For
example, 20 correct out of 30 displays 67%, not 66%. No responses still yields 70%;
all-match taps yield 30%. No combined headline percentage across active types.

Progress reads “Turn k of L” after warm-up. Each type's timeline has L points;
point k closes at `(n + k) × t` and uses the first k scored outcomes. Follow
F006's step chart, accessible table, exposure bands and warm-up exclusion.
Every denominator, time label, result and instruction must use actual configuration.

Start snapshots all four settings. Play again and interruption Restart reuse the
original snapshot with a fresh random sequence. New Home selections affect future
Home starts only. Timers, input boundaries, duplicate rejection, rotation,
foreground interruption and process-loss rules remain unchanged.

Practice still has n warm-up plus four scripted evaluated examples with untimed
feedback. It uses selected types/n/pace, independent of normal length. Starting a
normal session from practice completion uses the length snapshotted on practice entry.

### Storage and old results

Add length to the existing ordered preference transaction and immutable session
configuration. New saved records include scored-turn count and an explicit new
rules version; persist outcomes with exactly L entries per active type. Preserve
atomic summary/timeline saves, identical-payload retries and clear ordering.

Extend Room schema and raw-data preflight through an additive preserving migration.
All historical records are known 20-turn sessions: retain their IDs, timestamps,
types, level, pace, counts, rules version and any existing timelines; assign length
20. Rules versions 1–3 continue to require their original 20/6/14 invariants. New
rules validate the selected preset and its matching outcome counts. Legacy records
without timelines still have none; never reconstruct outcome order from counts.

Validate length at engine, preference and history boundaries. Settings recovery
may reset an invalid preference, but malformed history must remain an explicit
error without clamping, dropping records or destructive migration. Clearing
history leaves every setup preference intact. No raw stimulus/tap data is added.

## Acceptance criteria and verification

| ID | Observable acceptance criterion | Verification |
| --- | --- | --- |
| AC-01 | Home summary and Settings agree; all controls reside on Settings; Back retains selection | Navigation/Compose checks and rendered Home/Settings inspection |
| AC-02 | All presets work at every n, pace boundary and mode; defaults and durations are correct | Parameterized configuration, duration and selection fixtures |
| AC-03 | Each active type has exactly 30% targets, L outcomes and correct denominators | Seeded generation; perfect/no-input/all-tap/mixed tests at each L and n |
| AC-04 | All run actions use immutable configuration; practice remains four examples | Fake-clock start/restart/replay/practice tests; rotation, interruption and process loss |
| AC-05 | Length survives restart; invalid/missing values and read/write failures are honest | Preference round-trip, ordering/race/failure and app restart fixtures |
| AC-06 | Every old history schema migrates without lost/fabricated facts; new variable timelines validate and save atomically | All supported schema migrations/reopen, corrupt/invalid rows, retry/clear race fixtures |
| AC-07 | Play, results, charts and help contain no stale 20-turn assumptions | L=10/20/30/50 end-to-end flows; final timeline point equals displayed summary under the same rounding |
| AC-08 | Setup is usable in portrait/landscape, at 200% text and with labeled 48 dp controls | Emulator screenshots and accessibility inspection; normal Home summary/Start visible at 390 × 844 dp |
| AC-09 | New navigation preserves privacy access and adds no ad opportunities | F007 route/Back/Play again tests; no setting edits during active play |

## Dependencies

F002/F004/F005/F006 and ADR-002/003/004. Coordinate result metadata with F009 and
instruction copy with F010. See the combined proposal for implementation ordering.

## Open questions

Presets and retaining 1–3-back are agreed. If Symbol
is wanted now, first resolve replacement versus additional type, the exact symbols
and accessible presentation; that changes this scope and comparison metadata.

## Agreement record

2026-09-25: owner requested planning for separate Settings and configurable length,
types and memory distance. The values and detailed behavior above are recommendations.

The owner approved the full recommended contract on 2026-09-25. Number 1–9 is
retained; Symbol is deferred. Merge and publishing require separate authorization.
