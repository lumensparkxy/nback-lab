# Contributing

The original project is [MIT licensed](LICENSE). Submit only contributions you
have the right to share under that license, and preserve third-party attribution
and licensing when including external material. See
[third-party notices](docs/third-party-assets.md).

For ideas or bugs, [open an issue](https://github.com/lumensparkxy/nback-lab/issues).
Describe the expected behavior and a reproducible example. Discuss new product
behavior before implementing it; a feature proposal is not yet an agreed spec.

1. Read [the development workflow](docs/development.md) and [product scope](docs/product.md).
2. Pick a ready GitHub issue with an agreed specification and resolved blockers,
   or a bounded maintenance PR under [the maintenance rules](docs/development.md#maintenance).
3. Inspect the working tree; create `codex/<issue-number>-<short-description>` for
   agent work. Use a separate worktree for genuinely independent simultaneous work.
4. Make a focused change with acceptance-driven tests and relevant documentation.
5. Run the shared verification commands and obtain independent review.
6. Open a PR linking the issue/spec when applicable. Record exact evidence and remaining limitations.
7. The owner authorizes merging and releases initially.
8. Verify the merge and complete [delivery closeout](docs/development.md#delivery-closeout).

For the initial repository bootstrap, locally prepared [issue drafts](docs/backlog/README.md)
stand in for issue numbers until GitHub is connected. This exception does not make
draft gameplay specifications ready for implementation.

Do not commit build outputs, local SDK configuration, keys, or private logs.
Do not bundle unrelated cleanup with a feature. Dependency and toolchain upgrades
must be explicit changes with compatibility checks. Keep documentation accurate
when behavior changes; a passing build is not proof of a completed feature.
