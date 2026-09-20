# Deliver the first complete visual n-back session

Type: feature. Priority: high. Blocked by S001 and a validated H001.

Specification: `docs/features/F001-visual-session.md`.

Implement the agreed fixed-level session as one vertical slice: pure Kotlin
engine, Compose interaction and basic result. Use deterministic time/sequence
tests and critical boundary cases. Validate a complete emulator session and
interruption scenarios. No unapproved difficulty, persistence or audio features.

Completion: all agreed F001 criteria, independent review, current CI and emulator
evidence, and an owner-approved PR. This is also the fresh-agent harness trial:
record instruction discovery gaps, rework and verification shortcomings.
