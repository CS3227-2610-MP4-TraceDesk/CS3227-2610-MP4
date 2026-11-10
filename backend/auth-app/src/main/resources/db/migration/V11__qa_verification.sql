create table td_qa_verifications (
  resolution_id uuid primary key references td_report_resolutions(id) on delete cascade,
  checks jsonb not null check (jsonb_typeof(checks) = 'array' and jsonb_array_length(checks) <= 30),
  notes text not null check (octet_length(notes) <= 8000),
  decision varchar(12) not null check (decision in ('SAVE','CLOSE','SEND_BACK')),
  reason text not null check (octet_length(reason) <= 8000),
  check ((decision = 'SEND_BACK' and td_report_has_nonblank(reason)) or
         (decision <> 'SEND_BACK' and reason = ''))
);

alter table td_business_audit_events drop constraint td_business_audit_events_shape_check;
alter table td_business_audit_events add constraint td_business_audit_events_shape_check check (
  (event_code = 'REPORT_CREATED' and object_type = 'REPORT' and report_id is not null and draft_id is null and
    object_id = report_id and
    prior_status is null and new_status = 'OPEN' and prior_version is null and new_version = 1 and
    resolution_revision is null) or
  (event_code = 'REPORT_EDITED' and object_type = 'REPORT' and report_id is not null and draft_id is null and
    object_id = report_id and
    prior_status is null and new_status is null and prior_version > 0 and
    new_version = prior_version + 1 and resolution_revision is null) or
  (event_code = 'DRAFT_CREATED' and object_type = 'DRAFT' and draft_id is not null and report_id is null and
    object_id = draft_id and
    prior_status is null and new_status is null and prior_version is null and new_version = 1 and
    resolution_revision is null) or
  (event_code = 'DRAFT_EDITED' and object_type = 'DRAFT' and draft_id is not null and report_id is null and
    object_id = draft_id and
    prior_status is null and new_status is null and prior_version > 0 and
    new_version = prior_version + 1 and resolution_revision is null) or
  (event_code = 'DRAFT_DELETED' and object_type = 'DRAFT' and draft_id is not null and report_id is null and
    object_id = draft_id and
    prior_status is null and new_status is null and prior_version > 0 and new_version = prior_version and
    resolution_revision is null) or
  (event_code = 'DRAFT_SUBMITTED' and object_type = 'DRAFT' and draft_id is not null and report_id is not null and
    object_id = draft_id and
    prior_status is null and new_status = 'OPEN' and prior_version > 0 and
    new_version = prior_version + 1 and resolution_revision is null) or
  (event_code = 'REPORT_INVESTIGATION_STARTED' and object_type = 'REPORT' and report_id is not null and draft_id is null and
    object_id = report_id and
    prior_status = 'OPEN' and new_status = 'IN_PROGRESS' and prior_version > 0 and
    new_version = prior_version + 1 and resolution_revision is null) or
  (event_code = 'INVESTIGATION_NOTE_CREATED' and object_type = 'NOTE' and report_id is not null and draft_id is null and
    prior_status is null and new_status is null and prior_version is null and new_version = 1 and
    resolution_revision is null) or
  (event_code = 'INVESTIGATION_NOTE_UPDATED' and object_type = 'NOTE' and report_id is not null and draft_id is null and
    prior_status is null and new_status is null and prior_version > 0 and
    new_version = prior_version + 1 and resolution_revision is null) or
  (event_code = 'INVESTIGATION_NOTE_DELETED' and object_type = 'NOTE' and report_id is not null and draft_id is null and
    prior_status is null and new_status is null and prior_version > 0 and new_version is null and
    resolution_revision is null) or
  (event_code = 'REPORT_RESOLUTION_PROPOSED' and object_type = 'RESOLUTION' and report_id is not null and draft_id is null and
    prior_status = 'IN_PROGRESS' and new_status = 'READY_FOR_QA' and prior_version > 0 and
    new_version = prior_version + 1 and resolution_revision > 0)
 or
  (event_code in ('QA_VERIFICATION_SAVED','QA_VERIFICATION_CLOSED','QA_VERIFICATION_SENT_BACK')
    and object_type = 'RESOLUTION' and report_id is not null and draft_id is null
    and prior_status = 'READY_FOR_QA' and prior_version > 0
    and new_version = prior_version + 1 and resolution_revision > 0
    and new_status = case event_code
      when 'QA_VERIFICATION_SAVED' then 'READY_FOR_QA'
      when 'QA_VERIFICATION_CLOSED' then 'CLOSED'
      when 'QA_VERIFICATION_SENT_BACK' then 'IN_PROGRESS'
    end)
);
