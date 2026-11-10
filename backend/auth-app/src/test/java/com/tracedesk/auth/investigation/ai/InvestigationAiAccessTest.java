package com.tracedesk.auth.investigation.ai;

import com.tracedesk.ai.gateway.*;
import com.tracedesk.auth.ai.persistence.JdbcAiLedger;
import com.tracedesk.auth.investigation.application.InvestigationRepository.Note;
import com.tracedesk.auth.investigation.persistence.InvestigationStore;
import com.tracedesk.auth.reports.application.ReportRepository;
import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.reports.domain.ReportData;
import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static com.tracedesk.ai.gateway.GatewayPorts.*;
import static com.tracedesk.ai.gateway.GatewayTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvestigationAiAccessTest {
    static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW,ZoneOffset.UTC);
    static final String OUTPUT = "{\"hypotheses\":[],\"nextSteps\":[]}";
    static class Fixture implements AutoCloseable {
        final IsolatedPostgres db;
        final JdbcTemplate sql;
        final UUID project=UUID.randomUUID(), otherProject=UUID.randomUUID();
        final UUID developer=UUID.randomUUID(), second=UUID.randomUUID(), reporter=UUID.randomUUID(), qa=UUID.randomUUID();
        final UUID outsider=UUID.randomUUID(), foreignMember=UUID.randomUUID();
        final UUID report=UUID.randomUUID(), otherReport=UUID.randomUUID(), foreignReport=UUID.randomUUID();
        final UUID note=UUID.randomUUID(), otherNote=UUID.randomUUID();
        final AtomicInteger contextReads=new AtomicInteger(), calls=new AtomicInteger(), authCalls=new AtomicInteger(), noteReads=new AtomicInteger();
        final AtomicInteger metadataCalls=new AtomicInteger();
        final AtomicReference<UUID> live=new AtomicReference<>();
        final Ledger ledger;
        final InvestigationAiSource source;
        final Provider provider;
        Policy policy=new Policy(true,1,100000,50000,4,1,Duration.ofSeconds(60));
        Runnable beforeDispatch=() -> {}, beforeDelivery=() -> {};
        Authorizer capturedAuthorizer;
        ActorResolver capturedActors;
        Fixture() throws Exception {
            db=IsolatedPostgres.open(System.getenv());
            try {
                Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate(); sql=new JdbcTemplate(db);
                for(var id:List.of(developer,second,reporter,qa,outsider,foreignMember))
                    sql.update("insert into td_users(id,login_normalized,password_hash) values(?,?,'synthetic-hash')",id,"synthetic-"+id+"@example.test");
                sql.update("insert into td_projects(id,name) values(?,'Synthetic investigation'),(?,'Other synthetic project')",project,otherProject);
                member(developer,project,"DEVELOPER"); member(second,project,"DEVELOPER"); member(reporter,project,"REPORTER"); member(qa,project,"QA_REVIEWER");
                member(foreignMember,otherProject,"DEVELOPER");
                insertReport(report,project); insertReport(otherReport,project); insertReport(foreignReport,otherProject);
                insertNote(note,report); insertNote(otherNote,otherReport);
                live.set(developer); ledger=spy(new JdbcAiLedger(sql));
                source=new InvestigationAiSource() {
                    public Optional<ReportRepository.Membership> membership(UUID actor,UUID projectId) {
                        return new InvestigationStore(sql).membership(actor,projectId);
                    }
                    public Optional<ReportRepository.Report> report(UUID projectId,UUID reportId) {
                        return sql.query("select * from td_reports where project_id=? and id=?",rs -> rs.next() ? Optional.of(
                            new ReportRepository.Report(rs.getObject("id",UUID.class),rs.getObject("project_id",UUID.class),rs.getObject("reporter_user_id",UUID.class),
                                new ReportData(rs.getString("title"),rs.getString("description"),rs.getString("environment"),rs.getString("reproduction_steps"),rs.getString("expected_behavior"),rs.getString("actual_behavior"),rs.getString("text_logs")),
                                rs.getString("status"),rs.getObject("assigned_developer_id",UUID.class),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),rs.getInt("version"))) : Optional.empty(),projectId,reportId);
                    }
                    public Optional<Note> note(UUID projectId,UUID reportId,UUID noteId) {
                        noteReads.incrementAndGet();
                        return sql.query("select * from td_investigation_notes where project_id=? and report_id=? and id=?",rs -> rs.next() ? Optional.of(
                            new Note(rs.getObject("id",UUID.class),rs.getObject("report_id",UUID.class),rs.getObject("author_user_id",UUID.class),rs.getObject("last_edited_by_user_id",UUID.class),rs.getString("body"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),rs.getInt("version"))) : Optional.empty(),projectId,reportId,noteId);
                    }
                };
                provider=new Provider() {
                    public Metadata metadata() { metadataCalls.incrementAndGet(); beforeDispatch.run(); return new Metadata("synthetic-model",1,NOW.plusSeconds(900),1); }
                    public Completion complete(Context context) { calls.incrementAndGet(); beforeDelivery.run(); return new Completion(OUTPUT,new Usage(20,20)); }
                };
            } catch(Exception | Error failure) { db.close(); throw failure; }
        }
        void member(UUID actor,UUID projectId,String role) {
            sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,?,'ACTIVE')",projectId,actor,role);
        }
        void insertReport(UUID id,UUID projectId) {
            sql.update("insert into td_reports(id,project_id,reporter_user_id,assigned_developer_id,status,title,description,environment,reproduction_steps,expected_behavior,actual_behavior,text_logs) values(?,?,?,?,'IN_PROGRESS','title','description','env','steps','expected','actual','logs')",id,projectId,reporter,developer);
        }
        void insertNote(UUID id,UUID reportId) {
            sql.update("insert into td_investigation_notes(id,project_id,report_id,author_user_id,body,created_at,updated_at) values(?,?,?,?,'synthetic note',?,?)",id,project,reportId,developer,java.sql.Timestamp.from(NOW),java.sql.Timestamp.from(NOW));
        }
        InvestigationAiSelection selection() {
            return new InvestigationAiSelection(List.of("title"),List.of(new InvestigationAiSelection.SelectedNote(note,1)));
        }
        Request request() { return new Request(project,report,Capability.INVESTIGATION,1); }
        InvestigationAiAccess service() {
            return new InvestigationAiAccess(source,CLOCK,(actors,authorizer,contexts) -> {
                capturedAuthorizer=authorizer; capturedActors=actors;
                return new SecuredAiGateway(actors,(actor,request) -> {
                    authCalls.incrementAndGet(); authorizer.authorize(actor,request);
                },(actor,request) -> { contextReads.incrementAndGet(); return contexts.build(actor,request); },provider,ledger,policy,CLOCK);
            });
        }
        InvestigationAiOutput.Result suggest() { return suggest(report,1,selection()); }
        InvestigationAiOutput.Result suggest(UUID reportId,int version,InvestigationAiSelection selection) {
            try(var service=service()) { return service.suggest(project,reportId,version,selection,live::get); }
        }
        void denied(String code,int status,org.junit.jupiter.api.function.Executable work) {
            var failure=assertThrows(ReportService.Failure.class,work); assertEquals(code,failure.code()); assertEquals(status,failure.status());
        }
        void noAdmission() {
            assertEquals(0,contextReads.get()); assertEquals(0,calls.get()); assertEquals(0,metadataCalls.get());
            assertEquals(0,sql.queryForObject("select count(*) from td_ai_tickets",Integer.class));
            verify(ledger,never()).reserve(any(),any(),any(),any(),anyLong(),any());
        }
        Map<String,String> businessSnapshot() {
            var snapshot=new TreeMap<String,String>();
            var tables=sql.queryForList("select table_name from information_schema.tables where table_schema=current_schema() and table_type='BASE TABLE' and table_name like 'td_%' and table_name not like 'td_ai_%' and table_name <> 'td_security_events'",String.class);
            for(String table:tables) {
                if(!table.matches("td_[a-z_]+")) throw new AssertionError("Unexpected fixture table");
                snapshot.put(table,sql.queryForObject("select coalesce(jsonb_agg(to_jsonb(t) order by to_jsonb(t)::text)::text,'[]') from "+table+" t",String.class));
            }
            return snapshot;
        }
        @Override public void close() throws Exception { db.close(); }
    }
    @Test void a01_currentAssigneeAllowedAtAllThreeGatewayPoints() throws Exception {
        try(var f=new Fixture()) {
            var result=f.suggest(); assertTrue(result.hypotheses().isEmpty());
            assertEquals(3,f.authCalls.get()); assertEquals(1,f.contextReads.get()); assertEquals(1,f.calls.get());
        }
    }
    @Test void a02_reporterAndQaForbiddenEvenForMissingReport() throws Exception {
        try(var f=new Fixture()) { for(UUID actor:List.of(f.reporter,f.qa)) {
            f.live.set(actor); f.denied("FORBIDDEN",403,() -> f.suggest(UUID.randomUUID(),1,f.selection())); f.noAdmission();
        } }
    }
    @Test void a03_membershipAndEnabledStateMatrix() throws Exception {
        try(var f=new Fixture()) {
            for(UUID actor:List.of(f.outsider,f.foreignMember)) { f.live.set(actor); f.denied("NOT_FOUND",404,f::suggest); f.noAdmission(); }
            f.live.set(f.developer);
            f.sql.update("update td_users set enabled=false where id=?",f.developer);
            f.denied("NOT_FOUND",404,f::suggest); f.noAdmission(); f.sql.update("update td_users set enabled=true where id=?",f.developer);
            f.sql.update("update td_projects set enabled=false where id=?",f.project);
            f.denied("NOT_FOUND",404,f::suggest); f.noAdmission(); f.sql.update("update td_projects set enabled=true where id=?",f.project);
            f.sql.update("update td_project_memberships set state='DISABLED' where project_id=? and user_id=?",f.project,f.developer);
            f.denied("NOT_FOUND",404,f::suggest); f.noAdmission();
        }
    }
    @Test void a04_unassignedAndFormerAssigneeForbidden() throws Exception {
        try(var f=new Fixture()) {
            f.live.set(f.second); f.denied("FORBIDDEN",403,f::suggest); f.noAdmission();
            f.sql.update("update td_reports set assigned_developer_id=? where id=?",f.second,f.report);
            f.live.set(f.developer); f.denied("FORBIDDEN",403,f::suggest); f.noAdmission();
        }
    }
    @Test void a06_missingAndCrossProjectReportNotFound() throws Exception {
        try(var f=new Fixture()) { for(UUID id:List.of(UUID.randomUUID(),f.foreignReport)) {
            f.denied("NOT_FOUND",404,() -> f.suggest(id,1,f.selection())); f.noAdmission();
        } }
    }
    @Test void a07_missingAndOtherReportNoteNotFound() throws Exception {
        try(var f=new Fixture()) { for(UUID id:List.of(UUID.randomUUID(),f.otherNote)) {
            var selection=new InvestigationAiSelection(List.of("title"),List.of(new InvestigationAiSelection.SelectedNote(id,1)));
            f.denied("NOT_FOUND",404,() -> f.suggest(f.report,1,selection)); f.noAdmission();
        } }
    }
    @Test void a08_everyOtherStatusInvalid() throws Exception {
        try(var f=new Fixture()) { for(String status:List.of("OPEN","READY_FOR_QA","CLOSED")) {
            f.sql.update("update td_reports set status=? where id=?",status,f.report);
            f.denied("INVALID_STATE",409,f::suggest); f.noAdmission();
        } }
    }
    @Test void a09_staleReportAndNoteVersions() throws Exception {
        try(var f=new Fixture()) {
            f.denied("STALE_VERSION",409,() -> f.suggest(f.report,2,f.selection())); f.noAdmission();
            f.sql.update("update td_investigation_notes set version=2 where id=?",f.note);
            f.denied("STALE_VERSION",409,f::suggest); f.noAdmission();
        }
    }
    @Test void a10_liveSessionActorMustEqualResolvedActor() throws Exception {
        try(var f=new Fixture(); var service=f.service()) {
            AtomicInteger reads=new AtomicInteger();
            f.denied("UNAUTHENTICATED",401,() -> service.suggest(f.project,f.report,1,f.selection(),() -> reads.incrementAndGet()==1 ? f.developer : f.second));
            f.noAdmission();
        }
    }
    @Test void a12_authorizedAssigneeGetsUnavailableWhenDisabled() throws Exception {
        try(var f=new Fixture()) { f.policy=Policy.disabled(); f.denied("AI_UNAVAILABLE",503,f::suggest); f.noAdmission(); }
    }
    @Test void a13_initialDenialsSelectNothingAndReserveNothing() throws Exception {
        try(var f=new Fixture()) {
            f.live.set(null); f.denied("UNAUTHENTICATED",401,f::suggest); f.noAdmission();
            f.live.set(f.reporter); f.denied("FORBIDDEN",403,f::suggest); f.noAdmission();
            f.live.set(f.outsider); f.denied("NOT_FOUND",404,f::suggest); f.noAdmission();
            f.live.set(f.second); f.denied("FORBIDDEN",403,f::suggest); f.noAdmission();
            f.live.set(f.developer); f.denied("NOT_FOUND",404,() -> f.suggest(UUID.randomUUID(),1,f.selection())); f.noAdmission();
            f.denied("NOT_FOUND",404,() -> f.suggest(f.report,1,new InvestigationAiSelection(List.of("title"),List.of(new InvestigationAiSelection.SelectedNote(UUID.randomUUID(),1))))); f.noAdmission();
            f.sql.update("update td_reports set status='OPEN' where id=?",f.report); f.denied("INVALID_STATE",409,f::suggest); f.noAdmission();
            f.sql.update("update td_reports set status='IN_PROGRESS',version=2 where id=?",f.report); f.denied("STALE_VERSION",409,f::suggest); f.noAdmission();
        }
    }
    @Test void a15_changesDuringCompletionSuppressDeliveryAndSettleExactlyOnce() throws Exception {
        for(String scenario:List.of("reassign","revoke","session","status","report","noteEdit","noteDelete")) {
            try(var f=new Fixture()) {
                f.beforeDelivery=() -> {
                    switch(scenario) {
                        case "reassign" -> f.sql.update("update td_reports set assigned_developer_id=? where id=?",f.second,f.report);
                        case "revoke" -> f.sql.update("update td_project_memberships set state='DISABLED' where project_id=? and user_id=?",f.project,f.developer);
                        case "session" -> f.live.set(null);
                        case "status" -> f.sql.update("update td_reports set status='READY_FOR_QA' where id=?",f.report);
                        case "report" -> f.sql.update("update td_reports set description='changed',version=version+1 where id=?",f.report);
                        case "noteEdit" -> f.sql.update("update td_investigation_notes set body='changed',version=version+1 where id=?",f.note);
                        case "noteDelete" -> f.sql.update("delete from td_investigation_notes where id=?",f.note);
                    }
                };
                String code=switch(scenario) { case "reassign" -> "FORBIDDEN"; case "revoke","noteDelete" -> "NOT_FOUND"; case "session" -> "UNAUTHENTICATED"; case "status" -> "INVALID_STATE"; default -> "STALE_VERSION"; };
                int status=switch(code) { case "FORBIDDEN" -> 403; case "NOT_FOUND" -> 404; case "UNAUTHENTICATED" -> 401; default -> 409; };
                f.denied(code,status,f::suggest); assertEquals(1,f.calls.get(),scenario);
                verify(f.ledger,times(1)).settle(any(),eq(Outcome.SUCCESS),eq(40L),any());
                assertEquals(1,f.sql.queryForObject("select count(*) from td_ai_tickets where state='TERMINAL' and charged_units=40",Integer.class));
                assertEquals(0,f.sql.queryForObject("select count(*) from td_ai_tickets where state<>'TERMINAL'",Integer.class));
            }
        }
    }
    @Test void a17_everyOutcomeLeavesBusinessRowsAndAuditUnchanged() throws Exception {
        try(var f=new Fixture()) {
            long revision=1;
            // Each advanced policy revokes earlier AI metadata, so the fake must publish a newer revision.
            var metadataRevision=new AtomicLong();
            for(String scenario:List.of("success","denied","disabled","invalidOutput","providerFailure")) {
                var before=f.businessSnapshot(); f.live.set(scenario.equals("denied")?f.reporter:f.developer);
                boolean enabled=!scenario.equals("disabled");
                f.policy=new Policy(enabled,revision++,enabled?100000:0,enabled?50000:0,4,1,Duration.ofSeconds(60));
                f.beforeDelivery=() -> {};
                // For output and provider failures install a controlled fake at the gateway factory seam.
                try(var service=new InvestigationAiAccess(f.source,CLOCK,(actors,authorizer,contexts) -> new SecuredAiGateway(actors,authorizer,contexts,new Provider() {
                    public Metadata metadata() { return new Metadata("synthetic-model",metadataRevision.addAndGet(100),NOW.plusSeconds(900),1); }
                    public Completion complete(Context c) {
                        if(scenario.equals("providerFailure")) throw new GatewayFailure(GatewayFailure.Code.AI_UNAVAILABLE);
                        return new Completion(scenario.equals("invalidOutput")?"{\"hypotheses\":[],\"nextSteps\":[],\"action\":\"CLOSED\"}":OUTPUT,new Usage(20,20));
                    }
                },f.ledger,f.policy,CLOCK))) {
                    if(scenario.equals("success")) service.suggest(f.project,f.report,1,f.selection(),f.live::get);
                    else {
                        String code=switch(scenario) { case "denied" -> "FORBIDDEN"; case "invalidOutput" -> "AI_INVALID_OUTPUT"; default -> "AI_UNAVAILABLE"; };
                        f.denied(code,code.equals("FORBIDDEN")?403:code.equals("AI_INVALID_OUTPUT")?502:503,
                            () -> service.suggest(f.project,f.report,1,f.selection(),f.live::get));
                    }
                }
                assertEquals(before,f.businessSnapshot(),scenario);
            }
        }
    }
}
