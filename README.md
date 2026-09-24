# nback-lab

An offline visual n-back Android app, developed through a portable, evidence-based
agent coding harness. **Current implementation: selectable Position, Colour and
Number in all seven combinations, at 1-, 2- or 3-back, with guided practice and
local history.** Choose the types to remember and respond independently for each.
Colours have no name cue on the tile; an active Number appears inside it.

Your difficulty, selected types and 1–30 second turn interval are remembered locally.
Stimuli remain visible for 1 second at intervals 1–7, 2 seconds at 8–15 and
3 seconds at 16–30. New completed sessions include a per-type running-accuracy
chart and accessible turn-by-turn data. Older history retains its original summary. Completed normal
sessions are saved on-device with per-type results. History filters by exact mode
and difficulty and offers a confirmed clear across all modes and levels. Existing
Position-only history is preserved. Practice and unfinished sessions are not saved.

## Start here

- [Product and agreed scope](docs/product.md)
- [Feature specifications](docs/features/README.md)
- [Architecture decisions](docs/decisions/README.md)
- [Development workflow](docs/development.md) and [agent roles](docs/agents.md)
- [Toolchain and setup](docs/toolchain.md)
- [GitHub activation and issue drafts](docs/github-setup.md)
- [Bootstrap validation record](docs/validation/bootstrap.md)

## Run the checks

Install the prerequisites in [toolchain setup](docs/toolchain.md), then:

```bash
./scripts/doctor.sh
./scripts/verify.sh
./scripts/test-failure-gate.sh
```

Start an Android emulator from Android Studio's Device Manager. Select its serial
from `adb devices`, then:

```bash
ANDROID_SERIAL=emulator-5554 ./scripts/emulator-test.sh
ANDROID_SERIAL=emulator-5554 ./scripts/run-app.sh
```

Replace the example serial with the actual target. These commands refuse physical
devices and do not create, wipe or shut down emulators. The scripts discover common
macOS/Linux SDK locations or respect `JAVA_HOME` and `ANDROID_HOME`.

## Repository map

| Path | Responsibility |
| --- | --- |
| `app/` | Android lifecycle, Compose UI, settings and session history adapters |
| `engine/` | Pure Kotlin sequence generation, elapsed-time session state and scoring |
| `docs/features/` | Canonical feature behavior and acceptance criteria |
| `docs/decisions/` | Accepted and proposed architectural decisions |
| `scripts/` | Portable local and CI commands |
| `.github/` | Issue/PR templates and automated checks |
| `.codex/` | Optional Codex-specific role configuration |

GitHub issues own task status and priority. Feature docs own behavior. The harness
can be used by any agent that follows [AGENTS.md](AGENTS.md) and runs the shared
commands. No external AI API key is needed by the app or build.

The application ID and namespace are `com.maswadkar.nback`. This identity was
selected before distribution; installations of the former `com.example.nback`
development app remain separate, including their local data. No open-source
license has been selected.
