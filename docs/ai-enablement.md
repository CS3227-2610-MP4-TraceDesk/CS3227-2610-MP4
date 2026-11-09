# Shared SoCLaaS enablement

Issue #42 separates implemented enablement from live verification and deployed
release evidence. See the [canonical contract](../workflow/specs/ai-enablement.md).
All role adapters use the same secured gateway/ledger; configuration belongs to
the backend process, never the browser or `VITE_*` variables.

The [official SoCLaaS overview](https://soclaas.comp.nus.edu.sg/) describes free
access for the NUS SoC community and authenticated `GET /v1/models` discovery.
Key portal and detailed documentation require the NUS network. No balance or
pricing endpoint is assumed. This implementation supports explicit operator
sponsored-access authorization with finite resource units and fixed expiry;
account access must still be checked. It makes no monetary-balance or billing
claim. Changed provider terms require revalidation before further approval.

## Backend settings

Inject these through the process environment or hosting secret store. No code
reads environment files. Invalid enabled settings fail closed with safe errors.

| Variable | Meaning |
| --- | --- |
| `AI_ENABLED` | Explicit `true` enables configuration; absent/`false` disables it. |
| `SOCLAAS_API_KEY` | Secret, backend only. Never print, put in command arguments, commit or share in chat. |
| `SOCLAAS_MODEL` | Exact accessible chat model ID from the authenticated catalog. |
| `AI_MODEL_REVISION` | Positive immutable configured-model revision; increase when changing model. |
| `AI_POLICY_REVISION` | Positive immutable policy revision; increase on enable/disable, quota or model changes. Initial default1 cannot replace an already published different revision1 policy. |
| `AI_GLOBAL_DAILY_UNITS` | Finite shared UTC daily resource-unit ceiling; at most1,000,000,000,000. |
| `AI_ACTOR_DAILY_UNITS` | Positive per-user ceiling, no greater than the shared ceiling. |
| `AI_SPONSORED_ACCESS_ACKNOWLEDGED` | Explicit `true` confirms the operator has approved institutional access for this deployment. |
| `AI_SPONSORED_APPROVAL_REFERENCE` | Opaque nonsecret reference to an actual operator approval, never the key or approval document. Only its fingerprint is persisted. |
| `AI_SPONSORED_APPROVAL_REVISION` | Positive immutable sponsorship revision; increase for renewed/replaced approval. |
| `AI_SPONSORED_APPROVAL_EXPIRES_AT` | Explicit fixed UTC ISO-8601 expiry. Refresh never extends this approval. |

Policy, model and sponsorship revisions default to1 and must be positive integers
no greater than1,000,000,000. Enabled quotas have no implicit default: both must
be supplied. Disabled configuration ignores provider settings and uses zero
budgets, but still validates its policy revision for publication. Key/model and
approval-reference strings must be nonblank printable ASCII without whitespace,
at most4,096 characters; the configured model must additionally match the gateway's
128-character model-ID allowlist. Diagnostics name settings without supplied values.

The existing transport also accepts these optional stricter limits. Each must be
a positive integer at or below the listed ceiling; absent values use that ceiling.

| Variable | Ceiling |
| --- | ---: |
| `AI_CONNECT_TIMEOUT_MS` | 3,000 |
| `AI_REQUEST_TIMEOUT_MS` | 30,000 |
| `AI_MAX_RESPONSE_BYTES` | 65,536 |
| `AI_MAX_INPUT_BYTES` | 16,384 |
| `AI_MAX_INPUT_TOKENS` | 8,000 conservative input-estimate units |
| `AI_MAX_OUTPUT_BYTES` | 16,384 |
| `AI_MAX_OUTPUT_TOKENS` | 1,200 |

`SOCLAAS_BASE_URL` may be absent or exactly
`https://soclaas-api.comp.nus.edu.sg/v1`; all other values are rejected. It does
not provide a deployment endpoint override. Report/gateway limits still apply
when a lower transport ceiling causes additional requests to be rejected.

Choose quotas for the actual deployment; example values are not permission to
make requests. A request reserves at most9,200 conservative resource units and
uncertain dispatched attempts keep the reservation. These units are not currency
or a tokenizer. Shared concurrency remains at most4, per-user1, with60-second
reservations and the existing30-second transport deadline. Completion POSTs
never retry automatically.

For an interactive zsh terminal, enter the key without shell history/echo:

```sh
read -s "SOCLAAS_API_KEY?SoCLaaS API key: "
export SOCLAAS_API_KEY
echo
export SOCLAAS_MODEL='accessible-chat-model-id'
```

Run the backend from that same terminal; exporting in another terminal does not
update an already running process. Do not export the key for the Codex process
or send it to a development agent. A hosting secret store is preferable for
deployed services. Rotation requires a controlled process replacement; use a new
policy revision and validate access. Keep old/new credentials separated during
the transition, and revoke the old key through the provider's supported process.

## Publication, recovery and emergency disable

Startup configuration is immutable per process. Catalog-only refresh checks
the fixed provider origin independently of role traffic and publishes bounded,
expiring model metadata through PostgreSQL. It selects no report data and sends
no completion. Role calls still authorize before selecting context or invoking
the provider. Startup without a verified snapshot is unavailable for AI.

Metadata expires within15 minutes and cannot outlive the sponsorship approval.
Refresh failure or approval expiry denies new admission. PostgreSQL revisions
fence stale workers across instances. A refresh cannot restore an older policy
or extend approval. Dispatched attempts still settle; stale/revoked attempts
must not deliver suggestions. Accounting and business audits remain separate.

To disable AI, start a trusted backend process against the same application's
database with `AI_ENABLED=false` and a policy revision higher than every published
revision. Its startup publication fences old enabled workers without waiting for
every instance to restart. Verify AI-unavailable behavior and retain the new
revision. Ordinary report operations remain available. Then replace/drain old
instances. Never roll policy revisions backward during application rollback.

For recovery or model/quota changes, use a higher policy revision and explicit
valid approval; sponsorship changes need a higher approval revision. Revalidate
model access and wait for a verified shared snapshot. Never clear ledger tables,
reset revision high-water marks, refund uncertain attempts, or silently lengthen
an approval to recover service. Use metadata-only status and numeric accounting
events; keep prompts, suggestions, keys and private content out of logs.

## Controlled live Reporter evaluation

This tool is not a test/CI dependency or a production API. It requires an
explicitly isolated `_test` database and synthetic data. It creates an owned
schema, runs migrations, exercises the real Reporter service/shared gateway and
ledger, and drops only that owned schema on exit.
Ordinary backend tests and the synthetic browser fixture force `AI_ENABLED=false`
even if the parent terminal has live settings; only this explicitly opted-in
evaluation task inherits enablement for real provider use.

Before running, record the human-approved model, environment, expiry, resource
allowance and24-attempt maximum. Supply the settings above and the isolated DB
settings from [the test database guide](test-database.md). This dedicated runner
requires both shared/per-user ceilings to be exactly220,800 units for24 worst-case
reservations. The operator can decline or choose a lower allowance, but then this
full comparison must not run.
Do not use a live application database or production credentials.

The operator must create the ignored, nonsecret file
`workflow/reporter/runs/ai-live-approval.json`. Start from this unapproved template;
fill actual opaque account/environment references, model and expiry, and set
`approved` to true only after approving the exact allowance. The expiry must not
outlive `AI_SPONSORED_APPROVAL_EXPIRES_AT`.

```json
{
  "approved": false,
  "accountReference": "replace-with-opaque-account-reference",
  "environmentReference": "isolated-local",
  "model": "replace-with-accessible-model",
  "credentialSource": "terminal-environment",
  "maxAttempts": 24,
  "maxResourceUnits": 220800,
  "expiresAt": "replace-with-fixed-UTC-expiry",
  "caseIds": ["RAI-01", "RAI-02", "RAI-03", "RAI-04"],
  "stopOnFailure": true
}
```

Unknown/duplicate fields, changed fixture content, expired/mismatched approval or
broader quotas prevent startup. The runner claims the approval file once by
atomically renaming it before database/provider work. A failed setup also consumes
approval; repeat execution requires a new explicit operator authorization record.
This local guard records intent and prevents accidental replay; it is not an
identity-verification or tamper-resistant approval system.

Only after that authorization, set:

```sh
export TRACEDESK_AI_LIVE_CONFIRM=synthetic-only-24
export TRACEDESK_AI_LIVE_MAX_CALLS=24
./gradlew :backend:auth-app:reporterAiLiveEvaluation --no-daemon --console=plain
```

The command runs RAI-01–04, two context variants and three repetitions each,
with a fixed prompt/model and no completion retries. Expanded context deliberately
repeats supplied facts within the same six fields; it is a redundant-context
baseline, not evidence that added private data improves quality. Logs remain
excluded. Any failed attempt stops the run; do not rerun without reviewing and
renewing the remaining allowance. Catalog GETs are separate from the24 completion
attempt cap. Console output contains fixed case IDs/status only.

The current candidate uses `report-improvement-v2` and the separately pinned
`fixture-cases-v2.json` under `workflow/reporter/experiments/reporter-ai/`.
The original `fixture-cases.json` and its v1 observations are historical baseline
evidence. V2 retains the same four six-field inputs. Prompt corrections have not
yet been evaluated live; the v1 approval cannot authorize a v2 retest. Obtain a
fresh bounded approval before creating a new one-use record or running this task.

Local synthetic results are JSONL under ignored `workflow/reporter/runs/`.
They include validated model text for assessment, elapsed time, input data bytes,
validated suggestion bytes, fixture hash and numeric ledger outcomes/revisions.
Validated suggestion size is not raw provider response size. Ledger `actual_units`
is a bounded aggregate, not an input/output token breakdown or price. Missing
provider token breakdown/cost remains unknown. Inspect files as plain text;
model text remains untrusted. Retain only sanitized measurements in portable
evidence. Score useful wording, actionable questions and factual fidelity0–2
each, plus pass/fail forbidden behavior; never auto-award a quality score merely
because output parses. No independent rating is implied by the runner.

For invalid output, the local evaluation record may include a fixed diagnostic
stage/reason pair. It distinguishes gateway rejection from Reporter response,
change or question validation. These categories contain no rejected text,
provider keys, field values or parser exception messages. They are not returned
to browsers or written to application logs. A broad gateway category cannot
identify a narrower provider defect. The previously rejected v2 response was
not retained, so adding diagnostics cannot reconstruct its cause retroactively.

This service-level evaluation does not verify login, browser apply or a deployed
environment. Those need a separate authorized synthetic-account browser journey.
Developer and QA need their own integrations and evaluations; Reporter results
cannot certify them.

The controlled evaluation uses a test-classpath manager with background refresh
disabled and performs one explicit catalog refresh before the batch. This pins
the verified metadata snapshot for comparable observations; its existing validity
and fixed approval expiry still apply and stop an overlong run. Production's
scheduled refresh and every admission/delivery check remain unchanged. Do not use
this test harness as a production gateway configuration.
