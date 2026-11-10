# Reporter browser verification plan

Defined before the bounded live-browser harness is used, base `f20a2b8`.
Covers AI-LIVE-01/03; owning feature behavior remains TD-RPT-26–30. The user
explicitly deferred quality fixes. Model capability is a possible contributor,
not a verified explanation. The 11/24 v2 semantic failures remain recorded.

First verify the real HTTPS/React/session/CSRF/PostgreSQL/ledger path with its
synthetic provider. Exercise safe unavailable behavior, inert preview, selected
version-checked apply preserving logs/other fields, keyboard/mobile/accessibility
and the existing stale/revoked/no-retry coverage. Record candidate and exact
commands. Do not present these tests as live SoCLaaS verification.

Prepare a separate test-only operation requiring fresh, one-use approval BEFORE
database or provider access: synthetic case RAI-BROWSER-01, model default if
approved, prompt report-improvement-v2, maximum one completion and 9200 units,
fixed expiry and stop on first failure. Pin browser-live-case.json and the
candidate. Atomic context admission is global across actors and source kinds,
so low actual usage cannot permit another call. No completion or browser retries.

The user starts the backend with the key in the terminal. Launch browser/Node/
Vite only with an explicit environment allowlist excluding credentials. Attach
to the loopback fixture using test-only metadata/control files, fixed HTTPS proxy
and isolated _test database/schema. Disable live traces/screenshots/video and
all raw request/response content capture. The synthetic source contains a log
canary that must remain excluded from the provider's six fields.

Live journey: sign in, save the exact synthetic draft, demonstrate unavailable
before enabling admission (zero completions), request once, inspect escaped
preview, select only a proposed title and explicitly apply expectedVersion PATCH.
Read back to verify title/version plus unchanged description/environment/steps/
expected/actual/logs. No applicable title means stop/no apply/no retry, not a
fabricated success. Invalid/unavailable output also stops. Do not assess semantic
quality as passed merely because the browser path works.

Retain content-free outcome/boolean checks and numeric ledger resource counts.
Never retain a key, session, login/password, prompt/source/suggestion text in run
summaries. Keep metadata/control files temporary, local and owned; clean up the
fixture/schema on exit. No production target, accounts or deployment are in scope.

New live calls are NOT authorized by this plan or the user's instruction to
prepare verification. The existing evaluation approval is consumed. Seek the
specific bounded approval only once this operation is concrete and offline-tested.
