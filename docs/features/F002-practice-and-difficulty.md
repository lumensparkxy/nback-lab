# F002 — Session settings, difficulty and guided practice

Agreement: Agreed — owner approved the full contract and implementation on 2026-09-21.

Delivery: [issue #4](https://github.com/lumensparkxy/nback-lab/issues/4).

## User outcome

Choose how many turns back to compare, learn that rule through optional guided
practice, and return to the same chosen level on the next app launch.

## Scope and exclusions

Home session settings; visual position 1-, 2- and 3-back; remembered level;
level-specific instructions and optional, repeatable guided practice. Keep the
3×3 grid with all nine cells eligible. Difficulty means memory distance, not speed.

No adjustable timing, adaptive difficulty, saved results/history, accounts,
network access, analytics, audio playback, colour/letter/symbol modes or dual
n-back in this delivery. Do not show unavailable mode buttons. Display
“Position” as the current task; add a mode selector only when multiple modes exist.

## Relationship to F001

[F001](F001-visual-session.md) remains the accepted fixed 2-back baseline. When
F002 is implemented, the rules below extend it to configurable n and
add a separate practice flow. This extension does not retroactively change the delivered F001 baseline.

Only these normal-session assumptions change: selected n replaces fixed 2;
there are n warm-up trials, n + 20 total trials and a level-specific duration.
The grid, input semantics, six matches, scoring denominator, neutral in-session
feedback, interruptions and transient results otherwise follow F001.

| Level | Comparison | Warm-up | Scored turns | Duration |
| --- | --- | --- | --- | --- |
| 1-back | One turn earlier | 1 | 20 | 63 seconds |
| 2-back | Two turns earlier | 2 | 20 | 66 seconds |
| 3-back | Three turns earlier | 3 | 20 | 69 seconds |

## Behavior

### Home and session settings

- Present one choice group labeled “Difficulty”: 1-back, 2-back, 3-back.
  Exactly one is selected; first-use default is 2-back. Describe the actual
  comparison distance rather than labeling levels as health/ability ratings.
- Selection updates the explanation, static example, warm-up count and duration
  on Home. Examples: A → A (1-back), A → B → A (2-back), and
  A → B → C → A (3-back), with the final item identified as a match.
- Offer “Start session” and “Guided practice”. Neither starts automatically.
  Reading instructions, selecting a level or completing practice never starts
  normal gameplay. Retain the visual-task/fixed-timing disclosure from F001.
- Start snapshots the displayed level into an immutable session configuration.
  No level selector is available during play, practice, feedback, interruption
  or results. Delayed preference reads/writes must not change that configuration.
- Include the level on normal gameplay, interruption and results screens.
  Play again and Restart use the completed/interrupted session's level and a
  fresh random sequence. Home returns to the last in-memory selected level.

### Remembered selection

Persist only `selected_n`, an integer in 1..3, in app-private settings. No session
sequence, response, result or practice-completion flag is persisted. A successfully
saved selection survives process death and ordinary updates. Clear app data resets
it to 2. Keep existing cloud-backup/device-transfer exclusions; do not enable
backup as part of this feature. This is separate from F003 session-history storage.
See accepted [ADR-003](../decisions/ADR-003-session-preferences.md).

- Initial load: show a loading state; disable selection and both launch actions
  until the read succeeds or returns a handled failure. Do not briefly permit a
  default-level start while a different saved level is still loading.
- Missing preference: use 2 without an error. Invalid type/out-of-range value or
  corrupt settings: use 2 and show “Difficulty was reset to 2-back.” Replace the
  invalid stored setting when possible; never pass it into the engine.
- Read I/O failure: permit use with 2 and show “Couldn’t load your saved difficulty.
  Using 2-back.” A later recovery must not overwrite a selection already made by
  the user in this process or an active session.
- Selection changes immediately in memory; save asynchronously in selection order.
  While the latest save is pending, show a small “Saving…” status. Start/practice
  may use that displayed selection without waiting for disk. Only the latest
  selection may become the final stored value or clear its pending/error status.
- Save failure: retain the selected level for this process and allow play; show
  “Couldn’t remember this difficulty. You can still play.” Offer Retry on Home.
  A failed write must not crash the app or claim the preference was saved.
- If the process dies before a successful save, the next launch uses the last
  successfully stored level (or 2 if none). Settings persistence never restores
  an interrupted session or its results. Store/repository fakes verify races and
  failures without adding arbitrary delays to the product.

### Normal sessions at each level

For selected n, generate n + 20 cells before the clock starts. Uniformly choose
six distinct match indices among the 20 scored trials, n+1 through n+20. Warm-up
cells are independent uniform choices from all nine cells. For each scored trial,
copy the cell n turns earlier if designated a match; otherwise uniformly choose
one of the eight other cells. Extra matches at distance n are forbidden;
other repetitions/lures and adjacent matches remain allowed.

Each trial still has 1,000 ms highlight and a 3,000 ms half-open response window.
The first n turns have Match disabled. Classification uses equality exactly n
turns earlier. Results still have 20 outcomes, six hits+misses and fourteen false
alarms+correct rejections. Accuracy remains (hits + correct rejections) / 20;
no response gives 70% at every level. Show all four explanations, X/20 correct,
selected level and the note that results are not saved. Do not compare performance
across levels or introduce recommendations/pass thresholds.

Advance before handling input, reject duplicates, use monotonic elapsed time and
complete once, as in F001. Exact normal-session deadlines are 63,000 / 66,000 /
69,000 ms. Rotation retains level/sequence/original clock; genuine foreground loss
interrupts. At or after the selected level's deadline, completion wins over an
interruption. Process loss returns to Home with remembered settings only.

### Guided practice

Practice is explicitly labeled throughout and uses the selected n. It teaches
the same equality rule and response windows, but pauses between scored examples
for explanations. Explain this pacing difference before practice begins.

Use n automatic warm-up turns followed by four evaluated turns in fixed order.
Cells below are numbered 1–9 in row-major order for the test contract; visible
cell numbers are not required. They are deliberately scripted, not random.

| Level | Full sequence | Evaluated outcomes by equality |
| --- | --- | --- |
| 1-back | 1, 1, 5, 5, 9 | Match, non-match, match, non-match |
| 2-back | 1, 5, 1, 9, 1, 4 | Match, non-match, match, non-match |
| 3-back | 1, 5, 9, 1, 2, 9, 4 | Match, non-match, match, non-match |

1. Guided practice begins when its Home action is activated: n warm-up turns,
   then evaluated turn 1. Warm-up and each evaluated turn use 1,000 ms highlight,
   3,000 ms response window and the same single completed Match activation rule.
   Show separate warm-up progress and “Practice 1/4” through “Practice 4/4”.
2. During a response window, show only neutral acknowledgement after Match and
   disable further responses. Never reveal the comparison answer early.
3. At the end of an evaluated window, freeze progress and show feedback. Display
   the just-shown cell beside its n-back reference, labeled “This turn” and
   “N turns earlier”, without exposing a future cell. State whether positions
   matched, whether the user tapped or waited, and explain the expected action.
   All four outcomes get a plain-language explanation, without a pass threshold.
4. After turns 1–3, “Next example” begins exactly one fresh 3,000 ms window for
   the next scripted cell. Feedback has no timeout. Waiting does not expire future
   turns. Duplicate/stale Next activations cannot skip a turn. A Match arriving
   at the closing deadline is ignored because the app is now in feedback; it
   cannot start or answer the next example. Likewise, a held press never counts
   as a Next action without a fresh completed activation of that control.
5. Closing evaluated turn 4 shows “Practice complete” plus that turn's feedback,
   with “Practice again”, “Start session” and “Home”. Do not show normal-session
   accuracy/count cards or a saved result. Practice again restarts the full fixed
   script, including warm-up; Start session generates a fresh normal session at
   the same level. Each requires explicit activation.
6. “Skip practice” is available during warm-up, evaluated windows and feedback;
   it returns Home, with no result and no automatic normal session. No answers,
   partial counts or completion state survive leaving practice.

Timer reconciliation can consume expired warm-up turns and close the current
first evaluated turn, but must stop at its feedback barrier. Later evaluated turns
cannot start without Next. A long callback delay never skips feedback or silently
consumes the rest of practice. Recomposition does not advance/reset practice.

Worked input fixture: tap evaluated turns 1 and 2, wait on 3 and 4. At every n,
feedback must show one hit, one false alarm, one miss and one correct rejection,
in that order. Practice must not reuse the normal session's six-match/20-turn
validation or accuracy denominator.

### Practice lifecycle and navigation

Rotation preserves the script, level, accepted responses and current phase. A
running window continues from its original time; a feedback screen stays paused.
Back during unfinished practice (including feedback) or genuine foreground loss
shows “Practice interrupted” with Restart practice and Home. Restart begins the
full same-level script, with no partial score. Back from that screen returns Home.

The fourth window's deadline wins if already reached when interruption is
processed: practice is complete. Earlier feedback barriers still count as
unfinished practice and interrupt. The completion screen survives rotation and
background/foreground in the same process; Back returns Home. Process death
always returns Home and discards practice, while keeping successfully saved n.
Skip deliberately returns Home directly; it does not show interruption.

### Accessibility and presentation

Retain F001 contrast, stable-grid, 48 dp target, portrait/landscape and 200% font
requirements. The difficulty group exposes its label, each option and selected
state to TalkBack; Home settings, save errors/Retry, practice feedback and actions
are navigable. Static explanations/feedback may scroll. Timed practice keeps the
grid, progress and Match usable. Preserve predictable focus when feedback appears.
Do not announce changing cell positions automatically or claim nonvisual-equivalent
play. The mode remains a visual task; instructions clearly distinguish timed play
from self-paced reading between practice examples.

## Future training modes

The owner agreed a direction toward separate colour, sound, letter and symbol
n-back modes. These are future feature agreements, not selectable F002 options.
Each will define stimulus identity/equality, generation/lures, representation,
accessibility, timing and any device/audio interruption rules. Combined streams
(dual n-back) need a separate agreement; sound alone does not imply dual n-back.

Keep rule/timing/scoring logic independent of Android drawing/audio and snapshot
level in session configuration. Extract only shared logic needed by F002; do not
build unused mode implementations, a plugin framework or a speculative history
schema. Additional modes get separate GitHub issues when brought into planning.

## Acceptance criteria and verification

These agreed criteria are implementation requirements,
not assertions that the feature exists.

| ID | Observable criterion | Planned verification |
| --- | --- | --- |
| AC-01 | Only 1/2/3 selectable; default 2; explanation/example/duration match selected n | Home Compose tests at each n, invalid engine configuration tests, content review |
| AC-02 | Remember only valid selected n; correct first-load, rapid-write and failure behavior | Settings adapter tests: missing/valid/invalid/corrupt/read-failed/save-failed/retry; delayed writes; process restart; inspect backup exclusions |
| AC-03 | Active level is immutable; Play again/Restart preserve it | Delayed preference emissions/writes during normal/practice play; rotation; restart; Home reselection tests |
| AC-04 | Every normal level has n warm-ups, 20 outcomes, exactly six matches and correct duration/scoring | Injected random generation across seeds at n=1/2/3; edge match sets; no-input/all-Match/perfect fixtures; preserve F001 worked example at n=2 |
| AC-05 | Timing, duplicates, warm-up and lifecycle semantics hold at every n | Fake clock before/at/after 1,000 ms, n×3,000 ms and each final deadline; held activation; timer jumps; rotation/background/lock/process loss; real complete sessions at n=1 and n=3 plus existing n=2 regression |
| AC-06 | Exact scripts produce four expected explanations with no early correctness | Fixtures above at each n; tap turns 1/2 produces hit/false alarm/miss/correct rejection; no-input and all-Match practice checks |
| AC-07 | Feedback barriers require explicit Next; delays and duplicate/stale events cannot skip examples | Fake clock across warm-up/response/feedback; exact deadline and held-press tests; duplicate Next; long waits at feedback |
| AC-08 | Practice completion/Skip/Back/restart/process-loss flows are distinct and discard transient state correctly | Compose and emulator navigation matrix; rotation in response and feedback; background during feedback; final-deadline completion precedence |
| AC-09 | Practice remains separate and optional; no automatic normal start, persistence or score contamination | Skip without practice; practice→normal→results; repeat script; fresh random normal session; inspect persisted fields |
| AC-10 | All new settings, feedback and controls meet presentation/accessibility requirements | Semantics/target checks; portrait/landscape and 200% screenshots; TalkBack selector, Retry, feedback and completion navigation |
| AC-11 | Future-mode direction does not add inactive UI or runtime scope | Source/dependency/permission review; engine remains Android-free; no new modes or network/history fields |

Run `./scripts/doctor.sh` and `./scripts/verify.sh`; use an explicit emulator with
`ANDROID_SERIAL=... ./scripts/emulator-test.sh` and rendered QA. Obtain independent
review of engine/settings/UI/test changes. Map each criterion to revision-specific
evidence; a skipped/missing check is not a pass. UI labels containing fixed 2-back
assumptions and regression tests must be updated without weakening F001 coverage.

## Dependencies and approval

F001 delivery [#3](https://github.com/lumensparkxy/nback-lab/issues/3) is complete.
The owner approved this detailed contract and
[ADR-003](../decisions/ADR-003-session-preferences.md) on 2026-09-21. F003 history is not a blocker
and its storage choice is not made here. Issue #4 remains the delivery authority;
a specification PR must not close it as implemented.

## Agreement record

On 2026-09-21 the owner approved the direction, then explicitly approved the full
contract and ADR-003 presented in [PR #15](https://github.com/lumensparkxy/nback-lab/pull/15).
Approval includes the 63/66/69-second durations, n warm-ups plus four scripted
practice examples with paused feedback, remembered-selection/failure semantics,
and settings storage. The owner authorized merging the specification and
implementing issue #4. Gameplay implementation still requires validation and
separate owner authorization to merge its delivery PR.

## Presentation amendment

[F005](F005-guided-design.md) revises setup, optional instructions, result detail
and history control presentation. The gameplay and persistence contracts above
remain unchanged.
