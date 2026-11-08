alter table td_report_idempotency drop constraint td_report_idempotency_operation_check;
alter table td_report_idempotency add constraint td_report_idempotency_operation_check
  check (operation in ('REPORT_CREATE','DRAFT_CREATE','DRAFT_SUBMIT'));
alter table td_report_idempotency alter column report_id drop not null;
alter table td_report_idempotency add column draft_id uuid;

create table td_report_drafts (
  id uuid primary key,
  project_id uuid not null references td_projects(id),
  owner_user_id uuid not null references td_users(id),
  title varchar(640) not null default '',
  description varchar(40000) not null default '',
  environment varchar(16000) not null default '',
  reproduction_steps varchar(32000) not null default '',
  expected_behavior varchar(16000) not null default '',
  actual_behavior varchar(16000) not null default '',
  text_logs varchar(64000) not null default '',
  created_at timestamptz not null,
  updated_at timestamptz not null,
  expires_at timestamptz not null,
  version integer not null default 1,
  submitted_report_id uuid unique,
  constraint td_report_drafts_version_check check (version > 0),
  constraint td_report_drafts_expiry_check check (expires_at = created_at + interval '30 days'),
  constraint td_report_drafts_field_bounds check (
    char_length(title) <= 160 and octet_length(title) <= 640 and replace(replace(replace(title, chr(9), ''), chr(10), ''), chr(13), '') !~ '[[:cntrl:]]' and
    char_length(description) <= 10000 and octet_length(description) <= 40000 and replace(replace(replace(description, chr(9), ''), chr(10), ''), chr(13), '') !~ '[[:cntrl:]]' and
    char_length(environment) <= 4000 and octet_length(environment) <= 16000 and replace(replace(replace(environment, chr(9), ''), chr(10), ''), chr(13), '') !~ '[[:cntrl:]]' and
    char_length(reproduction_steps) <= 8000 and octet_length(reproduction_steps) <= 32000 and replace(replace(replace(reproduction_steps, chr(9), ''), chr(10), ''), chr(13), '') !~ '[[:cntrl:]]' and
    char_length(expected_behavior) <= 4000 and octet_length(expected_behavior) <= 16000 and replace(replace(replace(expected_behavior, chr(9), ''), chr(10), ''), chr(13), '') !~ '[[:cntrl:]]' and
    char_length(actual_behavior) <= 4000 and octet_length(actual_behavior) <= 16000 and replace(replace(replace(actual_behavior, chr(9), ''), chr(10), ''), chr(13), '') !~ '[[:cntrl:]]' and
    char_length(text_logs) <= 16000 and octet_length(text_logs) <= 64000 and replace(replace(replace(text_logs, chr(9), ''), chr(10), ''), chr(13), '') !~ '[[:cntrl:]]' and
    octet_length(title)+octet_length(description)+octet_length(environment)+octet_length(reproduction_steps)+octet_length(expected_behavior)+octet_length(actual_behavior)+octet_length(text_logs) <= 184640
  )
);
alter table td_reports add constraint td_reports_owner_project_unique unique(id,project_id,reporter_user_id);
alter table td_report_drafts add constraint td_report_drafts_submitted_report_fk
  foreign key(submitted_report_id,project_id,owner_user_id) references td_reports(id,project_id,reporter_user_id);
create index td_report_drafts_list_idx on td_report_drafts(project_id, owner_user_id, updated_at desc, id desc);
create index td_report_drafts_expiry_idx on td_report_drafts(expires_at);
alter table td_report_idempotency add constraint td_report_idempotency_draft_fk foreign key(draft_id) references td_report_drafts(id) on delete set null;
alter table td_report_idempotency add constraint td_report_idempotency_target_check check (
  (operation='REPORT_CREATE' and report_id is not null and draft_id is null) or
  (operation='DRAFT_CREATE' and report_id is null) or
  (operation='DRAFT_SUBMIT' and report_id is not null)
);

alter table td_business_audit_events drop constraint td_business_audit_events_event_code_check;
alter table td_business_audit_events add constraint td_business_audit_events_event_code_check
  check (event_code in ('REPORT_CREATED','REPORT_EDITED','DRAFT_CREATED','DRAFT_EDITED','DRAFT_DELETED','DRAFT_SUBMITTED'));
alter table td_business_audit_events drop constraint td_business_audit_events_new_status_check;
alter table td_business_audit_events drop constraint td_business_audit_events_new_version_check;
alter table td_business_audit_events alter column report_id drop not null;
alter table td_business_audit_events alter column new_status drop not null;
alter table td_business_audit_events add column draft_id uuid;
alter table td_business_audit_events add column prior_version integer;
alter table td_business_audit_events add constraint td_business_audit_events_version_check check (new_version > 0 and (prior_version is null or prior_version > 0));
alter table td_business_audit_events add constraint td_business_audit_events_target_check check (report_id is not null or draft_id is not null);

alter table td_security_events drop constraint td_security_events_route_template_check;
alter table td_security_events add constraint td_security_events_route_template_check check (route_template in (
  '/api/session/login','/api/session/logout','/api/projects/{projectId}',
  '/api/projects/{projectId}/reports','/api/projects/{projectId}/reports/{reportId}',
  '/api/projects/{projectId}/reporter-context','/api/projects/{projectId}/drafts',
  '/api/projects/{projectId}/drafts/{draftId}','/api/projects/{projectId}/drafts/{draftId}/submit'
));
