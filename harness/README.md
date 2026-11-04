# Development-agent harness

This is a portable coordination protocol and role prompt set. It uses the coding
host's existing agent execution tools. It does not install a scheduler, create
agents automatically, or supply application-time multi-agent orchestration.

## Use

For an authorized multi-agent task, the coordinating agent or team member:

1. Selects the relevant specification and assigns a bounded task using the
   [handoff template](../workflow/templates/handoff.md).
2. Supplies the selected role file, `AGENTS.md`, applicable criteria and the minimum
   necessary source context. Other source material is retrieved as needed.
3. Assigns separate files to concurrent implementers. One owner integrates shared
   contracts/migrations; conflicting edits are resolved before further changes.
4. Uses host-native agent tools when authorized and available. Without those tools,
   use sequential human/agent handoffs and report that no parallel run occurred.
5. Collects deliverables and verification evidence; sends findings back to the
   implementer with concrete failed criteria. A separate reviewer receives the
   specification and actual diff, not instructions to rubber-stamp it.

## Roles and triggers

| Role | Trigger | Required result |
| --- | --- | --- |
| [Specifier](roles/specifier.md) | Missing/new behavior contract | Criteria, interfaces, threat assumptions, unresolved choices |
| [Implementer](roles/implementer.md) | Scoped authorized contract ready | Bounded change, tests, evidence and limitations |
| [Reviewer](roles/reviewer.md) | Meaningful change and evidence ready | Independent findings mapped to criteria, no invented approvals |

The coordinator records actual run IDs, participants, timestamps, ownership and
handoffs under `workflow/runs/` when the first run occurs. Do not create fabricated
completed run records or claim three prompt files satisfy the course requirement.

## Resource and failure control

One owner per task; avoid duplicate whole-repository analysis. Set task-specific
time/call/token budgets where the host supports them. Record unknown measurements
as unknown. Stop and reassess after three materially similar failed attempts;
do not broaden permissions or retry indefinitely. Distinguish blocked work from
completed work, and preserve partial evidence for the next handoff.

Agent roles do not confer deployment, secret access, repository publication or
destructive migration permissions. Use current user authorization, never old MP3
approval records. Keep workflow templates outside application runtime code.
