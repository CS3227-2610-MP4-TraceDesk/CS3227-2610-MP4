package com.tracedesk.auth.verification;

import com.tracedesk.auth.reports.application.ReportService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.UUID;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Locale;

@Repository
public class TemplateStore {
    private static final JsonMapper JSON = JsonMapper.shared();
    private static final String FIELDS = "id,version,name,when_to_use,preconditions,test_cases";
    private static final int PROJECT_LIMIT = 50;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public record Case(String name, String steps, String expectedResult) {
        public Case {
            name = text(name, 300, true);
            steps = text(steps, 1000, false);
            expectedResult = text(expectedResult, 1000, false);
        }
    }

    public record Input(String name, String whenToUse, String preconditions, List<Case> testCases) {
        public Input {
            name = text(name, 120, true);
            whenToUse = text(whenToUse, 1000, false);
            preconditions = text(preconditions, 1000, false);
            if (testCases == null || testCases.isEmpty() || testCases.size() > 30)
                throw failure("INVALID_REQUEST", 400);
            var names = new HashSet<String>();
            for (var item : testCases)
                if (item == null || !names.add(item.name().toLowerCase(Locale.ROOT)))
                    throw failure("INVALID_REQUEST", 400);
            testCases = List.copyOf(testCases);
        }
    }

    public record Template(UUID id, int version, String name, String whenToUse,
                           String preconditions, List<Case> testCases) { }

    public TemplateStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    public List<Template> list(UUID actor, UUID project) {
        return tx.execute(status -> {
            requireQa(actor, project, false);
            return jdbc.query("select " + FIELDS + " from td_qa_templates where project_id=? order by name,id",
                    (rs, index) -> template(rs), project);
        });
    }

    public Template create(UUID actor, UUID project, Input input) {
        return tx.execute(status -> {
            requireQa(actor, project, true);
            if (jdbc.queryForObject("select count(*) from td_qa_templates where project_id=?", Integer.class, project)
                    >= PROJECT_LIMIT) throw failure("TEMPLATE_LIMIT", 409);
            UUID id = UUID.randomUUID();
            Template created = jdbc.query("""
                    insert into td_qa_templates(id,project_id,name,when_to_use,preconditions,test_cases,version)
                    values (?,?,?,?,?,cast(? as jsonb),1) returning
                    """ + FIELDS, rs -> rs.next() ? template(rs) : null, id, project,
                    input.name(), input.whenToUse(), input.preconditions(), JSON.writeValueAsString(input.testCases()));
            audit("QA_TEMPLATE_CREATED", actor, project, id, null, 1);
            return created;
        });
    }

    public Template update(UUID actor, UUID project, UUID id, int version, Input input) {
        return tx.execute(status -> {
            requireQa(actor, project, true);
            requireVersion(project, id, version);
            Template updated = jdbc.query("""
                    update td_qa_templates set name=?,when_to_use=?,preconditions=?,test_cases=cast(? as jsonb),
                        version=version+1 where project_id=? and id=? returning
                    """ + FIELDS, rs -> rs.next() ? template(rs) : null, input.name(), input.whenToUse(),
                    input.preconditions(), JSON.writeValueAsString(input.testCases()), project, id);
            audit("QA_TEMPLATE_UPDATED", actor, project, id, version, version + 1);
            return updated;
        });
    }

    public void delete(UUID actor, UUID project, UUID id, int version) {
        tx.executeWithoutResult(status -> {
            requireQa(actor, project, true);
            requireVersion(project, id, version);
            jdbc.update("delete from td_qa_templates where project_id=? and id=?", project, id);
            audit("QA_TEMPLATE_DELETED", actor, project, id, version, null);
        });
    }

    private void requireQa(UUID actor, UUID project, boolean write) {
        String role = jdbc.query("""
                select u.account_role from td_projects p
                join td_project_memberships m on m.project_id=p.id
                join td_users u on u.id=m.user_id
                where p.id=? and u.id=? and p.enabled and u.enabled and m.state='ACTIVE'
                """ + (write ? " for update of p for share of u,m" : " for share of p,u,m"), rs -> rs.next() ? rs.getString(1) : null, project, actor);
        if (role == null) throw failure("NOT_FOUND", 404);
        if (!role.equals("QA_REVIEWER")) throw failure("FORBIDDEN", 403);
    }

    private void requireVersion(UUID project, UUID id, int expected) {
        Integer version = jdbc.query("select version from td_qa_templates where project_id=? and id=?",
                rs -> rs.next() ? rs.getInt(1) : null, project, id);
        if (version == null) throw failure("NOT_FOUND", 404);
        if (version != expected) throw failure("STALE_VERSION", 409);
    }

    private void audit(String event, UUID actor, UUID project, UUID id, Integer prior, Integer next) {
        jdbc.update("""
                insert into td_business_audit_events(event_code,request_id,actor_id,project_id,object_type,object_id,
                    prior_version,new_version,outcome) values (?,?,?,?,'QA_TEMPLATE',?,?,?,'SUCCESS')
                """, event, UUID.randomUUID(), actor, project, id, prior, next);
    }

    private static Template template(ResultSet rs) throws SQLException {
        return new Template(rs.getObject("id", UUID.class), rs.getInt("version"), rs.getString("name"),
                rs.getString("when_to_use"), rs.getString("preconditions"),
                JSON.readValue(rs.getString("test_cases"), new TypeReference<List<Case>>() {}));
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
