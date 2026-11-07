# Developer presentation storyline and content guide

Status: evolving outline. Evidence slots are deliberately marked pending. Do not turn
them into claims until linked artifacts and results exist.

## Central story

**We turned an underspecified Developer role into a secure, testable investigation
workflow, and used Codex as an engineered multi-agent development process whose
quality, context and resource costs we could inspect.**

The presentation should feel like one causal story, not a catalogue of AI tools:

`initial ambiguity -> specification -> coordinated implementation -> adversarial
feedback -> correction -> measured trade-off -> deployed evidence -> reflection`

## Proposed slide sequence

### 1. Problem and personal ownership

- One-sentence TraceDesk purpose and the three application roles.
- Lifecycle: `OPEN -> IN_PROGRESS -> READY_FOR_QA -> CLOSED`, including QA rejection.
- Clearly identify the Developer-owned slice and team-level contributions.
- Evidence slot: product/architecture links and final contribution map — **pending**.

### 2. Why the Developer slice was difficult

- Trusted assignment, project/object authorization and lifecycle concurrency.
- Investigation notes and clarification have different visibility rules.
- AI is useful for hypotheses but must remain advisory.
- Evidence slot: final Developer threat/decision table — **pending**.

### 3. Specification before implementation

- Show one stable criterion covering normal, denied, stale and failed behavior.
- Explain how it maps to interface, code, test and evidence.
- Evidence slot: `workflow/specs/developer-investigation.md` and acceptance index —
  **pending**.

### 4. The coordination pattern

- Explain why a generator–verifier pattern was used for quality-critical changes.
- Use orchestrator–subagent or parallel agents only for bounded, independent work.
- Show an actual handoff with minimal context, file ownership and stop condition.
- Evidence slot: selected run under `workflow/developer/runs/` — **pending**.

### 5. Codex as a harness, not just a chatbot

- Feedforward: AGENTS, ADRs, role specification, skills and task handoff.
- Feedback: tests, architecture checks, security negatives and independent review.
- Show one harness weakness discovered and the rule/check added to prevent recurrence.
- Evidence slot: before/after harness change and executable check — **pending**.

### 6. A concrete failure and correction

- Pick one meaningful finding: authorization, stale version, transaction rollback,
  unsafe context selection, or misleading UI success.
- Show symptom, reviewer/test evidence, correction and same-revision rerun.
- Evidence slot: candidate manifest, concise diff and result — **pending**.

### 7. Securing the Developer AI capability

- Authorization before selecting context and again before delivery.
- Allowlisted text-only context; prompt/version/output/resource bounds.
- Preview plus explicit version-checked apply; no code execution or status mutation.
- Injection, malformed output, permission change and unavailable-provider tests.
- Evidence slot: gateway/context diagram and fake-provider security results — **pending**.

### 8. Context engineering experiment

- Compare focused versus broader sanitized context on a pinned bounded task.
- Hold criteria, model and tools constant where possible.
- Measure correctness, defects, retrieval effort, latency/calls/tokens when available.
- Explain inherited-context and learning-effect limitations.
- Evidence slot: Developer measurement JSONL/chart/rubric — **pending**.

### 9. Resource-aware outcome

- Frame the question as value: did extra agents/context improve quality or risk enough
  to justify time and token overhead?
- Include idle/blocked time, retries, unknown metrics and an unfavorable result if real.
- State when a single agent was the better choice.
- Evidence slot: paired comparison plus coordination timeline — **pending**.

### 10. Integrated product and deployment

- Demonstrate the real Developer journey against Reporter and QA handoffs.
- Show development/production separation, deployed revision and security smoke checks.
- Avoid relying on mock data or screenshots as proof of backend behavior.
- Evidence slot: deployed URL, revision and same-candidate checks — **pending**.

### 11. Reflection and takeaway

- What coordination pattern worked, what failed and why.
- Which context belonged upfront versus on-demand.
- How the harness evolved after mistakes.
- One concrete future improvement.
- Evidence slot: `docs/Reflections.md` with linked examples — **pending**.

## Evidence selection rules

Choose a small number of high-quality artifacts. Prefer one end-to-end evidence chain
over many screenshots. Every quantitative claim needs a source, candidate identity,
sample size and limitation. Every “multi-agent” claim needs actual roles and handoffs.
Every security claim needs a test or review result, not just an instruction.

Do not include private reasoning, credentials, raw prompts/responses, real user data,
session identifiers or unreadable full-screen logs. Redact synthetic canaries after
their expected behavior has been recorded.

## Incremental update cadence

After each meaningful slice:

1. Add or update its run, evidence and interaction summary.
2. Select at most one presentation-worthy decision, finding or measurement.
3. Link it into the pending evidence slot above.
4. Remove weaker or duplicate examples.
5. Recheck that the storyline still reflects the released product and personal work.
