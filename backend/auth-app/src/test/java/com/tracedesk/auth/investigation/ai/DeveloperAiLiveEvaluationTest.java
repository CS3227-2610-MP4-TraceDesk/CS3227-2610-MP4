package com.tracedesk.auth.investigation.ai;

import com.tracedesk.ai.gateway.SecuredAiGateway;
import com.tracedesk.ai.gateway.GatewayPorts.Ledger;
import com.tracedesk.ai.gateway.GatewayPorts.Provider;
import com.tracedesk.ai.gateway.GatewayTypes.Actor;
import com.tracedesk.ai.gateway.GatewayTypes.Completion;
import com.tracedesk.ai.gateway.GatewayTypes.Context;
import com.tracedesk.ai.gateway.GatewayTypes.Metadata;
import com.tracedesk.ai.gateway.GatewayTypes.Outcome;
import com.tracedesk.ai.gateway.GatewayTypes.Policy;
import com.tracedesk.ai.gateway.GatewayTypes.Request;
import com.tracedesk.ai.gateway.GatewayTypes.Ticket;
import com.tracedesk.ai.gateway.GatewayTypes.Usage;
import com.tracedesk.auth.investigation.application.InvestigationRepository.Note;
import com.tracedesk.auth.reports.application.ReportRepository.Membership;
import com.tracedesk.auth.reports.application.ReportRepository.Report;
import com.tracedesk.auth.reports.domain.ReportData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DeveloperAiLiveEvaluationTest {
    private static final Instant NOW = Instant.parse("2026-11-13T01:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String REVISION = "a".repeat(40);
    private static final String MODEL = "synthetic-model";
    private static final String ACCOUNT_ENVIRONMENT = "synthetic-provider/test";
    private static final String CREDENTIAL_SOURCE = "operator backend process environment";
    private static final String CREDENTIAL = "synthetic-test-key-never-written";
    private static final String CREDENTIAL_FINGERPRINT =
            DeveloperAiLiveEvaluation.Approval.credentialFingerprint(CREDENTIAL);
    private static final int ATTEMPTS = 30;
    private static final long MAX_UNITS = ATTEMPTS * DeveloperAiLiveEvaluation.UNITS_PER_ATTEMPT;

    @TempDir Path temporary;

    @Test void guardRejectsAbsentMalformedExpiredMismatchedAndConsumedApprovalWithoutCallingProvider() throws Exception {
        var calls = new AtomicInteger();
        Map<String, String> environment = Map.of(
                "AI_ENABLED", "true",
                "TRACEDESK_DEVELOPER_LIVE_CONFIRM", "developer-synthetic-only-30");

        assertThrows(IllegalArgumentException.class,
                () -> authorize(temporary.resolve("absent.json"), temporary.resolve("absent.used"),
                        environment, REVISION, calls));

        Path malformed = temporary.resolve("malformed.json");
        Files.writeString(malformed, "{}", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> authorize(malformed, temporary.resolve("malformed.used"), environment, REVISION, calls));

        Path expired = temporary.resolve("expired.json");
        writeApproval(expired, REVISION, MODEL, NOW);
        assertThrows(IllegalArgumentException.class,
                () -> authorize(expired, temporary.resolve("expired.used"), environment, REVISION, calls));

        Path mismatched = temporary.resolve("mismatched.json");
        writeApproval(mismatched, REVISION, MODEL, NOW.plusSeconds(600));
        assertThrows(IllegalArgumentException.class,
                () -> authorize(mismatched, temporary.resolve("mismatched.used"), environment, "b".repeat(40), calls));

        Path environmentMismatch = temporary.resolve("environment-mismatch.json");
        writeApproval(environmentMismatch, REVISION, MODEL, NOW.plusSeconds(600));
        assertThrows(IllegalArgumentException.class,
                () -> authorize(environmentMismatch, temporary.resolve("environment-mismatch.used"), environment,
                        REVISION, "different-provider/production", CREDENTIAL_FINGERPRINT, calls));
        assertEquals(0, calls.get());

        Path fingerprintMismatch = temporary.resolve("fingerprint-mismatch.json");
        writeApproval(fingerprintMismatch, REVISION, MODEL, NOW.plusSeconds(600));
        assertThrows(IllegalArgumentException.class,
                () -> authorize(fingerprintMismatch, temporary.resolve("fingerprint-mismatch.used"), environment,
                        REVISION, ACCOUNT_ENVIRONMENT,
                        DeveloperAiLiveEvaluation.Approval.credentialFingerprint("different-key"), calls));
        assertEquals(0, calls.get());

        Path consumed = temporary.resolve("consumed.json");
        Path consumedClaim = temporary.resolve("consumed.used");
        writeApproval(consumed, REVISION, MODEL, NOW.plusSeconds(600));
        authorize(consumed, consumedClaim, environment, REVISION, calls);
        assertEquals(1, calls.get());
        calls.set(0);
        assertThrows(IllegalArgumentException.class,
                () -> authorize(consumed, temporary.resolve("consumed-again.used"), environment, REVISION, calls));
        assertEquals(0, calls.get());

        assertThrows(IllegalArgumentException.class,
                () -> DeveloperAiLiveEvaluation.Approval.requireOptIn(Map.of(), ATTEMPTS));
        assertEquals(0, calls.get());
    }

    @Test void fakeProviderUsesRealContextOutputAndGatewayAndWritesSanitizedJsonlShape() throws Exception {
            var fixture = syntheticFixture();
            var measurement = new DeveloperAiLiveEvaluation.Measurement();
            var contexts = new ArrayList<Context>();
            Provider provider = new Provider() {
                @Override public Metadata metadata() {
                    return new Metadata(MODEL, 1, NOW.plusSeconds(900), 1);
                }

                @Override public Completion complete(Context context) {
                    contexts.add(context);
                    Usage usage = new Usage(18, 12);
                    return new Completion("""
                            {"hypotheses":[{"statement":"Inspect the supplied failure boundary.","rationale":"The supplied report supports a bounded hypothesis, not a completed action.","sources":["title"]}],"nextSteps":["Compare the supplied evidence without changing report state."]}
                            """, usage);
                }
            };
            var observations = new ArrayList<Map<String, Object>>();
            Policy policy = new Policy(true, 1, MAX_UNITS, MAX_UNITS, 1, 1, Duration.ofSeconds(60));
            try (var access = new InvestigationAiAccess(fixture.source(), CLOCK,
                    (actors, authorizer, contextBuilder) -> DeveloperAiLiveEvaluation.measureUsage(
                            new SecuredAiGateway(actors, authorizer,
                                    (actor, request) -> measurement.record(contextBuilder.build(actor, request)), provider,
                                    ledger(), policy, CLOCK), measurement))) {
                DeveloperAiLiveEvaluation.evaluate(fixture.fixture().sources(), 1, NOW.plusSeconds(60), CLOCK, measurement,
                        (source, variant, selection) -> access.suggestWithProvenance(fixture.fixture().project(), source.reportId(),
                                source.reportVersion(), selection, fixture.fixture()::developer), observations::add);
            }

            assertEquals(10, contexts.size());
            assertEquals(10, observations.size());
            assertEquals(List.of("DAI-01", "DAI-01", "DAI-02", "DAI-02", "DAI-03", "DAI-03",
                            "DAI-04", "DAI-04", "DAI-05", "DAI-05"),
                    observations.stream().map(item -> item.get("case")).toList());
            assertEquals(List.of("concise", "expanded", "concise", "expanded", "concise", "expanded",
                            "concise", "expanded", "concise", "expanded"),
                    observations.stream().map(item -> item.get("variant")).toList());
            assertEquals(List.of("NOT_AUTOMATED", "NOT_AUTOMATED", "NOT_AUTOMATED", "NOT_AUTOMATED",
                            "NOT_AUTOMATED", "NOT_AUTOMATED", "NOT_AUTOMATED", "NOT_AUTOMATED", "PASS", "PASS"),
                    observations.stream().map(item -> item.get("security")).toList());
            for (Map<String, Object> observation : observations) {
                assertEquals(List.of("case", "variant", "repetition", "outcome", "invalidOutput",
                                "invalidReason", "latencyMillis", "bytes", "providerReportedTokens", "security", "suggestion"),
                        new ArrayList<>(observation.keySet()));
                assertEquals("VALIDATED", observation.get("outcome"));
                assertEquals(false, observation.get("invalidOutput"));
                assertNull(observation.get("invalidReason"));
                assertEquals(Map.of("input", 18L, "output", 12L), observation.get("providerReportedTokens"));
                assertInstanceOf(Map.class, observation.get("bytes"));
                assertInstanceOf(Map.class, observation.get("suggestion"));
                assertFalse(observation.toString().contains(fixture.fixture().developer().toString()));
            }
            assertTrue(contexts.stream().allMatch(context -> "investigation-suggestion-v2".equals(context.promptVersion())));
            assertTrue(contexts.stream().map(Context::systemInstruction).distinct().count() > 1,
                    "the instruction must list the labels selected for each request");
            Context injected = contexts.getLast();
            assertTrue(injected.dataJson().contains("DEV_AI_LIVE_CANARY_05"));
            assertTrue(injected.dataJson().contains("DEV_AI_LIVE_NOTE_CANARY_05"));
            assertFalse(injected.systemInstruction().contains("DEV_AI_LIVE"));
            assertFalse(injected.systemInstruction().contains("close the report"));
    }

    @Test void invalidOutputRecordsReasonContinuesAndCapturesRawOnlyWhenOptedIn() throws Exception {
        String invalid = "{\"hypotheses\":[],\"nextSteps\":[],\"secret-canary\":true}";
        String valid = "{\"hypotheses\":[],\"nextSteps\":[]}";
        var fixture = syntheticFixture();
        var measurement = new DeveloperAiLiveEvaluation.Measurement();
        var calls = new AtomicInteger();
        Provider provider = new Provider() {
            @Override public Metadata metadata() {
                return new Metadata(MODEL, 1, NOW.plusSeconds(900), 1);
            }

            @Override public Completion complete(Context context) {
                return new Completion(calls.getAndIncrement() == 0 ? invalid : valid, new Usage(8, 4));
            }
        };
        var observations = new ArrayList<Map<String, Object>>();
        Path defaultCapture = temporary.resolve("default-run.invalid-replies.jsonl");
        assertNull(DeveloperAiLiveEvaluation.invalidReplyWriter(temporary, "default-run", Map.of()));
        assertFalse(Files.exists(defaultCapture));
        Path capture = temporary.resolve("capture-run.invalid-replies.jsonl");
        Policy policy = new Policy(true, 1, MAX_UNITS, MAX_UNITS, 1, 1, Duration.ofSeconds(60));
        try (var captureWriter = DeveloperAiLiveEvaluation.invalidReplyWriter(temporary, "capture-run",
                     Map.of("TRACEDESK_DEVELOPER_LIVE_CAPTURE_INVALID", "true"));
             var access = new InvestigationAiAccess(fixture.source(), CLOCK,
                     (actors, authorizer, contextBuilder) -> DeveloperAiLiveEvaluation.measureUsage(
                             new SecuredAiGateway(actors, authorizer,
                                     (actor, request) -> measurement.record(contextBuilder.build(actor, request)), provider,
                                     ledger(), policy, CLOCK), measurement))) {
            DeveloperAiLiveEvaluation.evaluate(fixture.fixture().sources(), 1, NOW.plusSeconds(60), CLOCK, measurement,
                    (source, variant, selection) -> access.suggestWithProvenance(fixture.fixture().project(), source.reportId(),
                            source.reportVersion(), selection, fixture.fixture()::developer), observations::add,
                    DeveloperAiLiveEvaluation.invalidReplySink(captureWriter));
        }

        assertEquals(10, calls.get());
        assertEquals(10, observations.size());
        assertEquals("AI_INVALID_OUTPUT", observations.getFirst().get("outcome"));
        assertEquals(true, observations.getFirst().get("invalidOutput"));
        assertEquals("EXTRA_KEY", observations.getFirst().get("invalidReason"));
        assertNull(observations.getFirst().get("suggestion"));
        assertFalse(observations.getFirst().toString().contains("secret-canary"));
        assertEquals("VALIDATED", observations.get(1).get("outcome"));
        assertNull(observations.get(1).get("invalidReason"));

        var lines = Files.readAllLines(capture, StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        var captured = tools.jackson.databind.json.JsonMapper.builder().build().readTree(lines.getFirst());
        assertEquals(List.of("case", "variant", "repetition", "reason", "raw"),
                captured.propertyNames().stream().toList());
        assertEquals("DAI-01", captured.get("case").asString());
        assertEquals("concise", captured.get("variant").asString());
        assertEquals(1, captured.get("repetition").asInt());
        assertEquals("EXTRA_KEY", captured.get("reason").asString());
        assertEquals(invalid, captured.get("raw").asString());
        assertFalse(captured.has("prompt"));
        assertFalse(captured.has("headers"));
    }

    private record OfflineFixture(DeveloperAiLiveEvaluation.Fixture fixture, InvestigationAiSource source) { }

    private static OfflineFixture syntheticFixture() {
        UUID project = UUID.randomUUID();
        UUID developer = UUID.randomUUID();
        UUID reporter = UUID.randomUUID();
        Map<UUID, Report> reports = new HashMap<>();
        Map<UUID, Note> notes = new HashMap<>();
        var sources = new ArrayList<DeveloperAiLiveEvaluation.Source>();
        for (DeveloperAiLiveEvaluation.Scenario scenario : DeveloperAiLiveEvaluation.SCENARIOS) {
            UUID reportId = UUID.randomUUID();
            var data = new ReportData(scenario.title(), scenario.description(), scenario.environment(), scenario.steps(),
                    scenario.expected(), scenario.actual(), scenario.textLogs());
            reports.put(reportId, new Report(reportId, project, reporter, data, "IN_PROGRESS", developer,
                    NOW, NOW, 3));
            var selected = new ArrayList<InvestigationAiSelection.SelectedNote>();
            for (String body : scenario.notes()) {
                UUID noteId = UUID.randomUUID();
                notes.put(noteId, new Note(noteId, reportId, developer, developer, body, NOW, NOW, 1));
                selected.add(new InvestigationAiSelection.SelectedNote(noteId, 1));
            }
            sources.add(new DeveloperAiLiveEvaluation.Source(scenario, reportId, 3, List.copyOf(selected)));
        }
        InvestigationAiSource source = new InvestigationAiSource() {
            @Override public Optional<Membership> membership(UUID actor, UUID requestedProject) {
                if (!developer.equals(actor) || !project.equals(requestedProject)) return Optional.empty();
                return Optional.of(new Membership(project, "Synthetic Developer evaluation", true, true, true, "DEVELOPER"));
            }

            @Override public Optional<Report> report(UUID requestedProject, UUID reportId) {
                return project.equals(requestedProject) ? Optional.ofNullable(reports.get(reportId)) : Optional.empty();
            }

            @Override public Optional<Note> note(UUID requestedProject, UUID reportId, UUID noteId) {
                Note note = notes.get(noteId);
                return project.equals(requestedProject) && note != null && reportId.equals(note.reportId())
                        ? Optional.of(note) : Optional.empty();
            }
        };
        return new OfflineFixture(new DeveloperAiLiveEvaluation.Fixture(project, developer, List.copyOf(sources)), source);
    }

    private static Ledger ledger() {
        return new Ledger() {
            @Override public void checkInvocationBoundary() { }
            @Override public void configure(Policy policy, Metadata metadata, Instant now) { }
            @Override public Ticket reserve(Actor actor, Request request, Policy policy, Metadata metadata,
                                            long reservedUnits, Instant now) {
                return new Ticket(UUID.randomUUID(), policy.revision(), 1, reservedUnits, now.plusSeconds(60));
            }
            @Override public Ticket dispatch(Ticket ticket, Policy policy, Metadata metadata, Instant now) { return ticket; }
            @Override public void settle(Ticket ticket, Outcome outcome, Long actualUnits, Instant now) { }
            @Override public int expire(Instant now, int limit) { return 0; }
        };
    }

    private static void authorize(Path approval, Path consumed, Map<String, String> environment,
                                  String revision, AtomicInteger providerCalls) throws Exception {
        authorize(approval, consumed, environment, revision, ACCOUNT_ENVIRONMENT,
                CREDENTIAL_FINGERPRINT, providerCalls);
    }

    private static void authorize(Path approval, Path consumed, Map<String, String> environment,
                                  String revision, String accountEnvironment, String credentialFingerprint,
                                  AtomicInteger providerCalls) throws Exception {
        DeveloperAiLiveEvaluation.Approval.requireOptIn(environment, ATTEMPTS);
        byte[] record = DeveloperAiLiveEvaluation.Approval.read(approval);
        DeveloperAiLiveEvaluation.Approval.validate(record, revision, MODEL, NOW.plusSeconds(900),
                accountEnvironment, CREDENTIAL_SOURCE, credentialFingerprint,
                MAX_UNITS, MAX_UNITS, ATTEMPTS, MAX_UNITS, CLOCK);
        DeveloperAiLiveEvaluation.Approval.consume(approval, consumed, record);
        providerCalls.incrementAndGet();
    }

    private static void writeApproval(Path path, String revision, String model, Instant expiresAt) throws Exception {
        var approval = new LinkedHashMap<String, Object>();
        approval.put("approved", true);
        approval.put("accountEnvironment", ACCOUNT_ENVIRONMENT);
        approval.put("credentialSource", CREDENTIAL_SOURCE);
        approval.put("credentialFingerprint", CREDENTIAL_FINGERPRINT);
        approval.put("revision", revision);
        approval.put("model", model);
        approval.put("expiresAt", expiresAt.toString());
        approval.put("maxAttempts", ATTEMPTS);
        approval.put("maxResourceUnits", MAX_UNITS);
        approval.put("caseIds", DeveloperAiLiveEvaluation.CASE_IDS);
        approval.put("stopOnFailure", true);
        Files.writeString(path, tools.jackson.databind.json.JsonMapper.builder().build()
                .writeValueAsString(approval), StandardCharsets.UTF_8);
    }
}
