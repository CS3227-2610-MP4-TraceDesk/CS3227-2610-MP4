package com.tracedesk.auth.verification;

import com.tracedesk.auth.reports.application.ReportService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.UUID;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

@Repository
public class DiscussionStore {
    private static final int PAGE_SIZE = 25;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    public record Comment(UUID id, UUID authorUserId, String body, Instant createdAt) { }
    public record Page(List<Comment> items, String nextCursor) { }
    private record Row(long sequence, Comment comment) { }

    public DiscussionStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    public Page list(UUID actor, UUID project, UUID report, Long before) {
        return tx.execute(status -> {
            authorize(actor, project, report);
            List<Row> rows = jdbc.query("""
                    select sequence_id,id,author_id,body,created_at from td_qa_comments
                    where project_id=? and report_id=? and (cast(? as bigint) is null or sequence_id<?)
                    order by sequence_id desc limit ?
                    """, (rs, index) -> new Row(rs.getLong(1), comment(rs)), project, report, before, before, PAGE_SIZE + 1);
            var page = rows.size() > PAGE_SIZE ? rows.subList(0, PAGE_SIZE) : rows;
            return new Page(page.stream().map(Row::comment).toList(),
                    rows.size() > PAGE_SIZE ? Long.toString(page.getLast().sequence()) : null);
        });
    }

    public Comment post(UUID actor, UUID project, UUID report, String body) {
        String clean = text(body, 2000, true);
        return tx.execute(status -> {
            authorize(actor, project, report);
            UUID id = UUID.randomUUID();
            Comment created = jdbc.query("""
                    insert into td_qa_comments(id,project_id,report_id,author_id,body,created_at)
                    values (?,?,?,?,?,?) returning id,author_id,body,created_at
                    """, rs -> rs.next() ? comment(rs) : null, id, project, report, actor, clean, Timestamp.from(clock.instant()));
            jdbc.update("""
                    insert into td_business_audit_events(event_code,request_id,actor_id,project_id,report_id,
                      object_type,object_id,outcome) values ('QA_COMMENT_POSTED',?,?,?,?,'QA_COMMENT',?,'SUCCESS')
                    """, UUID.randomUUID(), actor, project, report, id);
            return created;
        });
    }

    private void authorize(UUID actor, UUID project, UUID report) {
        String role = jdbc.query("""
                select u.account_role from td_projects p
                join td_project_memberships m on m.project_id=p.id
                join td_users u on u.id=m.user_id
                where p.id=? and u.id=? and p.enabled and u.enabled and m.state='ACTIVE'
                for share of p,u,m
                """, rs -> rs.next() ? rs.getString(1) : null, project, actor);
        if (role == null) throw failure("NOT_FOUND", 404);
        if (!role.equals("QA_REVIEWER")) throw failure("FORBIDDEN", 403);
        if (jdbc.query("select id from td_reports where project_id=? and id=? for share",
                ResultSet::next, project, report) == Boolean.FALSE) throw failure("NOT_FOUND", 404);
    }

    private static Comment comment(ResultSet rs) throws SQLException {
        return new Comment(rs.getObject("id", UUID.class), rs.getObject("author_id", UUID.class),
                rs.getString("body"), rs.getTimestamp("created_at").toInstant());
    }

    private static String text(String value, int limit, boolean required) {
        if (value == null || value.codePointCount(0, value.length()) > limit
                || value.codePoints().anyMatch(cp -> (cp >= 0xd800 && cp <= 0xdfff)
                    || (Character.isISOControl(cp) && cp != '\n' && cp != '\r' && cp != '\t'))
                || (required && value.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp))))
            throw failure("INVALID_REQUEST", 400);
        return value.strip();
    }

    private static ReportService.Failure failure(String code, int status) {
        return new ReportService.Failure(code, status);
    }
}
