package com.tracedesk.local;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LocalSampleAccountsSetupTest {
    @TempDir Path temp;

    @Test void rejectsUnknownRoleAndMissingConfirmationWithoutCreatingCredentials() {
        assertThrows(LocalReporterSetupCommand.SafeFailure.class, () ->
                LocalSampleAccountsSetupCommand.run(new String[]{"reset", "admin"}, Map.of(), temp.resolve("private")));
        assertThrows(LocalReporterSetupCommand.SafeFailure.class, () ->
                LocalSampleAccountsSetupCommand.run(new String[]{"provision"}, Map.of(), temp.resolve("private")));
        assertFalse(Files.exists(temp.resolve("private")));
    }

    @Test void allAccountProfilesRejectRemoteTargetsAndProhibitedProfiles() {
        for (var account : LocalReporterSetupCommand.Account.values()) {
            var env = new HashMap<>(Map.of("TRACEDESK_LOCAL_CONFIRM", "true",
                    "TRACEDESK_LOCAL_JDBC_URL", "jdbc:postgresql://example.invalid:55419/tracedesk_dev",
                    "TRACEDESK_LOCAL_DATABASE_USER", "tracedesk_dev",
                    "TRACEDESK_LOCAL_DATABASE_PASSWORD", "synthetic-guard-canary"));
            Path file = temp.resolve("private").resolve(account.filename);
            assertEquals("database target is outside the local allowlist", assertThrows(
                    LocalReporterSetupCommand.SafeFailure.class, () ->
                            LocalReporterSetupCommand.run(new String[]{"provision"}, env, file, account)).getMessage());
            env.put("SPRING_PROFILES_ACTIVE", "production");
            assertEquals("a prohibited Spring profile is active", assertThrows(
                    LocalReporterSetupCommand.SafeFailure.class, () ->
                            LocalReporterSetupCommand.run(new String[]{"provision"}, env, file, account)).getMessage());
            assertFalse(Files.exists(file));
        }
    }

    @Test void provisionsThreeRolesInOneProjectAndResetOnlyChangesTheNamedAccount() throws Exception {
        var env = new HashMap<>(System.getenv());
        assertEquals("true", env.get("TRACEDESK_LOCAL_SMOKE_CONFIRM_DISPOSABLE"));
        String url = env.get("TRACEDESK_LOCAL_JDBC_URL"), user = env.get("TRACEDESK_LOCAL_DATABASE_USER");
        assertEquals("jdbc:postgresql://127.0.0.1:55419/tracedesk_dev", url);
        assertEquals("tracedesk_dev", user);
        env.put("TRACEDESK_LOCAL_CONFIRM", "true");
        var ds = new DriverManagerDataSource(url, user, env.get("TRACEDESK_LOCAL_DATABASE_PASSWORD"));
        Path directory = temp.resolve("private");
        UUID project = null;
        // Never adopt an existing sample tuple, even in the disposable database.
        try (var c = ds.getConnection(); var q = c.createStatement(); var r = q.executeQuery(
                "select to_regclass('public.td_local_reporter_owner')")) {
            if (r.next() && r.getString(1) != null) assertEquals(0, count(ds, "select count(*) from td_local_reporter_owner"));
        }
        try {
            var first = LocalSampleAccountsSetupCommand.run(new String[]{"provision"}, env, directory);
            project = first.getFirst().projectUuid();
            assertEquals(3, first.size());
            var originalPasswords = new HashMap<LocalReporterSetupCommand.Account, String>();
            var accountIds = new HashMap<LocalReporterSetupCommand.Account, UUID>();
            var encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
            for (var account : LocalReporterSetupCommand.Account.values()) {
                var file = new LocalReporterSetupCommand.LocalReporterCredentialFile(directory.resolve(account.filename), account.login);
                var credential = file.readAndValidate(url, user);
                assertEquals(project, credential.projectUuid());
                if (account != LocalReporterSetupCommand.Account.REPORTER) {
                    assertTrue("tracedesk-demo".equals(credential.password()), "staff samples must use the exact demo password");
                    assertEquals(account == LocalReporterSetupCommand.Account.DEVELOPER ? "developer@tracedesk.test" : "qa@tracedesk.test", credential.login());
                }
                assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(directory.resolve(account.filename)));
                try (var c = ds.getConnection(); var q = c.prepareStatement(
                        "select u.id,u.password_hash,u.account_role,m.role,m.state from td_users u join td_project_memberships m on m.user_id=u.id where u.login_normalized=? and m.project_id=?")) {
                    q.setString(1, account.login); q.setObject(2, project);
                    try (var r = q.executeQuery()) {
                        assertTrue(r.next());
                        accountIds.put(account, r.getObject(1, UUID.class));
                        assertTrue(encoder.matches(credential.password(), r.getString(2)), "generated credential must match the stored hash");
                        assertEquals(account.role, r.getString(3)); assertEquals(account.role, r.getString(4));
                        assertEquals("ACTIVE", r.getString(5)); assertFalse(r.next());
                    }
                }
                originalPasswords.put(account, credential.password());
                UUID session = UUID.randomUUID();
                try (var c = ds.getConnection(); var q = c.prepareStatement(
                        "insert into spring_session(primary_id,session_id,creation_time,last_access_time,max_inactive_interval,expiry_time,principal_name) values(?,?,0,0,1800,9999999999999,?)")) {
                    q.setString(1, session.toString()); q.setString(2, session.toString()); q.setString(3, accountIds.get(account).toString()); q.executeUpdate();
                }
            }
            assertEquals(first, LocalSampleAccountsSetupCommand.run(new String[]{"provision"}, env, directory));
            for (var account : LocalReporterSetupCommand.Account.values()) {
                var credential = new LocalReporterSetupCommand.LocalReporterCredentialFile(directory.resolve(account.filename), account.login).readAndValidate(url, user);
                assertTrue(originalPasswords.get(account).equals(credential.password()), "repeat provision must preserve each password");
                assertAuthenticates(ds, account, credential.password(), accountIds.get(account), project);
            }
            assertEquals(3, count(ds, "select count(*) from td_project_memberships where project_id='" + project + "'"));
            LocalSampleAccountsSetupCommand.run(new String[]{"reset", "developer"}, env, directory);
            for (var account : LocalReporterSetupCommand.Account.values()) {
                var credential = new LocalReporterSetupCommand.LocalReporterCredentialFile(directory.resolve(account.filename), account.login).readAndValidate(url, user);
                assertTrue(originalPasswords.get(account).equals(credential.password()), "staff reset restores the same demo password and preserves other credentials");
                assertAuthenticates(ds, account, credential.password(), accountIds.get(account), project);
                assertEquals(account == LocalReporterSetupCommand.Account.DEVELOPER ? 0 : 1,
                        count(ds, "select count(*) from spring_session where principal_name='" + accountIds.get(account) + "'"));
            }
            // Upgrade only an intact, previously owned QA tuple; an unowned target
            // login collision must restore its old file/hash/session without adoption.
            var qa = LocalReporterSetupCommand.Account.QA;
            UUID qaId = accountIds.get(qa), collisionId = UUID.randomUUID();
            String oldPassword = LocalReporterSetupCommand.randomPassword();
            String oldHash = encoder.encode(oldPassword);
            var qaFiles = new LocalReporterSetupCommand.LocalReporterCredentialFile(directory.resolve(qa.filename), qa.login);
            try (var c = ds.getConnection(); var q = c.prepareStatement("update td_users set login_normalized=?,password_hash=? where id=?")) {
                q.setString(1, "qa.local@tracedesk.invalid"); q.setString(2, oldHash); q.setObject(3, qaId); q.executeUpdate();
            }
            qaFiles.install(new LocalReporterSetupCommand.Credential(url, user, "qa.local@tracedesk.invalid", oldPassword, project));
            byte[] oldFile = Files.readAllBytes(directory.resolve(qa.filename));
            try (var c = ds.getConnection(); var q = c.prepareStatement("insert into td_users(id,login_normalized,password_hash,account_role) values(?,?,?,'QA_REVIEWER')")) {
                q.setObject(1, collisionId); q.setString(2, qa.login); q.setString(3, oldHash); q.executeUpdate();
            }
            try {
                assertThrows(LocalReporterSetupCommand.SafeFailure.class, () -> LocalSampleAccountsSetupCommand.run(new String[]{"provision"}, env, directory));
                assertTrue(java.util.Arrays.equals(oldFile, Files.readAllBytes(directory.resolve(qa.filename))), "collision must restore the old credential file");
                assertEquals(1, count(ds, "select count(*) from spring_session where principal_name='" + qaId + "'"));
                try (var c = ds.getConnection(); var q = c.prepareStatement("select password_hash,login_normalized from td_users where id=?")) {
                    q.setObject(1, qaId);
                    try (var r = q.executeQuery()) { assertTrue(r.next()); assertTrue(oldHash.equals(r.getString(1))); assertEquals("qa.local@tracedesk.invalid", r.getString(2)); }
                }
            } finally {
                try (var c = ds.getConnection(); var q = c.prepareStatement("delete from td_users where id=?")) { q.setObject(1, collisionId); q.executeUpdate(); }
            }
            LocalSampleAccountsSetupCommand.run(new String[]{"provision"}, env, directory);
            assertEquals(0, count(ds, "select count(*) from spring_session where principal_name='" + qaId + "'"));
            assertEquals(1, count(ds, "select count(*) from td_local_qa_owner where user_id='" + qaId + "'"));
            assertTrue("tracedesk-demo".equals(qaFiles.readAndValidate(url, user).password()));
            assertAuthenticates(ds, qa, "tracedesk-demo", qaId, project);

            // A changed staff role cannot be repaired or silently adopted.
            try (var c = ds.getConnection(); var q = c.prepareStatement("update td_users set account_role='REPORTER' where id=?")) {
                q.setObject(1, accountIds.get(LocalReporterSetupCommand.Account.QA)); q.executeUpdate();
            }
            byte[] qaFile = Files.readAllBytes(directory.resolve("local-qa.json"));
            assertThrows(LocalReporterSetupCommand.SafeFailure.class, () -> LocalSampleAccountsSetupCommand.run(new String[]{"provision"}, env, directory));
            assertTrue(java.util.Arrays.equals(qaFile, Files.readAllBytes(directory.resolve("local-qa.json"))));

            // A failed staff marker write must roll back the account/membership and remove its staged file.
            try (var c = ds.getConnection(); var q = c.createStatement()) {
                q.executeUpdate("delete from spring_session where principal_name='" + accountIds.get(LocalReporterSetupCommand.Account.QA) + "'");
                q.executeUpdate("delete from td_project_memberships where user_id='" + accountIds.get(LocalReporterSetupCommand.Account.QA) + "'");
                q.executeUpdate("delete from td_users where id='" + accountIds.get(LocalReporterSetupCommand.Account.QA) + "'");
                q.executeUpdate("delete from td_local_qa_owner");
                q.execute("create function td_local_samples_fail() returns trigger language plpgsql as $$ begin raise exception 'synthetic marker failure'; end $$");
                q.execute("create trigger td_local_samples_fail before insert on td_local_qa_owner for each row execute function td_local_samples_fail()");
            }
            Files.delete(directory.resolve("local-qa.json"));
            try {
                assertThrows(LocalReporterSetupCommand.SafeFailure.class, () -> LocalSampleAccountsSetupCommand.run(new String[]{"provision"}, env, directory));
                assertFalse(Files.exists(directory.resolve("local-qa.json")));
                assertEquals(0, count(ds, "select count(*) from td_users where login_normalized='qa@tracedesk.test'"));
            } finally {
                try (var c = ds.getConnection(); var q = c.createStatement()) {
                    q.execute("drop trigger td_local_samples_fail on td_local_qa_owner"); q.execute("drop function td_local_samples_fail()");
                }
            }
            assertEquals(first.getFirst().projectUuid(), LocalSampleAccountsSetupCommand.run(new String[]{"provision"}, env, directory).getLast().projectUuid());
        } finally {
            if (project != null) try (var c = ds.getConnection()) {
                c.setAutoCommit(false);
                try {
                    for (var account : LocalReporterSetupCommand.Account.values()) {
                        try (var q = c.createStatement()) {
                            q.executeUpdate("delete from spring_session where principal_name in (select user_id::text from " + account.marker + " where project_id='" + project + "')");
                            q.executeUpdate("delete from td_project_memberships where user_id in (select user_id from " + account.marker + " where project_id='" + project + "')");
                            q.executeUpdate("delete from td_users where id in (select user_id from " + account.marker + " where project_id='" + project + "')");
                            q.executeUpdate("delete from " + account.marker + " where project_id='" + project + "'");
                        }
                    }
                    try (var q = c.prepareStatement("delete from td_projects where id=?")) { q.setObject(1, project); q.executeUpdate(); }
                    c.commit();
                } catch (Exception failure) { c.rollback(); throw failure; }
            }
        }
    }

    private static void assertAuthenticates(DriverManagerDataSource ds, LocalReporterSetupCommand.Account account,
                                            String password, UUID user, UUID project) throws Exception {
        var store = new com.tracedesk.auth.persistence.AuthStore(new org.springframework.jdbc.core.JdbcTemplate(ds));
        var encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
        var controller = new com.tracedesk.auth.api.AuthController(store,
                new com.tracedesk.auth.application.ProjectAuthorization(store),
                new com.tracedesk.auth.application.WorkspaceDiscovery(store), encoder, java.time.Clock.systemUTC());
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        var session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute("td.csrf", "synthetic-local-csrf");
        String previousId = session.getId();
        // Standalone MockMvc uses the production controller and real JDBC store,
        // with servlet sessions supplied by the test; never print request bodies.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/session/login")
                        .session(session).header("X-CSRF-TOKEN", "synthetic-local-csrf")
                        .contentType("application/json").content("{\"login\":\"" + account.login + "\",\"password\":\"" + password + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.userId").value(user.toString()));
        assertFalse(previousId.equals(session.getId()), "login must rotate the servlet session ID");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/session").session(session))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.role").value(account.role));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/workspaces").session(session))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].project.id").value(project.toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].role").value(account.role));
    }

    private static int count(DriverManagerDataSource ds, String sql) throws Exception {
        try (Connection c = ds.getConnection(); var q = c.createStatement(); var r = q.executeQuery(sql)) { r.next(); return r.getInt(1); }
    }
}
