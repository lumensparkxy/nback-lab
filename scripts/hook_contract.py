"""Offline contract for the approved advisory project hooks, not runtime proof."""
import json
import tomllib


def check_hooks(root):
    errors = []
    try:
        value = json.loads((root / '.codex/hooks.json').read_text())
        config_path = root / '.codex/config.toml'
        if config_path.exists():
            config = tomllib.loads(config_path.read_text())
            if 'hooks' in config:
                errors.append('Keep project hooks in hooks.json; extra inline hooks are unapproved')
            if any(config.get('features', {}).get(key) is False for key in ('hooks', 'codex_hooks')):
                errors.append('Project configuration disables the approved checkpoint hooks')
        events = value['hooks']
        if set(events) != {'SessionStart', 'PreCompact', 'Interrupt', 'Stop'}:
            raise ValueError('Expected the four approved checkpoint events')
        for event, groups in events.items():
            matcher = {'SessionStart': '^(startup|resume|compact)$', 'PreCompact': '^(manual|auto)$'}.get(event)
            handler = {'type': 'command',
                       'command': 'python3 "$(git rev-parse --show-toplevel)/scripts/harness_checkpoint.py" hook',
                       'timeout': 3 if event == 'Interrupt' else 5,
                       'statusMessage': 'Loading local handoff checkpoint' if event == 'SessionStart' else 'Saving local handoff checkpoint'}
            if event == 'SessionStart': handler['additionalContextLimit'] = 2000
            group = {'hooks': [handler]}
            if matcher: group['matcher'] = matcher
            if groups != [group]: errors.append(f'{event}: checkpoint hook definition drift')
    except (OSError, ValueError, TypeError, KeyError, AttributeError) as error:
        errors.append(f'Checkpoint hook contract is unavailable or invalid ({type(error).__name__})')
    return errors
