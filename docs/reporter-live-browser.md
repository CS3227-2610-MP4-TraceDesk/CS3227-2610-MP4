# Controlled Reporter live browser verification

This test-only operation prepares AI-LIVE-03. It is separate from the 24-case
service evaluation and from the default synthetic browser fixture. The user's
quality fixes are deferred: the v2 assessment still records 11/24 semantic
failures. A browser journey verifies safe delivery and explicit apply, not model
truthfulness or a model-specific explanation for those failures.

## Before live use

Obtain fresh explicit approval for RAI-BROWSER-01, the exact candidate/source
identity, model, fixed expiry, **one completion and at most 9,200 resource units**,
stopping on failure. Approval is consumed before database or catalog access.
Low measured usage does not permit another completion: a process-wide atomic
gate covers both source kinds and all actors. Neither completion nor browser
test retries are allowed. No applicable title change means stop without apply.

Use Java 25, the built frontend, a dedicated disposable PostgreSQL `_test`
database and synthetic accounts. The fixture binds loopback HTTPS port 18089;
the frontend uses its existing same-origin test proxy on port 4177. It owns a
random schema and removes that schema at shutdown. Do not use application or
production data. Normal tests and `reporterBrowserFixture` explicitly force AI
and the live flag off; only `reporterLiveBrowserFixture` permits this operation.

Create the ignored `workflow/reporter/runs/ai-live-browser-approval.json` only
after approval. This is a template, not an approval:

```json
{
  "approved": false,
  "accountReference": "opaque-operator-account",
  "environmentReference": "isolated-local",
  "model": "default",
  "credentialSource": "terminal-environment",
  "maxAttempts": 1,
  "maxResourceUnits": 9200,
  "expiresAt": "replace-with-fixed-UTC-expiry",
  "caseId": "RAI-BROWSER-01",
  "promptVersion": "report-improvement-v2",
  "fixtureSha256": "6502ba11a271b84a3d760203d9998a285b4cd27fd1847e8dc0799fc3b2cd5faf",
  "candidateRevision": "replace-with-reviewed-base-or-commit",
  "candidateSourceSha256": "replace-with-measured-source-identity",
  "stopOnFailure": true
}
```

The source identity recomputes actual file hashes from the candidate manifest;
it does not trust an environment label or the manifest's stored hash values.
Changing the reviewed files requires a new matching approval. The record also
binds the fixed input fixture, prompt and selected model. Unknown/duplicate keys,
trailing JSON, invalid numbers, mismatches or expiry fail closed. Consumed records
are retained under unique local filenames; replacing an approval is an explicit
operator action, not an automatic retry.

## Separate the backend and browser environments

Export the provider key only in the backend terminal using the hidden prompt
from [the enablement guide](ai-enablement.md). Configure sponsored access, fixed
expiry and both daily quotas of 9,200 units, plus the isolated test settings.
`TRACEDESK_LIVE_BROWSER_CANDIDATE` must match the approved revision label. Supply
temporary metadata, stop, command and acknowledgement paths via the existing
`TRACEDESK_BROWSER_FIXTURE_*` settings. The aggregate ledger result path,
`TRACEDESK_LIVE_BROWSER_LEDGER_RESULT`, must be under ignored Reporter runs.

Start `:backend:auth-app:reporterLiveBrowserFixture` from that terminal. The
operator-approved catalog validation sends no report context. The test fixture
uses one manually refreshed metadata snapshot and rejects duplicate manager
wiring, preserving the earlier stale-delivery fence. Exact saved context must
match the six allowed fixture fields; the stored log canary is excluded.

Start the attached Playwright project from an explicit environment allowlist,
**without the provider key**. Do not launch Node, npm, Vite or the browser from an
environment containing it. The dedicated configuration rejects that condition.
Pass both `TRACEDESK_REAL_BACKEND_TEST=true` and
`TRACEDESK_LIVE_BROWSER_TEST=true`, `TRACEDESK_LIVE_BROWSER_PROVIDER=live`, the
same metadata/control paths, the pinned case hash in
`TRACEDESK_LIVE_BROWSER_CASE_SHA256`, and an ignored result path in
`TRACEDESK_LIVE_BROWSER_RESULT`. The project is `reporter-ai-live-browser`.
It attaches to the already started backend; it never launches a keyed backend.

The local operator launcher prepared for this worktree is
`workflow/reporter/runs/run-ai-live-browser-42.py` (intentionally ignored).
It builds the current frontend in a credential-free environment before consuming
approval, rechecks the approved source identity, separates child environments,
controls fixture lifetime and has a
`--synthetic` mode for offline verification. Its database/JDK paths are local
development settings; other environments must configure their own isolated
resources. No credentials are stored in the launcher or approval file.

## What the test verifies and retains

The Reporter signs in, saves the fixed synthetic draft, verifies unavailable AI
before enabling admission, then requests once. It checks escaped preview and no
automatic mutation, selects only a proposed title and performs the normal
expected-version PATCH. A read-back must show the new title/version and unchanged
logs and all other fields. Stale/revoked delivery and other negative paths remain
offline tests; they do not require additional live calls.

Live traces, screenshots, video, HTML/JUnit reports and arbitrary error output
are disabled. A silent reporter and fixed errors prevent content capture. The
browser summary contains a fixed provider-mode enum, case identity, status and
boolean checks; the backend summary contains bounded accounting counts and
resource units. Neither may contain model/source text, keys, login/passwords or
session tokens. Temporary fixture metadata contains synthetic account identifiers
only and is deleted with the owned fixture directory after shutdown.

`APPLIED_TITLE` is a journey result, not a quality score. `NO_APPLICABLE_TITLE`,
invalid output or other failure stops without manufacturing a suggestion or
retrying. Inspect the sanitized result before any new authorization. Whole-app
deployment, remaining-role validation and the deferred quality findings are
separate gates.
