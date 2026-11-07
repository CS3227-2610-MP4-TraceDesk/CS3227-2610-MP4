alter table td_security_events drop constraint td_security_events_event_code_check;
alter table td_security_events drop constraint td_security_events_route_template_check;
alter table td_security_events add constraint td_security_events_event_code_check check (event_code in (
  'LOGIN_DENIED','LOGIN_INVALID_REQUEST','CSRF_INVALID','UNAUTHENTICATED','PROJECT_DENIED','PROJECT_INVALID_ID',
  'REPORT_DENIED','REPORT_INVALID_REQUEST','REPORT_CURSOR_INVALID'
));
alter table td_security_events add constraint td_security_events_route_template_check check (route_template in (
  '/api/session/login','/api/session/logout','/api/projects/{projectId}',
  '/api/projects/{projectId}/reports','/api/projects/{projectId}/reports/{reportId}',
  '/api/projects/{projectId}/reporter-context'
));

create function td_report_has_nonblank(value text) returns boolean
language sql immutable parallel safe
as $$ select value !~ U&'^[\0009-\000D\001C-\0020\00A0\1680\2000-\200A\2028\2029\202F\205F\3000]*$' $$;

create table td_reports (
  id uuid primary key,
  project_id uuid not null references td_projects(id),
  reporter_user_id uuid not null references td_users(id),
  title varchar(640) not null,
  description varchar(40000) not null,
  environment varchar(16000) not null,
  reproduction_steps varchar(32000) not null,
  expected_behavior varchar(16000) not null,
  actual_behavior varchar(16000) not null,
  text_logs varchar(64000) not null default '',
  status varchar(20) not null default 'OPEN',
  assigned_developer_id uuid references td_users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version integer not null default 1,
  constraint td_reports_status_check check (status in ('OPEN','IN_PROGRESS','READY_FOR_QA','CLOSED')),
  constraint td_reports_version_check check (version > 0),
  constraint td_reports_field_bounds check (
    char_length(title) between 1 and 160 and octet_length(title) <= 640 and td_report_has_nonblank(title) and replace(replace(replace(title, chr(9), ''), chr(10), ''), chr(13), '') !~ '[[:cntrl:]]' and
    char_length(description) between 1 and 10000 and octet_length(description) <= 40000 and td_report_has_nonblank(description) and replace(replace(replace(description, chr(9), ''), chr(10), ''), chr(13), '') !~ '[[:cntrl:]]' and
    char_length(environment) between 1 and 4000 and octet_length(environment) <= 16000 and td_report_has_nonblank(environment) and replace(replace(replace(environment, chr(9), ''), chr(10), ''), chr(13), '') !~ '[[:cntrl:]]' and
    char_length(reproduction_steps) between 1 and 8000 and octet_length(reproduction_steps) <= 32000 and td_report_has_nonblank(reproduction_steps) and replace(replace(replace(reproduction_steps, chr(9), ''), chr(10), ''), chr(13), '') !~ '[[:cntrl:]]' and
    char_length(expected_behavior) between 1 and 4000 and octet_length(expected_behavior) <= 16000 and td_report_has_nonblank(expected_behavior) and replace(replace(replace(expected_behavior, chr(9), ''), chr(10), ''), chr(13), '') !~ '[[:cntrl:]]' and
    char_length(actual_behavior) between 1 and 4000 and octet_length(actual_behavior) <= 16000 and td_report_has_nonblank(actual_behavior) and replace(replace(replace(actual_behavior, chr(9), ''), chr(10), ''), chr(13), '') !~ '[[:cntrl:]]' and
    char_length(text_logs) <= 16000 and octet_length(text_logs) <= 64000 and replace(replace(replace(text_logs, chr(9), ''), chr(10), ''), chr(13), '') !~ '[[:cntrl:]]'
  )
);
create index td_reports_list_idx on td_reports(project_id, updated_at desc, id desc);
create index td_reports_owner_idx on td_reports(project_id, reporter_user_id, updated_at desc, id desc);
create index td_reports_status_idx on td_reports(project_id, status, updated_at desc, id desc);

create table td_report_idempotency (
  actor_id uuid not null references td_users(id),
  project_id uuid not null references td_projects(id),
  operation varchar(24) not null check (operation = 'REPORT_CREATE'),
  idempotency_key uuid not null,
  body_sha256 bytea not null check (octet_length(body_sha256) = 32),
  report_id uuid not null references td_reports(id),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  primary key(actor_id, project_id, operation, idempotency_key),
  check (expires_at > created_at)
);
create index td_report_idempotency_expiry_idx on td_report_idempotency(expires_at);

create table td_business_audit_events (
  id bigserial primary key,
  occurred_at timestamptz not null default now(),
  event_code varchar(40) not null check (event_code = 'REPORT_CREATED'),
  request_id uuid not null,
  actor_id uuid not null references td_users(id),
  project_id uuid not null references td_projects(id),
  report_id uuid not null references td_reports(id),
  new_status varchar(20) not null check (new_status = 'OPEN'),
  new_version integer not null check (new_version = 1),
  outcome varchar(12) not null check (outcome = 'SUCCESS')
);
