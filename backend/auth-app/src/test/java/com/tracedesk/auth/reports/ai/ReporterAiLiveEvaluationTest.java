package com.tracedesk.auth.reports.ai;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class ReporterAiLiveEvaluationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC);
    private static final String APPROVAL = """
            {"approved":true,"accountReference":"operator-account-1","environmentReference":"isolated-local",
            "model":"synthetic-model","credentialSource":"terminal-environment","maxAttempts":24,
            "maxResourceUnits":220800,"expiresAt":"2026-11-09T00:30:00Z",
            "caseIds":["RAI-01","RAI-02","RAI-03","RAI-04"],"stopOnFailure":true}
            """;
    @Test void liveRunnerCannotStartFromOrdinaryCiOrIncompleteOperatorAuthorization() {
        for (var env : java.util.List.of(Map.<String, String>of(),
                Map.of("AI_ENABLED", "true"),
                Map.of("AI_ENABLED", "true", "TRACEDESK_AI_LIVE_CONFIRM", "synthetic-only-24"),
                Map.of("AI_ENABLED", "true", "TRACEDESK_AI_LIVE_CONFIRM", "synthetic-only-24", "TRACEDESK_AI_LIVE_MAX_CALLS", "25"))) {
            var failure = assertThrows(IllegalArgumentException.class, () -> ReporterAiLiveEvaluation.requireAuthorization(env));
            assertNull(failure.getCause());
        }
        assertDoesNotThrow(() -> ReporterAiLiveEvaluation.requireAuthorization(Map.of("AI_ENABLED", "true",
                "TRACEDESK_AI_LIVE_CONFIRM", "synthetic-only-24", "TRACEDESK_AI_LIVE_MAX_CALLS", "24")));
    }

    @Test void operatorRecordBindsModelCasesExpiryAndFiniteResourceAllowance() {
        var sponsorExpiry = CLOCK.instant().plusSeconds(3600);
        assertEquals(CLOCK.instant().plusSeconds(1800), ReporterAiLiveEvaluation.requireApproval(
                APPROVAL, "synthetic-model", sponsorExpiry, 220800, 220800, CLOCK));
        for (String invalid : java.util.List.of(APPROVAL.replace("\"approved\":true", "\"approved\":false"),
                APPROVAL.replace("synthetic-model", "another-model"), APPROVAL.replace("RAI-04", "RAI-99"),
                APPROVAL.replace("220800", "999999"), APPROVAL.replace("24,", "25,"),
                APPROVAL.replace("00:30:00", "00:00:00"), APPROVAL.replace("\"stopOnFailure\":true", "\"stopOnFailure\":false"),
                APPROVAL.replace("\"approved\":true", "\"approved\":true,\"approved\":true"),
                APPROVAL.replace("\"approved\":true", "\"extra\":0,\"approved\":true"), APPROVAL + " {}")) {
            assertThrows(IllegalArgumentException.class, () -> ReporterAiLiveEvaluation.requireApproval(
                    invalid, "synthetic-model", sponsorExpiry, 220800, 220800, CLOCK));
        }
        assertThrows(IllegalArgumentException.class, () -> ReporterAiLiveEvaluation.requireApproval(
                APPROVAL, "synthetic-model", CLOCK.instant().plusSeconds(60), 220800, 220800, CLOCK));
        assertThrows(IllegalArgumentException.class, () -> ReporterAiLiveEvaluation.requireApproval(
                APPROVAL, "synthetic-model", sponsorExpiry, 220801, 220800, CLOCK));
    }
}
