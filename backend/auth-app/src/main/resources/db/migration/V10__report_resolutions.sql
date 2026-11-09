alter table td_security_events drop constraint td_security_events_route_template_check;
alter table td_security_events add constraint td_security_events_route_template_check check (route_template in (
  '/api/session/login','/api/session/logout','/api/workspaces','/api/projects/{projectId}',
  '/api/projects/{projectId}/reports','/api/projects/{projectId}/reports/{reportId}',
  '/api/projects/{projectId}/reporter-context','/api/projects/{projectId}/drafts',
  '/api/projects/{projectId}/drafts/{draftId}','/api/projects/{projectId}/drafts/{draftId}/submit',
  '/api/projects/{projectId}/developer-context',
  '/api/projects/{projectId}/reports/{reportId}/investigation/start',
  '/api/projects/{projectId}/reports/{reportId}/notes',
  '/api/projects/{projectId}/reports/{reportId}/notes/{noteId}',
  '/api/projects/{projectId}/reports/{reportId}/resolutions'
));

create table td_report_resolutions (
  id uuid primary key,
  project_id uuid not null references td_projects(id),
  report_id uuid not null,
  author_user_id uuid not null references td_users(id),
  revision integer not null,
  root_cause varchar(16000) not null,
  change_summary varchar(16000) not null,
  verification_steps varchar(16000) not null,
  fix_reference varchar(800) not null default '',
  created_at timestamptz not null,
  constraint td_report_resolutions_report_project_fk foreign key (project_id, report_id)
    references td_reports(project_id, id) on delete cascade,
  constraint td_report_resolutions_revision_unique unique (report_id, revision),
  constraint td_report_resolutions_revision_check check (revision between 1 and 50),
  constraint td_report_resolutions_root_cause_check check (
    td_report_has_nonblank(root_cause) and char_length(root_cause) <= 4000 and octet_length(root_cause) <= 16000 and
    replace(replace(replace(root_cause, chr(9), ''), chr(10), ''), chr(13), '') !~ '[[:cntrl:]]'
  ),
  constraint td_report_resolutions_change_summary_check check (
    td_report_has_nonblank(change_summary) and char_length(change_summary) <= 4000 and octet_length(change_summary) <= 16000 and
    replace(replace(replace(change_summary, chr(9), ''), chr(10), ''), chr(13), '') !~ '[[:cntrl:]]'
  ),
  constraint td_report_resolutions_verification_steps_check check (
    td_report_has_nonblank(verification_steps) and char_length(verification_steps) <= 4000 and octet_length(verification_steps) <= 16000 and
    replace(replace(replace(verification_steps, chr(9), ''), chr(10), ''), chr(13), '') !~ '[[:cntrl:]]'
  ),
  constraint td_report_resolutions_fix_reference_check check (
    char_length(fix_reference) <= 200 and octet_length(fix_reference) <= 800 and
    replace(replace(replace(fix_reference, chr(9), ''), chr(10), ''), chr(13), '') !~ '[[:cntrl:]]'
  )
);
create index td_report_resolutions_project_report_idx
  on td_report_resolutions(project_id, report_id, revision);
