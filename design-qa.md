# Guided Play design QA

This is the historical validation record for [F005](docs/features/F005-guided-design.md),
delivered in [PR #27](https://github.com/lumensparkxy/nback-lab/pull/27).
Iteration 1 findings below were resolved in Iteration 2. Later configurable timing
and accuracy graphs are defined separately in
[F006](docs/features/F006-session-interval-and-accuracy-timeline.md).
Artifact paths identify retained local QA evidence; the images are not tracked in Git.

Source: `artifacts/design-audit-20260921/concept-3.png` (853 × 1844 pixels).
Implementation: `artifacts/design-implementation/f005-qa/home-reference.png`
and `results-overview.png` (1024 × 2216 pixels, Compose fixture at 390 × 844 dp,
density 2.625). Both images normalized to 390 × 844 for comparison; this is native
Android, not a browser/CSS viewport. The concept has no OS chrome. The fixture's
inset region needs comparison against the actual Activity before classifying it.
State: all three types selected, 2-back; results show a completed no-response session.

## Comparison history

Iteration 1 full view: `artifacts/design-implementation/comparison-1.png`.
The original captures were also opened at readable size for cards, examples,
result summaries and controls. Independent AI visual review agrees with these findings.

- **P2 colour fidelity:** orange fill/border is too strong; navy leans purple.
  Use a pale selected-card surface and subdued border, with blue navy ink.
- **P2 result continuation:** History precedes Play again, pushing the primary
  continuation below the initial viewport. Put Play again before secondary links.

## Required surfaces

- Typography: native sans-serif hierarchy and wrapping retain the intended
  emphasis; larger native controls are an accepted accessibility adaptation.
- Spacing/layout: two steps, separators, example and CTA preserve the direction.
  Actual Activity inset verification and post-fix capture remain pending.
- Colours/tokens: needs the P2 correction above; complete theme roles implemented.
- Asset fidelity: official Material Symbols replace conceptual standard icons;
  no raster placeholders. The actual stimulus renderer includes the Number digit
  and stable palette, correcting the illustrative reference's missing attribute.
- Copy/content: brief card labels and accurate n-dependent example are accepted;
  expanded help preserves complete rules. Results use neutral outcome definitions.

## Implementation checklist

1. Correct palette and result action order.
2. Capture actual Activity Home, practice, play, results, history and detail.
3. Recompare full view and focused controls; inspect large-text selector states.
4. Resolve independent source review and rerun affected checks.

## Iteration 2 — resolved

Revision `ff27d6b`. Full comparison: `artifacts/design-implementation/comparison-2.png`.
Focused comparison: `artifacts/design-implementation/controls-comparison-2.png`.
Revised captures: `artifacts/design-implementation/final/f005-qa/`.

Both P2 findings are resolved: pale peach surfaces use separate tokens, navy is
blue-toned, and Play again precedes secondary navigation. Miniature border inset
and stroke now scale to keep Number digits readable. Independent visual rereview
found no remaining P1/P2 issues; the source rereview found no unresolved findings.

Actual Activity capture: `final/home-device.png` under the same artifact directory
(1080 × 2424, 411 × 923 dp at 2.625 density). OS status/navigation insets account
for the extra top/bottom regions absent from the concept; Start, practice and Help
are all visible. `final/practice-device.png` and `final/practice-bottom-device.png`
show grouped feedback and reachable Next/Skip controls. `final/home-font2-top.png`
shows all three complete card labels and descriptions at 200% system text.

`final/f005-qa/activity-history.png`, `activity-detail.png`, `detail-font2-1.png`,
`detail-font2-2.png`, `history-font2-1.png` and `history-font2-2.png` were inspected.
Result text wraps inside its cards; partially visible content at scroll viewport
edges remains reachable. Native typography, standard icons, accessible control
sizes, shortened card copy and accurate examples are accepted adaptations.
No raster art is approximated. No shadows/gradients are needed for the native skin.

The revised results overview exposes all three types, the no-response caveat and
Play again. No unexplained colour-role or action-hierarchy mismatch remains.
The full and focused comparisons cover typography, spacing, colour, assets and
copy; all five required fidelity surfaces have been checked.

## Verification and limits

- Local verify script: passed on the final source.
- Final focused instrumentation: 14/14 passed, including real Activity recreation,
  200% portrait/landscape, all-seven-mode response layout and colour-rendering checks.
- Complete instrumentation: 61/61 passed at 106bac3 (app source unchanged since ff27d6b).
- Test portability corrections at 106bac3: 6/6 design/lifecycle checks passed on
  a 320 × 640, 160dpi display, matching CI. Logical reference viewports are fitted
  to available pixels without changing logical dp; system insets remain host-derived.
  The no-scroll Start test checks full layout bounds against every ancestor clip.
  Screenshots were inspected; device size/density and font scale were restored.
- Final-revision build/lint/harness/JVM verification passed. Final full local suite passed; hosted quality, Android UI and final gate
  passed in run 35626053052 on revision 106bac3.
- Help reset is checked with a fresh in-memory owner, not an end-to-end process kill.
- TalkBack itself was not manually operated. Semantic labels/state and target sizes
  are covered by instrumentation; screenshots alone do not prove screen-reader UX.
- The app is visual with fixed timing, as disclosed in its Help; no audio mode added.

Implementation checklist complete. No actionable P0/P1/P2 visual findings remain.

final result: passed

## Delivery verification

- [Fresh PR #27 CI](https://github.com/lumensparkxy/nback-lab/actions/runs/35648686738)
  passed at `64c5694`, whose files exactly match the reviewed `106bac3` revision.
  The ancestry-only update was independently reviewed before merge.
- [Post-merge CI](https://github.com/lumensparkxy/nback-lab/actions/runs/35651054335)
  passed at `4614700` after F004, F005 and F006 landed, including all 69 Android
  tests. This is development validation, not signed production-release validation.
