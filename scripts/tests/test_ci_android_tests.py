"""Protect the reduced CI gate against silently omitted tests and stale evidence."""
from pathlib import Path
import importlib.util
import tempfile
import re
import io
from contextlib import redirect_stderr
from unittest.mock import patch
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('ci_android_tests', ROOT / 'scripts/ci_android_tests.py')
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)


class CriticalSuiteTest(unittest.TestCase):
    def test_selection_names_real_tests_without_duplicates(self):
        self.assertEqual(len(ci.CRITICAL), len(set(ci.CRITICAL)))
        for selector in ci.CRITICAL:
            cls, method = selector.split('#')
            source = (ROOT / f'app/src/androidTest/java/com/maswadkar/nback/{cls}.kt').read_text()
            self.assertIn(f'@CriticalCi @Test fun {method}(', source, selector)
        annotated = {f'{p.stem}#{method}'
                     for p in (ROOT / 'app/src/androidTest/java/com/maswadkar/nback').glob('*Test.kt')
                     for method in re.findall(r'@CriticalCi\s+@Test fun (\w+)\(', p.read_text())}
        self.assertEqual(annotated, set(ci.CRITICAL))

    def report(self, body):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        path = Path(temporary.name) / 'TEST-example.xml'
        path.write_text(f'<testsuite>{body}</testsuite>')
        return path

    def test_complete_successful_selection_is_accepted(self):
        path = self.report('<testcase classname="Example" name="one"/>')
        self.assertEqual(ci.check_reports([path], ['Example#one']), 1)

    def test_no_reports_or_missing_selected_test_cannot_pass(self):
        path = self.report('<testcase classname="Example" name="other"/>')
        for paths in ([], [path]):
            with self.assertRaises(ValueError):
                ci.check_reports(paths, ['Example#one'])

    def test_failed_errored_or_skipped_tests_cannot_pass(self):
        for tag in ('failure', 'error', 'skipped'):
            path = self.report(f'<testcase classname="Example" name="one"><{tag}/></testcase>')
            with self.assertRaises(ValueError):
                ci.check_reports([path], ['Example#one'])

    def test_unchanged_reports_are_not_fresh_evidence(self):
        path = self.report('<testcase classname="Example" name="one"/>')
        before = ci.report_signatures(path.parent)
        fresh = [p for p, stamp in ci.report_signatures(path.parent).items() if before.get(p) != stamp]
        with self.assertRaises(ValueError):
            ci.check_reports(fresh, ['Example#one'])

    def test_modes_forward_annotation_only_for_critical_and_preserve_failure(self):
        for mode in ('critical', 'full'):
            with patch.object(ci.sys, 'argv', ['ci_android_tests.py', mode]), \
                 patch.object(ci.subprocess, 'run') as run, \
                 patch.object(ci, 'check_reports') as reports:
                run.return_value.returncode = 7
                self.assertEqual(ci.main(), 7)
                command = run.call_args.args[0]
                self.assertEqual(command[0], str(ROOT / 'scripts/emulator-test.sh'))
                self.assertEqual(command[1:], [
                    '-Pandroid.testInstrumentationRunnerArguments.annotation=com.maswadkar.nback.CriticalCi'
                ] if mode == 'critical' else [])
                reports.assert_not_called()

    def test_full_mode_rejects_fresh_critical_only_report(self):
        body = ''.join(f'<testcase classname="{ci.PREFIX}{selector.split("#")[0]}" name="{selector.split("#")[1]}"/>'
                       for selector in ci.CRITICAL)
        path = self.report(body)
        with patch.object(ci.sys, 'argv', ['ci_android_tests.py', 'full']), \
             patch.object(ci.subprocess, 'run') as run, \
             patch.object(ci, 'report_signatures', side_effect=[{}, {path: (1, 1)}]), \
             redirect_stderr(io.StringIO()) as errors:
            run.return_value.returncode = 0
            self.assertEqual(ci.main(), 1)
            self.assertIn('Missing executed tests', errors.getvalue())

    def test_full_mode_accepts_every_discovered_test(self):
        expected = ci.full_inventory()
        self.assertTrue(set(ci.PREFIX + s for s in ci.CRITICAL) < set(expected))
        path = self.report(''.join(f'<testcase classname="{s.split("#")[0]}" name="{s.split("#")[1]}"/>' for s in expected))
        with patch.object(ci.sys, 'argv', ['ci_android_tests.py', 'full']), \
             patch.object(ci.subprocess, 'run') as run, \
             patch.object(ci, 'report_signatures', side_effect=[{}, {path: (1, 1)}]):
            run.return_value.returncode = 0
            self.assertEqual(ci.main(), 0)

    def test_full_inventory_rejects_unsupported_annotations(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source = root / 'app/src/androidTest/java/example/ExampleTest.kt'
            source.parent.mkdir(parents=True)
            source.write_text('package example\nclass ExampleTest { @Test(timeout=50) fun timed() {} }')
            with patch.object(ci, 'ROOT', root), self.assertRaises(ValueError):
                ci.full_inventory()

    def test_full_inventory_includes_qualified_junit_annotations(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source = root / 'app/src/androidTest/java/example/ExampleTest.kt'
            source.parent.mkdir(parents=True)
            source.write_text('package example\nclass ExampleTest { @Test fun known() {} @org.junit.Test fun qualified() {} }')
            with patch.object(ci, 'ROOT', root):
                self.assertEqual(set(ci.full_inventory()), {'example.ExampleTest#known', 'example.ExampleTest#qualified'})

    def test_full_inventory_rejects_tests_in_nonstandard_filename(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source = root / 'app/src/androidTest/java/example/Unexpected.kt'
            source.parent.mkdir(parents=True)
            source.write_text('package example\nclass ExampleTest { @Test fun present() {} }')
            with patch.object(ci, 'ROOT', root), self.assertRaises(ValueError):
                ci.full_inventory()
