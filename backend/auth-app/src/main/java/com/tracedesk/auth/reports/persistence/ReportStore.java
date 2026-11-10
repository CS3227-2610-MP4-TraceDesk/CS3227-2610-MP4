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
                select p.id,p.name,u.enabled,p.enabled,m.state='ACTIVE',u.account_role
                from td_projects p join td_project_memberships m on m.project_id=p.id
                join td_users u on u.id=m.user_id where m.user_id=? and p.id=?
                """, rs -> rs.next() ? Optional.of(new Membership(rs.getObject(1,UUID.class),rs.getString(2),rs.getBoolean(3),rs.getBoolean(4),rs.getBoolean(5),rs.getString(6))) : Optional.empty(), actor, project);
    }

    @Override public Report create(UUID actor, UUID project, UUID key, ReportData data, UUID requestId, Instant now) {
        return transactions.execute(status -> {
            Membership membership = jdbc.query("""
                    select p.id,p.name,u.enabled,p.enabled,m.state='ACTIVE',u.account_role
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
                    insert into td_business_audit_events(event_code,request_id,actor_id,project_id,report_id,
                      object_type,object_id,new_status,new_version,outcome)
                    values('REPORT_CREATED',?,?,?,?, 'REPORT',?, 'OPEN',1,'SUCCESS')
                    """, requestId, actor, project, id, id);
            return loadReport(actor, project, id, false).orElseThrow();
        });
    }

    @Override public Draft createDraft(UUID actor, UUID project, UUID key, ReportData data, UUID requestId, Instant now) {
        return transactions.execute(status -> {
            lockReporter(actor, project);
            byte[] digest = data.sha256();
            var prior = jdbc.query("select body_sha256,draft_id,expires_at from td_report_idempotency where actor_id=? and project_id=? and operation='DRAFT_CREATE' and idempotency_key=? for update",
                    rs -> rs.next() ? new Object[]{rs.getBytes(1),rs.getObject(2,UUID.class),rs.getTimestamp(3).toInstant()} : null, actor, project, key);
            if (prior != null) {
                if (((Instant)prior[2]).isAfter(now)) {
                    if (!java.security.MessageDigest.isEqual((byte[])prior[0], digest)) throw new ReportService.Failure("IDEMPOTENCY_CONFLICT",409);
                    UUID priorId = (UUID) prior[1];
                    if (priorId == null) throw new ReportService.Failure("NOT_FOUND",404);
                    return loadDraft(actor, project, priorId, now, true).orElseThrow(() -> new ReportService.Failure("NOT_FOUND",404));
                }
                jdbc.update("delete from td_report_idempotency where actor_id=? and project_id=? and operation='DRAFT_CREATE' and idempotency_key=?",actor,project,key);
            }
            Integer count = jdbc.queryForObject("select count(*) from td_report_drafts where owner_user_id=? and project_id=? and expires_at>?",Integer.class,actor,project,ts(now));
            if (count != null && count >= 20) throw new ReportService.Failure("DRAFT_LIMIT",409);
            UUID id=UUID.randomUUID(); Instant expiry=now.plusSeconds(30L*24*60*60);
            insertDraft(id,project,actor,data,now,expiry);
            jdbc.update("insert into td_report_idempotency(actor_id,project_id,operation,idempotency_key,body_sha256,draft_id,created_at,expires_at) values(?,?,'DRAFT_CREATE',?,?,?,?,?)",
                    actor,project,key,digest,id,ts(now),ts(now.plusSeconds(86400)));
            audit("DRAFT_CREATED",requestId,actor,project,null,id,null,1,null);
            return loadDraft(actor,project,id,now,false).orElseThrow();
        });
    }

    @Override public DraftPage listDrafts(UUID actor, UUID project, int limit, Instant beforeUpdatedAt, UUID beforeId, Instant now) {
        lockReporter(actor,project);
        List<DraftSummary> rows=jdbc.query("""
                select id,project_id,title,created_at,updated_at,expires_at,version,submitted_report_id from td_report_drafts
                where owner_user_id=? and project_id=? and expires_at>? and (cast(? as timestamptz) is null or updated_at<cast(? as timestamptz) or (updated_at=cast(? as timestamptz) and id<cast(? as uuid)))
                order by updated_at desc,id desc limit ?
                """,(rs,n)->new DraftSummary(rs.getObject("id",UUID.class),rs.getObject("project_id",UUID.class),rs.getString("title"),
                rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),rs.getTimestamp("expires_at").toInstant(),rs.getInt("version"),rs.getObject("submitted_report_id",UUID.class)),
                actor,project,ts(now),beforeUpdatedAt==null?null:ts(beforeUpdatedAt),beforeUpdatedAt==null?null:ts(beforeUpdatedAt),beforeUpdatedAt==null?null:ts(beforeUpdatedAt),beforeId,limit+1);
        return new DraftPage(rows.size()>limit?rows.subList(0,limit):rows,rows.size()>limit);
    }

    @Override public Optional<Draft> draft(UUID actor, UUID project, UUID draft, Instant now) {
        lockReporter(actor,project);
        return loadDraft(actor,project,draft,now,true);
    }

    @Override public Draft updateDraft(UUID actor, UUID project, UUID draft, int expectedVersion, ReportData fields, UUID requestId, Instant now) {
        return transactions.execute(tx -> {
            lockReporter(actor,project);
            lockDraft(actor,project,draft);
            var current=loadDraft(actor,project,draft,now,true).orElseThrow(()->new ReportService.Failure("NOT_FOUND",404));
            if(current.submittedReportId()!=null) throw new ReportService.Failure("INVALID_STATE",409);
            if(current.version()!=expectedVersion) throw new ReportService.Failure("STALE_VERSION",409,current.version());
            if(ReportData.validateDraft(fields)!=null) throw new ReportService.Failure("VALIDATION_ERROR",400);
            jdbc.update("update td_report_drafts set title=?,description=?,environment=?,reproduction_steps=?,expected_behavior=?,actual_behavior=?,text_logs=?,updated_at=?,version=version+1 where id=? and version=?",
                    fields.title(),fields.description(),fields.environment(),fields.reproductionSteps(),fields.expectedBehavior(),fields.actualBehavior(),fields.textLogs(),ts(now),draft,expectedVersion);
            audit("DRAFT_EDITED",requestId,actor,project,null,draft,null,expectedVersion+1,expectedVersion);
            return loadDraft(actor,project,draft,now,false).orElseThrow();
        });
    }

    @Override public void deleteDraft(UUID actor, UUID project, UUID draft, int expectedVersion, UUID requestId, Instant now) {
        transactions.executeWithoutResult(tx -> {
            lockReporter(actor,project);
            lockDraft(actor,project,draft);
            var current=loadDraft(actor,project,draft,now,true).orElseThrow(()->new ReportService.Failure("NOT_FOUND",404));
            if(current.submittedReportId()!=null) throw new ReportService.Failure("INVALID_STATE",409);
            if(current.version()!=expectedVersion) throw new ReportService.Failure("STALE_VERSION",409,current.version());
            audit("DRAFT_DELETED",requestId,actor,project,null,draft,null,expectedVersion,expectedVersion);
            jdbc.update("delete from td_report_drafts where id=? and owner_user_id=? and project_id=? and version=?",draft,actor,project,expectedVersion);
        });
    }

    @Override public Report updateReport(UUID actor, UUID project, UUID report, int expectedVersion, ReportData fields, UUID requestId, Instant now) {
        return transactions.execute(tx -> {
            lockReporter(actor,project);
            // Scope by the trusted owner before selecting report fields, then lock the authorized row.
            Report current=jdbc.query("select id,project_id,reporter_user_id,title,description,environment,reproduction_steps,expected_behavior,actual_behavior,text_logs,status,assigned_developer_id,created_at,updated_at,version from td_reports where id=? and project_id=? and reporter_user_id=? for update",
                    rs->rs.next()?mapReport(rs):null,report,project,actor);
            if(current==null) throw new ReportService.Failure("NOT_FOUND",404);
            if(!"OPEN".equals(current.status())) throw new ReportService.Failure("INVALID_STATE",409);
            if(current.version()!=expectedVersion) throw new ReportService.Failure("STALE_VERSION",409,current.version());
            if(ReportData.validate(fields)!=null) throw new ReportService.Failure("VALIDATION_ERROR",400);
            jdbc.update("update td_reports set title=?,description=?,environment=?,reproduction_steps=?,expected_behavior=?,actual_behavior=?,text_logs=?,updated_at=?,version=version+1 where id=? and version=?",
                    fields.title(),fields.description(),fields.environment(),fields.reproductionSteps(),fields.expectedBehavior(),fields.actualBehavior(),fields.textLogs(),ts(now),report,expectedVersion);
            audit("REPORT_EDITED",requestId,actor,project,report,null,null,expectedVersion+1,expectedVersion);
            return loadReport(actor,project,report,true).orElseThrow();
        });
    }

    @Override public Report submitDraft(UUID actor, UUID project, UUID draftId, int expectedVersion, UUID key, UUID requestId, Instant now) {
        return transactions.execute(tx -> {
            lockReporter(actor,project);
            byte[] digest=sha256((draftId+"|"+expectedVersion).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            var prior=jdbc.query("select body_sha256,report_id,expires_at from td_report_idempotency where actor_id=? and project_id=? and operation='DRAFT_SUBMIT' and idempotency_key=? for update",
                    rs->rs.next()?new Object[]{rs.getBytes(1),rs.getObject(2,UUID.class),rs.getTimestamp(3).toInstant()}:null,actor,project,key);
            if(prior!=null && ((Instant)prior[2]).isAfter(now)) {
                if(!java.security.MessageDigest.isEqual((byte[])prior[0],digest)) throw new ReportService.Failure("IDEMPOTENCY_CONFLICT",409);
                return loadReport(actor,project,(UUID)prior[1],true).orElseThrow(()->new ReportService.Failure("NOT_FOUND",404));
            }
            if(prior!=null) jdbc.update("delete from td_report_idempotency where actor_id=? and project_id=? and operation='DRAFT_SUBMIT' and idempotency_key=?",actor,project,key);
            lockDraft(actor,project,draftId);
            Draft current=loadDraft(actor,project,draftId,now,true).orElseThrow(()->new ReportService.Failure("NOT_FOUND",404));
            if(current.submittedReportId()!=null) throw new ReportService.Failure("DRAFT_ALREADY_SUBMITTED",409);
            if(current.version()!=expectedVersion) throw new ReportService.Failure("STALE_VERSION",409,current.version());
            ReportData data=current.fields();
            if(ReportData.validate(data)!=null) throw new ReportService.Failure("VALIDATION_ERROR",400);
            UUID reportId=UUID.randomUUID();
            jdbc.update("insert into td_reports(id,project_id,reporter_user_id,title,description,environment,reproduction_steps,expected_behavior,actual_behavior,text_logs,status,created_at,updated_at,version) values(?,?,?,?,?,?,?,?,?,?,'OPEN',?,?,1)",
                    reportId,project,actor,data.title(),data.description(),data.environment(),data.reproductionSteps(),data.expectedBehavior(),data.actualBehavior(),data.textLogs(),ts(now),ts(now));
            jdbc.update("update td_report_drafts set submitted_report_id=?,updated_at=?,version=version+1 where id=? and version=? and submitted_report_id is null",reportId,ts(now),draftId,expectedVersion);
            jdbc.update("insert into td_report_idempotency(actor_id,project_id,operation,idempotency_key,body_sha256,report_id,draft_id,created_at,expires_at) values(?,?,'DRAFT_SUBMIT',?,?,?,?,?,?)",
                    actor,project,key,digest,reportId,draftId,ts(now),ts(now.plusSeconds(86400)));
            audit("DRAFT_SUBMITTED",requestId,actor,project,reportId,draftId,"OPEN",expectedVersion+1,expectedVersion);
            return loadReport(actor,project,reportId,true).orElseThrow();
        });
    }

    private void lockReporter(UUID actor, UUID project) {
        Membership m=jdbc.query("select p.id,p.name,u.enabled,p.enabled,m.state='ACTIVE',u.account_role from td_projects p join td_project_memberships m on m.project_id=p.id join td_users u on u.id=m.user_id where m.user_id=? and p.id=? for update of p,u,m",
                rs->rs.next()?new Membership(rs.getObject(1,UUID.class),rs.getString(2),rs.getBoolean(3),rs.getBoolean(4),rs.getBoolean(5),rs.getString(6)):null,actor,project);
        if(m==null||!m.userEnabled()||!m.projectEnabled()||!m.active()) throw new ReportService.Failure("NOT_FOUND",404);
        if(!"REPORTER".equals(m.role())) throw new ReportService.Failure("FORBIDDEN",403);
    }
    private Optional<Draft> loadDraft(UUID actor, UUID project, UUID id, Instant now, boolean checkExpiry) {
        List<Draft> found=jdbc.query("select id,project_id,owner_user_id,title,description,environment,reproduction_steps,expected_behavior,actual_behavior,text_logs,created_at,updated_at,expires_at,version,submitted_report_id from td_report_drafts where id=? and project_id=? and owner_user_id=?",
                (rs,n)->new Draft(rs.getObject("id",UUID.class),rs.getObject("project_id",UUID.class),rs.getObject("owner_user_id",UUID.class),
                        new ReportData(rs.getString("title"),rs.getString("description"),rs.getString("environment"),rs.getString("reproduction_steps"),rs.getString("expected_behavior"),rs.getString("actual_behavior"),rs.getString("text_logs")),
                        rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),rs.getTimestamp("expires_at").toInstant(),rs.getInt("version"),rs.getObject("submitted_report_id",UUID.class)),id,project,actor);
        if(found.isEmpty()) return Optional.empty();
        Draft draft=found.get(0);
        if(checkExpiry&&!draft.expiresAt().isAfter(now)) throw new ReportService.Failure("DRAFT_EXPIRED",410);
        return Optional.of(draft);
    }
    private void lockDraft(UUID actor,UUID project,UUID id) {
        jdbc.query("select id from td_report_drafts where id=? and project_id=? and owner_user_id=? for update",rs->null,id,project,actor);
    }
    private void insertDraft(UUID id,UUID project,UUID actor,ReportData f,Instant now,Instant expires) {
        jdbc.update("insert into td_report_drafts(id,project_id,owner_user_id,title,description,environment,reproduction_steps,expected_behavior,actual_behavior,text_logs,created_at,updated_at,expires_at,version) values(?,?,?,?,?,?,?,?,?,?,?,?,?,1)",
                id,project,actor,f.title(),f.description(),f.environment(),f.reproductionSteps(),f.expectedBehavior(),f.actualBehavior(),f.textLogs(),ts(now),ts(now),ts(expires));
    }
    private void audit(String event,UUID requestId,UUID actor,UUID project,UUID report,UUID draft,String status,int version,Integer priorVersion) {
        boolean draftEvent=event.startsWith("DRAFT_");
        jdbc.update("insert into td_business_audit_events(event_code,request_id,actor_id,project_id,report_id,draft_id,object_type,object_id,new_status,new_version,prior_version,outcome) values(?,?,?,?,?,?,?,?,?,?,?,'SUCCESS')",
                event,requestId,actor,project,report,draft,draftEvent?"DRAFT":"REPORT",draftEvent?draft:report,status,version,priorVersion);
    }
    private static java.sql.Timestamp ts(Instant instant) { return java.sql.Timestamp.from(instant); }
    private static byte[] sha256(byte[] value) {
        try { return java.security.MessageDigest.getInstance("SHA-256").digest(value); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
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
