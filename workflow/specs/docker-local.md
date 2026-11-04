# TD-DKR — Local Docker environment

Scope: the repository user requested Docker setup for TraceDesk. The current
application has a static frontend and no runnable Spring Boot service. This
specification covers a working local container environment and frontend image;
the application backend image is added with its first runnable feature.

## Acceptance criteria

- [x] TD-DKR-01: `docker compose config` validates development, preview and
  test service definitions without requiring real secrets or production files.
- [x] TD-DKR-02: Development Compose starts a frontend reachable on a local
  port and a persistent PostgreSQL database with an observable healthy state.
- [x] TD-DKR-03: A production-style frontend image builds from locked
  dependencies, runs without root, serves the built page, and exposes a
  frontend-specific health response. It contains no source environment files.
- [x] TD-DKR-04: A separate, disposable test database can run the existing
  `db-test-support` suite. It does not share the development database volume.
- [x] TD-DKR-05: Local ports bind to loopback, generated files and secrets stay
  out of image context, and instructions state how to start/stop each service
  without deleting development data.
- [x] TD-DKR-06: Docker use and the remaining backend/deployment integration
  boundary are documented. This is not a production deployment approval.

Record exact verification and any limitations in
[Docker evidence](../evidence/docker-local.md).
