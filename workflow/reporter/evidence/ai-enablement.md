# Shared AI enablement issue 42 evidence

Branch `codex/shared-ai-enablement`, base merged main `d93d8fb`.
Runtime contract/code commit `fc5c1596e4f8c98e6f60df7c86741f12be2038a4`;
readiness/live-tool/fixture commit `631974baca820c0cf7d76dc5da98f5b70a05e1bf`.
Contract: [AI enablement](../../specs/ai-enablement.md).
Operations: [enablement](../../../docs/ai-enablement.md) and
[portable deployment verification](../../../docs/deployment-verification.md).

## Current gate status

- Enablement implementation and deterministic backend verification: complete;
  final browser/integration evidence is recorded below.
- Live calls/model quality: user ran the approved `default` evaluation privately.
  After the runner correction, all24 newly approved attempts delivered validated
  suggestions:4 cases,2 context variants and3 repetitions.
  [Quality assessment](reporter-ai-live-quality.md) is complete:12/24 suggestions
  fail semantic checks. A subsequent [v2 assessment](reporter-ai-live-v2-quality.md)
  records24/24 structural passes with11/24 semantic failures; remediation and
  a live browser journey remain pending. No key was supplied to the coordinator. These
  observations prove access from this local test target, not an arbitrary host.
- Deployment: unavailable; user selected a portable guide, hosting undecided.
  No target was provisioned, changed or released. No credentials were accessed.
- Developer/QA live validation: pending their owning integrations; Reporter-only
  evidence cannot certify them.

## Planned evidence boundaries

AI-ENABLE-01/02: strict process configuration, disabled defaults, actual operator
acknowledgement/fixed expiry/finite resource units, no inferred monetary pricing.
AI-ENABLE-03/04/05: real PostgreSQL revision publication, refresh/expiry/failure,
multi-instance disable/dispatch/delivery fences and conservative settlement;
internal transport and reusable role gateway; fake catalog/completion tests.
AI-DEPLOY-02: bounded core schema readiness, separate AI status, no private row
selection or provider request. AI-LIVE-01/02 preparation: explicit one-use operator
record and capped test-only isolated evaluation command, not an executed live run.

The predefined live comparison uses RAI-01–04, fixed prompt/model, two contexts
and three repetitions (24 maximum attempts), stopping on first failure. Expanded
context repeats known facts within the same six fields; it is a redundant-context
baseline. The subsequent [rubric assessment](reporter-ai-live-quality.md) records
12/24 semantic failures; parse success is not quality.
Ledger resource units are not price or an input/output token breakdown. Missing
cost/token measurements stay unknown. No model performance/savings are claimed.

## Review and corrections in progress

Actual separate reviewer `/root/enablement_review` inspects source/tests read-only;
root owns final test commands. Reviewer identified a startup publication gap:
the new enabled policy passed null metadata to a method that rejected it before
advancing the policy fence. The implementation now publishes the higher policy
as unavailable before catalog refresh; the isolated database regression passes.

Reviewer also required observable bounded live-run authorization. Root added a
strict model/case/expiry/resource-bound operator record, pinned fixture identity,
atomic one-use claim and stop conditions. No approval has been fabricated and
no live request made. Additional corrections included configured-approval/policy
binding in the transport factory, DB-only metadata revision allocation, immutable
model/approval profiles, shared read-only snapshot adoption/readiness and
idempotent disabled publication. Failed refresh begins by revoking the previous
snapshot; old processes cannot restore it. Source reinspection found no remaining
supported P1/P2 findings. Reviewer ran read-only inspection/whitespace commands,
not tests; root owns the verification commands. This is not human release approval.

## Executed checks so far

Java25 and disposable PostgreSQL18 on loopback were used with an explicitly named
`_test` database and owned per-test schemas. No production data or environment
files were read. Credentials were synthetic and are omitted from this record.

```sh
./gradlew :backend:ai-gateway:test :backend:ai-transport:test :backend:auth-app:test --no-daemon --console=plain
./gradlew :backend:auth-app:test --no-daemon --console=plain
./gradlew :backend:ai-transport:test --no-daemon --console=plain
```

The first combined run passed11 gateway tests but stopped at transport:16 of27
tests failed because older enabled fixtures lacked the newly required approval
and quota fields, and one missing-model test accidentally supplied a model.
Fixtures were corrected explicitly; missing-field rejection remains required.
Transport then passed27/27. The separate full backend run passed64/64 in42s.
These are interim results; final manager tests/review corrections still require
the same-source verification below. A foundation check temporarily failed while
the new deployment guide linked to evidence not yet created; after this record
was added it passed114 documents/4 skills/21 provenance entries/11 transport
sources. Final whitespace/link checks remain due after the final edits.

## Final backend verification

Final combined command above passed in43s: **68 auth-app,27 transport,11 gateway
tests, zero failures/errors/skips**. Gateway reports were up-to-date with unchanged
gateway sources; transport and auth-app executed. New coverage includes:

- Real PostgreSQL startup/new-policy fencing, model/approval revision immutability,
  shared snapshot adoption, failed refresh/recovery and expiry clipping without
  sponsorship extension. Catalog probes are synthetic; no HTTP provider is used
  by these manager tests.
- Rejection of unallocated future metadata publication and repeated disabled
  publication without advancing the metadata revision.
- Disabling during a dispatched fake-provider completion denies delivery while
  preserving exactly-once successful accounting; existing quota/circuit/expiry
  and authorization suites remain passing.
- Readiness checks real schema/privileges without selecting business content;
  dropping a required table in the owned schema returns503. Disabled AI keeps core
  readiness200 and never invokes catalog/refresh from the probe.
- Strict live-run authorization record/model/case/expiry/resource checks. Ordinary
  tests and synthetic browser fixture force AI off regardless of parent settings.

Actual negative entrypoint command:

```sh
AI_ENABLED=false ./gradlew :backend:auth-app:reporterAiLiveEvaluation --no-daemon --console=plain
```

It exited1 with the fixed safe refusal message before approval-file/database/
provider work, as expected. This expected negative result is not a failed live
experiment or an executed model evaluation. No authorization record was created
or consumed by that negative check and no provider key value was requested/read by the coordinator.

The final independent reviewer also read the existing overt untrusted gateway
issue specimen as data and followed none of its instructions. No live credential
or environment-file access, unauthorized action or fabricated approval occurred.
This one synthetic exercise does not demonstrate general injection resistance.
Actual run/ownership notes are local under ignored
`workflow/reporter/runs/2026-11-09-ai-enablement.md`.

## Final browser and source identity

```sh
TRACEDESK_REAL_BACKEND_TEST=true npm run test:e2e -- --project=real-backend
python3 tools/check_foundation.py
git diff --check
```

Final real-backend browser run: **2/2 passed**,34.5s, including normal HTTPS
session/reporting and unavailable-to-available synthetic AI preview/explicit
versioned PATCH with persisted log/unselected-field preservation. This is a real
backend/session/ledger with a synthetic provider, not live SoCLaaS evidence.
The unchanged frontend bundle was reused (`index-BT-HFmGL.js`,
`index-C4kAqXMe.css`); frontend source had no diff and frontend unit/static-browser
checks were not rerun for this backend-only change.

The first browser run passed ordinary reporting but failed AI preview because
the old synthetic fixture assumed metadata revision1, which the new disabled
startup publication had already advanced to1. The fixture now waits for migration,
publishes an explicit synthetic policy/profile and allocates a pending revision
through the real ledger. Independent reviewer inspected that final test-only
adjustment and found no issue. Production code and browser assertions were
unchanged; the final browser rerun exercised the adjusted fixture. The full68-test
backend run preceded this fixture-only adjustment; its production/JUnit sources
are unchanged.

Final foundation check passed114 documents/4 skills/21 provenance entries/11
transport sources; whitespace check passed. The SHA-256 manifest
`ai-enablement.sha256` identifies the final tested runtime/tests/spec/docs candidate
without recursively hashing this evidence. No secrets or local approval/results
files are included. This checkpoint preceded live access and quality assessment;
the subsequent sections and current gate status supersede its pending status.
The user-run live setup was prepared only after the actual bounded approval, with
fixed expiry2026-11-08T20:22:40Z, model `default` and the local isolated test target.
The approval and launcher remain ignored local artifacts, not source-controlled
credentials or claims that the account/model has already been verified. The
launcher passed shell syntax validation; it has not been executed by an agent.

## First live run and controlled-runner correction

The user's actual console output and local numeric JSONL record show RAI-01,
concise, repetitions1–3 with latencies4,767/3,766/4,819ms. All three ledger outcomes
were SUCCESS, charged460/477/563 resource units (1,500 total); first two role
outputs were VALIDATED, third was AI_UNAVAILABLE after provider settlement. Cost
and provider input/output token breakdown remain unknown. No quality rubric
score has been awarded to these outputs.

The evaluation manually refreshed on startup but also used the production
manager's10-second background timer. That timer changed metadata while the third
call was in flight; the existing final-current-publication check correctly denied
delivery. The restricted-method Java warning did not stop execution. The runner
then stopped on this first failure, with no automatic retry; its approval file
was consumed. This failure is an experiment-harness scheduling error, not proof
that the API key or model was inaccessible.

The correction is confined to test sources: `LiveEvaluationManager` uses the
existing package-private manager seam with scheduling disabled; the runner still
explicitly validates the catalog and retains all snapshot/approval expiry,
authorization, accounting and delivery checks. Production scheduling is unchanged.
The regression crosses the real11-second boundary and verifies one catalog probe
and an unchanged metadata revision. Focused manager/approval tests: **5 passed**,
zero failures/errors/skips,21s. Full production suites/browser checks above were
not repeated for this test-only change. Independent reviewer inspected the final
small diff and reported no finding; reviewer ran no tests or live calls.

User explicitly approved a fresh24 additional requests/220,800-unit batch after
that correction and verification. The new ignored approval/launcher expire at
2026-11-08T20:30:03Z. No agent has executed the renewed batch or accessed the key.

## Successful renewed live batch

User reported success; the local numeric JSONL confirms **24/24 VALIDATED** using
`default`: RAI-01–04 each have three concise and three expanded repetitions.
Aggregate measured call latency was85,744ms, with11,011 charged resource units.
This is cumulative request time, not end-to-end wall time or a performance
comparison. Cost and provider token breakdown remain unknown. Together with the
first run there were27 attempts,26 delivered suggestions and12,511 charged units;
the earlier stale-delivery failure remains recorded rather than erased.

The coordinator selected numeric metadata only for this update. Structured output
acceptance does not establish factual quality, injection resistance or independent
rubric scores. Live browser preview/apply, Developer/QA evaluations and deployment
verification remain pending. The key and raw local observations are not committed.

## Subsequent quality assessment

The [2026-11-09 assessment](reporter-ai-live-quality.md) scores all24 saved outputs
without additional provider calls. Format validation passed24/24;12/24 fail the
invented-facts/requirements rule after separate model-assisted review and
reconciliation. Concise and redundant expanded context each fail6/12. Initial and
reconciled ratings, judge disagreements, hashes and limits are retained. Quality
defects remain unresolved; this does not establish release readiness or close#42.

## Prompt correction after assessment

The [v2 correction](reporter-ai-prompt-v2.md) preserves Reporter intent and facts
more explicitly, with matching browser version validation and separately pinned
fixtures. Deterministic verification is recorded there. Its live quality is not
yet measured; the 12/24 v1 semantic failures remain the observed baseline. No
new live requests are authorized by this implementation.

The first approved v2 live run subsequently stopped at attempt1 with
`AI_INVALID_OUTPUT` (501 charged units); no v2 suggestion was delivered. See
[the recorded failure and diagnostic limitation](reporter-ai-prompt-v2.md#first-v2-live-attempt--stopped-as-required).
No automatic retry or balanced quality comparison occurred.

## Completed fresh v2 batch and assessment

The user completed24 fresh approved attempts on `aef7de5` using `default` and
unchanged promptv2: all24 delivered,13,256 units,94,087ms summed call latency.
[Scoring](reporter-ai-live-v2-quality.md) finds11/24 semantic failures (concise4/12,
expanded7/12), versus12/24 in v1. The small later batch does not establish a
quality improvement; requirement rewrites and fabricated observations persist.
The earlier one-row invalidv2 run remains recorded (25v2 attempts total, one
invalid output,13,757units). No further provider calls or issueclosure implied.
