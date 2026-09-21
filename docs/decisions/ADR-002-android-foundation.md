# ADR-002 — Compose app and pure Kotlin engine

Status: Accepted foundation; implementation defaults noted below

Date: 2026-09-20

## Context

Visual n-back needs reproducible sequence/timing tests and Android lifecycle/UI
validation. The initial product is offline and does not need a backend.

## Decision

Use Kotlin and Jetpack Compose in `app`, with a separate pure Kotlin JVM `engine`
module. The app owns lifecycle, rendering and future persistence adapters; the
engine will own agreed session rules with injected time/randomness. This foundation
was approved in the blueprint. Do not add use-case layers or feature modules
without a demonstrated need.

Bootstrap defaults chosen within that scope: API 26 minimum, SDK 37 compile/target,
JDK 25 build runtime, JVM 17 bytecode and the pinned [toolchain](../toolchain.md).
Minimum OS support and the temporary `com.example.nback` application ID can change
before distribution. Storage technology remains undecided until F003 is ready.

## Alternatives

Putting the engine in Android UI classes would make deterministic testing harder.
A large modular framework would add setup without present product requirements.

## Consequences and verification

Engine tests run on the JVM; UI/lifecycle tests run on an emulator. [F001](../features/F001-visual-session.md) now defines the agreed fixed-session contract.
No application network permission or service integration is included.


## F001 implementation notes

The pure Kotlin engine accepts a monotonic clock and sequence factory, reconciles
elapsed trial boundaries, and exposes only current presentation state/results.
The Android ViewModel retains that engine through configuration recreation without
saved-state persistence, as required by F001 process-loss behavior. A main-thread
Handler wakes the engine at stimulus/trial boundaries; Compose only renders state
and forwards completed activations. Pausing stops callbacks immediately, with the
interruption decision made after the synchronous lifecycle transition to preserve
configuration recreation. A resumed activity refreshes from the original clock.
No new runtime dependencies or persistence layer are needed for this slice.

References: [ViewModel lifetime](https://developer.android.com/topic/libraries/architecture/viewmodel),
[Compose testing](https://developer.android.com/develop/ui/compose/testing/apis),
[accessibility verification](https://developer.android.com/develop/ui/compose/accessibility/testing).
