# QA review suggestions

Issue #53 adds a read-only review of the selected report awaiting verification.
The POST requires CSRF and the expected report version. The gateway rechecks
live QA membership, report state, and version before dispatch and delivery.
Context contains report behavior and the latest proposed resolution, excluding
logs, fix references, private notes and identities. The result contains up to
five regression checks and five evidence gaps. Closing the panel or leaving the
report discards pending results. No automatic retries or domain writes.

## Contract

`POST /api/projects/{projectId}/reports/{reportId}/qa-suggestions` takes
`{ "expectedVersion": 3 }` and returns capability `QA_PLANNING`, prompt version
`qa-planning-v1`, the report ID and version, and the two text arrays. A stale
source or a report outside verification returns 409. Oversized saved context
returns 400, invalid model output returns 502, and unavailable AI returns 503.

The panel discloses the provider transfer before generation and renders advisory
text next to the report.

- **TD-QAI-01** After generation, a reviewer can ask a follow-up in the same
  panel. The request adds a bounded question, the displayed plan, and at most
  two previous question/answer pairs to the same versioned endpoint. It uses
  `qa-follow-up-v1` and returns an answer plus advisory checks and evidence gaps.
  The gateway applies the same QA authorization, version checks, and limits.
- **TD-QAI-02** Select individual checks from either response and explicitly
  add them as Pending to the current Verification draft. Duplicate titles
  are skipped, existing entries and results are preserved, and additions that
  exceed the 30-check limit are rejected. Save verification persists the draft.
- **TD-QAI-03** Conversation and selection stay with the current report version
  while the panel is open. Closing it or changing source version clears them.
  A stale verification draft cannot accept suggestions from another version.

## Verification

Local candidate on `feat/qa-ai-53` based on `488948b`.

- Nine focused backend tests passed across QA and Reporter AI. QA tests
  cover latest-revision selection, context exclusions, role/project denial,
  stale delivery, and rejection of malformed advice.
- Frontend type checking, lint, 92 tests, and the production build passed.
- The real Developer-to-QA browser journey passed against isolated PostgreSQL
  and a synthetic provider, including CSRF denial, rendering both suggestion
  lists, mobile layout, and accessibility. The real Reporter AI browser test
  also passed. The broader Reporter report journey exceeded its 30-second
  timeout during the final sign-in assertion.
- Live SoCLaaS was outside that candidate's verification run.

For the follow-up and checklist additions, frontend checks (92 tests) and the
build passed, along with 10 focused QA and Reporter AI tests. The isolated
real-backend browser test generated a plan and a follow-up, explicitly selected
checks from both, then saved and reloaded them. A separate signed-in browser run
against live SoCLaaS returned HTTP 200 for a follow-up, added a Pending check to
the draft, and retained the conversation when switching tabs. The live check
left the saved demo checklist untouched.
