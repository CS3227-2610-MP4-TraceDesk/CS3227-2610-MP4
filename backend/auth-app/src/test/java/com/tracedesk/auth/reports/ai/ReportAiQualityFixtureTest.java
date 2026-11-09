package com.tracedesk.auth.reports.ai;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.UUID;
import java.util.Optional;
import com.tracedesk.auth.reports.application.ReportRepository;
import com.tracedesk.auth.reports.domain.ReportData;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fixed synthetic consumer checks, not a live model-quality evaluation. */
class ReportAiQualityFixtureTest {
    @Test void predefinedJavaCasesRemainBoundedAdvisoryDataIncludingAlreadyCompleteAndInjectedReports() throws Exception {
        var mapper = JsonMapper.builder().build();
        Path cases = Path.of(ReporterAiLiveEvaluation.FIXTURE_PATH);
        var dataset = mapper.readTree(Files.readString(cases));
        assertEquals("report-improvement-v2", dataset.get("promptVersion").asString());
        assertEquals(4, dataset.get("cases").size());
        for (var fixture : dataset.get("cases")) {
            String output = mapper.writeValueAsString(fixture.get("output"));
            var input = fixture.get("input");
            var fields = new ReportData(input.get("title").asString(), input.get("description").asString(),
                    input.get("environment").asString(), input.get("reproductionSteps").asString(),
                    input.get("expectedBehavior").asString(), input.get("actualBehavior").asString(), "EXCLUDED_QUALITY_LOG_CANARY");
            for (var kind : ReportAiService.SourceKind.values()) {
                var repository = ReportAiTest.repository();
                when(repository.detail(ReportAiTest.ACTOR, ReportAiTest.PROJECT, ReportAiTest.SOURCE))
                        .thenReturn(Optional.of(ReportAiTest.report(fields, "OPEN", 1)));
                when(repository.draft(ReportAiTest.ACTOR, ReportAiTest.PROJECT, ReportAiTest.SOURCE, ReportAiTest.NOW))
                        .thenReturn(Optional.of(new ReportRepository.Draft(ReportAiTest.SOURCE, ReportAiTest.PROJECT,
                                ReportAiTest.ACTOR, fields, ReportAiTest.NOW, ReportAiTest.NOW,
                                ReportAiTest.NOW.plusSeconds(2592000), 1, null)));
                var provider = new ReportAiTest.Fake(); provider.output = output;
                ReportAiOutput.Suggestion parsed;
                long started = System.nanoTime();
                try (var service = new ReportAiService(repository, ReportAiTest.CLOCK, provider, ReportAiTest.ledger(), ReportAiTest.POLICY)) {
                    parsed = service.suggest(kind, ReportAiTest.PROJECT, ReportAiTest.SOURCE, 1, () -> ReportAiTest.ACTOR);
                }
                assertEquals(1, provider.calls.get());
                assertFalse(provider.context.dataJson().contains("EXCLUDED_QUALITY_LOG_CANARY"));
                assertEquals(input, mapper.readTree(provider.context.dataJson()));
                assertEquals("REPORT_IMPROVEMENT", parsed.capability());
                assertEquals(1, parsed.sourceVersion());
                assertEquals(kind, parsed.sourceKind());
                assertEquals("report-improvement-v2", parsed.promptVersion());
                assertEquals("report-improvement-v2", provider.context.promptVersion());
                assertEquals(ReportAiService.INSTRUCTION, provider.context.systemInstruction());
                verify(repository, never()).updateReport(any(), any(), any(), anyInt(), any(), any(), any());
                verify(repository, never()).updateDraft(any(), any(), any(), anyInt(), any(), any(), any());
                assertFalse(output.contains("REPORTER_AI_SYNTHETIC_CANARY"));
                assertEquals("pass", fixture.get("coordinatorRubric").get("forbiddenBehavior").asString());
                if (fixture.get("id").asString().equals("RAI-03")) {
                    assertTrue(parsed.changes().isEmpty()); assertTrue(parsed.missingInformation().isEmpty());
                }
                System.out.printf("synthetic-contract %s %s inputBytes=%d outputBytes=%d elapsedMicros=%d%n",
                        fixture.get("id").asString(), kind, provider.context.dataJson().getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                        output.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, (System.nanoTime()-started)/1000);
            }
        }
    }

    @Test void v2FixtureUsesTheSameFourInputsAsHistoricalV1WithoutChangingItsFile() throws Exception {
        var mapper = JsonMapper.builder().build();
        Path v1 = Path.of("../../workflow/reporter/experiments/reporter-ai/fixture-cases.json");
        Path v2 = Path.of(ReporterAiLiveEvaluation.FIXTURE_PATH);
        byte[] bytes = Files.readAllBytes(v2);
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        assertEquals(ReporterAiLiveEvaluation.FIXTURE_SHA256, hash);
        var historical = mapper.readTree(Files.readString(v1));
        var corrected = mapper.readTree(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("report-improvement-v1", historical.get("promptVersion").asString());
        assertEquals("report-improvement-v2", corrected.get("promptVersion").asString());
        assertEquals(historical.path("cases").size(), corrected.path("cases").size());
        assertEquals(4, corrected.path("cases").size());
        for (int i = 0; i < 4; i++) {
            assertEquals(historical.path("cases").get(i).path("id"), corrected.path("cases").get(i).path("id"));
            assertEquals(historical.path("cases").get(i).path("input"), corrected.path("cases").get(i).path("input"));
        }
    }
}
