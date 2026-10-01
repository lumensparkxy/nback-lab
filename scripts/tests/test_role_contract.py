"""Detect semantic role drift that syntactically valid TOML cannot catch."""
from pathlib import Path
import json
import shutil
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
from role_contract import check_roles


class RoleContractTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='nback-role-contract-')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        for name in ('docs/agent-role-contract.json', 'docs/agents.md', '.codex/config.toml',
                     *[f'.codex/agents/{role}.toml' for role in ('planner', 'implementer', 'reviewer', 'verifier')]):
            destination = self.root / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / name, destination)

    def test_current_approved_configuration_passes(self):
        self.assertEqual([], check_roles(self.root))

    def test_each_approved_assignment_is_checked_not_just_syntax(self):
        for role in ('planner', 'implementer', 'reviewer', 'verifier'):
            path = self.root / f'.codex/agents/{role}.toml'
            original = path.read_text()
            for field in ('model', 'model_reasoning_effort', 'sandbox_mode', 'name'):
                lines = [f'{field} = "wrong"' if line.startswith(field + ' = ') else line
                         for line in original.splitlines()]
                path.write_text('\n'.join(lines))
                with self.subTest(role=role, field=field):
                    self.assertTrue(check_roles(self.root))
                path.write_text(original)

    def test_missing_fields_and_unapproved_permission_overrides_fail(self):
        path = self.root / '.codex/agents/reviewer.toml'
        original = path.read_text()
        for amendment in ('approval_policy = "never"\n', '[tools]\nall = true\n', 'max_concurrent_threads_per_session = 5\n'):
            path.write_text(amendment + original)
            self.assertTrue(check_roles(self.root))
        path.write_text('\n'.join(line for line in original.splitlines() if not line.startswith('model_reasoning_effort = ')))
        self.assertTrue(check_roles(self.root))

    def test_shared_concurrency_enablement_and_documentation_drift_fail(self):
        config = self.root / '.codex/config.toml'
        original = config.read_text()
        for before, after in (('enabled = true', 'enabled = false'),
                              ('max_concurrent_threads_per_session = 2', 'max_concurrent_threads_per_session = 3')):
            config.write_text(original.replace(before, after))
            self.assertTrue(check_roles(self.root))
        config.write_text(original)
        docs = self.root / 'docs/agents.md'
        docs.write_text(docs.read_text().replace('`gpt-6-astra`', '`wrong-model`'))
        self.assertTrue(check_roles(self.root))

    def test_missing_or_malformed_contract_and_role_fail_closed(self):
        path = self.root / '.codex/agents/verifier.toml'
        path.write_text('invalid [toml')
        self.assertTrue(check_roles(self.root))

    def test_incomplete_contract_cannot_disable_config_validation(self):
        path = self.root / 'docs/agent-role-contract.json'
        original = json.loads(path.read_text())
        cases = [original | {'agents': {}}, original | {'roles': {}}]
        for section, fields in [('agents', ['enabled', 'max_concurrent_threads_per_session'])]:
            for field in fields:
                value = json.loads(json.dumps(original))
                del value[section][field]
                cases.append(value)
        for role in original['roles']:
            for field in ('model', 'model_reasoning_effort', 'sandbox_mode'):
                value = json.loads(json.dumps(original))
                del value['roles'][role][field]
                cases.append(value)
        for value in cases:
            path.write_text(json.dumps(value))
            self.assertTrue(check_roles(self.root), value)

    def test_extra_or_renamed_role_files_are_rejected(self):
        directory = self.root / '.codex/agents'
        shutil.copyfile(directory / 'reviewer.toml', directory / 'unapproved.toml')
        self.assertTrue(check_roles(self.root))
        (directory / 'unapproved.toml').unlink()
        (directory / 'reviewer.toml').rename(directory / 'renamed.toml')
        self.assertTrue(check_roles(self.root))
        (self.root / 'docs/agent-role-contract.json').unlink()
        self.assertTrue(check_roles(self.root))
