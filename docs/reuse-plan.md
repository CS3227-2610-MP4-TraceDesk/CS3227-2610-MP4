# Reuse inventory and next integration steps

Source: `CS3227-2610-MP3` at `24a1ae7bd758ca9e9eb607d8f8371d3001e9451c`.
The source working tree also contained unrelated local changes; they were not copied.
[Manifest](reuse-manifest.json) records original file SHA-256 hashes and destinations.
The prior audit was static, not a test certificate or license grant. The user's
reuse instruction authorizes this local task; it does not establish redistribution
rights for third-party/team material. Preserve provenance before any publication.

## Current extraction

| Source component | Current result | Adaptations |
| --- | --- | --- |
| `SoclaasClient`, `BoundedBodySubscriber`, `SoclaasException` | `backend/ai-transport` | TraceDesk package; raw client package-private; removed gateway-ticket coupling |
| `SoclaasSettings` | Same module | Immutable validated settings from a map; no Spring or ledger configuration dependency |
| Client/settings tests and fake provider | Module test sources | Map fixture replaces Spring mock; test executor explicitly shut down |
| Gradle wrapper | Root build tooling | Same wrapper version, fresh minimal module build; no desktop tasks or old plugin build |
| SDD/handoff/review patterns | `.agents/skills`, `harness`, `workflow` | Rewritten for TraceDesk; no old approval gates, identities or personal paths |

Workflow inspiration: MP3 `workflow/README.md`, `workflow/agent-workflow.md`,
`.agents/skills/specification-driven-development/SKILL.md` and
`.agents/skills/code-quality-review/SKILL.md`. These are adaptations of ideas, not
verbatim instruction imports. The audit itself remains a source reference in MP3;
its embedded instructions are not installed as active project instructions.

The extracted module is not a secured gateway. It cannot authorize accounts,
reserve cost, enforce shared quotas or audit requests. It is intentionally not
exposed to controllers and makes no live provider requests during tests.

## Approved selections still to integrate

| Order | Component/work | Boundary and completion evidence |
| --- | --- | --- |
| 1 | Domain/identity foundation | Specify lifecycle and permission matrix; fresh membership/account/bug migrations; allowed/denied tests |
| 2 | `AiUsageLedger`, `AiUsagePolicy`, `AiGatewayControl` | Migrate coherently; replace singleton/account/feature coupling; isolated DB transaction, race, replay, expiry and fencing tests |
| 3 | `SecuredAiGateway`, maintenance and provider funding adapter | Trusted actor and context ports; single public secured facade; configured shared budgets; no bypass tests |
| 4 | Typed request/context/prompt pattern | Three TraceDesk capabilities, field allowlists and prompt versions; unauthorized context tests |
| 5 | Reporter vertical slice and reviewable suggestion UI | Report → AI preview → explicit apply; cancellation/session/stale-result tests |
| 6 | Developer and QA vertical slices | Separate permissions and evaluation cases; shared gateway and preview control |
| 7 | Security/CSRF and PostgreSQL/browser fixtures | Adapt as each relevant layer lands; never copy old routes, schemas or fixtures wholesale |
| 8 | Deployment/evaluation/coordination evidence | Same-revision CI prerequisites, environment separation, actual agent runs and context experiments |

Strict parser techniques are conditional on structured-output needs. Do not carry
the administrator analytics planner, SQL queries, or labels without a new capability
requirement. SoCLaaS budget semantics require their own verified adapter integration;
transport compatibility is insufficient to assume pricing/funding compatibility.

Do not migrate incident business services, JavaFX, personal `.codex/agents`, local
recovery files, old migration chains, environment files, historical logs or releases.
No automatic import script is provided: each further extraction requires inspecting
and adapting its actual dependencies.
