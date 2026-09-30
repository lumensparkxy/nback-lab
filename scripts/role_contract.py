"""Validate the owner-approved static adapter contract; no runtime claims."""
import json
from pathlib import Path
import tomllib

ROLES = {'planner', 'implementer', 'reviewer', 'verifier'}
FIELDS = {'name', 'description', 'developer_instructions', 'model', 'model_reasoning_effort', 'sandbox_mode'}


def check_roles(root):
    root = Path(root)
    errors = []
    try:
        contract = json.loads((root / 'docs/agent-role-contract.json').read_text())
        if not isinstance(contract, dict) or set(contract) != {'agents', 'roles'}:
            raise ValueError('Contract requires agents and roles objects')
        settings = contract['agents']
        if (not isinstance(settings, dict) or set(settings) != {'enabled', 'max_concurrent_threads_per_session'}
                or type(settings['enabled']) is not bool
                or type(settings['max_concurrent_threads_per_session']) is not int
                or settings['max_concurrent_threads_per_session'] < 1):
            raise ValueError('Contract requires typed enablement and positive concurrency')
        if not isinstance(contract['roles'], dict) or set(contract['roles']) != ROLES:
            raise ValueError('Contract must describe the four approved roles')
        for role, values in contract['roles'].items():
            if (not isinstance(values, dict) or set(values) != {'model', 'model_reasoning_effort', 'sandbox_mode'}
                    or any(not isinstance(value, str) or not value.strip() for value in values.values())
                    or values['sandbox_mode'] not in ('read-only', 'workspace-write')):
                raise ValueError(f'Incomplete or invalid contract for {role}')
        inventory = {path.stem for path in (root / '.codex/agents').glob('*.toml')}
        if inventory != ROLES:
            errors.append('Agent role files differ from approved contract inventory')
        project = tomllib.loads((root / '.codex/config.toml').read_text())
        for key, expected in contract['agents'].items():
            actual = project.get('agents', {}).get(key)
            if type(actual) is not type(expected) or actual != expected:
                errors.append(f'.codex/config.toml: agents.{key} differs from approved contract')
        documentation = (root / 'docs/agents.md').read_text()
        for role, expected in contract['roles'].items():
            path = root / f'.codex/agents/{role}.toml'
            try:
                actual = tomllib.loads(path.read_text())
            except (OSError, tomllib.TOMLDecodeError) as error:
                errors.append(f'{path.name}: {error}')
                continue
            for key, value in expected.items():
                if actual.get(key) != value:
                    errors.append(f'{role}.toml: {key} differs from approved contract')
            if actual.get('name') != role:
                errors.append(f'{role}.toml: name does not match filename')
            for key in ('description', 'developer_instructions'):
                if not isinstance(actual.get(key), str) or not actual[key].strip():
                    errors.append(f'{role}.toml: missing {key}')
            extra = set(actual) - FIELDS
            if extra:
                errors.append(f'{role}.toml: unapproved overrides: {sorted(extra)}')
            row = f'| {role.title()} | `{expected["model"]}` | `{expected["model_reasoning_effort"]}` |'
            if row not in documentation:
                errors.append(f'docs/agents.md: {role} assignment differs from contract')
    except (OSError, ValueError, KeyError, TypeError, tomllib.TOMLDecodeError) as error:
        errors.append(f'Agent contract unavailable or malformed: {error}')
    return errors
