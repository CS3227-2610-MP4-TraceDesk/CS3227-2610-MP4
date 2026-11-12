# SoCLaaS AI enablement, live validation and deployment gates

Feature: AI-ENABLEMENT, GitHub issue #42. This shared specification covers trusted
activation of the secured gateway, real-provider validation and deployment
verification for Reporter, Developer and QA AI. The integration owner has not
been assigned. It builds on [SHARED-AI-GATEWAY](secured-ai-gateway.md), Reporter
AI [TD-RPT-26–30](reporter-ai.md), and the role integrations that follow. It does
not authorize credential access, live requests, account changes or deployment.

## Verified provider facts and funding interpretation

The SoCLaaS service page states that NUS School of Computing community members
have free access, that API keys and documentation are available only from the
NUS network, and that `GET /v1/models` lists available model IDs. It does not
document a balance, credit, pricing, or spend-query endpoint. See the
[SoCLaaS service page](https://soclaas.comp.nus.edu.sg/). Therefore enablement
must not invent monetary prices, balances, token-to-currency conversion, or
claims of verified remaining funds. Provider model catalog metadata establishes
model availability only; arbitrary catalog fields are not accounting authority.

Use an explicit **sponsored-access mode**: a trusted operator acknowledges that
the selected SoCLaaS institutional access is approved for this deployment and
sets a finite resource-unit quota and a fixed approval expiry. The approval
metadata is backend-only, immutable for its revision, and cannot be extended by
the refresh process. Extending or replacing the approval requires a new explicit
operator action/revision. Resource units remain conservative gateway units, not
money or proof of provider billing. If the acknowledgement, model access,
positive finite quotas, or unexpired approval is absent or invalid, keep AI
disabled. The product must describe the provider disclosure and resource limits
without implying a monetary budget that has not been verified.

## Enablement contract

### Trusted configuration and shared publication

Preserve `Policy.disabled()` and zero-budget behavior as the default. Only trusted
backend process configuration may provide `SOCLAAS_API_KEY`, `SOCLAAS_MODEL`,
and positive immutable `AI_MODEL_REVISION` (increased when changing model),
an explicit sponsored-access mode, operator approval reference/acknowledgement
and fixed approval expiry, finite global and per-actor daily resource-unit
ceilings, and a positive policy revision (default 1 for initial publication
only). Policy is immutable for a running process. Reject missing, malformed, expired,
nonpositive, unlimited, contradictory, or over-limit values at startup or
publication with safe diagnostics. Never accept these values from HTTP/browser
input. Keep the fixed SoCLaaS origin and raw client internal. Do not read
environment files.

Role services keep a reusable long-lived secured gateway; configuration must not
construct a new provider client for each user request. Policy remains the
immutable startup value, while model metadata is refreshed dynamically. A
published state binds the fixed policy/quota revision, selected model/revision, metadata
revision, sponsored-approval fingerprint/revision and fixed expiry. PostgreSQL is
the cross-instance authority: metadata revision allocation and publication are
separate atomic, server-owned operations, monotonically increasing and fenced.
Catalog validation occurs between them without holding a database transaction;
only the allocated pending revision can become a new available snapshot. The ledger rejects an older
policy or metadata publication and same-revision equivocation. A stale process
cannot restore an older model, quota, approval, or expiry after a newer
publication or disable. A disable/reconfiguration publication takes effect
across instances, including while work is in flight; already dispatched requests
still settle conservatively but cannot deliver if role authorization/policy
denies delivery.

An explicitly configured operator refresh service validates the configured model
against the fixed-origin `/v1/models` catalog and refreshes trusted model metadata before expiry, with a
maximum 15-minute validity, publishing a new immutable metadata revision with
`validUntil <= now + 15 minutes`. Refresh must run independently of role request
traffic so idle instances do not leave stale shared state. This is a catalog GET
only: it receives no role source/context and never makes a completion request.
Refresh cannot renew or lengthen sponsorship approval. It may publish an enabled snapshot only while
the original approval remains valid. A failed refresh cannot silently reuse
stale metadata after expiry; any refresh failure marks AI unavailable immediately
and blocks new admissions until a successful refresh publishes a newer snapshot.
Startup without a valid snapshot is disabled/unavailable. The gateway's
request-driven catalog/completion operations remain after its authorization and
context-selection steps. Operator-authorized refresh is independent of those
role checks and must never access role data. Ordinary reporting and bug
management remain available.

The exact Java port names may follow existing packages, but the implementation
must provide equivalent operations to: (1) atomically allocate/publish the next
metadata revision in shared PostgreSQL, binding the selected model and approval
fingerprint/expiry; (2) validate catalog availability on the fixed origin; and
(3) report refresh health without exposing credentials or content. A refresh
failure must not be represented as successful renewal. The metadata snapshot
captured for a request's admission must also be the snapshot used for that
request's completion, even when another thread refreshes concurrently. Pass that
immutable snapshot explicitly through provider completion or provide an
equivalent concurrency-safe invocation binding. Existing
`Ledger.configure(policy, metadata, now)` already rejects stale revision
publication; add a ledger-owned revision allocation/publication seam as needed,
without weakening its fence. Do not add a public admin/config endpoint in this
slice.

### Startup, rotation and recovery operations

Document the exact supported deployment configuration names and validation,
secret-store injection and rotation, enablement/disablement, quota change,
approval renewal, model change, startup without metadata, refresh failure, expiry,
recovery and emergency disable procedure. Rotation/reconfiguration publishes a
new policy revision and/or metadata generation as appropriate. An operator can
disable AI through trusted configuration and publication without restarting
every instance; stale instances remain fenced.
Do not log keys, approval documents, report content, prompts or suggestions.
Operational metrics contain bounded counts/status/latency/revision and expiry
metadata only. AI readiness is separately observable from core application
readiness.

## Observable acceptance criteria

These criteria are requirements, not claims that implementation, live calls, or
deployment checks have passed.

| ID | Observable requirement | Required evidence |
| --- | --- | --- |
| **AI-ENABLE-01 — Trusted finite enablement** | Disabled/zero-budget defaults remain. Backend-only key/model/sponsored-access acknowledgement, fixed approval expiry and positive finite shared/per-actor daily unit quotas are validated; HTTP callers cannot choose secrets, origin, model, policy, or quotas. Factory exposes only the secured gateway, reused by long-lived role services. | Configuration tests for absent/invalid/expired values and safe diagnostics; source/API checks for no raw-client or browser bypass; ordinary report path works with AI disabled. |
| **AI-ENABLE-02 — Honest funding semantics** | The service-page facts above are recorded; no balance/pricing endpoint or monetary semantics are asserted without additional authoritative evidence. Sponsored approval is explicit, bounded in time, and reported as resource-unit authorization, not currency. Missing/ambiguous approval fails closed. | Review of documented provider facts and resource-unit UI/operations language; tests that absent/expired approval prevents catalog/completion admission and no inferred price/balance is persisted or logged. |
| **AI-ENABLE-03 — Refresh and cross-instance fencing** | Trusted refresh validates model availability, publishes immutable increasing policy/model/approval metadata revisions with metadata validity at most 15 minutes, and cannot extend sponsorship expiry. Refresh runs independently of user traffic. Failure/expiry blocks new admissions. A newer disable/reconfiguration fences stale instances; dispatched work still settles once and stale work cannot restore configuration. | Deterministic clock tests plus isolated PostgreSQL multi-instance tests for refresh success/failure/expiry, revision rollback/equivocation, disable during work, concurrent publication and recovery. Assert zero completion after stale/expired admission and exactly-once settlement for dispatched work. |
| **AI-ENABLE-04 — Shared role wiring and operations** | Reporter construction no longer hardcodes disabled settings; all role gateways consume the same publication/ledger policy mechanism. Startup, secret rotation, enable/disable, quotas, approval renewal, model refresh, failure and recovery are documented. No per-role divergent switch or public configuration endpoint is added. | Spring wiring/context tests across role services (including future role adapter seam), configuration documentation review, and secret/content log canaries. |
| **AI-ENABLE-05 — Safe failure and resource controls** | Missing/inaccessible model, invalid configuration, stale approval/metadata, failed publication, exhausted quotas/capacity, and refresh failure return safe unavailable behavior. Gateway request-driven catalog/completion follows authorization; operator refresh is catalog-only and has no role context. No unauthorized provider call or content/secret logging occurs. Gateway deadlines, circuit, cancellation, conservative settlement and no completion POST retry remain intact. | Fake-provider and isolated database tests for denied sources, failure paths, exhaustion, concurrent instances, in-flight disable, secret/content canaries, cancellation, circuit and no-retry assertions. |
| **AI-LIVE-01 — Controlled authorization** | Before any live call, a human operator records approved account/environment, model, explicit resource-unit allowance and expiry, synthetic case IDs, stop conditions, and authorized credentials source. No secrets enter issue/evidence/Git. | Sanitized authorization record and preflight checklist; if any item is absent, mark live validation unavailable and make no provider call. |
| **AI-LIVE-02 — Reporter quality evaluation** | Run RAI-01–04 using a fixed prompt/model revision with at least three repetitions per case; compare concise and expanded context using only the same six authorized fields within existing limits. Score useful wording, actionable questions, factual fidelity and forbidden behavior; capture invalid response rate, latency, bytes and only actual provider-reported tokens or verifiably available costs. | Sanitized case-level results with fixed conditions, rubric, actual sample count, failure results and unknowns. Fixture tests are not live quality evidence. |
| **AI-LIVE-03 — Reporter user journey** | In a controlled environment, a real Reporter session requests a suggestion, receives an escaped preview and explicitly applies selected fields through expected-version PATCH, preserving logs and unselected fields. Safe unavailable/funding denial and stale/revoked delivery are also covered without uncontrolled calls. | Browser/API evidence against a synthetic account and isolated test data; provider use remains under AI-LIVE-01 limits. |
| **AI-LIVE-04 — Remaining roles and milestone measures** | Developer and QA live evaluations occur only after their owning integrations exist. Reporter results cannot certify them. Required concise/expanded application and focused/broader development-context comparisons remain owned by the applicable workflow. | Separate role results, or explicitly pending/unavailable status and named prerequisite; no extrapolation from Reporter. |
| **AI-DEPLOY-01 — Candidate and environment separation** | Document selected hosting, HTTPS/origins, distinct development/test/production databases and credentials, secret storage, migrations and release process. Pin candidate revision; require applicable same-revision CI/security/database/browser checks. Untrusted PR jobs cannot read production secrets. | Sanitized configuration and CI evidence tied to exact revision; environment separation demonstrated without exposing credentials/data. |
| **AI-DEPLOY-02 — Readiness and operations** | Readiness checks required persistence/session/ledger dependencies; AI health is distinct from core readiness. Document metadata-only monitoring, refresh expiry recovery, emergency disable and rollback. | Readiness tests and sanitized operational procedure/telemetry review. |
| **AI-DEPLOY-03 — Authorized deployed smoke** | On an explicitly authorized deployment target, synthetic-account smoke tests cover session/CSRF/project isolation, ordinary report create/read/edit and Reporter preview/explicit apply, secure cookies/headers, migrations, accounting and AI-unavailable behavior. Claim whole-app deployment only after implemented Developer/QA journeys meet their own criteria. | Target, environment, deployed revision and sanitized journey outcomes; if no target is supplied/authorized, mark unavailable and do not deploy. |
| **AI-DEPLOY-04 — Traceable evidence and review** | Evidence maps each claimed criterion to actual checks, outcomes, independent diff review and findings. Local/staging, production, fake-provider and live-provider results are distinguished; no fabricated ownership, approval, usage, or metric. | Sanitized evidence record includes revision/working-tree identity, commands, pass/fail/skip/unavailable results and limitations. |

### Bounded live Reporter browser harness (AI-LIVE-01/03)

The real-browser journey is a separately invoked, test-only operation. Ordinary
tests and the default synthetic browser task must force AI off and the live flag
off. Provider activation must not be possible through an environment variable
alone. The dedicated live task requires an explicit, pre-existing authorization
record before it may touch its isolated test database, fetch the model catalog,
or contact the provider. The authorization record must have a strict allowlist
of keys and bind the approved synthetic account/environment, fixed candidate
and model, prompt fixture, resource-unit allowance, expiry, and stop conditions;
unknown keys, missing values, malformed values, or expired authorization fail
closed. The approval file contains no credentials. The browser driver may attach
only after a user-terminal-started backend has received the provider key through
its backend-only process environment; never pass the key to Node, Vite, the
browser, browser metadata, logs, or artifacts.

One invocation permits at most one completion, with a hard reservation ceiling
of 9,200 resource units. A shared atomic one-use admission gate must cover all
requests and actors, so concurrent requests cannot start another completion
even when the ledger's measured charge is below the reservation. Do not retry a
completion or browser project request. Use same-origin loopback HTTPS backed by the
owned isolated test database/schema. Disable traces, screenshots, video and
other content capture for live mode. Stop on any failure, expiry, consumed
approval, unavailable state, or uncertainty; do not re-run with the same
authorization.

The successful journey uses the synthetic Reporter account and its six saved
fields, requests one suggestion, verifies an inert escaped preview, then
explicitly selects a title and applies it through the normal expected-version
PATCH. Verify logs and all other unselected fields remain unchanged. A safe
unavailable response is acceptable only before any live completion. Stale or
revoked delivery and other negative paths must be exercised offline with fake
providers, without adding live calls. If the single response has no useful title
change, record that outcome and stop without applying or retrying; do not invent
or edit a suggestion. The sanitized run summary may contain fixed case/model/
prompt/revision identifiers, outcome, latency, byte counts, and provider-reported
usage when present. It must contain no model text, session tokens, login details,
passwords, prompts, or suggestion content.

Before the dedicated task is used live, fake-provider tests must show that
defaults and ordinary test environment variables cannot enable a live call, and
that the live guard rejects absent, malformed, expired, mismatched, or consumed
approval; enforces one global attempt under concurrency and the 9,200-unit cap;
and keeps credentials out of browser-facing processes and captured artifacts.
This contract adds no product/UI or role scope, quality remediation, deployment
authorization, or live-call authorization. Quality findings remain separate and
must not be attributed to a model without evidence.

## Security boundaries, dependencies and non-goals

Trust boundaries are operator/deployment configuration to application, metadata
refresh to shared ledger publication, independent backend instances to admission,
provider catalog/completion to the internal adapter, and application readiness
to operator action. Relevant threat rows: AI context disclosure, quota/resource
exhaustion, stale configuration, stale permission/result delivery, secret
exposure, unsafe provider output, malicious development-agent instructions and
production secret exposure in untrusted jobs.

Required dependencies are #39's secured gateway/ledger, Reporter #17, Developer
#31 and a future QA AI integration. Shared policy/schema ownership must remain
centralized. Exclude role feature expansion, administration UI, streaming,
repository ingestion, autonomous tools, automatic patches, arbitrary execution,
monetary price/balance estimation, account creation and deployment. No live
request or deployment is authorized by this specification. Operator account,
approval, exact quotas, credential injection and deployment target remain
external gates; record unavailable evidence honestly until supplied.

## Verification and measurement plan

Use synthetic content and fake providers for deterministic security and concurrency
coverage. Assert absence of provider access for initial authorization denial,
failed refresh, stale configuration, exhausted budget and expired approval.
Use isolated PostgreSQL for cross-instance publication, disable, quota and
settlement races. Verify unauthorized delivery after changes and that dispatched
attempts still settle once. Run the development-agent injection exercise required
by the Reporter workflow before first integrated AI acceptance. Independently
review the actual implementation diff. Live quality measurement follows
AI-LIVE-01/02; do not claim model quality from authored fixtures. Record latency,
bytes, failures, and provider-reported usage only when actually observed.

Status: specified before implementation. No criterion is verified by this file;
live provider, operator approval, account setup, deployment target and human
integration owner are outstanding.

For issue #70, see the [role AI evidence inventory and remaining run plan](../qa/evidence/role-ai-validation.md).
