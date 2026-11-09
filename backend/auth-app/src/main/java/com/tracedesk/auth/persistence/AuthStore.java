package com.tracedesk.auth.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.tracedesk.auth.application.ProjectAccessRepository;
import com.tracedesk.auth.application.WorkspaceMembershipRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AuthStore implements ProjectAccessRepository, WorkspaceMembershipRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    public AuthStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }
    public record User(UUID id, String hash, boolean enabled) { }
    public record Project(UUID id, String name) { }
    public Optional<User> user(String normalizedLogin) {
        return jdbc.query("select id,password_hash,enabled from td_users where login_normalized=?", rs -> rs.next()
                ? Optional.of(new User(rs.getObject(1, UUID.class),rs.getString(2),rs.getBoolean(3))) : Optional.empty(), normalizedLogin);
    }
    public boolean enabled(UUID id) {
        return jdbc.query("select enabled from td_users where id=?", rs -> rs.next() && rs.getBoolean(1), id);
    }
    @Override public Optional<ProjectAccessRepository.Snapshot> findProjectAccess(UUID userId, UUID projectId) {
        return jdbc.query("""
                select p.id, p.name, u.enabled, p.enabled, m.state = 'ACTIVE'
                from td_projects p
                join td_project_memberships m on m.project_id = p.id
                join td_users u on u.id = m.user_id
                where m.user_id = ? and p.id = ?
                """, rs -> rs.next()
                ? Optional.of(new ProjectAccessRepository.Snapshot(rs.getObject(1, UUID.class),
                        rs.getString(2), rs.getBoolean(3), rs.getBoolean(4), rs.getBoolean(5)))
                : Optional.empty(), userId, projectId);
    }
    @Override public WorkspaceMembershipRepository.Page activeMemberships(UUID actorId, int limit) {
        List<WorkspaceMembershipRepository.Membership> rows = jdbc.query("""
                select p.id, p.name, m.role
                from td_project_memberships m
                join td_projects p on p.id = m.project_id
                join td_users u on u.id = m.user_id
                where m.user_id = ? and u.enabled and p.enabled and m.state = 'ACTIVE'
                order by case m.role when 'REPORTER' then 1 when 'DEVELOPER' then 2 else 3 end,
                         lower(p.name), p.id
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
    public void changeMembershipRole(UUID projectId, UUID userId, String role) {
        transactions.executeWithoutResult(status -> {
            int changed = jdbc.update("update td_project_memberships set role=? where project_id=? and user_id=?",
                    role, projectId, userId);
            if (changed == 1) deleteUserSessions(userId);
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
