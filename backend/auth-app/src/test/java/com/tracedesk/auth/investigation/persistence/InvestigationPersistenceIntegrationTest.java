package com.tracedesk.auth.investigation.persistence;

import com.tracedesk.auth.investigation.domain.InvestigationNoteData;
import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.reports.domain.ReportData;
import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Required isolated PostgreSQL coverage for Developer locks, caps, versions and atomic audit. */
class InvestigationPersistenceIntegrationTest {
    @Test void startNotesReassignmentConstraintsConcurrencyAndAuditRollback() throws Exception {
        try (IsolatedPostgres db = IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
            JdbcTemplate sql = new JdbcTemplate(db); InvestigationStore store = new InvestigationStore(sql);
            UUID project=UUID.randomUUID(), foreignProject=UUID.randomUUID();
            UUID reporter=user(sql,"dev-rpt@example.test"), developer=user(sql,"dev-one@example.test"),
                    successor=user(sql,"dev-two@example.test"), qa=user(sql,"dev-qa@example.test");
            sql.update("insert into td_projects(id,name) values(?,?),(?,?)",project,"Developer Test",foreignProject,"Foreign Test");
            membership(sql,project,reporter,"REPORTER"); membership(sql,project,developer,"DEVELOPER");
            membership(sql,project,successor,"DEVELOPER"); membership(sql,project,qa,"QA_REVIEWER");
            membership(sql,foreignProject,developer,"DEVELOPER");
            UUID report=UUID.randomUUID(); Instant created=Instant.parse("2026-11-08T01:00:00Z");
            insertReport(sql,report,project,reporter,developer,"OPEN",1,created);

            var started=store.start(developer,project,report,1,UUID.randomUUID(),created.plusSeconds(1));
            assertEquals("IN_PROGRESS",started.status()); assertEquals(2,started.version());
            assertEquals(1,sql.queryForObject("select count(*) from td_business_audit_events where report_id=? and event_code='REPORT_INVESTIGATION_STARTED'",Integer.class,report));
            assertEquals("INVALID_STATE",failure(() -> store.start(developer,project,report,1,UUID.randomUUID(),created.plusSeconds(2))).code());

            UUID absentKey=UUID.randomUUID();
            assertEquals("NOT_FOUND",failure(() -> store.createNote(developer,project,UUID.randomUUID(),absentKey,
                    new InvestigationNoteData("missing report"),UUID.randomUUID(),created.plusSeconds(2))).code());
            assertEquals(0,sql.queryForObject("select count(*) from td_investigation_note_idempotency where idempotency_key=?",Integer.class,absentKey));
            UUID foreignReport=UUID.randomUUID();
            insertReport(sql,foreignReport,foreignProject,reporter,developer,"IN_PROGRESS",1,created);
            UUID foreignKey=UUID.randomUUID();
            assertEquals("NOT_FOUND",failure(() -> store.createNote(developer,project,foreignReport,foreignKey,
                    new InvestigationNoteData("foreign report"),UUID.randomUUID(),created.plusSeconds(2))).code());
            assertEquals(0,sql.queryForObject("select count(*) from td_investigation_note_idempotency where idempotency_key=?",Integer.class,foreignKey));

            // A concurrent reuse on another report is serialized by the scoped key before body validation.
            UUID raceReportA=UUID.randomUUID(),raceReportB=UUID.randomUUID(),raceKey=UUID.randomUUID();
            insertReport(sql,raceReportA,project,reporter,developer,"IN_PROGRESS",1,created);
            insertReport(sql,raceReportB,project,reporter,developer,"IN_PROGRESS",1,created);
            sql.execute("create table td_note_key_gate(report_id uuid primary key)");
            sql.update("insert into td_note_key_gate(report_id) values(?)",raceReportA);
            sql.execute("""
                    create function slow_gated_note_key() returns trigger language plpgsql as $$
                    begin
                      if exists(select 1 from td_note_key_gate where report_id=new.report_id) then perform pg_sleep(1); end if;
                      return new;
                    end $$
                    """);
            sql.execute("create trigger slow_gated_note_key before insert on td_investigation_note_idempotency for each row execute function slow_gated_note_key()");
            var keyPool=Executors.newFixedThreadPool(2);
            try {
                var winner=keyPool.submit(() -> store.createNote(developer,project,raceReportA,raceKey,
                        new InvestigationNoteData("winning body"),UUID.randomUUID(),created.plusSeconds(2)));
                assertTrue(awaitAdvisoryLock(sql),"first request must hold the scoped key while its insert is gated");
                var conflict=keyPool.submit(() -> store.createNote(developer,project,raceReportB,raceKey,
                        new InvestigationNoteData(" "),UUID.randomUUID(),created.plusSeconds(2)));
                assertNotNull(winner.get(5,TimeUnit.SECONDS));
                var cause=assertThrows(java.util.concurrent.ExecutionException.class,() -> conflict.get(5,TimeUnit.SECONDS)).getCause();
                assertTrue(cause instanceof ReportService.Failure); assertEquals("IDEMPOTENCY_CONFLICT",((ReportService.Failure)cause).code());
            } finally {
                keyPool.shutdownNow();
                sql.execute("drop trigger slow_gated_note_key on td_investigation_note_idempotency");
                sql.execute("drop function slow_gated_note_key()");
                sql.execute("drop table td_note_key_gate");
            }

            Instant reportUpdated=sql.queryForObject("select updated_at from td_reports where id=?",java.sql.Timestamp.class,report).toInstant();
            UUID key=UUID.randomUUID(); InvestigationNoteData body=new InvestigationNoteData("private note 🙂");
            var note=store.createNote(developer,project,report,key,body,UUID.randomUUID(),created.plusSeconds(3));
            assertEquals(1,note.version()); assertEquals(developer,note.authorUserId()); assertNull(note.lastEditedByUserId());
            assertEquals(note.id(),store.createNote(developer,project,report,key,body,UUID.randomUUID(),created.plusSeconds(4)).id());
            assertEquals("IDEMPOTENCY_CONFLICT",failure(() -> store.createNote(developer,project,report,key,
                    new InvestigationNoteData("changed"),UUID.randomUUID(),created.plusSeconds(5))).code());
            assertEquals(1,sql.queryForObject("select count(*) from td_business_audit_events where object_id=? and event_code='INVESTIGATION_NOTE_CREATED'",Integer.class,note.id()));
            assertEquals(2,sql.queryForObject("select version from td_reports where id=?",Integer.class,report));
            assertEquals(reportUpdated,sql.queryForObject("select updated_at from td_reports where id=?",java.sql.Timestamp.class,report).toInstant(),
                    "private note activity must not change the shared report ordering/version signal");

            assertEquals("FORBIDDEN",failure(() -> store.notes(successor,project,report,25,null,null)).code());
            assertEquals("FORBIDDEN",failure(() -> store.createNote(successor,project,report,UUID.randomUUID(),
                    new InvestigationNoteData(" "),UUID.randomUUID(),created.plusSeconds(5))).code(),
                    "assignment authorization must precede note-content validation");
            sql.update("update td_reports set assigned_developer_id=? where id=?",successor,report);
            assertEquals("FORBIDDEN",failure(() -> store.notes(developer,project,report,25,null,null)).code());
            var inherited=store.notes(successor,project,report,25,null,null);
            assertEquals(1,inherited.items().size()); assertEquals("private note 🙂",inherited.items().getFirst().body());
            var edited=store.updateNote(successor,project,report,note.id(),1,new InvestigationNoteData("successor edit"),
                    UUID.randomUUID(),created.plusSeconds(6));
            assertEquals(developer,edited.authorUserId()); assertEquals(successor,edited.lastEditedByUserId()); assertEquals(2,edited.version());
            assertEquals("STALE_VERSION",failure(() -> store.updateNote(successor,project,report,note.id(),1,
                    new InvestigationNoteData("stale"),UUID.randomUUID(),created.plusSeconds(7))).code());
            assertEquals("FORBIDDEN",failure(() -> store.updateNote(developer,project,report,UUID.randomUUID(),1,
                    new InvestigationNoteData("hidden"),UUID.randomUUID(),created.plusSeconds(7))).code(),
                    "former assignee is denied before note lookup");

            // Database constraints independently protect project/report consistency and note text bounds.
            assertThrows(DataAccessException.class,() -> rawNote(sql,UUID.randomUUID(),foreignProject,report,successor,"mismatch"));
            assertThrows(DataAccessException.class,() -> rawNote(sql,UUID.randomUUID(),project,report,successor," \t\n"));
            assertThrows(DataAccessException.class,() -> rawNote(sql,UUID.randomUUID(),project,report,successor,"x".repeat(8_001)));
            assertThrows(DataAccessException.class,() -> rawNote(sql,UUID.randomUUID(),project,report,successor,"note\u000b"));
            assertThrows(DataAccessException.class,() -> rawNote(sql,UUID.randomUUID(),project,report,successor,"note\u0085"));
            assertDoesNotThrow(() -> rawNote(sql,UUID.randomUUID(),project,report,successor,"tab\tline\ncarriage\rreturn"));

            // At 199 rows, synchronized creates serialize on the report and exactly one reaches the cap of 200.
            sql.update("delete from td_investigation_note_idempotency where report_id=?",report);
            sql.update("delete from td_investigation_notes where report_id=?",report);
            for (int i=0;i<199;i++) rawNote(sql,UUID.randomUUID(),project,report,successor,"seed "+i);
            CountDownLatch ready=new CountDownLatch(2), go=new CountDownLatch(1); var pool=Executors.newFixedThreadPool(2);
            try {
                var one=pool.submit(() -> createAfterGate(store,successor,project,report,ready,go,created.plusSeconds(8)));
                var two=pool.submit(() -> createAfterGate(store,successor,project,report,ready,go,created.plusSeconds(8)));
                assertTrue(ready.await(5,TimeUnit.SECONDS)); go.countDown();
                int successes=0, limits=0;
                for (var future : java.util.List.of(one,two)) {
                    try { future.get(10,TimeUnit.SECONDS); successes++; }
                    catch (java.util.concurrent.ExecutionException failure) {
                        if (failure.getCause() instanceof ReportService.Failure f && "NOTE_LIMIT".equals(f.code())) limits++;
                        else throw failure;
                    }
                }
                assertEquals(1,successes); assertEquals(1,limits);
                assertEquals(200,sql.queryForObject("select count(*) from td_investigation_notes where report_id=?",Integer.class,report));
            } finally { go.countDown(); pool.shutdownNow(); }

            // Same-key concurrent retry must replay the winner even when it creates note 200.
            sql.update("delete from td_investigation_note_idempotency where report_id=?",report);
            sql.update("delete from td_investigation_notes where report_id=?",report);
            for (int i=0;i<199;i++) rawNote(sql,UUID.randomUUID(),project,report,successor,"replay seed "+i);
            UUID replayKey=UUID.randomUUID(); CountDownLatch replayReady=new CountDownLatch(2), replayGo=new CountDownLatch(1);
            var replayPool=Executors.newFixedThreadPool(2);
            try {
                var one=replayPool.submit(() -> createAfterGate(store,successor,project,report,replayKey,replayReady,replayGo,created.plusSeconds(8)));
                var two=replayPool.submit(() -> createAfterGate(store,successor,project,report,replayKey,replayReady,replayGo,created.plusSeconds(8)));
                assertTrue(replayReady.await(5,TimeUnit.SECONDS)); replayGo.countDown();
                var first=one.get(10,TimeUnit.SECONDS); var second=two.get(10,TimeUnit.SECONDS);
                assertEquals(first.id(),second.id());
                assertEquals(200,sql.queryForObject("select count(*) from td_investigation_notes where report_id=?",Integer.class,report));
                assertEquals(1,sql.queryForObject("select count(*) from td_business_audit_events where object_id=? and event_code='INVESTIGATION_NOTE_CREATED'",Integer.class,first.id()));
            } finally { replayGo.countDown(); replayPool.shutdownNow(); }

            // Membership revocation holding the conflicting lock wins before a blocked note mutation.
            sql.update("delete from td_investigation_note_idempotency where report_id=?",report);
            sql.update("delete from td_investigation_notes where report_id=?",report);
            CountDownLatch membershipLocked=new CountDownLatch(1), commitRevocation=new CountDownLatch(1);
            var racePool=Executors.newFixedThreadPool(2); TransactionTemplate tx=new TransactionTemplate(new DataSourceTransactionManager(db));
            try {
                var revoke=racePool.submit(() -> tx.executeWithoutResult(status -> {
                    sql.queryForObject("select state from td_project_memberships where project_id=? and user_id=? for update",String.class,project,successor);
                    membershipLocked.countDown(); await(commitRevocation);
                    sql.update("update td_project_memberships set state='DISABLED' where project_id=? and user_id=?",project,successor);
                }));
                assertTrue(membershipLocked.await(5,TimeUnit.SECONDS));
                var blocked=racePool.submit(() -> store.createNote(successor,project,report,UUID.randomUUID(),
                        new InvestigationNoteData("must not commit"),UUID.randomUUID(),created.plusSeconds(9)));
                assertTrue(awaitLock(sql,"for share of p,u,m")); commitRevocation.countDown(); revoke.get(5,TimeUnit.SECONDS);
                var cause=assertThrows(java.util.concurrent.ExecutionException.class,() -> blocked.get(5,TimeUnit.SECONDS)).getCause();
                assertTrue(cause instanceof ReportService.Failure); assertEquals("NOT_FOUND",((ReportService.Failure)cause).code());
                assertEquals(0,sql.queryForObject("select count(*) from td_investigation_notes where report_id=?",Integer.class,report));
            } finally { commitRevocation.countDown(); racePool.shutdownNow(); }
            sql.update("update td_project_memberships set state='ACTIVE' where project_id=? and user_id=?",project,successor);

            // Audit failure rolls the note and idempotency record back together.
            sql.execute("create function fail_developer_audit() returns trigger language plpgsql as $$ begin raise exception 'synthetic audit failure'; end $$");
            sql.execute("create trigger fail_developer_audit before insert on td_business_audit_events for each row execute function fail_developer_audit()");
            int notesBefore=sql.queryForObject("select count(*) from td_investigation_notes",Integer.class);
            int keysBefore=sql.queryForObject("select count(*) from td_investigation_note_idempotency",Integer.class);
            assertThrows(DataAccessException.class,() -> store.createNote(successor,project,report,UUID.randomUUID(),
                    new InvestigationNoteData("rollback canary"),UUID.randomUUID(),created.plusSeconds(10)));
            assertEquals(notesBefore,sql.queryForObject("select count(*) from td_investigation_notes",Integer.class));
            assertEquals(keysBefore,sql.queryForObject("select count(*) from td_investigation_note_idempotency",Integer.class));
        }
    }

    private static com.tracedesk.auth.investigation.application.InvestigationRepository.Note createAfterGate(
            InvestigationStore store,UUID actor,UUID project,UUID report,CountDownLatch ready,CountDownLatch go,Instant now) throws Exception {
        return createAfterGate(store,actor,project,report,UUID.randomUUID(),ready,go,now);
    }
    private static com.tracedesk.auth.investigation.application.InvestigationRepository.Note createAfterGate(
            InvestigationStore store,UUID actor,UUID project,UUID report,UUID key,CountDownLatch ready,CountDownLatch go,Instant now) throws Exception {
        ready.countDown(); if(!go.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("gate timeout");
        return store.createNote(actor,project,report,key,new InvestigationNoteData("concurrent"),UUID.randomUUID(),now);
    }
    private static ReportService.Failure failure(org.junit.jupiter.api.function.Executable executable) {
        return assertThrows(ReportService.Failure.class,executable);
    }
    private static void await(CountDownLatch latch) {
        try { if(!latch.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("gate timeout"); }
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
    }
    private static boolean awaitLock(JdbcTemplate sql,String fragment) throws InterruptedException {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<deadline) {
            Integer waiting=sql.queryForObject("select count(*) from pg_stat_activity where pid<>pg_backend_pid() and wait_event_type='Lock' and lower(query) like ?",Integer.class,"%"+fragment.toLowerCase()+"%");
            if(waiting!=null&&waiting>0) return true; Thread.sleep(20);
        }
        return false;
    }
    private static boolean awaitAdvisoryLock(JdbcTemplate sql) throws InterruptedException {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<deadline) {
            Integer held=sql.queryForObject("select count(*) from pg_locks where locktype='advisory' and granted and pid<>pg_backend_pid()",Integer.class);
            if(held!=null&&held>0) return true; Thread.sleep(20);
        }
        return false;
    }
    private static UUID user(JdbcTemplate sql,String login) {
        UUID id=UUID.randomUUID(); sql.update("insert into td_users(id,login_normalized,password_hash) values(?,?,?)",id,login,"$argon2id$v=19$m=19456,t=2,p=1$synthetic$hash"); return id;
    }
    private static void membership(JdbcTemplate sql,UUID project,UUID user,String role) {
        sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,?,'ACTIVE')",project,user,role);
    }
    private static void insertReport(JdbcTemplate sql,UUID id,UUID project,UUID reporter,UUID developer,String status,int version,Instant now) {
        ReportData data=new ReportData("title","description","environment","steps","expected","actual","");
        sql.update("""
                insert into td_reports(id,project_id,reporter_user_id,title,description,environment,reproduction_steps,
                  expected_behavior,actual_behavior,text_logs,status,assigned_developer_id,created_at,updated_at,version)
                values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,id,project,reporter,data.title(),data.description(),data.environment(),data.reproductionSteps(),data.expectedBehavior(),
                data.actualBehavior(),data.textLogs(),status,developer,java.sql.Timestamp.from(now),java.sql.Timestamp.from(now),version);
    }
    private static void rawNote(JdbcTemplate sql,UUID id,UUID project,UUID report,UUID author,String body) {
        sql.update("""
                insert into td_investigation_notes(id,project_id,report_id,author_user_id,body,created_at,updated_at,version)
                values(?,?,?,?,?,now(),now(),1)
                """,id,project,report,author,body);
    }
}
