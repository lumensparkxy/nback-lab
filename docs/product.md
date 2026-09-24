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

The development harness and F001–F004 are implemented: selectable Position,
Colour and Number in all seven combinations, manual 1-/2-/3-back, Home settings,
guided practice and completed-session history with independent per-type results.
The selected difficulty/types and committed summaries are stored locally. Practice,
unfinished sessions and unsaved results remain transient. GitHub issues track
review and delivery status.

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

Configurable difficulty/practice is agreed in [F002](features/F002-practice-and-difficulty.md).
Persistent history is agreed in [F003](features/F003-results-and-history.md).

## Agreed next direction

On 2026-09-21 the owner approved the direction of Home session settings, manual
1-/2-/3-back selection (default 2), remembering the chosen level, and optional
repeatable guided practice. Fixed normal timing and neutral feedback remain.
The owner subsequently approved the detailed F002 contract and ADR-003 settings
storage decision on the same date. Implementation and revision-specific evidence are tracked by issue #4.

On 2026-09-21 the owner approved including combinations immediately in the next
stimulus-type feature, with users independently toggling which types are active.
[F004](features/F004-stimulus-types-and-responses.md) defines Position, Colour and
Number selection, independent responses, practice and per-type results/history.
The owner subsequently approved implementation: rules, settings, Number and
independent responses/results/history are agreed. The owner selected colour-only stimuli, with the Number digit inside the tile
when active and no colour-name cue during play. Sound
remains a later feature. Delivery is tracked in issues #23 and #24.
These additions remain outside the delivered initial visual-position scope.
Do not add unavailable-mode controls to the current app.

On 2026-09-21 the owner selected Guided Play (design 3) for the complete app.
[F005](features/F005-guided-design.md) defines this presentation revision.

On 2026-09-21 the owner approved [F006](features/F006-session-interval-and-accuracy-timeline.md):
a remembered 1–30 second pace, exposure bands of 1/2/3 seconds, and per-type
cumulative accuracy timelines for new completed sessions and their saved details.
This supersedes fixed timing and summary-only retention where explicitly stated.

## F007 monetization extension — 2026-09-24

The owner approved [F007](features/F007-ad-supported-monetization.md) for worldwide
13+ use: occasional AdMob interstitials after completed Results → Home, with
local frequency limits and conservative non-personalized age treatment. Core
exercises/history remain offline-capable. This supersedes the initial exclusion
of advertisements and network services only for this feature; no account, billing,
analytics or backend is added. Production enablement and publishing remain separate.
