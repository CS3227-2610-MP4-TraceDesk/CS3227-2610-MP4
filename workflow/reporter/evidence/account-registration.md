# Account registration verification

- Feature and criteria: TD-REG-01 through TD-REG-05, as specified in
  [account registration](../../specs/account-registration.md).
- Date: 2026-11-12 (Asia/Singapore). Human owner not supplied.
- User decision: users may select Reporter, Developer or QA during initial
  registration. No other human approval is recorded.
- Candidate identity: uncommitted working tree based on `01d348b59050333088b13b1f208d5d09e7c258e8`.
  Registration files, migration and contract were inspected at this candidate;
  unrelated pre-existing workspace content was preserved.
- Environment: macOS, Java 25 / Gradle 9.6.1, Node/npm and Playwright Chromium.
  Browser journeys used the development frontend and mocked authentication API.
  No database or real backend was used.
- Passed:
  - `./gradlew :backend:auth-app:test --tests com.tracedesk.auth.api.AuthControllerTest --console=plain` — 17 tests passed, 0 failed; includes registration role/normalization/hash, duplicate conflict, signed-in rejection, unknown/duplicate JSON fields, plus existing authentication tests.
  - `npm run check` — typecheck and lint passed; 10 test files and 99 tests passed.
  - `npm run build` — production frontend bundle built successfully.
  - `npx playwright test --project=development -g "registration sends the selected role"` — 1 mocked browser journey passed, including 320px viewport, CSRF header, selected role, success-to-sign-in, preserved email and cleared password.
  - `npx playwright test --project=development -g "registration sends the selected role|login stacks without overflow"` — 3 mocked browser checks passed (registration, 320px, 390px).
  - `python3 tools/check_foundation.py` — passed: 149 documents, 4 skills, 21 provenance entries and 11 transport Java sources.
- Failed/corrected browser attempt: a full mocked development run had 23 passes
  and one 320px layout assertion failure caused by a 0.000008 CSS-pixel
  subpixel rounding difference. The assertion now allows 0.1 CSS pixels; the
  registration and both mobile layout cases then passed. The remaining full
  browser suite was not rerun after this tolerance-only change.
- Unavailable / pending:
  - TD-REG-03 and database-backed parts of TD-REG-01/02: isolated PostgreSQL
    verification of migrations, account+audit rollback/atomicity, normalized
    duplicate races, disabled-account preservation and stored-role login.
    The user declined creating a new test database/container; no database was
    started or substituted.
  - Real browser-to-backend registration, full security/a11y review and
    independent post-implementation code review. The separate reviewer agent
    could not continue because its execution reached the account usage limit.
    An earlier contract review found and prompted corrections to contradictory
    operator-only role language; that was a specification review, not code review.
- No live or deployed check was performed. The mocked browser checks do not
  establish database migration or real endpoint acceptance.

## Final candidate hygiene

- `git diff --check` — passed.
- `git diff --check` — passed.
- Human decision source: the user's explicit response, “Let users choose
  Reporter, Developer or QA during registration.”
