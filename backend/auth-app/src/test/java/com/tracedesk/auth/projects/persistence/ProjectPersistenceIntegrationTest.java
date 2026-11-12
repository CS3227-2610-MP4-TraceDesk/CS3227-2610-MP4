package com.tracedesk.auth.projects.persistence;

import com.tracedesk.auth.persistence.AuthStore;
import com.tracedesk.auth.projects.application.ProjectService;
import com.tracedesk.auth.projects.application.ProjectRepository;
import com.tracedesk.auth.projects.domain.ProjectDirectoryCursor;
import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectPersistenceIntegrationTest {
    @Test void createReplayJoinDisabledMembershipDirectoryAndAuditRollback() throws Exception {
        try(IsolatedPostgres db=IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
            JdbcTemplate sql=new JdbcTemplate(db); AuthStore store=new AuthStore(sql);
            ProjectService service=new ProjectService(store,new ProjectDirectoryCursor("synthetic-project-cursor-secret-for-tests-0001"));
            UUID actor=user(sql,"REPORTER"), other=user(sql,"QA_REVIEWER");
            UUID key=UUID.randomUUID();
            var created=service.create(actor,"  Cafe\u0301 ",key,UUID.randomUUID());
            assertEquals("Café",created.name());
            assertEquals(created,service.create(actor,"Café",key,UUID.randomUUID()));
            assertEquals(1,count(sql,"select count(*) from td_projects where id=?",created.id()));
            assertEquals(1,count(sql,"select count(*) from td_project_business_audit where project_id=? and event_code='PROJECT_CREATED'",created.id()));
            assertEquals("REPORTER",sql.queryForObject("select role from td_project_memberships where project_id=? and user_id=?",String.class,created.id(),actor));
            assertThrows(ProjectService.Failure.class,()->service.create(actor,"Different",key,UUID.randomUUID()));

            var joined=service.join(other,created.id(),UUID.randomUUID());
            assertEquals(created,joined); assertEquals(created,service.join(other,created.id(),UUID.randomUUID()));
            assertEquals(1,count(sql,"select count(*) from td_project_business_audit where project_id=? and event_code='PROJECT_JOINED'",created.id()));
            sql.update("update td_project_memberships set state='DISABLED' where project_id=? and user_id=?",created.id(),other);
            var disabled=assertThrows(ProjectService.Failure.class,()->service.join(other,created.id(),UUID.randomUUID()));
            assertEquals("MEMBERSHIP_DISABLED",disabled.code());
            var directory=service.directory(actor,50,null);
            assertEquals("ACTIVE",directory.items().stream().filter(i->i.id().equals(created.id())).findFirst().orElseThrow().membership());
            var otherDirectory=service.directory(other,50,null);
            assertEquals("DISABLED",otherDirectory.items().stream().filter(i->i.id().equals(created.id())).findFirst().orElseThrow().membership());

            sql.execute("create function fail_project_audit() returns trigger language plpgsql as $$ begin raise exception 'synthetic'; end $$");
            sql.execute("create trigger fail_project_audit before insert on td_project_business_audit for each row execute function fail_project_audit()");
            UUID rollbackKey=UUID.randomUUID();
            assertThrows(DataAccessException.class,()->service.create(actor,"Rollback",rollbackKey,UUID.randomUUID()));
            assertEquals(0,count(sql,"select count(*) from td_projects where name='Rollback'"));
            assertEquals(0,count(sql,"select count(*) from td_project_create_idempotency where actor_id=? and idempotency_key=?",actor,rollbackKey));
        }
    }

    @Test void concurrentEqualCreateRequestsProduceOneProjectAndAudit() throws Exception {
        try(IsolatedPostgres db=IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
            JdbcTemplate sql=new JdbcTemplate(db); AuthStore first=new AuthStore(sql),second=new AuthStore(sql);
            UUID actor=user(sql,"DEVELOPER"),key=UUID.randomUUID();
            var pool=Executors.newFixedThreadPool(2); CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
            try {
                Future<?> a=pool.submit(()->create(first,actor,key,ready,go));
                Future<?> b=pool.submit(()->create(second,actor,key,ready,go));
                assertTrue(ready.await(5,TimeUnit.SECONDS));go.countDown();
                Object one=a.get(10,TimeUnit.SECONDS),two=b.get(10,TimeUnit.SECONDS);
                assertEquals(one,two);
                assertEquals(1,count(sql,"select count(*) from td_project_create_idempotency where actor_id=? and idempotency_key=?",actor,key));
                assertEquals(1,count(sql,"select count(*) from td_project_business_audit where event_code='PROJECT_CREATED' and actor_id=?",actor));
            } finally { pool.shutdownNow(); }
        }
    }

    @Test void roleAuthorizationUsesAccountRoleInsteadOfLegacyMembershipSnapshot() throws Exception {
        try(IsolatedPostgres db=IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
            JdbcTemplate sql=new JdbcTemplate(db); AuthStore auth=new AuthStore(sql);
            com.tracedesk.auth.reports.persistence.ReportStore reports=new com.tracedesk.auth.reports.persistence.ReportStore(sql);
            UUID actor=user(sql,"REPORTER"),project=UUID.randomUUID();
            sql.update("insert into td_projects(id,name) values(?,?)",project,"Role source test");
            sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,?,'ACTIVE')",project,actor,"DEVELOPER");
            assertEquals("REPORTER",reports.membership(actor,project).orElseThrow().role());
            auth.changeMembershipRole(project,actor,"QA_REVIEWER");
            assertEquals("REPORTER",reports.membership(actor,project).orElseThrow().role());
            auth.changeAccountRole(actor,"DEVELOPER");
            assertEquals("DEVELOPER",reports.membership(actor,project).orElseThrow().role());
        }
    }

    @Test void unicodeDirectoryPagesFollowDatabaseLowercaseOrderingExactly() throws Exception {
        try(IsolatedPostgres db=IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
            JdbcTemplate sql=new JdbcTemplate(db);AuthStore store=new AuthStore(sql);
            ProjectService service=new ProjectService(store,new ProjectDirectoryCursor("synthetic-project-cursor-secret-for-tests-0001"));
            UUID actor=user(sql,"QA_REVIEWER");
            List<String> names=new java.util.ArrayList<>(List.of("éclair","éclair","İstanbul","istanbul","ßeta","zeta","猫","😀 alpha","Ωmega","Ångström"));
            for(int i=0;i<45;i++) names.add("项目-"+String.format(java.util.Locale.ROOT,"%02d",i));
            for(int i=0;i<names.size();i++)sql.update("insert into td_projects(id,name) values(?,?)",UUID.nameUUIDFromBytes(("directory-"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8)),names.get(i));
            List<UUID> expected=sql.query("select id from td_projects order by lower(name),id",(rs,n)->rs.getObject(1,UUID.class));
            java.util.ArrayList<UUID> actual=new java.util.ArrayList<>();String cursor=null;
            do {var page=service.directory(actor,50,cursor);actual.addAll(page.items().stream().map(ProjectRepository.Summary::id).toList());cursor=page.nextCursor();if(!page.hasMore())break;} while(true);
            assertEquals(expected,actual,"continuation uses the exact lowercase sort key produced by PostgreSQL");
            assertEquals(actual.size(),actual.stream().distinct().count());
        }
    }

    private static Object create(AuthStore store,UUID actor,UUID key,CountDownLatch ready,CountDownLatch go){
        ready.countDown();try{if(!go.await(5,TimeUnit.SECONDS))throw new IllegalStateException();return store.create(actor,"Concurrent",key,UUID.randomUUID());}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
    }
    private static UUID user(JdbcTemplate sql,String role){UUID id=UUID.randomUUID();sql.update("insert into td_users(id,login_normalized,password_hash,account_role) values(?,?,?,?)",id,id+"@example.test","synthetic-hash",role);return id;}
    private static int count(JdbcTemplate sql,String query,Object... args){return sql.queryForObject(query,Integer.class,args);}
}
