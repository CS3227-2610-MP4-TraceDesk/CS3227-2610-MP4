package com.tracedesk.auth.persistence;

import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.tracedesk.auth.application.ProjectAuthorization;
import com.tracedesk.auth.domain.LoginPolicy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Required DB test: absent isolation configuration fails instead of skipping. */
class AuthPersistenceIntegrationTest {
    @Test
    void migrationConstraintsMembershipFreshnessSessionRevocationAndEvents() throws Exception {
        try (IsolatedPostgres db = IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
            JdbcTemplate sql = new JdbcTemplate(db);
            AuthStore store = new AuthStore(sql);
            ProjectAuthorization authorization = new ProjectAuthorization(store);

            UUID reporter = user(sql, "reporter@example.test");
            UUID developer = user(sql, "developer@example.test");
            UUID reviewer = user(sql, "reviewer@example.test");
            UUID projectA = UUID.randomUUID();
            UUID projectB = UUID.randomUUID();
            sql.update("insert into td_projects(id,name) values(?,?)", projectA, "Synthetic Project A");
            sql.update("insert into td_projects(id,name) values(?,?)", projectB, "Synthetic Project B");

            String normalizedDuplicate = LoginPolicy.normalize("ＲＥＰＯＲＴＥＲ@EXAMPLE.TEST");
            assertEquals("reporter@example.test", normalizedDuplicate);
            assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> user(sql, normalizedDuplicate), "normalized login identifiers are unique");

            UUID boundaryProject = UUID.randomUUID();
            sql.update("insert into td_projects(id,name) values(?,?)", boundaryProject, "🙂".repeat(120));
            assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> sql.update("insert into td_projects(id,name) values(?,?)", UUID.randomUUID(), "🙂".repeat(121)));
            assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> sql.update("insert into td_projects(id,name) values(?,?)", UUID.randomUUID(), " \t\n"),
                    "project names containing only whitespace are rejected");

            membership(sql, projectA, reporter, "REPORTER", "ACTIVE");
            membership(sql, projectA, developer, "DEVELOPER", "ACTIVE");
            membership(sql, projectA, reviewer, "QA_REVIEWER", "ACTIVE");
            for (UUID actor : List.of(reporter, developer, reviewer)) {
                assertTrue(authorization.readableProject(actor, projectA).isPresent());
                assertTrue(authorization.readableProject(actor, projectB).isEmpty(), "membership in project A must not grant project B access");
            }

            assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> membership(sql, projectA, reporter, "REPORTER", "ACTIVE"), "duplicate memberships are rejected");
            assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> membership(sql, projectB, reporter, "ADMIN", "ACTIVE"), "unrecognized roles are rejected");
            assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> membership(sql, projectB, reporter, "REPORTER", "PENDING"), "unrecognized membership states are rejected");
            assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> membership(sql, UUID.randomUUID(), reporter, "REPORTER", "ACTIVE"), "project foreign key is enforced");
            assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> membership(sql, projectA, UUID.randomUUID(), "REPORTER", "ACTIVE"), "user foreign key is enforced");

            sql.update("update td_project_memberships set state='DISABLED' where project_id=? and user_id=?", projectA, developer);
            assertTrue(authorization.readableProject(developer, projectA).isEmpty());
            store.setUserEnabled(reviewer, false);
            assertTrue(authorization.readableProject(reviewer, projectA).isEmpty(), "disabled users cannot read projects");
            assertFalse(store.enabled(UUID.randomUUID()), "missing users are not enabled");

            sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,?,?)",
                    projectB, reporter, "REPORTER", "ACTIVE");
            assertTrue(authorization.readableProject(reporter, projectB).isPresent());
            store.setUserEnabled(reporter, false);
            assertTrue(authorization.readableProject(reporter, projectB).isEmpty(), "disable observed by the same authorization query");
            store.setUserEnabled(reporter, true);

            UUID sessionPrimary = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            sql.update("""
                    insert into spring_session(primary_id,session_id,creation_time,last_access_time,
                        max_inactive_interval,expiry_time,principal_name)
                    values(?,?,0,0,1800,3600000,?)
                    """, sessionPrimary.toString(), sessionId.toString(), reporter.toString());
            sql.update("insert into spring_session_attributes(session_primary_id,attribute_name,attribute_bytes) values(?,?,?)",
                    sessionPrimary.toString(), "fixture", new byte[0]);
            sql.execute("drop table spring_session_attributes");
            assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> store.changeMembershipRole(projectA, reporter, "DEVELOPER"),
                    "membership update must roll back when associated session deletion fails");
            assertEquals("REPORTER", sql.queryForObject("select role from td_project_memberships where project_id=? and user_id=?", String.class, projectA, reporter));
            assertEquals(1, sql.queryForObject("select count(*) from spring_session where principal_name=?", Integer.class, reporter.toString()));
            sql.execute("create table spring_session_attributes (session_primary_id char(36) not null, attribute_name varchar(200) not null, attribute_bytes bytea not null, primary key (session_primary_id, attribute_name))");
            sql.update("insert into spring_session_attributes(session_primary_id,attribute_name,attribute_bytes) values(?,?,?)",
                    sessionPrimary.toString(), "fixture", new byte[0]);
            store.changeMembershipRole(projectA, reporter, "DEVELOPER");
            assertEquals("DEVELOPER", sql.queryForObject("select role from td_project_memberships where project_id=? and user_id=?", String.class, projectA, reporter));
            assertEquals(0, sql.queryForObject("select count(*) from spring_session where principal_name=?", Integer.class, reporter.toString()));
            assertEquals(0, sql.queryForObject("select count(*) from spring_session_attributes where session_primary_id=?", Integer.class, sessionPrimary.toString()));

            UUID disableSession = addSession(sql, developer);
            store.changeMembershipState(projectA, developer, "DISABLED");
            assertSessionDeleted(sql, developer, disableSession);

            UUID removeSession = addSession(sql, reporter);
            store.removeMembership(projectB, reporter);
            assertSessionDeleted(sql, reporter, removeSession);

            store.setUserEnabled(reviewer, true);
            UUID userDisableSession = addSession(sql, reviewer);
            store.setUserEnabled(reviewer, false);
            assertSessionDeleted(sql, reviewer, userDisableSession);

            // Synchronize after the authorization query's linearization point but
            // before the already-authorized read is delivered.
            CountDownLatch snapshotLoaded = new CountDownLatch(1);
            CountDownLatch allowDelivery = new CountDownLatch(1);
            ProjectAuthorization racingAuthorization = new ProjectAuthorization((actor, target) -> {
                var snapshot = store.findProjectAccess(actor, target);
                snapshotLoaded.countDown();
                try {
                    if (!allowDelivery.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("race test timed out");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                return snapshot;
            });
            var executor = Executors.newSingleThreadExecutor();
            try {
                var inFlight = executor.submit(() -> racingAuthorization.readableProject(reporter, projectA));
                assertTrue(snapshotLoaded.await(5, TimeUnit.SECONDS), "authorization query reached its decision point");
                store.changeMembershipState(projectA, reporter, "DISABLED");
                allowDelivery.countDown();
                assertTrue(inFlight.get(5, TimeUnit.SECONDS).isPresent(), "a read authorized before revocation may finish");
                assertTrue(authorization.readableProject(reporter, projectA).isEmpty(), "a subsequent query observes committed revocation");
            } finally {
                allowDelivery.countDown();
                executor.shutdownNow();
            }

            store.event("PROJECT_DENIED", "/api/projects/{projectId}", 404, reporter, projectA);
            sql.execute("drop table td_security_events");
            assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> store.event("PROJECT_DENIED", "/api/projects/{projectId}", 404, reporter, projectA));
        }
    }

    private static UUID user(JdbcTemplate sql, String login) {
        UUID id = UUID.randomUUID();
        sql.update("insert into td_users(id,login_normalized,password_hash) values(?,?,?)",
                id, login, "$argon2id$v=19$m=19456,t=2,p=1$synthetic$hash");
        return id;
    }

    private static void membership(JdbcTemplate sql, UUID project, UUID user, String role, String state) {
        sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,?,?)",
                project, user, role, state);
    }

    private static UUID addSession(JdbcTemplate sql, UUID actor) {
        UUID primary = UUID.randomUUID();
        sql.update("""
                insert into spring_session(primary_id,session_id,creation_time,last_access_time,
                    max_inactive_interval,expiry_time,principal_name)
                values(?,?,0,0,1800,3600000,?)
                """, primary.toString(), UUID.randomUUID().toString(), actor.toString());
        sql.update("insert into spring_session_attributes(session_primary_id,attribute_name,attribute_bytes) values(?,?,?)",
                primary.toString(), "fixture", new byte[0]);
        return primary;
    }

    private static void assertSessionDeleted(JdbcTemplate sql, UUID actor, UUID primary) {
        assertEquals(0, sql.queryForObject("select count(*) from spring_session where principal_name=?", Integer.class, actor.toString()));
        assertEquals(0, sql.queryForObject("select count(*) from spring_session_attributes where session_primary_id=?", Integer.class, primary.toString()));
    }
}
