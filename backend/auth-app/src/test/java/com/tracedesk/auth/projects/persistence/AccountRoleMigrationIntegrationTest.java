package com.tracedesk.auth.projects.persistence;

import com.tracedesk.auth.persistence.AuthStore;
import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AccountRoleMigrationIntegrationTest {
    @Test void onlyUnambiguousLegacyRoleSetsAreBackfilledAndRowsArePreserved() throws Exception {
        try(IsolatedPostgres db=IsolatedPostgres.open(System.getenv())){
            Flyway.configure().dataSource(db).locations("classpath:db/migration").target(MigrationVersion.fromVersion("10")).load().migrate();
            JdbcTemplate sql=new JdbcTemplate(db);
            UUID same=user(sql),conflict=user(sql),none=user(sql),p1=project(sql,"migration one"),p2=project(sql,"migration two");
            membership(sql,p1,same,"REPORTER","ACTIVE");membership(sql,p2,same,"REPORTER","DISABLED");
            membership(sql,p1,conflict,"DEVELOPER","ACTIVE");membership(sql,p2,conflict,"QA_REVIEWER","DISABLED");
            Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
            assertEquals("REPORTER",sql.queryForObject("select account_role from td_users where id=?",String.class,same));
            assertNull(sql.queryForObject("select account_role from td_users where id=?",String.class,conflict));
            assertNull(sql.queryForObject("select account_role from td_users where id=?",String.class,none));
            assertEquals("CONFLICTING_MEMBERSHIP_ROLES",sql.queryForObject("select reason from td_account_role_migration_pending where user_id=?",String.class,conflict));
            assertEquals("NO_MEMBERSHIP_ROLE",sql.queryForObject("select reason from td_account_role_migration_pending where user_id=?",String.class,none));
            assertArrayEquals(new String[]{"DEVELOPER","QA_REVIEWER"},sql.queryForObject("select legacy_roles from td_account_role_migration_pending where user_id=?",(rs,n)->(String[])rs.getArray(1).getArray(),conflict));
            assertEquals(4,sql.queryForObject("select count(*) from td_project_memberships where user_id in (?,?)",Integer.class,same,conflict));
            assertFalse(new AuthStore(sql).enabled(conflict),"unmapped accounts fail closed for session eligibility");
        }
    }
    private static UUID user(JdbcTemplate sql){UUID id=UUID.randomUUID();sql.update("insert into td_users(id,login_normalized,password_hash) values(?,?,?)",id,id+"@example.test","synthetic-hash");return id;}
    private static UUID project(JdbcTemplate sql,String name){UUID id=UUID.randomUUID();sql.update("insert into td_projects(id,name) values(?,?)",id,name);return id;}
    private static void membership(JdbcTemplate sql,UUID project,UUID user,String role,String state){sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,?,?)",project,user,role,state);}
}
