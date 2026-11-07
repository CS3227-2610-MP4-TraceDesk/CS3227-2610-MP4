# Acceptance criteria index

| Specification | Criteria | Status |
| --- | --- | --- |
| [Foundation](specs/foundation.md) | TD-FND-01 through TD-FND-06 | See owning specification and evidence |
| [Engineering foundations](specs/engineering-foundations.md) | TD-ENG-01 through TD-ENG-06 | See owning specification and evidence |
| [Local Docker environment](specs/docker-local.md) | TD-DKR-01 through TD-DKR-06 | See owning specification and evidence |
| [Reporter measurement tooling](reporter/specs/agentic-measurements.md) | TD-MET-01 through TD-MET-09 | See owning specification and evidence |
| [GitHub Actions coverage](reporter/specs/github-actions-coverage.md) | TD-CI-01 through TD-CI-05 | See owning specification and Reporter evidence |
| [Authentication and project membership](specs/auth-membership.md) | TD-AUTH-01 through TD-AUTH-12 | Bounded implementation present; partial verification and deferred criteria are recorded in Reporter evidence |
| [Login UI](specs/login-ui.md) | TD-LOGIN-01 through TD-LOGIN-06 | See [login authentication integration evidence](reporter/evidence/login-auth-integration.md); light-theme contrast and live backend browser verification remain pending |
| [Login authentication integration](specs/login-auth-integration.md) | TD-LOGIN-AUTH-01 through TD-LOGIN-AUTH-07 | Frontend implementation and mock API checks complete; live browser-to-backend verification remains pending; see [evidence](reporter/evidence/login-auth-integration.md) |
| [Shared UI and identity](specs/shared-ui.md) | TD-UI-01 through TD-UI-10 | Specified; production frontend port and acceptance verification pending |

Add Reporter, Developer, QA and shared gateway specifications as their implementation
begins. Do not declare unimplemented features accepted because their design is approved.
