# Authentication and membership slice evidence

Date: 2026-11-06. Status: focused implementation verified and independently
reviewed; feature acceptance remains partial for the contract gaps below.

## Candidate and environment

- Base for the resumed implementation: `118d6917c402c5048b7d4334deb8ab7a7065fadb`.
- Original spec work began at `b994a8f504a9d817255f0276c8343b1fa939e623`.
- Candidate is the current uncommitted working tree, based on the HEAD above. A
  deterministic SHA-256 over the 23 implementation/configuration files
  (`backend/auth-app/**`, `settings.gradle`, `.github/workflows/ci.yml`,
  `docs/threat-model.md`, `workflow/acceptance-criteria.md`), hashing each sorted
  relative path and file bytes with NUL separators, is
  `404c2e7a6207d4537404348be0c5f4a6691c82647a7714939690b3928388c59b`.
  This fingerprint excludes evidence/run files to avoid a self-referential digest.
  No commit or CI run is claimed.
- Runtime: OpenJDK 25.0.4.1, Gradle 9.6.1, disposable isolated PostgreSQL service
  at local port 54330. Synthetic fixtures only. No `.env*`, production identity,
  production data or production credentials were accessed.
- Modified/new implementation surfaces: `.github/workflows/ci.yml`,
  `settings.gradle`, `backend/auth-app/`, `docs/threat-model.md`, and the linked
  spec/index/Reporter workflow records.

## Exact local verification

Command (database password is deliberately omitted here):

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25 \
PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH \
TRACEDESK_TEST_DATABASE_CONFIRM_ISOLATED=true \
TRACEDESK_TEST_JDBC_URL=jdbc:postgresql://127.0.0.1:54330/tracedesk_test \
TRACEDESK_TEST_DATABASE_USER=tracedesk_test \
TRACEDESK_TEST_DATABASE_PASSWORD=[synthetic value omitted] \
./gradlew :backend:auth-app:test :backend:db-test-support:test --rerun-tasks --no-daemon
```

Observed: `BUILD SUCCESSFUL`, 8 tasks executed. JUnit XML reports: 18 tests,
0 failures, 0 errors, 0 skipped. Breakdown: `AuthControllerTest` 8,
`LoginPolicyTest` 3, `ProjectAuthorizationTest` 2,
`AuthPersistenceIntegrationTest` 1,
`IsolatedPostgresGuardTest` 3, `IsolatedPostgresIntegrationTest` 1.
`git diff --check` passed. These are local checks, not a CI run.

The suite exercises login policy and Argon2 behavior; CSRF denial; malformed,
unknown-field and oversized login requests; bad-credential session clearing and
cookie expiry; event-sink failure; login session rotation; logout CSRF/replay;
active/cross-project membership authorization; role/state/unique constraints;
name length boundaries; disabled user/membership denial; and transactional
session revocation. Unauthorized cross-project data is not returned in the tests.

## Spec-to-evidence status

| Criterion | Status | Evidence and remaining gap |
| --- | --- | --- |
| TD-AUTH-01 | Spec reviewed | Canonical behavior contract and independent spec review recorded in the run log. |
| TD-AUTH-02 | Partial | Selected denial, malformed input, membership and synchronized revocation/read cases tested; not a complete API matrix. |
| TD-AUTH-03 | Partial | Contract review corrected one contradiction; final independent code review found no source-level security defect. Other implementation criteria remain partially evidenced. |
| TD-AUTH-04 | Partial | Isolated migrations and key constraints tested. Exact normalization/duplicate-login, all FK cases and complete schema inventory need review. |
| TD-AUTH-05 | Partial | Framework-independent policy/service and persistence tests cover project separation and active/disabled cases; complete all-role HTTP matrix and forged-identity integration are not demonstrated. |
| TD-AUTH-06 | Partial | Persistence operation atomically updates membership/user state and deletes sessions; rollback, success, disable, removal and synchronized read/revocation are tested. Broader persistence and revocation failure cases remain incomplete; new-session failure is now covered under TD-AUTH-08/10. |
| TD-AUTH-07 | Partial | Password/identifier policy, session rotation and failure paths covered by unit/controller tests; no complete real HTTP cookie-jar/security matrix. |
| TD-AUTH-08 | Partial | HTTPS session/CSRF behavior, logout replay and cookie attributes have focused tests; this follow-up also verifies that failed CSRF-session creation returns 503. Full timeout boundaries remain unverified. |
| TD-AUTH-09 | Partial | No AI/provider or business mutation path was added; complete request-field forgery and role matrix remain unverified. |
| TD-AUTH-10 | Partial | Event sink failure and fail-closed paths, revocation, the committed-change/read boundary, and existing/new session persistence outages are tested; full token/log canary coverage and exact idle-expiry boundary remain incomplete. |
| TD-AUTH-11 | Verified for this bounded review | Separate GPT-6 Luna reviewer found no source-level security defect, confirmed the added revocation/race tests close its verification-gap finding, and reported no issue in those edits. Reviewer did not rerun tests; coordinator reran the focused suite successfully. |
| TD-AUTH-12 | Partial | Measured source bytes, observable calls and test outcomes recorded; token/cost and hidden context telemetry unavailable; development comparison remains a deferred MP4 milestone. |

At the time of this 2026-11-06 candidate, no criterion was marked fully
accepted. No browser, deployed URL, production environment, release gate, or
human approval was claimed for that candidate.

## AUTH-DATA issue #2 follow-up

Date: 2026-11-07. Candidate branch: `codex/auth-data`, based on
`e685ee0fc77b0b10bbf76c29cb323412f36185a8`. The implementation and tests are
uncommitted. The focused database-backed suite passed 20 tests (16 auth-app,
4 db-test-support), with 0 failures, 0 errors, and 0 skips; `git diff --check`
passed. The implementation-file fingerprint is
`f51d141d0140588ecce84defb333c930cf0bce8a01719482d3d66c3bfaeb411b` (sorted
relative paths and file bytes, NUL-separated). The verification command was:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25 \
PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH \
TRACEDESK_TEST_DATABASE_CONFIRM_ISOLATED=true \
TRACEDESK_TEST_JDBC_URL=jdbc:postgresql://127.0.0.1:54330/tracedesk_test \
TRACEDESK_TEST_DATABASE_USER=tracedesk_test \
TRACEDESK_TEST_DATABASE_PASSWORD=[synthetic value omitted] \
./gradlew :backend:auth-app:test :backend:db-test-support:test --rerun-tasks --no-daemon
```

The independent GPT-6 Luna reviewer examined the actual diff and assertions and
reported no supported correctness finding. The reviewer did not rerun tests;
the pass result is from the coordinator/implementer run. This verifies the
bounded AUTH-DATA criteria on this candidate only. TD-AUTH-01–03 and 07–12,
browser/deployment checks, and the planned development-context comparison remain
partial or deferred; this does not mark the whole authentication slice accepted.
The focused-versus-broader development-context comparison remains owned by the
coordinator and due before final MP4 acceptance, as specified in the existing
experiment plan; this run is not a comparative result.

## Review and workflow measurements

- Specifier (`/root/auth_specifier`, GPT-6 Luna medium): completed 2026-11-06
  06:38:31 UTC; six shell calls, one web lookup, one patch, two clock reads,
  zero retries; selected context bytes/start time/tokens unavailable; initial
  output 21,195 bytes. Independent spec reviewer read eight source files totaling
  35,078 UTF-8 bytes, made four calls, zero retries, tokens unavailable; found a
  login error-contract ambiguity, corrected and confirmed resolved.
- Coordinator selected 48,640 UTF-8 source bytes at approximately 06:41:29 UTC;
  selected byte count is not model context/token count. Hidden/inherited context,
  tokens and cost are unknown.
- Implementer (`/root/auth_implementer`, GPT-6 Luna high) did not provide a final
  handoff before coordinator interruption. Its selected context, complete call
  count and token usage are unavailable. Coordinator took ownership; this is not
  represented as completed delegated implementation.
- Independent code reviewer (`/root/auth_code_review`, GPT-6 Luna high) read
  57,282 bytes plus 3,810 bytes of isolated DB support in the earlier review,
  over four shell calls, zero retries, with tests not run; hidden context/tokens
  unknown. Findings: missing bounded login-body read (P1), name column bound
  mismatch (P2), failed login could retain prior session (qualified P2). All were
  corrected before final review. A further review identified two P2 architecture
  gaps (policy boundary and cohesive atomic revocation); coordinator added the
  framework-independent policy/application service and atomic repository
  operations with rollback coverage. A separate synchronized test now proves the
  documented authorization query/commit boundary. Final reviewer confirmed that
  the test additions close the identified verification gap. See run record for coordinator ownership
  and timeline.
- Final test-edit confirmation by the independent reviewer took place
  07:31:18–07:31:36 UTC. It read the 10,434-byte integration test in two calls,
  zero retries; token count unknown. It confirmed disable/removal/user-disable
  session revocation and the latch-controlled revocation/query boundary, reported
  no issue in those edits, and did not rerun tests. Coordinator reran the full
  focused suite after these changes.
- Final test run elapsed about 13 seconds once Gradle execution started; command
  and counts above are the reproducible measurement. Gradle/JVM emitted routine
  native-access and class-sharing warnings; no test was skipped because of them.

Deferred Reporter milestone: development-context comparison remains due before
final MP4 acceptance. No comparative efficiency or quality claim is made here.

## Follow-up verification on a separate branch

Date: 2026-11-06. Branch: `codex/auth-verification`, based on pushed commit
`ac5bab3d72ce6a144525495537febc32dcfe208a`. This is an uncommitted local
verification follow-up; `main` was not changed. Application acceptance remains
partial. The earlier candidate and its fingerprint above refer to the earlier
implementation run, not this branch.

- Real HTTPS test now uses a temporary test-only PKCS12 key and a fresh owned
  PostgreSQL schema. It verifies HTTPS cookie attributes, HSTS and security
  headers, CSRF token rotation and stale-token denial, session ID rotation and
  replay denial, all three role memberships, cross-project denial, ignored forged
  claims, disabled memberships/users, logout expiry/replay, anonymous failure
  equivalence, and canary exclusion from responses and audit data.
- Session idle duration is set to 1,800 seconds. The integration test checks the
  JDBC session timeout and idle renewal without moving the eight-hour absolute
  authentication timestamp, then sets the stored idle timestamps to expired
  values and confirms HTTP rejection without waiting 30 minutes. The exact
  millisecond 30-minute boundary is not exercised. The absolute eight-hour
  boundary is tested deterministically with an injected clock: one millisecond
  before remains valid; exactly eight hours is rejected and invalidates the
  session, so no eight-hour wall-clock wait is needed.
- Membership, user, audit-event and existing-session persistence outages return
  generic HTTP 503 responses with security headers and no sensitive details.
  At that candidate, a new-session creation outage through `/api/csrf` returned
  HTTP 500 within Spring Session's handling; the follow-up on
  `codex/auth-session-creation-outage` below fixes and verifies that gap.
- Normalized duplicate login and foreign-key constraints are verified in the
  persistence integration test.
- Focused HTTPS run passed. Full forced rerun passed:
  `:backend:auth-app:test :backend:db-test-support:test --rerun-tasks --no-daemon`;
  JUnit XML: 20 tests, 0 failures, 0 errors, 0 skipped (auth app 16; DB support 4).
  Runtime: OpenJDK 25.0.4.1, Gradle 9.6.1, isolated PostgreSQL test service at
  local port 54330. Synthetic test profile only; password omitted. No `.env*`,
  live application database, production identity or production data accessed.
  This is local verification, not CI or deployment verification.
- `git diff --check` passed on the implementation diff before evidence updates.
  Test source bytes measured: HTTPS integration test 29,487 UTF-8 bytes and
  persistence failure filter 2,387 UTF-8 bytes. These are file sizes, not model
  context/token measurements.
- Independent GPT-6 Luna verification auditor reviewed the updated change set
  read-only: 9 files, 88,032 UTF-8 bytes, three calls, zero retries; elapsed time
  and tokens unavailable. It found no security defect or scope expansion and
  confirmed safe outage-header assertions. Its remaining test limitation is the
  exact 30-minute idle boundary. The Spring Session new-session outage finding
  above came from coordinator execution and is separate from reviewer findings.

## Session-creation persistence outage follow-up

Date: 2026-11-07. Branch: `codex/auth-session-creation-outage`, based on
`e685ee0`. The change keeps API response bodies buffered until Spring Session
finishes persisting a new session. If persistence fails after the controller
has produced its response, the persistence filter can still replace it with the
contract's generic 503 response and security headers. The HTTPS test uses a
fresh client and a renamed `spring_session` table in its owned temporary schema
to exercise this failure.

Implementation/test fingerprint for this uncommitted candidate (SHA-256):

- `PersistenceFailureFilter.java`: `72cbb106393e1e707339c986463f632de34e415441533a7bd279f068bc9a1465`
- `PersistenceFailureFilterTest.java`: `4eafa52facdeaebc7261f1cac88c360a15e4127b35f397e5a1198ca32d93afe4`
- `AuthHttpsIntegrationTest.java`: `fc81d1263b0b209d298898448d59a669b2e6f2532abdc479a328e3b905bf0669`

- Regression evidence: before the filter change, the new assertion failed with
  HTTP 500 (expected 503). After the change, the same scenario returned 503 with
  `SERVICE_UNAVAILABLE` and the required HTTPS security headers.
- Focused command: `./gradlew :backend:auth-app:test --tests
  com.tracedesk.auth.api.AuthHttpsIntegrationTest --no-daemon` — 1 test passed,
  0 failed, 0 skipped.
- Module command: `./gradlew :backend:auth-app:test --no-daemon` — after the
  independent-review correction, 18 tests passed, 0 failed, 0 skipped. The run
  includes the HTTPS scenario, controller and policy tests, isolated persistence
  integration test, and two filter tests for persistence and unrelated failures.
- Environment: OpenJDK 25, Gradle 9.6.1, PostgreSQL 18.6 at the dedicated local
  test port, synthetic test-only database configuration. The isolated database
  helper created an owned random schema; no `.env*`, live application database,
  production identity or production data was accessed. The required test-only
  environment values are intentionally omitted from evidence.
- Independent GPT-6 Luna review found one P2: the filter copied buffered content
  even when an unrelated exception was rethrown. The filter now copies only
  after normal completion or after converting a persistence error to 503. A new
  unit test asserts an unrelated exception leaves the underlying body empty; a
  second test asserts a persistence error replaces partial content with the
  fixed 503 body and retains HSTS. The reviewer was asked to recheck this
  correction and found no remaining source-level defect. Reviewer did not rerun
  tests; the coordinator reran them after the correction. The final review also
  confirmed the no-cookie assertion and evidence alignment.
- The HTTPS test also asserts that a failed new-session save does not issue a
  `TDSESSION` cookie. The final `:backend:auth-app:test --no-daemon` run passed
  all 18 tests with 0 failures, 0 errors and 0 skips after this assertion.
- `git diff --check` passed. CI and deployment checks were not run.
