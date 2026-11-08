# DEV-DATA #27 implementation evidence

- Feature and criterion IDs: DEV-DATA #27; TD-DEV-06 through TD-DEV-10.
- Date and author: 2026-11-08, Codex implementation session. Independent
  read-only review was performed by harness agent `/root/dev_data_27_review`
  using `gpt-6-luna`; see the [run record](../runs/2026-11-08-dev-data-review.md).
- Revision: base `69fbee2cf00e15dd5f812f78b017adb72c78b65a` on
  `codex/dev-data-27`, plus the uncommitted working tree recorded by `git diff`
  and the untracked Developer investigation source/test/migration files.
- Environment: Windows/PowerShell; JDK 25 toolchain; Gradle 9.6.1. Docker was
  unavailable (`docker` command not found). No `.env*` file or live application
  database was read.

## Exact checks and outcomes

| Command | Outcome | Scope |
| --- | --- | --- |
| `.\gradlew.bat --no-daemon --console=plain :backend:auth-app:compileJava` | **Passed**: `BUILD SUCCESSFUL`, one compile task | Production Java compilation |
| `.\gradlew.bat --no-daemon --console=plain :backend:auth-app:testClasses` | **Passed**: `BUILD SUCCESSFUL` | All backend test sources compile |
| `.\gradlew.bat --no-daemon --console=plain :backend:auth-app:test --tests 'com.tracedesk.auth.investigation.domain.*' --tests 'com.tracedesk.auth.investigation.api.InvestigationControllerTest'` | First run **failed** one test because its Mockito setup mixed literal arguments and matchers; the test setup was corrected. Rerun **passed** 6 tests, 0 failed, 0 skipped. | Pure note limits/policy and direct controller contract |
| `.\gradlew.bat --no-daemon --console=plain :backend:auth-app:test --tests 'com.tracedesk.auth.*.domain.*' --tests 'com.tracedesk.auth.domain.*' --tests 'com.tracedesk.auth.application.*' --tests 'com.tracedesk.auth.security.*' --tests 'com.tracedesk.auth.api.AuthControllerTest' --tests 'com.tracedesk.auth.investigation.api.InvestigationControllerTest'` | **Passed** 29 tests across 10 suites; 0 failed, 0 skipped. | Broader non-database backend regression |
| `.\gradlew.bat --no-daemon --console=plain :backend:auth-app:test --tests 'com.tracedesk.auth.investigation.persistence.InvestigationPersistenceIntegrationTest'` | **Unavailable / required suite failed closed** before migration setup: `IsolatedPostgres.open` rejected missing `TRACEDESK_TEST_DATABASE_CONFIRM_ISOLATED` and database settings. 1 test failed, not skipped. | V5 migration, PostgreSQL constraints, locks, races, cap and audit rollback remain unproven in this environment |
| `docker compose --profile test up -d --wait test-db` | **Unavailable**: Docker executable not installed/found. | Could not provision the repository's disposable test database |
| `Test-NetConnection 127.0.0.1 -Port 54330 -InformationLevel Quiet` | **Unavailable**: returned `False`; no disposable PostgreSQL listener was present. | Confirms the documented local test endpoint was not available |
| `git diff --check` | **Passed** with no whitespace errors at the recorded working-tree state. | Patch hygiene |

After independent review, two focused regression runs failed only because a new
Mockito verification asserted one denial event while the expanded test correctly
emitted multiple denials. The event-count assertions were corrected; the final
focused suite passed 6 tests. The broader non-database suite then passed 29 tests,
0 failed and 0 skipped. The required PostgreSQL command was retried after the
all review fixes and remained unavailable at `IsolatedPostgres.open`; it failed rather
than skipping.

## Independent review and corrections

The read-only `gpt-6-luna` reviewer inspected the actual diff, production code,
migration, tests and evidence. Three P2 findings emerged across the initial and
focused follow-up passes:

1. semantic body/version validation preceded object authorization;
2. new idempotency reservation could produce an FK-driven `503` before opaque
   report lookup;
3. concurrent same-key replay/conflict could lose precedence while waiting on a
   report lock or while targeting different reports.

The implementation now performs semantic validation after locked object checks,
reserves new idempotency rows only after report authorization, and serializes the
scoped note-create key before replay lookup and report selection. Regression test
source covers invalid content/version while unassigned, absent/foreign reports,
same-key note 200 replay and same-key cross-report conflicting content. The final
independent read-only pass reported all findings resolved and no remaining
supported correctness, deadlock, lock-order or security defect. The reviewer did
not execute tests; PostgreSQL behavior remains unproven.

## Criterion status

- TD-DEV-06: implemented in `V5__developer_investigation.sql`, but database
  migration and invalid-fixture assertions are **not proven** until the isolated
  PostgreSQL suite runs.
- TD-DEV-07: implemented with trusted-session API handling and transaction-level
  membership/report locks; pure/direct API checks pass; PostgreSQL lock behavior
  remains pending.
- TD-DEV-08: implemented with note-only versions, allowlisted note responses and
  metadata-only audit writes; direct API checks pass; atomic database behavior
  remains pending.
- TD-DEV-09: request-shape, body-cap, CSRF, role and assignment-denial paths have
  focused checks. Cross-project, persistence rollback and live reassignment cases
  exist in the required PostgreSQL test source but remain unexecuted.
- TD-DEV-10: **not satisfied**. Independent implementation review is complete and
  its findings are corrected, but the required database suite remains unavailable.
  The synthetic assignment tests do not prove QA-ASSIGN #32 or a production
  Developer journey.

## Review and limitations

The implementation received an independent read-only agent review; the human
owner's own sign-off is recorded separately below. No live browser, deployment, production database, QA assignment journey,
provider call, token/cost measurement or release check was performed. Test output
contains synthetic identifiers/text only.

## 2026-11-08 note-limit follow-up

The investigation-note contract and candidate were simplified to one 8,000
code-point body limit. The redundant 32,000-byte limit was removed from the
specification, Java validator and V5 migration. The V5 body constraint delegates
the upper bound to `varchar(8000)` and retains only nonblank and control-character
checks. Direct PostgreSQL assertions were
added for disallowed C0 vertical tab and C1 NEL controls and for allowed TAB, LF
and CR. No Reporter or proposed-resolution field limits changed.

| Command | Outcome | Scope |
| --- | --- | --- |
| `.\gradlew.bat --no-daemon --console=plain :backend:auth-app:test --tests "com.tracedesk.auth.investigation.domain.InvestigationNoteDataTest"` | **Passed**: `BUILD SUCCESSFUL`; production and test sources compiled and the focused validator suite passed. | 8,000-code-point boundary, supplementary characters, blank/control/surrogate rejection and digest behavior |
| `.\gradlew.bat --no-daemon --console=plain :backend:auth-app:test --tests "com.tracedesk.auth.investigation.persistence.InvestigationPersistenceIntegrationTest"` | **Unavailable / required suite failed closed**: 1 test failed at `IsolatedPostgres.open` with `Explicit isolated test database confirmation is required`; 0 skipped. | The new database-level control-character assertions compiled but could not execute without the explicitly confirmed isolated PostgreSQL configuration. |
| `git diff --check` | **Passed**; only the existing line-ending warning for the modified specification was reported. | Patch hygiene |

The restricted runner initially hung before Gradle started because `java -version`
did not return; those attempts were terminated and produced no test result. The
recorded Gradle outcomes above are from the successful host-executed retries.

## 2026-11-08 manual HTTP run and DELETE parameter fix

A manual curl suite (synthetic seed; local dev PostgreSQL 18 on
`127.0.0.1:54329` with V1–V5 applied; backend on `localhost:8080` built from the
working tree before this fix) exercised developer-context, start, note
list/create/edit/delete, body limits, cursors, idempotency, the 200-note cap,
SQL-driven reassignment/revocation, business-audit counts and V5 constraint
probes. Result: 173 passed, 2 failed, 0 skipped. One failure was a defect in the
script (an all-digit UUID cannot be uppercased); a corrected manual request
returned `400` for uppercase and unhyphenated UUIDs. The other was a product
defect: `DELETE …/notes/{noteId}` without `expectedVersion` returned
`503 SERVICE_UNAVAILABLE`, because Spring's missing-parameter exception reached
the generic handler.

`InvestigationController.deleteNote` now accepts `expectedVersion` as optional,
so a missing, empty or non-numeric value goes through the controller's own check
and returns `400 INVALID_REQUEST` with a `DEVELOPER_INVALID_REQUEST` security
event. The new direct API regression test
`deleteWithMissingOrMalformedExpectedVersionIsInvalidRequestNotServiceUnavailable`
fails when the old required `@RequestParam` is restored (1 of 4 tests failed)
and passes with the fix.

| Command | Outcome | Scope |
| --- | --- | --- |
| `.\gradlew.bat --no-daemon --console=plain :backend:auth-app:test --tests 'com.tracedesk.auth.investigation.api.*' --tests 'com.tracedesk.auth.investigation.domain.*' --tests 'com.tracedesk.auth.*.domain.*' --tests 'com.tracedesk.auth.domain.*' --tests 'com.tracedesk.auth.application.*' --tests 'com.tracedesk.auth.security.*' --tests 'com.tracedesk.auth.api.AuthControllerTest'` | **Passed**: 30 tests, 0 failed, 0 skipped | Direct API including the new regression, plus the non-database backend checks |

Limitations: the manual run was against the local dev database, not the isolated
test database, so it does not satisfy TD-DEV-10's required PostgreSQL suite,
which was not run.

Live re-check: after the backend was restarted on the fixed working tree, the
same suite (reseeded) passed 176 of 176 checks with 0 skipped, including
`delete-no-version` → `400`. On that database, `flyway_schema_history` shows
V1–V5 applied successfully by the backend at startup, which confirms that the
`spring-boot-starter-flyway` dependency runs migrations under Spring Boot 4.1.2.

## Human owner review (2026-11-08)

The Developer contributor (`Izn432`) stated in the Claude Code session that
committed this candidate that they have tested it and reviewed the code, that it
looks good, and that it is ready to commit and push to `codex/dev-data-27`. The
statement covers the working tree on base
`69fbee2cf00e15dd5f812f78b017adb72c78b65a` as committed in that session. This
section records their statement only; the specific tests they ran were not
stated, and the commit session did not repeat any checks.

This sign-off does not change the criterion status above: TD-DEV-10 stays **not
satisfied** until the required isolated PostgreSQL suite passes on a recorded
candidate, and no runtime acceptance, issue closure or release is claimed.

## 2026-11-08 merge with `main` and migration renumbering

`main` (`341dde3`) was merged into `4c98b24` for PR #38. `main` had added
`V5__report_drafts_and_edits.sql`, so the Developer migration was renumbered and
reconciled as `V6__developer_investigation.sql`; earlier sections that say V5 refer
to this migration before the renumbering. A database that applied the old V5 must
be reset or repaired.

Reconciliation changes beyond the rename:

- V6 no longer drops the `new_status`/`new_version` checks or adds `prior_version`,
  all of which `main`'s V5 already handled.
- The security-event route allowlist keeps the three draft routes.
- `object_type`/`object_id` backfill and the audit shape check now cover
  `REPORT_EDITED` and the four `DRAFT_*` events.
- `ReportStore.audit` writes `object_type` and `object_id` for those events, because
  `object_id` is required. This touches Reporter-owned code.

| Command | Outcome | Scope |
| --- | --- | --- |
| `.\gradlew.bat --no-daemon --console=plain :backend:auth-app:test --continue` with the isolated-test settings pointed at a disposable PostgreSQL 18.6 cluster on `127.0.0.1:54331` | **Passed**: 36 tests across 16 suites, 0 failed, 0 skipped, including `InvestigationPersistenceIntegrationTest` and the Reporter persistence and HTTPS integration tests | Merged working tree: Flyway V1–V6 on PostgreSQL and the full `auth-app` suite |
| Direct `psql` probe in a disposable database: V1–V5, one legacy row per audit event type, then V6, then one new-shape row per event | **Passed**: V6 applied, backfill set the expected object type and target, new-shape rows were accepted, a mismatched draft row was rejected by the shape check, and the route allowlist holds both draft and Developer routes | Draft and report-edit audit events, which no automated test writes to PostgreSQL |

The cluster used synthetic credentials only and was deleted afterwards. These
results supersede the earlier "unavailable" PostgreSQL outcomes for this merged
candidate.

GitHub Actions run `42459090108` passed on PR #38 at head `9cd3dcf`, including
`Authentication policy tests` and `Reporter browser against isolated backend`,
whose integration fixtures apply V1–V6 to a fresh PostgreSQL 18 service. CI
migrates empty databases, so it does not exercise the V6 backfill or an upgrade
from the old V5, and no CI test writes draft or report-edit audit events.

### Human owner review of the merge (2026-11-08)

The Developer contributor (`Izn432`) stated in the Claude Code session that they
reviewed the latest changes and that the code looks good. This covers the merge
with `main`, the V6 reconciliation and the `ReportStore.audit` change in `9cd3dcf`.

Runtime acceptance of TD-DEV-10 is still not claimed. No agent reviewed this
reconciliation independently, and the CI gaps above remain.

## 2026-11-08 independent-review corrections

A fresh independent read-only review of PR #38 at
`67774853e732c299d3d4d6f14ceab4e32c291411` against `origin/main`
`341dde3409c577a11be2a1735d5b255a4cf6b869` found two P2 gaps: V6 did not
reject unrelated audit target columns for every event shape, and the persistence
suite did not exercise successful delete audit shape, reassignment and note-version
races, or audit-failure rollback for start/edit/delete.

The working-tree correction tightens every V6 audit branch to require the exact
report/draft target combination. The investigation persistence test now rejects
a report event carrying a draft target and a note event without a report target;
checks a successful hard delete and its exact metadata-only audit row; races two
updates at the same note version; commits reassignment ahead of a blocked note
mutation; and verifies audit failure rolls back start, create, edit and delete.

| Command | Outcome | Scope |
| --- | --- | --- |
| `.\gradlew.bat --no-daemon --console=plain :backend:auth-app:testClasses` | **Passed**: `BUILD SUCCESSFUL` | Production and test source compilation after the review corrections |
| `.\gradlew.bat --no-daemon --console=plain :backend:auth-app:test --tests 'com.tracedesk.auth.investigation.persistence.InvestigationPersistenceIntegrationTest' --rerun-tasks` with a fresh disposable PostgreSQL 18 cluster and explicit isolated-test settings | **Passed**: `BUILD SUCCESSFUL` | V1–V6 migration, invalid audit fixtures, successful delete audit, concurrent version serialization, reassignment race and audit rollback |
| `.\gradlew.bat --no-daemon --console=plain :backend:auth-app:test --rerun-tasks` with a second fresh disposable PostgreSQL 18 cluster and explicit isolated-test settings | **Passed**: 36 tests across 16 suites, 0 failed, 0 skipped | Full `auth-app` regression including Reporter persistence and HTTPS integration |
| `git diff --check` | **Passed**; only line-ending conversion warnings were printed | Patch hygiene |

Both disposable clusters used synthetic credentials, listened only on loopback
port 54331, were stopped after their run and were removed. The first restricted
runner attempt left a stopped workspace-local `.tmp-tracedesk-pg-54331` cluster;
it was verified stopped and removed before recording this evidence. No existing
application database or `.env*` file was read.

This resolves the two supported review findings in the candidate working tree.
TD-DEV-10 remains a broader runtime-acceptance gate: these backend checks do not
prove the future QA assignment UI/journey, deployment, or production behavior.

## 2026-11-08 CI clock-fixture correction

GitHub Actions exposed a time-dependent test defect after `76a347a`:
`InvestigationControllerTest.deleteWithMissingOrMalformedExpectedVersionIsInvalidRequestNotServiceUnavailable`
expected invalid-request security events to contain the synthetic actor, but the
test constructed `ApiFailureHandler` with the system clock while its controller
and session used the fixed instant `2026-11-08T02:03:04Z`. Once wall time was at
least eight hours later, the handler correctly treated that synthetic session as
expired and recorded a null actor. Production code was not implicated.

The controller test now shares one fixed `Clock` between the controller, service
and exception handler. This preserves the intended assertion—that a valid
synthetic session contributes its trusted actor to all four invalid DELETE events—
without depending on execution date or timezone.

| Command | Outcome | Scope |
| --- | --- | --- |
| `.\gradlew.bat --no-daemon --console=plain :backend:auth-app:test --tests "com.tracedesk.auth.investigation.api.InvestigationControllerTest.deleteWithMissingOrMalformedExpectedVersionIsInvalidRequestNotServiceUnavailable" --rerun-tasks` | **Passed**: `BUILD SUCCESSFUL` | Formerly failing missing/empty/malformed/non-positive `expectedVersion` regression |
| `.\gradlew.bat :backend:auth-app:test --no-daemon --rerun-tasks` with explicit isolated-test settings and a fresh disposable PostgreSQL 18 cluster | **Passed**: 36 tests across 16 suites, 0 failed, 0 skipped | Exact CI Gradle task after the clock-fixture correction |
| `git diff --check` | **Passed**; only line-ending conversion warnings were printed | Patch hygiene |

The disposable cluster used synthetic credentials, listened only on loopback
port 54331, and was stopped and removed after the run. No existing application
database or `.env*` file was read.
