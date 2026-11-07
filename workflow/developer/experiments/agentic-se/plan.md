# Developer context and resource experiment plan

Status: planned; no Developer results collected yet.
Owner: Developer contributor.

## Application comparison

After the secured Developer AI capability exists, compare concise versus expanded
authorized context on fixed synthetic investigation cases. Hold model, prompt version,
output limit and sampling configuration constant. Change only the authorized context.

Initial cases should cover a plausible cause, boundary condition, conflicting evidence,
irrelevant log noise and injected instructions. Define expected useful elements and
forbidden behavior before running. Score relevance, completeness and grounding from
0–2 each (total 0–6); score security separately. Retain failures and missing telemetry.

## Development comparison

Choose a bounded Developer task, pin its starting revision, criteria, tests, model and
tools, then compare isolated focused-context and broader-sanitized-context attempts.
Do not expose one attempt's solution to the other. Record retrievals, inherited-context
limitations, correctness, defects, elapsed time, calls/retries and available usage.

## Presentation analysis

Use paired cases and report sample size, missing metrics, limitations and coordination
overhead. Do not select a winner from token counts alone or generalize from one pair.
Link raw sanitized observations, candidate identities, rubrics, tests and handoffs.
