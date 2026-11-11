# QA Developer assignment

Issue #32. The QA contributor owns assignment so eligible Developers can start investigation.
The [report contract](reporter-reports.md) defines the allowed states, reason limits, and access rules.

## Workspace and API

QA can open reports from **All bugs** in the existing QA workspace. The report Summary
contains the current assignment and controls to assign, reassign, or clear it.
Closed reports show the recorded assignment without mutation controls.

- `GET /api/projects/{projectId}/reports/{reportId}/assignment?cursor=…`
  returns `{assignedDeveloperId, assignmentEligible, items, nextCursor, version}`.
  The current assignee can be null. The eligibility flag describes the current assignee.
  Each candidate contains only `id`. Pages are limited to 25 eligible same-project
  Developers, ordered by UUID. The cursor is signed and bound to actor, project, and report.
- `PATCH` to the same route takes `{expectedVersion, developerId?, reason?}`.
  Omitting the Developer ID clears the current assignment. Reassignment and clear
  require a nonblank reason. The limit is 500 Unicode code points and 2,000 UTF-8
  bytes with the shared plain-text rules. Explicit nulls and unknown fields are invalid.
  A successful write returns `{assignedDeveloperId, version, updatedAt}`.
- A closed report, an already-cleared assignment, or a request to assign the current
  assignee returns `INVALID_STATE`. An ineligible target returns `INVALID_TARGET`
  without distinguishing absent, wrong-project, inactive, or wrong-role targets.
  Stale commands return `STALE_VERSION` and the current authorized version.

The server derives the actor from the authenticated session and checks CSRF for
writes. The mutation locks actor/project/membership and target eligibility before
locking the report. Assignment, report version, timestamp, and one business audit
event commit together. Audit records contain prior/new assignee IDs and versions.
Reasons are validated at the boundary and excluded from audit and API output.
The current assignee's historical ID remains after membership changes. Developer
operations continue to require fresh active membership and a current assignment.

## Acceptance criteria

| ID | Evidence |
| --- | --- |
| TD-ASG-01 | QA-only candidate reads and commands enforce project, object, lifecycle and eligibility rules. |
| TD-ASG-02 | Initial assignment, reassignment and clear enforce versioned request and reason validation. |
| TD-ASG-03 | The report and business audit update together. Failed and concurrent requests commit no partial change. |
| TD-ASG-04 | Disabled or former Developers lose access while QA can recover the historical assignment. |
| TD-ASG-05 | API and isolated database tests cover allowed, denied, stale and rollback cases. |
