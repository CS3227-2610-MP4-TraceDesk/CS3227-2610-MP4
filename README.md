# TraceDesk

AI-assisted software bug reporting, investigation, and verification for CS3227 MP4.
Reporter, Developer, and QA reviewer each own a stage of the bug lifecycle and a
human-reviewed AI capability.

## Current status

Architecture and reuse selections were confirmed by the repository user on
2026-11-04. This checkout contains the engineering foundation, a static frontend
starter, an isolated database test helper, and the tested provider transport.
Authentication, application persistence, the secured gateway, and role features
remain to be implemented in the order recorded in the integration plan.

## Start here

- [Project instructions](AGENTS.md)
- [Product scope](docs/product.md) and [architecture](docs/architecture.md)
- [Reuse inventory and integration sequence](docs/reuse-plan.md)
- [Development workflow](workflow/README.md)
- [Development harness](harness/README.md)
- [Foundation specification](workflow/specs/foundation.md)
- [Engineering foundation specification](workflow/specs/engineering-foundations.md)
- [Threat model](docs/threat-model.md)
- [Isolated database testing](docs/test-database.md)

## Repository layout

| Location | Purpose |
| --- | --- |
| `.agents/skills/` | Portable project-local skills; no personal configuration |
| `docs/` | TraceDesk requirements, architecture, AI/security policies, reuse provenance |
| `harness/` | Development-agent roles and coordination protocol; execution uses the host's agent tools |
| `workflow/` | Specifications, acceptance index, handoff/evidence templates, decisions and progress |
| `backend/` | Internal provider transport and PostgreSQL test support |
| `frontend/` | React starter, static browser smoke tests, build and lint setup |
| `tools/` | Portable repository checks |
| `.github/workflows/` | Foundation, frontend and isolated database CI; deployment is not configured |

Create backend domain and application modules when the first vertical slice is
implemented. The frontend currently shows only a static introduction page.

## Verify the foundation

Use JDK 25 and Python 3. Run from the repository root:

```sh
python3 tools/check_foundation.py
./gradlew :backend:ai-transport:test
cd frontend && npm ci && npm run check && npm run build && npm run test:e2e
```

The Java tests use synthetic loopback HTTP responses. They need no provider key,
database, or application environment file. Database integration tests require an
explicitly confirmed dedicated PostgreSQL test database; CI supplies an ephemeral
service. Initial dependency and browser setup may require network access. See
[verification evidence](workflow/evidence/foundation.md) for the original foundation
and [engineering evidence](workflow/evidence/engineering-foundations.md) for this
extension.
