# PR #60 project cursor CI correction

Date: 2026-11-12 Asia/Singapore. Author: Codex; self-review only.
Criterion: TD-PROJ-04 cursor integrity test; no production behavior change.

Failed CI: Foundation CI run 43065741943, authentication job 130680078579,
merge revision `998c19b52061c185339ca4103ebad4e9911bd590` (main
`bea780c3d133468b0d3e4a463b63667deb144348` plus PR head
`144f1dbd8d5492e8d5445ab55ea00934f356728e`). Result: 109 tests,
1 failure at ProjectDirectoryCursorTest.java:13. The push-triggered run passed;
the failure occurred in the PR merge candidate containing newer main code.

The test generated a random UUID and replaced the cursor's last character with
`A`. For a cursor already ending in `A`, this makes no change, so valid decoding
correctly succeeds and the test incorrectly fails. A fixed UUID ending in
`000a` reproduces that unchanged-cursor case: replacing only the random UUID
with this fixture yielded 1 test / 1 failure at the original assertion.

Correction: parameterize two fixed UUIDs, including that reproducer. Replace the
first signature character with a guaranteed different character (`A` or `B`),
where all character bits are significant. Assert the cursor changed, rejection
of the altered signature, successful valid round-trip, rejection under another
secret and acceptance by a replica sharing the secret. Production cursor
validation and authorization are unchanged.

Verification in an isolated checkout of the merge revision, macOS / Java 25:

```sh
JAVA_HOME=/opt/homebrew/Cellar/openjdk@25/25.0.4.1/libexec/openjdk.jdk/Contents/Home ./gradlew :backend:auth-app:test --tests 'com.tracedesk.auth.projects.domain.*' --tests com.tracedesk.auth.projects.api.ProjectControllerTest --no-daemon --console=plain
```

Passed: 9 tests (2 cursor, 1 project-name, 6 controller), zero failures/errors/skips;
13-second build. Initial deterministic reproduction failed as expected (17-second
build). Local XML reports confirm counts. No database/provider call, full backend
suite, frontend suite, live browser journey or independent review was run for this
test-only correction. Full CI results remain separate. No skipped integration
suite is counted as a pass. Uncommitted reflections and existing evidence changes
in the original checkout were not included.

Changed test source: 1743 bytes; SHA-256 `adababef90f900c69afd31121b055b2ed6285665da7fb116464c7dd798da408d`.
