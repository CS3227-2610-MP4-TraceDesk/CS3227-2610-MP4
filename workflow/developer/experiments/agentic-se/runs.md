# DEV-AI experiment run log

Runs are launched by Claude Code with `codex exec --json`; Codex writes all code.
Token numbers come from the `turn.completed` usage event (host-reported). Raw event
logs and prompts stay outside the repo. Rows are in [measurements.jsonl](measurements.jsonl).

## write-tests

- Job: code the visible test cases from [dev-ai-test-cases.md](dev-ai-test-cases.md)
  plus a skeleton that throws; hidden cases saved outside the repo.
- Start revision `896bc33`; result commit `a32837a`, later amended to `3b9a606` on `exp/dev-ai-tests`.
- Model GPT-6.1 Sol, effort high, no extra agents, workspace-write sandbox.
- 2026-11-10 17:15:25Z → 17:34:17Z UTC (18 min 52 s).
- Tokens: 3,390,713 input (3,319,040 cached), 34,091 output (10,521 reasoning).
  36 shell commands, 7 file edits.
- Result: compiles; 49 new visible tests all fail with "not implemented" (expected).
  All 48 visible case IDs present; hidden cases absent from the commit.
- Existing backend tests: 129 pass, 1 fail. The failure is a Reporter fixture checksum
  that differs because of Windows line endings (CRLF), not related to this work.
- Limitation: Codex could not commit (sandbox blocked the git lock file); Claude Code
  made the commit with the unchanged files.
- Follow-up by Claude Code after review (no new test logic): moved 8 visible tests
  (V07, V15, V17, C02, C09, C13, A11, A14) unchanged into the hidden set, and removed
  leading/trailing spaces from V01 so it no longer reveals hidden V21. The unpushed
  commit was amended so the moved tests are not in branch history; result `3b9a606`
  (41 visible tests, 14 hidden). Visible and hidden tests compile; not run.
- Spec gaps Codex flagged: selected-but-empty report fields; exact rejection message
  wording; the error code when the session actor differs (tests use
  `401 UNAUTHENTICATED`, following the Reporter pattern).

## Job 1

Validate the AI's reply (`InvestigationAiOutput.parse`). Both writers started from
`3b9a606` in separate worktrees, ran at the same time, GPT-6.1 Sol, effort medium,
no extra agents, identical prompt except the context line. Uncommitted diffs; branches
`exp/dev-ai-job1-A` and `exp/dev-ai-job1-B`.

| | A focused | B broad | Checker |
| --- | ---: | ---: | ---: |
| Wall time | 2 min 01 s | 2 min 01 s | 1 min 02 s |
| Input tokens (cached) | 324,144 (292,096) | 399,740 (366,336) | 169,457 (141,568) |
| Output tokens | 2,457 | 2,727 | 1,084 |
| Shell commands | 15 | 8 | 4 |
| Repo files named in commands | 9 + 1 type | 18 | — |
| Lines added | 77 | 83 | — |
| Visible tests (19) | 19 pass | 19 pass | — |
| Hidden tests (5: V07 V11 V15 V17 V21) | 5 pass | 5 pass | — |
| Checker findings | 1 × P2 | none | — |

- Checker was blind (patches labelled X/Y; key kept outside the repo), read-only.
  Verdict: merge B.
- A's P2, verified by Claude Code: A uses `String.isBlank()`, so text made only of
  Unicode spaces such as U+00A0 counts as nonblank. The canonical nonblank rule lives in
  `workflow/specs/reporter-reports.md` and in existing helpers (`InvestigationText`,
  `ReportAiOutput`), none of which were in A's allowed list. B read both and matched them.
  No test covers this, visible or hidden.
- File counts come from paths appearing in shell commands, so they are approximate.
- Limitation: one pair; both runs shared the machine, so wall times may interfere.

## Job 2

Build the context sent to the AI (`InvestigationAiContext`). Same setup as job 1;
branches `exp/dev-ai-job2-A` and `exp/dev-ai-job2-B`.

| | A focused | B broad | Checker |
| --- | ---: | ---: | ---: |
| Wall time | 2 min 19 s | 2 min 03 s | 52 s |
| Input tokens (cached) | 337,644 (284,928) | 455,586 (420,608) | 187,615 (154,496) |
| Output tokens | 3,025 | 3,028 | 1,344 |
| Shell commands | 21 | 8 | 7 |
| Repo files named in commands | 15 (list + 6 types) | 17 | — |
| Lines added | 95 | 88 | — |
| Visible tests (9) | 9 pass | 9 pass | — |
| Hidden tests (5: C02 C05 C09 C12 C13) | 5 pass | 5 pass | — |
| Checker findings | none | none | — |

- Checker verdict: equal. Claude Code read both patches: near-identical logic (same
  validation, ordering with an unsigned-UUID tie-break, version checks, ceiling).
  Differences are wording of the fixed instruction and variable names.
- Wasted run: the first checker run read job 1's patches because Claude Code wrote a
  wrong path in its prompt (160,085 input / 776 output tokens, 35 s). Recorded as
  `outcome: error`, repetition 1; the valid run is repetition 2.
- Job 2 needed only material inside the focused list (the DEV-AI spec defines all of it),
  so the broad agent's extra reading bought nothing here.

## Job 3

Permission checks (`InvestigationAiAccess`). Both writers started from `0b5af0e` =
`3b9a606` + job 1 broad patch + job 2 focused patch (branch `exp/dev-ai-job3-base`),
so the access code could call real reply validation and context building. Both used a
local isolated test PostgreSQL (trust auth, per-test schemas) started by Claude Code
from binaries the test-writing run had downloaded. Branches `exp/dev-ai-job3-A`/`-B`.

| | A focused | B broad | Checker |
| --- | ---: | ---: | ---: |
| Wall time | 6 min 52 s | 3 min 35 s | 1 min 11 s |
| Input tokens (cached) | 1,510,323 (1,464,960) | 806,594 (750,976) | 319,163 (268,672) |
| Output tokens | 7,563 | 4,703 | 1,992 |
| Shell commands | 40 | 11 | 6 |
| Lines added | 124 | 117 | — |
| Visible tests (13) | 12 pass, a17 fails | 12 pass, a17 fails | — |
| Hidden tests (4: A05 A11 A14 A16) | 4 pass | 4 pass | — |
| Checker findings | 1 × P2 (shared) | 2 × P2 (1 shared) | — |

- **a17 is a test defect, not a writer defect.** Its fake provider always reports AI
  metadata revision 1 while the test advances the policy revision, which the existing
  ledger fences, so the gateway returns `AI_UNAVAILABLE` before output parsing. B
  diagnosed this in its final message; A reported the symptom only and spent more
  commands and tokens trying to explain it, apparently because the ledger code was
  outside its allowed list. Verified by Claude Code in `JdbcAiLedger`. Needs fixing in
  the tests before merge.
- Shared P2: a storage failure during reservation or settlement reaches the client as
  `AI_UNAVAILABLE`, while the Errors table says `SERVICE_UNAVAILABLE`. The gateway
  sanitizes it first, so fixing it needs a gateway change, which this slice excludes.
- B-only P2 (Claude Code's view: really P3): B copies the selection twice, so a caller
  mutating the list from another thread between two adjacent lines could make the
  context and the later checks disagree. The selection comes from trusted request
  parsing, so this is theoretical.
- Checker verdict: merge A (Y) after the shared fix. Quality is close; B cost about half.

## Result kept

Branch `feat/dev-ai-31-core` (local, not pushed): `3b9a606` tests → `0b5af0e` job 1 broad +
job 2 focused → `d89781b` job 3 focused (checker's pick, chosen by the Developer
contributor) → `c8b63ca` held-back tests added as ordinary tests and the a17 fixture
fixed (its fake provider now publishes a newer metadata revision after each policy change).

- DEV-AI tests: 55 pass, 0 fail (41 visible + 14 held back), isolated local PostgreSQL.
- Full backend suite: 184 pass, 1 fail — the known CRLF checksum failure in
  `ReportAiQualityFixtureTest`, unrelated.
- Still open: job 1's shared-nonblank note is fixed by the kept patch; the storage-error
  code (`SERVICE_UNAVAILABLE` vs `AI_UNAVAILABLE`) needs a gateway change outside this
  slice. Controller, UI, apply-to-note and security-event wiring are not built yet.

## Totals (one pair per job; what was seen, not proof)

| Job | Focused input tokens | Broad input tokens | Quality |
| --- | ---: | ---: | --- |
| 1 reply validation | 324,144 | 399,740 | focused had 1 spec bug; broad none |
| 2 context building | 337,644 | 455,586 | equal |
| 3 permission checks | 1,510,323 | 806,594 | close; focused needed 3.6× the commands |
| Checkers (valid runs) | 676,235 in total | | found 1 real bug + 1 spec gap no test caught |
| Wasted checker run | 160,085 | | wrong path in Claude Code's prompt |

## Job 3 rerun

Question: was A's extra cost in job 3 caused by the fixed list, or by how A read it
(one file per command)? One more focused run (A2) with the same commit `0b5af0e`,
model, effort, list and broken a17 test. The only prompt change: "read all of them
together in one single command at the start". Branch `exp/dev-ai-job3-A2`. It ran
alone on the machine (A and B had run side by side), so its time is not comparable.

| | A focused | A2 focused, batched | B broad |
| --- | ---: | ---: | ---: |
| Input tokens (cached) | 1,510,323 (1,464,960) | 1,126,161 (1,053,184) | 806,594 (750,976) |
| Output tokens | 7,563 | 6,311 | 4,703 |
| Shell commands | 40 | 12 | 11 |
| Test runs | 3 | 2 | 1 |
| Logged events | ~49 | 22 | ~18 |
| Visible tests | 12 + a17 fails | 12 + a17 fails | 12 + a17 fails |
| Hidden tests (4) | 4 pass | 4 pass | 4 pass |
| Named a17's real cause | no | yes | yes |

- Batching cut A's commands from 40 to 12 and its input tokens by 384K (25%). That
  explains about 55% of the 704K gap between A and B.
- The rest (320K) remains: A2 loaded the whole list at the start, so every later step
  carried a larger conversation than B's.
- A2 opened `JdbcAiLedger.java`, which is not on its list and not needed to compile, and
  then named a17's cause. So the instruction "read only these files" was not strictly
  followed once the agent needed to debug.
- Not reviewed by the checker (the question was cost, and tests matched A's).
