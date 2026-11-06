# Context and resource experiment plan

Status: planned; no application or comparative results collected yet.
Owner: coordinating development agent; student owner not yet supplied.
Contract: [measurement specification](../../specs/agentic-measurements.md).

## Application comparison

Due: before final MP4 acceptance, after all three secured AI capabilities exist.
Use synthetic fixtures only. Pilot one case per role and both variants (6 calls),
then review observed latency/usage and the available budget before the full run.
Target: five cases per role, two variants, three repetitions (90 calls total).
No automatic completion retries; failures are observations, not discarded runs.
Alternate variant order across cases/repetitions. Record model, prompt version,
revision, output limit and sampling configuration in the run manifest. Keep these
fixed across each comparison. Context is the only intended changed variable.

Concise context includes essential authorized fields. Expanded context adds
relevant authorized details, never cross-project information or secret material.
Finalize exact allowlists with each role spec before collection.

Synthetic case outline (write versioned fixtures before running):

| Role | Five case IDs and intended coverage |
| --- | --- |
| Reporter | R01 missing reproduction; R02 ambiguous expected/actual; R03 environment omission; R04 noisy logs; R05 injected instructions |
| Developer | D01 null handling; D02 boundary condition; D03 conflicting evidence; D04 irrelevant log noise; D05 injected code comment |
| QA | Q01 normal regression; Q02 boundary regression; Q03 incomplete resolution evidence; Q04 stale assumptions; Q05 injected verification text |

Before running, define expected useful elements and forbidden behavior for each
fixture. Score relevance, completeness and evidence grounding from 0 to 2 each:
0 = absent/incorrect; 1 = partial; 2 = meets the case expectations. Quality score
is their sum (0–6). Record evaluator and rubric version; keep component scores
and a short sanitized rationale in a separate evaluation artifact. Blind the
variant during scoring where feasible. Security pass/fail is separate and never
averaged into quality. Failed requests have no quality score, not a zero score.

## Development comparison

Due: before final MP4 acceptance. Choose a bounded implementation task and pin its
starting revision, acceptance tests and tool/model settings before collection.
Use isolated attempts from that same starting state: focused relevant context
versus a broader sanitized source bundle. Do not expose one attempt's solution
to the other. Record later retrievals and actual context limitations, including
inherited conversation context. Keep agent roles and test criteria constant.
One pair is a case study, not evidence of general superiority. Repeat on another
task if resources permit. No fabricated token estimates or cost savings.

For normal feature runs also record role intervals, handoffs, review findings,
corrections and observed calls/retries. Overlapping durations must not be summed
and called elapsed wall time. Explicitly label manual timings and unknown usage.

## Analysis and evidence

Use measurements.jsonl for observations and results.html for generated charts.
Keep the collection manifest, case fixtures, rubric sheets, test reports and run
handoffs linked by identifiers. Compare paired cases, report sample sizes and
missing metrics, and retain failures. Report quality and token/latency tradeoffs;
do not choose a winner based on token reduction alone. Timing describes this
hardware/provider/run only. Accounting reservations are not actual invoiced cost.

Never store real prompts, responses, credentials or private user content here.
Evidence metadata is manually reviewed before commit; field validation cannot
prove that an arbitrary string contains no secret. Runtime instrumentation belongs
in the secured-gateway feature specification and is not installed by this setup.
