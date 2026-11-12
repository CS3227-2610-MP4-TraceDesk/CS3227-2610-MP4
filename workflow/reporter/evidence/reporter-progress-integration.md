# Reporter activity integration with current main

Date: 2026-11-12. Supersedes the pre-integration five-event/V13 limitations in
[the original Reporter evidence](reporter-progress.md), whose original check
results and source identity remain historical records.

## Remote integration

The initial push of local commit `0276d3d` was rejected because `origin/main`
had advanced to `dfaed91` with QA assignment, verification and revision
send-back work. The Reporter commit was rebased onto that revision. Four content
conflicts were resolved while retaining both features: report route recognition,
controller endpoint constants, assignment plus activity service methods, and
the real browser's QA plus Reporter assertions. No force push is used.

QA migrations V14/V15 are now upstream. The unpublished activity migration was
renumbered V16 so a database already at V15 can upgrade without enabling
out-of-order migrations. A new isolated test creates real report/assignment,
Developer and QA data at V15, then applies only V16 and reads the retained
history. No user development database or environment file was accessed.

The current eight-code allowlist adds `QA_VERIFICATION_SAVED`,
`QA_VERIFICATION_CLOSED` and `QA_VERIFICATION_SENT_BACK` to the original five.
Their public statuses are READY_FOR_QA→READY_FOR_QA, READY_FOR_QA→CLOSED and
READY_FOR_QA→IN_PROGRESS respectively. The response still selects only the
same seven metadata fields. QA checks, notes, resolution details, send-back
reasons, actor IDs and assignment events remain excluded. The cursor signs the
updated fixed filter as version 2; the client has static labels for each event.

## Current candidate and checks

Source identity: upstream `dfaed91` plus
[reporter-progress-integration.sha256](reporter-progress-integration.sha256).
The rebased implementation was recorded as `3fd201f` before this documentation
update. All tests below use this merged runtime source. Local PostgreSQL tests
use only the explicitly confirmed Compose test database on port 54330 and
random isolated schemas; browser fixtures use HTTPS and synthetic accounts.
No live provider or hosted deployment was used.

| Command | Result |
| --- | --- |
| `cd frontend && npm run check && npm run build` | 119 tests pass; typecheck, lint and build pass. Vite reports a bundle-size advisory for the merged application, not a build failure. |
| `./gradlew :backend:auth-app:test --tests com.tracedesk.auth.reports.domain.ReportActivityCursorTest --tests com.tracedesk.auth.reports.persistence.ReportActivityPersistenceIntegrationTest --tests com.tracedesk.auth.reports.api.ReportHttpsIntegrationTest --tests com.tracedesk.auth.reports.persistence.ReportPersistenceIntegrationTest --tests com.tracedesk.auth.investigation.persistence.ResolutionPersistenceIntegrationTest --no-daemon --console=plain` with the documented isolated test environment | 9 tests across five suites pass, 0 failures/errors/skips; BUILD SUCCESSFUL in 27s. Includes assignment eligibility/conflicts, QA send-back/re-proposal preservation, activity pagination/revocation, exact API privacy/failures, real QA save/send-back/close metadata, and populated V15→V16 upgrade. |
| `cd frontend && npm run test:e2e -- --project=production --project=real-backend reporter-states.e2e.ts reporter-ai.e2e.ts reporter-real.e2e.ts developer-real.e2e.ts` with `TRACEDESK_REAL_BACKEND_TEST=true` and the same isolated test environment | 21 pass in 1.2 minutes. The combined Developer journey verifies the Reporter sees QA save/send-back status entries while private reasons, notes, check text and resolution details stay absent. Existing QA revision feedback assertions remain intact; 320/390/1440 layout and axe checks pass. |
| `python3 tools/check_foundation.py` and `git diff --check` | Pass. |

## Coordination and review

The original specifier updated only the Reporter spec, acceptance index and
activity threat row for the newly available eight-event contract. The
coordinator resolved and integrated code, migration ordering and regression
checks. The independent reviewer inspected preservation of QA routes/service
methods, explicit report detail/list DTOs, activity metadata projection and
V16 upgrade ordering; it reported no remaining source defects. The coordinator
verified the final 21-browser-test result after the review. These were actual
follow-up handoffs to the original `gpt-6-luna` agents, not new human approvals.

Deployment, hosted CI and a full backend test suite remain outside these local
results. The current history now covers the implemented QA closure/send-back
events; later event types still require an explicit contract/allowlist change.
