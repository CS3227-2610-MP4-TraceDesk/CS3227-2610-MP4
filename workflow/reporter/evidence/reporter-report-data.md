# REPORT-DATA issue #13 evidence

Status: implementation and focused verification complete; final evidence review
recorded below. Base `b69f64f`; branch
`codex/reporter-report-data`; started 2026-11-07 07:27:41 UTC.
Human student ownership not supplied.

## Bounded plan

Implement create/list/detail and minimal Reporter context from the existing
independently reviewed report contract, adding TD-RPT-06–10 before code. No
draft/edit/transition/clarification/UI/AI feature in this slice. Existing contract
is sufficient: a new specifier rewrite is unnecessary. Coordinator owns spec,
index/threat/evidence and verification. GPT-6 Luna implementer owns backend
production and focused tests; a separate GPT-6 Luna reviewer inspects actual
code/tests and evidence after the implementation handoff. Sequential ownership
avoids shared-file races. Implementer budget 60 calls/45 minutes; review budget
20 calls/25 minutes; reassess after three similar failures.

Map TD-RPT-06 to migration/constraints tests, 07 to create/idempotency/audit tests,
08 to visibility/context/pagination API tests, 09 to safe denial/failure tests,
10 to policy, concurrency, isolation guard and independent diff review. Changed
boundaries: browser→report API, trusted session→fresh project/object policy and
transaction→report/idempotency/business audit. Use synthetic isolated fixtures;
never read environment files, live data or raw user content into evidence.

Record exact working-tree identity, commands/pass/failure/skip results, actual
review findings/corrections, UTC intervals, selected source bytes and available
call/retry metrics; unknown tokens/cost remain unknown. One bounded run is not
a controlled comparison. Coordinator owns the development context comparison
due before final MP4 acceptance; three-role application comparison is due after
secured AI exists, as specified in the existing milestone experiment plan.

## Observed implementation and early verification

Existing sufficient contract was reused; no specifier agent rewrote it.
`/root/report_data_implementer` owns production, domain and persistence tests;
`/root/report_data_api_tests` authored the separate real-HTTPS test. Coordinator
integrated additional boundary assertions after the test agent's handoff.
`/root/report_data_reviewer` independently inspects actual source/tests. Review
began once production compiled, while verification and remaining test corrections
continued; this is staged review, not a completed pre-implementation approval.

The coordinator's source pass identified supplementary Unicode being encoded
one UTF-16 unit at a time in request hashing, an owner-less policy fact denying
Reporter detail reads, untyped SQL nulls/Instant binding, NULL assignment-filter
semantics, full-text selection for summary pages, and Spring configuration in an
application package. These were corrected before runtime acceptance. Exact
requests, user text and credentials are not copied into this evidence.

Initial checks (interim working tree, not the final candidate):

- Foundation checker passed: 69 documents, 4 skills, 21 provenance entries and
  9 transport Java sources. Tracked whitespace check passed.
- Compile attempt 1 failed: Jackson 2 imports on the Jackson 3 classpath and a
  report-vs-summary rendering mismatch. Corrected.
- Compile attempt 2 passed production but failed test compilation: outdated
  failure-handler constructor usage, a nonexistent leftover result type, a
  local variable collision and incorrect Jackson collection API use. Corrected.
- First database-backed suite compiled and ran 27 auth/report tests: 26 passed,
  1 failed, no skips. HTTPS boundary test passed. Failure: the DB accepted a
  title containing only NBSP/figure-space; migration constraint was corrected
  to use explicit Unicode whitespace. The later db-test-support task was not
  reached after this failure. This failed run is not final acceptance evidence.

The existing disposable Compose `test-db` was started on loopback port 54330
and became healthy. Only `_test` database/random owned schemas are used.
No environment file or live application database was read. Later focused checks
must prove the corrected constraints and synchronized membership race, including
absence of unauthorized report/idempotency/audit commits.

## Final behavior and criterion evidence

Implemented `POST /api/projects/{projectId}/reports`, report list/detail and
`GET /api/projects/{projectId}/reporter-context`. A Reporter creates only their
own version-1 OPEN report. Reporters read their own reports; active project
Developers/QA read shared reports. Lists select summary columns only. Request
identity claims are rejected; current session, membership, role and object
visibility determine access. No draft/edit/transition/clarification/UI/AI route
was added.

V4 adds report, scoped digest-only idempotency and metadata-only business audit
tables, required bounds and updated safe security-event allowlists. Create and
replay lock project/user/membership rows; report/idempotency/audit commit as one
transaction. Same-key changed content conflicts; replay returns the current
authorized report. Cursors have a process-local random 256-bit HMAC key,
canonical Base64URL decoding and 15-minute expiry. Restart invalidates cursors;
clients restart listing. No persistent key provisioning or multi-instance cursor
continuity is claimed.

| Criterion | Implementation and observed checks | Result |
| --- | --- | --- |
| TD-RPT-06 | `V4__reports.sql`; `ReportPersistenceIntegrationTest.reportConstraintsIdempotencyConcurrencyAndAuditRollback`: invalid foreign key/status/version/Unicode blank/control/length fixtures rejected; all-field maxima and TAB/LF/CR round trip accepted; initial UTC timestamp/version and audit persist | Pass |
| TD-RPT-07 | `ReportService` + `ReportStore`; persistence/API tests assert trusted owner, OPEN/version 1, exact text, same-key replay, emoji-only changed-body conflict, expired-key replacement, concurrent same-key single result and atomic audit rollback | Pass |
| TD-RPT-08 | Scoped summary/detail queries, `ReportCursorSigner` and Reporter context; domain/API tests assert own/other Reporter, Developer/QA visibility, opaque absent/foreign object responses, bounded keyset continuation, assignment/status filters, cursor actor/project/purpose/filter/signature/alias/expiry checks and response allowlists | Pass |
| TD-RPT-09 | Strict Jackson 3 keys/types, body filter and safe handlers; real HTTPS tests exercise complete-payload duplicate keys, wrong/forged/null fields, malformed UUID/key, fixed/chunked oversize, CSRF/session precedence, membership/user/project disablement, role changes and report/security/audit sink outages, no-store and secret-canary absence in captured logs/events | Pass |
| TD-RPT-10 | Pure policy/validator tests, isolated DB concurrency/rollback test, HTTPS API test and negative isolation probe; actual independent review and final candidate pinned below | Pass at this bounded backend slice |

The final source pass also corrected two late issues: noncanonical Base64URL
aliases could make a cursor tamper assertion flaky, and CSRF/malformed-request
event attribution called an actor resolver that could invalidate an expired
session before CSRF succeeded. Canonical segment checks plus an alias test and
nonmutating `forEvent()` plus a real backdated-session regression now cover
both. That regression proves 403/400 error attribution preserves the session,
then the normal authenticated read returns 401 and invalidates it.

The independent reviewer confirmed the corrected source boundaries, complete
duplicate-key fixture, Unicode constraints, actual membership-lock race,
assignment filters, nonmutating error-event attribution and canonical cursor
handling, with no remaining supported source finding. The initial duplicate-key
configuration concern was checked against the existing Jackson 3 property;
the configuration already existed. The new test was strengthened to duplicate
a key in a complete valid body so it proves parser rejection independently of
missing-field validation.

## Final candidate and commands

Candidate `b69f64f76910-plus-19b559c01a96e555`, base
`b69f64f7691024f4e5d9e7437c5354082249fa4f`. The
[manifest](reporter-report-data/candidate.json) pins 21 changed source/test/spec/
index/threat files. All hashes matched after the final test run. Evidence files
are excluded from this identity to avoid self-reference. No commit/push or
issue closure was performed during implementation.

- With the disposable Compose test service healthy and its documented synthetic
  test settings supplied explicitly, ran
  `./gradlew :backend:auth-app:test :backend:db-test-support:test --no-daemon`.
  Final run started 07:47:32 UTC; result observed by 07:48:21 UTC: build passed
  in 21 seconds. Auth-app executed 28 tests; the unchanged db-test-support task
  reused its successful four-test result from suite 2. Total verified results:
  **32 tests, zero failures/errors/skips**. See [sanitized results](reporter-report-data/test-results.json).
- Missing-configuration probe:
  `env -u TRACEDESK_TEST_DATABASE_CONFIRM_ISOLATED -u TRACEDESK_TEST_JDBC_URL -u TRACEDESK_TEST_DATABASE_USER -u TRACEDESK_TEST_DATABASE_PASSWORD ./gradlew :backend:auth-app:test --tests com.tracedesk.auth.reports.persistence.ReportPersistenceIntegrationTest --rerun-tasks --no-daemon`.
  Expected build failure observed: one test failed with the required explicit
  isolation-confirmation error; zero skips. Verified its exception before the
  final configured run restored passing output. [Probe result](reporter-report-data/negative-configuration.json).
- `python3 tools/check_foundation.py`: passed, 69 documents, four skills,
  21 provenance entries, nine transport Java sources.
- `git diff --check`: passed. Final closeout also checks untracked new files,
  portable links, candidate hashes and sanitized measurement validation.

Closeout checks passed: foundation checker over 70 documents after the ignored
local run was added; two observed measurement records validated by
`python3 workflow/reporter/tools/agentic_measurements.py workflow/reporter/evidence/reporter-report-data/observations.jsonl`;
all 21 candidate hashes/newlines/whitespace; evidence JSON parsing; pure
domain/application import boundary scan. The local coordination run remains
Git-ignored as requested. No failures/skips remain in the configured test result;
the intentional negative isolation failure remains separately identified.

All fixture content is synthetic; HTTPS uses temporary test certificates and
random owned DB schemas. No raw provider transport is on the deployable
classpath. AI/provider behavior, frontend/browser integration, later report
features, deployment and milestone context comparisons remain outside this
verification. Versioned edit conflicts are #15; only initial version and create/
replay concurrency are accepted here.

Final independent evidence review ended 07:49:51 UTC: no remaining supported
findings. Reviewer rehashed all 21 manifest entries (146,539 bytes), zero
mismatches, and cross-checked the sanitized configured and negative-probe
results. Reviewer did not execute tests. The disposable test service was
stopped after verification; development services/data were untouched.

## Measurements and reflection

Model for implementer, API-test author and independent reviewer: GPT-6 Luna.
Production implementer reported 49 direct calls (shell/patch/coordination), no
retries, finish 07:44:49 UTC; its start was not captured. Reported 17 owned-file
snapshots total 65,899 UTF-8 bytes; this is not full input-context or token usage.
API-test author reported 39 calls, exceeding its 35-call budget, with one
sandbox-blocked Gradle attempt and a canceled escalated retry; handoff at
07:38:13 UTC. Its original 34,694-byte test output was subsequently extended by
the coordinator; selected input-context bytes and start are unknown.
Production implementer stayed within its call budget; unrecorded start times
prevent claiming exact individual elapsed time or time-budget compliance.
Coordinator calls/retries and all model tokens/costs remain unknown.

Reviewer reported approximately 33 direct calls (one failed shell-quoting
attempt and corrected retry), exceeding the 20-call budget; finish 07:49:51 UTC,
start unrecorded, so exact duration/time-budget compliance is unknown. Reported
inspected specification/guidance/source snapshot aggregate: 180,954 bytes;
this is self-reported context selection, not host-measured model input. The
21-file candidate source selection was independently rehashed as above. Unknown
or approximate telemetry is preserved in the handoff record rather than treated
as exact or zero. [Measurement records](reporter-report-data/observations.jsonl)
cover only coordinator windows with captured UTC bounds; individual agents with
uncaptured starts are kept in the [handoff metadata](reporter-report-data/handoffs.json).

Stable public API interfaces allowed API-test authorship alongside production
work, with disjoint ownership. Coordinator performed test compatibility fixes
and added boundary assertions only after the test author handed off. A parallel
compile attempt was canceled to leave verification with one owner. Source
review overlapped verification, followed by a final result review. This helped
find concrete defects but the task has no baseline: no speed/token/quality
improvement claim follows. Next run should consolidate API/toolchain details
in the initial handoff and capture start/call metrics automatically. The two
milestone comparisons and final release gates remain pending.
