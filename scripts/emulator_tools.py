#!/usr/bin/env python3
"""Bounded emulator operations; never reset devices or stop shared services."""
from datetime import datetime, timezone
import json
import math
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import tempfile
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
DEFAULTS = {'ADB': 10, 'GRADLE': 1200, 'INSTALL': 120, 'LAUNCH': 30}


class OperationFailure(Exception):
    def __init__(self, message, code=1):
        super().__init__(message)
        self.code = code


class Interrupted(Exception):
    def __init__(self, signum):
        self.signum = signum


def interrupt(signum, _frame):
    raise Interrupted(signum)


def deadlines(environment):
    values = {}
    for kind, default in DEFAULTS.items():
        key = f'NBACK_{kind}_TIMEOUT_SECONDS'
        try:
            value = float(environment.get(key, default))
        except ValueError:
            raise OperationFailure(f'{key} must be a positive finite number', 2) from None
        if not math.isfinite(value) or value <= 0:
            raise OperationFailure(f'{key} must be a positive finite number', 2)
        values[kind] = value
    return values


def revision():
    try:
        result = subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=ROOT,
                                capture_output=True, text=True, timeout=3)
        value = result.stdout.strip()
        return value if result.returncode == 0 and re.fullmatch(r'[0-9a-f]{40,64}', value) else 'unavailable'
    except (OSError, subprocess.TimeoutExpired):
        return 'unavailable'


class Runner:
    def __init__(self, record, root=ROOT, grace=5):
        self.record = record
        self.root = root
        self.grace = grace

    def stop_client(self, process):
        # Only the Popen child belongs to this operation. Its process group could
        # contain a newly started shared adb server, so never signal the group.
        if process.poll() is not None:
            return
        process.terminate()
        try:
            process.wait(timeout=self.grace)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=2)

    def run(self, stage, arguments, timeout, capture=False):
        item = {'stage': stage, 'timeout_seconds': timeout}
        self.record['operations'].append(item)
        started = time.monotonic()
        process = None
        # A file avoids an unbounded pipe drain if a descendant holds stdout open.
        with tempfile.TemporaryFile() as output:
            try:
                process = subprocess.Popen(arguments, cwd=self.root, shell=False,
                                           start_new_session=True,
                                           stdout=output if capture else None)
                code = process.wait(timeout=timeout)
                if code != 0:
                    raise OperationFailure(f'{stage} failed', code if code > 0 else 128 - code)
                item.update(status='passed', exit_code=0)
                if capture:
                    output.seek(0)
                    return output.read(4096).decode('utf-8', errors='replace').strip()
            except subprocess.TimeoutExpired:
                item.update(status='timeout', exit_code=124)
                raise OperationFailure(f'{stage} timed out after {timeout:g}s', 124) from None
            except Interrupted as error:
                item.update(status='interrupted', exit_code=128 + error.signum)
                raise
            except OSError:
                item.update(status='failed', exit_code=127)
                raise OperationFailure(f'{stage} could not start', 127) from None
            except OperationFailure as error:
                item.update(status='failed', exit_code=error.code)
                raise
            finally:
                if process is not None and process.poll() is None:
                    self.stop_client(process)
                item['elapsed_seconds'] = round(time.monotonic() - started, 3)


def write_record(record):
    folder = ROOT / 'artifacts/emulator-operations'
    try:
        folder.mkdir(parents=True, exist_ok=True)
        path = folder / f'{record["run_id"]}.json'
        path.write_text(json.dumps(record, indent=2) + '\n')
        print(f'Emulator operation record: {path}', file=sys.stderr)
    except OSError:
        print('Warning: could not write emulator operation record.', file=sys.stderr)


def main(arguments):
    record = {'run_id': uuid.uuid4().hex, 'started_at': datetime.now(timezone.utc).isoformat(),
              'serial': os.environ.get('ANDROID_SERIAL', ''), 'revision': 'unavailable',
              'operations': []}
    started = time.monotonic()
    code = 1  # Unexpected exceptions must never leave a successful evidence record.
    previous = {s: signal.signal(s, interrupt) for s in (signal.SIGINT, signal.SIGTERM)}
    try:
        limits = deadlines(os.environ)
        if not arguments or arguments[0] not in ('test', 'run') or (arguments[0] == 'run' and len(arguments) > 1):
            raise OperationFailure('Use emulator-test.sh [Gradle arguments] or run-app.sh', 2)
        serial = record['serial']
        if not serial.strip():
            raise OperationFailure('Set ANDROID_SERIAL to the intended emulator', 2)
        record['mode'] = arguments[0]
        record['revision'] = revision()
        runner = Runner(record)
        adb = ['adb', '-s', serial]
        if runner.run('device-state', adb + ['get-state'], limits['ADB'], True) != 'device':
            raise OperationFailure('Selected target is not available', 2)
        if runner.run('emulator-check', adb + ['shell', 'getprop', 'ro.kernel.qemu'], limits['ADB'], True) != '1':
            raise OperationFailure('Selected target is not an emulator; refusing installation/tests', 2)
        if not re.fullmatch(r'[0-9]+', runner.run('activity-manager', adb + ['shell', 'am', 'get-current-user'], limits['ADB'], True)):
            raise OperationFailure('Selected emulator has no responsive ActivityManager', 2)
        gradle = ['./gradlew', '--no-daemon', '--console=plain']
        if arguments[0] == 'test':
            runner.run('connected-debug-tests', gradle + [':app:connectedDebugAndroidTest'] + arguments[1:], limits['GRADLE'])
        else:
            runner.run('assemble-debug', gradle + [':app:assembleDebug'], limits['GRADLE'])
            runner.run('install-debug', adb + ['install', '-r', 'app/build/outputs/apk/debug/app-debug.apk'], limits['INSTALL'])
            runner.run('launch-debug', adb + ['shell', 'am', 'start', '-W', '-n', 'com.example.nback/.MainActivity'], limits['LAUNCH'])
        code = 0
    except OperationFailure as error:
        code = error.code
        record['reason'] = str(error)
        print(str(error), file=sys.stderr)
    except Interrupted as error:
        code = 128 + error.signum
        record['reason'] = 'Interrupted by signal'
        print('Emulator operation interrupted.', file=sys.stderr)
    finally:
        record.update(exit_code=code, status='passed' if code == 0 else 'failed',
                      elapsed_seconds=round(time.monotonic() - started, 3))
        write_record(record)
        for signum, handler in previous.items():
            signal.signal(signum, handler)
    return code


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
