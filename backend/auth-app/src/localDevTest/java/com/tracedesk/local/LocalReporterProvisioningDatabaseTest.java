package com.tracedesk.local;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.io.OutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

/** Runs only against the separately launched, disposable local setup database. */
class LocalReporterProvisioningDatabaseTest {
    @TempDir Path temp;

    @Test void provisionsIdempotentlyAndResetPreservesOwnedBusinessDataAndUnrelatedSessions() throws Exception {
        Map<String,String> env=new HashMap<>(System.getenv());
        assertEquals("true",env.get("TRACEDESK_LOCAL_SMOKE_CONFIRM_DISPOSABLE"),"explicit disposable database test opt-in required");
        String url=env.get("TRACEDESK_LOCAL_JDBC_URL"), dbUser=env.get("TRACEDESK_LOCAL_DATABASE_USER"), dbPassword=env.get("TRACEDESK_LOCAL_DATABASE_PASSWORD");
        assertTrue(url!=null && url.matches("jdbc:postgresql://127\\.0\\.0\\.1:55419/tracedesk_dev"),"only the owned port-55419 disposable DB is permitted");
        assertTrue("tracedesk_dev".equals(dbUser) && dbPassword!=null && !dbPassword.isBlank(),"explicit disposable DB credentials required");
        env.put("TRACEDESK_LOCAL_CONFIRM","true");
        Path dir=temp.resolve("private"); Files.createDirectory(dir); Files.setPosixFilePermissions(dir,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        Path credentialPath=dir.resolve("local-reporter.json");
        DriverManagerDataSource ds=new DriverManagerDataSource(url,dbUser,dbPassword);
        PrintStream out=System.out, err=System.err;
        try(PrintStream sink=new PrintStream(OutputStream.nullOutputStream())) { System.setOut(sink); System.setErr(sink); org.flywaydb.core.Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate(); }
        finally { System.setOut(out); System.setErr(err); }
        assertNoPreexistingTuple(ds);
        UUID project=null, user=null, report=null, draft=null, unrelatedSession=UUID.randomUUID();
        try {
            injectMarkerFailure(ds);
            assertThrows(LocalReporterSetupCommand.SafeFailure.class,()->LocalReporterSetupCommand.run(new String[]{"provision"},env,credentialPath));
            assertFalse(Files.exists(credentialPath),"failed marker transaction left a credential file");
            assertEquals(0,count(ds,"select count(*) from td_users where login_normalized='"+LocalReporterSetupCommand.LOGIN+"'"));
            removeMarkerFailure(ds);
            var first=concurrentProvision(env,credentialPath);
            project=first.projectUuid();
            var files=new LocalReporterSetupCommand.LocalReporterCredentialFile(credentialPath);
            var firstCredential=files.readAndValidate(url,dbUser);
            user=findUser(ds,project);
            String firstHash=userHash(ds,user);
            assertTrue(new Argon2PasswordEncoder(16,32,1,19456,2).matches(firstCredential.password(),firstHash));

            var repeated=LocalReporterSetupCommand.run(new String[]{"provision"},env,credentialPath);
            assertEquals(first.projectUuid(),repeated.projectUuid());
            assertTrue(firstCredential.password().equals(files.readAndValidate(url,dbUser).password()),"repeat provision replaced local credentials");
            assertTrue(firstHash.equals(userHash(ds,user)),"repeat provision changed the stored password hash");
            assertEquals(1,count(ds,"select count(*) from td_local_reporter_owner"));
            assertEquals(1,count(ds,"select count(*) from td_project_memberships where project_id='"+project+"'"));

            byte[] matchingFile=Files.readAllBytes(credentialPath);
            try(Connection c=ds.getConnection();var p=c.prepareStatement("update td_project_memberships set role='DEVELOPER' where project_id=? and user_id=?")){p.setObject(1,project);p.setObject(2,user);p.executeUpdate();}
            assertThrows(LocalReporterSetupCommand.SafeFailure.class,()->LocalReporterSetupCommand.run(new String[]{"provision"},env,credentialPath));
            assertTrue(java.util.Arrays.equals(matchingFile,Files.readAllBytes(credentialPath)),"ownership mismatch overwrote credential file");
            try(Connection c=ds.getConnection();var p=c.prepareStatement("update td_project_memberships set role='REPORTER' where project_id=? and user_id=?")){p.setObject(1,project);p.setObject(2,user);p.executeUpdate();}

            Files.delete(credentialPath);
            assertThrows(LocalReporterSetupCommand.SafeFailure.class,()->LocalReporterSetupCommand.run(new String[]{"provision"},env,credentialPath));
            assertFalse(Files.exists(credentialPath),"missing owned credential was replaced by a new password");
            assertTrue(firstHash.equals(userHash(ds,user)),"missing credential changed the owned password hash");
            Files.write(credentialPath,matchingFile);
            Files.setPosixFilePermissions(credentialPath,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            Files.setPosixFilePermissions(credentialPath,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));

            assertEquals(project,concurrentProvision(env,credentialPath).projectUuid());
            assertEquals(1,count(ds,"select count(*) from td_local_reporter_owner"));

            report=UUID.randomUUID(); draft=UUID.randomUUID(); UUID ownSession=UUID.randomUUID();
            insertReport(ds,report,project,user); insertDraft(ds,draft,project,user);
            insertAudit(ds,report,project,user);
            insertSession(ds,ownSession,user.toString()); insertSession(ds,unrelatedSession,UUID.randomUUID().toString());
            byte[] goodCredential=Files.readAllBytes(credentialPath);
            Files.writeString(credentialPath,Files.readString(credentialPath).replace(firstCredential.password(),"0".repeat(64)));
            assertThrows(LocalReporterSetupCommand.SafeFailure.class,()->LocalReporterSetupCommand.run(new String[]{"reset"},env,credentialPath));
            assertTrue(firstHash.equals(userHash(ds,user)),"failed operation changed the password hash");
            Files.write(credentialPath,goodCredential);

            try(Connection c=ds.getConnection();var st=c.createStatement()) {
                st.execute("create function td_local_test_reset_failure() returns trigger language plpgsql as $$ begin raise exception 'synthetic reset failure'; end $$");
                st.execute("create trigger td_local_test_reset_failure before update on td_users for each row execute function td_local_test_reset_failure()");
            }
            try {
                assertThrows(LocalReporterSetupCommand.SafeFailure.class,()->LocalReporterSetupCommand.run(new String[]{"reset"},env,credentialPath));
                assertTrue(java.util.Arrays.equals(goodCredential,Files.readAllBytes(credentialPath)),"failed reset did not restore credentials");
                assertTrue(firstHash.equals(userHash(ds,user)),"failed reset changed hash");
                assertEquals(1,count(ds,"select count(*) from spring_session where principal_name='"+user+"'"));
            } finally {
                try(Connection c=ds.getConnection();var st=c.createStatement()) {
                    st.execute("drop trigger td_local_test_reset_failure on td_users");
                    st.execute("drop function td_local_test_reset_failure()");
                }
            }
            var rotated=LocalReporterSetupCommand.run(new String[]{"reset"},env,credentialPath);
            assertEquals(project,rotated.projectUuid());
            var rotatedCredential=files.readAndValidate(url,dbUser);
            String rotatedHash=userHash(ds,user);
            assertTrue(!firstHash.equals(rotatedHash),"reset did not rotate the password hash");
            assertTrue(new Argon2PasswordEncoder(16,32,1,19456,2).matches(rotatedCredential.password(),rotatedHash));
            assertEquals(1,count(ds,"select count(*) from td_reports where id='"+report+"' and project_id='"+project+"' and reporter_user_id='"+user+"'"));
            assertEquals(1,count(ds,"select count(*) from td_report_drafts where id='"+draft+"' and project_id='"+project+"' and owner_user_id='"+user+"'"));
            assertEquals(1,count(ds,"select count(*) from td_business_audit_events where report_id='"+report+"'"));
            assertEquals(0,count(ds,"select count(*) from spring_session where principal_name='"+user+"'"));
            assertEquals(1,count(ds,"select count(*) from spring_session where session_id='"+unrelatedSession+"'"));
        } finally {
            if(project!=null) cleanup(ds,project,report,draft,unrelatedSession);
            Files.deleteIfExists(credentialPath);
        }
    }

    private static LocalReporterSetupCommand.Result concurrentProvision(Map<String,String> env,Path path) throws Exception {
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var gate=new java.util.concurrent.CountDownLatch(1);
            var futures=java.util.List.of(
                pool.submit(()->{gate.await();return LocalReporterSetupCommand.run(new String[]{"provision"},env,path);}),
                pool.submit(()->{gate.await();return LocalReporterSetupCommand.run(new String[]{"provision"},env,path);}));
            gate.countDown();
            LocalReporterSetupCommand.Result first=null;
            for(var future:futures) {
                try {
                    var result=future.get(30,java.util.concurrent.TimeUnit.SECONDS);
                    if(first==null) first=result; else assertEquals(first.projectUuid(),result.projectUuid());
                } catch(java.util.concurrent.ExecutionException failure) {
                    assertTrue(failure.getCause() instanceof LocalReporterSetupCommand.SafeFailure,"unexpected concurrency failure");
                }
            }
            assertNotNull(first,"all concurrent provisions failed");
            return first;
        } finally { pool.shutdownNow(); }
    }
    private static void insertAudit(DriverManagerDataSource ds,UUID report,UUID project,UUID user) throws Exception {
        try(Connection c=ds.getConnection();var p=c.prepareStatement("insert into td_business_audit_events(event_code,request_id,actor_id,project_id,report_id,object_id,new_status,new_version,outcome) values('REPORT_CREATED',?,?,?,?,?,'OPEN',1,'SUCCESS')")) {
            p.setObject(1,UUID.randomUUID());p.setObject(2,user);p.setObject(3,project);p.setObject(4,report);p.setObject(5,report);p.executeUpdate();
        }
    }

    private static void assertNoPreexistingTuple(DriverManagerDataSource ds) throws Exception {
        try(Connection c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("select to_regclass('public.td_local_reporter_owner')")) {
            if(r.next() && r.getString(1)!=null) assertEquals(0,count(ds,"select count(*) from td_local_reporter_owner"),"disposable DB already has an owned tuple; refusing to touch it");
        }
        try(Connection c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("select to_regclass('public.td_users')")) {
            if(!r.next() || r.getString(1)==null) return;
        }
        assertEquals(0,count(ds,"select count(*) from td_users where login_normalized='"+LocalReporterSetupCommand.LOGIN+"'"));
        assertEquals(0,count(ds,"select count(*) from td_projects where name='"+LocalReporterSetupCommand.PROJECT+"'"));
    }
    private static void injectMarkerFailure(DriverManagerDataSource ds)throws Exception {try(Connection c=ds.getConnection();var s=c.createStatement()){s.execute("create table if not exists td_local_reporter_owner(singleton boolean primary key check(singleton), format_version integer not null, user_id uuid not null, project_id uuid not null)");s.execute("create function td_local_setup_test_reject_marker() returns trigger language plpgsql as $$ begin raise exception 'inert test failure'; end $$");s.execute("create trigger td_local_setup_test_reject_marker before insert on td_local_reporter_owner for each row execute function td_local_setup_test_reject_marker()");}}
    private static void removeMarkerFailure(DriverManagerDataSource ds)throws Exception {try(Connection c=ds.getConnection();var s=c.createStatement()){s.execute("drop trigger td_local_setup_test_reject_marker on td_local_reporter_owner");s.execute("drop function td_local_setup_test_reject_marker()");}}
    private static UUID findUser(DriverManagerDataSource ds,UUID project)throws Exception { try(Connection c=ds.getConnection();var p=c.prepareStatement("select user_id from td_local_reporter_owner where project_id=?")){p.setObject(1,project);try(var r=p.executeQuery()){assertTrue(r.next());return r.getObject(1,UUID.class);}} }
    private static String userHash(DriverManagerDataSource ds,UUID user)throws Exception {try(Connection c=ds.getConnection();var p=c.prepareStatement("select password_hash from td_users where id=?")){p.setObject(1,user);try(var r=p.executeQuery()){assertTrue(r.next());return r.getString(1);}}}
    private static int count(DriverManagerDataSource ds,String sql)throws Exception {try(Connection c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery(sql)){assertTrue(r.next());return r.getInt(1);}}
    private static void insertReport(DriverManagerDataSource ds,UUID id,UUID project,UUID user)throws Exception {try(Connection c=ds.getConnection();var p=c.prepareStatement("insert into td_reports(id,project_id,reporter_user_id,title,description,environment,reproduction_steps,expected_behavior,actual_behavior) values(?,?,?,'title','description','environment','steps','expected','actual')")){p.setObject(1,id);p.setObject(2,project);p.setObject(3,user);p.executeUpdate();}}
    private static void insertDraft(DriverManagerDataSource ds,UUID id,UUID project,UUID user)throws Exception {Instant now=Instant.now();try(Connection c=ds.getConnection();var p=c.prepareStatement("insert into td_report_drafts(id,project_id,owner_user_id,created_at,updated_at,expires_at) values(?,?,?,?,?,?)")){p.setObject(1,id);p.setObject(2,project);p.setObject(3,user);p.setTimestamp(4,Timestamp.from(now));p.setTimestamp(5,Timestamp.from(now));p.setTimestamp(6,Timestamp.from(now.plusSeconds(30L*24*60*60)));p.executeUpdate();}}
    private static void insertSession(DriverManagerDataSource ds,UUID id,String principal)throws Exception {try(Connection c=ds.getConnection();var p=c.prepareStatement("insert into spring_session(primary_id,session_id,creation_time,last_access_time,max_inactive_interval,expiry_time,principal_name) values(?,?,?,?,?,?,?)")){long now=System.currentTimeMillis();p.setString(1,id.toString());p.setString(2,id.toString());p.setLong(3,now);p.setLong(4,now);p.setInt(5,1800);p.setLong(6,now+1800000);p.setString(7,principal);p.executeUpdate();}}
    private static void cleanup(DriverManagerDataSource ds,UUID project,UUID report,UUID draft,UUID unrelatedSession)throws Exception {
        UUID user=findUser(ds,project);
        try(Connection c=ds.getConnection()) { c.setAutoCommit(false); try {
            try(var p=c.prepareStatement("delete from spring_session where session_id=?")){p.setString(1,unrelatedSession.toString());p.executeUpdate();}
            try(var p=c.prepareStatement("delete from td_business_audit_events where project_id=? and actor_id=?")){p.setObject(1,project);p.setObject(2,user);p.executeUpdate();}
            try(var p=c.prepareStatement("delete from td_reports where project_id=? and reporter_user_id=?")){p.setObject(1,project);p.setObject(2,user);p.executeUpdate();}
            try(var p=c.prepareStatement("delete from td_report_drafts where project_id=? and owner_user_id=?")){p.setObject(1,project);p.setObject(2,user);p.executeUpdate();}
            try(var p=c.prepareStatement("delete from td_project_memberships where project_id=? and user_id=?")){p.setObject(1,project);p.setObject(2,user);p.executeUpdate();}
            try(var p=c.prepareStatement("delete from td_local_reporter_owner where singleton=true and project_id=? and user_id=?")){p.setObject(1,project);p.setObject(2,user);p.executeUpdate();}
            try(var p=c.prepareStatement("delete from td_projects where id=? and name=?")){p.setObject(1,project);p.setString(2,LocalReporterSetupCommand.PROJECT);p.executeUpdate();}
            try(var p=c.prepareStatement("delete from td_users where id=? and login_normalized=?")){p.setObject(1,user);p.setString(2,LocalReporterSetupCommand.LOGIN);p.executeUpdate();}
            c.commit();
        }catch(Exception e){c.rollback();throw e;} }
    }
}
