# Reporter invalid-output diagnostics

Candidate: `codex/shared-ai-enablement`, base `afe413d`, plus the source files
identified in [the current manifest](ai-enablement.sha256). Contract:
[content-free diagnostic criteria](../../specs/reporter-ai.md#content-free-invalid-output-diagnostics),
TD-RPT-28/30 and AI-LIVE-02 preparation. This follows the
[first v2 live failure](reporter-ai-prompt-v2.md#first-v2-live-attempt--stopped-as-required).
Its rejected response was not retained, so its exact cause remains unknown.

## Change and security boundaries

The strict Reporter parser classifies its first rejection using fixed stage and
reason enums. Stages distinguish gateway output rejection, Reporter response
shape, changes/replacements and questions. Malformed JSON includes duplicate
keys and trailing content. Replacement validation still uses the canonical
`ReportData` rules; the diagnostic does not duplicate or relax field limits.
Generic gateway rejection is not proof of a particular provider defect.

A package-private trusted service constructor accepts a diagnostic sink. Existing
production constructors use a no-op sink. No endpoint, browser request option,
database column, application log or error-envelope field has been added. The
test-only live runner clears its diagnostic slot for every attempt and may save
only the fixed enum pair alongside invalid-output metadata in its ignored local
JSONL. It does not save rejected text, source fields, arbitrary key/field names,
parser messages, indices, credentials or original exception causes. A failed
diagnostic callback cannot replace the public error.

Public invalid responses remain `502 AI_INVALID_OUTPUT` without diagnostic
detail or cause. Strict validation, accepted response schema, authorization,
source-version fencing, admission/accounting, output limits, prompt v2 and
one-use evaluation approval/no-retry behavior remain unchanged. Successful and
denied requests do not emit an invalid-output diagnostic. This feature does not
validate factual truth or improve model quality by itself.

## Verification and review

Using Java25 and ordinary test tasks (AI disabled), ran:

```sh
JAVA_HOME=/opt/homebrew/Cellar/openjdk@25/25.0.4.1/libexec/openjdk.jdk/Contents/Home ./gradlew :backend:auth-app:test --tests com.tracedesk.auth.reports.ai.ReportAiOutputDiagnosticTest --tests com.tracedesk.auth.reports.ai.ReportAiTest --tests com.tracedesk.auth.reports.ai.ReportAiQualityFixtureTest --tests com.tracedesk.auth.reports.ai.ReporterAiLiveEvaluationTest --no-daemon --console=plain
```

**14 passed**, zero failures/errors/skips. Initial build passed in 12 seconds;
final rerun after review coverage corrections passed in 9 seconds. Tests cover
all stage/reason mappings, duplicate/trailing JSON, null/non-object/oversized
responses, forbidden/duplicate fields, blank/oversized/unsafe-Unicode replacements,
invalid/oversized question collections, exactly one diagnostic, valid/empty
silence, throwing callback and canary-free public error with no cause. Service
checks include gateway size/format failures and unauthenticated/stale-source
silence with zero provider calls. Existing role checks also exercise final
revocation/version fencing, strict HTTP errors, no mutation, context limits and
exact fields for all four fixtures through reports and drafts.

`python3 tools/check_foundation.py`: passed (122 documents, 4 skills,
21 provenance entries, 11 transport Java sources). `git diff --check`: passed.
All 46 manifest hashes match; prompt instruction/version and v2 fixture digest
remain unchanged. No frontend source, rendering, database schema, transport or
shared gateway changes; their broader suites were not rerun. The focused tests
used synthetic providers and no application database.

Actual coordination: `/root/output_diagnostic_spec` (`gpt-6-luna`) wrote the
bounded contract before implementation. Root removed categories that could not
be inferred from the gateway's generic error, retaining broad honest categories.
`/root/output_diagnostic_impl` (`gpt-6-luna`) implemented the parser/service/runner
and initial diagnostic tests. Root owned integration, documentation and checks.
Separate `/root/prompt_v2_reviewer` (`gpt-6-luna`) inspected the actual diff and
reported missing tests for gateway-format and direct-parser guards. Root added
these cases plus first-emission and replacement canaries; final review confirmed
the finding resolved and no remaining material findings. Review was read-only,
with no tests or external requests; it is not human release approval.


No live request is authorized or executed by this implementation. A fresh bounded
approval is required before the next live evaluation; the previous approval was
consumed. The v1 quality findings and the interrupted v2 run remain recorded.
