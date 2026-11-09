# ADR 002 — Shared contracts for role features

Status: accepted direction. This decision defines the common boundaries for
Reporter, Developer, and QA work. Feature specifications will provide concrete
API signatures, schema details, and acceptance tests before implementation.

## Authentication and authorization

- Use server-side sessions. Resolve the authenticated user ID from trusted
  server-side session context, never from a request body or browser-supplied role.
- Store project membership and role per user and project. Every role operation
  checks project membership and the applicable object-level permission on the
  server; a role name alone does not grant access to every bug.
- Use the session and CSRF protections described in
  [security](../../docs/security.md). Role feature code consumes a trusted actor
  and authorization service rather than implementing its own identity source.

## Persistence and lifecycle

- Use UUID identifiers for users, projects, and bugs; UTC timestamps; one ordered
  chain of fresh TraceDesk migrations; and a bug `version` for conflicting edits.
- Persist every successful domain mutation and its business audit event in one
  database transaction. AI usage accounting is separate from business audit.
- Use the shared lifecycle `OPEN` → `IN_PROGRESS` → `READY_FOR_QA` → `CLOSED`.
  A QA rejection returns a bug from `READY_FOR_QA` to `IN_PROGRESS` and requires
  a reason. The transition is an auditable reopen event, not a direct AI action.
- Reporter creates an `OPEN` report; Developer investigates and proposes a
  resolution; QA verifies and closes or reopens. The first domain specification
  must define transition preconditions, object permissions, and version behavior.

## AI gateway

- Expose one secured suggestion interface for all three role capabilities. A
  request identifies the capability and authorized source context/version; the
  result is bounded advisory content with its capability, prompt version, and
  source version. Define concrete types and signatures in the gateway spec.
- The shared gateway owns authorization, capacity/budget admission, provider
  access, settlement, and accounting. It authorizes before selecting context and
  reauthorizes before delivery. Role features cannot call the raw transport.
- Each role previews suggestions and applies them only through an explicit user
  action and a normal authorized application command. That command keeps the
  destination operation's ordinary lifecycle, locking and optimistic-concurrency
  rules; AI output does not create an additional persistence or provenance path.
  AI output cannot directly mutate a bug or make a lifecycle decision.

## Decisions for the first feature specifications

These were not settled by this shared starting point and must be defined before
the affected behavior is implemented:

- Who assigns a Developer and when; whether Reporter editing stops after
  investigation begins; and how clarification requests affect editing.
- Whether Reporter AI accepts an unsaved draft, an existing report, or both, and
  how draft authorization and source-version checks work.
- Concrete schema, API and error contracts, including denied, invalid, stale,
  and AI-unavailable responses; field size limits; and gateway quota limits.
- The example programming language for snippets and AI examples.

This decision records a design contract, not completed authentication, migrations,
role features, or a secured gateway. Each feature specification owns its observable
criteria and verification evidence.
