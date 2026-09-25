# F009 — Dedicated Results and progress across sessions

Agreement: Agreed — owner approved the specification and implementation on 2026-09-25.
Tracking: [issue #35](https://github.com/lumensparkxy/nback-lab/issues/35).
See the [combined proposal and decisions](session-experience-plan.md).

## User outcome

Understand a completed session, reopen previous sessions, and see how performance
varies across comparable sessions without implying that a harder or easier task
has the same meaning as the current one.

## Scope and exclusions

A Results destination with Sessions and Progress sections; shared immediate/saved
session detail; extend existing F006 charts to F008 lengths. Retain local history
and per-type scoring. No global performance score, cognitive/medical claim,
leaderboard, cloud sync, reaction-time metric, streaks or adaptive recommendations.

## Behavior

### Entry and navigation

- Home's Results link opens Sessions, containing the existing saved-history list.
  A labeled Progress selector switches to the cross-session view. Replace the old
  Home History link rather than add a second competing history destination.
- Completing normal play immediately opens “Session result”, including while a
  save is pending or failed. Actions are Play again, Results and Home. Results
  opens the Sessions list and retains this transient result as the Back destination.
- A saved row opens the same detail presentation using its stored snapshot. Back
  restores the previous section, filters and scroll position. Opening a saved row
  never regenerates gameplay, saves it again or uses current Settings metadata.
- Preserve existing saved-detail actions; Play again remains a current-result
  action, not a new replay action for arbitrary historical rules versions.
- Rotation retains the current route, filters and chart/table disclosure state.
  Process restart returns Home; committed history survives, transient results do
  not. Backgrounding a non-game screen is not a gameplay interruption.
- Settings and How to Play are reached via Home, not by editing a displayed result.
  Only explicit Home on the current completed normal result retains F007's possible
  ad opportunity. Section switches, Back, saved detail and Results → Home do not.

### One session

Show completion time where available, type set, n, scored length, warm-up count,
pace and total scheduled duration. Show separate type summaries: accuracy, correct
out of L, hits out of targets, misses, false alarms out of non-targets and correct
rejections. Keep concise cards and expandable explanations from F005, including
the visible 70% no-response caveat and honest save status/retry.

“Accuracy during this session” reuses F006's cumulative step graph, with elapsed
time on x and 0–100% accuracy on y. L scored points per active type, unscored
warm-up region, distinct line/marker styles, legend and equivalent data table.
The last point agrees with summary accuracy under the same display rounding.
No score is shown live during play. Older records without outcome order show the
existing timeline-unavailable explanation but retain their real summary.

### Sessions list

Keep all committed normal sessions until explicit global clear. Show newest first
using existing timestamp/ID ordering. Default filters to All; retain level/exact-mode
filters and add pace and scored length, each with an All option. Filter selections
combine with AND. Rows show date/time, actual configuration and per-type accuracy.
Never hide sessions merely because current Settings differ.

Keep global Clear history in a management menu with confirmation explicitly
covering all types, difficulties, paces and lengths, even when filtered. Preserve
F003's save/clear cutoff, retry, missing-detail and failure semantics; settings and
F007 counters survive. No new per-record deletion is proposed.

### Progress across saved sessions

At the top, show “Saved sessions” count across all committed history, with no
all-configuration average. Explain that this excludes practice, unfinished and
unsaved results; it is not an all-time counter after history is cleared.

Select a comparison group defined by exact type set, n, interval and scored
length. Use only compatible scoring rules: existing versions 1–3 and proposed
F008 rules are comparable at L=20 when those fields match, because their per-type
target proportions and score definitions are unchanged. New rule semantics need
an explicit compatibility decision; version number alone must not imply equivalence.

On first opening Progress, select the group of the most recently completed saved
session, with timestamp-descending/ID-ascending tie-break. Group choices come from
saved data and show readable configurations and counts. Keep the selection during
navigation/rotation. Do not silently switch groups when Settings changes or another
group gains a result. If a refresh removes the selected group, explain that it is
unavailable and offer the remaining groups. With no history, show the empty state.

For the selected group:

- Show group session count and latest per-type accuracy. No improvement badge,
  pass threshold or automatic claim based on a short series.
- Plot one final accuracy point per committed session per active type, covering
  all saved sessions in that group. X is “Sessions, oldest to newest”, ordered by
  recorded completion timestamp ascending then ID ascending; sessions are equally
  spaced. Label sparse dates and explain that spacing is session order, not elapsed
  time. Device clock changes can affect ordering, as in F003.
- Y is fixed 0–100%. Named per-type lines/markers connect recorded scores without
  smoothing, extrapolation or a synthetic zero start. Overlapping lines can be
  hidden individually for inspection without changing the saved configuration.
- Keep targets visible in an equivalent lazy data table: date/time, configuration,
  and each active type's accuracy, hit rate `100 × hits / targets`, and false-alarm
  rate `100 × false alarms / non-targets`. Use exact counts alongside rates. The
  graph's default and only metric in this slice is accuracy; the rates help explain it.
- A table row opens session detail; chart point selection is optional and cannot
  be the only way to inspect a result. No precision touch is required.
- A legacy summary can supply an authentic cross-session endpoint even when its
  within-session timeline is unavailable. Never fabricate that missing timeline.

Example at identical 2-back/Position/20 turns/3 seconds: session A has 4 hits,
2 misses, 3 false alarms and 11 correct rejections (75%); session B has 5 hits,
1 miss, 1 false alarm and 13 correct rejections (90%). Plot 75 and 90. A 3-back or
50-turn session belongs to another group and does not become a third point.

### States, performance and privacy

Loading, no history, no filter matches and load failure are distinct. Zero sessions
shows guidance to play a session; one selected-group session shows one point and
“Play another session with these settings to compare results.” Two or more show
the series without asserting a trend is statistically meaningful.

Pending/failed saves are visible with retry but excluded from list counts and
progress until commit; a successful retry contributes one point only. A clear
updates the graph/counts after commit, without resurrection by late callbacks.
Read failure hides stale comparisons and offers Retry; do not show it as zero
sessions or silently skip invalid rows. Follow F003 for removed records and failures.

Derive graphs from existing stored facts plus F008 length; no redundant scores,
cloud analytics or raw taps. Query/transform off the UI thread. Verify 10,000
records without one composed widget per plotted point or eagerly rendered table
row; do not silently truncate history to make the graph fast. Provide accessible
summaries, legends using more than colour, 48 dp actions and 200% text layouts.

## Acceptance criteria and verification

| ID | Observable acceptance criterion | Verification |
| --- | --- | --- |
| AC-01 | Results is reachable from Home/completion; sections, detail and Back preserve context | Navigation, rotation/process-loss and section/filter restoration tests |
| AC-02 | Immediate/saved detail agrees with original config and per-type counts at every supported length | Known streams, unsaved/saved/reopened fixtures, Settings changed after completion |
| AC-03 | Session graphs follow F006 at L points; old missing timelines remain honest | Perfect/no-input/mixed series, elapsed deadlines, no-timeline legacy fixtures |
| AC-04 | Progress includes all and only compatible committed sessions in selected group | Mixed n/mode/pace/length/rules fixtures; overlapping types are not exact-mode matches |
| AC-05 | Group selection, ordering and per-type values are deterministic; accessible table matches graph | Worked 75/90 example, equal timestamps, clock rollback, duplicate retries, one/many groups |
| AC-06 | Empty/loading/error/single-record/clear and removed-detail states remain truthful | Injected read/save/clear failures, pending-save cutoff and stale callback tests |
| AC-07 | All existing rows survive and 10,000-record views remain usable without truncation | Migration/reopen fixtures, large-history rendered scrolling and chart/table inspection |
| AC-08 | Graphs, controls and exact data work at large text, both orientations and with accessible navigation | Emulator screenshots, semantics and TalkBack inspection; coincident lines and long labels |
| AC-09 | New navigation cannot trigger ads or duplicate saves; only existing explicit current-result Home may qualify | F007 navigation/quota regression tests and save coordinator fixtures |

## Dependencies and open questions

Depends on F003/F004/F005/F006, F008 metadata and shared navigation, ADR-004 and
F007 route constraints. Results sections and exact comparison groups are agreed. Mixed-configuration combined scores are excluded from this draft.

## Agreement record

2026-09-25: owner requested a dedicated Results screen, session graph and progress
over played sessions. The navigation and comparison policy above are proposed.

The owner approved the full recommended contract on 2026-09-25. Number 1–9 is
retained; Symbol is deferred. Merge and publishing require separate authorization.
