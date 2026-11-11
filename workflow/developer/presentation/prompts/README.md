# Experiment prompts

Exact prompts sent to Codex (`codex exec`, GPT-6.1 Sol, medium effort) for the DEV-AI
context experiment. Runs, tokens and results are in
[runs.md](../../experiments/agentic-se/runs.md); how they were built is in the
[method report](../../experiments/agentic-se/method-report.md).

- `01-write-tests.md`: writes the visible test suite.
- `jobN-A.txt` / `jobN-B.txt`: writer prompts for focused (A) and broad (B). They are
  `jobN-*head` + `jobN-{A,B}-context.txt` + `jobN-*tail` joined; only the context line differs.
- `job3-A2.txt`: the job 3 rerun of A, told to read its list in one batch.
- `jobN-check.txt`: the blind, read-only checker (sees the two diffs as X and Y).

The isolated test database password (a placeholder; the database uses trust auth)
was replaced with `<redacted>`. Nothing else was changed.
