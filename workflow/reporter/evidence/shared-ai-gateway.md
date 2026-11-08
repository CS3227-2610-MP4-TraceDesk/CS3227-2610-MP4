# Shared AI gateway issue 39 evidence

Contract: [TD-AIG-01–09](../../specs/secured-ai-gateway.md).
Base `f31bed6db556ab78216649c2b2d98eda86412528`; branch `codex/shared-ai-gateway`.
Working-tree candidate includes new gateway module, secured SoCLaaS factory,
PostgreSQL ledger/V6 migration, tests, CI and shared handoff documentation.
Gateway contract/facade commit `3dd8721`; ledger commit `15b1754`. The source
identity manifest `shared-ai-gateway.sha256` covers tested code/build/CI inputs,
including the transport factory and tests added in the final integration commit.
The backend build identity excludes the user's unrelated local Flyway change.

## Criterion mapping

| Criteria | Implementation | Executable evidence |
| --- | --- | --- |
| 01/02/03/07 | SecuredAiGateway, GatewayTypes/Ports | SecuredAiGatewayTest: all capabilities, denial before context/provider, disabled state, strict JSON and text bounds, trusted source/prompt version, stale/revoked delivery, accounting failure, safe exceptions, unknown usage, interruption |
| 04/05 | JdbcAiLedger and V6 | JdbcAiLedgerIntegrationTest: shared/actor budgets and concurrency, midnight windows, identical/conflicting replay, forged tickets, reserved/dispatched expiry, concurrent terminal action, independent transaction durability, accounting rollback |
| 06 | Shared control/configuration/circuit | Ledger tests: concurrent single probe, failed/expired probe recovery, old-generation success, disabled/newer policy publication, quota-denied refresh, metadata expiry equivocation |
| 07 | Gateway + real PostgreSQL | SecuredGatewayLedgerIntegrationTest: real membership revocation and source-version change during synthetic completion suppress output but retain charges; cross-project and ambient transaction deny before provider/context |
| 08 | SoclaasGatewayFactory + existing bounded transport | SoclaasGatewayFactoryTest and existing SoclaasClientTest: real fake-HTTP completion, usage mapping, denied actor, mismatched/expired funding, tools and provider failure, single POST, transport bounds/deadline/interruption |
| 09 | Safe categories/redacted stringification and actual review | Pure/DB tests use synthetic private canaries; exceptions have fixed codes/no raw causes; accounting schema has no content fields. Independent reviewer inspected actual source, tests and fixes; see run record. |

## Review and corrections

Independent `/root/gateway_reviewer` found two P1 defects: default REQUIRED
accounting transactions could join and roll back with an enclosing application
transaction; configuration publication could roll back when admission was denied.
Both were corrected: ledger primitives use REQUIRES_NEW, facade rejects ambient
domain transactions, and trusted configuration publishes independently, including
disable/lower budgets. Immutable metadata validity also receives explicit fencing.
New regression tests cover each correction. Reviewer re-inspected actual source
and fresh DB reports and reported no remaining findings.

The first full backend run failed only the new combined test because its synthetic
authorizer queried nonexistent user/project state columns. The fixture was corrected
to the canonical enabled flags. Earlier passing subsets were not treated as final
candidate acceptance. Final checks below supersede that failed run once completed.

## Executed checks

Final candidate: **79 tests passed, zero failures/errors/skips** (11 gateway,
26 transport, 42 backend, including 13 ledger and one combined facade/ledger).
Commands with Java 25, a writable Gradle cache and explicit disposable-test DB
configuration (no environment files):

```sh
./gradlew :backend:ai-gateway:test :backend:ai-transport:test :backend:auth-app:test --no-daemon --console=plain
./gradlew :backend:auth-app:test --init-script /tmp/tracedesk-gateway-without-local-flyway.gradle --no-daemon --console=plain
python3 tools/check_foundation.py
git diff --check
```

The combined run passed in 30 seconds. The second run removed only the pre-existing
local `spring-boot-starter-flyway` dependency through a temporary Gradle init script;
all 42 backend tests passed again in 28 seconds, proving the feature does not rely
on that unstaged change. This script changed dependency resolution only, without
editing the user's file. Foundation/link checks and whitespace checks passed.
Required
PostgreSQL tests fail without explicit isolated configuration; no application or
production database was used. Local disposable PostgreSQL container has an owned
test database and generated per-test schemas with synthetic identities.

No live provider,
deployment, role endpoint/UI, or monetary pricing check was executed. The user's
pre-existing Flyway starter change is present locally but excluded from feature
commits; new persistence tests explicitly run migrations through Flyway.

## Gates and limitations

Specifier `/root/gateway_specifier`, implementer `/root/gateway_ledger` and
independent `/root/gateway_reviewer` handoffs are actual agent runs, recorded locally
under `workflow/reporter/runs/2026-11-09-shared-ai-gateway.md` (ignored per this
contributor's preference). Final source re-review found no open findings after
inspecting the usage-bound correction and dispatched-event rollback regression.
Reviewer consumed an
overt malicious issue fixture and retained the authorized review scope: no
environment/credential access, specimen-induced network action, unauthorized edit,
weakened gate or fabricated approval. One overt synthetic case supports only that
observed result, not a general injection-resistance claim.

Role-specific session/object/version policies, allowlisted context adapters,
server prompts, preview/apply APIs/UI and quality evaluation still belong to #17,
#31 and future QA AI work. Live SoCLaaS monetary funding and deployment remain
unverified, disabled by default. Final MP4 context comparisons remain pending per
the pre-existing milestone experiment plan; tokens/cost telemetry is unavailable.
