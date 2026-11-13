# QA Workspace and Verification

These entries summarize the QA work visible in the current conversation and the linked project records. They are a draft for the contributor to verify, not a complete record of every interaction.

## From mockup to QA workspace

**Prompt strategy.** I asked for the agreed QA mockup to be ported into the application, using the shared icon tray and collapsible sidebar. I requested consistent disclosure motion and a stable Verification heading while switching queue filters.

**AI contribution and corrections.** The agent implemented the workspace in reviewable slices. I inspected the interface and requested corrections to layout, disclosure behaviour, navigation and the visibility of AI review controls. Mockup behaviour and product behaviour needed to be distinguished during the port.

**Verification and judgement.** The [QA contribution evidence](../../workflow/qa/evidence/submission-evidence.md) links issues to specifications, implementation and recorded checks. UI reference images and mockups are not proof of deployment or end-to-end product acceptance.

**Reflection takeaway.** Reviewing the actual interface and its navigation states matters alongside automated tests; a visual mockup alone does not prove a working role workflow.

## Verification features and integration

**Prompt strategy.** I asked for the planned QA follow-on work: send-back reasons, review history, templates, QA-only discussion and workspace continuity. I also requested that failing checks and integration conflicts be investigated.

**AI contribution and corrections.** The work was split across issue branches. The coordinator handled shared integration and migration ordering. A bounded review recorded a duplicate selector and encoding artefact that were corrected. The [verification CI repair note](../../workflow/qa/evidence/qa-verification-ci.md) describes migration conflicts and local fixture issues.

**Verification and judgement.** The [QA evidence index](../../workflow/qa/evidence/submission-evidence.md) links the merged PRs to historical checks. Agent token counts, per-agent elapsed time and a single-agent comparison were not recorded, so these results do not establish savings.

**Reflection takeaway.** Splitting work helps clarify ownership, but shared schema changes need ordered integration and verification.

## Browser test repair — 12 November 2026

**Prompt strategy.** I asked why a check failed after the merges, then requested a fix, commit, PR and merge.

**AI contribution and corrections.** GitHub Actions showed a timeout in a real-backend Developer/QA browser journey. The test expected an old “All bugs” region after the QA queue layout changed. The locator was scoped to main content and the selected tab was asserted.

**Verification and judgement.** The frontend build and isolated real-backend journey passed locally, and [PR #79](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/pull/79) passed its checks before merge. An initial local run could not start because the local Alpine database image had an executable-format error; another isolated PostgreSQL image was used for the successful run.

**Reflection takeaway.** A CI job name did not identify the failing role or cause: the captured page and test locator were needed to diagnose the timeout.
