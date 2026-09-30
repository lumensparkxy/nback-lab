#!/usr/bin/env python3
"""Portable source-bound evidence. Hashes detect mismatch, not producer honesty."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import time
import uuid
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SCHEMA = 1


def digest(data):
    return hashlib.sha256(data).hexdigest()


def remaining(deadline):
    if deadline is None:
        return 3
    seconds = deadline - time.monotonic()
    if seconds <= 0:
        raise TimeoutError('Source identity budget expired')
    return min(3, seconds)


def file_hash(path, deadline=None):
    remaining(deadline)
    with Path(path).open('rb') as stream:
        checksum = hashlib.sha256()
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            remaining(deadline)
            checksum.update(chunk)
    return checksum.hexdigest()


def git(root, *arguments, deadline=None):
    result = subprocess.run(['git', *arguments], cwd=root, capture_output=True, timeout=remaining(deadline))
    if result.returncode:
        raise ValueError('Source identity requires an accessible Git checkout')
    return result.stdout


def source_identity(root=ROOT, deadline=None):
    """Hash tracked and nonignored untracked files, modes, symlinks and deletions.

    Never follow symlinks or read ignored build output, keys or local.properties.
    HEAD is provenance; the fingerprint identifies actual working files.
    """
    root = Path(root).resolve()
    head = git(root, 'rev-parse', 'HEAD', deadline=deadline).decode().strip()
    names = sorted(set(git(root, 'ls-files', '--cached', '--others', '--exclude-standard', '-z', deadline=deadline).split(b'\0')) - {b''})
    entries = []
    for raw in names:
        remaining(deadline)
        name = os.fsdecode(raw)
        path = root / name
        try:
            mode = path.lstat().st_mode
        except FileNotFoundError:
            entries.append([name, 'deleted'])
            continue
        if stat.S_ISLNK(mode):
            entries.append([name, 'symlink', digest(os.fsencode(os.readlink(path)))])
        elif stat.S_ISREG(mode):
            entries.append([name, 'file', bool(mode & stat.S_IXUSR), file_hash(path, deadline=deadline)])
        else:
            raise ValueError(f'Unsupported source entry: {name}')
    status = git(root, 'status', '--porcelain', '-z', '--untracked-files=all', deadline=deadline)
    remaining(deadline)
    return {'head': head, 'fingerprint': digest(json.dumps(entries, ensure_ascii=True, separators=(',', ':')).encode()),
            'dirty': bool(status), 'file_count': len(entries)}


def same_source(left, right):
    return (isinstance(left, dict) and isinstance(right, dict)
            and all(left.get(key) == right.get(key) and left.get(key) is not None
                    for key in ('head', 'fingerprint', 'dirty', 'file_count')))


def build_inputs(root=ROOT, arguments=()):
    """Opaque hashes of external Gradle inputs; never store values or host paths."""
    root = Path(root)
    home = os.environ.get('GRADLE_USER_HOME') or str(Path.home() / '.gradle')
    arguments = list(arguments)
    for index, argument in enumerate(arguments):
        if argument in ('-g', '--gradle-user-home'):
            if index + 1 >= len(arguments) or arguments[index + 1].startswith('-'):
                raise ValueError('Gradle user home requires a path')
            home = arguments[index + 1]
        elif argument.startswith('--gradle-user-home='):
            home = argument.split('=', 1)[1]
    if not home:
        raise ValueError('Gradle user home requires a path')
    home = Path(home)
    if not home.is_absolute():
        home = root / home
    # JVM system-property redirects would otherwise hide the effective properties.
    if any('gradle.user.home' in os.environ.get(key, '') for key in
           ('GRADLE_OPTS', 'JAVA_OPTS', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS')):
        raise ValueError('Use GRADLE_USER_HOME or --gradle-user-home instead of a JVM user-home override')
    properties = [root / 'local.properties', home / 'gradle.properties']
    environment = {key: value for key, value in os.environ.items()
                   if key.startswith('ORG_GRADLE_PROJECT_') or key in ('GRADLE_OPTS', 'JAVA_OPTS', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS')}
    return {'arguments_sha256': digest(json.dumps(arguments, separators=(',', ':')).encode()),
            'properties_sha256': [file_hash(path) if path.is_file() else None for path in properties],
            'environment_sha256': digest(json.dumps(environment, sort_keys=True, separators=(',', ':')).encode())}


def atomic_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode='w', dir=path.parent, prefix='.write-', delete=False) as stream:
            temporary = Path(stream.name)
            json.dump(value, stream, indent=2)
            stream.write('\n')
            stream.flush()
            os.fsync(stream.fileno())
        temporary.replace(path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def junit_methods(paths, expected):
    expected = list(expected)
    if not expected or len(set(expected)) != len(expected):
        raise ValueError('Expected test inventory is empty or duplicated')
    passed = set()
    for path in paths:
        for test in ET.parse(path).getroot().iter('testcase'):
            name = test.get('classname', '') + '#' + test.get('name', '')
            if any(test.find(tag) is not None for tag in ('failure', 'error', 'skipped')):
                raise ValueError(f'Test did not pass: {name}')
            passed.add(name)
    if not passed or set(expected) - passed:
        raise ValueError(f'Missing executed tests: {sorted(set(expected) - passed)}')
    return sorted(passed)


def preserve_reports(folder, paths):
    """Copy exact accepted bytes once; never rely on mutable Gradle output later."""
    records = []
    for index, path in enumerate(sorted(paths)):
        data = Path(path).read_bytes()
        relative = f'reports/{index:03d}.xml'
        destination = folder / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
        records.append({'path': relative, 'sha256': digest(data)})
    return records


def android_record(root, suite, before, after, expected, paths, exit_code, reason=None, configuration=None):
    folder = Path(root) / 'artifacts/evidence' / ('android-' + uuid.uuid4().hex)
    folder.mkdir(parents=True)
    record = {'schema': SCHEMA, 'kind': 'android', 'created_at': datetime.now(timezone.utc).isoformat(),
              'suite': suite, 'source_before': before, 'source_after': after,
              'serial': os.environ.get('ANDROID_SERIAL', ''), 'exit_code': exit_code,
              'expected_methods': list(expected), 'reports': preserve_reports(folder, paths),
              'build_inputs': configuration,
              'ci': {key: os.environ[key] for key in ('GITHUB_SHA', 'GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT') if key in os.environ}}
    record['status'] = 'failed'
    if reason:
        record['reason'] = reason
    if exit_code == 0:
        if not same_source(before, after):
            raise ValueError('Source changed during Android validation')
        record['passed_methods'] = junit_methods([folder / item['path'] for item in record['reports']], expected)
        record['status'] = 'passed'
    path = folder / 'evidence.json'
    atomic_json(path, record)
    return path


def checked_file(folder, item):
    relative = item.get('path')
    if not isinstance(relative, str) or Path(relative).is_absolute() or '..' in Path(relative).parts:
        raise ValueError('Evidence contains an invalid artifact path')
    path = (folder / relative).resolve()
    if not path.is_relative_to(folder.resolve()) or file_hash(path) != item.get('sha256'):
        raise ValueError('Evidence artifact hash or location does not match')
    return path


def validate_record(path, root=ROOT, kind=None, current=True):
    path = Path(path)
    record = json.loads(path.read_text())
    if record.get('schema') != SCHEMA or record.get('kind') not in ('android', 'review'):
        raise ValueError('Unsupported evidence schema or kind')
    if kind is not None and record['kind'] != kind:
        raise ValueError('Wrong evidence kind')
    if record.get('status') != 'passed' or record.get('exit_code') != 0:
        raise ValueError('Evidence does not describe a successful result')
    if not same_source(record.get('source_before'), record.get('source_after')):
        raise ValueError('Evidence source was unavailable or changed')
    if current and not same_source(record['source_after'], source_identity(root)):
        raise ValueError('Evidence is stale for the current source')
    if record['kind'] == 'android':
        if record.get('suite') not in ('critical', 'full'):
            raise ValueError('Unknown Android suite')
        reports = [checked_file(path.parent, item) for item in record.get('reports', [])]
        passed = junit_methods(reports, record.get('expected_methods', []))
        if passed != record.get('passed_methods'):
            raise ValueError('Recorded test results differ from preserved reports')
    else:
        checked_file(path.parent, record['report'])
        if not record.get('reviewer'):
            raise ValueError('Missing reviewer identity')
    return record


def review_record(root, snapshot, report, reviewer, disposition):
    before = json.loads(Path(snapshot).read_text())
    after = source_identity(root)
    if not same_source(before, after):
        raise ValueError('Review source changed; obtain a current review')
    folder = Path(root) / 'artifacts/evidence' / ('review-' + uuid.uuid4().hex)
    folder.mkdir(parents=True)
    data = Path(report).read_bytes()
    if not data.strip():
        raise ValueError('Review report is empty')
    (folder / 'review.md').write_bytes(data)
    path = folder / 'evidence.json'
    atomic_json(path, {'schema': SCHEMA, 'kind': 'review', 'created_at': datetime.now(timezone.utc).isoformat(),
                      'source_before': before, 'source_after': after, 'reviewer': reviewer,
                      'status': disposition, 'exit_code': 0 if disposition == 'passed' else 1,
                      'report': {'path': 'review.md', 'sha256': digest(data)}})
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    snapshot = commands.add_parser('snapshot')
    snapshot.add_argument('--output', type=Path, required=True)
    check = commands.add_parser('check')
    check.add_argument('record', type=Path)
    review = commands.add_parser('review')
    review.add_argument('--snapshot', type=Path, required=True)
    review.add_argument('--report', type=Path, required=True)
    review.add_argument('--reviewer', required=True)
    review.add_argument('--disposition', choices=('passed', 'needs-changes'), required=True)
    args = parser.parse_args()
    try:
        if args.command == 'snapshot':
            atomic_json(args.output, source_identity())
            print(args.output)
        elif args.command == 'check':
            value = validate_record(args.record)
            print(f'Current {value["kind"]} evidence verified: {args.record}')
        else:
            print(review_record(ROOT, args.snapshot, args.report, args.reviewer, args.disposition))
    except (ValueError, OSError, subprocess.TimeoutExpired, ET.ParseError) as error:
        print(f'Evidence check failed: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
