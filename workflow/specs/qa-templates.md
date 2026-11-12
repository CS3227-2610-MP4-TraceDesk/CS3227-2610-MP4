# QA verification templates

Issue #65. Active QA reviewers in the selected project manage shared test templates. Each has a name (120 characters), optional when-to-use and preconditions (1,000 each), and one to 30 cases with distinct names (300), steps and expected results (1,000 each).

- **TD-QAT-01** Project-scoped list, create, update and delete require active QA membership. Writes use CSRF and optimistic template versions. Persist only validated content and record a metadata-only audit event with each successful write.
- **TD-QAT-02** Explicit application copies case names to a bug's editable checklist draft as pending checks. Copy setup and case guidance into the draft notes within verification limits, preserving existing notes and checks. Reject an application that exceeds the checklist or note limits without changing the draft. Later template edits and deletion cannot alter applied checks.
- **TD-QAT-03** Normal verification save applies its existing authorization and version checks. The editor preserves unsaved text on a recoverable error.
