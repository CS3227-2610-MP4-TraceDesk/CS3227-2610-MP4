create table td_qa_comments (
  sequence_id bigint generated always as identity primary key,
  id uuid not null unique,
  project_id uuid not null references td_projects(id) on delete cascade,
  report_id uuid not null references td_reports(id) on delete cascade,
  author_id uuid not null references td_users(id),
  body text not null check (char_length(body) <= 2000 and td_report_has_nonblank(body)),
  created_at timestamptz not null
);
create index td_qa_comments_report_idx on td_qa_comments(project_id, report_id, sequence_id desc);

do $$
declare
  previous_events text;
  previous_shape text;
begin
  select pg_get_expr(conbin, conrelid) into strict previous_events
    from pg_constraint where conrelid='td_business_audit_events'::regclass
      and conname='td_business_audit_events_event_code_check';
  alter table td_business_audit_events drop constraint td_business_audit_events_event_code_check;
  execute format('alter table td_business_audit_events add constraint td_business_audit_events_event_code_check check ((%s) or %s)',
      previous_events, $events$event_code in ('QA_COMMENT_POSTED')$events$);

  select pg_get_expr(conbin, conrelid) into strict previous_shape
    from pg_constraint where conrelid='td_business_audit_events'::regclass
      and conname='td_business_audit_events_shape_check';
  alter table td_business_audit_events drop constraint td_business_audit_events_shape_check;
  execute format('alter table td_business_audit_events add constraint td_business_audit_events_shape_check check ((%s) or %s)',
      previous_shape, $shape$(event_code in ('QA_COMMENT_POSTED') and object_type='QA_COMMENT' and project_id is not null and report_id is not null
        and prior_version is null and new_version is null
        and draft_id is null and prior_status is null and new_status is null and resolution_revision is null
        and prior_assignee_id is null and new_assignee_id is null)$shape$);
end
$$;
