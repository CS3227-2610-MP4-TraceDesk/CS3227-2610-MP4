package com.tracedesk.auth.investigation.persistence;

import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.reports.domain.ReportData;
import com.tracedesk.auth.verification.TemplateStore;
import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TemplatePersistenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-12T02:03:04Z");

    @Test void templatesAreTenantAndRoleScopedVersionedAndAuditedWithoutChangingReports() throws Exception {
        try (IsolatedPostgres db = IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
            JdbcTemplate sql = new JdbcTemplate(db);
            TemplateStore store = new TemplateStore(sql);
            UUID project = UUID.randomUUID(), foreign = UUID.randomUUID();
            UUID qa = user(sql, "template-qa@example.test", "QA_REVIEWER");
            UUID otherQa = user(sql, "template-other-qa@example.test", "QA_REVIEWER");
            UUID developer = user(sql, "template-dev@example.test", "DEVELOPER");
            UUID reporter = user(sql, "template-rpt@example.test", "REPORTER");
            sql.update("insert into td_projects(id,name) values(?,?),(?,?)", project, "Template Project", foreign, "Other Project");
            membership(sql, project, qa, "QA_REVIEWER");
            membership(sql, project, developer, "DEVELOPER");
            membership(sql, project, reporter, "REPORTER");
            membership(sql, foreign, otherQa, "QA_REVIEWER");

            UUID report = report(sql, project, reporter, developer);
            String reportBefore = sql.queryForObject("select title||':'||status||':'||version from td_reports where id=?", String.class, report);
            int reportsBefore = sql.queryForObject("select count(*) from td_reports", Integer.class);
            var input = new TemplateStore.Input("Refund checks", "For refund changes", "Two refund records", List.of(
                    new TemplateStore.Case("Unique refund IDs", "Create two refunds", "Each appears once"),
                    new TemplateStore.Case("Refresh results", "Refresh the page", "Results stay deduplicated")));

            assertTrue(store.list(qa, project).isEmpty());
            var created = store.create(qa, project, input);
            assertEquals(1, created.version());
            assertEquals("Refund checks", created.name());
            assertEquals(2, created.testCases().size());
            assertEquals(1, store.list(qa, project).size());
            assertTrue(store.list(otherQa, foreign).isEmpty());

            assertEquals("FORBIDDEN", failure(() -> store.list(developer, project)).code());
            assertEquals("FORBIDDEN", failure(() -> store.create(reporter, project, input)).code());
            assertEquals("NOT_FOUND", failure(() -> store.list(qa, foreign)).code());
            assertEquals("NOT_FOUND", failure(() -> store.update(qa, foreign, created.id(), 1, input)).code());

            var updated = store.update(qa, project, created.id(), 1,
                    new TemplateStore.Input("Refund checks revised", input.whenToUse(), input.preconditions(), input.testCases()));
            assertEquals(2, updated.version());
            assertEquals("STALE_VERSION", failure(() -> store.update(qa, project, created.id(), 1, input)).code());
            assertEquals("STALE_VERSION", failure(() -> store.delete(qa, project, created.id(), 1)).code());
            assertEquals("FORBIDDEN", failure(() -> store.delete(developer, project, created.id(), 2)).code());
            assertEquals("NOT_FOUND", failure(() -> store.delete(qa, foreign, created.id(), 2)).code());

            var events = sql.queryForList("""
                    select event_code,actor_id,project_id,object_type,object_id,prior_version,new_version,outcome
                    from td_business_audit_events where event_code like 'QA_TEMPLATE_%' order by id
                    """);
            assertEquals(2, events.size());
            assertEquals("QA_TEMPLATE_CREATED", events.get(0).get("event_code"));
            assertEquals(qa, events.get(0).get("actor_id"));
            assertEquals(project, events.get(0).get("project_id"));
            assertEquals("QA_TEMPLATE", events.get(0).get("object_type"));
            assertEquals(created.id(), events.get(0).get("object_id"));
            assertNull(events.get(0).get("prior_version"));
            assertEquals(1, events.get(0).get("new_version"));
            assertEquals(1, events.get(1).get("prior_version"));
            assertEquals(2, events.get(1).get("new_version"));
            String audit = events.toString();
            assertFalse(audit.contains("Refund checks"));
            assertFalse(audit.contains("Unique refund IDs"));
            assertFalse(audit.contains("Two refund records"));

            store.delete(qa, project, created.id(), 2);
            assertTrue(store.list(qa, project).isEmpty());
            assertEquals(3, sql.queryForObject("select count(*) from td_business_audit_events where event_code like 'QA_TEMPLATE_%'", Integer.class));
            assertEquals(reportsBefore, sql.queryForObject("select count(*) from td_reports", Integer.class));
            assertEquals(reportBefore, sql.queryForObject("select title||':'||status||':'||version from td_reports where id=?", String.class, report));
        }
    }

    private static ReportService.Failure failure(org.junit.jupiter.api.function.Executable executable) {
        return assertThrows(ReportService.Failure.class, executable);
    }

    private static UUID user(JdbcTemplate sql, String login, String role) {
        UUID id = UUID.randomUUID();
        sql.update("insert into td_users(id,login_normalized,password_hash,account_role) values(?,?,?,?)",
                id, login, "$argon2id$v=19$m=19456,t=2,p=1$synthetic$hash", role);
        return id;
    }

    private static void membership(JdbcTemplate sql, UUID project, UUID user, String role) {
        sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,?,'ACTIVE')",
                project, user, role);
    }

    private static UUID report(JdbcTemplate sql, UUID project, UUID reporter, UUID developer) {
        UUID id = UUID.randomUUID();
        ReportData data = new ReportData("Existing report", "description", "environment", "steps", "expected", "actual", "");
        Timestamp created = Timestamp.from(NOW);
        sql.update("""
                insert into td_reports(id,project_id,reporter_user_id,title,description,environment,reproduction_steps,
                  expected_behavior,actual_behavior,text_logs,status,assigned_developer_id,created_at,updated_at,version)
                values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, project, reporter, data.title(), data.description(), data.environment(), data.reproductionSteps(),
                data.expectedBehavior(), data.actualBehavior(), data.textLogs(), "OPEN", developer, created, created, 1);
        return id;
    }
}
