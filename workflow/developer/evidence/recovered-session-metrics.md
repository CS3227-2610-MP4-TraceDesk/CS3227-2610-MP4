# Recovered agent session metrics

**Recovered after the fact from local logs. This is descriptive usage data, not a controlled comparison.**
It shows how much agent work each Developer slice consumed. It cannot show that one approach was better.

- Generated: 2026-11-09 15:06 UTC, by the Developer contributor's Claude Code session.
- Revision: base `cbdf64a`, working tree on `feat/dev-ai-31-spec`. The DEV-AI row is a snapshot of work still running.
- Source: local Claude Code and Codex session logs for this repository on one machine, so only this contributor's work.
- Method: a one-off script aggregated timestamps, git branch, model, token counts, tool names and turn durations, then was discarded.
  It did not emit prompts, responses, reasoning, tool arguments or session identifiers. Raw logs are not in this repository.

## Codex

| Slice | Sessions | Spawned subagents | Host helpers | Turns / active time | Uncached input | Cache read | Output | Tool calls | First to last (UTC) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Developer workflow setup | 2 | 0 | 2 | 23 / 18.0 min | 165,551 | 2,905,856 | 24,573 | 53 | 2026-11-07 14:25 to 2026-11-07 18:32 |
| DEV-PLAN #26 | 2 | 0 | 1 | 30 / 36.5 min | 466,612 | 16,042,496 | 64,939 | 104 | 2026-11-07 22:28 to 2026-11-08 06:25 |
| DEV-DATA #27 and DEV-UI #28 implementation | 12 | 3 | 8 | 184 / 176.9 min | 1,847,016 | 79,219,200 | 283,918 | 679 | 2026-11-07 23:52 to 2026-11-08 20:25 |
| Workspace discovery and browser journey | 2 | 1 | 2 | 51 / 59.5 min | 449,565 | 30,646,016 | 77,646 | 250 | 2026-11-09 07:12 to 2026-11-09 08:48 |
| DEV-AI #31 spec | 1 | 1 | 0 | 7 / 3.6 min | 103,904 | 2,145,024 | 17,804 | 31 | 2026-11-09 14:51 to 2026-11-09 15:06 |
| **Developer total** | **19** | **5** | **13** | **295 / 294.6 min** | **3,032,648** | **130,958,592** | **468,880** | **1,117** | |

## Claude Code

| Slice | Sessions | Spawned subagents | Responses | Uncached input | Cache read | Output | Tool calls | First to last (UTC) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Developer workflow setup | 1 | 1 | 36 | 78 | 3,626,389 | 46,009 | 42 | 2026-11-07 17:44 to 2026-11-07 19:48 |
| DEV-PLAN #26 | 6 | 0 | 61 | 162 | 11,016,483 | 31,016 | 51 | 2026-11-07 19:48 to 2026-11-07 23:07 |
| DEV-DATA #27 and DEV-UI #28 implementation | 10 | 1 | 228 | 509 | 27,818,262 | 239,229 | 352 | 2026-11-08 02:13 to 2026-11-09 06:12 |
| Workspace discovery and browser journey | 5 | 0 | 68 | 161 | 12,083,321 | 54,548 | 112 | 2026-11-09 06:12 to 2026-11-09 09:36 |
| DEV-RESOLUTION #30 | 4 | 1 | 85 | 201 | 17,410,580 | 149,532 | 141 | 2026-11-09 09:36 to 2026-11-09 14:47 |
| DEV-AI #31 spec | 1 | 0 | 21 | 48 | 3,811,258 | 56,396 | 40 | 2026-11-09 14:47 to 2026-11-09 15:06 |
| **Developer total** | **27** | **3** | **499** | **1,159** | **75,766,293** | **576,730** | **738** | |

Host totals are kept separate because the hosts count differently. Do not add them together.

Work on `main` or a detached checkout could not be attributed to a slice and is excluded above:
- claude: 6,367 output tokens, 10 tool calls.
- codex: 51,516 output tokens, 103 tool calls.

## How to read these numbers

- **Cache read dominates and is not new context.** Each model call re-reads the conversation, so the same text is counted again on every call.
- **Output tokens include reasoning** on both hosts.
- **Active time exists for Codex only**, from its recorded turn durations. Claude logs give first and last timestamps, which include idle time, so no duration is derived.
- **Models:** Claude sessions used `claude-opus-5-5`. Codex sessions used `gpt-5.6-sol` and `gpt-6-luna`, plus `codex-auto-review`.
- **Cost is unknown.** No monetary figure is claimed.

## Limitations

- **Claude totals are not reconciled.** For the 12 of 21 sessions that have a host-reported total, the host reports 206,319 output tokens (258,558 with its separate thinking count), while a per-file extraction gives 415,211. Three checks did not explain the gap. Copies of earlier responses in resumed sessions are a possible cause but are unconfirmed; the tables count each response once across all files. Treat Claude figures as approximate.
- **Codex tokens were not independently cross-checked.** Each is the final cumulative thread total minus any total inherited at the thread's first event.
- **Slice attribution is by git branch.** Codex records only the branch at session start, so a whole thread counts toward that branch. DEV-DATA and DEV-UI implementation are combined because both were worked on from `codex/dev-data-27`.
- **Spawned subagents** are threads a coordinating agent explicitly started. **Host helpers** are automatic host threads such as approval review and are not evidence of multi-agent coordination. Roles and handoffs are recorded only in the [run records](../runs/README.md).
- **Session totals can double count.** A Claude session that switched branches appears once under each branch, so the total exceeds the 21 session files found.
- Sessions deleted or run on another machine are absent. Logs were not created for this purpose.
