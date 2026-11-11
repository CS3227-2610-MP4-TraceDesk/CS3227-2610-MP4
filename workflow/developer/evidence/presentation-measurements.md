# Presentation measurements

Offline measurements for the Developer presentation. Reproducible; no provider call,
session log, prompt, `.env` file or user data was read.

- Generated: 2026-11-11 by the Developer contributor's Claude Code session.
- Revision: HEAD `896bc33` on `feat/dev-ai-31-spec`, working tree dirty (presentation
  docs only; no measured spec, skill or code file was modified).
- Command: `python workflow/developer/tools/presentation_measurements.py`
  (requires `tiktoken`; installed in a throwaway venv).
- Tokenizer: `tiktoken` `o200k_base`. Codex models use this family; Claude's tokenizer
  differs, so treat Claude-side counts as roughly comparable, not exact.

## M1. Context cost of reading "everything" vs a focused set

Question: AGENTS.md says "read only relevant references". What would an agent load if
it ignored that and followed every link instead?

| Context set | Files | Tokens | × focused |
| --- | ---: | ---: | ---: |
| A. AGENTS.md only (always loaded) | 1 | 879 | 0.09 |
| B. Focused set for DEV-AI #31 | 7 | 9,848 | 1.0 |
| C. Every doc reachable by links from AGENTS.md | 98 | 137,002 | 13.9 |
| D. All tracked Markdown | 122 | 152,198 | 15.5 |
| E. All Markdown + source code | 295 | 439,748 | 44.7 |

Set B is AGENTS.md, the `tracedesk-spec-development` and `tracedesk-ai-integration`
skills, the DEV-AI and secured-gateway specs, ADR 002 and the AI policy — chosen by
hand as what that task needed. It is listed in the script.

**Limitation.** This measures what *would* be loaded, not what agents actually read.
It does not show that a focused set produces better work; the planned paired
experiment in [plan.md](../experiments/agentic-se/plan.md) has not been run.

## M2. Process documents vs product code over time

Bytes of tracked Markdown vs source (`.java .ts .tsx .sql .css .js .mjs .kts .gradle`),
at the last first-parent commit of each day.

| Day | Commit | Markdown files / KB | Code files / KB | Docs share of bytes |
| --- | --- | ---: | ---: | ---: |
| 2026-11-04 | `ddef6e7` | 30 / 60 | 26 / 68 | 47% |
| 2026-11-06 | `7f8e86a` | 51 / 179 | 46 / 150 | 54% |
| 2026-11-07 | `03d9e99` | 89 / 386 | 97 / 505 | 43% |
| 2026-11-08 | `d19374f` | 98 / 501 | 122 / 793 | 39% |
| 2026-11-09 | `896bc33` | 122 / 709 | 173 / 1,225 | 37% |

Whole team repository, not Developer-only. Bytes are a size proxy, not effort or value.

## M3. Where development tokens went (derived)

From the [recovered session metrics](recovered-session-metrics.md), Codex Developer
total: 3,032,648 uncached input, 130,958,592 cache read, 468,880 output.

- Cache reads were **97.7%** of all Codex input tokens.
- About **286 input tokens were processed per output token**.

Same limitations as the source: recovered after the fact, one machine, cost unknown.
Claude figures are not reconciled and are not used here.

## M4. Independent review outcomes

Counted from the Developer [run records](../runs/README.md).

| Run | P1 | P2 | P2 fixed | P3 fixed / accepted |
| --- | ---: | ---: | ---: | --- |
| [DEV-DATA review](../runs/2026-11-08-dev-data-review.md) | 0 | 3 | 3 | — |
| [DEV-UI review](../runs/2026-11-08-dev-ui-implementation-review.md) | 0 | 2 | 2 | — (plus one scope wording fix) |
| [DEV-AI contract review](../runs/2026-11-09-dev-ai-contract-review.md) | 0 | 1 | 1 | — |
| [DEV-RESOLUTION review](../runs/2026-11-09-dev-resolution-review.md) | 0 | 2 | 2 | 5 / 4 |
| **Total** | **0** | **8** | **8** | |

**Inconsistency found while counting:** the DEV-RESOLUTION record says "seven P3
findings" but its table lists nine P3 rows. Not corrected here; the record's owner
should reconcile it.

## M5. Written agent rules vs enforced rules

Lecture 10 reports that most repositories write agent instructions but few enforce
them. Same audit on this repository, by reading `AGENTS.md`, `tools/check_foundation.py`,
`.github/workflows/ci.yml` and searching tracked files, at the revision above.

- Instruction size: root `AGENTS.md` 69 lines; with `frontend/AGENTS.md` and four
  skills, 233 lines.
- Committed agent deny rules: **0**. Committed hooks that can block an agent action:
  **0**. No `.claude/` or `.codex/` configuration is tracked.

The nine "Engineering invariants" in `AGENTS.md`:

| # | Rule (short) | How it is checked |
| ---: | --- | --- |
| 1 | AI is advisory; domain owns mutations | Feature tests only |
| 2 | Server-side membership and object permissions | Feature tests only |
| 3 | Domain code has no Spring/HTTP/SQL dependency | **Script, partial**: foundation check covers gateway and transport only. The 9 `auth-app` domain files are clean today but unchecked. |
| 4 | All AI calls go through the gateway | **Script + test**: foundation check and `TransportBoundaryTest` |
| 5 | Authorize before prompt and before delivery | Feature tests only |
| 6 | Mutation and audit event in one transaction | Feature tests only (no dedicated check found) |
| 7 | Fresh migrations, UTC, isolated test databases | **Script, partial**: isolated-database guard and its CI test; migrations and UTC unchecked |
| 8 | Keep secrets out; never read `.env*` | **Not checked**: no deny rule, hook or secret scan |
| 9 | Record exact checks, results and limitations | **Not checked**: written only |

Result: 1 rule fully script-checked, 2 partly, 4 covered only by feature tests, 2 not
checked. "Feature tests only" means a regression would be caught only where a test
happens to exercise it. Classification is by reading, not by running mutation tests.
