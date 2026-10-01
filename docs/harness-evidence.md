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

## Release coverage preflight

`release-check.sh` retains its unsigned build/audit tasks and adds start/end source
and external build-input hashes to `artifacts/android-audit/release.json`. Routine
CI may still run this command without full Android evidence. For a candidate,
run the full Android suite and then:

```bash
python3 scripts/release_preflight.py check \
  --android-evidence artifacts/evidence/android-RUN/evidence.json \
  --previous-version-code 3 --output artifacts/release-preflight.json
```

Replace the example baseline with the verified distributed version. The check
independently discovers the complete current test inventory; relabeling critical
evidence as full cannot pass. It verifies current source/build-input identity and
the actual release AAB and mapping hashes. Candidate version must exceed a supplied
baseline. Without one, the report explicitly retains that release decision.

If supplying extra Gradle arguments, use the same arguments after `--` on
`release-check.sh`, `ci_android_tests.py full`, and `release_preflight.py check`.
The release wrapper accepts one optional leading `--`. Release arguments allow
project properties (`-Pname=value`), diagnostic/build flags, and an explicit
Gradle user home. Task exclusions, dry runs, other tasks and project/settings
redirection are rejected before an old output can be certified. The effective
`GRADLE_USER_HOME` or `-g`/`--gradle-user-home` properties file is hashed; JVM
system-property user-home overrides are rejected. These checks still do not
make a developer machine a hermetic build environment.
Opaque hashes include those arguments, external local/user Gradle property files,
and Gradle-related environment properties; values and host paths are not retained.
Changing these inputs requires new evidence. Cross-host property differences fail
closed; do not bypass them by relabeling a report. This is a consistency check,
not proof of a hermetic toolchain or truthful producer. Debug full-suite results
remain distinct from optimized/signed/Play-installed verification. No preflight
result authorizes signing, merging, uploading or publishing.
