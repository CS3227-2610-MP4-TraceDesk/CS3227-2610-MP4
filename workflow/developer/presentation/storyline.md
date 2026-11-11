# Developer presentation storyline

**Topic: Less context, same code?**
A controlled test of Lecture 9's context advice and Lecture 8's generator–verifier
pattern on real DEV-AI work, judged by outcomes (Lecture 9) with harness lessons
(Lecture 10).

Deck: https://claude.ai/artifact/GHMTv6S2Bds6PGDhhKSFGC (13 slides, speaker notes =
script). Full talk: [script](script.md). Data: [experiment runs](../experiments/agentic-se/runs.md),
[measurements](../experiments/agentic-se/measurements.jsonl),
[test cases](../experiments/agentic-se/dev-ai-test-cases.md).

| Slide | Lecture | Point | Data |
| --- | --- | --- | --- |
| Cover | — | Two agents, same jobs, one reads less; a blind third reviews | runs.md |
| The feature | — | DEV-AI: assigned developer asks AI for causes and next steps; advice only; 3 blue boxes = the jobs | developer-ai.md |
| Two questions | 8, 9 | Worse code or just cheaper? Is the checker worth it? | — |
| How I ran it | 8 (isolation), 9 | Tests first (41 visible, 14 hidden); 3 jobs; same model, commit, prompt; own worktrees | dev-ai-test-cases.md, runs.md |
| A's reading list | 9 | The exact 7 docs (M1 focused set) + job files; reporter-reports.md missing | presentation-measurements.md M1, method-report.md §6 |
| 14 hidden tests | — | Plain-English list per job; kept outside repo; all versions passed | dev-ai-test-cases.md |
| One line differed | — | A, B and rerun context lines; shared prompt rules | method-report.md §5 |
| Results | 9 | All code passed all tests; focused total 2,172K vs broad 1,662K input tokens (+31%) | measurements.jsonl |
| Job 1 | 9 | Focused missed a rule defined in another file (non-breaking space counted as text) | runs.md job 1 |
| Job 3 + rerun | 9 | Steps drive tokens: A 40 steps 1.51M; A rerun reading in one step 12 steps 1.13M; B 11 steps 0.81M | runs.md job 3 and rerun |
| Checker | 8 | 0.68M tokens for 3 reviews; 2 real problems no test caught; each review about ⅓ of a writer run | runs.md |
| How careful to be | 10 | 3 pairs, one run each; my list; same-model checker; rough times | runs.md |
| Takeaways | 8, 9, 10 | Stop fixed lists by default (agent chose 17–28 files, not 98); batch reads, fewer test runs; keep blind checker; add tests for its 2 findings | runs.md |

## Rules

Every number needs a source. No credentials, raw prompts, session IDs or real user
data. Say so when something was not measured. One pair per job is "what I saw", not proof.
