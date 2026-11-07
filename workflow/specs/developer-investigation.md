# TD-DEV — Developer investigation and QA handoff contract

Status: canonical behavior contract for the Developer role (DEV-PLAN #26); not an
implementation or completion record. Human owner: not supplied. The approved
architecture, [product scope](../../docs/product.md), [shared role contracts](../decisions/002-shared-role-contracts.md),
[authentication and project membership contract](auth-membership.md) and
[Reporter reports and shared lifecycle contract](reporter-reports.md) (TD-RPT)
authorize the routine design choices stated here. Follow the [security contract](../../docs/security.md),
[threat model](../../docs/threat-model.md), [AI policy](../../docs/ai-policy.md),
[Developer completion gates](../developer/agentic-se.md) and [shared UI contract](shared-ui.md)
at implementation. This specification does not authorize deployment, membership
administration, production provisioning, provider integration or a new lifecycle
status.

## Goal and boundary

An active Developer in a project can see the reports assigned to them (My work)
and the shared fields of every report in the project (All bugs). The currently
assigned Developer can start investigation, keep private investigation notes,
request Reporter clarification, and submit a proposed resolution that moves the
report to QA. QA owns assignment, closure and rejection. Domain/application
policy owns authorization, transitions, versioning and persistence; UI role state,
preview fixtures and AI output are never identity or authority.

TD-RPT remains the owner of the shared report representation, the lifecycle edges,
the assignment rules, the clarification data/API contract, idempotency rules and
the error precedence. This contract **reuses those rules by reference** and adds
only Developer-owned behavior:

| Concern | Owner |
| --- | --- |
| Report fields, list/detail/progress, cursors, error shape and precedence | TD-RPT (unchanged) |
| Lifecycle edges and `startInvestigation`/`proposeResolution` command signatures | TD-RPT (unchanged); this contract adds their HTTP interface, fields and audit |
| Assignment, reassignment, clear and invalid-assignment recovery | TD-RPT rules; implementation in QA-ASSIGN #32 (TD-ASG) |
| Clarification request/reply data, caps, versions and endpoints | TD-RPT; Developer request UI and tests here (TD-DEV-16–20) |
| Developer context read, investigation notes, proposed resolutions | **This contract** |
| QA close/reject, rejection-reason representation, QA checklists | Future QA verification specification (not yet written) |
| Developer AI suggestions | DEV-AI #31, blocked on a separate secured-gateway specification |

Excluded: QA close/reject UI or endpoints, Reporter editing, file/repository/diff
ingestion, attachments, arbitrary code execution, automatic patches,
notifications, live chat, provider calls, deployment and new lifecycle statuses.

## Identity, access and visibility

All TD-RPT identity rules apply: the actor UUID comes from the server-side session,
current user/project/membership state is loaded from the database for every
operation, and actor, role, membership, status and assignment claims in a request
are rejected as server-owned fields. A valid session alone grants nothing.

Definitions used below (all evaluated freshly per request):

- **Active Developer**: active user with an active `DEVELOPER` membership in an
  active project.
- **Assigned Developer**: an active Developer whose UUID equals the report's
  `assigned_developer_id`. A stale assignment to a user whose membership is
  disabled, removed or changed to another role grants nothing (TD-RPT).
- **Staff**: an active Developer or active `QA_REVIEWER` in the report's project.

| Data | Read | Create / mutate |
| --- | --- | --- |
| Shared report fields, progress, report list (`assignedToMe` filter) | Reporter owner; all staff (TD-RPT) | Reporter only, `OPEN` only (TD-RPT) |
| Lifecycle `OPEN → IN_PROGRESS` | — | Assigned Developer |
| Lifecycle `IN_PROGRESS → READY_FOR_QA` with a proposed resolution | — | Assigned Developer |
| Clarification history | Reporter owner; all staff (TD-RPT) | Request: assigned Developer in `IN_PROGRESS`/`READY_FOR_QA`; reply: Reporter owner (TD-RPT) |
| Investigation notes | **Assigned Developer only** | Assigned Developer, any note on the report (including predecessors' notes), report in `IN_PROGRESS` or `READY_FOR_QA` |
| Proposed resolutions | All staff | Assigned Developer, via the transition above |

Design choices:

- Investigation notes are private working data for whoever currently holds the
  assignment. On reassignment the new assigned Developer can read the notes left
  by previous assignees (continuity of investigation); the former assignee loses
  read access at the same authorization point at which they lose mutation access.
  Notes are never readable by the Reporter, QA reviewers, unassigned Developers or
  other projects, and are never selected into report, list, progress,
  clarification, resolution or audit representations.
- The current assignee has full note permissions: add, edit and delete any note
  on the report, including notes left by previous assignees. Edit rights follow
  the assignment, not authorship. Each note keeps its original creator and
  records who last edited it, so the UI can show "Created by X · edited by Y".
  Deleting a predecessor's note is permanent; the business audit keeps only who
  deleted it and when, not the text.
- Proposed resolutions are a QA-visible handoff readable by all staff in the
  project. They are not shown to the Reporter in this slice; the Reporter sees
  only status through TD-RPT progress. This assumes Reporters may be external
  users (current leaning). If the team decides Reporters are internal
  employees, Reporter read access to resolutions should be revisited.
- A Developer can never self-assign, reassign or clear an assignment (TD-RPT,
  TD-ASG). Synthetic pre-assigned fixtures may support DEV-DATA tests, but they do
  not prove the production assignment journey, which requires QA-ASSIGN #32.

## Lifecycle preconditions

| Command | Actor | Required status | Result | Report version |
| --- | --- | --- | --- | --- |
| Start investigation | Assigned Developer | `OPEN` | `IN_PROGRESS` | +1 |
| Create/edit/delete any note on the report | Assigned Developer | `IN_PROGRESS` or `READY_FOR_QA` | note row changes | **unchanged** |
| Request clarification | Assigned Developer | `IN_PROGRESS` or `READY_FOR_QA` | TD-RPT clarification request | +1 (TD-RPT) |
| Propose resolution | Assigned Developer | `IN_PROGRESS` | resolution revision appended; `READY_FOR_QA` | +1 |

Notes on the table:

- A `CLOSED` report is read-only for every Developer action; existing notes stay
  readable by the assigned Developer and resolutions stay readable by staff.
- After QA rejection (`READY_FOR_QA → IN_PROGRESS`, owned by the QA contract) the
  assignment is unchanged and the assigned Developer may propose again; each
  proposal appends a new resolution revision.
- Reassignment or clear (QA) does not change status. A report left `IN_PROGRESS`
  with no valid assignee accepts no Developer mutation until QA assigns a current
  Developer. A Developer newly assigned to a `READY_FOR_QA` report can add notes
  and request clarification but cannot propose until QA rejects. This keeps the
  proposal QA is verifying fixed while QA reviews it.
- Note mutations deliberately do not increment the report version or touch
  `updated_at`. Incrementing them would expose private note activity to the
  Reporter and QA through the shared version, progress and list ordering, and
  would cause unrelated stale-version conflicts. Notes carry their own version.

## Data contract

Text rules are TD-RPT's: plain Unicode text, never HTML or executable code; count
code points and UTF-8 bytes after JSON decoding; reject unpaired surrogates, NUL
and C0/C1 controls other than TAB, LF and CR; no trimming, normalization or silent
truncation; "nonblank" means at least one non-whitespace code point. Snippets and
logs inside these fields are inert text and are never executed, fetched or
dereferenced. Identifiers are canonical UUIDs, timestamps UTC `timestamptz`,
versions positive integers starting at 1 and incremented once per successful
mutation of that row.

### Investigation note

Persist `id UUID`, `project_id UUID`, `report_id UUID`, `author_user_id UUID`
(the creator; never changes), nullable `last_edited_by_user_id UUID` (set on
each edit to the editing assignee), `body`, `created_at`, `updated_at`,
`version`.

| Field | Required | Maximum code points | Maximum UTF-8 bytes |
| --- | --- | ---: | ---: |
| `body` | yes, nonblank | 8,000 | 32,000 |

At most 200 notes may exist per report; creating the 201st returns
`409 NOTE_LIMIT`. Notes are retained with the report. Delete is an explicit hard
delete of the note row by the current assignee; the business audit keeps only
metadata. Note list order is fixed by `created_at ASC, id ASC`. Because the
current assignee can delete any note, a predecessor's notes never block the cap.

Notes and resolution revisions reference `(report_id, project_id)` through a
composite foreign key to the report, so a row's project always equals its
report's project.

### Proposed resolution revision

Persist `id UUID`, `project_id UUID`, `report_id UUID`, `author_user_id UUID`,
`revision` (1-based sequence per report), the fields below, and `created_at`.
Revisions are append-only and immutable; there is no edit or delete. The latest
revision is the current proposal; earlier revisions remain readable as history.

| Field | Required | Maximum code points | Maximum UTF-8 bytes |
| --- | --- | ---: | ---: |
| `rootCause` | yes, nonblank | 4,000 | 16,000 |
| `changeSummary` | yes, nonblank | 4,000 | 16,000 |
| `verificationSteps` | yes, nonblank | 4,000 | 16,000 |
| `fixReference` | no; empty allowed | 200 | 800 |

`fixReference` is an opaque plain-text pointer (for example a branch or commit
identifier) that the server never resolves. At most 50 revisions may exist per
report; a 51st proposal returns `409 RESOLUTION_LIMIT` and commits nothing.
`(report_id, revision)` is unique. Revision list order is fixed by
`revision ASC`.

### Request bodies

Every state-changing request body on a route introduced by this contract (start,
note and resolution routes) is capped at 64 KiB including JSON framing; the
TD-RPT clarification route keeps its own TD-RPT limits unchanged; the aggregate decoded text of a resolution is at most 48,800 UTF-8 bytes
(the sum of its field maxima). Unknown fields, duplicate keys, nulls, wrong types
and server-owned fields (`id`, actor/author, `projectId`, `reportId`, `status`,
`assignedDeveloperId`, `revision`, timestamps, `version` other than the expected
version) are rejected with `400 INVALID_REQUEST`.

## Interfaces

All routes use TD-AUTH same-origin session and CSRF rules, TD-RPT strict JSON,
cursor (signed, purpose/actor/project/report/filter-bound, 15-minute expiry,
`limit` 1–100 default 25), error shape, error precedence and `Cache-Control:
no-store`. Existing report list/detail/progress/clarification routes are reused
unchanged.

| Operation | Request | Success |
| --- | --- | --- |
| `GET /api/projects/{projectId}/developer-context` | none | `200 {project:{id,name},role:"DEVELOPER"}`; active Developer only |
| `GET /api/projects/{projectId}/reports?assignedToMe=true` (existing) | TD-RPT bounded query | My work list |
| `GET /api/projects/{projectId}/reports` (existing) | TD-RPT bounded query | All bugs list |
| `POST /api/projects/{projectId}/reports/{reportId}/investigation/start` | `{expectedVersion}` | `200` TD-RPT report detail, status `IN_PROGRESS` |
| `GET /api/projects/{projectId}/reports/{reportId}/notes?limit=&cursor=` | bounded query | `200 {items,nextCursor}`; assigned Developer only |
| `POST /api/projects/{projectId}/reports/{reportId}/notes` | `{body}`; `Idempotency-Key` | `201` note |
| `PATCH /api/projects/{projectId}/reports/{reportId}/notes/{noteId}` | `{body,expectedVersion}` | `200` note |
| `DELETE /api/projects/{projectId}/reports/{reportId}/notes/{noteId}?expectedVersion=` | none | `204` |
| `POST /api/projects/{projectId}/reports/{reportId}/resolutions` | `{rootCause,changeSummary,verificationSteps,fixReference,expectedVersion}` | `201 {report,resolution}`; report status `READY_FOR_QA` |
| `GET /api/projects/{projectId}/reports/{reportId}/resolutions?limit=&cursor=` | bounded query | `200 {items,nextCursor}`; staff |
| `POST /api/projects/{projectId}/reports/{reportId}/clarifications` (TD-RPT) | `{body,expectedReportVersion}` | `201` clarification request |

`developer-context` mirrors TD-RPT's `reporter-context`: another active role gets
`403 FORBIDDEN`; absent project, disabled/removed membership or non-member gets
the opaque `404 NOT_FOUND`. It returns no member list or profile data. The actor's
own user UUID comes from the existing session read, not from this response.

Representations (allowlists; nothing else is returned):

- **Note**: `id`, `reportId`, `authorUserId`, nullable `lastEditedByUserId`,
  `body`, `createdAt`, `updatedAt`, `version`.
- **Resolution**: `id`, `reportId`, `authorUserId`, `revision`, `rootCause`,
  `changeSummary`, `verificationSteps`, `fixReference`, `createdAt`.
- **Report**: the TD-RPT detail representation, unchanged. Notes and resolutions
  are never embedded in report, list or progress responses.

Every page has at most 100 entries and each response is capped at 5 MiB after
JSON encoding. No representation contains session data, email/login, credentials,
prompt/provider metadata, audit metadata or another project's content.

`expectedVersion` on start/propose is the **report** version; on note
edit/delete it is the **note** version. Start and propose do not take an
idempotency key: the expected report version already makes them at-most-once.
Because lifecycle state is checked at precedence step 6 before the version at
step 7, outcomes are deterministic: a repeated start after success returns
`409 INVALID_STATE` (report no longer `OPEN`); a repeated propose while the report
is still `READY_FOR_QA` returns `409 INVALID_STATE`; a repeated propose after QA
rejection has returned the report to `IN_PROGRESS` returns `409 STALE_VERSION`.

Note creation takes a required canonical UUID `Idempotency-Key` with TD-RPT replay
semantics (operation `NOTE_CREATE`, 24-hour retention, SHA-256 body digest only,
same key/different body `409 IDEMPOTENCY_CONFLICT`). As in TD-RPT step 5, the
replay record is consulted after session, membership, `DEVELOPER` role and project
scope are authorized and before the report is loaded for a new mutation. The
record stores only actor/project/operation/key, digest, report UUID and note UUID.
A matching replay then re-authorizes delivery: the report must still be visible
and the actor must still be its assigned Developer (`403 FORBIDDEN` otherwise),
and a since-deleted note returns `404 NOT_FOUND`. A replay never creates a second
note or audit event.

## Authorization, transactions and audit

Reads (developer context, lists, detail, note and resolution lists) take no row
locks; they follow TD-AUTH's READ COMMITTED linearization at the membership and
assignment query. A read whose authorization query completed before a
revocation or reassignment committed may finish; one started afterwards observes
it.

Each mutation runs in one database transaction that acquires locks in this
global order, which QA-ASSIGN (TD-ASG) and the QA verification commands must also
follow: **user/project/membership → report → note**.

1. Re-reads the actor's user, project and membership rows `FOR SHARE` and checks
   they are active with role `DEVELOPER`. Membership changes take conflicting
   locks, so a revocation and the mutation serialize.
2. Loads the report within the authorized project and locks its row:
   `FOR UPDATE` for start and propose (they change the report row);
   `FOR NO KEY UPDATE` for note create (serializes concurrent creates so the
   200-note cap cannot be exceeded, without changing the report row);
   `FOR SHARE` for note edit and delete. Assignment changes and QA transitions
   update the report row, so they serialize against all three.
3. For note edit/delete, locks the note row `FOR UPDATE` within that report.
4. Rechecks current assignment, lifecycle status, caps and expected version.
5. Writes the mutation, increments the applicable version(s) and updates
   `updated_at` only on rows it changes.
6. Inserts exactly one business audit event.

If reassignment, clear or membership revocation commits first, the Developer
command is denied; if the Developer command commits first, it stands and the QA
action sees the new version. Authorization, mutation, version increment and audit
are atomic: any failure, including audit insert failure, commits nothing. A
deadlock or serialization failure rolls back completely; the server may retry the
whole transaction once from step 1, and otherwise returns
`503 SERVICE_UNAVAILABLE` with nothing committed. No provider or network work
occurs inside these transactions.

| Event code | Prior → new status | Version recorded | Object IDs |
| --- | --- | --- | --- |
| `REPORT_INVESTIGATION_STARTED` | `OPEN → IN_PROGRESS` | report prior/new | report |
| `INVESTIGATION_NOTE_CREATED` | unchanged | note new | report, note |
| `INVESTIGATION_NOTE_UPDATED` | unchanged | note prior/new | report, note |
| `INVESTIGATION_NOTE_DELETED` | unchanged | note prior | report, note |
| `REPORT_RESOLUTION_PROPOSED` | `IN_PROGRESS → READY_FOR_QA` | report prior/new; revision number | report, resolution |

Audit records follow TD-RPT: UTC timestamp, event code, trusted actor UUID,
project/report/object UUIDs, statuses and versions as above, request correlation
UUID and outcome. Never store note, resolution or clarification text in audit
metadata, application logs or security events.

The V4 business-audit table only fits `REPORT_CREATED` (`new_status='OPEN'`,
`new_version=1`). A fresh migration extends it with `object_type`
(`REPORT`/`NOTE`/`RESOLUTION`), `object_id UUID` without a foreign key (deleted
notes keep their audit row), `prior_status`/`new_status` nullable for note events,
`prior_version`/`new_version` meaning the version of `object_type`'s row
(the report version for start/propose, the note version for note events), and
`resolution_revision` for proposals. Per-event check constraints keep each
event's columns exact and keep the existing `REPORT_CREATED` constraints intact.
The security-event allowlists add the new event codes and route templates. Denials are
bounded security events per TD-AUTH; if a required security-event write fails,
fail closed with `503 SERVICE_UNAVAILABLE` and deliver no protected content.

## Errors

TD-RPT's error shape and precedence apply unchanged. Developer-specific mapping
by precedence step:

| Step | Situation | Response |
| --- | --- | --- |
| 4 | Absent project, disabled user, disabled/removed membership, non-member | `404 NOT_FOUND` (opaque) |
| 4 | Active Reporter or QA calls a route restricted to Developers (start, note routes, propose, `developer-context`), whatever the report/note ID | `403 FORBIDDEN` |
| 4 | Active Reporter reads resolutions, whatever the report ID | `403 FORBIDDEN` |
| 5 | Note-create replay | as described under Interfaces |
| 6 | Report absent or in another project | `404 NOT_FOUND` (opaque) |
| 6 | Report visible, actor is a Developer but not its current assignee (start, propose, any note route) | `403 FORBIDDEN`, decided **before any note lookup**, so a real and a random `noteId` give the same response |
| 6 | Assignee: note ID absent or belonging to another report | `404 NOT_FOUND` |
| 6 | Wrong lifecycle status for the command | `409 INVALID_STATE` |
| 7 | Field validation, caps, idempotency, versions | TD-RPT codes plus `409 NOTE_LIMIT` and `409 RESOLUTION_LIMIT` |
| 8 | Persistence, audit or security-event sink failure; unresolved deadlock | `503 SERVICE_UNAVAILABLE` |

Validation errors never echo submitted text. Stale responses carry only the
current authorized version of the relevant object.

## UI contract

The Developer workspace reuses the shared AppShell and design system per
[shared UI](shared-ui.md). The development preview under `frontend/src/preview/`
and its fixtures are not implemented role behavior and cannot establish identity
or authorization; DEV-UI replaces the Developer preview path with real API calls.

- **Navigation**: Developer destinations are My work and All bugs. The workspace
  renders only after `developer-context` succeeds; any other result shows the
  safe denied/not-found/session-expired state.
- **My work**: `assignedToMe=true`; All bugs: unfiltered same-project list. Both
  support the TD-RPT `status` filter and cursor paging, with empty, loading,
  unavailable and expired-cursor states.
- **Report detail**: shared fields, status/assignee/version, an investigation
  activity view and, only when the session user is the assignee, the notes panel.
  Each note shows its creator and, when edited, its last editor ("Created by X ·
  edited by Y").
  The activity view is exactly the clarification history plus the resolution
  history, read from their own endpoints; it is never derived from business
  audit, which would expose note events. Action availability (start, note, clarify, propose) is a UI
  hint derived from status and assignee; the server remains the authority.
- **Confirmed state**: every action waits for the server result before showing
  success. Recoverable failures (`STALE_VERSION`, `INVALID_STATE`, `503`, network)
  preserve unsent note, clarification and resolution text and offer refresh.
  `403` after reassignment removes the notes panel and actions. `401` follows the
  shared session-expired flow.
- **Isolation**: drafts, selection and results are keyed by project and report and
  are discarded on report, project or account change; nothing leaks across them.
- **Safe rendering**: all user, report and (later) model text renders as plain
  text; no HTML interpretation, auto-linking of `fixReference`, or code execution.
- Accessibility follows the shared UI contract: keyboard operation, visible
  focus, contrast, responsive layout and reduced motion. Disclosures (activity,
  original report) and popovers (sort, filters) use the shared `Disclosure` and
  `AnimatedPopover` components and the shared motion rules in the
  [design system](../../docs/ui-design-system.md); Developer pages define no
  motion variants of their own.
- The QA preview's Change map expects patch data. A proposed resolution carries
  no diff or patch (repository/diff ingestion is excluded), so QA views of a
  Developer handoff show the Change map's existing no-patch fallback.

## AI boundary (for DEV-AI #31)

Developer AI is advisory and is blocked until a separately specified and verified
secured gateway exists; the internal SoCLaaS transport must not be wired to
Developer controllers or the browser. When specified, the eligible context is at
most: the authorized report's shared fields and the assigned Developer's
explicitly selected notes, selected after authorization and reauthorized
(membership plus assignment plus source version) before delivery. Clarification
text, resolutions, other reports, other projects, credentials and audit data are
excluded unless that later contract adds them explicitly. AI output may populate
an editable preview only; applying it is an ordinary note or resolution command
with the normal version check. AI never starts investigation, proposes a
resolution or changes status.

## Acceptance criteria

TD-DEV-01–05 are contract criteria for this issue: they may pass independent
specification review. Runtime criteria TD-DEV-06–30 belong to the delivery issues
listed below and remain pending until implemented and verified on a recorded
candidate. Implementation uses fresh TraceDesk migrations, isolated synthetic test
databases and no `.env*` files or live data.

| Criterion | Observable requirement | Required future tests/evidence |
| --- | --- | --- |
| **TD-DEV-01** | Developer context, the visibility table, assigned/unassigned/former-assignee access, lifecycle preconditions and the boundary between shared report, private notes, clarification and QA-visible resolution data match this contract. | Two-project, three-role, two-Developer API matrix for every route; reassignment and revocation remove note read and mutation at the authorization point; a new assignee can edit and delete a predecessor's note, keeping `authorUserId` and setting `lastEditedByUserId`; notes absent from every non-note response. |
| **TD-DEV-02** | Note and resolution fields, Unicode/byte/body limits, caps, ordering, retention, UUID/UTC/version rules and plain-text handling match this contract. | Validator tests at each limit and one over; multibyte and supplementary code points, controls, surrogates, blank required fields; 200/201 notes and 50/51 revisions with no partial mutation; synchronized concurrent creates at 199 notes leave exactly 200; composite project/report FK rejects mismatched rows; schema round trip. |
| **TD-DEV-03** | Start, note create/edit/delete and propose follow the precondition, locking, version, idempotency, atomic audit and denied/stale/failed/reassigned/concurrent outcomes above; note mutations do not change report version or `updated_at`. | Isolated PostgreSQL tests: one audit per success with exact per-event columns; rollback on audit failure; duplicate start (`INVALID_STATE`), duplicate propose before and after QA rejection (`INVALID_STATE`/`STALE_VERSION`); Developer note create versus QA reassignment under the global lock order without deadlock, and injected serialization failure yielding `503` with nothing committed; synchronized reassignment/revocation versus start, note and propose; QA reject versus Developer clarification; note replay and changed-body conflict. |
| **TD-DEV-04** | Routes, request/response allowlists, error mapping, `developer-context`, and the My work/All bugs/detail/notes/clarification/handoff UI behavior match this contract with server-side trusted identity. | Per-route direct API tests for allowed/denied/stale/unavailable; step-4 role `403` with nonexistent report IDs; non-assignee `403` identical for real and random note IDs; forged actor/role/status/assignee fields; cursor binding; browser-to-real-backend journeys including stale/reassigned/session-expired states and input preservation. |
| **TD-DEV-05** | Criteria map to tests and evidence; actual coordination and independent contract review are recorded without claiming unimplemented behavior or human approval. | Independent reviewer reads this contract for contradictions with TD-RPT/TD-AUTH, authorization-before-selection, transaction/audit coverage and test derivability; evidence records revision, findings and corrections under `workflow/developer/evidence/`. |

## Delivery issues

| Issue | Criteria | Scope | Blocked by |
| --- | --- | --- | --- |
| #27 DEV-DATA | TD-DEV-06–10 | `developer-context`, start investigation, notes, migrations and audit | #26; #32 for the production journey |
| #28 DEV-UI | TD-DEV-11–15 | My work, All bugs, detail, start and notes UI | #27 |
| #29 DEV-CLARIFY | TD-DEV-16–20 | Shared TD-RPT clarification migration and API (single owner), Developer question UI, interoperability with Reporter replies | #27; #16 depends on this for Reporter replies |
| #30 DEV-RESOLUTION | TD-DEV-21–25 | Resolution schema, propose endpoint and handoff UI | #27; QA verification specification for the rejection loop |
| #31 DEV-AI | TD-DEV-26–30 | Advisory hypotheses and next steps | #27, #28, secured gateway specification and verification |
| #32 QA-ASSIGN | TD-ASG-01–05 | QA assignment, reassignment and clear (QA contributor) | Prerequisite for the production Developer journey |

Applicable threat-model rows: forged role/project/bug ID, report object-ID
substitution, stale assignment or verification decision, CSRF or stolen session,
denial event storage failure, and (for #31) prompt injection, cross-project AI
context selection and raw provider leak.

## Open decisions and implementation status

Ownership decided by the Developer contributor on 2026-11-08:

1. DEV-CLARIFY #29 (Developer side) lands the shared clarification migration,
   API and question UI; REPORT-PROGRESS #16 builds on it for Reporter replies.
2. The QA contributor writes the QA verification specification, including the
   rejection-reason representation the Developer sees after
   `READY_FOR_QA → IN_PROGRESS`.
3. The QA contributor owns QA-ASSIGN #32.

The Developer contributor reviewed and kept: notes visible to the current
assignee including predecessors' notes, no QA access to notes, staff-only
resolutions, the four resolution fields, no report-version or `updated_at`
change on note mutation, and no re-proposal while `READY_FOR_QA`. They changed
note permissions to the current-assignee model above. The 64 KiB request cap and
numeric limits remain bounded design choices within the approved product and
architecture. They are not claims of human approval, implementation, verification
or release. Specified: behavior and future test mapping only. One independent
contract review and its corrections are recorded in
[DEV-PLAN evidence](../developer/evidence/developer-investigation-plan.md).
TD-DEV-06–30 are worded in their GitHub delivery issues and are planned, not
accepted. Pending: all runtime implementation and verification.
