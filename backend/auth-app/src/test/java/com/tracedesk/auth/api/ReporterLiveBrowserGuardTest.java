package com.tracedesk.auth.api;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ReporterLiveBrowserGuardTest {
    private static final Instant NOW = Instant.parse("2026-11-09T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final byte[] CASE;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    static {
        try { CASE = Files.readAllBytes(Path.of("../../workflow/reporter/experiments/reporter-ai/browser-live-case.json")); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    @TempDir Path temp;

    @Test void strictAllowlistAndBindingsRejectMalformedExpiredAndMismatchedApprovals() throws Exception {
        Path approval = approval(Map.of());
        var valid = assertDoesNotThrow(() -> ReporterLiveBrowserGuard.validate(approval, env(), CASE, CLOCK));
        assertEquals("configured-model", valid.model());
        assertEquals(NOW.plusSeconds(600), valid.expiresAt());
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", valid.candidateRevision());
        assertEquals(ReporterLiveBrowserGuard.sourceIdentity(Path.of("../..")), valid.candidateSourceSha256());
        assertRejected(approval(Map.of("unexpected", "value")), env(), CASE);
        assertRejected(approval(Map.of("model", "other")), env(), CASE);
        assertRejected(approval(Map.of("maxAttempts", 2)), env(), CASE);
        assertRejected(approval(Map.of("maxAttempts", 4_294_967_297L)), env(), CASE);
        assertRejected(approval(Map.of("maxResourceUnits", 9201)), env(), CASE);
        assertRejected(approval(Map.of("candidateRevision", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")), env(), CASE);
        assertRejected(approval(Map.of("candidateSourceSha256", "0".repeat(64))), env(), CASE);
        assertRejected(approval(Map.of("expiresAt", NOW.toString())), env(), CASE);
        assertRejected(approval, env(), "{}".getBytes());
        byte[] changed = CASE.clone(); changed[0] = (byte)'[';
        assertRejected(approval, env(), changed);
        assertRejected(approval(Map.of("approved", "false")), env(), CASE);
        assertRejected(approval(Map.of("stopOnFailure", "false")), env(), CASE);
        assertRejected(approval, env("AI_ENABLED", "false"), CASE);
        assertRejected(approval, env("TRACEDESK_LIVE_BROWSER_TEST", "false"), CASE);
        assertRejected(approval, env("AI_GLOBAL_DAILY_UNITS", "9199"), CASE);
        assertRejected(approval, env("AI_ACTOR_DAILY_UNITS", "9201"), CASE);
        assertRejected(approval, env("AI_SPONSORED_APPROVAL_EXPIRES_AT", NOW.plusSeconds(300).toString()), CASE);
        assertRejected(temp.resolve("missing.json"), env(), CASE);
        Files.writeString(approval, "{\"approved\":true,\"approved\":false}");
        assertRejected(approval, env(), CASE);
        Files.writeString(approval, "{} {}");
        assertRejected(approval, env(), CASE);
        Files.writeString(approval, "x".repeat(8193));
        assertRejected(approval, env(), CASE);
    }

    @Test void sourceIdentityRehashesManifestFilesAndRejectsUnsafePaths() throws Exception {
        Path repo = temp.resolve("repo");
        Path manifest = repo.resolve("workflow/reporter/evidence/ai-enablement.sha256");
        Path source = repo.resolve("src/Reporter.java");
        Files.createDirectories(manifest.getParent()); Files.createDirectories(source.getParent());
        Files.writeString(source, "candidate A");
        String relative = "src/Reporter.java";
        Files.writeString(manifest, "0".repeat(64) + "  " + relative + "\n");
        String first = ReporterLiveBrowserGuard.sourceIdentity(repo);
        Files.writeString(source, "candidate B");
        String second = ReporterLiveBrowserGuard.sourceIdentity(repo);
        assertNotEquals(first, second, "the digest follows actual source bytes, not stored manifest hashes");

        for (String unsafe : new String[]{"../outside", ".env.local", "src/.git/config", "workflow/reporter/evidence/ai-enablement.sha256"}) {
            Files.writeString(manifest, "0".repeat(64) + "  " + unsafe + "\n");
            assertThrows(IllegalStateException.class, () -> ReporterLiveBrowserGuard.sourceIdentity(repo), unsafe);
        }
        Files.writeString(manifest, "0".repeat(64) + "  " + relative + "\n" + "1".repeat(64) + "  " + relative + "\n");
        assertThrows(IllegalStateException.class, () -> ReporterLiveBrowserGuard.sourceIdentity(repo), "duplicate manifest paths fail closed");
        Path link = repo.resolve("src/link.java");
        try {
            Files.createSymbolicLink(link, source.getFileName());
            Files.writeString(manifest, "0".repeat(64) + "  src/link.java\n");
            assertThrows(IllegalStateException.class, () -> ReporterLiveBrowserGuard.sourceIdentity(repo), "symlinked source paths fail closed");
        } catch (UnsupportedOperationException | java.io.IOException unavailable) {
            // The production check rejects symlinks; some filesystems cannot create one in this test sandbox.
        }
    }

    @Test void claimIsAtomicAndOneUseGateAllowsOnlyOneConcurrentContext() throws Exception {
        Path approval = approval(Map.of());
        var auth = ReporterLiveBrowserGuard.validate(approval, env(), CASE, CLOCK);
        ReporterLiveBrowserGuard.claim(auth);
        assertFalse(Files.exists(approval));
        try (var stream = Files.list(temp)) { assertEquals(1, stream.filter(p -> p.getFileName().toString().startsWith("approval.json.consumed-")).count()); }
        assertThrows(IllegalStateException.class, () -> ReporterLiveBrowserGuard.claim(auth));

        var gate = new ReporterLiveBrowserGuard.OneUseGate(NOW.plusSeconds(60), CLOCK);
        int count = 12; var ready = new CountDownLatch(count); var start = new CountDownLatch(1);
        var successes = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(count)) {
            for (int i=0;i<count;i++) pool.submit(() -> {
                ready.countDown();
                try { start.await(); gate.consume(); successes.incrementAndGet(); }
                catch (Exception ignored) { }
            });
            ready.await(); start.countDown(); pool.shutdown();
            while (!pool.isTerminated()) Thread.sleep(5);
        }
        assertEquals(1, successes.get());
        assertTrue(gate.consumed());
    }

    @Test void unavailableAndExpiredGateDoNotConsumeAndContextExcludesLogsExactly() throws Exception {
        var fixture = JSON.readTree(CASE);
        var expected = ReporterLiveBrowserGuard.expectedSixFields(fixture);
        assertEquals(6, expected.size());
        assertFalse(expected.containsKey("textLogs"));
        String good = JSON.writeValueAsString(expected);
        assertTrue(ReporterLiveBrowserGuard.sameContext(good, fixture));
        assertFalse(ReporterLiveBrowserGuard.sameContext(good.substring(0, good.length()-1) + ",\"textLogs\":\"ignored\"}", fixture));

        var gate = new ReporterLiveBrowserGuard.OneUseGate(NOW.plusSeconds(60), CLOCK);
        gate.setAvailable(false);
        assertThrows(IllegalStateException.class, gate::consume);
        assertFalse(gate.consumed());
        var expired = new ReporterLiveBrowserGuard.OneUseGate(NOW.minusSeconds(1), CLOCK);
        assertThrows(IllegalStateException.class, expired::consume);
        assertFalse(expired.consumed());
    }

    private Path approval(Map<String,?> overrides) throws Exception {
        Path path = temp.resolve("approval.json");
        Map<String,Object> fields = new java.util.LinkedHashMap<>();
        fields.put("approved", true); fields.put("accountReference", "synthetic-reporter-1"); fields.put("environmentReference", "isolated-local");
        fields.put("model", "configured-model"); fields.put("credentialSource", "terminal-environment"); fields.put("maxAttempts", 1);
        fields.put("maxResourceUnits", 9200); fields.put("expiresAt", "2026-11-09T03:10:00Z"); fields.put("caseId", "RAI-BROWSER-01");
        fields.put("promptVersion", "report-improvement-v2"); fields.put("fixtureSha256", "6502ba11a271b84a3d760203d9998a285b4cd27fd1847e8dc0799fc3b2cd5faf");
        fields.put("candidateRevision", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        fields.put("candidateSourceSha256", ReporterLiveBrowserGuard.sourceIdentity(Path.of("../..")));
        fields.put("stopOnFailure", true);
        overrides.forEach(fields::put);
        Files.writeString(path, JSON.writeValueAsString(fields));
        return path;
    }

    private Map<String,String> env() {
        return Map.of("AI_ENABLED", "true", "TRACEDESK_LIVE_BROWSER_TEST", "true",
                "AI_GLOBAL_DAILY_UNITS", "9200", "AI_ACTOR_DAILY_UNITS", "9200", "SOCLAAS_MODEL", "configured-model",
                "TRACEDESK_LIVE_BROWSER_CANDIDATE", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "AI_SPONSORED_APPROVAL_EXPIRES_AT", "2026-11-09T03:20:00Z");
    }

    private Map<String,String> env(String key, String value) {
        Map<String,String> copy = new java.util.HashMap<>(env()); copy.put(key, value); return copy;
    }

    private void assertRejected(Path approval, Map<String,String> environment, byte[] fixture) {
        assertThrows(IllegalStateException.class, () -> ReporterLiveBrowserGuard.validate(approval, environment, fixture, CLOCK));
    }
}
