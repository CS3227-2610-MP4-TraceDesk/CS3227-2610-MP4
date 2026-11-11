package com.tracedesk.auth.reports.ai;

import com.tracedesk.ai.internal.soclaas.SoclaasSettings;
import com.tracedesk.auth.ai.configuration.SoclaasEnablementManager;
import com.tracedesk.auth.ai.configuration.LiveEvaluationManager;
import com.tracedesk.auth.ai.persistence.JdbcAiLedger;
import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.reports.persistence.ReportStore;
import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.security.MessageDigest;
import java.util.*;

/** Explicit opt-in operator tool; never part of tests/CI or the production runtime classpath. */
public final class ReporterAiLiveEvaluation {
    static final int MAX_CALLS = 24;
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    static final String FIXTURE_PATH = "../../workflow/reporter/experiments/reporter-ai/fixture-cases-v2.json";
    static final String FIXTURE_SHA256 = "49ba74abf137004141ebfcfe8a5c50e77a447f71aa122dea7fd2df63b7389164";
    private static final List<String> CASE_IDS = List.of("RAI-01", "RAI-02", "RAI-03", "RAI-04");

    private ReporterAiLiveEvaluation() { }

    static void requireAuthorization(Map<String, String> env) {
        if (!"synthetic-only-24".equals(env.get("TRACEDESK_AI_LIVE_CONFIRM"))
                || !"24".equals(env.get("TRACEDESK_AI_LIVE_MAX_CALLS"))
                || !"true".equals(env.get("AI_ENABLED"))) {
            throw new IllegalArgumentException("Live evaluation requires explicit synthetic-only 24-call authorization");
        }
    }

    static Instant requireApproval(String text, String model, Instant sponsorExpiry, long actorQuota, long globalQuota, Clock clock) {
        try {
            if (text.getBytes(StandardCharsets.UTF_8).length > 8192) throw new IllegalArgumentException();
            var approval = JSON.readTree(text);
            var keys = new HashSet<String>(); approval.propertyNames().forEach(keys::add);
            if (!approval.isObject() || !keys.equals(Set.of("approved", "accountReference", "environmentReference", "model",
                    "credentialSource", "maxAttempts", "maxResourceUnits", "expiresAt", "caseIds", "stopOnFailure"))
                    || !approval.path("approved").isBoolean() || !approval.path("approved").asBoolean()
                    || !approval.path("stopOnFailure").isBoolean() || !approval.path("stopOnFailure").asBoolean()) throw new IllegalArgumentException();
            for (String field : List.of("accountReference", "environmentReference")) {
                if (!approval.path(field).isString() || !approval.path(field).asString().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) throw new IllegalArgumentException();
            }
            if (!approval.path("model").isString() || !model.equals(approval.path("model").asString())
                    || !approval.path("credentialSource").isString()
                    || !Set.of("terminal-environment", "deployment-secret-store").contains(approval.path("credentialSource").asString())
                    || !approval.path("maxAttempts").isIntegralNumber() || approval.path("maxAttempts").asLong() != MAX_CALLS
                    || !approval.path("maxResourceUnits").isIntegralNumber() || approval.path("maxResourceUnits").asLong() != MAX_CALLS * 9200L
                    || actorQuota != MAX_CALLS * 9200L || globalQuota != MAX_CALLS * 9200L) throw new IllegalArgumentException();
            var ids = approval.path("caseIds");
            if (!ids.isArray() || ids.size() != CASE_IDS.size()) throw new IllegalArgumentException();
            for (int i = 0; i < CASE_IDS.size(); i++) if (!ids.get(i).isString() || !CASE_IDS.get(i).equals(ids.get(i).asString())) throw new IllegalArgumentException();
            if (!approval.path("expiresAt").isString()) throw new IllegalArgumentException();
            Instant expiry = Instant.parse(approval.path("expiresAt").asString());
            if (!expiry.isAfter(clock.instant()) || expiry.isAfter(sponsorExpiry)) throw new IllegalArgumentException();
            return expiry;
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("A valid bounded operator approval record is required");
        }
    }

    public static void main(String[] args) {
        try {
            if (args.length != 0) throw new IllegalArgumentException("No arguments accepted");
            run(System.getenv());
        } catch (Exception failure) {
            // Exceptions from network/SQL/filesystem must not echo credentials or content.
            System.err.println("Live evaluation stopped. No automatic retry. Check configuration and local sanitized results.");
            System.exit(1);
        }
    }

    private static void run(Map<String, String> env) throws Exception {
        requireAuthorization(env);
        var settings = SoclaasSettings.from(env);
        var clock = Clock.systemUTC();
        Path approvalPath = Path.of("../../workflow/reporter/runs/ai-live-approval.json");
        if (Files.size(approvalPath) > 8192) throw new IllegalArgumentException("Approval record too large");
        String approvalJson = Files.readString(approvalPath);
        Instant runExpiry = requireApproval(approvalJson, settings.model(), settings.approvalValidUntil(),
                settings.policy().actorDailyUnits(), settings.policy().globalDailyUnits(), clock);
        byte[] fixtureBytes = Files.readAllBytes(Path.of(FIXTURE_PATH));
        var fixtures = JSON.readTree(new String(fixtureBytes, StandardCharsets.UTF_8));
        String fixtureHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fixtureBytes));
        if (!FIXTURE_SHA256.equals(fixtureHash)) throw new IllegalStateException("Fixture changed; review required before live execution");
        if (fixtures.path("cases").size() != 4 || !ReportAiService.PROMPT_VERSION.equals(fixtures.path("promptVersion").asString())) {
            throw new IllegalStateException("Unexpected fixture identity");
        }
        Path output = Path.of("../../workflow/reporter/runs/ai-live-" + UUID.randomUUID() + ".jsonl");
        Files.createDirectories(output.getParent());
        // Claim once before DB/provider work: a failed setup cannot silently reuse approval.
        Files.move(approvalPath, output.resolveSibling(output.getFileName() + ".approval-consumed"), StandardCopyOption.ATOMIC_MOVE);
        // CREATE_NEW prevents overwriting previous observations. This directory is gitignored.
        try (var observations = Files.newBufferedWriter(output, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
             var db = IsolatedPostgres.open(env)) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
            var sql = new JdbcTemplate(db);
            var ledger = new JdbcAiLedger(sql);
            UUID actor = UUID.randomUUID(), project = UUID.randomUUID();
            sql.update("insert into td_users(id,login_normalized,password_hash,account_role) values(?,'live-synthetic@example.test','unusable-synthetic-hash','REPORTER')", actor);
            sql.update("insert into td_projects(id,name) values(?,'Synthetic live evaluation')", project);
            sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,'REPORTER','ACTIVE')", project, actor);
            var diagnostic = new java.util.concurrent.atomic.AtomicReference<ReportAiOutput.Diagnostic>();
            try (var manager = LiveEvaluationManager.open(settings, ledger, clock);
                 var service = new ReportAiService(new ReportStore(sql), clock, manager::createGateway, diagnostic::set)) {
                manager.refreshNow();
                if (manager.status() != SoclaasEnablementManager.Status.AVAILABLE) throw new IllegalStateException("AI not available");
                int calls = 0;
                for (var fixture : fixtures.path("cases")) {
                    for (String variant : List.of("concise", "expanded")) {
                        for (int repetition = 1; repetition <= 3; repetition++) {
                            if (calls >= MAX_CALLS || !runExpiry.isAfter(clock.instant())) throw new IllegalStateException("Run authorization exhausted");
                            var input = fixture.path("input");
                            var fields = new LinkedHashMap<String, String>();
                            for (String field : List.of("title", "description", "environment", "reproductionSteps", "expectedBehavior", "actualBehavior")) {
                                fields.put(field, input.path(field).asString());
                            }
                            if (variant.equals("expanded")) {
                                // Same facts and field allowlist: deliberately redundant expanded-context baseline.
                                fields.put("description", fields.get("description") + "\nSupplied context recap: "
                                        + "environment=" + fields.get("environment") + "; reproduction=" + fields.get("reproductionSteps")
                                        + "; expected=" + fields.get("expectedBehavior") + "; actual=" + fields.get("actualBehavior"));
                            }
                            UUID source = UUID.randomUUID();
                            var now = clock.instant();
                            sql.update("insert into td_report_drafts(id,project_id,owner_user_id,title,description,environment,reproduction_steps,expected_behavior,actual_behavior,text_logs,created_at,updated_at,expires_at) values(?,?,?,?,?,?,?,?,?,'EXCLUDED_LIVE_LOG_CANARY',?,?,?)",
                                    source, project, actor, fields.get("title"), fields.get("description"), fields.get("environment"), fields.get("reproductionSteps"), fields.get("expectedBehavior"), fields.get("actualBehavior"),
                                    Timestamp.from(now), Timestamp.from(now), Timestamp.from(now.plusSeconds(2592000)));
                            var observation = new LinkedHashMap<String, Object>();
                            diagnostic.set(null); // one diagnostic slot per explicit attempt
                            observation.put("case", fixture.path("id").asString());
                            observation.put("variant", variant); observation.put("repetition", repetition);
                            observation.put("model", settings.model()); observation.put("promptVersion", ReportAiService.PROMPT_VERSION);
                            observation.put("fixtureSha256", fixtureHash);
                            observation.put("approvalSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(approvalJson.getBytes(StandardCharsets.UTF_8))));
                            observation.put("inputDataBytes", JSON.writeValueAsString(fields).getBytes(StandardCharsets.UTF_8).length);
                            observation.put("providerCost", "unknown"); observation.put("providerTokenBreakdown", "unknown");
                            observation.put("qualityRubric", "pending independent assessment");
                            long started = System.nanoTime();
                            calls++; // An uncertain failed attempt consumes this operator cap; never retry it.
                            boolean failed = false;
                            try {
                                var result = service.suggest(ReportAiService.SourceKind.DRAFT, project, source, 1, () -> actor);
                                observation.put("outcome", "VALIDATED");
                                observation.put("changes", result.changes()); observation.put("missingInformation", result.missingInformation());
                                observation.put("validatedSuggestionBytes", JSON.writeValueAsString(result).getBytes(StandardCharsets.UTF_8).length);
                            } catch (ReportService.Failure safe) {
                                observation.put("outcome", safe.code()); failed = true;
                            }
                            var outputDiagnostic = diagnostic.get();
                            if (outputDiagnostic != null) {
                                observation.put("outputDiagnostic", Map.of("stage", outputDiagnostic.stage().name(), "reason", outputDiagnostic.reason().name()));
                            }
                            observation.put("elapsedMillis", (System.nanoTime() - started) / 1_000_000);
                            observation.put("accounting", sql.queryForList("select policy_revision,metadata_revision,funding_revision,reserved_units,actual_units,charged_units,outcome from td_ai_tickets where actor_id=? and created_at>=? order by created_at desc,id desc limit 1", actor, Timestamp.from(now)));
                            observations.write(JSON.writeValueAsString(observation)); observations.newLine(); observations.flush();
                            System.out.printf("live-evaluation case=%s variant=%s repetition=%d outcome=%s%n",
                                    fixture.path("id").asString(), variant, repetition, observation.get("outcome"));
                            if (failed) throw new IllegalStateException("Stopped after failed attempt");
                        }
                    }
                }
                System.out.printf("Live evaluation completed %d attempts. Synthetic results saved under workflow/reporter/runs/.%n", calls);
            }
        }
    }
}
