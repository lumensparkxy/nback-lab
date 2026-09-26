# Release record template

Copy into a new, ignored `artifacts/releases/<version>-<date>/release-record.md`
for a release run. Use repository-relative evidence paths and verified Console/CI
links. Keep credentials, private tester lists and sensitive account screenshots
out of the record. Preserve previous records rather than overwriting history.
For a maintenance PR, share only a sanitized summary of relevant evidence.

## Scope

- Mode and requested stopping point:
- Package, source revision and build configuration:
- Previous distributed version and baseline evidence:
- Candidate version name/code; new build or library promotion:
- Track, countries/testers description and rollout percentage:
- Existing authorization and actions still requiring a decision:

## Candidate evidence

| Check | Revision/artifact/device | Result and evidence |
| --- | --- | --- |
| Environment and structural/JVM/lint/build checks | | |
| Complete Android suite | | |
| Optimized smoke and relevant upgrade checks | | |
| Ads/privacy evidence where applicable | | |
| Final signed bundle identity, integrity and certificate | | |
| AAB SHA-256 and matching mapping location | | |

## Publishing materials

| Item/locale | Ready / needs changes / needs owner decision | Evidence; exact approved text or asset path/hash |
| --- | --- | --- |
| Release notes | | |
| Store name and descriptions | | |
| Screenshots, icon, feature graphic, optional video | | |
| Privacy, ads and app-content declarations | | |
| Public links and contact | | |

- Shared listing: affected tracks/audiences and authorized publication timing:

## Console checkpoints

- Observation timestamp, Console link and displayed state:
- Pending changes included in this action; unrelated changes excluded:
- Managed-publishing setting and expected submission/publication effect:
- Uploaded/library artifact version and verified saved materials:
- Action taken and observed result (or outcome unknown):
- Availability evidence for intended track/audience:
- Installed version and in-place upgrade evidence, or reason not verified:

## Handoff

- Current stage; readiness blockers or missing evidence:
- Last completed step and next safe action:
- Owner decisions still needed:
- Retained artifacts and sanitized evidence links:
- Follow-up responsibility; no monitoring implied unless actually configured:
