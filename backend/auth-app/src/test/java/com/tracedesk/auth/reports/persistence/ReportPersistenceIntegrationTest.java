package com.tracedesk.auth.reports.persistence;

import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.reports.domain.ReportData;
import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Required isolated-database coverage for report constraints, idempotency and atomic audit. */
class ReportPersistenceIntegrationTest {
    @Test void reportConstraintsIdempotencyConcurrencyAndAuditRollback() throws Exception {
        try (IsolatedPostgres db = IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
            JdbcTemplate sql = new JdbcTemplate(db);
            ReportStore reports = new ReportStore(sql);
            UUID reporter=user(sql,"rpt-reporter@example.test"), project=UUID.randomUUID();
            sql.update("insert into td_projects(id,name) values(?,?)",project,"Report Test Project");
            sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,'REPORTER','ACTIVE')",project,reporter);
            Instant now=Instant.parse("2026-11-07T01:02:03.123456Z");

            ReportData valid = new ReportData("🙂 title", "line one\nline two", "OS\tversion", "1. step\r\n2. step", "expected", "actual", "");
            UUID key=UUID.randomUUID(), request=UUID.randomUUID();
            var created=reports.create(reporter,project,key,valid,request,now);
            assertEquals(1,created.version()); assertEquals("OPEN",created.status()); assertEquals(valid,created.fields());
            assertEquals(now,created.createdAt());
            assertEquals(created.id(),reports.create(reporter,project,key,valid,UUID.randomUUID(),now.plusSeconds(1)).id());
            assertEquals(1,sql.queryForObject("select count(*) from td_business_audit_events where report_id=?",Integer.class,created.id()));
            ReportData changed = new ReportData("other", "line one\nline two", "OS\tversion", "1. step\r\n2. step", "expected", "actual", "");
            assertEquals("IDEMPOTENCY_CONFLICT",assertThrows(ReportService.Failure.class,
                    () -> reports.create(reporter,project,key,changed,request,now.plusSeconds(2))).code());

            // Expired keys are replaceable and cause one new report/audit row.
            sql.update("update td_report_idempotency set created_at=?, expires_at=? where actor_id=? and idempotency_key=?",
                    java.sql.Timestamp.from(now.minusSeconds(86401)),java.sql.Timestamp.from(now.minusSeconds(1)),reporter,key);
            var replacement=reports.create(reporter,project,key,changed,request,now);
            assertNotEquals(created.id(),replacement.id());

            // At most one committed result wins when two requests race on the same scoped key.
            UUID concurrentKey=UUID.randomUUID(); CountDownLatch ready=new CountDownLatch(2), go=new CountDownLatch(1);
            var pool=Executors.newFixedThreadPool(2);
            try {
                var one=pool.submit(()->createAfterGate(reports,reporter,project,concurrentKey,valid,request,now,ready,go));
                var two=pool.submit(()->createAfterGate(reports,reporter,project,concurrentKey,valid,request,now,ready,go));
                assertTrue(ready.await(5,TimeUnit.SECONDS)); go.countDown();
                assertEquals(one.get(10,TimeUnit.SECONDS).id(),two.get(10,TimeUnit.SECONDS).id());
            } finally { go.countDown(); pool.shutdownNow(); }

            // A create blocked behind revocation must re-read the locked membership after revocation commits.
            int beforeRevocationReports=sql.queryForObject("select count(*) from td_reports",Integer.class);
            int beforeRevocationAudits=sql.queryForObject("select count(*) from td_business_audit_events",Integer.class);
            CountDownLatch membershipLocked=new CountDownLatch(1), commitRevocation=new CountDownLatch(1);
            var racePool=Executors.newFixedThreadPool(2);
            TransactionTemplate gateTransactions=new TransactionTemplate(new DataSourceTransactionManager(db));
            try {
                var revocation=racePool.submit(()->gateTransactions.executeWithoutResult(tx->{
                    sql.queryForObject("select state from td_project_memberships where project_id=? and user_id=? for update",String.class,project,reporter);
                    membershipLocked.countDown();
                    try { if(!commitRevocation.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("revocation gate timeout"); }
                    catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                    sql.update("update td_project_memberships set state='DISABLED' where project_id=? and user_id=?",project,reporter);
                }));
                assertTrue(membershipLocked.await(5,TimeUnit.SECONDS));
                var blockedCreate=racePool.submit(()->reports.create(reporter,project,UUID.randomUUID(),valid,request,now));
                assertTrue(awaitMembershipLockWait(sql),"report create must reach and wait at its fresh membership row lock");
                commitRevocation.countDown(); revocation.get(5,TimeUnit.SECONDS);
                assertEquals("NOT_FOUND",assertThrows(java.util.concurrent.ExecutionException.class,()->blockedCreate.get(5,TimeUnit.SECONDS)).getCause() instanceof ReportService.Failure failure ? failure.code() : "unexpected failure");
                assertEquals(beforeRevocationReports,sql.queryForObject("select count(*) from td_reports",Integer.class));
                assertEquals(beforeRevocationAudits,sql.queryForObject("select count(*) from td_business_audit_events",Integer.class));
            } finally { commitRevocation.countDown();racePool.shutdownNow(); }
            sql.update("update td_project_memberships set state='ACTIVE' where project_id=? and user_id=?",project,reporter);

            // DB bounds include Unicode length/bytes, blank required text, C0/C1 controls, status, version and FKs.
            assertThrows(DataAccessException.class,()->insertRaw(sql,UUID.randomUUID(),UUID.randomUUID(),reporter,valid,"OPEN",1));
            assertThrows(DataAccessException.class,()->sql.update("insert into td_reports(id,project_id,reporter_user_id,title,description,environment,reproduction_steps,expected_behavior,actual_behavior,text_logs) values(?,?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(),project,reporter,"\u00a0\u2007","d","e","s","e","a",""));
            assertThrows(DataAccessException.class,()->insertRaw(sql,UUID.randomUUID(),project,reporter,new ReportData("title\u000b","d","e","s","e","a",""),"OPEN",1));
            assertThrows(DataAccessException.class,()->insertRaw(sql,UUID.randomUUID(),project,reporter,new ReportData("title\u0085","d","e","s","e","a",""),"OPEN",1));
            assertThrows(DataAccessException.class,()->insertRaw(sql,UUID.randomUUID(),project,reporter,valid,"INVALID",1));
            assertThrows(DataAccessException.class,()->insertRaw(sql,UUID.randomUUID(),project,reporter,valid,"OPEN",0));
            assertThrows(DataAccessException.class,()->insertRaw(sql,UUID.randomUUID(),UUID.randomUUID(),reporter,valid,"OPEN",1));
            ReportData over = new ReportData("t".repeat(161),"d","e","s","e","a","");
            assertThrows(DataAccessException.class,()->insertRaw(sql,UUID.randomUUID(),project,reporter,over,"OPEN",1));
            ReportData exactLimits = new ReportData("🙂".repeat(160),"🙂".repeat(10000),"🙂".repeat(4000),"🙂".repeat(8000),"🙂".repeat(4000),"🙂".repeat(4000),"🙂".repeat(16000));
            UUID maxId=UUID.randomUUID(); insertRaw(sql,maxId,project,reporter,exactLimits,"OPEN",1);
            assertEquals(184640,sql.queryForObject("select octet_length(title)+octet_length(description)+octet_length(environment)+octet_length(reproduction_steps)+octet_length(expected_behavior)+octet_length(actual_behavior)+octet_length(text_logs) from td_reports where id=?",Integer.class,maxId));
            ReportData tabs = new ReportData("title\tline", "d\nline", "e\rline", "s", "e", "a", "");
            assertDoesNotThrow(()->insertRaw(sql,UUID.randomUUID(),project,reporter,tabs,"OPEN",1));

            sql.execute("create function fail_report_audit() returns trigger language plpgsql as $$ begin raise exception 'synthetic audit failure'; end $$");
            sql.execute("create trigger fail_report_audit before insert on td_business_audit_events for each row execute function fail_report_audit()");
            UUID rollbackKey=UUID.randomUUID(); int beforeReports=sql.queryForObject("select count(*) from td_reports",Integer.class);
            int beforeKeys=sql.queryForObject("select count(*) from td_report_idempotency",Integer.class);
            assertThrows(DataAccessException.class,()->reports.create(reporter,project,rollbackKey,valid,request,now));
            assertEquals(beforeReports,sql.queryForObject("select count(*) from td_reports",Integer.class));
            assertEquals(beforeKeys,sql.queryForObject("select count(*) from td_report_idempotency",Integer.class));
        }
    }

    private static com.tracedesk.auth.reports.application.ReportRepository.Report createAfterGate(ReportStore store,UUID actor,UUID project,UUID key,ReportData fields,UUID request,Instant now,CountDownLatch ready,CountDownLatch go) throws Exception {
        ready.countDown(); if(!go.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("race gate timeout");
        return store.create(actor,project,key,fields,request,now);
    }
    private static void insertRaw(JdbcTemplate sql,UUID id,UUID project,UUID reporter,ReportData d,String status,int version) {
        sql.update("insert into td_reports(id,project_id,reporter_user_id,title,description,environment,reproduction_steps,expected_behavior,actual_behavior,text_logs,status,version) values(?,?,?,?,?,?,?,?,?,?,?,?)",
                id,project,reporter,d.title(),d.description(),d.environment(),d.reproductionSteps(),d.expectedBehavior(),d.actualBehavior(),d.textLogs(),status,version);
    }
    private static boolean awaitMembershipLockWait(JdbcTemplate sql) throws InterruptedException {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<deadline) {
            Integer waiting=sql.queryForObject("select count(*) from pg_stat_activity where pid<>pg_backend_pid() and wait_event_type='Lock' and query ilike '%for update of p,u,m%'",Integer.class);
            if(waiting!=null&&waiting>0) return true;
            Thread.sleep(20);
        }
        return false;
    }
    private static UUID user(JdbcTemplate sql,String login) {
        UUID id=UUID.randomUUID(); String role=login.contains("developer")?"DEVELOPER":login.contains("qa")||login.contains("review")?"QA_REVIEWER":"REPORTER"; sql.update("insert into td_users(id,login_normalized,password_hash,account_role) values(?,?,?,?)",id,login,"$argon2id$v=19$m=19456,t=2,p=1$synthetic$hash",role); return id;
    }
}
