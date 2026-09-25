# F010 — Dedicated How to Play

Agreement: Agreed — owner approved the specification and implementation on 2026-09-25.
Tracking: [issue #35](https://github.com/lumensparkxy/nback-lab/issues/35).
See the [combined proposal and decisions](session-experience-plan.md).

## User outcome

Find clear instructions without crowding Home, understand the chosen task, and
try guided examples before starting a normal session.

## Scope and exclusions

Move the existing Home disclosure to a dedicated scrollable screen with examples
based on current settings and an explicit Guided practice action. Reuse the F002/
F004 practice contract; no mandatory onboarding, completion tracking, new modes,
timed reading, audio narration or practice scores/history.

## Behavior

Home has a labeled How to Play action. The screen has a title and Back; Android
Back returns Home. Reading never starts a timer. Rotation preserves scroll and
expanded explanations; process restart returns Home under existing lifecycle rules.

Present a short explanation first, followed by these sections:

1. **The idea:** “Compare this turn with the one n turns earlier.” Substitute
   the selected n. Explain that n counts turns, not seconds, and that all earlier
   turns count whether the user responded or not.
2. **What to match:** explain Position, Colour and Number independently, showing
   which are selected. Only selected types get response buttons during play.
   Number means the digit, not an arithmetic operation; Colour has no word cue.
3. **A worked example:** show n+1 ordered stimuli, explicitly label the first as
   the reference and the last as the current turn, and highlight the exact n-turn
   gap. Use the actual palette, grid and selected attributes. Annotate the expected
   action in text, not only colour. Every selected type's match/non-match is explained.
4. **How to answer:** tap each matching type once before the turn ends; wait for
   non-matching types. Two matching types require both buttons; no simultaneous
   finger presses are needed. Taps cannot be undone. Neutral “Recorded” feedback
   acknowledges a tap without revealing correctness during a normal session.
5. **Timing and warm-up:** first n turns are watch-only. Show selected normal
   length, estimated duration, time per turn, visible exposure and blank period.
   Explain the full-turn response window, the 1-second interval's lack of blank
   phase, and that foreground interruptions end the active run without saving it.
6. **Understanding results:** explain hits, misses, false alarms and correct
   rejections. Accuracy includes correct waits, so no responses still gives 70%.
   Distinguish the running within-session graph from final scores across comparable
   saved sessions. Practice and unfinished sessions are not saved.
7. **Try guided practice:** an explicit action starts the existing n warm-up plus
   four evaluated examples using selected n/types/pace. Explain that feedback waits
   for Next, so practice has no fixed total duration and ignores normal-session
   length. Practice completion retains Start session / Practice again / Home.

For a concrete 2-back Position + Colour + Number example, show a blue 7 at
top-left, an orange 2 at bottom-right, then a blue 4 at top-left. Position and
Colour match; Number does not. Expected actions: Position match and Colour match.
For 1-back remove the middle stimulus; for 3-back insert a second intermediate
stimulus before the last. This is an annotated teaching example, not a replacement
for the agreed deterministic practice scripts.

Instructions use current displayed settings, with a compact configuration label.
They do not edit settings; “Change settings” returns Home and opens Settings,
whose Back destination remains Home. A later Help visit reflects the latest setup.
Practice snapshots configuration on its explicit launch. Returning to Home from
practice follows the existing exit policy; opening Help is not a pause mechanism.

Keep a short visual-task notice on Home before Start, with a link to full Help.
Explain fixed response windows, colour-vision limitations and no equivalent
nonvisual gameplay claim. Do not require reading Help or completing practice to
play. Keep optional practice directly reachable from Home for returning users.

Use headings, readable paragraphs and labeled diagrams with equivalent text.
Allow scroll at 200% text and in landscape; targets are at least 48 dp. Static
teaching diagrams may expose descriptions to screen readers; live gameplay must
not automatically announce stimulus answers. No ad presentation on Help, Back,
practice launch or practice completion; privacy access remains on Home.

## Acceptance criteria and verification

| ID | Observable acceptance criterion | Verification |
| --- | --- | --- |
| AC-01 | Home opens dedicated Help in one action; full disclosure is removed; Back and reading never start a run | Navigation, rotation/scroll and no-clock-start tests; rendered Home/Help |
| AC-02 | Instructions and diagrams accurately reflect all supported n/types/pace/length configurations | Parameterized example validation, selected-mode copy checks and annotated screenshots |
| AC-03 | Independent responses, warm-up, timing and four outcomes are explained without score/improvement claims | Content review against F002/F004/F006/F008/F009; timing-band boundaries |
| AC-04 | Explicit practice entry snapshots settings and preserves existing scripts, feedback and completion actions | All n/modes, four-example count, nondefault length/pace, interruption and duplicate Next tests |
| AC-05 | Visual limitations appear before Start and full instructions are accessible | Home notice, Help text alternatives, 200%/landscape and TalkBack navigation inspection |
| AC-06 | Help adds no persisted practice data, automatic session or ad opportunity | Storage/route review, practice/history exclusion and F007 navigation tests |

## Dependencies and open questions

Depends on F002/F004/F005/F006 and agreed F008 navigation/configuration. Coordinate
results wording with F009. Moving full instructions to Help while keeping a short Home notice and direct
practice shortcut is agreed.

## Agreement record

2026-09-25: owner requested a dedicated How to Play screen. The content structure,
navigation and contextual examples above are proposed for agreement.

The owner approved the full recommended contract on 2026-09-25. Number 1–9 is
retained; Symbol is deferred. Merge and publishing require separate authorization.
