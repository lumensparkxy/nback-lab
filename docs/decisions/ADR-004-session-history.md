# ADR-004 — Store completed session history in Room

Status: Accepted — owner approved with F003 on 2026-09-21

Date: 2026-09-21

## Context

[F003](../features/F003-results-and-history.md) defines an offline collection of
immutable completed normal-session summaries. It needs unique IDs, filtered and
ordered reads, atomic clear, validation and preservation through app upgrades.
The chosen-level preference already has an independent store under
[ADR-003](ADR-003-session-preferences.md). This decision must not move gameplay
rules into Android or turn one history feature into a generic data framework.

## Decision

Use one application-scoped Room database in `app`, with one entity and a small
DAO/adapter. Persist session ID (unique primary key), completion UTC timestamp,
n, rules version and hits/misses/false alarms/correct rejections. Validate the
F003 invariants at the adapter boundary. Derive accuracy and fixed rule metadata
rather than storing a second copy. Do not persist sequences, taps or unfinished
sessions; no shared history/preferences schema or future-mode framework.

Use asynchronous database access off the main thread and an application-scoped
save/clear coordinator. It serializes mutations, owns immutable pending/failed
snapshots in memory, and supplies observable per-result and aggregate status to
presentation state. It continues accepted writes after navigation and enforces
F003's clear cutoff. The database uniqueness constraint and transactional
identical-payload duplicate check protect retries; the coordinator alone does not
satisfy idempotency. Clear invalidates old in-memory retries only after commit.
Process death can lose uncommitted snapshots; a persistent outbox/worker is outside
this decision and must not be implied by a “Saved” label.

Start at schema version 1 and track Room's exported schema. Later schema changes
require explicit preserving migrations and tests; no destructive fallback. Choose
a database driver/open path that reports corruption without automatic file
removal/recreation, and prove that behavior with a corrupted-store fixture.
Unreadable data leaves history unavailable while gameplay and settings work.
Keep all existing backup and device-transfer exclusions; no storage/network
permission is added. Retention/deletion are F003 product rules, not an implicit
consequence of the library.

Pin a compatible stable Room runtime/compiler and required KSP tooling in the
version catalog during implementation, after checking the current AGP/Kotlin/JDK
combination. This specification PR installs no dependency or plugin. Do not
upgrade unrelated libraries/toolchain components as a side effect; surface a
required incompatible toolchain change before expanding scope.

## Alternatives

- Preferences DataStore remains appropriate for selected n, but a growing result
  collection with record-level queries/uniqueness is a separate concern.
- A JSON file would need custom atomic replacement, validation, migration and
  record-query handling; it offers little advantage for the agreed operations.
- Direct SQLite avoids Room code generation but adds manual mapping/schema work.
- A backend, generic repository hierarchy or separate history module adds no
  necessary capability for this offline first slice.

## Consequences and verification

Room adds a focused Android runtime/code-generation dependency and a schema file.
The JVM engine remains unchanged by storage selection. Repository fakes test
save/clear ordering and UI status without arbitrary timing; actual database tests
prove duplicate handling, transactions, reopening, corrupt/invalid data behavior
and filtered ordering. Version 1 needs creation/reopen/upgrade-from-no-history
coverage, not a fabricated earlier database migration. Later migrations must be
covered before schema changes ship.

The owner approved this storage boundary and F003 in PR #17 on 2026-09-21.
Implementation requires its own verified PR and separate merge authorization.

Sources: [Room overview](https://developer.android.com/training/data-storage/room),
[Room migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions),
[DataStore guidance](https://developer.android.com/topic/libraries/architecture/datastore).

## Implementation notes

Room 2.8.5, KSP 2.3.12 and bundled SQLite 2.7.1 are pinned with the existing
AGP/Kotlin/JDK versions. Bundled SQLite avoids the Android framework's default
corruption handler, which can delete damaged databases. Existing stores undergo
read-only integrity, version, identity, column and raw SQLite type/value checks
before Room opens them. DAO transactions also validate stored values before Room
can coerce numeric types. UTC completion timestamps use civil years 1–9999,
matching the presentation boundary. Rejected fixture files are preserved; this
is not a forensic promise about every WAL/shared-memory sidecar byte.

The schema identity check matches the exported version-1 schema. A later migration
must extend the read-only preflight to admit its supported source versions before
Room runs the preserving migration; changing only the Room version is insufficient.

## F004 extension — 2026-09-21

Owner implementation approval extends the same Room entity to schema version 2.
Retain original count columns for Position. Add `modeMask` (default 1) and eight
Colour/Number count columns (default 0). Inactive types must have all-zero counts
and are never displayed as scores. Each active type satisfies the 6/14/20 rules.
Rules version 1 remains Position-only; new configurations use rules version 2.
An additive transactional migration preserves original rows, IDs and timestamps.
Preflight separately validates supported v1/v2 identities, columns/defaults and
raw SQLite values before Room opens. Preserve the v1 exported schema and add v2;
no destructive fallback or synthesized outcomes for inactive types. Save/retry/clear
remain one atomic session operation through the existing coordinator.

## F006 extension — 2026-09-21

Owner approval adds schema 3 and rules version 3 for new sessions: an integer
`intervalSeconds` and three TEXT outcome strings (Position, Colour, Number).
Each active type has exactly 20 chronological stable codes: H hit, M miss, F false
alarm, C correct rejection. Inactive types have empty strings. Derive chart times
and cumulative percentages in the engine from outcomes and configuration; no
raw taps, stimulus sequences or redundant percentages are stored.

Additive migration 2→3 preserves existing records with interval 3 and empty
timelines; rules 1/2 remain legacy summary records. Validate raw SQL types, byte
and character lengths, allowed codes and exact histogram agreement before reads,
save, clear and migration. Preserve v1/v2 preflight identities and schema exports.
New immutable snapshots include outcome strings in identical-retry comparisons.

## F008/F009 extension — 2026-09-25

Owner approval adds schema 4 with `sessionLength INTEGER NOT NULL DEFAULT 20`.
Migration 3→4 is additive; exported schemas and preflight identities 1–3 remain.
New completions use rules version 4 and length 10/20/30/50. Each active type has
exactly 30% targets, 70% non-targets and L chronological outcomes. Legacy rules
1–3 still require length 20 and their original counts/timeline contracts. Length
participates in identical-payload retry checks. Raw SQLite predicates validate
length, numeric types, count invariants and outcome bytes before reads/mutations
or migration. No destructive fallback or reconstruction of old timelines.

F009 derives comparisons from committed records, with groups keyed by exact
mode, n, pace and length. Rules 1–4 share the existing scoring meaning at length
20; future versions need explicit compatibility review. Sorting, filtering and
score-series preparation run off the UI thread. Charts use one Canvas and exact
session data uses lazy rows, retaining all records. No aggregate scores, raw taps,
network analytics or new persistence dependency is introduced.
