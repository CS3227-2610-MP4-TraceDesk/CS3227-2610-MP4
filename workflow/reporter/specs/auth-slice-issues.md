# Authentication slice: issue planning and progress

Status: issue-planning artifact with progress updated for AUTH-DATA. The canonical behavior contract now exists at `workflow/specs/auth-membership.md`; current candidate evidence is linked below. This file does not close or update the corresponding GitHub issues.
Prepared from the user's 2026-11-06 request to create spec-driven GitHub issues before implementation.
Human owner: not supplied. GitHub issue links remain external to this planning file.
Criteria retain stable IDs; status and candidate evidence are recorded per issue and in [workflow/acceptance-criteria.md](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/acceptance-criteria.md).

Shared authority: [AGENTS.md](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/AGENTS.md), [docs/product.md](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/docs/product.md), [docs/architecture.md](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/docs/architecture.md), [docs/security.md](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/docs/security.md), [docs/threat-model.md](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/docs/threat-model.md), [workflow/decisions/002-shared-role-contracts.md](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/decisions/002-shared-role-contracts.md). Personal gates and evidence follow [workflow/reporter/README.md](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/reporter/README.md) and [workflow/reporter/agentic-se.md](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/reporter/agentic-se.md).

## Issue 1: AUTH-PLAN — Specify the first authentication and project authorization slice

Contract sources: [shared role contracts](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/decisions/002-shared-role-contracts.md), [architecture](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/docs/architecture.md), [security](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/docs/security.md), [Reporter completion gates](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/reporter/agentic-se.md). Canonical feature specification: [workflow/specs/auth-membership.md](../../specs/auth-membership.md); its specification review is recorded in [authentication evidence](../evidence/auth-membership.md).

### Problem and outcome

ADR 002 selects trusted server-side sessions and project-scoped membership, but the acceptance index has no concrete authentication contract. Define the bounded contract before implementing it, so API, policy, persistence and tests share observable requirements.

### Scope and non-goals

Write `workflow/specs/auth-membership.md` (planned; not yet created) using the Reporter feature specification template; update the shared acceptance index and applicable threat-model details. Cover local password authentication, server-side PostgreSQL sessions, membership lookup and a minimal protected project read boundary. Specify one role per user/project or explicitly resolve the cardinality before code. Define role identifiers for Reporter, Developer and QA reviewer without inventing new administrator powers.

Exclude registration, invitations, membership management UI/API, production account provisioning, password recovery, SSO, bug creation/lifecycle/assignment, AI invocation, React role journeys and deployment. Test accounts and memberships are synthetic fixtures only. Application authentication proves this bounded read boundary; it does not establish future bug-object permissions.

### Proposed acceptance criteria and verification

- [ ] **TD-AUTH-01:** Canonical spec names actors, trusted identity source, role/membership representation, chosen minimal protected read, exact request/response/status contracts, bounded field validation, password hashing policy, session lifetime/rotation/invalidation, CSRF flow and cookie/header policy. Define which denied outcomes produce security events, their bounded safe fields, and behavior when the security-event sink is unavailable; distinguish these events from business audit. Reviewer can derive deterministic tests without choosing missing security behavior.
- [ ] **TD-AUTH-02:** A table maps anonymous, valid member, wrong role, non-member, cross-project, forged actor/role, disabled or removed membership, malformed input, expired/revoked session and persistence-unavailable cases to observable outcomes. Define non-disclosure behavior for absent versus inaccessible projects, current-membership freshness and concurrent revocation/request semantics. Mark genuinely inapplicable stale/version cases with rationale.
- [ ] **TD-AUTH-03:** Each criterion maps to a planned policy, isolated-database or API test and an evidence path. A separate reviewer checks the contract and records findings/resolutions. The canonical spec distinguishes routine implementation decisions from material unresolved decisions and records supplied human decisions only.

### Threats and boundaries

Map forged role/project/object identifiers, CSRF/stolen session, credential/error leakage, unsafe database fixtures and malicious issue/handoff rows. Specify no password/session/token logging; no identity trusted from request fields; no provider calls or business-data changes on denied requests. Domain types stay independent of Spring, HTTP and concrete SQL. Document security event versus business audit semantics; any implemented domain mutation must commit with its business audit in one transaction.

### Decisions to settle before dependent implementation

Choose login identifier normalization and bounds, hashing algorithm/work factor, API names/statuses, session expiration/store behavior, CSRF acquisition and refresh, membership cardinality/active-state representation, session invalidation on role changes, and safe permission-denial responses. Choose a useful minimal read that can demonstrate project-object access without adding bug features. These are open details, not prior approvals; resolve ordinary choices within approved architecture and escalate only material scope/security decisions. Production provisioning remains deferred.

### Dependencies, ownership and completion gate

No application issue may start before this contract gate is met. Specifier owns the draft; coordinator integrates shared contracts; independent reviewer records actual review under `workflow/reporter/runs/`. Use a task budget and minimal context, record actual elapsed time/calls/context bytes and unknown token usage. Complete when the spec/index and reviewed criterion-to-test matrix exist and no material decision needed for this slice remains unresolved. Issue text alone is not a completed specification.

## Issue 2: AUTH-DATA — Implement membership persistence and project access policy

Contract sources: [shared role contracts](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/decisions/002-shared-role-contracts.md), [architecture](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/docs/architecture.md), [security](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/docs/security.md), [Reporter completion gates](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/reporter/agentic-se.md). Canonical feature specification: [workflow/specs/auth-membership.md](../../specs/auth-membership.md). Candidate verification is recorded in [AUTH-DATA evidence](../evidence/auth-membership.md#auth-data-issue-2-follow-up).

### Problem and outcome

Implement the AUTH-PLAN contract for PostgreSQL identities/projects/memberships and framework-independent project authorization, allowing a role feature to consume a trusted actor and checked project context.

### Scope and non-goals

Own fresh ordered TraceDesk migrations, repository ports/adapters and pure authorization policy tests. Use UUID identity keys, UTC timestamps and constraints dictated by the canonical contract. Use synthetic fixtures to represent Reporter, Developer and QA reviewer across two projects. No membership administration endpoint, public registration, bug tables/lifecycle, production seed accounts, copied MP3 schema or AI/provider wiring.

### Proposed acceptance criteria and verification

- [x] **TD-AUTH-04:** Fresh migrations apply to the dedicated isolated test schema; required identity/project/membership references, role validity and membership uniqueness/cardinality constraints reject invalid and duplicate fixtures. Tests assert persisted results, not only successful migration startup. Verified on the `codex/auth-data` candidate; see the linked evidence and run record.
- [x] **TD-AUTH-05:** The policy and HTTP tests allow the canonical project read for all three roles and deny anonymous, non-member and forged/cross-project access. Tests cover at least two projects and assert no unauthorized project data is delivered. The canonical contract grants the read to every active role, so no role-specific project-read denial exists; invalid role values are rejected by persistence. Verified on the `codex/auth-data` candidate; see the linked evidence and run record.
- [x] **TD-AUTH-06:** Unavailable persistence fails closed. Existing sessions are invalidated after committed membership removal/disable/role change, and synchronized authorization/revocation tests verify the documented query boundary. The HTTP suite verifies session-creation outage returns 503 without issuing a session cookie. Business mutation/audit atomicity is inapplicable: no domain mutation or membership administration endpoint exists. Verified on the `codex/auth-data` candidate; see the linked evidence and run record.

### Threats, dependencies and evidence

Depends on AUTH-PLAN. Covers forged role/project/object IDs, stale permissions, fixture targeting and secret leakage. Use `backend/db-test-support` only as a test dependency, verify test/schema ownership before mutation, and make the required CI database suite fail when configuration is missing. Keep domain independent of Spring/SQL/browser types. Coordinator owns shared migration ordering; parallel work requires stable interfaces and disjoint files.

### Completion gate

Map TD-AUTH-04–06 to exact files/tests and actual commands/revision in `workflow/reporter/evidence/`; record real implementer handoff, context/resources and independent diff review. Resolve findings and pass applicable checks. A skipped/unavailable required database test leaves this issue unverified. AUTH-VERIFY collects cross-boundary evidence; this issue still requires its own focused tests before handoff.

## Issue 3: AUTH-API — Integrate server-side sessions and a protected project API

Contract sources: [shared role contracts](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/decisions/002-shared-role-contracts.md), [architecture](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/docs/architecture.md), [security](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/docs/security.md), [Reporter completion gates](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/reporter/agentic-se.md). Canonical feature specification: `workflow/specs/auth-membership.md` (planned; not yet created by this issue-planning task). Criteria are proposed and unchecked.

### Problem and outcome

Expose the smallest useful authenticated API boundary using the canonical login, current-session, logout and protected-project contracts. Requests obtain identity from trusted server-side session context and use current project authorization before returning protected data.

### Scope and non-goals

Add the Spring application/API security integration and PostgreSQL session storage selected by the specification. Implement password verification, session lifecycle, CSRF, safe errors and security headers. Supply only the protected read named in AUTH-PLAN. No registration, provisioning, role-management endpoint, full React portal, bug workflow, AI endpoint or deployment.

### Proposed acceptance criteria and verification

- [ ] **TD-AUTH-07:** Valid synthetic credentials authenticate; invalid/unknown credentials have the specified safe non-enumerating response and cannot yield an authenticated session. Passwords are hashed using the canonical policy and never returned/logged. Authentication rotates the session ID; old IDs cannot confer authenticated access. No request identity/role/project claim can replace the trusted session actor.
- [ ] **TD-AUTH-08:** Anonymous/expired/revoked sessions cannot access the protected read; logout invalidates the session and old-session replay fails. Cookie attributes, session lifetime, response security headers and CSRF acquisition/rotation follow the canonical contract. Missing/invalid/stale CSRF on every applicable state-changing request, including login/logout as specified, is rejected without the forbidden state change.
- [ ] **TD-AUTH-09:** Direct HTTP requests from all three roles exercise allowed and denied project/role cases, including a valid session for project A targeting project B and forged actor/role fields. Unavailable membership/session persistence fails closed with a safe response. Tests assert no unauthorized protected fields, business writes or provider invocations, not only status codes.
- [ ] **TD-AUTH-10:** After the canonical membership/session invalidation event, subsequent requests using a previously valid session obey the defined denial/re-authentication behavior. Deterministic request/revocation race tests enforce the specified concurrency boundary. Oversized/malformed login and project inputs produce bounded safe errors; captured logs/responses contain no synthetic password or session-token canaries. Denied-request tests assert the required bounded security event is recorded without credential, session-token or private-field canaries, and verify the canonical unavailable-event-sink behavior.

### Dependencies, threats and completion gate

Depends on AUTH-PLAN and the AUTH-DATA interfaces/persistence policy; begin dependent integration after that handoff. Covers browser/API identity spoofing, CSRF/session fixation/replay, stale permissions and credential/log disclosure. Use test-only credentials and an isolated database; never read `.env*` or copy live sessions into evidence. Keep authorization in application/domain services, with Spring adapters supplying trusted identity. Raw SoCLaaS transport remains internal and unreachable from these routes.

Complete with criterion-linked API/security/database test results, exact revision or working-tree identity, actual specialized handoffs/resource measurements and independent review with resolved findings. No production or end-to-end bug-feature acceptance is implied.

## Issue 4: AUTH-VERIFY — Independently verify authentication boundaries and record measured evidence

Contract sources: [shared role contracts](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/decisions/002-shared-role-contracts.md), [architecture](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/docs/architecture.md), [security](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/docs/security.md), [Reporter completion gates](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/reporter/agentic-se.md). Canonical feature specification: `workflow/specs/auth-membership.md` (planned; not yet created by this issue-planning task). Criteria are proposed and unchecked.

### Problem and outcome

Verify the actual bounded authentication implementation against the canonical contract and preserve the Reporter evidence chain from criterion to handoff, change, test, review, correction and measured result.

### Scope and non-goals

Independently inspect the integrated diff and test assertions from AUTH-DATA/AUTH-API. Execute focused security and isolated-database suites against one recorded candidate. Record omissions honestly. No deployment, production data access, live provider evaluation or expansion into bug lifecycle/UI features.

### Proposed acceptance criteria and verification

- [ ] **TD-AUTH-11:** A reviewer who did not author the implementation inspects the actual diff, canonical spec and test results. The evidence matrix links TD-AUTH-01–10 to executable checks, exact commands, candidate identity and outcomes. It covers all roles/two projects, forged identity, session rotation/logout/expiry, CSRF, stale membership/role changes, synchronized races, safe errors/logs, missing persistence configuration and no unauthorized reads/writes/provider calls. Findings carry severity/location/impact, return through a recorded handoff and are retested after correction. Required failures/skips/unavailable checks cannot be reported as passing.
- [ ] **TD-AUTH-12:** Record real role intervals, selected context references and UTF-8 byte counts, observable tool calls/retries, test counts and review corrections under `workflow/reporter/`. Keep unknown tokens/costs unknown and distinguish selected-file bytes from inherited/unobservable total context. Link sanitized raw observations, a pinned candidate/working-tree manifest and reflection. State sample size and limitations; do not claim comparative savings from a single run.

### Measurement and security evidence plan

Before implementation intervals begin, record case IDs, task/time/call budgets, baseline/alternative and quality rubric or explicitly reference the existing deferred comparison plan at [workflow/reporter/experiments/agentic-se/plan.md](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/reporter/experiments/agentic-se/plan.md). Coordinator owns the development focused/broader-context comparison due before final MP4 acceptance. The three-role application comparison remains due at that milestone after secured AI capabilities exist; it is not an authentication-slice result. Record actual normal-run measurements now even if comparative experiments remain pending. Use the measurement kit schema and real observations only.

Map applicable threat rows explicitly. Prompt injection, AI budgets/context delivery and stale bug edits are outside this slice because it has no AI/bug path; verify that no such path was introduced. The malicious development-issue/handoff threat remains applicable: review scope and secret access boundaries, and retain the required full injection exercise as a named gate before the first AI slice unless performed earlier. Deployment verification remains pending, with no inferred release approval.

### Dependencies and completion gate

Depends on AUTH-PLAN, AUTH-DATA and AUTH-API; plan review/tests early, but final verification uses their integrated candidate. Reviewer owns independent findings; coordinator integrates evidence and records actual handoffs and resolved corrections. Complete only when applicable tests pass, material findings are resolved, evidence/measurements validate and remaining milestone gaps are explicit. Human student ownership stays unknown until supplied.
