create table td_users (
 id uuid primary key, login_normalized varchar(254) not null unique,
 password_hash varchar(255) not null, enabled boolean not null default true,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table td_projects (
 id uuid primary key, name varchar(480) not null, enabled boolean not null default true,
 created_at timestamptz not null default now(), check (length(name) between 1 and 120 and octet_length(name) <= 480 and btrim(name) <> '')
);
create table td_project_memberships (
 project_id uuid not null references td_projects(id) on delete cascade,
 user_id uuid not null references td_users(id) on delete cascade,
 role varchar(16) not null check (role in ('REPORTER','DEVELOPER','QA_REVIEWER')),
 state varchar(8) not null check (state in ('ACTIVE','DISABLED')),
 created_at timestamptz not null default now(), primary key(project_id,user_id)
);
create table td_security_events (
 id bigserial primary key, occurred_at timestamptz not null default now(),
 event_code varchar(40) not null, request_id uuid not null, route_template varchar(80) not null,
 outcome_status smallint not null, actor_id uuid, project_id uuid,
 check (event_code in ('LOGIN_DENIED','LOGIN_INVALID_REQUEST','CSRF_INVALID','UNAUTHENTICATED','PROJECT_DENIED','PROJECT_INVALID_ID')),
 check (route_template in ('/api/session/login','/api/session/logout','/api/projects/{projectId}'))
);
