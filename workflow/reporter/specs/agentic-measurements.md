# Agentic measurement and presentation evidence

Feature: TD-MET. Status: specified; implementation and verification must be linked
separately. User authorization: 2026-11-06 request to implement the proposed
context/resource evidence collection and visualizations. Human owner: unknown.

## Scope and boundaries

Provide an offline Python standard-library JSONL validator and static HTML/SVG
report generator, a documented record format, and controlled experiment plans.
The user supplies explicitly selected sanitized metadata files. This tooling does
not discover files, read environment configuration, contact providers, execute
commands from records, or modify application/business data. Reports are local
artifacts; there is no deployment or public upload in this slice.

Application runtime instrumentation, actual AI comparisons, production telemetry,
deployment, and full development-agent injection exercises remain future work.
Mock/test data validate the tooling and are never evidence of application quality
or efficiency. Existing business authorization, transactions, gateway contracts
and prompt policies are unchanged.

## Input and output contract

Use a documented version-1 flat schema with required fields: `kind`
(`application`/`development`), `source` (`observed`/`fixture`), `run_id`, `case_id`,
`variant`, `revision`, `model`, `prompt_version` (nullable for development),
`started_at`, `ended_at` (UTC ISO timestamps), `outcome`, `context_refs` (list),
`agent_role` (nullable for application), `security_result` (`pass`/`fail`/`unknown`),
`evidence_ref`, `token_source` (`provider`/`host`/`unknown`) and positive integer
`repetition`. Identity is the combination of kind, run, case, variant and
repetition; duplicates are rejected. Model may be null when the host does not
expose it, and incomplete conditions cannot support matched comparisons.
Document exact enum values and units before collecting records; reject
unrecognized keys at every structured level. References connect handoffs and
review evidence without embedding their text.

Support optional nullable numeric `input_tokens`, `output_tokens`,
`context_bytes`, `calls`, `retries` and `quality_score`. Derive elapsed duration
from the timestamps, rejecting end-before-start. Token counts require provider
or host provenance; unknown provenance cannot substantiate measured tokens.
Use rubric-referenced quality scores, with the rubric identified through the
experiment evidence. Do not calculate costs in this slice. A development
record may leave unavailable usage null. Context references describe selected
files/field names and retrieval, never their contents. Evidence references are
bounded identifiers or repository-relative paths, displayed as text rather than
automatically opened. Do not log raw prompts, responses, issues, reports, secrets,
student names, or private model reasoning. Raw record text must not appear in
validation errors. Schema validation reduces accidental collection; it cannot
prove that a caller has not encoded sensitive content in an allowed identifier.

Bound input before parsing: maximum 10 MiB total, 10,000 records, 16 KiB per line,
128 items per metadata list, and 256 characters per identifier/reference. Reject
duplicate JSON keys, duplicate observation IDs, non-object records, invalid UTF-8,
non-finite numbers, boolean-as-number metrics, negative counts/durations, invalid
UTC dates and incompatible field types. Numeric zero is valid measured zero;
missing/null is unknown. Metric units and provenance must be explicit in format
documentation and report labels. An empty dataset is valid and produces a clear
no-observations state. Any invalid record prevents generation of a new report;
retain a previously generated output unchanged on validation failure.

Generate a self-contained HTML report with embedded SVG, no JavaScript, network
assets or external dependencies. Escape every input-derived displayed value;
never interpolate it as markup, CSS, a URL, or an executable attribute. Include
input digest, schema/report version, source identity and observation counts.
Use atomic replacement for the final output after validation. Do not read or
write through evidence references. Concurrent edits to an input have no historical
guarantee: the report identifies the bytes actually read by their digest.

## Observable acceptance criteria

| ID | Criterion and planned verification |
| --- | --- |
| TD-MET-01 | A documented strict versioned JSONL schema accepts valid application and development records; tests reject unknown/nested keys, wrong types, duplicate keys/IDs, malformed input, bad dates and all declared bounds. |
| TD-MET-02 | Unknown metrics remain unknown in tables and charts; zero remains zero. Aggregates show observed and missing counts and exclude missing values without zero imputation. Tests cover mixed, all-null and empty datasets. |
| TD-MET-03 | Reports distinguish observed records from fixtures conspicuously; fixture data are excluded from observed aggregates. Empty application data visibly state that application experiments have not run. Tests prevent fixture-only input from producing observed claims. |
| TD-MET-04 | Static reports include variant summaries for observed context/tokens and latency, a token-quality scatter plot where suitable values exist, and an agent timeline from timestamps. Every chart states units, sample size and missing coverage, with a readable data table and meaningful empty state. Tests exercise partial coverage. |
| TD-MET-05 | Comparison grouping preserves experiment/case/repetition and model/prompt/rubric identity where applicable. Unmatched or mixed-condition observations may be displayed descriptively but must not produce a savings/superiority claim. The report does not infer quality, causality, waiting time, cost or coordination overhead from absent data. Tests cover unmatched variants and mixed models. |
| TD-MET-06 | Untrusted metadata is inert escaped text; diagnostics do not echo rejected content. Tests use synthetic HTML/script payloads, secret canaries, disallowed content keys and path/URL references, verifying no execution, fetching or referenced-file reads. |
| TD-MET-07 | Generation validates before replacing output; invalid input preserves existing output. The report identifies exact input bytes by digest and provides source record IDs. Tests verify fail-closed generation and digest changes. |
| TD-MET-08 | Application and development experiment plans predefine baseline, alternative, fixed conditions, repetition/sample budget, rubric, failure handling and limitations. No live calls occur in this slice; pending experiments and unavailable telemetry remain explicit. |
| TD-MET-09 | Actual specialized handoffs, independent review, focused test commands/results and working-tree identity are recorded. The evidence differentiates tooling verification from live application/development comparison results and links findings to corrections. |

## Experiment plans and measurements

Application milestone owner: coordinating development agent, with human evaluator
identity supplied when available. Due: before final MP4 acceptance. Pilot on fixed
synthetic cases, then consider five cases per role, two context variants and three
repetitions (90 calls) only within an explicitly recorded available budget. Hold
model, prompt version, output limits and fixtures constant. Compare concise and
expanded authorized context; randomize or alternate variant order. Use a fixed
rubric for relevance/completeness and unsupported claims; report security failures
separately. Record all failed calls and quality-score missingness, not just
successful results. Score rubric and evaluator provenance precede scoring.

Development milestone owner: coordinating development agent. Due: before final MP4
acceptance. Compare focused versus broader sanitized context using matched bounded
tasks or isolated attempts, with fixed criteria, tools and model where possible.
Capture context references, retrieval counts where observable, actual elapsed
times, calls/retries, correctness tests, reviewer findings and rework. Define
coordination time explicitly before measuring it; overlapping agents' durations
must not be presented as wall-clock total. Document learning/order effects and
task difficulty. Current tooling implementation is one observed coordinated run,
not a controlled comparison and not proof of improved efficiency.

Use a pilot to cap calls/time before each experiment. Unknown token and cost
telemetry stays null; byte counts are not tokens. Report small samples and
unfavorable results. Keep plans, raw sanitized JSONL, test/review evidence and
generated charts linked so presentation claims can be checked.

## Security applicability and coordination

Applicable threat-model rows: raw response/log/credential leakage and malicious
issue/document/agent handoff. Metadata allowlisting, bounded parsing, inert HTML,
safe diagnostics, limited file access and independent review address this slice.
Application object access, CSRF, stale assignment, gateway quota settlement and
production isolation are not exercised: this offline tool has no corresponding
runtime entry points. Do not claim those controls passed.

Specifier owns this contract; implementer owns tooling/tests/documentation;
coordinator owns integration/index/evidence; a separate reviewer assesses actual
diff and test results. One focused specification pass, no web/provider access or
secret files. Implementer/reviewer receive this spec and only relevant repository
context. Record actual task IDs, timestamps and ownership; stop and reassess after
three materially similar failures. Schema names and visual layout are routine
implementation choices within this contract, not unresolved security approvals.
