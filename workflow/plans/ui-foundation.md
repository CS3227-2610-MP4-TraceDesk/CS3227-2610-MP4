# UI foundation implementation plan

Status: documentation/reference preparation only. Frontend port pending.
Branch: `feat/ui-foundation`. Start from [shared UI criteria](../specs/shared-ui.md).

## Prepared artifacts

- [Design system and identity](../../docs/ui-design-system.md).
- [Prototype references, palette and source identity](../ui/reference/README.md).
- [Frontend instructions](../../frontend/AGENTS.md) and
  [portable UI skill](../../.agents/skills/tracedesk-ui/SKILL.md).
- Existing security, architecture, feature-spec, handoff and evidence templates.

## Work sequence

| Task | Dependencies | Bounded output | Verification | Status |
| --- | --- | --- | --- | --- |
| UI-1 Freeze the reference | User's agreed UI direction | Screenshots, source hashes, palette values, limitations | Artifact/link/hash review | Prepared; see run evidence |
| UI-2 Establish UI packages and tokens | UI-1 | Add missing SDK UI/Tailwind/Radix packages with exact lockfile versions; shared CSS tokens | Existing typecheck/lint/unit/build commands | Pending |
| UI-3 Port reusable shell and controls | UI-2 | Shared rail, contextual sidebar, frame, headers, pill toolbar, SDK wrappers | TD-UI-01/02/03/07/08; visual comparison | Pending |
| UI-4 Port layout families | UI-3 | Library, grouped form, review document, contextual AI pane | TD-UI-04/05/07; sidebar/zoom/native viewport checks | Pending |
| UI-5 Assemble QA reference screens | UI-4 | Queue, Summary/Change map, Verification, template library/editor; fixture adapter | TD-UI-05/06/09; independent review | Pending |
| UI-6 Connect identity and role features | Owning feature/API contracts plus UI-4 | Real session/member data and authorized commands using shared components | TD-UI-10 and owning security criteria | Pending |
| UI-7 Close visual/behavior gaps | Same-revision results from prior tasks | Updated screenshots, findings and recorded resolutions | All applicable TD-UI criteria; honest omissions | Pending |

Keep application source in the repo's existing frontend. Extract from the prototype
by responsibility rather than importing its entire monolithic main component.
Use planned module boundaries such as `frontend/src/shared/ui/` for primitives,
`frontend/src/shared/layout/` for layouts, and role folders for role behavior.
These are target locations, not currently implemented components. Keep existing
session/CSRF helpers and frontend checks. Do not replace the frontend package
manifest with the prototype's smaller manifest.

## Shared ownership and handoffs

One integrator owns tokens, dependencies, shell and shared interfaces. Teammates
own their role screens and use shared components. Assign actual people before
parallel edits; this plan does not allocate names. Hand off the spec/criterion IDs,
owned files, source revision, reference state, changed interfaces, check results
and unresolved decisions using the existing workflow template. Shared API or
security changes return to their owning specification before implementation.

For this user's specialized agents, follow the contributor model selection in
the Reporter workflow. When that model cannot be selected, report it rather than
claiming another agent fulfilled the requested role. Simple artifact maintenance
can be performed by one agent under the existing small-documentation exception.

## Verification and resource plan

Run existing frontend scripts from `frontend/`: `npm run check`, `npm run build`,
then the relevant `npm run test:e2e` and accessibility checks. Add focused
interaction/visual cases as the associated behavior is ported. Do not claim these
tests validate mock screens that are not yet in the frontend.

Capture visuals in a separate browser context with explicit viewport and scale.
Use the same synthetic fixtures, OS/font/browser/theme for comparisons. Include
sidebar expanded/collapsed, AI closed/open, light/dark, mobile, and empty/error
states. Preserve review action visibility and keyboard access. Record actual
inner/outer/visual viewport when diagnosing clipping. Never leave the user's
visible preview emulating a viewport wider than its actual window.

Record elapsed time, observed tool calls/retries, context supplied, quality
results and coordination overhead in actual run records. Use the existing
Reporter measurement kit for this user's runs. Tokens/costs unavailable from the
host remain unknown. A later focused-versus-broad-context experiment can reuse
UI-3/4 tasks with the same acceptance criteria; specify its owner and fixed
conditions before that experiment. This plan records no experiment outcome.

Record same-revision evidence and findings under the appropriate contributor's
workflow directory. Update the acceptance index only with proven implementation
results. Deployment, publication and release remain separate authorized actions.
