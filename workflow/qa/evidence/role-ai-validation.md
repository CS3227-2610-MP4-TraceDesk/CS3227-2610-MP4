# Role AI validation evidence and remaining runs

Issue #70. Audit baseline: TraceDesk main at `4b993bb`. Each observation
keeps its original candidate and environment. A historical pass applies to the
recorded scope and revision. Deployment evidence belongs to #69.

| Role and environment | Evidence already recorded | Remaining evidence |
| --- | --- | --- |
| Reporter, local service with live SoCLaaS | The v2 evaluation on `aef7de5` ran four synthetic cases in two context variants with three repetitions. All 24 delivered outputs were structurally valid and 11 failed semantic checks. Mean selected input was 299.25 bytes concise and 477 bytes expanded. The delivered batch charged 13,256 gateway units and accumulated 94,087 ms of request latency. See [scored v2 evidence](../../reporter/evidence/reporter-ai-live-v2-quality.md). | The signed-in live Reporter apply journey requires its own approval. The [browser preparation evidence](../../reporter/evidence/reporter-ai-browser-journey.md) used a fake provider. |
| QA, local signed-in browser with live SoCLaaS | The [QA AI specification](../../specs/qa-ai.md#verification) records a successful follow-up, explicit addition of a Pending check to the draft, and conversation retention across tabs. | The live browser note lacks a pinned candidate and measured context, resource and quality results. Reuse it as a functional observation. Run a bounded synthetic planning and follow-up evaluation for measurable evidence. |
| Developer | [Issue #31](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/issues/31) remains open. | Verify the integrated feature, then evaluate synthetic cases on its exact candidate. |
| Deployed, all roles | The [deployment guide](../../../docs/deployment-verification.md) describes the gate. | Record the deployed revision and permitted three-role results under #69. |

## Planned synthetic evaluation

Approve and pin the candidate, prompt versions, model, isolated environment,
expiry, and call and gateway-unit ceilings before contacting the provider.
Previously consumed approvals apply to their recorded runs. A QA evaluation
could use at most four completion attempts across two cases, with one plan and
one follow-up per case. Use a reproducible duplicate-refund case and a case
with incomplete verification evidence. Cover distinct refund IDs and the
missing evidence or a useful follow-up clarification without assuming unseen
implementation details. Developer cases and the attempt cap should follow the
actual #31 feature contract when it lands.

For each fixed case record UTC time, revision, provider/model identifier,
prompt version, UTF-8 selected context bytes and runtime instruction bytes
separately, completion outcome, wall-clock call latency, gateway units, and
provider-reported tokens when available. Record an unavailable metric as
unknown. Keep synthetic case IDs and rubric outcomes, with prompts, raw
model text, credentials, account details and session tokens out of the evidence.
Treat gateway units as the application's accounting measure.

Score fidelity to the supplied report and resolution, usefulness of the
suggested checks, and recognition of missing evidence. Record format failures
separately from content failures. An invented fact, disclosure or unsupported
claim of completed verification fails the relevant rubric item. A failed call
counts against the approved cap. Stop on provider or setup failure and record
the exact coverage reached.

## Offline security coverage

The existing `QaAiTest` covers role, project and version denial before context
selection, context allowlisting and latest-revision selection, bounded
follow-up input, stale delivery and malformed model advice. Existing Reporter
and gateway tests also cover provider guarding and fail-closed configuration.
Exercise wrong-role/project, stale-result and unavailable-provider paths with
controlled offline fixtures. Keep that evidence separate from live model
quality and deployed session checks.

### Offline rerun on `4b993bb`

Windows, Java 25 and Gradle wrapper. With `AI_ENABLED=false`, ran:

```powershell
.\gradlew.bat :backend:auth-app:test --tests '*QaAiTest' --tests '*ReportAiTest' --tests '*ReporterLiveBrowserGuardTest' :backend:ai-gateway:test --tests '*SecuredAiGatewayTest' --console=plain
```

**25 passed, zero failures or skips** (QA 4, Reporter 6, Reporter live
approval guard 4, gateway 11). The first auth-only run passed 12 of 14.
Two fixed-hash approval guard tests rejected the fixture after Windows
checkout converted it to CRLF. Comparing the checkout to the Git blob
confirmed the newline difference, and testing with the exact Git blob
bytes resolved both failures. The checkout fixture was restored afterward.
This was an offline test run with zero live requests.

## Run the QA evaluation

The dedicated Gradle task runs the shipped QA service against the secured
gateway with two built-in synthetic reports and their proposed fixes.
Each case runs planning once and follow-up once with that plan, for a
maximum of four completion attempts and 36,800 reserved gateway units.
Catalog refresh is separate. Failure stops the run without retries.

Start from a clean, reviewed checkout of the exact commit to be evaluated.
Use the existing [SoCLaaS settings](../../../docs/ai-enablement.md) with a
fresh approved model and sponsorship expiry. Set both global and actor
daily unit ceilings to 36,800. Supply the isolated PostgreSQL configuration
from the [test database guide](../../../docs/test-database.md). The command
creates synthetic accounts and reports in a disposable schema. Supply the
provider key through the backend process environment.

The operator records their approval in the ignored file
`workflow/qa/runs/qa-live-approval.json`. Replace the placeholder values
with the approved full Git SHA, exact model ID and future UTC expiry. The
record below is unapproved.

```json
{
  "approved": false,
  "revision": "<approved full Git SHA>",
  "model": "<approved model ID>",
  "expiresAt": "<approved UTC expiry>",
  "maxAttempts": 4,
  "maxResourceUnits": 36800,
  "caseIds": ["QAL-01", "QAL-02"],
  "stopOnFailure": true
}
```

Only after approval and environment setup, run the following from the
repository root:

```powershell
$env:AI_ENABLED = 'true'
$env:TRACEDESK_QA_LIVE_CONFIRM = 'qa-synthetic-only-4'
.\gradlew.bat :backend:auth-app:qaAiLiveEvaluation --no-daemon --console=plain
```

The task atomically consumes the approval before database or provider work.
The run's expiry can only shorten the gateway sponsorship window. A failed
setup after the claim still consumes the approval. Ordinary tests exercise
the runner with synthetic responses and do not invoke the live task.

The local JSONL records exact instruction and selected-context UTF-8 bytes
when context was built, end-to-end service latency, a settled ticket's
accounting fields when available, and structural validation status. The
gateway ledger exposes aggregate units and does not expose split provider
token counts to this runner. Missing measurements are marked unknown.

The automated score is a two-point coverage signal for each response.
QAL-01 checks for vocabulary about distinct refunds and comparison or refresh.
QAL-02 checks for boundary vocabulary and evidence or export vocabulary.
These are lexical indicators. Semantic fidelity is left unassessed in the
automated record. Raw responses, follow-up text, prompts and actor IDs are
excluded from the output.

### Runner verification on 2026-11-12

On the working tree based on `f7a2455`, Java 25 and the Gradle wrapper,
ran the following against an isolated PostgreSQL test database with
`AI_ENABLED=false` and a synthetic provider:

```powershell
.\gradlew.bat :backend:auth-app:test --tests '*QaAiLiveEvaluationTest' --tests '*QaEvaluationApprovalTest' --tests '*QaAiTest' --console=plain
```

**11 passed, zero failures or skips** (runner 3, approval 4, existing QA 4).
The runner test exercises real report and investigation stores, migrations,
the QA service and the JDBC gateway ledger. It checks all four attempts,
follow-up context, exact context and instruction byte counts, accounting,
and exclusion of response text and actor identifiers from observations.
The remaining tests cover expiry, failure stopping, strict approval bounds,
single-use claims and changed approval bytes. A test fixture initially
failed compilation because it passed a string to a byte-array argument;
that fixture was corrected before this successful run.

This is offline implementation evidence. Live model quality, human fidelity
scores and Developer evaluation remain pending under #70.
