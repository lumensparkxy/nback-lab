# F003 — Results and local history

Agreement: Agreed — owner approved the full contract and implementation on 2026-09-21.

Delivery: [issue #5](https://github.com/lumensparkxy/nback-lab/issues/5).

## User outcome and boundaries

Understand a completed normal session and review earlier results on the same
device. Save completed visual position sessions at 1-, 2- and 3-back, expose
newest-first history with difficulty filters and details, and offer Clear history.
Practice and interrupted sessions never become history records.

No accounts, network, analytics, cloud sync, export, charts, cross-level averages,
rankings, streaks, adaptive recommendations, new training modes, reaction-time
metrics, per-session deletion or release/signing work. A difficulty filter is the
comparison tool in this delivery; it does not introduce a performance model.

[F001](F001-visual-session.md) and [F002](F002-practice-and-difficulty.md) continue
to govern gameplay, timing and scoring. When implemented, F003
replaces only their completed-normal-results persistence/display assumptions.
Active sessions, practice, navigation state and unsaved results remain transient;
process restart opens Home with the remembered difficulty and committed history.
Previously completed sessions cannot be reconstructed or imported retroactively.

## Results and score explanation

Use the same result summary on the immediate Results screen and a saved result's
detail screen. Show Position, selected n, completion date/time, accuracy, correct
responses out of 20, and all four counts with these meanings:

| Count | Meaning |
| --- | --- |
| Hits | Tapped Match when the positions matched |
| Misses | Waited when the positions matched |
| False alarms | Tapped Match when the positions differed |
| Correct rejections | Waited when the positions differed |

The engine remains the scoring authority: hits + misses = 6, false alarms +
correct rejections = 14, total = 20. Warm-up is excluded. Accuracy remains
`100 * (hits + correct rejections) / 20`, yielding exact multiples of five.
Do not replace accuracy with a new score or infer ability/health from it.

Display this explanation on immediate and saved results:
“Accuracy includes correctly waiting on non-matches. Not tapping at all gives
70% accuracy, so check hits and misses too.” Also show the match denominator
(e.g. “Hits: 0 of 6”) and non-match denominator (“False alarms: 0 of 14”).
No color-only judgement, pass mark, congratulatory threshold or cognitive claim.

Verify no-input, all-Match and perfect fixtures at every n. Preserve the original
F001 worked sequence unchanged at n=2; use separate valid n=1/3 sequences for
the same mixed-outcome totals:

| Responses | Hits | Misses | False alarms | Correct rejections | Accuracy |
| --- | --- | --- | --- | --- | --- |
| None | 0 | 6 | 0 | 14 | 70% |
| Match on every scored turn | 6 | 0 | 14 | 0 | 30% |
| Perfect | 6 | 0 | 0 | 14 | 100% |
| Mixed outcomes (original F001 sequence at n=2) | 4 | 2 | 3 | 11 | 75% |

## Completion and durable saving

- Assign one opaque random session ID per accepted normal-session start,
  including Restart and Play again. Ignore rejected/repeated Start events.
  Capture one immutable record when that run first reaches RESULTS, including
  completion reached while processing Match, Back or foreground loss at the final
  deadline. Snapshot before navigation/restart can replace engine state.
- Snapshot the level, all four counts, rules version 1 and device wall-clock
  completion time once. Inject the wall clock and ID generator for tests; do not
  replace or modify the engine's injected monotonic gameplay clock.
- Request an asynchronous save automatically. Never save from recomposition or
  treat every repeated RESULTS publication as a new result. Retries reuse the
  same ID and identical payload. Two different completed runs with identical
  counts are still two records. Storage must enforce ID uniqueness.
- A transaction commits the whole record or none of it. Report “Saving result…”,
  then “Saved to history” only after successful commit. An identical record
  already committed under that ID counts as success; a conflicting payload under
  the same ID is an error, never an overwrite.
- A failed save leaves the immediate result usable with “Couldn’t save this
  result.” and Retry. A save accepted before navigation continues in an
  application-scoped coordinator; leaving Results, rotation or backgrounding
  must not cancel it. Home and Play again remain usable while storage is slow
  or unavailable; storage never owns or delays the gameplay clock.
- Keep pending/failed record snapshots only in process memory. Home and History
  expose a summary when any saves are pending or failed, with “Retry saving” for
  failed records. Retry requeues each failed ID once, not successful/pending IDs.
  This makes a failure retryable even after leaving its Results screen. Show
  “Unsaved results may be lost if the app closes.” while failures/pending saves
  exist. Do not present unsaved records as saved history rows.
- No silent retry loop or background scheduler. Retain a failed snapshot for
  explicit Retry until it saves, Clear history invalidates it, or the process
  ends. A late completion updates only its own record/status; it cannot change
  a newer session's displayed result or save status.
- Durable boundary: after commit, a record survives process restart and ordinary
  app updates. Before commit, process termination may lose that result. A commit
  whose acknowledgement is lost must not duplicate on Retry. No promise of
  exactly-once delivery across process death; the guarantee is at most one stored
  row per session ID. No interrupted or practice record, journal or outbox is
  persisted to recover unfinished/unsaved work.

## History and navigation

- Add History on Home and on normal Results. Do not expose it during timed play,
  practice or interruption. Home remains usable even when history cannot load.
- History initially selects All. Offer All / 1-back / 2-back / 3-back as a labeled
  single-choice group. Filter by saved level, not the current Home difficulty.
  Preserve filter and scroll position across rotation and detail→History Back.
  Returning Home and later opening History starts a new visit with All selected.
  These navigation choices are not persisted across process death.
- A row shows completion date/time, Position, n and accuracy. Tapping opens its
  immutable detail with the complete summary/explanation. Show “Review sessions
  at the same difficulty; different levels are not directly comparable.” on
  History. No aggregate score across levels or automatic improvement claim.
- Sort by recorded completion timestamp descending, then session ID ascending
  as a deterministic tie-break. Retry never changes the timestamp or row position.
  “Newest” means device-recorded completion time, not save/retry time. Equal times
  remain distinct; manual clock changes can reorder sessions. Do not claim a
  reliable real-world chronology when the device clock is wrong.
- Store an absolute UTC timestamp; render in the device's current locale and time
  zone using its 12/24-hour preference. Include date and time in rows; details also
  show the UTC offset so repeated DST times are distinguishable. Time-zone changes
  affect presentation only, not ordering. Refresh formatting on returning to the
  foreground. Elapsed gameplay duration is still the engine's fixed 63/66/69 seconds.
- Distinguish Loading, populated, empty and failed states. Empty All says “No saved
  sessions yet”; empty level says “No saved 3-back sessions yet” (matching n).
  A failed read says history could not load and offers Retry; it must not masquerade
  as an empty database or block normal gameplay. Do not show stale rows as current
  after a read failure; disable opening details and Clear until a successful load.
- Detail Back returns to the filtered list; History Back returns to the Home or
  Results screen that opened it, retaining that transient result if applicable.
  A Home action deliberately returns Home. History/details/confirmation survive
  rotation; process restart always returns Home. Backgrounding these non-game
  screens does not create a gameplay interruption or additional save.
- A saved record removed by a successful clear while its detail is visible must
  show “This result is no longer in history” with Back/Home, not a fabricated
  result. A current immediate result may remain visible in memory after clearing,
  labeled “Removed from history”, without save/Retry actions for that old ID.

## Retention, deletion and privacy

Agreed retention: keep all committed sessions until the user clears history,
clears app data or uninstalls. No automatic age/count expiry in this delivery.
This avoids silently dropping older results. History uses a lazy scrolling list;
verify empty, single-record and 10,000-record fixtures without rendering every row.
No charts or paging framework is required solely to satisfy this fixture.

Clear history is available only from a successfully loaded History screen. It
covers all difficulty levels, including when the list is filtered, and also
forgets pending/failed results captured before confirmation. Enable it if either
saved history or in-memory pending/failed records exist; otherwise disable it.

Confirmation title: “Clear all history?”
Body: “Delete all saved sessions and discard unsaved results at every difficulty?
This cannot be undone. Your difficulty setting will stay the same.”
Actions: Cancel / Clear all history. Cancel and system Back dismiss with no writes.

Treat a confirmed clear as an ordered operation alongside saves:

- Define its cutoff as the set of completed records captured before confirmation.
  Serialize database mutations so any earlier accepted insert finishes before
  clear deletes committed rows. Disable duplicate clear and retries for those old
  snapshots while clearing; show “Clearing history…”. New completions captured
  after confirmation are queued after clear and remain eligible to save.
- After a successful transaction, invalidate all pre-cutoff pending/failed/retry
  handles and remove their banners. Stale callbacks, repeated refresh and late
  Retry must not resurrect them. Future sessions can save normally. Clear does
  not reset selected n or any active gameplay configuration.
- A failed clear must not claim success or erase in-memory retry snapshots. The
  database operation is atomic: retain existing committed records, report
  “Couldn’t clear history” with Retry/Cancel, and keep their statuses accurate.
  Retry asks for a fresh confirmation with a new cutoff. Do not reset/recreate the
  database to make deletion appear successful. A cancelled coroutine must not be
  treated as a completed operation.
- If the process ends during clear, the transaction leaves either the prior
  committed history or the cleared history; never partial deletion. In-memory
  unsaved snapshots are lost with the process independently of clear outcome.

Keep app-private storage and existing cloud-backup/device-transfer exclusions.
No new permissions, remote services, identity fields or analytics. Clear is
logical deletion from app history, not a claim of forensic secure erasure.
Do not expose session contents in application logs or public QA artifacts.

## Data and architecture

Accepted [ADR-004](../decisions/ADR-004-session-history.md) selects a small Room
store in `app`. One record contains only session ID, completion timestamp, n,
rules version 1 and the four outcome counts. Accuracy, denominators and duration
are derived from the agreed rules. Do not store raw grids, seeds, individual taps,
practice state, user identifiers, pre-emptive mode fields or configuration schemas.

The Android layer owns navigation, clocks/IDs for saved records, the coordinator
and database adapter. The pure Kotlin engine keeps rules/timing/scoring and stays
Android-free. Settings remain in their existing independent DataStore.

Validate records on write and read: n in 1..3, supported rules version, nonnegative
counts satisfying 6/14/20 invariants and a representable timestamp. Do not clamp
bad counts into plausible results or silently drop invalid rows. Invalid data,
corruption or incompatible schema produces an explicit unavailable-history error;
leave files intact and keep gameplay/settings usable. Normal Retry can reopen a
transiently unavailable store. No automatic destructive migration or corruption
reset; recovery requiring data loss is a separate owner-approved feature.

F002 upgrades start with an empty history store; they preserve selected n. Export
schema version 1 with implementation. Any later schema change requires a version
bump, an explicit data-preserving migration and migration tests. No speculative
version-2 migration or dummy migration test is required now. Verify reopening the
same schema and installing the F003 update over an F002 app with a saved level.

## Acceptance criteria and verification

| ID | Observable criterion | Verification |
| --- | --- | --- |
| AC-01 | Immediate and saved summaries preserve scoring and explain the 70% baseline | No-input/all-Match/perfect and level-valid mixed fixtures at n=1/2/3; original F001 sequence at n=2; shared presentation and emulator inspection |
| AC-02 | Each completed normal run yields at most one immutable record; practice/partial runs yield none | Repeated terminal refresh, recomposition, rotation, final-deadline Match/Back/pause, duplicate Retry; different runs with equal counts |
| AC-03 | Commit/status/error boundaries are honest and navigation cannot cancel accepted writes | Suspended/failing adapter; leave Results/start next run during write; late failure and Home/History Retry; unknown-ack duplicate |
| AC-04 | Committed history survives restart; transient work does not | Kill process before/after commit, reopen database/app; default Home; saved difficulty retained |
| AC-05 | History, filters, details and navigation follow the specified states/order | Empty/one/mixed-level/10,000 records; ties, rollback, time zones/DST/12–24-hour display; rotation/Back/Home/read failure |
| AC-06 | Retention has no automatic expiry; Clear is explicit, global and atomic | Cancel/confirm/filtered clear; settings unchanged; reopen empty store; injected delete failure |
| AC-07 | Clear cannot resurrect older pending/failed results or delete post-cutoff completions | Delayed insert→clear; failed save→clear→stale Retry; commit with lost ack; fresh completion after cutoff; clear failure and process interruption |
| AC-08 | Storage is minimal, local, validated and non-destructive on incompatible/corrupt data | Schema/dependency/permission/backup review; invalid-record fixtures; file preservation after failed open; F002 update with selected n |
| AC-09 | New UI remains usable and accessible | 48 dp targets, labeled filters/counts/statuses/confirmation, portrait/landscape/200% text; TalkBack navigation and feedback; loading/error states |
| AC-10 | Existing gameplay/settings contracts remain intact | Full F001/F002 engine, settings, lifecycle and input regressions; engine stays Android-free |

Implementation checks: `./scripts/doctor.sh`, `./scripts/verify.sh`, an explicit
emulator via `ANDROID_SERIAL=... ./scripts/emulator-test.sh`, rendered QA and
independent review. Changes to failure handling also run
`./scripts/test-failure-gate.sh`. Map evidence to the final implementation revision;
a specification PR cannot mark runtime criteria complete.

## Dependencies and agreement

F001 issue #3 and F002 issue #4 are merged. Their old dependency block is resolved.
On 2026-09-21 the owner approved the direction, then approved the full contract
and ADR-004 in [PR #17](https://github.com/lumensparkxy/nback-lab/pull/17).
Agreement includes retention without automatic expiry, the score explanation,
save/clear/failure semantics and Room storage. The owner authorized merging the
specification and implementing issue #5. The implementation requires validation
and separate owner authorization for its delivery merge. Issue #5 remains the
delivery owner and cannot close as shipped when this specification merges.

## Presentation amendment

[F005](F005-guided-design.md) revises setup, optional instructions, result detail
and history control presentation. The gameplay and persistence contracts above
remain unchanged.
