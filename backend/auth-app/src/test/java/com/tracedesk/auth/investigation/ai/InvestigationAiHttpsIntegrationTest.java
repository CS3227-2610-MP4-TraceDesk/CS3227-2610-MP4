package com.tracedesk.auth.investigation.ai;

import com.tracedesk.ai.gateway.SecuredAiGateway;
import com.tracedesk.auth.ai.configuration.SoclaasEnablementManager;
import com.tracedesk.auth.ai.persistence.JdbcAiLedger;
import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.tracedesk.ai.gateway.GatewayPorts.Provider;
import static com.tracedesk.ai.gateway.GatewayTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Full DEV-AI HTTP boundary checks over HTTPS and a fresh, owned PostgreSQL schema. */
@ExtendWith(OutputCaptureExtension.class)
class InvestigationAiHttpsIntegrationTest {
    private static final String PASSWORD = "synthetic-dev-ai-password-2026";
    private static final String REPORT_CANARY = "dev-ai-report-canary-73c1";
    private static final String LOG_CANARY = "dev-ai-log-canary-19af";
    private static final String NOTE_CANARY = "dev-ai-note-canary-c024";
    private static final String REPLY_CANARY = "dev-ai-reply-canary-5e8b";
    private static final String OTHER_REPORT_CANARY = "dev-ai-other-report-canary-a91d";
    private static final String ACCOUNT_CANARY = "dev-ai-account-canary@example.test";
    private static final String ROUTE = "/api/projects/{projectId}/reports/{reportId}/investigation/suggestions";

    @Test
    void migrationV19AllowsSuggestionRouteAndStillRejectsUnknownRoutes() throws Exception {
        try (IsolatedPostgres db = IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration")
                    .schemas(db.schema()).defaultSchema(db.schema()).load().migrate();
            JdbcTemplate sql = new JdbcTemplate(db);
            sql.update("insert into td_security_events(event_code,request_id,route_template,outcome_status) values('DEVELOPER_DENIED',?,?,403)",
                    UUID.randomUUID(), ROUTE);
            assertEquals(1, sql.queryForObject("select count(*) from td_security_events where route_template=?",
                    Integer.class, ROUTE));
            assertThrows(DataAccessException.class, () -> sql.update(
                    "insert into td_security_events(event_code,request_id,route_template,outcome_status) values('DEVELOPER_DENIED',?,'/api/unknown',403)",
                    UUID.randomUUID()));
        }
    }

    @Test
    void realHttpsRevalidatesSessionsSuppressesRevokedResultsSettlesUsageAndLeaksNoCanaries(
            CapturedOutput capturedOutput) throws Exception {
        Path temp = Files.createTempDirectory("tracedesk-dev-ai-https-");
        try (IsolatedPostgres db = IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration")
                    .schemas(db.schema()).defaultSchema(db.schema()).load().migrate();
            String dbUrl = System.getenv("TRACEDESK_TEST_JDBC_URL")
                    + (System.getenv("TRACEDESK_TEST_JDBC_URL").contains("?") ? "&" : "?")
                    + "currentSchema=" + db.schema();
            String storePassword = UUID.randomUUID().toString().replace("-", "");
            Path keyStorePath = temp.resolve("ephemeral.p12");
            createKeyStore(keyStorePath, storePassword);
            SSLContext clientTls = trustKeyStore(keyStorePath, storePassword);

            SpringApplication app = new SpringApplication(
                    com.tracedesk.auth.AuthApplication.class, AiTestConfiguration.class);
            app.setWebApplicationType(WebApplicationType.SERVLET);
            app.setDefaultProperties(Map.ofEntries(
                    Map.entry("server.address", "127.0.0.1"),
                    Map.entry("server.port", "0"),
                    Map.entry("server.ssl.enabled", "true"),
                    Map.entry("server.ssl.key-store", keyStorePath.toAbsolutePath().toString()),
                    Map.entry("server.ssl.key-store-password", storePassword),
                    Map.entry("server.ssl.key-store-type", "PKCS12"),
                    Map.entry("server.ssl.key-alias", "tracedesk-test"),
                    Map.entry("TRACEDESK_DATABASE_URL", dbUrl),
                    Map.entry("TRACEDESK_PROJECT_CURSOR_SECRET", "synthetic-project-cursor-secret-for-tests-0001"),
                    Map.entry("TRACEDESK_DATABASE_USER", System.getenv("TRACEDESK_TEST_DATABASE_USER")),
                    Map.entry("TRACEDESK_DATABASE_PASSWORD", System.getenv("TRACEDESK_TEST_DATABASE_PASSWORD")),
                    Map.entry("spring.flyway.schemas", db.schema()),
                    Map.entry("spring.flyway.default-schema", db.schema()),
                    Map.entry("logging.level.root", "TRACE"),
                    Map.entry("logging.level.org.springframework.jdbc.core.StatementCreatorUtils", "OFF")));

            try (ConfigurableApplicationContext context = app.run()) {
                JdbcTemplate sql = context.getBean(JdbcTemplate.class);
                TestAiProvider provider = context.getBean(TestAiProvider.class);
                SoclaasEnablementManager enablement = context.getBean(SoclaasEnablementManager.class);
                assertEquals(1, context.getBeansOfType(InvestigationAiAccess.class).size(),
                        "Spring must use the production InvestigationConfiguration bean");
                verify(enablement).createGateway(any(), isA(InvestigationAiAccess.class), any());
                UUID developer = UUID.randomUUID(), secondDeveloper = UUID.randomUUID(), reporter = UUID.randomUUID();
                UUID project = UUID.randomUUID(), report = UUID.randomUUID(), note = UUID.randomUUID();
                seed(sql, developer, secondDeveloper, reporter, project, report, note);
                URI base = URI.create("https://127.0.0.1:"
                        + context.getEnvironment().getProperty("local.server.port"));
                String path = "/api/projects/" + project + "/reports/" + report + "/investigation/suggestions";
                String body = "{\"expectedVersion\":1,\"reportFields\":[\"description\",\"textLogs\"],"
                        + "\"notes\":[{\"id\":\"" + note + "\",\"expectedVersion\":1}]}";

                SessionClient valid = login(base, clientTls);
                BusinessCounts businessBefore = businessCounts(sql);
                HttpResponse<String> success = send(valid.client(), base, "POST", path, body, valid.csrf());
                assertEquals(200, success.statusCode(), success.body());
                assertTrue(success.body().contains(REPLY_CANARY));
                assertEquals(businessBefore, businessCounts(sql),
                        "suggestions must not create or change business or business-audit rows");
                assertNotNull(provider.lastContext.get());
                String expectedContext = "{\"report\":{\"description\":\"" + REPORT_CANARY
                        + "\",\"textLogs\":\"" + LOG_CANARY + "\"},\"notes\":[{\"ref\":\"note1\",\"body\":\""
                        + NOTE_CANARY + "\"}]}";
                assertEquals(expectedContext, provider.lastContext.get().dataJson(),
                        "provider context must be exact, ordered, and limited to the selection");
                for (String forbidden : List.of(developer.toString(), secondDeveloper.toString(), reporter.toString(),
                        project.toString(), report.toString(), note.toString(), ACCOUNT_CANARY,
                        "Synthetic title", "Synthetic environment", "Synthetic steps", "Synthetic expected",
                        "Synthetic actual", OTHER_REPORT_CANARY)) {
                    assertFalse(provider.lastContext.get().dataJson().contains(forbidden),
                            "provider context must exclude server-owned, unselected, account, and unrelated data");
                }

                SessionClient expired = login(base, clientTls);
                provider.beforeMetadata.set(() -> sql.update(
                        "update spring_session set last_access_time=0,expiry_time=0 where principal_name=?",
                        developer.toString()));
                int callsBeforeExpiry = provider.completions.get();
                assertError(send(expired.client(), base, "POST", path, body, expired.csrf()), 401, "UNAUTHENTICATED");
                assertEquals(callsBeforeExpiry, provider.completions.get(), "an expired session must not reach completion");

                SessionClient invalidatedBeforeDispatch = login(base, clientTls);
                provider.beforeMetadata.set(() -> deleteSessions(sql, developer));
                int callsBeforeDispatchInvalidation = provider.completions.get();
                assertError(send(invalidatedBeforeDispatch.client(), base, "POST", path, body,
                        invalidatedBeforeDispatch.csrf()), 401, "UNAUTHENTICATED");
                assertEquals(callsBeforeDispatchInvalidation, provider.completions.get(),
                        "session invalidation before dispatch must suppress completion");

                SessionClient invalidatedDuringCompletion = login(base, clientTls);
                long ticketsBeforeSessionRevocation = terminalSuccessTickets(sql);
                provider.insideCompletion.set(() -> deleteSessions(sql, developer));
                assertError(send(invalidatedDuringCompletion.client(), base, "POST", path, body,
                        invalidatedDuringCompletion.csrf()), 401, "UNAUTHENTICATED");
                assertSettledSuccess(sql, ticketsBeforeSessionRevocation);

                SessionClient mutationClient = login(base, clientTls);
                for (String scenario : List.of("membership", "reassignment", "status", "report-version",
                        "note-edit", "note-delete")) {
                    restore(sql, developer, project, report, note);
                    long ticketsBefore = terminalSuccessTickets(sql);
                    provider.insideCompletion.set(() -> {
                        switch (scenario) {
                            case "membership" -> sql.update(
                                    "update td_project_memberships set state='DISABLED' where project_id=? and user_id=?",
                                    project, developer);
                            case "reassignment" -> sql.update(
                                    "update td_reports set assigned_developer_id=? where id=?", secondDeveloper, report);
                            case "status" -> sql.update("update td_reports set status='READY_FOR_QA' where id=?", report);
                            case "report-version" -> sql.update(
                                    "update td_reports set version=version+1 where id=?", report);
                            case "note-edit" -> sql.update(
                                    "update td_investigation_notes set body='synthetic changed note',version=version+1 where id=?",
                                    note);
                            case "note-delete" -> sql.update("delete from td_investigation_notes where id=?", note);
                            default -> throw new AssertionError(scenario);
                        }
                    });
                    HttpResponse<String> denied = send(mutationClient.client(), base, "POST", path, body,
                            mutationClient.csrf());
                    int expectedStatus = scenario.equals("membership") || scenario.equals("note-delete") ? 404
                            : scenario.equals("reassignment") ? 403 : 409;
                    String expectedCode = scenario.equals("membership") || scenario.equals("note-delete") ? "NOT_FOUND"
                            : scenario.equals("reassignment") ? "FORBIDDEN"
                            : scenario.equals("status") ? "INVALID_STATE" : "STALE_VERSION";
                    assertError(denied, expectedStatus, expectedCode);
                    assertFalse(denied.body().contains(REPLY_CANARY), scenario);
                    assertSettledSuccess(sql, ticketsBefore);
                }

                restore(sql, developer, project, report, note);
                provider.reply.set("{\"hypotheses\":[{\"statement\":\"" + REPLY_CANARY
                        + "\",\"rationale\":\"synthetic\",\"sources\":[\"description\"]}],"
                        + "\"nextSteps\":[],\"unexpected\":true}");
                HttpResponse<String> invalidOutput = send(mutationClient.client(), base, "POST", path, body,
                        mutationClient.csrf());
                assertError(invalidOutput, 502, "AI_INVALID_OUTPUT");
                assertFalse(invalidOutput.body().contains(REPLY_CANARY));

                String securityEvents = String.join("\n", sql.queryForList(
                        "select row_to_json(e)::text from td_security_events e", String.class));
                for (String canary : canaries()) {
                    assertFalse(securityEvents.contains(canary),
                            "security events must exclude report, log, note, and provider canaries");
                }
            }
            for (String canary : canaries()) {
                assertFalse(capturedOutput.getAll().contains(canary),
                        "application logs must exclude report, log, note, and provider canaries");
            }
        } finally {
            try (var paths = Files.walk(temp)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (java.io.IOException ignored) { }
                });
            }
        }
    }

    private static List<String> canaries() {
        return List.of(REPORT_CANARY, LOG_CANARY, NOTE_CANARY, REPLY_CANARY,
                OTHER_REPORT_CANARY, ACCOUNT_CANARY);
    }

    private static void seed(JdbcTemplate sql, UUID developer, UUID secondDeveloper, UUID reporter,
                             UUID project, UUID report, UUID note) {
        Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
        sql.update("insert into td_users(id,login_normalized,password_hash,account_role) values(?,?,?,'DEVELOPER')",
                developer, ACCOUNT_CANARY, encoder.encode(PASSWORD));
        sql.update("insert into td_users(id,login_normalized,password_hash,account_role) values(?,?,?,'DEVELOPER')",
                secondDeveloper, "dev-ai-second@example.test", encoder.encode(PASSWORD));
        sql.update("insert into td_users(id,login_normalized,password_hash,account_role) values(?,?,?,'REPORTER')",
                reporter, "dev-ai-reporter@example.test", encoder.encode(PASSWORD));
        sql.update("insert into td_projects(id,name) values(?,'Synthetic DEV-AI project')", project);
        sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,'DEVELOPER','ACTIVE')",
                project, developer);
        sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,'DEVELOPER','ACTIVE')",
                project, secondDeveloper);
        sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,'REPORTER','ACTIVE')",
                project, reporter);
        sql.update("""
                insert into td_reports(id,project_id,reporter_user_id,assigned_developer_id,status,title,
                  description,environment,reproduction_steps,expected_behavior,actual_behavior,text_logs)
                values(?,?,?,?,'IN_PROGRESS','Synthetic title',?,'Synthetic environment','Synthetic steps',
                  'Synthetic expected','Synthetic actual',?)
                """, report, project, reporter, developer, REPORT_CANARY, LOG_CANARY);
        sql.update("""
                insert into td_reports(id,project_id,reporter_user_id,assigned_developer_id,status,title,
                  description,environment,reproduction_steps,expected_behavior,actual_behavior,text_logs)
                values(?,?,?,?,'IN_PROGRESS','Unrelated title',?,'Unrelated environment','Unrelated steps',
                  'Unrelated expected','Unrelated actual','Unrelated logs')
                """, UUID.randomUUID(), project, reporter, developer, OTHER_REPORT_CANARY);
        Instant now = Instant.parse("2026-11-13T00:00:00Z");
        sql.update("""
                insert into td_investigation_notes(id,project_id,report_id,author_user_id,body,created_at,updated_at)
                values(?,?,?,?,?,?,?)
                """, note, project, report, developer, NOTE_CANARY,
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
    }

    private static void restore(JdbcTemplate sql, UUID developer, UUID project, UUID report, UUID note) {
        sql.update("update td_project_memberships set state='ACTIVE' where project_id=? and user_id=?",
                project, developer);
        sql.update("update td_reports set assigned_developer_id=?,status='IN_PROGRESS',version=1 where id=?",
                developer, report);
        Instant now = Instant.parse("2026-11-13T00:00:00Z");
        sql.update("""
                insert into td_investigation_notes
                  (id,project_id,report_id,author_user_id,last_edited_by_user_id,body,created_at,updated_at,version)
                values(?,?,?,?,null,?,?,?,1)
                on conflict (id) do update set body=excluded.body,last_edited_by_user_id=null,
                  updated_at=excluded.updated_at,version=1
                """, note, project, report, developer, NOTE_CANARY,
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
    }

    private static BusinessCounts businessCounts(JdbcTemplate sql) {
        return new BusinessCounts(
                sql.queryForObject("select count(*) from td_reports", Long.class),
                sql.queryForObject("select count(*) from td_investigation_notes", Long.class),
                sql.queryForObject("select count(*) from td_investigation_note_idempotency", Long.class),
                sql.queryForObject("select count(*) from td_report_resolutions", Long.class),
                sql.queryForObject("select count(*) from td_business_audit_events", Long.class));
    }

    private record BusinessCounts(long reports, long notes, long noteIdempotency,
                                  long resolutions, long auditEvents) { }

    private static void deleteSessions(JdbcTemplate sql, UUID actor) {
        sql.update("delete from spring_session where principal_name=?", actor.toString());
    }

    private static long terminalSuccessTickets(JdbcTemplate sql) {
        return sql.queryForObject("select count(*) from td_ai_tickets where state='TERMINAL' and outcome='SUCCESS'",
                Long.class);
    }

    private static void assertSettledSuccess(JdbcTemplate sql, long before) {
        assertEquals(before + 1, terminalSuccessTickets(sql));
        assertEquals(0, sql.queryForObject("select count(*) from td_ai_tickets where state<>'TERMINAL'", Integer.class));
        assertEquals(before + 1, sql.queryForObject("""
                select count(*) from td_ai_tickets t
                where t.state='TERMINAL' and t.outcome='SUCCESS' and t.charged_units=40
                  and (select count(*) from td_ai_accounting_events e where e.ticket_id=t.id)=3
                  and (select max(e.event_code) from td_ai_accounting_events e where e.ticket_id=t.id
                       and e.revision=3)='SUCCESS'
                """, Long.class).longValue(),
                "each dispatched request must have reserve, dispatch, and success settlement rows");
    }

    private static SessionClient login(URI base, SSLContext clientTls) throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).sslContext(clientTls)
                .connectTimeout(Duration.ofSeconds(5)).build();
        String anonymousCsrf = jsonField(send(client, base, "GET", "/api/csrf", null, null).body(), "token");
        HttpResponse<String> login = send(client, base, "POST", "/api/session/login",
                "{\"login\":\"" + ACCOUNT_CANARY + "\",\"password\":\"" + PASSWORD + "\"}",
                anonymousCsrf);
        assertEquals(200, login.statusCode(), login.body());
        HttpResponse<String> csrf = send(client, base, "GET", "/api/csrf", null, null);
        assertEquals(200, csrf.statusCode(), csrf.body());
        HttpCookie cookie = cookies.getCookieStore().getCookies().stream()
                .filter(value -> value.getName().equals("TDSESSION")).findFirst().orElseThrow();
        assertFalse(cookie.getValue().isBlank());
        return new SessionClient(client, jsonField(csrf.body(), "token"));
    }

    private record SessionClient(HttpClient client, String csrf) { }

    private static void assertError(HttpResponse<String> response, int status, String code) {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(code, jsonField(response.body(), "code"));
        assertFalse(response.body().contains(REPORT_CANARY));
        assertFalse(response.body().contains(LOG_CANARY));
        assertFalse(response.body().contains(NOTE_CANARY));
        assertFalse(response.body().contains(REPLY_CANARY));
    }

    private static HttpResponse<String> send(HttpClient client, URI base, String method, String path,
                                              String body, String csrf) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10));
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
        else request.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String jsonField(String json, String key) {
        var matcher = java.util.regex.Pattern.compile("\\\"" + java.util.regex.Pattern.quote(key)
                + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json);
        assertTrue(matcher.find(), "missing JSON field " + key + " in " + json);
        return matcher.group(1);
    }

    private static void createKeyStore(Path path, String password) throws Exception {
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        Process process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "tracedesk-test", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "2", "-storetype", "PKCS12", "-keystore", path.toString(),
                "-storepass", password, "-keypass", password, "-dname", "CN=localhost", "-ext", "SAN=ip:127.0.0.1")
                .redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        if (process.waitFor() != 0)
            throw new IllegalStateException("Could not create ephemeral test TLS identity: " + new String(output));
    }

    private static SSLContext trustKeyStore(Path path, String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(path)) { keyStore.load(input, password.toCharArray()); }
        TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(keyStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, managers.getTrustManagers(), null);
        return context;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AiTestConfiguration {
        @Bean TestAiProvider testAiProvider(Clock clock) { return new TestAiProvider(clock); }

        @Bean(destroyMethod = "close")
        @Primary
        SoclaasEnablementManager testSoclaasEnablementManager(Clock clock, JdbcTemplate sql,
                                                              TestAiProvider provider) {
            Policy policy = new Policy(true, 9_001, 1_000_000, 500_000, 4, 1, Duration.ofSeconds(60));
            JdbcAiLedger ledger = new JdbcAiLedger(sql);
            SoclaasEnablementManager manager = mock(SoclaasEnablementManager.class);
            when(manager.createGateway(any(), any(), any())).thenAnswer(invocation -> new SecuredAiGateway(
                    invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2),
                    provider, ledger, policy, clock));
            return manager;
        }
    }

    static final class TestAiProvider implements Provider {
        private final Instant metadataValidUntil;
        final AtomicReference<Runnable> beforeMetadata = new AtomicReference<>(() -> { });
        final AtomicReference<Runnable> insideCompletion = new AtomicReference<>(() -> { });
        final AtomicReference<String> reply = new AtomicReference<>("{\"hypotheses\":[{\"statement\":\""
                + REPLY_CANARY + "\",\"rationale\":\"synthetic\",\"sources\":[\"description\"]}],\"nextSteps\":[]}");
        final AtomicReference<Context> lastContext = new AtomicReference<>();
        final AtomicInteger completions = new AtomicInteger();

        TestAiProvider(Clock clock) { this.metadataValidUntil = clock.instant().plusSeconds(900); }

        @Override public Metadata metadata() {
            beforeMetadata.getAndSet(() -> { }).run();
            return new Metadata("synthetic-model", 9_001, metadataValidUntil, 1);
        }

        @Override public Completion complete(Context context) {
            lastContext.set(context);
            completions.incrementAndGet();
            insideCompletion.getAndSet(() -> { }).run();
            return new Completion(reply.get(), new Usage(20, 20));
        }
    }
}
