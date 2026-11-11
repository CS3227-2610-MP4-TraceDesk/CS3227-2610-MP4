package com.tracedesk.auth.api;

import com.tracedesk.auth.application.ProjectAuthorization;
import com.tracedesk.auth.persistence.AuthStore;
import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the PostgreSQL authorization query/revocation linearization point over HTTPS. */
class AuthRevocationRaceIntegrationTest {
    private static final String PASSWORD = "race-synthetic-password-2026";

    @Test
    void authorizedInFlightReadMayFinishButCommittedRevocationBlocksReplay() throws Exception {
        Path temp = Files.createTempDirectory("tracedesk-auth-race-https-");
        try (IsolatedPostgres db = IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration")
                    .schemas(db.schema()).defaultSchema(db.schema()).load().migrate();
            String configuredUrl = System.getenv("TRACEDESK_TEST_JDBC_URL");
            String dbUrl = configuredUrl + (configuredUrl.contains("?") ? "&" : "?") + "currentSchema=" + db.schema();
            String keyPassword = UUID.randomUUID().toString().replace("-", "");
            Path keyStorePath = temp.resolve("race-test.p12");
            createKeyStore(keyStorePath, keyPassword);
            SSLContext clientTls = trustKeyStore(keyStorePath, keyPassword);

            SpringApplication app = new SpringApplication(com.tracedesk.auth.AuthApplication.class,
                    RaceTestConfiguration.class);
            app.setWebApplicationType(WebApplicationType.SERVLET);
            app.setDefaultProperties(Map.ofEntries(
                    Map.entry("server.address", "127.0.0.1"),
                    Map.entry("server.port", "0"),
                    Map.entry("server.ssl.enabled", "true"),
                    Map.entry("server.ssl.key-store", keyStorePath.toAbsolutePath().toString()),
                    Map.entry("server.ssl.key-store-password", keyPassword),
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
                AuthStore store = context.getBean(AuthStore.class);
                RepositoryProbe probe = context.getBean(RepositoryProbe.class);
                UUID actor = UUID.randomUUID();
                UUID project = UUID.randomUUID();
                sql.update("insert into td_projects(id,name) values(?,?)", project, "Race protected project");
                Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
                sql.update("insert into td_users(id,login_normalized,password_hash,account_role) values(?,?,?,'REPORTER')",
                        actor, "race-reporter@example.test", encoder.encode(PASSWORD));
                sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,?,'ACTIVE')",
                        project, actor, "REPORTER");

                URI base = URI.create("https://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port"));
                CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
                HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).sslContext(clientTls)
                        .connectTimeout(Duration.ofSeconds(5)).build();
                HttpResponse<String> csrf = send(client, base, "GET", "/api/csrf", null, null);
                assertEquals(200, csrf.statusCode());
                String csrfToken = jsonField(csrf.body(), "token");
                assertFalse(csrfToken.isBlank());
                HttpResponse<String> login = send(client, base, "POST", "/api/session/login",
                        "{\"login\":\"race-reporter@example.test\",\"password\":\"" + PASSWORD + "\"}", csrfToken);
                assertEquals(200, login.statusCode());
                assertEquals(actor.toString(), jsonField(login.body(), "userId"));
                String sessionId = cookies.getCookieStore().getCookies().stream()
                        .filter(cookie -> cookie.getName().equals("TDSESSION")).findFirst().orElseThrow().getValue();

                probe.arm();
                var executor = Executors.newSingleThreadExecutor();
                try {
                    var inFlight = executor.submit(() -> send(client, base, "GET", "/api/projects/" + project, null, null));
                    assertTrue(probe.awaitQuery(), "request must reach the repository query boundary");
                    assertEquals(1, sql.queryForObject("select count(*) from spring_session where principal_name=?",
                            Integer.class, actor.toString()));

                    store.changeMembershipState(project, actor, "DISABLED");
                    assertEquals("DISABLED", sql.queryForObject("select state from td_project_memberships where project_id=? and user_id=?",
                            String.class, project, actor));
                    assertEquals(0, sql.queryForObject("select count(*) from spring_session where principal_name=?",
                            Integer.class, actor.toString()), "committed revocation deletes the authenticated session");
                    probe.release();

                    HttpResponse<String> completedRead = inFlight.get(10, TimeUnit.SECONDS);
                    assertEquals(200, completedRead.statusCode(), "the read whose authorization query completed first may finish");
                    assertEquals("Race protected project", jsonField(completedRead.body(), "name"));
                    assertEquals(project.toString(), jsonField(completedRead.body(), "id"));

                    HttpClient replayClient = HttpClient.newBuilder().sslContext(clientTls)
                            .connectTimeout(Duration.ofSeconds(5)).build();
                    HttpResponse<String> replayedSession = sendWithCookie(replayClient, base, "/api/session", sessionId);
                    assertEquals(401, replayedSession.statusCode());
                    assertEquals("UNAUTHENTICATED", jsonField(replayedSession.body(), "code"));
                    HttpResponse<String> replayedProject = sendWithCookie(replayClient, base,
                            "/api/projects/" + project, sessionId);
                    assertEquals(401, replayedProject.statusCode());
                    assertEquals("UNAUTHENTICATED", jsonField(replayedProject.body(), "code"));
                    assertFalse(replayedProject.body().contains("Race protected project"));
                    assertEquals(0, sql.queryForObject("select count(*) from spring_session where principal_name=?",
                            Integer.class, actor.toString()), "the old cookie must not recreate a session after revocation");
                } finally {
                    probe.release();
                    executor.shutdownNow();
                }
            }
        } finally {
            try (var paths = Files.walk(temp)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (java.io.IOException ignored) { }
                });
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RaceTestConfiguration {
        @Bean
        RepositoryProbe repositoryProbe() { return new RepositoryProbe(); }

        @Bean
        @Primary
        ProjectAuthorization probedProjectAuthorization(AuthStore store, RepositoryProbe probe) {
            return new ProjectAuthorization((actor, target) -> {
                Optional<com.tracedesk.auth.application.ProjectAccessRepository.Snapshot> result =
                        store.findProjectAccess(actor, target);
                probe.blockAfterQueryIfArmed();
                return result;
            });
        }
    }

    private static final class RepositoryProbe {
        private volatile CountDownLatch queryCompleted = new CountDownLatch(0);
        private volatile CountDownLatch allowReturn = new CountDownLatch(0);
        private final AtomicBoolean armed = new AtomicBoolean();

        void arm() {
            queryCompleted = new CountDownLatch(1);
            allowReturn = new CountDownLatch(1);
            armed.set(true);
        }

        boolean awaitQuery() throws InterruptedException { return queryCompleted.await(10, TimeUnit.SECONDS); }

        void release() { allowReturn.countDown(); }

        void blockAfterQueryIfArmed() {
            if (!armed.compareAndSet(true, false)) return;
            queryCompleted.countDown();
            try {
                if (!allowReturn.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("revocation race probe timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("revocation race probe interrupted", interrupted);
            }
        }
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

    private static HttpResponse<String> sendWithCookie(HttpClient client, URI base, String path, String sessionId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10))
                .header("Cookie", "TDSESSION=" + sessionId).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
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
