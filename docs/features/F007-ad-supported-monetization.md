# F007 — Ad-supported monetization

Agreement: Agreed for implementation — owner approval 2026-09-24.
Tracking: [issue #31](https://github.com/lumensparkxy/nback-lab/issues/31).

Owner approved ads-only worldwide 13+ implementation and the release-readiness,
code-hygiene and ads/privacy workflows. Merge and publishing remain separate.
Production advertising stays disabled until release inputs and review are complete.

## User outcome

Users can keep practising every current n-back mode for free, with uninterrupted
exercises and immediate results. Occasional advertising supports the app. The
owner selected worldwide availability for teens and adults aged 13+, with ads
only initially. Exercises, settings and history remain usable offline without
an app account.

## Scope and exclusions

Agreed first release:

- Google AdMob interstitials only at an explicit completed Results → Home action.
- Local frequency limits, consent/privacy controls and safe failure handling.
- Accessible Home privacy notice and required privacy-options controls.
- No purchases or billing SDK in this release.
- Test-only integration first; production enablement is a separate release gate.

Excluded: Remove ads purchases, billing/entitlements, banners, native ads, rewarded
ads, app-open ads, subscriptions, paid exercise modes, account creation, cloud
history, remote configuration, mediation,
network product analytics and any promise of revenue or cognitive improvement.
Play again, practice, results and history remain free. No timers, scores or
stimulus generation rules change.

## Behavior

### Placement and navigation

| Surface/action | Agreed behavior |
| --- | --- |
| Launch, resume, Home, settings or instructions | No automatic ad presentation |
| Gameplay, practice, interruption or cancellation | No ads or monetization prompts |
| Results first appear or user reads the chart | Results immediately available; no ad |
| Explicit Home button on the current completed normal Results | The only possible interstitial trigger |
| Play again, History, Android Back/gesture, saved-result detail | Navigate normally without an ad |
| Privacy or consent flow | No overlapping ad |
| Exit/background | No ad and no deferred presentation on resume |

The trigger must be distinct from the shared `home()` callback: that callback
also serves Back, practice and other routes. Reopening a saved result is never
an ad opportunity. Returning from History to the current Results may still offer
its one opportunity if it has not already been used.

On an eligible Home tap, use only an already-loaded valid ad. Otherwise go Home
immediately. Never wait for a download, replace Home with a loading page, or show
a late ad after navigation. A failed show proceeds Home once. Normal dismissal
also proceeds Home once; it must not begin another exercise.

### Frequency policy (owner-confirmed product limits)

The owner confirmed this timing on 2026-09-24. These limits are our starting
product policy, not Google-prescribed numbers.

1. Count each newly completed normal session once, independent of its score,
   number of selected types, history-save success or repeated rendering.
2. The first three completions after this feature is installed form a grace
   period. Existing saved history is not imported into this count.
3. Require three additional completions after the grace period before the first
   opportunity: the earliest possible ad follows completion **6**.
4. After an ad starts showing, require at least three more completions AND a
   five-minute cooldown before another can show.
5. Also require five minutes since process start before the first ad of that
   process. Restarting the app therefore cannot bypass the cooldown.
6. Measure time monotonically while the process lives. Background time may count
   toward the cooldown, but resuming never triggers an ad. On process restart,
   start a new five-minute guard; rotation does not restart it. No wall-clock
   changes can shorten a guard.
7. An eligible Results Home action is a single-use opportunity. Rapid taps,
   rotation and repeated callbacks cannot produce multiple presentations.
8. No-fill, offline, consent restrictions and failures do not consume the
   completion threshold or reset the cooldown. A later newly completed session
   can provide another opportunity; skipped ads never accumulate into a queue.
9. Before invoking show, durably reserve/reset the completion quota and begin
   the cooldown. On confirmed display, anchor the cooldown to that display time.
   Restore both the prior quota and prior cooldown on a definite failure-to-show
   callback. If process loss makes presentation uncertain, keep the reservation;
   favour fewer ads. An in-flight callback may not overwrite newer state. If a
   reservation write fails, navigate Home without showing an ad; recheck the
   foreground screen and privacy state after any asynchronous reservation write.

Persist grace progress and the bounded post-grace/post-ad completion count
separately from history. Clearing history does not reset ad limits. Reinstalling
or clearing app data resets these local counters; cross-device enforcement is
out of scope. Deduplicate a completion using its existing run ID; a history retry
is not a new completion. A completion lost before persistence may be undercounted
after process death, but must not be reconstructed from saved history.

If counter storage cannot be read or written safely, skip ads and keep gameplay
available. Never clear or rewrite settings/history to repair monetization data.

Examples:

- Six 22-second sessions finish in under five minutes: no ad yet. Merely waiting
  on Results causes no popup. A later explicit Home tap can qualify.
- Completion 6 occurs after five minutes: Home can show an ad; Play again cannot.
- An ad follows completion 6. Completion 9 finishes two minutes later: no ad.
  A later qualifying Results Home tap after the cooldown can show one.
- Completion 6 has no loaded ad: go Home. Completion 7 may qualify; never show
  two ads to make up for the missed opportunity.

### Lifecycle and SDK handling

Use a single presentation coordinator with explicit eligibility, reservation,
showing and terminal states. Only a foreground/resumed Activity with the exact
current Results action may present. Invalidate stale requests on navigation,
backgrounding or privacy changes; discard loaded ads when they are no longer
allowed. An ad-load callback cannot navigate or show.

Initiate ad loading only on non-gameplay screens after privacy checks.
Do not initiate SDK monetization work from engine ticks or response taps;
an already-in-flight load may finish during play but must not affect the exercise.
Do not hold an Activity across destruction. Configuration recreation must not
repeat an ad or its Home navigation. Process loss returns to the existing app
startup behavior, with no replay of a pending advertisement.

Ad SDK Activity transitions must preserve completed results and the existing
save/retry state. An ad must never cause a completed session to be recorded as
interrupted or saved again.

### Privacy, audience and offline use

The owner confirmed **worldwide availability for teens and adults aged 13+**.
The proposed Play audience bands are 13–15, 16–17 and 18+. This is the intended
audience, not a universal consent threshold or a content-rating claim. Do not
infer age from scores or assume every teen can consent as an adult. Worldwide
is the distribution target; ad eligibility remains subject to the approved
regional privacy treatment. SDK/consent treatment is specified below.

Use non-personalized requests (`npa=1`) and a maximum ad content rating of PG.
Apply conservative treatment to **all** users, without collecting age or birthdate:
GMA `AgeRestrictedTreatment.CHILD` (the SDK's replacement for under-age-of-consent
request treatment) and, independently, UMP `setTagForUnderAgeOfConsent(true)`.
This is a request-treatment policy, not a claim that the audience is under 13.

| Audience/region | UMP | GMA | Ad eligibility |
| --- | --- | --- | --- |
| Ages 13–17, adults or unknown; EEA/UK/Switzerland | Under-age-of-consent treatment | CHILD, PG, npa=1 | Only after UMP permits requests |
| Same audience; other launch regions | Same conservative setting | Same conservative settings | Same gate; no personalized fallback |
| Initialization/consent error without valid permission | No assumed approval | No requests | Exercises remain usable |

Refresh UMP at app launch on Home. This treatment suppresses consent collection;
do not manufacture a consent form or claim one always appears. If UMP requires a
form or privacy-options entry, service it only on Home, never during play. A form
callback arriving during gameplay must be deferred until Home. Consent changes
invalidate loaded ads. Privacy remains available even when no SDK form is needed.
This configuration is not worldwide legal certification. Regional requirements,
SDK disclosures and account configuration remain release-review obligations.

Include a readable bundled privacy notice and an approved public privacy-policy
URL. Explain that exercises and history stay on-device while Google advertising
services use the network. Do not send scores, stimuli, history, run IDs or response timing as ad
targeting or analytics. SDK data collection and merged permissions must be
reviewed and reflected accurately in Play disclosures; do not claim the app
collects no data merely because history is local.

Lack of network, consent eligibility, ad inventory or SDK prerequisites must never
prevent a session, results, history access or saving. Privacy controls must
support TalkBack, 48dp targets and 200% font size. SDK-owned screens
need separate manual evidence; fake UI tests cannot certify them.

## Architecture boundary

- Keep `engine` entirely independent of ads, consent and Android.
- Add small Android adapters and an application-owned coordinator in `app`;
  Compose renders state and sends explicit actions. Inject ad, consent,
  persistence and monotonic-time interfaces for deterministic tests.
- Use a separate Preferences DataStore for monetization counters, with serialized
  updates and current backup exclusions.
  Do not change the history schema or couple ad eligibility to history reads.
- Pin compatible stable Mobile Ads and UMP dependencies
  after checking the current toolchain; no unrelated dependency upgrades.
- Follow ADR-005 covering network access, counter storage and
  lifecycle ownership. This is a scoped extension to the prior offline-only foundation. No backend or billing dependency is needed for this scope.

## Acceptance criteria and verification

| ID | Observable acceptance criterion | Verification |
| --- | --- | --- |
| AC-01 | Every current mode, practice, pace, scoring, results and history works offline without an ad or payment requirement | Existing regression suite plus offline emulator sessions/history |
| AC-02 | Only explicit Home from current completed normal Results can present; Play again, Back, History, launch/resume and practice cannot | Navigation matrix with fake ad presenter and rendered checks |
| AC-03 | First possible ad is completion 6; later ads require 3 new completions and 5 minutes; process start adds a 5-minute guard | Fake-clock boundaries at 299999/300000ms; counts 3/5/6 and 2/3 since ad; restart/rotation/background cases |
| AC-04 | A run counts once across types, renders and save retries; history clear does not reset quotas | Duplicate-completion, multi-type, save-failure, clear and persistence tests |
| AC-05 | No-fill/load failure/stale load navigates immediately without a late popup or queued catch-up ads | Controlled delayed callbacks and offline/no-inventory fake scenarios |
| AC-06 | One eligible tap produces at most one ad and one Home navigation; definite show failure restores quota and cooldown; uncertain process loss is conservative | Double taps, duplicate/reordered callbacks, show-failure rollback, reservation-write/navigation race, rotation and process-loss tests |
| AC-07 | Ads cannot alter completion/history or active timing, and storage failure disables ads only | Pending history save during ad, corrupt/unwritable monetization store and active-session callback tests |
| AC-08 | UMP permission precedes requests; required privacy options work; denied/error/changed consent preserves gameplay | Fake consent matrix plus UMP test geography/forms; cached-ad invalidation |
| AC-09 | Agreed age treatment, non-personalization and PG content restrictions are applied before requests; unknown/unsupported cases suppress ads | Request-configuration assertions and approved country/age matrix tests |
| AC-10 | Privacy controls remain accessible without obscuring Start or current settings | Emulator screenshots, TalkBack and 200% text in portrait/landscape |
| AC-11 | Development/CI use fake services or Google test ads; release configuration cannot accidentally ship test/incomplete monetization setup | Build-variant/config validation; official test-ad smoke evidence |
| AC-12 | Audience, disclosures, owned app ID and ad configuration are approved before production enablement | Release evidence checklist tied to final build; missing prerequisites block release |

## Dependencies and decisions for owner review

| ID | Decision | Proposed position / readiness impact |
| --- | --- | --- |
| D1 | Intended age range | Owner confirmed teens and adults aged 13+ on 2026-09-24. Audience treatment is covered by D4. |
| D2 | Launch countries | Owner confirmed worldwide availability on 2026-09-24. Regional ad eligibility is covered by D4. |
| D3 | Revenue model | Owner selected ads only initially on 2026-09-24. Purchases, prices and purchase verification are deferred. |
| D4 | Ad personalization and content | Resolved within owner-approved conservative all-user design: GMA CHILD, UMP under-age treatment, PG, npa=1; no age collection. Matrix above. |
| D5 | Placement and limits | Owner confirmed the proposed timing on 2026-09-24: Results Home opportunity from completion #6, then 3 sessions AND 5 minutes, plus the 5-minute process-start guard. Play again stays ad-free. |
| D6 | Owned application ID, AdMob IDs, policy URL and account ownership | Owner-supplied release inputs. Test adapters can precede production values after scope approval. |

F001–F006 and ADR-002/003/004 remain the baseline. Advertising explicitly extends
the current exclusion in `docs/product.md` and introduces optional network
services; the app may then be described as offline-capable, not network-free.
Changing the development application ID is a separately scoped release decision;
do not silently change installed data identity as part of adding an SDK.

## Delivery plan

Issue #31 owns implementation readiness, evidence and delivery status.
The steps below describe implementation and release dependencies.

1. **Agreement and tracking.** Implement the agreed worldwide/13+ treatment,
   product scope and ADR-005 under issue #31 with the relevant AC IDs,
   dependencies and practical checks. D6 must distinguish test versus release
   blockers. Work on short-lived issue branches with one implementation writer.
2. **Local policy and navigation.** Implement counters, clock rules, explicit
   Results Home trigger and coordinator using fake services. Cover AC-01–07.
   Keep production monetization disabled.
3. **Consent and AdMob adapter.** Integrate approved audience settings, UMP,
   test ads, privacy UI, lifecycle invalidation and network-failure behavior.
   Cover AC-05–11. Depends on step 2 and the privacy decisions.
4. **Integrated validation and review.** Run `./scripts/doctor.sh`,
   `./scripts/verify.sh` and
   `ANDROID_SERIAL=emulator-... ./scripts/emulator-test.sh` on an explicitly
   selected emulator. Run `./scripts/test-failure-gate.sh` if failure-gate/CI
   behavior changes. Capture SDK test ads, consent screens
   and screenshots against the final revision. Obtain independent
   reviewer-subagent findings, resolve them and rerun affected checks.
5. **Release readiness and handoff.** Verify final SDK data disclosures, Play
   Contains ads/Data safety/audience declarations, privacy URL, account and app
   readiness and signing/app identity. Check applicable
   AdMob app verification/app-ads.txt setup before live enablement. Produce an
   AC-to-evidence PR handoff. Owner separately authorizes merge, console changes,
   internal-track uploads and production publishing; no live-ad clicking in QA.

Tests requiring a configured AdMob/UMP account or release-distributed build
are reported separately from deterministic emulator tests. An unavailable
account, device capability or release prerequisite is blocked/not run, not passed.
No emulator wipe or physical device selection is implied.

## Agreement record

- 2026-09-24: Owner requested planning and a draft specification only, with
  implementation explicitly deferred until owner approval.
- 2026-09-24: Owner selected teens and adults and ads only initially. This
  resolves D3 and the broad audience direction; it is not implementation approval.
- 2026-09-24: Owner confirmed worldwide availability, minimum age 13+ and the
  proposed timing. D1, D2 and D5 are confirmed; these choices do not by themselves
  approve the remaining privacy treatment or authorize implementation.
- 2026-09-24: Owner authorized implementation and workflows 1, 2 and 3, then
  separately authorized GitHub issues/review PRs. D4 is resolved using the
  conservative all-user design and current SDK guidance above. D6 remains a
  production-release input gate, not a blocker for test integration.

## Sources checked 2026-09-24

These sources inform integration/policy constraints; our frequency and placement
choices above are agreed product decisions. Recheck relevant guidance when
implementing and before release.

- [AdMob interstitial placement](https://support.google.com/admob/answer/6201362?hl=en)
- [Google Play ads policy](https://support.google.com/googleplay/android-developer/answer/9857753?hl=en)
- [UMP integration](https://developers.google.com/admob/android/privacy)
- [EEA, UK and Switzerland consent requirements](https://support.google.com/admob/answer/13554116?hl=en)
- [Google Play Families policy](https://support.google.com/googleplay/android-developer/answer/9893335?hl=en)
- [AdMob test ads](https://developers.google.com/admob/android/test-ads)
- [AdMob age treatment and content rating](https://developers.google.com/admob/android/targeting)
- [UMP under-age-of-consent handling](https://developers.google.com/admob/android/privacy/gdpr)
