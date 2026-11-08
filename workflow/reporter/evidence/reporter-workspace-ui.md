# REPORT-UI issue #14 evidence

Status: planned/in progress. Base `9c9f448`; branch
`codex/reporter-workspace-ui`. First coordinator observation 2026-11-08
05:29:16 UTC. Human student ownership not supplied.

## Plan and ownership

TD-RPT-11–15 use the bounded Reporter workspace specification and existing
report APIs. Specifier owns the new UI contract; frontend implementer owns
production UI/client/unit tests; integration-test implementer owns the real
browser/isolated-backend fixture and extensions to existing Playwright/Gradle/CI.
Coordinator integrates docs and runs verification; a separate reviewer inspects
actual code, tests and results. All specialized agents use GPT-6 Luna.
Budgets: specifier 25 calls/20 minutes, frontend 70 calls/50 minutes, real-browser
tests 65 calls/45 minutes, reviewer 30 calls/30 minutes. Stop and reassess after
three similar failures; unknown telemetry remains unknown.

Scope: real authenticated Reports list/detail/composer and server-derived project
context. No project discovery API exists, so a supplied project UUID/link is an
untrusted target authorized by the server. Exclude persisted drafts, editing,
environment CRUD, role portals, AI and deployment. Shared shell/numeric design
contracts govern layout; QA fixtures never enter the authenticated Reporter app.
Reporter captures establish a new synthetic baseline, not copied QA pixels.

TD-RPT-11 maps to real context/visibility navigation, 12 to canonical validation/
plain text/input retention, 13 to actual loading/empty/denial/session/outage
states, 14 to immutable retry key/body and confirmed backend success, 15 to
real browser→same-origin API→isolated PostgreSQL plus keyboard/axe/contrast/
responsive/reduced-motion evidence. Mocks supplement and do not replace the
real journey. Pin source hashes before final checks; record failures/skips,
actual review/corrections and screenshot environment/geometry.

Pre-existing changes are preserved: `backend/auth-app/build.gradle` already
adds the Flyway starter, and `local-flyway-autoconfiguration.md` is untracked.
They are not authored by this task. The new fixture/task must not hide these
changes or claim an unmodified checkout when testing this working tree.
The user requested multiple logical commits for changes over 1,000 lines;
coordinator will separate contract, implementation and verification commits,
excluding the pre-existing local changes. No push is authorized in this task.

Measurement case RPT-UI-01 is one bounded run, not a controlled comparison.
Record role UTC starts/ends, selected context bytes, actual calls/retries and
unknown tokens/cost. Coordinator owns the development-context comparison due
before final MP4 acceptance; three-role application comparison remains due
after secured AI exists. No comparative savings claim follows from this run.
