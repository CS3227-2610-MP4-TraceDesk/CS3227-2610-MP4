# Login UI port verification

Date. 2026-11-07, Asia/Kuala_Lumpur.
Base revision. dd98d13b870062ffe06b396b2a8a78259e25df94, branch feat/login-ui.
This record describes the working-tree implementation on that base.

Scope follows [login criteria](../../specs/login-ui.md). The user explicitly kept
local fake sign-in for this first UI port. Real authentication and the three role
workspaces are later integration work. Development demo sign-in opens the existing
starter page with a local preview label and sign-out.

## Source identity

The reference is the approved unversioned local mockup served on port 4174.
The port's development frontend is served on port 4178. The following SHA-256
values identify the reference files used.

| Reference file | SHA-256 |
| --- | --- |
| src/SignIn.tsx | 630602cfda9de40674511279882ddc63932d20fbe075dfd6efa5da693133134c |
| src/useMascotMotion.ts | efbe8608fadd4057d34d2ee23fc520fbcda0b363a70a6a0b22d96009942cee8b |
| src/style.css | ed206b2fffe068dc05032ca913d0292115dd3e9e058d14b17c9e745c46cdc33f |
| public/tracedesk-logo.svg | cde12a5a47e291e6c8853e582824b82e1390c424e5e03d0c727c805190bd5d48 |
| public/tracedesk-favicon.svg | da6538ad4493709116a54cbc0ba2057d81261b95dfb8b97af6483733c4db30c8 |

The animation hook was copied byte for byte. The three existing canonical brand
SVGs match the corresponding mockup assets. Login CSS was extracted with its
root/theme tokens and shared typography, without unrelated role-page selectors.
The sign-in JSX preserves control sizes, labels, layout and interactions; its
asset URL uses the repo's existing brand directory. Fake credentials are isolated
in a development fixture and removed from production JavaScript.

## Verification

| Criterion | Evidence and result |
| --- | --- |
| TD-LOGIN-01 | Installed Chrome, Windows, device scale 1, reduced motion, no hovered controls. Compared mockup and development screenshots at 1440 by 900, 390 by 844 and 320 by 740, in both themes. All six comparisons had zero differing pixels. |
| TD-LOGIN-02 | Browser tests exercised both themes, show/hide password, labelled controls and mobile stacking. Axe returned zero violations in the production login in both themes. |
| TD-LOGIN-03 | Identical animation-hook hash plus browser tests for approach shadow movement, pat crescents returning to open eyes while stationary, darker resting shadow, password interruption and reduced-motion still gaze. |
| TD-LOGIN-04 | Browser test signed into all three demo accounts, signed out, and verified zero non-GET requests and zero cookies. Invalid credentials stayed on the form. |
| TD-LOGIN-05 | Built JavaScript contains none of the three demo email addresses, demo password or Demo accounts label. Production browser submission with demo credentials stayed on the form, reported the integration placeholder, and made no non-GET request. |

Commands run from frontend unless otherwise stated.

- npm run check passed TypeScript, ESLint and two existing unit tests.
- npm run build passed.
- npm run test:e2e passed all eight browser tests, including two production
  checks and six development checks.
- npm run test:e2e:a11y passed its production appearance test, including axe
  in both themes.
- The root portable foundation check passed with 59 documents, four skills,
  21 provenance entries and nine transport Java sources. The git diff whitespace
  check passed.

Screenshots were captured in an isolated headless Chrome context, outside the
human's browser. Local comparison PNGs and comparison JSON were written to the
operating-system temporary directory; these are local verification artifacts.
This is evidence of the listed viewport/theme states and hook identity rather
than a claim of identical rasterization on other browsers or operating systems.

## Corrections and limitations

The first build found an incorrect relative mascot asset path. Corrected it.
The first visual comparison found SDK styling differences caused by CSS layer
registration order. Moving the stylesheet import ahead of the App import, as in
the mockup, produced the zero-difference comparisons.

The first e2e invocation could not launch because the pinned Playwright browser
was absent. Installed Chromium with npx playwright install chromium and reran
the suite successfully.

npm installation reported 16 dependency audit findings (4 low, 10 moderate,
2 high) and engine warnings because local Node 24.7 is older than the existing
jsdom dependency's declared Node 24.15 minimum for the Node 24 release line.
The checks above nevertheless passed on this machine. Dependency remediation is
outside this visual port.

The user requested no subagents. Implementation and verification were performed
by the same agent; independent review remains outstanding. This record does not
claim the independent-review gate passed. No production authentication,
membership checks, provider invocations or backend state were added.

## Process observations

Selected context included repository/frontend instructions, UI and auth contracts,
mockup login and motion sources, SDK input styles and current test configuration.
The work used one implementer and sequential checks with no delegated work.
Token/cost telemetry and comparative resource savings were unavailable. The run
had a failed relative-path build, a CSS import-order correction and a browser
installation retry, all resolved as recorded above.

## Readability refinement

On 2026-11-07, after commit e75ef53, the user authorized a behavior-preserving
readability pass. Renamed pupil coordinates, reaction state and event callbacks,
named the existing timing and eye-geometry constants, and expanded the sign-in
JSX formatting. Values, SVG assets, animation keyframes and event flow are
preserved. The earlier byte-identical hook statement describes the initial port;
the current hook now uses descriptive names.

After this refinement, npm run check and npm run build passed. All eight existing
browser tests passed, including both-theme axe checks, production demo rejection,
all three development accounts, invalid credentials, mobile layout, reduced
motion, hop/pat relaxation and password interruption. New screenshots compared
against the same original mockup had zero differing pixels in all six previous
viewport/theme combinations. Implementation and verification used one agent per
the user's instruction; independent review remains outstanding.

## Login entrance

On 2026-11-07 the user approved TD-LOGIN-06: a restrained card entrance and
beetle wake-up, preserving immediate input access and reduced-motion support.
This extends the login reference with custom motion; it is not an Apps SDK preset
or an official Apple timing specification. No dependency or SVG geometry changed.

Verified working tree based on 85aac6550d53247279b7f0dc88ba109d631b014f.
Git blob identities for the implementation and browser checks:

- frontend/src/login/useMascotMotion.ts: caf74aab664249168263b0daa1efff849574f385
- frontend/src/style.css: fce273aff1974f5de3598725694fdf155cff84f6
- frontend/e2e/login-preview.e2e.ts: 1d5ec9d33d22c238993d4ba2fee74d007b039d0f

The existing frame loop now settles the wake-up from its current pose; pointer or
focus input accelerates settlement instead of snapping it. A pending approach hop
hands over after wake-up. Reduced motion starts at rest and also settles a running
entrance when enabled. CSS handles only the card's 300ms fade and 8px movement.
Input is never disabled and the form has no separate reveal delay.

The entrance test first failed because no wake-up existed. Regression checks
caught a dropped approach hop during wake-up, fixed by retaining the pending
approach. Mobile tests now measure both panels in one frame, avoiding comparisons
of different positions during motion. The reduced-motion interruption test uses
a controlled browser clock and verifies the eyes are still waking before changing
the preference.

Final npm run check passed (TypeScript, ESLint, two unit tests); npm run build
passed; npm run test:e2e passed all ten tests in 19.5 seconds, including production
axe/controls and demo rejection. Visual inspection used isolated Chromium at
1440 by 900, scale 1, dark theme, with screenshots during wake-up and after
settlement. The card remained 640 by 550. Screenshots are local temporary files
tracedesk-entrance-awakening.png and tracedesk-entrance-resting.png. This run did
not repeat the earlier six pixel-diff comparisons. Independent review remains
outstanding; no delegated work was used, following the user's instruction.
