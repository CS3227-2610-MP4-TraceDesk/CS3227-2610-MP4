# TraceDesk threat model

Status: initial design for the three-role web app. It adapts MP3's threat-to-evidence
format, not MP3's incident controls or approvals. Review each row when a feature's
data flow becomes concrete.

## Assets and trust boundaries

Assets: bug reports, pasted code/logs, project membership, investigation notes,
resolution evidence, QA decisions, sessions, AI budget and credentials, business
audit, and development/deployment credentials.

Untrusted entry points: browser requests; submitted bug text, code comments and logs;
model/provider output; repository issues and dependency/tool output presented to
development agents. Trust boundaries: browser → API; role service → project/object
authorization; selected context → provider; model output → preview; test database →
production database; development workflow → deployment credentials.

| Threat | Impact | Intended control | Evidence to collect | Residual risk |
| --- | --- | --- | --- | --- |
| Forged role, project or bug ID | Cross-project disclosure or mutation | Trusted PostgreSQL-backed session actor; fresh active project-membership query before the protected project read; inaccessible and absent projects share a response | TD-AUTH-05/09 direct API and isolated PostgreSQL tests across three roles/two projects; forged-claim assertions | A new endpoint can omit a check; future bug endpoints need object-level checks |
| Stale assignment or verification decision | Lost update or incorrect closure | Expected version/conditional mutation; recheck access and state in one transaction | Simultaneous assignment/QA decision tests, audit/state assertions | Ambiguous response after network failure |
| CSRF or stolen session | Unauthorized user action | Session-backed CSRF token required for state changes, identifier rotation, secure HttpOnly SameSite cookie, logout and membership-change invalidation | TD-AUTH-07/08/10 missing/stale CSRF, session fixation/replay, logout, role-change and synchronized revocation tests | Active XSS can act in a user's session |
| Credential guessing or login timing | Account compromise or account discovery | Argon2id verification with a dummy hash for unknown and format-invalid identifiers; uniform credential failure response; no account identifier in response/event | TD-AUTH-07 direct API timing-shape/status/body/cookie and credential-canary assertions | No rate limiter/account lockout is included in this first slice; public endpoint availability and distributed guessing remain residual risks |
| Denial event storage failure | Undetected unauthorized access or unsafe fail-open behavior | Denied reads and authentication attempts fail closed when the security-event store cannot persist a bounded, payload-free event | TD-AUTH-01/09/10 injected event-store failure tests assert 503, no session elevation, and no protected data | Store outage makes denial operations unavailable |
| Prompt injection in report, code or log | Misleading suggestion, data exfiltration request or attempted action | Minimized typed context, trusted instruction separated from data, no model tools, bounded output, human review | Captured provider payload, malicious text/output and absence-of-mutation tests | A person may trust bad advice |
| Cross-project AI context selection | Private data sent to provider | Authorize before field selection and again before delivery | Forged ID, reassignment, disabled membership and captured-request tests | Content already sent cannot be withdrawn mid-call |
| AI quota or cost exhaustion | Shared provider key depleted | Atomic reservation, per-user/global ceilings, deadlines, circuit and settlement | Concurrency, replay, timeout, recovery and budget tests | Provider prices/quotas may change |
| Raw provider response, log or credential leak | Private code, token or key exposure | Safe errors, no prompt/response logging, bounded metadata, ignored local secrets | Log/evidence scan and synthetic secret assertions | Provider retains allowed content under its own terms |
| Database fixture targets live data | Data loss or contamination | Explicit test confirmation, `_test` database name, target comparison, random owned schema and teardown | Guard tests plus ephemeral PostgreSQL CI test; missing config fails job | A test database with misleading naming still needs operator care |
| Malicious issue/doc/agent handoff | Wrong requirements, weakened checks or leaked secret | Canonical specs and user instructions; limited task context; independent diff review | Actual handoff, disagreement and review records | Human reviewers may miss a defect |
| Deployment identity or config drift | Production compromise or wrong revision | Separate dev/prod credentials; identity federation; same-revision tests and immutable image | CI/deployment inspection, deployed revision and role smoke tests | External IAM can drift |

No authentication, secured gateway, role API or deployment is implemented by this
document. The table records required controls and planned verification, not claims
that those controls already work.
