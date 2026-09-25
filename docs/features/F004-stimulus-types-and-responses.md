# F004 — Stimulus types and independent match responses

Agreement: Agreed — implementation approved on 2026-09-21, including colour-only
stimuli with no colour name inside the tile. When Number is active, its digit
appears inside the coloured tile.

Delivery: [issue #23](https://github.com/lumensparkxy/nback-lab/issues/23) (rules,
settings and history) and [issue #24](https://github.com/lumensparkxy/nback-lab/issues/24) (UI).

This feature extends F001–F003 only where explicitly described below. Owner
approval authorizes implementation within ready issue scope, but not merge or
publishing. The owner has resolved the Colour cue choice as colour alone.

## User outcome

Choose what to remember: a position, a colour, a number, or a combination.
When comparing the current turn with n turns earlier, report a match for each
selected type separately and understand performance for each type afterwards.

“Type” means the attribute being compared. A session's “mode” is its selected
set of types. Numbers are the first symbol set; shapes and sound can
be specified later without exposing unavailable options in this delivery.

## Scope and exclusions

Scope: Position, Colour and Number; type-specific match controls;
instructions and guided practice for the selected mode; per-type results and
local history. Keep the current manual 1-/2-/3-back selection and offline design.

Agreed first-delivery direction: include combinations immediately, with users
independently toggling which types are active. For the three types,
support all seven nonempty selections: Position, Colour, Number, Position +
Colour, Position + Number, Colour + Number, and Position + Colour + Number.
Do not call a three-type session “dual n-back”.

Sound is a later feature: no microphone, recording, audio permission, playback
or placeholder Sound control now. “Record a response” means accepting a match
activation for a particular type; it does not mean recording the user's voice.
No new speed settings, per-type n, adaptive difficulty, reaction-time metric,
account, cloud service, rankings or claims about cognitive improvement.

## Behavior

### Session setup

- Home offers a labeled “What to match” selection and the existing difficulty
  selection. Default remains Position and 2-back; existing users retain saved n.
- Use an independent on/off toggle for each type: Position, Colour and Number.
  Every toggle exposes its label and selected state. Require at least one
  selected type; prevent deselecting the last type and explain why. Do not
  expose controls for modes that are not delivered.
- Toggles configure the next session on Home; they are not gameplay response
  controls. The active type set stays fixed throughout a session or practice.
  During play, show a separate match control only for each active type.
- Show the exact configuration, for example “Position + Colour · 2-back”, with
  instructions and an example for those types. One n applies to every type.
- Remember the selected type set locally. Snapshot n and types on explicit
  Start or Guided practice. Play again and Restart retain that session's exact
  configuration and use a fresh sequence. Changing Home settings cannot alter
  an active session or a saved result.
- Extend F002's loading, ordered saving, retry and failure behavior to the type
  selection. Missing type settings mean Position; invalid settings visibly
  reset the type selection to Position without resetting a valid n. A failed
  save leaves the displayed selection usable and must not claim it was saved.

### What appears on each turn

| Active type | Presentation | What counts as a match |
| --- | --- | --- |
| Position | A tile appears in one of the nine existing grid cells | Same cell as n turns earlier |
| Colour | The tile uses a colour from a fixed palette | Same colour identity as n turns earlier |
| Number | The tile displays one digit from 1–9 | Same digit as n turns earlier |

In combined modes, one tile carries the selected attributes together: for
example, a blue tile showing 7 in the top-left cell. Attributes are compared
independently; matching position does not require matching colour or number.

When Position is inactive, use one stationary central tile without a grid.
When Colour is inactive, use a neutral tile with the existing position-indicator
contrast. When Number is inactive, show no digit. Inactive attributes do not vary
as distractors. Number identity is independent of grid-cell identity: 7 does
not mean cell 7. Digits must remain legible on every delivered colour.

Use six fixed colours: red, blue, green, yellow, purple and orange. Show colour
alone during play; do not add a colour name inside or beside the stimulus. Names
belong in instructions and practice feedback. Use contrasting digits and an
outline so the tile boundary remains visible, including for light colours.
This visual-only choice does not provide equivalent play for every colour-vision
deficiency; explain the visual requirements before starting.

Keep n warm-up turns followed by 20 scored turns. Show the stimulus for the
first 1,000 ms of each 3,000 ms turn, then hide its position/colour/number content
for the remaining 2,000 ms. The response window spans the full turn. All active
types share turn boundaries, warm-up and session duration (63/66/69 seconds).

### Response controls

Replace the generic Match label with one control per active type, always ordered
Position, Colour, Number. Visible labels are “Position match”,
“Colour match” and “Number match”. Keep the type label visible after activation;
add a check mark and a neutral “Recorded” state without revealing correctness.
Arrange the controls in one horizontally centered row in both orientations, using
compact rounded square buttons rather than full-width stacked rows. Show the type
and action on separate lines. Keep equal button sizes, at least 48 dp touch targets,
and allow taller buttons at enlarged text sizes without changing their row or
moving the grid when a response is recorded.

- Position-only sessions have one Position match control. A Position + Colour
  session has two; a three-type session has three.
- Tapping a control records a match response for that type on the current turn.
  Accept at most one response per type per scored turn. Disable only that
  control; the others remain available until answered or the window closes.
- If two or three types match, activate each matching control within the same
  window. Sequential taps suffice; simultaneous finger presses are not required.
- Not activating a type's control means a non-match response for that type.
  There is no “All match”, generic Match, or explicit No match control.
- Responses cannot be undone within a turn. Duplicate taps and held presses
  do not repeat responses. Every type resets to unanswered at the next turn.
- All responses are disabled during warm-up. Unknown/inactive type events and
  activations outside a response window have no scoring effect.
- Preserve F001's handling-time attribution: advance elapsed time before input;
  a boundary activation belongs to the new turn, and final-deadline input cannot
  change results. Each accepted activation has its own type and handling time.

For example, at 2-back, compare a **blue 7 at top-left** with the current
**blue 4 at top-left**. Position and Colour match; Number does not. Tap Position
match and Colour match, and leave Number unanswered. Tapping only Position
produces a Position hit, a Colour miss and a Number correct rejection.

### Generation and scoring

Generate each active type's stream independently before starting. For each type,
uniformly select exactly six distinct target turns from the 20 scored turns.
Warm-up values are independent uniform draws from that type's value set. At a
target, copy the value n turns earlier; otherwise draw uniformly from the values
excluding that reference. Prevent accidental extra n-back matches per type.

Do not force targets for different types to coincide or exclude coincidences.
A turn can have no matching types, one, several, or all. Independent generation
does not promise that every combination occurs in every session. Time and
randomness remain injected into the Android-free engine.

Score each type independently with the existing hit/miss/false-alarm/correct-
rejection rules. Each type has 20 outcomes, six targets and fourteen non-targets.
One wrong or missing response must not erase another type's correct response.

### Results and history

- Show the selected type set and n, then one result section per active type:
  accuracy, Correct X/20, Hits X/6, Misses, False alarms X/14 and Correct rejections.
- Use type-specific explanations. Preserve the explanation that no taps gives
  70% accuracy for each type and that hits and misses matter too.
- Do not introduce a combined headline percentage or an all-types-correct score.
  For multiple types, a history row shows compact labeled per-type percentages;
  opening the row shows the full breakdown.
- History offers difficulty and exact-mode filters; default both to All. A
  Position filter means Position-only, not every combination containing Position.
  Explain that comparisons should use the same type set and n. No cross-mode
  averages or automatic progress claims.
- Save one completed-session summary containing the mode and all its per-type
  counts atomically. Preserve F003's save/retry/idempotency/clear semantics.
  Clear history covers every mode and level and leaves session settings intact;
  update its confirmation text to state this scope.
- Preserve every existing history record as Position-only with its original n,
  timestamp, identity and counts. Never synthesize Colour or Number scores for
  older sessions. Define and test a preserving schema migration before delivery.
- Store summaries only, not raw stimulus streams, individual taps or reaction
  times. Practice, interrupted sessions and process-lost transient work stay unsaved.

### Instructions, practice and accessibility

Explain each selected comparison and the need to answer matching types separately
before Start. Retain F002's repeatable practice with n warm-up turns, four evaluated
examples and untimed feedback barriers. Feedback compares each type's current and
n-back value and explains that type's response and expected action.

For combined practice, use deterministic examples that include no types matching,
one type matching and all selected types matching; triple-mode practice also
includes exactly two matching. Every active type must appear as both match and
non-match across the four examples. The deterministic scripts below apply at n=1/2/3. Do not reveal answers until that example's response window closes.

Practice script definition (zero-based values): Position-only retains the exact
F002 scripts. Other single types use match/non-match/match/non-match. For pairs,
the four target sets are none / first type / both / second type. For triples,
they are none / Position / all / Position + Colour. Order types Position, Colour,
Number. For each stream, initialize warm-up value i to i modulo its cardinality;
for evaluated example j copy its n-back reference when targeted, otherwise use
(reference + 1) modulo cardinality. This fully defines every scripted stream
and ensures each type appears as both match and non-match.

Preserve interruption, rotation, deadline and process-loss behavior for the full
configuration and every type's response state. Practice remains unsaved and
never automatically starts a normal session.

Keep controls usable in portrait/landscape and at 200% text size, with at least
48 dp targets, accessible type labels and explicit recorded/disabled states.
Use text or icons as well as colour for selection, response status and feedback.
Do not automatically speak current stimulus values or imply equivalent nonvisual
gameplay. Explain the selected task's visual requirements before starting.

## Acceptance criteria and verification

These are implementation requirements, not evidence of delivered behavior.

| ID | Observable acceptance criterion | Planned verification |
| --- | --- | --- |
| AC-01 | Independent type toggles support all seven nonempty selections; at least one stays active; n/types are remembered, validated and snapshotted | All toggle combinations and last-toggle guard; settings read/write/failure/race fixtures; Home/restart/play-again flows |
| AC-02 | Each turn displays only active attributes with synchronized timing and only their match controls | Fake-clock phase checks; rendered fixtures for all seven selections; no type changes during play/practice |
| AC-03 | Responses are independent, once per active type per scored turn | All response subsets; duplicates, inactive types, warm-up, held/boundary/final-deadline input |
| AC-04 | Each stream has exactly six targets and no accidental extra matches at selected n | Seeded generation tests for every delivered type set at n=1/2/3; overlap fixtures |
| AC-05 | Results correctly report separate 20-outcome summaries | Perfect/no-input/all-tap/mixed fixtures; one type correct while another is wrong |
| AC-06 | Practice teaches independent responses without scoring future examples | Agreed scripts at each n; feedback barrier and delayed-callback checks |
| AC-07 | History preserves old results and saves new multi-type summaries atomically | Old-schema migration/reopen; duplicate retry, failure, filtered reads and global clear |
| AC-08 | Lifecycle changes preserve or cancel all types consistently | Rotation in stimulus/blank/feedback/results; foreground loss, process death and completion boundary |
| AC-09 | All controls and stimuli remain usable with the delivered palette/layout | Portrait/landscape/200% text screenshots; contrast and TalkBack control-navigation inspection |
| AC-10 | Existing Position-only behavior and offline/privacy boundaries remain intact | F001–F003 regressions with the deliberate control-label change; dependency/storage review |

## Dependencies and implementation readiness

Build on F001–F003 and ADR-002/003/004. The owner has approved expanding the
next feature to include combinations and independent type toggles immediately;
this resolves the earlier product-direction question about combined modes.
The F004 extensions in ADR-003/004 define type-set preferences and per-type
summaries. Keep storage in app and rules/timing/scoring in engine.

Issue #23 owns rules/settings/history; issue #24 owns UI. Both are ready, including
the approved colour-only presentation. The first feature delivery includes all
combinations. No additional dependencies or architectural layers are needed.

Implementation requires doctor/verify checks, emulator tests and rendered QA on
an explicitly selected emulator, plus independent review under the repository
workflow. Run the failure-gate check for failure-handling changes. Documentation
review alone cannot satisfy runtime acceptance criteria.

## Colour presentation decision

The owner selected colour alone: the tile contains the digit when Number is
active, with no additional colour-name cue.

## Agreement record

On 2026-09-21 the owner requested a feature description for secondary types such
as colour and symbols/numbers, possible later sound, and responses that identify
the relevant type. The owner subsequently confirmed: include combinations
immediately and let users toggle which types are active. This approved the delivery direction and selection interaction; subsequent
implementation approval is recorded below.

The owner subsequently approved implementation. This accepts Number 1–9, separate
labeled irreversible match responses and per-type results without a combined score.
The owner then selected colour alone because the symbol occupies the tile.
This resolves the remaining Colour UI question and authorizes its implementation.

## Presentation amendment

[F005](F005-guided-design.md) revises setup, optional instructions, result detail
and history control presentation. The gameplay and persistence contracts above
remain unchanged.

## F008–F010 amendment — 2026-09-25

Owner-approved [F008](F008-settings-and-session-length.md) generalizes normal
length and count/timing denominators and moves controls into Settings.
[F009](F009-results-and-progress.md) defines the Results destination and comparable
cross-session progress. [F010](F010-how-to-play.md) moves full instructions to a
dedicated screen. These supersede only the explicitly revised behavior; preserve
all remaining scoring, practice, lifecycle, storage and accessibility contracts.
