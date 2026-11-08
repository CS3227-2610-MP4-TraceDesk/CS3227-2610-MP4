-- Resource units are normalized token ceilings, never currency. No prompt or output data.
create table td_ai_control (
 singleton boolean primary key default true check (singleton),
 policy_revision bigint not null default 0 check(policy_revision>=0),
 metadata_revision bigint not null default 0 check(metadata_revision>=0),
 funding_revision bigint not null default 0 check(funding_revision>=0),
 policy_fingerprint varchar(128), model varchar(128),
 -- Canonical Instant text preserves nanoseconds for immutable metadata identity.
 metadata_valid_until varchar(40),
 circuit_revision bigint not null default 1 check(circuit_revision>0),
 failures integer not null default 0 check(failures between 0 and 3),
 open_until timestamptz, probe_active boolean not null default false
);
insert into td_ai_control(singleton) values(true);
create table td_ai_daily_units (
 window_day date not null, scope_id uuid not null,
 reserved_units bigint not null default 0 check(reserved_units>=0),
 charged_units bigint not null default 0 check(charged_units>=0),
 primary key(window_day,scope_id)
);
create table td_ai_tickets (
 id uuid primary key, actor_id uuid not null references td_users(id),
 project_id uuid not null references td_projects(id),
 capability varchar(24) not null check(capability in ('REPORT_IMPROVEMENT','INVESTIGATION','QA_PLANNING')),
 window_day date not null, model varchar(128) not null check(model ~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$'),
 metadata_revision bigint not null check(metadata_revision>0), funding_revision bigint not null check(funding_revision>0),
 policy_revision bigint not null check(policy_revision>0), circuit_revision bigint not null check(circuit_revision>0),
 probe boolean not null, reserved_units bigint not null check(reserved_units between 1 and 9200),
 revision bigint not null default 1 check(revision between 1 and 3),
 state varchar(12) not null check(state in ('RESERVED','DISPATCHED','TERMINAL')),
 created_at timestamptz not null, expires_at timestamptz not null check(expires_at>created_at),
 outcome varchar(12) check(outcome in ('SUCCESS','FAILURE','UNKNOWN','CANCELLED','EXPIRED')),
 actual_units bigint, charged_units bigint check(charged_units>=0 and charged_units<=reserved_units),
 settled_at timestamptz,
 check((state='RESERVED' and revision=1) or (state='DISPATCHED' and revision=2) or (state='TERMINAL' and revision in (2,3))),
 check(state='TERMINAL' or actual_units is null),
 check((state='TERMINAL')=(outcome is not null)),
 check((state='TERMINAL')=(settled_at is not null)),
 check((state='TERMINAL')=(charged_units is not null))
);
create index td_ai_tickets_active on td_ai_tickets(state,expires_at) where state<>'TERMINAL';
create index td_ai_tickets_actor_active on td_ai_tickets(actor_id) where state<>'TERMINAL';
create table td_ai_accounting_events (
 ticket_id uuid not null references td_ai_tickets(id), revision bigint not null check(revision between 1 and 3),
 event_code varchar(12) not null check(event_code in ('RESERVED','DISPATCHED','SUCCESS','FAILURE','UNKNOWN','CANCELLED','EXPIRED')),
 occurred_at timestamptz not null, charged_units bigint not null check(charged_units between 0 and 1000000000000),
 primary key(ticket_id,revision)
);
