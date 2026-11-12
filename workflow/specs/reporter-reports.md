# TD-RPT — Reporter reports and draft contract

Status: canonical behavior contract for the bounded report slice; not an
implementation or completion record. Human owner: not supplied. The approved
architecture, [product scope](../../docs/product.md), [shared role contracts](../decisions/002-shared-role-contracts.md),
and [authentication and project membership contract](auth-membership.md) authorize
the routine design choices stated here. Follow the [security contract](../../docs/security.md),
[AI policy](../../docs/ai-policy.md), [Reporter completion gates](../reporter/agentic-se.md),
and [shared UI contract](shared-ui.md) at implementation. This specification does not authorize
deployment, membership administration, production provisioning, or AI/provider
integration.

## Goal and boundary

An authenticated Reporter can save private drafts, submit an `OPEN` report, edit
its shared fields while it remains `OPEN`, and see status and approved activity
history. Active Developers and QA reviewers in the same project
can read the shared report fields for triage/review. The assigned Developer owns
investigation and clarification requests; QA reviewers own assignment and the
verification outcome. Domain/application policy owns authorization, transitions,
versioning and persistence. UI role state is never identity or authority.

This slice covers report/draft/list/detail/progress/clarification and bounded
role-transition contracts. It does not implement controllers, migrations, UI,
role notes, membership administration, notifications, attachments, repository
ingestion, execution of submitted code, or AI invocation. The secured shared AI
gateway remains a separate blocker before any role AI endpoint; no raw transport
or improvised Reporter model call is permitted by this contract.

Scope correction for issue #16 (2026-11-12): clarification requests and replies
were descoped. Their earlier design below is retained as deferred shared-contract
reference, not an MVP requirement or part of issue #16 implementation. The
read-only activity contract and TD-RPT-21–25 below supersede earlier mappings of
#16 to clarification. Current status is already supplied by report detail; this
issue does not require a separate `/progress` endpoint.

## Identity, projects, and report visibility

Resolve the actor UUID from the current server-side session. Load current user
state and membership from the database for every operation; never accept actor,
role, membership, or assignment claims from a request. A valid session alone does
not grant project access. A project ID and report/draft ID are canonical UUIDs.
All timestamps are UTC `timestamptz` values. `version` is a positive integer
starting at 1 and incremented once for every successful mutation of that row.

Each account has one trusted persisted role as specified by TD-AUTH; active
project membership grants project access and does not supply role authority.
Every operation requires an active project and active membership. Reporter
ownership is `(reporter_user_id == actor_id)`; Developer investigation access is
`(assigned_developer_id == actor_id)`; QA review access requires the active
`QA_REVIEWER` membership. A role by itself does not grant access across projects
or to every operation.

Reporters can list and read only their own reports and drafts in the selected
project. Active Developers and QA reviewers can list/read the shared fields of
all reports in that project for triage/review, including reports assigned to
another Developer. Only the currently assigned Developer can mutate
investigation/clarification data. No role may read another user's drafts.
Developer investigation notes and QA private review notes are explicitly outside
the report representation and must not be selected into these queries or
responses. They require separate role specifications and authorization.

An unassigned report may be assigned by an active QA reviewer to an active
`DEVELOPER` member of the same project. Only QA may reassign or clear an
assignment; reassignment/clear requires a nonblank reason of at most 500 Unicode
code points and 2,000 UTF-8 bytes. A Developer cannot self-assign or reassign.
Assignment, reassignment, and clear are allowed only while the report is `OPEN`,
`IN_PROGRESS`, or `READY_FOR_QA`; assignment is immutable on a `CLOSED` report.
Assignment change and its audit event are one versioned mutation. Every
operation uses TD-AUTH's fresh membership query: a read whose membership query
precedes a revocation commit may finish; an authorization query begun after that
commit must observe the revocation. Mutations lock/recheck membership and
assignment in their transaction so the mutation and revocation serialize; the
one that commits first defines the outcome. Assignment
never grants access after the Developer's membership is disabled, removed, or
changed to another role. In that case the old assignment remains as an invalid
reference, the former Developer is immediately denied, and an active QA reviewer
can recover by assigning a current Developer or clearing it with a reason. No
admin role or implicit administrator recovery path is introduced. Assignment
does not change report lifecycle status.

## Report and draft data contract

Text is plain Unicode text, never HTML or executable code. Count Unicode scalar
values (code points) and UTF-8 bytes after JSON decoding; reject unpaired
surrogates, NUL, and C0/C1 control characters other than TAB, LF, and CR. Do not
trim, normalize, silently truncate, or reinterpret submitted text. A required
field is nonblank if it contains at least one non-whitespace Unicode code point;
its stored value remains exactly as supplied. Line endings are preserved.
Per-request JSON is limited to 256 KiB including framing. Apply both code-point
and UTF-8 byte limits to every field, and enforce the aggregate decoded text
limit of 184,640 UTF-8 bytes, the sum of the individual field maxima.

| Field | Required on submission | Maximum code points | Maximum UTF-8 bytes |
| --- | --- | ---: | ---: |
| `title` | yes | 160 | 640 |
| `description` | yes | 10,000 | 40,000 |
| `environment` | yes | 4,000 | 16,000 |
| `reproductionSteps` | yes | 8,000 | 32,000 |
| `expectedBehavior` | yes | 4,000 | 16,000 |
| `actualBehavior` | yes | 4,000 | 16,000 |
| `textLogs` | no; empty allowed | 16,000 | 64,000 |

The 256 KiB wire-body cap covers JSON framing. Each field still has its
independent code-point and byte bound. A draft may omit any report field or store
it as an empty string;
it uses the same per-field, control-character, aggregate, and request-body rules.
On submit, all six required fields must be present and nonblank, and every field
must validate.

The selected project is request context, authorized from the route and database;
the project name/ID is not a writable report field. Actor IDs, role, status,
assignment, timestamps, version, audit metadata, and draft submission linkage are
server-owned and rejected if supplied in a writable body.

Persist a report with: `id UUID`, `project_id UUID`, `reporter_user_id UUID`,
the seven fields above, `status`, nullable `assigned_developer_id UUID`,
`created_at`, `updated_at`, and `version`. Initial status is always `OPEN`.
Persist a draft with: `id UUID`, `project_id UUID`, `owner_user_id UUID`, the
same seven fields (empty values allowed), `created_at`, `updated_at`, `expires_at`,
`version`, and nullable `submitted_report_id UUID`. A draft has no lifecycle
status. Submitting links it to its created report and makes the draft immutable;
the draft remains available to its owner for 30 days after creation, then expires
and is purged. Drafts persist across logout, but are never shared across users or
projects. Allow at most 20 unexpired drafts per `(owner_user_id, project_id)`;
creating a 21st returns `409 DRAFT_LIMIT`. Draft delete is explicit and owner-only.
Expired drafts are unavailable immediately and may be physically purged later.

Clarification data is an ordered report discussion, separate from the seven
submitted report fields. A clarification request has UUID, report UUID, requester
UUID, body (required, max 2,000 code points / 8,000 UTF-8 bytes), creation time,
and version. A reply has UUID, request UUID, author UUID, body with the same
limits, creation time, and version. No arbitrary file or HTML is accepted. A
request may have at most one Reporter reply; that first reply answers it. At
most five unanswered requests may exist for a report at once, and at most 1,000
request/reply records may be retained for one report; a new request/reply at the
cap returns `409 CLARIFICATION_LIMIT`. History is retained with the report and
read through the bounded clarification-list interface below.

## Lifecycle and allowed mutation

The only report statuses are `OPEN`, `IN_PROGRESS`, `READY_FOR_QA`, and `CLOSED`.
The only lifecycle edges are `OPEN → IN_PROGRESS → READY_FOR_QA → CLOSED` and
QA rejection `READY_FOR_QA → IN_PROGRESS` with a required reason. Reporter
creates `OPEN` reports and cannot close, reopen, or set status. The assigned
Developer starts investigation (`OPEN → IN_PROGRESS`) and proposes a resolution
(`IN_PROGRESS → READY_FOR_QA`) using the later Developer contract's resolution
fields. QA closes (`READY_FOR_QA → CLOSED`) or rejects (`READY_FOR_QA →
IN_PROGRESS`). Rejection requires a nonblank reason (maximum 500 code points /
2,000 UTF-8 bytes); closure does not require a reason in this shared contract.
Reporter edits all seven shared fields only while status is `OPEN`.
Developer/QA role transitions are specified here only as shared domain contracts;
their endpoint implementation and additional role data belong to their owning
feature slices. No transition is performed by AI.

An assigned Developer may create a clarification request only in
`IN_PROGRESS` or `READY_FOR_QA`. The Reporter who owns the report may reply to
an outstanding clarification in those same states. A clarification request
requires `expectedReportVersion` and creates clarification version 1. A reply
requires `expectedReportVersion` and `expectedClarificationVersion`; the reply
creates reply version 1, increments the clarification version once, and answers
the request. A reply is a separate append-only action and never changes report
fields or status; it increments the report version to expose the changed
activity snapshot. The
request becomes answered when the Reporter posts a reply; later follow-up is a
new request by the assigned Developer. No new request/reply is allowed after
`CLOSED`; history remains readable to the Reporter owner, same-project active
Developer and QA reviewer. Each action increments the report version once; new
rows start at version 1, and answering increments the existing request version
once. These row changes, the report-version increment and the audit event are
atomic. Clarification
content is visible to the report's authorized participants, not to unrelated
projects/users.

## Interfaces

The only project context needed by this feature is the existing protected
`GET /api/projects/{projectId}` response `{id,name}`; it is server-derived
after active membership lookup and the name follows TD-AUTH's 120-code-point
bound. That endpoint does not return a role. For the Reporter workspace, add a
bounded read contract `GET /api/projects/{projectId}/reporter-context` returning
`{project:{id,name},role:"REPORTER"}` only for an active Reporter member.
Another active project role receives `403 FORBIDDEN`; absent project,
disabled/removed membership, or non-member follows the opaque `404 NOT_FOUND`
project contract. It returns no member list, user profile, or other project
metadata. This is server-derived context, not a browser role selector.

The JSON API uses same-origin authenticated session and CSRF rules from TD-AUTH.
All state-changing calls require `X-CSRF-TOKEN`. Unknown fields, duplicate JSON
keys, nulls, wrong types, and client-supplied server-owned fields are rejected.
The `Idempotency-Key` header is a canonical UUID for report creation, draft
creation, and draft submission. List `limit` is 1–100, default 25; `cursor` is
an opaque signed server-issued continuation token bound to actor, project,
endpoint purpose, report ID when applicable, filters, sort, last key, and issue
time. It expires 15 minutes after issue.
Report-list sort is fixed by `updated_at DESC, id DESC`; clarification sort is
fixed by `created_at ASC, id ASC`; clients cannot request arbitrary sort/SQL.
Filtering is limited to exact `status` and exact `assignedToMe=true|false` for
staff, and exact `status` for Reporter. A cursor with a different actor/project,
endpoint purpose/report ID, filter, sort, expired token, or malformed signature
returns `400 INVALID_CURSOR`.
These are keyset pages over current rows, not a frozen database snapshot:
inserts/updates before the last key may be absent from later pages, and changed
sort keys may move across the cursor. Every page is authorized and bounded at
read time. Draft-list order is fixed by `updated_at DESC, id DESC`.

| Operation | Request | Success |
| --- | --- | --- |
| `POST /api/projects/{projectId}/reports` | All seven report fields; idempotency key | `201` report representation, always `OPEN` |
| `GET /api/projects/{projectId}/reports?limit=&cursor=&status=&assignedToMe=` | bounded query above | `200 {items,nextCursor}`; report summaries only |
| `GET /api/projects/{projectId}/reports/{reportId}` | none | `200` full shared report; no unbounded history |
| `GET /api/projects/{projectId}/reports/{reportId}/clarifications?limit=&cursor=` | limit 1–100, default 25; keyset cursor | `200 {items,nextCursor}`; flat entries ordered by creation time then UUID |
| `PATCH /api/projects/{projectId}/reports/{reportId}` | all seven fields plus `expectedVersion` | `200` updated report |
| `POST /api/projects/{projectId}/drafts` | any/all seven fields; idempotency key | `201` owner draft |
| `GET /api/projects/{projectId}/drafts?limit=&cursor=` | bounded query; owner only | `200 {items,nextCursor}`; draft summaries only |
| `GET /api/projects/{projectId}/drafts/{draftId}` | none | `200` owner draft |
| `PATCH /api/projects/{projectId}/drafts/{draftId}` | all seven fields plus `expectedVersion` | `200` updated draft |
| `DELETE /api/projects/{projectId}/drafts/{draftId}` | `expectedVersion` query value | `204` |
| `POST /api/projects/{projectId}/drafts/{draftId}/submit` | `expectedVersion`; idempotency key | `201` created report and `submittedDraftId` |
| `GET /api/projects/{projectId}/reports/{reportId}/progress` | none | `200 {reportId,status,assignedDeveloperId,updatedAt,version}` |
| `GET /api/projects/{projectId}/reports/{reportId}/activity?limit=&cursor=` | limit 1–100, default 25; signed keyset cursor | `200 {items,nextCursor}`; approved report audit metadata only, oldest first |
| `POST /api/projects/{projectId}/reports/{reportId}/clarifications` | `{body,expectedReportVersion}` | `201` clarification request |
| `POST /api/projects/{projectId}/reports/{reportId}/clarifications/{clarificationId}/replies` | `{body,expectedReportVersion,expectedClarificationVersion}` | `201` reply |
| `GET /api/projects/{projectId}/reporter-context` | none | `200 {project:{id,name},role:"REPORTER"}`; active Reporter only |

The report list summary contains only `id`, `projectId`, `reporterUserId`,
`title`, `status`, nullable `assignedDeveloperId`, `createdAt`, `updatedAt`, and
`version`. The report detail representation contains `id`, `projectId`,
`reporterUserId`, the seven shared fields, `status`, nullable
`assignedDeveloperId`, UTC timestamps, and `version`. Clarification-list items
contain request/reply IDs, actor UUID, body, creation time, and version for
authorized participants. Each clarification page has at most 100 flat entries;
each entry has exactly one bounded body, so no nested unbounded reply arrays are
returned. A request entry has `kind:"REQUEST"` and `requestId == id`; a reply
entry has `kind:"REPLY"` and its parent request UUID in `requestId`. A
response body is capped at 5 MiB after JSON encoding. Draft-list summaries contain
only `id`, `projectId`, `title`, `createdAt`, `updatedAt`, `expiresAt`,
`version`, and nullable `submittedReportId`. These representations never contain
private role notes, session data, email/login, credential data, prompt/provider
metadata, or other reports' content. Draft representations contain only draft
ID, project ID, the owner's fields, timestamps, expiration, version, and nullable
submitted report ID. Do not expose draft existence to another member.

### REPORT-PROGRESS (#16): read-only approved activity history

The existing report detail response continues to provide the report's current
status. This issue adds a bounded chronological history of confirmed business
events so its Reporter owner can follow submission, investigation start,
proposed resolution, and other event types only when a future schema records
them. It does not add clarification request/reply behavior, staff notes, QA
checklist details, private resolution content, send-back reasons, or actor
identity. Those fields and operations remain governed by their owning contracts.

`GET /api/projects/{projectId}/reports/{reportId}/activity?limit=&cursor=` is a
read-only endpoint. Resolve the authenticated actor from trusted session state;
require an active Reporter membership in the route project and fresh
authorization that the report belongs to that project and is owned by the
actor. An active project member without the Reporter role receives
`403 FORBIDDEN`; another Reporter, cross-project or absent report, and absent
or inactive membership receive the report visibility contract's opaque
`404 NOT_FOUND` behavior.
Authorize before selecting event rows and recheck current membership and report
ownership before returning each page. Return `Cache-Control: no-store`.

Only successful business audit rows associated with this report and these
eight confirmed event codes are eligible: `REPORT_CREATED`, `REPORT_EDITED`,
`DRAFT_SUBMITTED`, `REPORT_INVESTIGATION_STARTED`,
`REPORT_RESOLUTION_PROPOSED`, `QA_VERIFICATION_SAVED`,
`QA_VERIFICATION_CLOSED`, and `QA_VERIFICATION_SENT_BACK`.
`REPORT_EDITED` is a report-field update, not a lifecycle transition; its
status fields are null. `DRAFT_SUBMITTED` links to the created report and marks
its initial `OPEN` state. QA verification events expose only status/version
metadata: save records `READY_FOR_QA` to `READY_FOR_QA`, close records
`READY_FOR_QA` to `CLOSED`, and send-back records `READY_FOR_QA` to
`IN_PROGRESS`. The event payload never includes QA checks, notes, reason,
resolution content, or actor identity; the report's current status remains
authoritative. No other event is returned, including assignment and
private-note events.

The item allowlist is exact: `{id:string,eventCode,occurredAt,
priorStatus:null|status,newStatus:null|status,priorVersion:null|number,
newVersion:null|number}`. `id` is the decimal string form of the audit table's
bigint key. `occurredAt` is UTC. No actor IDs, request IDs, object IDs other
than the opaque item key, note/checklist/resolution bodies, assignment reasons,
send-back reasons, or arbitrary audit metadata are selected or serialized.
Missing old values remain null; no values are inferred from current report
state. Empty history returns `200 {items:[],nextCursor:null}`. An unavailable
audit store returns `503 SERVICE_UNAVAILABLE` without a partial page. Invalid,
expired, tampered, or rebound cursors return `400 INVALID_CURSOR`.

The requested page limit is 1–100, default 25; pages may be empty and are ordered `occurred_at ASC, id ASC`.
Continuation uses keyset position `(occurred_at,id)` and returns a next cursor
only when another eligible row exists. The opaque signed cursor binds purpose
`REPORT_ACTIVITY`, actor UUID, project UUID, report UUID, fixed event filter
and sort, last timestamp plus bigint ID, and issue time; it expires after 15
minutes. A dedicated typed signer or an extension to an existing signer may
implement this contract; it must preserve the bigint key without UUID
conversion. Pages read the current event rows, not a frozen snapshot.

The existing audit table has `occurred_at` and a bigint primary key but no
report activity index. The implementation migration must add an index beginning
with `(report_id, occurred_at, id)` (a partial index restricted to non-null
report IDs is suitable) to support report-scoped chronological keyset reads.
Do not alter or backfill historical evidence. The route-template and security
event allowlists must include this read endpoint and its opaque cursor/denial
handling.

Assignment and role-transition application interfaces are bounded domain commands
for later slices, not Reporter REST endpoints:

```text
assign(reportId, qaActor, developerId, reason, expectedVersion) -> Report
clearAssignment(reportId, qaActor, reason, expectedVersion) -> Report
startInvestigation(reportId, assignedDeveloperActor, expectedVersion) -> Report
proposeResolution(reportId, assignedDeveloperActor, resolution, expectedVersion) -> Report
close(reportId, qaActor, expectedVersion) -> Report
reject(reportId, qaActor, reason, expectedVersion) -> Report
```

Each command rechecks current membership, role, object permission, lifecycle
precondition and expected version inside the mutation boundary. Specific Developer
resolution schema and QA checklist/evidence schema are owned by their feature
specifications; the commands above do not authorize adding those fields to the
Reporter representation.

## Idempotency, transactions, and audit

Idempotency records are scoped by `(actor_id, project_id, operation,
idempotency_key)`, retained 24 hours, and committed with the created object.
Store a SHA-256 digest of the validated canonical body, not a second copy of
user content. A same-key/same-body retry returns the same object ID and its
freshly authorized current representation without another mutation or audit
event; the original creation status is not replayed as a stale snapshot (a
created report may since have advanced to another lifecycle status). Body
equality is computed over the validated
canonical JSON object with fields in contract order and exact decoded string
values; omitted optional draft fields are canonicalized to empty strings; keys
are unique, unknown keys are rejected, and no text normalization occurs. A
same-key/different-body retry returns `409 IDEMPOTENCY_CONFLICT`. Expired keys
may be treated as new requests.

For draft submission, idempotency uses the submit command body (`draftId` from
route plus `expectedVersion`). Same key/body replays the same report ID and
representation outcome. The unique `submitted_report_id`/draft link guarantees
at most one report per draft even with a different key. A second key after
successful submission returns `409 DRAFT_ALREADY_SUBMITTED` and identifies the
already-created report only to the authorized owner. A retry with the original
key remains replayable for 24 hours. Submission revalidates the draft content as
a complete report, checks expected version and expiry, creates exactly one
`OPEN` report, links the draft, increments draft version, and records one
submission audit event in a single database transaction.

Every successful report/draft/clarification/assignment/lifecycle mutation and its
business audit event commit in one database transaction. Use row locking or
conditional version update; a version mismatch returns `409 STALE_VERSION` with
the current authorized representation's version only. A race with membership
revocation or reassignment is serialized against current membership/assignment:
if revocation/reassignment commits before the mutation authorization point, deny
the operation; if the authorized mutation commits first, it may complete. The
database transaction must recheck/lock the authorization facts needed for the
mutation so that no mutation commits based on an already-revoked membership or
assignment. Authorization, mutation, version increment, and audit event are
atomic. No provider/network work is inside a domain transaction.

Audit records contain UTC timestamp, event code, actor UUID only when trusted,
project/report/draft UUIDs when strictly parsed and authorized, prior/new status
or assignee IDs where applicable, prior/new version, request correlation UUID,
and outcome. Clarification audit metadata includes action and object IDs, not
body text. Never put report/draft/clarification text, credentials, cookies,
session/CSRF tokens, or provider content in audit metadata, application logs, or
security events. Denials are recorded as bounded security events per TD-AUTH;
business audit events are distinct and do not replace them. If required security
event persistence fails, fail closed with `503 SERVICE_UNAVAILABLE` and do not
deliver protected content or commit the requested mutation.

## Error and precedence contract

Errors use TD-AUTH's fixed JSON shape
`{"error":{"code":"<stable-code>","message":"<generic-message>"}}`
and safe stable codes. The precedence is:

1. Enforce transport framing and body cap; malformed JSON, duplicate keys,
   unknown fields, wrong types, missing required request fields, or invalid path
   UUID returns `400 INVALID_REQUEST` (or `INVALID_CURSOR` for cursor parsing).
2. For a state-changing request, validate CSRF before authentication or business
   state. Invalid/missing token returns `403 CSRF_INVALID`.
3. Require valid session; otherwise `401 UNAUTHENTICATED`.
4. Resolve active project membership and role. Absent project, disabled user,
   disabled/removed membership, and non-member return the TD-AUTH opaque
   `404 NOT_FOUND`. Active member lacking the role/action gets `403 FORBIDDEN`.
5. For an idempotent operation, check a matching key/body record after current
   session, project membership, role, and project scope have been authorized,
   but before loading the original draft or checking version, state, expiry, or
   submission linkage. A draft-submit replay record retains only actor/project/
   operation/key, request digest, original draft UUID and resulting report UUID;
   it stores no body text. Replay is allowed after the 30-day draft is purged
   only if the actor is still an active Reporter and the resulting report is
   still visible to that owner in the project; return its current authorized
   representation without a mutation. A draft-create replay whose draft was
   explicitly deleted returns `404 NOT_FOUND`. Replays never bypass fresh
   authorization, and expired (24-hour) records provide no replay guarantee.
6. For non-replayed operations, resolve the object only within the authorized
   project and visibility scope. Absent, cross-project, other-user draft, or
   otherwise invisible object returns the same `404 NOT_FOUND` body. Visible
   object but disallowed action/state returns `403 FORBIDDEN` for role/
   ownership policy and `409 INVALID_STATE` for a recognized role's invalid
   lifecycle precondition.
7. Validate field content; invalid bounds/controls/required-on-submit fields
   return `400 VALIDATION_ERROR` with a generic
   message and stable code, no submitted text echoed. Same-key different-body
   returns `409 IDEMPOTENCY_CONFLICT`; a draft
   limit or already-submitted draft uses its stable 409 code; stale version
   returns `409 STALE_VERSION`. Expired draft returns `410 DRAFT_EXPIRED` to its
   authorized owner.
8. Persistence, required audit/security-event sink, or session-store outage
   returns `503 SERVICE_UNAVAILABLE` with no partial mutation or protected data.

Transport shape validation may run before auth, but field-level content
validation and object-specific errors occur only after membership, role, and
visibility checks. Thus malformed framing is a request error; content/existence
cannot be probed across project or ownership boundaries. All successful reads
and errors use `Cache-Control: no-store`. Do not leak SQL, exception, or
cross-project existence details.

## Acceptance criteria and future verification mapping

TD-RPT-01–05 are contract criteria: they may pass independent specification
review when their observable requirements and derivation are complete. Runtime
acceptance remains pending until implementation and required tests pass. The
owning implementation must use fresh TraceDesk migrations and isolated synthetic
test data. No MP3 migrations, live database, or `.env*` files are permitted.

| Criterion | Observable requirement | Required future tests/evidence |
| --- | --- | --- |
| **TD-RPT-01** | Report/draft/clarification fields, Unicode and byte limits, required submission fields, UUID/UTC/version rules, draft retention/caps and report representation match this contract. | Pure validation tests at exact limits and one over each; multibyte Unicode, supplementary code point, controls, malformed surrogate, blank-required values, aggregate/wire cap; schema round-trip and UTC/version assertions; draft expiry and 20/21 cap; five unanswered clarification requests/sixth denied, and 1,000/1,001 retained entries, asserting `CLARIFICATION_LIMIT` and no partial mutation/audit. |
| **TD-RPT-02** | Trusted identity, fresh membership and project/object/role/ownership checks enforce the visibility, assignment, edit and clarification matrix; membership or assignment changes remove access at the defined authorization point. | Synthetic two-project/three-role API tests: own/other Reporter, same-project Developer/QA, cross-project, forged actor/role/assignee, disabled/removed/changed membership, wrong/unassigned Developer, valid QA assignment/recovery, Reporter edit boundary, clarification states; synchronized revocation/reassignment race proving no unauthorized commit. |
| **TD-RPT-03** | Draft create/edit/delete/submit, report create/edit, versions, idempotency, expiry, persistence, and business audit follow the atomicity and replay rules. | Isolated PostgreSQL transaction tests for successful mutation+one audit, rollback on audit failure, stale version, concurrent same-key and different-key races, same-key changed body, duplicate draft submission, retry after membership revocation, immutable submitted draft, exact retention and purge behavior; submit just before expiry, purge the draft, retry the original key within 24 hours and assert the current authorized report with no second mutation/audit, then revoke authorization and assert replay denial. |
| **TD-RPT-04** | Interfaces, bounded pagination, response allowlists, error precedence, no-store behavior and safe failures match the tables. | API contract tests per route for allowed/denied/unavailable/stale/concurrent matrix; request-field allowlist, pagination binding/tamper/expiry, no private notes or foreign content, opaque 404, safe body/log/audit metadata, CSRF/session behavior, no partial response or mutation on persistence/event failure. |
| **TD-RPT-05** | Each criterion maps to tests and evidence; threat boundaries, implementation exclusions, actual coordination, independent review and measurements are recorded without implying feature acceptance or human approval. | Independent reviewer checks actual spec/change for contradictions, authorization-before-selection/delivery, transaction/audit/version coverage and test derivability. Implementation evidence records exact revision, paths and UTF-8 bytes, commands/results, actual handoffs/review/corrections, UTC intervals and observable calls/retries; unknown tokens/costs stay unknown. Link focused security tests and the applicable development-context comparison plan. |

## Delivery dependencies

### REPORT-DATA (#13) executable acceptance criteria

The #13 implementation is limited to report creation, report list/detail and
the minimal Reporter context read. Drafts, edits, transitions, clarification,
frontend and AI remain excluded. `textLogs` is present as a string in a create
body (empty is valid); the other six strings must be nonblank. All roles follow
the visibility rules above. Create uses a required canonical UUID key, 24-hour
replay and an atomic business audit. No report update API is added in this slice.

| Criterion | Observable requirement and verification |
| --- | --- |
| **TD-RPT-06** | Fresh isolated migrations enforce project/user references, seven text fields and bounds, lifecycle allowlist, positive version and UTC/audit persistence. DB tests reject invalid references, missing/blank required text, exceeded bounds, status and version; no fixtures use a live DB. |
| **TD-RPT-07** | Trusted session actor plus locked fresh membership/role authorize create; one version-1 OPEN report, idempotency record and bounded metadata-only audit commit together. Tests cover exact text/UUID/time round trip, same-key replay, changed-body conflict, expired key, concurrent replay and rollback on audit failure. |
| **TD-RPT-08** | List summaries and detail enforce own-Reporter/all-project-staff visibility; bounded keyset pages, status/assignment filters, purpose/actor/project/filter-bound expiring cursors and safe response allowlists. The minimal Reporter context read derives project/name/role server-side. Tests exercise pagination, tamper/expiry/rebinding, object opacity and context role denial. |
| **TD-RPT-09** | Anonymous, forged body identity, wrong role, cross-project/owner, disabled membership/user/project, malformed/oversized input and required persistence/event failure return the specified safe errors/no-store headers without forbidden mutation or provider invocation. Direct session/CSRF API tests exercise precedence, strict JSON types/duplicate/unknown keys, body caps and secret-safe diagnostics. |
| **TD-RPT-10** | Pure policy/validation, direct API and isolated PostgreSQL tests cover allowed/denied roles, create/audit rollback, positive initial version and synchronized membership/replay concurrency. Missing isolation settings fail required integration tests. Evidence pins the candidate, actual commands/results, independent review/corrections and measurement limitations. |

Versioned edit conflicts are #15; #13 proves the initial version invariant and
concurrent create/replay constraints, and does not claim an unimplemented edit
conflict test. Cursor signing is an internal server responsibility; key material
never appears in responses or logs. Implementation records its key lifecycle
and resulting restart behavior without adding a provisioning API.

This contract defines the bounded data and shared domain boundaries for the
Reporter report slice. The planned implementation issues are sequenced as:

| Issue | Planned ownership |
| --- | --- |
| #13 | report create/list/detail persistence (TD-RPT-06–10) |
| #14 | Reporter workspace and report form UI (TD-RPT-11–15), including the bounded Reporter context read |
| #15 | drafts and versioned report editing (TD-RPT-16–20) |
| #16 | read-only progress activity history (TD-RPT-21–25); clarification requests and replies are outside this issue |
| #17 | Reporter AI preview/apply (TD-RPT-26–30); implementation is blocked on the separately specified secured shared gateway |

These future issue IDs and runtime criteria are planned dependencies, not
acceptance claims in this contract. The current protected project read is only
`{id,name}`; the Reporter context read above is a newly specified future
interface because that endpoint carries no role today. The secured gateway is a
separate prerequisite and is not within issue #17's implementation scope.

Applicable security assertions include cross-project and other-owner denial,
fresh membership/assignment checks, no unauthorized data selection or response,
no forged request identity, race-safe mutation, no user content in logs/audit,
escaped plain text in clients, request/body bounds, and failure-closed event and
database behavior. AI injection/provider/context-selection tests are not
applicable to this slice because it defines no AI invocation; the shared gateway
remains a prerequisite for a later AI slice. The Reporter development-context
comparison is owned by the project coordinator and remains due before final MP4
acceptance; this contract task is not a controlled comparison. Application AI
quality evaluation remains due after all secured role capabilities exist.

### REPORT-PROGRESS (#16) executable acceptance criteria

These criteria apply only to the read-only activity history above. Clarification
requests and replies are explicitly descoped from issue #16 and retain their
separate existing contract and historical evidence. The current eight-event
implementation and local verification are recorded in
[progress evidence](../reporter/evidence/reporter-progress-integration.md); this does not
claim deployed acceptance.

| Criterion | Observable requirement | Planned verification |
| --- | --- | --- |
| **TD-RPT-21** | Only an authenticated Reporter with fresh active membership and ownership of the report in the route project can read activity. Active project members without Reporter role receive `403`; another Reporter, cross-project report IDs, absent reports, disabled users, and revoked memberships receive opaque `404`; no denial returns activity data. | Direct API tests for owner success; Developer/QA `403`, foreign Reporter/cross-project/absent report/disabled user/removed or disabled membership opaque `404`; synchronized membership-revocation check; verify safe response and no-store headers. |
| **TD-RPT-22** | The response contains only the exact item fields and only successful events in the eight-code allowlist. QA save, close, and send-back expose only confirmed prior/new status and version metadata; assignment events, actor/request IDs, private notes, QA checklist data, resolution content, and send-back reasons are excluded. | Populate eligible and excluded synthetic audit rows, including each QA decision, assignment, private-note and unknown codes; assert exact JSON keys and lifecycle mapping, no private strings or identity fields, and no audit metadata in logs. |
| **TD-RPT-23** | Pages default to 25 and cap at 100, return items in `occurred_at ASC, id ASC` order, and continue without duplicates or omissions across equal timestamps. The signed 15-minute cursor is bound to purpose, actor, project, report, filter and sort, preserving its bigint ID key. | Isolated PostgreSQL test with >100 rows and duplicate timestamps; walk all pages and compare to complete ordered result; check default, 1, 100 and invalid limits; tamper, expiry, actor/project/report/purpose/filter/sort rebinding; verify audit index supports report-scoped keyset query. |
| **TD-RPT-24** | The Reporter view presents loading, nonempty chronological history, an explicit empty state, denied access, and unavailable service states; the existing current-status display remains available independently of history loading. | Component/API integration checks for each response state (200 rows, empty 200, 403/404 denial, 503 unavailable, pending request); verify no stale report's activity appears after navigation or denial. |
| **TD-RPT-25** | The read path selects only approved columns after authorization, rechecks authorization before delivery, and handles audit-store failure as `503` without a partial page. Fresh TraceDesk schema includes the report activity index; implementation evidence records exact candidate and results without claiming clarification coverage. | Repository/API tests for authorize-before-query and before-delivery, audit-store failure, query projection and index migration; focused isolated database/API checks and independent review of route, response allowlist, cursor binding and denied cases; record exact revision, commands and outcomes. |

## Design choices and implementation status

The limits, 30-day draft retention, 20-draft cap, 24-hour idempotency retention,
role visibility, QA-only assignment/recovery, and clarified error precedence
above are bounded design choices within the approved product and architecture.
They are not claims of human approval, implementation, verification, or release.
No unresolved material authorization or security boundary remains for this
bounded contract. Specified: behavior and future test mapping only. Pending:
implementation and runtime tests. Contract review, corrections, index integration
and measurements are recorded in [REPORT-PLAN evidence](../reporter/evidence/reporter-reports-plan.md).
