# DEV-UI #28 verification and review evidence

- Feature and criterion IDs: DEV-UI #28; TD-DEV-11 through TD-DEV-15.
- Date and author: 2026-11-08, Codex primary implementation agent. Independent
  reviewer: `/root/issue_28_review`, `gpt-6-luna`, review and focused follow-up
  completed 2026-11-08.
- Revision / working-tree identity: base
  `8bf2d49bcda1b8ad90f6965756bf6dc45c5e2069`; follow-up candidate is the
  2026-11-09 working tree, including the new `frontend/src/developer/` and
  `frontend/e2e/developer-states.e2e.ts` files and the corrections below.
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

## Criterion evidence

| Criterion | Evidence and current result |
| --- | --- |
| TD-DEV-11 | **Candidate pass**. `App` uses `workspace=developer` only as an endpoint/UI route hint. `DeveloperWorkspace` renders protected content only after `GET developer-context` returns role `DEVELOPER`; 401 expires the session and 403/404 remains opaque. Unit tests assert the context and route behavior. |
| TD-DEV-12 | **Candidate pass for frontend/API contract**. My work uses the named `assigned-to-me` scope and sends `assignedToMe=true`; All bugs uses the named `all` scope and omits the assignment parameter so the backend returns the complete same-project list. Notes are requested only when the trusted session user UUID equals the detail assignment and are absent for another assignee. |
| TD-DEV-13 | **Candidate pass for implemented unit/component interactions**. Start and note create/edit/delete update UI only after success. Tests cover confirmed start/create/edit/delete and reassignment removal. Draft text and note-create idempotency attempt stay stable for retry; stale/unavailable messages require refresh. Real-backend stale/reassigned browser evidence remains pending. |
| TD-DEV-14 | **Candidate pass at unit/component level**. React text rendering is tested with HTML/script-like report, log and note values; DOM injection is absent. Cross-report note responses are rejected. A shared mutation scope aborts requests and discards late results when report/project/account context changes; a delayed create-response regression test switches reports before resolution. CSRF retry preserves body and idempotency key. Current browser rerun remains unavailable as recorded above. |
| TD-DEV-15 | **Partial / pending required real-backend evidence**. Isolated Chromium with routed synthetic API data passed desktop, 390px and 320px overflow checks, keyboard activation/focus return, two axe scans, safe rendering and reduced motion. The four required isolated-PostgreSQL environment variables were absent, so the same-candidate browser-to-real-backend journey was not run and is not claimed. |

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
"Choose another project" instead of the project-ID form. After rebasing onto
`d93d8fb12733863a626d6106a2547ba657eb51e7`, `npm run check` passed with 9 Vitest files
and 75 tests, `npm run build` passed, and
`npm run test:e2e -- --project=production` passed 21 tests, including the Developer
journey that had stalled above. That browser run uses routed synthetic API data, so
TD-DEV-15 remains pending. See
[workspace discovery evidence](workspace-discovery.md).

## Limitations and completion gates

- Same-candidate real HTTPS browser-to-backend coverage required by TD-DEV-15 is
  unavailable because isolated PostgreSQL settings are absent. The routed browser
  journey does not replace it.
- QA assignment/reassignment UI is issue #32; the current candidate can react to
  backend assignment state but cannot create that production state.
- Clarification, proposed resolution, QA controls, AI, repository ingestion and
  deployment are excluded by #28.
