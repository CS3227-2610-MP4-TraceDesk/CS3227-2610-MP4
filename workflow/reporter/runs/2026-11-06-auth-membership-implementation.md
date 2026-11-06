# Authentication and project-membership slice run

Status: implementation and focused verification complete; acceptance remains
partial pending final independent review and uncovered contract cases.
Base revision: `118d6917c402c5048b7d4334deb8ab7a7065fadb` (current repository
HEAD when coordinator resumed; initial spec run began on
`b994a8f504a9d817255f0276c8343b1fa939e623`).
Working tree includes earlier user-requested Reporter model preference, issue-planning
drafts/run record, this run record, the canonical spec, index and threat-model edits.
No application code existed at the base revision.

## Specification handoff

- Coordinator `/root` → `/root/auth_specifier`, role specifier, model requested:
  `gpt-6-luna`, medium reasoning; own only `workflow/specs/auth-membership.md`.
- Dispatched around 2026-11-06 06:36 UTC; precise start unavailable. Completed at
  06:38:31 UTC. Agent reports six shell calls, one web lookup, one patch and two
  clock reads; zero retries. Selected-context byte total/start time/tokens unknown;
  output spec was 21,195 bytes before coordinator correction. No tests or code edits.
- Contract covers TD-AUTH-01–12, specifies local sign-in, JDBC server sessions,
  CSRF, active project membership, one protected project read, denial-event
  behavior and exclusions. Specifier resolved routine choices within scope.

## Independent specification review

- `/root/auth_spec_review`, independent reviewer, `gpt-6-luna`, medium reasoning;
  no file ownership. Reviewed canonical spec, reviewer role/skill, ADR 002, security,
  architecture, database guide and acceptance index.
- Observed interval approximately 06:39–06:40 UTC (start not precise); eight source
  files, 35,078 UTF-8 bytes; four calls, zero retries, tokens unknown. No edits/tests.
- One medium finding: malformed login identifier had contradictory 400/401 behavior.
  Coordinator aligned the contract to return indistinguishable 401 for unknown,
  wrong-password, and syntactically present invalid-format/bounds identifiers;
  malformed body/shape remains 400. Reviewer reread and confirmed resolution.

## Implementer handoff

- Coordinator `/root` → `/root/auth_implementer`, role implementer, model explicitly
  requested: `gpt-6-luna`, high reasoning; dispatched about 06:41 UTC. Owns only
  `backend/auth-app/` and `settings.gradle`; excludes docs/spec/evidence and all
  out-of-scope registration, admin APIs, UI, bug lifecycle, AI, production seeds,
  deployment and `.env*` access. Implements the reviewed canonical contract.
- Source context supplied by exact paths in the handoff: root AGENTS; canonical
  spec; shared role contracts; security and isolated database docs; harness
  implementer role. Official current Spring Boot/Session/Security references were
  consulted by coordinator. Detailed selected source-byte total and actual agent
  interval pending implementer report; tokens/cost unknown. No claim of context
  minimality because full conversation inheritance was disabled for agents but
  total hidden host context is not observable.
- Base toolchain observation: JDK 25.0.4.1 and Gradle 9.6.1. Initial wrapper check
  under default Java 17 failed because Gradle cache lock write was sandbox-blocked.
  Re-run with `JAVA_HOME=/opt/homebrew/opt/openjdk@25` and approved access passed.
  No test suite has run yet. Dedicated test PostgreSQL exists in `compose.yaml`;
  application integration must use `backend/db-test-support` only.

## Coordinator work and pending evidence

- Coordinator selected 48,640 UTF-8 source bytes at 06:41:29 UTC from the canonical
  spec, project contracts/security, acceptance index, test-database guide, Reporter
  gates/evidence template and AGENTS.md. This is selected file size, not model
  context size. Tokens/cost and hidden/inherited context unknown.
- Coordinator updated acceptance index and threat-model intended controls. The
  threat model records rate limiting/account lockout as residual risk because the
  bounded contract excludes account lockout. Independent implementation review
  must assess that residual risk against the requested slice.
- Implementer delegated work stalled before a final handoff. Coordinator interrupted
  `/root/auth_implementer` after progress stopped, then took ownership of integration,
  corrections and verification; no final implementer report was available. The first
  code review was by the separate `/root/auth_code_review` GPT-6 Luna agent. It found
  an unbounded login body, project-name storage/check mismatch and retained session
  on bad credentials. Coordinator corrected these and requested a second review of
  the final candidate. Exact elapsed wall-time and hidden token usage are unavailable.
- Independent review also found that the authorization decision lacked a
  framework-independent application/domain boundary and that session revocation
  was composed by test-owned SQL/transaction rather than a cohesive persistence
  operation. Coordinator added `ProjectAccessPolicy`, `ProjectAuthorization` and
  its repository port; controller delegates to the service and persistence supplies
  fresh facts. `AuthStore` now owns transactional role/state/removal/user-enable
  changes and session deletion. The database test forces session-attribute deletion
  to fail, verifies membership-role rollback, then checks successful revocation.
- The coordinator changed project authorization to one SQL operation joining enabled
  user, enabled project and active membership, and bounded login request reading to
  8 KiB before JSON parsing. Failure-event persistence remains fail-closed. CI's auth
  job now provisions only its synthetic PostgreSQL service and required isolated-test
  environment; no production data or credentials were accessed.
- Final focused verification at 2026-11-06 07:20 UTC used JDK 25.0.4.1 and Gradle
  9.6.1, disposable PostgreSQL on local test port 54330, and:
  `JAVA_HOME=/opt/homebrew/opt/openjdk@25 PATH=/opt/homebrew/opt/openjdk@25/bin:$PATH TRACEDESK_TEST_DATABASE_CONFIRM_ISOLATED=true TRACEDESK_TEST_JDBC_URL=jdbc:postgresql://127.0.0.1:54330/tracedesk_test TRACEDESK_TEST_DATABASE_USER=tracedesk_test TRACEDESK_TEST_DATABASE_PASSWORD=[omitted] ./gradlew :backend:auth-app:test :backend:db-test-support:test --rerun-tasks --no-daemon`.
  Result: BUILD SUCCESSFUL, 8 Gradle tasks executed. XML reports: 18 tests, 0
  failures, 0 errors, 0 skipped (auth 14; DB support 4). This is a local focused
  suite, not a CI run or live deployment. `git diff --check` passed.
- Current verification limits: no live HTTPS/browser cookie test; no synchronized
  complete 30-minute/8-hour expiry test; no
  exercised persistence outage for Spring Session itself; and no comprehensive
  HTTP integration matrix for all criteria. The current tests establish a bounded
  first implementation only. Account lockout/rate limiting is explicitly out of
  scope and remains a threat-model residual.
- The delegated specifier and reviewers supplied actual context byte counts and
  observable call counts; output size for specifier was 21,195 bytes, reviewer read
  35,078 bytes over eight files. Implementer selected context totals, tokens and
  precise duration were not observable. Coordinator's previously selected source
  total is 48,640 UTF-8 bytes; total hidden/inherited context and token use are
  unknown. Do not infer cost or token savings from these byte counts.
- Final independent reviewer confirmed that the policy boundary and atomic
  revocation findings were resolved, the added disable/removal/user-disable and
  synchronized race checks close the verification gap, and no issue exists in
  those edits. Reviewer interval 07:31:18–07:31:36 UTC; two read calls, zero
  retries; selected file 10,434 UTF-8 bytes; tokens unknown. Reviewer did not
  rerun tests; coordinator's exact final rerun passed. Remaining partial criteria
  and candidate fingerprint are recorded in
  `workflow/reporter/evidence/auth-membership.md`.
- After the final reviewer identified incomplete revocation evidence, coordinator
  added isolated PostgreSQL assertions for membership disable, membership removal,
  and user disable session revocation, plus a latch-synchronized read/revocation
  test. The already-authorized query completes after revocation commits, while a
  subsequent fresh query denies access. The role-change operation is separately
  tested for rollback on session-deletion failure and successful session deletion.
  The focused suites were rerun at 2026-11-06 07:31 UTC with `--rerun-tasks`:
  BUILD SUCCESSFUL; 18 tests, 0 failures, 0 errors, 0 skipped. Exact command is in
  the evidence artifact. Final reviewer confirmation of this added test is pending.
