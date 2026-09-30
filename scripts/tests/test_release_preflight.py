"""Candidate preflight rejects partial, stale, changed and mismatched evidence."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
import ci_android_tests as ci
import harness_evidence as evidence
import release_preflight as preflight


class ReleasePreflightTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='nback-preflight-test-')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        (self.root / '.gitignore').write_text('artifacts/\napp/build/\nlocal.properties\n')
        source = self.root / 'app/src/androidTest/java/example/ExampleTest.kt'
        source.parent.mkdir(parents=True)
        source.write_text('package example\nclass ExampleTest { @Test fun one() {} @Test fun two() {} }')
        for arguments in (['init', '-q'], ['add', '.'], ['-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '-qm', 'fixture']):
            subprocess.run(['git', *arguments], cwd=self.root, check=True, capture_output=True)
        inventory = patch.object(ci, 'ROOT', self.root)
        inventory.start()
        self.addCleanup(inventory.stop)
        self.expected = ci.full_inventory()
        self.identity = evidence.source_identity(self.root)
        config = self.root / 'app/build/generated/source/buildConfig/release/example/BuildConfig.java'
        config.parent.mkdir(parents=True)
        config.write_text('APPLICATION_ID = "com.maswadkar.nback"; VERSION_CODE = 7; VERSION_NAME = "0.7.0";')
        self.android = self.android_record('full', self.expected)
        aab = 'app/build/outputs/bundle/release/app-release.aab'
        mapping = 'app/build/outputs/mapping/release/mapping.txt'
        for name in (aab, mapping):
            path = self.root / name
            path.parent.mkdir(parents=True)
            path.write_text('synthetic fixture, not a real release artifact')
        self.release = self.root / 'artifacts/android-audit/release.json'
        self.report = {'evidence_schema': 1, 'build_status': 'passed', 'source_before': self.identity,
                       'source_after': self.identity, 'build_inputs': evidence.build_inputs(self.root),
                       'candidate': preflight.candidate_facts(self.root), 'aab': aab, 'mapping': mapping,
                       'sha256': {name: evidence.file_hash(self.root / name) for name in (aab, mapping)}}
        evidence.atomic_json(self.release, self.report)

    def android_record(self, suite, methods, arguments=()):
        path = self.root / 'app/build/TEST-fixture.xml'
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text('<testsuite>' + ''.join(f'<testcase classname="{name.split("#")[0]}" name="{name.split("#")[1]}"/>' for name in methods) + '</testsuite>')
        return evidence.android_record(self.root, suite, self.identity, self.identity, methods, [path], 0,
                                       configuration=evidence.build_inputs(self.root, arguments))

    def check(self, **keywords):
        return preflight.check(self.android, self.release, self.root, **keywords)

    def test_matching_complete_evidence_passes_but_never_certifies_publication(self):
        result = self.check(previous_version=6)
        self.assertTrue(result['checks_passed'])
        self.assertFalse(result['publication_ready'])
        self.assertEqual(2, result['full_android_methods'])
        self.assertIn('signed/uploaded artifact verification', result['remaining'])
        self.assertIn('distributed version baseline not supplied', self.check()['remaining'])

    def test_critical_and_falsely_relabeled_partial_evidence_cannot_pass(self):
        self.android = self.android_record('critical', self.expected[:1])
        with self.assertRaisesRegex(ValueError, 'full Android'):
            self.check()
        value = json.loads(self.android.read_text())
        value['suite'] = 'full'
        evidence.atomic_json(self.android, value)
        with self.assertRaisesRegex(ValueError, 'complete inventory'):
            self.check()

    def test_changed_source_and_release_source_are_rejected(self):
        evidence.atomic_json(self.release, self.report | {'source_after': self.identity | {'fingerprint': 'changed'}})
        with self.assertRaisesRegex(ValueError, 'stale'):
            self.check()
        evidence.atomic_json(self.release, self.report)
        (self.root / 'new-source.txt').write_text('untracked change')
        with self.assertRaisesRegex(ValueError, 'stale'):
            self.check()

    def test_changed_aab_mapping_or_candidate_version_is_rejected(self):
        for key in ('aab', 'mapping'):
            path = self.root / self.report[key]
            original = path.read_bytes()
            path.write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError, 'hash/location'):
                self.check()
            path.write_bytes(original)
        report = self.report | {'candidate': self.report['candidate'] | {'version_code': 99}}
        evidence.atomic_json(self.release, report)
        with self.assertRaisesRegex(ValueError, 'identity changed'):
            self.check()

    def test_configuration_drift_and_old_distributed_versions_fail(self):
        self.android = self.android_record('full', self.expected, ['-Pexample=changed'])
        with self.assertRaisesRegex(ValueError, 'build inputs differ'):
            self.check()
        self.android = self.android_record('full', self.expected)
        for baseline in (-1, 7, 8):
            with self.assertRaisesRegex(ValueError, 'newer'):
                self.check(previous_version=baseline)

    def test_smoke_path_and_missing_build_binding_fail(self):
        evidence.atomic_json(self.release, self.report | {'aab': 'app/build/outputs/bundle/releaseSmoke/app.aab'})
        with self.assertRaisesRegex(ValueError, 'Invalid release aab'):
            self.check()
        evidence.atomic_json(self.release, self.report | {'evidence_schema': None})
        with self.assertRaisesRegex(ValueError, 'source-bound build'):
            self.check()

    def test_failed_new_build_removes_stale_success_and_preserves_failure_code(self):
        with patch.object(preflight, 'source_identity', return_value=self.identity), \
             patch.object(preflight.subprocess, 'run') as run:
            run.return_value.returncode = 17
            self.assertEqual(17, preflight.build([], self.root))
            self.assertFalse(self.release.exists())

    def test_task_skips_dry_runs_and_redirection_cannot_certify_existing_old_artifacts(self):
        for arguments in (['-x', ':app:bundleRelease'], ['--exclude-task=:app:bundleRelease'],
                          ['--dry-run'], ['-m'], ['--task-graph'], ['--project-dir', 'other'],
                          ['-I', 'skip.gradle'], ['-Dgradle.user.home=other'], [':app:help']):
            with self.subTest(arguments=arguments):
                evidence.atomic_json(self.release, self.report)
                with patch.object(preflight.subprocess, 'run') as run:
                    with self.assertRaisesRegex(ValueError, 'Unsupported release argument'):
                        preflight.build(arguments, self.root)
                    run.assert_not_called()
                    self.assertFalse(self.release.exists())
                with self.assertRaisesRegex(ValueError, 'Unsupported release argument'):
                    self.check(arguments=arguments)

    def test_shell_wrapper_normalizes_one_caller_separator(self):
        scripts = self.root / 'scripts'
        scripts.mkdir()
        (scripts / 'release-check.sh').write_bytes((ROOT / 'scripts/release-check.sh').read_bytes())
        (scripts / 'env.sh').write_text('NBACK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"\n')
        bin_path = self.root / 'bin'
        bin_path.mkdir()
        python = bin_path / 'python3'
        python.write_text(f'#!{sys.executable}\nimport json, os, sys\nfrom pathlib import Path\n'
                          'Path(os.environ["NBACK_TEST_ARGUMENTS"]).write_text(json.dumps(sys.argv[1:]))\n')
        python.chmod(0o755)
        output = self.root / 'artifacts/arguments.json'
        environment = os.environ | {'PATH': str(bin_path) + os.pathsep + os.environ['PATH'],
                                     'NBACK_TEST_ARGUMENTS': str(output)}
        for arguments in (['-Pcandidate=example'], ['--', '-Pcandidate=example']):
            subprocess.run(['bash', str(scripts / 'release-check.sh'), *arguments],
                           cwd=self.root, env=environment, check=True, capture_output=True)
            self.assertEqual(['scripts/release_preflight.py', 'build', '--', '-Pcandidate=example'],
                             json.loads(output.read_text()))
