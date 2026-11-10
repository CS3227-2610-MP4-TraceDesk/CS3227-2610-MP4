# Issue #37 — Local Reporter development setup evidence

Date: 2026-11-11 Singapore time. Branch `codex/local-reporter-setup`, base
`c90e672` plus uncommitted changes enumerated in the
[source manifest](local-reporter-setup.sha256). Owning
[specification](../../specs/local-reporter-setup.md),
[operator guide](../../../docs/local-reporter-setup.md).
Source-manifest SHA-256:
`117448ac6f4b2022d13a5375ded1134e8ceb17a468efb48fbb9b03b14b7d514f`.
The mixed acceptance-index edit and earlier uncommitted AI evidence are excluded
from this manifest; only the issue #37 index row is staged for this feature.
Human student owner: not supplied.

## Result and scope

TD-LOCAL-01 through TD-LOCAL-06 have local implementation and verification:

| Criterion | Implementation and actual verification |
| --- | --- |
| TD-LOCAL-01 | Explicit Gradle `localReporterSetup` command applies the existing migrations, generates one synthetic Reporter/project/membership and prints project UUID/login. Real browser signs in with its generated credentials. |
| TD-LOCAL-02 | Ownership marker, file lock and PostgreSQL advisory locks; matching-file/hash idempotence, role/missing-file conflicts, first-time concurrent provisioning, explicit reset, scoped session invalidation, marker/reset failure rollback and report/draft/audit preservation tested. |
| TD-LOCAL-03 | Exact dev database/user, literal loopback and explicit port, URL-option rejection, confirmation and profile guards; connected database/user/session-user/public-schema validation precedes migrations. Dedicated source sets are absent from production JAR and normal runtime. |
| TD-LOCAL-04 | Random password with existing Argon2id policy; private directory/file permissions, symlink/public-directory refusals, secure replacement and mismatch recovery. Generated password absent from frontend assets/backend JAR; credential directory ignored by Git and Docker. |
| TD-LOCAL-05 | Real HTTPS/session browser: anonymous session rotation, cookie flags, project discovery/Reporter context, draft save/read/resume, report create/list/read, CSRF/same-origin headers and denial for a real existing unowned project on context and report routes. No mocked identity. |
| TD-LOCAL-06 | Seven pure security/file checks plus one isolated database scenario, independent source review, package checks and final browser journey. Separate CI workflow added; remote CI has not run for this uncommitted candidate. |

The feature adds no application routes, public registration, role changes,
production seeds, AI invocation or deployment. Verification used only a newly
created disposable PostgreSQL 18 instance at loopback port 55419, database/user
`tracedesk_dev`. The existing Compose database on 54329 and frontend on 5173 were
not modified or stopped. No `.env*` files, production data or provider credentials
were accessed. The temporary verification database/container, generated local
Reporter credential file and lock were removed after checks.

## Commands and results

macOS, Java 25, Gradle wrapper, Node/React build, Chromium/Playwright and PostgreSQL
18. The isolated database password is an unrelated disposable synthetic value;
never substitute production credentials. Tests require this explicitly confirmed
owned target and fail if isolation configuration is missing.

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
export TRACEDESK_LOCAL_CONFIRM=true
export TRACEDESK_LOCAL_SMOKE_CONFIRM_DISPOSABLE=true
export TRACEDESK_LOCAL_JDBC_URL=jdbc:postgresql://127.0.0.1:55419/tracedesk_dev
export TRACEDESK_LOCAL_DATABASE_USER=tracedesk_dev
export TRACEDESK_LOCAL_DATABASE_PASSWORD=synthetic_local_setup_database_password
./gradlew :backend:auth-app:localDevTest :backend:auth-app:bootJar --no-daemon --console=plain
python3 tools/local-reporter-smoke.py
```

- Production compilation/bootJar: passed. ZIP inspection confirmed no
  `com/tracedesk/local/` setup or probe classes and no `.local` credential files.
- Final strengthened `localDevTest`: **8 passed**, zero failures/errors/skips,
  10 seconds. The combined build command above was exercised earlier; final
  strengthened tests ran with `:backend:auth-app:localDevTest` alone. Production
  application sources did not change afterward.
- From `frontend`, `npm run check`: type checking, lint and **77 unit tests passed**.
  `npm run build` passed; the smoke helper rebuilt the current frontend as well.
  Final E2E changes passed lint and the actual browser run.
- Attached local browser journey: passed, content-free summary
  `frontend/e2e-results/local-setup-result.json` was
  `{"phase":"COMPLETE","passed":true}`. The first journey passed before the
  existing-project negative case was added; the final strengthened journey
  passed with that real unowned project created and removed by a test-only
  probe. No traces, screenshots, video or detailed reports were retained.
- Generated synthetic credential file: mode0600; parent directory: mode0700.
  Programmatic checks compared the generated password against compiled frontend
  assets and every backend JAR entry without printing the password: absent.
- `python3 -m py_compile tools/local-reporter-smoke.py` and `git diff --check`:
  passed. Final foundation/link check passed:136 documents,4 skills,21 provenance
  entries and11 transport sources. Temporary backend/frontend ports were verified
  closed after cleanup.

The source-manifest fingerprint identifies source/configuration, not an executed
release or deployment. Generated credentials, cookies, SQL bodies, IDs and model
content are not retained in this evidence.

## Coordination, corrections and limits

Actual gpt-6-luna specialized agents: `/root/local_setup_spec` wrote the new
contract before implementation; `/root/local_setup_backend` implemented the
standalone command/tests; `/root/local_setup_browser` implemented the opt-in
browser test/config; `/root/local_setup_review` independently reviewed the actual
source/tests read-only. Root integrated, strengthened tests, fixed defects,
executed checks and wrote documentation/evidence. Actual handoffs and timings
are in the ignored run record `2026-11-11-local-reporter-setup.md`.

Review/integration corrections included dynamic explicit loopback ports instead
of a verification-only fixed port, hash-verified repeatability, locking before
credential installation, recovery instructions for mismatched database/file
state, safe Flyway logging, accurate draft navigation, and testing an existing
unowned project rather than only a nonexistent UUID. Final independent review
reported no remaining supported findings. The reviewer did not execute tests.

Failures were retained as observations: the initial test compilation referenced
private permission constants; the first database run exposed an unsupported
Flyway logger setting; strengthened fixtures initially rejected an empty existing
ownership table, recreated an existing table, and omitted the current audit
object ID. These were fixed against the current migrations without weakening
production guards. An initial browser collection check exposed an invalid
Playwright hook signature, corrected before execution. Repeated fixture failures
prompted reinspection of the migration chain before the final passing run.

Normal exceptions, database trigger failures and unsafe filesystem paths were
tested. Abrupt process/power loss and OS/filesystem faults during commit/rename
were not injected. Database/file replacement is not crash-atomic: mismatches fail
closed and require restoring matching local credentials. POSIX owner permissions
are required; Windows-native filesystem support is not verified. No monetary or
token savings, comparative model outcome, production readiness or remote CI pass
is claimed. The development-context comparison remains due at the Reporter
workflow milestone. Verification preceded the issue #37 commits. No push or issue closure performed.
