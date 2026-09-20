# GitHub activation

Chosen repository name: `nback-lab`. Owner: `lumensparkxy`. Visibility: public,
explicitly selected in the setup conversation. No license has been granted yet;
choose one deliberately before presenting the project as an open-source template.

## Activation sequence

1. Review local files and evidence; create the empty repository without an auto README.
2. Publish the reviewed bootstrap as the initial `main` commit. This initialization
   is authorized by the owner; subsequent changes use issue branches and PRs.
3. Create initial issues from [the drafts](backlog/README.md), replace draft dependency
   references with actual links, and apply ready/blocked/in-review labels.
4. Observe the real CI run. Correct failures; do not substitute local results.
5. Protect `main`: require pull requests and the `gate` check, require an up-to-date
   branch, resolve conversations, block force pushes/deletion, and apply rules to
   administrators. Disable automatic merges initially.
6. For a sole-maintainer repository, require zero formal approving reviews to avoid
   a self-approval deadlock. Independent agent review evidence and owner merge
   authorization remain workflow requirements; GitHub does not verify them.
   Increase required human approvals when another maintainer is available.
7. Read back settings and record the results in [bootstrap validation](validation/bootstrap.md).

## Suggested issue labels

`status:ready`, `status:blocked`, `status:in-review`, `type:harness`, `type:feature`,
`type:spec`, `priority:high`, `priority:normal`. Closed issues represent accepted
delivery; do not use a separate done label or duplicate status in a roadmap file.

## Enforcement boundaries

The workflow has no path filters: the final `gate` job fails if either dependency
fails, is cancelled or is skipped. Workflow token permissions are read-only and
external actions are pinned to commit IDs. Dependabot proposes updates for review.
There is no release workflow or signing credential.

Labels, templates, CODEOWNERS and AGENTS.md guide work. The branch rule enforces
PR/check requirements only after it is actually configured. Fresh-agent role
loading and an end-to-end feature PR are separate acceptance exercises.
