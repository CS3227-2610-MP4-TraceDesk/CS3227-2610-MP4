# Production deployment (Cloud Run + Neon)

TraceDesk production runs as one container on Google Cloud Run
(`asia-southeast1`) backed by a dedicated Neon PostgreSQL project. The image is
the `app` target of the root `Dockerfile`: Spring Boot serves the API and the
compiled frontend from the same origin.

## Resources

| Resource | Name |
| --- | --- |
| Cloud Run service | `tracedesk-run` |
| Artifact Registry repository | `tracedeskrepo` |
| Deploy identity (GitHub OIDC) | `tracedesk-deployer` service account, Workload Identity provider `github-actions/tracedesk` |
| Runtime identity | `tracedesk-runtime` service account (Secret Manager access only) |
| Database | Neon project `tracedesk`, database `tracedesk`, role `tracedesk_app` |

The Workload Identity provider accepts only this repository's ID, ref
`refs/heads/main`, and the GitHub `production` environment. No service-account
key exists.

## Configuration ownership

- **GitHub `production` environment variables** hold only non-secret deploy
  identifiers (project, region, repository, service, identities).
- **Secret Manager** holds `tracedesk-database-url` (JDBC, direct endpoint,
  `sslmode=require`), `tracedesk-database-user`, `tracedesk-database-password`,
  `tracedesk-project-cursor-secret` and `tracedesk-soclaas-api-key`.
- **Cloud Run** owns runtime settings and the secret references. The workflow
  deploys a new image only, so revisions inherit service configuration.

AI is deployed with `AI_ENABLED=false`. Enabling it is a separate decision under
[AI enablement](../workflow/specs/ai-enablement.md); set `AI_ENABLED`,
`SOCLAAS_MODEL` and the sponsored-approval variables on the service, never in
GitHub.

## Release

Every push to `main` runs `.github/workflows/deploy.yml`: build the `app` image,
push it by commit SHA, deploy the immutable digest, then require
`/health/ready` to report `READY`. Flyway applies fresh TraceDesk migrations on
startup. Roll back with
`gcloud run services update-traffic tracedesk-run --region asia-southeast1 --to-revisions <revision>=100`.

Emergency AI disable:
`gcloud run services update tracedesk-run --region asia-southeast1 --update-env-vars AI_ENABLED=false`.
