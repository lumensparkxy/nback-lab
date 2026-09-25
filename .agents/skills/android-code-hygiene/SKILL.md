---
name: android-code-hygiene
description: Audit unused Android code, resources and dependencies with inspectable evidence; use for cleanup requests or pre-release hygiene, and report candidates before removals.
---

# Android Code Hygiene

Read AGENTS.md and docs/android-readiness.md. Run scripts/code-hygiene.sh
and inspect release lint, R8 usage and the dependency report. Classify each
candidate by source reference, affected variants and confidence. Check manifest,
XML, generated code and reflection; R8 removal is not permission to delete source.
If unused-declaration IDE analysis is unavailable, say so rather than claiming a
complete dead-code scan. Propose small removals with affected test coverage.
Apply removals only within authorized scope and rerun lint/tests and optimized
smoke. Never delete files or exclude checks just to reduce reported counts.
