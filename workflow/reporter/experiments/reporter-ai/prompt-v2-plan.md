# Reporter prompt v2 follow-up plan

Defined before v2 checks/live evaluation, against source baseline `3541f11`.
Owner: Reporter workflow coordinator; human experiment owner not supplied.
Scope: preserve requirements and observed facts, and separate unknown diagnostics
from assertions in the existing advisory report-improvement capability.

The [v1 assessment](../../evidence/reporter-ai-live-quality.md) scored 24 saved
outputs, with 12 invented-fact/requirement failures and equal 6/12 failures per
context variant. Keep those observations and the original fixture unchanged.
V2 uses a separately identified fixture containing identical four inputs;
authored reference responses verify contracts only. No new fields, logs,
repository content, model tools or automatic apply are introduced.

## Deterministic verification

- Verify the v2 instruction/version and exact six fields arrive through the
  secured gateway for both REPORT and DRAFT, including complete and injected data.
- Verify malicious text remains source data, logs/identity are excluded, no
  business update occurs, and existing authorization/version/delivery checks pass.
- Check the 8000-unit input boundary includes the longer instruction: exact
  input admitted, one byte over denied without an additional completion.
- Verify the v2 fixture identity/hash offline; old fixture stays byte-identical.
  Check runner authorization remains opt-in, one-use, finite and fail-closed.
- Do not treat fake-provider reference outputs as proof of prompt adherence.

## Subsequent live comparison — not authorized or run by this plan

Only after focused checks and independent review, obtain fresh explicit operator
approval for the exact environment/model, expiry, synthetic cases and bounded
attempt/resource allowance. Existing approval is consumed/expired. API keys
stay in the operator terminal; never copy them into source or evidence.

Use RAI-01–04, concise and redundant-expanded variants, three repetitions each,
`report-improvement-v2`, the same accessible model ID `default` if confirmed,
existing output bounds, sequential ordering and stop-on-first-failure. Maximum
24 new attempts/220800 resource units if approved; no completion retry. Capture
every attempt, including unavailable/invalid outcomes. Record prompt and fixture
hashes, model ID and provider revision if available; otherwise mark it unknown.

Score each output with the existing 0–2 wording, actionable-questions and factual
fidelity rubric plus automatic forbidden-fact/behavior failure. Preserve Reporter
intent even when the expected requirement differs from ordinary API behavior.
Questions about genuinely missing facts are allowed; assertions about unobserved
timing, execution, detection, disclosure or intent fail. Complete reports may need
no questions: a score of 2 for actionable questions includes appropriate restraint
when no useful reproduction details are missing; it is not a count of questions.
Record evaluator provenance and disagreements. No invented numeric
release threshold: any invented fact remains a defect requiring disclosure.

Compare per-case/condition forbidden counts and ordinal scores with v1; record
latency, selected input/validated suggestion bytes, actual charged units and only
available provider-reported tokens/cost. Use unknown for missing values. The
later batch cannot isolate prompt causality: model alias revision/time may differ,
cases are small and ordering is not randomized. Expanded input repeats existing
facts, so it does not test additional useful facts. Do not claim general context
benefit, statistically established improvement or production readiness.
