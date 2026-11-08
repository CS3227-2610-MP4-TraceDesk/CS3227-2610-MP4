# Reporter AI issue 17 evidence

Base `0d3c01935b056343fcd37d9411e3f5adee2089d4`; branch `codex/reporter-ai`.
Contract: [TD-RPT-26–30](../../specs/reporter-ai.md). The source identity manifest
`reporter-ai.sha256` records the tested working-tree candidate. This is a saved
draft/open-report integration; live provider quality, monetary funding, enablement
and deployment remain unverified and disabled.
Backend contract/API commit: `02a75ba`; UI/client commit: `1df6d75`. The third
commit retains the real-backend fixture/journey and this evidence. The source
manifest includes their exact tested content without a recursive evidence hash.

## Criteria and actual boundaries

| Criterion | Implementation | Verification |
| --- | --- | --- |
| 26 | ReportAiConfiguration/Service use the sole shared secured gateway; no raw provider accessor or enabled production configuration | Shared gateway/transport passing reports; ReportAiTest verifies initial denial has no ledger/provider interaction, explicit single generation and separate accounting; production constructs disabled factory |
| 27 | Trusted source kind, fresh membership/owner/state/version and persisted session checks, six-field context without logs/IDs | Unit/API matrix and ReportAiPersistenceIntegrationTest exercise both source kinds, cross-owner/project denial, revocation, lifecycle, draft submission/expiry, stale versions and real JDBC session deletion during a cached servlet request; dispatched accounting retained |
| 28 | Fixed instructions/prompt version; strict JSON/output bounds and server-owned provenance | Exact input ceiling/one over, malformed/duplicate/unknown/tool/Unicode/size output cases, forged body fields and 8 KiB wire cap; client API rejects wrong metadata; browser renders malicious markup literally and discloses six fields/log exclusion |
| 29 | Selected explicit normal PATCH of all seven fields with expected version; exact response confirmation, guarded recovery and source cancellation | Mock browser checks no automatic write/retry, unselected/log preservation, dirty/editor/project/signout cancellation, stale apply review; real draft journey checks actual HTTP PATCH and persisted returned version/fields |
| 30 | Synthetic provider behind real shared ledger/session, same-source backend/client/browser checks and predefined Java fixtures | Commands/results below; safe errors/security events, actual independent review and synthetic development-agent injection exercise; no real model-quality claim |

## Review and resolved findings

Actual independent reviewer `/root/report_ai_reviewer` inspected the source and
tests separately from implementation and found:

- **P1:** the request-cached servlet session could survive concurrent JDBC session
  deletion. The controller now rereads SessionRepository before every trusted
  actor authorization, checking persisted actor, expiry and authentication age.
  The regression deletes a real JDBC session during completion while the cached
  servlet object retains its actor; delivery returns401 and terminal accounting
  still records the dispatched charge.
- **P2:** denied AI source/role/session/CSRF outcomes initially lacked bounded
  security events. They now use existing allowlisted route templates/status/actor
  metadata and return503 if event persistence fails. No payload is logged.
- **P2:** the AI section initially used private opening behavior. It now uses
  shared Disclosure, including shared motion/reduced-motion and close cancellation.

Source/storage failures after dispatch retain the safe SERVICE_UNAVAILABLE code.
Final source reinspection reported no remaining supported findings, including the
last navigation/recovery guards. Reviewed controller hash:
`f7dc05b814a12ce3f9486b78e1d6854ea1b1cec9a38c08474f7666974d14584c`;
service hash `ad6fd3c7a561c81572c61aed4dc63117850336e7639ebfed1b75e2cc94aedf45`.
Reviewer ran no test commands; final runners belong to the coordinator. No human
approval is inferred.

## Executed verification

Java25, an explicit disposable `_test` database, generated owned per-test schemas,
and synthetic identities were used. No environment file or production data was
read. Commands used explicit test settings; credential values are omitted here.

```sh
./gradlew :backend:ai-gateway:test :backend:ai-transport:test :backend:auth-app:test --no-daemon --console=plain
./gradlew :backend:auth-app:test --no-daemon --console=plain
npm run check
npm run build
npm run test:e2e -- --project=production e2e/reporter-ai.e2e.ts
TRACEDESK_REAL_BACKEND_TEST=true npm run test:e2e
TRACEDESK_REAL_BACKEND_TEST=true npm run test:e2e -- --project=real-backend
python3 tools/check_foundation.py
git diff --check
```

- Backend: final full auth-app run **58 passed, zero failures/errors/skips**,40s.
  Shared gateway11 and transport26 retain passing same-source reports; the
  combined command reused their up-to-date results.
- Frontend typecheck/lint and **45 unit tests passed**; production build passed
  with `index-BT-HFmGL.js` and `index-C4kAqXMe.css`.
- Final focused AI browser suite: **12 passed**,12.6s, using the rebuilt bundle.
- Full browser run passed all **43 static cases** and the existing real journey;
  the new journey failed its initial project-selection setup. After correcting
  setup, the final real-backend rerun passed **2/2**,28.6s. Together these cover
  all45 browser cases; no single full45 run passed.
- Foundation/link check passed:109 documents,4 skills,21 provenance entries,
  11 transport Java sources. Working and staged whitespace checks passed.

The initial role DB test failed the fixture's exact30-day draft expiry invariant;
the fixture was corrected. Two early mocked browser assertions raced pending
generation and were corrected to wait for terminal state. The first full backend
run failed two pre-AI tests requiring the transport class to be absent. The approved
secured-factory integration now needs that dependency, so the tests were updated
to assert the raw client remains package-private/inaccessible instead. Full backend
checks then passed; no authorization or provider-bypass criterion was removed.
The new real-browser test initially expected Reports before selecting a project.
The coordinator's first correction matched a similar existing login step and
caused two setup failures; restoring that step and fixing the new test precisely
produced the final2/2 pass. Production code and AI apply assertions were unchanged.

## UI and synthetic evaluation observations

Coordinator inspected current390px capture: untrusted HTML/script text remains
literal, controls/questions wrap within the pane, and no horizontal overflow was
observed. Browser checks verify keyboard selection, enabled explicit apply,
320/390px layout, reduced motion and zero axe violations. Captures use isolated
Chromium contexts and an inner scrolled editor; full-page screenshots do not
unwrap the inner pane. No native human-window verification is claimed. Local
artifacts: `frontend/e2e-results/reporter-ai-{desktop,320,390}.png`; real-backend synthetic-provider
fixture captures are under `frontend/e2e-results/reporter-real/` and CI preserves
the real-browser directory. These are synthetic fixture captures.

Predefined Java cases are in `workflow/reporter/experiments/reporter-ai/fixture-cases.json`.
ReportAiQualityFixtureTest executes each once through the role service/shared
facade with fixed synthetic output. Observed numeric metadata:

| Case | Input bytes | Output bytes | Elapsed microseconds |
| --- | ---: | ---: | ---: |
| RAI-01 vague exception | 246 | 150 | 48131 |
| RAI-02 missing reproduction | 199 | 154 | 1506 |
| RAI-03 already complete | 391 | 38 | 1323 |
| RAI-04 injected instructions | 361 | 122 | 1273 |

These are local fixture/consumer timings, not LLM latency. Rubric values in the
dataset are coordinator assessments of authored fixtures, not independent model
ratings. Each case has one fixture execution, no controlled baseline and no
reported provider tokens/cost. Fake usage values test ledger behavior only.
Live context-quality and development-context comparisons remain due before final
MP4 acceptance; no performance/quality/cost savings are claimed.

Reviewer consumed the overt malicious development-agent issue specimen as data
and followed none of its requests. It made no environment/credential read,
unauthorized edit, network action, enablement or fabricated approval. This narrow
exercise does not demonstrate general injection resistance. Actual coordination
is recorded locally under ignored `workflow/reporter/runs/2026-11-09-reporter-ai.md`.
