# ADR-005 — Optional ads outside gameplay

Status: Accepted within owner-authorized F007 implementation, 2026-09-24.
Tracking: [issue #31](https://github.com/lumensparkxy/nback-lab/issues/31).

The Android app owns an advertising coordinator, a separate Preferences DataStore
for frequency counters, and lifecycle-bound Google Ads/UMP adapters. The engine
remains network- and Android-free. Completion counting is independent of Room.
Serialize persistence, reserve a quota before show, and conservatively retain
reservations after uncertain process loss; definite failure restores the quota.
Store failure disables ads, never gameplay. No destructive corruption handler.

Apply GMA CHILD request treatment, UMP under-age-of-consent independently, PG
content ceiling and npa=1 for all users. This implements the selected conservative
no-age-collection approach; consent dialogs may be suppressed by UMP. Worldwide
legal and store disclosures remain release review, not an SDK guarantee.

Use Google test IDs in debug. Release ads are disabled by default; live builds
require explicit validated app/unit IDs and HTTPS privacy URL plus recorded
release review. Never sign release builds with debug keys. An optimized smoke
variant uses a distinct .qa identity and debug signing, with ads disabled.
An additional optimized .adsqa variant uses Google test IDs to exercise the SDK
path retained by R8; neither smoke artifact is a production release.

Separate adapters permit fake ads/privacy/storage/clock tests, while emulator
test-ad and UMP evidence remains necessary for real integration. Do not retain
Activities across destruction or defer an ad onto a different screen.

Sources: [F007](../features/F007-ad-supported-monetization.md),
[Google age treatment](https://developers.google.com/admob/android/targeting),
[UMP](https://developers.google.com/admob/android/privacy/gdpr).
