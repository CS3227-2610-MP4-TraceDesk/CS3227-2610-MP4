# Product scope

Baseline: earlier TraceDesk proposal, confirmed by the user on 2026-11-04.

## Roles and capabilities

| Role | Product responsibilities | AI capability |
| --- | --- | --- |
| Reporter | Create/edit reports within lifecycle rules, supply reproduction details and text logs, follow progress and clarification requests | Improve report wording and identify missing reproduction information |
| Developer | Investigate assigned bugs, maintain notes, submit a proposed resolution | Suggest hypotheses and investigation steps from authorized snippets and logs |
| QA reviewer | Maintain verification checklists, verify proposed resolutions, close or reopen bugs | Draft regression tests and assess gaps in resolution evidence |

Shared flow: report → investigate → propose resolution → verify → close or reopen.
The shared statuses and role boundaries are recorded in
[ADR 002](../workflow/decisions/002-shared-role-contracts.md). Transition
preconditions, editing rules, and assignment mechanics belong in the first domain
specification before implementation. Do not infer MP3's category-based access or
reporter-controlled closure rules.

## MVP boundaries

One programming language for examples, small pasted snippets and text logs, one
main AI action per role, a simple browser interface, and one shared bug lifecycle.
The example language is not yet selected; the transport foundation does not need it.
No repository ingestion, arbitrary code execution, automatic patches, autonomous
tool use, streaming, vector memory, or external issue integrations in the MVP.

## Course requirements

- Three meaningful roles, each with an LLM feature and end-to-end student ownership.
- Specification-driven implementation with criteria and verification evidence.
- Specialized, coordinated agents in the development process, with actual handoffs.
- Explicit AI and development-process security implementation and testing.
- Online deployment with separate development/production data and credentials.
- Context-engineering and resource-use measurements in application and development.
- Reflections on coordination costs, context choices, retries and resource tradeoffs.

Record student ownership when supplied; do not invent names or equal-work claims.
Development roles are not application users, and instructions alone are not evidence
of actual multi-agent execution.
