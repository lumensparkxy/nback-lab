# H002 — Harness evidence, validation and recovery

Agreement: owner approved all five stages in sequence on 2026-09-30.
Tracking: [issue #39](https://github.com/lumensparkxy/nback-lab/issues/39).
Baseline: [F000](F000-harness.md) and [ADR-001](../decisions/ADR-001-portable-agent-workflow.md).

## Outcome and boundaries

An agent can verify that its evidence still describes the current source, detect
configuration drift, check release coverage, measure its behavior on repeatable
tasks, and resume interrupted work from a concise checkpoint.

Implement in the order below, with one writer and independent review after each
stage. GitHub owns task status. Local records are evidence and resume hints;
they do not authorize implementation, merge, signing or publication. Application
behavior, approved role assignments and the routine critical CI policy stay as
agreed. Do not change global agent/tool configuration or bypass hook trust.

## Acceptance and verification

| ID | Observable outcome | Verification |
| --- | --- | --- |
| HU-01 | Evidence identifies committed and dirty/untracked source, verifies start/end stability, preserves and hashes accepted JUnit reports, and binds review reports to the reviewed source. Missing, changed or stale evidence cannot be certified as current. | Negative fixtures for source/report changes, missing methods and failed runs; actual explicitly selected emulator run; independent review. |
| HU-02 | Structural CI detects drift in approved role names, models, reasoning effort, sandbox requests and shared concurrency. Static checks do not claim runtime model loading or permissions. | Mutated configuration fixtures and current configuration validation. |
| HU-03 | A separate release preflight requires full Android evidence for the candidate source and verifies release build inputs and actual AAB/mapping identity. It reports gaps in optimized, signed and store-delivered proof. Routine CI remains critical. | Positive/negative candidate fixtures, real unsigned build plus full Android evidence; no signing/upload. |
| HU-04 | Isolated live-agent scenarios exercise stale review, missing prerequisites, scope/work preservation and interrupted handoff. Scores use observable outputs and filesystem evidence; runtime and available usage are recorded. Fixture regressions are distinguished from live results. | Deterministic grading regressions, real bounded Codex runs and inspection of their outputs. |
| HU-05 | Project hooks atomically save bounded checkpoints on interruption/compaction and reload concise resume context. They exclude transcript/private data, preserve earlier records and signal stale source. Hook discovery, callback tests and trusted runtime execution are reported separately. | Synthetic event regressions, hook discovery and fresh-session execution where trust permits. |

Evidence hashes detect mismatch and accidental alteration; they do not prove the
honesty of their producer. A recorded reviewer identity does not itself prove
independence. The lead checks the actual review and resolves its findings.

## Delivery

Run doctor, structural/Python checks and `verify.sh`. Run the deliberate failure
probe for changes to test/failure handling. Record exact revisions, commands,
counts, review disposition, live-versus-fixture checks and remaining limitations.
Prepare a linked review PR after the sequential stages. Merge remains a separate
owner decision.
