# Measurement evidence kit

Read the [plan](plan.md) before collecting observations. This kit does not collect
provider or host telemetry automatically. Future secured-gateway work must emit
sanitized application metadata; the coordinator records development intervals
and actual handoffs. Tokens unavailable from the host remain null.

From the repository root:

```sh
python3 workflow/reporter/tools/agentic_measurements.py workflow/reporter/experiments/agentic-se/measurements.jsonl
python3 workflow/reporter/tools/agentic_measurements.py workflow/reporter/experiments/agentic-se/measurements.jsonl --output workflow/reporter/experiments/agentic-se/results.html
python3 -m unittest discover -s workflow/reporter/tools/tests -p 'test_agentic_measurements.py' -v
```

Open results.html locally for the presentation charts and observation register.
The report is self-contained, has no scripts/network assets, and contains the
SHA-256 of its exact input bytes. Keep the raw JSONL and manifest beside the chart.
An empty dataset produces a pending state, not fabricated results.

## Version 1 record format

Each line is one JSON object. Required keys:

| Key | Meaning |
| --- | --- |
| kind | application or development |
| source | observed or fixture; fixtures never count as observed results |
| run_id, case_id, variant | Stable identifiers; a controlled comparison shares run and cases |
| repetition | Positive integer, maximum 10000 |
| revision | Git revision or working-tree identity linked to the manifest |
| model | Model identifier, or null if unavailable |
| prompt_version | Required identifier for application; nullable for development |
| started_at, ended_at | UTC ISO timestamps ending in Z; duration derived in milliseconds |
| outcome | success, rejected, timeout, error or incomplete |
| context_refs | Selected field names or repository-relative file references; no contents |
| agent_role | Role identifier for development; null for application |
| security_result | pass, fail or unknown; pass requires linked test evidence |
| evidence_ref | Repository-relative manifest/evidence reference, rendered as plain text |
| token_source | provider for application, host for development, unknown if unavailable |

Optional numeric keys: input_tokens, output_tokens, context_bytes (UTF-8 bytes,
not token estimates), calls, retries (nonnegative integer counts), quality_score
(0–6 per plan rubric, only for successful outcomes). Omitted/null metrics mean
unknown; zero is measured zero. Non-null tokens require a known token source.
No cost calculation is performed. Each kind/run/case/variant/repetition tuple
must be unique; use distinct case IDs for distinct agent intervals.

Bounds: 10 MiB file, 10000 records, 16 KiB per nonblank line, 128 context references,
256 characters per identifier/reference, numeric metrics at most 10^12. IDs allow
letters, numbers and `_.:/-`; references allow repository path characters/spaces
but no absolute paths, parent traversal or URLs. Unknown fields are rejected.

Treat each development row as a measured interval, with scope in its manifest;
record separate roles/phases when actual timestamps exist. Do not infer missing
agent timings, waiting, rework, calls or token usage. Reported medians include all
outcomes; quality excludes unavailable scores. Full observations remain in JSONL.
Groups retain model/prompt/revision/evidence identity; reports make no automatic
matched-pair savings claims. Use the case/repetition IDs in a reviewed analysis.

Schema checks cannot prove strings contain no secrets. Review metadata before
committing. Never copy prompts, provider responses, environment files or private
agent reasoning into the dataset. Only synthetic fixture outputs may be retained
in separately reviewed evaluation artifacts for scoring.
