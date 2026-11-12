do $$
declare
  previous_routes text;
begin
  select pg_get_expr(conbin, conrelid) into strict previous_routes
    from pg_constraint where conrelid='td_security_events'::regclass
      and conname='td_security_events_route_template_check';
  alter table td_security_events drop constraint td_security_events_route_template_check;
  execute format('alter table td_security_events add constraint td_security_events_route_template_check check ((%s) or route_template=%L)',
      previous_routes, '/api/projects/{projectId}/reports/{reportId}/investigation/suggestions');
end
$$;
