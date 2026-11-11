package com.tracedesk.local;

import com.tracedesk.auth.domain.LoginPolicy;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;

/** Standalone, opt-in local development account provisioner. */
public final class LocalReporterSetupCommand {
    static final String DB_USER = "tracedesk_dev";
    static final String LOGIN = "reporter.local@tracedesk.invalid";
    static final String PROJECT = "Local Reporter Workspace";
    private static final long LOCK_KEY = 0x545244534B4C4F43L;
    private static final long PROCESS_LOCK_KEY = 0x545244534B4C4F44L;
    private static final PasswordEncoder PASSWORD_ENCODER = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    private static final SecureRandom RANDOM = new SecureRandom();

    static final String DEMO_PASSWORD = "tracedesk-demo";

    enum Account {
        REPORTER("REPORTER", LOGIN, "td_local_reporter_owner", "local-reporter.json"),
        DEVELOPER("DEVELOPER", "developer@tracedesk.test", "td_local_developer_owner", "local-developer.json"),
        QA("QA_REVIEWER", "qa@tracedesk.test", "td_local_qa_owner", "local-qa.json");

        final String role, login, marker, filename;
        Account(String role, String login, String marker, String filename) {
            this.role = role; this.login = login; this.marker = marker; this.filename = filename;
        }
        boolean acceptsLogin(String value) {
            return login.equals(value) || (this == DEVELOPER && "developer.local@tracedesk.invalid".equals(value))
                    || (this == QA && "qa.local@tracedesk.invalid".equals(value));
        }
        String setupPassword() { return this == REPORTER ? randomPassword() : DEMO_PASSWORD; }
    }

    private final Account account;
    private LocalReporterSetupCommand(Account account) { this.account = account; }

    public static void main(String[] args) {
        try {
            Result result = run(args, System.getenv(), Path.of(".local", "local-reporter.json").toAbsolutePath().normalize());
            System.out.println("projectUuid=" + result.projectUuid());
            System.out.println("login=" + result.login());
        } catch (SafeFailure failure) {
            System.err.println("Local Reporter setup failed: " + failure.getMessage());
            System.exit(2);
        } catch (Exception failure) {
            System.err.println("Local Reporter setup failed: operation could not be completed safely.");
            System.exit(2);
        }
    }

    static Result run(String[] args, Map<String, String> env, Path credentialPath) {
        return run(args, env, credentialPath, Account.REPORTER);
    }

    static Result run(String[] args, Map<String, String> env, Path credentialPath, Account account) {
        return new LocalReporterSetupCommand(account).runForAccount(args, env, credentialPath);
    }

    private Result runForAccount(String[] args, Map<String, String> env, Path credentialPath) {
        if (!"true".equals(env.get("TRACEDESK_LOCAL_CONFIRM"))) throw new SafeFailure("explicit local confirmation is required");
        Path dir=credentialPath.getParent();
        try {
            boolean existed=Files.exists(dir,LinkOption.NOFOLLOW_LINKS);
            if(existed) checkPrivatePath(dir,LocalReporterCredentialFile.DIR_MODE,true);
            else { Files.createDirectories(dir); Files.setPosixFilePermissions(dir,LocalReporterCredentialFile.DIR_MODE); checkPrivatePath(dir,LocalReporterCredentialFile.DIR_MODE,true); }
            Path lockPath=dir.resolve(".local-reporter.lock");
            boolean lockExisted=Files.exists(lockPath,LinkOption.NOFOLLOW_LINKS);
            if(lockExisted) checkPrivatePath(lockPath,LocalReporterCredentialFile.FILE_MODE,false);
            try(FileChannel channel=FileChannel.open(lockPath,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)) {
                if(!lockExisted) { Files.setPosixFilePermissions(lockPath,LocalReporterCredentialFile.FILE_MODE); checkPrivatePath(lockPath,LocalReporterCredentialFile.FILE_MODE,false); }
                try(var processLock=channel.lock()) { return runWithFileLock(args,env,credentialPath); }
            }
        } catch(SafeFailure e) { throw e; }
        catch(Exception e) { throw new SafeFailure("private local setup lock could not be acquired"); }
    }

    private static void checkPrivatePath(Path path, Set<PosixFilePermission> mode, boolean directory) throws IOException {
        if(Files.isSymbolicLink(path) || directory != Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)) throw new SafeFailure("local setup path is unsafe");
        var owner=Files.getOwner(path,LinkOption.NOFOLLOW_LINKS);
        if(!owner.equals(path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"))) || !Files.getPosixFilePermissions(path,LinkOption.NOFOLLOW_LINKS).equals(mode)) throw new SafeFailure("local setup path owner or permissions are unsafe");
    }

    private Result runWithFileLock(String[] args, Map<String, String> env, Path credentialPath) {
        if (args == null || args.length != 1 || !(args[0].equals("provision") || args[0].equals("reset")))
            throw new SafeFailure("expected exactly one mode: provision or reset");
        String mode = args[0];
        if (!"true".equals(env.get("TRACEDESK_LOCAL_CONFIRM"))) throw new SafeFailure("explicit local confirmation is required");
        String profiles = env.getOrDefault("SPRING_PROFILES_ACTIVE", "");
        for (String profile : profiles.split(",")) {
            String p = profile.trim().toLowerCase(java.util.Locale.ROOT);
            if (Set.of("prod", "production", "test").contains(p)) throw new SafeFailure("a prohibited Spring profile is active");
        }
        String url = env.get("TRACEDESK_LOCAL_JDBC_URL");
        String user = env.get("TRACEDESK_LOCAL_DATABASE_USER");
        String password = env.get("TRACEDESK_LOCAL_DATABASE_PASSWORD");
        validateUrl(url);
        if (!DB_USER.equals(user) || password == null || password.isBlank()) throw new SafeFailure("explicit local database credentials are required");

        try (Connection preflight = DriverManager.getConnection(url, user, password)) {
            verifyConnectedIdentity(preflight);
        } catch (SafeFailure e) { throw e; }
        catch (Exception e) { throw new SafeFailure("database identity could not be verified"); }

        DriverManagerDataSource ds = new DriverManagerDataSource(url, user, password);
        try (Connection lockConnection = DriverManager.getConnection(url, user, password)) {
            verifyConnectedIdentity(lockConnection);
            try (var lock = lockConnection.prepareStatement("select pg_advisory_lock(?)")) { lock.setLong(1,PROCESS_LOCK_KEY); lock.execute(); }
            try { return runLocked(mode, url, user, password, credentialPath, ds); }
            finally { try (var unlock=lockConnection.prepareStatement("select pg_advisory_unlock(?)")) { unlock.setLong(1,PROCESS_LOCK_KEY); unlock.execute(); } }
        } catch (SafeFailure e) { throw e; }
        catch (Exception e) { throw new SafeFailure("database advisory lock could not be acquired safely"); }
    }

    private Result runLocked(String mode, String url, String user, String password, Path credentialPath, DataSource ds) {
        try {
            migrateSilently(ds);
        } catch (Exception e) { throw new SafeFailure("TraceDesk migrations could not be applied (" + migrationFailureSummary(e) + ")"); }
        LocalReporterCredentialFile files = new LocalReporterCredentialFile(credentialPath, account.login);
        if (mode.equals("provision")) {
            if (files.exists()) {
                Credential existing = files.readAndValidate(url,user);
                Result result = verifyExisting(ds, existing);
                if (account != Account.REPORTER && (!account.login.equals(existing.login()) || !DEMO_PASSWORD.equals(existing.password()))) {
                    return replaceCredentials(ds, files, existing, url, user);
                }
                return result;
            }
            String newPassword = account.setupPassword();
            UUID newUser = UUID.randomUUID(), newProject = account == Account.REPORTER ? UUID.randomUUID() : sharedProject(ds);
            try {
                files.install(new Credential(url, user, account.login, newPassword, newProject));
                provision(ds, newUser, newProject, PASSWORD_ENCODER.encode(newPassword));
            } catch (Exception e) {
                files.deleteIfPresent();
                if (e instanceof SafeFailure sf) throw sf;
                throw new SafeFailure("provisioning was rolled back; resolve the local database conflict and retry");
            }
            return new Result(newProject, account.login);
        }

        Credential prior = files.readAndValidate(url, user);
        verifyExisting(ds, prior);
        return replaceCredentials(ds, files, prior, url, user);
    }

    private Result replaceCredentials(DataSource ds, LocalReporterCredentialFile files, Credential prior,
                                      String url, String user) {
        String replacementPassword = account.setupPassword();
        Credential replacement = new Credential(url, user, account.login, replacementPassword, prior.projectUuid());
        byte[] oldBytes = files.bytes();
        try {
            files.install(replacement);
            reset(ds, prior.projectUuid(), PASSWORD_ENCODER.encode(replacementPassword));
        } catch (Exception e) {
            try { files.restore(oldBytes); } catch (Exception restoreFailure) {
                throw new SafeFailure("database reset failed and credential restoration requires manual recovery");
            }
            if (e instanceof SafeFailure sf) throw sf;
            throw new SafeFailure("reset transaction failed; prior credentials were restored");
        }
        return new Result(prior.projectUuid(), account.login);
    }

    private static void migrateSilently(DataSource ds) {
        Flyway.configure().dataSource(ds).loggers(new String[0])
                .locations("classpath:db/migration").load().migrate();
    }

    static String migrationFailureSummary(Throwable failure) {
        String flywayType = "FlywayException";
        String sqlState = null;
        Integer migrationLine = null;
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 16; depth++, current = current.getCause()) {
            String simpleName = current.getClass().getSimpleName();
            if (current.getClass().getName().startsWith("org.flywaydb.") && simpleName.matches("[A-Za-z0-9]+"))
                flywayType = simpleName;
            if (current.getClass().getName().equals("org.flywaydb.core.internal.sqlscript.FlywaySqlScriptException")) {
                try {
                    Object line = current.getClass().getMethod("getLineNumber").invoke(current);
                    if (line instanceof Integer number && number > 0) migrationLine = number;
                } catch (ReflectiveOperationException ignored) {
                    // Keep diagnostics safe and compatible across Flyway versions.
                }
            }
            if (current instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().matches("[0-9A-Z]{5}"))
                sqlState = sql.getSQLState();
        }
        return flywayType + (migrationLine == null ? "" : ", migration line " + migrationLine)
                + (sqlState == null ? "" : ", SQLSTATE " + sqlState);
    }

    private static void validateUrl(String url) {
        if (url == null || !url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]{1,5}/tracedesk_dev")) throw new SafeFailure("database target is outside the local allowlist");
        String portText=url.substring(url.lastIndexOf(':')+1,url.lastIndexOf('/'));
        try { int port=Integer.parseInt(portText); if(port<1||port>65535) throw new SafeFailure("database port is outside the local allowlist"); }
        catch(NumberFormatException e) { throw new SafeFailure("database port is outside the local allowlist"); }
    }

    private static void verifyConnectedIdentity(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("select current_database(), current_user, inet_server_addr()::text, inet_server_port(), current_schema(), session_user")) {
            if (!rs.next() || !"tracedesk_dev".equals(rs.getString(1)) || !DB_USER.equals(rs.getString(2))
                    || !"public".equals(rs.getString(5)) || !DB_USER.equals(rs.getString(6)))
                throw new SafeFailure("connected database identity is outside the local allowlist");
        }
    }

    private void provision(DataSource ds, UUID userId, UUID projectId, String hash) {
        transaction(ds, c -> {
            ensureMarkerTable(c);
            lock(c);
            try (var q = c.createStatement(); var rs = q.executeQuery("select user_id, project_id from " + account.marker + " where singleton=true")) {
                if (rs.next()) {
                    UUID ownedUser = rs.getObject(1, UUID.class), ownedProject = rs.getObject(2, UUID.class);
                    verifyRows(c, ownedUser, ownedProject);
                    throw new SafeFailure("owned sample account records exist without matching credentials; stop and restore a matching local credential backup");
                }
            }
            if (exists(c, "select exists(select 1 from td_users where login_normalized='" + account.login + "')")
                    || (account == Account.REPORTER && exists(c, "select exists(select 1 from td_projects where name='Local Reporter Workspace')")))
                throw new SafeFailure("conflicting local sample account records exist without an ownership marker");
            try (var p = c.prepareStatement("insert into td_users(id,login_normalized,password_hash,enabled,account_role) values(?,?,?,true,?)")) {
                p.setObject(1,userId); p.setString(2,account.login); p.setString(3,hash); p.setString(4,account.role); p.executeUpdate();
            }
            if (account != Account.REPORTER) verifySharedProject(c, projectId);
            if (account == Account.REPORTER) try (var p = c.prepareStatement("insert into td_projects(id,name,enabled) values(?,?,true)")) {
                p.setObject(1,projectId); p.setString(2,PROJECT); p.executeUpdate();
            }
            try (var p = c.prepareStatement("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,?,'ACTIVE')")) {
                p.setObject(1,projectId); p.setObject(2,userId); p.setString(3,account.role); p.executeUpdate();
            }
            try (var p = c.prepareStatement("insert into " + account.marker + "(singleton,format_version,user_id,project_id) values(true,1,?,?)")) {
                p.setObject(1,userId); p.setObject(2,projectId); p.executeUpdate();
            }
            return new Result(projectId, account.login);
        });
    }

    private Result verifyExisting(DataSource ds, Credential credential) {
        return transaction(ds, c -> {
            ensureMarkerTable(c); lock(c);
            UUID userId;
            try(var p=c.prepareStatement("select user_id,project_id,format_version from " + account.marker + " where singleton=true")) {
                try(var r=p.executeQuery()) {
                    if(!r.next() || r.getInt(3)!=1 || !credential.projectUuid().equals(r.getObject(2,UUID.class))) throw new SafeFailure("local credential/database mismatch; stop and restore a matching local credential backup before retrying");
                    userId=r.getObject(1,UUID.class);
                }
            }
            verifyRows(c,userId,credential.projectUuid());
            try(var p=c.prepareStatement("select password_hash,login_normalized from td_users where id=?")) {
                p.setObject(1,userId); try(var r=p.executeQuery()) { if(!r.next() || !credential.login().equals(r.getString(2)) || !PASSWORD_ENCODER.matches(credential.password(),r.getString(1))) throw new SafeFailure("local credential/database mismatch; stop and restore a matching local credential backup before retrying"); }
            }
            return new Result(credential.projectUuid(),account.login);
        });
    }

    private void reset(DataSource ds, UUID projectId, String hash) {
        transaction(ds, c -> {
            ensureMarkerTable(c); lock(c);
            UUID userId;
            try(var p=c.prepareStatement("select user_id from " + account.marker + " where singleton=true and project_id=? and format_version=1")) { p.setObject(1,projectId); try(var r=p.executeQuery()) { if(!r.next()) throw new SafeFailure("ownership marker is missing or mismatched"); userId=r.getObject(1,UUID.class); } }
            verifyRows(c,userId,projectId);
            try (var p = c.prepareStatement("update td_users set password_hash=?, login_normalized=?, updated_at=now() where id=?")) { p.setString(1,hash); p.setString(2,account.login); p.setObject(3,userId); p.executeUpdate(); }
            try (var p = c.prepareStatement("delete from spring_session_attributes where session_primary_id in (select primary_id from spring_session where principal_name=?)")) { p.setString(1,userId.toString()); p.executeUpdate(); }
            try (var p = c.prepareStatement("delete from spring_session where principal_name=?")) { p.setString(1,userId.toString()); p.executeUpdate(); }
            return new Result(projectId,account.login);
        });
    }

    private void ensureMarkerTable(Connection c) throws SQLException {
        try (var s=c.createStatement()) { s.execute("create table if not exists " + account.marker + "(singleton boolean primary key check(singleton), format_version integer not null, user_id uuid not null, project_id uuid not null)"); }
    }
    private static void lock(Connection c) throws SQLException { try (var p=c.prepareStatement("select pg_advisory_xact_lock(?)")) { p.setLong(1,LOCK_KEY); p.execute(); } }
    private static boolean exists(Connection c, String sql) throws SQLException { try (var s=c.createStatement(); var r=s.executeQuery(sql)) { return r.next() && r.getBoolean(1); } }
    private void verifyRows(Connection c, UUID user, UUID project) throws SQLException {
        if (account != Account.REPORTER) verifySharedProject(c, project);
        try (var p=c.prepareStatement("select u.login_normalized,u.enabled,u.account_role,p.name,p.enabled,m.role,m.state from td_users u cross join td_projects p cross join td_project_memberships m where u.id=? and p.id=? and m.user_id=u.id and m.project_id=p.id")) {
            p.setObject(1,user); p.setObject(2,project); try(var r=p.executeQuery()) {
                if (!r.next() || !account.acceptsLogin(r.getString(1)) || !r.getBoolean(2) || !account.role.equals(r.getString(3)) || !PROJECT.equals(r.getString(4)) || !r.getBoolean(5) || !account.role.equals(r.getString(6)) || !"ACTIVE".equals(r.getString(7)) || r.next())
                    throw new SafeFailure("owned sample account records are missing or changed");
            }
        }
        try (var p=c.prepareStatement("select format_version from " + account.marker + " where singleton=true and user_id=? and project_id=?")) {
            p.setObject(1,user); p.setObject(2,project); try(var r=p.executeQuery()) { if(!r.next() || r.getInt(1)!=1 || r.next()) throw new SafeFailure("ownership marker is malformed or mismatched"); }
        }
    }

    private UUID sharedProject(DataSource ds) {
        return transaction(ds, c -> {
            lock(c);
            try (var q = c.createStatement(); var r = q.executeQuery(
                    "select user_id, project_id from td_local_reporter_owner where singleton=true and format_version=1")) {
                if (!r.next()) throw new SafeFailure("provision the owned Reporter project first");
                UUID project = r.getObject(2, UUID.class);
                new LocalReporterSetupCommand(Account.REPORTER).verifyRows(c, r.getObject(1, UUID.class), project);
                return project;
            }
        });
    }

    private void verifySharedProject(Connection c, UUID project) throws SQLException {
        try (var q = c.prepareStatement(
                "select user_id from td_local_reporter_owner where singleton=true and format_version=1 and project_id=?")) {
            q.setObject(1, project);
            try (var r = q.executeQuery()) {
                if (!r.next()) throw new SafeFailure("shared local Reporter project ownership is missing or changed");
                new LocalReporterSetupCommand(Account.REPORTER).verifyRows(c, r.getObject(1, UUID.class), project);
            }
        }
    }

    private interface Tx<T> { T run(Connection c) throws Exception; }
    private static <T> T transaction(DataSource ds, Tx<T> work) {
        try(Connection c=ds.getConnection()) { c.setAutoCommit(false); try { T result=work.run(c); c.commit(); return result; } catch(Exception e) { c.rollback(); throw e; } }
        catch(SafeFailure e) { throw e; } catch(Exception e) { throw new SafeFailure("database transaction failed safely"); }
    }

    static String randomPassword() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        StringBuilder out = new StringBuilder(64); for(byte b:bytes) out.append(String.format("%02x", b & 255));
        String value = out.toString(); if(!LoginPolicy.validPassword(value)) throw new IllegalStateException("password policy"); return value;
    }
    record Result(UUID projectUuid, String login) { }
    record Credential(String jdbcUrl, String databaseUser, String login, String password, UUID projectUuid) {
        byte[] json() {
            String value = "{\"formatVersion\":1,\"jdbcUrl\":\""+jdbcUrl+"\",\"databaseUser\":\""+databaseUser+"\",\"login\":\""+login+"\",\"password\":\""+password+"\",\"projectUuid\":\""+projectUuid+"\"}";
            return value.getBytes(StandardCharsets.UTF_8);
        }
    }
    static final class SafeFailure extends RuntimeException { SafeFailure(String message) { super(message); } }

    static final class LocalReporterCredentialFile {
        private static final Set<PosixFilePermission> DIR_MODE=EnumSet.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_EXECUTE);
        private static final Set<PosixFilePermission> FILE_MODE=EnumSet.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE);
        private final Path target;
        private final String expectedLogin;
        LocalReporterCredentialFile(Path target) { this(target, LOGIN); }
        LocalReporterCredentialFile(Path target, String expectedLogin) { this.target=target; this.expectedLogin=expectedLogin; }
        boolean exists(){return Files.exists(target,LinkOption.NOFOLLOW_LINKS);}
        void install(Credential credential) {
            Path dir=target.getParent(), temp=null;
            try {
                checkOwnerAndMode(dir,DIR_MODE,true);
                assertSafeExistingTarget();
                temp=Files.createTempFile(dir,".local-reporter-",".tmp"); Files.setPosixFilePermissions(temp,FILE_MODE);
                Files.write(temp,credential.json()); checkOwnerAndMode(temp,FILE_MODE,false);
                Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                temp=null; checkOwnerAndMode(target,FILE_MODE,false);
            } catch(SafeFailure e) { throw e; }
            catch(Exception e) { throw new SafeFailure("secure credential file creation or replacement failed"); }
            finally { if(temp!=null) try{Files.deleteIfExists(temp);}catch(Exception ignored){} }
        }
        void assertSafeExistingTarget() throws IOException {
            if(!Files.exists(target,LinkOption.NOFOLLOW_LINKS)) return;
            if(Files.isSymbolicLink(target)||!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)) throw new SafeFailure("credential path is not a safe regular file");
            checkOwnerAndMode(target,FILE_MODE,false);
        }
        void checkOwnerAndMode(Path path, Set<PosixFilePermission> expected, boolean directory) throws IOException {
            if(Files.isSymbolicLink(path)) throw new SafeFailure("credential path cannot be a symbolic link");
            var owner=Files.getOwner(path,LinkOption.NOFOLLOW_LINKS);
            if(!owner.equals(path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name")))) throw new SafeFailure("credential path has an unexpected owner");
            if(!Files.getPosixFilePermissions(path,LinkOption.NOFOLLOW_LINKS).equals(expected)) throw new SafeFailure("credential path permissions are not private");
        }
        Credential readAndValidate(String url,String dbUser) {
            try {
                assertSafeExistingTarget(); String value=Files.readString(target,StandardCharsets.UTF_8);
                Matcher m=Pattern.compile("^\\{\\\"formatVersion\\\":1,\\\"jdbcUrl\\\":\\\"([^\\\"]*)\\\",\\\"databaseUser\\\":\\\"([^\\\"]*)\\\",\\\"login\\\":\\\"([^\\\"]*)\\\",\\\"password\\\":\\\"([0-9a-f]{64}|tracedesk-demo)\\\",\\\"projectUuid\\\":\\\"([0-9a-fA-F-]{36})\\\"\\}$").matcher(value);
                if(!m.matches() || !url.equals(m.group(1)) || !dbUser.equals(m.group(2)) || !acceptsLogin(m.group(3))) throw new SafeFailure("credential file is invalid or bound to another target");
                if (LOGIN.equals(expectedLogin) && !m.group(4).matches("[0-9a-f]{64}")) throw new SafeFailure("credential file is invalid");
                return new Credential(url,dbUser,m.group(3),m.group(4),UUID.fromString(m.group(5)));
            } catch(SafeFailure e){throw e;} catch(Exception e){throw new SafeFailure("credential file is invalid");}
        }
        private boolean acceptsLogin(String login) {
            for (Account account : Account.values()) {
                if (account.login.equals(expectedLogin)) return account.acceptsLogin(login);
            }
            return expectedLogin.equals(login);
        }
        byte[] bytes(){try{assertSafeExistingTarget();return Files.readAllBytes(target);}catch(Exception e){throw new SafeFailure("credential file cannot be safely read");}}
        void restore(byte[] bytes){ Path tmp=null; try {tmp=Files.createTempFile(target.getParent(),".local-reporter-restore-",".tmp"); Files.setPosixFilePermissions(tmp,FILE_MODE); Files.write(tmp,bytes); Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); tmp=null; checkOwnerAndMode(target,FILE_MODE,false);}catch(Exception e){throw new SafeFailure("credential restoration failed");}finally{if(tmp!=null)try{Files.deleteIfExists(tmp);}catch(Exception ignored){}} }
        void deleteIfPresent(){try{if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(target))Files.deleteIfExists(target);}catch(Exception ignored){}}
    }
}
