# QA workspace continuity

Issue #67. The authenticated workspace restores a bug's local review state during navigation.

- **TD-QAW-01** Cache the selected tab and unsaved verification inputs in memory by report under the account-and-project-keyed workspace. Clear them when leaving that workspace or when access is denied. Keep each draft attached to its source report version and resolution.
- **TD-QAW-02** Fetch the latest verification on reopening the bug. Preserve a dirty draft for review when a version changes and disable writes and suggestion application until the reviewer reloads. A successful decision clears that bug's draft.
- **TD-QAW-03** The bug overview links to the original report, available logs, developer resolutions and verification. Select a destination from the keyboard and move focus to that resource. Preserve the version-bound AI panel lifecycle in TD-QAI-03.
