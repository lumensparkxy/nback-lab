"""Evidence acceptance must survive stale source, report and review failures."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
import harness_evidence as evidence


class EvidenceTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='nback-evidence-test-')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.git('init', '-q')
        (self.root / '.gitignore').write_text('artifacts/\nbuild/\n*.jks\nlocal.properties\n')
        (self.root / 'source.txt').write_text('agreed source\n')
        self.git('add', '.')
        self.git('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '-qm', 'fixture')

    def git(self, *arguments):
        return subprocess.run(['git', *arguments], cwd=self.root, check=True, capture_output=True)

    def report(self, body='<testcase classname="Example" name="one"/>'):
        path = self.root / 'build/TEST-example.xml'
        path.parent.mkdir(exist_ok=True)
        path.write_text(f'<testsuite>{body}</testsuite>')
        return path

    def android(self):
        identity = evidence.source_identity(self.root)
        return evidence.android_record(self.root, 'full', identity, identity, ['Example#one'], [self.report()], 0)

    def test_real_source_hash_includes_dirty_untracked_modes_links_and_deletions(self):
        identities = [evidence.source_identity(self.root)]
        (self.root / 'source.txt').write_text('changed\n')
        identities.append(evidence.source_identity(self.root))
        (self.root / 'new.txt').write_text('untracked\n')
        identities.append(evidence.source_identity(self.root))
        (self.root / 'new.txt').chmod(0o755)
        identities.append(evidence.source_identity(self.root))
        (self.root / 'link').symlink_to('/outside/not-readable')
        identities.append(evidence.source_identity(self.root))
        (self.root / 'source.txt').unlink()
        identities.append(evidence.source_identity(self.root))
        self.assertEqual(len(identities), len({item['fingerprint'] for item in identities}))
        self.assertFalse(identities[0]['dirty'])
        self.assertTrue(all(item['dirty'] for item in identities[1:]))
        self.assertEqual(1, len({item['head'] for item in identities}))

    def test_ignored_builds_and_private_inputs_do_not_change_source_hash(self):
        before = evidence.source_identity(self.root)
        self.report()
        (self.root / 'private.jks').write_text('fixture only')
        (self.root / 'local.properties').write_text('fixture only')
        self.assertTrue(evidence.same_source(before, evidence.source_identity(self.root)))

    def test_preserved_passing_reports_survive_later_build_output_changes(self):
        path = self.android()
        self.report('<testcase classname="Example" name="one"><failure/></testcase>')
        value = evidence.validate_record(path, self.root, 'android')
        self.assertEqual(['Example#one'], value['passed_methods'])

    def test_source_changes_make_existing_evidence_stale(self):
        path = self.android()
        (self.root / 'source.txt').write_text('later edit')
        with self.assertRaisesRegex(ValueError, 'stale'):
            evidence.validate_record(path, self.root)

    def test_altered_preserved_report_cannot_pass(self):
        path = self.android()
        value = json.loads(path.read_text())
        (path.parent / value['reports'][0]['path']).write_text('<testsuite/>')
        with self.assertRaisesRegex(ValueError, 'hash'):
            evidence.validate_record(path, self.root)

    def test_failed_unstable_empty_or_partial_results_cannot_pass(self):
        identity = evidence.source_identity(self.root)
        failed = evidence.android_record(self.root, 'full', identity, None, ['Example#one'], [], 17)
        with self.assertRaisesRegex(ValueError, 'successful'):
            evidence.validate_record(failed, self.root)
        with self.assertRaisesRegex(ValueError, 'Source changed'):
            evidence.android_record(self.root, 'full', identity, identity | {'fingerprint': 'changed'}, ['Example#one'], [self.report()], 0)
        for paths in ([], [self.report('<testcase classname="Example" name="other"/>')]):
            with self.assertRaisesRegex(ValueError, 'Missing executed'):
                evidence.android_record(self.root, 'full', identity, identity, ['Example#one'], paths, 0)

    def test_review_is_bound_to_snapshot_and_preserved_report(self):
        snapshot = self.root / 'artifacts/source.json'
        evidence.atomic_json(snapshot, evidence.source_identity(self.root))
        report = self.root / 'artifacts/review.md'
        report.write_text('Independent fixture review: no findings.\n')
        path = evidence.review_record(self.root, snapshot, report, 'fixture-reviewer', 'passed')
        self.assertEqual('review', evidence.validate_record(path, self.root)['kind'])
        (self.root / 'untracked.txt').write_text('later source')
        with self.assertRaisesRegex(ValueError, 'stale'):
            evidence.validate_record(path, self.root)
        with self.assertRaisesRegex(ValueError, 'Review source changed'):
            evidence.review_record(self.root, snapshot, report, 'fixture-reviewer', 'passed')

    def test_artifact_path_escape_is_rejected(self):
        path = self.android()
        value = json.loads(path.read_text())
        value['reports'][0]['path'] = '../outside.xml'
        evidence.atomic_json(path, value)
        with self.assertRaisesRegex(ValueError, 'artifact path'):
            evidence.validate_record(path, self.root)
