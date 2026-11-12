# TraceDesk engineering workflow

Start from [the foundation decision](decisions/001-foundation.md) and
[shared role contracts](decisions/002-shared-role-contracts.md), then locate the
applicable contract through [the acceptance index](acceptance-criteria.md).
Specifications own criteria; the index only links to them.

## Contributor workflows

This directory retains shared decisions, acceptance criteria and baseline templates.
Personal process choices and evidence live in contributor subdirectories:

- [Reporter workflow](reporter/README.md): the Reporter contributor's retained
  Agentic SE process, experiments, tools and historical evidence.
- [Developer workflow](developer/README.md): the Developer contributor's
  process, templates, experiments, presentation evidence and future run records.

Teammates may maintain their own directories. Shared architecture/security contracts
and course requirements continue to apply across contributors.

## Feature loop

1. State the feature's actors, preconditions, inputs/limits, output, allowed and
   denied paths, stale/concurrent behavior, data/audit effects and AI boundaries.
2. Record observable criteria and relevant design decisions before implementation.
   Existing user authorization covers ordinary decisions within the approved scope.
3. Implement a bounded change with focused allowed/denied/failure tests.
4. Record commands, revision or working-tree identity, results and actual omissions.
5. Obtain an independent review for meaningful security/shared behavior when the
   host/team supports it; record actual reviewer identity and scope. A self-review
   must not be labeled independent.
6. Resolve findings and update evidence. Deployment/release requires an authorized
   action and relevant same-revision checks; foundation approval is not release approval.

Use [agent coordination](../harness/README.md) for multi-agent tasks. Avoid automatic
multi-agent fan-out for simple edits. Record actual task handoffs, not fictional runs.

## Templates

- [Feature specification](templates/feature-spec.md)
- [Handoff](templates/handoff.md)
- [Verification/review evidence](templates/evidence.md)
- [Reflection and resource experiment](templates/reflection.md)

Only mark a criterion complete after its evidence exists. Passing transport tests
does not prove role authorization, database safety or deployment readiness. Keep
failed/skipped/unavailable checks visible. Do not copy raw provider payloads,
credentials, user content or private agent reasoning into evidence.

## UI research

For substantial UI design investigations, see the [UI research workflow](ui-research/README.md) and its [runner specification](specs/ui-research-runner.md). It is a development tool; an actual run and review must be recorded before claiming either occurred.
