# Shared secured AI gateway and usage accounting

Feature: SHARED-AI-GATEWAY, GitHub issue #39. The user authorized creation and implementation of
this shared prerequisite for Reporter #17 and Developer #31 on 2026-11-09.
Human/student owner has not been supplied. This is a shared backend capability;
the Reporter contributor workflow records this implementation's process evidence.
Architecture and ADR 002 already authorize the component boundary. No production
provider configuration, deployment, currency pricing or live request is authorized
by this specification.

## Scope and integration boundary

Implement one secured, non-streaming suggestion facade, provider-independent
application ports, an internal SoCLaaS bridge and fresh PostgreSQL accounting
persistence. Keep admission, circuit and ledger updates transactional. The raw
`SoclaasClient` remains package-private; role services cannot obtain a bypass
client. The public facade is constructed with trusted server-owned collaborators.

This slice deliberately has no role HTTP endpoints, UI, role prompt templates,
role context repository implementation, apply action or lifecycle mutation. Each
role slice supplies its independently tested authorization/context policy and
server-owned versioned prompt. Synthetic collaborators exercise the shared facade
without pretending those later slices are integrated. Ordinary bug management
must not require AI availability. No automatic tools, code execution, background
request resumption, streaming or completion retries are added.

## Stable logical ports

Concrete Java names may follow existing package conventions; equivalent signatures
must preserve these boundaries:

```java
Suggestion suggest(SuggestionRequest request);
// Request: projectId, sourceId, capability, expectedSourceVersion. No actor,
// prompt, provider URL, credential, model, budget or role from an HTTP caller.
Actor currentActor();                             // ActorResolver
void authorize(Actor actor, SuggestionRequest request); // CapabilityAuthorizer
ContextSnapshot build(Actor actor, SuggestionRequest request); // ContextBuilder
ModelMetadata metadata();                        // internal ModelProvider
Completion complete(ContextSnapshot context);    // internal ModelProvider
```

Capabilities are `REPORT_IMPROVEMENT`, `INVESTIGATION`, and `QA_PLANNING`.
`ContextSnapshot` contains immutable source version, prompt version, trusted system
instructions and allowlisted JSON object data. `Suggestion` contains bounded text,
capability, prompt version and source version. Actor is resolved once from trusted
request/session context; subsequent checks validate its current access, including
session validity. These are not browser identity claims. Ports carry Java/domain
values, never HTTP, Spring, JDBC or provider JSON node types.

The accounting port atomically reserves a server-generated ticket, marks dispatch,
settles with the expected ticket revision, and expires eligible tickets. Each
transition returns a typed outcome; callers cannot forge a successful admission
from an arbitrary UUID. PostgreSQL transactions must not span provider calls.
The facade asks the ledger to reject any ambient domain transaction before
context selection or a provider catalog/completion call. Ledger primitives use
independent transactions, so an enclosing rollback cannot erase paid usage.

## Authorization, context and delivery

1. Validate UUIDs, capability and nonnegative expected version. Resolve the actor.
2. Authorize project membership, capability role and object ownership/assignment
   before invoking the context builder. Denial must cause zero context selection,
   ledger reservations and provider completions.
3. Build only the role's allowlisted snapshot and compare its source version to the
   request. Require a nonblank bounded prompt version and system instruction;
   data must be a JSON object. Text remains untrusted data, separate from system
   instructions. Never ingest files or repositories implicitly.
4. Validate enabled policy, fresh model/funding metadata and shared admission.
   Immediately before dispatch, fence expired/revoked metadata or reservation.
5. Perform at most one bounded completion. Validate nonblank bounded advisory
   output; no tool/function calls. Settle even on failure or cancellation.
6. Reauthorize the actor and source version before returning any content. Revoked
   membership/session/assignment or a changed source causes a safe denied/stale
   outcome with no suggestion delivery. Accounting still reflects any dispatch.

The gateway never applies suggestions. Later role apply commands require explicit
user action and the destination mutation's ordinary authorization, lifecycle,
concurrency and business-audit rules. A role contract may require a source-version
check when the destination operation overwrites versioned source data, but the
gateway does not create a blanket AI-provenance requirement.

## Resource policy and funding meaning

Default: disabled, zero budgets. No provider call is admitted until an operator
explicitly supplies positive finite budgets, a known model and a validated funding
snapshot. Enabling/configuration is a later deployment action. Invalid, missing,
negative, overflowed, stale or ambiguous model/funding data fails closed.

Accounting uses conservative normalized resource units; it is not exact money,
provider billing or task success. The implementation records the unit policy and
funding/model revisions used for each ticket. It must not infer provider prices
from arbitrary model catalog fields. A production pricing/funding adapter requires
verified SoCLaaS semantics before production enablement; synthetic fixtures may
supply explicit validated snapshots. No silent unlimited or zero-price fallback.

Transport ceilings remain 30 seconds request, 3 seconds connect, 64 KiB response,
16 KiB input/output, 8000 input-token estimate and 1200 output tokens. UTF-8 bytes
plus framing provide the conservative input estimate, not a tokenizer. Gateway
limits cannot exceed these. Completion POST is never retried. Catalog GET may
retry once within its original deadline. Cancellation/interruption cancels pending
transport work and preserves interruption state.

Safe initial upper limits: four shared active requests, one per actor; configured
budgets may reduce these. One reservation expires after 60 seconds, greater than
the maximum completion request deadline. Model/funding validity cannot exceed
15 minutes. A deployment can choose stricter bounds. UTC accounting windows are
fixed daily windows, and a ticket remains associated with its admission window
through settlement, including across midnight.

## Atomic ledger, replay and recovery

A reservation records ticket ID, actor/project/capability, window, model/funding
revision, conservative reserved units, timestamps, revision and state. No prompt,
input, model output, credential or session token is persisted. Admission atomically
checks shared and actor outstanding capacity and `charged + reserved + requested`
against both budgets. Concurrent requests cannot oversubscribe either budget.
Multi-process safety comes from PostgreSQL locking/constraints, not JVM locks.

States are reserved, dispatched and terminal. Mark-dispatched succeeds exactly
once with matching live reservation/version and valid metadata/configuration.
Reserved expiry releases capacity and units because no provider dispatch occurred.
Dispatched success with trustworthy, bounded usage charges the conservative
normalized usage; missing, inconsistent, negative or above-bound usage, provider
failure, cancellation and uncertain dispatch charge the full reservation. In no
case can a post-dispatch failure become a free request.

Settlement and expiry atomically release capacity, move reserved units to the
appropriate charged total and append a bounded accounting event. Repeated identical
settlement has no additional effect. A terminal ticket cannot be rewritten by a
conflicting replay. Revision fencing rejects delayed workers after expiry or other
terminal transition; they cannot release or charge capacity again. Expiry of a
dispatched ticket conservatively charges its reservation. Maintenance is bounded
and safe to run concurrently; it accounts for work and never resumes requests.

Business audit and AI accounting are separate. Accounting event failure rolls
back its ledger/counter transition. Circuit and admission state remain coherent
with the associated transaction. Expose bounded safe outcome categories rather
than arbitrary provider/database exception text.

Trusted configuration publication is a separate committed transaction before
admission; a newer disabled policy or a newer policy whose admission is denied
still fences older instances. Disabled publication accepts no metadata and
retains the previous metadata fence. Same-revision policy/model/funding/expiry
equivocation is rejected; refresh of metadata validity requires a new revision.
Backend configuration owners must explicitly publish a disable/refresh when it
changes, independently of whether any role requests a suggestion. No public
configuration-management endpoint or hot-reload scheduler is supplied here.

## Circuit and stale-revision fencing

Qualifying provider unavailability/timeouts/invalid responses update a shared
circuit; authorization, validation and local quota denials do not. Initially, three
consecutive qualifying failures open the circuit for 30 seconds. Open circuits
reject completion admission. After cooldown, allow one atomic half-open probe,
not one per application instance. A successful current-generation probe closes
and resets failures; failure reopens. Circuit generation/revision fences delayed
success/failure from older requests, so it cannot close a newer open circuit.
Model/funding refresh must reject stale revisions and cannot revive an older
configuration. All state remains bounded even if the worker disappears.

## Safe results, diagnostics and observability

Results distinguish forbidden, stale source, invalid request, unavailable/disabled,
quota/capacity rejection, timeout and invalid provider output using fixed categories.
Never expose raw provider bodies, SQL exceptions, input text or secrets. Record
only bounded capability/model/prompt-version/outcome/latency/size and optional
provider-reported token counts. Missing usage remains unknown, not zero. Metadata
and ledger event values are bounded and cannot become a payload logging channel.

## Observable acceptance criteria

- **TD-AIG-01 — Sole secured boundary:** all three capabilities use one facade;
  trusted actor resolution and initial authorization precede selection/admission;
  denied/cross-project/forged-actor paths cause no selection or provider call.
  Transport remains inaccessible as a public completion facade.
- **TD-AIG-02 — Bounded advisory contract:** immutable versioned context, separate
  system/data, server-owned prompts and bounded output; malformed data/output,
  tools and excessive sizes fail safely. No business mutation or apply operation.
- **TD-AIG-03 — Fail-closed policy:** disabled defaults, finite positive configured
  quotas, validated fresh model/funding revisions; absent/stale/ambiguous data and
  quota overflow never dispatch. Resource units are identified as non-currency.
- **TD-AIG-04 — Atomic shared admission:** isolated PostgreSQL concurrent global
  and actor admission proves budget/capacity ceilings across store instances and
  daily rollover; transaction failure leaves no partial counters/events/ticket.
- **TD-AIG-05 — Single settlement and recovery:** success/unknown/failure paths,
  settlement replay, conflicting replay, reserved/dispatched expiry, event rollback
  and delayed revision fencing preserve counters, conservative charges and
  capacity. Concurrent expiry/settlement has one terminal effect.
- **TD-AIG-06 — Circuit and metadata fences:** shared open/half-open behavior,
  one concurrent probe, cooldown and stale worker/metadata revision rejection are
  proved with deterministic clocks and isolated database concurrency.
- **TD-AIG-07 — Delivery authorization:** membership/session/assignment removal
  and source-version changes during a synthetic completion suppress content;
  settlement still occurs. Initial denial never reads context.
- **TD-AIG-08 — Provider failure isolation:** synthetic HTTP tests prove bounded
  deadlines, cancellation/interruption, no completion retry, malformed/oversized
  response and tools rejection; adapter only accepts validated configured model.
- **TD-AIG-09 — Safe evidence and handoff:** tests assert no secret/payload log or
  unsafe exception leakage; independent review maps actual diff to criteria;
  evidence records revision/check outcomes and accurately lists pending role
  integration, pricing/funding validation, live quality and deployment work.

## Planned evidence and implementation status

Use pure gateway tests with synthetic trusted actors/context/providers, existing
transport fake-HTTP tests, and isolated PostgreSQL ledger tests. Include denied,
failed, stale and concurrent assertions about absence of provider calls and
partial writes. No live provider is needed for these checks. Required database CI
must fail when isolation configuration is missing rather than silently skip.
Threat boundaries are AI context disclosure, quota/cost exhaustion, provider-output
injection, stale permissions and malicious development-agent handoffs in the shared
threat model. Role browser journeys, application context-quality comparisons and
live provider/funding validation remain their owning milestones, not evidence of
this backend foundation's completion. The development-agent injection exercise
must run before the first integrated AI slice is accepted.

Status: specified for implementation; no criterion is claimed verified by this
file. Record actual checks and independent review in contributor evidence. Routine
technical defaults above fit the approved architecture. Provider funding/currency
semantics and deployment credentials remain unresolved integration work; fail
closed until independently validated, rather than inventing production semantics.
