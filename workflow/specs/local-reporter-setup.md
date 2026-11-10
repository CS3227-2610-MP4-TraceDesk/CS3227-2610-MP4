# DEV-SETUP — Repeatable local Reporter provisioning

Status: behavior contract for issue #37; this document is not implementation or
completion evidence. Human owner: not supplied. The authorized scope is a local,
synthetic Reporter account and its project in a disposable development database,
with real session and Reporter report/draft access. This does not authorize
production provisioning or deployment.

## Goal and boundaries

Provide one explicit, repeatable command to provision exactly one local synthetic
user, project, and active `REPORTER` membership. The command returns a usable
`projectUUID` and login identifier while placing the randomly generated password
in a local owner-only credential file. Re-running against the same intact owned
records is idempotent and creates no duplicates. An explicit reset rotates the
owned account password and invalidates that account's sessions without deleting
reports or drafts.

The provisioner is a local-development tool, isolated in a Gradle `localDev`
source set and launch task. Its classes and dependencies are excluded from the
production application and ordinary test/runtime artifacts. It is not an
application endpoint or a seed runner at service startup. It only provisions
within `tracedesk_dev` on a loopback PostgreSQL port explicitly selected by
the operator, authenticated as `tracedesk_dev`; it applies existing TraceDesk migrations before writing data.
It does not change the shared authentication contract, user/project/membership
schema, role rules, session policy, report lifecycle, or application API.

Out of scope: public registration, discovery APIs, invitations, roles other than
Reporter, production seeds or account creation, other databases, deployment, AI,
report or draft creation/deletion, and changing existing application behavior.

## Command and safety contract

Expose a standalone Gradle `:backend:auth-app:localReporterSetup` task from the
`localDev` source set with `--args='provision'` or `--args='reset'`. Require the
environment variable `TRACEDESK_LOCAL_CONFIRM=true` for either mode. Missing or
misspelled confirmation fails
before connecting. The tool rejects active Spring profiles `prod`, `production`,
or `test`; it must not inherit application
configuration or credentials from environment fallbacks, dotenv files, Spring
config imports, or the default application profile.

The only accepted JDBC target is literal host `127.0.0.1` or `localhost`, an
explicit port from 1 through 65535, database `tracedesk_dev`, username
`tracedesk_dev`, and no query parameters, aliases, or URL options. Port `55419`
is reserved for the separately launched disposable instance used in verification;
the existing Compose development database may be selected explicitly at port
`54329`. Require explicit task-only environment variables
`TRACEDESK_LOCAL_JDBC_URL`, `TRACEDESK_LOCAL_DATABASE_USER`, and
`TRACEDESK_LOCAL_DATABASE_PASSWORD`; do not read `.env*` files or fall back to production/test configuration. Before migrations
or any write, inspect the connected server and current database and verify the
exact database/user/schema identity; the parsed JDBC endpoint must be literal
loopback. Docker bridge addresses reported by PostgreSQL are permitted because
the published client endpoint is loopback. Any mismatch, unknown
or remote host, non-allowlisted port/database/user, unrecognized URL option, missing
configuration, profile conflict, or failed identity query aborts without schema
or data mutation. Error output gives a safe reason but omits credentials and
full connection URLs.

Provisioning must use one transaction and a PostgreSQL advisory lock scoped to a
stable TraceDesk local-provisioning key. The transaction records a private,
dev-only ownership marker for its provisioned account/project/membership tuple.
The marker includes a format version and immutable IDs and is not exposed through
application APIs. Under the lock, verify the marker and all referenced records
before acting. If no marker and no conflicting natural-key records exist, create
the user, project, active Reporter membership, and marker atomically. If a
marker exists and all rows match its IDs and expected local ownership attributes,
return the same IDs without creating records. If the marker is missing but
conflicting user/project/membership data exists, or any marked row is missing,
changed, disabled, or has an unexpected role/relationship, fail closed; do not
repair, adopt, delete, or create a second identity. Database uniqueness remains
the final defense against duplicate normalized login and membership records.

Use a fixed synthetic project name `Local Reporter Workspace` and a stable
synthetic login identifier `reporter.local@tracedesk.invalid`, subject to the
normal authentication normalization/validation rules. The project and user IDs
are generated UUIDs on first provisioning and retained by the ownership marker.
The command prints only the project UUID and login identifier to stdout. It does
not print passwords, password hashes, JDBC credentials/URLs, session IDs, or
other user content.

Generate a cryptographically random password meeting the current authentication
password policy (at least 12 Unicode code points; at most 128 code points and
512 UTF-8 bytes). Store it only in ignored path `.local/local-reporter.json`,
with restrictive owner-read/write permissions (POSIX `0600`); create the
containing directory as owner-only (`0700`). Create via a temporary file in the
same directory and atomic rename. Refuse symlinks, non-regular existing target,
wrong owner, or group/other permissions. If a credential file already exists during ordinary `provision`, validate its
owner-only permissions, bound database URL/user, login, project UUID, ownership
marker IDs, and password against the stored hash. If all match, keep it and
return the existing IDs; on any mismatch, fail closed without overwriting it. On any failed provisioning transaction,
remove the newly generated temporary/final credential file; if secure creation,
permission setting, or atomic rename is unavailable, roll back and fail. Ensure
`.local/` is excluded from Git and packaged artifacts. The JSON file contains
only format version, exact non-secret JDBC URL binding, database username,
synthetic login, password, and project UUID; it is never loaded as application
configuration. Read it only for the explicit real-browser verification flow,
and never print or log its contents.

`reset` is only allowed after the same database/profile/confirmation checks and
under the same advisory lock. It requires an intact ownership marker and exact
owned rows. Generate and atomically install a fresh credential file using the
same permissions/secret rules, update only the owned account's password hash,
and delete only that account's server-side sessions in the same database
transaction. Preserve the user, project, membership, reports, drafts, audit
history, and all unrelated sessions and records. If the credential file cannot
be safely replaced, abort before committing the password/session transaction;
if the database transaction fails after file staging, restore the prior file
atomically and report failure. A process or machine failure during the database
commit/file-replace window may leave a staged file; detect it on the next invocation
and fail closed with a recovery instruction rather than guessing the current password. Output only the same login identifier and
project UUID. Repeated reset intentionally rotates again; it is not idempotent.

## Session and user-facing behavior

After provisioning, the account must authenticate through the existing session
login flow. Authorization continues to derive identity from the server-side
session and role/project access from the active membership; the CLI is not a
login bypass. The Reporter can use the existing browser workspace and report
draft/create/read behavior within the provisioned project. Other projects and
other roles remain inaccessible according to existing server-side checks. Do
not add a setup-only project discovery route or trust a browser-supplied role or
project membership. Existing draft/report ownership and authorization rules
apply unchanged.

## Acceptance criteria

- **TD-LOCAL-01 — Provision:** On an explicitly selected eligible local DB, the
  confirmed command applies fresh TraceDesk migrations as needed and atomically
  creates one enabled synthetic account, one fixed-name project, one active
  Reporter membership, and one ownership marker. It returns a usable project
  UUID and login identifier.
- **TD-LOCAL-02 — Repeatability and reset:** Repeated provision returns identical
  IDs without duplicates. A matching credential file is retained after ownership
  and password-hash validation. Explicit reset rotates the owned password and
  invalidates only that account's sessions while preserving account/project/
  membership IDs, reports, drafts, and audit history. Ownership conflicts fail
  closed. Concurrency and injected database/filesystem failures leave no partial
  records and detect staged-file recovery state safely.
- **TD-LOCAL-03 — Artifact and target isolation:** The standalone `localDev`
  source set/task is excluded from production application artifacts and ordinary
  test/runtime classpaths; application startup never provisions accounts. Before
  migrations or writes, require exact host allowlist, explicit port, exact
  `tracedesk_dev` database/user, and successful connected identity check. Reject
  missing confirmation, conflicting production/test profile, missing explicit
  configuration, or any mismatch without mutation.
- **TD-LOCAL-04 — Credential protection:** Generate a password within current
  authentication bounds, store it only in ignored `.local/local-reporter.json`
  with owner-only file/directory permissions and an exact DB binding, and never
  print or log it. Verify symlink, file collision, failure cleanup, Git and
  packaged artifact exclusions using inert canaries.
- **TD-LOCAL-05 — Real Reporter browser journey:** Use the generated credentials
  through real session login and browser-to-backend access. Verify the Reporter
  enters the provisioned project and can use existing report/draft operations;
  CSRF/session protections and cross-project denial hold. Mock identities and
  demo credential shortcuts do not count.
- **TD-LOCAL-06 — Evidence and negative coverage:** Verify wrong/missing target
  configuration, role boundaries, artifact exclusion, persistence, credential
  handling, reset preservation, concurrency and browser journey. Record exact
  revision/working-tree identity, commands and DB isolation identity without
  secrets. Distinguish pass, fail, skip and unavailable; claim no production
  provisioning or deployment acceptance.

## Verification boundary and references

Use a separately launched disposable development PostgreSQL instance at loopback
port `55419`, database `tracedesk_dev`, with the expected `tracedesk_dev` account,
for verification. Never run verification against the user's existing `54329`
service. The CLI may target that service only when explicitly selected through
the required confirmation and exact URL. Browser verification uses the separately
owned disposable instance and must stop before altering or deleting unrelated data. Do not use
the isolated test database as a target for this command. Tests for rejection
paths should use a fake connection identity or controlled isolated fixture, not
production or user data. Never include generated credentials in screenshots,
logs, evidence, or agent handoffs.

Relevant contracts: [product scope](../../docs/product.md), [security and
deployment](../../docs/security.md), [ADR 002 shared role
contracts](../decisions/002-shared-role-contracts.md), [authentication and
membership](auth-membership.md), [Docker local environment](docker-local.md),
and [Reporter workflow](../reporter/README.md). Applicable security assertions
include safe database selection before migration, no credential leakage, no
untrusted source of actor identity, membership enforcement, and no hidden
provisioning path. No AI context, provider call, role expansion, or business
report mutation is introduced by this feature.

## Interfaces to implement

- Gradle `localDev` source set and standalone `:backend:auth-app:localReporterSetup` task with
  `provision` / `reset` modes and required `TRACEDESK_LOCAL_CONFIRM=true`.
- Local-only environment variables `TRACEDESK_LOCAL_JDBC_URL`,
  `TRACEDESK_LOCAL_DATABASE_USER`, `TRACEDESK_LOCAL_DATABASE_PASSWORD` and exact
  target identity verification; no generic Spring config fallback.
- `LocalReporterSetupCommand` CLI and `LocalReporterProvisioner` service owning
  lock, marker inspection, idempotence, transaction,
  and reset boundary; repository access must use existing auth/project/membership
  persistence rather than duplicating schema semantics.
- `LocalReporterCredentialFile` writer responsible for random password generation, secure
  create/replace, atomic staging, rollback, and safe cleanup.
- Focused integration/security tests and a real backend/browser verification
  record mapped to TD-LOCAL-01 through TD-LOCAL-06.

Open material decisions: none within the approved local-only boundary. The local
DB is an explicitly separate disposable instance; starting or altering any
existing default local DB remains outside this contract.
