# Contributing

1. Read [the development workflow](docs/development.md) and [product scope](docs/product.md).
2. Pick a ready GitHub issue with an agreed specification and resolved blockers.
3. Inspect the working tree; create `codex/<issue-number>-<short-description>` for
   agent work. Use a separate worktree for genuinely independent simultaneous work.
4. Make a focused change with acceptance-driven tests and relevant documentation.
5. Run the shared verification commands and obtain independent review.
6. Open a PR linking the issue. Record exact evidence and remaining limitations.
7. The owner authorizes merging and releases initially.

For the initial repository bootstrap, locally prepared [issue drafts](docs/backlog/README.md)
stand in for issue numbers until GitHub is connected. This exception does not make
draft gameplay specifications ready for implementation.

Do not commit build outputs, local SDK configuration, keys, or private logs.
Do not bundle unrelated cleanup with a feature. Dependency and toolchain upgrades
must be explicit changes with compatibility checks. Keep documentation accurate
when behavior changes; a passing build is not proof of a completed feature.
