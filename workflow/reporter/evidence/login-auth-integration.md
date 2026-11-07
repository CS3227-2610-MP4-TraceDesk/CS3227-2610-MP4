# Login authentication integration evidence

Status: frontend implementation complete; end-to-end acceptance is partial.

## Identity and scope

- Base revision: `1d3df86413383f23627bf5c7d2cecc5cd2ab033b`.
- Branch: `codex/login-auth-integration`.
- Feature: connect the existing sign-in UI to the authentication API in
  `workflow/specs/auth-membership.md`.
- Working tree contains implementation changes; no commit was created.
- The UI now restores sessions from `GET /api/session`, obtains JSON CSRF tokens
  from `GET /api/csrf`, submits credentials to `POST /api/session/login`,
  confirms login with a session read, and logs out through the API. It never
  submits a role or user ID. Development shortcuts fill synthetic credentials
  and submit through the same API; production omits them.
- Vite development and preview use HTTPS with a self-signed local certificate.
  The same-origin proxy targets the host backend, including from Docker.
- Registration, local account provisioning, role workspaces, AI, deployment and
  production seed users remain out of scope.

## Verification

- PASS — `cd frontend && npm run check`: typecheck, lint, and Vitest; 13 tests
  passed.
- PASS — `cd frontend && npm run build`.
- PARTIAL — `cd frontend && npm run test:e2e`: 12 of 13 passed after correcting
  the dev HTTPS URL and retry fixture. The remaining existing light-theme axe
  check reports input text contrast of 2.42:1 (foreground `#ababab`, background
  `#686868`). Dark-theme axe passed. The contrast finding remains unresolved.
- PASS — `./gradlew :backend:auth-app:test`: Gradle reports successful; tasks
  were up-to-date.
- PASS — `git diff --check`.
- PASS — independent code review by `/root/login_auth_review` (GPT-6 Luna,
  2026-11-07). It found that logout accepted arbitrary 2xx status codes; the
  client now accepts only 204, and a regression test covers a 202 response.
- OUTSTANDING — the Playwright success journey uses an intercepted synthetic
  API, not a live backend. The backend HTTPS suite runs independently. The local
  auth API does not provision accounts, and no isolated account fixture was
  established for a browser-to-real-backend run. Therefore TD-LOGIN-AUTH-07
  and the live portion of TD-LOGIN-AUTH-04 remain unverified.
- OUTSTANDING — production UI behavior is browser-tested against a mocked 401;
  a full browser journey with the Spring API and isolated database remains
  needed.

## Security assertions exercised

- Client signed-in state requires a successful session API response.
- Login request sends only login and password, with a CSRF header; logout uses a
  fresh CSRF token and exact 204 success semantics.
- Logout service failure retains signed-in UI; startup service failure offers a
  retry. Failed credentials receive a generic message.
- Browser tests verify Secure, HttpOnly and SameSite=Lax session-cookie flags,
  refresh persistence from the server, and server-side logout clearing.
- No roles or membership claims are read from browser storage or sent in login
  requests. No secrets are logged or included in URLs.

## Process record

The specifier `/root/login_auth_specifier` reviewed scope and handed off
criteria before implementation. The independent reviewer inspected the actual
diff and tests and their correction is recorded above. AI prompt injection tests
do not apply because this feature has no AI context path. The full UI-to-live-API
test and the light-theme contrast failure are explicit remaining work; no
completion or release claim is made for those criteria.
