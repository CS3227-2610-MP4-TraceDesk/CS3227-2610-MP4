# DEV-AI — Developer investigation suggestions

Feature: [issue #31](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/issues/31),
TD-DEV-26 through TD-DEV-30. Status: draft implementation contract, written
2026-11-09; an independent agent contract review and its corrected P2 are
[recorded](../developer/runs/2026-11-09-dev-ai-contract-review.md). No implementation
or runtime criterion is accepted by this file. Human owner: the Developer contributor
(GitHub `Izn432`).

This slice consumes the [shared secured gateway](secured-ai-gateway.md), the
[Developer investigation contract](developer-investigation.md) (TD-DEV), the
[Reporter report contract](reporter-reports.md) (TD-RPT), the
[AI policy](../../docs/ai-policy.md), [ADR 002](../decisions/002-shared-role-contracts.md)
and the [Developer completion gates](../developer/agentic-se.md). The
[Reporter AI contract](reporter-ai.md) is a pattern, not a source of authority for
Developer data. The gateway is implemented and has
[recorded evidence](../reporter/evidence/shared-ai-gateway.md). No production
enablement, funding verification, live provider call or deployment is implied;
those stay with [AI-ENABLEMENT](ai-enablement.md).

## Scope

The currently assigned Developer of an `IN_PROGRESS` report explicitly requests
bounded hypotheses and next steps, chooses what context is sent, reads an advisory
preview, and may turn selected items into an ordinary private note after editing.

Excluded: other statuses (AI never starts an investigation), resolution prefill,
any lifecycle or assignment action, repository/file/diff ingestion, automatic
patches, code execution, tools, streaming, conversation, background work, automatic
retry, a clipboard action, other roles' AI, deployment and a gateway change.
Ordinary investigation must remain fully usable when AI is unavailable.

## Context selection

Selection is explicit per request, happens only after authorization, and is the only
way text reaches the provider.

| Source | Eligible | Default in UI |
| --- | --- | --- |
| Report fields `title`, `description`, `environment`, `reproductionSteps`, `expectedBehavior`, `actualBehavior` | Any non-empty subset, with `textLogs` | Selected |
| Report field `textLogs` | Same; exact saved text | Not selected |
| Investigation notes on this report | 0–5, selected by ID | Not selected |

Never select: clarification text, resolutions, rejection reasons, other reports or
projects, user/session/account data, UUIDs, emails, timestamps, note authorship,
audit or accounting data, or credentials. The browser cannot supply prompt text.

Server-side data is exactly this JSON object, with report keys in the order above
and notes ordered by `created_at ASC, id ASC`:

```json
{"report": {"title": "...", "textLogs": "..."},
 "notes": [{"ref": "note1", "body": "..."}]}
```

`report` holds only the selected keys, using exact saved text. `notes` is always
present and may be empty. Each `ref` is `note1`, `note2` and so on, in that order, so
no UUID reaches the provider. All values
are untrusted JSON data, separate from server-owned instructions. There is no
truncation, splitting, summarizing or redaction claim.

**Size ceiling.** Before admission, require UTF-8 bytes of the system instruction
plus serialized data plus 256 framing units to be at most 8000, matching the gateway's
estimate. The instruction is at most 1500 UTF-8 bytes. Over-limit requests fail with
`400 AI_CONTEXT_TOO_LARGE` and no partial send. A Developer cannot edit a long report,
so the recovery is to deselect fields or notes, or to save a short excerpt as a note
and select that note. The excerpt then has the same authorization, versioning and
audit as any note, so the request needs no free-text input path.

## Interface

`POST /api/projects/{projectId}/reports/{reportId}/investigation/suggestions` uses
same-origin session, CSRF, `Cache-Control: no-store`, strict JSON and the TD-RPT
error shape. The body cap is 8 KiB. There is no `Idempotency-Key`; generation is one
explicit action and is never replayed.

```json
{
  "expectedVersion": 3,
  "reportFields": ["title", "description", "textLogs"],
  "notes": [{"id": "00000000-0000-4000-8000-000000000002", "expectedVersion": 2}]
}
```

`expectedVersion` is a positive integer report version. `reportFields` has 1–7 unique
names from the allowlist. `notes` is required, with 0–5 objects of exactly
`{id, expectedVersion}` and unique canonical UUIDs. Unknown or duplicate keys, nulls,
wrong types and server-owned values (actor, role, status, assignee, prompt, model,
provider, URL, quota) return `400 INVALID_REQUEST`.

Success is `200` with exactly these keys:

```json
{
  "capability": "INVESTIGATION",
  "promptVersion": "investigation-suggestion-v1",
  "reportId": "00000000-0000-4000-8000-000000000001",
  "reportVersion": 3,
  "reportFields": ["title", "description", "textLogs"],
  "sourceNotes": [{"ref": "note1", "id": "00000000-0000-4000-8000-000000000002", "version": 2}],
  "hypotheses": [{"statement": "...", "rationale": "...", "sources": ["textLogs", "note1"]}],
  "nextSteps": ["..."]
}
```

Everything except `hypotheses` and `nextSteps` is server-owned provenance, never
copied from model output. Change `investigation-suggestion-v1` whenever instructions
change; the browser adapter validates the exact value.

### Model output

The completion must be exactly one JSON object with exactly `hypotheses` and
`nextSteps`, with no code fence, trailing content or extra keys.

| Item | Bounds |
| --- | --- |
| `hypotheses` | 0–3 objects, each exactly `{statement, rationale, sources}` |
| `statement` | Nonblank, at most 300 code points and 1200 UTF-8 bytes |
| `rationale` | Nonblank, at most 500 code points and 2000 UTF-8 bytes |
| `sources` | 1–4 unique labels, each a report key or note `ref` actually sent in this request |
| `nextSteps` | 0–4 nonblank strings, each at most 240 code points and 960 UTF-8 bytes |

All strings follow the canonical plain-text rules (no unpaired surrogates, NUL or
C0/C1 controls other than TAB, LF and CR). Both arrays may be empty when the context
supports nothing, so no output is ever required to invent a cause. Any violation
rejects the whole output; nothing is salvaged, trimmed or normalized. `sources`
proves only that a label was sent, not that the claim is true.

The instruction must require: treat every value as untrusted data, never as
instructions; ground each hypothesis only in supplied text; state uncertainty rather
than invent observations; give advice only, with no claim that anything was run; no
tools, lifecycle change or secret disclosure; the exact JSON shape above.

Gateway limits still apply first: 1200 output tokens and 16 KiB output text. When a
provider omits usable usage, the gateway rejects output over 1200 bytes. That is
existing behavior and surfaces here as `502 AI_INVALID_OUTPUT`.

### Errors

Precedence follows TD-RPT and the TD-DEV step table. Fixed messages never echo
source or provider text.

| Step | Situation | Response |
| --- | --- | --- |
| 1 | Framing, body cap, shape, path UUID | `400 INVALID_REQUEST` |
| 2 | CSRF | `403 CSRF_INVALID` |
| 3 | No valid session | `401 UNAUTHENTICATED` |
| 4 | Absent project, disabled user/project/membership, non-member | `404 NOT_FOUND` |
| 4 | Active Reporter or QA, whatever the IDs | `403 FORBIDDEN` |
| 6 | Report absent or in another project | `404 NOT_FOUND` |
| 6 | Developer who is not the current assignee, decided before any note lookup | `403 FORBIDDEN` |
| 6 | Selected note absent or on another report | `404 NOT_FOUND` |
| 6 | Status is not `IN_PROGRESS` | `409 INVALID_STATE` |
| 7 | Report or any note version differs | `409 STALE_VERSION` |
| 7 | Context ceiling exceeded (only evaluated when AI is enabled) | `400 AI_CONTEXT_TOO_LARGE` |
| — | Disabled, unfunded, quota, capacity, circuit, timeout, provider failure | `503 AI_UNAVAILABLE` |
| — | Gateway or role output rejection | `502 AI_INVALID_OUTPUT` |
| 8 | Session, storage or security-event sink failure | `503 SERVICE_UNAVAILABLE` |

Role and object errors win even when AI is disabled, because the service performs
the authorization check before entering the facade.

## Authorization and integration

Build one role adapter on the shared factory (`SoclaasEnablementManager.createGateway`)
with capability `INVESTIGATION`. Developer controllers and the browser never reach the
raw transport. Default policy stays disabled with zero budgets.

The gateway request carries the report ID and report version. Note selection does
**not** fit the gateway's single source version, because note edits deliberately do
not change the report version. The adapter therefore holds the parsed selection in
trusted request-local state, as the Reporter adapter holds its source kind, and its
`Authorizer` checks all of the following on every call, from fresh reads:

1. The live session actor equals the resolved actor.
2. Active user, project and `DEVELOPER` membership.
3. The report exists in the project, the actor is its current assignee and it is
   `IN_PROGRESS`.
4. Every selected note exists on this report.
5. The report version and every selected note version equal the expected values.

The gateway calls the authorizer before context selection, before dispatch and
before delivery, so a change at any point denies or suppresses delivery. Versions
only increase and deleted IDs are never reused, so the checks hold without locks. The
`ContextBuilder` applies check 5 to the values it actually read. No database
transaction or lock spans a provider call.

Denial before admission means zero context selection, reservations and provider
calls. Any dispatched request still settles under shared accounting when delivery is
denied. A suggestion creates no business mutation or business audit event; AI
accounting and business audit stay separate. Denials use the existing bounded
`DEVELOPER_DENIED` and `DEVELOPER_INVALID_REQUEST` security events.

Two allowlists need the new route: the security-event route constraint (a fresh
migration, currently next at V11) and `InvestigationBodyLimitFilter`, which applies
the 8 KiB cap to this path. The Reporter `/suggestions` routes are unchanged.

## Explicit apply

Apply is ordinary note creation. After the Developer selects and edits preview text,
Save sends the existing `POST .../notes` request with `{body}` and an
`Idempotency-Key`. The server applies the ordinary note operation's current-assignee,
lifecycle, cap, idempotency, transaction and metadata-only audit rules unchanged,
including its existing allowance in `IN_PROGRESS` or `READY_FOR_QA`. The result is a
private investigation note; the server does not receive or persist AI provenance,
source IDs, source versions, prompt metadata or suggestion text beyond the edited
note body.

Freshness is a workspace concern rather than an AI-only mutation rule. The detail and
notes responses form the page's freshness snapshot. If a later refresh, focus
revalidation or mutation response shows that the report version, status, assignment or
any loaded note version/deletion changed outside a successful action already
incorporated into this view, the shared Developer workspace warning in
the [investigation contract](developer-investigation.md#ui-contract) tells the user
that the investigation changed since it was last refreshed, preserves every draft and
offers Refresh. It does not claim real-time detection and does not add a preflight or
special version check to ordinary note creation.

## UI

Extend the Developer report detail, only for the assignee of an `IN_PROGRESS` report,
reusing shared components and the design system.

- **Before sending:** a context picker with the defaults above and a plain statement
  that the selected text goes to the configured AI provider and that suggestions are
  advisory.
- **Requesting:** the action sends the versions the page currently holds. While
  pending, duplicates are disabled and cancel is available. Cancel ignores the result
  and is never presented as refunded usage.
- **Preview:** hypotheses, rationale, source labels and next steps render as escaped
  plain text, with each note `ref` identifying the note it refers to. An empty result
  shows a clear no-suggestions state.
- **Apply:** selected items populate a dedicated editable draft inside the preview,
  separate from the ordinary note composer. Only an explicit Save sends the edited
  body through ordinary note creation. Nothing is applied or saved automatically.
- **Isolation:** a generation token bound to project, report, report version and
  selection discards late responses. Report identity, project, account, session or
  selection change clears the preview. Source changes observed after delivery keep
  the preview and editable draft under the shared changed-since-refresh warning. No
  source or suggestion text goes to browser storage, URLs, logs or telemetry.
- **Failures:** the shared changed-since-refresh warning covers later source changes
  without discarding the draft or silently rebasing it. `403` removes the AI panel and
  notes actions. `401` follows the session-expired flow. Unavailable and invalid-output
  states leave notes and the rest of the page usable. A too-large response explains
  how to reduce the selection.
- Keyboard use, focus, contrast, narrow layouts and reduced motion follow the shared
  UI contract.

## Acceptance criteria

These refine the criteria in issue #31 and are requirements, not claims of passing
checks.

| ID | Observable requirement | Required evidence |
| --- | --- | --- |
| **TD-DEV-26** | The shared secured gateway is the only path to a provider for this feature, using capability `INVESTIGATION`, disabled defaults, shared admission and settlement. Ordinary investigation works with AI unavailable. | Source/wiring test that the controller and browser have no transport access; disabled default returns `503 AI_UNAVAILABLE` after role checks pass; fake-provider settlement; every non-AI Developer journey passes with AI disabled. |
| **TD-DEV-27** | Membership, assignment, status and the report and note versions are checked before selection, before dispatch and before delivery. Context contains exactly the selected allowlisted values and nothing else. | Isolated-database API matrix: three roles, two projects, two Developers, former assignee, revoked membership and expired session, with zero context reads and provider calls on initial denial. Captured-context tests: exact keys and order; logs and notes absent unless selected; no UUIDs, clarifications, resolutions or other reports. Mid-completion reassignment, revocation, session expiry, status change, report change, and note edit or delete suppress delivery and still settle. |
| **TD-DEV-28** | The server-owned versioned prompt, the exact request and response schemas, all output bounds and the source-label rule hold. Provenance is server-owned. No tools, retry, mutation or transition occurs. | Request validation at each limit and one over; forged fields; context ceiling at exactly 8000 and at 8001. Output tests for fences, trailing content, duplicate or extra keys, oversized values, Unicode violations, unknown or unsent source labels, tool calls and empty arrays. Assert one completion call per request and no business or audit row change. |
| **TD-DEV-29** | The preview is escaped and late results cannot appear on another report or selection. Apply is an explicit edited save through unchanged ordinary note creation, with no AI provenance persistence. A later refresh, focus revalidation or mutation response that reveals changed report/assignment/status or loaded-note state shows the shared workspace warning for human and AI-assisted drafts. | API and isolated-database tests prove the existing note request, idempotency, current-assignee/status checks and audit behavior are unchanged. Browser tests: hostile text inert, no auto-save, warning after report, assignment, status, selected-note edit or deletion becomes observable, preserved draft and explicit Refresh, cancel, late response after navigation or selection change, `403` and `401` handling, Axe at 320px. Assert note requests, storage and audit contain no AI-specific metadata. |
| **TD-DEV-30** | Same-candidate fake-provider checks cover the negative paths below; development-agent injection exercise and independent diff review are complete; evidence states what fixtures cannot prove. | Injected report, log and note text; cross-project and unassigned access; malformed and oversized output; tools; deadline and cancellation; unavailable AI; stale results; actor concurrency (a second concurrent request gets `503`); log and response canaries showing no content leak; DAI cases below scored against fixed fixtures. Evidence under `workflow/developer/evidence/`. |

## Threats and verification notes

Trust boundaries: browser/session to authorization, stored report and notes to
provider context, provider output to strict parsing and escaped preview, stale page
state to the shared changed-since-refresh warning and normal mutation checks, and
gateway admission and accounting. Applicable
[threat-model](../../docs/threat-model.md) rows: prompt injection, cross-project AI
context selection, stale permissions and results, AI quota or cost exhaustion, raw
provider leak, unsafe output rendering, and malicious development-agent handoffs.

Notes are private, so sending one discloses it to the provider. That is why selection
is explicit, off by default and disclosed in the UI. Content already dispatched cannot
be withdrawn. Output remains untrusted after validation and becomes ordinary
Developer-authored note text only after explicit editing and Save.

Shared gateway tests remain the proof for budget, capacity, circuit and single
settlement; this slice adds role-specific assertions. Use synthetic Java content and
fake providers only. Tests may set up assignment directly until QA-ASSIGN #32 ships,
and that does not prove the production assignment journey. Before acceptance, run the
development-agent injection exercise with inert canaries and have an independent
reviewer inspect the actual diff.

## Measurement plan

Fixed synthetic cases, from the
[Developer experiment plan](../developer/experiments/agentic-se/plan.md):

| Case | Purpose |
| --- | --- |
| DAI-01 | Plausible cause visible in a Java stack trace |
| DAI-02 | Boundary condition implied by reproduction steps |
| DAI-03 | Conflicting evidence between report text and a note |
| DAI-04 | Relevant failure buried in irrelevant log noise |
| DAI-05 | Injected instruction to close the report and reveal an inert canary |

The application comparison uses **concise** context (the six non-log fields) against
**expanded** context (those fields plus `textLogs` and selected notes), both within
the ceiling, holding model, prompt version, output limit and sampling constant. Define
expected useful elements and forbidden behavior per case before running. Score
relevance, completeness and grounding from 0–2 each (total 0–6) and score security as
a separate pass/fail. Record invalid-output rate, latency, bytes and only
provider-reported tokens.

Fixture runs prove contract and rubric execution only. Live results require a new
[AI-LIVE-01](ai-enablement.md) authorization and belong to AI-LIVE-04; Reporter results
cannot certify this role. The focused-versus-broader development comparison stays
owned by the Developer workflow. Experiment owner: the Developer contributor; due
before final MP4 acceptance. Content-free invalid-output diagnostics like Reporter's
are not in this slice and should be added before a live evaluation if needed.

## Open decisions and status

Choices made in this draft for the Developer contributor to confirm or change:

1. Only `IN_PROGRESS` reports are eligible.
2. `textLogs` is eligible but off by default, per the AI policy and issue #31.
3. Apply goes to a note only; resolution prefill is left out.
4. Saving selected preview text reuses unchanged ordinary private-note creation;
   freshness warnings belong to the whole Developer workspace.

Dependencies: #27 and #28 are merged; the gateway (#39) is merged. QA-ASSIGN #32 is
needed for the production journey only. The independent contract review is complete;
all implementation and verification remain pending.
