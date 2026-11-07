# TraceDesk UI and visual identity

Status: implementation contract for the UI foundation. The reference prototype is
captured under [UI reference](../workflow/ui/reference/README.md). Production
frontend implementation and its acceptance checks are pending.

## Identity and source precedence

Use the product name **TraceDesk**. Use sentence case for interface text. Describe
actions with verbs and state what a user can do next in empty and error states.
The rounded beetle direction was approved in chat on 2026-11-07. Its
[editable SVG](../frontend/public/brand/tracedesk-beetle.svg) uses separate shell,
head, eyes, pupils, antennae and leg groups for future interaction. The SVG is a
vector trace of the approved reference. The compact
[logo](../frontend/public/brand/tracedesk-logo.svg) uses a compact filled beetle
with short thick antennae, six legs and a lower-shell split. The
[favicon](../frontend/public/brand/tracedesk-favicon.svg) places that mark on an
off-white rounded square. The sign-in header uses the compact logo inside a
32 px off-white circle, with an 8 px gap before an 18 px semibold TraceDesk
wordmark. The full mascot supplies the expressive sign-in animation.
Role icons come from the Apps SDK UI icon set. Keep decorative branding restrained.

Resolve implementation questions in this order:

1. The applicable behavior specification and the shared security/lifecycle contract.
2. Explicit numeric values and component roles in this document.
3. Captured prototype CSS and the corresponding reference screenshot/state.
4. Default behavior of the pinned SDK version.

Report conflicts with their source and affected screen. Generated concept images
are exploratory and do not override the captured implementation. This document
distills the relevant Apple design guidance so teammates need no personal skill
installation. Codex's private components are references, not npm dependencies.

## Palette

The supplied [OpenAI light/dark colour reference](../workflow/ui/reference/images/openai-colour-reference.png)
is retained in the repository. The table below is the exact shell palette already
used by the accepted prototype. Its custom shell values differ from parts of that
chart. Use this table for the shell and SDK semantic tokens for SDK controls.
Do not independently sample screenshots or introduce another palette.

| Token | Light | Dark | Intended use |
| --- | --- | --- | --- |
| `--td-page` | `#ffffff` | `#181818` | Main reading and work surface |
| `--td-sidebar` | `#f9f9f9` | `#1c1c1c` | Contextual navigation and grouped form surface |
| `--td-rail` | `#ededed` | `#212121` | Outer frame and icon rail |
| `--td-pressed` | `#eaeae8` | `#303030` | Navigation selection and neutral active surfaces |
| `--td-ink` | `#1a1c1f` | `#dfdfdf` | Primary shell text and icons |
| `--td-muted` | `#666665` | `#ababab` | Secondary shell text and icons |
| `--td-line` | `#e8e8e6` | `#ffffff14` | Separators and subtle borders |
| `--td-float` | `#ffffff` | `#282828` | Pills, popovers and composer surface |

SDK text and icon hierarchy uses the following resolved values from package
0.2.2. Inherit icon colour with `currentColor`. These are runtime values from the
installed package, not estimates of the chart's small labels. The raw results
are retained in [capture metadata](../workflow/ui/reference/capture-metadata.json).

| SDK hierarchy token | Light | Dark |
| --- | --- | --- |
| `--color-text` | `#0d0d0d` | `#ffffff` |
| `--color-text-secondary` | `#5d5d5d` | `#afafaf` |
| `--color-text-tertiary` | `#8f8f8f` | `#8f8f8f` |
| `--color-text-inverse` | `#ffffff` | `#0d0d0d` |

| Semantic accent | SDK token | Light / dark | Use |
| --- | --- | --- | --- |
| Blue | `--color-text-info-soft` | `#004f99` / `#339cff` | Change-map bars and informational emphasis |
| Green | `--color-text-success-soft` | `#008635` / `#04b84c` | Passed checks and verified success |
| Orange | `--color-text-warning-soft` | `#923b0f` / `#fb6a22` | Awaiting verification and attention |
| Red | `--color-text-danger-soft` | `#ba2623` / `#fa423e` | Failed checks, validation and destructive actions |

Use SDK `Badge` colours `info`, `success`, `warning`, `danger`, and `secondary`
for corresponding status surfaces. A label or icon must accompany status colour.
Use tertiary text only for supplementary information after contrast verification.
The prototype's `#4592ed` library icon and `#4588f3` focus outline are recorded
in the CSS reference; consolidation into accessible semantic tokens is a planned
UI-foundation task, not evidence that all prototype contrast has passed.

## Typography

Use `-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif` as the single UI
stack. Windows captures use Segoe UI. Font rendering differs across operating
systems; compare pixels on the same OS/font, browser, viewport and scale.
No proprietary font file is bundled or licensed through this project.

| Location | Size / line height | Weight | Tracking |
| --- | --- | --- | --- |
| Default shell body | 14px / 1.5 | 400 | Normal |
| Library/settings page heading | 24px / 1.16 | 650 | -0.034em |
| Form heading | 28px / 1.16 | 600 | -0.034em |
| Bug title in top header | 14px / 24px | 500 | Normal |
| Bug title inside Summary | 22px / 1.25 | 600 | -0.015em |
| Summary/Verification tab | 13px / 18px | 430 | Normal |
| Review with AI text action | 13px / 20px | 430 | Normal |
| Sidebar caption / metadata | 13px / inherited body leading | 500 / 400 | Normal |
| Form field label | 14px / inherited body leading | 600 | Normal |
| Change-map filename / counts | 12px / 11px size respectively | 400 | Normal |

Keep content hierarchy independent of the generic `h1` style. The top header,
Summary title, library heading, and form heading have different roles and sizes.
Use monospace only for code, logs, line numbers and diff text.

## Shell, layout and spacing

All measurements are CSS pixels. Size includes padding and borders under
`border-box`. Preserve stable scrollbar space when centering review content.

| Element | Contract |
| --- | --- |
| Outer frame gutter | Top/right/bottom 4px, left 0px before the icon rail |
| Rail | 48px wide, 34px buttons with 10px radius; Settings anchored at bottom |
| Main frame | 12px radius, subtle 1px border, flexible remaining width |
| Contextual sidebar | 275px preferred; exact prototype expression `clamp(240px, 275px, min(520px, calc(100vw - 320px)))`; mobile overlay rule below |
| Main and AI headers | 52px high; review header inline inset 8px |
| Review toolbar | Minimum 46px high, 4px block and 16px inline padding, 8px gap |
| Review pill group | 32px high, 2px inset and inter-tab gap, full rounding |
| Review pill button | 28px high, 8px inline padding; selected tab uses ink at 5% alpha |
| Review Summary | Width 100%, maximum 1152px including padding; centered horizontally |
| Summary padding | 8px top, 24px inline and bottom |
| Unwrapped pill bottom to status row | 15px from centered 32px group in 46px toolbar plus 8px content inset |
| Change-map column | 300px wide, 40px gap from reading column, 6px top padding, sticky top 8px |
| Change-map rows | 16px separation, 6px label-to-track gap, 3px bar height |
| Library page | 32px block / 48px inline padding, 24px section gaps |
| Library search and rows | Search 240px wide; rows minimum 50px high with 8px block padding |
| Form | Maximum 816px including padding, 28px top / 24px inline / 48px bottom |
| Form sections | 36px below heading, 32px between sections |
| Grouped form details | 16px padding, 16px radius, 1px line border |
| Short field row | Label then control columns, 24px gap; control at least 200px or 42% |
| Template case | Expandable compact row with labelled fields inside; exact CSS in reference |
| Comment composer | 24px radius for thread composer; inline review comments use 12px radius |

The pills track the pane edges; the reading content tracks the centered Summary
container. For available scrollport width W, container width is min(W, 1152px),
and text starts 24px inside that container. Its outer margin grows when W exceeds
1152px. Do not hard-code the larger screenshot margin as a constant page inset.

At a Summary container width of at least 800px, show the 300px Change map.
Hide it below that width and whenever this bug's AI review is open. At a review
workspace width of 650px or less, stack AI above the document. At a browser
viewport width of 700px or less, use a 40px rail and an overlay sidebar; forms
become one column. Library mobile padding is 24px 12px 20px. Summary content
keeps its 24px inset and toolbar keeps its 16px inset. Allow toolbar pills to
wrap at very narrow widths; the 15px gap describes the single-row toolbar only.

## Corner radii and nested surfaces

Use the same radius for the same component purpose and size across roles, themes
and pages. Reuse the shared component or existing SDK radius token. All values
below are CSS pixels; pills and circles use the geometric values shown.

| Radius | Current use in the reference mockup |
| --- | --- |
| 2px | Thin change-map tracks; the fill inherits the track radius |
| 6px | Compact rows inside the summary popover |
| 7px | Legacy compact check-row select |
| 8px | Sidebar selections, account trigger/menu rows, detail tabs and compact QA selects |
| 9px | Legacy 28px brand and thread-entry icon containers |
| 10px | Rail/header buttons, library icon containers, QA board cards, activity rows, proposed-fix groups and report text areas |
| 12px | Main frame, mobile sidebar overlay, grid cards, QA board columns/inspectors, code panels and inline review comments |
| 16px | Grouped forms, template case groups, settings groups, summary popover and review chat bubbles |
| 24px | Sign-in card and thread composer |
| Full rounding | Primary pill actions, filter/view capsules and review tab groups; current CSS uses 999px or 9999px |
| 50% | Circular logo badge, avatars and elliptical mascot shadow |

The 7px and 9px values record existing prototype components. Use them only when
porting those components; new variants should reuse the closest applicable shared
component instead of introducing another radius value.

SDK inputs retain their per-size geometry. In the pinned SDK, radius tokens
--control-radius-sm, --control-radius-md, --control-radius-lg and
--control-radius-xl resolve to 6px, 8px, 10px and 12px at the default 16px root
font size. The sign-in Input with size xl is 40px tall with a 10px radius. Select
the component size for its purpose before choosing a radius override.

### Nested corner alignment

For an inset surface that follows a parent's rounded corner, start with
**inner radius = max(0px, outer radius - inset)**. Measure the inset from the
parent's outer border edge to the child's outer border edge. With border-box
sizing, include the parent's border, padding and any child margin in that
distance. A 16px outer radius and an 8px total inset give an 8px inner radius.

A container's own inner border edge follows the same geometry. A uniform 2px
border on the 24px sign-in card leaves a 22px inner corner. Let the browser's
border and overflow clipping produce that curve. The two full-bleed sign-in
panels share the card's clipping boundary and have a straight central divider.

Apply the inset rule to concentric surfaces that visually follow one another.
A form input placed well inside a card retains its control radius. Its corners
do not need to track the distant card corners. Likewise, QA board cards use
their documented 10px radius inside 12px board columns because they are separate
content items. Pills, circular badges and avatars retain their geometric shape.

Compare nested corners at the rendered size in both themes. Verify an even gap
around the shared corner, include border thickness in the measurement, and avoid
adding a second independent rounded outline at a clipped full-bleed edge. These
rules document the intended relationship; the prototype has not received a
complete audit of every nested surface.

## Approved component roles

Use `@openai/apps-sdk-ui` 0.2.2 as the component baseline. The prototype uses
Tailwind CSS v4 with `@tailwindcss/vite`, and Radix through `radix-ui` where needed.
Copy exact resolved dependency versions from the reference lockfile when porting.
The repo frontend already has React/Vite; install only the missing UI packages.

| Purpose | Component and variant |
| --- | --- |
| Primary save/create/submit | SDK `Button`, `color="primary"` |
| Toolbar text action | SDK `Button`, secondary ghost, `size="sm"`, explicit review pill metrics |
| Rail/header icon | SDK `Button`, secondary ghost, `uniform`, accessible name; SDK icon |
| Summary/Verification tabs | SDK ghost buttons with tab roles, selected state, keyboard handling inside shared capsule |
| Board/List and Pass/Fail/Pending | SDK `SegmentedControl`, `size="sm"`, `pill={false}` |
| Search | SDK `Input`, search type, `pill`, search adornment and explicit label |
| Single-line and long input | SDK `Input` / `Textarea`, visible associated label |
| Template picker | SDK `Select`, ghost, `size="sm"`, downward chevron |
| Account, summary, add-check menu | SDK `Popover`, anchored to actual trigger; clamp to available viewport |
| Status | SDK `Badge` with semantic colour and readable label |
| Template/Activity disclosure | Native `details`/`summary`, SDK chevron, controlled expansion where editing can rerender |
| Change map | Native labelled list/rows with CSS tracks using SDK semantic colour; counts remain readable text |

Share styling wrappers for each role. SDK defaults do not replace the explicit
dimensions above. Avoid parallel hand-built replacements for SDK controls.
Do not add decorative toolbar actions whose behavior has not been specified.

## Apple-derived interaction rules

- Reveal details progressively. Template case rows remain compact until opened;
  typing in an opened case must not collapse it.
- Preserve spatial context. Collapsing navigation changes available width while
  keeping the selected bug, active tab, and unsent work associated with that bug.
- Provide immediate hover and pressed feedback. The captured navigation uses a
  120ms background/transform transition and a 0.99 pressed scale.
- Keep surfaces quiet. Use the specified rounded groups and faint borders;
  reserve strong contrast for the primary action and meaningful states.
- Use motion to explain changes in place. Any future animated pane movement must
  be interruptible and preserve scroll/focus. A spring or motion dependency is
  not needed for this port; static native interaction is acceptable.
- Honour `prefers-reduced-motion`. Omit nonessential movement and pressed scaling
  in reduced-motion mode; the existing prototype's pressed scale needs this check.
- Provide visible keyboard focus and labelled icon buttons. Tabs work with arrow
  keys/Home/End, disclosures with keyboard, and popovers restore sensible focus.
- Measure contrast and target usability. Preserve compact desktop geometry while
  providing usable touch targets where needed and documenting deliberate changes.
  No claim of accessibility conformance follows from visual resemblance alone.

