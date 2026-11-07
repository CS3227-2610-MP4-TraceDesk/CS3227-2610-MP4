# AUTH-VERIFY — issue #4

Status: verified for this bounded candidate, including final independent evidence review.
Started 2026-11-07 at 06:29:44 UTC.
Branch: `codex/auth-verify-issue-4`; integrated starting revision: `aabf800`.
Human student ownership has not been supplied.

## Plan and ownership

Use `workflow/specs/auth-membership.md` as the behavior contract. Coordinator
owns integration, production corrections, execution and evidence; GPT-6 Luna
implementer owns added HTTPS assertions; a separate GPT-6 Luna auditor owns
independent findings and the criterion-to-test matrix. No new behavior contract
is required. Each delegated task is bounded to 12 tool calls and 25 minutes;
stop and reassess after three materially similar failures or a material scope
decision. No deployment, production data, environment-file inspection or live
provider evaluation is authorized by this task.

Cases: AUTH-V01 integrated policy/API/database suite; AUTH-V02 missing isolated
database settings must fail; AUTH-V03 malformed/wrong-type request shapes;
AUTH-V04 captured response/log/event canaries; AUTH-V05 real session replay after
committed membership/user revocation; AUTH-V06 independent source/evidence review.
Fixed conditions: JDK 25, locked Gradle/dependencies, dedicated ephemeral Compose
PostgreSQL service, random owned schemas, synthetic fixtures. Quality rubric:
canonical status/data/event/state behavior, absence of forbidden disclosure or
mutation, reproducible identity/results, honest review and telemetry limitations.

This is one verification case study, not a controlled context comparison.
Baseline/alternative experiments use the existing
`workflow/reporter/experiments/agentic-se/plan.md`: coordinator owns the development
comparison due before final MP4 acceptance; the three-role application comparison
is due at that milestone after secured AI features exist. Tokens/cost remain
unknown unless supplied by actual telemetry.

## Results

The final integrated candidate is `aabf8005f7c9-plus-1cbed719f37b55b9`:
base `aabf8005f7c917d0120274b9a1f97901c94a9d8a` plus the source/test changes
listed with SHA-256 hashes in [candidate.json](auth-verification/candidate.json).
The manifest pins 35 implementation, test, build, CI and contract files using
sorted relative path + NUL + bytes + NUL. Evidence files are excluded to avoid
self-reference. All pinned files were checked unchanged after the final suite.

- PASS: forced integrated suite, **24 tests** (20 auth, 4 database support),
  **0 failures, 0 errors, 0 skips**. Gradle reported 18 seconds. Sanitized suite
  and individual-test outcomes/timings: [test-results.json](auth-verification/test-results.json).
- PASS: missing required isolation configuration makes the required database
  suite fail, rather than skip or connect to a fallback. This intentional
  negative run had exit 1 and one failed test; it is **not** counted as a passing
  integration test. [negative-configuration.json](auth-verification/negative-configuration.json).
- PASS: `git diff --check`.
- Independent source review: GPT-6 Luna `/root/auth_verify_auditor`, no remaining
  supported source finding. It inspected the integrated implementation/history,
  actual changes, contract and assertions; coordinator executed the tests.
- Not run: CI on this branch, browser-to-live-backend journey, deployment,
  production verification, controlled context comparison or live provider eval.

Reproduce using JDK 25 and the documented disposable Compose test service:

```sh
docker compose --profile test up -d --wait test-db
JAVA_HOME=/opt/homebrew/opt/openjdk@25 \
PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH \
TRACEDESK_TEST_DATABASE_CONFIRM_ISOLATED=true \
TRACEDESK_TEST_JDBC_URL=jdbc:postgresql://127.0.0.1:54330/tracedesk_test \
TRACEDESK_TEST_DATABASE_USER=tracedesk_test \
TRACEDESK_TEST_DATABASE_PASSWORD=synthetic_test_password \
./gradlew :backend:auth-app:test :backend:db-test-support:test --rerun-tasks --no-daemon
docker compose --profile test stop test-db
```

The password above is the repository's public synthetic test fixture, never a
development or production credential. Runtime: JDK 25.0.4.1, Gradle 9.6.1,
PostgreSQL 18.6; loopback-only disposable service and random owned schemas.
The helper verifies isolation before schema mutation and tears down its schema.
No `.env*`, live application database, real account/session or provider was used.

## Criterion → executable evidence matrix

Test abbreviations link to actual code:
[HTTP](../../../backend/auth-app/src/test/java/com/tracedesk/auth/api/AuthHttpsIntegrationTest.java),
[race](../../../backend/auth-app/src/test/java/com/tracedesk/auth/api/AuthRevocationRaceIntegrationTest.java),
[controller](../../../backend/auth-app/src/test/java/com/tracedesk/auth/api/AuthControllerTest.java),
[DB](../../../backend/auth-app/src/test/java/com/tracedesk/auth/persistence/AuthPersistenceIntegrationTest.java),
[login policy](../../../backend/auth-app/src/test/java/com/tracedesk/auth/application/LoginPolicyTest.java),
[authorization](../../../backend/auth-app/src/test/java/com/tracedesk/auth/application/ProjectAuthorizationTest.java),
[failure filter](../../../backend/auth-app/src/test/java/com/tracedesk/auth/security/PersistenceFailureFilterTest.java),
[isolation guard](../../../backend/db-test-support/src/test/java/com/tracedesk/testing/IsolatedPostgresGuardTest.java).
Each method and its final outcome is pinned in `test-results.json`; multiple
request cases within the HTTP methods do not inflate the JUnit test count.

| Criterion | Result for this bounded candidate | Concrete evidence |
| --- | --- | --- |
| TD-AUTH-01 | Verified contract review | Canonical auth spec, shared role contracts, initial and integrated independent audits; no new material contract decision. |
| TD-AUTH-02 | Verified matrix below | Explicit allowed/denied/failed/stale/concurrent outcomes mapped to HTTP, controller, policy and DB assertions. |
| TD-AUTH-03 | Verified traceability/review | This matrix, candidate hashes, sanitized test results and the actual finding/correction/review chain below. Human ownership remains unknown. |
| TD-AUTH-04 | Passed | DB test: normalized duplicate login, foreign keys, role/state/uniqueness, project bounds and whitespace, sessions/events; fresh V1–V3 migrations. |
| TD-AUTH-05 | Passed | HTTP: Reporter/Developer/QA memberships, A allowed/B denied, forged claims, exactly two project fields; business-table fingerprint unchanged across allowed/denied reads. Authorization tests cover disabled facts. |
| TD-AUTH-06 | Passed | DB: atomic session deletion and rollback on revocation failure, fresh membership queries, synchronized repository race. HTTP race: real request authorized before commit may finish; subsequent old-cookie requests fail and cannot recreate the session. |
| TD-AUTH-07 | Passed | HTTP/controller/login policy: valid credentials, uniform credential failures, Argon2id configuration, ID/CSRF rotation and fixation replay, forged identity rejection, strict JSON shapes; captured credentials/session/CSRF canaries absent from logs/events. |
| TD-AUTH-08 | Passed with precision limit below | HTTP: secure non-persistent cookies, headers, token rotation/stale denial, idle renewal/forced expiry, logout deletion failure503 and successful 204/replay 401. Controller: exact eight-hour boundary and CSRF before logout state changes. |
| TD-AUTH-09 | Passed for auth-module scope | HTTP: all roles/two projects and safe membership/user/event/session outages; no protected fields on denial, business-state fingerprint, raw transport class absent from runtime classpath. Source/dependency review confirms no provider or business-write route. |
| TD-AUTH-10 | Passed for specified slice | HTTP: malformed/oversized bodies and project IDs, wrong types, duplicate keys, bounded events, no credential/session/CSRF/private-field canaries; four committed invalidation/replay cases; deterministic real HTTP revocation race; unavailable sink503. |
| TD-AUTH-11 | Passed | Separate non-author auditor inspected actual integrated source/diff, test outcomes and final evidence; initial P2 corrected and retested. No remaining finding. |
| TD-AUTH-12 | Passed for bounded verification; milestone experiments deferred | Actual role intervals and known calls/context references, validated sanitized JSONL, pinned manifest, attempts and reflection. Unknown tokens/cost/counts remain unknown. Independent evidence review confirmed the bounded gate; comparison owner/due milestone remains explicit. |

## Explicit outcome/case matrix

| Case | Expected observation | Executable layer |
| --- | --- | --- |
| Each active role in project A | 200 with only project id/name | HTTP, DB |
| Project B non-member / nonexistent project | Identical 404; no project details | HTTP across all three roles |
| Anonymous / revoked cookie | 401; no protected project fields | HTTP replay cases and race |
| Idle-expired / absolute-expired session | 401; invalid session removed | HTTP storage expiry; controller exact eight-hour boundary |
| Disabled membership / disabled user / removed membership | Fresh query denies; canonical operations delete sessions; subsequent old-cookie requests401 | HTTP, DB, authorization tests |
| Role change | Session deletion and reauthentication required despite the read allowing every role | HTTP AuthStore change, DB rollback/success |
| Wrong role | No forbidden-role distinction at this read: all three active roles may read; unsupported role values fail DB constraints | HTTP three-role matrix, DB |
| Forged actor/role/project claims | Login claim fields400; query/header claims cannot grant cross-project access | HTTP and controller trusted-actor assertions |
| Bad/unknown/disabled/format-invalid/out-of-bounds login | Same401 generic response and cleared cookie; no authenticated session | HTTP, login policy |
| Wrong types/null/missing/unknown/duplicate fields, malformed or >8KiB body | Generic 400; one bounded request-denial event; no credential-denial event or session upgrade | HTTP plus body-limit/controller cases |
| Noncanonical or oversized project ID | Generic 400; no raw path/canary or project data | HTTP |
| Missing/invalid/stale CSRF | 403 before credential checking or logout state mutation; token rotates after auth/logout | HTTP and controller |
| Required membership/user/session/security-event store unavailable | Generic 503; no protected fields or session elevation | HTTP and failure-filter/controller tests |
| Session deletion fails during logout | 503, never204; persisted session row demonstrates unconfirmed invalidation | HTTP test-only DELETE trigger |
| Revocation commits after query but before delivery | Already-authorized HTTP read 200 may finish; later session/project replay 401, no session resurrection | Latch-controlled HTTPS race and DB service race |
| Required test-isolation settings absent | Required suite fails before creating a schema; no fallback | Guard tests and intentional negative run |

## Independent findings, correction and review

1. Auditor examined committed `aabf800` and reported **P2**: String login fields
   allowed JSON scalar coercion, violating wrong-type request handling. A new
   real HTTPS probe reproduced numeric login returning 401 instead of 400.
2. Coordinator changed only the login request decoder to accept JSON values as
   Object and permit actual Strings. Numeric, boolean, array, object and null
   fields now return 400; valid strings and canonical invalid-identifier 401 remain.
3. Test implementer added captured-output and committed-invalidation/replay cases;
   coordinator added business-state/classpath assertions and logout-delete
   failure. Two focused attempts failed because the new fixture assumed cookie
   retention after a 503. The fixture now starts a separate fresh session for its
   successful logout check; it retains the 503 and persisted-row assertions.
4. A separate new HTTPS race test pauses immediately after the real membership
   query, commits revocation through AuthStore, then releases the response.
   No production test hook or management API was introduced.
5. Integrated source review by `/root/auth_verify_auditor` at
   06:39:54–06:40:58 UTC found **no remaining supported source defect**, resolving
   the initial P2. Auditor did not execute tests. Coordinator passed the full
   suite, then added absent/oversized project request assertions, repinned all
   files and passed the full suite again. See [attempts.json](auth-verification/attempts.json).
6. Final source-delta and evidence review by the same independent non-author
   auditor, **06:46:44–06:47:39 UTC**, inspected candidate
   `aabf8005f7c9-plus-1cbed719f37b55b9`, the final case matrix, finding resolution,
   manifest, test outcomes, attempts and raw measurements: **no remaining
   supported finding**. It confirmed the earlier TD-AUTH-02/03 partial ratings
   were missing report artifacts, now supplied, and TD-AUTH-11/12 pass for this
   bounded task. The reviewer did not edit files or execute tests. This review
   selected ten files totaling 130,371 full-file bytes, made five orchestration
   calls (including clock reads), zero retries; tokens remain unknown. Its
   measured context is the state read before this final evidence append.

## Threat boundaries and limits

Applicable rows: forged actor/project claims, CSRF/stolen sessions, credential
disclosure/guessing, unavailable denial-event storage, unsafe DB fixtures and
malicious development issues/handoffs. The tests and independent source review
above exercise those boundaries. No unauthorized instruction or secret access
was accepted. The full synthetic development-agent injection exercise is still
owned by the coordinator and due before acceptance of the first AI slice.

No AI/bug endpoint, business mutation or business audit transaction exists here.
Provider-path absence is structural within this module and its dependency graph;
the executable classpath assertion names the raw transport class. Business-state
assertions cover users/projects/memberships, while session/security-event writes
are intentional. Neither assertion claims to prove arbitrary future code safe.

Idle expiry is checked using the configured 1,800-second JDBC lifetime, renewal and
forced expired timestamps; the exact millisecond at the 30-minute boundary is
not sampled. The eight-hour boundary is deterministic. Log capture covers this
test's normal WARN logging profile, not an operator-enabled TRACE profile.
Self-signed TLS is test-only. Browser/production/deployment acceptance belongs to
its separate gates; no release or human approval is implied.

## Measurements and reflection

Sanitized role observations: [observations.jsonl](auth-verification/observations.jsonl).
Validated offline report: [measurements.html](auth-verification/measurements.html).
Full source-file identity and test metadata are linked above. The raw rows use
the existing measurement-kit schema and contain no credentials, captured logs,
session values or private content.

- Initial auditor: 06:30:27 UTC start, exact end unavailable; 11 orchestrated
  calls reported. Its reported 170,138-byte total mixed a worktree file edited by
  another owner, so exact pinned context bytes remain unknown and are not used
  as a precise context metric.
- HTTPS-test implementer: 06:30:46–06:33:12 UTC. Final self-report 14 calls and
  one failed patch, exceeding its 12-call budget. Earlier count/byte summaries
  disagreed; raw context/call totals are marked unknown rather than silently
  choosing an inconsistent number. This is a process limitation.
- Race-test implementer: 06:36:02–06:37:48 UTC; four orchestrated calls/six nested
  calls, zero retries; five pinned input refs total 41,962 bytes.
- Integrated auditor: 06:39:54–06:40:58 UTC; four orchestrated calls, zero retries;
  five current candidate references total 98,859 full-file bytes at that review.
  The later project-test delta is recorded by final evidence review.
- Final evidence auditor: 06:46:44–06:47:39 UTC; five calls, zero retries;
  130,371 full-file reference bytes. Exact paths and interval are in the final
  review observation row; this is selected file size, not total model context.
- Coordinator total call count/tokens/cost are unknown. The test command's
  observed window includes approvals and output collection; Gradle process time
  is separately reported. Parallel role intervals are not summed as wall time.

Sample size is one authentication verification task, with one initial integrated
pass and one final repinned pass. The initial probe found a real parser defect;
the two fixture failures exposed a mistaken assumption about failed logout
cookies. Keeping those failures avoids overstating first-pass quality. The
independent reviewer and explicit query latch added coordination/setup cost but
produced stronger concurrency and disclosure evidence. No comparative speed,
token savings or model-quality claim is supported. Controlled development and
three-role application comparisons remain due before final MP4 acceptance.
