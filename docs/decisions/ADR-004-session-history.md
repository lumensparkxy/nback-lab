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
