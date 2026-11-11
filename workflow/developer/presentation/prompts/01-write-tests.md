Model: GPT-6.1 Sol · Effort: High · Extra agents: none · Open folder: D:\CS3227-wt\tests

---

You are writing tests only, for an experiment. Do not implement any feature logic.

Work only inside D:\CS3227-wt\tests (branch exp/dev-ai-tests). Do not edit any other folder.

Read:
1. AGENTS.md
2. workflow/specs/developer-ai.md (the DEV-AI spec)
3. D:\CS3227-2610-MP4\workflow\developer\experiments\agentic-se\dev-ai-test-cases.md (the test cases; read it from that path, do NOT copy it into this repo)
4. Existing code only as needed to match style: backend/ai-gateway (GatewayPorts), backend/auth-app/.../reports/ai (Reporter AI pattern), backend/auth-app/.../investigation.

Task:
A. For each of the three jobs (validate the AI reply, build the context, permission checks), create the smallest main-code skeleton the tests need: types and method signatures under the investigation package, every method body `throw new UnsupportedOperationException("not implemented")`. No logic. Fit the existing GatewayPorts Authorizer/ContextBuilder where the spec says so.
B. Write one JUnit test class per job covering every case in the test-cases file that is NOT marked (hidden). Put the case ID in each test name (e.g. v10_statementCodePointLimit). Use synthetic text and fake providers only. Job 3 uses the existing isolated test-database setup.
C. Write the (hidden) cases (V11, V21, C05, C12, A05, A16) as separate test classes saved ONLY under D:\CS3227-wt\hidden\ , mirroring the package path. They must not be inside the repo folder.
D. Run the backend compile and the new visible tests. Expected: everything compiles, and the new tests fail with UnsupportedOperationException (or a clear assertion). Nothing else in the build should break.
E. Commit only the skeleton and the visible tests on exp/dev-ai-tests with message: "test(developer): add DEV-AI experiment tests". No Co-Authored-By or "Generated with" lines. Do not push.

Rules: no .env files, no credentials, no live AI calls, do not change the spec.

Reply with, and nothing else:
- the commit hash
- the files you created (visible and hidden)
- the exact test command and the result counts (pass / fail / skipped / error)
- any test case you could not write, and why
- any place the spec was unclear
