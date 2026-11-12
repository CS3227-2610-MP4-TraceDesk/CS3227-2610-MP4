create table td_qa_templates (
  id uuid primary key,
  project_id uuid not null references td_projects(id) on delete cascade,
  name text not null check (char_length(name) <= 120 and td_report_has_nonblank(name)),
  when_to_use text not null check (char_length(when_to_use) <= 1000),
  preconditions text not null check (char_length(preconditions) <= 1000),
  test_cases jsonb not null check (jsonb_typeof(test_cases)='array' and jsonb_array_length(test_cases) between 1 and 30),
  version integer not null check (version > 0)
);
create index td_qa_templates_project_idx on td_qa_templates(project_id, id);

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
      previous_events, $events$event_code in ('QA_TEMPLATE_CREATED','QA_TEMPLATE_UPDATED','QA_TEMPLATE_DELETED')$events$);

  select pg_get_expr(conbin, conrelid) into strict previous_shape
    from pg_constraint where conrelid='td_business_audit_events'::regclass
      and conname='td_business_audit_events_shape_check';
  alter table td_business_audit_events drop constraint td_business_audit_events_shape_check;
  execute format('alter table td_business_audit_events add constraint td_business_audit_events_shape_check check ((%s) or %s)',
      previous_shape, $shape$(event_code in ('QA_TEMPLATE_CREATED','QA_TEMPLATE_UPDATED','QA_TEMPLATE_DELETED') and object_type='QA_TEMPLATE' and project_id is not null and report_id is null
        and (case event_code when 'QA_TEMPLATE_CREATED' then prior_version is null and new_version=1
          when 'QA_TEMPLATE_UPDATED' then prior_version>0 and new_version=prior_version+1
          else prior_version>0 and new_version is null end) is true
        and draft_id is null and prior_status is null and new_status is null and resolution_revision is null
        and prior_assignee_id is null and new_assignee_id is null)$shape$);
end
$$;
alter table td_business_audit_events drop constraint td_business_audit_events_target_check;
alter table td_business_audit_events add constraint td_business_audit_events_target_check check (
  report_id is not null or draft_id is not null or
  event_code in ('ACCOUNT_REGISTERED','QA_TEMPLATE_CREATED','QA_TEMPLATE_UPDATED','QA_TEMPLATE_DELETED')
);
