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
