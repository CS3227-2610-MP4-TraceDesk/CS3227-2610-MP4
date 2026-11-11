# DEV-AI context experiment: how it was done

Written 2026-11-11. This explains every step of the focused-vs-broad experiment, how
each part worked, and where everything is. Results are in [runs.md](runs.md); raw
numbers in [measurements.jsonl](measurements.jsonl).

**Who did what.** The Developer contributor directed the experiment and made the
choices (run each job, review tests first, move more tests to hidden, keep job 3's A).
Claude Code designed the test cases and prompts, created the worktrees, launched every
Codex run from the command line, ran the tests and recorded the results. Codex
(GPT-6.1 Sol) wrote all test code and all feature code. Claude Code edited test code
only twice, mechanically (moving tests to the hidden set, and fixing the broken a17
fixture), and wrote no feature code.

## 1. The question

1. Does giving the agent less to read make worse code, or just cheaper code?
2. Is a read-only checker agent worth its tokens?

## 2. Splitting the feature into three jobs

The DEV-AI spec has three parts with little overlap, so each became one job:

| Job | Class | What it does |
| --- | --- | --- |
| 1 | `InvestigationAiOutput` | Checks the AI's reply: exact JSON shape, limits, characters, source labels |
| 2 | `InvestigationAiContext` | Builds what is sent to the AI: selected fields only, order, note refs, 8000-byte limit |
| 3 | `InvestigationAiAccess` | Permission checks before selection, before sending and before delivery; error codes |

All three live in `backend/auth-app/src/main/java/com/tracedesk/auth/investigation/ai/`.

## 3. Writing the tests

1. **Designing the cases.** Claude Code wrote 55 cases from the spec into
   [dev-ai-test-cases.md](dev-ai-test-cases.md): input and expected result for each,
   every one restating a spec rule (24 for job 1, 14 for job 2, 17 for job 3).
2. **Coding them.** One Codex run (GPT-6.1 Sol, **high** effort, about 19 minutes) in
   its own worktree `D:\CS3227-wt\tests` turned the cases into JUnit tests. It also
   wrote a skeleton for each class whose methods only throw "not implemented", so the
   tests compile and the class names and method signatures are fixed for both writers.
   High effort was used here because every later result depends on these tests.
3. **Checking.** All tests compiled and all new tests failed with "not implemented",
   as expected. Codex could not commit (its sandbox blocked git's lock file), so
   Claude Code committed its files unchanged.
4. **Reviewing.** The Developer contributor asked for a review before running. Claude
   Code read all three test classes against the case list and the gateway code. It
   found that one visible test (V01) gave away part of a hidden one (V21) by using
   text with spaces at the ends, and that hidden A16 mostly overlapped visible A15.

Test files (in the repo now): `backend/auth-app/src/test/java/com/tracedesk/auth/investigation/ai/`
`InvestigationAiOutputTest`, `InvestigationAiContextTest`, `InvestigationAiAccessTest`.

## 4. Hiding tests

**Why.** If the agents see every test, they can write code that passes the tests
without following the spec. Hidden tests check whether the code follows the spec on
points the visible tests do not cover.

**How.**

1. Codex wrote the 6 originally hidden cases (V11, V21, C05, C12, A05, A16) into
   `D:\CS3227-wt\hidden\`, a folder **outside** the git repository, so they were never
   in any commit.
2. After the review, 8 more visible tests (V07, V15, V17, C02, C09, C13, A11, A14)
   were moved, unchanged, out of the test classes into new files in that same folder by
   a small script, and V01's leaking spaces were removed. Total: 41 visible, 14 hidden.
3. The test commit was not yet pushed, so it was **amended** (rewritten) rather than
   followed by a new commit. Otherwise the moved tests would still be in the branch
   history, where an agent running `git log -p` could read them.
4. The case list itself names the hidden cases, so it was never copied into the
   writers' worktrees. It sits in the main checkout.

**Limits of the hiding.** The hiding relied on location and on the prompt rule "stay
inside this folder"; the sandbox could still read other folders. The old pre-amend
commit also stayed in git's local undo log (reflog). Afterwards every writer's command
log was searched for the hidden folder, the case-list file, `HiddenTest`, `reflog` and
the old commit id: **no writer touched any of them.**

**Running them.** After a writer finished, the hidden files for that job were copied
into its worktree, the visible and hidden tests were run together, the JUnit XML was
summarised, and the hidden files were deleted again. Writers never had them during
their run.

## 5. Running A and B at the same time

1. **Same start.** For each job, two git worktrees were made from the same commit,
   each on its own branch, e.g. `D:\CS3227-wt\job1-A` on `exp/dev-ai-job1-A` and
   `D:\CS3227-wt\job1-B` on `exp/dev-ai-job1-B`. A worktree is a second working
   folder of the same repository, so the agents could not see or overwrite each
   other's files.
2. **Same tools.** A copy of the same Gradle cache went into each worktree, so neither
   had to download dependencies and both ran the identical test command.
3. **Same prompt except one line.** Each job's prompt said what to build, which test
   class defines the API, "follow the spec, not just the tests", "do not edit tests",
   "stay inside this folder", "do not commit", and the exact test command. The only
   difference was the context line:
   - A: "read ONLY these files, and nothing else except Java types you must open to
     compile against" plus the list;
   - B: "read whatever files in this repository you judge relevant".
4. **Same model.** Both: `codex exec --json -m gpt-6.1-sol -c model_reasoning_effort=medium -s workspace-write`,
   network allowed only so the local test database could be reached.
5. **At the same time.** A small script (`run.sh`, outside the repo) recorded the start
   time, ran `codex exec` in the given worktree with the prompt file, saved its JSON
   event stream, and recorded the end time. Claude Code started the A and B scripts as
   two background processes in the same moment and was notified when each finished.
6. **Job 3's start.** Job 3's permission code calls jobs 1 and 2, so job 3 started from
   a commit holding the chosen job 1 (B) and job 2 (A) patches (`0b5af0e`). Its tests
   need PostgreSQL, so Claude Code started one local test database (from binaries the
   test-writing run had downloaded) and gave both writers the same connection
   settings. Each test creates its own schema, so the two runs could not collide.

## 6. How A's file list was chosen

A's list was the **"focused set for DEV-AI"** from the earlier measurement (M1 in
[presentation measurements](../../evidence/presentation-measurements.md)), reused
unchanged: `AGENTS.md`, the two skills `tracedesk-spec-development` and
`tracedesk-ai-integration`, the DEV-AI spec, the secured-gateway spec, ADR 002 and the
AI policy. Then the job's own skeleton and test files were added, and A could open
Java types it needed to compile.

That set had been picked by hand as "what DEV-AI needs" before this experiment. It was
reused, not re-checked, so the experiment tests the list a person would really use.
That is why job 1 found a gap: the DEV-AI spec says text must follow the "canonical
plain-text rules" and be "nonblank", but does not say where those rules are defined.
They are in `workflow/specs/reporter-reports.md` and in existing helpers
(`InvestigationText`, `ReportAiOutput`). None of those were on the list. B found them
on its own.

## 7. The checker

The checker is a third Codex run that reviews code but cannot change it.

- **Read-only.** Started with `-s read-only`, in a clean worktree at the base commit.
- **Blind.** Each writer's changes were saved as a patch file and renamed `X.diff` and
  `Y.diff`; which was A and which B changed between jobs and was written to a key file
  the checker could not see.
- **Same model and effort** as the writers (GPT-6.1 Sol, medium).
- **Prompt.** It named the job, the spec sections, the test class, said both patches
  pass the tests, and asked for spec violations the tests may miss, bugs, security and
  maintainability problems, each with patch, severity (P1/P2/P3), file:line, one
  sentence and an example input, then a verdict.
- **Verification.** Claude Code checked every finding against the spec and code before
  counting it, and gave its own severity where it disagreed (job 3's B-only finding:
  rated P3, not P2).

## 8. Measuring

- **Tokens** from the `turn.completed` event Codex prints with `--json`: input, cached
  input, output and reasoning tokens. These are Codex's own counts, not estimates.
- **Time** from the start and end stamps in `run.sh`.
- **Commands and edits** counted from the JSON events.
- **Files read** found by scanning the commands for file paths; approximate.
- **Tests** from Gradle's JUnit XML. **Lines changed** from `git diff --stat`.
- Every row was checked with the existing validator
  (`workflow/reporter/tools/agentic_measurements.py`) before being added.

## 9. Why A used 1.51M input tokens in job 3 and B only 0.81M

"Input tokens" is not "how much the agent read once". Each step an agent takes is one
model call, and every call sends the **whole conversation so far** again (most of it
from cache). So input tokens ≈ number of steps × conversation size.

| Job 3 | A focused | B broad |
| --- | ---: | ---: |
| Commands | 40 | 11 |
| Of which: reading files | about 31, one file per command, several twice | 7, several files per command |
| Test runs | 3 | 1 |
| Input per step (rough: one call per logged event) | about 31K over ~49 events | about 45K over ~18 events |

B's conversation was bigger per step because it read more, but it took far fewer
steps. A read its listed files one at a time (and re-read several), then ran the
tests three times and inspected the results trying to explain the a17 failure, but
never opened the ledger code where the cause was. B read files in batches, ran the
tests once, opened the ledger code and named the cause. The same pattern shows in jobs
1 and 2 (A 15 and 21 commands, B 8 and 8), but there the conversations stayed small
so the gap was small.

So the extra cost came from **more steps**, partly from how A worked through its list
and partly from debugging without the code it needed, not from A reading more.

**Rerun to separate the two.** A was run again with the same list, told to read it all
in one command (A2). It took 12 commands and 1,126,161 input tokens: about 55% of the
A–B gap came from the one-file-per-step pattern. The rest stayed because A2 carried the
whole list in every step. A2 also opened ledger code outside its list to debug. Details
in [runs.md](runs.md#job-3-rerun).

## 10. Mistakes and fixes

| What went wrong | Cost | Fix |
| --- | --- | --- |
| Codex could not commit (sandbox) | none | Claude Code committed the unchanged files |
| Visible V01 leaked hidden V21 | none | Spaces removed before any writer ran |
| First job 2 checker prompt pointed at job 1's patches (Claude Code's path error) | 160,085 input tokens | Recorded as a failed run; rerun with the right path |
| Test a17 was wrong: its fake AI kept metadata revision 1 while the policy changed | both job 3 writers failed one test | Fake now publishes a newer revision; all 55 pass |

## 11. Where everything is

| Thing | Where |
| --- | --- |
| Case list | [dev-ai-test-cases.md](dev-ai-test-cases.md) |
| Results per job | [runs.md](runs.md) |
| Measurement rows | [measurements.jsonl](measurements.jsonl) |
| Final code and all 55 tests | branch `feat/dev-ai-31-core` (local, not pushed) |
| Experiment branches | `exp/dev-ai-tests`, `exp/dev-ai-job{1,2,3}-{A,B}`, `exp/dev-ai-job3-base` |
| Worktrees, prompts, raw JSON logs, patches, blind keys | `D:\CS3227-wt\` (outside the repo, not committed: they contain prompts and raw agent output) |
