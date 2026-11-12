# Reporter status and activity history — issue #16

Historical pre-integration snapshot. For the current eight-event contract,
V16 migration and verification after upstream QA integration, see
[the integration record](reporter-progress-integration.md). The results and
limitations below describe the original candidate before that integration.

Date: 2026-11-12. Criteria: TD-RPT-21–25. Base revision:
`7f7563c22c8afddaf6763e3db9d4cdba5dbdb47d` plus the uncommitted task source
identified by [reporter-progress.sha256](reporter-progress.sha256). This is local implementation evidence,
not a deployment or production acceptance claim.

## Change and security boundary

The Reporter report detail shows an Activity section using
`GET /api/projects/{projectId}/reports/{reportId}/activity`. It renders only the
five currently recorded shared event codes in chronological order. Each page
defaults to 25 items and caps at 100; signed continuation binds actor, project,
report, purpose, filter, order and position for 15 minutes. Audit bigint IDs stay
decimal strings across JSON/JavaScript. V13 adds the report-scoped audit index and
the security-event route allowlist entry.

The repository authorizes current enabled account, Reporter role, active project
membership and report ownership before selecting seven approved metadata columns,
then checks authorization again at delivery within its transaction. Membership
locks serialize revocation. Private-note events and bodies, staff resolution
content, actor/request IDs, reasons and other reports never enter the activity
response. Denials are recorded through existing security events; missing audit
storage returns 503 without a partial page. No business writes or AI calls are
introduced by this read endpoint.

The UI offers explicit paging, refresh and retry, displays loading/empty/failure
states, and cancels/discards late requests on report/project/session changes.
Manual refresh reloads report detail first so its status is current, then reloads
activity; the confirmed detail status remains visible during initial activity
loading. It uses existing SDK buttons, section styles and colour tokens.

## Executed checks

All database checks use the existing Compose **test** service at loopback port
54330, database `tracedesk_test`, with `IsolatedPostgres` randomly owned schemas
and teardown. The development database at 54329 is not used. The test environment
sets `TRACEDESK_TEST_DATABASE_CONFIRM_ISOLATED=true` and the documented test-only
JDBC/user/password variables; no environment or local credential files were read.
AI remains disabled/synthetic; no live provider calls were made.

| Command | Result / assertions |
| --- | --- |
| `cd frontend && npm run check && npm run build` | 115 tests pass; typecheck, lint and build pass. Includes exact response-shape rejection, large IDs, paging, explicit retries, expired cursors, denial clearing, late-response cancellation and integrated current status during activity loading. |
| `cd frontend && npm run test:e2e -- --project=production --project=real-backend reporter-states.e2e.ts reporter-ai.e2e.ts reporter-real.e2e.ts developer-real.e2e.ts` with the isolated test environment and `TRACEDESK_REAL_BACKEND_TEST=true` | 21 pass: 18 controlled Reporter regressions and 3 real-backend journeys. The extended Developer journey signs in as the report owner after real investigation/resolution transitions, checks chronological metadata and READY_FOR_QA status, private-note/resolution exclusion, cross-project and other-owner denial, 1440/390/320 layouts and axe accessibility. |
| `./gradlew :backend:auth-app:test --tests com.tracedesk.auth.reports.domain.ReportActivityCursorTest --tests com.tracedesk.auth.reports.persistence.ReportActivityPersistenceIntegrationTest --tests com.tracedesk.auth.reports.api.ReportHttpsIntegrationTest --no-daemon --console=plain` with the isolated test environment | Final aggregate: 3 tests pass, 0 failed/errors/skipped; BUILD SUCCESSFUL in 16s. Covers HTTPS response keys, anonymous/wrong-role/owner/project/disabled access, query validation, denial-audit failure, audit-store outage, cursor expiry/tampering/binding, 109 tied-timestamp approved events across 100+9 pages, full numeric/distinct ID comparison, and synchronized revocation. |
| `cd frontend && npm run test:e2e -- --project=real-backend developer-real.e2e.ts` with the same isolated environment | Final UI candidate: 1 pass in 31.2s after mobile toolbar wrapping. Verifies internal main overflow, refresh action in viewport and activity card captures at 1440/390/320; axe has zero violations. |
| `python3 tools/check_foundation.py` and `git diff --check` | Pass. |

The first attempted browser command could not bind port 4177 in the sandbox;
it was rerun with local-server permission. The first real journey then timed out
because the synthetic report owner lacked project membership: the application
correctly showed only the project directory. The test fixture now gives that
synthetic owner an active membership. The corrected combined suite passed.
Additional test-only corrections addressed an unsupported Testing Library option
and an assertion that ran before the report list finished loading. These were
test harness failures, not passing runs.

The expanded PostgreSQL test exposed a real paging defect missed by the initial
small fixture: selecting `id::text AS id` made unqualified `ORDER BY id` sort
lexically, while the continuation predicate compared the underlying bigint.
The 109-event fixture returned 100 + 20 rows, including duplicates. The
coordinator diagnosed the alias mismatch; the numeric audit-table ID is now
explicitly qualified in the sort/boundary. Final verification must compare the
complete ordered ID sequence and distinct count, not just the first page.
The first full-sequence assertion itself repeated the text-alias mistake in
its expected query. The coordinator corrected the independent baseline to fetch
raw `Long` IDs before conversion to strings; the final aggregate above passes
the exact-sequence and uniqueness assertions. The reviewer inspected both fixes.

Screenshot inspection of the narrow report detail exposed an existing clipped
version label. The detail toolbar now wraps at narrow widths. The final targeted
browser check additionally measures internal main overflow, verifies the refresh
button is in the viewport, and captures the activity card after scrolling (the
existing full-page helper intentionally resets to the top).

## Review and limitations

Actual coordination and review are recorded in
[the durable coordination summary](reporter-progress-coordination.md). Independent source review
found a missing explicit filter binding in the activity cursor. The fixed filter
contract is now signed and verified. The independent reviewer also rechecks the
numeric-order correction identified by the expanded database test; the earlier
source review alone did not catch that bug. Final source review has no outstanding
supported defects. It independently inspected the corrected numeric expected-ID
baseline and final 320/1440 activity-card screenshots. Final execution counts
were verified by the coordinator from test XML and browser output. The full
backend suite and hosted CI were not run; only the focused suites above are
claimed.

| Criterion | Local result |
| --- | --- |
| TD-RPT-21 | Verified direct HTTPS role/owner/project/disabled denials, trusted session and no-store; real browser checks other-owner and cross-project denial. |
| TD-RPT-22 | Verified current event/projection allowlist and private-note/resolution exclusion. No future QA event support claimed. |
| TD-RPT-23 | Verified default/max limits, invalid limits, numeric tied-timestamp paging, complete ID sequence, distinct IDs, boundary cursor and identity/lifetime/tamper checks; fixed filter/sort/purpose binding independently reviewed. |
| TD-RPT-24 | Verified component and integrated workspace states/cancellation plus real browser chronology, current status, narrow layout and axe checks. |
| TD-RPT-25 | Verified fresh migrations/index, authorization before selection/delivery, revocation serialization and failure-closed audit-store/security-event outages. |

Generated synthetic screenshot paths: `frontend/e2e-results/artifacts/developer-real.e2e.ts-real-ce928-sions-and-responsive-access-real-backend/reporter-activity-{320,390,1440}.png`.
Browser: Chromium 156.0.8078.4, macOS/darwin 24.2.0 ARM64, system UI font,
dark theme, device scale 1, reduced motion. Screenshots are local generated
artifacts, not frozen reference baselines or production data.

Issue #16 excludes clarification requests/replies. Their old shared design is
marked deferred in the Reporter specification. QA closure/rejection/reopening
events are not implemented in the current audit schema; they are not invented
or silently admitted to this timeline. Their owning QA lifecycle slice must
define and record those events, then extend the explicit Reporter allowlist and
tests. Current status continues to display any valid canonical report status.

The frozen UI references contain QA layouts, not a Reporter activity timeline;
this section extends existing Reporter components and was checked with synthetic
fixtures. No claim of pixel equivalence or human approval is made. No deployed
environment, hosted CI run, live user data or production migration was exercised.
Development-context comparisons remain the coordinator-owned final MP4 milestone
experiment; this implementation run is not a controlled comparison and supports
no claim of token/cost savings.
