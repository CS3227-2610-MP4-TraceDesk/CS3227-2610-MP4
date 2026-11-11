package com.tracedesk.auth.verification;

import com.tracedesk.auth.reports.application.ReportService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Repository
public class VerificationStore {
    private static final JsonMapper JSON = JsonMapper.shared();
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate tx;

    public VerificationStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    public record Review(int version, String status, int revision, List<VerificationCommand.Check> checks,
                         String notes, VerificationCommand.Decision decision, String reason) { }
    private record Report(int version, String status) { }
    private record Resolution(UUID id, int revision) { }

    public Review read(UUID actor, UUID project, UUID report) {
        return tx.execute(ignored -> {
            Report current = authorize(actor, project, report, false);
            Resolution resolution = latest(project, report);
            return jdbc.query("""
                    select checks::text, notes, decision, reason from td_qa_verifications where resolution_id=?
                    """, rs -> rs.next() ? new Review(current.version(), current.status(), resolution.revision(),
                        JSON.readValue(rs.getString(1), new TypeReference<List<VerificationCommand.Check>>() {}),
                        rs.getString(2), VerificationCommand.Decision.valueOf(rs.getString(3)), rs.getString(4))
                    : new Review(current.version(), current.status(), resolution.revision(), List.of(), "",
                        VerificationCommand.Decision.SAVE, ""), resolution.id());
        });
    }

    public Review write(UUID actor, UUID project, UUID report, VerificationCommand command) {
        return tx.execute(ignored -> {
            Report current = authorize(actor, project, report, true);
            if (current.version() != command.expectedVersion()) throw failure("STALE_VERSION", 409);
            if (!"READY_FOR_QA".equals(current.status())) throw failure("INVALID_STATE", 409);
            Resolution resolution = latest(project, report);
            String next = switch (command.decision()) {
                case SAVE -> "READY_FOR_QA";
                case CLOSE -> "CLOSED";
                case SEND_BACK -> "IN_PROGRESS";
            };
            String event = switch (command.decision()) {
                case SAVE -> "QA_VERIFICATION_SAVED";
                case CLOSE -> "QA_VERIFICATION_CLOSED";
                case SEND_BACK -> "QA_VERIFICATION_SENT_BACK";
            };
            int version = current.version() + 1;
            jdbc.update("""
                    insert into td_qa_verifications(resolution_id, checks, notes, decision, reason)
                    values (?, cast(? as jsonb), ?, ?, ?) on conflict (resolution_id) do update
                    set checks=excluded.checks, notes=excluded.notes, decision=excluded.decision, reason=excluded.reason
                    """, resolution.id(), JSON.writeValueAsString(command.checks()), command.notes(),
                    command.decision().name(), command.reason());
            jdbc.update("update td_reports set version=?, status=?, updated_at=? where project_id=? and id=?",
                    version, next, Timestamp.from(clock.instant()), project, report);
            jdbc.update("""
                    insert into td_business_audit_events(event_code,request_id,actor_id,project_id,report_id,
                      object_type,object_id,prior_status,new_status,prior_version,new_version,resolution_revision,outcome)
                    values (?, ?, ?, ?, ?, 'RESOLUTION', ?, 'READY_FOR_QA', ?, ?, ?, ?, 'SUCCESS')
                    """, event, UUID.randomUUID(), actor, project, report, resolution.id(), next,
                    current.version(), version, resolution.revision());
            return new Review(version, next, resolution.revision(), command.checks(),
                    command.notes(), command.decision(), command.reason());
        });
    }

    private Report authorize(UUID actor, UUID project, UUID report, boolean write) {
        String role = jdbc.query("""
                select u.account_role from td_projects p join td_project_memberships m on m.project_id=p.id
                join td_users u on u.id=m.user_id
                where p.id=? and u.id=? and p.enabled and u.enabled and m.state='ACTIVE'
                for share of p,u,m
                """, rs -> rs.next() ? rs.getString(1) : null, project, actor);
        if (role == null) throw failure("NOT_FOUND", 404);
        if (!"QA_REVIEWER".equals(role)) throw failure("FORBIDDEN", 403);
        Report current = jdbc.query("select version,status from td_reports where project_id=? and id=?"
                        + (write ? " for update" : " for share"),
                rs -> rs.next() ? new Report(rs.getInt(1), rs.getString(2)) : null, project, report);
        if (current == null) throw failure("NOT_FOUND", 404);
        return current;
    }

    private Resolution latest(UUID project, UUID report) {
        Resolution resolution = jdbc.query("""
                select id,revision from td_report_resolutions where project_id=? and report_id=?
                order by revision desc limit 1
                """, rs -> rs.next() ? new Resolution(rs.getObject(1, UUID.class), rs.getInt(2)) : null, project, report);
        if (resolution == null) throw failure("INVALID_STATE", 409);
        return resolution;
    }

    private static ReportService.Failure failure(String code, int status) {
        return new ReportService.Failure(code, status);
    }
}
