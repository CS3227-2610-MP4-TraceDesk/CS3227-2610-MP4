# TraceDesk UI reference set

Captured on 2026-11-07 from the team's local prototype after the user accepted
the QA Change map, compact review pills, and expanded template fields. These are
implementation references for that direction, not claims that the production
frontend implements it or that each capture received separate human sign-off.

Read the [design system](../../../docs/ui-design-system.md) and
[shared UI criteria](../../specs/shared-ui.md) before comparing images.

## Captured states

Desktop images use a 1440×900 CSS viewport and device scale 1. The mobile capture
uses 390×844. All use reduced-motion mode, synthetic fixtures, Windows system
fonts and a separately launched headless Chrome context. Exact browser version,
theme, state and measured dimensions are in [capture metadata](capture-metadata.json).

| Reference | Screen/state |
| --- | --- |
| [QA queue](images/qa-queue-dark.png) | Dark, sidebar expanded |
| [QA Summary](images/qa-summary-dark.png) | Dark, sidebar expanded, TD-023, AI closed |
| [QA Summary with collapsed sidebar](images/qa-summary-collapsed-dark.png) | Dark, TD-023, AI closed |
| [QA with AI review](images/qa-ai-review-dark.png) | Dark, sidebar collapsed, TD-023 AI open, map hidden |
| [QA Verification](images/qa-verification-dark.png) | Dark, sidebar collapsed, Board view |
| [Template library](images/qa-templates-dark.png) | Dark, sidebar collapsed, default saved templates |
| [Template editor](images/qa-template-editor-dark.png) | Dark, Export checks, first case expanded |
| [Appearance](images/appearance-light.png) | Light, sidebar collapsed |
| [QA Summary in light mode](images/qa-summary-light.png) | Light, sidebar collapsed, TD-023 |
| [Mobile Summary](images/qa-summary-mobile-dark.png) | Dark, sidebar collapsed, map hidden |
| [Supplied palette chart](images/openai-colour-reference.png) | User-provided OpenAI light/dark palette reference |

Only the current QA/layout states above are frozen by this artifact set. Reporter
and Developer reuse the shared identity; additional detailed role references
should be captured when their UI slices are specified. Review screenshots do not
establish production authentication, AI behavior, authorization or persistence.

## Source and dependency identity

[Source identity](source/identity.json) records SHA-256 and size for prototype
source files. The prototype was an unversioned design workspace, so no Git commit
is invented for it. Matching [reference CSS](source/style.css),
[prototype package manifest](source/package.json), and
[prototype dependency lockfile](source/package-lock.json) are retained as design
evidence. These files are not imported by the production frontend. The lockfile
records exact resolved dependency versions and does not replace the repo's own
frontend lockfile.

The retained source CSS is the source for fine details omitted from a numeric
table. It includes some earlier/demo selectors; use only the current component
and matching screen state. Future porting should extract shared responsibilities
rather than copy unused selectors or obsolete prototype behavior.

## Reproduce a visual comparison

1. Open the implementation under test in an isolated browser context. Set the
   viewport, scale, OS/font, reduced motion and theme to match metadata.
2. Load deterministic synthetic data equivalent to TD-023 and the sample export
   template. Select QA, then the named destination and state in the table.
3. Ensure fonts and layout are ready. Capture the viewport without browser chrome
   and without changing the user's visible browser's emulated size.
4. Compare the corresponding image and computed bounds. Inspect toolbar/action
   visibility, container centering, map visibility, text wrapping and focus.
5. Record browser/font differences separately from layout regressions. A viewport
   screenshot can look correct while an emulated page extends beyond a native
   window; verify native-window accessibility for that scenario too.

The reference rendering is a visual baseline, not a blanket accessibility pass.
Keyboard, contrast, reduced-motion, error and integration checks remain required.

## Source limitations and known differences

- AI responses, file-change counts and sign-in role buttons are synthetic demo
  behavior. Template data is in memory and resets on reload.
- The QA title-only template implementation was expanded to include optional
  context and structured cases. Applying a template copies instructions into a
  bug's checks; later template changes do not change that snapshot.
- A diff viewer and real patch ingestion are not present in the captured build.
- The prototype shell colours intentionally differ from the supplied generic
  palette in some places. Follow the design-system token table and resolved SDK
  palette rather than transcribing small screenshot labels.
- Full pixel identity across Windows/macOS fonts is not promised. Preserve the
  stack, geometry and hierarchy, and record the actual comparison environment.

## Updating this set

Preserve a rationale and criterion mapping for any baseline change. A failed
comparison is a finding to investigate, not authorization to overwrite the
reference. Change the spec, design-system values, source identity and affected
captures together when the team's intended design changes. Keep file names and
links portable; never require a teammate's machine paths or personal skill cache.
