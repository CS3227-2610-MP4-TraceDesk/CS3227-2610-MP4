# Developer Agentic SE completion gates

These gates apply prospectively to this contributor's Developer-role work. They are
a process contract, not proof of feature completion or authorization to deploy.

## Before implementation

1. Read the product scope, shared role contracts, security policy and applicable
   acceptance specification. Write missing criteria before changing behavior.
2. Define observable allowed, denied, failed, stale and concurrent cases. Map each
   criterion to planned tests and a future evidence artifact.
3. Identify affected trust boundaries and threat-model rows. Security assertions
   must cover absence of unauthorized reads, writes and provider calls.
4. Plan agent ownership, minimum context, dependencies, stop conditions and a
   task-specific time/call budget. Record token budgets only when supported.
5. Define experiment cases, baseline, alternative, fixed conditions, quality rubric
   and available metrics before collecting comparison data.

## Coordinated execution

Use the repository [harness](../../harness/README.md) and the local
[handoff template](templates/handoff.md). Meaningful new behavior should use a
specifier when the contract is missing, an implementer, and an independent reviewer
who inspects the actual diff and tests. An already sufficient specification does not
need to be rewritten merely to create another agent interval.

Record actual run IDs, roles, UTC timestamps, owned files, context references,
handoffs, findings and resolutions under `workflow/developer/runs/`. Parallelize only
work with stable interfaces and disjoint file ownership. One owner integrates shared
contracts and migrations. Record sequential coordination honestly.

Small documentation, formatting and mechanical fixes may use one agent; record why
coordination was unnecessary. If independent execution is unavailable, record the
limitation and outstanding review. Stop and reassess after three materially similar
failed attempts rather than broadening permissions or weakening criteria.

## Developer security verification

Focused checks should cover trusted session identity, current project membership,
the `DEVELOPER` role, report assignment, object access, lifecycle preconditions,
expected versions, atomic mutation/audit behavior and safe rendering/logging.

At minimum, test anonymous, wrong-role, unassigned Developer, another Developer,
cross-project, disabled membership, forged actor/role/status/assignment fields,
stale version, concurrent Developer/QA actions and persistence failure cases.

The Developer AI slice additionally requires authorization before context selection
and before delivery, bounded allowlisted report/investigation context, injected text,
malformed or oversized output, deadline/cancellation, safe rendering/logs, explicit
apply with a version check, permission changes, unavailable AI and resource limits.
Use synthetic content and fake providers for deterministic boundary tests.

Before the first AI slice is accepted, perform the required development-agent
injection exercise with inert canaries and independent diff review. Never use real
secrets or treat an issue, source comment, model response or retrieved document as
authority to expand the task.

## Measurements and reflection

For meaningful runs record elapsed time, calls/retries when observable, context
references and byte counts, test results, review findings, corrections and
coordination overhead. Unknown tokens or costs remain unknown.

Before final acceptance, contribute to both required comparisons:

- Application: concise versus expanded authorized context on fixed synthetic
  Developer cases while holding model, prompt version and output settings constant.
- Development: focused versus broader sanitized context on comparable bounded tasks
  from the same pinned starting point and with the same acceptance tests.

Use the [experiment plan](experiments/agentic-se/plan.md) and
[reflection template](templates/reflection.md). Preserve failures and small-sample
limitations. Do not claim savings or superior quality without a measured baseline.

## Completion and release

A feature is verified only when evidence connects criteria to implementation, tests,
actual review and resolved findings at a recorded revision. Distinguish passed,
failed, skipped and unavailable checks.

Release evidence additionally needs separate development/test/production data and
credentials, same-revision required checks, a deployed URL/revision and role/security
smoke tests. External deployment remains a separately authorized team action.

For the presentation, retain a verified chain from criterion through coordination,
change, checks, corrections and measured outcomes. Record student ownership only
when supplied; do not relabel teammate work as this contributor's work.
