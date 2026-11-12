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
