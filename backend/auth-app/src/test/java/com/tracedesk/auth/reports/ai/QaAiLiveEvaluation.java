package com.tracedesk.auth.reports.ai;

import com.tracedesk.ai.gateway.GatewayTypes.Context;
import com.tracedesk.ai.internal.soclaas.SoclaasSettings;
import com.tracedesk.auth.ai.configuration.LiveEvaluationManager;
import com.tracedesk.auth.ai.configuration.SoclaasEnablementManager;
import com.tracedesk.auth.ai.persistence.JdbcAiLedger;
import com.tracedesk.auth.investigation.domain.ProposedResolutionData;
import com.tracedesk.auth.investigation.persistence.InvestigationStore;
import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.reports.domain.ReportData;
import com.tracedesk.auth.reports.persistence.ReportStore;
import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Test-classpath operator command. The normal test tasks never invoke main. */
public final class QaAiLiveEvaluation {
    static final List<Scenario> SCENARIOS = List.of(
            new Scenario("QAL-01", "Refund rows duplicate after refresh",
                    "Open a payment with completed refunds and refresh twice.",
                    "Each distinct refund is shown once and the total stays stable.",
                    "A completed refund appears twice after refresh.",
                    "Refresh appends existing rows.", "Replace the current list and preserve distinct refund IDs.",
                    "Compare refund rows and totals before and after refreshing.",
                    "How should QA check that two distinct refunds still appear after refresh?",
                    "distinct|two|multiple|unique", "before|after|total|refresh"),
            new Scenario("QAL-02", "Export date filter includes earlier rows",
                    "Select a start date and export the CSV.",
                    "Rows on the start date are included and earlier rows are excluded.",
                    "Rows from the day before the start date appear in the CSV.",
                    "The export omits the start-date boundary.", "Apply the selected date boundary when building the export.",
                    "A developer tried the export once; no build or boundary results were recorded.",
                    "What evidence would show that the start date itself is included?",
                    "boundary|start date|earlier|before", "evidence|build|csv|record")
    );

    record Scenario(String id, String title, String steps, String expected, String actual,
                    String cause, String fix, String verification, String question,
                    String firstIndicator, String secondIndicator) { }
    record Source(Scenario scenario, UUID id, int version) { }

    @FunctionalInterface
    interface Reviewer {
        QaAiService.Review review(Source source, JsonNode followUp);
    }

    @FunctionalInterface
    interface Accounting {
        Map<String, Object> next();
    }

    @FunctionalInterface
    interface ObservationSink {
        void write(Map<String, Object> observation) throws IOException;
    }

    static final class Measurement {
        private int dataBytes;
        private int instructionBytes;

        Context record(Context context) {
            dataBytes = context.dataJson().getBytes(StandardCharsets.UTF_8).length;
            instructionBytes = context.systemInstruction().getBytes(StandardCharsets.UTF_8).length;
            return context;
        }
    }

    private QaAiLiveEvaluation() { }

    public static void main(String[] args) {
        try {
            if (args.length != 0) throw new IllegalArgumentException("No arguments accepted");
            run(System.getenv());
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            System.err.println("QA evaluation stopped. Check the approval and sanitized observations. No automatic retry.");
            System.exit(1);
        }
    }

    private static void run(Map<String, String> environment) throws Exception {
        QaEvaluationApproval.requireOptIn(environment);
        var settings = SoclaasSettings.from(environment);
        var clock = Clock.systemUTC();
        Path repository = Path.of("../..").toAbsolutePath().normalize();
        String revision = QaEvaluationApproval.cleanRevision(repository);
        Path runDirectory = repository.resolve("workflow/qa/runs");
        Path approval = runDirectory.resolve("qa-live-approval.json");
        byte[] record = QaEvaluationApproval.read(approval);
        Instant expiry = QaEvaluationApproval.validate(record, revision, settings.model(), settings.approvalValidUntil(),
                settings.policy().actorDailyUnits(), settings.policy().globalDailyUnits(), clock);
        var scopedEnvironment = new HashMap<>(environment);
        scopedEnvironment.put("AI_SPONSORED_APPROVAL_EXPIRES_AT", expiry.toString());
        var liveSettings = SoclaasSettings.from(scopedEnvironment);
        String runId = "qa-live-" + UUID.randomUUID();
        QaEvaluationApproval.consume(approval, runDirectory.resolve(runId + ".approval-consumed"), record);
        try (var writer = Files.newBufferedWriter(runDirectory.resolve(runId + ".jsonl"),
                     StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
             var database = IsolatedPostgres.open(environment)) {
            Flyway.configure().dataSource(database).locations("classpath:db/migration").load().migrate();
            var sql = new JdbcTemplate(database);
            var reports = new ReportStore(sql);
            var investigations = new InvestigationStore(sql);
            var fixture = seedFixture(sql, reports, investigations, clock);
            UUID project = fixture.project();
            UUID qa = fixture.qa();
            var measurement = new Measurement();
            Set<UUID> recordedTickets = new HashSet<>();
            if (!expiry.isAfter(clock.instant())) throw new IllegalStateException("QA evaluation approval expired");
            try (var manager = LiveEvaluationManager.open(liveSettings, new JdbcAiLedger(sql), clock);
                 var service = new QaAiService(reports, investigations, (actors, authorizer, contexts) ->
                         manager.createGateway(actors, authorizer, (actor, request) ->
                                 measurement.record(contexts.build(actor, request))))) {
                manager.refreshNow();
                if (manager.status() != SoclaasEnablementManager.Status.AVAILABLE)
                    throw new IllegalStateException("QA evaluation provider unavailable");
                evaluate(fixture.sources(), expiry, clock, revision, settings.model(), measurement,
                        (source, followUp) -> service.review(project, source.id(), source.version(), followUp, () -> qa),
                        () -> account(sql, qa, recordedTickets),
                        observation -> {
                            writer.write(ReportAiOutput.JSON.writeValueAsString(observation));
                            writer.newLine();
                            writer.flush();
                        });
            }
        }
        System.out.println("QA evaluation completed. Sanitized observations are in workflow/qa/runs/.");
    }

    static void evaluate(List<Source> sources, Instant expiry, Clock clock, String revision, String model,
                         Measurement measurement, Reviewer reviewer, Accounting accounting,
                         ObservationSink sink) throws IOException {
        if (!sources.stream().map(source -> source.scenario().id()).toList().equals(QaEvaluationApproval.CASE_IDS))
            throw new IllegalArgumentException("Unexpected QA evaluation cases");
        int attempts = 0;
        for (Source source : sources) {
            JsonNode followUp = null;
            for (int turn = 0; turn < 2; turn++) {
                Instant start = clock.instant();
                if (attempts >= QaEvaluationApproval.ATTEMPTS || !expiry.isAfter(start))
                    throw new IllegalStateException("QA evaluation allowance exhausted");
                attempts++;
                var observation = new LinkedHashMap<String, Object>();
                observation.put("case", source.scenario().id());
                observation.put("attempt", attempts);
                observation.put("startedAt", start.toString());
                observation.put("revision", revision);
                observation.put("model", model);
                observation.put("promptVersion", followUp == null ? QaAiService.PROMPT_VERSION : QaAiService.FOLLOW_UP_VERSION);
                observation.put("providerTokenBreakdown", "unknown");
                measurement.dataBytes = 0;
                measurement.instructionBytes = 0;
                long startedNanos = System.nanoTime();
                QaAiService.Review result = null;
                try {
                    result = reviewer.review(source, followUp);
                    observation.put("outcome", "VALIDATED");
                    observation.put("coverageScore", coverage(source.scenario(), result));
                    observation.put("coverageMaximum", 2);
                    observation.put("semanticFidelity", "not assessed");
                } catch (ReportService.Failure rejected) {
                    observation.put("outcome", safeOutcome(rejected.code()));
                } catch (RuntimeException failure) {
                    observation.put("outcome", "REQUEST_FAILED");
                    result = null;
                }
                observation.put("elapsedMillis", (System.nanoTime() - startedNanos) / 1_000_000);
                observation.put("contextBytes", measurement.dataBytes == 0 ? "unknown" : measurement.dataBytes);
                observation.put("instructionBytes", measurement.instructionBytes == 0 ? "unknown" : measurement.instructionBytes);
                boolean accountingFailed = false;
                try {
                    Map<String, Object> ticket = accounting.next();
                    observation.put("gatewayAccounting", ticket.isEmpty() ? "unknown" : ticket);
                } catch (RuntimeException failure) {
                    observation.put("gatewayAccounting", "unknown");
                    accountingFailed = true;
                }
                sink.write(observation);
                if (result == null || accountingFailed)
                    throw new IllegalStateException("Stopped after a failed QA attempt or measurement");
                if (turn == 0) {
                    followUp = ReportAiOutput.JSON.valueToTree(Map.of(
                            "question", source.scenario().question(),
                            "plan", Map.of("checks", result.checks(), "evidenceGaps", result.evidenceGaps()),
                            "history", List.of()));
                }
            }
        }
    }

    static Map<String, Object> account(JdbcTemplate sql, UUID actor, Set<UUID> recordedTickets) {
        var newTickets = sql.query("""
                select id, reserved_units, actual_units, charged_units, outcome
                from td_ai_tickets where actor_id=? and capability='QA_PLANNING'
                """, (rows, index) -> {
                    var accounting = new LinkedHashMap<String, Object>();
                    accounting.put("reserved_units", rows.getLong("reserved_units"));
                    accounting.put("actual_units", rows.getObject("actual_units"));
                    accounting.put("charged_units", rows.getObject("charged_units"));
                    accounting.put("outcome", rows.getString("outcome"));
                    return Map.entry(rows.getObject("id", UUID.class), accounting);
                }, actor).stream().filter(ticket -> !recordedTickets.contains(ticket.getKey())).toList();
        if (newTickets.isEmpty()) return Map.of();
        if (newTickets.size() != 1) throw new IllegalStateException("Unexpected QA evaluation ticket count");
        var ticket = newTickets.getFirst();
        recordedTickets.add(ticket.getKey());
        return ticket.getValue();
    }

    private static String safeOutcome(String code) {
        return switch (code) {
            case "AI_INVALID_OUTPUT", "AI_CONTEXT_TOO_LARGE", "STALE_VERSION", "INVALID_STATE",
                    "FORBIDDEN", "NOT_FOUND", "UNAUTHENTICATED", "AI_UNAVAILABLE" -> code;
            default -> "REQUEST_FAILED";
        };
    }

    private static int coverage(Scenario scenario, QaAiService.Review result) {
        String text = String.join(" ", result.checks()) + " " + String.join(" ", result.evidenceGaps())
                + " " + (result.answer() == null ? "" : result.answer());
        return (indicator(scenario.firstIndicator()).matcher(text).find() ? 1 : 0)
                + (indicator(scenario.secondIndicator()).matcher(text).find() ? 1 : 0);
    }

    private static Pattern indicator(String alternatives) {
        return Pattern.compile("\\b(?:" + alternatives + ")\\b", Pattern.CASE_INSENSITIVE);
    }

    record Fixture(UUID project, UUID qa, List<Source> sources) { }

    static Fixture seedFixture(JdbcTemplate sql, ReportStore reports, InvestigationStore investigations, Clock clock) {
        UUID project = UUID.randomUUID();
        sql.update("insert into td_projects(id,name) values(?,'Synthetic QA evaluation')", project);
        UUID qa = member(sql, project, "QA_REVIEWER", "qa");
        UUID reporter = member(sql, project, "REPORTER", "reporter");
        UUID developer = member(sql, project, "DEVELOPER", "developer");
        var sources = SCENARIOS.stream()
                .map(scenario -> seed(scenario, reports, investigations, project, qa, reporter, developer, clock))
                .toList();
        return new Fixture(project, qa, sources);
    }

    private static UUID member(JdbcTemplate sql, UUID project, String role, String name) {
        UUID id = UUID.randomUUID();
        sql.update("insert into td_users(id,login_normalized,password_hash,account_role) values(?,?,'unusable-synthetic-hash',?)",
                id, name + "-evaluation@example.test", role);
        sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,?,'ACTIVE')",
                project, id, role);
        return id;
    }

    private static Source seed(Scenario scenario, ReportStore reports, InvestigationStore investigations,
                               UUID project, UUID qa, UUID reporter, UUID developer, Clock clock) {
        var fields = new ReportData(scenario.title(), scenario.actual(), "Synthetic QA evaluation",
                scenario.steps(), scenario.expected(), scenario.actual(), "");
        var report = reports.create(reporter, project, UUID.randomUUID(), fields, UUID.randomUUID(), clock.instant());
        var assignment = reports.changeAssignment(qa, project, report.id(), developer, null,
                report.version(), UUID.randomUUID(), clock.instant());
        var started = investigations.start(developer, project, report.id(), assignment.version(),
                UUID.randomUUID(), clock.instant());
        var proposed = investigations.propose(developer, project, report.id(),
                new ProposedResolutionData(scenario.cause(), scenario.fix(), scenario.verification(), ""),
                started.version(), UUID.randomUUID(), clock.instant());
        return new Source(scenario, report.id(), proposed.report().version());
    }
}
