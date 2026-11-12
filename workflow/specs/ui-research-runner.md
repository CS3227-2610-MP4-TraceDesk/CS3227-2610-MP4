# UI research runner specification

Status: implementation in progress. User requested a reusable local workflow and script on 2026-11-12. Scope is development-time UI research, not an application AI feature.

- UIR-01: Accept a non-empty bounded task, a positive per-stage timeout and an optional empty output directory.
- UIR-02: Run researcher, designer, reviewer and coordinator in that order, each in a fresh read-only Codex CLI session, passing previous outputs as bounded untrusted context.
- UIR-03: Store the brief, completed outputs, timestamps and a final report in a local run directory; never overwrite existing output.
- UIR-04: Stop on stage failure, timeout, empty output or an oversized handoff, and retain a truthful failure record without saving raw CLI errors.
- UIR-05: Reuse the UI skill and harness, identify sources and unknowns, do not fabricate evidence, and do not change or deploy the app.
- UIR-06: Document that plaintext output may contain sensitive content, that read-only is not a confidentiality guarantee, and that tokens, actual model, cost and screenshots are not automatically measured.

Verification: focused unit tests with mocked sessions test handoffs, failure, invalid inputs and overwrite prevention. A live run requires a configured Codex account and explicit authorization for the invoked sessions; a mock is not evidence of a live multi-agent run.
