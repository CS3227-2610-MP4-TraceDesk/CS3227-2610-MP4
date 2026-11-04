package com.tracedesk.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.sql.DriverManager;

import org.junit.jupiter.api.Test;

/** Required CI test: absent configuration fails; it never skips. */
class IsolatedPostgresIntegrationTest {
    @Test
    void ownsSelectsAndDropsOnlyItsSchema() throws Exception {
        var settings = System.getenv();
        String schema;
        try (var fixture = IsolatedPostgres.open(settings);
             var connection = fixture.getConnection()) {
            schema = fixture.schema();
            assertEquals(schema, connection.getSchema());
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE fixture_marker (id integer PRIMARY KEY)");
            }
            try (var fresh = fixture.getConnection();
                 var statement = fresh.createStatement();
                 var rows = statement.executeQuery("SELECT count(*) FROM fixture_marker")) {
                rows.next();
                assertEquals(0, rows.getInt(1));
            }
        }
        try (var admin = DriverManager.getConnection(settings.get("TRACEDESK_TEST_JDBC_URL"),
                settings.get("TRACEDESK_TEST_DATABASE_USER"), settings.get("TRACEDESK_TEST_DATABASE_PASSWORD"));
             var statement = admin.createStatement();
             var rows = statement.executeQuery("SELECT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = '" + schema + "')")) {
            rows.next();
            assertFalse(rows.getBoolean(1));
        }
    }
}
