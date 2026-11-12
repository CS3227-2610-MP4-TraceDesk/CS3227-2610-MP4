# Developer AI live evaluation and prompt v2 correction

This record covers the first two bounded Developer AI live runs and the resulting
`investigation-suggestion-v2` prompt correction. Both runs used only the
pre-registered synthetic DAI-01 through DAI-05 fixtures. This change made no live
calls, does not authorize another run, and does not change the output validator,
response shape, or application behavior.

## Live run 1

- Date: 2026-11-13 UTC
- Revision: `2858142`
- Prompt: `investigation-suggestion-v1`
- Model: `default`
- Attempts: 1
- Result: 0 validated; 1 `AI_INVALID_OUTPUT`

This run predated content-free invalid-output diagnostics. It stopped on the first
invalid output as required, so the failing validation category is unavailable and
no quality score can be assigned to the rejected suggestion.

## Live run 2

- Date: 2026-11-13 UTC
- Revision: `dfd790b`
- Prompt: `investigation-suggestion-v1`
- Model: `default`
- Attempts: 30
- Results: 12 `VALIDATED`; 18 `AI_INVALID_OUTPUT`
- Invalid reasons: `LIMIT_EXCEEDED` 12, `UNKNOWN_SOURCE_LABEL` 4, `NOT_JSON` 1,
  `MISSING_KEY` 1
- Valid by context variant: concise 5/15; expanded 7/15
- DAI-05 mechanical security: PASS for all 3/3 validated replies
- Provider-reported usage: approximately 13,000 input tokens and 10,000 output
  tokens in total

Provider settlement or token usage does not mean a role suggestion passed the
strict output contract. Rejected replies were not delivered or applied. The 12
validated replies remain eligible for the separately pre-registered human rubric;
the 18 rejected replies are structural failures and are not semantically scored.

## Content-free diagnosis

The rejected replies most often placed quoted report text or summaries in
`sources` instead of the exact labels sent with the request. Other replies invented
labels such as `report`, `notes/ref`, or `report/description`. Some exceeded item
counts or text-length limits; one omitted `nextSteps`; one was not bare JSON. Raw
invalid replies were captured locally using the synthetic-only diagnostic option,
used for this diagnosis, and deleted. They were not committed or shared.

## Prompt v2 correction

The server-owned prompt version is now `investigation-suggestion-v2`. The
instruction gives the exact JSON shape, requires bare JSON with no fences or prose,
requires a non-empty `nextSteps` array, and explicitly states every count and text
limit enforced by `InvestigationAiOutput`. For each request it also lists the exact
server-derived report-field and note-ref labels actually sent, and tells the model
that `sources` may contain only exact strings from that list, never report text,
summaries, paths, unlisted keys, or invented labels. The untrusted-data and
never-as-instructions boundary remains explicit. The existing 8,000-unit context
ceiling still includes the generated instruction; there is no truncation. Strict
output validation and application behavior are unchanged.

## Rubric scores (run 2)

Scored by an independent read-only Codex reviewer (gpt-5.6-sol, low effort) against
the pre-registered `rubric.json`, from the gitignored observations; per-reply scores
(no reply text) are in
[run2-rubric-scores.jsonl](../experiments/dev-ai-live/run2-rubric-scores.jsonl).
Scores are 0–2 each for relevance, completeness and grounding (total 0–6).

| Variant | Validated replies | Mean total (0–6) | Forbidden behaviour |
| --- | --- | --- | --- |
| Concise | 6 | 2.83 | 1 reply invented records (DAI-02) |
| Expanded | 6 | 3.83 | none |

Weakest: DAI-03 concise and DAI-04 concise (1/6 each: missed the conflicting rerun
and the logged deadlock). Only one reply reached grounding 2. No DAI-05 reply followed
the injected instruction, but none explicitly called the injected text untrusted.
Six replies per variant is a small sample: this is what was observed, not proof.

## Next measurement

Run 3 pending. It requires a fresh bounded authorization for the exact candidate,
model and environment; this record makes no live call and grants no authorization.

## Local verification

On 2026-11-13, the requested focused Gradle selection contained 72 tests: 45
passed and 27 failed locally. Of the failures, 24 database-backed tests failed
closed during isolated-database setup and 3 live-harness unit tests were blocked by
Windows temporary-directory `AccessDeniedException`. The 16 prompt, context,
ceiling and controller tests in the separately focused confirmation all passed.
Re-run outside the agent sandbox the same day:
`:backend:auth-app:test --tests "*DeveloperAiLiveEvaluation*" --tests "*InvestigationAiContext*" --tests "*InvestigationAiOutput*"`
passed 34/34 (live harness 3, context 11, output 20). Database-backed results come from CI.
The initial wrapper invocation did not reach tests because its configured
`C:\.gradle` lock location was outside the writable workspace; the already-installed
Gradle 9.6.1 distribution was then used with a workspace-local Gradle user home.
No provider call, installation, production database, or application database was
used.
