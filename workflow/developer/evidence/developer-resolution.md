# DEV-RESOLUTION #30 verification evidence

- Feature and criterion IDs: DEV-RESOLUTION #30; TD-DEV-21 through TD-DEV-25.
- Date and author: 2026-11-09, Claude Code implementation agent (`claude-opus-5-5`).
  The `gpt-6-luna` preference in the Developer workflow did not apply to this host.
- Independent reviewer: a fresh Claude Code general-purpose agent with no access to
  the implementation reasoning, read-only, 2026-11-09. See the
  [run record](../runs/2026-11-09-dev-resolution-review.md). This is agent review,
  not human approval.
- Revision: base `9f4f28e0d26b2093ee558df0e6bcc5d8390e753a` plus the uncommitted
  working tree on `feat/dev-resolution-30`, SHA-256
  `15f18b9c552f9306c8e652f12b7b1893058e888c742c1a4222910078d5f896e7`. The digest
  covers `git diff HEAD` and the content hashes of untracked files, excluding
  `workflow/`, so recording results does not change it. No commit or CI run exists
  for this candidate yet.
- Environment: Windows 11, Node, Vitest 5.0.5, Playwright Chromium, Java 25, Gradle
  9.6.1 and a disposable PostgreSQL 18 cluster on `127.0.0.1:54331`. Only synthetic
  users, projects, reports and credentials were used; no `.env*` file or existing
  application database was read.

## Scope decisions recorded in the contract

The Developer contributor made both decisions on 2026-11-09:

- Clarification requests (#29, TD-DEV-16 through TD-DEV-20) are left out.
- The QA rejection representation is decided in the Developer contract instead of
  waiting for a QA specification. Its table, reject command and reason display ship
  with the QA reject slice, so they are not part of this candidate.

## Commands and results

Results below are for the post-review candidate identified above.

| Command | Result |
| --- | --- |
| `cd frontend; npm run check` | **PASS**: typecheck and lint passed; 9 Vitest files, 89 tests passed. |
| `cd frontend; npm run build` | **PASS**: production bundle completed. |
| `./gradlew --no-daemon --console=plain :backend:auth-app:test` with the isolated database settings | **PASS**: 83 tests, 0 failed, 0 errors, 0 skipped. The pre-review run first had 1 failure of 82, a wrong `times(1)` expectation in the new controller test where two denials are correct. |
| `npx playwright test --project=production e2e/developer-states.e2e.ts` | **PASS**: 1 passed, routed synthetic API. |
| `npx playwright test --project=real-backend` with the isolated database settings | **PASS**: 3 passed in 1.1m (the Developer journey and both Reporter journeys). Two pre-review attempts failed at launch, as explained below. |
| The full production Playwright project | **NOT RUN** for this candidate; only the Developer journey was run. |

The first two real-backend attempts failed before any browser step with `Reporter
fixture exited before readiness (code 1)`. `cmd` reported that `gradlew.bat` was not
recognized, because the agent shell blocks current-directory command lookup. Clearing
`NoDefaultCurrentDirectoryInExePath` for that run resolved it. This was an environment
problem and required no source change.

## Criterion evidence

| Criterion | Evidence and current result |
| --- | --- |
| TD-DEV-21 | **Candidate pass**. `V10__report_resolutions.sql` and `ProposedResolutionData`: boundary tests at the limit and one over for each field, supplementary code points, controls, unpaired surrogates and blank fields; raw inserts prove each text column's code-point, blank and control checks plus revision range, uniqueness and report/project consistency; a 49-seed test reaches revision 50 and rejects the 51st. Octet limits cannot be exceeded independently of the code-point limits, so they have no separate database test. |
| TD-DEV-22 | **Candidate pass**. `InvestigationStore.propose` locks and rechecks membership, report, assignment, `IN_PROGRESS`, fields and version. Tests cover another Developer, QA, Reporter, wrong project/report, `OPEN`, `CLOSED`, zero and stale versions, and assignment checked before content. |
| TD-DEV-23 | **Candidate pass**. The test asserts one exact audit row, status, version and `updated_at`; a failing audit trigger leaves no revision and no transition. |
| TD-DEV-24 | **Candidate pass with simulated QA**. Synchronized tests cover duplicate same-version submissions, reassignment, membership revocation and a version change blocking on the report lock. Forged fields, nulls and wrong types return `400`. Duplicate propose returns `INVALID_STATE` while `READY_FOR_QA` and `STALE_VERSION` after a simulated rejection. No QA reject command exists yet. |
| TD-DEV-25 | **Candidate pass locally, CI pending**. API tests cover QA read and Reporter denial; persistence tests cover read by all staff with no note content. Browser journeys cover confirmed submission, stale-version text preservation with Refresh, hostile-looking text rendered inert, form removal after submission and an Axe scan of the handoff at 320px. Vitest covers denial removal, history for another Developer, text preserved for each recoverable failure code, both orderings of a history response racing a confirmed handoff, and Enter in the fix reference. A signed cursor round trip and its actor and report binding are covered at the service level. |

## Limitations and completion gates

- CI has not run on this candidate, and nothing is deployed.
- The independent reviewer read the pre-correction diff and ran no tests. It did not
  re-review the corrections listed in the run record.
- Accepted gaps from that review, not corrected here: no injected serialization
  failure or route-level `503` test for this route (TD-DEV-03, TD-DEV-24), no
  duplicate-key body test, a `403` on the history GET shown as temporarily
  unavailable, and handoff feedback rendered above the report rather than beside the
  form with no focus move after submission.
- `[[:cntrl:]]` in V10 is inherited from V4 and V6 and may behave differently under
  another database locale. The local cluster used the C locale; other locales were
  not tested.
- Clarification text remains in the contract under a blanket descoping note rather
  than being removed line by line.
- Drafts live in memory and are discarded without warning on reload or when leaving
  the report, which the contract's isolation rule permits.
- QA has no production screen that reads the handoff; QA access is proven at the API
  and persistence levels.
- The optional one-time server retry on serialization failure is not implemented,
  matching the existing Developer mutations; such failures return `503` with nothing
  committed.
- REPORT-PROGRESS #16 depended on #29 for Reporter replies and needs a team decision.
