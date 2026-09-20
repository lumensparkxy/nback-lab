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

Engine tests run on the JVM; UI/lifecycle tests run on an emulator. The scaffold
only establishes the module boundary; gameplay contracts are still draft.
No application network permission or service integration is included.
