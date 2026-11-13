# Detailed User and Developer Guides

Date: 2026-11-13. Documentation-only update on `codex/detailed-guides`.
Source baseline: `b051f5067624387f86c7ffd025c78b690bf91cac` plus the guide,
visual-manifest, screenshot, diagram and evidence changes in this working tree.
Pre-existing untracked presentation material and local Flyway evidence were left
untouched. No human approvals or production checks are inferred.

## Scope and source checks

Expanded `docs/UserGuide.md` and `docs/DeveloperGuide.md` using the supplied MP3
guides as organizational examples only. Consulted current product scope, acceptance
index, shared contracts, UI/design reference, security/AI policy, README setup,
role components/API clients/controllers, validation types, migrations, test
configuration and CI. Corrected outdated guide statements about Developer AI and
the README's AI setup. The listed hosted URL is attributed to the README without
claiming independent deployment verification.

Added three current local screenshots (sign-in, Developer report, Developer AI /
resolution controls), retained the two existing Reporter/QA captures and added
four UML figures (component, conceptual class, state machine and sequence).
Editable Graphviz sources and the SVG sequence source are committed alongside
rendered images. See `docs/images/README.md` for capture details and regeneration.

## Verification

- Local Vite startup: initial sandbox attempt failed with loopback `EPERM`;
  rerun with approved execution succeeded on HTTPS port 4186.
- Screenshot capture: initial temporary script had a TypeScript annotation in
  JavaScript; corrected before successful capture. Initial sandbox Chromium launch
  was denied; approved execution succeeded. The first sign-in capture caught an
  entrance animation and was replaced after awaiting animations with reduced motion.
- Final capture command: `node /tmp/tracedesk-guide-capture.mjs` (temporary local
  script, not a new application dependency). Three PNGs inspected visually.
- `dot -Tsvg` for architecture, domain and lifecycle: passed with no diagnostics.
- `python3 tools/check_foundation.py`: PASS (portable repository check).
- `git diff --check`: PASS.

Documentation and capture work changed no application source, migration, test
configuration or dependency. Full frontend/backend/database/live-provider suites
were not rerun for this documentation-only change. Mock route confirmations are
not backend transaction evidence. Existing screenshot environments were not
re-established. No external site deployment, publishing or production mutation
was performed.

## Final documentation results

- Portable foundation check: PASS, 187 documents, 5 skills, 21 provenance entries, 11 transport Java sources.
- Guide local file/heading-anchor check: PASS, 91 local targets.
- SVG XML validation: PASS, all four figures.
- Diagram visual inspection: all four rendered and inspected; lifecycle labels adjusted to remove touching text.
- Final whitespace check: PASS.

Guide/visual working-tree fingerprint: `1cdfa43113dd06ec98f21ac708175ee1cbcbe8a68c634da23c1e4aed71bead84`. This is SHA-256 of
the sorted `sha256 + two spaces + relative path + newline` records for the
13 authored/updated guide and visual files below; evidence and pre-existing
screenshots/unrelated files are excluded.

```text
f9caf27bd2b049aae31fa06e2ae9c29b6062d4141cf6312e4c207fb977795f40  docs/DeveloperGuide.md
ad5389a8c1c4294e061964c56b3a014c46ddaa35053ae121462a5fee4b1153cc  docs/UserGuide.md
bb3008b18d0e4f151e10943a228aab45f4d52f745b03f19c148ccc062d2ed2b9  docs/images/README.md
76baabcf73f784558d5a108e41ceb5fcbf6e1be9f7c8ec7e7cb31d25b2b50c49  docs/images/diagrams/architecture.dot
85e88f3719b5489b3ce55b753d070cbc6311eb200894678afed0a8db08a4adce  docs/images/diagrams/architecture.svg
5c73a423f8d1c47bbe371aa2e93c6138dfb54ddad2d4848502c0a760a62a0815  docs/images/diagrams/domain.dot
df8d01f7e134a8563d2eb50f45c800a757929bb555a1c6e24cd832d985b092ae  docs/images/diagrams/domain.svg
c0bd4df7ca6d0936a25c2b3b8d738fae38172749ecb74ab34071242bb5b74093  docs/images/diagrams/handoff-sequence.svg
2341b3cd633657db0e5c6eccf4f5e164f25c53d6cdfa5a04c6803c8eca45d375  docs/images/diagrams/lifecycle.dot
16d4a28735a6043f8c292592e71c6f595048053d37e3f84e1f11bc1eae873328  docs/images/diagrams/lifecycle.svg
ec3c770ed89545ffb9fb72c65a302275d94c3c8844f8aa0073d44773f3575431  docs/images/guide-developer-investigation.png
a5f29a62607ac9a488c0cb24a04ff31eb59c8b25d301a40d31be18cc23d1c82c  docs/images/guide-developer-notes-ai.png
764382fc8b7566da3bce9b0be56a925888096f20403baaf0f8d95e4640e76e49  docs/images/guide-sign-in.png
```

The temporary Vite server was stopped after capture. Vite emitted existing
public-SVG raw-import warnings; the captured mascot and screens rendered and
were visually checked. No application asset imports were changed.
