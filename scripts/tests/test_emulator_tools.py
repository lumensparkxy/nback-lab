"""Exercise real helper subprocesses against fake tools; never use a device."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('emulator_tools', ROOT / 'scripts/emulator_tools.py')
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)

FAKE = '''import json, os, signal, sys, time
from pathlib import Path
args = sys.argv[1:]
if os.environ.get('FAKE_STDERR'): print('* daemon started successfully', file=sys.stderr)
if Path(sys.argv[0]).name == 'gradlew':
    stage = 'gradle'
elif args[-1:] == ['get-state']:
    stage = 'state'
elif args[-1:] == ['ro.kernel.qemu']:
    stage = 'emulator'
elif args[-1:] == ['get-current-user']:
    stage = 'health'
elif 'install' in args:
    stage = 'install'
else:
    stage = 'launch'
with open('calls.jsonl', 'a') as f:
    f.write(json.dumps({'stage': stage, 'args': args, 'pid': os.getpid()}) + '\\n')
if stage == os.environ.get('FAKE_HANG'):
    time.sleep(60)
if stage == os.environ.get('FAKE_FAIL'):
    sys.exit(17)
if stage == 'state': print(os.environ.get('FAKE_STATE', 'device'))
if stage == 'emulator': print(os.environ.get('FAKE_EMULATOR', '1'))
if stage == 'health': print(os.environ.get('FAKE_HEALTH', '0'))
if stage == 'gradle': print('live build output', flush=True)
'''


class EmulatorToolsTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix='nback-tools-test-')
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / 'scripts').mkdir()
        for name in ('emulator_tools.py', 'emulator-test.sh', 'run-app.sh', 'env.sh'):
            shutil.copyfile(ROOT / 'scripts' / name, self.root / 'scripts' / name)
        sdk = self.root / 'sdk'; (sdk / 'platform-tools').mkdir(parents=True)
        for path in (self.root / 'gradlew', sdk / 'platform-tools/adb'):
            path.write_text(f'#!{sys.executable}\n' + FAKE); path.chmod(0o755)
        self.environment = {k: v for k, v in os.environ.items()
                            if not k.startswith(('FAKE_', 'NBACK_'))}
        self.environment.update(ANDROID_HOME=str(sdk), ANDROID_SERIAL='emulator-5998',
                                NBACK_ADB_TIMEOUT_SECONDS='5', NBACK_GRADLE_TIMEOUT_SECONDS='5',
                                NBACK_INSTALL_TIMEOUT_SECONDS='5', NBACK_LAUNCH_TIMEOUT_SECONDS='5')

    def command(self, mode='test', extra=()):
        return ['bash', 'scripts/emulator-test.sh' if mode == 'test' else 'scripts/run-app.sh', *extra]

    def invoke(self, mode='test', extra=(), **environment):
        return subprocess.run(self.command(mode, extra), cwd=self.root,
                              env=self.environment | environment, capture_output=True, text=True, timeout=12)

    def calls(self):
        path = self.root / 'calls.jsonl'
        return [json.loads(line) for line in path.read_text().splitlines()] if path.exists() else []

    def records(self):
        return [json.loads(p.read_text()) for p in (self.root / 'artifacts/emulator-operations').glob('*.json')]

    def reset_calls(self):
        (self.root / 'calls.jsonl').unlink(missing_ok=True)

    def test_healthy_test_forwards_exact_arguments_and_records_no_argument_values(self):
        extra = ['-Pexample=private value', '--info']
        result = self.invoke(extra=extra)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('live build output', result.stdout)
        calls = self.calls()
        self.assertEqual(['state', 'emulator', 'health', 'gradle'], [c['stage'] for c in calls])
        self.assertEqual(['--no-daemon', '--console=plain', ':app:connectedDebugAndroidTest', *extra], calls[-1]['args'])
        self.assertTrue(all(c['args'][:2] == ['-s', 'emulator-5998'] for c in calls[:3]))
        record = self.records()[0]
        self.assertEqual('emulator-5998', record['serial'])
        self.assertEqual('unavailable', record['revision'])  # isolated directory has no Git repository
        self.assertEqual('passed', record['status'])
        self.assertNotIn('private value', json.dumps(record))

    def test_run_mode_builds_installs_then_launches_selected_target(self):
        result = self.invoke('run')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(['state', 'emulator', 'health', 'gradle', 'install', 'launch'], [c['stage'] for c in self.calls()])
        self.assertIn(':app:assembleDebug', self.calls()[3]['args'])
        self.assertTrue(all(c['args'][:2] == ['-s', 'emulator-5998'] for c in self.calls() if c['stage'] != 'gradle'))

    def test_healthy_probe_stderr_does_not_change_protocol_results(self):
        result = self.invoke(FAKE_STDERR='1')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('* daemon started successfully', result.stderr)
        self.assertEqual('passed', self.records()[0]['status'])

    def test_missing_serial_and_invalid_deadlines_do_not_touch_tools(self):
        cases = [{'ANDROID_SERIAL': ''}]
        cases += [{f'NBACK_{kind}_TIMEOUT_SECONDS': value}
                  for kind in helper.DEFAULTS for value in ('0', '-1', 'NaN', 'inf', 'bad')]
        for environment in cases:
            with self.subTest(environment=environment):
                self.assertEqual(2, self.invoke(**environment).returncode)
                self.assertEqual([], self.calls())

    def test_unavailable_physical_and_unhealthy_targets_stop_before_build(self):
        for environment, expected in [({'FAKE_STATE': 'offline'}, ['state']),
                                      ({'FAKE_FAIL': 'state'}, ['state']),
                                      ({'FAKE_EMULATOR': '0'}, ['state', 'emulator']),
                                      ({'FAKE_HEALTH': 'Error'}, ['state', 'emulator', 'health'])]:
            with self.subTest(environment=environment):
                result = self.invoke(**environment)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual(expected, [c['stage'] for c in self.calls()])
                self.reset_calls()

    def test_each_hung_stage_times_out_and_stops_later_operations(self):
        stages = ['state', 'emulator', 'health', 'gradle', 'install', 'launch']
        for index, stage in enumerate(stages):
            with self.subTest(stage=stage):
                kind = {'state': 'ADB', 'emulator': 'ADB', 'health': 'ADB',
                        'gradle': 'GRADLE', 'install': 'INSTALL', 'launch': 'LAUNCH'}[stage]
                result = self.invoke('run', FAKE_HANG=stage, **{f'NBACK_{kind}_TIMEOUT_SECONDS': '2'})
                self.assertEqual(124, result.returncode, result.stderr)
                calls = self.calls()
                self.assertEqual(stages[:index + 1], [c['stage'] for c in calls])
                with self.assertRaises(ProcessLookupError):
                    os.kill(calls[-1]['pid'], 0)
                self.reset_calls()
        self.assertTrue(all(r['operations'][-1]['status'] == 'timeout' for r in self.records()))

    def test_ordinary_failure_preserves_exit_code_and_records_are_unique(self):
        self.assertEqual(0, self.invoke().returncode)
        self.assertEqual(17, self.invoke(FAKE_FAIL='gradle').returncode)
        records = self.records()
        self.assertEqual(2, len(records))
        self.assertEqual({0, 17}, {r['exit_code'] for r in records})
        self.assertEqual(2, len({r['run_id'] for r in records}))

    def test_signals_stop_only_owned_client_and_return_nonzero(self):
        sentinel = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(60)'])
        self.addCleanup(lambda: sentinel.poll() is None and sentinel.kill())
        for signum in (signal.SIGINT, signal.SIGTERM):
            with self.subTest(signum=signum):
                environment = self.environment | {'FAKE_HANG': 'gradle', 'NBACK_GRADLE_TIMEOUT_SECONDS': '20'}
                process = subprocess.Popen(self.command(), cwd=self.root, env=environment,
                                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                try:
                    deadline = time.monotonic() + 5
                    while time.monotonic() < deadline and not any(c['stage'] == 'gradle' for c in self.calls()):
                        time.sleep(0.02)
                    self.assertEqual('gradle', self.calls()[-1]['stage'])
                    process.send_signal(signum)
                    self.assertEqual(128 + signum, process.wait(timeout=8))
                    with self.assertRaises(ProcessLookupError):
                        os.kill(self.calls()[-1]['pid'], 0)
                    self.assertIsNone(sentinel.poll())
                finally:
                    if process.poll() is None: process.kill(); process.wait()
                    self.reset_calls()
        sentinel.terminate(); sentinel.wait(timeout=2)

    def test_client_ignoring_terminate_is_killed_without_group_signals(self):
        record = {'operations': []}
        runner = helper.Runner(record, root=self.root, grace=0.1)
        script = "import os,signal,time; from pathlib import Path; signal.signal(signal.SIGTERM, signal.SIG_IGN); Path('pid').write_text(str(os.getpid())); time.sleep(60)"
        with self.assertRaises(helper.OperationFailure) as failure:
            runner.run('hung-client', [sys.executable, '-c', script], 0.5)
        self.assertEqual(124, failure.exception.code)
        with self.assertRaises(ProcessLookupError):
            os.kill(int((self.root / 'pid').read_text()), 0)
        self.assertEqual('timeout', record['operations'][0]['status'])


if __name__ == '__main__':
    unittest.main()
