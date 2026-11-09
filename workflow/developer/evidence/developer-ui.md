# DEV-UI #28 verification and review evidence

- Feature and criterion IDs: DEV-UI #28; TD-DEV-11 through TD-DEV-15.
- Date and author: 2026-11-08, Codex primary implementation agent. Independent
  reviewer: `/root/issue_28_review`, `gpt-6-luna`, review and focused follow-up
  completed 2026-11-08. A second independent agent, `/root/independent_review`,
  reviewed the real-backend follow-up on 2026-11-09; its findings and resolutions
  are recorded below.
- Revision / working-tree identity: base
  `237db2a7c3856e41ab4f1766536e5d34df68f4f1`; follow-up candidate is the
  2026-11-09 working tree. The corrected real-backend captures identify the
  source/test/config candidate as SHA-256
  `8dd4062947ff04332af152d18c29db5efc45f2a4678a779dbaed52bc196070a8`;
  their manifest includes the untracked Developer browser test by path and content.
- Environment: Windows, Node 24.16.0, npm lockfile install, Vitest 5.0.5,
  Vite 8.3.4, Playwright 1.64.1 Chromium, Java/Gradle project toolchain.

## Exact commands and outcomes

| Command | Outcome |
| --- | --- |
| `cd frontend; npm run check` | Follow-up **PASS** on 2026-11-09: typecheck and lint passed; 7 Vitest files, 36 tests passed, zero failures. The two added tests cover delayed-result isolation and confirmed note edit/delete behavior. |
| `cd frontend; npm run build` | **PASS**: 825 modules transformed; production bundle completed. |
| `cd frontend; npm run test:e2e -- --project=production e2e/developer-states.e2e.ts` | Historical 2026-11-08 candidate rerun **PASS** after correcting row focus. The 2026-11-09 follow-up rerun was **UNAVAILABLE/stalled** during configured web-server startup before test execution and was terminated; no current browser pass is claimed. |
| `cd frontend; npx vitest run src/developer/developerApi.test.ts src/developer/DeveloperWorkspace.test.tsx` | Follow-up **PASS**: 2 files, 10 focused tests, including an All bugs request without `assignedToMe`, confirmed edit/delete, and a delayed create response discarded after report switching. |
| `./gradlew --no-daemon --console=plain :backend:auth-app:test --tests "com.tracedesk.auth.investigation.api.InvestigationControllerTest"` | Sandboxed attempt was **UNAVAILABLE/stalled** and was stopped. Outside-sandbox rerun **PASS**: `BUILD SUCCESSFUL` in 7s. |
| `git diff --check` | **PASS**, no whitespace errors. |
| `cd frontend; npm run check` (post-review fixes) | **PASS** on 2026-11-09: typecheck and lint passed; 9 Vitest files and 75 tests passed. |
| `cd frontend; npm run build` (post-review fixes) | **PASS** on 2026-11-09: 936 modules transformed and the production bundle completed. |
| `cd frontend; npm run test:e2e -- --project=production e2e/developer-states.e2e.ts` (post-review fixes) | Test **PASS** on 2026-11-09 in 2.1s. The routed journey also asserted that dark-theme Sign out resolves to `--td-ink` and produced no Axe violations. The runner remained alive after reporting the pass because of the known configured web-server teardown issue and was stopped; no test failure occurred. |
| Fresh corrected real-backend journey | **PASS** on 2026-11-09: final stable-digest rerun passed 1/1 Playwright test in 21.8s (journey 8.8s) through Chromium, the Spring fixture and disposable PostgreSQL 18 on loopback port 54331. The first sandboxed attempt failed before fixture startup because Gradle was redirected to unwritable `C:\.gradle`; rerunning with the normal writable user Gradle cache resolved that environment issue. |

## Criterion evidence

| Criterion | Evidence and current result |
| --- | --- |
| TD-DEV-11 | **Candidate pass**. `App` uses `workspace=developer` only as an endpoint/UI route hint. `DeveloperWorkspace` renders protected content only after `GET developer-context` returns role `DEVELOPER`; 401 expires the session and 403/404 remains opaque. Unit tests assert the context and route behavior. |
| TD-DEV-12 | **Candidate pass for frontend/API contract**. My work uses the named `assigned-to-me` scope and sends `assignedToMe=true`; All bugs uses the named `all` scope and omits the assignment parameter so the backend returns the complete same-project list. Notes are requested only when the trusted session user UUID equals the detail assignment and are absent for another assignee. |
| TD-DEV-13 | **Candidate pass**. Start and note create/edit/delete update UI only after success. Unit tests cover confirmed start/create/edit/delete and reassignment removal. The corrected real-backend journey passed its explicit requirement that the prior private-note body and denied unsaved edit disappear immediately after reassignment denial, before session invalidation. Draft text and note-create idempotency attempt stay stable for retry in unit coverage. |
| TD-DEV-14 | **Candidate pass at unit/component level**. React text rendering is tested with HTML/script-like report, log and note values; DOM injection is absent. Cross-report note responses are rejected. A shared mutation scope aborts requests and discards late results when report/project/account context changes; a delayed create-response regression test switches reports before resolution. CSRF retry preserves body and idempotency key. Current browser rerun remains unavailable as recorded above. |
| TD-DEV-15 | **Candidate pass locally**. The corrected same-candidate isolated run covers assigned/unassigned access, stale start, confirmed start/notes, immediate protected-content removal after reassignment denial, session expiry, safe rendering, keyboard focus return, 1440/390/320 layouts, light/dark Axe scans and reduced motion. Its digest manifest includes the untracked Developer test. CI confirmation remains pending. |

Allowed/denied coverage includes Developer context, assigned versus another-assignee
note visibility, opaque project/report failure handling, reassignment removal and
session expiry routing. Existing DEV-DATA controller tests passed. Persistence,
transaction, audit and concurrency coverage remains owned by DEV-DATA evidence.
No AI interface or provider work is in this slice.

## Independent review

The independent reviewer inspected the actual current-tree diff, source, tests,
criteria and this evidence. It reported:

1. **P2**: context Retry reused the same project ID and therefore did not trigger
   the project-ID keyed effect. Fixed by explicitly calling `loadContext()` for a
   same-ID submission; a focused test asserts two context calls and recovery.
2. **P2**: a notes 403 could leave note/start controls visible if the defensive
   detail refresh also failed. Fixed by treating `notesState='denied'` as a
   conservative no-controls gate for the note panel and Start action; the test
   now makes the refresh fail and asserts the composer is absent.
3. **Scope/criteria gap**: the general UI contract described clarification and
   resolution activity while #28 excludes both. Reconciled in the owning contract:
   #29 owns clarification activity and #30 owns resolution activity; #28 does not
   claim a completed activity view.

The same reviewer performed a focused follow-up and confirmed all three items
resolved with no remaining behavior finding on those points. It also identified
the stale 33-test evidence count, corrected here to 34. Review was independent
agent review, not human approval. Residual limitation: TD-DEV-15 real-backend
browser evidence remains pending.

## Follow-up workaround review and corrections (2026-11-09)

A later user-requested review by the primary Codex agent found two implementation
defects that the earlier independent review missed. This follow-up is not relabeled
as a separate independent-agent approval:

1. **P1 — resolved:** All bugs incorrectly sent `assignedToMe=false`, which means
   "not assigned to me" and excluded the current Developer's assigned reports.
   The client now uses named `assigned-to-me` and `all` scopes; `all` omits the
   query parameter. A unit assertion and the routed-browser test definition cover
   the exact URL; the current browser rerun limitation is recorded above.
2. **P1 — resolved:** late start/note mutation responses could update a different
   report after navigation. Selection changes now abort the shared mutation
   controller and advance a scope token; every success, failure and denied-state
   recovery checks that token before applying state. A delayed note-create test
   switches reports before the response and proves the old result is discarded.
3. **P2 — resolved:** component coverage now exercises confirmed edit and delete,
   and the evidence no longer claims that the older start/create-only tests covered
   those paths.
4. **P3 — resolved:** the handoff no longer lists dependency-recovery ignore rules;
   the unnecessary backup artifact was deleted and those configuration edits were
   reverted.

Current focused and full unit checks pass. The current Playwright rerun did not
start its test because web-server startup stalled, so TD-DEV-15 remains pending.

## Workspace-discovery follow-up (2026-11-09)

Later workspace-discovery work changed this candidate: when the Developer workspace
is opened through the picker, its loading, denied and unavailable gates offer
"Choose another project" instead of the project-ID form. After merging
`c7369504d769f561106584240e3b8de596c09494`, `npm run check` passed with 9 Vitest files
and 75 tests, `npm run build` passed, and
`npm run test:e2e -- --project=production` passed 21 tests, including the Developer
journey that had stalled above. That browser run uses routed synthetic API data, so
TD-DEV-15 remains pending. See
[workspace discovery evidence](workspace-discovery.md).

## Isolated real-backend browser follow-up (2026-11-09)

The pending TD-DEV-15 journey now runs through Chromium → HTTPS Vite same-origin
proxy → the real Spring application → a fresh owned schema in a disposable
PostgreSQL 18.6 cluster on `127.0.0.1:54331`. It uses synthetic users, projects,
reports and credentials only. No `.env*`, existing application database or
production data was read.

The shared browser fixture now launches through `gradlew.bat` on Windows, keeps
Developer report ownership isolated from the Reporter test account, and exposes
bounded local test controls for report-version advancement, reassignment and
Developer-session invalidation. `frontend/e2e/developer-real.e2e.ts` uses no API
route mocking. It verifies My work versus All bugs, an unassigned report without
private actions, inert hostile-looking report/note text, a real stale start and
refresh, confirmed start and note creation, reassignment denial and protected
action removal, and session expiry. The corrected journey also passes immediate
removal of the private note body and denied edit. It checks keyboard sidebar close/focus return,
1440×900, 390×844 and 320×844 without horizontal overflow, reduced motion, and
light/dark Axe results after theme transitions settle.

The run exposed two fixture/test defects and one product accessibility defect:

1. Developer fixture reports initially belonged to the Reporter fixture user and
   broke the Reporter empty-list precondition. A separate synthetic report owner
   now preserves both journeys' isolation.
2. Several locators assumed assignment text was a standalone node or that Settings
   returned directly to detail. They now follow the actual accessible UI structure.
3. The dark Settings `Sign out` secondary-outline text had insufficient contrast.
   The control now uses the centralized `var(--td-ink)` semantic token; the
   historical Axe run completed after the shared colour transition settled.

| Command | Outcome |
| --- | --- |
| `cd frontend; npm run check` | **PASS**: typecheck and lint passed; 9 Vitest files, 75 tests passed, 0 failed. |
| `cd frontend; npm run build` | **PASS**: 936 modules transformed and the production bundle completed. |
| `.\gradlew.bat --no-daemon --console=plain :backend:auth-app:testClasses` | **PASS**: fixture and backend test sources compiled, `BUILD SUCCESSFUL`. |
| `cd frontend; $env:TRACEDESK_REAL_BACKEND_TEST='true'; $env:TRACEDESK_TEST_DATABASE_CONFIRM_ISOLATED='true'; $env:TRACEDESK_TEST_JDBC_URL='jdbc:postgresql://127.0.0.1:54331/tracedesk_test'; $env:TRACEDESK_TEST_DATABASE_USER='tracedesk_test'; $env:TRACEDESK_TEST_DATABASE_PASSWORD='synthetic_test_password'; npm run test:e2e -- --project=real-backend e2e/developer-real.e2e.ts` | **PASS**: the final Developer journey passed in 31.3s after the contrast correction. |
| Same settings with `npm run test:e2e -- --project=real-backend` | **PASS**: 3/3 real-backend tests in 1.0m (Developer plus both existing Reporter journeys). |

Current capture metadata under `frontend/e2e-results/developer-real/` records Chromium,
the base revision and corrected candidate digest,
the three viewports, light theme, reduced-motion state and zero horizontal
overflow. The corrected helper records a deterministic manifest
of the tracked source/test/config diff plus every untracked, non-ignored candidate
file; workflow records are excluded so recording a result does not change
the measured candidate. Screenshots accompany the JSON metadata. The CI real-backend project
and artifact upload now include the Developer journey, but no CI run on this
working tree is claimed.

## Independent real-backend follow-up review resolution (2026-11-09)

`/root/independent_review` reviewed all staged, unstaged and untracked changes and
reported three findings. All three are resolved in the current working tree:

1. **P1 — resolved and verified:** candidate capture now includes the
   untracked Developer test through a path-and-content SHA-256 manifest. It covers
  staged and unstaged source/test/config changes relative to `HEAD` and excludes
  ignored files and workflow records.
2. **P1 — resolved and verified:** reassignment now asserts that both the
   previously rendered private note and the denied edit text disappear immediately,
   before session invalidation or reload.
3. **P2 — resolved:** the Sign out contrast override uses the centralized
   `var(--td-ink)` token through `.developer-sign-out`, rather than duplicated
   light/dark literals. The routed Developer journey confirms that the computed
   dark-theme button colour equals the semantic ink colour and has no Axe finding.

Post-review `npm run check`, `npm run build`, the routed browser journey and the
corrected real-backend journey passed as recorded above.

## Limitations and completion gates

- TD-DEV-15 has a corrected local isolated-PostgreSQL pass. CI confirmation remains
  pending; no deployment or production acceptance is claimed.
- QA assignment/reassignment UI is issue #32; the current candidate can react to
  backend assignment state but cannot create that production state.
- Clarification, proposed resolution, QA controls, AI, repository ingestion and
  deployment are excluded by #28.
