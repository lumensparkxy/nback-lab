#!/usr/bin/env python3
"""Bind unsigned builds to full Android evidence; never sign or publish."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

from harness_evidence import atomic_json, build_inputs, file_hash, same_source, source_identity, validate_record
from ci_android_tests import full_inventory

ROOT = Path(__file__).resolve().parents[1]
TASKS = [':app:lintRelease', ':app:assembleRelease', ':app:bundleRelease', ':app:assembleReleaseSmoke', ':app:assembleAdsSmoke']


def validate_arguments(arguments):
    """Allow properties and diagnostic/build options, never alternate or skipped tasks."""
    flags = {'--offline', '--stacktrace', '--full-stacktrace', '--info', '--debug', '--warn', '--quiet',
             '--refresh-dependencies', '--rerun-tasks', '--build-cache', '--no-build-cache',
             '--configuration-cache', '--no-configuration-cache', '--parallel', '--no-parallel'}
    index = 0
    while index < len(arguments):
        argument = arguments[index]
        if argument in ('-g', '--gradle-user-home'):
            index += 1
            if index == len(arguments) or not arguments[index] or arguments[index].startswith('-'):
                raise ValueError('Gradle user home requires a path')
        elif (argument in flags or re.fullmatch(r'-P[\w.][\w.-]*(?:=.*)?', argument)
              or re.fullmatch(r'--max-workers=[1-9]\d*', argument)
              or argument.startswith('--gradle-user-home=') and argument.split('=', 1)[1]):
            pass
        else:
            raise ValueError('Unsupported release argument: task skipping, dry runs and project redirection are forbidden')
        index += 1


def candidate_facts(root):
    configs = list((Path(root) / 'app/build/generated/source/buildConfig/release').rglob('BuildConfig.java'))
    if len(configs) != 1:
        raise ValueError('Expected one current release BuildConfig')
    text = configs[0].read_text()
    code = re.search(r'\bVERSION_CODE\s*=\s*(\d+)\s*;', text)
    name = re.search(r'\bVERSION_NAME\s*=\s*("(?:[^"\\]|\\.)*")\s*;', text)
    package = re.search(r'\bAPPLICATION_ID\s*=\s*"([\w.]+)"', text)
    if not code or not name or not package:
        raise ValueError('Release version/application identity is unavailable')
    if package.group(1) != 'com.maswadkar.nback':
        raise ValueError('Release candidate must be the application, not a smoke variant')
    return {'application_id': package.group(1), 'version_code': int(code.group(1)), 'version_name': json.loads(name.group(1))}


def build(arguments, root=ROOT):
    root = Path(root)
    output = root / 'artifacts/android-audit/release.json'
    output.unlink(missing_ok=True)
    validate_arguments(arguments)
    before = source_identity(root)
    configuration = build_inputs(root, arguments)
    started = datetime.now(timezone.utc).isoformat()
    result = subprocess.run(['./gradlew', '--no-daemon', '--console=plain', *TASKS, *arguments], cwd=root)
    if result.returncode:
        return result.returncode
    result = subprocess.run([sys.executable, str(root / 'scripts/android_audit.py'), 'release'], cwd=root)
    if result.returncode:
        return result.returncode
    try:
        after = source_identity(root)
        if not same_source(before, after) or configuration != build_inputs(root, arguments):
            raise ValueError('Source or build inputs changed during the release build')
        report = json.loads(output.read_text())
        report.update(evidence_schema=1, build_status='passed', source_before=before, source_after=after,
                      build_inputs=configuration, candidate=candidate_facts(root), build_started_at=started,
                      build_completed_at=datetime.now(timezone.utc).isoformat())
        atomic_json(output, report)
    except (ValueError, OSError, subprocess.TimeoutExpired):
        output.unlink(missing_ok=True)
        raise
    return 0


def check(android_path, release_path, root=ROOT, arguments=(), previous_version=None):
    root = Path(root)
    validate_arguments(arguments)
    android = validate_record(android_path, root, 'android')
    if android['suite'] != 'full':
        raise ValueError('Release preflight requires full Android coverage')
    # Never trust a producer-supplied label or expected_methods list as the inventory.
    expected = set(full_inventory())
    if set(android['expected_methods']) != expected or set(android['passed_methods']) != expected:
        raise ValueError('Android evidence does not cover the current complete inventory')
    report = json.loads(Path(release_path).read_text())
    if report.get('evidence_schema') != 1 or report.get('build_status') != 'passed':
        raise ValueError('Release report lacks a successful source-bound build')
    source = source_identity(root)
    if not same_source(report.get('source_before'), report.get('source_after')) or not same_source(source, report.get('source_after')):
        raise ValueError('Release build is stale or its source changed')
    configuration = build_inputs(root, arguments)
    if android.get('build_inputs') != configuration or report.get('build_inputs') != configuration:
        raise ValueError('Candidate build inputs differ from Android or release evidence')
    facts = candidate_facts(root)
    if report.get('candidate') != facts:
        raise ValueError('Candidate version/application identity changed')
    for key, prefix in [('aab', 'app/build/outputs/bundle/release/'), ('mapping', 'app/build/outputs/mapping/release/')]:
        relative = report.get(key)
        if not isinstance(relative, str) or not relative.startswith(prefix) or '..' in Path(relative).parts:
            raise ValueError(f'Invalid release {key} path')
        path = (root / relative).resolve()
        if not path.is_relative_to(root.resolve()) or file_hash(path) != report.get('sha256', {}).get(relative):
            raise ValueError(f'Release {key} hash/location does not match')
    if previous_version is not None and (previous_version < 0 or facts['version_code'] <= previous_version):
        raise ValueError('Candidate version code is not newer than the supplied distributed baseline')
    return {'checks_passed': True, 'publication_ready': False, 'source': source, 'candidate': facts,
            'full_android_methods': len(expected), 'aab_sha256': report['sha256'][report['aab']],
            'mapping_sha256': report['sha256'][report['mapping']], 'previous_version_code': previous_version,
            'remaining': ['installed optimized smoke', 'signed/uploaded artifact verification', 'owner release authorization',
                          'live store/track verification'] + ([] if previous_version is not None else ['distributed version baseline not supplied'])}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='mode', required=True)
    builder = commands.add_parser('build')
    builder.add_argument('gradle_args', nargs=argparse.REMAINDER)
    checker = commands.add_parser('check')
    checker.add_argument('--android-evidence', type=Path, required=True)
    checker.add_argument('--release-report', type=Path, default=ROOT / 'artifacts/android-audit/release.json')
    checker.add_argument('--previous-version-code', type=int)
    checker.add_argument('--output', type=Path)
    checker.add_argument('gradle_args', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    extra = args.gradle_args[1:] if args.gradle_args[:1] == ['--'] else args.gradle_args
    try:
        if args.mode == 'build':
            return build(extra)
        value = check(args.android_evidence, args.release_report, arguments=extra, previous_version=args.previous_version_code)
        if args.output:
            atomic_json(args.output, value)
        print(json.dumps(value, indent=2))
    except (ValueError, OSError, KeyError, TypeError, ET.ParseError, subprocess.TimeoutExpired) as error:
        if args.mode == 'check' and args.output:
            args.output.unlink(missing_ok=True)
        print(f'Release preflight failed: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
