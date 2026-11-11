# MVP navigation: remove saved Environments

Date: 2026-11-12 Asia/Singapore (checks completed 2026-11-11 UTC).
Author: Codex; self-review only, no independent review claimed.
Base revision: `5aa1c203e9b6f62ae0fc08e7fdf33395f1b1583b` plus the changed-file identities below.

Scope and authorization: user requested removal of the non-MVP Environments page.
Updates REPORT-UI navigation point 4 and TD-RPT-11–15 navigation coverage, plus
shared UI Reporter navigation. This is a bounded removal, not acceptance of all
criteria in either specification. Reports and bottom Settings remain; the report
Environment field remains. Removed the page, tray entry, environment-only leave
handler/styles and preview fixture. Existing unsaved/submission protections on
remaining report/project navigation are retained.

## Verification

Commands run from `frontend/` unless specified:

- `npm run check`: passed type checking, lint, 9 test files / 77 tests.
- `npm run build`: passed.
- `npm run test:e2e -- --project=development --project=production -g 'role fixtures open|appearance|CSRF-only|known validation|business denial|idempotency conflict'`:
  initially 5 passed, 1 failed because the updated preview test expected Settings
  as the heading, while the selected Settings subpage is Appearance.
- Corrected that assertion; `npm run test:e2e -- --project=development -g 'role fixtures open'`:
  1 passed, including absence of Environments, Reports/Settings navigation,
  1440/390/320px Reporter checks and zero axe violations at those widths.
- Final `npm run typecheck` and `npm run lint`: passed after browser-test changes.
- Root `git diff --check`: passed.

Initial browser starts were blocked by sandbox port binding (EPERM); the checks
above ran after approved execution with local-server access. An initial
`npm run check` from the repository root failed because the package is in
`frontend/`; corrected by using that directory. No deployment or real-backend
journey ran. Browser APIs used synthetic mocks/preview fixtures. Existing Vite
preview asset-import warnings remain outside this removal. No full role-feature
audit or independent agent review was performed for this small removal.

Screenshots are local ignored artifacts under
`frontend/e2e-results/artifacts/workspace-preview.e2e.ts-r-638dd-t-an-authentication-request-development/reporter-mvp-{1440,390,320}.png`.
Desktop and 320px captures were visually inspected: Reports is visible, no
Environments icon, Settings remains at the bottom. Reference capture is a QA
layout, so no pixel-equivalence claim is made for Reporter navigation. No stored
reference baselines or historical evidence were replaced.

## Changed-file identity

- `frontend/e2e/workspace-preview.e2e.ts`: 20914 bytes; SHA-256 `82ad380cecb02b34dadf62db7a0e6ebee1f0a91cba5ed7f5ed93aac16859194b`
- `frontend/src/preview/workspaceFixtures.tsx`: 1974 bytes; SHA-256 `6b62a45fe98755043902708323acbb2114b0eb9ddd72d5414d47828169159450`
- `frontend/src/reports/ReporterWorkspace.test.tsx`: 11159 bytes; SHA-256 `418158a2c7135337d451f8cddb93a28e8b81d6446e5a08f720116fba34dc11c6`
- `frontend/src/reports/ReporterWorkspace.tsx`: 72302 bytes; SHA-256 `3090ffc1930fafbf2f6df42444575e99cf5972a133e32ce45777dad617888d79`
- `frontend/src/reports/reporter-workspace.css`: 9738 bytes; SHA-256 `cc3b016b37e8c2f5d4f556cea9fcbdb139ce6f7cbfc4dec13e3f2632a80adaf1`
- `workflow/specs/reporter-workspace-ui.md`: 17889 bytes; SHA-256 `23a4b408dee82f6d3ba116a4b39003c7e7a82ad39930d90961016ea5a9fbf1d8`
- `workflow/specs/shared-ui.md`: 11871 bytes; SHA-256 `f4947d18820fe7311c7dcf468a1aafeaf23d1f7d61e1dfc39afc822df01da76b`
