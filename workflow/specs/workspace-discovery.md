# TD-WS — Authenticated workspace discovery and routing

Status: implementation candidate. This bounded shared prerequisite is authorized by
the accepted authentication, membership, shared UI and role contracts. Human owner:
not supplied.

## Goal and boundary

After a trusted session is established, list the actor's active project memberships
so the browser can present the projects under the single role freshly loaded from
the actor's account. Selecting any active membership routes to that account's role
workspace. A query parameter, demo shortcut or browser control is only a navigation
hint and cannot establish a role. The project directory and immediate join flow
are specified in [TD-PROJ](project-discovery-joining.md).

`GET /api/workspaces` returns at most 100 active memberships for the trusted actor:

```json
{"items":[{"project":{"id":"<uuid>","name":"<plain text>"},"role":"REPORTER|DEVELOPER|QA_REVIEWER"}],"hasMore":false}
```

Rows are ordered by case-insensitive project name and project UUID. The query
joins the current user, project and membership and returns only enabled accounts,
enabled projects and `ACTIVE` memberships. Role comes from the account, never the
legacy membership snapshot. No request field, header or URL role is
accepted. There is no mutation or business audit event. The existing role-specific
context endpoint reauthorizes the selected project before protected role data is
rendered.

An absent/expired session returns `401 UNAUTHENTICATED` and records a bounded
security event. Persistence or event-sink failure returns `503 SERVICE_UNAVAILABLE`
without cached workspace data. An actor with no memberships receives an empty list.
If more than 100 memberships exist, `hasMore` is true and the UI states that the
list is incomplete; arbitrary project-ID fallback is intentionally not added.

The picker presents all active memberships under the account's server-returned
role. Reporter, Developer and QA projects are selectable when the corresponding
production workspace is available. A development preview is not an authorization
path.
Changing projects returns to the picker, preserves the existing unsaved-work warning,
and discards protected state owned by the previous workspace.

Out of scope: membership administration, invitations, changing a role, pagination
beyond the bounded MVP list, and production/demo data seeding.

## Acceptance criteria

| Criterion | Observable requirement |
| --- | --- |
| **TD-WS-01** | The endpoint derives the actor only from the trusted session and returns only enabled projects with that actor's active membership and stored role, bounded to 100 items plus `hasMore`. |
| **TD-WS-02** | Anonymous, expired, disabled, removed, cross-user and persistence/event failure paths disclose no workspace data; forged actor or role inputs cannot affect the result. |
| **TD-WS-03** | The authenticated UI shows loading, empty, unavailable and bounded-list states, groups projects by server role, and never treats URL/demo role selection as identity. |
| **TD-WS-04** | Selecting Reporter or Developer routes to that workspace and the role-specific context endpoint reauthorizes it; changing projects returns to discovery without leaking the previous workspace's protected state. QA discovery cannot enter the preview workspace. |
| **TD-WS-05** | Focused backend and frontend tests cover allowed memberships, inactive/disabled filtering, role-hint mismatch, selection, session expiry, unavailable discovery and QA-disabled behavior; verification and independent review evidence record actual outcomes and gaps. |

