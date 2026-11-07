# Developer contributor workflow

This directory holds the current contributor's Agentic SE process and presentation
evidence for the TraceDesk Developer application role. The application-role scope is
investigation of assigned reports, clarification requests, proposed resolutions and
the handoff to QA. Shared behavior contracts remain under `workflow/specs/`.

This directory does not prove that a feature, experiment, review or deployment ran.
Only dated artifacts backed by actual commands, revisions and results are evidence.
Reporter history remains under `workflow/reporter/` and must not be copied here as
Developer work.

## Working files

- [Completion gates](agentic-se.md)
- [Working notes](notes.md)
- [Developer workflow principles](workflow-principles.md)
- [Feature specification template](templates/feature-spec.md)
- [Agent handoff template](templates/handoff.md)
- [Verification template](templates/evidence.md)
- [Reflection template](templates/reflection.md)
- [Measurement kit instructions](experiments/agentic-se/README.md)
- [Developer experiment plan](experiments/agentic-se/plan.md)
- [Presentation evidence plan](presentation/README.md)
- [Presentation storyline](presentation/storyline.md)
- [Assignment interaction summaries](../../logs/developer/README.md)

Use the subdirectories consistently:

| Directory | Purpose |
| --- | --- |
| `specs/` | Contributor planning, issue breakdowns and experiment specifications |
| `runs/` | Actual specifier/implementer/reviewer handoffs and coordination summaries |
| `evidence/` | Criterion-linked test and independent-review evidence |
| `experiments/` | Sanitized context/resource datasets, manifests and generated charts |
| `presentation/` | Slide outline and links to verified evidence; no unsupported claims |
| `templates/` | Reusable personal workflow templates |
| `tools/` | Developer-specific workflow helpers only; reuse shared tools where possible |

The canonical Developer behavior contract should be created at
`workflow/specs/developer-investigation.md` and indexed in
`workflow/acceptance-criteria.md` before implementation. Do not place the canonical
application contract in this personal directory.

## Using Codex for this project

1. Start a bounded task by naming the criterion or asking Codex to draft the
   missing specification before code.
2. Ask Codex to coordinate a specifier, implementer and independent reviewer for
   meaningful feature/security changes. Small mechanical edits do not need fan-out.
3. Give each agent only the applicable contract, source files and threat boundaries;
   let it retrieve additional context when necessary.
4. Ask Codex to record the real handoff under `runs/`, exact checks under
   `evidence/`, and sanitized measurements under `experiments/`.
5. Verify generated summaries and presentation claims yourself. Do not save private
   reasoning, raw prompts, secrets, user content or provider payloads.
6. Ask Codex to distinguish passed, failed, skipped and unavailable checks and to
   keep unknown tokens/costs unknown.

For this contributor's future specialized development agents, select `gpt-6-luna`
explicitly when the host supports it. If it is unavailable, record that limitation
rather than silently substituting another model. This development-agent preference
does not select the model used by TraceDesk's application AI.

## Presentation story to preserve

For every meaningful slice, preserve a short evidence chain:

`criterion -> agent handoff -> implementation -> test/review -> correction -> measurement`

The final individual presentation should use actual examples from these chains to
explain coordination, context selection, security behavior, resource trade-offs,
failures and lessons learned. Templates or configured agents alone are not evidence
that Multi-Agent SE occurred.

## Repository-level deliverables to track

The assignment also requires current `docs/UserGuide.md`, `docs/DeveloperGuide.md`,
`docs/Reflections.md`, a product website, online deployment with development/
production separation, and verified interaction summaries under `logs/`. These are
team deliverables and should describe the final released product accurately; this
workflow setup does not claim they already exist or are complete.
