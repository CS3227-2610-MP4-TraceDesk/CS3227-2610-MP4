# REPORT-UI issue #14 evidence

Status: implemented; local checks and independent review are recorded below. Base `9c9f448`; branch
`codex/reporter-workspace-ui`. First coordinator observation 2026-11-08
05:29:16 UTC. Human student ownership not supplied.

## Plan and ownership

TD-RPT-11–15 use the bounded Reporter workspace specification and existing
report APIs. Specifier owns the new UI contract; frontend implementer owns
production UI/client/unit tests; integration-test implementer owns the real
browser/isolated-backend fixture and extensions to existing Playwright/Gradle/CI.
Coordinator integrates docs and runs verification; a separate reviewer inspects
actual code, tests and results. All specialized agents use GPT-6 Luna.
Budgets: specifier 25 calls/20 minutes, frontend 70 calls/50 minutes, real-browser
tests 65 calls/45 minutes, reviewer 30 calls/30 minutes. Stop and reassess after
three similar failures; unknown telemetry remains unknown.

Scope: real authenticated Reports list/detail/composer and server-derived project
context. No project discovery API exists, so a supplied project UUID/link is an
untrusted target authorized by the server. Exclude persisted drafts, editing,
environment CRUD, role portals, AI and deployment. Shared shell/numeric design
contracts govern layout; QA fixtures never enter the authenticated Reporter app.
Reporter captures establish a new synthetic baseline, not copied QA pixels.

TD-RPT-11 maps to real context/visibility navigation, 12 to canonical validation/
plain text/input retention, 13 to actual loading/empty/denial/session/outage
states, 14 to immutable retry key/body and confirmed backend success, 15 to
real browser→same-origin API→isolated PostgreSQL plus keyboard/axe/contrast/
responsive/reduced-motion evidence. Mocks supplement and do not replace the
real journey. Pin source hashes before final checks; record failures/skips,
actual review/corrections and screenshot environment/geometry.

Pre-existing changes are preserved: `backend/auth-app/build.gradle` already
adds the Flyway starter, and `local-flyway-autoconfiguration.md` is untracked.
They are not authored by this task. The new fixture/task must not hide these
changes or claim an unmodified checkout when testing this working tree.
The user requested multiple logical commits for changes over 1,000 lines;
coordinator will separate contract, implementation and verification commits,
excluding the pre-existing local changes. No push is authorized in this task.

Measurement case RPT-UI-01 is one bounded run, not a controlled comparison.
Record role UTC starts/ends, selected context bytes, actual calls/retries and
unknown tokens/cost. Coordinator owns the development-context comparison due
before final MP4 acceptance; three-role application comparison remains due
after secured AI exists. No comparative savings claim follows from this run.


## Candidate and commands

Production changes are commits `ba9bbbd` (client/validation) and `6593a70`
(workspace), following contract commit `05cafe0`. The final
[candidate manifest](reporter-workspace-ui/candidate.json) pins SHA-256 and UTF-8
bytes for all changed source, tests and configuration, including untracked files.
The manifest explicitly includes the pre-existing local Flyway dependency in the
tested build file; the feature commits exclude that dependency. Screenshot
metadata alone hashes tracked changes only, so the manifest is authoritative for
untracked sources. Subsequent commits package those same file contents and docs.

Commands below ran from `frontend/` unless a repository-root command is identified.
Database checks used the existing local `test-db` service on loopback port 54330,
database/user `tracedesk_test`, explicit isolation confirmation and synthetic
fixture password. No environment files or live application database were read.

| Check | Actual result |
| --- | --- |
| `npm run check` | PASS: TypeScript, ESLint (zero warnings), 26 unit/component tests across five files. Final full check after browser-selector corrections passed. |
| `npm run build` | PASS: 822 modules; production CSS/JS generated. Production sources remained unchanged during final browser checks. |
| `npm run test:e2e` | PASS: 31 tests in 38.0s. Includes foundation/login/shared shell and six controlled Reporter failure tests. These controlled tests are supplemental, not real backend evidence. |
| `TRACEDESK_REAL_BACKEND_TEST=true npm run test:e2e -- --project=real-backend` with explicit isolated test DB settings | PASS: one integrated real HTTPS browser journey. Final capture run passed in 23.9s (journey 10.8s), recorded in the manifest. No route mocks in this journey. |
| Root `./gradlew :backend:auth-app:test --no-daemon` with isolated test settings | PASS in 18s: 28 tests, zero failures/errors/skips, including real HTTPS report boundaries and persistence concurrency/idempotency/audit rollback. |
| Root `./gradlew :backend:auth-app:compileTestJava --no-daemon` | PASS: new fixture compiles with Java 25. |
| Fixture startup/stop smoke | PASS: owned HTTPS backend became ready and cleaned its temporary files/process/schema. |
| Missing-isolation-settings probe | PASS: fixture helper refused startup before database contact when required configuration was removed. |
| Root `python3 tools/check_foundation.py` and `git diff --check` | PASS: 95 documents, 4 skills, 21 provenance entries, 9 transport Java sources; whitespace clean. Final capture-helper/browser-test ESLint also passed. |

### Corrections and earlier failures

Do not treat earlier runs as passing. Frontend check attempts 1–3 stopped on type
narrowing, strict effect/ref lint rules, then an unreachable state comparison;
implementation was corrected without disabling checks. Attempt 4 reached 24/27
units: two new tests used ambiguous New report selectors and one expected an
impossible wire overflow. Maximum valid code points/UTF-8 values inherently stay
below the wire cap; the corrected test verifies that feasible boundary. Attempt 5
reached 24/26 and exposed Environment/Environments selector ambiguity. Attempt 6
reached 25/26: a blocked Settings→Environments navigation correctly remained in
Settings, but the test expected the composer immediately. The test now returns to
Reports and verifies the preserved form. Later full checks passed 26/26.

Initial ordinary browser run passed 24/31. Six failures were a shared ambiguous
Reports h1/h2 selector; one legacy startup test assumed both StrictMode requests
would reach its mock. The corrected startup fixture explicitly restores
availability before retry and retains the request/error/recovery assertions.
The next complete run passed 31/31. Existing development-preview SVG-import and
Node color warnings remain non-fatal; production build passed.

Real-browser attempts 1–4 failed on Password/Show password selector ambiguity, a
nonexistent `Project:` label, an incorrect expectation of a project form after
reauthentication at an authorized project URL, and an immediate focus assertion
racing the implementation's next-frame focus. The final focus assertion polls for
actual focus and visible focus; no assertion was removed. The third attempt had
already passed create/list/detail, real 503 retry and membership/role denial.
The fifth journey passed. A further targeted capture rerun waits for actual shell
animations to finish because an empty-list screenshot had caught the sidebar
halfway through opening. The team reassessed the full selector/interaction
sequence after repeated new-test failures rather than altering product semantics.

## Acceptance mapping

| Criterion | Evidence and result |
| --- | --- |
| TD-RPT-11 | PASS locally: real session rotation/cookie flags, server context, list→create→actual detail→list→read/reload; Reporter/nonmember/Developer context denial. Component tests reject stale project responses and stale old-account 401s. Shared AppShell/SDK controls reused; no browser role identity source. |
| TD-RPT-12 | PASS locally: per-field exact/over bounds, supplementary Unicode, Java-compatible blank rules, allowed line endings, forbidden controls/surrogates and combined maxima in validation units. API response-shape tests. Real browser checks required labels, invalid focus, literal markup/log text, retained body through a real service outage; no injected DOM nodes or executed canaries. |
| TD-RPT-13 | PASS locally: real empty/filter-no-results, opaque project/nonmember 404, Developer 403, revoked session 401, real audit-outage 503 and recovery. Real loading states consume actual pending requests; component tests also hold delayed requests. Supplemental controlled browser network loss/known validation/business denial/conflict tests assert honest states. |
| TD-RPT-14 | PASS locally: actual 201 only, real double click produces one POST, 503 retry keeps identical body/key and opens actual returned status/version. Existing isolated HTTPS/database tests cover changed-body conflict, replay and concurrency. Supplemental UI tests cover CSRF-only one-time retry, known 400 editable/new-key behavior, 403 and 409 without success. Pending navigation and 20-second timeout freeze are component-tested. No persistent draft/edit capability added. |
| TD-RPT-15 | PASS locally with limitations below: real browser→HTTPS same-origin proxy→isolated backend/PostgreSQL, login/logout/re-auth/revocation; desktop/320/390 geometry, overlay Escape/focus return, field focus, light/dark axe (including contrast), reduced motion and form retention. Hosted CI is configured, not yet run on GitHub. |

## Security and visual evidence

The test-only Java entry point uses test classpaths, a random owned schema in the
confirmed `_test` database, synthetic accounts/projects and temporary loopback TLS.
It asserts the active DataSource and schema before seeding. Local file commands
invalidate synthetic sessions or temporarily rename the owned audit table; no
administrative HTTP endpoint is introduced. The fixture/task is excluded from the
production application artifact. The same-origin test proxy disables certificate
verification only for its fixed loopback backend when the explicit test flag is
set. Real browser traces/video and automatic failure screenshots are disabled to
avoid preserving session/credential traffic; retained screenshots contain only
synthetic report content. The pre-existing database service is left running.

[Captures and geometry](reporter-workspace-ui/captures.json) retain ten actual
states: empty/list/detail, light/dark composer, 320/390 composer/detail and 320
reduced-motion composer. PNG files are beside the metadata. Chromium
156.0.8078.4 on Darwin 24.2.0, system UI font stack, scale 1; desktop 1440×900,
mobile 320/390×844. Each capture records UTC, revision, theme, geometry and motion.
Coordinator inspected light/dark desktop composer, mobile 320 composer/detail,
empty/list captures. No horizontal overflow; desktop form 816px, header 52px,
sidebar 275px; mobile rail 40px. Computed form padding is 28/24/48px,
heading 28px, label weight 600, grouped border 1px/radius 16px. Shared numeric
contracts govern this new Reporter baseline; existing QA reference screens only
supply shell/component geometry. Screenshots show the top of the internally
scrollable main pane; lower fields/actions remain reachable through scrolling
and were exercised by the actual submission test.

Authorization and persistence remain server-owned. UI generations/abort signals
prevent stale account/project requests from repopulating protected state.
Reviewer/root findings resolved first-submit closure/duplicate-lock behavior,
request cleanup, CSRF cancellation, stale filtered rows, same-project retry,
unknown-outcome retry/navigation, Settings→Environments guard and denied POST404.
No AI/provider boundary changes; prompt injection/apply tests are not applicable
to this slice. No report text or security material is persisted in browser storage.

## Independent review, measurement and limitations

Independent reviewer `/root/report_ui_reviewer` (GPT-6 Luna) inspected actual
contracts, production code, tests and test fixtures. Production-source review at
2026-11-08 06:11:45 UTC found no supported remaining code defect; final read-only evidence review at 2026-11-08 06:19:18 UTC also found no
supported findings. It independently checked all 19 source/config hashes and
byte counts, capture metadata and all ten PNG hashes/sizes with zero mismatches,
and inspected desktop light/dark and mobile composer visuals. It was read-only and did not run tests itself.
Coordinator ran all verification and reviewed screenshots. No human approval,
student ownership, deployment or GitHub CI success is inferred.

[Observations](reporter-workspace-ui/observations.jsonl) record actual known
intervals; unknown tokens, cost, total calls, input-context bytes and incomplete
agent start times stay null. The reviewer observation window was 37m08s,
including waits for final evidence, exceeding its initial 30-minute planning
budget; the bounded final pass remained read-only. Specifier reported 15 calls/zero retries over its
known interval; implementer's approximate call estimate is not an exact metric.
Output artifact byte counts are not input context size. Full run handoff is in
the ignored local run log, with portable decisions/findings retained here.
Coordination improved isolation/retry review but introduced substantial handoff
and new-test correction work. This single case has no controlled baseline and
supports no speed, token or cost savings claim. Development-context comparison
remains coordinator-owned before final MP4 acceptance; three-role application
comparison follows secured AI. Those milestone experiments remain pending.

Limitations: automated Chromium/axe is not a complete assistive-technology audit;
no Safari/Firefox, manual screen-reader, production deployment or hosted CI run
is claimed. Project discovery is absent in the existing API; the user needs an
authorized project UUID/link. Environments is visibly unavailable. Persisted
drafts, editing, AI and environment CRUD remain outside issue #14.
