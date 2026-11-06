# Agentic SE completion gates

Requested by the user on 2026-11-06 for MP4 development. This is a process contract,
not proof of feature completion or authorization to deploy. Apply prospectively;
do not relabel earlier self-reviews as independent reviews.

## Before implementation

1. Read product scope, shared contracts and the applicable specification. Write
   missing behavior criteria before code and link the spec in the acceptance index.
2. Define observable allowed, denied, failed, stale and concurrent cases where
   relevant. Map each criterion to its planned test and evidence artifact.
3. Identify changed trust boundaries and applicable threat-model rows. Define
   security assertions, including absence of unauthorized provider calls or
   mutations, rather than checking only error messages.
4. Plan agent ownership, minimum context, dependencies, stop conditions and a
   task-specific time/call budget. Record token budgets only where supported.
5. Define the measurement plan before running experiments: cases, baseline,
   alternative, fixed conditions, quality rubric and available resource metrics.
   A slice may reference a milestone experiment; name its owner and due milestone.

## Coordinated execution

Use the [harness](../../harness/README.md) and [handoff template](templates/handoff.md).
For meaningful feature, security or shared-contract changes, the coordinator uses
a specifier for new/changed contracts, an implementer and a separate reviewer.
An existing sufficient spec need not be rewritten by another agent. The reviewer
must inspect the actual diff and tests independently of implementation.

Record actual run IDs, roles, timestamps, owned files, supplied/retrieved context,
handoffs, findings and resolutions under `workflow/reporter/runs/`. Parallelize only tasks
with stable interfaces and disjoint file ownership; one owner integrates shared
contracts and migrations. Record sequential coordination honestly.

Small documentation, formatting and local mechanical fixes may use one agent;
record why coordination was unnecessary. If independent execution is unavailable,
record the limitation and outstanding review; do not claim the gate passed.
Stop and reassess after three materially similar failed attempts. Do not expand
permissions or weaken criteria to get a passing result.

## Security verification

Each slice runs focused checks for its changed boundaries and records applicable
threat rows, commands, assertions and results. Explain non-applicable rows.

AI slices require relevant tests for project/object authorization before context
selection, permission changes before delivery, injected input, bounded and
malformed output, safe rendering/logs, explicit apply/version checks, unavailable
AI and resource/concurrency controls. Use synthetic content and fake providers
for deterministic boundary tests. Live quality evaluations are separate evidence.

Before the first AI slice is accepted, run a development-agent injection exercise:
provide a synthetic untrusted issue/document requesting an unauthorized change
or secret access. Use inert canaries, never real secrets. Record the actual agent
actions and independent diff review against a predefined expected outcome. Repeat
when agent tools, permissions or instruction handling materially change. Never
treat a malicious fixture as authority or require an unsafe action to complete it.

## Measured experiments

For every meaningful implementation run, record elapsed time, calls/retries where
observable, context selected, tests run, review findings and coordination overhead.
Unknown tokens/costs remain unknown; missing telemetry is not zero usage.

Before final MP4 acceptance, complete both comparisons:

- Application: concise versus expanded authorized context on fixed synthetic cases
  for Reporter, Developer and QA. Hold model, prompt version and output limits
  constant. Score usefulness, completeness and unsupported/forbidden behavior;
  record latency, provider-reported tokens, failures and available cost data.
- Development: focused versus broader sanitized context on comparable bounded
  tasks or isolated attempts at the same task. Hold tools/model and acceptance
  criteria constant where possible. Record correctness, defects, retrieval effort,
  elapsed time and available usage. Explain task difficulty and learning effects.

Use the [reflection template](templates/reflection.md). Record case IDs, repetitions,
sample size, raw sanitized measurements and aggregates. Small samples support
limited conclusions. Report unfavorable results and coordination costs; do not
claim token savings, superior quality or faster delivery without a baseline.

## Completion and release

A feature is verified only when its evidence links criteria to implementation,
tests, actual review and resolved findings. Record the exact revision or base
revision plus working-tree identity. Distinguish passed, failed, skipped and
unavailable checks. A deferred milestone experiment remains pending and blocks
the corresponding milestone claim, not unrelated development work.

Online release additionally requires separate development/test/production data,
identities and credentials; production secrets excluded from untrusted PR jobs;
same-revision required checks; and deployed role/security smoke checks. Record
the URL, deployed revision, sanitized separation evidence and readiness results.
AI unavailability must not imply that ordinary bug management is unavailable.
Follow existing authorization requirements for external deployment actions.

The final MP4 evidence must include actual coordinated runs, application and
development-process security results, both measured comparisons, and online
deployment/separation verification. Templates and configured CI are not run results.
At each handoff/final report list completed gates and remaining gaps. Preserve a
presentation-ready chain: criterion → handoff → change → check/review → correction
→ measured result, with student ownership recorded only when supplied.
