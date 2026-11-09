# Portable deployment verification

Hosting is undecided. This guide prepares issue #42's deployment gate; no target
has been provisioned or verified. Select an operator-owned host, domain, secret
store and isolated staging database before applying these steps. Do not run local
Compose with its synthetic defaults as a production deployment.

## Candidate and environment

Record the exact commit, build identity, environment name, public HTTPS origin,
database/migration identity, secret-source names and operator authorization.
Keep secret values, cookies, report bodies and personal data out of the record.
Development, test, staging and production need separate credentials/data. Use
synthetic accounts for staging verification and an agreed provisioning process;
public registration or production seed accounts are not part of this issue.

Require CI checks from that same revision: foundation/module checks, shared AI
gateway and transport tests, authentication/role/isolated-database tests, frontend
typecheck/lint/unit/build and browser/security checks. Artifacts from another
revision or skipped database suites cannot satisfy the gate. Build the backend
with Java25 (`:backend:auth-app:bootJar`) and the frontend with its lockfile/build
commands. Record artifact hashes; never bake secrets into either artifact.

Keep production secrets outside untrusted pull-request jobs. Configure a protected
release environment with trusted revisions and operator-controlled secret access.
No deployment workflow is installed here because the hosting target and release
credentials are not supplied.

## Routing and startup

Serve frontend assets and `/api/` under one HTTPS origin. Route `/api/` to the
backend without exposing its database port, and route the health endpoints below
to the backend for operator probes. The current static frontend image serves
assets only; its `/health/frontend` response does not prove API readiness. Use
the selected host's reverse proxy/load balancer rather than Vite's development
server in production. Permit only trusted proxy headers at the edge, and verify
that the backend observes HTTPS so HSTS and Secure session cookies behave as
intended. Do not disable secure cookies to make an HTTP deployment pass.

Inject `TRACEDESK_DATABASE_URL`, `TRACEDESK_DATABASE_USER` and
`TRACEDESK_DATABASE_PASSWORD` through the selected host's secret/config system.
Start with AI disabled and a correct increasing policy revision. Back up the
selected application database under the operator's release procedure before
schema changes; review the fresh migration chain and required privileges. Verify
migrations and readiness before routing users. Do not apply test fixtures or
copy old MP3 migrations/data into the application database.

Inject SoCLaaS configuration only through the backend secret store after
[enablement authorization](ai-enablement.md). Verify the host can reach the fixed
SoCLaaS origin and selected model; the public service page's NUS-network notice
for portal/docs is not evidence that an arbitrary cloud host has API access.

## Readiness and telemetry

- `GET /health/live` reports process liveness only.
- `GET /health/ready` verifies required user/project/membership/session and AI
  ledger schema/permissions without selecting private rows. It returns503 for
  unavailable required persistence,200 when core dependencies are ready, and a
  separate bounded `ai` state (`DISABLED`, `UNAVAILABLE`, `AVAILABLE`). AI failure
  does not by itself make ordinary reporting unready.

These probes cannot prove a real provider completion, completed migration history,
role authorization or a successful user journey. Verify those separately. Set
probe timeouts appropriate to the bounded database query and monitor status,
request latency, denied requests, refresh/approval expiry and numeric accounting
events. Do not enable HTTP body/header logging, database parameter logging,
environment dumps or public detailed health diagnostics.

## Authorized synthetic-account smoke evidence

On the chosen target and exact deployed revision:

1. Verify HTTPS, same-origin API routing, security headers, Secure/HttpOnly/SameSite
   session cookies, login rotation, CSRF rejection and logout/replay denial.
2. Verify allowed project access, cross-project/nonmember denial, and private-field
   exclusion using separate synthetic identities.
3. Create/read/list an ordinary report, save/resume a draft and perform an allowed
   version-checked edit. Check stale edits fail and business audit is atomic.
4. Verify ordinary reporting while AI is disabled/unavailable. After explicit
   provider authorization, generate one bounded Reporter suggestion, inspect safe
   preview and explicitly apply selected fields through PATCH. Check unselected
   fields/logs remain exact and accounting contains no content/key/session data.
5. Verify controlled revocation/stale result denial and the emergency disable
   procedure. Do not simulate uncontrolled provider outages or unlimited traffic.
6. Verify implemented Developer/QA journeys against their own criteria. Record
   missing integrations as unavailable; Reporter smoke tests do not certify them.

Record pass/fail/skip/unavailable per check, deployment URL/environment/revision,
sanitized commands, actual outcomes, independent review and resolved findings.
State whether evidence is local, staging or production. Do not claim a live
provider check from synthetic tests, or a deployed check from a local fixture.

## Disable, rollback and release decision

Publish a higher disabled policy revision using the trusted backend configuration
to stop new AI admission across instances; verify denied delivery and retained
accounting for in-flight work. Keep core reporting available. Drain old instances
before rollback and retain policy/metadata revision high-water marks. Verify the
older application artifact is compatible with the migrated schema; prefer a
forward fix over destructive schema reversal. Database restore is a separate
operator decision with explicit data-loss implications, not an automatic action.

Release only after required same-revision checks and authorized target smoke
verification pass. Remaining live quality/role/deployment gaps stay open in the
[evidence record](../workflow/reporter/evidence/ai-enablement.md).
