# Isolated PostgreSQL tests

The `backend/db-test-support` module is a test-only helper. Add it to a future
backend module with `testImplementation project(':backend:db-test-support')`; never
place it on a production runtime classpath.

Before creating a schema, `IsolatedPostgres.open(System.getenv())` requires:

| Variable | Meaning |
| --- | --- |
| `TRACEDESK_TEST_DATABASE_CONFIRM_ISOLATED=true` | Explicit operator confirmation |
| `TRACEDESK_TEST_JDBC_URL` | Plain PostgreSQL JDBC URL whose database name ends in `_test`; no URL credentials or query parameters |
| `TRACEDESK_TEST_DATABASE_USER` | Test-only database account |
| `TRACEDESK_TEST_DATABASE_PASSWORD` | Test-only database password |

It compares the target with `TRACEDESK_DATABASE_URL` and `DATABASE_URL` if set.
Provide a dedicated disposable database; the helper generates its own random
schema, verifies that every connection selects it, then drops only that schema.
The `_test` suffix and confirmation are safeguards, not a substitute for choosing
the correct server and account. The helper does not inspect local environment files.

Run `./gradlew :backend:db-test-support:test` with the variables set. The required
integration test **fails** when configuration is absent; it does not skip. The
`isolated-database` CI job supplies ephemeral PostgreSQL and synthetic credentials.
Locally, the guard-only tests can run without a database using:

```sh
./gradlew :backend:db-test-support:test --tests com.tracedesk.testing.IsolatedPostgresGuardTest
```

When application migrations are added, run them against the fixture's `DataSource`
and owned schema. Keep reset operations scoped to that schema and fail CI if the
database integration suite cannot execute.
