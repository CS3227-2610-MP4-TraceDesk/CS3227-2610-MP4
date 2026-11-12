create index td_business_audit_report_activity_idx
  on td_business_audit_events(report_id, occurred_at, id);

alter table td_security_events drop constraint if exists td_security_events_route_template_check;
alter table td_security_events add constraint td_security_events_route_template_check check (route_template in (
  '/api/session','/api/session/login','/api/session/logout','/api/session/register','/api/workspaces',
  '/api/projects','/api/projects/{projectId}','/api/projects/{projectId}/join',
  '/api/projects/{projectId}/reports','/api/projects/{projectId}/reports/{reportId}',
  '/api/projects/{projectId}/reports/{reportId}/activity',
  '/api/projects/{projectId}/reporter-context','/api/projects/{projectId}/drafts',
  '/api/projects/{projectId}/drafts/{draftId}','/api/projects/{projectId}/drafts/{draftId}/submit',
  '/api/projects/{projectId}/developer-context','/api/projects/{projectId}/reports/{reportId}/investigation/start',
  '/api/projects/{projectId}/reports/{reportId}/notes','/api/projects/{projectId}/reports/{reportId}/notes/{noteId}',
  '/api/projects/{projectId}/reports/{reportId}/resolutions'
));
