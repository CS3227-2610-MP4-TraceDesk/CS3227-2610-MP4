# Reporter prompt v2 correction

Candidate: branch `codex/shared-ai-enablement`, base `3541f11`, plus the source
files bound by [the current manifest](ai-enablement.sha256). Contract:
[TD-RPT-28/30 and post-live correction](../../specs/reporter-ai.md).
The [v1 assessment](reporter-ai-live-quality.md) remains unchanged: 12 of 24
suggestions failed semantic checks. **V2 live evaluation stopped on its first invalid output; no v2 suggestion was delivered for semantic scoring.**

## Change and boundaries

`ReportAiService` now sends `report-improvement-v2`: preserve Reporter expected
intent, concrete commands, versions and observations; ask for unknown details;
avoid invented timing, execution, detection, safe handling, disclosure or logging
observations. Non-bug content must not become fabricated test results. Questions
must not request an entire repository. Complete reports may need no suggestion.
The strict browser adapter accepts v2 and rejects old/unknown prompt versions;
backend and frontend must ship together.

The gateway, exact six-field selection, schema, no-tools rule, authorization,
settlement, source-version delivery checks and explicit version-checked apply
are unchanged. The longer instruction counts toward the existing input ceiling,
so less source text fits; oversized sources still fail before dispatch without
truncation. No runtime semantic truth checker has been added. Model instructions
cannot guarantee factual correctness.

The original v1 fixture remains byte-identical. The separately pinned v2 fixture
keeps the same four inputs and authored reference responses. Its hash is
`49ba74abf137004141ebfcfe8a5c50e77a447f71aa122dea7fd2df63b7389164`.
It explicitly explains that actionable-questions score 2 includes appropriate
restraint on the complete case. The live runner selects/checks this fixture;
its opt-in, finite quotas, expiry, one-use claim and stop-on-failure remain intact.
The [follow-up measurement plan](../experiments/reporter-ai/prompt-v2-plan.md)
was recorded before checks. No live evaluation is authorized by that plan.

## Executed verification

Using Java 25 and synthetic providers, with ordinary tests forcing AI disabled:

```sh
JAVA_HOME=/opt/homebrew/Cellar/openjdk@25/25.0.4.1/libexec/openjdk.jdk/Contents/Home ./gradlew :backend:auth-app:test --tests com.tracedesk.auth.reports.ai.ReportAiTest --tests com.tracedesk.auth.reports.ai.ReportAiQualityFixtureTest --tests com.tracedesk.auth.reports.ai.ReporterAiLiveEvaluationTest --no-daemon --console=plain
```

**10 passed**, zero failures/errors/skips, build 18 seconds. Coverage includes
both source kinds, exact allowlisted context, server prompt/provenance, no business
updates, authorization denial before provider/ledger, revoked/stale delivery,
exact/over 8000 input limit, malformed output and strict bounded operator approval.
Offline fixture checks compare all four v2 inputs/IDs with v1 and validate the
runner's pinned digest. After the scoring-metadata clarification and pin update,
the two fixture checks passed again (8-second build). After expanding all four
cases through both source kinds, the full focused command above passed again:
10 tests, zero failures/errors/skips, 11-second build.

From `frontend/`:

```sh
npm run check
npm run build
npm run test:e2e -- --project=production reporter-ai.e2e.ts
```

Check passed typecheck/lint and **47 unit tests**. Production build passed.
Focused browser suite: **12 passed**, 13.3 seconds, synthetic HTTP responses.
It covers inert preview, selected apply preserving logs/unselected fields,
dirty/stale/cancelled/project/signout delivery, safe failures/no retry, keyboard
and accessibility at 320/390px. No layout/component/style changes or visual
baseline regeneration occurred. This is not live-provider browser evidence.

Initial Gradle execution could not write its cache under sandbox restrictions;
the authorized rerun passed. Initial browser launch could not bind loopback under
the sandbox; the authorized rerun passed. Those failures were environmental,
not passing tests. No production or application database was used. Unchanged
database, gateway/transport and whole-app browser suites were not rerun.

## Independent review and limitations

`/root/prompt_v2_implementer` (`gpt-6-luna`) owned backend prompt/tests/fixture.
Root owned criteria, browser API version integration, documentation and checks.
Separate `/root/prompt_v2_reviewer` (`gpt-6-luna`) inspected the actual diff,
fixtures and version wiring read-only. It found no supported authorization,
budget or provenance regression. Its proposed scoring finding (empty questions
on an already-complete report) was retracted after checking the existing
appropriate-restraint interpretation. New v2 metadata makes it explicit; the
original fixture and live ratings were not changed to satisfy review.
The reviewer ran no tests/provider calls and is not a human release approver.

Fake responses test wiring/contracts, not model adherence. Prompt v2 may still
invent facts. Fresh bounded approval and live scoring are required to assess it;
the old approval is consumed/expired. No new API requests, keys or deployment
were used. Live Reporter browser journey, owning Developer/QA evaluations and
authorized deployment verification remain pending under #42.

Final portable checks: `python3 tools/check_foundation.py` passed (119 documents,
4 skills, 21 provenance entries, 11 transport Java sources); `git diff --check`
passed. The v1 assessment verification still reproduces all 24 hashes, scores
and aggregate totals. Final independent delta review reported no material findings.

## First v2 live attempt — stopped as required

The user ran the fresh approved launcher against candidate
`afe413d01b81946bd633f20be26eff95bee5e0ec`, model `default`, prompt
`report-improvement-v2`, at approximately 2026-11-09 15:34 Singapore time.
The pasted console and local JSONL agree: RAI-01 / concise / repetition 1
returned **AI_INVALID_OUTPUT**, after 2,556 ms. One ledger completion settled
SUCCESS, charged **501 resource units** (3,174 reserved). Provider settlement
success does not mean the role suggestion passed validation. No suggestion was
delivered or applied; the runner stopped immediately without retry. Thus the
observed structural invalid rate for this interrupted run is 1/1, not a full
24-case quality result. No balanced v1/v2 quality comparison can be made.

Local ignored source: `workflow/reporter/runs/ai-live-7d4a810a-3011-43c1-a570-8716a8be9051.jsonl`,
SHA-256 `3d41f92f61334b2c143e14f55070ca1c382bdf39ce4c77f64cac1f051a1da94c`. Its one-use approval was consumed; the remaining
attempt allowance cannot be silently reused. No agent accessed the key or made
provider calls. Including all earlier batches, observed attempts total 28,
delivered suggestions 26, charged resource units 13,012. Missing token breakdown
and monetary cost remain unknown.

The current parser exposes only a fixed safe invalid-output error. The runner
does not retain rejected raw text or a content-free validation category, so the
exact failing rule (JSON shape, field/replacement limits, Unicode or another
role validation rule) is unavailable. Do not infer it from the generic code.
The Java native-access warning did not cause this failure: execution reached
the provider and role validation. Next diagnostic work should preserve strict
validation and add safe bounded validation categories with synthetic tests,
then obtain fresh bounded approval before any new live request. No new model
quality score is assigned to an unavailable suggestion.

The subsequent [content-free diagnostic change](reporter-ai-output-diagnostics.md)
adds safe categories for future attempts. It cannot recover this rejected response
or establish its exact cause; no new live run is implied.
