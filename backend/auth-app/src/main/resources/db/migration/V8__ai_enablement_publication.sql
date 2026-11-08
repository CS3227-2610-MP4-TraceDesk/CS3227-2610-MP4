-- Shared operator sponsorship and metadata-refresh authority. These values are
-- bounded identifiers/timestamps only; keys, prompts and provider payloads stay out.
alter table td_ai_control
    add column approval_fingerprint varchar(64),
    add column approval_valid_until varchar(40),
    add column metadata_available boolean not null default false,
    add column metadata_pending boolean not null default false,
    add column model_revision bigint not null default 0 check(model_revision>=0),
    add column expected_model varchar(128);

-- Old installations begin fail-closed and require an explicit refreshed publication.
update td_ai_control set metadata_available=false where singleton=true;
