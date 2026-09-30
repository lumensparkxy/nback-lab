# Live agent evaluations

Run the opt-in benchmark with the existing authenticated Codex CLI:

```bash
python3 scripts/agent_eval.py list
python3 scripts/agent_eval.py run --repeat 1 --timeout-seconds 300
```

Use `--case CASE_ID` for a focused run. Each repeat runs five scenarios in order:
unresolved product scope, stale review evidence, missing executed methods,
preserving unrelated owner work during a bounded fix, and interrupted handoff.
Definitions and the answer schema are in [agent-evals](agent-evals/scenarios.json).

Each case uses a new synthetic Git checkout and an ephemeral real `codex exec`
turn. The runner supplies the approved role instructions/model/effort/sandbox
directly; it does not test the custom-subagent loader. It ignores user config
without editing it, retains execpolicy rules and hook trust, reuses existing CLI
authentication, and copies no credentials or actual owner work into fixtures.
The fixtures isolate task files; they are not a container or a security audit of
all host access. No main-repository source or Android device is used by an agent.

Grading checks structured decisions, honest coverage/review claims, file hashes,
preservation of pre-existing dirty work, and independently evaluated numeric
outputs for the bounded fix using a size/numeric/intermediate-bounded interpreter.
It does not execute arbitrary generated Python in
the parent process. Git HEAD/index/config changes also fail the fixture's explicit
no-staging/commit rule. Interrupted runs save available results and stop their
owned CLI session; they do not proceed to further scenarios. If permissions
prevent cleanup verification, the available result is still saved as inconclusive
with an explicit cleanup error and the owned process id. Further scenarios stop;
denied access never establishes process absence.
A completed real turn and observed tool execution are required
for a pass. Failed CLI calls, missing output and timeouts are inconclusive, not
evidence that a role is worse. Offline grader regressions never count as live
model results. Do not change model assignments based on one small benchmark run.
The answer schema defines coverage as current passing evidence and review state
as the supplied certificate's validity. Both `blocked` and `needs_changes` reject
missing coverage. Version/hash changes distinguish clarified schemas from older
experiments; never silently rescore historical runs as if their instructions matched.

Results are atomically written under ignored `artifacts/agent-evals/run-*/` with
harness/role/dataset/CLI identity, per-case runtime, available JSONL token usage,
tool counts and changed fixture paths. Raw reasoning/transcripts/stderr are not
retained. Structured answers contain only the synthetic task material. Model,
effort and permission settings are recorded as requested; effective values remain
unavailable unless runtime metadata independently establishes them. Zero tokens
and missing usage are distinct; no monetary cost is inferred. A source change
during evaluation invalidates its stability claim. The CLI timeout terminates
only the owned evaluation session/process group.

These scenarios are a repeatable baseline for workflow behavior. They do not
establish general coding capability, Android correctness or release readiness.
Keep historical failures alongside subsequent runs and compare like-for-like
datasets/settings. CI runs offline grading tests; authenticated live runs remain
an explicit local command.

Source: [official non-interactive Codex documentation](https://learn.chatgpt.com/docs/non-interactive-mode).
