# nback-lab

An offline visual n-back Android app, developed through a portable, evidence-based
agent coding harness. **Current implementation: fixed visual 2-back sessions.** Read the instructions,
complete a 66-second session and inspect the four outcome counts and accuracy.
Practice settings and saved history are not implemented yet.

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
| `app/` | Android lifecycle, Compose UI, future persistence adapters |
| `engine/` | Pure Kotlin sequence generation, elapsed-time session state and scoring |
| `docs/features/` | Canonical feature behavior and acceptance criteria |
| `docs/decisions/` | Accepted and proposed architectural decisions |
| `scripts/` | Portable local and CI commands |
| `.github/` | Issue/PR templates and automated checks |
| `.codex/` | Optional Codex-specific role configuration |

GitHub issues own task status and priority. Feature docs own behavior. The harness
can be used by any agent that follows [AGENTS.md](AGENTS.md) and runs the shared
commands. No external AI API key is needed by the app or build.

The development application ID is `com.example.nback`; an owned ID and license
must be chosen before distribution. No open-source license has been selected.
