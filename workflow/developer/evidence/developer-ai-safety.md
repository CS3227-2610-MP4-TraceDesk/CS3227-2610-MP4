# Developer AI safety evidence

Acceptance item: TD-DEV-30. Base `a135fc9` (main). Tested candidate: branch
`feat/dev-ai-31-safety` at `89c8f71` (PR #85); this evidence update is documentation only.

## Coverage

| TD-DEV-30 item | Test file:method |
| --- | --- |
| Completion exceeds the 30-second gateway deadline: `503 AI_UNAVAILABLE`, no result delivery, failure settlement, and unchanged business/audit rows | `InvestigationAiAccessTest:tdDev30_completionDeadlineReturnsUnavailableWithoutDeliveryAndSettlesUsage` |
| Backend/client cancellation: no result delivery, exactly one cancelled settlement, and unchanged business/audit rows | `InvestigationAiAccessTest:tdDev30_cancelledBackendCompletionSuppressesDeliveryAndSettlesExactlyOnce` |
| Same-actor concurrency: the second in-flight request receives `503 AI_UNAVAILABLE`, does not reach completion, and the first completes and settles normally | `InvestigationAiAccessTest:tdDev30_secondConcurrentRequestForActorIsUnavailableWhileFirstCompletes` |
| Report, log, note, provider reply, account, and other-report canaries are absent from every asserted non-success HTTPS response body and headers | `InvestigationAiHttpsIntegrationTest:realHttpsRevalidatesSessionsSuppressesRevokedResultsSettlesUsageAndLeaksNoCanaries` via `assertError` |

These are fake-provider fixtures. Their passing CI run (below) covers deterministic backend authorization, delivery suppression, accounting calls, row isolation, and response/log canary handling; they do not prove live-provider cancellation, latency, billing, deployment configuration, browser disconnect propagation, or production database behavior.

## Commands and results

- `git diff --check` — pass.
- `.\gradlew.bat --no-daemon :backend:auth-app:test --tests "com.tracedesk.auth.investigation.ai.*"` — unavailable before compilation because the sandbox denied creation of the configured wrapper lock under `C:\.gradle`.
- Offline equivalent using the already-installed Gradle 9.6.1 distribution and a writable copy of the installed dependency cache: compilation passed; test task failed with 66 tests total, 42 passed, 24 failed, 0 skipped. All 24 failures were database-backed investigation tests failing closed during local isolated-database setup with `IllegalArgumentException`; this includes the three new access tests and the two HTTPS integration tests.
- GitHub Actions, Foundation CI run 43308850765 on `89c8f71` (PostgreSQL 18 service): all
  `com.tracedesk.auth.investigation.ai.*` suites ran — 66 tests, 0 failures, 0 errors,
  0 skipped (from the JUnit XML artifact). All 14 PR checks passed.

## Independent reviews

- Endpoint review, 2026-11-12: no P1/P2.
- Endpoint test review: 4 P2 fixed.
- Panel review: 1 P2 fixed.

Review details are in `workflow/developer/runs/codex-token-log.md`.

DAI-01 through DAI-05 scoring and the development-agent injection exercise are deferred to the live validation item.
