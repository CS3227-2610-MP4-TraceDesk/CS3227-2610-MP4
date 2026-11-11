# Local Reporter account and project

Issue #37 provides an explicit local-only provisioning tool. It creates one
synthetic Reporter, a project named `Local Reporter Workspace`, and an active
Reporter membership using the real authentication and report tables. It is not
run during application startup and is excluded from the production backend JAR.
The original command creates only the Reporter; the optional sample-account
command below adds Developer and QA accounts. Neither command calls AI.

## Optional: provision all three sample roles

For a fresh setup, follow the database configuration below, then replace the
Reporter-only provision command with:

```sh
./gradlew :backend:auth-app:localSampleAccountsSetup --args=provision --console=plain
```

For an existing local Reporter setup, run the same command with the same
`TRACEDESK_LOCAL_*` settings and intact `.local/local-reporter.json`. It validates
and reuses that Reporter, then adds Developer and QA to the same
`Local Reporter Workspace` project. Repeating an already-matching setup reuses all three accounts without changing
passwords or creating duplicates. Previously owned Developer/QA sample accounts
from the earlier local setup are updated to the exact demo credentials below,
preserving IDs/project membership and invalidating only their old sessions.

| Account role | Login | Password / credential record |
| --- | --- | --- |
| Reporter | `reporter.local@tracedesk.invalid` | `.local/local-reporter.json` |
| Developer | `developer@tracedesk.test` | `tracedesk-demo`; `.local/local-developer.json` |
| QA reviewer | `qa@tracedesk.test` | `tracedesk-demo`; `.local/local-qa.json` |

Developer and QA use the exact fixed password `tracedesk-demo`, matching the
existing demo sign-in buttons; no other staff demo password is generated. The
Reporter keeps its original generated credentials in its file. Keep credential
records private and out of source control. These are normal accounts with server-assigned roles
and active membership, not browser demo identities. Setup does not create reports,
assign bugs or bypass existing role/assignment/lifecycle checks. Use the same
frontend and backend startup steps below, and sign out before switching accounts.

Accounts are provisioned sequentially, each in its own atomic database operation.
If a later account fails, earlier accounts remain provisioned; correct the stated
conflict and rerun. The command never adopts another account, repairs changed
roles, or silently resets an existing password.

To explicitly reset one sample account and invalidate only its sessions, select
its role:

```sh
./gradlew :backend:auth-app:localSampleAccountsSetup --args='reset developer' --console=plain
```

The other supported selectors are `reset reporter` and `reset qa`. Developer/QA
reset restores the same `tracedesk-demo` password; Reporter reset retains its
existing random-password behavior. Existing Reporter-only
`localReporterSetup --args=provision` and `--args=reset` remain supported.

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
export TRACEDESK_PROJECT_CURSOR_SECRET="$(openssl rand -hex 32)"
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
