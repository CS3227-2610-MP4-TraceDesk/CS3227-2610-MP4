# Engineering foundations verification — 2026-11-04

Scope: TD-ENG. Base revision: `5608d9f` plus the uncommitted changes in this task.
No commit, push, hosted CI run, deployment, or independent review is claimed.

## Results

| Check | Outcome |
| --- | --- |
| Ten frontend source hashes against pinned MP3 commit | Matched; adapted package name, Vite proxy, browser harness and TypeScript project references |
| `npm ci` with Node 24 | Passed; 238 locked packages installed |
| `npm run check` | Passed: TypeScript, ESLint with zero warnings, 2 Vitest cases |
| `npm run build` | Passed; static production bundle generated |
| `npm run test:e2e` | Passed: 1 Chromium smoke test, including axe with zero reported violations |
| `./gradlew :backend:db-test-support:test --tests com.tracedesk.testing.IsolatedPostgresGuardTest --no-daemon` | Passed: 3 guard cases |
| Full `:backend:db-test-support:test` against temporary PostgreSQL 18 | Passed: 4 tests; container stopped afterward |
| `python3 tools/check_foundation.py` | Passed: 27 Markdown documents, 3 skills, 21 provenance entries, 9 transport Java sources |
| CI YAML parse and job inspection | Passed: `foundation`, `frontend`, and `isolated-database` jobs present; hosted run not performed |
| `git diff --check` and authored-text whitespace scan | Passed |

Generated reports are ignored under `frontend/e2e-results/`,
`backend/db-test-support/build/test-results/test/`, and
`backend/db-test-support/build/reports/tests/test/`.

## Failures resolved during setup

- Default host Node 25 does not meet several pinned package engine ranges. Checks
  used bundled Node 24.19.0; CI specifies Node 24.16.0.
- The first lint run flagged a React Fast Refresh warning. The component was
  moved to its own file, then lint passed with zero warnings.
- Playwright initially found no tests because its custom `.e2e.ts` pattern was
  absent. After adding the pattern, it found the test.
- The browser test initially lacked its matching Chromium binary. The Playwright
  browser was installed and the test passed.
- Sandbox restrictions blocked local preview and Docker access; those checks ran
  with authorized local privileges. A temporary PostgreSQL container was removed.

## Scope limits

The browser run covers the static starter only. It does not prove authentication,
CSRF enforcement, project isolation, role actions or backend integration. The
database test helper proves owned-schema selection and cleanup; it has not yet
been used by TraceDesk migrations or repository tests. CI jobs are configured,
but no hosted run is claimed. The threat model lists intended controls and planned
evidence for features that have not yet been implemented.
