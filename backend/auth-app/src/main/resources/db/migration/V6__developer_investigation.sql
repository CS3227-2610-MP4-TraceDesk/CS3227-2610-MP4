alter table td_security_events drop constraint td_security_events_event_code_check;
alter table td_security_events drop constraint td_security_events_route_template_check;
alter table td_security_events add constraint td_security_events_event_code_check check (event_code in (
  'LOGIN_DENIED','LOGIN_INVALID_REQUEST','CSRF_INVALID','UNAUTHENTICATED','PROJECT_DENIED','PROJECT_INVALID_ID',
  'REPORT_DENIED','REPORT_INVALID_REQUEST','REPORT_CURSOR_INVALID',
  'DEVELOPER_DENIED','DEVELOPER_INVALID_REQUEST','DEVELOPER_CURSOR_INVALID'
));
alter table td_security_events add constraint td_security_events_route_template_check check (route_template in (
  '/api/session/login','/api/session/logout','/api/projects/{projectId}',
  '/api/projects/{projectId}/reports','/api/projects/{projectId}/reports/{reportId}',
  '/api/projects/{projectId}/reporter-context','/api/projects/{projectId}/drafts',
  '/api/projects/{projectId}/drafts/{draftId}','/api/projects/{projectId}/drafts/{draftId}/submit',
  '/api/projects/{projectId}/developer-context',
  '/api/projects/{projectId}/reports/{reportId}/investigation/start',
  '/api/projects/{projectId}/reports/{reportId}/notes',
  '/api/projects/{projectId}/reports/{reportId}/notes/{noteId}'
));

alter table td_reports add constraint td_reports_project_id_id_unique unique (project_id, id);

create table td_investigation_notes (
  id uuid primary key,
  project_id uuid not null references td_projects(id),
  report_id uuid not null,
  author_user_id uuid not null references td_users(id),
  last_edited_by_user_id uuid references td_users(id),
  body varchar(8000) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  version integer not null default 1,
  constraint td_investigation_notes_report_project_fk foreign key (project_id, report_id)
    references td_reports(project_id, id) on delete cascade,
  constraint td_investigation_notes_version_check check (version > 0),
  constraint td_investigation_notes_time_check check (updated_at >= created_at),
  constraint td_investigation_notes_body_check check (
    td_report_has_nonblank(body) and
    replace(replace(replace(body, chr(9), ''), chr(10), ''), chr(13), '') !~ '[[:cntrl:]]'
  )
);
create index td_investigation_notes_list_idx
  on td_investigation_notes(project_id, report_id, created_at, id);

create table td_investigation_note_idempotency (
  actor_id uuid not null references td_users(id),
  project_id uuid not null references td_projects(id),
  operation varchar(24) not null check (operation = 'NOTE_CREATE'),
  idempotency_key uuid not null,
  body_sha256 bytea not null check (octet_length(body_sha256) = 32),
  report_id uuid not null,
  note_id uuid not null,
  created_at timestamptz not null,
  expires_at timestamptz not null,
  primary key (actor_id, project_id, operation, idempotency_key),
  constraint td_note_idempotency_report_project_fk foreign key (project_id, report_id)
    references td_reports(project_id, id) on delete cascade,
  constraint td_note_idempotency_expiry_check check (expires_at > created_at)
);
create index td_investigation_note_idempotency_expiry_idx
  on td_investigation_note_idempotency(expires_at);

alter table td_business_audit_events drop constraint td_business_audit_events_event_code_check;
alter table td_business_audit_events alter column new_version drop not null;
alter table td_business_audit_events add column object_type varchar(12) not null default 'REPORT';
alter table td_business_audit_events add column object_id uuid;
alter table td_business_audit_events add column prior_status varchar(20);
alter table td_business_audit_events add column resolution_revision integer;
update td_business_audit_events
  set object_type = case when event_code like 'DRAFT_%' then 'DRAFT' else 'REPORT' end,
      object_id = case when event_code like 'DRAFT_%' then draft_id else report_id end
  where object_id is null;
alter table td_business_audit_events alter column object_id set not null;
alter table td_business_audit_events add constraint td_business_audit_events_shape_check check (
  (event_code = 'REPORT_CREATED' and object_type = 'REPORT' and object_id = report_id and
    prior_status is null and new_status = 'OPEN' and prior_version is null and new_version = 1 and
    resolution_revision is null) or
  (event_code = 'REPORT_EDITED' and object_type = 'REPORT' and object_id = report_id and
    prior_status is null and new_status is null and prior_version > 0 and
    new_version = prior_version + 1 and resolution_revision is null) or
  (event_code = 'DRAFT_CREATED' and object_type = 'DRAFT' and object_id = draft_id and report_id is null and
    prior_status is null and new_status is null and prior_version is null and new_version = 1 and
    resolution_revision is null) or
  (event_code = 'DRAFT_EDITED' and object_type = 'DRAFT' and object_id = draft_id and report_id is null and
    prior_status is null and new_status is null and prior_version > 0 and
    new_version = prior_version + 1 and resolution_revision is null) or
  (event_code = 'DRAFT_DELETED' and object_type = 'DRAFT' and object_id = draft_id and report_id is null and
    prior_status is null and new_status is null and prior_version > 0 and new_version = prior_version and
    resolution_revision is null) or
  (event_code = 'DRAFT_SUBMITTED' and object_type = 'DRAFT' and object_id = draft_id and report_id is not null and
    prior_status is null and new_status = 'OPEN' and prior_version > 0 and
    new_version = prior_version + 1 and resolution_revision is null) or
  (event_code = 'REPORT_INVESTIGATION_STARTED' and object_type = 'REPORT' and object_id = report_id and
    prior_status = 'OPEN' and new_status = 'IN_PROGRESS' and prior_version > 0 and
    new_version = prior_version + 1 and resolution_revision is null) or
  (event_code = 'INVESTIGATION_NOTE_CREATED' and object_type = 'NOTE' and
    prior_status is null and new_status is null and prior_version is null and new_version = 1 and
    resolution_revision is null) or
  (event_code = 'INVESTIGATION_NOTE_UPDATED' and object_type = 'NOTE' and
    prior_status is null and new_status is null and prior_version > 0 and
    new_version = prior_version + 1 and resolution_revision is null) or
  (event_code = 'INVESTIGATION_NOTE_DELETED' and object_type = 'NOTE' and
    prior_status is null and new_status is null and prior_version > 0 and new_version is null and
    resolution_revision is null) or
  (event_code = 'REPORT_RESOLUTION_PROPOSED' and object_type = 'RESOLUTION' and
    prior_status = 'IN_PROGRESS' and new_status = 'READY_FOR_QA' and prior_version > 0 and
    new_version = prior_version + 1 and resolution_revision > 0)
);
