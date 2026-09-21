# ADR-003 — Persist the selected level separately from session history

Status: Accepted — owner approved with F002 on 2026-09-21

Date: 2026-09-21

## Context

[F002](../features/F002-practice-and-difficulty.md) adds one remembered setting,
selected n in 1..3. Gameplay and practice remain transient. F003 history has
unresolved data/storage requirements; choosing a database for it now would add
scope without a settled need. ADR-002's deferred storage decision concerned that
history; this decision makes a narrow exception for settings only.

## Decision

Use one application-scoped Preferences DataStore instance in the Android `app`
module, behind a small settings adapter. Persist only integer `selected_n`;
default to 2 and validate at the adapter and engine configuration boundaries.
Serialize preference writes and expose explicit loading/saving/error states as
specified in F002. Do not perform storage I/O in Compose or in the pure Kotlin
engine. Snapshot the selected level when creating a session.

`androidx.datastore:datastore-preferences` is pinned to 1.2.1 in the version
catalog for this implementation, with compatibility checked through the standard
build and test workflow. A small preference does not require a new
module, generic repository framework, service locator or database schema.

Keep the app's existing backup/device-transfer exclusions. Do not store sequences,
responses, practice completion, results, identifiers or analytics. F003 still
needs its own storage and retention decision; this ADR does not choose its store.

## Alternatives

- Memory only: does not meet the approved remembered-selection direction.
- SharedPreferences: avoids a dependency, but DataStore provides an asynchronous,
  transactional settings API and observable changes suited to the required state.
- A database or typed multi-mode settings schema: unnecessary for one integer and
  would pre-empt unresolved history/mode requirements.

## Consequences and verification

One focused Android dependency and adapter are added during implementation;
engine timing/scoring remain independently testable. Use isolated temporary
stores for round-trip, invalid-value, corruption and write-order tests, and an
adapter fake for injected I/O failure/retry behavior. Verify process restart and
that saved-level updates cannot alter an active session. Settings loss resets to
2 rather than reconstructing gameplay.

Owner agreement is recorded in issue #4 and PR #15; implementation is authorized.

Source: [Android DataStore documentation](https://developer.android.com/topic/libraries/architecture/datastore).

## F004 extension — 2026-09-21

Owner implementation approval extends the same settings store with integer
`selected_types`: Position=1, Colour=2, Number=4, valid nonempty masks 1–7.
Missing/invalid masks default to Position without resetting a valid n; report
invalid masks visibly. Save level and mask together in one ordered DataStore
edit. Session snapshots are immutable. No new library or persistence layer.

## F006 extension — 2026-09-21

Owner approval adds integer `interval_seconds`, default 3 and range 1–30. Save it
with n and mode in the existing ordered transaction. Missing values default
silently; invalid values reset only this field with notice. Commit slider changes
on interaction completion, avoiding a disk write for every drag position.
