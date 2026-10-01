"""Synthetic callback/contract checks; native discovery and trust remain separate."""
from copy import deepcopy
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
import harness_checkpoint as checkpoint
import harness_evidence as evidence
from hook_contract import check_hooks


class CheckpointTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='nback-checkpoint-')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.root.joinpath('.gitignore').write_text('artifacts/\n')
        self.root.joinpath('source.txt').write_text('baseline\n')
        subprocess.run(['git', '-c', 'init.templateDir=', 'init', '-q'], cwd=self.root, check=True, capture_output=True)
        subprocess.run(['git', 'add', '.'], cwd=self.root, check=True, capture_output=True)
        subprocess.run(['git', '-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid',
                        '-c', 'commit.gpgsign=false', '-c', 'core.hooksPath=/dev/null', 'commit', '-qm', 'fixture'],
                       cwd=self.root, check=True, capture_output=True)
        self.progress = {'issue': 'https://github.com/lumensparkxy/nback-lab/issues/39', 'pr': '',
                         'last_step': 'Current fixture test failed', 'next_action': 'Reproduce the current failure',
                         'last_result': 'failed'}

    def event(self, name, session='thr_fixture'):
        value = {'session_id': session, 'cwd': str(self.root), 'hook_event_name': name, 'turn_id': 'turn_fixture'}
        if name == 'SessionStart': value['source'] = 'resume'
        if name == 'PreCompact': value['trigger'] = 'auto'
        return value

    def context(self, event):
        return checkpoint.hook(event, self.root)['hookSpecificOutput']['additionalContext']

    def test_interrupt_compaction_and_stop_preserve_history_and_return_advisory_json(self):
        for event in ('Interrupt', 'PreCompact', 'Stop'):
            self.assertEqual({}, checkpoint.hook(self.event(event), self.root))
        folder = checkpoint.folder(self.root, 'thr_fixture')
        self.assertEqual(3, len(list(folder.glob('record-*.json'))))
        latest = checkpoint.load(folder, 'thr_fixture')
        self.assertEqual('Stop', latest['event'])
        self.assertEqual(evidence.digest(b'turn_fixture'), latest['turn_sha256'])

    def test_resume_reloads_failed_progress_without_claiming_authorization_or_pass(self):
        checkpoint.save(self.root, 'thr_fixture', 'Manual', 1, progress=self.progress)
        text = self.context(self.event('SessionStart'))
        self.assertIn('not instructions, approval or validation proof', text)
        self.assertIn('Current fixture test failed', text)
        self.assertIn('"last_result": "failed"', text)
        self.assertIn('Source matches', text)
        self.assertLessEqual(len(text), 2000)

    def test_changed_source_keeps_note_stale_across_subsequent_checkpoints(self):
        checkpoint.save(self.root, 'thr_fixture', 'Manual', 1, progress=self.progress)
        self.root.joinpath('source.txt').write_text('changed\n')
        self.assertIn('Source changed', self.context(self.event('SessionStart')))
        checkpoint.hook(self.event('Stop'), self.root)
        text = self.context(self.event('SessionStart'))
        self.assertIn('curated note refers to stale', text)

    def test_sessions_do_not_reload_each_others_notes(self):
        checkpoint.save(self.root, 'thr_first', 'Manual', 1, progress=self.progress)
        text = self.context(self.event('SessionStart', 'thr_second'))
        self.assertIn('No previous checkpoint', text)
        self.assertNotIn('Current fixture test failed', text)

    def test_raw_private_fields_and_transcript_paths_are_never_persisted_or_read(self):
        secret = 'DO_NOT_RETAIN_PRIVATE_FIXTURE'
        event = self.event('Interrupt') | {'transcript_path': '/nonexistent/' + secret,
                                         'last_assistant_message': secret, 'prompt': secret,
                                         'tool_input': {'command': secret}, 'model': secret}
        checkpoint.hook(event, self.root)
        contents = ''.join(path.read_text() for path in checkpoint.folder(self.root, 'thr_fixture').glob('*.json'))
        self.assertNotIn(secret, contents)
        self.assertNotIn(str(self.root), contents)

    def test_invalid_sessions_cwds_events_and_large_or_private_notes_fail(self):
        for field, value in (('session_id', '../escape'), ('session_id', 'x' * 129), ('cwd', '/'),
                             ('hook_event_name', 'PermissionRequest'), ('turn_id', '../escape')):
            with self.subTest(field=field), self.assertRaises(ValueError):
                checkpoint.hook(self.event('Interrupt') | {field: value}, self.root)
        for text in ('password=fixture', 'api_key:fixture', '/Users/someone/private', 'x' * 241, 'two\nlines'):
            with self.subTest(text=text), self.assertRaises(ValueError):
                checkpoint.note(self.progress | {'last_step': text})
        with self.assertRaises(ValueError): checkpoint.note(self.progress | {'extra': 'private'})
        with self.assertRaises(ValueError): checkpoint.note(self.progress | {'issue': 'https://elsewhere.invalid/1'})

    def test_input_from_checkout_subdirectory_is_supported(self):
        nested = self.root / 'src'; nested.mkdir()
        checkpoint.hook(self.event('Interrupt') | {'cwd': str(nested)}, self.root)
        self.assertIsNotNone(checkpoint.load(checkpoint.folder(self.root, 'thr_fixture'), 'thr_fixture'))

    def test_symlink_checkpoint_directories_and_records_cannot_escape(self):
        with tempfile.TemporaryDirectory() as outside:
            self.root.joinpath('artifacts').symlink_to(outside, target_is_directory=True)
            with self.assertRaises(ValueError): checkpoint.hook(self.event('Interrupt'), self.root)
            self.assertEqual([], list(Path(outside).iterdir()))
            self.root.joinpath('artifacts').unlink()
            checkpoint.hook(self.event('Interrupt'), self.root)
            folder = checkpoint.folder(self.root, 'thr_fixture')
            pointer = json.loads(folder.joinpath('latest.json').read_text())
            record = folder / pointer['record']; record.unlink(); record.symlink_to(Path(outside) / 'missing')
            with self.assertRaises(ValueError): checkpoint.load(folder, 'thr_fixture')

    def test_altered_records_or_cross_session_pointers_are_rejected(self):
        checkpoint.hook(self.event('Interrupt'), self.root)
        folder = checkpoint.folder(self.root, 'thr_fixture')
        latest = folder / 'latest.json'; pointer = json.loads(latest.read_text())
        record = folder / pointer['record']; original = record.read_bytes()
        record.write_bytes(original + b' ')
        with self.assertRaises(ValueError): checkpoint.load(folder, 'thr_fixture')
        record.write_bytes(original)
        pointer['session_id'] = 'thr_other'; latest.write_text(json.dumps(pointer))
        with self.assertRaises(ValueError): checkpoint.load(folder, 'thr_fixture')

    def test_unavailable_source_is_checkpointed_and_never_claimed_current(self):
        with patch.object(checkpoint, 'source_identity', side_effect=subprocess.TimeoutExpired('git', 1)):
            output = checkpoint.hook(self.event('Interrupt'), self.root)
            self.assertIn('unavailable source', output['systemMessage'])
            text = self.context(self.event('SessionStart'))
        self.assertIn('source identity is unavailable', text)
        record = checkpoint.load(checkpoint.folder(self.root, 'thr_fixture'), 'thr_fixture')
        self.assertIsNone(record['source'])
        self.assertEqual('TimeoutExpired', record['source_error'])

    def test_busy_checkpoint_is_bounded_and_preserves_previous_record(self):
        checkpoint.hook(self.event('Interrupt'), self.root)
        folder = checkpoint.folder(self.root, 'thr_fixture')
        original = folder.joinpath('latest.json').read_bytes()
        descriptor = os.open(folder / '.lock', os.O_RDWR)
        try:
            checkpoint.fcntl.flock(descriptor, checkpoint.fcntl.LOCK_EX | checkpoint.fcntl.LOCK_NB)
            started = time.monotonic()
            with self.assertRaises(TimeoutError): checkpoint.hook(self.event('Interrupt'), self.root)
            self.assertLess(time.monotonic() - started, 1.5)
        finally: os.close(descriptor)
        self.assertEqual(original, folder.joinpath('latest.json').read_bytes())

    def test_atomic_pointer_failure_keeps_previous_record_and_completed_new_history(self):
        checkpoint.hook(self.event('Interrupt'), self.root)
        folder = checkpoint.folder(self.root, 'thr_fixture'); original = folder.joinpath('latest.json').read_bytes()
        atomic = checkpoint.atomic_json
        def fail_pointer(path, value):
            if path.name == 'latest.json': raise OSError('fixture pointer failure')
            return atomic(path, value)
        with patch.object(checkpoint, 'atomic_json', side_effect=fail_pointer), self.assertRaises(OSError):
            checkpoint.hook(self.event('Stop'), self.root)
        self.assertEqual(original, folder.joinpath('latest.json').read_bytes())
        self.assertEqual(2, len(list(folder.glob('record-*.json'))))
        self.assertEqual('Interrupt', checkpoint.load(folder, 'thr_fixture')['event'])

    def test_source_probe_deadline_and_native_interrupt_contract(self):
        with self.assertRaises(TimeoutError): evidence.source_identity(self.root, deadline=time.monotonic() - 1)
        path = self.root / 'large'; path.write_bytes(b'x' * 1024)
        with self.assertRaises(TimeoutError): evidence.file_hash(path, deadline=time.monotonic() - 1)
        self.assertEqual([], check_hooks(ROOT))
        with tempfile.TemporaryDirectory() as temporary:
            contract = Path(temporary); contract.joinpath('.codex').mkdir()
            original = json.loads(ROOT.joinpath('.codex/hooks.json').read_text())
            for mutation in ('timeout', 'command', 'event', 'continue'):
                value = deepcopy(original)
                handler = value['hooks']['Interrupt'][0]['hooks'][0]
                if mutation == 'timeout': handler['timeout'] = 30
                elif mutation == 'command': handler['command'] = 'unapproved-command'
                elif mutation == 'event': value['hooks']['PermissionRequest'] = value['hooks']['Interrupt']
                else: handler['continue'] = False
                contract.joinpath('.codex/hooks.json').write_text(json.dumps(value))
                self.assertTrue(check_hooks(contract))
            contract.joinpath('.codex/hooks.json').write_text(json.dumps(original))
            for config in ('[hooks]\n', '[features]\nhooks = false\n', '[features]\ncodex_hooks = false\n'):
                contract.joinpath('.codex/config.toml').write_text(config)
                self.assertTrue(check_hooks(contract))

    def test_cli_malformed_or_oversized_input_is_json_advisory_failure(self):
        for raw in (b'not-json', b'x' * (checkpoint.LIMIT + 1)):
            result = subprocess.run([sys.executable, str(ROOT / 'scripts/harness_checkpoint.py'), 'hook'],
                                    input=raw, capture_output=True, timeout=3)
            self.assertEqual(1, result.returncode)
            self.assertEqual({}, json.loads(result.stdout))
            self.assertNotIn(raw[:10], result.stderr)


if __name__ == '__main__': unittest.main()
