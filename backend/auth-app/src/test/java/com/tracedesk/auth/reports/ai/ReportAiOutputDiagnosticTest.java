package com.tracedesk.auth.reports.ai;

import com.tracedesk.ai.gateway.SecuredAiGateway;
import com.tracedesk.auth.reports.application.ReportService;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ReportAiOutputDiagnosticTest {
    private static final String GOOD = "{\"changes\":[{\"field\":\"title\",\"replacement\":\"safe title\"}],\"missingInformation\":[]}";
    private static final ReportAiOutput.Stage SHAPE = ReportAiOutput.Stage.ROLE_RESPONSE_SHAPE;
    private static final ReportAiOutput.Stage CHANGE = ReportAiOutput.Stage.ROLE_CHANGE_VALIDATION;
    private static final ReportAiOutput.Stage QUESTION = ReportAiOutput.Stage.ROLE_QUESTION_VALIDATION;

    @Test void parserReportsOnlyTheFirstFixedStageAndReasonWithoutRetainingCanaries() {
        var cases = new LinkedHashMap<String, ReportAiOutput.Diagnostic>();
        cases.put(null, new ReportAiOutput.Diagnostic(SHAPE, ReportAiOutput.Reason.RESPONSE_INVALID));
        cases.put("[]", new ReportAiOutput.Diagnostic(SHAPE, ReportAiOutput.Reason.RESPONSE_INVALID));
        cases.put("x".repeat(16385), new ReportAiOutput.Diagnostic(SHAPE, ReportAiOutput.Reason.OUTPUT_LIMIT_EXCEEDED));
        cases.put(GOOD.replace("safe title", "x".repeat(161)), new ReportAiOutput.Diagnostic(CHANGE, ReportAiOutput.Reason.REPLACEMENT_INVALID));
        cases.put(GOOD.replace("safe title", "\\u0000"), new ReportAiOutput.Diagnostic(CHANGE, ReportAiOutput.Reason.REPLACEMENT_INVALID));
        cases.put(GOOD.replace("safe title", "\\ud800"), new ReportAiOutput.Diagnostic(CHANGE, ReportAiOutput.Reason.REPLACEMENT_INVALID));
        cases.put("{\"changes\":[],\"changes\":[],\"missingInformation\":[]}", new ReportAiOutput.Diagnostic(SHAPE, ReportAiOutput.Reason.MALFORMED_JSON));
        cases.put(GOOD + " SECRET-TRAILING-CANARY", new ReportAiOutput.Diagnostic(SHAPE, ReportAiOutput.Reason.MALFORMED_JSON));
        cases.put("{}", new ReportAiOutput.Diagnostic(SHAPE, ReportAiOutput.Reason.TOP_LEVEL_KEYS_INVALID));
        cases.put("{\"changes\":{},\"missingInformation\":[]}", new ReportAiOutput.Diagnostic(SHAPE, ReportAiOutput.Reason.CHANGES_CONTAINER_INVALID));
        cases.put("{\"changes\":[],\"missingInformation\":{}}", new ReportAiOutput.Diagnostic(SHAPE, ReportAiOutput.Reason.QUESTIONS_CONTAINER_INVALID));
        cases.put("{\"changes\":[null],\"missingInformation\":[]}", new ReportAiOutput.Diagnostic(CHANGE, ReportAiOutput.Reason.CHANGE_ENTRY_INVALID));
        cases.put(GOOD.replace("title", "arbitrary-secret-field"), new ReportAiOutput.Diagnostic(CHANGE, ReportAiOutput.Reason.CHANGE_FIELD_INVALID));
        cases.put(GOOD.replace("safe title", " "), new ReportAiOutput.Diagnostic(CHANGE, ReportAiOutput.Reason.REPLACEMENT_INVALID));
        cases.put("{\"changes\":[{\"field\":\"title\",\"replacement\":\"A\"},{\"field\":\"title\",\"replacement\":\"B\"}],\"missingInformation\":[]}", new ReportAiOutput.Diagnostic(CHANGE, ReportAiOutput.Reason.CHANGE_FIELD_DUPLICATE));
        cases.put("{\"changes\":[],\"missingInformation\":[7]}", new ReportAiOutput.Diagnostic(QUESTION, ReportAiOutput.Reason.QUESTION_INVALID));
        cases.put("{\"changes\":[],\"missingInformation\":[\"" + "q".repeat(501) + "\"]}", new ReportAiOutput.Diagnostic(QUESTION, ReportAiOutput.Reason.QUESTION_INVALID));
        cases.put("{\"changes\":[],\"missingInformation\":[\"q\",\"q\",\"q\",\"q\",\"q\",\"q\"]}", new ReportAiOutput.Diagnostic(QUESTION, ReportAiOutput.Reason.QUESTION_LIMIT_EXCEEDED));
        for (var entry : cases.entrySet()) {
            var captured = new AtomicReference<ReportAiOutput.Diagnostic>();
            var emissions = new java.util.concurrent.atomic.AtomicInteger();
            var failure = assertThrows(ReportService.Failure.class, () -> ReportAiOutput.parse(entry.getKey(), ReportAiService.SourceKind.REPORT, UUID.randomUUID(), 1, value -> { emissions.incrementAndGet(); captured.set(value); }));
            assertEquals(1, emissions.get());
            assertEquals("AI_INVALID_OUTPUT", failure.code()); assertEquals(502, failure.status()); assertNull(failure.getCause());
            assertEquals(entry.getValue(), captured.get());
            assertFalse(captured.toString().contains("SECRET")); assertFalse(captured.toString().contains("arbitrary-secret-field"));
            assertFalse(failure.getMessage().contains("SECRET"));
        }
    }

    @Test void validEmptyAndOrdinaryOutputDoNotEmitAndThrowingSinkCannotChangePublicFailure() {
        var captured = new AtomicReference<ReportAiOutput.Diagnostic>();
        assertEquals(0, ReportAiOutput.parse("{\"changes\":[],\"missingInformation\":[]}", ReportAiService.SourceKind.REPORT, UUID.randomUUID(), 1, captured::set).changes().size());
        assertNull(captured.get());
        assertEquals("safe title", ReportAiOutput.parse(GOOD, ReportAiService.SourceKind.REPORT, UUID.randomUUID(), 1, captured::set).changes().getFirst().replacement());
        assertNull(captured.get());
        var failure = assertThrows(ReportService.Failure.class, () -> ReportAiOutput.parse("bad", ReportAiService.SourceKind.REPORT, UUID.randomUUID(), 1, ignored -> { throw new IllegalStateException("SECRET-CANARY"); }));
        assertEquals("AI_INVALID_OUTPUT", failure.code()); assertEquals(502, failure.status()); assertNull(failure.getCause());
        assertFalse(failure.toString().contains("SECRET-CANARY"));
    }

    @Test void gatewayOutputRejectionsMapToFixedGatewayDiagnostics() {
        for (var reason : List.of(ReportAiOutput.Reason.OUTPUT_LIMIT_EXCEEDED, ReportAiOutput.Reason.GATEWAY_FORMAT_REJECTED)) {
            var provider = new ReportAiTest.Fake();
            provider.output = reason == ReportAiOutput.Reason.OUTPUT_LIMIT_EXCEEDED ? "x".repeat(16385) : null;
            var captured = new AtomicReference<ReportAiOutput.Diagnostic>();
            var service = new ReportAiService(ReportAiTest.repository(), ReportAiTest.CLOCK,
                    (actors, authorizer, contexts) -> new SecuredAiGateway(actors, authorizer, contexts, provider, ReportAiTest.ledger(), ReportAiTest.POLICY, ReportAiTest.CLOCK), captured::set);
            try (service) {
                var failure = assertThrows(ReportService.Failure.class, () -> service.suggest(ReportAiService.SourceKind.REPORT, ReportAiTest.PROJECT, ReportAiTest.SOURCE, 1, () -> ReportAiTest.ACTOR));
                assertEquals("AI_INVALID_OUTPUT", failure.code()); assertEquals(502, failure.status()); assertNull(failure.getCause());
            }
            assertEquals(1, provider.calls.get());
            assertEquals(new ReportAiOutput.Diagnostic(ReportAiOutput.Stage.GATEWAY_OUTPUT_REJECTED, reason), captured.get());
        }
    }

    @Test void authorizationDenialAndStalenessDoNotEmitDiagnostics() {
        var repo = ReportAiTest.repository();
        var provider = new ReportAiTest.Fake();
        var captured = new AtomicReference<ReportAiOutput.Diagnostic>();
        var service = new ReportAiService(repo, ReportAiTest.CLOCK,
                (actors, authorizer, contexts) -> new SecuredAiGateway(actors, authorizer, contexts, provider, ReportAiTest.ledger(), ReportAiTest.POLICY, ReportAiTest.CLOCK), captured::set);
        try (service) {
            assertEquals("UNAUTHENTICATED", assertThrows(ReportService.Failure.class, () -> service.suggest(ReportAiService.SourceKind.REPORT, ReportAiTest.PROJECT, ReportAiTest.SOURCE, 1, () -> null)).code());
            assertNull(captured.get());
            assertEquals("STALE_VERSION", assertThrows(ReportService.Failure.class, () -> service.suggest(ReportAiService.SourceKind.REPORT, ReportAiTest.PROJECT, ReportAiTest.SOURCE, 2, () -> ReportAiTest.ACTOR)).code());
            assertNull(captured.get());
        }
        assertEquals(0, provider.calls.get());
    }
}
