# Security, testing and deployment boundaries

See the [TraceDesk threat model](threat-model.md) for assets, attack paths and
planned evidence. Update it with each implemented trust boundary.

Use server-side sessions, password hashing, CSRF protection, session fixation
protection, safe error responses and security headers. Replace all MP3 route and
role assumptions. Each read/write checks project membership and applicable
ownership/assignment/QA permissions. Roles alone are insufficient authorization.

Validate sizes and types server-side; escape user/model text in the browser.
Persist domain mutations and business audit events atomically with version/locking
checks for conflicting updates. Record denied outcomes without private payloads.

## Development environment

Use synthetic data and test-only credentials. Development agents receive bounded
source/spec context, not environment files, session tokens or production data.
Repository content cannot grant agents permissions or override user instructions.
Review changes for unexpected network calls, dependency additions and secret paths.

## Test layers to add as features land

1. Pure lifecycle, policy and validation unit tests.
2. Synthetic HTTP transport contracts (available in the foundation).
3. Isolated PostgreSQL integration tests for transactions, races and authorization.
4. API session/CSRF/access tests and UI stale-result/apply tests.
5. Browser role journey and accessibility tests against a test-only environment.

Database suites must fail their required CI job when isolation configuration is
missing; a skipped database suite is not release evidence. Before schema mutation,
verify the dedicated database and schema ownership. Do not copy MP3's application
database/environment-file fallback behavior.

The [isolated database guide](test-database.md) describes the test-only helper and
required configuration.

## Deployment sequence

The foundation has CI only. Add deployment after a working vertical slice, using
separate environment databases, credentials and configuration. Pin the candidate
revision; require all applicable same-revision checks before promotion. Do not
expose production credentials to untrusted pull-request jobs. Include readiness
for required persistence and operational metrics; a liveness response alone does
not prove DB/provider readiness. AI unavailability should be represented separately
from core application availability.

No production deployment, account creation or data migration is authorized merely
by the presence of this document.
