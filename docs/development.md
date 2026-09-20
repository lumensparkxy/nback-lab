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
