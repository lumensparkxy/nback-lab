# Local handoff checkpoints

The optional Codex adapter in [.codex/hooks.json](../.codex/hooks.json) saves an
advisory checkpoint on `PreCompact`, `Interrupt` and `Stop`. `SessionStart`
(startup, resume or compact) reloads the same session's concise context and saves
a new record. These hooks use the portable [checkpoint command](../scripts/harness_checkpoint.py);
other tools can record a note with the same command without lifecycle hooks.

Before a long validation run and at meaningful handoff boundaries, the lead
records bounded public workflow facts. Use the native session id shown by the
SessionStart hook; `SESSION_ID` below is a placeholder, not another chat's id:

```bash
python3 scripts/harness_checkpoint.py note --session-id SESSION_ID \
  --issue https://github.com/lumensparkxy/nback-lab/issues/39 \
  --last-step 'Current verification completed' --last-result passed \
  --next-action 'Obtain independent review of the current revision'
```

An optional `--pr` accepts a public PR link in this repository. Result values are
`passed`, `failed`, `not_run` and `unavailable`; describe only what was observed.
The hook automatically captures source identity and the lifecycle event. It
preserves the latest curated note; it does not infer progress from a transcript.
Without a note, the resume hint explicitly asks the agent to inspect the issue
and current evidence. A note or checkpoint never establishes scope, approval,
passing coverage or delivery. GitHub remains the authority for task status.

Records live under ignored `artifacts/handoffs/<session-hash>/`. Each save adds
an immutable uniquely named record and atomically replaces a hashed latest
pointer. Per-session locks bound contention; earlier records are preserved.
Source identity includes current tracked and nonignored untracked contents.
Changed/unavailable source makes earlier progress stale, even when later hooks
capture a new source identity. Matching hashes alone do not prove passing tests.
Different sessions never automatically consume each other's notes.

Only event/session identity, a hash of the optional turn id, source identity and
the explicit public progress fields are retained. Transcript paths, prompts,
assistant messages, tool arguments, raw logs and environment values are ignored.
Do not put credentials, private user data or machine paths into notes. Input and
record sizes are bounded; the command rejects control characters, obvious
credential assignments and home paths, but is not a general privacy classifier.
Malformed/oversized payloads and filesystem failures report advisory failure,
without claiming a checkpoint was saved. An unavailable source probe still saves
the bounded checkpoint with an explicit unavailable marker.

Source probes have a one-second budget for Interrupt and 1.5 seconds for other
events. Lock acquisition has a 0.25-second limit. Native hook timeouts are three
seconds for Interrupt and five seconds otherwise. These command hooks are
synchronous and return JSON; they never block continuation, override permission
decisions, restart an interrupted turn or ask Stop to create a continuation loop.
The scripts target this repository's macOS/Linux Python/Git workflow. Native
timeouts remain the outer limit for slow filesystem operations.

## Discovery, review and activation

Codex loads project hooks only from a trusted project configuration layer. It
also requires review and trust of each exact non-managed hook definition. Use
`/hooks` in the CLI to inspect these four project definitions and trust them.
Changed definitions require review again. Start/resume a fresh session afterward
and verify a new record plus model-visible resume context.

Static JSON checks, synthetic callback tests, native `hooks/list` discovery and
trusted lifecycle execution are different evidence. A discovered untrusted hook
is skipped and is not activated. Do not edit global trust/tool configuration or
bypass hook trust automatically. Other user/plugin hooks may also run; this
repository's structural contract certifies only its project definitions.

Source: [official Codex hooks documentation](https://learn.chatgpt.com/docs/hooks).
