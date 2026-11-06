# Architecture

Status: approved direction. Shared role decisions are recorded in
[ADR 002](../workflow/decisions/002-shared-role-contracts.md); detailed role
contracts are developed per slice.

```text
React role UI → API/session security → TraceDesk application services
                                       ├─ domain + repository ports
                                       └─ AI coordinator
                                          ├─ authorized typed context
                                          └─ secured gateway
                                             ├─ admission/accounting/circuit
                                             └─ internal SoCLaaS transport
PostgreSQL adapters implement repositories, accounting and audit storage.
```

## Boundaries

Use one deployable backend initially, not microservices. Logical boundaries can
be packages/modules; extract separate libraries only where a boundary earns it.

| Boundary | Owns | Must not depend on |
| --- | --- | --- |
| Domain | Bug lifecycle, verification and invariant types | Spring, SQL, provider, UI |
| Application | Use cases, project/object authorization, repository ports | Provider wire format or incident-specific types |
| AI capabilities | Role-specific context allowlists, prompt versions, output validation | Unrestricted repository reads or arbitrary tool execution |
| Secured gateway | Trusted actor resolution, admission, deadline/cancellation, settlement, audit | MP3 accounts, incident enums, role portals |
| Provider adapter | HTTP transport, bounded decoding, safe provider failures | Accounts, JDBC, domain entities |
| Persistence | Fresh migrations, locking, authorized repository queries | Browser identity claims |

The current `backend/ai-transport` module contains package-private transport
implementation only. It exposes no public invocation facade and is not wired into
an application. A future secured gateway/factory may construct it internally;
application services receive only the secured facade. Do not make the raw client
public to speed up a vertical slice.

## Intended integration ports

Define concrete signatures with the gateway specification, not speculative stubs:

- `ActorResolver`: obtain a trusted authenticated identity.
- `CapabilityAuthorizer`: enforce project, role and object access.
- `ContextBuilder`: assemble an immutable allowlisted snapshot and record version.
- `AdmissionStore`: atomically reserve and settle cost/capacity with audit.
- `ModelProvider`: internal bounded completion and validated metadata operations.
- `Suggestion`: validated advisory content, capability, prompt version, source version.

Keep quota/circuit/audit persistence together during migration so splitting
interfaces does not break atomicity. Reauthorize on delivery; reject applying a
stale suggestion. Never hold a domain transaction across a provider HTTP call.

## Storage and operations

PostgreSQL stores business data, membership, sessions, business audit and AI usage.
AI usage is conservative resource accounting, not exact invoicing or task success.
Separate identities/configuration/databases for development, test and production.
No durable orchestration, scheduler or message broker is needed for the MVP's
single-request AI suggestions. Ledger expiry accounts for a request; it does not
resume work.
