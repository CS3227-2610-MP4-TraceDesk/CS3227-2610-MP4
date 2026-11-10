package com.tracedesk.auth.reports.ai;

import com.tracedesk.ai.gateway.SecuredAiGateway;
import com.tracedesk.auth.investigation.application.InvestigationRepository;
import com.tracedesk.auth.reports.application.ReportRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import static com.tracedesk.auth.reports.ai.ReportAiTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QaAiTest {
    private final ReportRepository reports = repository();
    private final InvestigationRepository investigations = mock(InvestigationRepository.class);
    private final Fake provider = new Fake();

    QaAiTest() {
        when(reports.membership(ACTOR, PROJECT)).thenReturn(Optional.of(
                new ReportRepository.Membership(PROJECT, "Project", true, true, true, "QA_REVIEWER")));
        when(reports.detail(ACTOR, PROJECT, SOURCE)).thenReturn(Optional.of(report(fields("Refund"), "READY_FOR_QA", 3)));
        when(investigations.resolutions(ACTOR, PROJECT, SOURCE, 50, null))
                .thenReturn(new InvestigationRepository.ResolutionPage(List.of(resolution(1), resolution(2)), false));
        provider.output = """
                {"checks":["Confirm one row after refresh."],"evidenceGaps":["Record the build tested."]}
                """;
    }

    private InvestigationRepository.Resolution resolution(int revision) {
        return new InvestigationRepository.Resolution(UUID.randomUUID(), SOURCE, ACTOR, revision,
                "Root " + revision, "Deduplicate refunds", "Refresh the list", "PRIVATE-REF-CANARY", NOW);
    }

    private QaAiService service() {
        var ledger = ledger();
        return new QaAiService(reports, investigations, (actors, authorizer, contexts) ->
                new SecuredAiGateway(actors, authorizer, contexts, provider, ledger, POLICY, CLOCK));
    }

    @Test void usesOnlyAllowedContextAndLatestResolution() {
        try (var service = service()) {
            var result = service.review(PROJECT, SOURCE, 3, () -> ACTOR);
            assertEquals("QA_PLANNING", result.capability());
            assertEquals(3, result.sourceVersion());
            assertEquals(List.of("Confirm one row after refresh."), result.checks());
            var data = ReportAiOutput.JSON.readTree(provider.context.dataJson());
            assertEquals(Set.of("report", "resolution"), data.propertyNames());
            assertEquals("Root 2", data.path("resolution").path("rootCause").asString());
            assertFalse(provider.context.dataJson().contains("CANARY"));
            assertFalse(provider.context.dataJson().contains(ACTOR.toString()));
            assertEquals(1, provider.calls.get());
        }
    }

    @Test void deniesWrongProjectRoleAndVersionBeforeSelectingResolution() {
        try (var service = service()) {
            failure("NOT_FOUND", () -> service.review(UUID.randomUUID(), SOURCE, 3, () -> ACTOR));
            failure("STALE_VERSION", () -> service.review(PROJECT, SOURCE, 2, () -> ACTOR));
            when(reports.membership(ACTOR, PROJECT)).thenReturn(Optional.of(
                    new ReportRepository.Membership(PROJECT, "Project", true, true, true, "DEVELOPER")));
            failure("FORBIDDEN", () -> service.review(PROJECT, SOURCE, 3, () -> ACTOR));
            verifyNoInteractions(investigations);
            assertEquals(0, provider.calls.get());
        }
    }

    @Test void followUpUsesBoundedReviewContextAndValidatesTheReply() {
        try (var service = service()) {
            var followUp = ReportAiOutput.JSON.readTree("""
                    {"question":"How do I check a second refund?","plan":{"checks":["Refresh once"],"evidenceGaps":[]},
                    "history":[{"question":"Which build?","answer":"Record the build number."}]}
                    """);
            provider.output = """
                    {"answer":"Compare both distinct refund IDs after refresh.","checks":["Refresh a payment with two refunds."],"evidenceGaps":[]}
                    """;
            var result = service.review(PROJECT, SOURCE, 3, followUp, () -> ACTOR);
            assertEquals("qa-follow-up-v1", result.promptVersion());
            assertEquals("Compare both distinct refund IDs after refresh.", result.answer());
            var data = ReportAiOutput.JSON.readTree(provider.context.dataJson());
            assertEquals(followUp, data.path("followUp"));
            assertEquals("Root 2", data.path("resolution").path("rootCause").asString());
            assertFalse(provider.context.dataJson().contains("CANARY"));
            provider.output = "{\"answer\":\" \u2002\",\"checks\":[],\"evidenceGaps\":[]}";
            failure("AI_INVALID_OUTPUT", () -> service.review(PROJECT, SOURCE, 3, followUp, () -> ACTOR));
        }
    }

    @Test void rechecksVersionBeforeDeliveryAndRejectsMalformedAdvice() {
        try (var service = service()) {
            provider.output = """
                    {"checks":["Check once"],"evidenceGaps":[],"close":true}
                    """;
            failure("AI_INVALID_OUTPUT", () -> service.review(PROJECT, SOURCE, 3, () -> ACTOR));
            provider.after = () -> when(reports.detail(ACTOR, PROJECT, SOURCE))
                    .thenReturn(Optional.of(report(fields("Updated"), "READY_FOR_QA", 4)));
            failure("STALE_VERSION", () -> service.review(PROJECT, SOURCE, 3, () -> ACTOR));
            assertEquals(2, provider.calls.get());
        }
    }
}
