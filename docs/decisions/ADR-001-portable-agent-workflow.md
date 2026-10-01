# ADR-001 — Portable workflow with Codex adapter

Status: Accepted

Date: 2026-09-20

## Context

The owner prioritizes a reusable Android development harness with autonomy inside
agreed scope. Project knowledge must survive conversations and agent changes.

## Decision

Keep behavior in feature specs, technical rationale in decision records, and task
status in GitHub issues. Share Markdown instructions and executable commands
across agent tools. Add Codex role files as an adapter, inheriting model choice.
Use one writer and independent review for meaningful changes. Owner controls
scope changes, major architectural changes, merging and publishing initially.
Agreed in the blueprint conversation on this date.

## Alternatives

Conversation-only knowledge is hard to audit/handoff. Provider-only instructions
reduce reuse. Multiple writers in one checkout add avoidable coordination risk.

## Consequences and verification

Some checks can be automated; role behavior and owner judgment remain review
responsibilities. CI files alone do not enforce branch protection. Validate the
harness through a real feature and record local versus hosted evidence separately.


## Amendment — explicit role models, 2026-09-26

The owner approved explicit model and reasoning assignments for the four Codex
roles, replacing the original model-inheritance choice for those roles only.
Planner and implementer use `gpt-6-sol` with `high` effort; reviewer uses
`gpt-6-astra` with `high`; verifier uses `gpt-6-sol` with `medium`.
Assignments and runtime validation are documented in [agent roles](../agents.md).
Portable scope, evidence and approval rules remain in shared Markdown; other
agent tools can follow the same workflow without these model identifiers.
Permissions and tool defaults remain inherited except for the existing role
sandbox settings. No change to product architecture or release authorization.

## Amendment — evidence and recovery adapter, 2026-09-30

The owner approved the five-stage [H002 extension](../features/H002-harness-reliability.md):
source-bound evidence, approved role drift checks, full candidate release
preflight, isolated live evaluations and project-local lifecycle checkpoints.
Shared Python/Markdown commands remain the portable workflow. Optional Codex
hooks reload bounded curated progress rather than retaining private transcripts.
They are advisory and require native trust of their exact definitions; they do
not enforce scope, grant approval, certify tests or replace GitHub task status.
No global configuration, role assignments, application behavior, routine critical
CI coverage or release/merge authorization is changed by this extension.

## Amendment — Sol role upgrade, 2026-10-01

The owner approved moving planner and implementer to `gpt-6.1-sol` with `xhigh`
effort and verifier to `gpt-6.1-sol` with `high`, increasing each role's effort by
one level. Reviewer retains `gpt-6-astra` with `high`. This supersedes the
2026-09-26 model assignments for those three roles only.

The role files, [approved contract](../agent-role-contract.json) and
[agent documentation](../agents.md) define the same assignments. Use a compatible
Codex runtime and verify actual role loading in a fresh session. Model access and
simple throughput checks do not establish general coding or review quality;
keep workflow evaluations and runtime evidence separate from static validation.
The change preserves role instructions, sandbox settings, concurrency, global
configuration, product behavior and owner merge/release approval boundaries.
