---
name: tracedesk-spec-development
description: Implement or change TraceDesk behavior using its approved scope, explicit acceptance criteria and verification evidence. Use for feature, architecture, persistence or deployment changes.
---

# TraceDesk specification-driven development

Read root `AGENTS.md`, [workflow](../../../workflow/README.md), and the relevant
contract in the [acceptance index](../../../workflow/acceptance-criteria.md).
If missing, write a bounded specification before code using the
[template](../../../workflow/templates/feature-spec.md).

Existing user approval covers ordinary choices within the approved architecture;
do not add repetitive approval gates. Material unresolved scope or security changes
need a decision. Historical MP3 approvals do not apply.

Map each changed behavior to observable criteria, implementation and evidence.
Include denied, failed, stale and concurrent cases where relevant. Update the
owning specification and tests together; do not weaken tests to fit a defect.
Run focused checks, record exact outcomes using the
[evidence template](../../../workflow/templates/evidence.md), and tick only proven
criteria. Report skips and limitations explicitly.

For tasks that actually need authorized agent coordination, use the
[harness](../../../harness/README.md); a small edit does not require agent fan-out.
