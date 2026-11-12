# QA assignment issue #32 verification evidence

- Date: 2026-11-12.
- Branch: `feat/qa-assignment-32`; base: `d27a015`.
- Implementation SHA-256: `b2300d2beb4ff7641fb832569a7c21451d5aa13ad9bbdf49ac433fc620c5fc0f`. The digest is over the sorted changed frontend/backend names encoded as UTF-8, each followed by NUL, then raw file bytes and NUL. It includes the untracked `QaAssignment.tsx` and V12 migration. Workflow evidence and this index update are excluded.
- Environment: local frontend and backend, using synthetic fixtures and a dedicated disposable PostgreSQL 18 Docker database. No live AI calls, production database, deployed checks, remote CI or human acceptance are claimed.

## Verification results

| Command or check | Result |
| --- | --- |
| `cd frontend; npm run check` | **PASS**: typecheck and lint passed; 9 files and 93 tests passed. |
| `cd frontend; npm run build` | **PASS**: production build completed. |
| `./gradlew :backend:auth-app:test --tests '*ReportHttpsIntegrationTest' --tests '*ReportPersistenceIntegrationTest' --tests '*InvestigationPersistenceIntegrationTest' --tests '*ReportDataTest' --console=plain` | **PASS**: 10 tests (ReportHttps 1, ReportData 5, ReportPersistence 3, InvestigationPersistence 1), 0 failed, 0 skipped, using the dedicated disposable PostgreSQL 18 Docker database. |
| Manual browser journey against isolated real backend (`ReportRealBackend`, port 4180) | **PASS**: QA assigned, reassigned and cleared a report, then reloaded. Developer opened My work; the handoff showed version 5, assigned to the logged-in Developer, with Start investigation available. Keyboard dropdown interaction was exercised. No horizontal document overflow at 320px or 390px. Screenshots outside the repository: `E:/MP4-TRACEDESK/.qa32-checks/assignment-browser.jpg` and `E:/MP4-TRACEDESK/.qa32-checks/developer-handoff.jpg`; synthetic fixtures only. |

The frontend unit test uses mocked fetch and verifies CSRF handling, stale-version feedback, reason preservation, and assign/clear flows. Backend and domain coverage includes QA versus Developer access, cross-project and ineligible targets, candidate paging, assignment/reassignment/clear, Unicode reason bounds (including the focused `validAssignmentReason` cases), stale and concurrent Developer starts, audit failure rollback, disabled historical assignee denial and QA recovery, and CLOSED reports.

## Acceptance criteria

| Criterion | Evidence and result |
| --- | --- |
| TD-ASG-01 | **Local candidate pass.** API and persistence checks cover QA-only candidate reads and writes, project/report scope, eligible Developers, cross-project and ineligible targets, and paging. |
| TD-ASG-02 | **Local candidate pass.** Tests cover versioned assign/reassign/clear, required reason validation, Unicode and length boundaries, stale versions, and invalid lifecycle states. Frontend checks cover assign and clear flows. |
| TD-ASG-03 | **Local candidate pass.** Persistence coverage verifies version and audit updates are atomic, audit failure rollback, and concurrent/stale developer-start handling. |
| TD-ASG-04 | **Local candidate pass.** Tests cover denial to disabled historical assignees and recovery by QA. The real-backend browser journey verifies the assigned report appears in Developer My work with Start investigation available. |
| TD-ASG-05 | **Local candidate pass.** Focused API, persistence, domain, frontend and real-backend browser checks are listed above. CI remains pending. |

## Review and limitations

- An independent read-only backend review by `/root/slides_review` recorded no actionable findings. It did not independently review the frontend or the later-added test assertions.
- Initial verification had two test-only issues: the new persistence test needed a checked-exception declaration, and three frontend selectors did not match the visible placeholder. Both were corrected; neither required a feature implementation change.
- Local implementation verification is recorded; CI is pending. No deployment or production acceptance is claimed.
