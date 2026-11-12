# UI research runner implementation plan

**Goal:** Run a bounded, local, development-time research → design → review → synthesis workflow with truthful records.

**Design:** A Python CLI invokes separate read-only, ephemeral Codex CLI sessions sequentially, passing bounded handoffs. Existing TraceDesk design guidance and harness conventions remain authoritative.

1. Specify scope and failure behaviour in `workflow/specs/ui-research-runner.md`.
2. Add focused tests for ordered handoffs, failure, invalid inputs and overwrite prevention.
3. Implement the runner with bounded time and output, local run records and fail-closed handoffs.
4. Add role instructions, a reusable skill and teammate-facing usage documentation.
5. Run the focused tests and CLI help. Report whether a real agent run was performed; do not treat mocks as live evidence.
