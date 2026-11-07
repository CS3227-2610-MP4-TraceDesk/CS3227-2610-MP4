# Login UI port

Status: approved login presentation. Backend authentication behavior is specified
separately in [login authentication integration](login-auth-integration.md).

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
| TD-LOGIN-04 | Development Demo accounts fills synthetic credentials and submits through the same backend authentication API; no local role selection or fake sign-in | Browser journey with synthetic accounts and backend authentication |
| TD-LOGIN-05 | Production builds omit demo accounts and synthetic credentials; authentication comes only from the session API and no fake session is created | Built asset inspection and production browser check |
| TD-LOGIN-06 | On login entry, the card fades and settles upward by 8px over 300ms without bounce or a separate form delay. The beetle opens its eyes, perks its antennae and settles 4px with a matching shadow over roughly 600ms. Input accelerates its settlement from the current pose; controls remain usable. Reduced motion skips the entrance, including when enabled during it. The resting layout is unchanged | Browser entrance, interruption and reduced-motion checks |

Development account shortcuts only fill the form; the authentication API owns
the session and identity. Do not add client-side roles or describe the visual
login port alone as working authentication. See the linked integration
specification for session restoration, error behavior and secure local transport.

Run the existing frontend check/build/e2e pipeline. Record source hashes,
comparison conditions, results and any remaining gaps in Reporter evidence.
