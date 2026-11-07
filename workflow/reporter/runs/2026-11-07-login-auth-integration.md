# Login authentication integration run

Status: frontend implementation complete; end-to-end acceptance partial.

- Feature: Connect the login UI to real backend authentication.
- Coordinator/implementer: `/root` (GPT-6; branch `codex/login-auth-integration`).
- Base revision: `1d3df86413383f23627bf5c7d2cecc5cd2ab033b`.
- Human owner: not supplied.
- Authorization: user requested implementation on 2026-11-07, following the
  proposed feature scope. The approved authentication API contract is
  `workflow/specs/auth-membership.md`; this run adds frontend integration
  criteria without adding a new identity or authorization mechanism.
- Specifier: `/root/login_auth_specifier` (GPT-6 Luna medium), read-only handoff
  on 2026-11-07 around 04:23 UTC. It reviewed the login UI/auth contracts and
  relevant API/frontend sources; no files edited. It identified the stale
  cookie-based CSRF helper, fake local role preview, and local HTTPS/proxy
  boundary. Selected-file bytes, tool calls and tokens for that agent are not
  available from its host telemetry; no retries were reported.
- Specification: `workflow/specs/login-auth-integration.md`, linked from the
  acceptance index. It defines seven observable criteria for session restore,
  login, logout, errors, demo shortcuts, local HTTPS and end-to-end verification.
  The existing login presentation contract now points at the integration spec.
- Threats: credential/session/CSRF leakage, forged roles/identity, stale
  client state, insecure transport, malicious API errors, and unavailable auth.
  Assert no signed-in state without API confirmation, no role claims in requests,
  no secret in storage/logs/URLs, secure cookie flags, and no fake success.
- Out of scope: registration, account provisioning API, production seeds,
  role-specific routes, bug operations, AI/provider work and deployment.
- Implementation owner: coordinator; one frontend owner for API client, App and
  login state, with backend contract unchanged. Independent reviewer
  `/root/login_auth_review` (GPT-6 Luna) inspected the actual diff and tests on
  2026-11-07. It found logout accepted arbitrary 2xx responses; corrected to
  exact 204 with a 202 regression test. It also found browser tests mock the API
  and do not establish a real UI-to-backend login with isolated accounts. That
  remains outstanding.
- Planned checks: Vitest API/UI tests, frontend `npm run check` and `npm run
  build`, Playwright flows at desktop/mobile with accessibility checks, and the
  existing HTTPS backend auth suite using its isolated synthetic database.
  Required checks may be adjusted only with a recorded reason.
- Resource bound: up to 45 minutes and 35 observable tool calls for the bounded
  implementation/review cycle; no token budget is available. Stop earlier if
  a material API/security decision is found or after three materially similar
  failed attempts.
- Measurement: compare the candidate against the current login UI at the base
  revision using the same demo form cases, API response fixtures, browser
  viewport/theme, and acceptance rubric. Record elapsed time, observable calls,
  selected-file bytes, test outcomes and reviewer corrections; hidden context,
  tokens and costs remain unknown. The focused-versus-broader process study is
  a separate deferred MP4 milestone; no efficiency claim will be inferred.
- Injection exercise: not applicable because this slice has no AI prompt/context
  path; it remains required before the first AI slice.

## Handoffs and implementation evidence

Append actual implementation/review timestamps, owned files, handoffs, commands,
results, corrections and remaining limitations here. Do not record estimated
tokens or imagined approvals.

## Implementation and verification

- Implementer: `/root` (GPT-6), sequential after specifier handoff. Changed the
  frontend auth client, session-driven app/login states, HTTPS Vite proxy and
  browser/unit checks; updated local docs and acceptance/evidence records.
- `cd frontend && npm run check`: PASS; typecheck/lint and 12 Vitest tests.
- `cd frontend && npm run build`: PASS.
- Initial `cd frontend && npm run test:e2e`: PARTIAL; 12/13 passed. The
  light-theme axe scan ran during the theme transition and measured blended
  colors. Follow-up correction: the test waits for the input background to
  reach its settled light value before axe scans. Final run: PASS, all 13/13.
- `./gradlew :backend:auth-app:test`: PASS (Gradle reports tasks up-to-date).
- `git diff --check`: PASS.
- Live browser-to-backend authentication remains unverified: API calls in
  Playwright are mocked, and account provisioning is out of scope. Backend
  auth tests run separately with their isolated synthetic database. See
  `workflow/reporter/evidence/login-auth-integration.md`.
- At the time of the original implementation run, no commit had yet been
  created. The feature was later committed as `64f3d80`. Human owner not
  supplied. Repository base remains
  `1d3df86413383f23627bf5c7d2cecc5cd2ab033b`; work is on
  `codex/login-auth-integration`.

## Follow-up accessibility test correction

- After feature commit `64f3d80`, the user asked to fix the reported contrast
  failure. Investigation showed axe ran while the theme transition blended
  light/dark colors; the settled light input colors passed axe.
- Updated the browser test to wait until the input wrapper reaches the expected
  light-theme background before the accessibility scan. No product color change
  was needed.
- Final checks on 2026-11-07: `npm run test:e2e` PASS (13/13), `npm run check`
  PASS (13 Vitest tests), `npm run build` PASS, and `git diff --check` PASS.
- This test timing correction is an uncommitted follow-up on the feature branch.

## PR foundation check correction

- Both failing PR checks were the portable foundation job and reported that the
  engineering reuse manifest pointed to deleted `frontend/src/shared/csrf.ts`.
- Updated the provenance destination to its current replacement,
  `frontend/src/auth/sessionApi.ts`, and documented the adaptation: JSON CSRF
  tokens are fetched from the backend instead of read from a cookie.
- `python3 tools/check_foundation.py`: PASS (62 documents, 4 skills, 21
  provenance entries, 9 transport Java sources).
- The PR branch will be updated with this fix so CI reruns on the corrected
  head.
- After pushing commit `0e54933`, both foundation runs passed. One of the two
  concurrent frontend runs exposed a timing race in the unavailable-service
  test: Retry could start before both StrictMode startup reads had settled.
  The test now waits for the two initial reads. The focused test passed five
  consecutive runs, then the full browser suite passed 13/13; `npm run check`,
  `npm run build`, the foundation check and `git diff --check` also passed.
- Follow-up test stabilization is to be committed and pushed to the PR branch
  so its latest frontend workflow reruns with the race correction.
