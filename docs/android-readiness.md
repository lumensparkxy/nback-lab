# Android readiness workflows

These workflows implement the owner-selected release, code-hygiene and ads/privacy
capabilities under [issue #31](https://github.com/lumensparkxy/nback-lab/issues/31).
Repository-local skills live in `.agents/skills`; scripts remain usable without
Codex. No workflow signs, uploads, merges, publishes or deletes source implicitly.

## Release readiness

Run `./scripts/release-check.sh`. It builds release lint, an optimized unsigned
APK/AAB and a debug-signed optimized `releaseSmoke` APK with separate `.qa` identity
and ads disabled. An additional optimized `adsSmoke` variant uses `.adsqa` and
Google test IDs with advertising enabled. Validate that path before live release;
an ads-disabled R8 build cannot prove the ad path survives optimization. Review `artifacts/android-audit/release.json`, mapping files and
manifest. Install smoke only on an explicitly selected emulator; exercise Home,
practice/results/history and upgrades before a release. Debug tests alone do not
certify R8. Preserve mapping with the exact signed version. Increment versionCode
for actual releases, choose signing/key custody with the owner, and validate the
final signed AAB and Play internal-track result. No release key is configured here.

## Code hygiene (report only)

Run `./scripts/code-hygiene.sh`. Inspect release lint candidates, R8 `usage.txt`
and `artifacts/android-audit/dependencies.txt`. Review references across all
variants, manifest/XML, generated Room/Compose code and reflection before proposing
small removals. R8-removed classes include library internals; they are not a list
of deletable source. For unused Kotlin declarations, run IDE inspection when
available and record its configuration; this script makes no completeness claim.
Do not automatically add dependencies to perform a one-off audit or delete code.
Run affected tests, lint and optimized installed smoke after approved removals.

## Ads/privacy readiness

Run `./scripts/ads-privacy-check.sh`. Inspect actual release merged permissions,
backup/debug flags, app identity and live-ad switch. The JSON is build evidence,
not legal or Play certification. Read [F007](features/F007-ad-supported-monetization.md)
and [ADR-005](decisions/ADR-005-monetization.md) for consent/age treatment.

Debug always uses Google sample app and interstitial IDs. Instrumentation uses a
custom application that disables SDK requests; fake tests and actual test-ad
smoke are distinct evidence. Default release and releaseSmoke disable ads. Live
release requires all these Gradle properties from the owner's release environment:

- `nback.liveAds=true`
- `nback.admobAppId` and `nback.admobUnitId`: owned AdMob IDs (not secrets)
- `nback.privacyUrl`: approved public HTTPS policy URL
- `nback.releaseReviewed=true`: record review evidence before setting it

The switch is a build guard, not evidence itself. Review UMP/AdMob regional message
configuration, appropriate worldwide audience declarations, actual SDK data
collection, Play Contains ads/Data safety and the published notice. A sample
AdMob app ID may not have a publisher consent message; failed SDK/account checks
must be reported, not bypassed or called passes. Do not click live ads in QA.

Conservative all-user under-age treatment can suppress consent forms. Inspect
both UMP and GMA settings independently. Validate no requests without SDK
permission, no late forms during gameplay, cached-ad invalidation, privacy access,
offline usability and real Google test-ad presentation. Keep permissions/SDK
reviews current when dependencies or targeting change.

Sources: [release preparation](https://developer.android.com/studio/publish/preparing),
[R8](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization),
[lint](https://developer.android.com/studio/write/lint),
[UMP](https://developers.google.com/admob/android/privacy).
