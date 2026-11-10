package com.tracedesk.auth.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Test-classpath only one-use authorization for the explicitly invoked live browser fixture. */
final class ReporterLiveBrowserGuard {
    static final String CASE_ID = "RAI-BROWSER-01";
    static final String PROMPT_VERSION = "report-improvement-v2";
    static final String FIXTURE_SHA256 = "6502ba11a271b84a3d760203d9998a285b4cd27fd1847e8dc0799fc3b2cd5faf";
    static final int MAX_RESOURCE_UNITS = 9200;
    static final Set<String> KEYS = Set.of("approved", "accountReference", "environmentReference", "model",
            "credentialSource", "maxAttempts", "maxResourceUnits", "expiresAt", "caseId", "promptVersion",
            "fixtureSha256", "candidateRevision", "candidateSourceSha256", "stopOnFailure");
    private static final String MANIFEST = "workflow/reporter/evidence/ai-enablement.sha256";
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final Set<String> SIX_FIELDS = Set.of("title", "description", "environment",
            "reproductionSteps", "expectedBehavior", "actualBehavior");

    private ReporterLiveBrowserGuard() { }

    record Authorization(String model, Instant expiresAt, String candidateRevision, String candidateSourceSha256,
                         Path approvalPath, String approvalSha256) {
        @Override public String toString() { return "Authorization[approved live fixture]"; }
    }

    static Authorization validate(Path approval, Map<String, String> env, byte[] fixture, Clock clock) {
        return validate(approval, env, fixture, clock, Path.of("../.."));
    }

    static Authorization validate(Path approval, Map<String, String> env, byte[] fixture, Clock clock, Path repositoryRoot) {
        try {
            if (!"true".equals(env.get("AI_ENABLED")) || !"true".equals(env.get("TRACEDESK_LIVE_BROWSER_TEST")))
                throw denied();
            if (!"9200".equals(env.get("AI_GLOBAL_DAILY_UNITS")) || !"9200".equals(env.get("AI_ACTOR_DAILY_UNITS")))
                throw denied();
            String model = required(env, "SOCLAAS_MODEL");
            String candidate = required(env, "TRACEDESK_LIVE_BROWSER_CANDIDATE");
            String sourceIdentity = sourceIdentity(repositoryRoot);
            if (!candidate.matches("[0-9a-fA-F]{40}")) throw denied();
            Instant sponsorship = Instant.parse(required(env, "AI_SPONSORED_APPROVAL_EXPIRES_AT"));
            if (fixture == null || !FIXTURE_SHA256.equals(sha256(fixture))) throw denied();
            JsonNode fixtureNode = JSON.readTree(fixture);
            if (!CASE_ID.equals(fixtureNode.path("caseId").asString())
                    || !PROMPT_VERSION.equals(fixtureNode.path("promptVersion").asString())
                    || !Set.of("title", "description", "environment", "reproductionSteps", "expectedBehavior", "actualBehavior", "textLogs")
                            .equals(fieldNames(fixtureNode.path("fields")))
                    || !"AI_DO_NOT_SEND_LOG_CANARY".equals(fixtureNode.path("fields").path("textLogs").asString())) throw denied();

            if (Files.size(approval) > 8192) throw denied();
            byte[] approvalBytes = Files.readAllBytes(approval);
            if (approvalBytes.length == 0 || approvalBytes.length > 8192) throw denied();
            JsonNode node = JSON.readTree(approvalBytes);
            if (!node.isObject()) throw denied();
            Set<String> actual = new HashSet<>(node.propertyNames());
            if (!KEYS.equals(actual)
                    || !node.path("approved").isBoolean() || !node.path("approved").asBoolean()
                    || !identifier(node, "accountReference")
                    || !"isolated-local".equals(text(node, "environmentReference"))
                    || !model.equals(text(node, "model"))
                    || !"terminal-environment".equals(text(node, "credentialSource"))
                    || !numberEquals(node.path("maxAttempts"), 1L)
                    || !numberEquals(node.path("maxResourceUnits"), MAX_RESOURCE_UNITS)
                    || !CASE_ID.equals(text(node, "caseId"))
                    || !PROMPT_VERSION.equals(text(node, "promptVersion"))
                    || !FIXTURE_SHA256.equals(text(node, "fixtureSha256"))
                    || !candidate.equals(text(node, "candidateRevision"))
                    || !sourceIdentity.equals(text(node, "candidateSourceSha256"))
                    || !node.path("stopOnFailure").isBoolean() || !node.path("stopOnFailure").asBoolean()) throw denied();
            Instant expiry = Instant.parse(text(node, "expiresAt"));
            if (!expiry.isAfter(clock.instant()) || expiry.isAfter(sponsorship)) throw denied();
            return new Authorization(model, expiry, candidate, sourceIdentity, approval.toAbsolutePath(), sha256(approvalBytes));
        } catch (RuntimeException | IOException failure) {
            if (failure instanceof IllegalStateException state && "Live browser authorization rejected".equals(state.getMessage())) throw state;
            throw denied();
        }
    }

    /** Fingerprints the actual bytes of a bounded, explicitly enumerated source manifest. */
    static String sourceIdentity(Path repositoryRoot) {
        try {
            Path root = repositoryRoot.toRealPath();
            Path manifestRelative = Path.of(MANIFEST);
            Path manifest = safeSourcePath(root, manifestRelative);
            byte[] manifestBytes;
            try (var input = Files.newInputStream(manifest)) { manifestBytes = input.readNBytes(65_537); }
            if (manifestBytes.length > 65_536) throw denied();
            String manifestText = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(manifestBytes)).toString();
            Map<String, String> paths = new java.util.TreeMap<>();
            for (String line : manifestText.split("\\R", -1)) {
                if (line.isEmpty()) continue;
                if (line.length() < 67 || !line.matches("[0-9a-fA-F]{64}  .+")) throw denied();
                String relative = line.substring(66);
                Path source = Path.of(relative);
                if (relative.contains("\\") || source.isAbsolute() || !source.normalize().toString().replace('\\', '/').equals(relative)
                        || relative.equals(MANIFEST) || paths.containsKey(relative)) throw denied();
                for (Path part : source) {
                    String name = part.toString();
                    if (name.equals("..") || name.equals(".") || name.equalsIgnoreCase(".git") || name.startsWith(".env")) throw denied();
                }
                Path actual = safeSourcePath(root, source);
                if (!Files.isRegularFile(actual)) throw denied();
                byte[] sourceBytes;
                try (var input = Files.newInputStream(actual)) { sourceBytes = input.readNBytes(4_194_305); }
                if (sourceBytes.length > 4_194_304) throw denied();
                paths.put(relative, sha256(sourceBytes));
            }
            if (paths.isEmpty()) throw denied();
            StringBuilder canonical = new StringBuilder();
            paths.forEach((relative, hash) -> canonical.append(hash).append("  ").append(relative).append('\n'));
            return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException failure) {
            if (failure instanceof IllegalStateException state && "Live browser authorization rejected".equals(state.getMessage())) throw state;
            throw denied();
        }
    }

    private static Path safeSourcePath(Path root, Path relative) throws IOException {
        if (relative.isAbsolute()) throw denied();
        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root)) throw denied();
        Path cursor = root;
        for (Path component : root.relativize(candidate)) {
            cursor = cursor.resolve(component);
            if (Files.isSymbolicLink(cursor)) throw denied();
        }
        Path real = candidate.toRealPath();
        if (!real.startsWith(root)) throw denied();
        return real;
    }

    /** Atomically moves the validated authorization before any database or catalog operation. */
    static void claim(Authorization authorization) {
        Path claimLock = authorization.approvalPath().resolveSibling(authorization.approvalPath().getFileName() + ".claim");
        boolean acquired = false;
        try {
            Files.createFile(claimLock);
            acquired = true;
            if (Files.size(authorization.approvalPath()) > 8192) throw denied();
            byte[] bytes = Files.readAllBytes(authorization.approvalPath());
            if (!authorization.approvalSha256().equals(sha256(bytes))) throw denied();
            Path claimed = authorization.approvalPath().resolveSibling(authorization.approvalPath().getFileName()
                    + ".consumed-" + java.util.UUID.randomUUID());
            Files.move(authorization.approvalPath(), claimed, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException | UnsupportedOperationException failure) { throw denied(); }
        finally { if (acquired) try { Files.deleteIfExists(claimLock); } catch (IOException ignored) { } }
    }

    static Map<String, String> expectedSixFields(JsonNode fixture) {
        JsonNode fields = fixture.path("fields");
        Map<String, String> expected = new LinkedHashMap<>();
        for (String key : SIX_FIELDS) expected.put(key, fields.path(key).asString());
        return expected;
    }

    static boolean sameContext(String dataJson, JsonNode fixture) {
        try {
            JsonNode actual = JSON.readTree(dataJson);
            if (!SIX_FIELDS.equals(new HashSet<>(actual.propertyNames()))) return false;
            return JSON.readTree(JSON.writeValueAsString(expectedSixFields(fixture))).equals(actual);
        } catch (RuntimeException ignored) { return false; }
    }

    static final class OneUseGate {
        private final AtomicBoolean available = new AtomicBoolean(true);
        private final AtomicBoolean consumed = new AtomicBoolean();
        private final Instant expiry;
        private final Clock clock;
        OneUseGate(Instant expiry, Clock clock) { this.expiry = expiry; this.clock = clock; }
        void setAvailable(boolean value) { available.set(value); }
        void requireAvailable() {
            if (!available.get() || !expiry.isAfter(clock.instant())) throw denied();
        }
        void consume() {
            requireAvailable();
            if (!consumed.compareAndSet(false, true)) throw denied();
        }
        boolean consumed() { return consumed.get(); }
    }

    static IllegalStateException denied() { return new IllegalStateException("Live browser authorization rejected"); }
    private static String required(Map<String,String> env, String key) {
        String value = env.get(key); if (value == null || value.isBlank()) throw denied(); return value;
    }
    private static String text(JsonNode node, String key) {
        JsonNode value = node.get(key); if (value == null || !value.isString()) throw denied(); return value.asString();
    }
    private static boolean identifier(JsonNode node, String key) {
        JsonNode value = node.get(key); return value != null && value.isString()
                && value.asString().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    }
    private static boolean numberEquals(JsonNode value, long expected) {
        return value.isIntegralNumber() && value.canConvertToLong() && value.asLong() == expected;
    }
    private static Set<String> fieldNames(JsonNode node) {
        if (!node.isObject()) return Set.of(); return new HashSet<>(node.propertyNames());
    }
    private static String sha256(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(); }
    }
}
