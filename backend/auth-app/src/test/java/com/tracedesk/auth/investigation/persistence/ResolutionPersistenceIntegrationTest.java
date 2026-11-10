package com.tracedesk.auth.investigation.persistence;

import com.tracedesk.auth.investigation.domain.InvestigationNoteData;
import com.tracedesk.auth.investigation.domain.ProposedResolutionData;
import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.reports.domain.ReportData;
import com.tracedesk.testing.IsolatedPostgres;
import com.tracedesk.auth.verification.VerificationStore;
import com.tracedesk.auth.verification.VerificationCommand;
import static com.tracedesk.auth.verification.VerificationCommand.Decision.*;
import static com.tracedesk.auth.verification.VerificationCommand.Result.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Required isolated PostgreSQL coverage for the atomic Developer-to-QA handoff. */
class ResolutionPersistenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-09T01:00:00Z");
    private static final ProposedResolutionData VALID = new ProposedResolutionData("cause 🙂", "change", "verify", "branch");

    @Test void qaSavesClosesAndSendsBackWithVersionAndResolutionIsolation() throws Exception {
        try (IsolatedPostgres db = IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
            var sql = new JdbcTemplate(db);
            var developerStore = new InvestigationStore(sql);
            var qaStore = new VerificationStore(sql, java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
            UUID project = UUID.randomUUID(), foreign = UUID.randomUUID();
            UUID reporter = user(sql, "qa-rpt@example.test"), developer = user(sql, "qa-dev@example.test"),
                    qa = user(sql, "qa-review@example.test");
            sql.update("insert into td_projects(id,name) values(?,?),(?,?)", project, "QA Test", foreign, "Foreign Test");
            membership(sql, project, reporter, "REPORTER");
            membership(sql, project, developer, "DEVELOPER");
            membership(sql, project, qa, "QA_REVIEWER");
            UUID report = report(sql, project, reporter, developer, "IN_PROGRESS", 2);
            developerStore.propose(developer, project, report, VALID, 2, UUID.randomUUID(), NOW);

            var pending = new VerificationCommand.Check("Refresh the payment", Pending, "");
            var passed = new VerificationCommand.Check(pending.title(), Pass, "Observed once");
            var save = new VerificationCommand(3, List.of(pending), "Staging", SAVE, "");
            assertEquals("FORBIDDEN", failure(() -> qaStore.write(developer, project, report, save)).code());
            assertEquals("NOT_FOUND", failure(() -> qaStore.read(qa, foreign, report)).code());
            assertEquals("CHECKS_NOT_PASSED", failure(() -> new VerificationCommand(3, List.of(pending), "", CLOSE, "")).code());
            assertEquals(4, qaStore.write(qa, project, report, save).version());
            assertEquals(List.of(pending), qaStore.read(qa, project, report).checks());
            assertEquals("STALE_VERSION", failure(() -> qaStore.write(qa, project, report, save)).code());

            var back = qaStore.write(qa, project, report, new VerificationCommand(4, List.of(pending), "Staging", SEND_BACK, "Still duplicated"));
            assertEquals("IN_PROGRESS", back.status());
            assertEquals("Still duplicated", qaStore.read(qa, project, report).reason());
            developerStore.propose(developer, project, report, VALID, back.version(), UUID.randomUUID(), NOW);
            var next = qaStore.read(qa, project, report);
            assertEquals(2, next.revision());
            assertTrue(next.checks().isEmpty());

            var close = new VerificationCommand(next.version(), List.of(passed), "", CLOSE, "");
            sql.execute("alter table td_business_audit_events rename to qa_test_audit_unavailable");
            try { assertThrows(DataAccessException.class, () -> qaStore.write(qa, project, report, close)); }
            finally { sql.execute("alter table qa_test_audit_unavailable rename to td_business_audit_events"); }
            assertEquals(next, qaStore.read(qa, project, report));
            assertEquals("CLOSED", qaStore.write(qa, project, report, close).status());
            assertEquals(3, sql.queryForObject("select count(*) from td_business_audit_events where event_code like 'QA_%'", Integer.class));
        }
    }

    @Test void proposalTransitionsAtomicallyAndEnforcesAccessStateVersionCapsAndRaces() throws Exception {
        try (IsolatedPostgres db = IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
            JdbcTemplate sql = new JdbcTemplate(db); InvestigationStore store = new InvestigationStore(sql);
            UUID project = UUID.randomUUID(), foreign = UUID.randomUUID();
            UUID reporter = user(sql, "res-rpt@example.test"), developer = user(sql, "res-one@example.test"),
                    other = user(sql, "res-two@example.test"), qa = user(sql, "res-qa@example.test");
            sql.update("insert into td_projects(id,name) values(?,?),(?,?)", project, "Resolution Test", foreign, "Foreign Test");
            membership(sql, project, reporter, "REPORTER"); membership(sql, project, developer, "DEVELOPER");
            membership(sql, project, other, "DEVELOPER"); membership(sql, project, qa, "QA_REVIEWER");
            membership(sql, foreign, developer, "DEVELOPER");

            // A successful proposal commits the revision, transition, version and one exact audit event.
            UUID report = report(sql, project, reporter, developer, "IN_PROGRESS", 2);
            store.createNote(developer, project, report, UUID.randomUUID(), new InvestigationNoteData("private canary"), UUID.randomUUID(), NOW);
            UUID request = UUID.randomUUID();
            var proposal = store.propose(developer, project, report, VALID, 2, request, NOW);
            assertEquals("READY_FOR_QA", proposal.report().status()); assertEquals(3, proposal.report().version());
            assertEquals(NOW, proposal.report().updatedAt());
            assertEquals(1, proposal.resolution().revision()); assertEquals("cause 🙂", proposal.resolution().rootCause());
            assertEquals(developer, proposal.resolution().authorUserId());
            assertEquals(1, sql.queryForObject("""
                    select count(*) from td_business_audit_events
                    where event_code='REPORT_RESOLUTION_PROPOSED' and request_id=? and actor_id=? and project_id=? and report_id=?
                      and object_type='RESOLUTION' and object_id=? and prior_status='IN_PROGRESS' and new_status='READY_FOR_QA'
                      and prior_version=2 and new_version=3 and resolution_revision=1 and outcome='SUCCESS'
                    """, Integer.class, request, developer, project, report, proposal.resolution().id()));

            // Every staff member reads the handoff; the Reporter and other projects do not, and private notes never appear.
            for (UUID staff : List.of(developer, other, qa)) {
                var page = store.resolutions(staff, project, report, 25, null);
                assertEquals(1, page.items().size()); assertFalse(page.hasMore());
                assertFalse(page.items().getFirst().toString().contains("private canary"));
            }
            assertEquals("FORBIDDEN", failure(() -> store.resolutions(reporter, project, report, 25, null)).code());
            assertEquals("NOT_FOUND", failure(() -> store.resolutions(developer, foreign, report, 25, null)).code());
            assertEquals("NOT_FOUND", failure(() -> store.resolutions(developer, project, UUID.randomUUID(), 25, null)).code());

            // Repeats follow the lifecycle-before-version precedence, before and after a simulated QA rejection.
            assertEquals("INVALID_STATE", failure(() -> store.propose(developer, project, report, VALID, 3, UUID.randomUUID(), NOW)).code());
            sql.update("update td_reports set status='IN_PROGRESS',version=4 where id=?", report);
            assertEquals("STALE_VERSION", failure(() -> store.propose(developer, project, report, VALID, 3, UUID.randomUUID(), NOW)).code());
            var second = store.propose(developer, project, report, new ProposedResolutionData("second", "change", "verify", ""), 4, UUID.randomUUID(), NOW.plusSeconds(1));
            assertEquals(2, second.resolution().revision()); assertEquals("", second.resolution().fixReference());

            // Revision pagination keeps ascending order across a signed cursor position.
            var first = store.resolutions(qa, project, report, 1, null);
            assertTrue(first.hasMore()); assertEquals(1, first.items().getFirst().revision());
            var rest = store.resolutions(qa, project, report, 1, first.items().getFirst().id());
            assertFalse(rest.hasMore()); assertEquals(2, rest.items().getFirst().revision());

            // Authorization and state are decided before content, and no denied attempt commits anything.
            UUID working = report(sql, project, reporter, developer, "IN_PROGRESS", 2);
            ProposedResolutionData blank = new ProposedResolutionData(" ", "change", "verify", "");
            assertEquals("FORBIDDEN", failure(() -> store.propose(other, project, working, blank, 2, UUID.randomUUID(), NOW)).code());
            assertEquals("FORBIDDEN", failure(() -> store.propose(qa, project, working, VALID, 2, UUID.randomUUID(), NOW)).code());
            assertEquals("FORBIDDEN", failure(() -> store.propose(reporter, project, working, VALID, 2, UUID.randomUUID(), NOW)).code());
            assertEquals("NOT_FOUND", failure(() -> store.propose(developer, foreign, working, VALID, 2, UUID.randomUUID(), NOW)).code());
            assertEquals("NOT_FOUND", failure(() -> store.propose(developer, project, UUID.randomUUID(), VALID, 2, UUID.randomUUID(), NOW)).code());
            assertEquals("VALIDATION_ERROR", failure(() -> store.propose(developer, project, working, blank, 2, UUID.randomUUID(), NOW)).code());
            assertEquals("INVALID_REQUEST", failure(() -> store.propose(developer, project, working, VALID, 0, UUID.randomUUID(), NOW)).code());
            assertEquals("STALE_VERSION", failure(() -> store.propose(developer, project, working, VALID, 1, UUID.randomUUID(), NOW)).code());
            UUID opened = report(sql, project, reporter, developer, "OPEN", 1);
            assertEquals("INVALID_STATE", failure(() -> store.propose(developer, project, opened, VALID, 1, UUID.randomUUID(), NOW)).code());
            UUID closed = report(sql, project, reporter, developer, "CLOSED", 5);
            assertEquals("INVALID_STATE", failure(() -> store.propose(developer, project, closed, VALID, 5, UUID.randomUUID(), NOW)).code());
            assertEquals(0, count(sql, working)); assertEquals(0, count(sql, opened)); assertEquals(0, count(sql, closed));
            assertEquals("IN_PROGRESS", status(sql, working)); assertEquals(2, version(sql, working));

            // The database independently enforces bounds, project consistency and revision uniqueness.
            assertThrows(DataAccessException.class, () -> raw(sql, foreign, working, developer, 1, "mismatch", ""));
            assertThrows(DataAccessException.class, () -> raw(sql, project, working, developer, 1, " \t\n", ""));
            assertThrows(DataAccessException.class, () -> raw(sql, project, working, developer, 1, "x".repeat(4_001), ""));
            assertThrows(DataAccessException.class, () -> raw(sql, project, working, developer, 1, "bad\u0085", ""));
            assertThrows(DataAccessException.class, () -> raw(sql, project, working, developer, 1, "root", "x".repeat(201)));
            assertThrows(DataAccessException.class, () -> raw(sql, project, working, developer, 0, "root", ""));
            assertThrows(DataAccessException.class, () -> raw(sql, project, working, developer, 51, "root", ""));
            for (String value : List.of(" \t\n", "x".repeat(4_001), "bad\u0085")) {
                assertThrows(DataAccessException.class, () -> rawText(sql, project, working, developer, value, "verify"));
                assertThrows(DataAccessException.class, () -> rawText(sql, project, working, developer, "change", value));
            }
            raw(sql, project, working, developer, 1, "root", "");
            assertThrows(DataAccessException.class, () -> raw(sql, project, working, developer, 1, "duplicate", ""));

            // At 49 revisions the 50th commits and the 51st reports the cap, ahead of the version check.
            UUID capped = report(sql, project, reporter, developer, "IN_PROGRESS", 2);
            for (int revision = 1; revision <= 49; revision++) raw(sql, project, capped, developer, revision, "seed", "");
            assertEquals(50, store.propose(developer, project, capped, VALID, 2, UUID.randomUUID(), NOW).resolution().revision());
            sql.update("update td_reports set status='IN_PROGRESS',version=9 where id=?", capped);
            assertEquals("RESOLUTION_LIMIT", failure(() -> store.propose(developer, project, capped, VALID, 1, UUID.randomUUID(), NOW)).code());
            assertEquals(50, count(sql, capped)); assertEquals("IN_PROGRESS", status(sql, capped));

            // Two submissions at the same version serialize on the report row: exactly one wins.
            UUID raced = report(sql, project, reporter, developer, "IN_PROGRESS", 2);
            CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
            try {
                var one = pool.submit(() -> proposeAfterGate(store, developer, project, raced, ready, go));
                var two = pool.submit(() -> proposeAfterGate(store, developer, project, raced, ready, go));
                assertTrue(ready.await(5, TimeUnit.SECONDS)); go.countDown();
                int successes = 0, invalid = 0;
                for (var future : List.of(one, two)) {
                    try { future.get(10, TimeUnit.SECONDS); successes++; }
                    catch (ExecutionException problem) {
                        if (problem.getCause() instanceof ReportService.Failure f && "INVALID_STATE".equals(f.code())) invalid++;
                        else throw problem;
                    }
                }
                assertEquals(1, successes); assertEquals(1, invalid);
                assertEquals(1, count(sql, raced)); assertEquals(3, version(sql, raced)); assertEquals(1, audits(sql, raced));
            } finally { go.countDown(); pool.shutdownNow(); }

            // A committed reassignment wins over a proposal blocked on the report row.
            UUID reassigned = report(sql, project, reporter, developer, "IN_PROGRESS", 2);
            CountDownLatch reportLocked = new CountDownLatch(1), commit = new CountDownLatch(1);
            var racePool = Executors.newFixedThreadPool(2); TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(db));
            try {
                var change = racePool.submit(() -> tx.executeWithoutResult(status -> {
                    sql.queryForObject("select status from td_reports where id=? for update", String.class, reassigned);
                    reportLocked.countDown(); await(commit);
                    sql.update("update td_reports set assigned_developer_id=? where id=?", other, reassigned);
                }));
                assertTrue(reportLocked.await(5, TimeUnit.SECONDS));
                var blocked = racePool.submit(() -> store.propose(developer, project, reassigned, VALID, 2, UUID.randomUUID(), NOW));
                assertTrue(awaitLock(sql, "from td_reports")); commit.countDown(); change.get(5, TimeUnit.SECONDS);
                var cause = assertThrows(ExecutionException.class, () -> blocked.get(5, TimeUnit.SECONDS)).getCause();
                assertEquals("FORBIDDEN", ((ReportService.Failure) cause).code());
                assertEquals(0, count(sql, reassigned)); assertEquals("IN_PROGRESS", status(sql, reassigned));
            } finally { commit.countDown(); racePool.shutdownNow(); }

            // A committed QA rejection returning the row to a newer version beats a proposal blocked behind it.
            UUID rejected = report(sql, project, reporter, developer, "IN_PROGRESS", 4);
            CountDownLatch rejectLocked = new CountDownLatch(1), commitReject = new CountDownLatch(1);
            var rejectPool = Executors.newFixedThreadPool(2);
            try {
                var change = rejectPool.submit(() -> tx.executeWithoutResult(status -> {
                    sql.queryForObject("select status from td_reports where id=? for update", String.class, rejected);
                    rejectLocked.countDown(); await(commitReject);
                    sql.update("update td_reports set version=5 where id=?", rejected);
                }));
                assertTrue(rejectLocked.await(5, TimeUnit.SECONDS));
                var blocked = rejectPool.submit(() -> store.propose(developer, project, rejected, VALID, 4, UUID.randomUUID(), NOW));
                assertTrue(awaitLock(sql, "from td_reports")); commitReject.countDown(); change.get(5, TimeUnit.SECONDS);
                var cause = assertThrows(ExecutionException.class, () -> blocked.get(5, TimeUnit.SECONDS)).getCause();
                assertEquals("STALE_VERSION", ((ReportService.Failure) cause).code());
                assertEquals(0, count(sql, rejected));
            } finally { commitReject.countDown(); rejectPool.shutdownNow(); }

            // A committed membership revocation wins over a proposal blocked on the membership row.
            UUID revoked = report(sql, project, reporter, developer, "IN_PROGRESS", 2);
            CountDownLatch membershipLocked = new CountDownLatch(1), commitRevocation = new CountDownLatch(1);
            var revokePool = Executors.newFixedThreadPool(2);
            try {
                var change = revokePool.submit(() -> tx.executeWithoutResult(status -> {
                    sql.queryForObject("select state from td_project_memberships where project_id=? and user_id=? for update", String.class, project, developer);
                    membershipLocked.countDown(); await(commitRevocation);
                    sql.update("update td_project_memberships set state='DISABLED' where project_id=? and user_id=?", project, developer);
                }));
                assertTrue(membershipLocked.await(5, TimeUnit.SECONDS));
                var blocked = revokePool.submit(() -> store.propose(developer, project, revoked, VALID, 2, UUID.randomUUID(), NOW));
                assertTrue(awaitLock(sql, "for share of p,u,m")); commitRevocation.countDown(); change.get(5, TimeUnit.SECONDS);
                var cause = assertThrows(ExecutionException.class, () -> blocked.get(5, TimeUnit.SECONDS)).getCause();
                assertEquals("NOT_FOUND", ((ReportService.Failure) cause).code());
                assertEquals(0, count(sql, revoked));
            } finally { commitRevocation.countDown(); revokePool.shutdownNow(); }
            sql.update("update td_project_memberships set state='ACTIVE' where project_id=? and user_id=?", project, developer);

            // An audit failure rolls the revision and the transition back together.
            UUID rollback = report(sql, project, reporter, developer, "IN_PROGRESS", 2);
            sql.execute("create function fail_resolution_audit() returns trigger language plpgsql as $$ begin raise exception 'synthetic audit failure'; end $$");
            sql.execute("create trigger fail_resolution_audit before insert on td_business_audit_events for each row execute function fail_resolution_audit()");
            assertThrows(DataAccessException.class, () -> store.propose(developer, project, rollback, VALID, 2, UUID.randomUUID(), NOW));
            assertEquals(0, count(sql, rollback)); assertEquals("IN_PROGRESS", status(sql, rollback)); assertEquals(2, version(sql, rollback));
        }
    }

    private static com.tracedesk.auth.investigation.application.InvestigationRepository.Proposal proposeAfterGate(
            InvestigationStore store, UUID actor, UUID project, UUID report, CountDownLatch ready, CountDownLatch go) throws Exception {
        ready.countDown(); if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("gate timeout");
        return store.propose(actor, project, report, VALID, 2, UUID.randomUUID(), NOW);
    }
    private static ReportService.Failure failure(org.junit.jupiter.api.function.Executable executable) {
        return assertThrows(ReportService.Failure.class, executable);
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("gate timeout"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
    }
    private static boolean awaitLock(JdbcTemplate sql, String fragment) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Integer waiting = sql.queryForObject("select count(*) from pg_stat_activity where pid<>pg_backend_pid() and wait_event_type='Lock' and lower(query) like ?", Integer.class, "%" + fragment.toLowerCase() + "%");
            if (waiting != null && waiting > 0) return true;
            Thread.sleep(20);
        }
        return false;
    }
    private static int count(JdbcTemplate sql, UUID report) {
        return sql.queryForObject("select count(*) from td_report_resolutions where report_id=?", Integer.class, report);
    }
    private static int audits(JdbcTemplate sql, UUID report) {
        return sql.queryForObject("select count(*) from td_business_audit_events where report_id=? and event_code='REPORT_RESOLUTION_PROPOSED'", Integer.class, report);
    }
    private static String status(JdbcTemplate sql, UUID report) {
        return sql.queryForObject("select status from td_reports where id=?", String.class, report);
    }
    private static int version(JdbcTemplate sql, UUID report) {
        return sql.queryForObject("select version from td_reports where id=?", Integer.class, report);
    }
    private static UUID user(JdbcTemplate sql, String login) {
        UUID id = UUID.randomUUID();
        sql.update("insert into td_users(id,login_normalized,password_hash) values(?,?,?)", id, login, "$argon2id$v=19$m=19456,t=2,p=1$synthetic$hash");
        return id;
    }
    private static void membership(JdbcTemplate sql, UUID project, UUID user, String role) {
        sql.update("insert into td_project_memberships(project_id,user_id,role,state) values(?,?,?,'ACTIVE')", project, user, role);
    }
    private static UUID report(JdbcTemplate sql, UUID project, UUID reporter, UUID developer, String status, int version) {
        UUID id = UUID.randomUUID(); ReportData data = new ReportData("title", "description", "environment", "steps", "expected", "actual", "");
        java.sql.Timestamp created = java.sql.Timestamp.from(NOW.minusSeconds(60));
        sql.update("""
                insert into td_reports(id,project_id,reporter_user_id,title,description,environment,reproduction_steps,
                  expected_behavior,actual_behavior,text_logs,status,assigned_developer_id,created_at,updated_at,version)
                values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, project, reporter, data.title(), data.description(), data.environment(), data.reproductionSteps(),
                data.expectedBehavior(), data.actualBehavior(), data.textLogs(), status, developer, created, created, version);
        return id;
    }
    private static void rawText(JdbcTemplate sql, UUID project, UUID report, UUID author, String change, String verification) {
        sql.update("""
                insert into td_report_resolutions(id,project_id,report_id,author_user_id,revision,root_cause,change_summary,verification_steps,created_at)
                values(?,?,?,?,1,'root',?,?,now())
                """, UUID.randomUUID(), project, report, author, change, verification);
    }
    private static void raw(JdbcTemplate sql, UUID project, UUID report, UUID author, int revision, String root, String reference) {
        sql.update("""
                insert into td_report_resolutions(id,project_id,report_id,author_user_id,revision,root_cause,change_summary,verification_steps,fix_reference,created_at)
                values(?,?,?,?,?,?,'change','verify',?,now())
                """, UUID.randomUUID(), project, report, author, revision, root, reference);
    }
}
