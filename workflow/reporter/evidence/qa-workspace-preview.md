# QA queue and Summary preview

Issue #21. Local work on `feat/qa-workspace`, based on `a42226c`. This
covers the queue and Summary portions of TD-UI-01, TD-UI-03, TD-UI-04,
TD-UI-07 and TD-UI-08. The preview uses synthetic bug and patch data.

- `npm run check` passed TypeScript, ESLint and all 13 unit tests.
- `npm run build` passed. The QA workspace is loaded from the development
  preview entry point.
- `npm run test:e2e` passed all 21 browser tests. QA coverage includes shared
  search, queue filters, keyboard selection and disclosure, selection across
  sidebar collapse and resize, the responsive Change map, missing patch data,
  mobile focus return, and the shared axe checks.
- A separate Chromium check at 701px with the sidebar at its maximum verified
  that grid cards fit the remaining 240px workspace and that sort and display
  controls update the queue.

Compared dark mode at 1440 × 900 CSS pixels, scale 1, with reduced motion in
Chromium 156.0.8078.4 on Windows. The queue toolbar fits on one row. The TD-023
Summary measured 1101px wide after reserving the scrollbar gutter, with a 46px
toolbar and 300px Change map. Local captures are
`tracedesk-qa-queue-port.png` and `tracedesk-qa-summary-port.png` in the system
temporary directory. This is a visual and dimensional comparison.

The Summary adds a return action for queue navigation. It omits the
reference's comment composer, AI and checklist actions until their slices
are ported. Test templates still uses the shared preview placeholder.
Authenticated role and API wiring and independent review remain pending.

## Opening and closing motion

QA uses shared `Disclosure` and `AnimatedPopover` components for report and
activity rows and the queue's Sort bugs and Display options menus. The
installed Apps SDK Popover disables visibility animations. The shared
popover uses its underlying Radix primitive at the version already in the
lockfile, together with the existing SDK surface tokens and buttons.

Typecheck, lint, 13 unit tests, build, the foundation check and all 22 browser
tests passed. The browser checks cover keyboard activation, Escape and
focus return, timings, the popover exit transition, and reduced motion.
A separate Chromium sample confirmed intermediate disclosure heights on
open and close and successful reversal before closing finished.
