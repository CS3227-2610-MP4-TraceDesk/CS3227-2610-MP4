# QA review discussion

Issue #66. Human comments belong to a single bug and are visible only to active QA reviewers in that project. Authors can post comments to a report they can access. Developers and Reporters have no comment access.

- **TD-QAD-01** The server checks fresh project membership, QA role and report scope on every read and write. Writes require CSRF. Author and timestamp are assigned on the server. Persist the comment and metadata-only audit event atomically.
- **TD-QAD-02** Comments contain up to 2,000 Unicode characters of nonblank text and are rendered as plain text in bug activity. Keep unsent text after a recoverable failure. Bound reads to a paginated list.
- **TD-QAD-03** Verify multiple reviewers and reports, cross-project and wrong-role denial, author attribution, and safe rendering. Comments do not change the report lifecycle or enter AI context.
