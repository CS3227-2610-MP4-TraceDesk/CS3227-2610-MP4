# Workflow principles and presentation storyline — 2026-11-07

## Goal and scope

Turn relevant course concepts into project-specific operating rules for future
TraceDesk Developer work, and start an evidence-based individual presentation
storyline without claiming unfinished results.

## Context reviewed

Course material on multi-agent coordination, context/resource management and harness
engineering was reviewed as reference content rather than repository authority. It
was combined with the assignment brief, shared TraceDesk contracts and existing
project workflow. No external reference file was copied into the repository.

## Project decisions retained

- Select coordination patterns from task structure; do not spawn agents merely to
  demonstrate Multi-Agent SE.
- Prefer generator–verifier for criteria-driven quality/security changes and bounded
  orchestrator/subagent work for separable investigations.
- Use routing, targeted retrieval and structured notes instead of continually
  expanding context.
- Evaluate AI use by verified outcomes and risk reduction, not tokens consumed.
- Treat repository instructions as feedforward controls and executable tests/reviews
  as feedback controls; test and evolve the harness itself.
- Keep development-agent tooling separate from TraceDesk's secured SoCLaaS runtime
  architecture.

## Changes

- Added `workflow/developer/workflow-principles.md`.
- Added `workflow/developer/presentation/storyline.md`.
- Linked both from the Developer workflow and presentation index.

## Verification and limits

The guide is an operational project interpretation, not a claim that the workflows
or experiments have already run. The contributor should verify presentation emphasis
against the final grading brief and time limit.

Repository checks after the documentation changes: `git diff --check` passed, the
foundation checker passed, the shared measurement suite passed 8 tests, and the
empty Developer measurement dataset rendered as a pending report with 0 observations.
