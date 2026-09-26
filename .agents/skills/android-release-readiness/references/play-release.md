# Play release procedure

Use for preparation or authorized publication. Follow observed Console state;
button names and requirements can change. Recheck Google's linked guidance when
executing the relevant steps. Use an available supported connector or browser;
request a user login handoff if needed, never their password.

## 1. Establish current state

- Verify developer account, package, intended track, countries/testers and rollout
  percentage. Derive these from current authorization, not a historical release.
  Do not expand testing to production or widen audiences automatically.
- Inspect the track, artifact library, policy/setup blockers, Publishing overview
  and managed-publishing setting. Record pending changes, including unrelated
  listing or other-track changes. Do not discard existing drafts to clear a path.
- On resume, reconcile this live state with the saved release record before any
  mutation. If an upload or submission timed out, determine whether it succeeded
  before retrying; an unknown outcome is not permission to duplicate the action.

## 2. Prepare or reuse the exact candidate

For a new binary, inspect uploaded version codes across tracks/library, choose a
higher unused code and the intended version name, and record the delivered source
revision after any version edit. Complete the shared release checks on this
candidate. Follow repository branch/review rules for source or version edits;
release work does not grant merge authorization.

For promotion, reuse the exact verified Play library artifact. Do not rebuild,
resign or increment its version merely to move it to another track. Confirm that
its original validation is complete and applicable, and recheck current policy
and publishing materials for the destination audience.

Sign only with the authorized upload-key setup. Verify package, version, release
flags/permissions, signing certificate and bundle integrity in the final AAB;
record its SHA-256 and retain the matching R8 mapping. Distinguish the upload-key
certificate from Play's app-signing certificate used on installed APKs. Never
create/rotate keys, use debug signing, expose passwords, or upload `.qa`/`.adsqa`
artifacts as a workaround for missing release credentials.

## 3. Review publishing materials

Complete [the materials checklist](publishing-materials.md), preparing corrections
locally within scope. Record the exact text and asset files/hashes to be submitted,
with ready / needs changes / needs owner decision for each item. Resolve release
blockers before submission; test builds do not establish policy compliance.

## 4. Prepare the authorized Console draft

When draft/upload actions are authorized, open the intended track and create or
resume its matching draft. Upload the verified AAB or select its existing library
entry; enter the reviewed localized release notes. Check retained bundles as well
as the new selection, device compatibility changes, and Play warnings/errors.
Resolve concrete errors without weakening checks or inventing disclosures.

Save only when the observed action keeps the release in draft/preparation. Read
back the saved version, notes and selected assets. Inspect Publishing overview
again; a button labelled Publish may make changes live immediately. Do not cross
that boundary in preparation mode. Managed publishing is not a universal hold:
edits to existing release notes, tester-list membership and rollout expansion to
100% can take effect outside that hold. Inspect the specific action and obtain
missing authorization for its actual effect before saving it.

## 5. Review the exact action and submit

Present package, revision/version, artifact identity, track, audience, rollout,
materials review, checks and limitations. Include every change that the Console
will submit together, and whether approval can make it live automatically under
the current publishing settings. Isolate unrelated pending changes if supported;
otherwise stop that action and ask the owner to resolve its scope. Never silently
submit another release or listing change.

Use existing explicit approval if it covers this exact action and its effects.
Otherwise obtain approval now, after the release is concrete and reviewable. Under
standard publishing, review submission may lead to automatic publication; approval
must cover that consequence. Do not change managed publishing just to avoid an
approval boundary. Recheck live scope immediately before acting.

Submit for review or start the authorized rollout as appropriate to the observed
state. Resolve policy rejection/setup requirements before retrying. Do not promise
a review completion time or interpret a pending review as a failure.

## 6. Verify and hand off

Reopen Publishing overview and the target release. Record the exact displayed
state and timestamp, version, countries and rollout percentage. Distinguish:
draft / ready to send / in review / ready to publish / available / rejected or
blocked. An accepted click or saved draft is not proof of availability.

When available, verify the intended tester/store access and installed version on
an explicitly selected suitable test device. For an update, exercise an in-place
upgrade from the previous distributed version, preserving history and settings,
then smoke-test current core flows. Do not uninstall/clear data to make that check
pass. Report distribution evidence and installed-upgrade evidence separately;
missing device/account access leaves the latter unverified.

Preserve the [release record](release-record.md), artifacts and next action. If
review is still pending, hand off that state. Monitoring, rollout expansion,
halting or rollback requires the applicable owner authorization; do not schedule
follow-ups or change a rollout merely because this procedure mentions them.

## Official sources

Checked when authoring on 2026-09-26; recheck relevant guidance at execution time.

- [Prepare and roll out a release](https://support.google.com/googleplay/android-developer/answer/9859348?hl=en)
- [Control review and publication timing](https://support.google.com/googleplay/android-developer/answer/9859654?hl=en)
- [Android app signing](https://developer.android.com/studio/publish/app-signing)
