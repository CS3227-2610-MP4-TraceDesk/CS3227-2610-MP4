# Local three-role sample-account setup

Date: 2026-11-12 Asia/Singapore. Author/integrator: Codex.
Base: `7683685` plus the source identities in the
[candidate manifest](local-sample-accounts.json). Criteria: TD-LOCAL-07–09,
with regression coverage of TD-LOCAL-01–04/06. No new browser acceptance claim.
User request: add sample Developer and QA accounts to the local database setup.

## Behavior and boundaries

Added opt-in `:backend:auth-app:localSampleAccountsSetup --args=provision`.
It reuses the Reporter then provisions Developer and QA, all in the existing
owned Local Reporter Workspace. Role/login/marker/file profiles are fixed in
local-only code. The original command, marker and credential format remain
compatible. Shared loopback identity/profile guards, advisory/process locks,
password hashing, permission/ownership checks and rollback/restore logic are
reused. Each identity is an atomic operation; the three-account command is
sequential and can retain earlier successful accounts if a later account fails.
Explicit `reset reporter|developer|qa` affects only the selected owned identity.
No assignment, report, application endpoint, provider or production seed added.

## Checks actually run

Environment: macOS, JDK 25.0.4.1, Gradle 9.6.1, PostgreSQL 18 Alpine.
Created an owned disposable container `tracedesk-local-samples-20261112` with
loopback port 55419, database/user `tracedesk_dev`, and temporary filesystem
storage. After verification, `docker rm --force tracedesk-local-samples-20261112`
removed that owned disposable container successfully. The user's normal database
on port 54329 was not used or changed.
Synthetic database password below is exclusive to this disposable fixture.

```sh
JAVA_HOME=/opt/homebrew/Cellar/openjdk@25/25.0.4.1/libexec/openjdk.jdk/Contents/Home TRACEDESK_LOCAL_CONFIRM=true TRACEDESK_LOCAL_SMOKE_CONFIRM_DISPOSABLE=true TRACEDESK_LOCAL_JDBC_URL=jdbc:postgresql://127.0.0.1:55419/tracedesk_dev TRACEDESK_LOCAL_DATABASE_USER=tracedesk_dev TRACEDESK_LOCAL_DATABASE_PASSWORD=synthetic_local_samples_test_password ./gradlew :backend:auth-app:localDevTest :backend:auth-app:bootJar --no-daemon --console=plain
```

Initial complete run passed in 16s. After review-driven login assertions:

```sh
JAVA_HOME=/opt/homebrew/Cellar/openjdk@25/25.0.4.1/libexec/openjdk.jdk/Contents/Home TRACEDESK_LOCAL_CONFIRM=true TRACEDESK_LOCAL_SMOKE_CONFIRM_DISPOSABLE=true TRACEDESK_LOCAL_JDBC_URL=jdbc:postgresql://127.0.0.1:55419/tracedesk_dev TRACEDESK_LOCAL_DATABASE_USER=tracedesk_dev TRACEDESK_LOCAL_DATABASE_PASSWORD=synthetic_local_samples_test_password ./gradlew :backend:auth-app:localDevTest --no-daemon --console=plain
```

Before the fixed-credential correction below: **11 tests passed, zero failures/errors/skips**, 14s. XML counts:
3 existing command tests, 4 existing safety tests, 1 existing Reporter database
scenario and 3 new sample-account tests. New coverage includes all role profiles'
target/profile denial, invalid CLI selection and missing confirmation, three
same-project roles, credential file permissions, password/hash correspondence,
repeated provisioning retaining all passwords, per-account reset/session deletion,
changed QA role rejection, marker-failure rollback/file cleanup and recovery.

The added login helper exercises the production AuthController with a real JDBC
AuthStore through standalone MockMvc: CSRF-authenticated login, session ID
rotation, fresh session role and workspace membership/project identity. Each of
the three credentials is exercised before and after Developer-only reset. Servlet
sessions are test-provided; this is not live HTTP/cookie/JDBC-session persistence
or browser acceptance. No request bodies or generated passwords are printed.

A ZIP inspection of the built production JAR confirmed no
`BOOT-INF/classes/com/tracedesk/local/` entries, covering both CLI classes and
nested helper types. The existing CI `localDevTest` invocation includes these new
tests automatically. No CI workflow modification or remote CI pass is claimed.

Focused old command/safety tests also passed before the complete run. Their first
sandboxed Gradle launch was denied cache-lock access; the authorized rerun passed.
No application test failure was hidden by that environment retry.

## Independent review and limits

Per the Reporter workflow and TraceDesk review skill, separate read-only reviewer
`/root/sample_accounts_review` (`gpt-6-luna`) reviewed the actual specification,
code, tests and Gradle wiring. It found that hash matching alone did not prove
new account login. The coordinator added the real-controller/JDBC checks above;
the reviewer independently rechecked that delta and confirmed resolution with no
remaining supported finding. The reviewer did not execute tests or inspect
credentials/databases; this is agent review, not human approval. Exact host
call/token/cost totals and start timestamp were not captured and remain unknown.
The coordinator owned all edits. Two focused reviewer passes occurred, with no
parallel implementers. No new UI or application behavior was changed.

Only disposable fixtures were populated during implementation. To populate an
operator's normal local database, that operator runs the documented opt-in task
with their local database password in their own terminal. Historical Reporter
browser evidence remains historical; no new full browser run or deployment was
performed for this extension. Unrelated presentation and local Flyway notes were
not modified or incorporated as proof of this change.

## Fixed Developer/QA credentials — user correction

The user subsequently requested the exact credentials shown in the existing demo
sign-in UI: `developer@tracedesk.test` / `qa@tracedesk.test`, each with the explicit
synthetic password `tracedesk-demo`. Only these staff sample accounts change;
Reporter-only credentials remain compatible. No random staff password is generated.
The owning specification and local guide now describe these exact values and
staff reset restoring the fixed password instead of rotating to a random value.
Existing frontend fixture values were inspected and already match; no UI change.

For an intact marker-owned legacy staff account, provision verifies its old file,
login/hash, role and project ownership before updating to the requested credentials.
Its user/project/membership IDs remain; its sessions are invalidated. File staging
and restoration reuse the existing reset path. A collision with an unowned target
email rolls back and restores the previous file/hash/sessions rather than adopting
the other account. Repeating the matching fixed setup preserves credentials/sessions.

The same full Gradle localDevTest + bootJar command above was rerun using a fresh
owned disposable container `tracedesk-fixed-demo-20261112` on 55419. Result: **11
passed, zero failures/errors/skips**, 21s. Extended assertions cover exact staff
emails/password, real-controller login, fixed-password reset, legacy QA migration,
target-email collision rollback/file/hash/session preservation, preserved user ID,
and successful migrated login. Reconfirmed all local classes excluded from the
production JAR. Manifest source hashes and XML results describe this final candidate.

The same independent reviewer rechecked this delta read-only and reported no
supported findings, including credential ownership binding and collision rollback.
It did not run tests or access credentials/databases. This adds a third focused
review pass to the two earlier passes; no parallel implementation occurred. Normal
local database 54329 was not populated or changed. The operator runs provision in
their terminal with their local database credentials to create/update these accounts.
