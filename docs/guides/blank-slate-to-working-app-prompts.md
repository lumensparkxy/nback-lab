# From blank slate to working app: a prompt playbook

**N-back edition — a separate companion to the LinkedIn post.**

These are reusable prompts reconstructed and refined from the N-back development
workflow. They are **not a verbatim transcript** of the original conversations.
The sequence makes the decisions, tools and evidence explicit so someone else can
follow it. It does not promise a particular delivery time or eliminate product
decisions.

## How to use this

Start in a **new project directory**. Paste one numbered prompt at a time, inspect
the output, answer unresolved product questions, and continue when its checkpoint
is satisfied. Do not paste the whole guide as a single instruction. Reading this
guide does not authorize the actions in its example prompts.

Replace `{{placeholders}}` before sending a prompt. Approval prompts are for the
owner to send after inspecting the named specification, change or release. An
agent must not generate approval on the owner's behalf.

The examples use Android, Kotlin and Jetpack Compose. To apply the process to a
different app, replace the product brief and platform choices before starting;
keep the specification, implementation, review and verification loop.

You need a coding agent with project-file and terminal access. Android validation
also needs a compatible JDK/SDK and an available emulator. Prompt 2 checks those
prerequisites. GitHub steps need an authenticated account and repository access.
Use an emulator serial discovered on your machine, not a serial copied from an
example. No AI API is needed inside this N-back app.

| Route | Prompts | Result |
| --- | --- | --- |
| First working app | 1–5 | A complete playable session, tested and inspected locally, with a reviewable PR |
| Accepted delivery | 6 | Reviewed work merged with recorded evidence |
| Richer product | 7, then repeat 5–6 | Additional features delivered one agreed scope at a time |
| Reusable workflows | 8, when useful | Skills and commands grounded in observed work |
| Distribution | 9–10 | Release preparation, then a separately authorized store submission or rollout |

## 1. Turn the idea into a bounded product

**Paste into the new project.** This first conversation establishes the outcome
and proposes the development approach; it does not build gameplay.

```text
I want to build N-back, an offline Android game, and use it to establish a reusable
workflow for working with coding agents.

First inspect this directory and any existing instructions. Preserve existing
work; do not assume that an existing repository is empty.

The first useful experience is: understand the instructions, start a short visual
position-matching session, complete it and understand the result. Begin with fixed
2-back on a 3×3 grid. Defer accounts, a backend, analytics, ads, audio, adaptive
difficulty and saved history. Describe it as a working-memory exercise; do not
promise medical benefits or increased intelligence.

Use Kotlin and Jetpack Compose, with a pure Kotlin engine for rules, timing and
scoring. Make time and randomness controllable in tests.

Before implementation, propose:
- A one-page product brief, the first complete user journey and explicit exclusions.
- The product decisions we need to settle before the first feature is ready.
- The smallest practical architecture and its tradeoffs.
- A portable coding-agent harness: durable instructions, specifications, task
  tracking, build/test commands, independent review and emulator verification.
- A dependency-ordered sequence of small deliverable features.

Save the proposal in project documentation and show me the decisions that require
my input. Recommend defaults and explain their consequences. Do not mark draft
decisions as accepted or implement gameplay yet.
```

**Checkpoint:** You can explain the first user journey and its exclusions in a
few sentences. The agent has proposed a concrete bootstrap scope you can approve.

## 2. Build the approved harness before gameplay

**Send after reviewing the blueprint from prompt 1.** Select an existing emulator
and replace the serial below. If none is available, the agent should diagnose the
prerequisite and leave device verification explicitly incomplete.

```text
I approve the bootstrap blueprint at {{BLUEPRINT_PATH_OR_REVISION}}. Build the
development harness and a launchable Android shell within that scope. This
authorizes local implementation, tests and routine repairs, not gameplay or
external publication. Use emulator {{EMULATOR_SERIAL}} for device checks.

Create a clear entry point in README.md and a project AGENTS.md. Keep product scope,
feature behavior, architecture decisions, task status and validation evidence in
distinct places. Use docs/product.md, docs/features/, docs/decisions/ and local
bootstrap issue drafts until GitHub is activated.

Define the operating loop: one implementation writer per working directory;
read-only planning/review; independent verification when useful; owner decisions
for scope changes, major architecture, merges, signing identity and publication.
Each delegation needs scope, allowed actions, acceptance criteria, expected
evidence and a stopping condition. Existing owner approval remains valid.

Build the app shell and pure Kotlin engine module. Inspect the installed toolchain,
check current official compatibility documentation and pin compatible versions.
Do not copy old version numbers blindly or change global agent configuration.
Report missing prerequisites instead of silently skipping their checks.

Provide shared commands:
- scripts/doctor.sh for environment diagnosis.
- scripts/verify.sh for structural checks, JVM tests, lint and builds.
- scripts/emulator-test.sh and scripts/run-app.sh for an explicitly selected emulator.
- scripts/test-failure-gate.sh to prove that an intentional assertion failure
  produces a failing command and the expected fresh test report, then restore
  the ordinary passing test state.

Device commands must not fall back to a physical device, wipe an emulator or stop
someone else's processes. Keep credentials, private data and machine-specific
paths out of tracked configuration. Keep generated evidence outside source control.

Add issue/PR templates and CI using the shared commands. Record local checks,
hosted CI and repository protection as separate kinds of evidence.

If this agent supports project-specific roles, configure the smallest useful
adapter and verify actual role execution separately from configuration syntax.
Keep the shared workflow usable by another agent tool.

Run the local checks, launch and recreate the shell, and inspect the rendered
screen. Delegate independent review of the harness, wait for findings, repair
them and rerun affected checks. If independent review cannot run, report that
limitation and arrange separate review before claiming readiness.

Return an acceptance/evidence table with commands, revision, results, artifacts
and outstanding prerequisites. Stop at a reviewed bootstrap ready for publication.
```

**Checkpoint:** The shell visibly runs; local checks and the deliberate-failure
probe behave correctly; independent review has been resolved. A launch screen
proves the harness can run an app, not that gameplay exists.

## 3. Activate GitHub and make work traceable

**Send after reviewing the bootstrap.** Choose visibility deliberately. This
prompt explicitly authorizes initial repository publication and tracking setup.

```text
I approve publishing the reviewed bootstrap revision {{BOOTSTRAP_REVISION}} to
GitHub repository {{OWNER}}/{{REPOSITORY}}, with visibility {{PUBLIC_OR_PRIVATE}}.
Create the repository if it does not exist; if it exists, inspect it and preserve
its work. Check the outgoing content for secrets and unrelated files first.
Do not select a license on my behalf.

Publish the approved bootstrap as main. Create real GitHub issues from the local
drafts, separating the harness, first-feature specification and implementation.
Link dependencies and replace draft references with actual issue links. Issues
own live status; specifications own behavior.

Observe hosted CI. Fix scoped bootstrap failures through a branch and PR. Require
the relevant checks, pull requests, resolved conversations and no force pushes
or deletion on main, using supported repository rules. For a sole maintainer,
avoid requiring a formal self-approval that GitHub cannot provide; retain
independent review and owner merge authorization in the workflow.

Read back the repository rules and record what is actually enforced. If permissions
or the GitHub plan prevent enforcement, report the gap and an owner decision;
do not describe a workflow file as enforced protection.

Report the repository, issue links, CI evidence and any repair PR. Do not merge
a repair PR without my approval. Mark harness acceptance complete only when all
required evidence exists and I have accepted it.
```

**Checkpoint:** Actual issues and CI exist, dependencies are correct, and the
harness is accepted. If a bootstrap repair needs merging, use prompt 6 for that
specific PR before marking its dependent implementation issue ready.

## 4. Write the first feature specification

This is the most product-specific prompt. It supplies the original small N-back
contract rather than asking an agent to invent the missing game rules. Later
features can deliberately amend this baseline.

```text
Work on the first-session specification issue {{SPEC_ISSUE_URL}}. Inspect product
scope, accepted decisions and blockers. Write docs/features/F001-visual-session.md
as a draft specification; do not implement gameplay yet.

Use these proposed first-session rules:

Flow: instructions -> explicit Start -> two warm-up turns -> twenty scored turns
-> results. Use fixed visual 2-back on all nine cells of a 3×3 grid, with one Match
control. Explain A -> B -> A before play. No history, selectable difficulty,
practice mode, audio, pause/resume or networking in this slice.

Timing: each turn lasts three seconds, highlighted for one second and blank for
two. The response window includes both phases. The complete session lasts
66 seconds. Use elapsed monotonic time, not wall-clock time or recomposition.
Timestamp a completed activation when handled; reconcile elapsed time first.
An activation exactly at a boundary belongs to the new turn. Press-down alone
is not an activation. Accept at most one Match per scored turn, discard warm-up
input and reject input after completion. Holding the button must not auto-repeat.
Delayed timer delivery must not extend the session or replay missed stimuli.

Generation: create all 22 positions with injectable randomness. Select exactly six
distinct match indices uniformly from turns 3–22. At each selected index copy the
position from two turns earlier; otherwise choose from the other eight positions
so no accidental extra 2-back matches appear. Allow adjacent matches. Do not reveal
upcoming positions or correctness during play.

Scoring: report hits, misses, false alarms and correct rejections. Accuracy is
100 × (hits + correct rejections) / 20. Warm-up is excluded. Explain the counts:
no taps produces 70% accuracy but zero detected matches, so accuracy alone must
not be presented as target detection. No pass/fail or health claim.

Use this deterministic fixture, numbering cells 1–9 in row-major order:
Positions: 1,5,1,9,2,9,4,6,4,8,3,7,3,2,5,2,8,6,9,6,1,4.
Match activations: turns 3,4,6,8,9,13,15, each 500 ms after turn start.
Expected: 4 hits, 2 misses, 3 false alarms, 11 correct rejections, 75% accuracy.
Check the arithmetic independently and include a worked turn-by-turn example.

Lifecycle: rotation preserves sequence, responses and the original time origin.
Leaving the foreground during play, except configuration recreation, cancels the
session. System Back cancels too. Show an interruption state with Restart and
Home; never a partial score. Restart uses a fresh full session. Process loss
returns to Home. Completion wins when the 66-second deadline has already passed
at the time an interruption is handled. Results survive rotation/backgrounding
in the same process until a new session, Home or process loss.

Presentation: keep the grid stable in portrait and landscape; distinguish the
highlight with fill and outline. Define minimum text/indicator contrast, 48 dp
touch targets, usable 200% text size, semantic controls and accessible instructions
and results. Disclose that this first version is a visual task with fixed timing;
do not claim a nonvisual equivalent. Include neutral response acknowledgement,
warm-up/scored progress, Play again and Home behavior.

Assign stable acceptance IDs. Map each to an engine test, device test or explicit
manual check. Cover normal use, exact boundaries, duplicate input, delayed events,
interruption and process loss. State dependencies and any unresolved choices.

Have an independent reviewer challenge the specification for contradictions and
untestable wording. Resolve editorial ambiguities; bring any product-rule conflict
back to me. Prepare the specification PR and show the reviewed revision for approval.
Do not mark it agreed or merge it on my behalf.
```

**Checkpoint:** Every acceptance criterion has an observable outcome. The fixture
has been checked. Any remaining product decisions are answered. Inspect the spec,
then use prompt 6 to accept and merge its concrete PR before prompt 5 begins.

## 5. Implement the approved feature through a complete review loop

**Send after accepting the specification.** This is the long-running work prompt:
routine implementation, tests and repairs belong inside the authorized session.

```text
I approve the feature contract {{SPEC_PATH_AND_APPROVED_REVISION}} and authorize
implementation of {{IMPLEMENTATION_ISSUE_URL}}. Use emulator {{EMULATOR_SERIAL}}.

Read the repository instructions, linked specification, accepted decisions and
current issue state. Confirm that dependencies are accepted and no product
decisions remain open. Inspect the working tree and preserve unrelated changes.
Work on a focused issue branch, using one writer per working directory.

Implement the complete agreed user journey. Keep gameplay rules, elapsed timing,
sequence generation and scoring in the pure Kotlin engine; the Android layer
owns rendering and lifecycle integration. Add only necessary dependencies.

Derive tests from the acceptance criteria and worked examples. Include boundary
and failure cases, not just the path taken by the implementation. For discovered
bugs, reproduce the failure before fixing it and add the relevant regression test.

Run doctor.sh and verify.sh. Run the relevant emulator tests on the explicit target,
complete a real session, inspect the actual screens, and capture the required
orientation, large-text, interruption and accessibility evidence. A successful
build is not evidence that the user journey works.

Delegate independent review of the stable implementation against the specification.
Wait for findings, resolve them, rerun affected checks and obtain another review
after material fixes. Do not edit beneath an active reviewer. Each delegation
must state its scope, allowed paths/actions, criteria and expected evidence.

Continue autonomously through routine fixes in this scope. Do not weaken tests
or alter product rules to get a pass. After two attempts at the same unresolved
failure, stop speculative edits, gather evidence and revise the hypothesis.

Commit and push the scoped change, create a PR linked to the issue, and inspect
hosted checks. Include an acceptance/evidence table, reviewed revision, screenshots,
limitations and remaining decisions. Keep large/private artifacts out of Git.
If a required check cannot run, report exactly what is missing and leave readiness
incomplete. Do not merge, close the issue as shipped or publish the app.

Finish with a working app demonstration and a concrete PR for my review, or a
durable handoff identifying the real blocker and the next action needed.
```

**Checkpoint:** You can play the first complete session on the selected emulator
and see correct results. Acceptance evidence, independent review and hosted checks
refer to the actual PR revision. At this point there is a working local app.

## 6. Accept and merge a specific reviewed change

Use this for a specification, bootstrap repair or implementation PR only after
reviewing that PR. Replace the placeholders with the actual URL and full commit
SHA. For a specification PR, accepting it agrees the contract; prompt 5 separately
authorizes its implementation.

```text
I accept the reviewed scope and evidence for {{PR_URL}} at {{EXPECTED_HEAD_SHA}}
and authorize merging that revision when its required checks are passing. For a
specification PR, this records my agreement to that exact contract.

Refresh the PR head/base, checks, review findings and dependencies. Verify that
the evidence applies to the revision being merged. If the head or product scope
has changed, show the difference and obtain approval for the changed revision.
Use a merge operation guarded against an unexpected head change; never bypass
repository protection.

Verify the merged commit and target. Close only issues whose full agreed scope
is delivered and accepted. Synchronize a clean local main without overwriting
unrelated work. Preserve unique test/review evidence and record a concise handoff.
Report any post-merge checks still pending. Do not start the next feature or
delete branches, worktrees or artifacts as part of this prompt.
```

**Checkpoint:** The merged result is verified and task status matches delivered
scope. A green check, an open PR and an accepted merge are different milestones.

## 7. Grow the app one feature at a time

Choose **one** next outcome. This prompt prepares its specification and issue;
it does not authorize implementing unresolved behavior. Once agreed and merged,
reuse prompts 5–6.

```text
The next user outcome I want is {{NEXT_USER_OUTCOME}}.

Inspect current behavior and the accepted specifications before proposing changes.
Write a feature specification covering the user journey, states, defaults, edge
cases, accessibility and acceptance criteria. Identify exactly which earlier
rules it changes, and preserve the rest. Include data migration and existing-user
behavior when storage changes.

Recommend the smallest complete scope. Separate decisions that need my input
from routine implementation choices. Add an architecture decision only for a
significant tradeoff. Define tests and examples that can demonstrate correctness.

Create or update the linked specification and implementation issues, with explicit
dependencies. Independently review the proposed contract and prepare its PR.
Show me the unresolved choices and reviewed specification before implementation.
```

The N-back progression provides concrete choices for `{{NEXT_USER_OUTCOME}}`:

| Next outcome | Decisions to settle before implementation |
| --- | --- |
| Choose 1-, 2- or 3-back and learn through guided practice | Default level, warm-up length, remembered settings, practice timing/feedback and exit behavior |
| Revisit completed sessions | What gets saved, save failures/retries, ordering, retention and confirmed deletion; practice and unfinished sessions are excluded |
| Combine Position, Colour and Number | All seven combinations, independent responses/scoring, colour cues, number presentation and migration of old position-only history |
| Improve the visual design | Review visual alternatives, select one, specify all relevant screens/states, then verify the rendered result |
| Adjust pace and see accuracy through a session | Interval/exposure rules, cumulative-accuracy denominator, accessible chart data and compatibility with older history |
| Use dedicated Settings, Results/Progress and How to Play | Session lengths, navigation, comparable configurations, instructions and preserved user data |

The table follows the main product progression, with later work grouped for
reuse. Monetization was a separately approved extension in the real project;
it is not a prerequisite for a working N-back app or for this sequence.

## 8. Turn repeated work into reusable skills

**Optional, after observing a recurring need.** Establish reliable commands and
evidence first. A skill can then describe how and when to use them.

```text
Review the completed work for {{REPEATED_WORKFLOW}} and identify the repeatable
steps, real failures and checks that prevented incorrect results. Propose one
small project-local skill for that workflow, using existing tools and scripts.

Define when to use it, required inputs, allowed actions, ordered steps, success
evidence, failure handling and stopping conditions. Link canonical specifications
and commands instead of copying them into competing instructions. Keep permissions
and owner approvals explicit; skill instructions cannot create extra authority.

Show the proposed scope before installing new dependencies or making architectural
changes. Within the approved workflow, create the project-local skill, independently
review it and exercise it on a bounded example. Report what actually ran, the
revision used, the resulting artifacts and limitations. Keep it usable without
changing global agent configuration. Do not merge or publish it automatically.
```

**Checkpoint:** The skill has a demonstrated use and verified output. In N-back,
later reusable workflows covered release readiness, code-hygiene review and
ads/privacy review. None is necessary merely to create the first game.

## 9. Prepare an actual release

**Optional: local success does not establish release readiness.** Replace the
identity and version with owner-selected values. Use your own application ID;
do not reuse the identity of the existing N-back app.

```text
Prepare release {{VERSION_NAME_AND_CODE}} of {{APP_NAME}} using the owner-selected
application ID {{APPLICATION_ID}} and source revision {{SOURCE_REVISION}}.
The intended audience and distribution route are {{AUDIENCE_AND_ROUTE}}.

Inspect the current repository release workflow and verify the relevant current
official platform requirements. Evaluate the exact revision, not an earlier build.
Run all required checks and the complete Android suite, including tests omitted
from any faster routine CI selection. Build and exercise the optimized release
configuration; debug-build success alone is insufficient.

Check identity, versioning, permissions, persistence/upgrade behavior and actual
SDK data practices. For this offline first version, do not introduce advertising,
analytics or accounts as release work. If separately approved SDKs exist, verify
their consent, privacy and runtime behavior under their own agreed scope.

Prepare release notes, accurate listing copy, screenshots of the tested app,
required graphics, privacy/disclosure drafts and a tester guide. Avoid health
claims or features not present in this artifact. Ask for missing owner/legal
facts instead of inventing them.

Present signing identity and key-custody choices if not already agreed. Never put
credentials or signing keys in source control or logs. Record build identifiers,
checksums, review evidence and any unresolved requirements.

Stop with a concrete release package and a short list of decisions needed for
distribution. Preparation does not authorize signing with an unapproved identity,
uploading, submitting for review, creating a rollout or publishing a store listing.
```

**Checkpoint:** The exact proposed release has complete evidence and reviewable
materials. Account, identity, audience and disclosure decisions are settled.

## 10. Submit the approved release and verify its state

**Optional, consequential action.** Send only after inspecting the release record
and public disclosures. Name the precise track, audience and rollout size; internal
testing, closed/open testing and production are separate scopes.

```text
I approve the release record {{RELEASE_RECORD}} for {{APP_NAME}}, version
{{VERSION_NAME_AND_CODE}}, artifact {{ARTIFACT_PATH_AND_CHECKSUM}} and its reviewed
public listing/disclosures. Use the already agreed signing identity and key custody.

I authorize {{UPLOAD_SUBMIT_OR_ROLLOUT_ACTION}} to Google Play track {{TRACK}},
for {{COUNTRIES_AND_TESTER_AUDIENCE}}, with rollout {{ROLLOUT_SCOPE}}. This
authorization does not include other tracks or a broader audience.

Verify the exact artifact, account, identity and current track before acting.
If final signing remains necessary and is covered by our agreed process, sign
that build, verify its signature and record the final checksum before upload.
If promoting an already verified uploaded artifact, reuse it without rebuilding.

Follow the repository release procedure. Reuse existing authorization for routine
steps within scope. Bring changed public disclosures or unresolved requirements
back to me. Report any platform or tool step that requires my direct action.

After the authorized action, read back the actual status, version and track.
Verify the applicable tester/store link and its access conditions. Distinguish
uploaded, submitted, under review, available to testers and available in production.
If approval is pending, leave that explicit with the next check; do not report
publication or imply ongoing monitoring that has not been arranged.
```

**Checkpoint:** Report the state actually observed. Store review can remain
pending after all authorized work is complete.

## Three prompts for keeping longer sessions useful

These are recovery tools, not additional mandatory stages.

### Resume in a fresh session

```text
Continue {{ISSUE_URL}} from the durable handoff at {{HANDOFF_PATH_OR_LINK}}.
Read the project instructions, accepted spec, decisions and latest review first.
Inspect the current branch, working tree, issue/PR state and evidence revision.
Reconcile the handoff with current reality. Summarize what is done, what remains
and which approvals already exist, then continue the authorized unfinished work.
Preserve unrelated changes. Do not repeat completed checks unless changes,
failures or an evidence gap justify it.
```

### Diagnose a repeated failure

```text
The same failure has survived two attempts. Stop speculative edits. Capture the
exact failure and environment, identify what the evidence proves, and list the
remaining hypotheses. Run the smallest discriminating check. Fix the demonstrated
cause within the agreed scope, add a meaningful regression test when applicable,
and rerun affected checks. Do not suppress the failure or weaken acceptance criteria.
```

### Change the product intentionally

```text
I want to change {{CURRENT_BEHAVIOR}} to {{DESIRED_OUTCOME}}. Before implementing,
show the affected specifications, acceptance criteria, architecture and existing
user data. Propose the smallest amendment and explain the tradeoffs. Preserve the
rest of the contract. Obtain agreement to the changed behavior, then use the normal
issue, implementation, review and verification loop.
```

## Connection to the actual N-back history

The guide draws on the repository's real sequence. Prompt wording has been edited
for reuse, and the current product includes later amendments beyond the initial
fixed-session contract shown in prompt 4. These prompts do not replace this
repository's current operating instructions or authorize rerunning its bootstrap.

| Historical step | Repository evidence |
| --- | --- |
| Harness before gameplay | Bootstrap commit `8476a5e`; [F000](../features/F000-harness.md) and [bootstrap validation](../validation/bootstrap.md) |
| Specify, then implement the first game | Spec `63dc537` → implementation `1448853`; [F001](../features/F001-visual-session.md) and [visual-session evidence](../validation/visual-session.md) |
| Specify, then implement practice/difficulty | `7e165ec` → `5f0f146`; [F002](../features/F002-practice-and-difficulty.md) |
| Specify, then implement history | `4fec28b` → `bcca188`; [F003](../features/F003-results-and-history.md) |
| Add combinations, design and pace | `a3badca`, `d4298f5`, `4614700`; [feature index](../features/README.md) |
| Preserve the operating loop as the app grows | [Development workflow](../development.md), [roles](../agents.md), [readiness workflows](../android-readiness.md) |

For another product, carry over the process rather than N-back's game rules.
The reusable unit is an agreed user outcome, a bounded implementation task and
evidence that the delivered behavior satisfies it.
