package com.tracedesk.auth.reports.persistence;

import com.tracedesk.auth.reports.application.ReportRepository;
import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.reports.domain.ReportData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReportStore implements ReportRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    public ReportStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    @Override public Optional<Membership> membership(UUID actor, UUID project) {
        return jdbc.query("""
                select p.id,p.name,u.enabled,p.enabled,m.state='ACTIVE',m.role
                from td_projects p join td_project_memberships m on m.project_id=p.id
                join td_users u on u.id=m.user_id where m.user_id=? and p.id=?
                """, rs -> rs.next() ? Optional.of(new Membership(rs.getObject(1,UUID.class),rs.getString(2),rs.getBoolean(3),rs.getBoolean(4),rs.getBoolean(5),rs.getString(6))) : Optional.empty(), actor, project);
    }

    @Override public Report create(UUID actor, UUID project, UUID key, ReportData data, UUID requestId, Instant now) {
        return transactions.execute(status -> {
            Membership membership = jdbc.query("""
                    select p.id,p.name,u.enabled,p.enabled,m.state='ACTIVE',m.role
                    from td_projects p join td_project_memberships m on m.project_id=p.id
                    join td_users u on u.id=m.user_id where m.user_id=? and p.id=?
                    for update of p,u,m
                    """, rs -> rs.next() ? new Membership(rs.getObject(1,UUID.class),rs.getString(2),rs.getBoolean(3),rs.getBoolean(4),rs.getBoolean(5),rs.getString(6)) : null, actor, project);
            if (membership == null || !membership.userEnabled() || !membership.projectEnabled() || !membership.active()) throw new ReportService.Failure("NOT_FOUND",404);
            if (!"REPORTER".equals(membership.role())) throw new ReportService.Failure("FORBIDDEN",403);

            var prior = jdbc.query("select body_sha256,report_id,expires_at from td_report_idempotency where actor_id=? and project_id=? and operation='REPORT_CREATE' and idempotency_key=? for update",
                    rs -> rs.next() ? new Object[]{rs.getBytes(1), rs.getObject(2,UUID.class),rs.getTimestamp(3).toInstant()} : null, actor, project, key);
            byte[] digest = data.sha256();
            if (prior != null) {
                if (((Instant)prior[2]).isAfter(now)) {
                    if (!java.security.MessageDigest.isEqual((byte[])prior[0], digest)) throw new ReportService.Failure("IDEMPOTENCY_CONFLICT",409);
                    return loadReport(actor, project, (UUID)prior[1], false).orElseThrow(() -> new ReportService.Failure("NOT_FOUND",404));
                }
                jdbc.update("delete from td_report_idempotency where actor_id=? and project_id=? and operation='REPORT_CREATE' and idempotency_key=?", actor, project, key);
            }

            UUID id = UUID.randomUUID();
            jdbc.update("""
                    insert into td_reports(id,project_id,reporter_user_id,title,description,environment,reproduction_steps,expected_behavior,actual_behavior,text_logs,status,created_at,updated_at,version)
                    values(?,?,?,?,?,?,?,?,?,?,'OPEN',?, ?,1)
                    """, id, project, actor, data.title(), data.description(), data.environment(), data.reproductionSteps(),
                    data.expectedBehavior(), data.actualBehavior(), data.textLogs(), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
            jdbc.update("""
                    insert into td_report_idempotency(actor_id,project_id,operation,idempotency_key,body_sha256,report_id,created_at,expires_at)
                    values(?,?,'REPORT_CREATE',?,?,?, ?, ?)
                    """, actor, project, key, digest, id, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now.plusSeconds(86400)));
            jdbc.update("""
                    insert into td_business_audit_events(event_code,request_id,actor_id,project_id,report_id,new_status,new_version,outcome)
                    values('REPORT_CREATED',?,?,?,?, 'OPEN',1,'SUCCESS')
                    """, requestId, actor, project, id);
            return loadReport(actor, project, id, false).orElseThrow();
        });
    }

    @Override public Page list(UUID actor, UUID project, String status, Boolean assignedToMe, int limit, Instant beforeUpdatedAt, UUID beforeId) {
        Membership m = membership(actor, project).filter(x -> x.userEnabled() && x.projectEnabled() && x.active())
                .orElseThrow(() -> new ReportService.Failure("NOT_FOUND",404));
        if (!List.of("REPORTER","DEVELOPER","QA_REVIEWER").contains(m.role())) throw new ReportService.Failure("FORBIDDEN",403);
        String sql = """
                select id,project_id,reporter_user_id,title,status,assigned_developer_id,created_at,updated_at,version
                from td_reports where project_id=? and (? <> 'REPORTER' or reporter_user_id=?)
                  and (cast(? as varchar) is null or status=?)
                  and (cast(? as boolean) is null or ((assigned_developer_id is not distinct from ?) = ?))
                  and (cast(? as timestamptz) is null or updated_at < cast(? as timestamptz) or (updated_at=cast(? as timestamptz) and id < cast(? as uuid)))
                order by updated_at desc,id desc limit ?
                """;
        List<Summary> rows = jdbc.query(sql, (rs, row) -> new Summary(rs.getObject("id",UUID.class),rs.getObject("project_id",UUID.class),
                rs.getObject("reporter_user_id",UUID.class),rs.getString("title"),rs.getString("status"),rs.getObject("assigned_developer_id",UUID.class),
                rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),rs.getInt("version")),
                project, m.role(), actor, status, status, assignedToMe, actor, assignedToMe,
                beforeUpdatedAt == null ? null : java.sql.Timestamp.from(beforeUpdatedAt),
                beforeUpdatedAt == null ? null : java.sql.Timestamp.from(beforeUpdatedAt),
                beforeUpdatedAt == null ? null : java.sql.Timestamp.from(beforeUpdatedAt), beforeId, limit + 1);
        boolean more = rows.size() > limit;
        return new Page(more ? rows.subList(0, limit) : rows, more);
    }

    @Override public Optional<Report> detail(UUID actor, UUID project, UUID report) {
        return loadReport(actor, project, report, true);
    }
    private Optional<Report> loadReport(UUID actor, UUID project, UUID id, boolean enforceOwner) {
        Membership m = membership(actor, project).filter(x -> x.userEnabled() && x.projectEnabled() && x.active())
                .orElseThrow(() -> new ReportService.Failure("NOT_FOUND",404));
        if (!List.of("REPORTER","DEVELOPER","QA_REVIEWER").contains(m.role())) throw new ReportService.Failure("FORBIDDEN",403);
        String ownerClause = enforceOwner && "REPORTER".equals(m.role()) ? " and reporter_user_id=?" : "";
        List<Object> args = new ArrayList<>(List.of(project, id)); if (enforceOwner && "REPORTER".equals(m.role())) args.add(actor);
        return jdbc.query("select id,project_id,reporter_user_id,title,description,environment,reproduction_steps,expected_behavior,actual_behavior,text_logs,status,assigned_developer_id,created_at,updated_at,version from td_reports where project_id=? and id=?"+ownerClause,
                rs -> rs.next() ? Optional.of(mapReport(rs)) : Optional.empty(), args.toArray());
    }
    private Report mapReport(ResultSet rs, int row) throws SQLException { return mapReport(rs); }
    private Report mapReport(ResultSet rs) throws SQLException {
        return new Report(rs.getObject("id",UUID.class),rs.getObject("project_id",UUID.class),rs.getObject("reporter_user_id",UUID.class),
                new ReportData(rs.getString("title"),rs.getString("description"),rs.getString("environment"),rs.getString("reproduction_steps"),rs.getString("expected_behavior"),rs.getString("actual_behavior"),rs.getString("text_logs")),
                rs.getString("status"),rs.getObject("assigned_developer_id",UUID.class),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),rs.getInt("version"));
    }
}
