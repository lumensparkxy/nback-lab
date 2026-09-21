"""Regression tests for rejecting stale/irrelevant negative-control evidence."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
REPORT = 'engine/build/test-results/test/TEST-com.example.nback.engine.HarnessFailureProbeTest.xml'


class FailureGateTest(unittest.TestCase):
    def run_gate(self, failure_marker=None, stale=False, stale_copy=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'scripts').mkdir()
            shutil.copy(ROOT / 'scripts/test-failure-gate.sh', root / 'scripts')
            (root / 'scripts/env.sh').write_text(f'NBACK_ROOT="{root}"\n')
            report = root / REPORT
            report.parent.mkdir(parents=True)
            if stale:
                report.write_text('<testsuite><testcase><failure message="HARNESS_EXPECTED_FAILURE"/></testcase></testsuite>')
            if stale_copy:
                (root / 'artifacts').mkdir()
                (root / 'artifacts/expected-failure.xml').write_text('<testsuite name="previous-attempt"/>')
            # First call represents the failing build; a second call would succeed.
            stub = '#!/usr/bin/env python3\nfrom pathlib import Path\nimport sys\n'
            stub += 'if Path("called").exists(): sys.exit(0)\nPath("called").touch()\n'
            if failure_marker is not None:
                xml = f'<testsuite><testcase><failure message="{failure_marker}"/></testcase></testsuite>'
                stub += f'Path({REPORT!r}).write_text({xml!r})\n'
            stub += 'sys.exit(1)\n'
            (root / 'gradlew').write_text(stub)
            (root / 'gradlew').chmod(0o755)
            result = subprocess.run(['bash', 'scripts/test-failure-gate.sh'], cwd=root, capture_output=True, text=True)
            preserved = (root / 'artifacts/expected-failure.xml').exists()
            return result.returncode, preserved

    def test_fresh_expected_assertion_is_accepted_and_preserved(self):
        self.assertEqual((0, True), self.run_gate('HARNESS_EXPECTED_FAILURE'))

    def test_stale_report_cannot_certify_a_pre_test_failure(self):
        code, preserved = self.run_gate(stale=True)
        self.assertNotEqual(0, code)
        self.assertFalse(preserved)

    def test_unrelated_assertion_is_rejected(self):
        code, preserved = self.run_gate('SOME_OTHER_FAILURE')
        self.assertNotEqual(0, code)
        self.assertFalse(preserved)

    def test_failed_new_attempt_removes_previous_copied_evidence(self):
        for marker in (None, 'SOME_OTHER_FAILURE'):
            with self.subTest(marker=marker):
                code, preserved = self.run_gate(marker, stale=True, stale_copy=True)
                self.assertNotEqual(0, code)
                self.assertFalse(preserved)


if __name__ == '__main__':
    unittest.main()
