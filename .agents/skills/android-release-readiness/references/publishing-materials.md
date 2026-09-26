# Publishing-material review

Use the candidate build, agreed specs, delivered changes since the destination
track's previous release, and current store listing as evidence. Do not infer
shipped behavior from an issue title or an unmerged PR. If the previous distributed
version cannot be established, record that gap before claiming a complete changelog.

Review existing materials even when no update seems necessary; retain accurate
approved wording. For each item, record ready / needs changes / needs owner
decision, the evidence and the proposed correction. Prepare routine corrections
within scope; do not invent unresolved public, product or privacy decisions.

| Material | Required review |
| --- | --- |
| Release notes | Explain actual user-visible additions/fixes since that track's baseline. Exclude unfinished features, internal implementation noise, unsupported performance/health/intelligence claims, promotional copy and private details. Check each locale, language tags, character limits and version consistency. |
| App name and short/full descriptions | Match current feature names, supported modes, offline behavior and monetization. Avoid promising network-free operation or no data collection when SDK behavior contradicts it. No medical or general-intelligence improvement claims are approved for this app. |
| Screenshots | Visually inspect each image against the candidate's real screens and navigation. Cover changed user-visible flows; replace stale screens. Check language, readability, cropping, dimensions and absence of test overlays, private data or debug identifiers. Do not represent a generated mockup as an actual app screenshot. |
| Icon, feature graphic and optional video | Check current branding, factual captions, file format/dimensions and legibility. Inspect the actual media, not only filenames. Recheck any optional video's access and whether it still represents the app. |
| Privacy, ads and app-content declarations | Compare final manifest, permissions, shipped SDK configuration and actual collection/sharing with the public notice, Data safety, Contains ads, Advertising ID, audience/content rating and app-access answers. Use F007/ADR-005 and the repository ads/privacy workflow; an ads-disabled switch alone does not establish every declaration. Escalate unresolved disclosure choices. |
| Links and public contact | Open privacy/support/website/video links and inspect the destination. Confirm public contact details are intentional and approved; never infer permission to publish a private address. |
| Localizations and consistency | Check supported store locales and fallback content. Feature names, claims, versions and images must agree across notes, listing and candidate. Preserve approved translations; flag uncertainty rather than silently changing meaning. |

The main store listing and its assets are shared across tracks. Before changing
them for a test release, check every affected audience, including users still on
the production version. Keep candidate-only material local until its publication
is accurate and authorized for those audiences; do not assume a testing-track
approval also authorizes replacing shared production-facing screenshots or copy.
Record affected tracks and intended material timing in the release record.

## Format checks

As of the official guidance checked on 2026-09-26: release notes allow 500 Unicode
characters per language; app name, short description and full description allow
30, 80 and 4,000 characters respectively. The store icon is 512 x 512 pixels and
the feature graphic is 1024 x 500. Check current official requirements and the
Console's validation at execution time, including file types, screenshot/device
requirements and applicable locales; these numbers are not a complete asset test.

Preserve the exact final text and asset hashes in the release record. After saving
an authorized Console draft, read them back and check for truncation, wrong locale
or accidentally retained old material before submission.

## Official sources

- [Release notes and release preparation](https://support.google.com/googleplay/android-developer/answer/9859348?hl=en)
- [Store listing text](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en)
- [Preview assets and screenshots](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en)
- [Data safety declarations](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
