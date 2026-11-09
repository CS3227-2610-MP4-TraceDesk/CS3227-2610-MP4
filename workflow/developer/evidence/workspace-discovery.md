# TD-WS workspace discovery verification and review evidence

- Feature and criterion IDs: workspace discovery and routing; TD-WS-01 through TD-WS-05.
- Date and author: 2026-11-09, Claude Code primary agent (Opus 5.5) working on the
  existing candidate. Independent reviewer: a separate Claude subagent with no part
  in the implementation. This is independent agent review, not human approval.
- Revision / working-tree identity: reviewed on base
  `8bf2d49bcda1b8ad90f6965756bf6dc45c5e2069` plus the 2026-11-09 working tree, then
  rebased onto `d93d8fb12733863a626d6106a2547ba657eb51e7`. Results below are from the
  rebased tree. The rebase renumbered the migration to
  `V8__workspace_discovery_security_route.sql` because `main` already has a V7; the
  reviewer saw it as V7. Rebase conflict resolution and the `reporter-ai` and
  `reporter-real` browser test updates were not independently reviewed.
- Environment: Windows, Java/Gradle project toolchain, Vitest 5.0.5, Vite 8.3.4,
  Playwright Chromium, local disposable PostgreSQL with a database name ending
  `_test`. `IsolatedPostgres` created and dropped its own random schema. The
  `TRACEDESK_TEST_*` settings were supplied only as process environment variables;
  no application database was used.

## Exact commands and outcomes

| Command | Outcome |
| --- | --- |
| `cd frontend; npm run check` | **PASS** on the rebased tree: typecheck and lint passed; 9 Vitest files, 75 tests, zero failures. |
| `cd frontend; npm run build` | **PASS**: 936 modules transformed. |
| `cd frontend; npm run test:e2e -- --project=production` | **PASS**: 21 passed, using routed synthetic API data, after `reporter-ai.e2e.ts` was updated for the picker. |
| `cd frontend; npm run test:e2e -- --project=development` | **PASS**: 23 passed. |
| `cd frontend; npm run test:e2e -- --project=real-backend` with `TRACEDESK_REAL_BACKEND_TEST=true` | **UNAVAILABLE on this Windows host**: the fixture spawns `./gradlew` and reported that it could not launch, before any browser journey ran. Not counted as a pass; the CI `reporter-real-browser` job runs this project. |
| `.\gradlew.bat --no-daemon --console=plain :backend:auth-app:test --rerun-tasks` with `TRACEDESK_TEST_*` set | **PASS**: 64 tests, 0 failures, 0 errors, 0 skipped, including V1–V8 migration and PostgreSQL integration tests. No backend file changed after this run. |
| `python -X utf8 tools/check_foundation.py` | **PASS**. |
| Same Gradle suite without `TRACEDESK_TEST_*` (earlier run, focused classes) | **Failed closed as designed**: `IsolatedPostgres.open` rejected the missing settings. Not counted as a pass. |
| `git diff --check` | **PASS**. |

The stale-membership regression test was also confirmed to fail with its fix
temporarily removed (13 of 14 `App.test.tsx` tests passed), then passed once the
source was restored.

## Criterion evidence

| Criterion | Evidence and current result |
| --- | --- |
| TD-WS-01 | **Candidate pass.** Actor comes only from session attributes; forged role query and header are ignored in `AuthControllerTest`. PostgreSQL test covers enabled-user, enabled-project and active-membership filtering, role ordering, and the bound at exactly 100 and at 101 rows. |
| TD-WS-02 | **Candidate pass.** Anonymous and expired requests return 401 with no items and a recorded event; persistence and event-sink failures return 503 with no items. Disabled membership, disabled project, disabled user and other-user rows are excluded on real PostgreSQL. V8 allows the `/api/workspaces` event route, and a PostgreSQL test writes that event. |
| TD-WS-03 | **Candidate pass at unit level.** Loading, empty, unavailable with retry, server-role grouping and bounded-list states are tested; URL role hints have no effect. |
| TD-WS-04 | **Candidate pass at unit level.** Reporter and Developer selection reach their context endpoints. Change project returns to discovery, refetches memberships, and keeps the unsaved-work warning. Project, route, memberships and logout errors clear on sign-out, logout 401, discovery 401 and session expiry. QA discovery cannot enter a workspace, and a denied or unavailable selected workspace offers discovery instead of a project-ID form. |
| TD-WS-05 | **Candidate pass for required focused tests and review**, within the limitations below. |

## Independent review

The first review of the working tree reported eight findings:

1. **P1 — resolved:** the V6 route constraint rejected `/api/workspaces`, so every
   401 became a 503 with no event. Mocked controller tests hid this. Fixed by V8
   plus a PostgreSQL event test.
2. **P2 — resolved:** an in-flight discovery Retry could outlive sign-out and show
   the previous account's memberships. Retry now runs through the abortable effect.
3. **P2 — resolved:** the selected project and URL route survived sign-out, session
   expiry and account change. All signed-out transitions now share one reset.
4. **P2 — resolved:** the Reporter list Change project opened the project-ID form
   instead of returning to discovery.
5. **P3 — resolved:** denied and unavailable gates had no way back to discovery.
6. **P3 — resolved:** a logout error could carry over to the next account.
7. **P3 — resolved:** a deep link beyond the first 100 memberships was reported as
   unavailable to the account; it now has its own message.
8. **P3 — resolved:** missing test coverage and this evidence record.

The primary agent separately found and fixed stale memberships surviving a logout
401 before the first review. The same reviewer's follow-up confirmed findings 1–7
and the coverage part of finding 8, and reported no new defect from the fixes. It
reran Vitest (56 of 56) but not the backend or Playwright suites.

The follow-up raised two further P3 items:

- **Resolved after review:** the picker reused the old membership list after Change
  project. It now refetches, with a test assertion. This two-line change landed
  after the follow-up and has **not** been independently re-reviewed; the unit and
  browser checks above were rerun on it.
- **Accepted, not fixed:** an expired session reloaded at `?project=X` keeps that
  route for the next sign-in, because it cannot be told apart from a legitimate
  pre-login deep link. The stored role decides routing and the server reauthorizes.

## Limitations

- No same-candidate browser-to-real-backend journey was run for discovery; browser
  checks use routed synthetic API data.
- Authorization inside `reporter-context` and `developer-context` was outside this
  review; those endpoints are mocked in the frontend tests.
- A session that expires now re-enters through the picker rather than returning to
  its previous project. The reviewer found no spec conflict.
- No human owner or approval is recorded.
