# REPORT-PLAN issue #12 evidence

Status: TD-RPT-01–05 contract/design gates complete with independent review;
no report feature implementation or runtime acceptance is claimed.
Started 2026-11-07 at 07:06:38 UTC.
Base revision: `3f40a6e`; branch: `codex/reporter-report-contract`.
Human student ownership has not been supplied.

## Bounded plan

Deliver `workflow/specs/reporter-reports.md`, index it, map TD-RPT-01–05 to
observable future tests and independent contract review, and update applicable
threat-model references. No controllers, migrations, UI or AI invocation in this
issue. Existing approved architecture authorizes ordinary bounded choices; do
not invent human approvals or import historical MP3 roles.

Coordinator owns integration/index/security/evidence. A GPT-6 Luna specifier
owns only the canonical spec; a separate GPT-6 Luna reviewer inspects its actual
contract for contradictions, missing authorization/transaction/error details
and test derivability. Specifier budget: 15 calls/25 minutes; review budget:
10 calls/20 minutes. Stop for material scope/security uncertainty or three
materially similar failures. Use synthetic examples, never environment files.

Verification plan: independent contract audit, criteria/endpoint/role matrix
cross-check, Markdown local-link and whitespace checks. Runtime test suites are
not applicable to a docs-only contract; future implementation tests must not be
reported as already passed. Capture role UTC intervals, selected references and
full-file UTF-8 bytes, observable calls/retries; tokens/cost unknown.

Measurement case RPT-PLAN-01: one bounded spec/review task, not a controlled
comparison. Refer to `workflow/reporter/experiments/agentic-se/plan.md` for the
development comparison (coordinator-owned, due before final MP4 acceptance) and
the three-role application comparison (due at that milestone after secured AI
capabilities exist). Quality rubric: internally consistent contracts, derived
allowed/denied/stale/concurrent tests, no security/role scope expansion and no
misleading acceptance or approval claims.

## Criterion and boundary evidence

| Criterion | Contract review target | Future runtime evidence (not run in #12) |
| --- | --- | --- |
| TD-RPT-01 | Field/representation tables, Unicode/byte/body bounds, UUID/UTC, trusted project context | Validator boundaries, round trips, safe text rendering and bounded response assertions |
| TD-RPT-02 | Identity/visibility, assignment recovery, OPEN edit limit, lifecycle and clarification rules | Two-project/three-role owner/role matrix and synchronized membership/assignment races |
| TD-RPT-03 | Draft lifetime/cap/submission, idempotency, versions and transactional business audit | Isolated PostgreSQL replay/concurrency, expiry, stale versions and audit rollback |
| TD-RPT-04 | Route/payload/response, pagination, errors and failure precedence | Per-route allowed/denied/unavailable/stale/concurrent API checks and no unauthorized selection/mutation |
| TD-RPT-05 | Actual specifier/reviewer handoffs, corrections, this evidence and candidate identity | Each implementation slice supplies its own same-candidate runtime results and independent review |

Changed trust boundaries are browser→report API, session→fresh project/object
authorization, draft owner→shared submitted report, and mutation→business audit.
The threat model now links these planned controls to this contract. AI/provider
boundaries and deployment remain unchanged and unverified by this task.

## Review and corrections

The specifier authored only the canonical contract. The coordinator integrated
the acceptance index, threat model and evidence. A separate GPT-6 Luna reviewer
read the actual contract and integration diff, using `tracedesk-review`.

The initial consistency pass corrected conflicting aggregate-byte arithmetic
(the seven maxima sum to 184,640 bytes), clarification/report version wording,
unbounded embedded history and full-text lists, unspecified cursor semantics,
draft-create idempotency, and a validation-field map incompatible with the fixed
authentication error envelope. It also separated future issue ownership from
contract acceptance and specified minimal trusted Reporter context.

Independent review identified a replay-order defect: resolving an expired or
purged draft before looking up the submission record contradicted the 24-hour
retry promise. It also identified ambiguous clarification version ownership,
CLOSED assignment clearing, and cursor substitution across resources. These
are contract defects discovered before runtime implementation; no claim of
runtime bug reproduction or passing runtime tests is made.

Those defects are corrected. The reviewer rechecked the behavior contract and
requested two final test-mapping additions: submit near expiry → purge → replay
within 24 hours (including revoked-access denial), and clarification 5/6 and
1,000/1,001 boundaries with no partial mutation/audit. Both were added; the
focused final independent recheck at 07:22:52–07:22:54 UTC confirmed resolution
with no remaining findings. Contract gates TD-RPT-01–05 pass at specification
level. Runtime criteria and tests for #13–17 remain pending.

## Candidate and checks

Candidate: `3f40a6e9b15b-plus-4b02c4a43baf4244`; base
`3f40a6e9b15b3b818317bab57ac67d8e44fda19b`.
The [manifest](reporter-reports-plan/candidate.json) pins full-file SHA-256 and
byte sizes for the spec, acceptance index and threat model. Evidence files are
excluded from its identity to avoid self-referential hashes. Changes are
uncommitted; no commit, push, PR or issue closure was performed.

- `git diff --check`: passed; tracked Markdown changes have no whitespace errors.
- Inline Python local-link/whitespace check over all four changed Markdown
  files, including untracked files: passed, 24 local links resolved and final
  newlines present. Closeout recheck passed with all 27 local links, whitespace,
  final newlines and all manifest SHA-256 values matching the final candidate.
- Inline Python contract check: passed, TD-RPT-01–05 and the five route groups
  present; individual field byte maxima sum to 184,640.
- Measurement validation uses the existing Reporter tool and
  [sanitized observations](reporter-reports-plan/observations.jsonl).
  `python3 workflow/reporter/tools/agentic_measurements.py workflow/reporter/evidence/reporter-reports-plan/observations.jsonl`:
  passed, four observed records validated.
- Runtime/backend/frontend suites: not run; no behavior or executable code
  changed. Planned tests in the canonical contract are not observed results.
- Two coordinator patch applications failed due to unmatched context, then
  were corrected using actual file content; no application code was affected.

## Resource observations and reflection

Specifier: GPT-6 Luna, 2026-11-07 07:10:48–07:20:50 UTC (602 seconds).
Its 13 reported selected-reference snapshots total 63,207 UTF-8 bytes; its
28,813-byte authored output brings the reported inspected reference/output
total to 92,020 bytes. These are full-file snapshot sizes, not tokenizer counts
or cumulative repeated-read traffic. Reference paths: spec-development skill,
acceptance index, product, ADR 002, architecture, security, AI policy,
auth-membership spec, feature-spec template, Agentic SE gates, shared-ui spec,
Reporter README, and this evidence file. The specifier did not reliably record
call/retry counts and reported exceeding its 15-call budget; counts remain
unknown, not zero. Its time budget was met. Tokens and costs are unknown.

Independent review: GPT-6 Luna, 07:13:41–07:22:06 UTC (505 seconds), 13 exec
calls including one failed quoting attempt and retry, four coordination messages
and two waits; the 10-call budget was exceeded, time budget met. Ten selected
full-file snapshots totaled 76,436 UTF-8 bytes. The final focused recheck used
two outer tool calls, no retries, and selected the 29,336-byte spec and
6,024-byte evidence (35,360 bytes). These snapshot totals vary as files change;
they are not cumulative model-context usage. Exact selected paths/metrics are
in the observations and [specifier context record](reporter-reports-plan/specifier-context.json).
Coordinator call/retry counts and token/cost telemetry were not reliably
captured and remain unknown. Coordinator timing covers the whole observation
window and overlaps agent time; durations must not be summed as elapsed time.

The task remained docs-only and sequential: specifier → independent review →
coordinator correction/integration → review recheck. Multiple incremental
coordinator clarifications increased coordination overhead and contributed to
the call-budget overrun; the better next handoff is one consolidated contract
checklist before drafting. Review caught implementability defects, but this
single task has no controlled baseline and supports no speed, quality or token
savings claim. The development-context and three-role application comparisons
remain separate milestone gates.
