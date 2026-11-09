# Reporter AI review

Issue #17 adds review of an owned saved draft or an owned `OPEN` report. Save new
or edited content first, open **Review with AI**, read the provider disclosure,
and request suggestions. Select the proposed fields to save; **Apply selected
changes** uses the normal version-checked edit API. Unselected fields and logs
are preserved. Suggestions never submit reports or change their lifecycle.

The provider receives only title, description, environment, reproduction steps,
expected behavior and actual behavior. Logs are excluded. A stored report can
exceed the AI input budget; an oversized source is rejected without truncation or
provider dispatch. Model text is untrusted, strictly validated advisory content.
Already complete reports may return no changes or questions.

The server-owned `report-improvement-v2` prompt asks the model to preserve the
Reporter's requirements, concrete commands, versions and observations. Unknown
details belong in focused questions; the prompt forbids invented timing,
execution or safety-test outcomes. This follows the
[v1 quality assessment](../workflow/reporter/evidence/reporter-ai-live-quality.md).
These instructions reduce ambiguity but cannot guarantee factual correctness.
The original v1 results remain the baseline; v2 live quality is not yet measured.

AI defaults to disabled. `ReportAiConfiguration` now delegates to the shared
[enablement manager](ai-enablement.md), requiring trusted backend configuration,
explicit sponsored-access approval, finite resource limits and verified expiring
model metadata. Live account/model validation and deployed release verification
remain separate gates. Normal reporting remains usable. The browser test provider is
test-only and exercises the real session, API, shared gateway, ledger and PATCH
path; it does not demonstrate live model quality or funding readiness.

See the [behavior contract](../workflow/specs/reporter-ai.md) and
[executed evidence](../workflow/reporter/evidence/reporter-ai.md).
