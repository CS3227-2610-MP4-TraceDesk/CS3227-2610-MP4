package com.tracedesk.auth.api;

import com.tracedesk.testing.IsolatedPostgres;
import com.tracedesk.auth.persistence.AuthStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.flywaydb.core.Flyway;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Full HTTP boundary check over HTTPS and a fresh, owned PostgreSQL schema. */
@ExtendWith(OutputCaptureExtension.class)
class AuthHttpsIntegrationTest {
    private static final String PASSWORD = "synthetic-long-password-2026";
    private static final String PRIVACY_CANARY = "https-event-canary-7f31c9";

    @Test
    void realHttpsSessionCsrfAndRoleMembershipBoundaries(CapturedOutput capturedOutput) throws Exception {
        Path temp = Files.createTempDirectory("tracedesk-auth-https-");
        List<String> logCanaries = new ArrayList<>(List.of(PASSWORD, PRIVACY_CANARY));
        assertFalse(java.lang.reflect.Modifier.isPublic(
                Class.forName("com.tracedesk.ai.internal.soclaas.SoclaasClient").getModifiers()),
                "the secured factory may load transport, but role controllers cannot access the raw client");
        try (IsolatedPostgres db = IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration")
                    .schemas(db.schema()).defaultSchema(db.schema()).load().migrate();
            String dbUrl = System.getenv("TRACEDESK_TEST_JDBC_URL") +
                    (System.getenv("TRACEDESK_TEST_JDBC_URL").contains("?") ? "&" : "?") +
                    "currentSchema=" + db.schema();
            String storePassword = UUID.randomUUID().toString().replace("-", "");
            Path keyStorePath = temp.resolve("ephemeral.p12");
            createKeyStore(keyStorePath, storePassword);
            SSLContext clientTls = trustKeyStore(keyStorePath, storePassword);

            SpringApplication app = new SpringApplication(com.tracedesk.auth.AuthApplication.class);
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
                    Map.entry("logging.level.root", "WARN")));

            try (ConfigurableApplicationContext context = app.run()) {
                JdbcTemplate sql = context.getBean(JdbcTemplate.class);
                UUID projectA = UUID.randomUUID();
                UUID projectB = UUID.randomUUID();
                sql.update("insert into td_projects(id,name) values(?,?)", projectA, "Synthetic A");
                sql.update("insert into td_projects(id,name) values(?,?)", projectB, "Synthetic B");
                Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
                List<UUID> actors = new ArrayList<>();
                String[] roles = {"REPORTER", "DEVELOPER", "QA_REVIEWER"};
                for (int i = 0; i < roles.length; i++) {
                    UUID id = UUID.randomUUID();
                    actors.add(id);
                    String login = "https-" + roles[i].toLowerCase() + "@example.test";
                    logCanaries.add(login);
                    sql.update("insert into td_users(id,login_normalized,password_hash,account_role) values(?,?,?,?)",
                            id, login, encoder.encode(PASSWORD), roles[i]);
                    sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,?,'ACTIVE')",
                            projectA, id, roles[i]);
                }
                UUID disabledActor = UUID.randomUUID();
                sql.update("insert into td_users(id,login_normalized,password_hash,enabled) values(?,?,?,false)",
                        disabledActor, "disabled@example.test", encoder.encode(PASSWORD));

                URI base = URI.create("https://127.0.0.1:" +
                        context.getEnvironment().getProperty("local.server.port"));
                for (int i = 0; i < roles.length; i++) {
                    CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
                    HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).sslContext(clientTls)
                            .connectTimeout(Duration.ofSeconds(5)).build();
                    HttpResponse<String> csrf = send(client, base, "GET", "/api/csrf", null, null);
                    assertEquals(200, csrf.statusCode());
                    String token = jsonField(csrf.body(), "token");
                    logCanaries.add(token);
                    assertFalse(token.isBlank());
                    assertSecureResponse(csrf);
                    HttpCookie anonymous = cookies.getCookieStore().getCookies().stream()
                            .filter(c -> c.getName().equals("TDSESSION")).findFirst().orElseThrow();
                    String anonymousId = anonymous.getValue();
                    logCanaries.add(anonymousId);

                    String login = "https-" + roles[i].toLowerCase() + "@example.test";
                    HttpResponse<String> loggedIn = send(client, base, "POST", "/api/session/login",
                            "{\"login\":\"" + login + "\",\"password\":\"" + PASSWORD + "\"}", token);
                    assertEquals(200, loggedIn.statusCode(), roles[i]);
                    assertEquals(actors.get(i).toString(), jsonField(loggedIn.body(), "userId"));
                    assertSecureResponse(loggedIn);
                    HttpCookie authenticated = cookies.getCookieStore().getCookies().stream()
                            .filter(c -> c.getName().equals("TDSESSION")).findFirst().orElseThrow();
                    assertNotEquals(anonymousId, authenticated.getValue(), "login must rotate session ID");
                    HttpClient fixationReplay = HttpClient.newBuilder().sslContext(clientTls).build();
                    HttpResponse<String> preAuthReplay = sendWithCookie(fixationReplay, base, anonymousId);
                    assertEquals(401, preAuthReplay.statusCode(), "pre-authentication session identifier must be invalid after login");

                    HttpResponse<String> session = send(client, base, "GET", "/api/session", null, null);
                    assertEquals(200, session.statusCode());
                    assertEquals(actors.get(i).toString(), jsonField(session.body(), "userId"));
                    assertEquals(roles[i], jsonField(session.body(), "role"));
                    assertSecureResponse(session);
                    assertNonPersistentCookie(loggedIn);

                    String businessStateBefore = businessState(sql);
                    HttpResponse<String> allowed = send(client, base, "GET", "/api/projects/" + projectA, null, null);
                    assertEquals(200, allowed.statusCode(), roles[i]);
                    assertEquals("Synthetic A", jsonField(allowed.body(), "name"));
                    assertTrue(allowed.body().contains("\"id\""));
                    assertEquals(2, java.util.regex.Pattern.compile("\"[^\"]+\"\\s*:").matcher(allowed.body()).results().count());
                    HttpResponse<String> denied = send(client, base, "GET", "/api/projects/" + projectB +
                            "?userId=" + UUID.randomUUID() + "&role=ADMIN&note=" + PRIVACY_CANARY, null, null);
                    assertEquals(404, denied.statusCode(), roles[i]);
                    assertEquals("NOT_FOUND", jsonField(denied.body(), "code"));
                    assertFalse(denied.body().contains("Synthetic B"));
                    assertFalse(denied.body().contains(PRIVACY_CANARY));
                    HttpResponse<String> absentProject = send(client, base, "GET", "/api/projects/" + UUID.randomUUID(), null, null);
                    assertEquals(404, absentProject.statusCode());
                    assertEquals(denied.body(), absentProject.body(), "absent and inaccessible projects must be indistinguishable");
                    assertEquals(businessStateBefore, businessState(sql),
                            "allowed and denied project reads must not mutate users, projects or memberships");
                    if (i == 1) {
                        sql.update("update td_project_memberships set state='DISABLED' where project_id=? and user_id=?", projectA, actors.get(i));
                        HttpResponse<String> disabledMembership = send(client, base, "GET", "/api/projects/" + projectA, null, null);
                        assertEquals(404, disabledMembership.statusCode(), "fresh authorization denies a disabled membership");
                        assertEquals("NOT_FOUND", jsonField(disabledMembership.body(), "code"));
                        assertFalse(disabledMembership.body().contains("Synthetic A"));
                        sql.update("update td_project_memberships set state='ACTIVE' where project_id=? and user_id=?", projectA, actors.get(i));
                    }
                    for (String invalidProject : List.of(projectA.toString().toUpperCase(), PRIVACY_CANARY + "x".repeat(1024))) {
                        HttpResponse<String> malformed = send(client, base, "GET", "/api/projects/" + invalidProject, null, null);
                        assertEquals(400, malformed.statusCode());
                        assertEquals("INVALID_REQUEST", jsonField(malformed.body(), "code"));
                        assertFalse(malformed.body().contains(PRIVACY_CANARY));
                        assertFalse(malformed.body().contains("Synthetic A"));
                        assertSecureResponse(malformed);
                    }

                    HttpResponse<String> staleCsrf = send(client, base, "POST", "/api/session/logout", "{}", token);
                    assertEquals(403, staleCsrf.statusCode(), "pre-authentication CSRF token must be stale after login");
                    assertEquals("CSRF_INVALID", jsonField(staleCsrf.body(), "code"));
                    assertEquals(200, send(client, base, "GET", "/api/session", null, null).statusCode(),
                            "stale CSRF denial must not change session state");

                    HttpResponse<String> refreshedCsrf = send(client, base, "GET", "/api/csrf", null, null);
                    String logoutToken = jsonField(refreshedCsrf.body(), "token");
                    logCanaries.add(logoutToken);
                    assertNotEquals(token, logoutToken, "authentication rotates CSRF token");
                    String authenticatedId = cookies.getCookieStore().getCookies().stream()
                            .filter(c -> c.getName().equals("TDSESSION")).findFirst().orElseThrow().getValue();
                    logCanaries.add(authenticatedId);
                    String repositorySessionId = new String(java.util.Base64.getDecoder().decode(authenticatedId), StandardCharsets.UTF_8);
                    logCanaries.add(repositorySessionId);

                    if (i == 0) {
                        var priorSession = sql.queryForMap("select primary_id,session_id,last_access_time,max_inactive_interval,expiry_time from spring_session where principal_name=?", actors.get(i).toString());
                        assertEquals(repositorySessionId, ((String) priorSession.get("session_id")).trim());
                        long authAtBefore = sessionAttributeLong(sql, (String) priorSession.get("primary_id"), "td.authAt");
                        Thread.sleep(5);
                        HttpResponse<String> renewed = send(client, base, "GET", "/api/session", null, null);
                        assertEquals(200, renewed.statusCode());
                        var renewedSession = sql.queryForMap("select last_access_time,max_inactive_interval,expiry_time from spring_session where principal_name=?", actors.get(i).toString());
                        long renewedAt = ((Number) renewedSession.get("last_access_time")).longValue();
                        assertTrue(renewedAt > ((Number) priorSession.get("last_access_time")).longValue(),
                                "authenticated access renews the idle timestamp");
                        assertEquals(1800, ((Number) renewedSession.get("max_inactive_interval")).intValue());
                        assertEquals(renewedAt + 1_800_000L, ((Number) renewedSession.get("expiry_time")).longValue());
                        assertEquals(authAtBefore, sessionAttributeLong(sql, (String) priorSession.get("primary_id"), "td.authAt"),
                                "idle renewal must not move the absolute-authentication timestamp");
                        sql.update("update spring_session set last_access_time=0,expiry_time=0 where session_id=?", repositorySessionId);
                        HttpResponse<String> idleExpired = send(client, base, "GET", "/api/session", null, null);
                        assertEquals(401, idleExpired.statusCode(), "an idle-expired JDBC session is rejected");
                        assertEquals("UNAUTHENTICATED", jsonField(idleExpired.body(), "code"));
                        assertEquals(0, sql.queryForObject("select count(*) from spring_session where session_id=?", Integer.class, repositorySessionId));
                    }

                    if (i == 1) {
                        sql.execute("alter table td_project_memberships rename to td_project_memberships_unavailable");
                        try {
                            HttpResponse<String> membershipOutage = send(client, base, "GET", "/api/projects/" + projectA, null, null);
                            assertEquals(503, membershipOutage.statusCode());
                            assertEquals("SERVICE_UNAVAILABLE", jsonField(membershipOutage.body(), "code"));
                            assertFalse(membershipOutage.body().contains("Synthetic A"));
                            assertSecureResponse(membershipOutage);
                        } finally {
                            sql.execute("alter table td_project_memberships_unavailable rename to td_project_memberships");
                        }
                        sql.execute("alter table td_users rename to td_users_unavailable");
                        try {
                        HttpResponse<String> userStoreOutage = send(client, base, "GET", "/api/session", null, null);
                            assertEquals(503, userStoreOutage.statusCode());
                            assertEquals("SERVICE_UNAVAILABLE", jsonField(userStoreOutage.body(), "code"));
                            assertSecureResponse(userStoreOutage);
                        } finally {
                            sql.execute("alter table td_users_unavailable rename to td_users");
                        }
                        sql.execute("alter table td_security_events rename to td_security_events_unavailable");
                        try {
                            HttpResponse<String> eventStoreOutage = send(client, base, "GET", "/api/projects/" + projectB, null, null);
                            assertEquals(503, eventStoreOutage.statusCode());
                            assertEquals("SERVICE_UNAVAILABLE", jsonField(eventStoreOutage.body(), "code"));
                            assertFalse(eventStoreOutage.body().contains("Synthetic B"));
                            assertSecureResponse(eventStoreOutage);
                        } finally {
                            sql.execute("alter table td_security_events_unavailable rename to td_security_events");
                        }
                    }

                    if (i == 2) {
                        sql.execute("alter table spring_session rename to spring_session_unavailable");
                        try {
                            HttpClient freshClient = HttpClient.newBuilder().sslContext(clientTls).build();
                            HttpResponse<String> sessionCreationOutage = send(freshClient, base, "GET", "/api/csrf", null, null);
                            assertEquals(503, sessionCreationOutage.statusCode(), "session creation must fail with a safe unavailable response");
                            assertEquals("SERVICE_UNAVAILABLE", jsonField(sessionCreationOutage.body(), "code"));
                            assertTrue(sessionCreationOutage.headers().allValues("set-cookie").stream()
                                    .noneMatch(cookie -> cookie.startsWith("TDSESSION=")),
                                    "failed session creation must not issue a session cookie");
                            assertSecureResponse(sessionCreationOutage);

                            HttpResponse<String> sessionOutage = send(client, base, "GET", "/api/session", null, null);
                            assertEquals(503, sessionOutage.statusCode());
                            assertEquals("SERVICE_UNAVAILABLE", jsonField(sessionOutage.body(), "code"));
                            assertFalse(sessionOutage.body().contains(actors.get(i).toString()));
                            assertSecureResponse(sessionOutage);
                        } finally {
                            sql.execute("alter table spring_session_unavailable rename to spring_session");
                        }

                        sql.execute("alter table spring_session rename to spring_session_unavailable");
                        try {
                            CookieManager freshCookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
                            HttpClient freshClient = HttpClient.newBuilder().cookieHandler(freshCookies).sslContext(clientTls)
                                    .connectTimeout(Duration.ofSeconds(5)).build();
                            HttpResponse<String> creationOutage = send(freshClient, base, "GET", "/api/csrf", null, null);
                            assertEquals(503, creationOutage.statusCode());
                            assertEquals("SERVICE_UNAVAILABLE", jsonField(creationOutage.body(), "code"));
                            assertFalse(freshCookies.getCookieStore().getCookies().stream()
                                    .anyMatch(cookie -> cookie.getName().equals("TDSESSION")),
                                    "failed session creation must not issue a session cookie");
                            assertSecureResponse(creationOutage);
                        } finally {
                            sql.execute("alter table spring_session_unavailable rename to spring_session");
                        }
                        HttpResponse<String> restoredSession = send(client, base, "GET", "/api/session", null, null);
                        assertEquals(200, restoredSession.statusCode(), "restoring the isolated session table restores service");
                        sql.update("update td_users set enabled=false where id=?", actors.get(i));
                        HttpResponse<String> disabledUserSession = send(client, base, "GET", "/api/session", null, null);
                        assertEquals(401, disabledUserSession.statusCode(), "disabled user cannot continue using an existing session");
                        assertEquals("UNAUTHENTICATED", jsonField(disabledUserSession.body(), "code"));
                    }

                    if (i == 1) {
                        sql.execute("create function deny_session_delete() returns trigger language plpgsql as $$ begin raise exception 'synthetic delete outage'; end $$");
                        sql.execute("create trigger deny_session_delete before delete on spring_session for each row execute function deny_session_delete()");
                        try {
                            HttpResponse<String> failedLogout = send(client, base, "POST", "/api/session/logout", "{}", logoutToken);
                            assertEquals(503, failedLogout.statusCode(), "logout must not claim success if invalidation fails");
                            assertEquals("SERVICE_UNAVAILABLE", jsonField(failedLogout.body(), "code"));
                            assertSecureResponse(failedLogout);
                            assertEquals(1, sql.queryForObject("select count(*) from spring_session where session_id=?", Integer.class, repositorySessionId));
                        } finally {
                            sql.execute("drop trigger deny_session_delete on spring_session");
                            sql.execute("drop function deny_session_delete()");
                        }
                        // A failed invalidation need not retain the browser cookie. Establish
                        // a separate fresh session to exercise the successful logout contract.
                        cookies.getCookieStore().removeAll();
                        String freshLoginCsrf = jsonField(send(client, base, "GET", "/api/csrf", null, null).body(), "token");
                        assertEquals(200, send(client, base, "POST", "/api/session/login",
                                "{\"login\":\"" + login + "\",\"password\":\"" + PASSWORD + "\"}", freshLoginCsrf).statusCode());
                        logoutToken = jsonField(send(client, base, "GET", "/api/csrf", null, null).body(), "token");
                        authenticatedId = cookies.getCookieStore().getCookies().stream()
                                .filter(cookie -> cookie.getName().equals("TDSESSION")).findFirst().orElseThrow().getValue();
                        logCanaries.addAll(List.of(freshLoginCsrf, logoutToken, authenticatedId));
                        HttpResponse<String> logout = send(client, base, "POST", "/api/session/logout", "{}", logoutToken);
                        assertEquals(204, logout.statusCode());
                        assertSecureResponse(logout);

                        HttpClient replay = HttpClient.newBuilder().sslContext(clientTls).build();
                        HttpResponse<String> replayResponse = sendWithCookie(replay, base, authenticatedId);
                        assertEquals(401, replayResponse.statusCode(), "logout must invalidate server session");
                        assertEquals("UNAUTHENTICATED", jsonField(replayResponse.body(), "code"));
                        assertSecureResponse(replayResponse);
                        assertClearedCookie(logout);
                        HttpResponse<String> afterLogoutCsrf = send(client, base, "GET", "/api/csrf", null, null);
                        assertNotEquals(logoutToken, jsonField(afterLogoutCsrf.body(), "token"),
                                "the next anonymous session obtains fresh CSRF state after logout");
                    }

                    assertEquals(0, sql.queryForObject("select count(*) from td_security_events where " +
                            "route_template not in ('/api/session','/api/session/login','/api/session/logout','/api/workspaces','/api/projects','/api/projects/{projectId}','/api/projects/{projectId}/join') " +
                            "or event_code not in ('LOGIN_DENIED','LOGIN_INVALID_REQUEST','CSRF_INVALID','UNAUTHENTICATED','PROJECT_DENIED','PROJECT_INVALID_ID','PROJECT_INVALID_REQUEST','PROJECT_MUTATION_DENIED')", Integer.class));
                    assertEquals(0, sql.queryForObject("select count(*) from td_security_events where " +
                            "actor_id is not null and actor_id not in (?,?,?)", Integer.class, actors.get(0), actors.get(1), actors.get(2)));
                    assertEquals(0, sql.queryForObject("select count(*) from td_security_events where " +
                            "concat_ws('|',event_code,route_template,actor_id::text,project_id::text) like ?", Integer.class,
                            "%" + PRIVACY_CANARY + "%"));
                    assertEquals(actors.get(i), sql.queryForObject("select actor_id from td_security_events where event_code='PROJECT_DENIED' and project_id=? order by id desc limit 1", UUID.class, projectB));
                    assertEquals(List.of("actor_id", "event_code", "id", "occurred_at", "outcome_status", "project_id", "request_id", "route_template"),
                            sql.queryForList("select column_name from information_schema.columns where table_schema=? and table_name='td_security_events' order by column_name", String.class,
                                    db.schema()));
                }

                List<HttpResponse<String>> failedLogins = new ArrayList<>();
                List<String> failureLogins = List.of(PRIVACY_CANARY + "@example.test", "not-an-email",
                        "disabled@example.test", "https-reporter@example.test");
                for (String login : failureLogins) {
                    CookieManager failedCookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
                    HttpClient failedClient = HttpClient.newBuilder().cookieHandler(failedCookies).sslContext(clientTls).build();
                    String csrfToken = jsonField(send(failedClient, base, "GET", "/api/csrf", null, null).body(), "token");
                    logCanaries.add(csrfToken);
                    String rejectedPassword = (login.equals(PRIVACY_CANARY + "@example.test")
                            || login.equals("https-reporter@example.test")) ? PRIVACY_CANARY : PASSWORD;
                    String body = "{\"login\":\"" + login + "\",\"password\":\"" + rejectedPassword + "\"}";
                    HttpResponse<String> failure = send(failedClient, base, "POST", "/api/session/login", body, csrfToken);
                    assertFalse(failure.body().contains(PRIVACY_CANARY));
                    assertClearedCookie(failure);
                    assertEquals(0, sql.queryForObject("select count(*) from td_security_events where concat_ws('|',event_code,route_template,actor_id::text,project_id::text) like ?", Integer.class,
                            "%" + csrfToken + "%"), "CSRF values are not persisted in security events");
                    failedLogins.add(failure);
                }
                assertEquals(401, failedLogins.get(0).statusCode());
                assertEquals(failedLogins.get(0).body(), failedLogins.get(1).body(), "format-invalid login matches unknown-user failure");
                assertEquals(failedLogins.get(0).body(), failedLogins.get(2).body(), "disabled-user login matches unknown-user failure");
                assertEquals(failedLogins.get(0).body(), failedLogins.get(3).body(), "wrong-password login matches unknown-user failure");
                assertEquals("INVALID_CREDENTIALS", jsonField(failedLogins.get(0).body(), "code"));
                assertEquals(0, sql.queryForObject("select count(*) from td_security_events where concat_ws('|',event_code,route_template,actor_id::text,project_id::text) like ?", Integer.class,
                        "%" + PRIVACY_CANARY + "%"), "login IDs, passwords, paths and query text are not persisted in events");
                assertEquals(0, sql.queryForObject("select count(*) from td_security_events where concat_ws('|',event_code,route_template,actor_id::text,project_id::text) like ?", Integer.class,
                        "%" + PRIVACY_CANARY + "%"));

                CookieManager forgedCookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
                HttpClient forgedClient = HttpClient.newBuilder().cookieHandler(forgedCookies).sslContext(clientTls).build();
                String forgedCsrf = jsonField(send(forgedClient, base, "GET", "/api/csrf", null, null).body(), "token");
                logCanaries.add(forgedCsrf);
                HttpResponse<String> forgedLogin = send(forgedClient, base, "POST", "/api/session/login",
                        "{\"login\":\"https-reporter@example.test\",\"password\":\"" + PASSWORD + "\",\"userId\":\"" + actors.get(0) + "\",\"role\":\"REPORTER\"}", forgedCsrf);
                assertEquals(400, forgedLogin.statusCode(), "identity/role request fields are rejected");
                assertEquals("INVALID_REQUEST", jsonField(forgedLogin.body(), "code"));
                assertEquals(401, send(forgedClient, base, "GET", "/api/session", null, null).statusCode(),
                        "forged login claims do not create an authenticated session");

                List<String> duplicateKeyBodies = List.of(
                        "{\"login\":\"invalid-first@example.test\",\"login\":\"https-reporter@example.test\",\"password\":\"" + PASSWORD + "\"}",
                        "{\"login\":\"https-reporter@example.test\",\"password\":\"wrong-first-password\",\"password\":\"" + PASSWORD + "\"}");
                int invalidRequestEventsBefore = sql.queryForObject("select count(*) from td_security_events where event_code='LOGIN_INVALID_REQUEST' and route_template='/api/session/login'", Integer.class);
                int credentialDenialsBefore = sql.queryForObject("select count(*) from td_security_events where event_code='LOGIN_DENIED' and route_template='/api/session/login'", Integer.class);
                for (String duplicateKeyBody : duplicateKeyBodies) {
                    CookieManager duplicateCookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
                    HttpClient duplicateClient = HttpClient.newBuilder().cookieHandler(duplicateCookies).sslContext(clientTls).build();
                    String duplicateCsrf = jsonField(send(duplicateClient, base, "GET", "/api/csrf", null, null).body(), "token");
                    logCanaries.add(duplicateCsrf);
                    HttpResponse<String> duplicateKey = send(duplicateClient, base, "POST", "/api/session/login",
                            duplicateKeyBody, duplicateCsrf);
                    assertEquals(400, duplicateKey.statusCode());
                    assertEquals("INVALID_REQUEST", jsonField(duplicateKey.body(), "code"));
                    assertEquals("Invalid request", jsonField(duplicateKey.body(), "message"));
                    assertFalse(duplicateKey.body().contains(PASSWORD));
                    assertSecureResponse(duplicateKey);
                    assertTrue(duplicateKey.headers().allValues("set-cookie").stream()
                            .noneMatch(cookie -> cookie.startsWith("TDSESSION=")),
                            "a malformed login must not issue or replace the session cookie");
                    HttpResponse<String> duplicateSession = send(duplicateClient, base, "GET", "/api/session", null, null);
                    assertEquals(401, duplicateSession.statusCode(), "duplicate fields containing valid credentials must remain anonymous");
                    assertEquals("UNAUTHENTICATED", jsonField(duplicateSession.body(), "code"));
                    assertSecureResponse(duplicateSession);
                    assertEquals(0, sql.queryForObject("select count(*) from td_security_events where route_template='/api/session/login' and event_code not in ('LOGIN_INVALID_REQUEST','LOGIN_DENIED','CSRF_INVALID')", Integer.class));
                }
                assertEquals(invalidRequestEventsBefore + duplicateKeyBodies.size(),
                        sql.queryForObject("select count(*) from td_security_events where event_code='LOGIN_INVALID_REQUEST' and route_template='/api/session/login'", Integer.class),
                        "each duplicate-key request records its bounded malformed-login event");
                assertEquals(credentialDenialsBefore,
                        sql.queryForObject("select count(*) from td_security_events where event_code='LOGIN_DENIED' and route_template='/api/session/login'", Integer.class),
                        "duplicate-key requests are rejected before credential verification");

                List<String> malformedLoginBodies = List.of(
                        "{\"login\":123,\"password\":\"" + PASSWORD + "\"}",
                        "{\"login\":true,\"password\":\"" + PASSWORD + "\"}",
                        "{\"login\":\"https-reporter@example.test\",\"password\":123}",
                        "{\"login\":\"https-reporter@example.test\",\"password\":123456789012}",
                        "{\"login\":\"https-reporter@example.test\",\"password\":false}",
                        "{\"login\":[],\"password\":\"" + PASSWORD + "\"}",
                        "{\"login\":{},\"password\":\"" + PASSWORD + "\"}",
                        "{\"login\":null,\"password\":\"" + PASSWORD + "\"}",
                        "{\"login\":\"https-reporter@example.test\",\"password\":[]}",
                        "{\"login\":\"https-reporter@example.test\",\"password\":{}}",
                        "{\"login\":\"https-reporter@example.test\",\"password\":null}",
                        "{}", "null", "[]",
                        "{\"login\":\"" + PRIVACY_CANARY + "@example.test\",\"password\":\"unterminated",
                        "{\"login\":\"https-reporter@example.test\",\"password\":\"" + "x".repeat(8_200) + "\"}");
                int malformedEventsBefore = sql.queryForObject("select count(*) from td_security_events where event_code='LOGIN_INVALID_REQUEST'", Integer.class);
                int malformedCredentialDenialsBefore = sql.queryForObject("select count(*) from td_security_events where event_code='LOGIN_DENIED'", Integer.class);
                for (String malformedBody : malformedLoginBodies) {
                    CookieManager malformedCookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
                    HttpClient malformedClient = HttpClient.newBuilder().cookieHandler(malformedCookies)
                            .sslContext(clientTls).build();
                    String malformedCsrf = jsonField(send(malformedClient, base, "GET", "/api/csrf", null, null).body(), "token");
                    logCanaries.add(malformedCsrf);
                    HttpResponse<String> malformedLogin = send(malformedClient, base, "POST", "/api/session/login",
                            malformedBody, malformedCsrf);
                    assertEquals(400, malformedLogin.statusCode(), "wrong scalar types, malformed JSON, and oversized bodies are request-shape errors");
                    assertEquals("INVALID_REQUEST", jsonField(malformedLogin.body(), "code"));
                    assertEquals("Invalid request", jsonField(malformedLogin.body(), "message"));
                    assertFalse(malformedLogin.body().contains(PASSWORD));
                    assertFalse(malformedLogin.body().contains(PRIVACY_CANARY));
                    assertSecureResponse(malformedLogin);
                    assertTrue(malformedLogin.headers().allValues("set-cookie").stream()
                            .noneMatch(cookie -> cookie.startsWith("TDSESSION=")));
                    HttpResponse<String> malformedSession = send(malformedClient, base, "GET", "/api/session", null, null);
                    assertEquals(401, malformedSession.statusCode(), "malformed login does not authenticate");
                    assertEquals("UNAUTHENTICATED", jsonField(malformedSession.body(), "code"));
                }
                assertEquals(malformedEventsBefore + malformedLoginBodies.size(),
                        sql.queryForObject("select count(*) from td_security_events where event_code='LOGIN_INVALID_REQUEST'", Integer.class));
                assertEquals(malformedCredentialDenialsBefore,
                        sql.queryForObject("select count(*) from td_security_events where event_code='LOGIN_DENIED'", Integer.class));

                String oversizedLogin = "a".repeat(260) + "@example.test";
                CookieManager oversizedLoginCookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
                HttpClient oversizedLoginClient = HttpClient.newBuilder().cookieHandler(oversizedLoginCookies)
                        .sslContext(clientTls).build();
                String oversizedLoginCsrf = jsonField(send(oversizedLoginClient, base, "GET", "/api/csrf", null, null).body(), "token");
                logCanaries.add(oversizedLoginCsrf);
                HttpResponse<String> oversizedIdentifier = send(oversizedLoginClient, base, "POST", "/api/session/login",
                        "{\"login\":\"" + oversizedLogin + "\",\"password\":\"" + PASSWORD + "\"}", oversizedLoginCsrf);
                assertEquals(401, oversizedIdentifier.statusCode(), "an oversized but well-formed login identifier is invalid credentials");
                assertEquals("INVALID_CREDENTIALS", jsonField(oversizedIdentifier.body(), "code"));
                assertClearedCookie(oversizedIdentifier);

                AuthStore store = context.getBean(AuthStore.class);
                store.setUserEnabled(actors.get(2), true);
                List<Integer> revocationActors = List.of(0, 1, 2, 0);
                for (int revocation = 0; revocation < revocationActors.size(); revocation++) {
                    int actorIndex = revocationActors.get(revocation);
                    String role = roles[actorIndex].toLowerCase();
                    CookieManager revocationCookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
                    HttpClient revocationClient = HttpClient.newBuilder().cookieHandler(revocationCookies)
                            .sslContext(clientTls).build();
                    String revocationCsrf = jsonField(send(revocationClient, base, "GET", "/api/csrf", null, null).body(), "token");
                    logCanaries.add(revocationCsrf);
                    assertEquals(200, send(revocationClient, base, "POST", "/api/session/login",
                            "{\"login\":\"https-" + role + "@example.test\",\"password\":\"" + PASSWORD + "\"}",
                            revocationCsrf).statusCode(), "fresh authenticated session for revocation case " + revocation);
                    String oldSessionId = revocationCookies.getCookieStore().getCookies().stream()
                            .filter(cookie -> cookie.getName().equals("TDSESSION")).findFirst().orElseThrow().getValue();
                    logCanaries.add(oldSessionId);
                    if (revocation == 0) store.changeAccountRole(actors.get(actorIndex), "DEVELOPER");
                    else if (revocation == 1) store.changeMembershipState(projectA, actors.get(actorIndex), "DISABLED");
                    else if (revocation == 2) store.removeMembership(projectA, actors.get(actorIndex));
                    else store.setUserEnabled(actors.get(actorIndex), false);

                    HttpClient replayClient = HttpClient.newBuilder().sslContext(clientTls).build();
                    HttpResponse<String> replayedSession = sendWithCookie(replayClient, base, "/api/session", oldSessionId);
                    assertEquals(401, replayedSession.statusCode(), "AuthStore invalidation rejects old session case " + revocation);
                    assertEquals("UNAUTHENTICATED", jsonField(replayedSession.body(), "code"));
                    HttpResponse<String> replayedProject = sendWithCookie(replayClient, base,
                            "/api/projects/" + projectA, oldSessionId);
                    assertEquals(401, replayedProject.statusCode(), "revoked session cannot read project case " + revocation);
                    assertEquals("UNAUTHENTICATED", jsonField(replayedProject.body(), "code"));
                    assertFalse(replayedProject.body().contains("Synthetic A"));
                }
                String securityEvents = String.join("\n", sql.queryForList("select row_to_json(e)::text from td_security_events e", String.class));
                for (String canary : logCanaries) {
                    assertFalse(securityEvents.contains(canary), "security events must exclude credential, session and CSRF canaries");
                }
            }
            for (String canary : logCanaries) {
                assertFalse(capturedOutput.getAll().contains(canary), "application logs must not contain password, cookie, or CSRF canaries");
            }
        } finally {
            try (var paths = Files.walk(temp)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (java.io.IOException ignored) { }
                });
            }
        }
    }

    private static String businessState(JdbcTemplate sql) throws Exception {
        List<String> rows = sql.queryForList("select 'users:' || row_to_json(u)::text as row from td_users u " +
                "union all select 'projects:' || row_to_json(p)::text from td_projects p " +
                "union all select 'memberships:' || row_to_json(m)::text from td_project_memberships m order by row", String.class);
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(String.join("\n", rows).getBytes(StandardCharsets.UTF_8)));
    }

    private static HttpResponse<String> send(HttpClient client, URI base, String method, String path,
                                              String body, String csrf) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10));
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
        else request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String jsonField(String json, String key) {
        var matcher = java.util.regex.Pattern.compile("\\\"" + java.util.regex.Pattern.quote(key) +
                "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json);
        assertTrue(matcher.find(), "missing JSON field " + key + " in " + json);
        return matcher.group(1);
    }

    private static long sessionAttributeLong(JdbcTemplate sql, String primaryId, String name) throws Exception {
        byte[] bytes = sql.queryForObject("select attribute_bytes from spring_session_attributes where session_primary_id=? and attribute_name=?",
                byte[].class, primaryId, name);
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (Long) input.readObject();
        }
    }

    private static HttpResponse<String> sendWithCookie(HttpClient client, URI base, String sessionId) throws Exception {
        return sendWithCookie(client, base, "/api/session", sessionId);
    }

    private static HttpResponse<String> sendWithCookie(HttpClient client, URI base, String path, String sessionId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(base.resolve(path))
                .header("Cookie", "TDSESSION=" + sessionId).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void assertSecureResponse(HttpResponse<String> response) {
        Map<String, List<String>> headers = response.headers().map();
        assertTrue(headers.containsKey("strict-transport-security"));
        assertEquals("nosniff", response.headers().firstValue("x-content-type-options").orElse("").toLowerCase());
        assertEquals("no-referrer", response.headers().firstValue("referrer-policy").orElse(""));
        assertTrue(response.headers().firstValue("content-security-policy").orElse("").contains("default-src 'self'"));
        assertTrue(response.headers().firstValue("cache-control").orElse("").contains("no-store"));
        for (String setCookie : headers.getOrDefault("set-cookie", List.of())) {
            if (setCookie.startsWith("TDSESSION=")) {
                assertTrue(setCookie.contains("HttpOnly"));
                assertTrue(setCookie.contains("Secure"));
                assertTrue(setCookie.toLowerCase().contains("samesite=lax"));
                assertTrue(setCookie.contains("Path=/"));
                assertFalse(setCookie.toLowerCase().contains("domain="));
            }
        }
    }

    private static void assertNonPersistentCookie(HttpResponse<String> response) {
        List<String> cookies = response.headers().allValues("set-cookie").stream()
                .filter(value -> value.startsWith("TDSESSION=")).toList();
        assertFalse(cookies.isEmpty(), "authentication must set the session cookie");
        for (String cookie : cookies) {
            assertFalse(cookie.toLowerCase().contains("max-age="), "session cookie must not persist in the browser");
            assertFalse(cookie.toLowerCase().contains("expires="), "session cookie must not persist in the browser");
        }
    }

    private static void assertClearedCookie(HttpResponse<String> response) {
        List<String> cookies = response.headers().allValues("set-cookie").stream()
                .filter(value -> value.startsWith("TDSESSION=")).toList();
        assertFalse(cookies.isEmpty(), "logout expires the session cookie");
        assertTrue(cookies.stream().anyMatch(value -> value.toLowerCase().contains("max-age=0")));
        assertTrue(cookies.stream().allMatch(value -> value.contains("HttpOnly") && value.contains("Secure")
                && value.toLowerCase().contains("samesite=lax") && value.contains("Path=/")));
    }

    private static void createKeyStore(Path path, String password) throws Exception {
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        Process process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "tracedesk-test", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "2", "-storetype", "PKCS12", "-keystore", path.toString(),
                "-storepass", password, "-keypass", password, "-dname", "CN=localhost", "-ext", "SAN=ip:127.0.0.1")
                .redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        if (process.waitFor() != 0) throw new IllegalStateException("Could not create ephemeral test TLS identity: " + new String(output));
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
}
