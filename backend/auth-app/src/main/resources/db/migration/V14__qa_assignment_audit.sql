alter table td_business_audit_events
  add column prior_assignee_id uuid,
  add column new_assignee_id uuid;

-- Preserve the complete set of event shapes installed by earlier migrations.
do $$
declare
  previous_shape text;
  previous_events text;
begin
  select pg_get_expr(conbin, conrelid) into strict previous_events
    from pg_constraint
   where conrelid = 'td_business_audit_events'::regclass
     and conname = 'td_business_audit_events_event_code_check';
  alter table td_business_audit_events drop constraint td_business_audit_events_event_code_check;
  execute format($events$
    alter table td_business_audit_events add constraint td_business_audit_events_event_code_check
      check ((%s) or event_code in ('REPORT_ASSIGNED','REPORT_REASSIGNED','REPORT_ASSIGNMENT_CLEARED'))
  $events$, previous_events);
  select pg_get_expr(conbin, conrelid) into strict previous_shape
    from pg_constraint
   where conrelid = 'td_business_audit_events'::regclass
     and conname = 'td_business_audit_events_shape_check';

  alter table td_business_audit_events drop constraint td_business_audit_events_shape_check;
  execute format($check$
    alter table td_business_audit_events add constraint td_business_audit_events_shape_check check (
      ((%s) and prior_assignee_id is null and new_assignee_id is null) or
      (object_type = 'REPORT' and project_id is not null and report_id is not null and draft_id is null and object_id = report_id
       and prior_status is null and new_status is null and resolution_revision is null
       and prior_version is not null and new_version is not null
       and prior_version > 0 and new_version = prior_version + 1
       and (
         (event_code = 'REPORT_ASSIGNED' and prior_assignee_id is null and new_assignee_id is not null) or
         (event_code = 'REPORT_REASSIGNED' and prior_assignee_id is not null and new_assignee_id is not null
          and prior_assignee_id <> new_assignee_id) or
         (event_code = 'REPORT_ASSIGNMENT_CLEARED' and prior_assignee_id is not null and new_assignee_id is null)
       ))
    )
  $check$, previous_shape);
end
$$;
