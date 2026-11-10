# TD-PROJ — Shared project creation, discovery and open joining

Status: implementation candidate for issue #55. Human owner: not supplied.

## Goal and trusted role contract

Allow any authenticated, enabled account whose operator-assigned role is
`REPORTER`, `DEVELOPER`, or `QA_REVIEWER` to create a project, browse the
authenticated project directory, join an enabled project immediately, and open
any project where they have active membership. The role belongs to the account
and remains the same in every project. It is set during account provisioning,
before project selection. There is no application API or UI to register an
account, select/change a role, administer a project, or grant another user
membership. Project creation grants only the creator's normal membership; it
does not confer owner powers.

The server obtains the actor UUID from the authenticated session, checks the
enabled account and account role from PostgreSQL, then independently checks
active membership for each project-content operation. Neither request fields,
headers, paths, directory results, browser state nor creator status establish
identity, role, or access. The session holds only actor identity and timestamps;
it does not cache the account role. A committed account-role change deletes all
sessions for that user in the same transaction. The next request must sign in
again.

## Existing-role migration and explicit operator mapping

The current schema stores role on each membership. A user may therefore have
different historical roles across projects, or no memberships from which to
infer a role. Migration must not pick a majority, prefer active memberships, or
assign a default. Add a nullable `td_users.account_role` with a check for the
three permitted values. Backfill it only where every existing membership row
(including disabled memberships) has the same role. Preserve the old
membership `role` values as legacy migration evidence; no authorization query
may use them after this migration.

Create a bounded migration-pending table containing user UUID, reason
(`CONFLICTING_MEMBERSHIP_ROLES` or `NO_MEMBERSHIP_ROLE`) and distinct legacy
role codes for every account whose account role remains null. Do not copy login,
report, draft or note content into this table. For each conflict, the operator
reviews the old role set and project UUIDs with the authorized data owner,
chooses one account role, and explicitly accepts how it applies in each
existing project. Reporter, Developer and QA access are not totally ordered, so
the migration cannot infer a least-privilege role. Do not inspect or prepare
mappings from a real application database as development work.

Provide an offline SQL mapping template that loads an explicit UUID-to-role
mapping into a temporary table and, in one transaction, verifies that every
pending UUID appears exactly once, no extra UUID appears, every value is one of
the three allowed role codes, and no account role changes without its pending
row. It updates `account_role`, deletes that user's sessions, and removes the
pending row only after successful mapping. Any failed completeness/role check
raises an error and rolls back the entire mapping transaction. Never include
login or private content in the mapping artifact. Accounts with pending
mappings are ineligible for login/session refresh and all project operations
until resolved. Fresh operator-provisioned accounts must supply an account
role; there is no default. Ordinary startup may continue with pending rows but
must fail closed for each affected account.

Project membership keeps its active/disabled state and project/user keys. Its
legacy `role` snapshot is not an authorization source. New memberships copy
the account role for compatibility until a separately coordinated contract
removes the column. All report, investigation, AI and workspace authorization
must resolve the role through the trusted user/account row and fresh membership
state. Project/report ownership and existing content remain unchanged.

## Project name and API contracts

Normalize project names by trimming Unicode whitespace and NFC normalization.
Require 1–120 Unicode code points and at most 480 UTF-8 bytes after
normalization; reject control characters and blank values. Store and return
names as plain text, and escape them in the browser.

Authenticated APIs use JSON, `Cache-Control: no-store`, the existing safe error
shape and session/CSRF policy. State-changing requests require the current
`X-CSRF-TOKEN`. Do not accept actor or role properties; reject unknown request
fields and duplicate JSON keys.

| Request | Success | Denial / failure |
| --- | --- | --- |
| `GET /api/session` | `200 {userId, role}` using fresh enabled account state | Existing `401 UNAUTHENTICATED`; `503 SERVICE_UNAVAILABLE` on persistence failure |
| `GET /api/projects?limit=50&cursor=...` | Up to 50 safe summaries `{id,name,membership}` with stable opaque next cursor; membership is `ACTIVE`, `DISABLED`, or `NONE`; every enabled project appears to an enabled authenticated actor regardless of their own membership | `401 UNAUTHENTICATED`; `400 INVALID_REQUEST` for invalid bounds/cursor; `503 SERVICE_UNAVAILABLE` |
| `POST /api/projects` | `201 {id,name}` after project, creator `ACTIVE` membership using account role, and `PROJECT_CREATED` audit commit | `400 INVALID_REQUEST`; `401 UNAUTHENTICATED`; `403 CSRF_INVALID`; `409 IDEMPOTENCY_CONFLICT`; `503 SERVICE_UNAVAILABLE` |
| `POST /api/projects/{projectId}/join` | `200 {project:{id,name},membership:"ACTIVE"}` after membership and `PROJECT_JOINED` audit commit | `400 INVALID_REQUEST`; `401 UNAUTHENTICATED`; `403 CSRF_INVALID`; `404 NOT_FOUND` for absent/disabled project; `409 MEMBERSHIP_DISABLED`; `503 SERVICE_UNAVAILABLE` |

Directory pages include enabled projects even when they have no active members.
They are ordered by normalized case-insensitive project name then UUID. A
cursor is opaque, integrity-protected and bound to the order/version;
it cannot request a project ID or private fields. `ACTIVE` lets the user open
the project. `NONE` offers Join. `DISABLED` is visible as unavailable, with no
Join action. Directory data never includes member lists, report content, drafts,
notes, credentials, account identifiers, role details or private role data.
Anonymous discovery is denied.

## Create and join transaction rules

`POST /api/projects` accepts only `name` and a canonical UUID
`Idempotency-Key`. The actor is the session actor. One database transaction
validates the enabled account and assigned role, inserts the enabled project,
the creator's active membership (role snapshot copied from the account), an
idempotency record bound to the normalized request hash, and one metadata-only
business audit row. Same actor/key/request replays the original result without
another audit event. Reusing a key with a different request returns 409.
Concurrent equal requests create one project and one audit row. Audit failure
rolls back project, membership and idempotency record.

`POST /api/projects/{projectId}/join` accepts only a canonical UUID path and
has no body or client idempotency key. In one transaction it locks the enabled
project row, rechecks enabled actor, assigned role and current membership, then
creates the membership if absent. An existing active membership returns the
stable success result without another mutation or audit event. A concurrent
request waits for the project lock and observes the committed active row; only
one logical join event is written. Database membership uniqueness remains a
second race guard. A disabled membership is a revoked
grant: open join cannot reactivate it and returns a fixed conflict. A removed
membership (no row) may be explicitly re-created by a later join, creating a
new audit event; deletion itself is outside this feature. Disabled users and
projects fail closed. Audit failure rolls back the join.

Business audit events are distinct from security events and AI accounting.
They contain only event code, request UUID, actor UUID, project UUID, outcome
and UTC timestamp. They never contain project names, role-private content,
credentials, session identifiers or request bodies.

Extend the existing security-event allowlist with routes `/api/projects`,
`/api/projects/{projectId}/join` and event codes `PROJECT_INVALID_REQUEST` and
`PROJECT_MUTATION_DENIED`. Record every denied or malformed request to these
routes: unauthenticated requests, invalid/expired session, CSRF failure,
invalid name/JSON/UUID/cursor/key, idempotency conflict, unavailable project,
disabled membership and denied object access. Use `CSRF_INVALID` for CSRF,
`PROJECT_INVALID_REQUEST` for invalid shape/bounds, and
`PROJECT_MUTATION_DENIED` for other denied outcomes. The event stores only the
route template, status, request UUID, trusted actor UUID when available, and a
project UUID only after canonical parsing. Do not store the name, raw cursor,
key, path, session, token, request body, or exception text. Successful directory
reads create no event. Security-event persistence remains separate from the
business mutation; if it fails, return 503 and do not return directory data or
perform the requested mutation. Project create/join audit persistence failure
rolls back the business transaction and returns 503.

## Frontend behavior

After authentication, the picker states that the role is assigned to the
account and applies to every project. All three roles can create projects and
browse/join the shared directory. Show a labelled create form, active project
directory with membership state and pagination, and immediate Join/Open
actions. Preserve entered name on recoverable failure. Show loading, empty,
validation, unavailable/retry, denied, expired-session and disabled-membership
states. Hide no project solely because the account role differs; selecting an
active membership routes by the account role. Do not show a role picker.

For a role workspace that is not implemented, state this clearly after project
selection without presenting a simulated feature as live. Directory visibility
does not render role workspace data. Existing workspace endpoints continue to
require active membership and freshly loaded role-specific permissions.

## Acceptance criteria

| ID | Observable requirement | Verification |
| --- | --- | --- |
| **TD-PROJ-01** | Account-level trusted role is constrained to the three roles; request/browser values cannot assign it; role changes invalidate sessions; no creator privilege exists. | Pure role policy, API spoofing, persisted role/session invalidation tests. |
| **TD-PROJ-02** | Migration identifies same-role, conflicting-role and no-membership accounts; only unambiguous roles are backfilled; explicit operator mapping is required for all pending accounts; historical memberships, ownership and content remain intact. | Fresh isolated PostgreSQL migration scenarios for each category, invalid/missing mapping denial and preservation assertions. |
| **TD-PROJ-03** | Any enabled authenticated role can create a bounded project with creator membership/account role and metadata audit in one transaction. | API/DB all-role create; malformed/CSRF/role-spoof denial; repeat/concurrent idempotency; audit rollback. |
| **TD-PROJ-04** | Any enabled authenticated user gets a bounded paginated directory of every enabled project name/UUID and their own membership state only; anonymous access is denied. | API privacy, stable ordering/cursor, invalid cursor, page-boundary, anonymous/session-expiry and multi-user tests. |
| **TD-PROJ-05** | Explicit CSRF-protected join immediately creates one active membership with trusted role and audit; repeats/concurrency are idempotent; disabled memberships cannot be reactivated; deleted memberships require a new explicit join. | All-role API/DB tests with synchronized concurrent join, replay, denial and audit failure. |
| **TD-PROJ-06** | Every role can create, discover, join and open their active memberships with truthful workspace routing and visible loading/validation/denied/expired/unavailable/retry states; no client role selector. | Frontend state/API tests and real browser journeys for all preassigned roles, including create/join/reopen. |
| **TD-PROJ-07** | Discovery exposes no report/draft/note/member/credential/private-role data; directory alone grants no content access; existing ownership, assignment and QA boundaries remain enforced. | Cross-project/object denial and response/log/audit canary checks across roles. |
| **TD-PROJ-08** | Required pure policy, session/CSRF API, isolated PostgreSQL migration/concurrency/rollback, browser, and independent review evidence records exact revision and actual outcomes. | Focused commands; evidence distinguishes pass/fail/skip/unavailable and records resolved findings. |

## Boundaries and handoff

Coordinate the global-role source and membership contract with Developer and QA
workspace owners. No new fourth role, public registration, role self-selection,
membership approval, invitations, membership administration, project deletion,
notifications, AI or deployment work is included. Existing account
provisioning remains an operator responsibility. No real database is used to
derive mappings or fixtures.

Before behavior changes, update ADR 002, `auth-membership.md`,
`workspace-discovery.md`, the acceptance index and threat model. Cut over every
role authorization query in `AuthStore`, `ReportStore`, `ReportAiService` and
`InvestigationStore` in the same release so no path trusts the legacy membership
snapshot. Add a regression proving that changing only a legacy membership role
cannot change access while changing the account role does. The fresh migration
keeps old role snapshots for evidence, but all reads and new business
authorization use `td_users.account_role`.
