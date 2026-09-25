---
name: android-ads-privacy-review
description: Review Android advertising SDK configuration, consent behavior and Play disclosure readiness; use before enabling monetization or changing ads/privacy dependencies.
---

# Android Ads Privacy Review

Read AGENTS.md, docs/android-readiness.md, F007 and ADR-005. Run
scripts/ads-privacy-check.sh and inspect the real merged manifest and build
configuration. Separately verify UMP permission/age treatment and GMA request
age treatment, PG limit and non-personalized requests. Check test/live IDs,
privacy access, pending callbacks after navigation, offline behavior and no ads
during active exercises. Current all-user under-age treatment can suppress forms;
do not invent consent evidence or bypass eligibility to get a screenshot.
Report fake-test, actual SDK and publisher-account evidence separately. Review
Data safety, audience, SDK data disclosures and public privacy policy against
current official sources. This workflow does not certify worldwide legality.
Do not enable live ads, change console declarations or publish without the
owner's authorization and concrete review evidence.
