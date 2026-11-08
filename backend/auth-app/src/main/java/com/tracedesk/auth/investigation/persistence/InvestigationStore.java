package com.tracedesk.auth.investigation.persistence;

import com.tracedesk.auth.investigation.application.InvestigationRepository;
import com.tracedesk.auth.investigation.domain.InvestigationNoteData;
import com.tracedesk.auth.reports.application.ReportRepository;
import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.reports.domain.ReportData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class InvestigationStore implements InvestigationRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public InvestigationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    @Override public Optional<ReportRepository.Membership> membership(UUID actor, UUID project) {
        return jdbc.query("""
                select p.id,p.name,u.enabled,p.enabled,m.state='ACTIVE',m.role
                from td_projects p join td_project_memberships m on m.project_id=p.id
                join td_users u on u.id=m.user_id where m.user_id=? and p.id=?
                """, rs -> rs.next() ? Optional.of(mapMembership(rs)) : Optional.empty(), actor, project);
    }

    @Override public ReportRepository.Report start(UUID actor, UUID project, UUID report, int expectedVersion,
                                                   UUID requestId, Instant now) {
        return transactions.execute(tx -> {
            requireDeveloperLocked(actor, project);
            ReportRepository.Report current = lockReport(project, report, "for update");
            requireAssigned(current, actor);
            if (!"OPEN".equals(current.status())) throw failure("INVALID_STATE", 409);
            if (expectedVersion < 1) throw failure("INVALID_REQUEST", 400);
            if (current.version() != expectedVersion) throw failure("STALE_VERSION", 409);
            int nextVersion = current.version() + 1;
            jdbc.update("update td_reports set status='IN_PROGRESS',updated_at=?,version=? where project_id=? and id=?",
                    timestamp(now), nextVersion, project, report);
            audit("REPORT_INVESTIGATION_STARTED", requestId, actor, project, report, "REPORT", report,
                    "OPEN", "IN_PROGRESS", current.version(), nextVersion, null);
            return loadReport(project, report).orElseThrow();
        });
    }

    @Override public NotePage notes(UUID actor, UUID project, UUID report, int limit,
                                    Instant afterCreatedAt, UUID afterId) {
        requireDeveloper(actor, project);
        ReportRepository.Report current = loadReport(project, report).orElseThrow(() -> failure("NOT_FOUND", 404));
        requireAssigned(current, actor);
        List<Note> rows = jdbc.query("""
                select id,report_id,author_user_id,last_edited_by_user_id,body,created_at,updated_at,version
                from td_investigation_notes where project_id=? and report_id=?
                  and (cast(? as timestamptz) is null or created_at > cast(? as timestamptz)
                    or (created_at=cast(? as timestamptz) and id > cast(? as uuid)))
                order by created_at asc,id asc limit ?
                """, this::mapNote, project, report,
                afterCreatedAt == null ? null : timestamp(afterCreatedAt),
                afterCreatedAt == null ? null : timestamp(afterCreatedAt),
                afterCreatedAt == null ? null : timestamp(afterCreatedAt), afterId, limit + 1);
        boolean more = rows.size() > limit;
        return new NotePage(more ? rows.subList(0, limit) : rows, more);
    }

    @Override public Note createNote(UUID actor, UUID project, UUID report, UUID key,
                                     InvestigationNoteData data, UUID requestId, Instant now) {
        return transactions.execute(tx -> {
            requireDeveloperLocked(actor, project);
            lockNoteKey(actor, project, key);
            byte[] digest = data.sha256();
            Idempotency prior = findNoteKey(actor, project, key, now);
            if (prior != null) {
                requireReplayMatch(prior, report, digest);
                ReportRepository.Report visible = loadReport(project, prior.reportId)
                        .orElseThrow(() -> failure("NOT_FOUND", 404));
                requireAssigned(visible, actor);
                return loadNote(project, prior.reportId, prior.noteId)
                        .orElseThrow(() -> failure("NOT_FOUND", 404));
            }
            ReportRepository.Report current = lockReport(project, report, "for no key update");
            requireAssigned(current, actor);
            Idempotency committedWhileWaiting = findNoteKey(actor, project, key, now);
            if (committedWhileWaiting != null) {
                requireReplayMatch(committedWhileWaiting, report, digest);
                return loadNote(project, report, committedWhileWaiting.noteId)
                        .orElseThrow(() -> failure("NOT_FOUND", 404));
            }
            requireMutableNoteState(current);
            if (!InvestigationNoteData.valid(data)) throw failure("VALIDATION_ERROR", 400);
            Integer count = jdbc.queryForObject("select count(*) from td_investigation_notes where project_id=? and report_id=?",
                    Integer.class, project, report);
            if (count != null && count >= 200) throw failure("NOTE_LIMIT", 409);
            UUID noteId = UUID.randomUUID();
            if (!reserveNoteKey(actor, project, report, key, noteId, digest, now)) {
                Idempotency concurrent = findNoteKey(actor, project, key, now);
                if (concurrent == null) throw failure("SERVICE_UNAVAILABLE", 503);
                requireReplayMatch(concurrent, report, digest);
                return loadNote(project, report, concurrent.noteId)
                        .orElseThrow(() -> failure("NOT_FOUND", 404));
            }
            jdbc.update("""
                    insert into td_investigation_notes(id,project_id,report_id,author_user_id,body,created_at,updated_at,version)
                    values(?,?,?,?,?,?,?,1)
                    """, noteId, project, report, actor, data.body(), timestamp(now), timestamp(now));
            audit("INVESTIGATION_NOTE_CREATED", requestId, actor, project, report, "NOTE", noteId,
                    null, null, null, 1, null);
            return loadNote(project, report, noteId).orElseThrow();
        });
    }

    @Override public Note updateNote(UUID actor, UUID project, UUID report, UUID note, int expectedVersion,
                                     InvestigationNoteData data, UUID requestId, Instant now) {
        return transactions.execute(tx -> {
            requireDeveloperLocked(actor, project);
            ReportRepository.Report current = lockReport(project, report, "for share");
            requireAssigned(current, actor);
            requireMutableNoteState(current);
            Note existing = lockNote(project, report, note);
            if (!InvestigationNoteData.valid(data)) throw failure("VALIDATION_ERROR", 400);
            if (expectedVersion < 1) throw failure("INVALID_REQUEST", 400);
            if (existing.version() != expectedVersion) throw failure("STALE_VERSION", 409);
            int nextVersion = existing.version() + 1;
            jdbc.update("""
                    update td_investigation_notes set body=?,last_edited_by_user_id=?,updated_at=?,version=?
                    where project_id=? and report_id=? and id=?
                    """, data.body(), actor, timestamp(now), nextVersion, project, report, note);
            audit("INVESTIGATION_NOTE_UPDATED", requestId, actor, project, report, "NOTE", note,
                    null, null, existing.version(), nextVersion, null);
            return loadNote(project, report, note).orElseThrow();
        });
    }

    @Override public void deleteNote(UUID actor, UUID project, UUID report, UUID note, int expectedVersion,
                                     UUID requestId, Instant now) {
        transactions.executeWithoutResult(tx -> {
            requireDeveloperLocked(actor, project);
            ReportRepository.Report current = lockReport(project, report, "for share");
            requireAssigned(current, actor);
            requireMutableNoteState(current);
            Note existing = lockNote(project, report, note);
            if (expectedVersion < 1) throw failure("INVALID_REQUEST", 400);
            if (existing.version() != expectedVersion) throw failure("STALE_VERSION", 409);
            jdbc.update("delete from td_investigation_notes where project_id=? and report_id=? and id=?", project, report, note);
            audit("INVESTIGATION_NOTE_DELETED", requestId, actor, project, report, "NOTE", note,
                    null, null, existing.version(), null, null);
        });
    }

    private ReportRepository.Membership requireDeveloper(UUID actor, UUID project) {
        ReportRepository.Membership membership = membership(actor, project)
                .filter(m -> m.userEnabled() && m.projectEnabled() && m.active())
                .orElseThrow(() -> failure("NOT_FOUND", 404));
        if (!"DEVELOPER".equals(membership.role())) throw failure("FORBIDDEN", 403);
        return membership;
    }

    private ReportRepository.Membership requireDeveloperLocked(UUID actor, UUID project) {
        ReportRepository.Membership membership = jdbc.query("""
                select p.id,p.name,u.enabled,p.enabled,m.state='ACTIVE',m.role
                from td_projects p join td_project_memberships m on m.project_id=p.id
                join td_users u on u.id=m.user_id where m.user_id=? and p.id=?
                for share of p,u,m
                """, rs -> rs.next() ? mapMembership(rs) : null, actor, project);
        if (membership == null || !membership.userEnabled() || !membership.projectEnabled() || !membership.active())
            throw failure("NOT_FOUND", 404);
        if (!"DEVELOPER".equals(membership.role())) throw failure("FORBIDDEN", 403);
        return membership;
    }

    private ReportRepository.Report lockReport(UUID project, UUID report, String lock) {
        return jdbc.query(reportSelect() + " where project_id=? and id=? " + lock,
                rs -> rs.next() ? mapReport(rs) : null, project, report);
    }

    private Optional<ReportRepository.Report> loadReport(UUID project, UUID report) {
        return jdbc.query(reportSelect() + " where project_id=? and id=?",
                rs -> rs.next() ? Optional.of(mapReport(rs)) : Optional.empty(), project, report);
    }

    private Note lockNote(UUID project, UUID report, UUID note) {
        Note existing = jdbc.query("""
                select id,report_id,author_user_id,last_edited_by_user_id,body,created_at,updated_at,version
                from td_investigation_notes where project_id=? and report_id=? and id=? for update
                """, rs -> rs.next() ? mapNote(rs, 0) : null, project, report, note);
        if (existing == null) throw failure("NOT_FOUND", 404);
        return existing;
    }

    private Optional<Note> loadNote(UUID project, UUID report, UUID note) {
        return jdbc.query("""
                select id,report_id,author_user_id,last_edited_by_user_id,body,created_at,updated_at,version
                from td_investigation_notes where project_id=? and report_id=? and id=?
                """, rs -> rs.next() ? Optional.of(mapNote(rs, 0)) : Optional.empty(), project, report, note);
    }

    private Idempotency findNoteKey(UUID actor, UUID project, UUID key, Instant now) {
        Idempotency prior = jdbc.query("""
                select body_sha256,report_id,note_id,expires_at from td_investigation_note_idempotency
                where actor_id=? and project_id=? and operation='NOTE_CREATE' and idempotency_key=? for update
                """, rs -> rs.next() ? new Idempotency(rs.getBytes(1), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getTimestamp(4).toInstant()) : null, actor, project, key);
        if (prior != null && !prior.expiresAt.isAfter(now)) {
            jdbc.update("delete from td_investigation_note_idempotency where actor_id=? and project_id=? and operation='NOTE_CREATE' and idempotency_key=?",
                    actor, project, key);
            return null;
        }
        return prior;
    }

    private void lockNoteKey(UUID actor, UUID project, UUID key) {
        long lockId = actor.getMostSignificantBits() ^ actor.getLeastSignificantBits()
                ^ project.getMostSignificantBits() ^ project.getLeastSignificantBits()
                ^ key.getMostSignificantBits() ^ key.getLeastSignificantBits();
        jdbc.query("select pg_advisory_xact_lock(?)", rs -> {
            if (rs.next()) rs.getObject(1);
            return null;
        }, lockId);
    }

    private boolean reserveNoteKey(UUID actor, UUID project, UUID report, UUID key, UUID note,
                                   byte[] digest, Instant now) {
        return jdbc.update("""
                insert into td_investigation_note_idempotency
                  (actor_id,project_id,operation,idempotency_key,body_sha256,report_id,note_id,created_at,expires_at)
                values(?,?,'NOTE_CREATE',?,?,?,?,?,?) on conflict do nothing
                """, actor, project, key, digest, report, note, timestamp(now), timestamp(now.plusSeconds(86_400))) == 1;
    }

    private void requireReplayMatch(Idempotency prior, UUID report, byte[] digest) {
        if (!java.security.MessageDigest.isEqual(prior.digest, digest) || !prior.reportId.equals(report))
            throw failure("IDEMPOTENCY_CONFLICT", 409);
    }

    private void requireAssigned(ReportRepository.Report report, UUID actor) {
        if (report == null) throw failure("NOT_FOUND", 404);
        if (!actor.equals(report.assignedDeveloperId())) throw failure("FORBIDDEN", 403);
    }
    private void requireMutableNoteState(ReportRepository.Report report) {
        if (!List.of("IN_PROGRESS", "READY_FOR_QA").contains(report.status())) throw failure("INVALID_STATE", 409);
    }

    private void audit(String code, UUID requestId, UUID actor, UUID project, UUID report,
                       String objectType, UUID objectId, String priorStatus, String newStatus,
                       Integer priorVersion, Integer newVersion, Integer resolutionRevision) {
        jdbc.update("""
                insert into td_business_audit_events
                  (event_code,request_id,actor_id,project_id,report_id,object_type,object_id,
                   prior_status,new_status,prior_version,new_version,resolution_revision,outcome)
                values(?,?,?,?,?,?,?,?,?,?,?,?, 'SUCCESS')
                """, code, requestId, actor, project, report, objectType, objectId,
                priorStatus, newStatus, priorVersion, newVersion, resolutionRevision);
    }

    private static String reportSelect() {
        return """
                select id,project_id,reporter_user_id,title,description,environment,reproduction_steps,
                  expected_behavior,actual_behavior,text_logs,status,assigned_developer_id,created_at,updated_at,version
                from td_reports
                """;
    }
    private static ReportRepository.Membership mapMembership(ResultSet rs) throws SQLException {
        return new ReportRepository.Membership(rs.getObject(1, UUID.class), rs.getString(2), rs.getBoolean(3),
                rs.getBoolean(4), rs.getBoolean(5), rs.getString(6));
    }
    private static ReportRepository.Report mapReport(ResultSet rs) throws SQLException {
        return new ReportRepository.Report(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getObject("reporter_user_id", UUID.class), new ReportData(rs.getString("title"),
                rs.getString("description"), rs.getString("environment"), rs.getString("reproduction_steps"),
                rs.getString("expected_behavior"), rs.getString("actual_behavior"), rs.getString("text_logs")),
                rs.getString("status"), rs.getObject("assigned_developer_id", UUID.class),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(), rs.getInt("version"));
    }
    private Note mapNote(ResultSet rs, int ignored) throws SQLException {
        return new Note(rs.getObject("id", UUID.class), rs.getObject("report_id", UUID.class),
                rs.getObject("author_user_id", UUID.class), rs.getObject("last_edited_by_user_id", UUID.class),
                rs.getString("body"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(), rs.getInt("version"));
    }
    private static java.sql.Timestamp timestamp(Instant value) { return java.sql.Timestamp.from(value); }
    private static ReportService.Failure failure(String code, int status) { return new ReportService.Failure(code, status); }
    private record Idempotency(byte[] digest, UUID reportId, UUID noteId, Instant expiresAt) { }
}
