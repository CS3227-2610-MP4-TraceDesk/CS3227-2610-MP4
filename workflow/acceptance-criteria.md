# Acceptance criteria index

| Specification | Criteria | Status |
| --- | --- | --- |
| [Foundation](specs/foundation.md) | TD-FND-01 through TD-FND-06 | See owning specification and evidence |
| [Engineering foundations](specs/engineering-foundations.md) | TD-ENG-01 through TD-ENG-06 | See owning specification and evidence |
| [Local Docker environment](specs/docker-local.md) | TD-DKR-01 through TD-DKR-06 | See owning specification and evidence |
| [Reporter measurement tooling](reporter/specs/agentic-measurements.md) | TD-MET-01 through TD-MET-09 | See owning specification and evidence |
| [GitHub Actions coverage](reporter/specs/github-actions-coverage.md) | TD-CI-01 through TD-CI-05 | See owning specification and Reporter evidence |
| [Authentication and project membership](specs/auth-membership.md) | TD-AUTH-01 through TD-AUTH-12 | Current integrated candidate and criterion matrix: [AUTH-VERIFY evidence](reporter/evidence/auth-verification.md); deployment and milestone comparisons remain separate gates |
| [Login UI](specs/login-ui.md) | TD-LOGIN-01 through TD-LOGIN-06 | See [login authentication integration evidence](reporter/evidence/login-auth-integration.md); live backend browser verification remains pending |
| [Login authentication integration](specs/login-auth-integration.md) | TD-LOGIN-AUTH-01 through TD-LOGIN-AUTH-07 | Frontend implementation and mock API checks complete; live browser-to-backend verification remains pending; see [evidence](reporter/evidence/login-auth-integration.md) |
| [Shared UI and identity](specs/shared-ui.md) | TD-UI-01 through TD-UI-10 | Specified; production frontend port and acceptance verification pending |
| [Reporter reports and shared lifecycle](specs/reporter-reports.md) | TD-RPT-01 through TD-RPT-05 | REPORT-PLAN contract review recorded in [evidence](reporter/evidence/reporter-reports-plan.md); REPORT-DATA results below; runtime acceptance for #14–17 remains pending |
| [Reporter report API and persistence](specs/reporter-reports.md#report-data-13-executable-acceptance-criteria) | TD-RPT-06 through TD-RPT-10 | REPORT-DATA implementation and verification recorded in [evidence](reporter/evidence/reporter-report-data.md); later report UI/edit/progress/AI slices remain separate |
| [Developer investigation and QA handoff](specs/developer-investigation.md) | TD-DEV-01 through TD-DEV-05 (contract); TD-DEV-06 through TD-DEV-30 planned in delivery issues #27–31 | Specified for DEV-PLAN #26; independent contract review and corrections in [evidence](developer/evidence/developer-investigation-plan.md); no runtime acceptance claimed |

Add Reporter, Developer, QA and shared gateway specifications as their implementation
begins. Do not declare unimplemented features accepted because their design is approved.
