alter table td_business_audit_events drop constraint td_business_audit_events_event_code_check;
alter table td_business_audit_events add constraint td_business_audit_events_event_code_check check (event_code in (
  'REPORT_CREATED','REPORT_EDITED','DRAFT_CREATED','DRAFT_EDITED','DRAFT_DELETED','DRAFT_SUBMITTED',
  'REPORT_INVESTIGATION_STARTED','INVESTIGATION_NOTE_CREATED','INVESTIGATION_NOTE_UPDATED',
  'INVESTIGATION_NOTE_DELETED','REPORT_RESOLUTION_PROPOSED','ACCOUNT_REGISTERED'));
alter table td_business_audit_events alter column project_id drop not null;
alter table td_business_audit_events drop constraint td_business_audit_events_shape_check;
alter table td_business_audit_events add constraint td_business_audit_events_shape_check check (
  (event_code='ACCOUNT_REGISTERED' and project_id is null and report_id is null and draft_id is null and
   object_type='ACCOUNT' and object_id=actor_id and prior_status is null and
   new_status in ('REPORTER','DEVELOPER','QA_REVIEWER') and prior_version is null and new_version is null and
   resolution_revision is null and outcome='SUCCESS') or
  (event_code = 'REPORT_CREATED' and object_type = 'REPORT' and project_id is not null and report_id is not null and draft_id is null and
    object_id = report_id and prior_status is null and new_status = 'OPEN' and prior_version is null and new_version = 1 and resolution_revision is null) or
  (event_code = 'REPORT_EDITED' and object_type = 'REPORT' and project_id is not null and report_id is not null and draft_id is null and
    object_id = report_id and prior_status is null and new_status is null and prior_version > 0 and new_version = prior_version + 1 and resolution_revision is null) or
  (event_code = 'DRAFT_CREATED' and object_type = 'DRAFT' and project_id is not null and draft_id is not null and report_id is null and
    object_id = draft_id and prior_status is null and new_status is null and prior_version is null and new_version = 1 and resolution_revision is null) or
  (event_code = 'DRAFT_EDITED' and object_type = 'DRAFT' and project_id is not null and draft_id is not null and report_id is null and
    object_id = draft_id and prior_status is null and new_status is null and prior_version > 0 and new_version = prior_version + 1 and resolution_revision is null) or
  (event_code = 'DRAFT_DELETED' and object_type = 'DRAFT' and project_id is not null and draft_id is not null and report_id is null and
    object_id = draft_id and prior_status is null and new_status is null and prior_version > 0 and new_version is null and resolution_revision is null) or
  (event_code = 'DRAFT_SUBMITTED' and object_type = 'DRAFT' and project_id is not null and draft_id is not null and report_id is not null and
    object_id = draft_id and prior_status is null and new_status = 'OPEN' and prior_version > 0 and new_version = prior_version + 1 and resolution_revision is null) or
  (event_code = 'REPORT_INVESTIGATION_STARTED' and object_type = 'REPORT' and project_id is not null and report_id is not null and draft_id is null and
    object_id = report_id and prior_status = 'OPEN' and new_status = 'IN_PROGRESS' and prior_version > 0 and new_version = prior_version + 1 and resolution_revision is null) or
  (event_code = 'INVESTIGATION_NOTE_CREATED' and object_type = 'NOTE' and project_id is not null and report_id is not null and draft_id is null and
    prior_status is null and new_status is null and prior_version is null and new_version = 1 and resolution_revision is null) or
  (event_code = 'INVESTIGATION_NOTE_UPDATED' and object_type = 'NOTE' and project_id is not null and report_id is not null and draft_id is null and
    prior_status is null and new_status is null and prior_version > 0 and new_version = prior_version + 1 and resolution_revision is null) or
  (event_code = 'INVESTIGATION_NOTE_DELETED' and object_type = 'NOTE' and project_id is not null and report_id is not null and draft_id is null and
    prior_status is null and new_status is null and prior_version > 0 and new_version is null and resolution_revision is null) or
  (event_code = 'REPORT_RESOLUTION_PROPOSED' and object_type = 'RESOLUTION' and project_id is not null and report_id is not null and draft_id is null and
    prior_status = 'IN_PROGRESS' and new_status = 'READY_FOR_QA' and prior_version > 0 and new_version = prior_version + 1 and resolution_revision > 0)
);
alter table td_business_audit_events drop constraint td_business_audit_events_target_check;
alter table td_business_audit_events add constraint td_business_audit_events_target_check
  check (report_id is not null or draft_id is not null or event_code='ACCOUNT_REGISTERED');

alter table td_security_events drop constraint td_security_events_event_code_check;
alter table td_security_events add constraint td_security_events_event_code_check check (event_code in (
  'LOGIN_DENIED','LOGIN_INVALID_REQUEST','CSRF_INVALID','UNAUTHENTICATED','PROJECT_DENIED','PROJECT_INVALID_ID',
  'PROJECT_INVALID_REQUEST','PROJECT_MUTATION_DENIED','REPORT_DENIED','REPORT_INVALID_REQUEST','REPORT_CURSOR_INVALID',
  'DEVELOPER_DENIED','DEVELOPER_INVALID_REQUEST','DEVELOPER_CURSOR_INVALID','REGISTRATION_INVALID_REQUEST',
  'REGISTRATION_DENIED'));
alter table td_security_events drop constraint td_security_events_route_template_check;
alter table td_security_events add constraint td_security_events_route_template_check check (route_template in (
  '/api/session','/api/session/login','/api/session/logout','/api/session/register','/api/workspaces',
  '/api/projects','/api/projects/{projectId}','/api/projects/{projectId}/join',
  '/api/projects/{projectId}/reports','/api/projects/{projectId}/reports/{reportId}',
  '/api/projects/{projectId}/reporter-context','/api/projects/{projectId}/drafts',
  '/api/projects/{projectId}/drafts/{draftId}','/api/projects/{projectId}/drafts/{draftId}/submit',
  '/api/projects/{projectId}/developer-context','/api/projects/{projectId}/reports/{reportId}/investigation/start',
  '/api/projects/{projectId}/reports/{reportId}/notes','/api/projects/{projectId}/reports/{reportId}/notes/{noteId}',
  '/api/projects/{projectId}/reports/{reportId}/resolutions'));
