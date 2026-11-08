package com.tracedesk.ai.gateway;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class GatewayTypes {
    private GatewayTypes() { }
    public enum Capability { REPORT_IMPROVEMENT, INVESTIGATION, QA_PLANNING }
    public enum Outcome { SUCCESS, FAILURE, UNKNOWN, CANCELLED }
    public record Actor(UUID id) { }
    public record Request(UUID projectId, UUID sourceId, Capability capability, long expectedSourceVersion) { }
    public record Context(long sourceVersion, String promptVersion, String systemInstruction, String dataJson) {
        @Override public String toString() { return "Context[redacted]"; }
    }
    public record Usage(long inputTokens, long outputTokens) { }
    public record Completion(String text, Usage usage) {
        @Override public String toString() { return "Completion[redacted]"; }
    }
    public record Suggestion(String text, Capability capability, String promptVersion, long sourceVersion) {
        @Override public String toString() { return "Suggestion[redacted]"; }
    }
    /** Revisions are server-owned; funding is a validated resource-unit policy, not currency. */
    public record Metadata(String model, long revision, Instant validUntil, long fundingRevision) { }
    public record Policy(boolean enabled, long revision, long globalDailyUnits, long actorDailyUnits,
                         int globalConcurrency, int actorConcurrency, Duration reservationTtl) {
        public static Policy disabled() { return new Policy(false, 1, 0, 0, 4, 1, Duration.ofSeconds(60)); }
    }
    public record Ticket(UUID id, long revision, long circuitRevision, long reservedUnits, Instant expiresAt) { }

    public static void validatePolicy(Policy p) {
        validatePolicyShape(p);
        if (!p.enabled()) throw new GatewayFailure(GatewayFailure.Code.AI_DISABLED);
    }
    public static void validatePolicyShape(Policy p) {
        if (p == null || p.revision() < 1 || p.reservationTtl() == null
                || p.reservationTtl().compareTo(Duration.ofSeconds(31)) < 0
                || p.reservationTtl().compareTo(Duration.ofSeconds(60)) > 0
                || p.globalConcurrency() < 1 || p.globalConcurrency() > 4 || p.actorConcurrency() != 1
                || p.globalDailyUnits() < 0 || p.globalDailyUnits() > 1_000_000_000_000L
                || p.actorDailyUnits() < 0 || p.actorDailyUnits() > p.globalDailyUnits()
                || (p.enabled() && p.actorDailyUnits() == 0)) throw new GatewayFailure(GatewayFailure.Code.INVALID_REQUEST);
    }
    public static void validateMetadata(Metadata m, Instant now) {
        if (m == null || m.model() == null || !m.model().matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")
                || m.revision() < 1 || m.fundingRevision() < 1 || m.validUntil() == null
                || !m.validUntil().isAfter(now) || m.validUntil().isAfter(now.plusSeconds(900)))
            throw new GatewayFailure(GatewayFailure.Code.AI_UNAVAILABLE);
    }
}
