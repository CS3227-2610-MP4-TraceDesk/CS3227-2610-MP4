# TraceDesk engineering workflow

Start from [the approved decision](decisions/001-foundation.md), then locate the
applicable contract through [the acceptance index](acceptance-criteria.md).
Specifications own criteria; the index only links to them.

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
