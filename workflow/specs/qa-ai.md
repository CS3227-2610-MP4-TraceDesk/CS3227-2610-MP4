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
text next to the report. It does not accept freeform prompts or attach evidence.

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
- Live SoCLaaS QA generation and suggestion quality were not evaluated.
