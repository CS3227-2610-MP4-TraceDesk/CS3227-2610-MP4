# TraceDesk

AI-assisted software bug reporting, investigation, and verification for CS3227 MP4.
Reporter, Developer, and QA reviewer each own a stage of the bug lifecycle and a
human-reviewed AI capability.

## Current status

Architecture and reuse selections were confirmed by the repository user on
2026-11-04. This checkout contains the engineering foundation, login UI connected
to the bounded authentication API, an isolated database test helper, and the
tested provider transport. Persistence, the secured gateway, and role workflows
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
- [Local Docker setup](docs/docker.md)
- [UI identity and design system](docs/ui-design-system.md),
  [UI specification](workflow/specs/shared-ui.md),
  [reference screens](workflow/ui/reference/README.md), and
  [UI implementation plan](workflow/plans/ui-foundation.md)

## Repository layout

| Location | Purpose |
| --- | --- |
| `.agents/skills/` | Portable project-local skills; no personal configuration |
| `docs/` | TraceDesk requirements, architecture, AI/security policies, reuse provenance |
| `harness/` | Development-agent roles and coordination protocol; execution uses the host's agent tools |
| `workflow/` | Specifications, acceptance index, handoff/evidence templates, decisions and progress |
| `backend/` | Internal provider transport and PostgreSQL test support |
| `frontend/` | React login UI, local demo preview, browser tests, build and lint setup |
| `tools/` | Portable repository checks |
| `.github/workflows/` | Foundation, frontend and isolated database CI; deployment is not configured |
| `compose.yaml`, `Dockerfile`, `docker/` | Local containers and built frontend image |

The frontend opens on the approved login screen and uses server-confirmed
sessions. Role workspaces remain pending.

## Inspect the login UI locally

Run from the repository root after installing Node and npm. The current jsdom
dependency declares Node 24.15 or newer in the Node 24 release line.

```sh
cd frontend
npm ci
npm run dev
```

Open https://localhost:5173/. Vite serves HTTPS with a locally generated,
self-signed certificate; the browser may ask you to trust it. Start the local
database and authentication API as described in the [Docker guide](docs/docker.md).
The login API requires an account already provisioned in that database. The
development-only **Demo accounts** disclosure fills synthetic credentials and
submits them to the same backend API; it does not create or authenticate users
locally. Production builds omit those shortcuts. See the
[login criteria](workflow/specs/login-ui.md),
[authentication integration contract](workflow/specs/login-auth-integration.md),
and [verification evidence](workflow/reporter/evidence/login-auth-integration.md).

To inspect the shared shell with sample data, open
`https://localhost:5173/?workspace-preview` in the Vite development server.
Choose Reporter, Developer, or QA reviewer. This preview is separate from sign-in
and does not need the local authentication API.

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

For a container-based local environment, see the [Docker guide](docs/docker.md).
