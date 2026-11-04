# Foundation verification — 2026-11-04

Scope: TD-FND. Base revision: `1542274eaef6572a40e80487548682109c719e55` plus
the uncommitted foundation changes. No commit or hosted CI run is claimed.

Author: the implementing coding agent. Review: self-review only; no independent
agent/human review or multi-agent run is claimed. No live provider, database,
production configuration or deployment was used.

## Environment and results

macOS ARM64, JDK 25, Gradle wrapper 9.6.1, Jackson 3.1.5, JUnit 6.0.3.
Python 3 for repository checks; official skill validation used a temporary Python
environment with PyYAML 6.0.3. No Python dependency was added to the application.

| Check | Result | Criteria |
| --- | --- | --- |
| `python3 tools/check_foundation.py` | Passed: 23 Markdown documents, 3 skills, 11 source records, 9 Java files | TD-FND-01/02/05/06 |
| `./gradlew :backend:ai-transport:test --no-daemon` with JDK 25 | 22 tests passed, 0 failed, 0 errors, 0 skipped | TD-FND-02/03/04 |
| Official skill-creator `quick_validate.py` validation for each local skill | All 3 passed | TD-FND-01 |
| CI YAML parse and trigger/permission/job inspection | Passed; hosted workflow not run | TD-FND-06 |
| SHA-256 comparison with pinned MP3 commit using `git show` | All 11 imported source files matched | TD-FND-05 |
| `git diff --check` plus untracked-text whitespace inspection | Passed | Foundation hygiene |

Test reports: `backend/ai-transport/build/test-results/test/` and
`backend/ai-transport/build/reports/tests/test/index.html` (generated and ignored).

- `SoclaasClientTest`: 14 tests covering non-streaming messages, model access,
  provider errors, no completion retries, bounded catalog retry, usage validation,
  hostile/malformed output, safe logs, oversized/stalled bodies and interruption.
- `SoclaasSettingsTest`: 6 tests covering disabled defaults, credentials, exact
  endpoint, bounded limits, secret-safe diagnostics and immutable snapshots.
- `TransportBoundaryTest`: 2 tests covering package-private raw client and the
  reusable fake provider driving a real synthetic completion.

## Reproduction

Select JDK 25 via `JAVA_HOME`, then run the README verification commands.
For official skill validation, locate the installed skill-creator validator and use
a Python environment containing PyYAML; run its `quick_validate.py` on each folder
under `.agents/skills/`. The repository check itself requires only the standard library.

[Working-tree checksums](foundation-files.sha256) identify the authored files at
verification time, excluding this evidence directory and ignored/generated output.
Verify them from the repository root with `shasum -a 256 -c
workflow/evidence/foundation-files.sha256`.

## Initial failures and resolution

- The default shell selected Java 17, while the module requires 25. Tests explicitly
  selected the installed JDK 25.
- Sandbox restrictions prevented Gradle native/cache access; the test command ran
  with granted access and loopback HTTP permission.
- The first offline attempt compiled production sources but could not resolve the
  missing JSpecify test dependency. An authorized online retry succeeded.
- The available Python interpreters lacked PyYAML. The first dependency download
  was network-blocked; an authorized install into a temporary environment succeeded,
  followed by all three official skill validations.

## Limits

The CI file is configured and locally inspected, not executed on GitHub. No public
AI facade, identity, business authorization, persistence, ledger, circuit recovery,
UI or deployment is implemented by this foundation. Those remain in the
[integration plan](../../docs/reuse-plan.md). Provider settings retain the audited
endpoint; no current live service compatibility or funding behavior was verified.
