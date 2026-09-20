# Agent roles and delegation

The lead is the active main agent, not an extra mandatory subagent. It owns the
issue, plan, integration, verification coverage, review resolution and handoff.
It may implement directly. A multi-agent system is optional; the portable
workflow still applies when an agent tool has no subagent support.

| Role | Use | Access/expected output |
| --- | --- | --- |
| Planner | Ambiguous or cross-cutting work | Read-only; plan, dependencies, risks, unresolved decisions |
| Implementer | Bounded implementation | Workspace writes; focused changes and tests, evidence and limitations |
| Reviewer | Meaningful code/harness changes | Read-only; prioritized actionable findings with file/line evidence, or no findings plus residual risks |
| Verifier | Independent execution/Android QA | Build/test/device tools and artifact writes; commands, target, results, screenshots; no source edits |

## Dispatch contract

Supply: issue and agreed spec; objective; allowed paths/actions; constraints;
acceptance criteria; expected evidence/output; and stopping condition. A subagent
does not gain authority to merge, publish, alter scope or use unrelated services.

The lead explicitly requests review and waits before reporting readiness. Role
definitions do not trigger or enforce that sequence on their own. Never count a
review of an earlier version as a review of material fixes made afterward.

Default to one writer per working directory. Read-only research can run alongside
implementation when the task is independent. Review a stable working tree; do not
edit beneath an active reviewer. Start with at most two subagents. No recursive
delegation unless the lead explicitly assigns it.

If subagents are unavailable, disclose this and request an independent human or
separate-session review. Self-review does not meet the independent-review gate.

## Codex adapter

Project configuration is in [.codex/config.toml](../.codex/config.toml); role files
are in [.codex/agents](../.codex/agents). They inherit the user's selected model
and reasoning effort, so the project does not pin a provider-specific model.
Planner and reviewer request a read-only sandbox; implementer and verifier request
workspace-write. Verifier's no-source-edit rule is behavioral, not a filesystem
ACL. Runtime/user sandbox overrides can affect actual permissions; inspect the
active session and do not treat these files as security enforcement.

Use a trusted project session so Codex can load project settings. A local TOML
parse proves syntax only. To test loading, start a fresh Codex task in this repo
and ask it to name the available custom roles, then delegate a bounded read-only
inspection to `planner` and `reviewer`. Confirm their output and effective access.
Do not edit global trust or tool configuration automatically.

Source: [official Codex subagent documentation](https://learn.chatgpt.com/docs/agent-configuration/subagents).
