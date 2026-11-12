# QA contribution evidence index and reflection draft

Issue #71. This document indexes the QA contribution evidence available in the repository and the merged pull requests. It is a draft for contributor review; it does not claim personal sign-off, course acceptance, deployment, or production readiness.

## Product and scope

TraceDesk is a secured bug-reporting, investigation, and QA-verification application. The QA reviewer receives a Developer resolution, checks it against the original report, records verification, and closes or sends back the resolution. The QA contribution extends this workflow with revision feedback, review history, reusable test templates, project-scoped discussion, and review-state continuity.

The governing contracts are the [QA verification specification](../../specs/qa-verification.md), [QA review-history specification](../../specs/qa-history.md), [QA templates specification](../../specs/qa-templates.md), [QA discussion specification](../../specs/qa-discussion.md), and [QA workspace-continuity specification](../../specs/qa-workspace-continuity.md). The shared [acceptance-criteria index](../../acceptance-criteria.md) currently lists QA queue, AI, verification, and assignment contracts; it does not yet index the newer #64–67 specifications. Their issue-level criteria remain in the linked specification files.

## Implemented QA work and evidence

| Issue / merged PR | Contribution | Specification and implementation evidence | Verification evidence |
| --- | --- | --- | --- |
| [#63 / PR #72](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/pull/72) | Preserves QA send-back reasons on the matching Developer resolution revision, including earlier revisions. | [TD-QAV-05–06](../../specs/qa-verification.md#developer-handoff-issue-63); staff resolution history remains project-scoped and the reason is rendered as plain text. | PR summary records send-back/re-proposal coverage, plain-text rendering, and Reporter/cross-project denial. The PR’s [checks](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/pull/72/checks) show 14 successful checks. |
| [#64 / PR #75](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/pull/75) | Adds paginated “Reviewed by me” history and a read-only verification snapshot tied to the decided resolution revision. | [TD-QAH-01–03](../../specs/qa-history.md); implementation includes [`QaReviewHistory`](../../../frontend/src/qa/QaReviewHistory.tsx), its [rendering test](../../../frontend/src/qa/QaReviewHistory.test.tsx), and the history API. | PR summary records the isolated resolution-persistence suite passing with two reviewers and role/project-denial coverage, plus three focused UI tests. [Checks](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/pull/75/checks): 14 successful. |
| [#65 / PR #76](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/pull/76) | Adds project-scoped QA test templates and copies selected guidance/checks into a verification draft as independent pending checks. | [TD-QAT-01–03](../../specs/qa-templates.md); [`TemplateController`](../../../backend/auth-app/src/main/java/com/tracedesk/auth/verification/TemplateController.java), [`TemplateStore`](../../../backend/auth-app/src/main/java/com/tracedesk/auth/verification/TemplateStore.java), [V17 migration](../../../backend/auth-app/src/main/resources/db/migration/V17__qa_templates.sql), and [template application tests](../../../frontend/src/qa/applyQaTemplate.test.ts). | PR summary records isolated PostgreSQL CRUD/authorization and five focused frontend tests passing. [Checks](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/pull/76/checks): 14 successful. |
| [#66 / PR #74](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/pull/74) | Adds human review comments visible only to active QA reviewers in the project. | [TD-QAD-01–03](../../specs/qa-discussion.md); [`DiscussionController`](../../../backend/auth-app/src/main/java/com/tracedesk/auth/verification/DiscussionController.java), [`DiscussionStore`](../../../backend/auth-app/src/main/java/com/tracedesk/auth/verification/DiscussionStore.java), [V18 migration](../../../backend/auth-app/src/main/resources/db/migration/V18__qa_discussion.sql), and [discussion UI test](../../../frontend/src/qa/QaDiscussion.test.tsx). | PR summary records isolated PostgreSQL coverage for report/project isolation, pagination, reviewer attribution, and audit rollback. [Checks](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/pull/74/checks): 14 successful. |
| [#67 / PR #73](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/pull/73) | Restores the selected tab and unsaved verification draft per bug inside the signed-in project workspace, protects stale drafts, and adds keyboard-accessible resource shortcuts. | [TD-QAW-01–03](../../specs/qa-workspace-continuity.md); [workspace implementation](../../../frontend/src/qa/QaWorkspace.tsx) and [workspace tests](../../../frontend/src/qa/QaWorkspace.test.tsx). | PR summary records two-bug draft and stale-version coverage plus browser assertions for shortcut focus. [Checks](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/pull/73/checks): 14 successful. |

GitHub reports all five pull requests merged into `main`; each merged PR reports 14 successful checks. The PR summaries are the source for the focused test results above. The check links are CI evidence for those merged revisions; they are not deployment or production acceptance evidence.

## Process evidence and reflection draft

The QA work was delivered as issue-scoped changes with explicit acceptance criteria, implementation, tests, documentation, and reviewable pull requests. Each feature was linked to a specification before its behavior was treated as complete. The changes used separate issue branches and pull requests, and the merge sequence incorporated shared-branch updates and migration ordering before the final PR checks. The resulting evidence can be followed from each issue specification to its implementation, tests, and CI result in the table above.

A focused read-only review of the #65 and #66 persistence/controller changes checked QA-role and active-membership authorization, CSRF on writes, project/report query scoping, transaction boundaries, and migration/audit constraints. That review found no material security or data-scoping findings. It was a bounded review of those changes, not a claim that every QA feature received an independent review.

The contribution demonstrates specification-led QA workflow implementation and focused verification in isolated database and frontend checks. It does not establish that every planned role feature is finished or that production behavior has been accepted. The contributor should add a personal reflection, confirm ownership and wording, and sign off before this draft is used as individual-contribution evidence.

## Run observations and handoffs

- For #64, the coordinator retained authorization and persistence ownership. The
  presentation handoff used the history entry contract and existing UI patterns.
  The separate read-only review checked reviewer and tenant scope and found a
  duplicate test selector and an encoding artifact. Both were corrected before
  the branch passed its focused checks.
- For #65, #66 and #67, bounded handoffs covered the existing template editor,
  comment presentation and contextual shortcuts. The coordinator owned server
  boundaries and cross-branch integration. The #65 and #66 server review checked
  the final implementation against the QA-only contract.
- The combined checkout passed 129 frontend tests, four focused PostgreSQL tests
  and one real-backend browser flow using synthetic data. The merge sequence
  reserved Flyway V16 for Reporter activity and applied QA templates V17 before
  discussion V18. PR #76 needed a browser assertion scoped to the status badge
  after Reporter activity introduced a second matching label. The focused
  browser rerun and the final remote checks passed.
- Agent context bytes, token counts, per-agent elapsed time and coordination
  overhead were not measured for these QA slices. The recorded retries and test
  counts describe the observed work. They cannot establish time or token savings
  against a single-agent baseline.

Related QA evidence includes [assignment](qa-assignment.md),
[verification CI repairs](qa-verification-ci.md), and
[AI review and follow-up](../../specs/qa-ai.md#verification). See the existing
[Reporter reflection](../../../docs/Reflections.md#reporter-role) for its own
measured coordination examples and the [Developer evidence index](../../developer/evidence/README.md)
for that contribution's recorded work.

## Pending evidence and sign-off

- **Contributor sign-off and personal reflection:** pending. No personal statement or teammate evaluation is inferred here.
- **Developer AI issue #31:** pending; this QA index does not claim its implementation or verification.
- **Live evidence issue #70:** pending; the linked QA checks are not live-provider or live-product evidence.
- **Deployment issue #69:** pending; no online deployment or production/development separation acceptance is claimed.
