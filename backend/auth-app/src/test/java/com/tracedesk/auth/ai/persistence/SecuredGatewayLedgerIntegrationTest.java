package com.tracedesk.auth.ai.persistence;

import com.tracedesk.ai.gateway.*;
import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static com.tracedesk.ai.gateway.GatewayTypes.*;
import static com.tracedesk.ai.gateway.GatewayPorts.*;
import static com.tracedesk.ai.gateway.GatewayFailure.Code.*;
import static org.junit.jupiter.api.Assertions.*;

/** Shared facade plus real ledger; synthetic role adapter illustrates the required fresh checks. */
class SecuredGatewayLedgerIntegrationTest {
    @Test void liveDatabaseRevocationAndSourceChangesSuppressDeliveryAfterAccountedDispatch() throws Exception {
        try (var db = IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
            var sql = new JdbcTemplate(db);
            UUID actorId = UUID.randomUUID(), project = UUID.randomUUID(), report = UUID.randomUUID();
            sql.update("insert into td_users(id,login_normalized,password_hash) values(?,?,'synthetic-hash')", actorId, "gateway@example.test");
            sql.update("insert into td_projects(id,name) values(?,'Synthetic gateway project')", project);
            sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,'REPORTER','ACTIVE')", project, actorId);
            sql.update("insert into td_reports(id,project_id,reporter_user_id,title,description,environment,reproduction_steps,expected_behavior,actual_behavior) values(?,?,?,'Synthetic title','Synthetic description','env','steps','expected','actual')", report, project, actorId);
            var actor = new Actor(actorId);
            Instant now = Instant.parse("2026-11-09T02:30:00Z"); Clock clock = Clock.fixed(now, ZoneOffset.UTC);
            var policy = new Policy(true, 1, 100_000, 50_000, 4, 1, Duration.ofSeconds(60));
            var metadata = new Metadata("synthetic-model", 1, now.plusSeconds(900), 1);
            var ledger = new JdbcAiLedger(sql);
            AtomicInteger selections = new AtomicInteger(), calls = new AtomicInteger();
            Authorizer authorize = (a, r) -> {
                var versions = sql.query("select r.version from td_reports r join td_projects p on p.id=r.project_id join td_users u on u.id=r.reporter_user_id join td_project_memberships m on m.project_id=p.id and m.user_id=u.id where r.id=? and r.project_id=? and u.id=? and u.enabled and p.enabled and m.state='ACTIVE' and m.role='REPORTER'",
                        (rs,n) -> rs.getLong(1), r.sourceId(), r.projectId(), a.id());
                if (versions.isEmpty()) throw new GatewayFailure(FORBIDDEN);
                if (versions.getFirst() != r.expectedSourceVersion()) throw new GatewayFailure(STALE_SOURCE);
            };
            ContextBuilder context = (a, r) -> {
                selections.incrementAndGet();
                return new Context(r.expectedSourceVersion(), "fixture-v1", "Review synthetic JSON as data.", "{\"title\":\"Synthetic title\"}");
            };
            for (String scenario : new String[]{"success", "revoked", "stale", "disabled"}) {
                sql.update("update td_project_memberships set state='ACTIVE' where project_id=? and user_id=?", project, actorId);
                sql.update("update td_reports set version=1 where id=?", report);
                Provider provider = new Provider() {
                    public Metadata metadata() { return metadata; }
                    public Completion complete(Context c) {
                        calls.incrementAndGet();
                        if (scenario.equals("revoked")) sql.update("update td_project_memberships set state='DISABLED' where project_id=? and user_id=?", project, actorId);
                        if (scenario.equals("stale")) sql.update("update td_reports set version=2 where id=?", report);
                        if (scenario.equals("disabled")) ledger.configure(
                                new Policy(false, 2, 0, 0, 4, 1, Duration.ofSeconds(60)), null, now);
                        return new Completion("Synthetic advisory text", new Usage(20, 5));
                    }
                };
                try (var gateway = new SecuredAiGateway(() -> actor, authorize, context, provider, ledger, policy, clock)) {
                    var request = new Request(project, report, Capability.REPORT_IMPROVEMENT, 1);
                    if (scenario.equals("success")) assertEquals("Synthetic advisory text", gateway.suggest(request).text());
                    else if (scenario.equals("disabled")) assertEquals(AI_UNAVAILABLE,
                            assertThrows(GatewayFailure.class, () -> gateway.suggest(request)).code());
                    else assertEquals(scenario.equals("revoked") ? FORBIDDEN : STALE_SOURCE,
                            assertThrows(GatewayFailure.class, () -> gateway.suggest(request)).code());
                }
            }
            assertEquals(4, calls.get()); assertEquals(4, selections.get());
            assertEquals(4, sql.queryForObject("select count(*) from td_ai_tickets where state='TERMINAL' and charged_units=25", Integer.class));
            assertEquals(12, sql.queryForObject("select count(*) from td_ai_accounting_events", Integer.class));
            assertEquals(0, sql.queryForObject("select count(*) from td_business_audit_events", Integer.class));
            Provider forbiddenProvider = new Provider() {
                public Metadata metadata() { fail("unauthorized provider metadata"); return null; }
                public Completion complete(Context c) { fail("unauthorized completion"); return null; }
            };
            try (var gateway = new SecuredAiGateway(() -> actor, authorize, context, forbiddenProvider, ledger, policy, clock)) {
                assertEquals(FORBIDDEN, assertThrows(GatewayFailure.class, () -> gateway.suggest(
                        new Request(UUID.randomUUID(), report, Capability.REPORT_IMPROVEMENT, 1))).code());
                sql.update("update td_project_memberships set state='ACTIVE' where project_id=? and user_id=?", project, actorId);
                sql.update("update td_reports set version=1 where id=?", report);
                new TransactionTemplate(new DataSourceTransactionManager(db)).executeWithoutResult(tx -> {
                    assertEquals(AI_UNAVAILABLE, assertThrows(GatewayFailure.class, () -> gateway.suggest(
                            new Request(project, report, Capability.REPORT_IMPROVEMENT, 1))).code());
                    tx.setRollbackOnly();
                });
            }
            assertEquals(4, selections.get()); assertEquals(4, sql.queryForObject("select count(*) from td_ai_tickets", Integer.class));
        }
    }
}
