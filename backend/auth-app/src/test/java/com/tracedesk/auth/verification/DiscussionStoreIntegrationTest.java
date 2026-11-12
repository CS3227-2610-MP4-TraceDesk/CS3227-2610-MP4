package com.tracedesk.auth.verification;

import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.reports.domain.ReportData;
import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Required isolated PostgreSQL coverage for QA-only report discussion storage. */
class DiscussionStoreIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-12T03:00:00Z");

    @Test
    void discussionIsProjectAndReportScopedAuthorBoundPaginatedAndAuditedAtomically() throws java.sql.SQLException {
        try (IsolatedPostgres db = IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
            JdbcTemplate sql = new JdbcTemplate(db);
            DiscussionStore store = new DiscussionStore(sql, Clock.fixed(NOW, ZoneOffset.UTC));

            UUID project = UUID.randomUUID();
            UUID otherProject = UUID.randomUUID();
            sql.update("insert into td_projects(id,name) values(?,?),(?,?)",
                    project, "Discussion test", otherProject, "Other discussion test");

            UUID reporter = user(sql, "discussion-reporter", "REPORTER");
            UUID qaOne = user(sql, "discussion-qa-one", "QA_REVIEWER");
            UUID qaTwo = user(sql, "discussion-qa-two", "QA_REVIEWER");
            UUID developer = user(sql, "discussion-developer", "DEVELOPER");
            membership(sql, project, reporter, "REPORTER");
            membership(sql, project, qaOne, "QA_REVIEWER");
            membership(sql, project, qaTwo, "QA_REVIEWER");
            membership(sql, project, developer, "DEVELOPER");

            UUID report = report(sql, project, reporter);
            UUID separateReport = report(sql, project, reporter);
            UUID privateReport = report(sql, otherProject, reporter);

            DiscussionStore.Comment first = store.post(qaOne, project, report, "Visible to project QA");
            DiscussionStore.Comment second = store.post(qaTwo, project, report, "Second reviewer comment");
            assertEquals(qaOne, first.authorUserId(), "The store binds authorship to its actor argument.");
            assertEquals(qaTwo, second.authorUserId());
            assertEquals(List.of(second, first), store.list(qaTwo, project, report, null).items());
            assertEquals(List.of(second, first), store.list(qaOne, project, report, null).items());

            DiscussionStore.Comment isolated = store.post(qaOne, project, separateReport, "Only on this report");
            assertEquals(List.of(isolated), store.list(qaTwo, project, separateReport, null).items());
            assertFalse(store.list(qaOne, project, report, null).items().contains(isolated));

            // Seed enough sequence-ordered rows to exercise the store's fixed 25-item cursor page.
            for (int index = 0; index < 26; index++) {
                sql.update("""
                        insert into td_qa_comments(id,project_id,report_id,author_id,body,created_at)
                        values(?,?,?,?,?,?)
                        """, UUID.randomUUID(), project, report, qaOne, "Page seed " + index,
                        Timestamp.from(NOW.plusSeconds(index + 1)));
            }
            DiscussionStore.Page newest = store.list(qaOne, project, report, null);
            assertEquals(25, newest.items().size());
            assertNotNull(newest.nextCursor());
            assertEquals(qaOne, newest.items().getFirst().authorUserId());
            DiscussionStore.Page oldest = store.list(qaTwo, project, report, Long.parseLong(newest.nextCursor()));
            assertEquals(3, oldest.items().size());
            assertNull(oldest.nextCursor());
            assertTrue(newest.items().stream().noneMatch(comment -> oldest.items().contains(comment)));

            assertEquals("FORBIDDEN", failure(() -> store.list(reporter, project, report, null)).code());
            assertEquals("FORBIDDEN", failure(() -> store.post(developer, project, report, "Not QA")).code());
            assertEquals("NOT_FOUND", failure(() -> store.list(qaOne, otherProject, report, null)).code());
            assertEquals("NOT_FOUND", failure(() -> store.list(qaOne, project, privateReport, null)).code());

            UUID eventObjectId = first.id();
            var audit = sql.queryForMap("""
                    select request_id,actor_id,project_id,report_id,object_type,object_id,outcome
                    from td_business_audit_events where event_code='QA_COMMENT_POSTED' and object_id=?
                    """, eventObjectId);
            assertNotNull(audit.get("request_id"));
            assertEquals(qaOne, audit.get("actor_id"));
            assertEquals(project, audit.get("project_id"));
            assertEquals(report, audit.get("report_id"));
            assertEquals("QA_COMMENT", audit.get("object_type"));
            assertEquals(eventObjectId, audit.get("object_id"));
            assertEquals("SUCCESS", audit.get("outcome"));

            int commentCountBeforeRollback = countComments(sql, report);
            int auditCountBeforeRollback = countAudits(sql, report);
            sql.execute("""
                    create function fail_qa_comment_audit() returns trigger language plpgsql as $$
                    begin
                      if new.event_code = 'QA_COMMENT_POSTED' then raise exception 'synthetic audit failure'; end if;
                      return new;
                    end $$
                    """);
            sql.execute("create trigger fail_qa_comment_audit before insert on td_business_audit_events " +
                    "for each row execute function fail_qa_comment_audit()");
            assertThrows(DataAccessException.class, () -> store.post(qaTwo, project, report, "Must roll back"));
            assertEquals(commentCountBeforeRollback, countComments(sql, report));
            assertEquals(auditCountBeforeRollback, countAudits(sql, report));
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

    private static UUID report(JdbcTemplate sql, UUID project, UUID reporter) {
        UUID id = UUID.randomUUID();
        ReportData data = new ReportData("title", "description", "environment", "steps", "expected", "actual", "");
        Timestamp created = Timestamp.from(NOW.minusSeconds(60));
        sql.update("""
                insert into td_reports(id,project_id,reporter_user_id,title,description,environment,reproduction_steps,
                  expected_behavior,actual_behavior,text_logs,status,assigned_developer_id,created_at,updated_at,version)
                values(?,?,?,?,?,?,?,?,?,?,'OPEN',null,?,?,1)
                """, id, project, reporter, data.title(), data.description(), data.environment(),
                data.reproductionSteps(), data.expectedBehavior(), data.actualBehavior(), data.textLogs(), created, created);
        return id;
    }

    private static int countComments(JdbcTemplate sql, UUID report) {
        return sql.queryForObject("select count(*) from td_qa_comments where report_id=?", Integer.class, report);
    }

    private static int countAudits(JdbcTemplate sql, UUID report) {
        return sql.queryForObject("select count(*) from td_business_audit_events " +
                "where report_id=? and event_code='QA_COMMENT_POSTED'", Integer.class, report);
    }
}
