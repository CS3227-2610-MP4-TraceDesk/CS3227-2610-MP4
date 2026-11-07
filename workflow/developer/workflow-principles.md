# Developer workflow principles

This operational guide converts the project's coordination, context-engineering and
harness requirements into repeatable working rules. Course material informed these
choices, but TraceDesk behavior remains governed by `AGENTS.md`, shared contracts,
feature specifications and security policy.

## Choose a coordination pattern deliberately

Do not equate Multi-Agent SE with spawning the maximum number of agents. Select a
pattern from the task shape and record why it was appropriate:

| Situation in this project | Preferred pattern | Evidence to retain |
| --- | --- | --- |
| Contract/code where criteria are explicit and quality is critical | Generator–verifier: implementer plus independent reviewer | Criteria, candidate revision, findings, correction and rerun |
| One coordinator needs different bounded investigations | Orchestrator–subagent | Subtask ownership, minimal context, returned summaries and integration decision |
| Independent work on stable, disjoint files | Agent team/parallel agents | File ownership, isolation, timing and integration outcome |
| Strong sequential dependencies | Sequential handoffs | Trigger, stop condition, artifact references and next owner |

Message-bus and shared-state orchestration are not justified for the current coding
workflow or TraceDesk MVP without a concrete approved need.

Use explicit delegation for meaningful work. Avoid subagents for trivial edits or
when coordination cost exceeds the benefit. Record actual overhead and idle/blocked
time rather than claiming automatic speedups.

Handoffs should reference specifications, ADRs, issues, commits, diffs and evidence
by path instead of repeating them. Redact secrets and private data. Use disjoint file
ownership or isolated worktrees when concurrent edits could create collisions,
branch confusion, context contamination or locks.

## Engineer context for value

Keep stable, general instructions in root guidance and use it as a router to focused
contracts, skills and contributor files. For each task:

1. Name the goal and applicable criterion.
2. Supply the smallest clearly relevant specification/source set upfront.
3. State where the agent may search and what it must ignore.
4. Retrieve additional context just in time with targeted searches.
5. Update [notes](notes.md) with current state, decisions, findings, open questions,
   next actions and change history before a long task is handed off or compacted.
6. Remove stale or irrelevant context from subsequent handoffs.

Optimize for completed, verified work—not token consumption. Before an experiment,
define outcomes such as correctness, time, scale and risk, then compare workflows and
their results. Use low effort for quick mechanical work, medium for ordinary bounded
implementation, and higher effort only for genuinely difficult multi-step trade-offs.
Record model and effort choices when known.

Prompt caching, persisted reasoning and compaction are possible resource mechanisms,
not assumed achievements. Record cache/reasoning telemetry only when exposed. Keep
stable prompt prefixes stable when measuring cache reuse. Compaction is lossy, so
persist a verified note or handoff before relying on it for a long task.

## Treat the repository as a harness

The TraceDesk harness has feedforward and feedback controls:

| Control | Current or intended project mechanism |
| --- | --- |
| Feedforward behavior/security direction | `AGENTS.md`, skills, ADRs, feature specifications and handoffs |
| Maintainability feedback | Format, lint, build and focused unit/integration checks |
| Architecture feedback | Dependency/boundary tests and source checks for domain isolation |
| Behavior feedback | Acceptance tests covering allowed, denied, failed, stale and concurrent paths |
| Process feedback | Independent review, evidence manifests, measurements and reflections |

Instructions alone are weak evidence. Convert important invariants into executable
checks where practical, test the harness itself, track how it was actually used, and
update it when failures expose a missing or misleading rule. Let agents run the
relevant application/tests when authorized instead of accepting plausible prose.

Keep development-agent tooling separate from product architecture. TraceDesk's
application AI remains a bounded advisory capability through the secured SoCLaaS
gateway. Do not introduce a different runtime agent platform without an explicit
architecture decision.

## Future-session checklist

Before meaningful Developer work, a future session should be able to answer:

- What exact criterion and bounded outcome are being pursued?
- Which coordination pattern fits, and why is single-agent work insufficient?
- What minimum context is supplied, and what may be retrieved later?
- What files or branches does each participant own?
- What security and lifecycle failures must be tested?
- What executable feedback will catch mistakes?
- What real measurements and limitations will be preserved for reflection/presentation?
- What handoff lets the next session resume without rereading the entire history?
