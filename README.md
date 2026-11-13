# TraceDesk

- Live application: <https://tracedesk-run-660566661066.asia-southeast1.run.app>
- Project website: <https://cs3227-2610-mp4-tracedesk.github.io/CS3227-2610-MP4/>

TraceDesk is a local web app for reporting software bugs, investigating them,
and reviewing proposed fixes. This guide starts the current application on your
computer with Docker, PostgreSQL, Spring Boot, and the React frontend. It is for
local development, not production deployment.

For role workflows, see the [User Guide](docs/UserGuide.md). For architecture,
security, testing and the development process, see the [Developer Guide](docs/DeveloperGuide.md)
and [Reflections](docs/Reflections.md). The [documentation website source](docs/index.md)
is included; publication is a separate step.

## Requirements

- Docker Desktop with Docker Compose
- JDK 25
- Node.js 24.15 or newer (Node 24 line) and npm, if running the frontend outside Docker
- macOS or Linux shell for the sample-account setup command

## Start TraceDesk locally

Run the following steps from the repository root unless a step says otherwise.

### 1. Start PostgreSQL and the development frontend

```sh
docker compose --profile dev up --build -d --wait db frontend-dev
docker compose ps
```

This starts PostgreSQL on `127.0.0.1:54329` and the HTTPS frontend on
`https://localhost:5173`. The database persists in a Docker volume. The frontend
uses a local self-signed certificate, so your browser may show a certificate
warning the first time.

The default database is synthetic local development data:

- Database: `tracedesk_dev`
- Username: `tracedesk_dev`
- Password: `tracedesk_dev_only`

If you have configured another local database password or port, use those same
values in the setup and backend commands below. Local Compose overrides can be
placed in `.env` using [.env.example](.env.example); keep `.env` private and out
of source control.

### 2. Create the sample accounts

In a terminal at the repository root, run:

```sh
export TRACEDESK_LOCAL_CONFIRM=true
export TRACEDESK_LOCAL_JDBC_URL=jdbc:postgresql://127.0.0.1:54329/tracedesk_dev
export TRACEDESK_LOCAL_DATABASE_USER=tracedesk_dev
read -rs 'TRACEDESK_LOCAL_DATABASE_PASSWORD?Local database password: '
export TRACEDESK_LOCAL_DATABASE_PASSWORD
printf '\n'
./gradlew :backend:auth-app:localSampleAccountsSetup --args=provision --console=plain
```

For the default Compose database password, enter `tracedesk_dev_only` at the
prompt. This provisions real local accounts and memberships in
`Local Reporter Workspace`; it does not create bugs or bypass normal sign-in.
You can run this command again to reuse the same accounts.

| Role | Email | Password |
| --- | --- | --- |
| Developer | `developer@tracedesk.test` | `tracedesk-demo` |
| QA reviewer | `qa@tracedesk.test` | `tracedesk-demo` |
| Reporter | `reporter.local@tracedesk.invalid` | Generated; stored in `.local/local-reporter.json` |

The Reporter credential file is private to your machine. Read it locally when
signing in as Reporter; never commit or share it. The Developer and QA credentials
are fixed to match the demo sign-in shortcuts.

### 3. Run the backend

Keep the setup terminal open so its database environment variables remain set.
In a second terminal at the repository root, configure and start the backend:

```sh
export TRACEDESK_DATABASE_URL=jdbc:postgresql://127.0.0.1:54329/tracedesk_dev
export TRACEDESK_DATABASE_USER=tracedesk_dev
export TRACEDESK_DATABASE_PASSWORD=tracedesk_dev_only
export TRACEDESK_PROJECT_CURSOR_SECRET="$(openssl rand -hex 32)"
export AI_ENABLED=false
./gradlew :backend:auth-app:bootRun
```

If your local database password or port differs, set the corresponding values
here too. The backend applies database migrations on startup and listens on port
8080. AI is disabled for this local run.

### 4. Sign in

Open [https://localhost:5173](https://localhost:5173), accept the local
certificate warning if shown, and sign in with one of the accounts above. The
frontend forwards API requests to the backend on port 8080. Choose
`Local Reporter Workspace` after sign-in. Sign out before switching roles.

## Stop the app

Stop the backend with Ctrl+C in its terminal. Stop the Compose services while
keeping the database volume and its data:

```sh
docker compose stop frontend-dev db
```

To start them again, repeat step 1. Do not use `docker compose down --volumes`
unless you intend to erase the local database and all its data.

## Troubleshooting

- **Frontend cannot reach the API:** make sure the backend is running on port
  8080 and the frontend is using the Compose `dev` profile.
- **Login fails:** confirm the setup command completed, the backend and setup
  target the same database, and the email/password match the selected role.
- **Database connection fails:** check `docker compose ps` and verify the
  database password and port. The local setup tool accepts only an explicit
  loopback PostgreSQL address and the `tracedesk_dev` database/user.
- **Migration fails:** keep the existing database volume intact and share the
  safe error summary. Do not delete the volume to work around a migration error.

## More information

- [Detailed Docker and local database guide](docs/docker.md)
- [Local account provisioning and reset](docs/local-reporter-setup.md)
- [Product scope](docs/product.md)
- [Architecture](docs/architecture.md)
- [Project instructions](AGENTS.md)
