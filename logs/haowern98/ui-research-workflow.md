# UI Research Agent Workflow

## Coordinating research, design and review — 13 November 2026

**Prompt strategy.** I asked for a reusable and more automated UI design research workflow, grounded in the lecture's coordinated-agent and context-engineering ideas. I wanted a script and shared instructions that teammates could run for a specific design task.

**AI contribution and corrections.** The agent implemented a local runner that passes results sequentially through research, design, a separate automated reviewer and a final coordinator. The first real attempt failed because the configured CLI model was unavailable. A later attempt exposed a Windows read-only sandbox shell failure. The runner was changed to accept a model override and explicitly selected repository-file context.

**Verification and judgement.** A source-based QA sidebar run completed all four stages on three selected files. The reviewer flagged unsupported item counts and an ambiguous retry proposal. The final report retained the need for further validation. No live UI or screenshot was inspected, and tokens, cost and a single-agent baseline were not recorded. The [sanitized run summary](../../workflow/ui-research/evidence/2026-11-13-qa-sidebar.md) states the timing, inputs and limits. [PR #83](https://github.com/CS3227-2610-MP4-TraceDesk/CS3227-2610-MP4/pull/83) contains the workflow; it is a development tool, not the application's QA AI feature.

**Reflection takeaway.** Explicit handoffs and a reviewer helped challenge a plausible proposal, while selected context avoided an inaccessible shell. The run is one observation, not evidence that multiple agents are faster or cheaper.
