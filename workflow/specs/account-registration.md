# TD-REG — Account registration

Status: implementation candidate, 2026-11-12. Focused unit and mocked browser
checks pass; database-backed transaction/race verification and independent code
review remain pending. User explicitly requested account registration and
selected: “Let users choose Reporter, Developer or QA during registration.” This
supersedes the operator-only role assignment/public registration exclusions for
new accounts only. Existing-account role changes, password recovery, email
delivery/verification, SSO and deployment are excluded.

## Behavior and interface

The unauthenticated sign-in screen offers Create account. Reuse the shared
sign-in layout/SDK controls/theme/mascot, with labeled Email, Password, Confirm
password and Account role controls. Offer exactly Reporter, Developer and QA
reviewer, default Reporter. Explain password minimum (12 characters). Validation,
busy/error/success states are accessible; prevent duplicate submits, require
matching passwords in the UI, never persist credentials or role in browser
storage, clear password inputs on successful registration and on form-mode
changes. Preserve email when returning to sign-in. Success returns to sign-in
with “Account created. Sign in to continue.” Do not sign in automatically or
infer an authenticated role from a selection. New accounts have no projects;
existing create/join flow supplies project membership after real login.

`POST /api/session/register` accepts exactly JSON `{login,password,role}` with
string values, no duplicate/unknown fields, and the current same-origin session
CSRF token in `X-CSRF-TOKEN`. Reuse the 8 KiB pre-deserialization body cap,
including chunked input. Existing `GET /api/csrf`, cookie, security headers and
no-store behavior remain in force. No actor/user/membership fields are accepted.

- Normalize login with the existing LoginPolicy email-shaped identifier rules;
  enforce 3–254 UTF-8 bytes after normalization, single @, nonblank components,
  no whitespace/controls. This is an identifier, not verified email ownership.
- Password is 12–128 Unicode code points / at most 512 UTF-8 bytes, untrimmed and
  unnormalized, using the same Argon2id encoder as login. Reject malformed
  Unicode/control values if needed to prevent ambiguous storage/credentials;
  do not weaken existing sign-in acceptance to add registration.
- Role must be exactly REPORTER, DEVELOPER or QA_REVIEWER. The server stores this
  validated initial choice on the new account; subsequent authentication and
  authorization always resolve the stored role, active membership and objects.
- Anonymous requests with valid CSRF create one enabled UUID account and one
  metadata-only ACCOUNT_REGISTERED business audit in the same transaction.
  Audit contains new user UUID, accepted role, request UUID and UTC timestamp;
  no email/password/hash/body/session/CSRF content. New migration, no old edits.
- Return `201 {registered:true}` only after commit. No automatic session login,
  role assignment to existing accounts, membership, local fixture marker or AI.
- Duplicate normalized login returns `409 ACCOUNT_UNAVAILABLE` with generic
  “Account could not be created with these details.” Never overwrite/adopt a
  disabled account or change any existing role/password. Concurrent duplicates
  produce one account/audit and a safe conflict for the loser.
- Signed-in actor registering another identity receives `409 ALREADY_AUTHENTICATED`.
  Invalid shape/content/role: `400 INVALID_REQUEST`; invalid CSRF:
  `403 CSRF_INVALID`; persistence/audit/required denial-event failure:
  `503 SERVICE_UNAVAILABLE`, safe fixed JSON envelope, no partial mutation.
  Shape/framing validation may precede CSRF; CSRF precedes account lookup/write.
- Use bounded metadata-only registration denial/security events with a fresh
  migration extending accepted event codes/routes; never log credential content.
  Browser retries registration only once after a confirmed CSRF_INVALID. Network
  or other uncertain failure is not automatically replayed; preserve email and
  provide a truthful retry/sign-in message. No success claimed on failure.

## Acceptance criteria

| ID | Observable result and verification |
| --- | --- |
| TD-REG-01 | All three validated initial roles register through the bounded CSRF-protected endpoint; normalized identifiers and Argon2 hashes round-trip through real login/session role resolution; no automatic membership/session authentication. |
| TD-REG-02 | Malformed/duplicate/unknown fields, forged actor/membership, invalid role/login/password, oversized fixed/chunked input, missing CSRF and already-signed-in requests fail safely without account mutation; no secret-bearing errors/logs. |
| TD-REG-03 | Isolated PostgreSQL proves account+audit atomicity, rollback on injected audit failure, duplicate/case-normalized conflict and concurrent duplicate behavior. Existing disabled accounts/roles/passwords remain unchanged. |
| TD-REG-04 | UI supports create/sign-in mode, role choice, confirmation, pending guard, input correction, safe errors and success-to-sign-in; getSession remains the only authentication source. API tests assert exact fields/CSRF and no network replay. |
| TD-REG-05 | Focused backend, database, frontend and real browser checks cover the above, keyboard/a11y and 320/390px/desktop views; record revision, commands, pass/fail/unavailable, independent review/corrections, screenshots and limits. Mocked browser results are identified and do not replace real backend verification. |

Read product, ADR002, authentication, shared project and UI contracts with this
bounded extension. Existing denied/object permission, role-change session
invalidation and non-registration endpoints remain unchanged.
