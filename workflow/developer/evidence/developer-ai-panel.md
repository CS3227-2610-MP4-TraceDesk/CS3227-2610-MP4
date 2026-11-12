# DEV-AI suggestion panel — verification (issue #31, TD-DEV-29)

Branch `feat/dev-ai-31-panel`, based on `main` at c30d388. Frontend only.

## What was added

- `developerApi.ts`: `requestInvestigationSuggestions` (AbortSignal, CSRF, error mapping).
- `DeveloperAiPanel.tsx`: context picker and disclosure, request/cancel, escaped preview,
  separate editable draft, explicit Save through ordinary note creation, generation-token
  isolation, error states (401/403/unavailable/invalid output/too large).
- Unit tests in `DeveloperAiPanel.test.tsx`, `developerApi.test.ts`, `DeveloperWorkspace.test.tsx`.
- Mocked-route Playwright journey `e2e/developer-ai.e2e.ts` with Axe at 320px, added to the
  `production` project in `playwright.config.ts`.

## Checks (local, Windows, recorded from the Codex run on 2026-11-12)

| Command | Result |
| --- | --- |
| `npm run check` (typecheck, lint `--max-warnings 0`, Vitest) | Pass: 17 files, 146 tests, 0 failed |
| Focused Developer API, panel and workspace tests | Pass: 39/39 |
| Playwright `developer-ai.e2e.ts` | Pass: 1/1 including Axe at 320px; Playwright's web-server teardown lingered afterwards |

## Gaps

- The Codex run stopped on a provider usage limit before writing its summary; this file was
  written from the run's recorded check output.
- No real-backend browser journey yet; the e2e uses mocked routes. CI results are separate.
