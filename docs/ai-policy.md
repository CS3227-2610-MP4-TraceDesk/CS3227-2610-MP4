# AI integration policy

## Authorized context

| Capability | Candidate allowlist (finalized in slice specification) | Excluded |
| --- | --- | --- |
| Report improvement | Current user's draft title, description, environment, reproduction steps, expected/actual behavior | Other users' reports, account data |
| Investigation | Authorized assigned bug, supplied snippets/logs, relevant reproduction steps | Other projects, credentials, unrelated history |
| QA planning | Authorized bug, proposed resolution, selected verification evidence | Unrelated bugs, account/session data |

Select fields after authorization. Treat all supplied text, including code comments
and logs, as untrusted data. Version server-owned prompts; separate instructions
from JSON data. Redaction heuristics do not prove arbitrary free text contains no
secrets: bound and minimize fields, explain provider disclosure in the UI, and do
not automatically ingest files or entire repositories.

## Invocation invariants

- One non-streaming completion per user action; no tool/function calls.
- No automatic retry of completion POSTs. A failed catalog GET may retry once
  within its original deadline.
- Production endpoint is server-owned and allowlisted. No user-controlled URLs,
  redirects or provider keys in frontend code.
- Reserve shared capacity/cost before dispatch, settle once, retain conservative
  charges when usage is uncertain. Disabled/unavailable AI must not break ordinary
  bug management.
- Validate output shape/size; render escaped text. Require explicit apply and a
  source-version check. AI cannot submit, approve, close, reopen or execute code.
- Reject unauthorized delivery after membership/session/assignment changes.
- Record only bounded metadata: capability, model, prompt version, outcome,
  latency, size and provider-reported token counts; missing usage is unknown.

The extracted transport retains MP3's conservative safety ceilings (30s request,
3s connect, 64KiB response, 16KiB input/output, 8000 input and 1200 output token
ceilings). These are transport constraints, not a final quota policy. Its input
token estimate conservatively counts UTF-8 bytes plus framing; this is not a
tokenizer. Gateway worker/concurrency/reservation limits must be specified together
before enabling an application AI endpoint.

## Required negative-path coverage

Unauthorized/cross-project reads; injected instructions in text; malformed and
oversized responses; tool-call rejection; safe error/log content; unavailable AI;
deadline, interruption and cancellation; concurrent admission/settlement; stale
metadata and circuit fencing; stale UI/session results and explicit apply.

## Evaluation

Use synthetic cases per role with expected useful content and forbidden behavior.
Compare concise versus expanded context on the same cases and prompt/model version.
Record quality rubric, latency, tokens and failure rates. Report reasoning/cache
tokens only when actually supplied by the provider; do not fabricate internal
reasoning or token savings. See the reflection template for development evidence.
