# QA verification CI repair note

Date: 2026-11-12. This note supersedes earlier migration-number and pre-merge
verification references for PR #59 after merge base `7f7563c`.

## Repair

- Main added account roles at V11 and registration at V12. PR #59's QA
  verification migration at V11 collided with main's V11 and was renamed V13.
  PR #62's QA assignment migration at V12 collided with main's V12 and was
  renamed V14.
- Audit migrations extend the installed event-code and event-shape constraints
  while preserving registration events.
- Current authorization reads `td_users.account_role` and still requires an
  active project membership. Legacy per-project role snapshots never grant
  authority. Regression coverage includes conflicting legacy roles.

## Verification status

- Frontend: `npm run check` passed (typecheck, lint, and 99 tests); `npm run
  build` passed. These results are local, not remote CI.
- Initial full backend run: 114 tests, 111 passed and 3 failed. The failures
  were fixed-hash fixtures with CRLF working bytes versus LF Git blobs. The
  working bytes were restored from the exact Git blobs; no fixture change is
  committed.
- Focused backend rerun command:
  `./gradlew :backend:auth-app:test --tests '*ReporterLiveBrowserGuardTest' --tests '*ReportAiQualityFixtureTest' --console=plain`
  passed 6 tests after the byte restoration.
- Local real-backend browser run passed all 3 tests.

Remote CI and deployment acceptance are not claimed by this note.

PR #62 subsequently landed first and installed assignment at V14. The pending
verification migration moved to V15. The merged workspace keeps assignment in
Summary and verification in its own tab. The resolution integration test now
applies V14, records a real assignment audit event, and upgrades to V15 before
exercising verification writes.
