package com.tracedesk.auth.persistence;

import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Executes the actual offline operator SQL template using only synthetic account IDs. */
class AccountRoleMappingTemplateIntegrationTest {
    private static final String INSERT_MARKER = "-- Add one explicit INSERT per reviewed UUID, for example:";

    @Test
    void actualTemplateValidatesRollsBackAndInvalidatesSessions() throws Exception {
        try (IsolatedPostgres db = IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
            JdbcTemplate sql = new JdbcTemplate(db);

            UUID first = pendingUser(sql, "mapping-first@example.test");
            UUID second = pendingUser(sql, "mapping-second@example.test");
            UUID extra = UUID.randomUUID();
            sql.update("insert into td_users(id,login_normalized,password_hash,account_role) values(?,?,?,?)",
                    extra, "mapping-extra@example.test", "synthetic-hash", "REPORTER");
            UUID firstSession = addSession(sql, first);
            UUID secondSession = addSession(sql, second);

            assertTemplateFails(sql, first, "REPORTER"); // incomplete
            assertUntouched(sql, first, second, firstSession, secondSession);
            assertTemplateFails(sql, first, "REPORTER", second, "DEVELOPER", extra, "QA_REVIEWER"); // extra account
            assertUntouched(sql, first, second, firstSession, secondSession);
            assertTemplateFails(sql, first, "REPORTER", second, "ADMIN"); // invalid role
            assertUntouched(sql, first, second, firstSession, secondSession);

            sql.execute("create function reject_mapping_session_delete() returns trigger language plpgsql as $$ begin raise exception 'synthetic session deletion failure'; end $$");
            sql.execute("create trigger reject_mapping_session_delete before delete on spring_session for each row execute function reject_mapping_session_delete()");
            assertTemplateFails(sql, first, "REPORTER", second, "DEVELOPER"); // DML rollback after session deletion fails
            assertUntouched(sql, first, second, firstSession, secondSession);
            sql.execute("drop trigger reject_mapping_session_delete on spring_session");
            sql.execute("drop function reject_mapping_session_delete()");

            runTemplate(sql, first, "REPORTER", second, "DEVELOPER");
            assertEquals("REPORTER", role(sql, first));
            assertEquals("DEVELOPER", role(sql, second));
            assertEquals(0, pendingCount(sql, first) + pendingCount(sql, second));
            assertSessionGone(sql, firstSession);
            assertSessionGone(sql, secondSession);
            assertEquals(0, sql.queryForObject("select count(*) from spring_session_attributes where session_primary_id in (?,?)",
                    Integer.class, firstSession.toString(), secondSession.toString()));
            assertEquals("REPORTER", role(sql, extra), "the template must not touch non-pending accounts");
        }
    }

    private static void assertTemplateFails(JdbcTemplate sql, Object... mappings) throws IOException {
        assertThrows(SQLException.class, () -> runTemplate(sql, mappings));
    }

    /** Injects synthetic rows at the template's documented insertion point and executes the complete SQL file. */
    private static void runTemplate(JdbcTemplate sql, Object... mappings) throws IOException, SQLException {
        String template = Files.readString(templatePath());
        StringBuilder inserts = new StringBuilder(INSERT_MARKER);
        for (int i = 0; i < mappings.length; i += 2) {
            UUID user = (UUID) mappings[i];
            String role = (String) mappings[i + 1];
            inserts.append("\ninsert into td_explicit_account_role_mapping(user_id,account_role) values ('")
                    .append(user).append("','").append(role).append("');");
        }
        String script = template.replace(INSERT_MARKER, inserts.toString());
        try (Connection connection = sql.getDataSource().getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(script);
            } catch (SQLException failure) {
                try { connection.rollback(); } catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                throw failure;
            }
        }
    }

    private static Path templatePath() throws IOException {
        for (Path candidate : new Path[]{Path.of("backend/auth-app/operator/account-role-mapping-template.sql"),
                Path.of("operator/account-role-mapping-template.sql")}) {
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IOException("operator mapping template not found from test working directory");
    }

    private static UUID pendingUser(JdbcTemplate sql, String login) {
        UUID id = UUID.randomUUID();
        sql.update("insert into td_users(id,login_normalized,password_hash,account_role) values(?,?,?,null)", id, login, "synthetic-hash");
        sql.update("insert into td_account_role_migration_pending(user_id,reason,legacy_roles) values(?,'NO_MEMBERSHIP_ROLE',array[]::varchar(16)[])", id);
        return id;
    }

    private static UUID addSession(JdbcTemplate sql, UUID actor) {
        UUID primary = UUID.randomUUID();
        sql.update("insert into spring_session(primary_id,session_id,creation_time,last_access_time,max_inactive_interval,expiry_time,principal_name) values(?,?,0,0,1800,3600000,?)",
                primary.toString(), UUID.randomUUID().toString(), actor.toString());
        sql.update("insert into spring_session_attributes(session_primary_id,attribute_name,attribute_bytes) values(?,'td.actor',?)", primary.toString(), new byte[]{1});
        return primary;
    }

    private static String role(JdbcTemplate sql, UUID id) { return sql.queryForObject("select account_role from td_users where id=?", String.class, id); }
    private static int pendingCount(JdbcTemplate sql, UUID id) { return sql.queryForObject("select count(*) from td_account_role_migration_pending where user_id=?", Integer.class, id); }
    private static void assertUntouched(JdbcTemplate sql, UUID a, UUID b, UUID aSession, UUID bSession) {
        assertNull(role(sql, a)); assertNull(role(sql, b));
        assertEquals(1, pendingCount(sql, a)); assertEquals(1, pendingCount(sql, b));
        assertSessionPresent(sql, aSession); assertSessionPresent(sql, bSession);
    }
    private static void assertSessionPresent(JdbcTemplate sql, UUID id) { assertEquals(1, sql.queryForObject("select count(*) from spring_session where primary_id=?", Integer.class, id.toString())); }
    private static void assertSessionGone(JdbcTemplate sql, UUID id) { assertEquals(0, sql.queryForObject("select count(*) from spring_session where primary_id=?", Integer.class, id.toString())); }
}
