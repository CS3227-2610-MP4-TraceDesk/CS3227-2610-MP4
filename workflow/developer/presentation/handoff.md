# Handoff: presentation + DEV-AI context experiment

Written 2026-11-11 for a new Claude chat. Read this whole file first.

## Roles (important)

- **The user (Developer contributor, GitHub `Izn432`)** runs everything in ChatGPT/Codex
  and pastes back outputs (test results, token counts, review findings).
- **ChatGPT/Codex** writes all DEV-AI code. It may spin up sub-agents.
- **Claude (you)** owns the presentation and the experiment: design the test cases,
  write the exact prompts the user pastes into ChatGPT (including which model and
  effort to pick, and when to spawn agents), record results, and update the deck.
  Claude does **not** write DEV-AI implementation code.
- The user can open ChatGPT on a Claude worktree as a project — tell them when that
  is useful and which folder.

## How to talk to the user

Plain, simple English. Short answers. No jargon or IDs (say "the permission checks",
not "TD-DEV-27"). The user found earlier answers bloated; keep it tight.

## What exists already (all uncommitted on `feat/dev-ai-31-spec`)

- Slide deck (13 slides, speaker notes = script), rebuilt 2026-11-11 around the
  experiment: https://claude.ai/artifact/GHMTv6S2Bds6PGDhhKSFGC — "Less context, same code?"
  The earlier deck (BVCGhuLebgbaAGzy2YYtii) was deleted at the user's request.
  A copy of the source, an offline presenter and a PDF are in [deck/](deck/README.md);
  the exact experiment prompts are in [prompts/](prompts/README.md).
- [script.md](script.md), [storyline.md](storyline.md).
- [presentation-measurements.md](../evidence/presentation-measurements.md): M1–M5.
- [presentation_measurements.py](../tools/presentation_measurements.py): reproduces
  M1–M2 (needs `tiktoken`).
- [recovered-session-metrics.md](../evidence/recovered-session-metrics.md): M3's source.
- The user said: no clickbait wording (no "tokenmaxxing"/"valuemaxxing").

Current talk, one line per slide: tokens mostly spent re-reading chat (97.7%) →
focused context 14× cheaper (9.8K vs 137K tokens) → checker agent found 8 bugs, all
fixed → 9 agent rules, only 1 fully enforced, 0 hooks → waste (two agents on one
branch, docs once > code, wrong-goal agent) → limits → keep/drop/change.

**Weakness the user raised:** it's all looking backwards; nothing is being tested.
The experiment below fixes that.

## Lectures 8–10 (key points)

Extracted text: `C:\Users\seani\AppData\Local\Temp\claude\lectures\l8.txt`, `l9.txt`,
`l10.txt` (if missing, ask the user to attach `l8.pdf`, `l9.pdf`, `l10.pdf`; the PDF
reader needs poppler, which is not installed — extract text with `pypdf` instead).

- **L8 Multi-agent coordination:** patterns (generator–verifier, orchestrator–subagent,
  agent teams, message bus, shared state). Generator–verifier fits when quality matters
  and criteria are explicit. Subagents cost more tokens than one agent. Isolate agents
  (worktrees, folders, containers) to avoid file collisions and branch confusion.
  Codex agent configs: `.codex/agents/*.toml` with name, model, reasoning effort,
  read-only sandbox, instructions.
- **L9 Context engineering + resource-aware use:** minimal context that fully
  specifies behaviour; load skills progressively; don't just grow the context.
  "Tokenmaxxing" (judging by tokens burned) vs judging by outcomes: define outcomes,
  examine workflow (model, context), produce evidence comparing them. Reasoning effort
  levels (low → max); "start with Sol at medium". Prompt caching (stable content
  first), compaction.
- **L10 Harness engineering:** feedforward (AGENTS.md, skills) and feedback (tests,
  structural checks, hooks) controls. Survey: most repos write instructions but few
  enforce them (few deny rules or PreToolUse hooks; most harnesses untested). Advice:
  turn rules into scripts, test the harness, track real usage.

## The experiment (agreed with the user)

**Question:** does giving the agent less to read make worse code, or just cheaper code?
And is the checker agent worth its tokens?

1. Split DEV-AI ([spec](../../specs/developer-ai.md)) into 3 small jobs, e.g.:
   - validate the AI's reply (strict output parsing and limits),
   - build the context sent to the AI (selected fields only, order, size ceiling),
   - permission checks before sending and before showing the result.
   Confirm the split against the spec; pick jobs with little overlap.
2. **Write the tests first** (Claude designs them; ChatGPT can code them in a
   separate step). Both agents get the identical tests. Draw cases from the spec's
   acceptance criteria and the DAI-01..05 cases, including the injection case.
3. For each job, two writer agents start from the **same commit**, each in its **own
   git worktree**:
   - **A, focused:** told to read only a fixed list of files (≈ the 7-file set in M1).
   - **B, broad:** told to read whatever it thinks is relevant.
   Same model and effort for A and B. Neither sees the other's work.
4. A **checker agent** (read-only) reviews both diffs without being told which is A or B.
5. Keep the better result; merge it as the real DEV-AI code.

**Measure per attempt:** input/output tokens (from Codex, or from local Codex session
logs as M3 did — never copy prompts or session IDs into the repo), wall time, tests
passing, checker findings by severity, lines changed, files the agent opened if visible.
Also the checker's own tokens vs bugs it found.

**Record** rows in `workflow/developer/experiments/agentic-se/measurements.jsonl`
using the schema in `workflow/reporter/tools/agentic_measurements.py`
(required: kind, source, run_id, case_id, variant, revision, model, prompt_version,
started_at, ended_at, outcome, context_refs, agent_role, security_result,
evidence_ref, token_source, repetition; optional numbers: input_tokens,
output_tokens, quality_score, calls, retries, context_bytes). Unknown = null, not 0.
Also update [plan.md](../experiments/agentic-se/plan.md).

**Optional L10 add-on:** add one real block (deny reading `.env*`, or a hook) before
the runs and count how often agents hit it.

**Caveats to say out loud:** doubles tokens; 3 pairs is a small sample — "what I
saw", not proof.

## Open questions to ask the user first

- Which models and effort levels their ChatGPT/Codex shows (for the prompts).
- How they can see token counts per agent/thread (Codex status, or logs).
- Whether to create the worktrees for them, and where.

## Rules

- Follow [AGENTS.md](../../../AGENTS.md). AI output is advisory; never fabricate data
  or approvals; every number on a slide needs a source; say when something wasn't measured.
- No credentials, raw prompts/responses, session IDs or user data in the repo.
- Don't read `.env*` files.
- Commit/push only when the user asks. No Co-Authored-By or "Generated with" lines.
- Known issue: the DEV-RESOLUTION review record says 7 P3 findings but lists 9.
