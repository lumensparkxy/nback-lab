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

Only the app shell and development harness are implemented. Feature agreement is
separate from implementation status; use GitHub issues for delivery tracking.

## Not in the initial scope

Dual/audio n-back, subscriptions, advertisements, social features, leaderboards,
remote configuration, research claims, publishing and release signing.

## Decisions still needed before gameplay

Grid layout, trial count, stimulus/response timing, match distribution, scoring,
warm-up behavior, duplicate input handling, pause/background/process-death behavior,
supported difficulty range and accessibility behavior for the spatial task.
See [F001](features/F001-visual-session.md); these are deliberately not invented
by the scaffold.
