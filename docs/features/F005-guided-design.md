# F005 — Guided Play visual design

Status: Agreed, 2026-09-21. The owner selected design 3 from the app-wide design review.

## Outcome and scope

Apply the selected warm off-white, navy and apricot direction to the existing
native Compose app. Home presents a short two-step session setup; practice, play,
results and history share typography, colour roles and control styling.
This builds on F004 (PR #25); delivery must follow that dependency.
No engine, timing, persistence schema, scoring, permissions or mode changes.
No audio, achievements, automatic difficulty or improvement claims.

## Acceptance criteria

- **D01 Home:** n-back identity and History link, title “Set up your next round”,
  three equal selectable type cards, horizontal 1-/2-/3-back controls, accurate
  active-type/level example, duration, primary Start session and secondary guided
  practice. Normal portrait setup and Start fit without scrolling at the reference
  390 × 844 dp viewport; narrow, landscape and 200% text remain scrollable and usable.
- **D02 Selection:** all seven nonempty combinations remain available and remembered.
  Last active type stays visibly selected even though deselection is disabled.
  Preserve loading and failure notices and retry actions.
- **D03 Help:** a collapsed inline How to play disclosure holds full rules, colour
  palette, timing/access requirements and practice explanation. The Home example
  shows every selected attribute and repeats after exactly n turns. This explicitly
  amends F002/F004 instruction presentation; their rules remain unchanged.
  Disclosure state survives rotation but is not persisted across process loss.
- **D04 Play/practice:** keep stable board, one centered response row, independent
  response states and existing accessible labels. Group feedback per type in quiet
  cards. Preserve all lifecycle and practice barriers.
- **D05 Results:** concise per-type overview shows accuracy, hits, misses and false
  alarms; the 70% no-response caveat is outside collapsed details. Full breakdowns
  expose all four counts, denominators and neutral definitions. Details start
  collapsed per displayed result and survive rotation. Save status remains honest;
  Play again is primary and History/Home secondary. Saved detail uses the same summary.
- **D06 History:** compact difficulty and exact-mode filters precede saved records.
  Clear history moves into a labeled management menu with existing confirmation and
  failure recovery. Back/Home remain accessible; filters, lazy loading and scroll
  restoration retain existing semantics. No cross-mode aggregate score.
- **D07 Quality:** consistent complete light theme, standard sourced icons, no fake
  unavailable controls. Interactive targets >=48 dp, clear selected/disabled states,
  accessible labels and no clipped/overlapping controls at 200% text or landscape.

## Verification

Existing engine/storage/settings/lifecycle regression suites, focused Compose
layout/interaction tests, emulator screenshots compared with selected design,
local verify script and independent source/visual review. Record exact revision
and deviations required for accurate gameplay examples and native accessibility.

## F008–F010 amendment — 2026-09-25

Owner-approved [F008](F008-settings-and-session-length.md) generalizes normal
length and count/timing denominators and moves controls into Settings.
[F009](F009-results-and-progress.md) defines the Results destination and comparable
cross-session progress. [F010](F010-how-to-play.md) moves full instructions to a
dedicated screen. These supersede only the explicitly revised behavior; preserve
all remaining scoring, practice, lifecycle, storage and accessibility contracts.
