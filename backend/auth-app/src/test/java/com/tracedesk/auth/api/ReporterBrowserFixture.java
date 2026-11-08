package com.tracedesk.auth.api;

import com.tracedesk.auth.AuthApplication;
import com.tracedesk.auth.persistence.AuthStore;
import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.core.env.MapPropertySource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

/** Owned, test-only application process for the same-candidate Reporter browser journey. */
public final class ReporterBrowserFixture {
    private static final int PORT = 18089;
    private static final String PASSWORD = "Synthetic-browser-password-2026!";

    private ReporterBrowserFixture() { }

    public static void main(String[] args) throws Exception {
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
            Flyway.configure().dataSource(db).locations("classpath:db/migration")
                    .schemas(db.schema()).defaultSchema(db.schema()).load().migrate();
            String rawTestUrl = required("TRACEDESK_TEST_JDBC_URL");
            String dbUrl = rawTestUrl + (rawTestUrl.contains("?") ? "&" : "?") + "currentSchema=" + db.schema();
            String keyStorePassword = UUID.randomUUID().toString().replace("-", "");
            Path keyStore = workDirectory.resolve("backend-test.p12");
            createKeyStore(keyStore, keyStorePassword);

            UUID projectId = UUID.randomUUID();
            UUID deniedProjectId = UUID.randomUUID();
            UUID reporterId = UUID.randomUUID();
            UUID nonmemberId = UUID.randomUUID();
            UUID developerId = UUID.randomUUID();
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            String login = "reporter-" + suffix + "@example.test";
            String nonmemberLogin = "nonmember-" + suffix + "@example.test";
            String developerLogin = "developer-" + suffix + "@example.test";

            SpringApplication app = new SpringApplication(AuthApplication.class);
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
            });

            try (ConfigurableApplicationContext context = app.run()) {
                JdbcTemplate sql = context.getBean(JdbcTemplate.class);
                if (context.getBean(DataSource.class) != db ||
                        !db.schema().equals(sql.queryForObject("select current_schema()", String.class))) {
                    throw new IllegalStateException("Fixture application did not bind to its owned PostgreSQL schema");
                }
                sql.update("insert into td_projects(id,name) values(?,?)", projectId, "Synthetic Reporter Project");
                sql.update("insert into td_projects(id,name) values(?,?)", deniedProjectId, "Synthetic Unavailable Project");
                Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
                seedUser(sql, encoder, reporterId, login);
                seedUser(sql, encoder, nonmemberId, nonmemberLogin);
                seedUser(sql, encoder, developerId, developerLogin);
                sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,'REPORTER','ACTIVE')",
                        projectId, reporterId);
                sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,'DEVELOPER','ACTIVE')",
                        projectId, developerId);
                Path metadataTemp = workDirectory.resolve("fixture.json.tmp");
                Files.writeString(metadataTemp, "{\"projectId\":\"" + projectId + "\",\"deniedProjectId\":\"" +
                        deniedProjectId + "\",\"reporterId\":\"" + reporterId + "\",\"login\":\"" + login +
                        "\",\"nonmemberLogin\":\"" + nonmemberLogin + "\",\"developerLogin\":\"" +
                        developerLogin + "\"}");
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
                        } else if ("audit-off".equals(command) && !auditUnavailable) {
                            sql.execute("alter table td_business_audit_events rename to td_business_audit_events_fixture_off");
                            auditUnavailable = true;
                            Files.writeString(ackFile, "done");
                        } else if ("audit-on".equals(command) && auditUnavailable) {
                            sql.execute("alter table td_business_audit_events_fixture_off rename to td_business_audit_events");
                            auditUnavailable = false;
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
        }
    }

    private static void seedUser(JdbcTemplate sql, Argon2PasswordEncoder encoder, UUID id, String login) {
        sql.update("insert into td_users(id,login_normalized,password_hash) values(?,?,?)",
                id, login, encoder.encode(PASSWORD));
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
