# Development workflow

## Sources of truth

| Question | Authority |
| --- | --- |
| What product are we building? | [Product scope](product.md) |
| What should a feature do? | Agreed file in [features](features/README.md) |
| Why this architecture? | Accepted [decision records](decisions/README.md) |
| What is next, blocked, assigned or shipped? | GitHub issues and their linked PRs |
| How do humans and agents work? | This document, [AGENTS.md](../AGENTS.md), [agent roles](agents.md) |
| What ran successfully? | Revision-specific test reports, CI runs and review evidence |

Keep status in GitHub rather than maintaining a second roadmap status board.
The feature index groups specifications; it does not duplicate task status.
If an issue changes agreed behavior, amend the spec with owner agreement first.

## Definition of ready

- Issue states the outcome, scope, exclusions and links the relevant specification.
- Acceptance criteria have stable IDs and observable outcomes.
- Dependencies/blockers are resolved, and product questions are answered.
- Verification approach is practical for the available environment.
- Owner has agreed the scope. A label alone does not establish authorization.

Investigation/specification tasks may be ready without gameplay decisions; their
output is a proposal, not permission to implement it.

## Maintenance

A small, bounded dependency, build-tool or documentation maintenance PR may be
its own tracking unit. Record the outcome, allowed changes, compatibility evidence,
validation and remaining decisions in the PR. Separate issue/specification
placeholders are unnecessary for these changes. Features, unresolved behavior and
larger independently scheduled work still require a ready issue and agreed criteria.
Owner approval in the current task can establish the maintenance scope; record its
scope in the PR without publishing private conversation content.

Maintenance retains independent review for meaningful code/harness changes,
current-revision checks and owner merge authorization. A dependency bot is a source
of proposals, not approval. Review upstream compatibility and licensing changes;
new license terms, major architecture and scope decisions require explicit owner
agreement. Record a deferred PR's reason and revisit condition in its metadata;
do not silently accept it, suppress updates or change bot settings. Related approved
updates may be handled together only with explicit scope and combined verification.

## Delivery loop

1. Inspect issue, specs, decisions, branch and working tree. Declare assumptions.
2. Write a brief plan proportional to complexity. Delegate bounded work only when
   useful, following [agent roles](agents.md).
3. Implement on an issue branch with one writer per checkout. Write tests from
   acceptance criteria. Reproduce bugs before fixing them.
4. Run `./scripts/verify.sh`. For UI changes also run emulator tests and inspect
   screenshots. Record target, commands, revision and results.
5. Obtain independent review of meaningful code/harness changes against the spec,
   not just a summary from the implementer. Fix findings, rerun affected checks,
   and rereview material changes.
6. Prepare a PR linking the issue with an acceptance/evidence table. Owner reviews
   and authorizes merge. Only merged, accepted delivery closes the implementation issue.
7. Complete delivery closeout below; personal skills may assist but are not required.

## Delivery closeout

The lead owns these steps after the owner authorizes the concrete merge. Existing
authorization remains valid for routine, safe cleanup of that completed task unless
the owner requests retention. A readiness review alone does not authorize merging.

1. Refresh the exact PR head/base, changed files, review evidence, unresolved
   conversations, required checks and repository rules. Match evidence to that
   revision. Merge using an allowed strategy with a server-enforced expected-head
   guard. A changed head needs revalidation; never bypass protection. For multiple
   PRs, follow dependencies and refresh each remaining PR after the previous merge.
2. Read back the merged state, commit and target. Reconcile linked issues only
   when their whole agreed scope is delivered; distinguish queued from merged.
   Update authorized issue/PR metadata without sending unsolicited comments.
3. Fetch the scoped remote and fast-forward a clean base checkout. Preserve dirty
   files and local commits; do not stash, reset or rebase someone else's work.
4. Record a small durable evidence summary: tested/reviewed revision, commands,
   results and counts, reviewer findings/disposition, CI run links and limitations.
   Preserve unique screenshots/reports/APKs outside any worktree being removed,
   verify copies, and record archive identifiers/checksums. Do not commit private
   logs, signing data or machine-specific paths. CI artifacts have finite retention;
   their links alone are not permanent evidence. Avoid rewriting historical records;
   link later verification when it supersedes an earlier result.
5. Remove only this completed task's temporary branch/worktree. Check tracked,
   untracked and ignored content, worktree locks, agent/task ownership, editor/build
   use and dependent PRs. Never remove the current working directory, primary
   checkout, permanent/shared branch, active work, new commits or uncertain data.
   Retain such items and explain why. Do not stop processes to make removal possible.
6. Prove delivery before deleting: verify exact PR/local head and merge reachability
   from the fetched base. For squash/rebase, identical PR-head and merge Git trees
   are sufficient; otherwise establish equivalent delivered content or retain it.
   Use ordinary worktree removal from a retained directory, then safe branch deletion.
   Never force removal. If deletion fails only because squash changed ancestry,
   a verified recovery bundle containing that exact head plus expected-old-SHA
   guarded ref deletion is permitted. Record the bundle identifier. Prune stale
   remote tracking refs; repository-configured remote deletion is separate from
   local cleanup. Do not delete other remote branches as part of this step.
7. Report merged/queued/blocked PRs, issue outcomes, checks, local base state,
   cleanup, recovery location and retained work with reasons. Do not start another
   feature merely because cleanup succeeded.

Required pre-merge checks must pass before merging. This repository also runs CI
on `main`, but does not make that redundant post-merge run a cleanup prerequisite
when the merge content is verified identical to the tested PR. If a task/repository
rule explicitly requires post-merge checks, wait for those before cleanup. Always
report an unfinished post-merge run as pending with its run link and revision;
do not imply background monitoring after the task ends. The owner receives that
handoff unless an agent/automation has explicitly accepted follow-up. On subsequent
project work, check pending closeout runs and record meaningful failures or completion.

Keep this contract tool-neutral. Optional provider configuration or personal merge
skills may implement it; a fresh clone must be sufficient to discover the rules.

## CI coverage and speed

Pull requests and pushes to `main` run all harness/JVM tests, lint, debug builds,
the failure-gate probe, and a 20-test critical Android selection. The required
`gate` still requires both `quality` and `android-ui` to succeed. Selection lives
in `scripts/ci_android_tests.py`; CI verifies every selected test actually passed
using fresh JUnit reports, so an empty or partial run cannot produce a green gate.

The selection protects launch/warm-up, a real timed session and results,
independent responses, practice, rotation/interruption, preferences, completed
session persistence, clear ordering, database corruption and both history migrations.
All engine tests remain because they cheaply cover scoring/timing across levels
and all seven modes. The remaining Android tests stay in the repository; broader
layout/accessibility combinations, repeated timed sessions and additional error
cases are deferred from routine CI. This accepts later detection for regressions
covered only by those tests.

Run the complete suite with **Actions → CI → Run workflow → android-suite: full**
on the intended branch, or locally with `ANDROID_SERIAL=emulator-... ./scripts/emulator-test.sh`.
A full run is required before a release. Feature PRs must still run relevant
acceptance tests locally, including tests outside the critical selection; the fast
CI selection does not replace feature-specific evidence. Review the selection
when adding features, especially persistence, migrations or privacy behavior.

To reproduce the fast selection locally:
`ANDROID_SERIAL=emulator-... python3 scripts/ci_android_tests.py critical`.
`./scripts/verify.sh` remains the complete non-device check. No nightly job is
created. Optimized release builds are a separate cost and are not changed here.

## Definition of done

- Agreed acceptance criteria are satisfied, with traceable evidence.
- Required CI checks pass for the revision being merged.
- Relevant emulator checks and visual inspection are complete for UI changes.
- Review findings are fixed or explicitly dispositioned by the owner.
- Specs/decisions/setup instructions reflect the delivered behavior.
- No secrets, unrelated edits, silent skips or unexplained weakened checks.
- Remaining limitations and any manual release checks are explicit.

A PR may be marked blocked/incomplete when required checks cannot run. "Not run"
does not satisfy done. Successful local checks are not a hosted CI run, and a CI
workflow file is not a protected-branch rule. See [GitHub setup](github-setup.md).

## Handoff and learning

When interrupted, record issue/spec links, branch and revision, changes made,
commands/results, review findings, remaining steps and concrete blockers in the
issue/PR. Keep large generated artifacts out of Git; link to CI artifacts.
Before GitHub exists, use [bootstrap validation](validation/bootstrap.md).

After repeated failures, diagnose before retrying. Add a regression test when
possible; change guidance only for a demonstrated reusable lesson. Review changes
to agent instructions like code. Do not accumulate contradictory rules or copy
private conversation details into project documentation.
