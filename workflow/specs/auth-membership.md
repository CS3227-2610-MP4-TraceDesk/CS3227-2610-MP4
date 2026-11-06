# TD-AUTH — Local authentication and project membership boundary

Status: behavior contract for the first authentication slice; not implementation
or completion evidence. Human owner: not supplied. The authorized scope is the
bounded first slice described in the 2026-11-06 authentication issue planning
artifact. The foundation approval in [ADR 001](../decisions/001-foundation.md)
and the shared contracts in [ADR 002](../decisions/002-shared-role-contracts.md)
authorize routine choices inside this boundary; neither authorizes deployment,
provisioning, or broader product work.

## Goal, actors, and boundary

Provide local password sign-in for pre-provisioned synthetic users, a PostgreSQL
server-side session, current project-membership authorization, and one minimal
protected project read. A trusted session establishes the user identity. The
membership record establishes that user's role in a particular project. A role
alone never grants access, and no identity, role, or project membership claim in
the browser is authoritative.

The only roles are `REPORTER`, `DEVELOPER`, and `QA_REVIEWER`. A user may have
memberships in multiple projects, but has at most one role in any one project.
Membership rows have `ACTIVE` or `DISABLED` state. Removing a membership deletes
or revokes that row. There is no global user role. A project read is permitted to
any active member of that project, irrespective of the member's role; this slice
does not establish bug-level permissions or imply that every later operation is
available to every member.

The protected resource is `GET /api/projects/{projectId}`, returning only
`{"id":"<uuid>","name":"<name>"}` for an existing project with an active
membership. Project IDs and users are UUIDs. Project names are nonblank, at most
120 Unicode code points, and are treated as plain text by clients. This read is
the whole protected business-data surface of this slice.

Out of scope: public registration, invitations, membership administration,
account provisioning, password recovery, SSO, account lockout policy, bug data or
lifecycle, AI invocation, role UI/journeys, deployment, and production seed data.
All test users, projects, memberships, and credentials are synthetic fixtures.
There are no domain mutations in this slice, so a business audit transaction is
not applicable; a denied security event is an operational security record, not a
business audit event.

## Persistence and identity semantics

Add fresh ordered TraceDesk migrations for `users`, `projects`,
`project_memberships`, and Spring Session's JDBC tables. Use UUID primary keys,
UTC `timestamptz` values, foreign keys, a unique normalized login identifier, a
unique `(project_id, user_id)` membership, and a database constraint for the
three role identifiers and two membership states. User records contain an
Argon2id encoded password hash, enabled state, and UTC creation/update timestamps;
they never contain a recoverable password. Password changes, deletion, or user
disablement are not API features in this slice.

The login identifier is an email-shaped local identifier, normalized by trim and
Unicode NFKC followed by lowercase using `Locale.ROOT`; it is not used to send
email. Accept 3–254 UTF-8 bytes after normalization, with exactly one `@`, a
nonempty local and domain part, no control characters or whitespace, and treat a syntactically present but format-invalid or out-of-bounds identifier as invalid credentials, with the exact same public response and session behavior as a bad password. Malformed JSON, missing fields, wrong types, or unknown fields remain request-shape errors. The identifier is
not returned by any endpoint. Passwords are 12–128 Unicode code points and at
most 512 UTF-8 bytes; do not trim, normalize, or silently truncate them. A
synthetic account must be provisioned outside the application API.

Hash passwords with Argon2id, unique random salt, and at least 19 MiB memory,
2 iterations, parallelism 1. Store a self-describing encoded hash so parameters
can be migrated; use a maintained implementation and constant-time verification.
Unknown and format-invalid identifiers perform a verification against a fixed valid dummy hash so
the login response does not reveal account existence. These are minimum settings;
benchmarking may increase work factors without changing this wire contract.

Spring Session JDBC stores opaque session identifiers and server-side attributes
in PostgreSQL. The session contains only the authenticated UUID and timestamps
needed for the 30-minute idle and 8-hour absolute limits; it does not cache roles
or membership state. Never issue a self-contained identity/role token. Delete
the server-side session on logout, expiry, authentication failure after session
creation, and invalidation events described below. Persistence outages fail
closed.

## HTTP contract

All API responses are JSON except `204`. Responses containing authentication or
project data use `Cache-Control: no-store`. Errors have the fixed shape
`{"error":{"code":"<stable-code>","message":"<generic-message>"}}` with no
stack, SQL, credential, or existence detail. All routes return `X-Content-Type-Options:
 nosniff`, `Referrer-Policy: no-referrer`, and a restrictive same-origin
`Content-Security-Policy`; API responses do not enable cross-origin access.

| Request | Success | Failure behavior |
| --- | --- | --- |
| `GET /api/csrf` | `200 {"token":"<opaque-token>"}`; creates/refreshes server-side CSRF state and the TDSESSION cookie only | `503 SERVICE_UNAVAILABLE` if session/CSRF persistence is unavailable |
| `POST /api/session/login` | `200 {"userId":"<uuid>"}`; rotates session ID and sets session cookie | `400 INVALID_REQUEST` for malformed/oversized JSON, missing/incorrect field types, unknown fields, or oversized body; `401 INVALID_CREDENTIALS` identically for unknown, disabled, wrong-password, or format-invalid/out-of-bounds identifiers; `403 CSRF_INVALID`; `503 SERVICE_UNAVAILABLE` on required persistence or event failure |
| `GET /api/session` | `200 {"userId":"<uuid>"}` for a valid session | `401 UNAUTHENTICATED` when absent, expired, revoked, or user disabled; `503 SERVICE_UNAVAILABLE` when session/user persistence is unavailable |
| `POST /api/session/logout` | `204`; invalidates the current server session and clears its cookie | `401 UNAUTHENTICATED` if no valid session; `403 CSRF_INVALID`; `503 SERVICE_UNAVAILABLE` if invalidation cannot be confirmed |
| `GET /api/projects/{projectId}` | `200 {"id":"<uuid>","name":"<name>"}` after fresh membership authorization | `400 INVALID_REQUEST` for malformed UUID; `401 UNAUTHENTICATED`; `404 NOT_FOUND` for absent, disabled, or non-member project; `503 SERVICE_UNAVAILABLE` if required persistence/event recording fails |

Login accepts only `{"login":"...","password":"..."}`; reject unknown
fields, duplicate JSON keys, nulls, wrong types, and bodies over 8 KiB with
`400 INVALID_REQUEST`. A syntactically present but invalid-format or out-of-bounds
login identifier returns `401 INVALID_CREDENTIALS`, just like a wrong password. No request accepts `userId`, `role`, or
membership claims; if such fields are supplied, reject as malformed. Project UUID
path segments have a strict canonical UUID representation. `GET /api/session`
never returns roles or project membership. The protected project response does
not include membership or user details.

Unknown identifiers, wrong passwords, and format-invalid or out-of-bounds
identifiers have identical status, response body, and cookie behavior. They do not create or upgrade a session. Unknown project and
non-membership both return the same `404` body to avoid disclosing project
existence. An authenticated member with any of the three roles receives the
same project representation. There is no `403` role distinction at this
resource.

## Cookies, session lifecycle, and CSRF

The session cookie is named `TDSESSION`, is `HttpOnly; Secure; SameSite=Lax;
Path=/`, has no `Domain`, and is non-persistent (browser-session cookie); server
expiration remains authoritative. Every deployment and test exercising cookies
uses HTTPS. Do not weaken `Secure` to accommodate plain HTTP tests. Add
`Strict-Transport-Security` when served over HTTPS. Authentication rotates the
session identifier; the pre-authentication identifier is invalid immediately
and cannot inherit authenticated state. Logout invalidates server state before
returning `204` and expires the cookie with matching attributes.

`GET /api/csrf` establishes a session-backed unpredictable CSRF token and
returns it in JSON for same-origin clients. The session cookie is never exposed
to JavaScript. Every state-changing request, including login and logout, requires
`X-CSRF-TOKEN` equal to the token in the current server-side session. The CSRF
token is not accepted from a request body, URL, or cookie. A missing, malformed,
or stale token returns `403 CSRF_INVALID` before credentials are checked or
session/business state is changed. Rotate the token when an anonymous session
becomes authenticated and after logout; the client obtains a fresh token using
`GET /api/csrf`. SameSite is defense in depth, not a replacement for token checks.

Spring Security adapters enforce a 30-minute server-side idle timeout and
8-hour absolute lifetime from authentication. A request cannot extend the
absolute lifetime. Authenticated requests renew idle expiry only after valid
session lookup. Expired sessions are deleted on access or normal session cleanup;
the request immediately receives `401`. The browser cookie is not evidence of a
live session without the corresponding PostgreSQL record.

## Membership freshness, revocation, and concurrency

Resolve the actor UUID from the server-side session. For every protected project
read, load the project and active membership from PostgreSQL in the same
application-level authorization operation; do not use a role or membership
cached in the session, process memory, or request. Missing/disabled membership,
disabled user, or absent project denies delivery using the `404` contract.

Every request checks the membership row against the database after the request
begins authorization. A membership disable, removal, role change, or user
disable becomes effective for authorization checks that observe the committed
change. If a request has already completed its membership query before the
revocation commits, that in-flight read may complete; revocation does not cancel
an already authorized response. After the change commits, a subsequently
started authorization query must observe it under PostgreSQL READ COMMITTED.
No long-lived transaction or row lock is held for this read. This provides a
clear linearization point at the membership query and avoids claiming that an
already running request can be recalled.

A membership role change invalidates all sessions for that user as part of the
same database transaction as the membership update, then the user's next
request must authenticate again. Membership removal/disable and user disable
also delete that user's sessions transactionally. Because the current slice has
no management API, these operations are specified for persistence/admin paths
that may be added later; tests must invoke the persistence operation directly.
If session deletion cannot be committed with the change, the membership change
must roll back. Fresh membership lookup still denies access even if a stale
session record remains due to an abnormal manual database action. Concurrent
revocation and read outcomes follow the linearization rule above; no `409` or
version token is relevant because the endpoint is read-only.

## Denials, security events, and failure policy

Record denied login attempts (including bad/unknown credentials and malformed
login requests) and protected-project denials (unauthenticated, non-member,
disabled user/membership, absent project, invalid identifier, and invalid CSRF)
as security events. Do not record successful reads or login attempts in this
slice. Events have a UTC timestamp, stable event code, request correlation UUID,
route template, outcome status, and actor UUID only when already trusted. A
project UUID may be recorded only after successful strict parsing. Never store
raw path text, login identifier, password, request body, session ID/token,
CSRF token, cookie, IP address, user agent, project name, or exception message.
Bound all string fields to fixed allowlisted codes/templates. Security events
are operational records and are not business audit events.

Persist each denial event to a dedicated security-event table in PostgreSQL,
without coupling it to a business-data transaction. If the sink is unavailable
or event persistence fails, fail closed: return `503 SERVICE_UNAVAILABLE`, do not
deliver protected project data, do not establish/upgrade a session, and do not
claim the denial was recorded. A bad-credentials request normally returns
`401`; if its required event cannot be stored, it returns `503`. Do not fall back
to an unbounded file log or include secrets in logs. Ordinary authentication,
session-store, or membership-store failures also return safe `503` and fail
closed.

## Required application work and module boundaries

The repository currently contains `backend/ai-transport` (internal provider
transport only) and `backend/db-test-support` (isolated PostgreSQL test support).
It has no Spring application, domain, user/project persistence, session, or
business-audit module. This slice therefore requires new TraceDesk application
work: a Spring Boot deployable module with security/API adapters and composition;
framework-independent application/domain identity and authorization policy
types; PostgreSQL migrations and repository adapters; JDBC session and security
event persistence; and integration/API tests. These may be packages in one
deployable backend rather than separate deployables. Keep domain/application
policy independent of Spring, HTTP, and concrete SQL types. The transport module
must remain internal and must not be connected to these routes. Use
`backend/db-test-support` with the dedicated ephemeral test database and its
existing guard; missing required test-database configuration is a failed check,
not a skip or pass. Never read `.env*`, use a live database, or import MP3
migrations.

## Acceptance criteria and evidence plan

All criteria below are unchecked until implemented and independently verified.
Evidence belongs under `workflow/reporter/evidence/`; keep it sanitized and record
candidate revision/working-tree identity, exact commands, outcomes, selected
context references and byte counts, actual handoffs/review, and unknown tokens or
costs as unknown. Selected-file bytes exclude inherited or hidden host context.
The four issue bodies in `workflow/reporter/specs/auth-slice-issues.md` divide
implementation ownership but do not replace this contract. The Reporter
focused-versus-broader development-context comparison is due before final MP4
acceptance; it is not a result of this specification run.

| Criterion | Observable behavior | Planned verification and evidence |
| --- | --- | --- |
| TD-AUTH-01 | API, role cardinality, field bounds, password hashing, session, CSRF, cookie, denial-event fields/failure behavior, and module work are fully specified here. | Contract review against each section and deterministic test derivation; `workflow/reporter/evidence/auth-membership.md` records independent spec review and any corrections. |
| TD-AUTH-02 | Anonymous, member, wrong role, non-member, cross-project, forged identity, malformed input, disabled/removed membership, expired/revoked session, unavailable persistence/event sink, freshness and concurrent-revocation outcomes match the tables and rules above. Stale versions are inapplicable because the only business endpoint is read-only. | Parameterized API tests plus synchronized PostgreSQL revocation/read test; assertions cover status/body, no protected fields, no session upgrade, and event behavior. |
| TD-AUTH-03 | Each criterion maps to planned tests/evidence; the contract has no unresolved material decision within the bounded slice and records no human owner/approval not supplied. | Independent reviewer records findings/resolution, exact revision and scope in `workflow/reporter/runs/` and evidence artifact; do not label author self-review independent. |
| TD-AUTH-04 | Fresh migrations enforce identity/project/membership references, UUIDs, valid role/state, one role per user/project, normalized login uniqueness, and session/security-event persistence. | Isolated PostgreSQL migration and constraint tests with synthetic fixtures; verify stored rows and rejection cases under `backend/db-test-support`. |
| TD-AUTH-05 | Pure authorization policy and persistence allow any active member to read only their project; deny anonymous, non-member, disabled, forged, and cross-project access for all three roles. | Policy matrix plus isolated API/database tests over all roles and at least two projects; assert zero unauthorized data delivery or business mutation. |
| TD-AUTH-06 | Persistence failure fails closed; committed membership/user disable, removal, or role change has the freshness/session invalidation defined above. No domain mutation endpoint exists. | Isolated PostgreSQL failure and synchronized independent-transaction race tests; role/removal invalidation test; record business-mutation/audit assertion as inapplicable and assert no write route exists. |
| TD-AUTH-07 | Valid synthetic credentials authenticate; invalid/unknown credentials are indistinguishable and cannot create an authenticated session; password hashes are Argon2id; session ID rotates; request identity/role fields cannot replace the trusted actor. | Direct API tests with cookie jar; fixation/replay, unknown-user, format-invalid identifier, malformed-body and credential-canary log/response assertions. |
| TD-AUTH-08 | Anonymous/expired/revoked sessions fail; logout invalidates replay; cookie, CSRF acquisition/rotation, session lifetime, and headers follow this contract; state changes with absent/stale CSRF have no effect. | API security tests over login/logout and protected calls, including HTTPS cookie attributes, missing/wrong/stale token, expiry and replay. |
| TD-AUTH-09 | All three roles exercise allowed member reads and denied project/identity cases; unavailable session/membership persistence returns safe failure; no AI/provider path or business mutation is invoked. | Direct HTTP tests over two projects; assert exact response data, no forbidden writes/provider calls, and no identity/role trust from supplied fields. |
| TD-AUTH-10 | After committed membership/user invalidation, old sessions cannot authorize a subsequent request; races obey the stated query/commit boundary; errors and events contain no credential/token/private-field canaries; unavailable event sink follows fail-closed behavior. | Deterministically synchronized PostgreSQL revocation tests, safe-error/log/event assertions, injected event-store failure, malformed/oversized inputs. |
| TD-AUTH-11 | A reviewer who did not author implementation inspects actual diff and tests; criteria map to exact candidate checks, failures/skips/unavailable states remain visible, and findings are resolved/retested. | Independent implementation review and evidence matrix covering TD-AUTH-01–10, with revision, commands, assertions, severity/location/impact, correction and retest. |
| TD-AUTH-12 | Actual development intervals, context references/UTF-8 byte counts, observable calls/retries, tests, review corrections and reflection are recorded without invented tokens/costs or comparative claims. | Sanitized run/evidence artifacts under `workflow/reporter/`; comparison references the existing deferred plan, with owner/due milestone recorded by coordinator before final MP4 acceptance. |

## Applicable security boundaries and deferred work

Applicable threat-model rows: forged role/project/object identifiers; CSRF or
stolen session; credential/error/log leakage; stale permissions; unsafe database
fixtures; malicious issue/document/agent handoff. Assert no identity from request
fields, no protected data on denial, no session elevation on failed login, no
business mutation/provider invocation, and no secret in responses, logs, events,
or evidence. Use synthetic inert canaries. Run database tests only through the
isolated test helper and dedicated test database. Update the threat model when
implementation makes the boundaries concrete.

AI context selection, provider accounting, bug-object authorization/versioning,
prompt injection, suggestion delivery, and deployment are out of scope because
this slice adds none of those paths. Before the first AI slice, complete the
required development-agent injection exercise from the Reporter gates. No
authentication, security control, acceptance criterion, or human approval is
claimed complete by this design document.

## Source references

- [Product scope](../../docs/product.md)
- [Architecture](../../docs/architecture.md)
- [Security and deployment](../../docs/security.md)
- [Threat model](../../docs/threat-model.md)
- [ADR 002 shared role contracts](../decisions/002-shared-role-contracts.md)
- [Authentication issue planning artifact](../reporter/specs/auth-slice-issues.md)
- [Reporter completion gates](../reporter/agentic-se.md)
- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
- [Spring Security CSRF reference](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
