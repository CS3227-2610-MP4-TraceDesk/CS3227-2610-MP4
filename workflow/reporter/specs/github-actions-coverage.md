# GitHub Actions coverage specification

Status: bounded CI change specification; not implementation or completion
evidence. This specification covers the next safe addition to the existing
GitHub Actions workflow. It does not authorize release, hosting, or deployment
configuration.

## Goal and context

Run the existing authentication application's tests in CI so that changes to its
current policy unit tests are checked on pushes and pull requests. The repository
is a React + Spring + PostgreSQL web application. Its present CI already checks
the foundation transport, frontend typecheck/lint/unit tests/build, a frontend
browser and accessibility smoke test, isolated database test support, and a
frontend container image smoke test.

The authentication module is `backend/auth-app`; its current test is
`LoginPolicyTest`, a policy unit test. This job adds coverage for that existing
test suite. It must not imply that authentication HTTP, persistence, session,
membership, or end-to-end behavior has been implemented or verified.

## Scope and exclusions

In scope: add an auth-app test job to the existing CI workflow, using the
repository's Java 25 toolchain and Gradle wrapper, on the same push and
pull-request events and read-only repository permissions as the current CI.
The job runs `./gradlew :backend:auth-app:test --no-daemon` and preserves the
module's JUnit XML and HTML test reports as revision-linked CI evidence, even
when tests fail.

The older MP3 workflow categories are comparison context only: app build/test,
admin browser, attachment cross-platform, and deploy on main/tag release. TraceDesk
has no admin browser surface or desktop attachment application. Its existing
React browser smoke test is the applicable browser check; a desktop OS matrix
for attachment behavior is inapplicable to this web application. Do not add
those jobs by copying historical workflow assumptions.

Release and deployment are deferred. No hosting provider, deployment target,
environment configuration, or release provider is approved. The security policy
places deployment after a working vertical slice, with separate environment
databases, credentials and configuration, pinned candidate revisions, and no
production secrets exposed to untrusted pull-request jobs. This CI coverage
specification does not authorize any deployment or release trigger, credential,
environment, tag/promotion rule, or production action.

## Observable acceptance criteria

| ID | Criterion | Evidence |
| --- | --- | --- |
| TD-CI-01 | The workflow runs the `backend:auth-app:test` Gradle task on pushes and pull requests, using Java 25 and the checked-in Gradle wrapper. | A workflow run for the candidate revision shows the auth-app test command and outcome. |
| TD-CI-02 | The auth test job uses no production credentials or environment files and requires no external service for the current policy unit tests. | Workflow review confirms only repository source and synthetic/local test configuration are used; no `.env*` file is read. |
| TD-CI-03 | JUnit XML and HTML reports are uploaded with the candidate revision in the artifact name, including after test failure. | A run demonstrates report artifact availability for success and/or failure; unavailable reports are identified without claiming a pass. |
| TD-CI-04 | The job is additive to existing foundation, frontend, isolated-database, and container-build checks and preserves their triggers and behavior. | Diff and workflow review establish existing jobs and triggers remain intact. |
| TD-CI-05 | CI scope remains limited to existing auth-app tests; its presence is not represented as end-to-end authentication or release evidence. | Workflow and evidence wording identify the current suite as policy unit tests and make no broader claim. |

## Verification and status

Verify workflow syntax and inspect the changed workflow diff. Run the exact
Gradle task locally when the available Java/dependency environment permits, and
record pass, failure, skip, or unavailable distinctly. A local run is not a
substitute for a GitHub Actions run; report that run as pending or unavailable
until observed. Record the tested revision or working-tree identity and exact
commands in the Reporter evidence area. No criteria are marked complete by this
specification. Initial implementation evidence is recorded in
[github-actions-coverage.md](../evidence/github-actions-coverage.md), and actual
coordination is recorded in
[the run record](../runs/2026-11-06-github-actions-coverage.md).

## References

- [Current CI workflow](../../../.github/workflows/ci.yml)
- [Authentication and membership specification](../../specs/auth-membership.md)
- [Security and deployment boundaries](../../../docs/security.md)
- [Product scope](../../../docs/product.md)
