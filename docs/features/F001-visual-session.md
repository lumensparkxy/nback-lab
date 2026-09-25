# F001 — Fixed-level visual n-back session

Agreement: Agreed — owner approved the full contract on 2026-09-20.

Specification: [issue #2](https://github.com/lumensparkxy/nback-lab/issues/2).
Delivery: [issue #3](https://github.com/lumensparkxy/nback-lab/issues/3).

## User outcome

Complete a short visual position-matching session and understand the result.
This first playable slice is not a validated cognitive assessment or a claim
about health or intelligence improvement.

[F002](F002-practice-and-difficulty.md) extends this baseline with selectable n
and guided practice; its explicit overrides apply to the configurable delivery.

## Scope and exclusions

One fixed 2-back level, instructions, a 3×3 grid, one Match button, a complete
session and an in-memory result. All nine grid positions are eligible.
No adaptive level, selectable difficulty, separate practice mode, audio task,
account, network access, saved history, pause/resume or release work.
Practice/difficulty belong to [F002](F002-practice-and-difficulty.md);
persistent results/history belong to [F003](F003-results-and-history.md).

## Behavior

### Instructions and flow

Explain: “Tap Match when the highlighted square is in the same position as two
turns ago. Otherwise, wait. The first two turns are warm-up.” Include a static
A → B → A example that identifies the third turn as a match. Reading instructions
does not start the timer; the user explicitly chooses Start.

Start → 2 warm-up trials → 20 scored trials → results. A normal session lasts
66 seconds, with no separate countdown. Trial 1 begins when Start is accepted.
Show warm-up progress separately from scored progress (1/20 through 20/20).
Warm-up uses identical stimulus timing; Match is disabled and taps are discarded.
Each scored trial receives one outcome when its response window closes.

### Timing and input

Use elapsed monotonic time supplied to the engine. For one-based trial i,
start time s = session start + (i − 1) × 3,000 ms:

- Highlight the position during [s, s + 1,000 ms).
- Leave the grid unhighlighted during [s + 1,000 ms, s + 3,000 ms).
- Accept Match on a scored trial during the full [s, s + 3,000 ms) window.
- At s + 3,000 ms, close the old window and begin the next trial. At session
  start + 66,000 ms, close the final window and produce results exactly once.

Timestamp input when the app handles the activation, using the engine's clock.
Advance elapsed time before attributing input: an activation exactly at a boundary
belongs to the new trial. At or after 66,000 ms it cannot change the result.
Wall-clock changes must not affect timing. An activation is one completed click
or equivalent accessibility action. A press begun in one trial and released in
the next is attributed to the next trial if the control accepts that activation;
press-down alone is not a response. The first accepted Match activation records
a response; subsequent activations in that trial are ignored. A new trial accepts
a new activation. Holding the control generates no automatic repeats.

Give neutral acknowledgement (“Response recorded”) and disable Match for the
rest of that trial. Reveal correctness only in results. There is no No Match
button: no accepted activation means a non-match response. Input before Start,
during warm-up, after interruption or after completion has no scoring effect.
Late events use their handling time and are not backdated.

Recomposition must not advance or reset a session. Delayed timer delivery uses
actual elapsed time: reconcile expired trials, count their unanswered responses
normally, and render the current phase without replaying missed stimuli or
extending the session. A background interruption instead follows the policy below
and never produces a partial score.

### Sequence generation

Generate all 22 positions before starting, with injected randomness for tests.
Select exactly six distinct scored trial indices uniformly without replacement
from trials 3–22. These are the intended matches: 6/20 = 30%.

Choose each warm-up position independently and uniformly from the nine cells.
Then process scored trials in order:

- At a selected match index, copy the position from exactly two trials earlier.
- Otherwise, choose uniformly from the eight cells excluding that two-back
  position, preventing unintended extra 2-back matches.

Consecutive matches and repetitions at distances other than two are allowed.
There is no additional control of lure frequency or match spacing in this slice.
Do not reveal the generated sequence or upcoming match indices during play.

### Scoring and results

| Actual trial | Accepted Match activation | No accepted activation |
| --- | --- | --- |
| Same position as two trials earlier | Hit | Miss |
| Different position from two trials earlier | False alarm | Correct rejection |

Display all four counts with plain-language explanations, “Correct: X/20”, and
accuracy = 100 × (hits + correct rejections) / 20. This yields integer percentages
in steps of five; warm-up never enters the denominator. Counts sum to 20;
hits + misses = 6; false alarms + correct rejections = 14.
No pass/fail threshold, adaptive recommendation or reaction-time score.
A session with no taps yields 0 hits, 6 misses, 0 false alarms, 14 correct rejections
and 70% accuracy; accuracy alone is not target detection.

Results offer Play again (fresh sequence and full session) and Home. Results
survive rotation and background/foreground within the same process, until another
session starts, Home is chosen or the process is lost. Nothing is saved to history.

### Worked deterministic session

For this example, cells 1–9 are in row-major order (1 top-left, 9 bottom-right).
Visible cell numbers are not required. Each Match is one activation 500 ms after
that trial starts.

| Trial | Cell | 2-back match? | Response | Outcome |
| --- | --- | --- | --- | --- |
| 1 | 1 | — | — | Warm-up |
| 2 | 5 | — | — | Warm-up |
| 3 | 1 | Yes | Match | Hit |
| 4 | 9 | No | Match | False alarm |
| 5 | 2 | No | None | Correct rejection |
| 6 | 9 | Yes | Match | Hit |
| 7 | 4 | No | None | Correct rejection |
| 8 | 6 | No | Match | False alarm |
| 9 | 4 | Yes | Match | Hit |
| 10 | 8 | No | None | Correct rejection |
| 11 | 3 | No | None | Correct rejection |
| 12 | 7 | No | None | Correct rejection |
| 13 | 3 | Yes | Match | Hit |
| 14 | 2 | No | None | Correct rejection |
| 15 | 5 | No | Match | False alarm |
| 16 | 2 | Yes | None | Miss |
| 17 | 8 | No | None | Correct rejection |
| 18 | 6 | No | None | Correct rejection |
| 19 | 9 | No | None | Correct rejection |
| 20 | 6 | Yes | None | Miss |
| 21 | 1 | No | None | Correct rejection |
| 22 | 4 | No | None | Correct rejection |

Expected result: 4 hits, 2 misses, 3 false alarms, 11 correct rejections;
15/20 correct and 75% accuracy. Matches are trials 3, 6, 9, 13, 16, 20.

### Lifecycle and navigation

- Rotation/configuration recreation within the same process preserves the
  sequence, responses and original time origin. Time continues during recreation;
  recreation cannot introduce a new trial, extra time or duplicate completion.
- System Back during play cancels immediately and shows “Session interrupted”
  with Restart and Home. No confirmation dialog or partial score.
- Losing the foreground/resumed state during play (app switching, screen locking
  or an interruption that pauses the activity) cancels the session, except for
  configuration recreation. On return, show the interruption screen; never resume
  gameplay automatically.
- Restart creates a fresh sequence, both warm-up trials and reset counts. Home
  returns to instructions. Back from interruption/results also returns Home.
- Process loss discards any session or unsaved result. A new process starts at
  Home; never reconstruct a partial session or display partial results.
- At the final deadline, completion wins if elapsed time is already 66,000 ms
  when interruption is processed. Before that deadline, interruption produces no
  result. Completed results are not converted to interrupted sessions.

### Accessibility and presentation

Keep the grid spatially stable in portrait and landscape. Distinguish the stimulus
using fill and outline, not hue alone; use no movement or flashing animation.
Require at least 4.5:1 text contrast and 3:1 active-cell indicator contrast against
its surroundings. Controls have at least 48×48 dp touch targets, semantic labels
and explicit enabled/disabled state. At 200% font size, instructions/results may
scroll; gameplay must keep the grid, progress and Match control usable.

Provide accessible navigation for instructions, controls and results. Identify
the grid as a visual task without automatic spoken cell announcements. This slice
does not offer a nonvisual equivalent or adjustable timing; explain on the start
screen that it requires seeing positions and uses fixed three-second turns.
Test TalkBack navigation without claiming equivalent nonvisual gameplay support.

## Acceptance criteria and verification

These are agreed implementation requirements, not assertions of implemented
behavior or passing tests. Time and randomness are controllable test inputs.

| ID | Observable criterion | Planned verification |
| --- | --- | --- |
| AC-01 | Classify every eligible trial by exact two-back equality; exclude warm-up | Pure Kotlin full worked-example test and individual match/non-match cases |
| AC-02 | Accept one activation per scored trial within its window | Fake-clock checks just before/at/after 1,000 ms, 6,000 ms, 9,000 ms and 66,000 ms; duplicates and inputs outside play; UI tests for held input and a press/release spanning a trial boundary |
| AC-03 | Complete once after 22 trials, with specified counts and accuracy | Worked example, all-correct, no-input and all-Match tests; repeated completion calls; complete emulator flow |
| AC-04 | Interrupt, recreate and restart without partial results, per policy | Engine interruption/deadline ordering; emulator rotation during highlight, blank interval and results, background/lock, Back, Restart and actual process recreation |
| AC-05 | Controls/text satisfy presentation and accessibility rules | Compose semantics/touch-target checks; portrait/landscape screenshots at normal/200% font; contrast inspection; TalkBack navigation evidence |
| AC-06 | Generate 22 valid cells with exactly six eligible matches | Injected-random tests across seeds, cells within 1–9, no extra matches, edge match sets including adjacent/first/last scored trials; inspect uniform-selection algorithm |
| AC-07 | Explain task before Start; show neutral acknowledgement and progress | Compose/emulator start-to-result flow, disabled warm-up input, separate warm-up/scored progress, no correctness feedback during play |
| AC-08 | Elapsed-time timing survives recomposition and reconciles delayed delivery | Fake-clock jumps across phases/trials, wall-clock independence, recomposition/rotation; no replay or extra time |
| AC-09 | Results are transient; Play again/Home follow the contract | Two complete sessions with fresh state; rotate/background results; Home and fresh-process reset; inspect absence of persistence/network additions |

## Dependencies

[F000](F000-harness.md) and accepted
[architecture](../decisions/ADR-002-android-foundation.md). Delivery readiness and
the issue #3 delivery status are tracked in GitHub.

## Agreement record

On 2026-09-20 the owner asked to proceed with specification issue #2 following
discussion of fixed 2-back, a 3×3 grid, two warm-up plus 20 scored trials, a
one-second stimulus every three seconds, one Match button and session-end results.

On 2026-09-20 the owner approved the completed contract in the issue #2 project
discussion, including exactly six matches, accuracy and outcome counts,
interruption/process-loss rules and the visual accessibility scope. Agreement is
recorded in [issue #2](https://github.com/lumensparkxy/nback-lab/issues/2) and
delivered through [PR #12](https://github.com/lumensparkxy/nback-lab/pull/12).
No product decisions remain open for this fixed-level slice. Configurable
difficulty and persistent history still require their own feature agreements.
Specification approval is separate from authorization to merge or implement #3.

## F008–F010 amendment — 2026-09-25

Owner-approved [F008](F008-settings-and-session-length.md) generalizes normal
length and count/timing denominators and moves controls into Settings.
[F009](F009-results-and-progress.md) defines the Results destination and comparable
cross-session progress. [F010](F010-how-to-play.md) moves full instructions to a
dedicated screen. These supersede only the explicitly revised behavior; preserve
all remaining scoring, practice, lifecycle, storage and accessibility contracts.
