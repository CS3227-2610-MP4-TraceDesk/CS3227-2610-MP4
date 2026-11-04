# ADR 001 — TraceDesk reuse foundation

Date: 2026-11-04. Status: architecture/component selection approved by repository user.

## Evidence of decision

After reviewing the proposal in this chat, the user said:
“all confirmed and you may create reasonable directory to store all the skills,
harness, workflow etc.”

This confirms the three-role scope, Java/Spring + React + PostgreSQL direction,
SoCLaaS adapter direction, component selections, and permission to establish local
reusable resources. No personal name is inferred. It does not certify finished
implementation, test results, independent review, code ownership rights or release.

## Decision

Use a modular monolith and extract bounded AI transport/tests first. Move the
coupled admission/accounting/circuit subsystem together after defining TraceDesk
identity and fresh migrations. Reuse workflow patterns with project-local skills
and host-neutral development-agent roles. Do not copy personal `.codex/agents`,
historical approvals, old domain logic, secrets, desktop build or migration chain.

## Consequences

Initial transport is deliberately internal and cannot serve application AI routes.
Production security depends on the later secured gateway. The development harness
coordinates work through the available host; it is not an autonomous runtime.
Role contracts and detailed UI/domain behavior remain per-slice specification work.
