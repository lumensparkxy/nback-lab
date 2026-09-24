import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

MODULE = Path(__file__).resolve().parents[1] / 'android_audit.py'
spec = importlib.util.spec_from_file_location('android_audit', MODULE)
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class AndroidAuditTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.patch = patch.object(audit, 'ROOT', self.root)
        self.patch.start(); self.addCleanup(self.patch.stop)
        self.manifest = self.root / 'app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml'
        self.manifest.parent.mkdir(parents=True)
        self.config = self.root / 'app/build/generated/source/buildConfig/release/com/maswadkar/nback/BuildConfig.java'
        self.config.parent.mkdir(parents=True)
        self.config.write_text('boolean ADS_ENABLED = false;')
        self.write_manifest()

    def write_manifest(self, extra='', debug='false'):
        self.manifest.write_text(f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.maswadkar.nback">{extra}<application android:debuggable="{debug}" android:allowBackup="false"/></manifest>')

    def test_disabled_ads_still_requires_manual_policy_review(self):
        result = audit.privacy()
        self.assertFalse(result['live_ads']); self.assertFalse(result['policy_certified'])
        self.assertTrue(result['remaining'])

    def test_debuggable_or_ad_id_permission_fails(self):
        self.write_manifest(debug='true')
        with self.assertRaises(AssertionError): audit.privacy()
        self.write_manifest('<uses-permission android:name="com.google.android.gms.permission.AD_ID"/>')
        with self.assertRaises(AssertionError): audit.privacy()

    def test_live_test_ids_or_missing_privacy_url_fail(self):
        for config in ['boolean ADS_ENABLED = true; String ID="3940256099942544";', 'boolean ADS_ENABLED = true;']:
            self.config.write_text(config)
            with self.assertRaises(AssertionError): audit.privacy()

    def test_missing_evidence_is_not_a_pass(self):
        self.manifest.unlink()
        with self.assertRaises(AssertionError): audit.privacy()
        with self.assertRaises(AssertionError): audit.release()
        with self.assertRaises(AssertionError): audit.hygiene()

    def test_failed_check_removes_old_report(self):
        out = self.root / 'artifacts/android-audit/privacy.json'
        out.parent.mkdir(parents=True); out.write_text('{"old_success": true}')
        self.manifest.unlink()
        with patch('sys.argv', ['android_audit.py', 'privacy']):
            with self.assertRaises(SystemExit) as error: audit.main()
        self.assertEqual(1, error.exception.code); self.assertFalse(out.exists())

    def test_hygiene_never_authorizes_source_deletion(self):
        p = self.root / 'app/build/reports/lint-results-release.xml'; p.parent.mkdir(parents=True)
        p.write_text('<issues><issue id="UnusedResources" message="candidate"/></issues>')
        p = self.root / 'app/build/outputs/mapping/release/usage.txt'; p.parent.mkdir(parents=True)
        p.write_text('library.RemovedClass\n')
        data = audit.hygiene()
        self.assertFalse(data['safe_to_delete']); self.assertEqual(1, data['r8_removed_lines'])
        self.assertEqual(1, len(data['lint_candidates']))
