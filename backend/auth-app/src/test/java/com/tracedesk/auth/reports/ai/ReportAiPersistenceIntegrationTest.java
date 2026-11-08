package com.tracedesk.auth.reports.ai;

import com.tracedesk.ai.gateway.*;
import com.tracedesk.auth.ai.persistence.JdbcAiLedger;
import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.reports.persistence.ReportStore;
import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.tracedesk.ai.gateway.GatewayTypes.*;
import static org.junit.jupiter.api.Assertions.*;

class ReportAiPersistenceIntegrationTest {
    @Test void realSourceQueriesAccountingAndDeliveryRevocationAreIntegrated()throws Exception{
        try(var db=IsolatedPostgres.open(System.getenv())){
            Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
            var sql=new JdbcTemplate(db);var repo=new ReportStore(sql);var ledger=new JdbcAiLedger(sql);
            UUID actor=UUID.randomUUID(),other=UUID.randomUUID(),project=UUID.randomUUID(),report=UUID.randomUUID(),draft=UUID.randomUUID();
            sql.update("insert into td_users(id,login_normalized,password_hash) values(?,'report-ai@example.test','synthetic-hash'),(?,'other-ai@example.test','synthetic-hash')",actor,other);
            sql.update("insert into td_projects(id,name) values(?,'Synthetic AI project')",project);
            sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,'REPORTER','ACTIVE'),(?,?,'REPORTER','ACTIVE')",project,actor,project,other);
            sql.update("insert into td_reports(id,project_id,reporter_user_id,title,description,environment,reproduction_steps,expected_behavior,actual_behavior,text_logs) values(?,?,?,'title','description','env','steps','expected','actual','PRIVATE-LOG-CANARY')",report,project,actor);
            Instant now=Instant.parse("2026-11-09T03:00:00Z");Clock clock=Clock.fixed(now,ZoneOffset.UTC);
            sql.update("insert into td_report_drafts(id,project_id,owner_user_id,title,description,environment,reproduction_steps,expected_behavior,actual_behavior,text_logs,created_at,updated_at,expires_at) values(?,?,?,'draft','','','','','','DRAFT-LOG-CANARY',?,?,?)",draft,project,actor,java.sql.Timestamp.from(now),java.sql.Timestamp.from(now),java.sql.Timestamp.from(now.plusSeconds(2592000)));
            AtomicInteger calls=new AtomicInteger();var policy=new Policy(true,1,100000,50000,4,1,Duration.ofSeconds(60));
            for(String scenario:List.of("success","stale","revoked","lifecycle","draftSuccess","draftSubmitted","draftExpired")){
                sql.update("update td_project_memberships set state='ACTIVE' where project_id=? and user_id=?",project,actor);
                sql.update("update td_reports set status='OPEN',version=1 where id=?",report);
                sql.update("update td_report_drafts set submitted_report_id=null,created_at=?,expires_at=?,version=1 where id=?",java.sql.Timestamp.from(now),java.sql.Timestamp.from(now.plusSeconds(2592000)),draft);
                var kind=scenario.startsWith("draft")?ReportAiService.SourceKind.DRAFT:ReportAiService.SourceKind.REPORT;UUID source=kind==ReportAiService.SourceKind.DRAFT?draft:report;
                var provider=new GatewayPorts.Provider(){
                    public Metadata metadata(){return new Metadata("synthetic-model",1,now.plusSeconds(900),1);}
                    public Completion complete(Context context){
                        calls.incrementAndGet();assertFalse(context.dataJson().contains("LOG-CANARY"));assertFalse(context.dataJson().contains("textLogs"));
                        switch(scenario){
                            case "stale" -> sql.update("update td_reports set version=2 where id=?",report);
                            case "revoked" -> sql.update("update td_project_memberships set state='DISABLED' where project_id=? and user_id=?",project,actor);
                            case "lifecycle" -> sql.update("update td_reports set status='IN_PROGRESS' where id=?",report);
                            case "draftSubmitted" -> sql.update("update td_report_drafts set submitted_report_id=? where id=?",report,draft);
                            case "draftExpired" -> sql.update("update td_report_drafts set created_at=?,expires_at=? where id=?",java.sql.Timestamp.from(now.minusSeconds(2592000)),java.sql.Timestamp.from(now),draft);
                        }
                        return new Completion(ReportAiTest.OUTPUT,new Usage(20,20));
                    }
                };
                try(var service=new ReportAiService(repo,clock,provider,ledger,policy)){
                    if(scenario.equals("success")||scenario.equals("draftSuccess")) assertEquals(source,service.suggest(kind,project,source,1,()->actor).sourceId());
                    else {
                        String expected=switch(scenario){case "stale"->"STALE_VERSION";case "revoked"->"NOT_FOUND";case "draftExpired"->"DRAFT_EXPIRED";default->"INVALID_STATE";};
                        assertEquals(expected,assertThrows(ReportService.Failure.class,()->service.suggest(kind,project,source,1,()->actor)).code());
                    }
                    int before=calls.get();
                    assertEquals("NOT_FOUND",assertThrows(ReportService.Failure.class,()->service.suggest(kind,project,source,1,()->other)).code());
                    assertEquals("NOT_FOUND",assertThrows(ReportService.Failure.class,()->service.suggest(kind,UUID.randomUUID(),source,1,()->actor)).code());
                    assertEquals(before,calls.get());
                }
            }
            // An in-flight servlet request retains its cached session after logout. Persisted deletion must still deny delivery.
            sql.update("update td_project_memberships set state='ACTIVE' where project_id=? and user_id=?",project,actor);
            sql.update("update td_reports set status='OPEN',version=1 where id=?",report);
            org.springframework.session.SessionRepository<org.springframework.session.Session> sessions=(org.springframework.session.SessionRepository) new org.springframework.session.jdbc.JdbcIndexedSessionRepository(sql,
                    new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(db)));
            var persisted=sessions.createSession();persisted.setAttribute("td.actor",actor);persisted.setAttribute("td.authAt",clock.millis());persisted.setAttribute("td.csrf","csrf");sessions.save(persisted);
            var cached=new org.springframework.mock.web.MockHttpSession(null,persisted.getId());
            cached.setAttribute("td.actor",actor);cached.setAttribute("td.authAt",clock.millis());cached.setAttribute("td.csrf","csrf");
            var logoutProvider=new GatewayPorts.Provider(){
                public Metadata metadata(){return new Metadata("synthetic-model",1,now.plusSeconds(900),1);}
                public Completion complete(Context context){calls.incrementAndGet();sessions.deleteById(persisted.getId());return new Completion(ReportAiTest.OUTPUT,new Usage(20,20));}
            };
            try(var service=new ReportAiService(repo,clock,logoutProvider,ledger,policy)){
                var events=new com.tracedesk.auth.persistence.AuthStore(sql);
                var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new ReportAiController(service,clock,events,sessions)).build();
                mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/projects/{projectId}/reports/{reportId}/suggestions",project,report)
                        .session(cached).header("X-CSRF-TOKEN","csrf").contentType("application/json").content("{\"expectedVersion\":1}"))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized())
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error.code").value("UNAUTHENTICATED"));
                assertEquals(actor,cached.getAttribute("td.actor"),"the cached servlet session still has the actor after the persisted logout");
                assertNull(sessions.findById(persisted.getId()));
                assertEquals(1,sql.queryForObject("select count(*) from td_security_events where event_code='UNAUTHENTICATED' and route_template='/api/projects/{projectId}/reports/{reportId}'",Integer.class));
            }
            assertEquals(8,calls.get());
            assertEquals(8,sql.queryForObject("select count(*) from td_ai_tickets where state='TERMINAL' and charged_units=40",Integer.class));
            assertEquals(24,sql.queryForObject("select count(*) from td_ai_accounting_events",Integer.class));
            assertEquals(0,sql.queryForObject("select count(*) from td_business_audit_events",Integer.class));
            assertEquals(0,sql.queryForObject("select count(*) from td_ai_tickets where state<>'TERMINAL'",Integer.class));
        }
    }
}
