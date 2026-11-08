# Shared AI gateway integration handoff

Issue #39 supplies a backend foundation for Reporter #17, Developer #31 and the
future QA AI slice. It adds no role endpoint or screen. AI is disabled until
trusted backend wiring supplies an enabled finite policy and an expiring validated
model/resource-funding snapshot. Live pricing/funding compatibility, role context
adapters and deployment remain separate work.

## Modules and construction

- `backend:ai-gateway`: provider-independent immutable types, trusted ports and
  `SecuredAiGateway.suggest(Request)`.
- `backend:ai-transport`: fixed-origin SoCLaaS adapter. Call
  `SoclaasGatewayFactory.create(...)`; it returns only a secured gateway and
  owns its internal client. Close the gateway at application shutdown.
- `backend:auth-app`: `JdbcAiLedger` and Flyway V7 provide shared PostgreSQL
  configuration, daily units, tickets, accounting events and circuit state.
  Construct the ledger using the application's `JdbcTemplate`. There is no
  automatic enabled Spring bean or scheduler in this feature.

No dependency on test database support belongs on a production runtime classpath.
The raw `SoclaasClient` stays package-private and has no public provider accessor.
The gateway accepts trusted collaborators for deterministic tests; browser input
must never select or construct those collaborators.

## Responsibilities of each role feature

1. Resolve the actor from the trusted, current server-side session. The request
   contains only project/source IDs, capability and expected source version.
2. Implement `Authorizer` using fresh session, user, project, membership, role,
   object ownership/assignment and source-version checks on **every** invocation.
   The gateway calls it before context, immediately before dispatch and before
   delivery. Reject stale source versions rather than authorizing only an ID.
   A cached positive decision does not satisfy this port.
3. Implement `ContextBuilder` with the capability's bounded field allowlist and
   immutable source snapshot. Supply server-owned instructions/prompt version and
   a JSON object containing untrusted data. Never include arbitrary repository
   data, private role notes outside that role, account data or automatic logs.
4. Run the gateway outside a domain transaction. `JdbcAiLedger` rejects an
   ambient Spring transaction before any context or catalog/completion call.
   Accounting primitives commit independently; an application rollback cannot
   erase a paid provider attempt.
5. Handle fixed `GatewayFailure.Code` values with safe API errors. Preview
   `Suggestion.text` as escaped plain text, with capability, prompt version and
   source version. A suggestion does not perform a mutation. Applying it requires
   explicit user action plus the normal authorized expected-version command.
6. Verify these adapters through role API/DB/browser tests, including revocation,
   source changes and per-project/session selection changes. The synthetic shared
   tests do not certify a future role implementation.

## Policy, publication and accounting

`Policy.disabled()` has zero budgets. Enabled policies require positive finite
shared/per-actor UTC daily units, at most four shared active requests and one
per actor, and a reservation lifetime of 31–60 seconds. The transport has a
30-second deadline. Request units reserve the conservative UTF-8 input estimate
plus framing and 1,200 maximum output tokens; no request reserves over 9,200 units.
Budgets and capacity are shared across projects, capabilities and backend instances.

The configuration owner must call `ledger.configure(policy, metadata, now)` on
trusted configuration changes. Publication commits separately from admission,
including when a newer policy denies admission or disables AI. Disable accepts
null metadata and preserves its previous high-water mark. Revisions are positive,
monotonically increasing, server-owned and immutable; changing budgets requires a
new policy revision, and refreshing model/funding validity requires a new metadata
revision. The gateway also publishes its snapshot before admission. An old
instance cannot republish an older configuration after a newer one is committed.
There is no browser/operator configuration endpoint or hot-reload service here.

`Metadata` binds one allowlisted model identity, metadata revision, funding
revision and expiry (at most 15 minutes away). SoCLaaS catalog access validates
model availability; arbitrary catalog price fields are never converted to money.
These ledger units represent conservative token resources, **not invoiced cost**.
Production monetary funding/price validation must be independently verified
before an operator enables live traffic. No live provider was needed for testing.

Reserved tickets can be cancelled or expired with zero charge before dispatch.
Dispatch commits before the single completion POST. Valid positive bounded usage
can reduce the reserved charge; absent/inconsistent usage, interruption, timeout
and uncertain provider attempts retain the full reservation. Settlement replay is
idempotent only for the identical original result. Expired or conflicting workers
cannot settle or dispatch again. Usage stays in its original UTC day across
midnight; active capacity does not reset at midnight.

The facade performs bounded expiry maintenance before an admitted attempt.
An operator may also call `expire(now, limit)` (1–1,000) for recovery while idle.
Maintenance accounts for disappeared work and never retries/resumes it. Three
qualifying provider failures open the shared circuit for 30 seconds, then one
atomic probe may run. Circuit generations prevent an old success from closing a
newer open circuit. No transaction remains open during provider work.

Accounting stores numeric units and bounded actor/project/capability/model/revision
metadata, ticket times/states and event codes. It stores no prompt, report body,
suggestion, credential or session token. AI accounting is separate from business
audit. Missing provider usage remains unknown. Failed accounting blocks suggestion
delivery and is recoverable through conservative expiry.

See the [canonical specification](../workflow/specs/secured-ai-gateway.md) for
criteria and the [implementation evidence](../workflow/reporter/evidence/shared-ai-gateway.md)
for executed checks and remaining integration work.
