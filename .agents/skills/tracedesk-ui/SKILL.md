---
name: tracedesk-ui
description: Implement or review TraceDesk frontend identity, shared components and role layouts against its design system and captured prototype. Use for UI screens, styling, navigation, forms and visual verification.
---

# TraceDesk UI workflow

## Select bounded context

Read root `AGENTS.md`, [frontend guidance](../../../frontend/AGENTS.md),
[design system](../../../docs/ui-design-system.md), and the applicable part of
[shared UI specification](../../../workflow/specs/shared-ui.md). Locate the
matching screenshot/state in [reference manifest](../../../workflow/ui/reference/README.md).
For behavior changes also use `tracedesk-spec-development`. Read only the owning
role/API contract and source needed for the current slice.

## Implement

1. State the affected criterion IDs, layout family and current shared component
   to extend. Resolve any source conflict before editing the affected behavior.
2. Reuse shell/tokens/SDK variants and preserve their per-purpose dimensions.
   Carry the relevant distilled Apple interaction rules from the design system
   into the implementation; no external Apple skill is required.
3. Keep changes within the assigned files. Coordinate shared token/interface
   changes through one integrator using the repo harness when agents are used.
4. Preserve identity/security contracts and isolate demo behavior in fixtures.
   Apply AI suggestions through the normal authorized explicit action only.
5. Preserve input, selection and focus across supported pane changes. Complete
   loading, empty, error and keyboard states for the behavior in this slice.

## Verify and hand off

- Run the existing frontend check/build and focused interaction tests. Verify
  per-bug context, template snapshots, sidebar and AI transitions when affected.
- Compare the actual UI against the matching reference state, not a different
  theme or generated concept. Inspect computed dimensions for the toolbar,
  content bounds and trailing action rather than estimating screenshot pixels.
- Capture viewport, scale, OS/font, browser, theme, fixture and source revision.
  Exercise 320/390px and desktop widths plus container breakpoints when relevant.
  Use a separate browser context; restore any human-visible emulation changes.
- Check visible labels/focus, keyboard behavior, contrast, reduced motion and
  safe rendering. Run the relevant accessibility checks and record limitations.
- Report changed components, criterion results, screenshot paths and remaining
  gaps using the existing evidence/handoff templates. New baselines need an
  explicit rationale; never regenerate them just to hide a regression.

Use `tracedesk-review` for independent review when required and available. Do not
claim a completed production feature from a prototype screenshot or documentation
check. This skill is a repeatable workflow, not proof that it has been followed.
