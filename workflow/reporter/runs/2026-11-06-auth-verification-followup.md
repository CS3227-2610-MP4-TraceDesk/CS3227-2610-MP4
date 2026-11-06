# Authentication verification follow-up

Status: local security verification complete for the covered paths; one
session-creation outage limitation remains. No commit, push, CI run or deployment
is claimed.

## Handoff and scope

- Worked on separate branch `codex/auth-verification`, based on
  `ac5bab3d72ce6a144525495537febc32dcfe208a`; `main` was not modified.
- `/root/auth_verification_audit` (GPT-6 Luna) performed an independent,
  read-only security review of the resulting diff: 9 files, 88,032 UTF-8 bytes,
  three calls, zero retries; elapsed time and tokens unavailable. It found no
  security defect or scope expansion and confirmed the outage-header assertions.
  It also noted the exact 30-minute idle boundary is not tested. It did not edit
  or execute tests.
- `/root/https_integration_tests` (GPT-6 Luna) implemented the HTTPS integration
  test and returned ownership. It reported 19 files / 91,139 UTF-8 bytes checked,
  approximately 21 tool calls including retries and about six minutes elapsed;
  token use unavailable. The coordinator then made the explicit outage-case
  correction below and reran tests.
- No credentials, `.env*`, production settings or user data were included in
  agent prompts or read as task context. Test data and the TLS keystore are
  synthetic and temporary.

## Verification result

The full test command, with only the synthetic password omitted here, was:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25 \
PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH \
TRACEDESK_TEST_DATABASE_CONFIRM_ISOLATED=true \
TRACEDESK_TEST_JDBC_URL=jdbc:postgresql://127.0.0.1:54330/tracedesk_test \
TRACEDESK_TEST_DATABASE_USER=tracedesk_test \
TRACEDESK_TEST_DATABASE_PASSWORD=[synthetic value omitted] \
./gradlew :backend:auth-app:test :backend:db-test-support:test --rerun-tasks --no-daemon
```

Result: `BUILD SUCCESSFUL`, 8 tasks executed; JUnit XML reports 20 tests, 0
failures, 0 errors and 0 skipped (auth app 16, DB support 4). The dedicated
HTTPS integration test also passed independently. `git diff --check` passed.
No CI, browser, live deployment or production database verification is claimed.

The HTTP test starts TraceDesk on HTTPS with an ephemeral test key and creates
an owned schema in the isolated PostgreSQL service. It checks TLS/cookies,
security headers, CSRF/session rotation and replay, role/project authorization,
forged request claims, account/membership disablement, logout, error equivalence,
privacy canaries and generic persistence-outage responses for membership, user,
audit-event and existing-session paths. The separate deterministic clock test
proves eight-hour expiration at the exact boundary without an eight-hour wait.
The session test verifies the 1,800-second configured idle window and forces
stored session timestamps beyond expiry to prove rejection; exact millisecond
idle-boundary behavior remains untested.

An attempted new-session creation outage (`GET /api/csrf` with the Spring Session
table unavailable) returned HTTP 500 under Spring Session's internal handling.
That assertion was removed from the passing suite and remains a known gap; no
safe 503 behavior is claimed for new-session creation when session persistence
fails. Existing-session lookup and the other tested persistence paths return
generic 503 responses with security headers.

## Actual measurements

- HTTPS integration-test source: 29,487 UTF-8 bytes.
- Persistence failure filter source: 2,387 UTF-8 bytes.
- Delegated HTTPS test agent: 19 files / 91,139 UTF-8 bytes checked, ~21 calls
  including retries, ~6 minutes; tokens unavailable.
- Independent reviewer measurements are above. Byte counts are source-size
  measurements, not model context or token counts.

See [auth-membership evidence](../evidence/auth-membership.md) for the full
criterion-level status, prior measurements and outstanding partial acceptance.
