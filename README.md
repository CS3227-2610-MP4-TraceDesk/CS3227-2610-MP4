# TraceDesk

AI-assisted software bug reporting, investigation, and verification for CS3227 MP4.
Reporter, Developer, and QA reviewer each own a stage of the bug lifecycle and a
human-reviewed AI capability.

## Current status

Architecture and reuse selections were confirmed by the repository user on
2026-11-04. This checkout contains the engineering foundation and an isolated,
tested provider transport extraction. It is not yet a runnable web application.
Authentication, persistence, the secured gateway, and role features remain to be
implemented in the order recorded in the integration plan.

## Start here

- [Project instructions](AGENTS.md)
- [Product scope](docs/product.md) and [architecture](docs/architecture.md)
- [Reuse inventory and integration sequence](docs/reuse-plan.md)
- [Development workflow](workflow/README.md)
- [Development harness](harness/README.md)
- [Foundation specification](workflow/specs/foundation.md)

## Repository layout

| Location | Purpose |
| --- | --- |
| `.agents/skills/` | Portable project-local skills; no personal configuration |
| `docs/` | TraceDesk requirements, architecture, AI/security policies, reuse provenance |
| `harness/` | Development-agent roles and coordination protocol; execution uses the host's agent tools |
| `workflow/` | Specifications, acceptance index, handoff/evidence templates, decisions and progress |
| `backend/` | Java modules; currently only internal provider transport and synthetic tests |
| `tools/` | Portable repository checks |
| `.github/workflows/` | Foundation CI; deployment is not configured |

Do not add empty application directories: create the React frontend and backend
domain/application modules when their first vertical slice is implemented.

## Verify the foundation

Use JDK 25 and Python 3. Run from the repository root:

```sh
python3 tools/check_foundation.py
./gradlew :backend:ai-transport:test
```

The Java tests use synthetic loopback HTTP responses. They need no provider key,
database, or application environment file. Initial Gradle dependency resolution
may require network access. See [verification evidence](workflow/evidence/foundation.md)
for what actually ran in this checkout.
