# REPORT-UI — Reporter workspace, reports, and new report

Status: implementation contract for issue [#14](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/issues/14); implementation and runtime acceptance are pending. Human owner: not supplied. This contract refines the approved architecture, [product scope](../../docs/product.md), [shared role contracts](../decisions/002-shared-role-contracts.md), [authentication contract](auth-membership.md), and the canonical [Reporter report contract](reporter-reports.md). Follow [shared UI](shared-ui.md), the [design system](../../docs/ui-design-system.md), the [security contract](../../docs/security.md), and the [Reporter completion gates](../reporter/agentic-se.md). It does not authorize deployment, account/project provisioning, or AI/provider integration.

## Goal and boundary

An authenticated Reporter opens a project they are authorized to use, lists and reads their reports, and submits a new report through the real same-origin API. The workspace uses the shared shell and server-derived Reporter context. Browser state, query parameters, labels, and client-side role selection never establish identity or permission.

This slice includes Reports navigation, the report list and detail, New report, a minimal Reporter project context, and honest loading, empty, no-results, denied, expired-session, conflict, and unavailable states. The existing Environments navigation destination remains visibly identified but unavailable until an owning contract/API exists. Settings reuses the existing Appearance and Account/sign-out behavior. There is no project discovery endpoint in scope: the initial project is supplied as a canonical UUID in `?project=<uuid>` or entered in a labeled Open project form. The UUID is an untrusted target only; the server must resolve the current session and authorize membership/role before returning project context.

Exclude environment-management CRUD, persisted drafts, report editing, AI invocation, file upload, Developer or QA portals, project/member administration, production provisioning, and deployment. Do not create sample reports, demo identities, hardcoded project/role mappings, or simulated successful mutations in the authenticated workspace.

## Identity, project context, and navigation

1. Resolve sign-in state through `GET /api/session`; use only the server-side session. Do not infer role from UI selection, URL, `localStorage`, or request fields.
2. Resolve the chosen project with `GET /api/projects/{projectId}/reporter-context`. Render its server-returned `{project:{id,name},role:"REPORTER"}` only after a successful response. An active non-Reporter role receives `403 FORBIDDEN`; anonymous/expired sessions receive `401 UNAUTHENTICATED`; absent or inaccessible project/membership follows the opaque `404 NOT_FOUND`; service/persistence failure is unavailable. Never substitute generic or cached Reporter identity after denial.
3. Validate project route input as a canonical UUID for user feedback, but treat every accepted value as untrusted. Do not offer a guessed or hardcoded project list. On switching projects, clear previous protected list/detail state immediately, cancel or disregard stale responses, and fetch the new context before any report call.
4. Use one shared `AppShell` with the Reporter tray destinations Reports, Environments, and bottom Settings. Reports owns its sidebar groups/list and New report action. Environments opens an explanatory unavailable state. Appearance changes the shared theme; Account's sign-out calls the real logout path. Do not add filler destinations.
5. Keep selection, list filters, and unsaved form data in memory only. Pane collapse/resize, Settings/Appearance, and recoverable errors preserve the form; returning from Settings restores it. Navigating from a dirty composer to another report, project, or Environments asks whether to discard; cancel keeps the composer and focus, and only an explicit discard clears it. If submission is unconfirmed, explain that leaving does not cancel a possibly saved report and that retrying first avoids creating a second report. Reload, sign-out, and successful submission clear protected in-memory state. This is transient UI state, not a persisted draft.
6. Key requests and component state by authenticated session generation and project UUID. Ignore responses arriving after sign-out, reauthentication, project switch, or selection change. Report selection is keyed by report UUID; when navigating back to the list, retain the current in-memory filter and loaded page for that project.

## Report field and wire contract

Use the canonical seven fields, in this order. Text is plain Unicode; preserve exact decoded string values and line endings. Do not trim, normalize, silently truncate, interpret as markup, or echo submitted text in generic errors. Required means at least one non-whitespace Unicode code point; local checks do not alter the value. Reject NUL, unpaired surrogates, and C0/C1 controls except TAB, LF, and CR. Apply both code-point and UTF-8 byte bounds; the JSON request body including framing is at most 256 KiB.

| Field | Required | Max code points | Max UTF-8 bytes |
| --- | --- | ---: | ---: |
| `title` | yes | 160 | 640 |
| `description` | yes | 10,000 | 40,000 |
| `environment` | yes | 4,000 | 16,000 |
| `reproductionSteps` | yes | 8,000 | 32,000 |
| `expectedBehavior` | yes | 4,000 | 16,000 |
| `actualBehavior` | yes | 4,000 | 16,000 |
| `textLogs` | no; empty allowed | 16,000 | 64,000 |

The client validates these bounds before submission and associates each error with its visible field using `aria-describedby`/`aria-invalid`; the server remains authoritative. Count UTF-8 bytes after encoding the exact string. Unknown keys, duplicate JSON keys, nulls, incorrect types, and server-owned fields are rejected by the API. The payload contains only the seven fields. Do not send project, actor, role, status, assignment, timestamps, version, or audit data in the writable body.

## API integration and visible behavior

Use the existing authenticated same-origin API client/session/CSRF layer. Protected responses are not cached. State-changing requests include the current `X-CSRF-TOKEN`. Obtain `GET /api/csrf` only through the existing client behavior; on `403 CSRF_INVALID`, refresh the token and retry once with the same immutable request. Do not retry `403 FORBIDDEN` as CSRF. A `401` clears context, reports, selected detail, pending project data, and tokens from UI memory, then routes to the real sign-in flow. A membership/role `404` or role `403` clears protected project data and shows the generic denied state. API/service `503`, network failure, malformed response, and unexpected server errors show unavailable/retry state without fabricated content.

| Screen/action | API contract | UI result |
| --- | --- | --- |
| Open project | `GET /api/projects/{projectId}/reporter-context` | Server-derived project name and Reporter role; reject other role/status responses as specified above. |
| List reports | `GET /api/projects/{projectId}/reports?limit=25&status=&cursor=` | Render only returned summaries (`id`, `title`, `status`, `updatedAt` and other contract fields). `limit` is 1–100; use the server `nextCursor` for bounded Load more. Exact status filtering only; do not add client-only full-text search or claim results beyond loaded pages. |
| Open report | `GET /api/projects/{projectId}/reports/{reportId}` | Render only the authorized full shared representation and its seven fields; no role notes or fabricated activity. Return-to-list preserves in-memory filter/page. |
| Create report | `POST /api/projects/{projectId}/reports`, seven fields plus canonical UUID `Idempotency-Key` header | Treat only `201` with a valid returned report representation as success. Open that returned report and display its actual status/version. API creates status `OPEN`; UI does not pre-announce success or invent an ID/status. |

Display the canonical status vocabulary (`OPEN`, `IN_PROGRESS`, `READY_FOR_QA`, `CLOSED`) with readable labels; no extra lifecycle status. List filters are server-backed exact status choices. List empty and filtered no-results are distinct: the first explains that the Reporter has no reports in this project; the second offers clearing the active filter. Loading is explicit and does not reuse another project's data. Failed list/detail reads preserve only safe current-project selection when authorization remains valid and offer retry; denied/401 responses clear protected content.

## Submission, retry, and privacy behavior

- While editing, the primary action validates the form and prevents the network request on invalid fields; keep focus on the first invalid field and announce the validation summary. Disable duplicate submission while pending. Do not clear any field on local or server rejection.
- Create one idempotency key per immutable canonical body. Disable duplicate clicks while one request is in flight. For timeout, connection loss, or `5xx` with unknown commit outcome, retain the exact body and key, label the outcome as unconfirmed, and allow only retry of that same pair or leaving the composer through its discard confirmation. Freeze fields until the outcome resolves; do not create a new key/body while the prior commit could have succeeded.
- `400 VALIDATION_ERROR` is a known rejection: show safe field-level errors where the response identifies fields without echoing their values, allow editing, and use a new key for the revised body. `409 IDEMPOTENCY_CONFLICT` is a conflict, never a success; preserve the entered body, freeze automatic retries, explain that the submission key conflicts, and require a safe user-led resolution path. Never claim the report was saved based on a `409`.
- A confirmed `201` must contain a well-formed report ID and all representation values needed to open the actual saved detail. If the response is malformed, show an unconfirmed/unavailable outcome and retry with the same key/body; do not fabricate a detail route from form values.
- Do not persist report text, session identifiers, CSRF tokens, or credentials in browser storage, URL, console, analytics, or client logs. The canonical project UUID in the shared project URL is the sole allowed project context in the URL; names, role claims and report field values stay out of it. All seven fields render as escaped text; `textLogs` may use a monospace text surface but is not syntax-highlighted as executable code and is never inserted as HTML.

## Visual and interaction contract

Use `AppShell`, `SidebarGroup`, `SidebarItem`, and available SDK `Button`, `Input`, `Textarea`, `Badge`, and semantic tokens. Extend an existing shared component if needed; do not make a parallel shell/control system. Apply the exact shared palette, system font, icon set, form heading (28px/1.16/600), field label (14px/600), form maximum width 816px including padding, 28px top/24px inline/48px bottom padding, 36px after heading, 32px between sections, and grouped field details at 16px padding/16px radius/1px border. Primary create/submit uses SDK primary Button; all inputs have visible associated labels. Render status by label as well as SDK semantic Badge color.

At viewport width ≤700px, use the shared 40px rail and mobile overlay sidebar; form fields become one column and no action or field clips at 320px. Verify at 320px, 390px, and desktop (1440px reference width), in light and dark themes. Preserve the shared 275px desktop sidebar and 242–472px resize bounds, overlay dismissal/focus restoration, and reversible 300ms collapse/open motion; reduced-motion mode skips transition. Form state survives collapse/resize and theme change. Keep keyboard focus visible, logical, and stable; use native labels, semantic headings, button/submit semantics, `aria-live` for async outcomes, and accessible required/error/status text. Meet contrast using design-system and SDK semantic tokens; do not rely on color alone.

The shared QA captures define shell and component geometry only. Reporter screens have no frozen pixel baseline: capture actual Reporter empty/list/detail/composer states as a new, synthetic-data baseline with an explicit rationale, and verify computed geometry against the numeric contracts. Do not copy QA sample reports or treat screenshots as integration/accessibility evidence.

## Observable acceptance criteria

The issue acceptance IDs are retained verbatim; all remain pending until code and evidence exist.

| ID | Observable requirement | Required verification/evidence |
| --- | --- | --- |
| **TD-RPT-11** | Reports navigation, list/detail and New report use shared components/tokens and server-derived project context. Browser role selection cannot establish identity or authorization. | Same-candidate browser with real auth/backend verifies context and create/list/detail; forged role and project switch cannot select identity; synthetic non-member and non-Reporter cases deliver no protected report content; stale project response cannot overwrite current view. |
| **TD-RPT-12** | The composer implements canonical required fields/bounds, clear field errors and safe plain-text rendering. Input survives recoverable failures. | Boundary tests at and one over every code-point/UTF-8 bound, Unicode supplementary characters and permitted line endings, forbidden controls, blank-required values, max aggregate/wire body, and generic error safety. Browser verifies labels, errors, untrusted markup/log text rendering, and preservation on validation/network/5xx failure. |
| **TD-RPT-13** | Loading, empty, no-results, denied, expired-session and unavailable states reflect actual API responses, without fabricated sample reports or successful actions. | Real API/browser scenarios cover initial loading, empty project, filter no-results, opaque project 404, role 403, expired/revoked 401, backend/network unavailable, retry, and absence of unauthorized data; mocks are supplemental only. |
| **TD-RPT-14** | Submission waits for backend confirmation, handles the specified repeat-submit behavior and opens the actual saved report/status. This issue does not provide persisted drafts or report editing until `REPORT-EDIT` is ready. | Isolated database/API and same-browser tests cover one create on double click, same-key/body retry after unknown outcome, changed-body key conflict, known validation rejection, CSRF-only one-time retry, actual `201` detail/status, and no false success on 403/409/5xx. Confirm reload/sign-out does not restore an unsaved composer. |
| **TD-RPT-15** | Same-candidate browser-to-real-backend tests with isolated synthetic accounts cover create/list/detail, cross-project denial and session handling; keyboard, responsive, contrast and reduced-motion checks pass with recorded limitations. | Same-candidate browser against real backend and isolated PostgreSQL uses synthetic users/projects; exercise create→list→detail, cross-project denial and anonymous/expired/logout/re-auth flows over HTTPS via the test same-origin proxy. Capture desktop/320/390 geometry; verify keyboard-only operation, focus return, contrast in both themes, reduced motion, and no stale selection/input leakage. Record browser/OS/font/theme, viewport, source revision and limitations. Mocks do not satisfy this criterion. |

## Threat boundaries and implementation ownership

Trust boundaries: browser project UUID and input strings → authenticated API; session cookie/CSRF state → same-origin API; API response → rendering; asynchronous project/list/detail/create responses → current client selection. Assert no cross-project report disclosure, no client-established role, no stale response disclosure after project/account changes, no writable server-owned fields, no content leakage to storage/logs, and no false success or duplicate mutation after retries. The shared API/domain layer owns authorization, session/CSRF enforcement, report persistence, audit, version and idempotency; UI checks never replace these controls.

Production code may consume only the existing session/context/report endpoints and shared frontend APIs. If a required backend API is unavailable, retain the honest unavailable state and report the dependency; do not wire a mock into the signed-in app. The backend returns the owning contract's generic error shape. Client retry is limited to one `CSRF_INVALID` refresh/retry; all other 401/403/409 outcomes follow this contract without blind retries.

## Required handoff and open status

Implementation evidence must use the Reporter evidence workflow and map each TD-RPT-11–15 result to exact test/check, output, candidate revision/working-tree identity, changed paths and UTF-8 byte counts, browser environment/screens, actual independent review and resolved findings. Record UTC start/end, observable tool calls/retries, selected context and test results; tokens/costs not reported by tools remain unknown. Use synthetic accounts and an isolated PostgreSQL test database. Never read `.env*` files or use production data. Record any unavailable real-browser integration dependency as pending. Do not update or relabel earlier contributor evidence.

Open external dependency: the current integrated application/API must expose `reporter-context` and report list/detail/create routes and support HTTPS session testing through the same-origin proxy. Human student ownership remains unknown. These facts are status items, not permission to use another identity source or to emulate successful backend behavior.
