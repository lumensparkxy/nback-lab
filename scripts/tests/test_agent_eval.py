"""Offline grading regressions, never substitute for live model runs."""
import json
import os
from pathlib import Path
import sys
import subprocess
import signal
import time
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
import agent_eval as evaluation


class AgentEvalTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='nback-eval-grader-')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)

    def prepare(self, identifier):
        case = next(case for case in evaluation.cases() if case['id'] == identifier)
        evaluation.fixture(self.root, identifier)
        expected = case['expect']
        answer = {'decision': expected['decisions'][0], 'reason': 'Fixture finding', 'next_action': 'Resolve the current blocker',
                  'evidence': [expected['evidence']], 'coverage': expected['coverage'][0], 'review_state': expected['review_state']}
        return case, answer, evaluation.snapshot(self.root)

    def grade(self, case, answer, before):
        return evaluation.grade(case, answer, before, evaluation.snapshot(self.root), self.root)

    def test_all_read_only_cases_accept_observed_expected_decisions(self):
        for case in evaluation.cases():
            if case['role'] == 'implementer': continue
            with self.subTest(case=case['id']), tempfile.TemporaryDirectory() as temporary:
                self.root = Path(temporary)
                case, answer, before = self.prepare(case['id'])
                self.assertTrue(all(self.grade(case, answer, before).values()))

    def test_stale_review_and_false_complete_coverage_cannot_pass(self):
        case, answer, before = self.prepare('reviewer_stale_evidence')
        answer.update(decision='ready', review_state='current', coverage='complete')
        self.assertFalse(all(self.grade(case, answer, before).values()))

    def test_incomplete_junit_and_unresolved_scope_cannot_claim_ready(self):
        for identifier in ('verifier_missing_method', 'planner_unresolved_scope'):
            with self.subTest(case=identifier), tempfile.TemporaryDirectory() as temporary:
                self.root = Path(temporary)
                case, answer, before = self.prepare(identifier)
                answer.update(decision='ready', coverage='complete')
                self.assertFalse(all(self.grade(case, answer, before).values()))

    def test_ready_fix_requires_correct_outputs_and_preserved_owner_work(self):
        case, answer, before = self.prepare('implementer_preserve_work')
        self.assertFalse(all(self.grade(case, answer, before).values()))
        evaluation.write(self.root, 'src/double.py', 'def double(value):\n    return value + value\n')
        self.assertTrue(all(self.grade(case, answer, before).values()))
        evaluation.write(self.root, 'owner-notes.txt', 'lost owner work')
        self.assertFalse(all(self.grade(case, answer, before).values()))

    def test_read_only_work_changes_and_blank_or_malformed_answers_fail(self):
        case, answer, before = self.prepare('planner_interrupted_handoff')
        for bad in (None, [], {}, answer | {'next_action': ''}, answer | {'evidence': [42]}):
            self.assertFalse(all(self.grade(case, bad, before).values()))
        evaluation.write(self.root, 'src/other.py', 'unauthorized edit')
        self.assertFalse(all(self.grade(case, answer, before).values()))

    def test_numeric_grader_rejects_code_execution_and_accepts_arithmetic_alternatives(self):
        path = self.root / 'double.py'
        for expression in ('value * 2', '2 * value', 'value + value'):
            path.write_text(f'def double(value):\n    return {expression}\n')
            self.assertTrue(evaluation.arithmetic_correct(path))
        for text in ('import os\nos.abort()\n', 'def double(value):\n    return __import__("os").abort()\n',
                     'def double(value):\n    return value + 1\n', 'def double(value):\n    return value ** 1000000\n',
                     'def double(value: missing_type):\n    return value * 2\n',
                     'def double(value):\n    return "x" * 1000000000000\n',
                     'def double(value):\n    return (1000000 * 1000000) * 1000000\n'):
            path.write_text(text)
            self.assertFalse(evaluation.arithmetic_correct(path))

    def test_actual_event_metrics_deduplicate_tools_and_retain_available_usage_only(self):
        events = [{'type': 'item.started', 'item': {'id': '1', 'type': 'command_execution'}},
                  {'type': 'item.completed', 'item': {'id': '1', 'type': 'command_execution'}},
                  {'type': 'turn.completed', 'usage': {'input_tokens': 10, 'cached_input_tokens': 5, 'output_tokens': 2,
                                                       'reasoning_output_tokens': 0, 'private': 'excluded'}}]
        result = evaluation.events('\n'.join(json.dumps(event) for event in events))
        self.assertEqual(1, result['completed_turns'])
        self.assertEqual(1, result['tool_counts']['command_execution'])
        self.assertEqual(0, result['usage']['reasoning_output_tokens'])
        self.assertNotIn('private', result['usage'])
        self.assertIsNone(evaluation.events('{"type":"turn.started"}')['usage'])

    def test_invalid_failed_or_unfinished_events_cannot_establish_live_completion(self):
        self.assertEqual(2, evaluation.events('not json\n{"type":"turn.failed"}')['event_errors'])
        self.assertEqual(0, evaluation.events('{"type":"turn.started"}')['completed_turns'])

    def test_staging_unrelated_owner_work_changes_protected_git_state(self):
        self.prepare('implementer_preserve_work')
        before = evaluation.git_state(self.root)
        subprocess.run(['git', 'add', 'owner-notes.txt'], cwd=self.root, check=True, capture_output=True)
        self.assertNotEqual(before, evaluation.git_state(self.root))

    def test_empty_commit_changes_resolved_head_despite_unchanged_index(self):
        self.prepare('implementer_preserve_work')
        before = evaluation.git_state(self.root)
        subprocess.run(['git', '-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid',
                        '-c', 'commit.gpgsign=false', '-c', 'core.hooksPath=/dev/null', 'commit', '--allow-empty', '-qm', 'unauthorized'],
                       cwd=self.root, check=True, capture_output=True)
        after = evaluation.git_state(self.root)
        self.assertEqual(before['index'], after['index'])
        self.assertNotEqual(before['head'], after['head'])

    def test_harmless_index_stat_refresh_does_not_count_as_staging(self):
        self.prepare('planner_unresolved_scope')
        before = evaluation.git_state(self.root)
        path = self.root / 'owner-notes.txt'
        path.write_bytes(path.read_bytes())
        subprocess.run(['git', 'status', '--porcelain'], cwd=self.root, check=True, capture_output=True)
        self.assertEqual(before, evaluation.git_state(self.root))

    def test_denied_group_probe_requires_independent_inventory_not_assumed_absence(self):
        with patch.object(evaluation.os, 'killpg', side_effect=PermissionError), \
             patch.object(evaluation.subprocess, 'run') as inventory:
            inventory.return_value.stdout = '123\n456\n'
            self.assertTrue(evaluation.group_exists(123))
            inventory.return_value.stdout = '456\n'
            self.assertFalse(evaluation.group_exists(123))
            inventory.side_effect = subprocess.CalledProcessError(1, 'ps')
            with self.assertRaises(subprocess.CalledProcessError): evaluation.group_exists(123)

    @unittest.skipUnless(os.name == 'posix', 'Owned process-group check is POSIX-specific')
    def test_owned_process_group_is_terminated_without_signaling_the_test_process(self):
        child = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(60)'], start_new_session=True)
        try:
            cleanup = evaluation.cleanup_owned(child)
            self.assertIsNotNone(child.wait(timeout=3))
            self.assertIn(cleanup['status'], ('stopped', 'unverified'))
            if cleanup['status'] == 'unverified': self.assertIn('error', cleanup)
        finally:
            if child.poll() is None: child.kill(); child.wait()

    @unittest.skipUnless(os.name == 'posix', 'Owned descendant cleanup is POSIX-specific')
    def test_descendant_ignoring_term_is_killed_even_when_leader_exits(self):
        heartbeat = self.root / 'heartbeat.txt'
        code = ('import signal,time\nfrom pathlib import Path\n'
                'signal.signal(signal.SIGTERM,signal.SIG_IGN)\n'
                f'path=Path({str(heartbeat)!r})\n'
                'for count in range(1200):\n    path.write_text(str(count)); time.sleep(.05)\n')
        leader = ('import subprocess,sys,time\n'
                  f'subprocess.Popen([sys.executable,"-c",{code!r}])\ntime.sleep(60)\n')
        child = subprocess.Popen([sys.executable, '-c', leader], start_new_session=True)
        try:
            deadline = time.monotonic() + 3
            while not heartbeat.exists() and time.monotonic() < deadline: time.sleep(.01)
            self.assertTrue(heartbeat.exists())
            evaluation.stop_owned(child)
            before = heartbeat.read_text()
            time.sleep(.2)
            self.assertEqual(before, heartbeat.read_text())
        finally:
            try: os.killpg(child.pid, signal.SIGKILL)
            except ProcessLookupError: pass
            child.wait(timeout=3)

    @unittest.skipUnless(os.name == 'posix', 'Signal interruption is POSIX-specific')
    def test_signal_interrupts_blocking_cli_wait_and_preserves_inconclusive_result(self):
        marker = self.root / 'cli-started.txt'
        cli = self.root / 'fixture-cli'
        cli.write_text(f'#!{sys.executable}\nimport os,time\nfrom pathlib import Path\n'
                       f'Path({str(marker)!r}).write_text(str(os.getpid()))\ntime.sleep(60)\n')
        cli.chmod(0o755)
        folder = self.root / 'result'; folder.mkdir()
        code = (f'import sys,json\nsys.path.insert(0,{str(ROOT / "scripts")!r})\nimport agent_eval as e\n'
                f'r=e.run_case(e.cases()[0],{str(cli)!r},__import__("pathlib").Path({str(folder)!r}),30)\n'
                'print(json.dumps({"interrupted":r["interrupted"],"result":r["result"]}))\n')
        driver = subprocess.Popen([sys.executable, '-c', code], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, start_new_session=True)
        try:
            deadline = time.monotonic() + 3
            while not marker.exists() and time.monotonic() < deadline: time.sleep(.01)
            self.assertTrue(marker.exists())
            driver.send_signal(signal.SIGTERM)
            stdout, stderr = driver.communicate(timeout=8)
            self.assertEqual(0, driver.returncode, stderr)
            self.assertEqual({'interrupted': True, 'result': 'inconclusive'}, json.loads(stdout))
            self.assertTrue((folder / 'result.json').exists())
        finally:
            if marker.exists():
                try: os.killpg(int(marker.read_text()), signal.SIGKILL)
                except ProcessLookupError: pass
            if driver.poll() is None: driver.kill(); driver.wait()

    def test_denied_cleanup_preserves_timeout_or_interruption_and_restores_handlers(self):
        from unittest.mock import Mock
        for failure in (subprocess.TimeoutExpired('fixture-cli', 1), evaluation.EvaluationInterrupted()):
            with self.subTest(failure=type(failure).__name__):
                folder = self.root / type(failure).__name__; folder.mkdir()
                child = Mock(pid=123456, returncode=0)
                child.communicate.side_effect = [failure, ('', '')]
                child.poll.return_value = 0
                previous = {signum: signal.getsignal(signum) for signum in (signal.SIGINT, signal.SIGTERM)}
                with patch.object(evaluation.subprocess, 'Popen', return_value=child), \
                     patch.object(evaluation, 'stop_owned', side_effect=PermissionError), \
                     patch.object(evaluation, 'fixture'), \
                     patch.object(evaluation, 'git_state', return_value={'head': 'unchanged'}):
                    result = evaluation.run_case(evaluation.cases()[0], 'offline-fixture-cli', folder, 1)
                self.assertEqual('inconclusive', result['result'])
                self.assertFalse(result['runtime_completed'])
                self.assertEqual('unverified', result['cleanup']['status'])
                self.assertEqual('PermissionError', result['cleanup']['error'])
                self.assertTrue(result['timed_out'] or result['interrupted'])
                self.assertEqual(result, json.loads((folder / 'result.json').read_text()))
                for signum, handler in previous.items(): self.assertEqual(handler, signal.getsignal(signum))

    def test_unverified_cleanup_stops_remaining_scenarios_and_returns_failure(self):
        result = {'result': 'inconclusive', 'elapsed_seconds': 1, 'interrupted': False,
                  'cleanup': {'status': 'unverified', 'error': 'PermissionError'}}
        source = {'head': 'a', 'fingerprint': 'b', 'dirty': False, 'file_count': 1}
        version = subprocess.CompletedProcess([], 0, 'codex-cli offline-fixture\n', '')
        with patch.object(evaluation, 'ROOT', self.root), \
             patch.object(evaluation, 'check_roles', return_value=[]), \
             patch.object(evaluation.shutil, 'which', return_value='/offline-fixture-cli'), \
             patch.object(evaluation.subprocess, 'run', return_value=version), \
             patch.object(evaluation, 'file_hash', return_value='synthetic-hash'), \
             patch.object(evaluation, 'source_identity', return_value=source), \
             patch.object(evaluation, 'run_case', return_value=result) as run_case, \
             patch.object(sys, 'argv', ['agent_eval.py', 'run']):
            self.assertEqual(1, evaluation.main())
        self.assertEqual(1, run_case.call_count)
        record = json.loads(next(self.root.glob('artifacts/agent-evals/run-*/evaluation.json')).read_text())
        self.assertFalse(record['complete'])
        self.assertEqual(1, record['summary']['inconclusive'])
