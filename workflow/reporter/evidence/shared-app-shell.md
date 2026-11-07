# Shared app shell preview evidence

The initial implementation was based on main commit `e685ee0` on
`feat/shared-app-shell`. It covers the shell portions of TD-UI-01, TD-UI-02,
TD-UI-07, TD-UI-08 and TD-UI-10.

`frontend/src/shell/AppShell.tsx` owns the frame and responsive sidebar state.
`IconTray.tsx` owns the shared navigation controls and bottom Settings action.
`Sidebar.tsx` renders the contextual panel and provides shared group and item
components. Callers supply top tray items, active area, optional sidebar content
and workspace content. The role-specific fixtures and the preview are under
`frontend/src/preview`. App imports the preview only in development.

- `npm run check` passed TypeScript, ESLint and both unit tests.
- `npm run build` passed. An asset search found no sample workspace content,
  demo credentials or shell stylesheet selectors in the production build.
- `npm run test:e2e` passed all 13 Chromium tests in 31.6 seconds. Coverage includes
  all three demo roles, desktop and mobile sidebar states, keyboard focus, both
  themes with axe, and production rejection of demo credentials.
- A separate 390 × 844 browser smoke check covered New report navigation, the
  account popover and sign-out.
- Compared the expanded QA shell with the frozen QA queue capture in isolated
  Chromium 156 on Windows at 1440 × 900, scale 1, dark theme and reduced motion.
  The browser assertions verified a 48px tray, 34px tray button, 275px sidebar
  and 4px frame gutter. The tray and sidebar visually match with minor rendering
  differences. This comparison covers shell chrome only.

Local screenshots are in the system temporary directory as
`tracedesk-shell-expanded.png`, `tracedesk-shell-collapsed.png` and
`tracedesk-shell-mobile.png`. Role pages and real sign-in are separate slices.
Independent review remains pending.

The tray badge trial was reverted after visual review. The tray follows the
spacing in the captures, with the beetle branding on sign-in and the browser tab.

The desktop sidebar now has a 300ms width and opacity transition using sampled
CSS easing. The REA study of the installed Codex bundle
(`ev_b33880a6f1bf51c68582d6f97875dbcba31780ad5763d3aed32f10905a3050f3`)
identified a 300ms spring with 0.1 bounce and contextual left panel limits of
290–520px including the rail. TraceDesk subtracts its 48px tray to obtain
242–472px, preserves its 275px starting width, and reserves 240px for the
workspace inside the frame. The mobile overlay retains its fixed sizing.

Browser coverage checks pointer capture and both resize limits, keyboard
resizing, width restoration after reopening and viewport changes, an
intermediate animated frame, reversal, and reduced motion. The collapsed
desktop sidebar stays mounted to preserve its state and is inert while its
track clips it to zero width.

The branch was advanced to main commit `17b2ca6` before integrating the shell.
The regular application retains the merged authentication code, session styles,
and login browser suite. The Vite entry point loads role fixtures only for the
explicit development preview. Shell browser checks now run separately from the
authentication journey, and the production browser check exercises the preview
query to verify that normal authentication still applies.

After integration, `npm run check` passed TypeScript, ESLint and 13 unit tests.
`npm run build` passed. The production assets contained no preview markers,
sample workspace data, demo credentials or shell selectors. The HTTPS browser
suite passed all 19 tests in 54.0 seconds, with mocked authentication responses
and separate development shell fixtures. The portable foundation check passed.
