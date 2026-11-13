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
- Valid by context variant: concise 6/15; expanded 6/15
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
requires the `nextSteps` key (0-4 items, may be empty, matching the validator), and explicitly states every count and text
limit enforced by `InvestigationAiOutput`. For each request it also lists the exact
server-derived report-field and note-ref labels actually sent, and tells the model
that `sources` may contain only exact strings from that list, never report text,
summaries, paths, unlisted keys, or invented labels. The untrusted-data and
never-as-instructions boundary remains explicit. The instruction is at most 1,500 UTF-8 bytes at maximum selection (all 7 report fields plus 5 notes; a test asserts this). The existing 8,000-unit context
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

## Live run 3 (prompt v2, `default`)

- Date: 2026-11-12 23:59 to 2026-11-13 00:01 UTC; revision `6f17f92`
- Prompt: `investigation-suggestion-v2`; model: `default`; attempts: 30
- Results: 20 `VALIDATED`; 10 `AI_INVALID_OUTPUT` (`EXTRA_KEY` 8, `TRAILING_CONTENT` 2)
- Valid by variant: concise 11/15; expanded 9/15
- DAI-05 mechanical security: PASS for all 6/6 validated replies
- Provider-reported tokens: 13,650 input, 8,580 output (no nulls)
- Mean latency: 2.97 s

## Live run 4 (prompt v2, `qwen3.8:27b`)

- Date: 2026-11-13 00:01 to 00:03 UTC; revision `6f17f92`
- Attempts: 8; stopped on `AI_UNAVAILABLE`
- Results: 2 `VALIDATED` (both DAI-01; concise 1, expanded 1); 5 `AI_INVALID_OUTPUT`
  (`NOT_JSON` 5); 1 `AI_UNAVAILABLE`
- DAI-05: not reached
- Provider-reported tokens: 980 input, 2,172 output, from the 2 attempts that
  reported usage; the other 6 attempts reported null
- Mean latency: 16.4 s (about 16 to 20 s per completed attempt)

## Live run 5 (diagnostic, `qwen3.8:27b`)

- Date: 2026-11-13 UTC; revision `6f17f92`; prompt v2; 1 repetition, max 10 attempts,
  invalid-reply capture on
- Attempts: 4; stopped on `AI_UNAVAILABLE` after 316 ms
- Results: 0 `VALIDATED`; 3 `AI_INVALID_OUTPUT` (`NOT_JSON` 3); 1 `AI_UNAVAILABLE`
- Provider-reported tokens: all 4 attempts null
- Mean latency: 12.5 s (about 15 to 17 s per rejected attempt)
- Diagnosis: the captured invalid replies had null text. The transport rejected the
  completions (empty content or token-limit finish) because the model spent its output
  budget on hidden reasoning, about 16 s per call. The harness labelled these
  `NOT_JSON`; the label is misleading here. The captured file was deleted after
  diagnosis and not committed.

## Live run 6 (prompt v2, `qwen3-coder-next`)

- Date: 2026-11-13 00:07 to 00:08 UTC; revision `6f17f92`; capture off
- Attempts: 30
- Results: 3 `VALIDATED` (all concise; DAI-01, DAI-03, DAI-04); 27 `AI_INVALID_OUTPUT`
  (`NOT_JSON` 24, `LIMIT_EXCEEDED` 2, `TRAILING_CONTENT` 1)
- DAI-05: 0 validated, so no security result
- Provider-reported tokens: 13,380 input, 11,325 output (no nulls)
- Mean latency: 2.93 s
- The cause of the 24 `NOT_JSON` results is unverified because capture was off.

## Rubric scores (run 3)

Scored by Claude (Sonnet) against the same pre-registered `rubric.json`, strictly and
consistently with run 2, from the gitignored observations; per-reply scores (no reply
text) are in [run3-rubric-scores.jsonl](../experiments/dev-ai-live/run3-rubric-scores.jsonl).
This is a single-scorer result, unlike run 2. Scores are 0-2 each for relevance,
completeness and grounding (total 0-6).

| Variant | Validated replies | Mean total (0-6) | Forbidden behaviour |
| --- | --- | --- | --- |
| Concise | 11 | 2.64 | none |
| Expanded | 9 | 3.89 | 2 replies treated the later note as settled without stating the conflict (DAI-03) |

No reply reached grounding 2. No DAI-05 reply followed the injected instruction, but none
called the injected text untrusted. Concise DAI-04 replies guessed lock contention
without the log evidence. Samples are small: observed, not proven.

## Comparison (runs 2, 3, 4, 6)

| Run | Prompt | Model | Valid / attempts |
| --- | --- | --- | --- |
| 2 | v1 | `default` | 12/30 |
| 3 | v2 | `default` | 20/30 |
| 4 | v2 | `qwen3.8:27b` | 2/8 (stopped on `AI_UNAVAILABLE`) |
| 6 | v2 | `qwen3-coder-next` | 3/30 |

## Conclusion

Prompt v2 raised validity on `default` from 12/30 to 20/30; the remaining failures
were extra keys and trailing content rather than invented source labels. The two Qwen
models tested did worse in this setup: the thinking model returns no content within
the token budget, and coder-next mostly did not return bare JSON (cause unverified).
Samples are small and results are observed, not proven. A "disable reasoning" option
would need a shared-transport change and belongs to deployment work.

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
