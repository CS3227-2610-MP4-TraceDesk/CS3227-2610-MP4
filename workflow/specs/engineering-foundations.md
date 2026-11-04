# TD-ENG — Frontend and test foundations

Scope: adapt useful engineering setup from MP3 for TraceDesk. The user requested
this adoption after a read-only comparison. This specification covers tooling and
synthetic fixtures, not product behavior, authentication or deployment.

## Acceptance criteria

- [x] TD-ENG-01: A TraceDesk frontend has reproducible locked dependencies and
  runs type checking, linting, unit tests and a production build without backend
  services or private configuration.
- [x] TD-ENG-02: A browser smoke suite starts its own static preview on a dedicated
  port, checks the starter page and accessibility, and produces failure-only
  diagnostic artifacts. It cannot silently attach to another application server.
- [x] TD-ENG-03: A PostgreSQL test helper rejects missing isolation confirmation,
  production database targets and non-test database names before mutation; an
  integration test proves owned schema selection and cleanup against ephemeral CI
  PostgreSQL. Missing configuration fails a required job rather than skipping.
- [x] TD-ENG-04: A TraceDesk-specific threat model maps role, project, AI and
  development-process threats to controls, planned evidence and residual risk.
- [x] TD-ENG-05: CI runs frontend and database foundation checks independently;
  a failing or skipped required suite cannot be mistaken for passing coverage.
- [x] TD-ENG-06: Reuse provenance and remaining integration limitations are
  documented; no MP3 user data, migrations, approvals or production config copied.

## Evidence

Record exact local check outcomes in [engineering evidence](../evidence/engineering-foundations.md).
Full backend security and deployment remain future feature work.
