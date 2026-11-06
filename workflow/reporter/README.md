# Reporter personal workflow

This directory holds the current user's personal Agentic SE process and presentation
evidence. The user requested contributor separation on 2026-11-06. The directory
name is a contributor workflow label, not authorization to restrict or expand
application roles, and not an assertion of student ownership of all features.

Teammates can use different personal workflows. Shared [project workflow](../README.md),
[role contracts](../decisions/002-shared-role-contracts.md), architecture, security
invariants and course requirements still apply to everyone.

## Apply to this user's implementation work

For this user's future specialized development agents (specifier, implementer and
independent reviewer), select `gpt-6-luna` explicitly when spawning them. Pass
the bounded task context and applicable references to each agent. If that model
is unavailable, report the limitation instead of silently substituting another
model. This is a contributor preference for development agents; it does not
select TraceDesk's application AI provider or rewrite historical run records.

Follow the [completion gates](agentic-se.md): specifications before behavior changes,
actual specialized coordination and independent review for meaningful changes,
security tests, measured experiments and honest evidence. Use personal templates
here rather than imposing their extra fields on shared templates.

- [Feature specification template](templates/feature-spec.md)
- [Agent handoff template](templates/handoff.md)
- [Verification template](templates/evidence.md)
- [Reflection template](templates/reflection.md)
- [Measurement kit and commands](experiments/agentic-se/README.md)
- [Experiment plan](experiments/agentic-se/plan.md)
- [Current report](experiments/agentic-se/results.html)
- [Actual coordinated run](runs/2026-11-06-measurement-kit.md)
- [Measurement verification](evidence/agentic-measurements.md)

Keep personal process specs in `specs/`, handoffs in `runs/`, verification in
`evidence/`, datasets/reports in `experiments/`, and supporting tools in `tools/`.
Shared application behavior specifications and decisions remain in the shared
workflow directories and acceptance index. Personal process preferences cannot
override shared authorization or security requirements.

## Relocation provenance

The measurement kit originally lived directly under workflow/ and tools/. It was
moved here without new application experiments. Dataset references were updated
to their relocated artifacts; timestamps and numeric observations were preserved.
The HTML report was regenerated from the relocated JSONL. The original checksum
manifest is retained as historical data in
[evidence/agentic-measurements-before-move.sha256](evidence/agentic-measurements-before-move.sha256);
its old paths/hashes describe the pre-move snapshot and are not current checks.
The current manifest identifies relocated artifacts. Earlier command text in the
verification guide now uses the runnable relocated paths; migration checks are
recorded separately. No new independent agent review is claimed for this mechanical
move and scope correction.
