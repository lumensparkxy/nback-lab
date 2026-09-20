# Product scope

Status: agreed direction, 2026-09-20.

## Purpose

Build a simple offline visual n-back Android app while proving a reusable agentic
development harness. Product quality and a reproducible development process are
both first-class outcomes. No medical or general intelligence improvement claims
are part of the approved product.

## Agreed scope

- Visual position-based n-back, initially a small fixed-level session.
- Instructions and practice, then configurable n-back level.
- Session results and local session history.
- Offline operation: no account, backend, network analytics or cloud sync.
- Kotlin, Jetpack Compose and an independently testable pure Kotlin engine.

## Current implementation

The development harness and fixed visual 2-back session are implemented, including
instructions, timed play, interruption/restart and transient results. Configurable
practice and saved history are not implemented. GitHub issues track review and
delivery status.

## Not in the initial scope

Dual/audio n-back, subscriptions, advertisements, social features, leaderboards,
remote configuration, research claims, publishing and release signing.

## Agreed first session

[F001](features/F001-visual-session.md) defines the agreed first playable slice:
fixed 2-back on all nine cells of a 3×3 grid, two warm-up plus 20 scored trials,
a one-second highlight every three seconds, and exactly six scored matches.
One Match control records responses; results show accuracy and all four outcome
counts. Rotation preserves elapsed timing, interruptions cancel active sessions,
and process loss discards transient state. Gameplay is visual with fixed timing;
the spec defines contrast, text-size and accessible-control requirements.

Configurable difficulty/practice and persistent history remain draft specifications in
[F002](features/F002-practice-and-difficulty.md) and
[F003](features/F003-results-and-history.md).
