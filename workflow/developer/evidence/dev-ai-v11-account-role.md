# DEV-AI account roles after V11

- Date: 2026-11-13 (Asia/Singapore). Author: Codex; self-review only.
- Scope: TD-DEV-27 account-role authorization compatibility; no provider calls,
  deployment, migration edits or changes to other features.
- Candidate: base `b2d73757a92ec5ae159b4aa1816ace24ccf9d44e` plus the changes in
  this working tree: explicit account roles in the shared DEV-AI
  fixture, two regression tests, and source-port/specification clarification.
- Environment: Windows PowerShell, Gradle 9.6.1, Java 25, isolated local PostgreSQL
  database `tracedesk_dev_ai_test` at `127.0.0.1:55437`. The user's four supplied
  `TRACEDESK_TEST_*` environment settings were exported for database runs; no live
  database or provider was used.

## Cause and correction

V11 adds `td_users.account_role` and backfills users that exist during migration;
the DEV-AI fixture migrates an empty schema and then inserts users without that
column, leaving their account roles null. `InvestigationAiSource.membership` in
the database fixture delegates to `InvestigationStore.membership`, whose merged
query already selects `u.account_role` into `ReportRepository.Membership.role()`.
`ReportStore`, ordinary Developer operations and QA stores also use the persisted
account role, so the existing `InvestigationAiAccess` role check correctly rejects
these null-role fixture users.

The fixture now explicitly provisions each synthetic account's intended role.
No existing assertion or expected outcome changed. Added tests prove that a
Developer account with a legacy Reporter membership role is allowed, Reporter/QA
accounts with legacy Developer membership roles are denied before admission,
and changing the account role before dispatch or delivery suppresses the request
or result. The delivery test checks settlement. Existing tests retain trusted
actor, active membership, current-assignee, lifecycle, version, denial-precedence
and business/audit invariants. The source port and owning specification clarify
ADR 002's account-role rule without changing production authorization logic.

## Commands and results

Run from `D:\CS3227-wt\final` on `feat/dev-ai-31-core`. In these commands `$gradle`
denotes the local Gradle 9.6.1 wrapper distribution.

```powershell
# Before the correction:
& $gradle --gradle-user-home .gradle/dev-ai-cache :backend:auth-app:test --tests '*InvestigationAi*' --tests '*ReporterLiveBrowserGuardTest' --no-daemon --console=plain
# After the correction:
& $gradle --gradle-user-home .gradle/dev-ai-cache :backend:auth-app:test --tests '*InvestigationAi*' --no-daemon --console=plain
# Full auth-app suite (the user's supplied command):
& $gradle --gradle-user-home .gradle/dev-ai-cache :backend:auth-app:test --no-daemon --console=plain
# Remaining backend modules, forced to execute rather than use up-to-date results:
& $gradle --gradle-user-home .gradle/dev-ai-cache :backend:ai-gateway:test :backend:ai-transport:test :backend:db-test-support:test --rerun-tasks --no-daemon --console=plain
git diff --check
```

- Before correction: 59 tests, 45 passed, 14 failed, 0 skipped. Ten failures are
  the nine listed Access cases plus A14; four are Reporter guard cases, including
  temporary-directory access failures under this sandbox.
- Focused DEV-AI after correction: **57 passed, 0 failed, 0 skipped**.
- Full auth-app suite: **179 passed, 7 failed, 0 skipped** (186 total), including
  all 57 DEV-AI tests passing. Failures: the four Reporter guard cases reproduced
  on main; the known CRLF-sensitive
  `ReportAiQualityFixtureTest.v2FixtureUsesTheSameFourInputsAsHistoricalV1WithoutChangingItsFile`;
  and `QaEvaluationApprovalTest.approvalCanBeClaimedOnlyOnceAcrossConcurrentAttempts`
  plus `claimRejectsBytesThatDifferFromTheValidatedApproval`, both failing JUnit
  temporary-directory cleanup with `AccessDeniedException` under the sandbox.
  The QA failures were not separately compared on main and were left unchanged.
- Remaining backend modules: gateway **11 passed**, transport **27 passed**,
  database test support **4 passed**; **0 failed, 0 skipped** in each. An initial
  invocation was up to date; the recorded counts are from the forced execution.
- Combined full backend: **221 passed, 7 failed, 0 skipped** (228 total).
- `git diff --check`: passed. No live-provider tests or deployment checks ran.
- XML/HTML reports: `backend/*/build/test-results/test/` and
  `backend/*/build/reports/tests/test/`; focused XML copied to ignored
  `.gradle/dev-ai-verification/focused/` before the full run.

## Reporter guard baseline

To obey the restriction against touching other worktrees, exported the local
`origin/main` revision `f1350236b1b612747de02d0126e547fc05f8d062` with `git archive`
into `.gradle/dev-ai-main-baseline/` inside this worktree, omitting environment
files. This is an ignored source snapshot, not a Git worktree; the active branch
was never switched. Guard implementation and tests have no diff from main.

```powershell
& $gradle -p .gradle/dev-ai-main-baseline --gradle-user-home .gradle/dev-ai-cache :backend:auth-app:test --tests '*ReporterLiveBrowserGuardTest' --no-daemon --console=plain
```

Main baseline: **0 passed, 4 failed, 0 skipped**, reproducing the same guard
assertion locations (`:96`, `:73`, `:33`) and temporary-directory
`AccessDeniedException` (including cleanup failure in the fourth test) as this
branch. Left Reporter behavior/tests unchanged. Baseline XML is under
`.gradle/dev-ai-main-baseline/backend/auth-app/build/test-results/test/`.

These are synthetic backend checks only. They do not prove browser behavior,
live provider quality, production assignment journeys or deployment readiness.

## Working-tree identity and commit limitation

SHA-256 of the changed source/specification files (working-tree bytes):

| File | SHA-256 |
| --- | --- |
| `InvestigationAiSource.java` | `707e590097c0dd30c5f1d5daae68bfcf86bf2aa9c3b1274540c3b3af721587c3` |
| `InvestigationAiAccessTest.java` | `b34aa2b49c8da12bcb18ab41599eb207f8f2ee63ad0f566bf45ca95e832830cf` |
| `workflow/specs/developer-ai.md` | `c77275d975d448b5c979372610fc6952a6d0a18a1559732d070e017cba469fc8` |

Attempted to stage the four changed files and commit with
`fix(developer): resolve DEV-AI role from account role after V11`. Both staging
and commit failed because the filesystem sandbox denies creating
`D:/CS3227-2610-MP4/.git/worktrees/final/index.lock`, outside the writable root.
No commit was created; the changes remain unstaged on `feat/dev-ai-31-core`.
No branch switch, push or operation on another worktree occurred.
