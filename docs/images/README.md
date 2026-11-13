# Guide visual manifest

These figures support the [User Guide](../UserGuide.md) and
[Developer Guide](../DeveloperGuide.md). All screenshots show actual TraceDesk
frontend components with synthetic data, not generated artwork. They do not
establish provider success or production deployment acceptance.

## Screenshots

| File | Origin and scope |
| --- | --- |
| `guide-sign-in.png` | Captured 2026-11-13 from the current local Vite frontend, with a synthetic unauthenticated session response. 1440 × 1000 viewport. |
| `guide-developer-investigation.png` | Captured 2026-11-13 after the Developer component received a synthetic confirmed start-investigation response. 1440 × 900 viewport. |
| `guide-developer-notes-ai.png` | Same Developer fixture, scrolled to investigation suggestions and proposed resolution. No completion requested. 1440 × 900 viewport. |
| `reporter-new-report.png` | Existing repository synthetic Reporter browser capture; retained without modification. Original capture environment is not re-established by this update. |
| `qa-verification-queue.png` | Existing repository synthetic QA queue capture; retained without modification. Original capture environment is not re-established by this update. |

New captures use source baseline `b051f5067624387f86c7ffd025c78b690bf91cac`,
macOS/arm64, Chromium 156.0.8078.4 headless, device scale factor 1, dark appearance,
system font stack and reduced motion. Screenshots were visually inspected after
capture. No human browser settings were changed. The Developer data comes from
`frontend/e2e/developer-states.e2e.ts`, with attack-test strings replaced by benign
synthetic refund text and the project named `Guide example project`.

To refresh these figures, start a separate local frontend, create an isolated
Playwright browser context, and fulfill only the synthetic API routes used by the
owning browser test. Do not log into a live account or call the provider for a
screenshot. Await fonts and finite entrance animations. Capture the sign-in page,
then the Developer report after the start action confirms, then scroll the
investigation-suggestion heading into view. Check controls and visible text
against source before replacing an image. Preserve the fixture distinction in
captions and record the new revision/browser/viewport here.

## UML diagrams

| Diagram | Notation / purpose | Editable source |
| --- | --- | --- |
| `diagrams/architecture.svg` | UML component/dependency view; logical responsibilities, not separate deployed services. | `diagrams/architecture.dot` |
| `diagrams/domain.svg` | Conceptual UML classes and multiplicities; selected schema relationships, not ORM entity declarations. | `diagrams/domain.dot` |
| `diagrams/lifecycle.svg` | UML report state machine with actors and transition guards. | `diagrams/lifecycle.dot` |
| `diagrams/handoff-sequence.svg` | UML sequence view of resolution submission and QA closing, with dashed return messages. | The SVG itself is editable XML with explicit participant/message labels. |

Regenerate the Graphviz figures from the repository root:

```sh
dot -Tsvg docs/images/diagrams/architecture.dot -o docs/images/diagrams/architecture.svg
dot -Tsvg docs/images/diagrams/domain.dot -o docs/images/diagrams/domain.svg
dot -Tsvg docs/images/diagrams/lifecycle.dot -o docs/images/diagrams/lifecycle.svg
```

Graphviz is optional documentation tooling, not an application dependency. The
committed SVGs render as normal Markdown images without a JavaScript diagram
runtime. Check labels, arrow direction, multiplicities, clipping and readability
at guide content width after editing. The sequence diagram is a successful-path
summary; it does not imply transactions stay open during human testing or AI calls.
