# Local operator notes

`TRACEDESK_PROJECT_CURSOR_SECRET` is required for application startup. Provide a
random value with at least 32 UTF-8 bytes through the deployment secret manager;
configure the same value on every replica. The application has no fallback
secret. Rotating it invalidates directory cursors already held by clients, who
can restart pagination from the first page. Keep the secret out of source control,
logs, and evidence.

`account-role-mapping-template.sql` is an offline template for the separately
authorized migration operator. It contains no mappings. Populate it only after
the approved data owner has reviewed synthetic-free, authorized migration
evidence and supplied explicit UUID-to-role decisions.
