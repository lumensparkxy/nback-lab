# Harness evidence

[H002](features/H002-harness-reliability.md) extends the existing revision-specific
workflow. Run `python3 scripts/ci_android_tests.py critical` or `full` with an
explicit `ANDROID_SERIAL` as before. A successful run now preserves accepted
JUnit XML and writes a unique `artifacts/evidence/android-*/evidence.json` record.
CI uploads these records alongside the existing reports.

The source identity hashes tracked and nonignored untracked files, executable
modes, symlink targets and deletions. It includes dirty state and HEAD provenance.
Ignored artifacts, build outputs, keys and local setup are excluded. Build inputs
outside source control need separate candidate verification. A source change
during a run fails certification, even if Gradle and the tests succeeded.
The emulator wrapper also records start/end identity; unavailable Git identity
does not certify source, even when the underlying device operation succeeds.

Check a retained record against current source and preserved report hashes:

```bash
python3 scripts/harness_evidence.py check artifacts/evidence/android-RUN/evidence.json
```

Before dispatching independent review, save the stable source identity:

```bash
python3 scripts/harness_evidence.py snapshot --output artifacts/review-source.json
```

Give the reviewer that snapshot and the actual scope/diff. Save its report under
ignored artifacts. Register the checked report after resolving its findings:

```bash
python3 scripts/harness_evidence.py review --snapshot artifacts/review-source.json \
  --report artifacts/reviewer.md --reviewer reviewer-session-id --disposition passed
```

Registration fails if source changed since the snapshot. Use `needs-changes` for
unresolved findings; that record cannot pass `check`. Material fixes require a new
review and snapshot. Record hashes detect accidental mismatch, not malicious
fabrication; the lead still verifies reviewer independence and report contents.
Keep generated evidence ignored and retain unique records through closeout.
