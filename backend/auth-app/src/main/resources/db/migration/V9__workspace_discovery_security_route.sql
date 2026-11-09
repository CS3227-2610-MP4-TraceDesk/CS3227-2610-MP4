alter table td_security_events drop constraint td_security_events_route_template_check;
alter table td_security_events add constraint td_security_events_route_template_check check (route_template in (
  '/api/session/login','/api/session/logout','/api/workspaces','/api/projects/{projectId}',
  '/api/projects/{projectId}/reports','/api/projects/{projectId}/reports/{reportId}',
  '/api/projects/{projectId}/reporter-context','/api/projects/{projectId}/drafts',
  '/api/projects/{projectId}/drafts/{draftId}','/api/projects/{projectId}/drafts/{draftId}/submit',
  '/api/projects/{projectId}/developer-context',
  '/api/projects/{projectId}/reports/{reportId}/investigation/start',
  '/api/projects/{projectId}/reports/{reportId}/notes',
  '/api/projects/{projectId}/reports/{reportId}/notes/{noteId}'
));
