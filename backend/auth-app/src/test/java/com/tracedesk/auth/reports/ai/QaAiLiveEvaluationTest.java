package com.tracedesk.auth.reports.ai;

import com.tracedesk.ai.gateway.SecuredAiGateway;
import com.tracedesk.ai.gateway.GatewayTypes.*;
import com.tracedesk.ai.gateway.GatewayPorts.Provider;
import com.tracedesk.auth.ai.persistence.JdbcAiLedger;
import com.tracedesk.auth.investigation.persistence.InvestigationStore;
import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.reports.persistence.ReportStore;
import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.tracedesk.auth.reports.ai.ReportAiTest.CLOCK;
import static com.tracedesk.auth.reports.ai.ReportAiTest.NOW;
import static com.tracedesk.auth.reports.ai.ReportAiTest.POLICY;
import static org.junit.jupiter.api.Assertions.*;

class QaAiLiveEvaluationTest {
    private static final String REVISION = "a".repeat(40);

    @Test void usesRealStoresAndGatewayWithSyntheticProviderAndSanitizedObservations() throws Exception {
        try (var database = IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(database).locations("classpath:db/migration").load().migrate();
            var sql = new JdbcTemplate(database);
            var reports = new ReportStore(sql);
            var investigations = new InvestigationStore(sql);
            var fixture = QaAiLiveEvaluation.seedFixture(sql, reports, investigations, CLOCK);
            var measurements = new QaAiLiveEvaluation.Measurement();
            var contextsSent = new ArrayList<Context>();
            Provider provider = new Provider() {
                @Override public Metadata metadata() {
                    return new Metadata("synthetic-model", 1, NOW.plusSeconds(900), 1);
                }

                @Override public Completion complete(Context context) {
                    contextsSent.add(context);
                    boolean followUp = QaAiService.FOLLOW_UP_VERSION.equals(context.promptVersion());
                    String body = followUp
                            ? """
                              {"answer":"Check distinct IDs before and after refresh.","checks":[],"evidenceGaps":[]}
                              """
                            : """
                              {"checks":["Compare distinct rows after refresh.","OUTPUT_CANARY"],"evidenceGaps":["Record the CSV boundary evidence."]}
                              """;
                    return new Completion(body, new Usage(20, 20));
                }
            };
            var observations = new ArrayList<Map<String, Object>>();
            var recorded = new HashSet<UUID>();
            try (var service = new QaAiService(reports, investigations, (actors, authorizer, contexts) ->
                    new SecuredAiGateway(actors, authorizer, (actor, request) ->
                            measurements.record(contexts.build(actor, request)), provider, new JdbcAiLedger(sql), POLICY, CLOCK))) {
                QaAiLiveEvaluation.evaluate(fixture.sources(), NOW.plusSeconds(60), CLOCK, REVISION, "synthetic-model",
                        measurements, (source, followUp) -> service.review(fixture.project(), source.id(), source.version(),
                                followUp, fixture::qa),
                        () -> QaAiLiveEvaluation.account(sql, fixture.qa(), recorded), observations::add);
            }

            assertEquals(4, contextsSent.size());
            assertEquals(List.of("QAL-01", "QAL-01", "QAL-02", "QAL-02"),
                    observations.stream().map(item -> item.get("case")).toList());
            assertEquals(List.of(1, 2, 3, 4), observations.stream().map(item -> item.get("attempt")).toList());
            assertEquals(4, recorded.size());
            assertTrue(QaAiLiveEvaluation.account(sql, fixture.qa(), recorded).isEmpty());
            for (int index = 0; index < observations.size(); index++) {
                var observation = observations.get(index);
                var context = contextsSent.get(index);
                assertEquals(context.promptVersion(), observation.get("promptVersion"));
                assertEquals(context.dataJson().getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                        observation.get("contextBytes"));
                assertEquals(context.systemInstruction().getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                        observation.get("instructionBytes"));
                assertEquals("VALIDATED", observation.get("outcome"));
                var ticket = assertInstanceOf(Map.class, observation.get("gatewayAccounting"));
                assertEquals(40L, ticket.get("charged_units"));
                assertFalse(ticket.containsKey("id"));
                assertEquals("unknown", observation.get("providerTokenBreakdown"));
            }
            assertTrue(contextsSent.get(1).dataJson().contains("OUTPUT_CANARY"));
            assertFalse(observations.toString().contains("OUTPUT_CANARY"));
            assertFalse(observations.toString().contains(fixture.qa().toString()));
            assertEquals(2, observations.getFirst().get("coverageScore"));
        }
    }

    @Test void unavailableProviderStopsAfterFirstAttemptAndWritesOnlySafeOutcome() {
        var sources = QaAiLiveEvaluation.SCENARIOS.stream()
                .map(scenario -> new QaAiLiveEvaluation.Source(scenario, UUID.randomUUID(), 4)).toList();
        var observations = new ArrayList<Map<String, Object>>();
        assertThrows(IllegalStateException.class, () -> QaAiLiveEvaluation.evaluate(sources,
                NOW.plusSeconds(60), CLOCK, REVISION, "synthetic-model", new QaAiLiveEvaluation.Measurement(),
                (source, followUp) -> { throw new ReportService.Failure("AI_UNAVAILABLE", 503); }, Map::of, observations::add));
        assertEquals(1, observations.size());
        assertEquals("AI_UNAVAILABLE", observations.getFirst().get("outcome"));
        assertEquals("unknown", observations.getFirst().get("gatewayAccounting"));
    }

    @Test void expiryPreventsDispatch() {
        var sources = QaAiLiveEvaluation.SCENARIOS.stream()
                .map(scenario -> new QaAiLiveEvaluation.Source(scenario, UUID.randomUUID(), 4)).toList();
        assertThrows(IllegalStateException.class, () -> QaAiLiveEvaluation.evaluate(sources,
                NOW, CLOCK, REVISION, "synthetic-model", new QaAiLiveEvaluation.Measurement(),
                (source, followUp) -> fail("No dispatch is allowed"), Map::of, item -> fail("No observation expected")));
    }
}
