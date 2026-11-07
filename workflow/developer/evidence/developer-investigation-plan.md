# DEV-PLAN issue #26 evidence

Status: TD-DEV-01–05 contract drafted, independently reviewed once, and corrected.
No Developer feature is implemented and no runtime acceptance is claimed.
Human student ownership has not been supplied. No human approval of this contract
is recorded here.

- Feature and criteria: [Developer investigation contract](../../specs/developer-investigation.md), TD-DEV-01–05.
- Drafted and reviewed on base `c116af7` (branch `codex/developer-workflow-scaffolding`,
  uncommitted). Committed on branch `codex/developer-investigation-contract` from
  `origin/main` `03d9e99`, which already contains `c116af7`.
- Reviewed contract identity: blob `28ce864` (pre-correction draft).
  Corrected contract: blob `7628b42`, 27,986 bytes, at 2026-11-07T18:26:50Z.
- At 2026-11-07T19:48:44Z the coordinator rechecked the contract against the newer
  `03d9e99` changes to `docs/ui-design-system.md` (shared motion) and
  `workflow/specs/shared-ui.md` (QA preview, Change map) and added two UI bullets:
  shared `Disclosure`/`AnimatedPopover` motion, and the Change map no-patch
  fallback for Developer handoffs. Committed contract: blob `2fb5603`,
  28,491 bytes. These two bullets were not independently reviewed.
- Environment: Claude Code desktop on Windows 11; Git Bash; `gh` CLI.

## Coordination actually performed

| Role | Agent | UTC interval | Owned files / output |
| --- | --- | --- | --- |
| Coordinator and specifier | Claude Opus 5.5 (main session) | 2026-11-07T18:22:28Z – 18:24:00Z (draft), 18:25:30Z – 18:26:50Z (corrections) | `workflow/specs/developer-investigation.md`, index row, this evidence |
| Independent contract reviewer | Claude Opus 5.5 subagent, fresh context, read-only, `tracedesk-review` skill | 2026-11-07T18:24:09Z – 18:25:23Z (self-reported) | Findings report only; edited no files |

The Developer workflow prefers `gpt-6-luna` for specialized agents. It was not
available in this host, so both roles ran on Claude Opus 5.5. Reviewer
independence is limited: it was a separate context with no access to the
specifier's reasoning, but the same model family. Subagent resource use reported by
the host: 9 tool calls, about 101 s, about 100.8k tokens. Coordinator tokens and
cost are unknown.

Context supplied to the reviewer: the contract, the index diff, issue #26's
criteria as text, and paths to TD-RPT, TD-AUTH, ADR 002, product scope, threat
model, AI policy and the V4 report migration. It did not read the issue on GitHub.

## Review findings and corrections

| # | Severity | Finding | Correction |
| --- | --- | --- | --- |
| 1 | Major | `FOR SHARE` on note create let concurrent creates exceed the 200-note cap | Note create locks the report `FOR NO KEY UPDATE`; synchronized 199→2-create test added to TD-DEV-02 |
| 2 | Major | Lock modes/order unspecified; deadlock risk with QA-ASSIGN; note-read locking conflicted with TD-AUTH's no-lock read rule | Global order user/project/membership → report → note, also binding TD-ASG and QA; membership `FOR SHARE`; reads take no locks per TD-AUTH; deadlock/serialization → one retry then `503` |
| 3 | Major | 403 vs 404 let non-assignees probe note IDs | Assignee check runs before note lookup; test with real and random IDs |
| 4 | Major | Error table mislabelled TD-RPT precedence steps; replay vs assignment recheck unclear | Table split by step 4–8; role 403 independent of report ID; replay ordering and post-replay re-authorization specified |
| 5 | Major | Index cited a nonexistent evidence file; TD-DEV-06–30 implied defined | This file created; index says TD-DEV-06–30 are planned in issues |
| 6 | Minor | Duplicate start/propose outcome given as "or" | Deterministic outcomes per case |
| 7 | Minor | 64 KiB cap appeared to override TD-RPT clarification limit | Cap limited to routes this contract introduces |
| 8 | Minor | V4 audit table cannot hold the new events; note project consistency | Audit extension columns and per-event checks specified; composite `(report_id, project_id)` FK |
| 9 | Minor | Issue's "investigation activity" undefined | Defined as clarification + resolution history, never from audit |
| 10 | Minor | Former assignee's notes undeletable and count toward cap | First documented as an accepted limitation; superseded on 2026-11-08 by the Developer contributor's decision that the current assignee has full note permissions |

The corrected contract was not sent back to the reviewer for a second pass.

## Checks

| Check | Command | Result |
| --- | --- | --- |
| Local Markdown links in the contract | `grep -oE '\]\(([^)#]+)'` over the file, `test -e` each path from `workflow/specs/` | Pass (re-run after corrections, see below) |
| Whitespace | `git diff --check` | Pass |
| Runtime tests | — | Not applicable: docs-only contract; no implementation exists |

## Criteria status

| Criterion | Status |
| --- | --- |
| TD-DEV-01–04 | Contract text present and corrected after one independent review; runtime evidence belongs to #27–31 |
| TD-DEV-05 | Mapping to future tests present; this file records the actual review. A second review of the corrected text is still outstanding |

## Developer contributor decisions (2026-11-08)

The Developer contributor reviewed the contract's design choices in this session
and decided:

- #29 (Developer side) lands the shared clarification migration, API and question
  UI. The QA contributor writes the QA verification spec and owns #32.
- Kept as drafted: predecessor notes visible to the new assignee, no QA access to
  notes, staff-only resolutions, the four resolution fields, no report-version or
  `updated_at` change on note mutation, no re-proposal while `READY_FOR_QA`.
- Staff-only resolutions depend on Reporters being external users (current
  leaning); revisit if they are internal employees.
- Changed: the current assignee can add, edit and delete any note on the report.
  Notes keep their creator and record a last editor ("Created by X · edited by Y").

These coordinator edits followed the review and have not been independently
reviewed.
