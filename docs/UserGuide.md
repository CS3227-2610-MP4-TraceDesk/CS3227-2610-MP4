---
layout: default
title: User Guide
nav_order: 2
---

# TraceDesk User Guide

TraceDesk helps teams report software bugs, investigate them and verify proposed fixes. A project has Reporter, Developer and QA reviewer workflows. What you can see and do depends on your account, project membership, report status and assignment.

## Contents

- [Open the application and sign in](#open-the-application-and-sign-in)
- [Report and track a bug](#report-and-track-a-bug)
- [Use Reporter AI assistance](#use-reporter-ai-assistance)
- [Investigate a bug as a Developer](#investigate-a-bug-as-a-developer)
- [Verify a fix as a QA reviewer](#verify-a-fix-as-a-qa-reviewer)
- [Use QA AI review](#use-qa-ai-review)
- [Use test templates and discussion](#use-test-templates-and-discussion)
- [Appearance, navigation and sign out](#appearance-navigation-and-sign-out)
- [Troubleshooting and current limits](#troubleshooting-and-current-limits)

## Open the application and sign in

A public deployment URL has not been verified in the documentation for this revision. To run TraceDesk locally, follow the [local setup in the repository README](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4#start-tracedesk-locally). The local setup starts PostgreSQL and the frontend, provisions sample accounts, and starts the backend. Its default local run has AI disabled. The frontend uses a self-signed local HTTPS certificate.

1. Open the application and sign in with an account provisioned for the environment.
2. If offered a project choice, select a project where your account has an active membership. Role and project membership determine the workspace.
3. To create an account, select **Create account**, enter an email-shaped login and password, confirm the password and choose the initial role. After successful registration, sign in. A new account does not automatically receive project membership.
4. Local demo shortcuts and sample accounts apply only to a locally provisioned environment; they are not production credentials.

## Report and track a bug

1. In the Reporter workspace, select **New report**.
2. Enter the title, description, environment, steps to reproduce, expected behaviour and actual behaviour. Add text logs if useful.
3. Submit the report, or use **Save draft** if you want to continue later. Draft changes are saved when you select the save action; a saved draft has an expiry.
4. Open a saved draft to continue it. Save changes before selecting **Submit as report**. Open a submitted report from the list to inspect its details and available activity.
5. If editing is available, save your changes. A report that has changed or moved beyond the editable state may require a refresh; do not assume unsaved edits were accepted.

![New report form in a synthetic local workspace](images/reporter-new-report.png)

*The screenshot is from a local synthetic browser test. It is illustrative and does not establish a public deployment.*

## Use Reporter AI assistance

Save the draft or eligible open report first. Open **Review with AI**, read the provider disclosure and request suggestions when AI is enabled for the environment. Review the proposed fields, select the ones you want, and use **Apply selected changes**. This uses the ordinary version-checked edit path; it does not submit the report. The model can make mistakes, so check the result against your own observations. The [Reporter AI details](reporter-ai.md) describe the selected input and limits.

## Investigate a bug as a Developer

1. Open **My work** to see assigned reports, or **All bugs** for reports available in the project. Select a report to inspect its details.
2. For an eligible report assigned to you, select **Start investigation**. Private investigation notes are available to the current assignee; use **Save note** to record one.
3. In **Proposed resolution**, enter the root cause, change summary and verification steps. Add a fix reference if applicable, then select **Send to QA** when the required fields are complete.
4. If QA sends the resolution back, review the reason on the relevant resolution revision and update the investigation or propose another resolution as appropriate.

Developer AI components are under development; availability of a complete end-user Developer AI journey is not established by this guide. Do not treat an unavailable control as a successful AI review.

## Verify a fix as a QA reviewer

1. Open **Verification** and choose **Awaiting verification**. Select a report to review its original details and the Developer's proposed resolution.
2. Select **Verification** on the report. Add or edit checks, record their outcomes and notes, and choose **Save verification** to persist the draft.
3. When the required checks pass, select **Verify and close**. If the fix needs more work, provide a reason and select **Send back to Developer**.
4. Use **Reviewed by me** to find your completed reviews, or **All bugs** to browse the project queue. Available actions still depend on the report state and your permissions.

![Verification queue with synthetic QA reports](images/qa-verification-queue.png)

*An example local QA queue with synthetic reports; the current account and data may differ.*

## Use QA AI review

On an eligible report, select **Review with AI**, read the disclosure and request a review. The panel can suggest regression checks and identify missing evidence. Ask a follow-up in the panel if needed. Select useful suggestions and choose **Add selected to Verification**; the additions enter the current checklist draft as Pending. Review and save the verification draft yourself. The AI does not test the application, close a bug or apply a fix.

The panel sends selected report and latest-resolution text to the configured AI provider. AI requires an enabled, authorized backend and may be unavailable; see [AI enablement](ai-enablement.md). Review suggestions critically and avoid placing secrets in reports.

## Use test templates and discussion

QA reviewers can open **Test templates**, create or edit a reusable set of checks, and use the available template controls while preparing verification. A template is a starting point; check its relevance to the current bug. The report's **Discussion** supports human review comments visible to active QA reviewers in that project. It is not a channel for sharing credentials or sensitive production data.

## Appearance, navigation and sign out

Use the sidebar control to collapse or expand navigation and the appearance controls to choose a theme. Select another project through the available project control or Settings. Sign out before using another account, especially on a shared device.

## Troubleshooting and current limits

- **Cannot sign in or load a project:** check that the frontend and backend are running, then confirm the account and active project membership.
- **AI review unavailable:** AI may be disabled, the provider may be unreachable, or access may not be configured. Ordinary workflows do not require a successful model response.
- **A report changed while editing:** refresh and review the latest state before retrying; avoid overwriting someone else's update.
- **A button is disabled:** inspect the report status, assignment and required fields or checks.
- **Screens or features differ:** this guide describes the repository version it accompanies. A publicly accessible deployment and its exact revision have not been verified here.
