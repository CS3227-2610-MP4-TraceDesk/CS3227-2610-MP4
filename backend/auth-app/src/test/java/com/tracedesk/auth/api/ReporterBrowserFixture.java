package com.tracedesk.auth.api;

import com.tracedesk.auth.AuthApplication;
import com.tracedesk.auth.persistence.AuthStore;
import com.tracedesk.auth.ai.persistence.JdbcAiLedger;
import com.tracedesk.auth.ai.configuration.LiveEvaluationManager;
import com.tracedesk.auth.ai.configuration.SoclaasEnablementManager;
import com.tracedesk.auth.reports.ai.ReportAiService;
import com.tracedesk.auth.reports.ai.QaAiService;
import com.tracedesk.auth.investigation.application.InvestigationRepository;
import com.tracedesk.ai.gateway.SecuredAiGateway;
import com.tracedesk.auth.reports.application.ReportRepository;
import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.ai.internal.soclaas.SoclaasSettings;
import com.tracedesk.ai.gateway.*;
import com.tracedesk.ai.gateway.GatewayPorts.ContextBuilder;
import com.tracedesk.ai.gateway.GatewayPorts.Provider;
import com.tracedesk.ai.gateway.GatewayTypes.*;
import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.core.env.MapPropertySource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.Set;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;

/** Owned, test-only application process for the same-candidate Reporter browser journey. */
public final class ReporterBrowserFixture {
    private static final int PORT = Integer.parseInt(
            System.getenv().getOrDefault("TRACEDESK_BROWSER_FIXTURE_PORT", "18089"));
    private static final String PASSWORD = "Synthetic-browser-password-2026!";
    private static final AtomicBoolean AI_AVAILABLE = new AtomicBoolean(true);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static volatile boolean LIVE_MODE;
    private static volatile ReporterLiveBrowserGuard.Authorization LIVE_AUTHORIZATION;
    private static volatile ReporterLiveBrowserGuard.OneUseGate LIVE_GATE;
    private static volatile JsonNode LIVE_CASE;
    private static volatile SoclaasEnablementManager LIVE_MANAGER;

    private ReporterBrowserFixture() { }

    public static void main(String[] args) throws Exception {
        LIVE_MODE = "true".equals(System.getenv("TRACEDESK_LIVE_BROWSER_TEST"));
        try {
            runFixture();
        } catch (Throwable failure) {
            if (!LIVE_MODE) {
                if (failure instanceof Exception exception) throw exception;
                if (failure instanceof Error error) throw error;
                throw new IllegalStateException("Fixture failed");
            }
            System.err.println("Live Reporter browser fixture stopped safely.");
            if (LIVE_MANAGER != null) { LIVE_MANAGER.close(); LIVE_MANAGER = null; }
            System.exit(2);
        } finally {
            if (LIVE_MANAGER != null) LIVE_MANAGER.close();
        }
    }

    private static void runFixture() throws Exception {
        Path liveResult = null;
        SoclaasSettings liveSettings = null;
        if (LIVE_MODE) {
            Path approval = Path.of("../../workflow/reporter/runs/ai-live-browser-approval.json");
            byte[] fixtureBytes = Files.readAllBytes(Path.of("../../workflow/reporter/experiments/reporter-ai/browser-live-case.json"));
            var authorization = ReporterLiveBrowserGuard.validate(approval, System.getenv(), fixtureBytes, Clock.systemUTC());
            liveSettings = SoclaasSettings.from(System.getenv());
            liveResult = requiredPath("TRACEDESK_LIVE_BROWSER_LEDGER_RESULT");
            Path runRoot = Path.of("../../workflow/reporter/runs").toAbsolutePath().normalize();
            if (!liveResult.normalize().startsWith(runRoot)) throw ReporterLiveBrowserGuard.denied();
            ReporterLiveBrowserGuard.claim(authorization);
            LIVE_AUTHORIZATION = authorization;
            LIVE_GATE = new ReporterLiveBrowserGuard.OneUseGate(authorization.expiresAt(), Clock.systemUTC());
            LIVE_CASE = JSON.readTree(fixtureBytes);
        }
        Path metadata = requiredPath("TRACEDESK_BROWSER_FIXTURE_METADATA");
        Path stopFile = requiredPath("TRACEDESK_BROWSER_FIXTURE_STOP_FILE");
        Path commandFile = requiredPath("TRACEDESK_BROWSER_FIXTURE_COMMAND_FILE");
        Path ackFile = requiredPath("TRACEDESK_BROWSER_FIXTURE_ACK_FILE");
        Path workDirectory = metadata.getParent();
        Files.createDirectories(workDirectory);
        Files.deleteIfExists(stopFile);
        Files.deleteIfExists(commandFile);
        Files.deleteIfExists(ackFile);

        try (IsolatedPostgres db = IsolatedPostgres.open(System.getenv())) {
            boolean ledgerReady = false;
            try {
            Flyway.configure().dataSource(db).locations("classpath:db/migration")
                    .schemas(db.schema()).defaultSchema(db.schema()).load().migrate();
            ledgerReady = true;
            if (LIVE_MODE) {
                LIVE_MANAGER = LiveEvaluationManager.open(liveSettings, new JdbcAiLedger(new JdbcTemplate(db)), Clock.systemUTC());
                LIVE_GATE.requireAvailable();
                LIVE_MANAGER.refreshNow();
                LIVE_GATE.requireAvailable();
                if (LIVE_MANAGER.status() != SoclaasEnablementManager.Status.AVAILABLE)
                    throw ReporterLiveBrowserGuard.denied();
            }
            String rawTestUrl = required("TRACEDESK_TEST_JDBC_URL");
            String dbUrl = rawTestUrl + (rawTestUrl.contains("?") ? "&" : "?") + "currentSchema=" + db.schema();
            String keyStorePassword = UUID.randomUUID().toString().replace("-", "");
            Path keyStore = workDirectory.resolve("backend-test.p12");
            createKeyStore(keyStore, keyStorePassword);

            UUID projectId = UUID.randomUUID();
            UUID deniedProjectId = UUID.randomUUID();
            UUID reporterId = UUID.randomUUID();
            UUID developerFixtureReporterId = UUID.randomUUID();
            UUID nonmemberId = UUID.randomUUID();
            UUID developerId = UUID.randomUUID();
            UUID qaId = UUID.randomUUID();
            UUID successorId = UUID.randomUUID();
            UUID assignedReportId = UUID.randomUUID();
            UUID unassignedReportId = UUID.randomUUID();
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            String login = "reporter-" + suffix + "@example.test";
            String developerFixtureReporterLogin = "developer-fixture-reporter-" + suffix + "@example.test";
            String nonmemberLogin = "nonmember-" + suffix + "@example.test";
            String developerLogin = "developer-" + suffix + "@example.test";
            String qaLogin = "qa-" + suffix + "@example.test";
            String successorLogin = "developer-successor-" + suffix + "@example.test";

            SpringApplication app = new SpringApplication(AuthApplication.class);
            app.addPrimarySources(Set.of(AiFixtureConfiguration.class));
            app.setWebApplicationType(WebApplicationType.SERVLET);
            Map<String, Object> fixedProperties = Map.ofEntries(
                    Map.entry("server.address", "127.0.0.1"),
                    Map.entry("server.port", PORT),
                    Map.entry("server.ssl.enabled", "true"),
                    Map.entry("server.ssl.key-store", keyStore.toAbsolutePath().toString()),
                    Map.entry("server.ssl.key-store-password", keyStorePassword),
                    Map.entry("server.ssl.key-store-type", "PKCS12"),
                    Map.entry("server.ssl.key-alias", "tracedesk-test"),
                    Map.entry("spring.datasource.url", dbUrl),
                    Map.entry("spring.datasource.username", required("TRACEDESK_TEST_DATABASE_USER")),
                    Map.entry("spring.datasource.password", required("TRACEDESK_TEST_DATABASE_PASSWORD")),
                    Map.entry("TRACEDESK_DATABASE_URL", dbUrl),
                    Map.entry("TRACEDESK_DATABASE_USER", required("TRACEDESK_TEST_DATABASE_USER")),
                    Map.entry("TRACEDESK_DATABASE_PASSWORD", required("TRACEDESK_TEST_DATABASE_PASSWORD")),
                    Map.entry("spring.flyway.schemas", db.schema()),
                    Map.entry("spring.flyway.default-schema", db.schema()),
                    Map.entry("spring.flyway.enabled", false),
                    Map.entry("logging.level.root", "WARN"));
            app.addInitializers(context -> {
                context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("reporterBrowserFixture", fixedProperties));
                context.getBeanFactory().registerSingleton("dataSource", (DataSource) db);
                if (LIVE_MODE) context.getBeanFactory().registerSingleton("soclaasEnablementManager", LIVE_MANAGER);
            });

            try (ConfigurableApplicationContext context = app.run()) {
                JdbcTemplate sql = context.getBean(JdbcTemplate.class);
                if (LIVE_MODE && context.getBeansOfType(SoclaasEnablementManager.class).size() != 1)
                    throw ReporterLiveBrowserGuard.denied();
                if (context.getBean(DataSource.class) != db ||
                        !db.schema().equals(sql.queryForObject("select current_schema()", String.class))) {
                    throw new IllegalStateException("Fixture application did not bind to its owned PostgreSQL schema");
                }
                sql.update("insert into td_projects(id,name) values(?,?)", projectId, "Synthetic Reporter Project");
                sql.update("insert into td_projects(id,name) values(?,?)", deniedProjectId, "Synthetic Unavailable Project");
                Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
                seedUser(sql, encoder, reporterId, login);
                seedUser(sql, encoder, developerFixtureReporterId, developerFixtureReporterLogin);
                seedUser(sql, encoder, nonmemberId, nonmemberLogin);
                seedUser(sql, encoder, developerId, developerLogin);
                seedUser(sql, encoder, successorId, successorLogin);
                seedUser(sql, encoder, qaId, qaLogin);
                sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,'QA_REVIEWER','ACTIVE')",
                        projectId, qaId);
                sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,'REPORTER','ACTIVE')",
                        projectId, reporterId);
                sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,'DEVELOPER','ACTIVE')",
                        projectId, developerId);
                sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,'DEVELOPER','ACTIVE')",
                        projectId, successorId);
                seedDeveloperReport(sql, assignedReportId, projectId, developerFixtureReporterId, developerId,
                        "Assigned browser report <img src=x onerror=window.__developerInjected=true>");
                seedDeveloperReport(sql, unassignedReportId, projectId, developerFixtureReporterId, null,
                        "Unassigned browser report");
                Path metadataTemp = workDirectory.resolve("fixture.json.tmp");
                Files.writeString(metadataTemp, "{\"projectId\":\"" + projectId + "\",\"deniedProjectId\":\"" +
                        deniedProjectId + "\",\"reporterId\":\"" + reporterId + "\",\"login\":\"" + login +
                        "\",\"nonmemberLogin\":\"" + nonmemberLogin + "\",\"developerLogin\":\"" +
                        developerLogin + "\",\"qaLogin\":\"" + qaLogin + "\",\"developerId\":\"" + developerId +
                        "\",\"assignedReportId\":\"" + assignedReportId +
                        "\",\"unassignedReportId\":\"" + unassignedReportId + "\"}");
                Files.move(metadataTemp, metadata, java.nio.file.StandardCopyOption.ATOMIC_MOVE);

                AuthStore store = context.getBean(AuthStore.class);
                boolean auditUnavailable = false;
                while (!Files.exists(stopFile)) {
                    if (Files.exists(commandFile)) {
                        String command = Files.readString(commandFile).trim();
                        Files.deleteIfExists(commandFile);
                        if ("invalidate-reporter-session".equals(command)) {
                            store.setUserEnabled(reporterId, false);
                            store.setUserEnabled(reporterId, true);
                            Files.writeString(ackFile, "done");
                        } else if ("invalidate-developer-session".equals(command)) {
                            store.setUserEnabled(developerId, false);
                            store.setUserEnabled(developerId, true);
                            Files.writeString(ackFile, "done");
                        } else if ("advance-developer-report-version".equals(command)) {
                            sql.update("update td_reports set version=version+1 where id=?", assignedReportId);
                            Files.writeString(ackFile, "done");
                        } else if ("reassign-developer-report".equals(command)) {
                            sql.update("update td_reports set assigned_developer_id=? where id=?", successorId, assignedReportId);
                            Files.writeString(ackFile, "done");
                        } else if ("audit-off".equals(command) && !auditUnavailable) {
                            sql.execute("alter table td_business_audit_events rename to td_business_audit_events_fixture_off");
                            auditUnavailable = true;
                            Files.writeString(ackFile, "done");
                        } else if ("audit-on".equals(command) && auditUnavailable) {
                            sql.execute("alter table td_business_audit_events_fixture_off rename to td_business_audit_events");
                            auditUnavailable = false;
                            Files.writeString(ackFile, "done");
                        } else if ("ai-off".equals(command) || "ai-on".equals(command)) {
                            AI_AVAILABLE.set("ai-on".equals(command));
                            if (LIVE_MODE) LIVE_GATE.setAvailable(AI_AVAILABLE.get());
                            Files.writeString(ackFile, "done");
                        } else {
                            Files.writeString(ackFile, "invalid");
                        }
                    }
                    Thread.sleep(200);
                }
            } finally {
                Files.deleteIfExists(keyStore);
            }
            } finally {
                if (LIVE_MODE && ledgerReady) writeLedgerSummary(liveResult, new JdbcTemplate(db), LIVE_AUTHORIZATION);
            }
        }
    }

    /** Test-only provider behind the real secured gateway, ledger, HTTP/session and UI. */
    @TestConfiguration(proxyBeanMethods=false)
    public static class AiFixtureConfiguration {
        @Bean @Primary
        ReportAiService browserReportAiService(ReportRepository reports, Clock clock, ReportAiService.Factory factory) {
            return new ReportAiService(reports, clock, factory);
        }

        @Bean @Primary
        QaAiService browserQaAiService(ReportRepository reports, InvestigationRepository investigations,
                                      ReportAiService.Factory factory) {
            return new QaAiService(reports, investigations, factory);
        }

        @Bean
        @org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization
        ReportAiService.Factory browserAiGatewayFactory(JdbcTemplate sql, Clock clock,
                                                        ObjectProvider<SoclaasEnablementManager> managers) {
            if (LIVE_MODE) {
                SoclaasEnablementManager manager = managers.getIfAvailable();
                if (manager == null || manager != LIVE_MANAGER) throw ReporterLiveBrowserGuard.denied();
                return (actors, authorizer, contexts) -> manager.createGateway(actors, authorizer, liveContextBuilder(contexts));
            }
            var ledger = new JdbcAiLedger(sql);
            var policy = new Policy(true, 100, 1_000_000, 500_000, 4, 1, Duration.ofSeconds(60));
            var now = clock.instant();
            var expiry = now.plusSeconds(900);
            var approval = "0".repeat(64); // Explicit synthetic approval; never used by production transport.
            ledger.publishPolicy(policy, now);
            ledger.prepareEnablement(policy, "synthetic-java-model", 1, 1, approval, expiry, now);
            long revision = ledger.nextMetadataRevision(policy, "synthetic-java-model", 1, 1, approval, expiry, now);
            var metadata = new Metadata("synthetic-java-model", revision, expiry, 1, approval, expiry);
            Provider provider = new Provider() {
                public Metadata metadata() { return metadata; }
                public Completion complete(Context context) {
                    if (!AI_AVAILABLE.get()) throw new GatewayFailure(GatewayFailure.Code.AI_UNAVAILABLE);
                    if (context.dataJson().contains("textLogs") || context.dataJson().contains("AI_DO_NOT_SEND_LOG_CANARY"))
                        throw new GatewayFailure(GatewayFailure.Code.AI_REJECTED);
                    if ("qa-follow-up-v1".equals(context.promptVersion())) {
                        return new Completion("""
                            {"answer":"Compare the totals before and after refreshing twice.",
                             "checks":["Refresh a payment with two distinct refunds."],"evidenceGaps":[]}
                            """, new Usage(350, 90));
                    }
                    if ("qa-planning-v1".equals(context.promptVersion())) {
                        if (!context.dataJson().contains("Deduplicate") && !context.dataJson().contains("synthetic change summary"))
                            throw new GatewayFailure(GatewayFailure.Code.AI_REJECTED);
                        return new Completion("""
                            {"checks":["Replay the synthetic steps and compare the result."],
                             "evidenceGaps":["Record the build used for verification."]}
                            """, new Usage(300, 80));
                    }
                    return new Completion("""
                        {"changes":[{"field":"title","replacement":"Clearer Java report <img src=x onerror=window.__aiInjected=true>"}],
                         "missingInformation":["Which Java version reproduces this issue?"]}
                        """, new Usage(300, 80));
                }
            };
            return (actors, authorizer, contexts) -> new SecuredAiGateway(
                    actors, authorizer, contexts, provider, ledger, policy, clock);
        }
    }

    private static ContextBuilder liveContextBuilder(ContextBuilder delegate) {
        return (actor, request) -> {
            try { LIVE_GATE.requireAvailable(); }
            catch (IllegalStateException unavailable) { throw new ReportService.Failure("AI_UNAVAILABLE", 503); }
            Context context = delegate.build(actor, request);
            if (!ReportAiService.PROMPT_VERSION.equals(context.promptVersion())
                    || !ReporterLiveBrowserGuard.sameContext(context.dataJson(), LIVE_CASE))
                throw new ReportService.Failure("AI_UNAVAILABLE", 503);
            try { LIVE_GATE.consume(); }
            catch (IllegalStateException unavailable) { throw new ReportService.Failure("AI_UNAVAILABLE", 503); }
            return context;
        };
    }

    private static void writeLedgerSummary(Path result, JdbcTemplate sql,
                                          ReporterLiveBrowserGuard.Authorization authorization) throws Exception {
        long tickets = sql.queryForObject("select count(*) from td_ai_tickets", Long.class);
        long dispatched = sql.queryForObject("select count(distinct ticket_id) from td_ai_accounting_events where event_code='DISPATCHED'", Long.class);
        long charged = sql.queryForObject("select coalesce(sum(charged_units),0) from td_ai_tickets", Long.class);
        Map<String, Long> outcomes = new LinkedHashMap<>();
        sql.query("select outcome,count(*) from td_ai_tickets where state='TERMINAL' group by outcome order by outcome",
                rs -> { outcomes.put(rs.getString(1), rs.getLong(2)); });
        Map<String,Object> summary = new LinkedHashMap<>();
        summary.put("caseId", ReporterLiveBrowserGuard.CASE_ID);
        summary.put("model", authorization.model());
        summary.put("promptVersion", ReporterLiveBrowserGuard.PROMPT_VERSION);
        summary.put("candidateRevision", authorization.candidateRevision());
        summary.put("candidateSourceSha256", authorization.candidateSourceSha256());
        summary.put("ticketCount", tickets); summary.put("dispatchedCount", dispatched);
        summary.put("chargedResourceUnits", charged); summary.put("outcomeCounts", outcomes);
        summary.put("outcome", outcomes.isEmpty() ? "NO_ATTEMPT" : outcomes.containsKey("FAILURE")
                || outcomes.containsKey("UNKNOWN") || outcomes.containsKey("EXPIRED") ? "STOPPED_ON_FAILURE" : "COMPLETED");
        Path temp = result.resolveSibling(result.getFileName() + ".tmp");
        Files.createDirectories(result.getParent());
        Files.writeString(temp, JSON.writeValueAsString(summary));
        Files.move(temp, result, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static void seedUser(JdbcTemplate sql, Argon2PasswordEncoder encoder, UUID id, String login) {
        sql.update("insert into td_users(id,login_normalized,password_hash) values(?,?,?)",
                id, login, encoder.encode(PASSWORD));
    }

    private static void seedDeveloperReport(JdbcTemplate sql, UUID id, UUID projectId, UUID reporterId,
                                            UUID developerId, String title) {
        sql.update("""
                insert into td_reports(id,project_id,reporter_user_id,title,description,environment,reproduction_steps,
                  expected_behavior,actual_behavior,text_logs,status,assigned_developer_id,created_at,updated_at,version)
                values(?,?,?,?,?,?,?,?,?,?,'OPEN',?,now(),now(),1)
                """, id, projectId, reporterId, title,
                "Untrusted description <script>window.__developerInjected=true</script>",
                "Synthetic isolated PostgreSQL", "1. Open the Developer workspace",
                "The report remains inert text", "The real backend returns the assigned report",
                "<b>plain synthetic log text</b>", developerId);
    }

    private static void createKeyStore(Path path, String password) throws Exception {
        Path keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "keytool.exe" : "keytool");
        Process process = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "tracedesk-test",
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "1", "-storetype", "PKCS12",
                "-keystore", path.toAbsolutePath().toString(), "-storepass", password, "-keypass", password,
                "-dname", "CN=127.0.0.1", "-ext", "SAN=ip:127.0.0.1")
                .redirectErrorStream(true).start();
        process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
        if (process.waitFor() != 0) {
            throw new IllegalStateException("Unable to create the temporary browser-fixture TLS certificate");
        }
    }

    private static Path requiredPath(String name) {
        return Path.of(required(name)).toAbsolutePath();
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing required " + name);
        return value;
    }
}
