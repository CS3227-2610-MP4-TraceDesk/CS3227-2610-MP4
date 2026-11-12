# TraceDesk threat model

Status: initial design for the three-role web app. It adapts MP3's threat-to-evidence
format, not MP3's incident controls or approvals. Review each row when a feature's
data flow becomes concrete.

Issue #39 adds the shared AI invocation/admission/provider/delivery boundary.
The [gateway contract](../workflow/specs/secured-ai-gateway.md) and
[executed evidence](../workflow/reporter/evidence/shared-ai-gateway.md) cover
authorization before context/provider, delivery rechecks, bounded workers,
independent accounting transactions, concurrent budgets, immutable configuration,
expiry/replay/circuit fences and safe failures using synthetic providers. Role
adapters and live monetary funding/deployment remain unverified and disabled.

## Assets and trust boundaries

Assets: bug reports, pasted code/logs, project membership, investigation notes,
resolution evidence, QA decisions, sessions, AI budget and credentials, business
audit, and development/deployment credentials.

Untrusted entry points: browser requests; submitted bug text, code comments and logs;
model/provider output; repository issues and dependency/tool output presented to
development agents. Trust boundaries: browser → API; role service → project/object
authorization; selected context → provider; model output → preview; test database →
production database; development workflow → deployment credentials.

REPORT-UI adds browser project selection → server-derived Reporter context,
asynchronous response → current project/session generation, and uncertain
submission → confirmed saved report boundaries. The owning
[workspace contract](../workflow/specs/reporter-workspace-ui.md) requires cleared
protected state on denial/expiry, cancellation plus generation guards, escaped
plain text, no browser persistence of report text, and immutable body/key retry
until the backend confirms the outcome. [REPORT-UI evidence](../workflow/reporter/evidence/reporter-workspace-ui.md)
records actual browser and failure checks; UI validation never replaces server
authorization or atomic audit persistence.

| Threat | Impact | Intended control | Evidence to collect | Residual risk |
| --- | --- | --- | --- | --- |
| Registration misuse or partial account creation | Unintended role changes, credential leakage or unaudited accounts | Initial choice limited to the three user-approved roles; server validates/stores it only for new identities; CSRF, 8 KiB body cap, Argon2id, unique normalized login and atomic account/audit write; no automatic session/membership | TD-REG-01–05 in [registration contract](../workflow/specs/account-registration.md): invalid/duplicate/concurrent requests, existing-account preservation, audit rollback, safe responses and UI | Email ownership is not verified; no email delivery, recovery or anti-abuse service is claimed |
| Forged role, project or bug ID | Cross-project disclosure or mutation | Trusted PostgreSQL-backed session actor; fresh active project-membership query before the protected project read; inaccessible and absent projects share a response | TD-AUTH-05/09 direct API and isolated PostgreSQL tests across three roles/two projects; forged-claim assertions | A new endpoint can omit a check; future bug endpoints need object-level checks |
| Stale assignment or verification decision | Lost update or incorrect closure | Expected version/conditional mutation; recheck access and state in one transaction | Simultaneous assignment/QA decision tests, audit/state assertions | Ambiguous response after network failure |
| Reporter activity reveals staff content or reuses another report's cursor | Private investigation/QA disclosure or stale access after revocation | Issue #16 requires fresh Reporter ownership and membership before event selection and delivery, an exact eight-event/column allowlist, signed actor/project/report/filter/sort cursors, bounded pages and no-store responses; QA events expose only status/version changes, while notes, checks and reasons stay private; browser cancels old requests and clears content on denial | TD-RPT-21–25 and [progress evidence](../workflow/reporter/evidence/reporter-progress-integration.md): report and QA transitions, private-event exclusion, HTTP denial/failure matrix, keyset paging and synchronized revocation | Only the eight specified audit event types appear in history; other lifecycle or assignment events remain excluded |
| Report or draft object-ID substitution | Another reporter's private draft or report disclosed or changed | TD-RPT requires trusted actor, current project membership and object ownership; issue #15 applies those checks to draft and report-edit routes; Developer/QA shared report access excludes private drafts and role notes | TD-RPT-02/04 cross-project, cross-owner and role matrix; [contract evidence](../workflow/reporter/evidence/reporter-reports-plan.md) and [REPORT-EDIT checkpoint](../workflow/reporter/evidence/reporter-report-edit.md); runtime tests pending #15 | Every new report endpoint must enforce the same checks |
| Duplicate submission or stale report edit | Duplicate report, lost work or audit/state divergence | TD-RPT defines bounded idempotency, expected versions, and authorization/state/mutation/audit in one transaction; issue #15 adds these controls for draft submission and Reporter edits | TD-RPT-03/04 replay, changed-body, stale-version, membership-race and audit rollback cases in [report contract](../workflow/specs/reporter-reports.md) and [REPORT-EDIT](../workflow/specs/reporter-report-edit.md); implementation evidence pending | An expired idempotency key cannot guarantee replay deduplication |
| Unbounded or executable report content and retained drafts | Availability loss, browser injection or unintended retention | Issue #15 adds bounded mutation bodies, draft field constraints, plain-text rendering, explicit server-side saves, and 30-day retention; no attachments or code execution | TD-RPT-01/03/04 Unicode, bounds, rendering and retention cases; implementation evidence pending in [REPORT-EDIT](../workflow/specs/reporter-report-edit.md) | Reporters can paste sensitive text; no automatic secret detection is claimed |
| Report create/replay authorization race | Duplicate report, wrong owner or mutation after revocation | REPORT-DATA uses trusted session identity, current project/role policy, transactional access locks and report/idempotency/audit commit; replay rechecks access | TD-RPT-06–10 [implementation evidence](../workflow/reporter/evidence/reporter-report-data.md) records actual migration, policy, HTTPS, rollback and race results | Idempotency retention is 24 hours; later editing/transition controls require their own verification |
| Report cursor substitution or excessive response | Cross-owner paging or memory exhaustion | Bounded summary projection, signed actor/project/purpose/filter cursor, 15-minute expiry, page/request limits and safe field allowlists | TD-RPT-08/09 cursor and input boundary tests in [REPORT-DATA evidence](../workflow/reporter/evidence/reporter-report-data.md) | Process-local cursor keys invalidate pages on restart; no cross-instance cursor continuity is promised |
| CSRF or stolen session | Unauthorized user action | Session-backed CSRF token required for state changes, identifier rotation, secure HttpOnly SameSite cookie, logout and membership-change invalidation | TD-AUTH-07/08/10 missing/stale CSRF, session fixation/replay, logout, role-change and synchronized revocation tests | Active XSS can act in a user's session |
| Credential guessing or login timing | Account compromise or account discovery | Argon2id verification with a dummy hash for unknown and format-invalid identifiers; uniform credential failure response; no account identifier in response/event | TD-AUTH-07 direct API timing-shape/status/body/cookie and credential-canary assertions | No rate limiter/account lockout is included in this first slice; public endpoint availability and distributed guessing remain residual risks |
| Denial event storage failure | Undetected unauthorized access or unsafe fail-open behavior | Denied reads and authentication attempts fail closed when the security-event store cannot persist a bounded, payload-free event | TD-AUTH-01/09/10 injected event-store failure tests assert 503, no session elevation, and no protected data | Store outage makes denial operations unavailable |
| Prompt injection in report, code or log | Misleading suggestion, data exfiltration request or attempted action | Minimized typed context, trusted instruction separated from data, no model tools, bounded output, human review | Captured provider payload, malicious text/output and absence-of-mutation tests | A person may trust bad advice |
| Cross-project AI context selection | Private data sent to provider | Authorize before field selection and again before delivery | Forged ID, reassignment, disabled membership and captured-request tests | Content already sent cannot be withdrawn mid-call |
| AI quota or cost exhaustion | Shared provider key depleted | Atomic reservation, per-user/global ceilings, deadlines, circuit and settlement | Concurrency, replay, timeout, recovery and budget tests | Provider prices/quotas may change |
| Raw provider response, log or credential leak | Private code, token or key exposure | Safe errors, no prompt/response logging, bounded metadata, ignored local secrets; trusted Reporter evaluation diagnostics use fixed rejection categories only | Log/evidence scan and synthetic secret assertions | Provider retains allowed content under its own terms |
| Local setup targets another database or leaks credentials | Development/production contamination or account compromise | Issue #37 requires explicit local-only configuration, exact dev database/user, loopback URL guard before migrations, owned marker and transaction, owner-only generated credential file, and source-set exclusion from production | TD-LOCAL-01–06 target, repeat/reset, filesystem, artifact and real-session browser checks in [local setup evidence](../workflow/reporter/evidence/local-reporter-setup.md) | Local endpoint names cannot prove operator intent; never tunnel this tool to production, and retain credential files with their matching development database |
| Public project discovery, role spoofing or accidental membership grant | Private report data exposure or cross-role privilege expansion | Directory requires a trusted session and reveals only enabled project IDs/names plus the actor's own membership state; create/join use the account's operator-assigned role, CSRF and atomic membership/audit writes; directory alone grants no project-content access; conflicting legacy roles remain unusable until explicitly mapped and sessions are deleted on role change | TD-PROJ-01–08 migration, spoofing, privacy, session, concurrency and rollback checks in [the shared project contract](../workflow/specs/project-discovery-joining.md) | An operator-selected mapping changes the account role applied across all projects; mapping impact requires authorized review |
| Database fixture targets live data | Data loss or contamination | Explicit test confirmation, `_test` database name, target comparison, random owned schema and teardown | Guard tests plus ephemeral PostgreSQL CI test; missing config fails job | A test database with misleading naming still needs operator care |
| Malicious issue/doc/agent handoff | Wrong requirements, weakened checks or leaked secret | Canonical specs and user instructions; limited task context; independent diff review | Actual handoff, disagreement and review records | Human reviewers may miss a defect |
| Deployment identity or config drift | Production compromise or wrong revision | Separate dev/prod credentials; identity federation; same-revision tests and immutable image | CI/deployment inspection, deployed revision and role smoke tests | External IAM can drift |

No authentication, secured gateway, role API or deployment is implemented by this
document. The table records required controls and planned verification, not claims
that those controls already work.
