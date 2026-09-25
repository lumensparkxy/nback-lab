#!/usr/bin/env python3
"""Inspect build evidence. Does not remove code, sign, upload, or certify policy."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'


def manifest_facts(path):
    root = ET.parse(path).getroot()
    app = root.find('application')
    assert app is not None, 'Missing application'
    return {
        'package': root.get('package'),
        'debuggable': app.get(ANDROID + 'debuggable', 'false'),
        'backup': app.get(ANDROID + 'allowBackup'),
        'permissions': sorted(x.get(ANDROID + 'name') for x in root.findall('uses-permission')),
        'metadata': {x.get(ANDROID + 'name'): x.get(ANDROID + 'value') for x in app.findall('meta-data')},
    }


def one(pattern):
    files = list(ROOT.glob(pattern))
    assert len(files) == 1, f'Expected one current {pattern}, found {len(files)}'
    return files[0]


def release():
    apk = one('app/build/outputs/apk/release/*-unsigned.apk')
    aab = one('app/build/outputs/bundle/release/*.aab')
    mapping = ROOT / 'app/build/outputs/mapping/release/mapping.txt'
    assert mapping.is_file() and mapping.stat().st_size, 'R8 mapping missing'
    smoke = one('app/build/outputs/apk/releaseSmoke/*.apk')
    ads_smoke = one('app/build/outputs/apk/adsSmoke/*.apk')
    artifacts = [apk, aab, mapping, smoke, ads_smoke]
    return {'sha256': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in artifacts},
            'ads_smoke_apk': str(ads_smoke.relative_to(ROOT)), 'unsigned_apk' : str(apk.relative_to(ROOT)), 'aab': str(aab.relative_to(ROOT)),
            'mapping': str(mapping.relative_to(ROOT)), 'smoke_apk': str(smoke.relative_to(ROOT)),
            'publication_ready': False,
            'remaining': ['owner signing/release approval', 'installed optimized smoke evidence', 'store declarations']}


def privacy():
    path = one('app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml')
    data = manifest_facts(path)
    assert data['package'] == 'com.maswadkar.nback', 'Unexpected app identity'
    assert data['debuggable'] == 'false', 'Release is debuggable'
    assert data['backup'] == 'false', 'Backup policy changed'
    assert not any(p == 'com.google.android.gms.permission.AD_ID' or p.startswith('android.permission.ACCESS_ADSERVICES_') for p in data['permissions']), 'Unexpected advertising identifier/services permission'
    config = one('app/build/generated/source/buildConfig/release/**/BuildConfig.java').read_text()
    enabled = bool(re.search(r'ADS_ENABLED\s*=\s*true', config))
    if enabled:
        assert '3940256099942544' not in config, 'Test ID in live configuration'
        assert re.search(r'PRIVACY_URL\s*=\s*"https://', config), 'Missing public privacy policy'
    data.update(live_ads=enabled, policy_certified=False,
                remaining=['review actual SDK data disclosures', 'AdMob regional/account settings',
                           'Play target audience and Data safety declarations', 'privacy URL and owner release approval'])
    return data


def hygiene():
    report = ROOT / 'app/build/reports/lint-results-release.xml'
    assert report.is_file(), 'Release lint evidence missing'
    candidates = []
    for issue in ET.parse(report).getroot().findall('issue'):
        if issue.get('id') in ('UnusedResources', 'UnusedIds', 'UnusedAttribute'):
            candidates.append({'id': issue.get('id'), 'message': issue.get('message')})
    usage = ROOT / 'app/build/outputs/mapping/release/usage.txt'
    assert usage.is_file(), 'R8 usage evidence missing; run optimized release build'
    return {'lint_candidates': candidates, 'r8_removed_lines': len(usage.read_text().splitlines()),
            'r8_usage': str(usage.relative_to(ROOT)), 'safe_to_delete': False,
            'review_required': ['manifest/resource entry points', 'reflection and generated code',
                                'all build variants', 'dependency use and upstream keep rules'],
            'limitations': 'R8 removes artifact code, not proof source can be deleted; unused Kotlin declarations require separate inspection.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('release', 'privacy', 'hygiene'))
    args = parser.parse_args()
    folder = ROOT / 'artifacts/android-audit'
    folder.mkdir(parents=True, exist_ok=True)
    output = folder / f'{args.mode}.json'
    # A failed new check must never leave a stale successful report at this path.
    output.unlink(missing_ok=True)
    try:
        result = globals()[args.mode]()
        result['revision'] = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
        tracked = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=ROOT).split(b'\0')
        source = hashlib.sha256()
        for name in sorted(set(tracked)):
            if name and (ROOT / name.decode()).is_file():
                source.update(name + b'\0' + (ROOT / name.decode()).read_bytes() + b'\0')
        result['source_sha256'] = source.hexdigest()
        result['working_tree_dirty'] = bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True).strip())
    except (AssertionError, OSError, ET.ParseError) as error:
        parser.exit(1, f'{args.mode} check failed: {error}\n')
    output.write_text(json.dumps(result, indent=2) + '\n')
    print(f'{args.mode} evidence: {output.relative_to(ROOT)} (not publication approval)')


if __name__ == '__main__':
    main()
