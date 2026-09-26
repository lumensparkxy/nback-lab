# nback-lab

An open-source Android app and a reusable example of specification-driven agent
development. Original project code, documentation, prompts and agent workflows
are available under the [MIT license](LICENSE), with
[third-party materials retaining their own terms](docs/third-party-assets.md).

[Get N-Back: Visual Memory on Google Play](https://play.google.com/store/apps/details?id=com.maswadkar.nback)
· [Build your own app with the prompt playbook](docs/guides/blank-slate-to-working-app-prompts.md)
· [Documentation](docs/README.md)

An offline-capable visual n-back Android app, developed through a portable, evidence-based
agent coding harness. **Current implementation: selectable Position, Colour and
Number in all seven combinations, at 1-, 2- or 3-back, with guided practice and
local history.** Choose the types to remember and respond independently for each.
Colours have no name cue on the tile; an active Number appears inside it.

Dedicated Settings remembers your difficulty, selected types, 1–30 second turn
interval and 10/20/30/50 scored turns (default 20). Home summarizes the next run;
How to Play provides contextual instructions and optional guided practice.
Stimuli remain visible for 1 second at intervals 1–7, 2 seconds at 8–15 and
3 seconds at 16–30. New completed sessions include a per-type running-accuracy
chart and accessible turn-by-turn data. Older history retains its original summary. Completed normal
sessions are saved on-device with per-type results. Results includes Sessions and Progress. Sessions filters by exact mode, difficulty,
pace and length; Progress compares all saved runs with the same configuration.
A confirmed history clear covers every configuration. Existing
Position-only history is preserved. Practice and unfinished sessions are not saved.

## Start here

- [Blank slate → working app: copy-and-paste prompt playbook](docs/guides/blank-slate-to-working-app-prompts.md)
- [Product and agreed scope](docs/product.md)
- [Feature specifications](docs/features/README.md)
- [Architecture decisions](docs/decisions/README.md)
- [Development workflow](docs/development.md) and [agent roles](docs/agents.md)
- [Toolchain and setup](docs/toolchain.md)
- [Release, code-hygiene and ads/privacy workflows](docs/android-readiness.md)
- [GitHub activation and issue drafts](docs/github-setup.md)
- [Bootstrap validation record](docs/validation/bootstrap.md)
- [Contributing](CONTRIBUTING.md) and [license / third-party notices](docs/third-party-assets.md)

## Run the checks

Install the prerequisites in [toolchain setup](docs/toolchain.md), then:

```bash
./scripts/doctor.sh
./scripts/verify.sh
./scripts/test-failure-gate.sh
```

Routine CI runs 26 critical Android tests; the complete suite is available manually
and required before releases. See [CI coverage](docs/development.md#ci-coverage-and-speed).

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
| `docs/guides/` | Reusable prompt sequence for taking a new app from idea to delivery |
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
development app remain separate, including their local data.

## Reuse and licensing

You can study, modify and redistribute the original project under the [MIT
license](LICENSE), including for commercial use. Keep the copyright and license
notice with copies or substantial portions. The license includes no warranty.
Bundled third-party materials and downloaded dependencies retain their own licenses
and terms; see [third-party notices](docs/third-party-assets.md).

For a separately distributed fork, choose your own application ID, signing identity,
store listing, privacy disclosures and any service accounts. Development builds use
test advertising configuration; production services require your own setup and
review. See [release readiness](docs/android-readiness.md) before distribution.
