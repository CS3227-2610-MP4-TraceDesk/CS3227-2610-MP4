# Local Reporter account and project

Issue #37 provides an explicit local-only provisioning tool. It creates one
synthetic Reporter, a project named `Local Reporter Workspace`, and an active
Reporter membership using the real authentication and report tables. It is not
run during application startup and is excluded from the production backend JAR.
It does not create Developer/QA accounts or call AI.

## Provision the local development database

Use Java 25 and a POSIX filesystem (verified on macOS; Linux CI configured),
with the existing local Compose database:

```sh
docker compose up -d --wait db
export TRACEDESK_LOCAL_CONFIRM=true
export TRACEDESK_LOCAL_JDBC_URL=jdbc:postgresql://127.0.0.1:54329/tracedesk_dev
export TRACEDESK_LOCAL_DATABASE_USER=tracedesk_dev
```

Set `TRACEDESK_LOCAL_DATABASE_PASSWORD` to your local database password in that
terminal. This is the database password, not your Reporter login password. The
Compose synthetic development default is documented in [Docker setup](docker.md).
Do not paste passwords into issues, chat, commands saved in shell history, or
committed files. In zsh, enter the database password without displaying it:

```sh
read -rs 'TRACEDESK_LOCAL_DATABASE_PASSWORD?Local development database password: '
export TRACEDESK_LOCAL_DATABASE_PASSWORD
printf '\n'
./gradlew :backend:auth-app:localReporterSetup --args=provision --console=plain
```

Only explicitly selected `127.0.0.1` or `localhost` PostgreSQL endpoints with an
explicit port, database `tracedesk_dev`, and user `tracedesk_dev` are eligible.
URLs with query parameters, alternate databases/users and production/test profiles
are refused before migration. The local environment variables never fall back to
application or test credentials. Explicit confirmation means you have verified
this target contains development data; do not use tunnels to a remote database.

The command prints the project UUID and login identifier. The randomly generated
Reporter password is saved in `.local/local-reporter.json` in the repository
root, with owner-only permissions. Open that file locally to retrieve the
synthetic login/password. Do not share or commit it. The folder is ignored by
Git and excluded from Docker build contexts.

Rerun the same command to obtain the same IDs without duplicates or password
rotation. The tool verifies the ownership marker, membership, target binding
and saved credential file. It refuses conflicting or altered records rather
than adopting another account or changing its role. Keep the credential file
with the development database it belongs to; switching targets with the same
file fails closed.

## Run the real backend and frontend

In the backend terminal, map the explicitly selected local database settings:

```sh
export TRACEDESK_DATABASE_URL="$TRACEDESK_LOCAL_JDBC_URL"
export TRACEDESK_DATABASE_USER="$TRACEDESK_LOCAL_DATABASE_USER"
export TRACEDESK_DATABASE_PASSWORD="$TRACEDESK_LOCAL_DATABASE_PASSWORD"
export AI_ENABLED=false
./gradlew :backend:auth-app:bootRun
```

In a separate terminal start the frontend:

```sh
cd frontend
npm ci
npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

If Compose already runs the frontend at 5173, use that existing frontend instead.
Open `https://localhost:5173` and accept/trust the local development certificate.
The same-origin proxy forwards `/api` to the host backend at port 8080. Sign in
using the generated credentials and select `Local Reporter Workspace` in the
existing project picker, or use `/?project=<printed-project-UUID>`. You can create
reports and save/resume drafts. The normal backend session, CSRF, project and
object authorization checks still apply. AI remains disabled.

## Explicit credential reset

With the same confirmed local target:

```sh
./gradlew :backend:auth-app:localReporterSetup --args=reset --console=plain
```

Reset rotates only the owned Reporter's password and invalidates that account's
sessions. Read the new password from the protected local file. Project/account
IDs, memberships, reports, drafts and business audit history are retained;
unrelated accounts and sessions are untouched. Reset is intentionally a mutation
each time. It is not database deletion or a way to repair changed role/ownership.

If the tool reports a recovery condition after interruption, stop and inspect
the local state using the documented fixed recovery diagnostics. Do not delete
ownership records, run reset repeatedly, or weaken file permissions to bypass a
failure. Database/file commit cannot be made crash-atomic; mismatches fail closed.
Never resolve a setup problem by deleting an existing development database volume.

## Isolated verification

The implementation verification uses a separately owned disposable PostgreSQL
instance on `127.0.0.1:55419`, named `tracedesk_dev`, with an independent local
password. It never uses the normal Compose database on 54329 or `_test` fixtures.
With that database selected via the local environment settings:

```sh
./gradlew :backend:auth-app:localDevTest --no-daemon --console=plain
export TRACEDESK_LOCAL_SMOKE_CONFIRM_DISPOSABLE=true
python3 tools/local-reporter-smoke.py
```

The smoke helper requires the exact disposable target, provisions twice, builds
the current frontend, starts the normal backend with temporary localhost HTTPS,
and attaches Playwright. A test-only fixture creates an existing project without
Reporter membership to verify denied access, then removes that project. It uses ports 18089, 4177 and 4178, refuses occupied
ports, disables AI, and removes its temporary server/certificate on exit. It
leaves development account/project data and its generated credential file in
place for repeatability verification. Traces, screenshots, video and content
reports are disabled. The helper passes no provider key to child processes.

See the [owning specification](../workflow/specs/local-reporter-setup.md) and
[verification evidence](../workflow/reporter/evidence/local-reporter-setup.md)
for actual results and limits. Local setup is not deployment verification.
