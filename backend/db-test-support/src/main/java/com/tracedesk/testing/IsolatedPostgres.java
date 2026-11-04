package com.tracedesk.testing;

import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import javax.sql.DataSource;

/** Test-only, owned schema. Consumers must depend on this module as testImplementation. */
public final class IsolatedPostgres implements DataSource, AutoCloseable {
    private static final String TEST_URL = "TRACEDESK_TEST_JDBC_URL";
    private static final String TEST_USER = "TRACEDESK_TEST_DATABASE_USER";
    private static final String TEST_PASSWORD = "TRACEDESK_TEST_DATABASE_PASSWORD";
    private static final String CONFIRMATION = "TRACEDESK_TEST_DATABASE_CONFIRM_ISOLATED";

    private final String url;
    private final String user;
    private final String password;
    private final String schema;
    private boolean closed;

    private IsolatedPostgres(String url, String user, String password, String schema) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.schema = schema;
    }

    public static IsolatedPostgres open(Map<String, String> settings) throws SQLException {
        if (!"true".equals(settings.get(CONFIRMATION))) {
            throw new IllegalArgumentException("Explicit isolated test database confirmation is required");
        }
        String url = required(settings, TEST_URL);
        String user = required(settings, TEST_USER);
        String password = required(settings, TEST_PASSWORD);
        DatabaseTarget target = parseTarget(url);
        if (!target.database().matches("[a-z][a-z0-9_]*_test")) {
            throw new IllegalArgumentException("Test database name must end in _test");
        }
        for (String productionName : new String[]{"TRACEDESK_DATABASE_URL", "DATABASE_URL"}) {
            String productionUrl = settings.get(productionName);
            if (productionUrl != null && !productionUrl.isBlank()
                    && target.equals(parseTarget(productionUrl))) {
                throw new IllegalArgumentException("Test database target matches an application database");
            }
        }

        String schema = "td_test_" + UUID.randomUUID().toString().replace("-", "");
        IsolatedPostgres fixture = new IsolatedPostgres(url, user, password, schema);
        try (Connection admin = DriverManager.getConnection(url, user, password);
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        try (Connection ignored = fixture.getConnection()) {
            return fixture;
        } catch (SQLException | RuntimeException failure) {
            try {
                fixture.close();
            } catch (SQLException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    public String schema() {
        return schema;
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (closed) {
            throw new SQLException("Test schema has been closed");
        }
        Connection connection = DriverManager.getConnection(url, user, password);
        try {
            connection.setSchema(schema);
            try (Statement statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT current_schema()")) {
                if (!result.next() || !schema.equals(result.getString(1))) {
                    throw new SQLException("Connection did not select owned test schema");
                }
            }
            return connection;
        } catch (SQLException | RuntimeException failure) {
            connection.close();
            throw failure;
        }
    }

    @Override
    public Connection getConnection(String username, String suppliedPassword) throws SQLException {
        if (!user.equals(username) || !password.equals(suppliedPassword)) {
            throw new SQLException("Test credentials do not match fixture credentials");
        }
        return getConnection();
    }

    @Override
    public void close() throws SQLException {
        if (closed) {
            return;
        }
        try (Connection admin = DriverManager.getConnection(url, user, password);
             Statement statement = admin.createStatement()) {
            statement.execute("DROP SCHEMA " + schema + " CASCADE");
            closed = true;
        }
    }

    private static String required(Map<String, String> settings, String name) {
        String value = settings.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + name);
        }
        return value;
    }

    private static DatabaseTarget parseTarget(String raw) {
        try {
            String source = raw.startsWith("jdbc:") ? raw.substring(5) : raw;
            URI uri = new URI(source);
            if (!"postgresql".equals(uri.getScheme()) && !"postgres".equals(uri.getScheme())) {
                throw new IllegalArgumentException("PostgreSQL test URL required");
            }
            if (uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getPath() == null || !uri.getPath().matches("/[a-z][a-z0-9_]*")) {
                throw new IllegalArgumentException("Test URL requires a plain host and database name");
            }
            return new DatabaseTarget(uri.getHost().toLowerCase(), uri.getPort() < 0 ? 5432 : uri.getPort(),
                    uri.getPath().substring(1));
        } catch (URISyntaxException failure) {
            throw new IllegalArgumentException("Invalid PostgreSQL test URL");
        }
    }

    private record DatabaseTarget(String host, int port, String database) { }

    @Override public PrintWriter getLogWriter() { return null; }
    @Override public void setLogWriter(PrintWriter writer) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override public void setLoginTimeout(int seconds) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override public int getLoginTimeout() { return 0; }
    @Override public Logger getParentLogger() { return Logger.getLogger(IsolatedPostgres.class.getName()); }
    @Override public <T> T unwrap(Class<T> type) throws SQLException {
        if (type.isInstance(this)) return type.cast(this);
        throw new SQLException("Unsupported wrapper type");
    }
    @Override public boolean isWrapperFor(Class<?> type) { return type.isInstance(this); }
}
