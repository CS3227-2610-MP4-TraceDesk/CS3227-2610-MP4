# Reporter role
## Multi-Agent Software Engineering

The most useful division of work was a coordinator, a bounded specifier or
implementer, and a separate read-only reviewer. The benefit came from explicit
contracts and a correction loop, rather than simply increasing agent count.

For Reporter report planning, issue #12, the specifier owned the canonical report
contract while the coordinator handled integration and evidence. Independent
review found that resolving an expired or purged draft before checking its
submission replay record contradicted the promised 24-hour retry behavior. A
Reporter who submitted near expiry could otherwise lose a safe way to recover
an uncertain submission. The contract was corrected to check an authorized
replay first, and a submit → purge → retry test scenario was added. Review also
clarified version ownership and cursor binding. These were specification defects
caught before implementation, not runtime failures reproduced by that task.
See the [REPORT-PLAN evidence](../workflow/reporter/evidence/reporter-reports-plan.md).

This example changed how I assess agent output: a polished specification is not
sufficient evidence that its edge cases agree. Asking a reviewer to trace one
concrete scenario across expiry, authorization and retry rules exposed a
contradiction that a general request to “review the design” could miss. A handoff
should therefore identify the exact contract, owned files, risky scenarios and
expected evidence, with the coordinator responsible for reconciling findings.

Coordination also had a measurable cost. The issue #12 specifier interval was
602 seconds and the independent review interval was 505 seconds. The coordinator
observation window was 1,031 seconds and overlapped those intervals; adding the
three would misrepresent elapsed time. The reviewer used 13 execution-tool calls
against a planned budget of 10, including a failed quoting attempt and retry.
The specifier reported exceeding its 15-call budget, but its exact count was not
captured. Incremental coordinator clarifications contributed to overhead. The
next handoff should consolidate constraints before drafting and checkpoint the
remaining budget before requesting another review. This single case has no
single-agent baseline, so it cannot establish faster delivery or token savings.

Separate evaluators also need verification. In the Reporter v2 quality review,
both the coordinator and separate evaluator initially counted 11 semantic
failures, but they disagreed on eight individual outputs. Several evaluator
rationales were attached to the wrong records. Rechecking exact case, variant
and repetition keys, plus primary Java evidence, reconciled the failure flags.
Matching totals had hidden different judgments. I would retain per-output hashes,
initial ratings and disagreements, and require keyed counterexamples rather
than treating agent consensus as ground truth. Reconciled ratings are not a
second independent experiment or human approval.
See the [v2 assessment](../workflow/reporter/evidence/reporter-ai-live-v2-quality.md).

## Context Engineering

For Reporter development, context engineering meant giving each agent the
contracts and current artifacts needed for its specific decision. A useful
example is the report/draft specification task, issue #12.
Its artifacts record how context was selected
for development agents during specification and review.

The specifier needed product scope, shared role contracts, authentication,
architecture/security rules and the feature-specification template to define
Reporter report behavior. The reviewer needed the resulting report contract,
its authorization dependencies, threat model and evidence to check consistency.
The final reviewer recheck selected only the changed contract and its evidence,
because its task was to confirm specific corrections rather than repeat the
whole design review. The development agents used `gpt-6-luna`; the coordinator's
model telemetry was not recorded.

| Recorded phase | Selected reference files | Reference snapshot bytes | Observed execution-tool calls | Result |
| --- | ---: | ---: | ---: | --- |
| Contract specification | 13 | 63,207 | Unknown; reported over the 15-call budget | Produced the Reporter contract for review |
| Independent contract review | 10 | 76,436 | 13, including one failed quoting attempt and retry | Found contract inconsistencies and requested corrections |
| Final correction recheck | 2 | 35,360 | 2, with no retries | Confirmed the corrections with no remaining delta findings |

Sources: [REPORT-PLAN evidence](../workflow/reporter/evidence/reporter-reports-plan.md),
[recorded observations](../workflow/reporter/evidence/reporter-reports-plan/observations.jsonl)
and [specifier context inventory](../workflow/reporter/evidence/reporter-reports-plan/specifier-context.json).
The byte totals describe selected full-file snapshots at the time, not repeated
read traffic, tokenizer counts or complete model input. The specifier's authored
output was recorded separately and is not included as an input reference.

The draft replay finding shows why the right dependencies mattered. A draft
could expire or be purged after successful submission, while the submission's
24-hour replay guarantee still applied. Checking draft existence first would
contradict that guarantee. The reviewer needed both the draft lifecycle and
idempotency contract to see the conflict. Supplying only a draft endpoint's
local description would risk hiding the cross-boundary invariant. The correction
made authorized replay lookup precede draft resolution and added the
submit-near-expiry → purge → retry scenario, including denial after access
revocation. This was specification-level verification; no runtime test was
executed in that planning task.

The final recheck illustrates narrowing context after the problem is understood.
It used the report contract and evidence to verify the known fixes, with two
recorded calls and no retries. That smaller selection was appropriate for a
correction check, but it cannot establish that two files would have been enough
for the initial review. The reviewer had already seen the broader contract, and
the later task was smaller. Comparing 76,436 with 35,360 snapshot bytes as if they
were interchangeable attempts would confuse reduced review scope with improved
model efficiency.

There were also context-management failures. Multiple incremental coordinator
clarifications contributed to the call-budget overrun. Two coordinator patches
failed because their expected text did not match the actual file and had to be
corrected using current content. These observations suggest supplying one
consolidated handoff before drafting and refreshing the exact affected section
before applying a patch. Additional context should resolve a concrete uncertainty,
not repeat instructions or introduce unrelated source history.

My next Reporter handoff would include the owning criterion, current revision,
owned files, relevant shared invariants, known edge cases and a clear stopping
condition. The agent could retrieve supporting sections as needed. A correction
handoff would add the finding, changed behavior and evidence required to close
it. This balances focused context with access to dependencies rather than
assuming the shortest prompt is always sufficient.

This case supports a practical lesson about selecting and refreshing development
context, not a causal claim about quality, speed or cost savings. Agents inherited
conversation context, no isolated focused-versus-broad attempts were run from the
same revision, and host tokens/costs were unavailable. A future controlled
comparison must hold the Reporter task, starting revision, model, tools and
acceptance tests constant and record later retrievals and defects as well as
resource use.

## Resource-aware Use

The Reporter application experiments showed why resource use must be analyzed
alongside quality. Two complete live batches compared report-improvement prompt
v1 with v2, using the same four synthetic case inputs, two context variants and
three repetitions (24 outputs per batch). All complete-batch outputs passed
structural validation, but semantic review counted invented facts or changed
requirements as failures. The [v1 assessment](../workflow/reporter/evidence/reporter-ai-live-quality.md)
and [v2 assessment](../workflow/reporter/evidence/reporter-ai-live-v2-quality.md)
record the results and scoring limitations. V2 increased instruction length from 724 to 1,472 UTF-8 bytes. Its
complete batch used 13,256 charged gateway units compared with 11,011 for v1,
about 20.4% more, while semantic failures only changed from 12 to 11. Summed call
latency increased from 85,744 to 94,087 ms. These observations do not isolate the
cause of the increase, but they provide no demonstrated efficiency gain from the
longer prompt. Gateway units are neither currency nor an input/output token
breakdown; actual monetary cost remains unknown.

Failure accounting matters as much as successful-output accounting. The first v2
live attempt returned `AI_INVALID_OUTPUT` after 2,556 ms, charged 501 units and
delivered no suggestion. The runner stopped without retry. Including that attempt,
v2 consumed 13,757 units across 25 attempts and delivered 24 suggestions. Reporting
only the later complete batch would understate resources and omit a real failure.
The rejected response was not retained, so its exact validation cause remains
unknown. Later content-free diagnostic categories improved observability without
recovering that historical cause or justifying weaker validation.
See the [interrupted-run record](../workflow/reporter/evidence/reporter-ai-prompt-v2.md#first-v2-live-attempt--stopped-as-required).

A separate Reporter live browser journey used a maximum of one completion and
9,200 resource units, with no retry. Sanitized summaries recorded one dispatch,
one successful ledger settlement and 571 charged units. All nine browser checks
passed, including escaped preview, no automatic update, explicit title selection,
a version-checked update and preservation of other fields. This demonstrates a
bounded integration journey; one successful case cannot establish model quality.
The provider key stayed in the operator’s terminal, and evidence retained fixed
statuses, booleans and usage summaries rather than raw content. See the
[Reporter browser evidence](../workflow/reporter/evidence/reporter-ai-browser-journey.md).

My practical lesson is to use deterministic tests for authorization, field
selection, stale delivery, parsing and application rules before spending live
requests on model behavior. Fake-provider checks establish wiring and boundaries;
live evaluations assess actual suggestions. Neither substitutes for the other.
Finite attempt limits, no automatic completion retries and explicit stop
conditions made failures visible and bounded additional spending.

