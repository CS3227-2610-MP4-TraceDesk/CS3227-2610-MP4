# Login UI port

Status. Login presentation and local development preview; authentication integration
and role workspace ports are separate work.

The user requested the approved local mockup's login to be ported without visual
changes and explicitly retained Demo accounts and local fake sign-in first.
Use the current local mockup as the login reference. Older frozen QA references
do not override the approved login. This slice follows TD-UI-02, TD-UI-03,
TD-UI-07 and TD-UI-10 where applicable.

| Criterion | Observable result | Verification |
| --- | --- | --- |
| TD-LOGIN-01 | Copy login layout, SDK 0.2.2 components, font, tokens, assets and control sizes; desktop card is 640 by 550 with equal panels, 24px radius and 2px border | Compare actual mockup and repo in identical Chrome viewport, theme, motion preference and scale |
| TD-LOGIN-02 | Both themes, keyboard controls, labelled email/password, visibility toggle and demo disclosure operate; 320/390px layouts stack without horizontal overflow | Browser checks and axe |
| TD-LOGIN-03 | The same mascot hook supplies pointer gaze, approach hop, shadow/leg response, one-shot pat with auto relaxation, blink, antenna movement and password look-away; reduced motion uses still poses | Compare hook identity and exercise browser states |
| TD-LOGIN-04 | Development Demo accounts fills the same synthetic credentials and matches the same local roles; successful preview opens the existing starter content with a visible demo label and sign-out | Browser form checks, without API calls |
| TD-LOGIN-05 | Production builds omit demo accounts, synthetic credentials and local role selection; submission reports that sign-in is unavailable until integration; no fake session or protected calls | Built asset inspection and production browser check |

Demo role state is component-local and cleared on reload/sign-out. It creates no
cookie, session, membership, backend identity, authorization claim or persistence.
The authentication API contract remains authoritative for later integration.
The production submission message is an intentional integration placeholder.
Do not describe this slice as working production authentication.

Run the existing frontend check/build/e2e pipeline. Record source hashes,
comparison conditions, results and any remaining gaps in Reporter evidence.
