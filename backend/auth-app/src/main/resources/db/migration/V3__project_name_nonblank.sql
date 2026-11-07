alter table td_projects
    add constraint td_projects_name_nonblank
    check (name !~ '^[[:space:]]*$');
