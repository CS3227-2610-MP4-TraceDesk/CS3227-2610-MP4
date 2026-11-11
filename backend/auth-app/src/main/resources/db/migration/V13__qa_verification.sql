create table td_qa_verifications (
  resolution_id uuid primary key references td_report_resolutions(id) on delete cascade,
  checks jsonb not null check (jsonb_typeof(checks) = 'array' and jsonb_array_length(checks) <= 30),
  notes text not null check (octet_length(notes) <= 8000),
  decision varchar(12) not null check (decision in ('SAVE','CLOSE','SEND_BACK')),
  reason text not null check (octet_length(reason) <= 8000),
  check ((decision = 'SEND_BACK' and td_report_has_nonblank(reason)) or
         (decision <> 'SEND_BACK' and reason = ''))
);

-- Extend installed audit rules without replacing registration or other event shapes.
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
      check ((%s) or event_code in ('QA_VERIFICATION_SAVED','QA_VERIFICATION_CLOSED','QA_VERIFICATION_SENT_BACK'))
  $events$, previous_events);

  select pg_get_expr(conbin, conrelid) into strict previous_shape
    from pg_constraint
   where conrelid = 'td_business_audit_events'::regclass
     and conname = 'td_business_audit_events_shape_check';
  alter table td_business_audit_events drop constraint td_business_audit_events_shape_check;
  execute format($shape$
    alter table td_business_audit_events add constraint td_business_audit_events_shape_check check (
      (%s) or
      (event_code in ('QA_VERIFICATION_SAVED','QA_VERIFICATION_CLOSED','QA_VERIFICATION_SENT_BACK')
        and object_type = 'RESOLUTION' and project_id is not null and report_id is not null and draft_id is null
        and prior_status = 'READY_FOR_QA' and prior_version > 0
        and new_version = prior_version + 1 and resolution_revision > 0
        and new_status = case event_code
          when 'QA_VERIFICATION_SAVED' then 'READY_FOR_QA'
          when 'QA_VERIFICATION_CLOSED' then 'CLOSED'
          when 'QA_VERIFICATION_SENT_BACK' then 'IN_PROGRESS'
        end)
    )
  $shape$, previous_shape);
end
$$;
