---
layout: default
title: Developer Guide
nav_order: 3
---

# TraceDesk Developer Guide

TraceDesk is a web application for reporting software bugs, investigating them and verifying proposed fixes. Its current implementation uses a React/TypeScript frontend, a Java/Spring backend, PostgreSQL and a shared secured AI gateway. The following guide describes the repository and its development process; it does not claim that an online deployment has been accepted.

## Contents

- [Development setup](#development-setup)
- [Architecture and source map](#architecture-and-source-map)
- [Persistence, authorization and privacy](#persistence-authorization-and-privacy)
- [Bug workflow](#bug-workflow)
- [Web security](#web-security)
- [Advisory AI integration](#advisory-ai-integration)
- [Verification](#verification)
- [Deployment and operation](#deployment-and-operation)
- [Troubleshooting](#troubleshooting)
- [Specification-driven and multi-agent workflow](#specification-driven-and-multi-agent-workflow)
- [Acknowledgements](#acknowledgements)

## Development setup

Use JDK 25, Node.js 24 and Docker Desktop with Compose. From the repository root, follow the [README local setup](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4#start-tracedesk-locally) in order: start the local database and frontend, provision synthetic accounts, configure and start the backend, then open the local HTTPS frontend. The provided setup uses a self-signed certificate and defaults to AI disabled.

Keep credentials in private environment configuration, not in source control or logs. Do not replace an existing environment file. The [Docker guide](docker.md) explains ports and local services; the [local account guide](local-reporter-setup.md) explains provisioning and the effect of reset operations.

## Architecture and source map

```text
React UI → session and API security → application services
                                      ├─ domain and repository ports → PostgreSQL
                                      └─ role AI context → secured gateway → SoCLaaS
```

| Area | Responsibility |
| --- | --- |
| `frontend/` | React role workspaces, shared shell, forms, accessibility and browser tests. |
| `backend/auth-app/` | Sessions, project/report APIs, role workflows, persistence adapters and migrations. |
| `backend/ai-gateway/` | Provider-independent authorization, admission, accounting and gateway contracts. |
| `backend/ai-transport/` | Bounded SoCLaaS transport behind the gateway. |
| `workflow/` | Shared decisions, feature specifications, criteria, role-specific process, experiments, evidence and development tools. |
| `harness/` and `.agents/skills/` | Portable agent handoffs, role guidance and scoped skills used by the development process. |
| `logs/` | Verified summaries of development interactions. |

The [architecture document](architecture.md) and [shared role contracts](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/decisions/002-shared-role-contracts.md) describe the intended boundaries. Some older supporting documents describe earlier integration states; use the source and linked feature specifications for the current slice.

## Persistence, authorization and privacy

The backend owns state transitions and project/object authorization. PostgreSQL stores the application's records, sessions, project membership and audit/accounting data. Fresh TraceDesk migrations live in the backend; test fixtures use a separate database or isolated schema. Access decisions derive from the authenticated actor and active membership, not a role claimed by the browser.

Developer private investigation notes are restricted to the current assignee. QA discussion is restricted to active QA reviewers in the project. Lifecycle, version checks and role boundaries are enforced by the backend rather than delegated to the interface or AI output. See [security](security.md), [test database](test-database.md) and the corresponding role specifications.

## Bug workflow

The shared flow is report → investigation → proposed resolution → QA verification → close or send back. Reporters can save drafts and submit reports. Developers can work on assigned bugs and send a resolution to QA. QA can manage checks and notes, use reusable templates, record a decision, and inspect review history. The application restricts available actions by lifecycle state and authorization. See the [QA specifications](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/tree/main/workflow/specs) for the exact contracts.

## Web security

The application uses authenticated sessions and server-side authorization, with CSRF protections for writes and safe handling of untrusted text. AI suggestions are advisory; application code validates and authorizes any explicit user action that applies one. Review [security](security.md), the [threat model](threat-model.md) and role tests for precise boundaries and limitations. Do not put real credentials, user reports or provider responses in test evidence.

## Advisory AI integration

- **Reporter:** a saved draft or eligible report can receive suggested wording and missing-information questions; applying selected fields is an explicit version-checked action. See [Reporter AI](reporter-ai.md).
- **QA reviewer:** an eligible report and latest proposed resolution can be used to generate verification suggestions and follow-up answers. Selected checks are added explicitly to the verification draft, which the reviewer saves. See the [QA AI contract](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/specs/qa-ai.md).
- **Developer:** investigation AI core work exists in the repository. A complete user-facing and deployed journey must be verified separately; do not infer it from core modules alone.

AI enablement is controlled by trusted backend configuration, model/access approval and finite limits. It is disabled in the README's default local setup. Provider keys stay on the backend. [AI policy](ai-policy.md), [enablement](ai-enablement.md), [gateway](ai-gateway.md) and [role validation evidence](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/qa/evidence/role-ai-validation.md) distinguish synthetic tests, live provider checks and unverified deployment claims.

## Verification

From `frontend/`:

```sh
npm ci
npm run check
npm run build
```

`npm run check` runs TypeScript checking, lint and unit tests. Browser checks use Playwright; the real-backend project also needs the explicitly isolated PostgreSQL fixture and its environment variables. See [test database](test-database.md) and the repository CI workflows for current commands. Backend builds and focused tests use the checked-in Gradle wrapper and JDK 25. Never point test fixtures at development or production data.

A test pass applies to the revision and environment recorded with it. The [QA evidence index](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/qa/evidence/submission-evidence.md) links the QA specifications, implementation and historical PR checks. Verify the latest candidate separately; old evidence is not a current release certificate.

## Deployment and operation

A publicly reachable deployment and separate production/development environments must be verified for submission. The repository's [deployment verification guide](deployment-verification.md) describes the required same-revision, HTTPS, routing, secret, database and role-smoke checks. It does not itself demonstrate that an environment was deployed. Use operator-managed hosting and separate environment credentials; local Compose is a development setup.

## Troubleshooting

- **Local connection failure:** check the database, backend and frontend are running and that configured ports match.
- **No projects after sign-in:** confirm active project membership; account registration does not grant it automatically.
- **AI unavailable:** check enablement, model access and provider connectivity without exposing credentials.
- **Version conflict:** reload the current report and re-evaluate unsaved edits before retrying.
- **Migration or test fixture failure:** verify the target is the intended isolated database; do not erase a development volume to hide a failure.

## Specification-driven and multi-agent workflow

The [engineering workflow](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/workflow/README.md) links decisions, feature specifications and acceptance criteria. The [harness](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/blob/main/harness/README.md) describes bounded roles, ownership and handoffs. Contributor areas under `workflow/reporter/`, `workflow/developer/` and `workflow/qa/` retain their own evidence; shared specifications and templates remain at the workflow root.

The local UI research runner under `scripts/ui_research.py` and `workflow/ui-research/` coordinates research, design and automated review sessions. Its run records and limitations are described in its README. Skill files describe a process; actual execution, review and measurements require corresponding evidence. [Reflections](Reflections.md) discusses recorded examples and their limitations.

## Acknowledgements

TraceDesk uses React, TypeScript, Spring Boot, PostgreSQL, Playwright and OpenAI Apps SDK UI components, along with the dependencies listed in the repository manifests. SoCLaaS supplies model access when separately configured. The shared UI design system documents Codex/OpenAI and Apple-inspired references. The structure and website presentation of these guides follow the team's MP3 documentation template; the project-specific descriptions and evidence are written for TraceDesk. Consult the dependency manifests and [reuse manifest](reuse-manifest.json) for more detail; add any missing project-specific sources before final submission.

The repository's [reuse inventory](reuse-plan.md) and [engineering provenance](engineering-reuse.json) record existing adaptations from MP3, including SoCLaaS transport and tests, as well as frontend tooling. The assignment states that previous MPs may not be reused; the team must resolve the eligibility of these existing adaptations with the teaching team. This acknowledgement does not establish that the adaptations are permitted.
