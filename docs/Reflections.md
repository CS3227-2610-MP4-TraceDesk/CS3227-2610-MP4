---
layout: default
title: Reflections
nav_order: 4
---

# Reflections

## Reporter role
### Multi-Agent Software Engineering

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
See the [REPORT-PLAN evidence](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/reporter/evidence/reporter-reports-plan.md).

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
See the [v2 assessment](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/reporter/evidence/reporter-ai-live-v2-quality.md).

### Context Engineering

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

Sources: [REPORT-PLAN evidence](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/reporter/evidence/reporter-reports-plan.md),
[recorded observations](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/reporter/evidence/reporter-reports-plan/observations.jsonl)
and [specifier context inventory](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/reporter/evidence/reporter-reports-plan/specifier-context.json).
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

### Resource-aware Use

The Reporter application experiments showed why resource use must be analyzed
alongside quality. Two complete live batches compared report-improvement prompt
v1 with v2, using the same four synthetic case inputs, two context variants and
three repetitions (24 outputs per batch). All complete-batch outputs passed
structural validation, but semantic review counted invented facts or changed
requirements as failures. The [v1 assessment](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/reporter/evidence/reporter-ai-live-quality.md)
and [v2 assessment](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/reporter/evidence/reporter-ai-live-v2-quality.md)
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
See the [interrupted-run record](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/reporter/evidence/reporter-ai-prompt-v2.md#first-v2-live-attempt--stopped-as-required).

A separate Reporter live browser journey used a maximum of one completion and
9,200 resource units, with no retry. Sanitized summaries recorded one dispatch,
one successful ledger settlement and 571 charged units. All nine browser checks
passed, including escaped preview, no automatic update, explicit title selection,
a version-checked update and preservation of other fields. This demonstrates a
bounded integration journey; one successful case cannot establish model quality.
The provider key stayed in the operator’s terminal, and evidence retained fixed
statuses, booleans and usage summaries rather than raw content. See the
[Reporter browser evidence](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/reporter/evidence/reporter-ai-browser-journey.md).

My practical lesson is to use deterministic tests for authorization, field
selection, stale delivery, parsing and application rules before spending live
requests on model behavior. Fake-provider checks establish wiring and boundaries;
live evaluations assess actual suggestions. Neither substitutes for the other.
Finite attempt limits, no automatic completion retries and explicit stop
conditions made failures visible and bounded additional spending.


## Developer role

This section is an evidence-based draft for the Developer contributor to review and personalize. It does not assert that the contributor has approved this reflection.

### Multi-Agent Software Engineering

The DEV-DATA work used a coordinator as implementation owner and a separate read-only reviewer with a bounded scope. The reviewer found, among other things, that note validation occurred before assignment/object authorization, and that a new idempotency row could expose a foreign-key failure instead of the intended opaque response. The coordinator corrected the ordering and added test assertions. A follow-up raised a same-key race, leading to a documented lock and race-test source. This illustrates the value of asking a reviewer to trace authorization and concurrency boundaries rather than only check style. The reviewer did not run tests; at that point isolated PostgreSQL runtime verification remained unavailable. See the [Developer data review record](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/developer/runs/2026-11-08-dev-data-review.md).

### Context Engineering

The DEV-UI handoff identified the changed files, relevant acceptance criteria, frontend contracts, security risks, excluded features and already-observed checks. The independent reviewer then found retry and denied-state issues; the implementer corrected them and the reviewer checked the corrections. Identifying scope and evidence made the handoff more useful than sending the reviewer the whole conversation. The record does not measure all context reads or tokens, so it cannot establish a context-size improvement. See the [Developer UI handoff](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/developer/runs/2026-11-08-dev-ui-implementation-review.md).

### Resource-aware Use

The DEV-UI record reports an initial browser test-control failure followed by a passing isolated Chromium journey, plus a stalled Gradle attempt before a successful outside-sandbox run. It also states that wall-clock and token telemetry were unavailable, and that a later same-candidate real-backend check was pending at that point. These are useful observations about retries and evidence limits, not measurements of savings. The [Developer measurement plan](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/developer/experiments/agentic-se/plan.md) describes future comparisons; a plan is not itself an experiment result.

## QA reviewer role

The following is a draft based on the recorded QA evidence. It is intended for the QA contributor to review and amend; it does not claim personal sign-off or invent a controlled experiment. See the [QA contribution evidence](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/qa/evidence/submission-evidence.md) for issue-level sources.

### Multi-Agent Software Engineering

A useful division was to keep authorization, persistence and integration with a coordinator while giving bounded presentation or review tasks separate ownership. For review history, the independent read-only review identified a duplicate test selector and an encoding artefact; both were corrected before focused checks passed. The finding shows why a handoff should identify the exact contract and files to review rather than ask for a general impression. It does not prove that every QA change received an independent review. See the [QA evidence index](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/qa/evidence/submission-evidence.md#run-observations-and-handoffs).

Coordination created work as well as parallel opportunities. The QA template, discussion and workspace branches shared migrations and integration dependencies. Their integration required ordered migrations and conflict resolution; one browser assertion also had to be scoped after a second matching label appeared. This suggests that tightly coupled schema and interface changes benefit from one integration owner and explicit ordering. The recorded evidence does not contain per-agent elapsed time or a comparable single-agent baseline, so it cannot establish that delegation saved time.

### UI research workflow: coordinated design review

The UI research workflow was developed to make design research repeatable for the team. Its local runner invokes a researcher, designer, separate automated reviewer and coordinator in sequence, with each stage receiving the task and earlier handoffs. In one actual QA sidebar run, the researcher received three selected repository files and the designer proposed empty and collapsed states. The reviewer challenged counts without an authoritative total and asked for a retry action with a defined scope. The final handoff removed unsupported counts and retained unresolved questions. This is an example of a reviewer testing a design proposal, not evidence that the suggested UI was implemented or approved.

The run also revealed a context and tooling problem. The first configured CLI model was unavailable to the account, and a later read-only sandbox could not launch its Windows shell to inspect files. The runner was adjusted to accept an explicitly requested model and bounded, selected file contents. That made the source-based run possible, but the run did not inspect the live interface, CSS, the shared sidebar component or screenshots. The run took about 60 seconds from start to finish; token usage, cost and a single-agent baseline are unknown, so there is no basis to claim a speed or cost improvement. See the [sanitized UI research run summary](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/ui-research/evidence/2026-11-13-qa-sidebar.md).

### Context Engineering

QA verification required the original report and relevant Developer resolution, while the AI planning context deliberately excluded logs, fix references, private notes and identities. This is an application context boundary, separate from the context passed to development agents. Version and project/role checks constrain when the suggestion can be delivered. The [QA AI contract](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/specs/qa-ai.md) records the selected information and follow-up limits.

For development handoffs, the QA evidence describes scoped review of the template and discussion changes against the relevant authorization and persistence contracts. A future handoff should include the current specification, affected files, exact revision, evidence and unresolved questions. Our QA slice did not capture complete context byte counts, token counts or later retrievals, so we cannot quantify any savings.

### Resource-aware Use and Evaluation

A local signed-in QA browser observation recorded a successful live SoCLaaS follow-up and an explicitly selected Pending check added to the verification draft. That observation is useful for functionality, but it did not record a pinned candidate, measured context, cost or a quality evaluation. It cannot support a claim of model accuracy or resource savings. The [role AI validation record](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/qa/evidence/role-ai-validation.md) distinguishes that observation from the planned bounded evaluation.

For offline verification, the same record reports 25 focused QA, Reporter, approval-guard and gateway tests passing, with zero live requests. Those checks exercise implementation and security boundaries; they do not establish live suggestion quality. The QA verification repair evidence also records a local real-backend browser run, including the migration and fixture issues encountered. Treating these results separately avoids presenting a mock-provider test as a live AI experiment or a local pass as deployment evidence. See [QA verification CI repair](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/qa/evidence/qa-verification-ci.md).

The next improvement is to run the documented, approved QA evaluation with synthetic cases and retain the actual rubric results and available resource measurements. Compare approaches only when the same cases and evaluation criteria have been used; do not fill missing measurements with estimates.
