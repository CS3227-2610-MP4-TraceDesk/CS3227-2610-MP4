---
name: tracedesk-ai-integration
description: Integrate or modify TraceDesk AI capabilities, provider transport, context selection or shared gateway accounting while preserving authorization and bounded resource use.
---

# TraceDesk AI integration

Read [AI policy](../../../docs/ai-policy.md), [architecture](../../../docs/architecture.md)
and the applicable feature criteria. Consult [reuse inventory](../../../docs/reuse-plan.md)
before extracting more source; migration status is component-specific.

Keep raw transport internal. Role services must use a secured gateway that resolves
the trusted actor, authorizes context, admits usage, dispatches with bounds, settles
once and rechecks access before delivery. Do not wire the extracted transport directly
to a controller or treat model validation as authorization.

Move admission/accounting/circuit/audit persistence together. Fresh TraceDesk
migrations must replace old account foreign keys and fixed incident feature enums.
Use typed field allowlists and server-owned versioned prompts. Generated text is
untrusted advice; no automatic lifecycle changes, code execution or tool calls.

Use synthetic provider fixtures first. Verify deadlines, body limits, malformed
output, safe diagnostics, no completion retry and cancellation. For gateway work,
also verify isolated-database concurrency, settlement replay, expiry, stale revision
fencing and forbidden access. Record actual evidence and remaining integration gaps.
