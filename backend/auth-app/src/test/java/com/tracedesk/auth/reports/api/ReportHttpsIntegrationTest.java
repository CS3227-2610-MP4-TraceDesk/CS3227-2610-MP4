package com.tracedesk.auth.reports.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the public report API over HTTPS against a fresh, owned PostgreSQL schema. */
@ExtendWith(OutputCaptureExtension.class)
class ReportHttpsIntegrationTest {
    private static final String PASSWORD = "synthetic-report-password-2026";
    private static final String CANARY = "report-private-canary-4c921";
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void reportHttpsBoundaryEnforcesIdentityVisibilityIdempotencyAndSafeErrors(CapturedOutput output) throws Exception {
        assertFalse(java.lang.reflect.Modifier.isPublic(
                Class.forName("com.tracedesk.ai.internal.soclaas.SoclaasClient").getModifiers()),
                "the secured factory may load transport, but role controllers cannot access the raw client");
        Path temp = Files.createTempDirectory("tracedesk-report-https-");
        List<String> canaries = new ArrayList<>(List.of(PASSWORD, CANARY));
        try (IsolatedPostgres db = IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration")
                    .schemas(db.schema()).defaultSchema(db.schema()).load().migrate();
            String configuredUrl = System.getenv("TRACEDESK_TEST_JDBC_URL");
            String dbUrl = configuredUrl + (configuredUrl.contains("?") ? "&" : "?") + "currentSchema=" + db.schema();
            String keyPassword = UUID.randomUUID().toString().replace("-", "");
            Path keyStorePath = temp.resolve("ephemeral.p12");
            createKeyStore(keyStorePath, keyPassword);
            SSLContext clientTls = trustKeyStore(keyStorePath, keyPassword);

            SpringApplication app = new SpringApplication(com.tracedesk.auth.AuthApplication.class);
            app.setWebApplicationType(WebApplicationType.SERVLET);
            app.setDefaultProperties(Map.ofEntries(
                    Map.entry("server.address", "127.0.0.1"), Map.entry("server.port", "0"),
                    Map.entry("server.ssl.enabled", "true"),
                    Map.entry("server.ssl.key-store", keyStorePath.toAbsolutePath().toString()),
                    Map.entry("server.ssl.key-store-password", keyPassword),
                    Map.entry("server.ssl.key-store-type", "PKCS12"), Map.entry("server.ssl.key-alias", "tracedesk-test"),
                    Map.entry("TRACEDESK_DATABASE_URL", dbUrl),
                    Map.entry("TRACEDESK_PROJECT_CURSOR_SECRET", "synthetic-project-cursor-secret-for-tests-0001"),
                    Map.entry("TRACEDESK_DATABASE_USER", System.getenv("TRACEDESK_TEST_DATABASE_USER")),
                    Map.entry("TRACEDESK_DATABASE_PASSWORD", System.getenv("TRACEDESK_TEST_DATABASE_PASSWORD")),
                    Map.entry("spring.flyway.schemas", db.schema()), Map.entry("spring.flyway.default-schema", db.schema()),
                    Map.entry("logging.level.root", "WARN")));

            try (ConfigurableApplicationContext context = app.run()) {
                JdbcTemplate sql = context.getBean(JdbcTemplate.class);
                Fixture fixture = seed(sql);
                URI base = URI.create("https://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port"));
                Session reporter = login(base, clientTls, fixture.ownerLogin(), fixture.ownerId(), canaries);
                Session otherReporter = login(base, clientTls, fixture.otherLogin(), fixture.otherReporterId(), canaries);
                Session developer = login(base, clientTls, fixture.developerLogin(), fixture.developerId(), canaries);
                Session qa = login(base, clientTls, fixture.qaLogin(), fixture.qaId(), canaries);
                Session foreignReporter = login(base, clientTls, fixture.foreignLogin(), fixture.foreignReporterId(), canaries);

                String reports = "/api/projects/" + fixture.projectId() + "/reports";
                String contextPath = "/api/projects/" + fixture.projectId() + "/reporter-context";
                String body = reportBody(CANARY);
                String key = UUID.randomUUID().toString();
                HttpResponse<String> created = request(reporter, base, "POST", reports, body, key);
                assertEquals(201, created.statusCode(), "report creation should succeed");
                secure(created);
                JsonNode report = parse(created.body());
                String reportId = report.path("id").asText();
                assertTrue(canonicalUuid(reportId));
                assertEquals(fixture.projectId().toString(), report.path("projectId").asText());
                assertEquals(fixture.ownerId().toString(), report.path("reporterUserId").asText());
                assertEquals("OPEN", report.path("status").asText());
                assertEquals(1, report.path("version").asInt());
                assertTrue(report.path("createdAt").asText().endsWith("Z"));
                assertTrue(report.path("updatedAt").asText().endsWith("Z"));
                assertEquals("Issue 🧪", report.path("title").asText());
                assertEquals("", report.path("textLogs").asText());
                assertEquals(Set.of("id", "projectId", "reporterUserId", "title", "description",
                        "environment", "reproductionSteps", "expectedBehavior", "actualBehavior", "textLogs",
                        "status", "assignedDeveloperId", "createdAt", "updatedAt", "version"), Set.copyOf(onlyKeys(report)));
                for (String forbidden : List.of("login", "password", "role", "session", "csrf", "prompt", "provider", "notes")) {
                    assertFalse(created.body().toLowerCase().contains(forbidden), "response must not include " + forbidden);
                }

                HttpResponse<String> ownerDetail = request(reporter, base, "GET", reports + "/" + reportId, null, null);
                assertEquals(200, ownerDetail.statusCode());
                assertEquals(reportId, parse(ownerDetail.body()).path("id").asText());
                assertEquals(CANARY + "\nline two 🧭", parse(ownerDetail.body()).path("description").asText());

                HttpResponse<String> replay = request(reporter, base, "POST", reports, body, key);
                assertEquals(201, replay.statusCode(), replay.body());
                assertEquals(reportId, parse(replay.body()).path("id").asText(), "same-key replay returns the same report");
                assertEquals(1, parse(replay.body()).path("version").asInt());
                HttpResponse<String> conflict = request(reporter, base, "POST", reports,
                        reportBody(CANARY).replace("Issue 🧪", "Issue 🧬"), key);
                assertEquals(409, conflict.statusCode());
                assertEquals("IDEMPOTENCY_CONFLICT", errorCode(conflict));
                secure(conflict);

                HttpResponse<String> secondCreated = request(reporter, base, "POST", reports,
                        reportBody(CANARY + "-page-two"), UUID.randomUUID().toString());
                assertEquals(201, secondCreated.statusCode(), "second report creation should succeed");
                String secondReportId = parse(secondCreated.body()).path("id").asText();
                assertNotEquals(reportId, secondReportId);
                sql.update("update td_reports set updated_at=created_at + interval '1 second' where id=?",
                        UUID.fromString(secondReportId));

                HttpResponse<String> ownerList = request(reporter, base, "GET", reports + "?limit=1", null, null);
                assertEquals(200, ownerList.statusCode(), "report list should succeed");
                secure(ownerList);
                JsonNode ownerItems = parse(ownerList.body()).path("items");
                assertEquals(1, ownerItems.size());
                assertEquals(secondReportId, ownerItems.get(0).path("id").asText());
                assertEquals(Set.of("id", "projectId", "reporterUserId", "title", "status", "assignedDeveloperId",
                                "createdAt", "updatedAt", "version"), Set.copyOf(onlyKeys(ownerItems.get(0))));
                assertFalse(ownerList.body().contains(CANARY), "list summaries must not return report body fields");
                String cursor = parse(ownerList.body()).path("nextCursor").asText();
                assertFalse(cursor.isBlank(), "a bounded first page with remaining results returns a continuation cursor");
                HttpResponse<String> nextPage = request(reporter, base, "GET", reports + "?limit=1&cursor=" + cursor, null, null);
                assertEquals(200, nextPage.statusCode(), "continuation page should succeed");
                assertEquals(1, parse(nextPage.body()).path("items").size());
                assertEquals(reportId, parse(nextPage.body()).path("items").get(0).path("id").asText());
                assertTrue(parse(nextPage.body()).path("nextCursor").isNull());
                for (String rebound : List.of(
                        reports + "?limit=1&status=CLOSED&cursor=" + cursor,
                        "/api/projects/" + fixture.foreignProjectId() + "/reports?limit=1&cursor=" + cursor)) {
                    HttpResponse<String> invalidCursorUse = request(reporter, base, "GET", rebound, null, null);
                    assertEquals(400, invalidCursorUse.statusCode());
                    assertEquals("INVALID_CURSOR", errorCode(invalidCursorUse));
                    secure(invalidCursorUse);
                }
                HttpResponse<String> invalidLimit = request(reporter, base, "GET", reports + "?limit=0&cursor=" + cursor, null, null);
                assertEquals(400, invalidLimit.statusCode());
                assertEquals("INVALID_REQUEST", errorCode(invalidLimit));
                String tamperedCursor = cursor.substring(0, cursor.length() - 1) + (cursor.endsWith("A") ? "B" : "A");
                HttpResponse<String> tampered = request(reporter, base, "GET", reports + "?limit=1&cursor=" + tamperedCursor, null, null);
                assertEquals(400, tampered.statusCode());
                assertEquals("INVALID_CURSOR", errorCode(tampered));
                HttpResponse<String> actorRebound = request(otherReporter, base, "GET", reports + "?limit=1&cursor=" + cursor, null, null);
                assertEquals(400, actorRebound.statusCode());
                assertEquals("INVALID_CURSOR", errorCode(actorRebound));

                HttpResponse<String> anotherOwnerList = request(otherReporter, base, "GET", reports, null, null);
                assertEquals(200, anotherOwnerList.statusCode());
                assertEquals(0, parse(anotherOwnerList.body()).path("items").size());
                HttpResponse<String> hiddenDetail = request(otherReporter, base, "GET", reports + "/" + reportId, null, null);
                assertEquals(404, hiddenDetail.statusCode());
                assertEquals("NOT_FOUND", errorCode(hiddenDetail));
                assertEquals("Not found", parse(hiddenDetail.body()).path("error").path("message").asText());
                secure(hiddenDetail);
                HttpResponse<String> absentDetail = request(otherReporter, base, "GET", reports + "/" + UUID.randomUUID(), null, null);
                assertEquals(404, absentDetail.statusCode());
                assertEquals(hiddenDetail.body(), absentDetail.body(), "invisible and absent reports have the same response");
                HttpResponse<String> foreignList = request(foreignReporter, base, "GET",
                        "/api/projects/" + fixture.foreignProjectId() + "/reports", null, null);
                assertEquals(200, foreignList.statusCode());
                assertEquals(0, parse(foreignList.body()).path("items").size());
                HttpResponse<String> crossProject = request(reporter, base, "GET",
                        "/api/projects/" + fixture.foreignProjectId() + "/reports/" + reportId, null, null);
                assertEquals(404, crossProject.statusCode());
                assertEquals("NOT_FOUND", errorCode(crossProject));

                for (Session staff : List.of(developer, qa)) {
                    HttpResponse<String> staffList = request(staff, base, "GET", reports, null, null);
                    assertEquals(200, staffList.statusCode());
                    assertEquals(2, parse(staffList.body()).path("items").size(), "same-project staff can triage all reports");
                    HttpResponse<String> staffDetail = request(staff, base, "GET", reports + "/" + reportId, null, null);
                    assertEquals(200, staffDetail.statusCode());
                    assertEquals(CANARY + "\nline two 🧭", parse(staffDetail.body()).path("description").asText());
                    assertEquals(Set.copyOf(onlyKeys(report)), Set.copyOf(onlyKeys(parse(staffDetail.body()))));
                    HttpResponse<String> staffContext = request(staff, base, "GET", contextPath, null, null);
                    assertEquals(403, staffContext.statusCode());
                    assertEquals("FORBIDDEN", errorCode(staffContext));
                }
                assertEquals(0, items(request(developer, base, "GET", reports + "?assignedToMe=true", null, null)).size());
                assertEquals(2, items(request(developer, base, "GET", reports + "?assignedToMe=false", null, null)).size(),
                        "unassigned reports must match assignedToMe=false");
                sql.update("update td_reports set assigned_developer_id=? where id=?", fixture.developerId(), UUID.fromString(reportId));
                assertEquals(1, items(request(developer, base, "GET", reports + "?assignedToMe=true", null, null)).size());
                assertEquals(1, items(request(developer, base, "GET", reports + "?assignedToMe=false", null, null)).size());
                assertEquals(2, items(request(qa, base, "GET", reports + "?assignedToMe=false", null, null)).size());
                assertEquals(0, items(request(reporter, base, "GET", reports + "?status=CLOSED", null, null)).size());
                sql.update("update td_reports set assigned_developer_id=null where id=?", UUID.fromString(reportId));

                HttpResponse<String> contextReply = request(reporter, base, "GET", contextPath, null, null);
                assertEquals(200, contextReply.statusCode());
                secure(contextReply);
                JsonNode contextJson = parse(contextReply.body());
                assertEquals(fixture.projectId().toString(), contextJson.path("project").path("id").asText());
                assertEquals("Synthetic Reporter Project", contextJson.path("project").path("name").asText());
                assertEquals("REPORTER", contextJson.path("role").asText());
                assertEquals(Set.of("project", "role"), Set.copyOf(onlyKeys(contextJson)));
                assertEquals(Set.of("id", "name"), Set.copyOf(onlyKeys(contextJson.path("project"))));
                assertFalse(contextReply.body().contains(fixture.ownerLogin()));

                HttpResponse<String> nonMemberRead = request(foreignReporter, base, "GET", reports, null, null);
                assertEquals(404, nonMemberRead.statusCode());
                assertEquals("NOT_FOUND", errorCode(nonMemberRead));
                sql.update("update td_project_memberships set state='DISABLED' where project_id=? and user_id=?", fixture.projectId(), fixture.ownerId());
                try {
                    assertEquals(404, request(reporter, base, "GET", reports + "/" + reportId, null, null).statusCode());
                } finally {
                    sql.update("update td_project_memberships set state='ACTIVE' where project_id=? and user_id=?", fixture.projectId(), fixture.ownerId());
                }
                sql.update("update td_users set enabled=false where id=?", fixture.ownerId());
                try {
                    assertEquals(404, request(reporter, base, "GET", reports + "/" + reportId, null, null).statusCode());
                } finally {
                    sql.update("update td_users set enabled=true where id=?", fixture.ownerId());
                }
                sql.update("update td_projects set enabled=false where id=?", fixture.projectId());
                try {
                    assertEquals(404, request(reporter, base, "GET", reports + "/" + reportId, null, null).statusCode());
                } finally {
                    sql.update("update td_projects set enabled=true where id=?", fixture.projectId());
                }
                sql.update("update td_users set account_role='DEVELOPER' where id=?", fixture.ownerId());
                try {
                    assertEquals(403, request(reporter, base, "POST", reports, body, key).statusCode(),
                            "same-key replay still authorizes the actor's current role");
                } finally {
                    sql.update("update td_users set account_role='REPORTER' where id=?", fixture.ownerId());
                }

                sql.execute("alter table td_security_events rename to td_security_events_unavailable");
                HttpResponse<String> eventOutage;
                try {
                    eventOutage = request(foreignReporter, base, "GET", reports + "/" + reportId, null, null);
                } finally {
                    sql.execute("alter table td_security_events_unavailable rename to td_security_events");
                }
                assertEquals(503, eventOutage.statusCode());
                assertEquals("SERVICE_UNAVAILABLE", errorCode(eventOutage));

                sql.execute("alter table td_reports rename to td_reports_unavailable");
                HttpResponse<String> reportOutage;
                try {
                    reportOutage = request(reporter, base, "GET", reports, null, null);
                } finally {
                    sql.execute("alter table td_reports_unavailable rename to td_reports");
                }
                assertEquals(503, reportOutage.statusCode());
                assertEquals("SERVICE_UNAVAILABLE", errorCode(reportOutage));

                HttpResponse<String> forged = request(reporter, base, "POST", reports,
                        body.substring(0, body.length() - 1) + ",\"reporterUserId\":\"" + fixture.otherReporterId() + "\"}",
                        UUID.randomUUID().toString());
                assertEquals(400, forged.statusCode());
                assertEquals("INVALID_REQUEST", errorCode(forged));
                assertFalse(forged.body().contains(fixture.otherReporterId().toString()));
                for (String malformedBody : List.of("{}", "null", "[]", "{\"title\":null}",
                        "{\"title\":4}", body.substring(0, body.length() - 1) + ",\"surprise\":true}",
                        body.substring(0, body.length() - 1) + ",\"title\":\"duplicate\"}")) {
                    HttpResponse<String> malformed = request(reporter, base, "POST", reports, malformedBody,
                            UUID.randomUUID().toString());
                    assertEquals(400, malformed.statusCode(), "malformed report request should be rejected safely");
                    assertEquals("INVALID_REQUEST", errorCode(malformed));
                    assertFalse(malformed.body().contains(CANARY));
                    secure(malformed);
                }
                HttpResponse<String> badKey = request(reporter, base, "POST", reports, body, "not-a-canonical-uuid");
                assertEquals(400, badKey.statusCode());
                assertEquals("INVALID_REQUEST", errorCode(badKey));
                HttpResponse<String> badPath = request(reporter, base, "GET", reports + "/" + CANARY, null, null);
                assertEquals(400, badPath.statusCode());
                assertEquals("INVALID_REQUEST", errorCode(badPath));
                HttpResponse<String> oversized = request(reporter, base, "POST", reports,
                        reportBody(CANARY).replace("\"textLogs\":\"\"", "\"textLogs\":\"" + "x".repeat(270 * 1024) + "\""),
                        UUID.randomUUID().toString());
                assertEquals(400, oversized.statusCode());
                assertEquals("INVALID_REQUEST", errorCode(oversized));
                assertFalse(oversized.body().contains(CANARY));
                HttpResponse<String> chunkedOversized = chunked(reporter, base, reports,
                        reportBody(CANARY).replace("\"textLogs\":\"\"", "\"textLogs\":\"" + "x".repeat(270 * 1024) + "\""),
                        UUID.randomUUID().toString());
                assertEquals(400, chunkedOversized.statusCode());
                assertEquals("INVALID_REQUEST", errorCode(chunkedOversized));

                HttpResponse<String> missingCsrf = requestWithoutCsrf(reporter, base, "POST", reports, body, UUID.randomUUID().toString());
                assertEquals(403, missingCsrf.statusCode());
                assertEquals("CSRF_INVALID", errorCode(missingCsrf));
                HttpResponse<String> noCsrfAnonymous = anonymous(base, clientTls, "POST", reports, body, UUID.randomUUID().toString(), null);
                assertEquals(403, noCsrfAnonymous.statusCode(), "CSRF validation precedes authentication for mutations");
                assertEquals("CSRF_INVALID", errorCode(noCsrfAnonymous));
                HttpResponse<String> malformedBeforeCsrf = requestWithoutCsrf(reporter, base, "POST", reports,
                        "{\"unknown\":true}", UUID.randomUUID().toString());
                assertEquals(400, malformedBeforeCsrf.statusCode(), "request shape validation precedes CSRF");
                assertEquals("INVALID_REQUEST", errorCode(malformedBeforeCsrf));
                HttpResponse<String> anonymousRead = anonymous(base, clientTls, "GET", reports, null, null, null);
                assertEquals(401, anonymousRead.statusCode());
                assertEquals("UNAUTHENTICATED", errorCode(anonymousRead));
                secure(anonymousRead);

                HttpResponse<String> wrongRoleCreate = request(developer, base, "POST", reports, body, UUID.randomUUID().toString());
                assertEquals(403, wrongRoleCreate.statusCode());
                assertEquals("FORBIDDEN", errorCode(wrongRoleCreate));
                HttpResponse<String> wrongRoleInvalidContent = request(developer, base, "POST", reports,
                        reportBodyWithTitle(" \t\n"), UUID.randomUUID().toString());
                assertEquals(403, wrongRoleInvalidContent.statusCode(), "membership role checks precede field-content validation");
                assertEquals("FORBIDDEN", errorCode(wrongRoleInvalidContent));
                HttpResponse<String> invalidContent = request(reporter, base, "POST", reports,
                        reportBodyWithTitle(" \t\n"), UUID.randomUUID().toString());
                assertEquals(400, invalidContent.statusCode());
                assertEquals("VALIDATION_ERROR", errorCode(invalidContent));
                assertFalse(invalidContent.body().contains(" \t\n"));

                assertEquals(2, items(request(reporter, base, "GET", reports, null, null)).size(),
                        "failed requests and idempotent retries must not create another report");
                String securityEvents = String.join("\n", sql.queryForList("select row_to_json(e)::text from td_security_events e", String.class));
                assertFalse(securityEvents.contains(CANARY), "security events must not contain report content");

                String rollbackKey = UUID.randomUUID().toString();
                int reportsBeforeFailure = sql.queryForObject("select count(*) from td_reports", Integer.class);
                int keysBeforeFailure = sql.queryForObject("select count(*) from td_report_idempotency", Integer.class);
                int auditsBeforeFailure = sql.queryForObject("select count(*) from td_business_audit_events", Integer.class);
                sql.execute("alter table td_business_audit_events rename to td_business_audit_events_unavailable");
                HttpResponse<String> auditOutage;
                try {
                    auditOutage = request(reporter, base, "POST", reports, reportBody(CANARY + "-rollback"), rollbackKey);
                } finally {
                    sql.execute("alter table td_business_audit_events_unavailable rename to td_business_audit_events");
                }
                assertEquals(503, auditOutage.statusCode());
                assertEquals("SERVICE_UNAVAILABLE", errorCode(auditOutage));
                secure(auditOutage);
                assertEquals(reportsBeforeFailure, sql.queryForObject("select count(*) from td_reports", Integer.class));
                assertEquals(keysBeforeFailure, sql.queryForObject("select count(*) from td_report_idempotency", Integer.class));
                assertEquals(auditsBeforeFailure, sql.queryForObject("select count(*) from td_business_audit_events", Integer.class));
                HttpResponse<String> retryAfterRestore = request(reporter, base, "POST", reports,
                        reportBody(CANARY + "-rollback"), rollbackKey);
                assertEquals(201, retryAfterRestore.statusCode());
                assertEquals(reportsBeforeFailure + 1, sql.queryForObject("select count(*) from td_reports", Integer.class));
                assertEquals(keysBeforeFailure + 1, sql.queryForObject("select count(*) from td_report_idempotency", Integer.class));
                assertEquals(auditsBeforeFailure + 1, sql.queryForObject("select count(*) from td_business_audit_events", Integer.class));

                String assignmentPath = reports + "/" + parse(retryAfterRestore.body()).path("id").asText() + "/assignment";
                verifyAssignmentApi(base, assignmentPath, qa, developer, foreignReporter, fixture, canaries);

                // Error-event attribution must not invalidate session state before CSRF succeeds.
                String otherSession = sql.queryForObject("select primary_id from spring_session where principal_name=?",
                        String.class, fixture.otherReporterId().toString());
                var serialized = new java.io.ByteArrayOutputStream();
                try (var out = new java.io.ObjectOutputStream(serialized)) {
                    out.writeObject(Long.valueOf(System.currentTimeMillis() - Duration.ofHours(9).toMillis()));
                }
                sql.update("update spring_session_attributes set attribute_bytes=? where session_primary_id=? and attribute_name='td.authAt'",
                        serialized.toByteArray(), otherSession);
                assertEquals(403, requestWithoutCsrf(otherReporter, base, "POST", reports, body, UUID.randomUUID().toString()).statusCode());
                assertEquals(1, sql.queryForObject("select count(*) from spring_session where primary_id=?", Integer.class, otherSession),
                        "CSRF denial must not invalidate an absolutely expired session while attributing an event");
                assertEquals(400, requestWithoutCsrf(otherReporter, base, "POST", reports, "{", UUID.randomUUID().toString()).statusCode());
                assertEquals(400, requestWithoutCsrf(otherReporter, base, "POST", reports, "{}", UUID.randomUUID().toString()).statusCode());
                assertEquals(1, sql.queryForObject("select count(*) from spring_session where primary_id=?", Integer.class, otherSession),
                        "malformed and shape-error event attribution must not invalidate a session");
                HttpResponse<String> expiredRead = request(otherReporter, base, "GET", reports, null, null);
                assertEquals(401, expiredRead.statusCode());
                assertEquals("UNAUTHENTICATED", errorCode(expiredRead));
                assertEquals(0, sql.queryForObject("select count(*) from spring_session where primary_id=?", Integer.class, otherSession));
            }
        } finally {
            try (var paths = Files.walk(temp)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (java.io.IOException ignored) { }
                });
            }
        }
        for (String canary : canaries) assertFalse(output.getAll().contains(canary), "logs must not contain report text or auth canaries");
    }

    private void verifyAssignmentApi(URI base, String path, Session qa, Session developer, Session foreign,
                                     Fixture fixture, List<String> canaries) throws Exception {
        var candidates = request(qa, base, "GET", path, null, null);
        assertEquals(200, candidates.statusCode());
        var page = parse(candidates.body());
        assertEquals(Set.of("assignedDeveloperId", "assignmentEligible", "version", "items", "nextCursor"),
                Set.copyOf(onlyKeys(page)));
        assertEquals(1, page.path("items").size());
        assertEquals(Set.of("id"), Set.copyOf(onlyKeys(page.path("items").get(0))));
        assertEquals(fixture.developerId().toString(), page.path("items").get(0).path("id").asText());
        assertEquals(403, request(developer, base, "GET", path, null, null).statusCode());
        assertEquals(404, request(foreign, base, "GET", path, null, null).statusCode());

        String assign = json.writeValueAsString(Map.of("developerId", fixture.developerId(), "expectedVersion", 1));
        assertEquals("CSRF_INVALID", errorCode(requestWithoutCsrf(qa, base, "PATCH", path, assign, null)));
        assertEquals("FORBIDDEN", errorCode(request(developer, base, "PATCH", path, assign, null)));
        String forged = json.writeValueAsString(Map.of("developerId", fixture.developerId(), "expectedVersion", 1, "status", "CLOSED"));
        assertEquals("INVALID_REQUEST", errorCode(request(qa, base, "PATCH", path, forged, null)));
        var assigned = request(qa, base, "PATCH", path, assign, null);
        assertEquals(200, assigned.statusCode());
        assertEquals(Set.of("assignedDeveloperId", "version", "updatedAt"), Set.copyOf(onlyKeys(parse(assigned.body()))));
        assertEquals(2, parse(assigned.body()).path("version").asInt());
        assertEquals("STALE_VERSION", errorCode(request(qa, base, "PATCH", path, assign, null)));
        assertEquals("VALIDATION_ERROR", errorCode(request(qa, base, "PATCH", path, "{\"expectedVersion\":2}", null)));
        String reason = "assignment-reason-private-canary";
        canaries.add(reason);
        var cleared = request(qa, base, "PATCH", path,
                json.writeValueAsString(Map.of("expectedVersion", 2, "reason", reason)), null);
        assertEquals(200, cleared.statusCode());
        assertTrue(parse(cleared.body()).path("assignedDeveloperId").isNull());
        assertFalse(cleared.body().contains(reason));
    }

    private Fixture seed(JdbcTemplate sql) {
        UUID project = UUID.randomUUID(), foreignProject = UUID.randomUUID();
        sql.update("insert into td_projects(id,name) values (?,?),(?,?)", project, "Synthetic Reporter Project", foreignProject, "Synthetic Other Project");
        Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
        UUID owner = addUser(sql, encoder, "owner"), other = addUser(sql, encoder, "other"),
                developer = addUser(sql, encoder, "developer"), qa = addUser(sql, encoder, "qa"), foreign = addUser(sql, encoder, "foreign");
        membership(sql, project, owner, "REPORTER"); membership(sql, project, other, "REPORTER");
        membership(sql, project, developer, "DEVELOPER"); membership(sql, project, qa, "QA_REVIEWER");
        membership(sql, foreignProject, foreign, "REPORTER");
        membership(sql, foreignProject, owner, "REPORTER");
        return new Fixture(project, foreignProject, owner, other, developer, qa, foreign,
                "report-owner@example.test", "report-other@example.test", "report-developer@example.test",
                "report-qa@example.test", "report-foreign@example.test");
    }

    private UUID addUser(JdbcTemplate sql, Argon2PasswordEncoder encoder, String suffix) {
        UUID id = UUID.randomUUID();
        String role=suffix.equals("developer")?"DEVELOPER":suffix.equals("qa")?"QA_REVIEWER":"REPORTER";
        sql.update("insert into td_users(id,login_normalized,password_hash,account_role) values (?,?,?,?)", id,
                "report-" + suffix + "@example.test", encoder.encode(PASSWORD),role);
        return id;
    }

    private static void membership(JdbcTemplate sql, UUID project, UUID user, String role) {
        sql.update("insert into td_project_memberships(project_id,user_id,role,state) values (?,?,?,'ACTIVE')", project, user, role);
    }

    private Session login(URI base, SSLContext tls, String login, UUID userId, List<String> canaries) throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).sslContext(tls).connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> csrf = send(client, base, "GET", "/api/csrf", null, null, null);
        assertEquals(200, csrf.statusCode()); secure(csrf);
        String anonymousToken = parse(csrf.body()).path("token").asText(); canaries.add(anonymousToken);
        HttpResponse<String> response = send(client, base, "POST", "/api/session/login",
                "{\"login\":\"" + login + "\",\"password\":\"" + PASSWORD + "\"}", null, anonymousToken);
        assertEquals(200, response.statusCode(), "synthetic fixture login should succeed"); secure(response);
        assertEquals(userId.toString(), parse(response.body()).path("userId").asText());
        cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("TDSESSION"))
                .findFirst().ifPresent(cookie -> canaries.add(cookie.getValue()));
        String token = parse(send(client, base, "GET", "/api/csrf", null, null, null).body()).path("token").asText();
        canaries.add(token);
        return new Session(client, token);
    }

    private HttpResponse<String> request(Session session, URI base, String method, String path, String body, String idempotencyKey) throws Exception {
        return send(session.client(), base, method, path, body, idempotencyKey, method.equals("GET") ? null : session.csrf());
    }

    private HttpResponse<String> requestWithoutCsrf(Session session, URI base, String method, String path, String body, String key) throws Exception {
        return send(session.client(), base, method, path, body, key, null);
    }

    private HttpResponse<String> anonymous(URI base, SSLContext tls, String method, String path, String body, String key, String csrf) throws Exception {
        HttpClient client = HttpClient.newBuilder().sslContext(tls).connectTimeout(Duration.ofSeconds(5)).build();
        return send(client, base, method, path, body, key, csrf);
    }

    private static HttpResponse<String> chunked(Session session, URI base, String path, String body, String key) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").header("Idempotency-Key", key)
                .header("X-CSRF-TOKEN", session.csrf())
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes))).build();
        return session.client().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> send(HttpClient client, URI base, String method, String path,
                                              String body, String key, String csrf) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10));
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        if (key != null) request.header("Idempotency-Key", key);
        if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
        else request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String reportBody(String canary) {
        return "{\"title\":\"Issue 🧪\",\"description\":\"" + canary + "\\nline two 🧭\",\"environment\":\"Linux test\"," +
                "\"reproductionSteps\":\"1. Run test\",\"expectedBehavior\":\"Works\",\"actualBehavior\":\"Fails\",\"textLogs\":\"\"}";
    }

    private static String reportBodyWithTitle(String title) {
        String escaped = title.replace("\\", "\\\\").replace("\"", "\\\"").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
        return "{\"title\":\"" + escaped + "\",\"description\":\"details\",\"environment\":\"test\"," +
                "\"reproductionSteps\":\"steps\",\"expectedBehavior\":\"expected\",\"actualBehavior\":\"actual\",\"textLogs\":\"\"}";
    }

    private JsonNode items(HttpResponse<String> response) throws Exception {
        assertEquals(200, response.statusCode(), "report list must succeed before inspecting items");
        JsonNode items = parse(response.body()).path("items");
        assertTrue(items.isArray());
        return items;
    }

    private JsonNode parse(String source) throws Exception { return json.readTree(source); }
    private String errorCode(HttpResponse<String> response) throws Exception { return parse(response.body()).path("error").path("code").asText(); }

    private static List<String> onlyKeys(JsonNode value) {
        return List.copyOf(value.propertyNames());
    }

    private static boolean canonicalUuid(String value) {
        try { return UUID.fromString(value).toString().equals(value); } catch (IllegalArgumentException ignored) { return false; }
    }

    private static void secure(HttpResponse<String> response) {
        assertTrue(response.headers().firstValue("strict-transport-security").isPresent());
        assertEquals("nosniff", response.headers().firstValue("x-content-type-options").orElse("").toLowerCase());
        assertTrue(response.headers().firstValue("cache-control").orElse("").contains("no-store"));
    }

    private static void createKeyStore(Path path, String password) throws Exception {
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        Process process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "tracedesk-test", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "2", "-storetype", "PKCS12", "-keystore", path.toString(),
                "-storepass", password, "-keypass", password, "-dname", "CN=localhost", "-ext", "SAN=ip:127.0.0.1")
                .redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        if (process.waitFor() != 0) throw new IllegalStateException("Could not create ephemeral test TLS identity");
    }

    private static SSLContext trustKeyStore(Path path, String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(path)) { keyStore.load(input, password.toCharArray()); }
        TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(keyStore);
        SSLContext context = SSLContext.getInstance("TLS"); context.init(null, managers.getTrustManagers(), null); return context;
    }

    private record Session(HttpClient client, String csrf) { }
    private record Fixture(UUID projectId, UUID foreignProjectId, UUID ownerId, UUID otherReporterId,
                           UUID developerId, UUID qaId, UUID foreignReporterId, String ownerLogin,
                           String otherLogin, String developerLogin, String qaLogin, String foreignLogin) { }
}
