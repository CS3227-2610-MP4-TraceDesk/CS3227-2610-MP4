# Local Docker setup

Docker Compose runs the current TraceDesk frontend and local PostgreSQL. The
Spring Boot application has no runnable entry point yet; add its service and
image when the first backend feature is implemented. These commands are local
development and image checks, not production deployment.

## Development

From the repository root:

```sh
docker compose --profile dev up --build -d --wait db frontend-dev
docker compose ps
```

Open `http://127.0.0.1:5173`. Source changes under `frontend/` refresh through
Vite. The development database listens only on `127.0.0.1:54329` and persists in
the `dev-db-data` Docker volume. It is named `tracedesk_dev`; the default local
account and password are synthetic and configurable with `.env` (copy from
[`.env.example`](../.env.example)). No database tables or application users exist yet.

To stop these containers while retaining development data:

```sh
docker compose stop frontend-dev db
```

To recreate the frontend dependency volume after changing `package-lock.json`,
run `docker compose --profile dev run --rm frontend-dev npm ci`, then restart
`frontend-dev`. The development image itself uses the lockfile on rebuild.
Do not use `docker compose down --volumes` unless you intend to delete local
development database data.

## Built frontend image

```sh
docker compose --profile preview up --build -d --wait frontend-preview
```

Open `http://127.0.0.1:8080`. `http://127.0.0.1:8080/health/frontend` checks
only the frontend image. The image runs as the non-root `node` user and serves
only compiled static files. Stop it with `docker compose stop frontend-preview`.
The frontend image does not provide an application API; `/api/` returns 404 until
a secured backend and reverse proxy are added.
The CI `container-build` job builds this image and checks the frontend health
response after the normal frontend checks pass.

## Disposable database for integration tests

The test database uses a separate service with temporary storage and port 54330:

```sh
docker compose --profile test up -d --wait test-db
TRACEDESK_TEST_DATABASE_CONFIRM_ISOLATED=true \
TRACEDESK_TEST_JDBC_URL=jdbc:postgresql://127.0.0.1:54330/tracedesk_test \
TRACEDESK_TEST_DATABASE_USER=tracedesk_test \
TRACEDESK_TEST_DATABASE_PASSWORD=synthetic_test_password \
./gradlew :backend:db-test-support:test
docker compose stop test-db
docker compose rm -f test-db
```

The Java command needs JDK 25. Its fixture creates a randomly named schema,
verifies every connection selects that schema, and drops it after the test.
The test service has no named volume and its data disappears when stopped.
It never shares the development database volume.

## Environment boundaries

Compose binds published ports to loopback and uses local synthetic credentials.
`.env` is ignored by Git and excluded from Docker build context. Do not use this
Compose file for production credentials, public exposure, or deployment. When
the Spring backend exists, define its database URL and secrets through a separate
deployment configuration, then add a backend service here only for local work.
