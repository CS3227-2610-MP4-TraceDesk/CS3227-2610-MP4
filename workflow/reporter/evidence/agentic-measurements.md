# Measurement evidence kit verification — 2026-11-06

Scope TD-MET-01–09. Base revision 95ddf630f96852c684ab2a5a38c6e870a821095b
plus uncommitted changes identified by [SHA-256 manifest](agentic-measurements.sha256).
Author/implementer: `/root`. Independent reviewer: `/root/measurement_review`.
Actual coordination: [run record](../runs/2026-11-06-measurement-kit.md).
Environment: local macOS, Python standard library, no added dependencies/provider calls.

## Commands and observed results

| Command | Result |
| --- | --- |
| `python3 -m unittest discover -s workflow/reporter/tools/tests -p 'test_agentic_measurements.py' -v` | Initial 7 methods passed; final 8 passed, 0 failed/skipped. Reviewer independently reran final suite. |
| `python3 workflow/reporter/tools/agentic_measurements.py workflow/reporter/experiments/agentic-se/measurements.jsonl --output workflow/reporter/experiments/agentic-se/results.html` | Passed: one observed development interval; report generated. |
| `python3 tools/check_foundation.py` | Passed after final documentation integration. |
| `git diff --check` | Passed; tracked changes checked. |
| `LC_ALL=C shasum -a 256 -c workflow/reporter/evidence/agentic-measurements.sha256` | Passed for evidence inputs/artifacts. |

## Criteria and review

TD-MET-01/02/03/06/07: tests exercise strict records, duplicates, bounds,
invalid dates/encoding/numbers, unknown versus zero, fixture separation, safe
errors/HTML and preserving output after rejected input. Digest tracks exact bytes.
TD-MET-04/05: report tests exercise partial coverage, separated models, timeline,
empty state and lack of comparative claims. The observed dataset has no application
points; real token-quality plots require application observations. Numeric values
are retained in JSONL and displayed in the observation register.
TD-MET-08: application and development experiment plans exist; neither comparison
has run. Model settings, synthetic fixtures and evaluator sheets must be pinned
before future collection; case outlines alone are not execution-ready fixtures.
TD-MET-09: actual specifier/implementer/reviewer handoffs and correction recorded.

One P2 finding: file metadata size check followed by an unbounded read could
exceed the declared memory bound. Fixed by checking the opened regular file and
capping the read at 10 MiB + 1 byte. Added regression for understated metadata;
reviewer verified resolved. The spec was finalized during initial tooling work;
this sequencing limitation is recorded rather than retroactively claiming otherwise.

## Remaining evidence and limitations

No live application telemetry, provider token counts, quality scores, cost data,
controlled efficiency comparison, runtime instrumentation, deployment or full
agent-injection exercise. The single observed coordinator window is partial, not
full task duration or proof of resource savings. Tokens/calls/retries are unknown.
No browser visual inspection was performed; static report structure/content are
tested. Existing AGENTS/workflow edits from the prior task were preserved.
This is tooling/security-test and actual coordination evidence only. Application
security, production separation and milestone experiments remain pending.

The initial checksum command failed because the host Perl locale was unavailable.
Rerunning with LC_ALL=C passed; this was an environment failure, not a hash mismatch.

## Contributor-directory relocation verification — 2026-11-06

Moved personal artifacts and tools under workflow/reporter at the user's request.
Updated links, commands and dataset references; preserved numeric observations.
Regenerated the report and current checksum manifest. The original checksum
manifest is retained as agentic-measurements-before-move.sha256 (historical only).

Relocated unittest command: 8 passed, 0 failed/skipped. Relocated generator:
1 observed record validated. Foundation link check: 42 documents passed.
`git diff --check` passed. This mechanical move used one agent; no new independent
review or experiment is claimed. Shared project/security requirements remain in
place while the personal process is scoped to this contributor.
