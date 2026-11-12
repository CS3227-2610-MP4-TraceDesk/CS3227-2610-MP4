# UI research workflow

This local development tool coordinates four sequential, separate Codex CLI sessions: research, design, independent automated review, and synthesis. It builds on `tracedesk-ui` and the repository's `harness/README.md`. It does not change the app, perform usability tests with people, or grant approval to implement a design.

## Prerequisites and usage

Install Python 3.10+ and the Codex CLI, sign in to Codex and run from the repository root. Each stage invokes `codex exec` in an ephemeral session with the CLI read-only sandbox. Agent calls can consume time and account resources; an active account and appropriate access are required.

```sh
python scripts/ui_research.py --task "Improve the QA verification sidebar" --timeout 300
```

Provide a non-sensitive task and identify reference URLs or file paths in the task when needed. The default output is a timestamped directory under `.local/ui-research/`, which is ignored by Git. Use `--output` to choose an empty directory. Existing output is never overwritten. Task text and agent responses are stored in plaintext locally: do not include credentials, private data or real customer reports. Review all outputs for sensitive data before sharing them.

Use `--model MODEL` to select a model supported by your Codex account without changing your global configuration. The run records the requested model; it does not independently verify the actual model used.

When a Codex sandbox cannot launch file-inspection tools, use `--context path/to/file` (up to five times) to include explicitly selected repository text files in the prompts. The runner rejects paths outside the repository, `.env` paths, unsupported file types, and totals over 100,000 bytes. Selected file contents are sent to Codex; inspect them for sensitive data first. The run record saves their paths, not their contents.

## What happens

The coordinator passes the task and bounded earlier results to each stage in order. Each stage can inspect the repository read-only. Research findings need source paths or URLs; the designer produces a proposal; a fresh reviewer session checks the proposal; the final session summarizes findings and unresolved questions. Separate sessions are automated reviewers, not independent human approval. Browser tools or network access depend on the user's Codex configuration; reference capture is not guaranteed. Screenshots are not automatically captured.

The output folder contains `brief.md`, the completed stage files, `report.md` on success, and `run.json` containing stage timestamps and status. A stage that fails, times out, returns an empty or oversized handoff stops the run; completed outputs and failure metadata remain available. Error output from the CLI is not persisted to reduce accidental secret exposure. Retry by starting a new run after diagnosing the cause. The runner does not verify whether sources or claims are correct: the user must inspect the report.

Token counts, cost and the actual model are not collected; unknown values must not be treated as zero. Compare real runs only after defining the task, evaluation criteria and measurement method. These files alone are not evidence that a run or experiment happened.

## Safety and limits

The read-only sandbox is a host feature, not a guarantee that an agent cannot read sensitive files or transmit information. Use only on workspaces and references you are authorized to inspect, with a trusted CLI configuration and appropriate network permissions. Instructions tell agents not to access secrets, but the user must not rely on prompts as a security boundary. The runner never auto-applies changes or publishes a report.
