package com.tracedesk.ai.internal.soclaas;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Map;


/** Validated backend-only settings; supplied values never appear in diagnostics. */
public final class SoclaasSettings {
    static final URI ORIGIN = URI.create("https://soclaas-api.comp.nus.edu.sg/v1");
    private final boolean enabled;
    private final String apiKey;
    private final String model;
    private final Map<String, Integer> limits;
    private final com.tracedesk.ai.gateway.GatewayTypes.Policy policy;
    private final long approvalRevision;
    private final long modelRevision;
    private final Instant approvalValidUntil;
    private final String approvalFingerprint;

    private SoclaasSettings(boolean enabled, String apiKey, String model, Map<String, String> environment, long disabledRevision) {
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.model = model;
        if (!enabled) {
            this.policy = new com.tracedesk.ai.gateway.GatewayTypes.Policy(false, disabledRevision, 0, 0, 4, 1, Duration.ofSeconds(60));
            this.approvalRevision = 0;
            this.modelRevision = 0;
            this.approvalValidUntil = null;
            this.approvalFingerprint = null;
        } else {
            long revision = readLong(environment, "AI_POLICY_REVISION", 1, 1_000_000_000L);
            long global = readRequiredLong(environment, "AI_GLOBAL_DAILY_UNITS", 1_000_000_000_000L);
            long actor = readRequiredLong(environment, "AI_ACTOR_DAILY_UNITS", global);
            this.policy = new com.tracedesk.ai.gateway.GatewayTypes.Policy(true, revision, global, actor, 4, 1, Duration.ofSeconds(60));
            String ack = environment.getOrDefault("AI_SPONSORED_ACCESS_ACKNOWLEDGED", "false");
            if (!"true".equals(ack)) throw invalid("AI_SPONSORED_ACCESS_ACKNOWLEDGED");
            String reference = required(environment, "AI_SPONSORED_APPROVAL_REFERENCE");
            this.approvalRevision = readLong(environment, "AI_SPONSORED_APPROVAL_REVISION", 1, 1_000_000_000L);
            this.modelRevision = readLong(environment, "AI_MODEL_REVISION", 1, 1_000_000_000L);
            try { this.approvalValidUntil = Instant.parse(required(environment, "AI_SPONSORED_APPROVAL_EXPIRES_AT")); }
            catch (RuntimeException invalid) { throw invalid("AI_SPONSORED_APPROVAL_EXPIRES_AT"); }
            this.approvalFingerprint = fingerprint(reference);
        }
        // Validated values so later environment mutation cannot alter an active client.
        this.limits = Map.of("AI_CONNECT_TIMEOUT_MS", readLimit(environment, "AI_CONNECT_TIMEOUT_MS", 3000),
                "AI_REQUEST_TIMEOUT_MS", readLimit(environment, "AI_REQUEST_TIMEOUT_MS", 30000),
                "AI_MAX_RESPONSE_BYTES", readLimit(environment, "AI_MAX_RESPONSE_BYTES", 65536),
                "AI_MAX_INPUT_BYTES", readLimit(environment, "AI_MAX_INPUT_BYTES", 16384),
                "AI_MAX_INPUT_TOKENS", readLimit(environment, "AI_MAX_INPUT_TOKENS", 8000),
                "AI_MAX_OUTPUT_BYTES", readLimit(environment, "AI_MAX_OUTPUT_BYTES", 16384),
                "AI_MAX_OUTPUT_TOKENS", readLimit(environment, "AI_MAX_OUTPUT_TOKENS", 1200));
    }

    /** Reads deployment settings without reading environment files or accepting arbitrary hosts. */
    public static SoclaasSettings from(Map<String, String> environment) {
        String flag = environment.getOrDefault("AI_ENABLED", "false");
        if (!flag.equals("true") && !flag.equals("false")) {
            throw invalid("AI_ENABLED");
        }
        if (flag.equals("false")) {
            return new SoclaasSettings(false, "", "", environment,
                    readLong(environment, "AI_POLICY_REVISION", 1, 1_000_000_000L));
        }
        String origin = environment.getOrDefault("SOCLAAS_BASE_URL", ORIGIN.toString());
        if (!origin.equals(ORIGIN.toString())) {
            throw invalid("SOCLAAS_BASE_URL");
        }
        return new SoclaasSettings(true, required(environment, "SOCLAAS_API_KEY"),
                required(environment, "SOCLAAS_MODEL"), environment, 0);
    }

    public boolean enabled() {
        return enabled;
    }

    public String model() {
        return model;
    }


    String apiKey() {
        return apiKey;
    }

    Duration connectTimeout() {
        return Duration.ofMillis(limits.get("AI_CONNECT_TIMEOUT_MS"));
    }

    Duration requestTimeout() {
        return Duration.ofMillis(limits.get("AI_REQUEST_TIMEOUT_MS"));
    }

    int maxResponseBytes() {
        return limits.get("AI_MAX_RESPONSE_BYTES");
    }

    int maxInputBytes() {
        return limits.get("AI_MAX_INPUT_BYTES");
    }

    int maxInputTokens() {
        return limits.get("AI_MAX_INPUT_TOKENS");
    }

    int maxOutputBytes() {
        return limits.get("AI_MAX_OUTPUT_BYTES");
    }

    public int maxOutputTokens() {
        return limits.get("AI_MAX_OUTPUT_TOKENS");
    }

    public com.tracedesk.ai.gateway.GatewayTypes.Policy policy() { return policy; }
    public long approvalRevision() { return approvalRevision; }
    public long modelRevision() { return modelRevision; }
    public Instant approvalValidUntil() { return approvalValidUntil; }
    public String approvalFingerprint() { return approvalFingerprint; }

    private static long readLong(Map<String,String> environment, String name, long fallback, long maximum) {
        try {
            long value = Long.parseLong(environment.getOrDefault(name, Long.toString(fallback)));
            if (value > 0 && value <= maximum) return value;
        } catch (RuntimeException ignored) { }
        throw invalid(name);
    }

    private static long readRequiredLong(Map<String,String> environment, String name, long maximum) {
        if (!environment.containsKey(name)) throw invalid(name);
        return readLong(environment, name, 0, maximum);
    }

    private static String fingerprint(String reference) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(reference.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(); }
    }

    private int readLimit(Map<String, String> environment, String name, int maximum) {
        if (!enabled) {
            return maximum;
        }
        try {
            int value = Integer.parseInt(environment.getOrDefault(name, Integer.toString(maximum)));
            if (value > 0 && value <= maximum) {
                return value;
            }
        } catch (NumberFormatException exception) {
            // Never attach a parser exception, which contains the supplied value.
        }
        throw invalid(name);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank() || value.length() > 4096
                || value.chars().anyMatch(character -> character <= 32 || character >= 127)) {
            throw invalid(name);
        }
        return value;
    }

    private static IllegalArgumentException invalid(String name) {
        return new IllegalArgumentException("Invalid or missing " + name);
    }
}
