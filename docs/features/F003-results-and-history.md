# F003 — Results and local history

Agreement: Draft

## User outcome

Understand session performance and review previous sessions on the same device.

## Scope and exclusions

Results explanation and local persistence/history. No account, sync or analytics.

## Behavior

Persist a completed session at most once. The engine's agreed scoring semantics
must remain consistent with displayed results. Choose the storage implementation
when its data requirements are known; it is not selected by the harness scaffold.

## Acceptance criteria and verification

| ID | Draft criterion | Verification |
| --- | --- | --- |
| AC-01 | Displayed results agree with engine outcomes | Mapping/format tests and emulator flow |
| AC-02 | A completed session remains available after app restart without duplicates | Persistence integration and restart tests |
| AC-03 | History remains usable with no sessions and at the agreed retention limit | Empty/boundary scenarios |

## Dependencies

[F001](F001-visual-session.md); persistence decision before implementation.

## Open questions

Result metrics, fields, ordering, retention, deletion, backup/restore policy,
abandoned/practice sessions, migration strategy and time-zone display.
