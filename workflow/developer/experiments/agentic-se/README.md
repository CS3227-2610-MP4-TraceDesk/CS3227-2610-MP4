# Developer measurement evidence kit

Read [the plan](plan.md) before collecting observations. The dataset starts empty;
do not copy Reporter observations. From the repository root, use the existing generic
validator/renderer with Developer-owned inputs:

```sh
python3 workflow/reporter/tools/agentic_measurements.py workflow/developer/experiments/agentic-se/measurements.jsonl
python3 workflow/reporter/tools/agentic_measurements.py workflow/developer/experiments/agentic-se/measurements.jsonl --output workflow/developer/experiments/agentic-se/results.html
python3 -m unittest discover -s workflow/reporter/tools/tests -p 'test_agentic_measurements.py' -v
```

The report is presentation support, not proof by itself. Keep the raw sanitized JSONL,
candidate manifest, rubric/evaluation sheet and evidence links together. Unknown host
tokens and costs remain null, not zero.

Each JSONL row must follow the schema documented by the shared measurement tool.
Record repository-relative context references, UTC timestamps, actual outcome,
revision, evidence reference and observable metrics. Never store prompts, responses,
credentials, environment files, user content or private reasoning.
