# QA assignment CI repair note

Date: 2026-11-12. This note supersedes earlier migration-number and pre-merge
verification references for PR #62 after merge base `7f7563c`.

## Repair

- Main added account roles at V11 and registration at V12. PR #59's QA
  verification migration at V11 collided with main's V11, so it was renamed
  V13. PR #62's QA assignment migration at V12 collided with main's V12, so it
  was renamed V14. Audit migrations now extend the installed event-code and
  event-shape constraints while preserving registration events.
- Authorization continues to use `td_users.account_role`; an active project
  membership is still required. Legacy per-project role snapshots are not an
  authorization source. Regressions cover accounts with conflicting legacy
  roles.
- The assignment UI test fixture now supplies a QA account role, project
  directory response, and the accessible `Open QA Assignment Project` button.

## Verification status

- Frontend: `npm run check` passed (typecheck, lint, and 100 tests); `npm run
  build` passed. These results are local, not remote CI.
- Initial full backend run: 115 tests, 112 passed and 3 failed. All three were
  fixed-hash fixtures whose working bytes had CRLF line endings while the Git
  blobs use LF. The working bytes were restored from the exact Git blobs; no
  fixture change is committed.
- Focused backend rerun:
  `./gradlew :backend:auth-app:test --tests '*ReporterLiveBrowserGuardTest' --tests '*ReportAiQualityFixtureTest' --console=plain`
  passed 6 tests after the byte restoration.
- `TRACEDESK_REAL_BACKEND_TEST=true npm run test:e2e -- --project=real-backend`
  passed all 3 real-backend browser journeys against a disposable PostgreSQL
  database after the repair. This is local browser evidence, not deployment acceptance.

Remote CI and deployment acceptance are not claimed by this note.
