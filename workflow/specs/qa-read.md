# Authenticated QA queue and Summary

Issue #50 uses the existing workspace discovery, report, and resolution read
contracts. The QA preview remains a separate development entry point.

- **TD-QAR-01** Open QA through a discovered QA project membership. Read the
  selected project's READY_FOR_QA queue with the report API and its cursor.
- **TD-QAR-02** Show the original report and Developer resolution revisions in
  the existing Summary layout. Render fields and fix references as plain text.
- **TD-QAR-03** Provide loading, empty, retry and denied states. Clear selected
  data on project or session change and disregard canceled responses.
- **TD-QAR-04** Verify a real Developer handoff and project access boundaries.

Verification writes, templates, comments, AI, diffs, and assignment are separate.

## Local verification

Candidate working tree on `feat/qa-read-50` based on `fb4ef4b`.

- `npm run check` passed type checking, lint, and 92 unit/component tests.
  The QA regression covers a failed resolution read, retry, plain-text fix
  references, and denied access after a project switch.
- `npm run build` and `:backend:auth-app:compileTestJava` passed.
- The existing `developer-real.e2e.ts` journey passed with the Spring fixture
  and isolated PostgreSQL 18 on loopback. It verified Developer handoff,
  signed-in QA queue and Summary, private-note exclusion, cross-project
  resolution denial, 320px and 390px layout, and an axe scan of QA Summary.
- The standard browser suite passed all 44 tests after redirecting Playwright
  artifacts to a drive with available space.
