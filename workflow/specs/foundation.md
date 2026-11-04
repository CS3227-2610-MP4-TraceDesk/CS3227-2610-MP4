# TD-FND — Reusable engineering foundation

Scope authorized by [ADR 001](../decisions/001-foundation.md). This is infrastructure
and development-resource setup, not a complete application slice.

## Acceptance criteria

- [x] TD-FND-01: Local skills, harness roles and workflow templates use portable
  links and TraceDesk terminology; no personal profiles or historical approvals copied.
- [x] TD-FND-02: Adapted HTTP transport and settings compile without Spring, JDBC,
  account tables or MP3 package dependencies. Raw transport is non-public.
- [x] TD-FND-03: Synthetic transport tests prove bounded decoding, no completion
  retries, rejected tools, safe diagnostics, deadlines and cancellation.
- [x] TD-FND-04: Configuration is disabled by default, validates the exact trusted
  provider endpoint, bounds limits and does not echo secret values.
- [x] TD-FND-05: Source provenance and adaptation inventory exist; deferred gateway
  and application work is explicit rather than presented as migrated.
- [x] TD-FND-06: A portable foundation check and CI definition run the local resource
  checks and Java tests without production secrets or a database.

## Boundaries and evidence

Use synthetic loopback HTTP only in tests. No live provider call, database mutation,
deployment or historical workflow execution is part of this scope. Test helpers
are test-source-only. [Evidence](../evidence/foundation.md) records actual results;
CI configuration can be locally inspected without claiming a hosted run occurred.
