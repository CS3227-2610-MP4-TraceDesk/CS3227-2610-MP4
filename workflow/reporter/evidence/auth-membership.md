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
| TD-AUTH-06 | Partial | Persistence operation atomically updates membership/user state and deletes sessions; rollback, success, disable, removal and synchronized read/revocation are tested. Session-store outage remains untested. |
| TD-AUTH-07 | Partial | Password/identifier policy, session rotation and failure paths covered by unit/controller tests; no complete real HTTP cookie-jar/security matrix. |
| TD-AUTH-08 | Partial | CSRF, logout replay and cookie behavior have focused tests; full HTTPS cookie attributes and timeout/expiry boundaries remain unverified. |
| TD-AUTH-09 | Partial | No AI/provider or business mutation path was added; complete request-field forgery and role matrix remain unverified. |
| TD-AUTH-10 | Partial | Event sink failure and fail-closed paths, revocation and the committed-change/read boundary are tested; full token/log canary coverage and session-store outage remain incomplete. |
| TD-AUTH-11 | Verified for this bounded review | Separate GPT-6 Luna reviewer found no source-level security defect, confirmed the added revocation/race tests close its verification-gap finding, and reported no issue in those edits. Reviewer did not rerun tests; coordinator reran the focused suite successfully. |
| TD-AUTH-12 | Partial | Measured source bytes, observable calls and test outcomes recorded; token/cost and hidden context telemetry unavailable; development comparison remains a deferred MP4 milestone. |

No criterion is marked fully accepted by this evidence. No browser, deployed URL,
production environment, release gate, or human approval is claimed.

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
