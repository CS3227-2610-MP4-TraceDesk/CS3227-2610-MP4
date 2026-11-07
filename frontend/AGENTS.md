# Frontend UI instructions

Read root `AGENTS.md`. For UI work, follow the
[TraceDesk UI skill](../.agents/skills/tracedesk-ui/SKILL.md),
[design system](../docs/ui-design-system.md), and
[owning UI criteria](../workflow/specs/shared-ui.md). Use only the relevant
references from the [capture manifest](../workflow/ui/reference/README.md).

- Reuse the shared UI/layout components as they become available. Extend an
  existing component before adding an overlapping variant.
- Follow the [shared shell integration rules](../workflow/specs/shared-ui.md#shared-shell-integration)
  when adding a role page or navigation destination.
- Use the [shared opening and closing motion rules](../docs/ui-design-system.md#shared-opening-and-closing-motion)
  for all roles. Keep timings and effects in shared components or styles.
- Use the agreed Apps SDK UI components and per-purpose sizes. Keep tokens,
  typography, spacing, radius and semantic colours centralized.
- Treat exact numeric contracts as authoritative over sampled screenshot pixels.
  Compare the same reference state, viewport, theme and font environment.
- Read the owning feature contract for role behavior. Preserve shared lifecycle,
  session/CSRF, object permissions and the secured SoCLaaS gateway boundary.
- Keep synthetic role selection, simulated AI and sample file-change data in
  explicit fixtures. They cannot become production identity, provider calls or
  evidence of real tests/code changes.
- Keep code/logs/AI output safely rendered. All interactive controls need an
  actual behavior, associated labels and visible keyboard focus.
- Preserve selected-bug context and drafts when changing tabs/panes. Sidebar
  collapse and AI opening must not cause unrelated content to take over the pane.
- Use isolated browser contexts for viewport tests. Leave the human's visible
  browser at its native window size; inspect inner/outer viewport mismatch when
  content appears clipped.

Run `npm run check` and `npm run build` for changed frontend code, plus relevant
`npm run test:e2e` and accessibility scenarios. Record exact results and gaps.
Documentation-only edits run the portable foundation check from the repo root.
Do not add a new verification runner or overwrite existing test configuration
merely to port the prototype.
