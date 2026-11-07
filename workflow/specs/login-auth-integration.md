# Connect the login UI to backend authentication

Status: implementation contract for connecting the existing sign-in screen to
the bounded local authentication API. This does not add public registration,
account provisioning, role workspaces, bug operations, AI calls, deployment, or
production seed data. Authentication and authorization continue to follow the
[authentication and membership contract](auth-membership.md) and [shared role
contracts](../decisions/002-shared-role-contracts.md).

## User behavior and trust boundary

The browser uses only same-origin `/api` requests. `TDSESSION` remains a
server-issued `HttpOnly; Secure; SameSite=Lax` cookie. The browser never reads,
stores, or invents the session identifier, identity, project membership, or role.
The API's JSON response from `GET /api/csrf` is the sole CSRF-token source; the
token exists in memory only and is fetched again before each login or logout.

On startup the UI asks the backend for the current session. It shows the minimal
signed-in state only after a successful session response. This screen does not
infer a role or grant access to role-specific features. Refreshing the page asks
the server again; the server-side session is authoritative.

The development-only Demo accounts disclosure fills synthetic credentials and
uses the same login API flow as any other account. It does not authenticate
locally. The listed accounts must be provisioned outside the application API in
a local development or test database. Production builds omit the disclosure,
credential fixtures and local sign-in shortcuts.

For local browser development, Vite serves HTTPS with an automatically generated
self-signed certificate and proxies `/api` to the local backend. This TLS
termination is for local development only; production HTTPS and proxy
configuration remain deployment work. The backend continues to issue secure
cookies. No API CORS exception is introduced.

## API interaction rules

- Startup: `GET /api/session` with same-origin credentials and `cache: no-store`.
  A `200` shows signed-in state, `401` shows the sign-in form, and network/`5xx`
  keeps the user unauthenticated while showing a retryable service message.
- Login: fetch `GET /api/csrf`, then `POST /api/session/login` with exactly
  `{ "login": "…", "password": "…" }`, JSON content type, the returned token
  in `X-CSRF-TOKEN`, and same-origin credentials. A `200` is followed by
  `GET /api/session`; show signed-in state only after that response is `200`.
- Logout: fetch a fresh token, then `POST /api/session/logout` with
  `X-CSRF-TOKEN` and same-origin credentials. `204` clears the signed-in view;
  `401` is already signed out. A `503` or network failure keeps the signed-in
  view and reports that logout could not be confirmed.
- `403 CSRF_INVALID` is known to occur before credentials or session state are
  changed. Refresh the token and retry that same operation once. A second `403`
  is shown as a recoverable request error; never retry indefinitely.
- `401 INVALID_CREDENTIALS` uses one generic message. `503` and network errors
  use a service-unavailable message without response internals. `400` and other
  unexpected statuses use a generic safe request error. Do not log request
  bodies, passwords, CSRF values, cookies, or server response payloads.
- While a request is pending, disable duplicate submission/action and expose
  the busy state accessibly. Preserve the entered email and allow correction;
  never persist the password or CSRF token across reloads.

No domain mutation or business audit event is added by this frontend slice.
Logout invalidation and login/session lifecycle remain owned by the backend API.

## Acceptance criteria

| Criterion | Observable result | Verification |
| --- | --- | --- |
| TD-LOGIN-AUTH-01 | Startup checks the server session. A valid session displays the signed-in placeholder; an absent/expired session displays login; an unavailable service never displays authenticated state and offers retry. Refresh restores state only from the API. | Frontend API/unit tests and browser journey for valid, absent, unavailable and refresh cases. |
| TD-LOGIN-AUTH-02 | Login obtains JSON CSRF, sends exactly the specified login fields and header with same-origin credentials, verifies the resulting session, and never uses local role selection or fake sign-in. | Assert request method, path, body, headers, cookie/session journey and signed-in state using synthetic fixtures. |
| TD-LOGIN-AUTH-03 | Logout obtains a fresh CSRF token, posts to the API, clears local signed-in state only after `204` or confirmed `401`, and retains it on unavailable/failed logout. | Browser/API tests for success, already-signed-out, CSRF refresh, `503` and network failure; backend replay checks remain authoritative. |
| TD-LOGIN-AUTH-04 | Invalid credentials, CSRF rejection, unavailable API and unexpected responses show safe, accessible errors. Pending requests prevent duplicate actions. No password/token/cookie is stored or logged by the frontend. | UI tests for each error/status, keyboard/axe checks, and source review for storage/logging. |
| TD-LOGIN-AUTH-05 | Development Demo accounts only fill synthetic credentials and submit through the same backend API. Production assets contain no demo accounts or credentials. No client-side role is treated as identity. | Development browser test for all three shortcuts; production bundle inspection and browser request assertions. |
| TD-LOGIN-AUTH-06 | The local Vite browser origin uses HTTPS, preserves the API's Secure/HttpOnly/SameSite session cookie through its same-origin proxy, and introduces no CORS or cookie-security weakening. | Browser HTTPS/API journey with synthetic users; inspect cookie flags and proxy behavior. Document that the generated development certificate is self-signed. |
| TD-LOGIN-AUTH-07 | Login and logout work end to end with the real authentication API and isolated synthetic accounts; errors and session restoration match the API contract. | Same-revision frontend checks/build/browser tests plus backend HTTPS contract suite; record exact revision, commands, results, review and gaps in Reporter evidence. |

## Threats and exclusions

Applicable threats: credential/session/CSRF leakage, session fixation/replay,
forged identity or role claims, stale client state, untrusted network transport,
malicious error responses, and unavailable authentication services. Assert that
the browser has no successful signed-in state until the server confirms it, that
no request submits `userId`, `role` or membership claims, and that secrets are
absent from URLs, local/session storage, console output and evidence.

AI prompt/context threats, bug-object permissions, role-specific routes, account
provisioning, registration, membership administration, production deployment,
and production account fixtures are out of scope. Existing local account
credentials are test data only and must not be represented as production users.

## Evidence ownership

Implementation and browser evidence are recorded in
`workflow/reporter/evidence/login-auth-integration.md`. Record the exact base
revision and working-tree fingerprint, commands/results, browser/viewport,
fixture, TLS limitations, actual independent review and any remaining gaps.
Tokens/costs and inherited context not visible to tools remain unknown.
