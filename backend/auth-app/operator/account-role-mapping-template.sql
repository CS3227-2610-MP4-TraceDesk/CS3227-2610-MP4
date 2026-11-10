-- Offline operator template. Use only against the approved isolated migration
-- target after the authorized data owner has reviewed legacy roles and project
-- UUIDs. This file contains no mappings and no account content.
begin;

create temporary table td_explicit_account_role_mapping (
  user_id uuid primary key,
  account_role varchar(16) not null
) on commit drop;

-- Add one explicit INSERT per reviewed UUID, for example:
-- insert into td_explicit_account_role_mapping(user_id,account_role)
-- values ('<reviewed-user-uuid>','DEVELOPER');

do $$
declare pending_count bigint; supplied_count bigint; mismatch_count bigint;
begin
  select count(*) into pending_count from td_account_role_migration_pending;
  select count(*) into supplied_count from td_explicit_account_role_mapping;
  select count(*) into mismatch_count
    from td_account_role_migration_pending p
    full outer join td_explicit_account_role_mapping m using(user_id)
    where p.user_id is null or m.user_id is null;
  if pending_count <> supplied_count or mismatch_count <> 0 then
    raise exception 'Account-role mapping is incomplete or contains extra accounts';
  end if;
  if exists(select 1 from td_explicit_account_role_mapping
            where account_role not in ('REPORTER','DEVELOPER','QA_REVIEWER')) then
    raise exception 'Account-role mapping contains an unsupported role';
  end if;
  if exists(select 1 from td_users u join td_explicit_account_role_mapping m on m.user_id=u.id
            where u.account_role is not null) then
    raise exception 'Mapping would overwrite an account without a pending row';
  end if;
end $$;

update td_users u set account_role=m.account_role
from td_explicit_account_role_mapping m
join td_account_role_migration_pending p using(user_id)
where u.id=m.user_id and u.account_role is null;

delete from spring_session_attributes
where session_primary_id in (
  select s.primary_id from spring_session s
  join td_explicit_account_role_mapping m on m.user_id::text=s.principal_name
);
delete from spring_session s using td_explicit_account_role_mapping m
where m.user_id::text=s.principal_name;
delete from td_account_role_migration_pending p using td_explicit_account_role_mapping m
where p.user_id=m.user_id;

commit;
