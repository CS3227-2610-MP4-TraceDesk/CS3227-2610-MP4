# Shared UI foundation specification

Status: specified; production implementation and verification pending.

## Goal, roles and boundaries

Reporter, Developer and QA reuse the same shell and visual language.
The latest detailed reference covers QA. Reporter/Developer production behavior
still follows its owning feature specification, not a guess from QA screenshots.

Use [the design system](../../docs/ui-design-system.md), [reference set](../ui/reference/README.md),
and [shared role contracts](../decisions/002-shared-role-contracts.md).
Domain state, authorization, sessions, persistence, deployment and AI provider
implementation remain with their owning contracts. UI sample data cannot assert
that those features are implemented. No agent may use browser role selection as
production identity or call the raw model transport from the browser.

## Layout and use cases

### Navigate and open a bug

QA's top rail contains Verification and Test templates; Settings stays at the
bottom. Verification sidebar lists bugs grouped Awaiting verification and Reviewed
by me, with a search field. It lists actual items rather than duplicating the
queue filter tabs. The library queue supports its specified filters and opens the
selected bug. Contextual sidebars can collapse, and overlay on mobile. Preserve
the rail when a sidebar is collapsed. Keep the account control compact at bottom.

Reporter rail contains Reports and Environments plus bottom Settings; submitted
reports and drafts live within Reports. New report is a button within Reports.
Developer navigation remains subject to its owning feature scope. Do not invent
Help, dashboards or destinations merely to fill rail space.

### Review a bug

The top header has the selected bug title and contextual controls. No breadcrumbs
or fake browser/native window titlebar are added. Summary and Verification live
in the compact toolbar; Review with AI is the trailing pill.

Summary keeps status/ID, title, project/build/check metadata, report details,
proposed fix, Activity and a comment entry point. Clarification requests use the
existing authorized discussion flow. The optional Change map is a 300px unboxed
column with file names, changed-line proportions and text addition/deletion
counts. Map bars represent each file's additions plus deletions divided by the
total changed lines for this patch. Empty or zero-total data must avoid invalid
bar widths; unavailable data reads No patch attached. It has no pin control or
separate checklist, decisions, or repeated fix explanation.

Opening Review with AI reveals that bug's conversation beside its review document
and hides the optional Change map. On narrow workspaces the panes stack. Closing
AI restores the document width. Switching bugs restores each bug's own tab,
conversation and draft. Another bug's text/results cannot leak into the selection.
Essential report, fix and verification content stays accessible without the map.

The existing summary popover is a contextual shortcut to this bug's resources,
not a pinned PR summary. Use its actual SDK anchor, grouped rows, available-width
clamp and keyboard focus handling from the reference CSS and component behavior.

### Maintain a template

The template library opens a settings-style editor. A rounded details group has
Name, When to use, and Preconditions. Test cases are compact expandable rows with
Check name, Steps and Expected result. Add opens a new case; remove affects only
that case. Expansion survives typing and rerenders. Require a nonblank template
name and at least one case with a unique nonblank name. Context, steps and expected
result are optional for compatibility with existing title-only templates.

Applying a template copies case instructions/preconditions into the selected bug's
checks as a snapshot. Existing check results/notes are not overwritten, and duplicate
check names are not appended. Later template edits/deletion do not mutate applied
checks. Pass/fail, observed results and evidence belong to the bug's verification.
Persistence and API validation for templates are future feature integration work.

### Verify and record a result

Verification owns Board/List, check selection, Pending/Pass/Fail results, case
instructions, observed notes, template application, AI suggestion preview, and
authorized close/reopen. Preserve existing prototype interaction order. Shared
domain criteria, object authorization and version checks decide which operations
are actually allowed. A disabled UI control cannot replace a server-side check.

## State and failure handling

Treat code/logs/AI output as untrusted text. Do not render arbitrary HTML or execute
submitted code. Preserve user input on recoverable failure. Expose field validation,
empty and no-match states, loading, denied, unavailable and stale responses using
the existing component language. Do not show a successful mutation before the
owning operation confirms it. AI unavailability leaves ordinary review usable.

Map labels to ADR 002's domain states. Ready for QA/Awaiting verification maps to
READY_FOR_QA, Investigating to IN_PROGRESS, and Closed to CLOSED. Clarification
and reopen are workflow/audit facts with owning domain rules, not permission to
create extra database status values. The prototype's remaining labels are demo
presentation until the owning feature contract resolves them.

Diff viewing was discussed as an optional next capability, but is not implemented
by the reference. Do not add an inert View diff button. Sample Change map counts
are explicitly synthetic. A real patch upload/API/viewer needs its own bounded
specification before integration.

## Acceptance criteria

| ID | Observable requirement | Verification |
| --- | --- | --- |
| TD-UI-01 | Shell uses shared tokens, rail/sidebar/frame geometry and contextual navigation; no breadcrumbs or pin actions | Reference captures, DOM bounds, keyboard navigation |
| TD-UI-02 | Light/dark themes use specified shell palette and SDK semantic states consistently | Token assertions and light/dark captures |
| TD-UI-03 | Correct component variants and typography are used by purpose, including compact review capsules | Computed styles against the design-system tables |
| TD-UI-04 | Summary has centered max1152px width, 24px inset, 300px optional column/40px gap at800px pane width, measured toolbar spacing | Bounds at799/800px pane widths, wide screens, sidebar states |
| TD-UI-05 | AI opens/closes for the selected bug, hides map, preserves per-bug context, and stacks at650px workspace width | Two-bug switching, drafts, narrow/wide interaction checks |
| TD-UI-06 | Template fields and expansion/save/remove/validation work; applying copies instructions without overwriting checks or following later template edits | Component and role-flow tests including legacy templates |
| TD-UI-07 | Controls remain reachable at narrow widths and zoom; main frame does not clip trailing actions | 320/390/1440px tests, native-window smoke, effective viewport recorded |
| TD-UI-08 | Keyboard labels/focus/tab/disclosure behavior, contrast and reduced motion are verified | Focused accessibility scan plus manual keyboard/reduced-motion checks |
| TD-UI-09 | Untrusted content renders safely; unavailable/denied/stale states are explicit without fabricated success | Synthetic text/AI failure fixtures and integration tests when APIs exist |
| TD-UI-10 | Production integration retains real identity/security contracts; demo role switching, AI and file counts remain isolated fixtures | Review imports, feature integration checks and owning security tests |

All criteria remain pending for the production frontend. Screenshots of the
prototype and a passing documentation check do not mark them implemented.

## Decisions still owned elsewhere

Final logo artwork, extra product destinations, production diff ingestion,
API signatures for role features, and unsaved-navigation policy beyond the current
prototype require explicit feature decisions. Do not invent groupmate ownership.
