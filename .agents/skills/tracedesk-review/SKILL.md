---
name: tracedesk-review
description: Review TraceDesk changes against feature criteria, security boundaries, reuse isolation and verification evidence. Use for explicit code reviews or a development handoff to review.
---

# TraceDesk review

Read the applicable criteria, [architecture](../../../docs/architecture.md), actual
diff and evidence. Follow the [reviewer role](../../../harness/roles/reviewer.md).

Prioritize real correctness/security defects over stylistic thresholds. Check
project/object authorization, AI gateway bypass, provider/data bounds, stale result
handling, transaction/accounting races, test isolation and secret-safe diagnostics.
Look for old incident imports, copied schemas and misleading pass/approval claims.

Return findings with severity, source location, concrete impact and the missing
criterion/test. Explain what was reviewed and what remains unverified. Report no
findings if none are supported. Do not invent an independent or human approval.
In a review-only task, report before editing; in an authorized fix-and-review task,
fix in-scope defects and verify them without requesting redundant permission.
