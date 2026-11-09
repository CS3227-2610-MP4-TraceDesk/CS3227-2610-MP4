package com.tracedesk.auth.ai.persistence;

import com.tracedesk.ai.gateway.GatewayFailure;
import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.tracedesk.ai.gateway.GatewayFailure.Code.*;
import static com.tracedesk.ai.gateway.GatewayTypes.*;
import static org.junit.jupiter.api.Assertions.*;

/** Every test owns an isolated schema; absent isolation configuration is a failure. */
class JdbcAiLedgerIntegrationTest {
    private static final Instant NOW=Instant.parse("2026-11-09T23:59:50Z");
    private static final Policy POLICY=new Policy(true,1,1000,800,4,1,Duration.ofSeconds(60));
    private static final String APPROVAL="0".repeat(64);
    private static Metadata metadata(Instant now) { return new Metadata("synthetic-model",1,NOW.plusSeconds(900),1,APPROVAL,NOW.plusSeconds(1800)); }

    @Test void ambientRollbackCannotEraseAccountingAndFacadeBoundaryRejectsIt() throws Exception {
        try (Fixture f=new Fixture()) {
            TransactionTemplate outer=new TransactionTemplate(new DataSourceTransactionManager(f.db));
            f.ledger.checkInvocationBoundary();
            outer.executeWithoutResult(tx->{
                assertCode(AI_UNAVAILABLE,f.ledger::checkInvocationBoundary);
                Ticket ticket=f.ledger.dispatch(f.reserve(f.actor,100,NOW),POLICY,metadata(NOW),NOW);
                f.ledger.settle(ticket,Outcome.SUCCESS,50L,NOW);
                tx.setRollbackOnly();
            });
            assertEquals(1,f.count("td_ai_tickets"));assertEquals(3,f.count("td_ai_accounting_events"));
            assertEquals(50,f.total("charged_units"));assertEquals(0,f.total("reserved_units"));
            assertEquals("TERMINAL",f.sql.queryForObject("select state from td_ai_tickets",String.class));
        }
    }

    @Test void disabledPublicationAndDeniedLowerBudgetAdmissionPermanentlyFenceOlderWorkers() throws Exception {
        try (Fixture f=new Fixture()) {
            Ticket old=f.reserve(f.actor,100,NOW);
            Policy disabled=new Policy(false,2,0,0,4,1,Duration.ofSeconds(60));
            f.ledger.configure(disabled,null,NOW);
            assertCode(AI_UNAVAILABLE,()->f.reserve(f.other,10,NOW));
            assertCode(AI_UNAVAILABLE,()->f.ledger.dispatch(old,POLICY,metadata(NOW),NOW));
            assertCode(AI_DISABLED,()->f.ledger.reserve(f.other,f.request,disabled,null,10,NOW));
            assertEquals(2,f.sql.queryForObject("select policy_revision from td_ai_control",Long.class));
        }
        try (Fixture f=new Fixture()) {
            Ticket old=f.reserve(f.actor,100,NOW);
            Policy lower=new Policy(true,2,1,1,4,1,Duration.ofSeconds(60));
            assertCode(AI_QUOTA_EXCEEDED,()->f.ledger.reserve(f.other,f.request,lower,metadata(NOW),10,NOW));
            assertCode(AI_UNAVAILABLE,()->f.ledger.dispatch(old,POLICY,metadata(NOW),NOW));
            assertCode(AI_UNAVAILABLE,()->f.reserve(f.other,10,NOW));
            assertEquals(2,f.sql.queryForObject("select policy_revision from td_ai_control",Long.class));
            assertEquals(1,f.count("td_ai_tickets"));assertEquals(100,f.total("reserved_units"));
        }
    }

    @Test void repeatedDisabledPolicyPublicationDoesNotWriteOrAdvanceMetadataRevision() throws Exception {
        try (Fixture f=new Fixture()) {
            Policy disabled=new Policy(false,2,0,0,4,1,Duration.ofSeconds(60));
            f.ledger.configure(disabled,null,NOW);
            long afterDisable=f.sql.queryForObject("select metadata_revision from td_ai_control",Long.class);
            f.ledger.configure(disabled,null,NOW.plusSeconds(1));
            assertEquals(afterDisable,f.sql.queryForObject("select metadata_revision from td_ai_control",Long.class));
            Policy newerDisabled=new Policy(false,3,0,0,4,1,Duration.ofSeconds(60));
            f.ledger.configure(newerDisabled,null,NOW.plusSeconds(2));
            assertEquals(afterDisable+1,f.sql.queryForObject("select metadata_revision from td_ai_control",Long.class));
        }
    }

    @Test void newMetadataRevisionMustBeAllocatedBeforeCatalogBackedPublication() throws Exception {
        try (Fixture f=new Fixture()) {
            String approval="d".repeat(64);
            f.ledger.publishPolicy(POLICY,NOW);
            f.ledger.prepareEnablement(POLICY,"synthetic-model",1,1,approval,NOW.plusSeconds(1800),NOW);
            Metadata unallocated=new Metadata("synthetic-model",2,NOW.plusSeconds(900),1,approval,NOW.plusSeconds(1800));
            assertCode(AI_UNAVAILABLE,()->f.ledger.configure(POLICY,unallocated,NOW));
            assertFalse(f.sql.queryForObject("select metadata_available from td_ai_control",Boolean.class));
        }
    }

    @Test void newerEnabledPolicyWithNoMetadataFencesOldAdmissionBeforeCatalogRefresh() throws Exception {
        try (Fixture f=new Fixture()) {
            f.ledger.configure(POLICY,metadata(NOW),NOW);
            Policy newer=new Policy(true,2,1000,800,4,1,Duration.ofSeconds(60));
            f.ledger.publishPolicy(newer,NOW); // startup is fail-closed until a new catalog snapshot is published
            assertFalse(f.sql.queryForObject("select metadata_available from td_ai_control",Boolean.class));
            assertCode(AI_UNAVAILABLE,()->f.ledger.reserve(f.other,f.request,POLICY,metadata(NOW),10,NOW));
        }
    }

    @Test void sponsorshipAndModelRevisionsAreImmutableAndSharedSnapshotsAreAdopted() throws Exception {
        try (Fixture f=new Fixture()) {
            String approval="a".repeat(64);
            f.ledger.publishPolicy(POLICY,NOW);
            f.ledger.prepareEnablement(POLICY,"synthetic-model",1,1,approval,NOW.plusSeconds(1800),NOW);
            assertCode(AI_UNAVAILABLE,()->f.ledger.configure(POLICY,
                    new Metadata("synthetic-model",2,NOW.plusSeconds(900),1,approval,NOW.plusSeconds(1800)),NOW));
            long rev=f.ledger.nextMetadataRevision(POLICY,"synthetic-model",1,1,approval,NOW.plusSeconds(1800),NOW);
            Metadata published=new Metadata("synthetic-model",rev,NOW.plusSeconds(900),1,approval,NOW.plusSeconds(1800));
            f.ledger.configure(POLICY,published,NOW);
            assertEquals(published,f.ledger.readCurrentMetadata(POLICY,"synthetic-model",1,1,approval,NOW.plusSeconds(1800),NOW));
            assertCode(AI_UNAVAILABLE,()->f.ledger.prepareEnablement(POLICY,"other-model",1,1,approval,NOW.plusSeconds(1800),NOW));
            assertCode(AI_UNAVAILABLE,()->f.ledger.prepareEnablement(POLICY,"synthetic-model",1,1,"b".repeat(64),NOW.plusSeconds(1800),NOW));
            assertCode(AI_UNAVAILABLE,()->f.ledger.nextMetadataRevision(new Policy(true,0,1000,800,4,1,Duration.ofSeconds(60)),"synthetic-model",1,1,approval,NOW.plusSeconds(1800),NOW));
            assertEquals(published,f.ledger.readCurrentMetadata(POLICY,"synthetic-model",1,1,approval,NOW.plusSeconds(1800),NOW));
        }
    }

    @Test void staleRefreshCannotInvalidateNewerPublishedProfileAndSharedReadersAdoptIt() throws Exception {
        try (Fixture f=new Fixture()) {
            String approval="c".repeat(64); Policy active=new Policy(true,2,1000,800,4,1,Duration.ofSeconds(60));
            f.ledger.publishPolicy(active,NOW);
            f.ledger.prepareEnablement(active,"model-v2",2,3,approval,NOW.plusSeconds(1800),NOW);
            long rev=f.ledger.nextMetadataRevision(active,"model-v2",2,3,approval,NOW.plusSeconds(1800),NOW);
            Metadata published=new Metadata("model-v2",rev,NOW.plusSeconds(900),3,approval,NOW.plusSeconds(1800));
            f.ledger.configure(active,published,NOW);
            assertEquals(published,new JdbcAiLedger(f.sql).readCurrentMetadata(active,"model-v2",2,3,approval,NOW.plusSeconds(1800),NOW));
            Policy stale=new Policy(true,1,1000,800,4,1,Duration.ofSeconds(60));
            assertCode(AI_UNAVAILABLE,()->new JdbcAiLedger(f.sql).nextMetadataRevision(stale,"model-v2",2,3,approval,NOW.plusSeconds(1800),NOW));
            assertEquals(published,f.ledger.readCurrentMetadata(active,"model-v2",2,3,approval,NOW.plusSeconds(1800),NOW));
            Policy disabled=new Policy(false,3,0,0,4,1,Duration.ofSeconds(60));
            f.ledger.publishPolicy(disabled,NOW);
            assertNull(f.ledger.readCurrentMetadata(active,"model-v2",2,3,approval,NOW.plusSeconds(1800),NOW));
        }
    }

    @Test void metadataValidityCannotBeExtendedOrShortenedWithinTheSameRevision() throws Exception {
        try (Fixture f=new Fixture()) {
            f.ledger.configure(POLICY,metadata(NOW),NOW);
            for (long delta:new long[]{-1,1}) {
                Metadata changed=new Metadata("synthetic-model",1,NOW.plusSeconds(900+delta),1,APPROVAL,NOW.plusSeconds(1800));
                Instant publication=NOW.plusSeconds(1);
                assertCode(AI_UNAVAILABLE,()->f.ledger.configure(POLICY,changed,publication));
            }
            f.ledger.configure(POLICY,new Metadata("synthetic-model",2,NOW.plusSeconds(901),1,APPROVAL,NOW.plusSeconds(1800)),NOW.plusSeconds(1));
            assertCode(AI_UNAVAILABLE,()->f.ledger.configure(POLICY,metadata(NOW),NOW.plusSeconds(1)));
        }
    }

    @Test void settlementReplayForgedTicketsAndUtcDailyCounters() throws Exception {
        try (Fixture f=new Fixture()) {
            Ticket reserved=f.reserve(f.actor,400,NOW);
            assertEquals(400,f.total("reserved_units"));
            assertCode(STALE_TICKET,()->f.ledger.dispatch(new Ticket(reserved.id(),1,1,399,reserved.expiresAt()),POLICY,metadata(NOW),NOW));
            Ticket dispatched=f.ledger.dispatch(reserved,POLICY,metadata(NOW),NOW);
            assertCode(STALE_TICKET,()->f.ledger.dispatch(reserved,POLICY,metadata(NOW),NOW));
            f.ledger.settle(dispatched,Outcome.SUCCESS,100L,NOW.plusSeconds(20));
            f.ledger.settle(dispatched,Outcome.SUCCESS,100L,NOW.plusSeconds(21));
            assertCode(STALE_TICKET,()->f.ledger.settle(dispatched,Outcome.SUCCESS,101L,NOW.plusSeconds(21)));
            assertEquals(0,f.total("reserved_units")); assertEquals(100,f.total("charged_units"));
            assertEquals(3,f.count("td_ai_accounting_events"));
            assertEquals("2026-11-09",f.sql.queryForObject("select window_day::text from td_ai_tickets where id=?",String.class,reserved.id()));
            Ticket midnight=f.reserve(f.actor,800,NOW.plusSeconds(20));
            assertEquals(800,f.sql.queryForObject("select reserved_units from td_ai_daily_units where scope_id=? and window_day='2026-11-10'",Long.class,f.actor.id()));
            f.ledger.settle(midnight,Outcome.CANCELLED,null,NOW.plusSeconds(21));
            assertEquals(0,f.total("reserved_units"));
        }
    }

    @Test void quotaCapacityAndMidnightActiveCapacityAreShared() throws Exception {
        try (Fixture f=new Fixture()) {
            Ticket first=f.reserve(f.actor,700,NOW);
            assertCode(AI_CAPACITY_EXCEEDED,()->f.reserve(f.actor,1,NOW.plusSeconds(20)));
            Ticket second=f.reserve(f.other,300,NOW);
            assertCode(AI_QUOTA_EXCEEDED,()->f.reserve(f.user(),1,NOW));
            f.ledger.settle(first,Outcome.CANCELLED,null,NOW);
            f.ledger.settle(second,Outcome.CANCELLED,null,NOW);
            Ticket dispatched=f.ledger.dispatch(f.reserve(f.actor,800,NOW),POLICY,metadata(NOW),NOW);
            f.ledger.settle(dispatched,Outcome.UNKNOWN,null,NOW);
            assertCode(AI_QUOTA_EXCEEDED,()->f.reserve(f.actor,1,NOW));
            assertEquals(800,f.total("charged_units"));
            assertNotNull(f.reserve(f.actor,800,NOW.plusSeconds(20)));
        }
    }

    @Test void globalCapacityIsIndependentOfDailyWindowAndBudget() throws Exception {
        try (Fixture f=new Fixture()) {
            Policy single=new Policy(true,1,1000,800,1,1,Duration.ofSeconds(60));
            f.ledger.reserve(f.actor,f.request,single,metadata(NOW),10,NOW);
            assertCode(AI_CAPACITY_EXCEEDED,()->new JdbcAiLedger(f.sql).reserve(f.other,f.request,single,metadata(NOW.plusSeconds(20)),10,NOW.plusSeconds(20)));
            assertEquals(1,f.count("td_ai_tickets"));
        }
    }

    @Test void concurrentInstancesCannotOversubscribeGlobalOrActorBudget() throws Exception {
        try (Fixture f=new Fixture()) {
            Actor third=f.user();
            CountDownLatch ready=new CountDownLatch(3),go=new CountDownLatch(1);
            var pool=Executors.newFixedThreadPool(3);
            try {
                var futures=java.util.stream.Stream.of(f.actor,f.other,third).map(actor->pool.submit(()->{
                    ready.countDown(); assertTrue(go.await(5,TimeUnit.SECONDS));
                    try { return new JdbcAiLedger(f.sql).reserve(actor,f.request,POLICY,metadata(NOW),600,NOW); }
                    catch(GatewayFailure failure) { assertEquals(AI_QUOTA_EXCEEDED,failure.code()); return null; }
                })).toList();
                assertTrue(ready.await(5,TimeUnit.SECONDS)); go.countDown();
                int wins=0; for(var future:futures) if(future.get(10,TimeUnit.SECONDS)!=null) wins++;
                assertEquals(1,wins); assertEquals(600,f.total("reserved_units"));
            } finally { go.countDown();pool.shutdownNow(); }
            f.ledger.expire(NOW.plusSeconds(60),100);
            CountDownLatch gate=new CountDownLatch(1);
            var actors=Executors.newFixedThreadPool(2);
            try {
                var a=actors.submit(()->actorRace(f,gate)); var b=actors.submit(()->actorRace(f,gate)); gate.countDown();
                assertEquals(1,a.get(10,TimeUnit.SECONDS)+b.get(10,TimeUnit.SECONDS));
            } finally { gate.countDown();actors.shutdownNow(); }
        }
    }

    @Test void conservativeUsageAndExpiryReleaseExactlyOnceAndFenceLateWorkers() throws Exception {
        try (Fixture f=new Fixture()) {
            Ticket reserved=f.reserve(f.actor,100,NOW);
            Ticket dispatched=f.ledger.dispatch(f.reserve(f.other,200,NOW),POLICY,metadata(NOW),NOW);
            assertEquals(1,f.ledger.expire(NOW.plusSeconds(60),1));
            assertEquals(1,f.ledger.expire(NOW.plusSeconds(60),1));
            assertEquals(0,f.ledger.expire(NOW.plusSeconds(60),1));
            assertEquals(0,f.total("reserved_units")); assertEquals(200,f.total("charged_units"));
            assertCode(STALE_TICKET,()->f.ledger.dispatch(reserved,POLICY,metadata(NOW),NOW));
            assertCode(STALE_TICKET,()->f.ledger.settle(dispatched,Outcome.SUCCESS,0L,NOW.plusSeconds(61)));
            Instant later=NOW.plusSeconds(61);
            for(Long usage:new Long[]{null,-1L,101L}) {
                Ticket ticket=f.ledger.dispatch(f.reserve(f.actor,100,later),POLICY,metadata(later),later);
                f.ledger.settle(ticket,Outcome.SUCCESS,usage,later);
            }
            assertEquals(500,f.total("charged_units"));
        }
    }

    @Test void circuitAllowsOneProbeAndIgnoresOldGenerationSuccess() throws Exception {
        try (Fixture f=new Fixture()) {
            Actor oldActor=f.user(); Ticket old=f.ledger.dispatch(f.reserve(oldActor,10,NOW),POLICY,metadata(NOW),NOW);
            for(int i=0;i<3;i++) {
                Ticket ticket=f.ledger.dispatch(f.reserve(f.actor,10,NOW),POLICY,metadata(NOW),NOW);
                f.ledger.settle(ticket,Outcome.FAILURE,null,NOW);
            }
            f.ledger.settle(old,Outcome.SUCCESS,1L,NOW.plusSeconds(1));
            assertCode(AI_CIRCUIT_OPEN,()->f.reserve(f.actor,10,NOW.plusSeconds(1)));
            Instant probeTime=NOW.plusSeconds(30);
            Ticket probe=f.reserve(f.actor,10,probeTime);
            assertCode(AI_CIRCUIT_OPEN,()->f.reserve(f.other,10,probeTime));
            Ticket probing=f.ledger.dispatch(probe,POLICY,metadata(probeTime),probeTime);
            f.ledger.settle(probing,Outcome.FAILURE,null,probeTime);
            assertCode(AI_CIRCUIT_OPEN,()->f.reserve(f.other,10,probeTime.plusSeconds(1)));
            Instant recovered=probeTime.plusSeconds(30);
            Ticket success=f.ledger.dispatch(f.reserve(f.other,10,recovered),POLICY,metadata(recovered),recovered);
            f.ledger.settle(success,Outcome.SUCCESS,1L,recovered);
            assertNotNull(f.reserve(f.actor,10,recovered));
            assertEquals(0,f.sql.queryForObject("select failures from td_ai_control",Integer.class));
        }
    }

    @Test void cooldownProbeAdmissionIsAtomicAcrossInstancesAndRecoversAfterExpiry() throws Exception {
        try (Fixture f=new Fixture()) {
            for(int i=0;i<3;i++) {
                Ticket t=f.ledger.dispatch(f.reserve(f.actor,10,NOW),POLICY,metadata(NOW),NOW);
                f.ledger.settle(t,Outcome.FAILURE,null,NOW);
            }
            Instant after=NOW.plusSeconds(30);
            CountDownLatch gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
            try {
                var a=pool.submit(()->probeRace(f,f.actor,gate,after));
                var b=pool.submit(()->probeRace(f,f.other,gate,after));
                gate.countDown();assertEquals(1,a.get(10,TimeUnit.SECONDS)+b.get(10,TimeUnit.SECONDS));
            } finally {gate.countDown();pool.shutdownNow();}
            assertEquals(1,f.ledger.expire(after.plusSeconds(60),100));
            assertNotNull(f.reserve(f.actor,10,after.plusSeconds(60)));
        }
    }

    @Test void policyMetadataFencesAndEventFailuresAreAtomicAndSanitized() throws Exception {
        try (Fixture f=new Fixture()) {
            Ticket old=f.reserve(f.actor,10,NOW);
            Policy newer=new Policy(true,2,1000,800,4,1,Duration.ofSeconds(60));
            Metadata fresh=new Metadata("synthetic-v2",2,NOW.plusSeconds(900),2);
            Ticket ticket=f.ledger.reserve(f.other,f.request,newer,fresh,10,NOW);
            assertCode(AI_UNAVAILABLE,()->f.ledger.dispatch(old,POLICY,metadata(NOW),NOW));
            assertCode(AI_UNAVAILABLE,()->f.ledger.reserve(f.user(),f.request,newer,new Metadata("changed",2,NOW.plusSeconds(900),2),10,NOW));
            assertCode(AI_UNAVAILABLE,()->f.ledger.reserve(f.user(),f.request,new Policy(true,2,999,800,4,1,Duration.ofSeconds(60)),fresh,10,NOW));
            f.sql.execute("create function fail_ai_event() returns trigger language plpgsql as $$ begin raise exception 'synthetic secret payload'; end $$");
            f.sql.execute("create trigger fail_ai_event before insert on td_ai_accounting_events for each row execute function fail_ai_event()");
            long tickets=f.count("td_ai_tickets"), events=f.count("td_ai_accounting_events"), units=f.total("reserved_units");
            GatewayFailure failure=assertThrows(GatewayFailure.class,()->f.ledger.reserve(f.user(),f.request,newer,fresh,10,NOW));
            assertEquals("AI_UNAVAILABLE",failure.getMessage());assertNull(failure.getCause());
            assertEquals(tickets,f.count("td_ai_tickets"));assertEquals(events,f.count("td_ai_accounting_events"));assertEquals(units,f.total("reserved_units"));
            assertCode(AI_UNAVAILABLE,()->f.ledger.dispatch(ticket,newer,fresh,NOW));
            assertEquals("RESERVED",f.sql.queryForObject("select state from td_ai_tickets where id=?",String.class,ticket.id()));
            assertCode(AI_UNAVAILABLE,()->f.ledger.settle(ticket,Outcome.CANCELLED,null,NOW));
            assertEquals(units,f.total("reserved_units"));
            assertCode(AI_UNAVAILABLE,()->f.ledger.expire(NOW.plusSeconds(60),100));
            assertEquals(units,f.total("reserved_units"));
            f.sql.execute("drop trigger fail_ai_event on td_ai_accounting_events");
            assertEquals(2,f.ledger.expire(NOW.plusSeconds(60),100));
        }
    }

    @Test void dispatchedSettlementEventFailureRollsBackChargeCapacityAndCircuit() throws Exception {
        try (Fixture f=new Fixture()) {
            for(int i=0;i<2;i++) {
                Ticket t=f.ledger.dispatch(f.reserve(f.actor,10,NOW),POLICY,metadata(NOW),NOW);
                f.ledger.settle(t,Outcome.FAILURE,null,NOW);
            }
            Ticket pending=f.ledger.dispatch(f.reserve(f.actor,10,NOW),POLICY,metadata(NOW),NOW);
            long events=f.count("td_ai_accounting_events");
            f.sql.execute("create function fail_ai_event() returns trigger language plpgsql as $$ begin raise exception 'synthetic secret payload'; end $$");
            f.sql.execute("create trigger fail_ai_event before insert on td_ai_accounting_events for each row execute function fail_ai_event()");
            assertCode(AI_UNAVAILABLE,()->f.ledger.settle(pending,Outcome.FAILURE,null,NOW));
            assertEquals(20,f.total("charged_units"));assertEquals(10,f.total("reserved_units"));
            assertEquals(events,f.count("td_ai_accounting_events"));
            assertEquals("DISPATCHED",f.sql.queryForObject("select state from td_ai_tickets where id=?",String.class,pending.id()));
            assertEquals(2,f.sql.queryForObject("select failures from td_ai_control",Integer.class));
            assertEquals(1,f.sql.queryForObject("select circuit_revision from td_ai_control",Long.class));
            f.sql.execute("drop trigger fail_ai_event on td_ai_accounting_events");
            f.ledger.settle(pending,Outcome.FAILURE,null,NOW);
            assertEquals(30,f.total("charged_units"));assertEquals(0,f.total("reserved_units"));
            assertCode(AI_CIRCUIT_OPEN,()->f.reserve(f.other,10,NOW));
        }
    }

    @Test void concurrentExpirySettlementHasOneTerminalEffect() throws Exception {
        try (Fixture f=new Fixture()) {
            Ticket ticket=f.ledger.dispatch(f.reserve(f.actor,100,NOW),POLICY,metadata(NOW),NOW);
            var pool=Executors.newFixedThreadPool(2); CountDownLatch gate=new CountDownLatch(1);
            try {
                var settlement=pool.submit(()->{gate.await();try {f.ledger.settle(ticket,Outcome.SUCCESS,20L,NOW.plusSeconds(59));}catch(GatewayFailure denied){assertEquals(STALE_TICKET,denied.code());}return true;});
                var expiry=pool.submit(()->{gate.await();return new JdbcAiLedger(f.sql).expire(NOW.plusSeconds(60),100);});
                gate.countDown();assertTrue(settlement.get(10,TimeUnit.SECONDS));expiry.get(10,TimeUnit.SECONDS);
                assertEquals(0,f.total("reserved_units"));assertTrue(f.total("charged_units")==20 || f.total("charged_units")==100);
                assertEquals(3,f.count("td_ai_accounting_events"));
            } finally {gate.countDown();pool.shutdownNow();}
        }
    }

    private static int probeRace(Fixture f,Actor actor,CountDownLatch gate,Instant now) throws Exception {
        assertTrue(gate.await(5,TimeUnit.SECONDS));
        try {new JdbcAiLedger(f.sql).reserve(actor,f.request,POLICY,metadata(now),10,now);return 1;}
        catch(GatewayFailure failure) {assertEquals(AI_CIRCUIT_OPEN,failure.code());return 0;}
    }
    private static int actorRace(Fixture f,CountDownLatch gate) throws Exception {
        assertTrue(gate.await(5,TimeUnit.SECONDS));
        try {new JdbcAiLedger(f.sql).reserve(f.actor,f.request,POLICY,metadata(NOW.plusSeconds(60)),10,NOW.plusSeconds(60));return 1;}
        catch(GatewayFailure failure) {assertEquals(AI_CAPACITY_EXCEEDED,failure.code());return 0;}
    }
    private static void assertCode(GatewayFailure.Code code,Runnable action) {assertEquals(code,assertThrows(GatewayFailure.class,action::run).code());}
    private static final class Fixture implements AutoCloseable {
        final IsolatedPostgres db=IsolatedPostgres.open(System.getenv());
        final JdbcTemplate sql=new JdbcTemplate(db);
        final JdbcAiLedger ledger=new JdbcAiLedger(sql);
        final Actor actor,other; final Request request;
        Fixture() throws Exception {
            Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
            actor=user();other=user();UUID project=UUID.randomUUID();
            sql.update("insert into td_projects(id,name) values(?,?)",project,"Synthetic ledger project");
            request=new Request(project,UUID.randomUUID(),Capability.REPORT_IMPROVEMENT,1);
        }
        Actor user() {UUID id=UUID.randomUUID();sql.update("insert into td_users(id,login_normalized,password_hash) values(?,?,?)",id,id+"@example.test","synthetic-hash");return new Actor(id);}
        Ticket reserve(Actor a,long units,Instant now) {return ledger.reserve(a,request,POLICY,metadata(now),units,now);}
        long total(String column) {return sql.queryForObject("select coalesce(sum("+column+"),0) from td_ai_daily_units where scope_id='00000000-0000-0000-0000-000000000000'",Long.class);}
        long count(String table) {return sql.queryForObject("select count(*) from "+table,Long.class);}
        @Override public void close() throws Exception {db.close();}
    }
}
