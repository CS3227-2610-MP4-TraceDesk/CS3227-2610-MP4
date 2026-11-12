package com.tracedesk.auth.reports.persistence;

import com.tracedesk.auth.investigation.domain.InvestigationNoteData;
import com.tracedesk.auth.investigation.domain.ProposedResolutionData;
import com.tracedesk.auth.investigation.persistence.InvestigationStore;
import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.reports.application.ReportRepository;
import com.tracedesk.auth.reports.domain.ReportCursorSigner;
import com.tracedesk.auth.reports.domain.ReportActivityCursor;
import com.tracedesk.auth.reports.domain.ReportData;
import com.tracedesk.testing.IsolatedPostgres;
import com.tracedesk.auth.verification.VerificationCommand;
import com.tracedesk.auth.verification.VerificationStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ReportActivityPersistenceIntegrationTest {
    @Test void upgradesExistingQaSchemaAndReadsOnlyPublicQaMetadata() throws Exception {
        try (IsolatedPostgres db=IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration").schemas(db.schema()).defaultSchema(db.schema()).target("15").load().migrate();
            JdbcTemplate sql=new JdbcTemplate(db); ReportStore reports=new ReportStore(sql); InvestigationStore investigations=new InvestigationStore(sql);
            UUID owner=user(sql,"qa-history-owner@example.test","REPORTER"),developer=user(sql,"qa-history-dev@example.test","DEVELOPER"),qa=user(sql,"qa-history-reviewer@example.test","QA_REVIEWER"),project=UUID.randomUUID();
            sql.update("insert into td_projects(id,name) values(?,?)",project,"QA History Upgrade");
            for (UUID member:List.of(owner,developer,qa)) sql.update("insert into td_project_memberships(project_id,user_id,role,state) select ?,id,account_role,'ACTIVE' from td_users where id=?",project,member);
            Instant base=Instant.parse("2026-11-12T00:00:00Z");
            var report=reports.create(owner,project,UUID.randomUUID(),new ReportData("QA history","Description","Browser","Steps","Expected","Actual",""),UUID.randomUUID(),base);
            reports.changeAssignment(qa,project,report.id(),developer,"",1,UUID.randomUUID(),base.plusSeconds(1));
            investigations.start(developer,project,report.id(),2,UUID.randomUUID(),base.plusSeconds(2));
            var resolution=new ProposedResolutionData("private-root-canary","private-change-canary","private-verification-canary","");
            investigations.propose(developer,project,report.id(),resolution,3,UUID.randomUUID(),base.plusSeconds(3));
            new VerificationStore(sql,Clock.fixed(base.plusSeconds(4),ZoneOffset.UTC)).write(qa,project,report.id(),new VerificationCommand(4,List.of(),"private-QA-note-canary",VerificationCommand.Decision.SAVE,""));
            new VerificationStore(sql,Clock.fixed(base.plusSeconds(5),ZoneOffset.UTC)).write(qa,project,report.id(),new VerificationCommand(5,List.of(),"private-QA-note-canary",VerificationCommand.Decision.SEND_BACK,"private-send-back-canary"));
            investigations.propose(developer,project,report.id(),resolution,6,UUID.randomUUID(),base.plusSeconds(6));
            var checks=List.of(new VerificationCommand.Check("private-check-canary",VerificationCommand.Result.Pass,"private-check-note-canary"));
            new VerificationStore(sql,Clock.fixed(base.plusSeconds(7),ZoneOffset.UTC)).write(qa,project,report.id(),new VerificationCommand(7,checks,"private-QA-note-canary",VerificationCommand.Decision.CLOSE,""));
            var upgrade=Flyway.configure().dataSource(db).locations("classpath:db/migration").schemas(db.schema()).defaultSchema(db.schema()).load().migrate();
            assertEquals(1,upgrade.migrationsExecuted,"only forward migration V16 should apply after V15");
            var service=new ReportService(reports,new ReportCursorSigner(),Clock.fixed(base.plusSeconds(10),ZoneOffset.UTC));
            var history=service.activity(owner,project,report.id(),100,null).items();
            assertEquals(List.of("REPORT_CREATED","REPORT_INVESTIGATION_STARTED","REPORT_RESOLUTION_PROPOSED","QA_VERIFICATION_SAVED","QA_VERIFICATION_SENT_BACK","REPORT_RESOLUTION_PROPOSED","QA_VERIFICATION_CLOSED"),history.stream().map(ReportRepository.Activity::eventCode).toList());
            assertEquals("CLOSED",history.getLast().newStatus());
            assertFalse(history.toString().contains("private-"),"no private verification, resolution or send-back text is selected");
            assertEquals("CLOSED",service.detail(owner,project,report.id()).status());
        }
    }
    @Test void activityUsesRealDeveloperTransitionsKeysetAndOwnerAuthorization() throws Exception {
        try(IsolatedPostgres db=IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration").schemas(db.schema()).defaultSchema(db.schema()).load().migrate();
            JdbcTemplate sql=new JdbcTemplate(db); ReportStore reports=new ReportStore(sql); InvestigationStore investigation=new InvestigationStore(sql);
            UUID owner=user(sql,"activity-owner@example.test","REPORTER"),developer=user(sql,"activity-developer@example.test","DEVELOPER");
            UUID other=user(sql,"activity-other@example.test","REPORTER"),project=UUID.randomUUID();
            sql.update("insert into td_projects(id,name) values(?,?)",project,"Activity Test Project");
            for(UUID member:List.of(owner,developer,other)) sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,'REPORTER','ACTIVE')",project,member);
            sql.update("update td_users set account_role='DEVELOPER' where id=?",developer);
            Instant base=Instant.parse("2026-11-12T01:00:00Z"); ReportData fields=new ReportData("Title","Description","Environment","Steps","Expected","Actual","");
            var report=reports.create(owner,project,UUID.randomUUID(),fields,UUID.randomUUID(),base);
            fields=new ReportData("Edited title",fields.description(),fields.environment(),fields.reproductionSteps(),fields.expectedBehavior(),fields.actualBehavior(),fields.textLogs());
            report=reports.updateReport(owner,project,report.id(),1,fields,UUID.randomUUID(),base.plusSeconds(1));
            sql.update("update td_reports set assigned_developer_id=? where id=?",developer,report.id());
            investigation.start(developer,project,report.id(),2,UUID.randomUUID(),base.plusSeconds(2));
            String privateCanary="private-note-activity-canary";
            investigation.createNote(developer,project,report.id(),UUID.randomUUID(),new InvestigationNoteData(privateCanary),UUID.randomUUID(),base.plusSeconds(3));
            investigation.propose(developer,project,report.id(),new ProposedResolutionData("secret root cause","secret change","secret verification",""),3,UUID.randomUUID(),base.plusSeconds(4));
            UUID reportId=report.id();
            Instant sameTime=Instant.parse("2026-11-12T02:00:00.123456Z");
            for(int i=0;i<105;i++) sql.update("""
                    insert into td_business_audit_events(event_code,request_id,actor_id,project_id,report_id,object_type,object_id,
                      prior_status,new_status,prior_version,new_version,outcome)
                    values('REPORT_EDITED',?,?,?,?, 'REPORT',?,null,null,1,2,'SUCCESS')
                    """,UUID.randomUUID(),owner,project,reportId,reportId);
            sql.update("update td_business_audit_events set occurred_at=? where report_id=?",java.sql.Timestamp.from(sameTime),reportId);
            assertEquals(110,sql.queryForObject("select count(*) from td_business_audit_events where report_id=? and occurred_at=?",Integer.class,reportId,java.sql.Timestamp.from(sameTime)));

            var activityCursor=new ReportActivityCursor();
            var service=new ReportService(reports,new ReportCursorSigner(),activityCursor,Clock.fixed(base.plusSeconds(600),ZoneOffset.UTC));
            int auditCount=sql.queryForObject("select count(*) from td_business_audit_events where report_id=?",Integer.class,reportId);
            assertEquals(110,auditCount,"five business events plus 105 additional approved report edits and one private note");
            assertEquals(25,service.activity(owner,project,reportId,null,null).items().size(),"default limit is 25");
            assertEquals(100,service.activity(owner,project,reportId,100,null).items().size(),"maximum supported limit is 100");
            assertEquals("INVALID_REQUEST",assertThrows(ReportService.Failure.class,()->service.activity(owner,project,reportId,101,null)).code());
            List<ReportService.ActivityResult> pages=new ArrayList<>(); String cursor=null;
            do { var page=service.activity(owner,project,reportId,100,cursor); pages.add(page); cursor=page.nextCursor(); } while(cursor!=null);
            List<ReportRepository.Activity> items=pages.stream().flatMap(p->p.items().stream()).toList();
            List<String> expectedIds=sql.queryForList("select id from td_business_audit_events where project_id=? and report_id=? and outcome='SUCCESS' and event_code in ('REPORT_CREATED','REPORT_EDITED','DRAFT_SUBMITTED','REPORT_INVESTIGATION_STARTED','REPORT_RESOLUTION_PROPOSED') order by occurred_at asc,id asc",Long.class,project,reportId).stream().map(String::valueOf).toList();
            assertEquals(109,items.size()); assertEquals(109,items.stream().map(ReportRepository.Activity::id).distinct().count());
            assertEquals(expectedIds,items.stream().map(ReportRepository.Activity::id).toList(),"all pages match the complete numeric bigint key order without gaps or duplicates");
            assertEquals(2,pages.size()); assertEquals(100,pages.get(0).items().size()); assertEquals(9,pages.get(1).items().size());
            var boundary=activityCursor.verify(pages.get(0).nextCursor(),owner,project,reportId,base.plusSeconds(600));
            assertEquals(Long.parseLong(pages.get(0).items().get(99).id()),boundary.eventId()); assertEquals(sameTime,boundary.occurredAt());
            assertEquals("REPORT_CREATED",items.get(0).eventCode()); assertEquals("REPORT_EDITED",items.get(1).eventCode());
            assertEquals("REPORT_INVESTIGATION_STARTED",items.get(2).eventCode());
            assertEquals("REPORT_RESOLUTION_PROPOSED",items.get(3).eventCode());
            assertTrue(items.subList(4,items.size()).stream().allMatch(x->x.eventCode().equals("REPORT_EDITED")));
            assertEquals(items.stream().map(x->Long.parseLong(x.id())).sorted().toList(),items.stream().map(x->Long.parseLong(x.id())).toList());
            assertTrue(items.stream().allMatch(x->x.occurredAt().equals(sameTime)));
            assertEquals("OPEN",items.get(0).newStatus()); assertNull(items.get(1).priorStatus()); assertNull(items.get(1).newStatus());
            assertEquals("IN_PROGRESS",items.get(2).newStatus()); assertEquals("READY_FOR_QA",items.get(3).newStatus());
            assertEquals(auditCount,sql.queryForObject("select count(*) from td_business_audit_events where report_id=?",Integer.class,reportId),"read must not write business audit rows");
            assertEquals(109,auditCount-1,"private note is present in storage but never returned");
            assertThrows(ReportService.Failure.class,()->service.activity(other,project,reportId,25,null));
            assertEquals("FORBIDDEN",assertThrows(ReportService.Failure.class,()->service.activity(developer,project,reportId,25,null)).code());
            CountDownLatch selected=new CountDownLatch(1),release=new CountDownLatch(1),revocationStarted=new CountDownLatch(1),revocationFinished=new CountDownLatch(1);
            AtomicBoolean pauseOnce=new AtomicBoolean(true);
            JdbcTemplate pausingSql=new JdbcTemplate(db) {
                @Override public <T> List<T> query(String statement,RowMapper<T> mapper,Object... args) {
                    List<T> result=super.query(statement,mapper,args);
                    if(statement.contains("select id::text as id")&&pauseOnce.compareAndSet(true,false)) {
                        selected.countDown();
                        try { if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("activity delivery gate timeout"); }
                        catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                    }
                    return result;
                }
            };
            var gatedService=new ReportService(new ReportStore(pausingSql),new ReportCursorSigner(),new ReportActivityCursor(),Clock.fixed(base.plusSeconds(600),ZoneOffset.UTC));
            var pool=Executors.newFixedThreadPool(2);
            try {
                var pageFuture=pool.submit(()->gatedService.activity(owner,project,reportId,25,null));
                assertTrue(selected.await(5,TimeUnit.SECONDS),"the report-scoped page must reach its selected result set");
                var revokeFuture=pool.submit(()->{ revocationStarted.countDown(); sql.update("update td_project_memberships set state='DISABLED' where project_id=? and user_id=?",project,owner); revocationFinished.countDown(); });
                assertTrue(revocationStarted.await(5,TimeUnit.SECONDS));
                assertFalse(revocationFinished.await(150,TimeUnit.MILLISECONDS),"revocation waits while the authorized page is being rechecked and delivered");
                release.countDown();
                assertEquals(25,pageFuture.get(5,TimeUnit.SECONDS).items().size());
                revokeFuture.get(5,TimeUnit.SECONDS);
                assertEquals("NOT_FOUND",assertThrows(ReportService.Failure.class,()->service.activity(owner,project,reportId,25,null)).code());
            } finally { release.countDown(); pool.shutdownNow(); }
        }
    }
    private static UUID user(JdbcTemplate sql,String login,String role) {
        UUID id=UUID.randomUUID(); sql.update("insert into td_users(id,login_normalized,password_hash,account_role) values(?,?,?,?)",id,login,"$argon2id$v=19$m=19456,t=2,p=1$synthetic$hash",role); return id;
    }
}
