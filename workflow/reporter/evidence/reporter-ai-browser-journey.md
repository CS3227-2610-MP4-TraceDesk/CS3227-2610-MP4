# Reporter browser verification preparation

Date: 2026-11-09. Branch `codex/shared-ai-enablement`, base
`f20a2b86feb075558c5ca096ca52f5e7f0176b7a` plus uncommitted changes.
Candidate source identity (SHA-256 of sorted manifest entries recomputed from
actual bytes): `8c7af83e2ee24fbf59f4e049de0fa879d99f99e5c44e764c568499c755964b30`.
The [candidate manifest](ai-enablement.sha256) identifies the reviewed inputs;
this evidence document is outside that manifest to avoid a self-reference.

Scope: AI-LIVE-01/03 preparation and TD-RPT-26–30 regression. The user deferred
quality fixes; 11/24 v2 semantic failures remain recorded. Model limitations are
an unverified explanation. No live provider request occurred during this phase.

## Implementation and review

The test-only fixture requires a fresh strict approval before database/catalog
access, pins synthetic context, model, prompt, expiry and candidate source, and
consumes approval atomically. One global context gate admits at most one
completion across actors/source kinds. Default tasks force live mode off.
The attached browser driver checks unavailable AI, inert escaped preview,
explicit selected title apply, versioned PATCH and preservation of other fields.
Live artifacts contain only fixed statuses, boolean checks and aggregate usage;
traces, screenshots, video and content-bearing reports are disabled.

The ignored local launcher builds the current frontend in a credential-free
child environment, checks actual Git/source identity and owns fixture cleanup.
The backend alone receives the provider key. See the portable
[operator guide](../../../docs/reporter-live-browser.md) and
[predefined plan](../experiments/reporter-ai/browser-live-plan.md).

Actual specialized agents: `/root/browser_live_spec`, `/root/browser_live_backend`
and `/root/browser_live_frontend`; independent read-only reviewer
`/root/prompt_v2_reviewer`, all gpt-6-luna per contributor workflow.
Root performed integration and checks. Review prompted actual-byte source
binding and a fresh frontend build before backend startup. The reviewer
retracted the unsupported missing-Git-revision finding after verifying the
existing comparison; final review reported no material findings. Review did
not execute tests or access secrets/provider/database resources.

## Verification

Environment: macOS, Java 25, Gradle wrapper, Chromium/Playwright, disposable
PostgreSQL 18 on loopback 55418. Only owned randomized test schemas were used.
Synthetic test credentials below belong exclusively to the disposable fixture.
The backend checks ran with:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
export TRACEDESK_TEST_DATABASE_CONFIRM_ISOLATED=true
export TRACEDESK_TEST_JDBC_URL=jdbc:postgresql://127.0.0.1:55418/tracedesk_ai_enablement_test
export TRACEDESK_TEST_DATABASE_USER=tracedesk_test
export TRACEDESK_TEST_DATABASE_PASSWORD=synthetic_ai_enablement_password
./gradlew :backend:auth-app:test --tests com.tracedesk.auth.api.ReporterLiveBrowserGuardTest --tests com.tracedesk.auth.reports.ai.ReportAiTest --tests com.tracedesk.auth.reports.ai.ReportAiPersistenceIntegrationTest --no-daemon --console=plain
```

Final focused backend result: **11 passed**, zero failures/errors/skips, 14s.
Four guard tests cover strict approval, source tampering, concurrent claim and
one-use context admission; six role tests and one isolated persistence test
include stale/revoked delivery. Initial compilation exposed an ambiguous JDBC
lambda and an obsolete checked IOException catch; both were corrected before
this passing run.

From `frontend`, `npm run check` and `npm run build`: passed type checking,
lint, **47 unit tests** and production build. A caught-error lint finding was
corrected with a narrow documented suppression to avoid retaining raw causes.

From the repository root:

```sh
python3 workflow/reporter/runs/run-ai-live-browser-42.py --synthetic
```

Two attached dry runs passed; the final run rebuilt the frontend first.
Its ignored `browser-synthetic-5cfab61920e6408c8ba83d1911298846/browser-result.json`
records `APPLIED_TITLE` and all nine boolean checks true. This is synthetic
provider evidence, with no live completion or live ledger claim.

With the same isolated database settings, from `frontend`:

```sh
env -u SOCLAAS_API_KEY TRACEDESK_REAL_BACKEND_TEST=true npm run test:e2e -- --project=real-backend
```

Final existing-browser regression: **2/2 passed**, 31.2s. Baseline before changes:
2/2 passed, 30.4s. The final run exercised real HTTPS login/session/CSRF,
PostgreSQL reporting, denied access and explicit AI apply using a fake provider.
Root visually inspected the actual preview-region desktop and 320px screenshots
under ignored `frontend/e2e-results/reporter-real/`; text remained escaped and
wrapped, with unchecked title and disabled apply until selection. Earlier
whole-page captures showed the form top, so region captures were added.

## Remaining gates

AI-LIVE-03 remains pending an explicitly approved one-completion live journey,
maximum 9,200 resource units, fixed expiry, stopping on failure with no retry.
No applicable title is a stop without apply, not a fabricated pass. Prior
24-attempt approvals are consumed and do not authorize this operation.
Developer/QA validation and actual deployment remain pending. Hosting remains
undecided; the portable deployment guide is available. No deployment occurred.
Verification was performed before committing these changes. The user subsequently
selected “Keep it offline for now”; no live approval was granted. Unrelated
presentation and local Flyway notes were not modified. The change exceeds 1,000
lines and is split into logical commits at the user’s request.
