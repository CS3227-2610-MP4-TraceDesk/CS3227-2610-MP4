package com.tracedesk.auth.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.tracedesk.auth.application.ProjectAccessRepository;
import com.tracedesk.auth.application.WorkspaceMembershipRepository;
import com.tracedesk.auth.projects.application.ProjectRepository;
import com.tracedesk.auth.projects.application.ProjectService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AuthStore implements ProjectAccessRepository, WorkspaceMembershipRepository, ProjectRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    public AuthStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }
    public record User(UUID id, String hash, boolean enabled, String role) {
        public User(UUID id,String hash,boolean enabled){this(id,hash,enabled,"REPORTER");}
    }
    public record Project(UUID id, String name) { }
    public Optional<User> user(String normalizedLogin) {
        return jdbc.query("select id,password_hash,enabled,account_role from td_users where login_normalized=?", rs -> rs.next()
                ? Optional.of(new User(rs.getObject(1, UUID.class),rs.getString(2),rs.getBoolean(3),rs.getString(4))) : Optional.empty(), normalizedLogin);
    }
    public Optional<String> accountRole(UUID id) {
        return jdbc.query("select account_role from td_users where id=? and enabled", rs -> rs.next()
                ? Optional.ofNullable(rs.getString(1)) : Optional.empty(), id);
    }
    public boolean enabled(UUID id) {
        return jdbc.query("select enabled and account_role is not null from td_users where id=?", rs -> rs.next() && rs.getBoolean(1), id);
    }
    @Override public Optional<ProjectAccessRepository.Snapshot> findProjectAccess(UUID userId, UUID projectId) {
        return jdbc.query("""
                select p.id, p.name, u.enabled, p.enabled, m.state = 'ACTIVE', u.account_role
                from td_projects p
                join td_project_memberships m on m.project_id = p.id
                join td_users u on u.id = m.user_id
                where m.user_id = ? and p.id = ?
                """, rs -> rs.next()
                ? Optional.of(new ProjectAccessRepository.Snapshot(rs.getObject(1, UUID.class),
                        rs.getString(2), rs.getBoolean(3), rs.getBoolean(4), rs.getBoolean(5), rs.getString(6)))
                : Optional.empty(), userId, projectId);
    }
    @Override public WorkspaceMembershipRepository.Page activeMemberships(UUID actorId, int limit) {
        List<WorkspaceMembershipRepository.Membership> rows = jdbc.query("""
                select p.id, p.name, u.account_role
                from td_project_memberships m
                join td_projects p on p.id = m.project_id
                join td_users u on u.id = m.user_id
                where m.user_id = ? and u.enabled and u.account_role is not null and p.enabled and m.state = 'ACTIVE'
                order by lower(p.name), p.id
                limit ?
                """, (rs, row) -> new WorkspaceMembershipRepository.Membership(
                        rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)), actorId, limit + 1);
        boolean hasMore = rows.size() > limit;
        return new WorkspaceMembershipRepository.Page(hasMore ? List.copyOf(rows.subList(0, limit)) : List.copyOf(rows), hasMore);
    }
    public void event(String code, String route, int status, UUID actor, UUID project) {
        jdbc.update("insert into td_security_events(event_code,request_id,route_template,outcome_status,actor_id,project_id) values(?,?,?,?,?,?)",
                code, UUID.randomUUID(), route, status, actor, project);
    }
    @Override public ProjectRepository.Page directory(UUID actor, int limit, String afterName, UUID afterId) {
        List<ProjectRepository.Summary> rows = jdbc.query("""
                select p.id,p.name,coalesce(m.state,'NONE'),lower(p.name)
                from td_projects p cross join td_users u
                left join td_project_memberships m on m.project_id=p.id and m.user_id=u.id
                where u.id=? and u.enabled and u.account_role is not null and p.enabled
                  and (cast(? as text) is null or lower(p.name)>cast(? as text)
                    or (lower(p.name)=cast(? as text) and p.id>cast(? as uuid)))
                order by lower(p.name),p.id limit ?
                """, (rs,n)->new ProjectRepository.Summary(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4)),
                actor,afterName,afterName,afterName,afterId,limit+1);
        boolean more=rows.size()>limit;
        return new ProjectRepository.Page(more?List.copyOf(rows.subList(0,limit)):List.copyOf(rows),more,null);
    }
    @Override public ProjectRepository.Created create(UUID actor,String name,UUID key,UUID requestId) {
        return transactions.execute(status -> {
            lockCreateKey(actor,key);
            Account account=jdbc.query("select enabled,account_role from td_users where id=? for share",rs->rs.next()?new Account(rs.getBoolean(1),rs.getString(2)):null,actor);
            if(account==null||!account.enabled||account.role==null) throw new ProjectService.Failure("UNAUTHENTICATED",401);
            byte[] digest=sha256(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Object[] prior=jdbc.query("select request_sha256,project_id from td_project_create_idempotency where actor_id=? and idempotency_key=? for update",
                    rs->rs.next()?new Object[]{rs.getBytes(1),rs.getObject(2,UUID.class)}:null,actor,key);
            if(prior!=null){
                if(!java.security.MessageDigest.isEqual((byte[])prior[0],digest)) throw new ProjectService.Failure("IDEMPOTENCY_CONFLICT",409);
                ProjectRepository.Created original=jdbc.query("select id,name from td_projects where id=?",rs->rs.next()?new ProjectRepository.Created(rs.getObject(1,UUID.class),rs.getString(2)):null,prior[1]);
                if(original==null) throw new ProjectService.Failure("SERVICE_UNAVAILABLE",503);
                return original;
            }
            UUID project=UUID.randomUUID();
            jdbc.update("insert into td_projects(id,name,enabled) values(?,?,true)",project,name);
            jdbc.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,?,'ACTIVE')",project,actor,account.role);
            jdbc.update("insert into td_project_create_idempotency(actor_id,idempotency_key,request_sha256,project_id) values(?,?,?,?)",actor,key,digest,project);
            jdbc.update("insert into td_project_business_audit(event_code,request_id,actor_id,project_id,outcome) values('PROJECT_CREATED',?,?,?,'SUCCESS')",requestId,actor,project);
            return new ProjectRepository.Created(project,name);
        });
    }
    @Override public ProjectRepository.Created join(UUID actor,UUID project,UUID requestId) {
        return transactions.execute(status -> {
            ProjectRepository.Created target=jdbc.query("select id,name from td_projects where id=? and enabled for update",
                    rs->rs.next()?new ProjectRepository.Created(rs.getObject(1,UUID.class),rs.getString(2)):null,project);
            if(target==null) throw new ProjectService.Failure("NOT_FOUND",404);
            Account account=jdbc.query("select enabled,account_role from td_users where id=? for share",rs->rs.next()?new Account(rs.getBoolean(1),rs.getString(2)):null,actor);
            if(account==null||!account.enabled||account.role==null) throw new ProjectService.Failure("UNAUTHENTICATED",401);
            String state=jdbc.query("select state from td_project_memberships where project_id=? and user_id=? for update",rs->rs.next()?rs.getString(1):null,project,actor);
            if("ACTIVE".equals(state)) return target;
            if("DISABLED".equals(state)) throw new ProjectService.Failure("MEMBERSHIP_DISABLED",409);
            jdbc.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,?,'ACTIVE')",project,actor,account.role);
            jdbc.update("insert into td_project_business_audit(event_code,request_id,actor_id,project_id,outcome) values('PROJECT_JOINED',?,?,?,'SUCCESS')",requestId,actor,project);
            return target;
        });
    }
    private void lockCreateKey(UUID actor,UUID key) {
        long lock=actor.getMostSignificantBits()^actor.getLeastSignificantBits()^key.getMostSignificantBits()^key.getLeastSignificantBits();
        jdbc.query("select pg_advisory_xact_lock(?)",rs->{if(rs.next())rs.getObject(1);return null;},lock);
    }
    private record Account(boolean enabled,String role) { }
    private static byte[] sha256(byte[] value) {
        try{return java.security.MessageDigest.getInstance("SHA-256").digest(value);}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public void changeMembershipRole(UUID projectId, UUID userId, String role) {
        jdbc.update("update td_project_memberships set role=? where project_id=? and user_id=?",role,projectId,userId);
    }
    public void changeAccountRole(UUID userId,String role) {
        transactions.executeWithoutResult(status -> {
            int changed=jdbc.update("update td_users set account_role=? where id=?",role,userId);
            if(changed==1) deleteUserSessions(userId);
        });
    }
    public void changeMembershipState(UUID projectId, UUID userId, String state) {
        transactions.executeWithoutResult(status -> {
            int changed = jdbc.update("update td_project_memberships set state=? where project_id=? and user_id=?",
                    state, projectId, userId);
            if (changed == 1) deleteUserSessions(userId);
        });
    }
    public void removeMembership(UUID projectId, UUID userId) {
        transactions.executeWithoutResult(status -> {
            int changed = jdbc.update("delete from td_project_memberships where project_id=? and user_id=?",
                    projectId, userId);
            if (changed == 1) deleteUserSessions(userId);
        });
    }
    public void setUserEnabled(UUID userId, boolean enabled) {
        transactions.executeWithoutResult(status -> {
            int changed = jdbc.update("update td_users set enabled=? where id=?", enabled, userId);
            if (changed == 1) deleteUserSessions(userId);
        });
    }
    private void deleteUserSessions(UUID userId) {
        jdbc.update("delete from spring_session_attributes where session_primary_id in (select primary_id from spring_session where principal_name=?)", userId.toString());
        jdbc.update("delete from spring_session where principal_name=?", userId.toString());
    }
}
