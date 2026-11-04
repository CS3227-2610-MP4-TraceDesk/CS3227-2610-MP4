# Docker setup verification — 2026-11-04

Scope: TD-DKR. Base revision: `38d1d49` plus uncommitted Docker setup changes.
No hosted deployment or production readiness is claimed.

Environment: Docker Desktop 28.1.1, Compose v2.35.1, macOS ARM64, JDK 25 for
the database test. Synthetic local credentials only. No source environment file
was read or used. No live provider, hosted CI or production deployment was run.

## Local checks

| Check | Result | Criteria |
| --- | --- | --- |
| `docker compose config --quiet` and each `dev`, `preview`, `test` profile | All four configurations valid | TD-DKR-01 |
| `docker compose --profile dev up --build -d --wait db frontend-dev` | Both services started; PostgreSQL healthy | TD-DKR-02 |
| Local GET on `127.0.0.1:5173/` | HTTP success, TraceDesk Vite HTML | TD-DKR-02 |
| `docker volume inspect tracedesk-mp4_dev-db-data` | Named development data volume exists | TD-DKR-02/04 |
| `docker compose --profile preview up --build -d --wait frontend-preview` | Image built after typecheck, lint, 2 unit tests and production bundle; container healthy | TD-DKR-03 |
| Local GET `/health/frontend` and HEAD `/` on port 8080 | JSON frontend health and HTTP 200 HTML with security headers | TD-DKR-03 |
| `docker compose exec -T frontend-preview id -u` | UID 1000, non-root | TD-DKR-03 |
| `docker compose --profile test up -d --wait test-db` | Separate PostgreSQL container healthy | TD-DKR-04 |
| `:backend:db-test-support:test --rerun-tasks --no-daemon` against port 54330 | All guard and schema lifecycle tests passed, no skip | TD-DKR-04 |
| `docker compose stop frontend-preview test-db` and `docker compose rm -f frontend-preview test-db` | Temporary containers stopped and removed; development services remain running | TD-DKR-04/05 |
| `python3 tools/check_foundation.py` and `git diff --check` | Passed after documentation and configuration changes | TD-DKR-05/06 |

`compose.yaml` publishes ports only on 127.0.0.1. PostgreSQL 18's named
development volume is mounted at `/var/lib/postgresql`, matching its versioned
data-directory layout. The test service uses tmpfs there, with no shared volume.
The Docker build context excludes environment files and generated outputs; the
final image copies only the compiled frontend and static server. These statements
are from configuration and build inspection, not a secret scan of Docker history.

The `container-build` CI job was added to rebuild and smoke test the image. Its
hosted execution remains unverified. The development frontend and database were
left running for local use at ports 5173 and 54329. The backend is not yet a
runnable Spring application, so there is no backend container or end-to-end
role workflow to test.
