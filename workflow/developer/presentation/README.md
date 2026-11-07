# Developer presentation evidence plan

The final presentation is individual. Build slides from verified project artifacts,
not reconstructed memories or template files.

Use the evolving [storyline and content guide](storyline.md) to decide which evidence
to capture. The operational rationale is summarized in the
[Developer workflow principles](../workflow-principles.md).

## Suggested narrative

1. TraceDesk problem, three roles and shared lifecycle.
2. Developer-owned features and interfaces with Reporter/QA.
3. Specification-driven criteria and security boundaries.
4. One real multi-agent run: trigger, roles, context, handoffs and integration.
5. One finding or failed attempt and the resulting correction.
6. Developer AI authorization, prompt/context boundary and resource controls.
7. Focused-versus-broad context experiment with measurements and limitations.
8. Coordination benefits, idle/blocking/retry costs and when a single agent was better.
9. Deployment separation and same-revision verification evidence.
10. Personal contribution and lessons learned.

## Capture during development

- Stable criterion IDs and issue links.
- Dated run/handoff records and agent role ownership.
- Small, readable diffs or architecture diagrams tied to the feature.
- Exact test commands and summarized results.
- Independent findings and before/after corrections.
- Sanitized charts generated from `measurements.jsonl`.
- Actual latency, calls, retries and tokens/costs where exposed.
- Blocked/failed/skipped outcomes and honest limitations.
- Deployed URL, revision and environment-separation evidence when authorized.

Do not show credentials, private prompts/reasoning, user content, session IDs or raw
provider payloads. Verify every generated summary before using it in slides.
