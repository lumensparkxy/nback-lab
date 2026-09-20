#!/usr/bin/env python3
"""Small, offline structural checks. These do not certify agent behavior."""
from pathlib import Path
import re
import sys
import tomllib

ROOT = Path(__file__).resolve().parent.parent
errors = []
required = [
    'AGENTS.md', 'README.md', 'CONTRIBUTING.md', 'docs/product.md',
    'docs/development.md', 'docs/agents.md', 'docs/toolchain.md',
    'docs/github-setup.md', 'docs/features/F000-harness.md',
    '.codex/config.toml', '.github/workflows/ci.yml',
    '.github/pull_request_template.md', 'gradle/wrapper/gradle-wrapper.jar',
]
for name in required:
    if not (ROOT / name).is_file():
        errors.append(f'Missing {name}')

for path in [*ROOT.glob('*.md'), *ROOT.glob('docs/**/*.md')]:
    for target in re.findall(r'(?<!!)\[[^\]]*\]\(([^)]+)\)', path.read_text()):
        if '://' in target or target.startswith(('#', 'mailto:')):
            continue
        target = target.split('#')[0]
        if target and not (path.parent / target).exists():
            errors.append(f'{path.relative_to(ROOT)}: broken link {target}')

for path in [ROOT / 'gradle/libs.versions.toml', ROOT / '.codex/config.toml', *ROOT.glob('.codex/agents/*.toml')]:
    if not path.exists():
        continue
    try:
        data = tomllib.loads(path.read_text())
        if path.parent.name == 'agents':
            for field in ('name', 'description', 'developer_instructions'):
                if not isinstance(data.get(field), str) or not data[field].strip():
                    errors.append(f'{path.name}: missing {field}')
    except tomllib.TOMLDecodeError as exc:
        errors.append(f'{path}: {exc}')

for role in ('planner', 'implementer', 'reviewer', 'verifier'):
    if not (ROOT / f'.codex/agents/{role}.toml').is_file():
        errors.append(f'Missing agent role {role}')

if errors:
    print('\n'.join(errors), file=sys.stderr)
    sys.exit(1)
print('Harness structure, local Markdown links and TOML syntax passed.')
