# Developer workflow setup — 2026-11-07

## Goal and scope

Establish a contributor-specific workflow area for the student owning TraceDesk's
Developer application role, based on the reusable structure of `workflow/reporter/`.
Preserve Reporter history instead of copying it as Developer evidence. Add guidance
for collecting presentation, reflection and assignment-required interaction summaries.

## Codex use

One coordinating Codex agent inspected the assignment brief, root project guidance,
Reporter workflow, templates, experiment plan and harness. No specialized subagents
were used because this was a bounded documentation and directory-setup task with no
application behavior, trust boundary or production code change.

## Changes

- Added `workflow/developer/` with completion gates, notes, templates, experiment
  planning, presentation planning, and empty evidence/run/spec/tool locations.
- Updated shared project guidance and the workflow index to recognize both Reporter
  and Developer contributor workflows without globally routing the team to either.
- Clarified that existing Reporter artifacts are retained historical records.
- Added `logs/` and `logs/developer/` instructions for sanitized interaction summaries.
- Recorded missing team deliverables—User Guide, Developer Guide and Reflections—in
  the Developer workflow checklist without claiming they are complete.

## Verification and limitations

- `git diff --check`: passed.
- Developer Markdown relative-link check: passed.
- In the sandboxed Codex command session, the first `python`, `python3` and `py`
  lookups resolved to Windows Store alias paths that this sandbox could not launch.
  This was specific to the Codex execution environment and does not imply that the
  contributor's normal PowerShell Python installation is unavailable. The bundled
  workspace Python runtime was used successfully for the checks below.
- Empty Developer measurement dataset rendering: passed, with 0 observations and no
  fabricated results.
- Shared measurement-tool unit suite: 8 passed after directing temporary files to a
  verified workspace directory; the initial run failed only because the default
  sandbox temporary directory was unwritable.
- `tools/check_foundation.py`: passed in explicit UTF-8 mode; the initial run used the
  Windows default encoding and could not decode a repository document.
- No application tests were necessary because no runtime behavior changed.
- Calls, token usage and cost are unknown because the host did not expose them here.

## Human verification requested

The Developer contributor should verify that the ownership wording and suggested
presentation narrative match the team's actual allocation before committing. Future
summaries must record real outcomes and must not infer multi-agent execution.

## Next action

Create and independently review the canonical
`workflow/specs/developer-investigation.md` contract, then index its stable criteria
before implementing the Developer backend or UI.
