# DEV-AI #31 contract update and independent review

- Task / feature ID: DEV-AI #31, TD-DEV-26 through TD-DEV-30.
- From / to: Codex primary agent (`/root`) to independent read-only reviewer
  (`/root/dev_ai_spec_review`, `gpt-6-luna`), at 2026-11-09T15:07:46Z.
- Coordination: sequential. The primary agent updated the specification; the reviewer
  inspected the actual working tree and did not edit files.
- Scope: remove the proposed Developer-specific source-metadata field, reuse unchanged
  ordinary private-note creation for explicit Save, and make changed-since-refresh
  warning behavior common to human and AI-assisted Developer drafts.
- Exclusions: implementation, tests, runtime acceptance, deployment and changes to the
  already-implemented Reporter AI flow.
- Criteria and context: root `AGENTS.md`, architecture, AI/security policy, ADR 002,
  secured gateway contract, Developer investigation contract, issue #31 criteria and
  the untracked `workflow/specs/developer-ai.md`.
- Base revision: `cbdf64a0c3ccc8068a344d9e622523528c17e60a` plus the uncommitted working tree.
- Commands already run by the primary agent: a targeted search for the removed field,
  `git diff --check`, `git diff --stat`, `git status --short` and targeted file reads.
  The reviewer independently ran `git status --short`, `git diff --stat`, `git diff`,
  `git rev-parse HEAD` and targeted searches/file reads.
- Resource telemetry: exact reviewer calls, elapsed time, tokens and cost were not
  exposed by the host and remain unknown.

## Review result and correction loop

The reviewer found one P2: the draft described AI-assisted note Save as
`IN_PROGRESS`-only even though unchanged ordinary note creation also permits
`READY_FOR_QA`. The contract now delegates to the ordinary lifecycle rules and names
both allowed states. The same reviewer re-read the correction, marked the P2 resolved
and reported no remaining findings.

The reviewer confirmed that Developer specifications contain no AI-specific source
metadata field, the shared freshness warning covers human and AI-assisted drafts, and
the shared policy consistently uses the destination mutation's normal authorization,
lifecycle and concurrency rules. No tests were run because this handoff reviewed a
draft contract; implementation and verification remain pending. No runtime or human
approval is claimed.
