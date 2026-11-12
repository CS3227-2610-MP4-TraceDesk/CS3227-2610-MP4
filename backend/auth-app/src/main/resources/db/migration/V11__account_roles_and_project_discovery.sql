alter table td_users add column account_role varchar(16);
alter table td_users add constraint td_users_account_role_check
  check (account_role is null or account_role in ('REPORTER','DEVELOPER','QA_REVIEWER'));

with membership_roles as (
  select user_id, count(distinct role) as role_count, min(role) as only_role,
         array_agg(distinct role order by role) as legacy_roles
  from td_project_memberships group by user_id
)
update td_users u set account_role = r.only_role
from membership_roles r where r.user_id=u.id and r.role_count=1;

create table td_account_role_migration_pending (
  user_id uuid primary key references td_users(id) on delete cascade,
  reason varchar(32) not null check (reason in ('CONFLICTING_MEMBERSHIP_ROLES','NO_MEMBERSHIP_ROLE')),
  legacy_roles varchar(16)[] not null,
  created_at timestamptz not null default now(),
  check ((reason='NO_MEMBERSHIP_ROLE' and cardinality(legacy_roles)=0) or
         (reason='CONFLICTING_MEMBERSHIP_ROLES' and cardinality(legacy_roles)>1))
);
insert into td_account_role_migration_pending(user_id,reason,legacy_roles)
select u.id, case when r.user_id is null then 'NO_MEMBERSHIP_ROLE' else 'CONFLICTING_MEMBERSHIP_ROLES' end,
       coalesce(r.legacy_roles, array[]::varchar(16)[])
from td_users u left join (
  select user_id, count(distinct role) as role_count,
         array_agg(distinct role order by role) as legacy_roles
  from td_project_memberships group by user_id
) r on r.user_id=u.id
where u.account_role is null;

create table td_project_create_idempotency (
  actor_id uuid not null references td_users(id) on delete cascade,
  idempotency_key uuid not null,
  request_sha256 bytea not null check (octet_length(request_sha256)=32),
  project_id uuid not null references td_projects(id),
  created_at timestamptz not null default now(),
  primary key(actor_id,idempotency_key)
);
create table td_project_business_audit (
  id bigserial primary key,
  event_code varchar(24) not null check (event_code in ('PROJECT_CREATED','PROJECT_JOINED')),
  request_id uuid not null,
  actor_id uuid not null references td_users(id),
  project_id uuid not null references td_projects(id),
  outcome varchar(8) not null check (outcome='SUCCESS'),
  occurred_at timestamptz not null default now()
);

alter table td_security_events drop constraint td_security_events_event_code_check;
alter table td_security_events add constraint td_security_events_event_code_check check (event_code in (
  'LOGIN_DENIED','LOGIN_INVALID_REQUEST','CSRF_INVALID','UNAUTHENTICATED','PROJECT_DENIED','PROJECT_INVALID_ID',
  'PROJECT_INVALID_REQUEST','PROJECT_MUTATION_DENIED','REPORT_DENIED','REPORT_INVALID_REQUEST','REPORT_CURSOR_INVALID',
  'DEVELOPER_DENIED','DEVELOPER_INVALID_REQUEST','DEVELOPER_CURSOR_INVALID'));
alter table td_security_events drop constraint td_security_events_route_template_check;
alter table td_security_events add constraint td_security_events_route_template_check check (route_template in (
  '/api/session','/api/session/login','/api/session/logout','/api/workspaces','/api/projects','/api/projects/{projectId}',
  '/api/projects/{projectId}/join',
  '/api/projects/{projectId}/reports','/api/projects/{projectId}/reports/{reportId}',
  '/api/projects/{projectId}/reporter-context','/api/projects/{projectId}/drafts',
  '/api/projects/{projectId}/drafts/{draftId}','/api/projects/{projectId}/drafts/{draftId}/submit',
  '/api/projects/{projectId}/developer-context',
  '/api/projects/{projectId}/reports/{reportId}/investigation/start',
  '/api/projects/{projectId}/reports/{reportId}/notes',
  '/api/projects/{projectId}/reports/{reportId}/notes/{noteId}',
  '/api/projects/{projectId}/reports/{reportId}/resolutions'));
