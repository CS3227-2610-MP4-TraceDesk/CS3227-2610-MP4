# QA verification

Issue #58. Signed-in QA reviewers record checks and decide the outcome of the
latest proposed resolution. Each resolution starts with its own checklist.

- **TD-QAV-01** Only active QA members of the report's project may read or
  mutate verification. Writes require CSRF and the expected report version.
- **TD-QAV-02** Board and List show the same checks. Save up to 30 unique
  titles (300 characters each), results, check notes and verification notes.
  A save keeps the report in READY_FOR_QA and increments its version.
- **TD-QAV-03** Close requires at least one check and all checks passing.
  Send back requires a reason and moves the report to IN_PROGRESS. The
  checklist, outcome, version and business audit event commit atomically.
- **TD-QAV-04** GET and POST under the report's /qa-verification endpoint
  return the current resolution review and report version. Stale writes are
  rejected, and prior resolution checks never carry over automatically.

The awaiting-verification queue removes a report after a decision. The
send-back reason is stored with its resolution. [QA review history](qa-history.md) is tracked in issue #64.

## Developer handoff (issue #63)

- **TD-QAV-05** The staff-only resolution list returns `rejection: null` until
  QA sends back that revision. After send-back it returns `reason`,
  `reviewerUserId`, and `createdAt` from the saved verification and its matching
  audit event. This reads existing records and accepts reasons saved under the
  current verification limit. It adds no new writes or lifecycle transitions.
- **TD-QAV-06** The Developer sees the reason as plain text beside the relevant
  revision, including in Earlier revisions. Later revisions do not inherit it.
  Reporter and nonmember requests remain denied. The response excludes
  checklist results and private verification notes.

AI suggestions can be added to the Verification draft through explicit
selection. [QA review discussion](qa-discussion.md) is scoped to active QA
reviewers in the project.

## Local verification

- Frontend typecheck, lint, 92 unit tests, and production build passed.
- Isolated PostgreSQL tests exercised both decisions, stale-version rejection,
  revision isolation, and rollback when the audit event could not be saved.
- The real-backend browser test saved and reloaded a QA check and notes and
  exercised the missing-CSRF and wrong-role responses. It used synthetic
  data and the deterministic AI fixture.
