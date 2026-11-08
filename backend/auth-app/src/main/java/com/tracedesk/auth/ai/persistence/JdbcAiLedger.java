package com.tracedesk.auth.ai.persistence;

import com.tracedesk.ai.gateway.GatewayFailure;
import com.tracedesk.ai.gateway.GatewayPorts;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.TransactionDefinition;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import static com.tracedesk.ai.gateway.GatewayFailure.Code.*;
import static com.tracedesk.ai.gateway.GatewayTypes.*;

/** PostgreSQL owns cross-process admission. No transaction spans a provider call. */
public final class JdbcAiLedger implements GatewayPorts.Ledger {
    private static final UUID GLOBAL = new UUID(0, 0);
    private final JdbcTemplate sql;
    private final TransactionTemplate transactions;

    public JdbcAiLedger(JdbcTemplate sql) {
        this.sql = Objects.requireNonNull(sql);
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(sql.getDataSource())));
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** A provider call inside a business transaction could couple unrelated commits or retain locks. */
    @Override public void checkInvocationBoundary() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new GatewayFailure(AI_UNAVAILABLE);
    }

    /** Publish revocation/high-water marks independently of a later denied admission. */
    @Override public void configure(Policy policy, Metadata metadata, Instant now) {
        safe(() -> {
            validatePolicyShape(policy);
            if (now == null) throw new GatewayFailure(INVALID_REQUEST);
            if (policy.enabled() || metadata != null) validateMetadata(metadata, now);
            transactions.executeWithoutResult(tx -> {
                Control c = lock();
                fence(policy, metadata, c);
                if (metadata == null) {
                    sql.update("update td_ai_control set policy_revision=?,policy_fingerprint=? where singleton=true",policy.revision(),fingerprint(policy));
                } else {
                    sql.update("update td_ai_control set policy_revision=?,policy_fingerprint=?,metadata_revision=?,funding_revision=?,model=?,metadata_valid_until=? where singleton=true",
                            policy.revision(),fingerprint(policy),metadata.revision(),metadata.fundingRevision(),metadata.model(),metadata.validUntil().toString());
                }
            });
            return null;
        });
    }

    @Override public Ticket reserve(Actor actor, Request request, Policy policy, Metadata metadata, long units, Instant now) {
        return safe(() -> {
            configure(policy, metadata, now);
            validatePolicy(policy); validateMetadata(metadata, now);
            if (actor == null || actor.id() == null || GLOBAL.equals(actor.id()) || request == null
                    || request.projectId() == null || request.sourceId() == null || request.capability() == null
                    || request.expectedSourceVersion() < 0 || units < 1 || units > 9200)
                throw new GatewayFailure(INVALID_REQUEST);
            return transactions.execute(tx -> {
                Control control = lock();
                fence(policy, metadata, control);
                boolean probe = control.openUntil != null;
                if (probe && (control.openUntil.isAfter(now) || control.probeActive)) throw new GatewayFailure(AI_CIRCUIT_OPEN);
                Long active = sql.queryForObject("select count(*) from td_ai_tickets where state<>'TERMINAL'", Long.class);
                Long actorActive = sql.queryForObject("select count(*) from td_ai_tickets where state<>'TERMINAL' and actor_id=?", Long.class, actor.id());
                if (active >= policy.globalConcurrency() || actorActive >= policy.actorConcurrency()) throw new GatewayFailure(AI_CAPACITY_EXCEEDED);
                Date day = Date.valueOf(now.atOffset(ZoneOffset.UTC).toLocalDate());
                admit(day, GLOBAL, units, policy.globalDailyUnits());
                admit(day, actor.id(), units, policy.actorDailyUnits());
                UUID id = UUID.randomUUID(); Instant expires = now.plus(policy.reservationTtl()).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
                sql.update("insert into td_ai_tickets(id,actor_id,project_id,capability,window_day,model,metadata_revision,funding_revision,policy_revision,circuit_revision,probe,reserved_units,state,created_at,expires_at) values(?,?,?,?,?,?,?,?,?,?,?,?, 'RESERVED',?,?)",
                        id, actor.id(), request.projectId(), request.capability().name(), day, metadata.model(), metadata.revision(), metadata.fundingRevision(), policy.revision(), control.revision, probe, units, ts(now), ts(expires));
                sql.update("update td_ai_control set probe_active=? where singleton=true", probe || control.probeActive);
                event(id, 1, "RESERVED", now, 0);
                return new Ticket(id, 1, control.revision, units, expires);
            });
        });
    }

    @Override public Ticket dispatch(Ticket ticket, Policy policy, Metadata metadata, Instant now) {
        return safe(() -> {
            validateTicket(ticket); configure(policy, metadata, now); validatePolicy(policy); validateMetadata(metadata, now);
            return transactions.execute(tx -> {
                Control control = lock(); Row row = row(ticket.id()); matching(ticket, row);
                fence(policy, metadata, control);
                if (!row.state.equals("RESERVED") || !row.expires.isAfter(now)
                        || row.policy != policy.revision() || row.metadata != metadata.revision()
                        || row.funding != metadata.fundingRevision() || !row.model.equals(metadata.model())
                        || control.revision != row.circuit || (control.openUntil != null && !row.probe))
                    throw new GatewayFailure(STALE_TICKET);
                sql.update("update td_ai_tickets set state='DISPATCHED',revision=2 where id=?", row.id);
                event(row.id, 2, "DISPATCHED", now, 0);
                return new Ticket(row.id, 2, row.circuit, row.units, row.expires);
            });
        });
    }

    @Override public void settle(Ticket ticket, Outcome outcome, Long actualUnits, Instant now) {
        safe(() -> {
            validateTicket(ticket);
            if (outcome == null || now == null) throw new GatewayFailure(INVALID_REQUEST);
            transactions.executeWithoutResult(tx -> {
                Control control = lock(); Row row = row(ticket.id());
                if (row.state.equals("TERMINAL")) {
                    // Replay must carry the original pre-terminal revision and all immutable ticket fields.
                    matchingFields(ticket, row);
                    long expected = row.revision - 1;
                    if (ticket.revision() != expected || !outcome.name().equals(row.outcome) || !Objects.equals(actualUnits, row.actual)) throw new GatewayFailure(STALE_TICKET);
                    return;
                }
                matching(ticket, row);
                if (!row.expires.isAfter(now)) throw new GatewayFailure(STALE_TICKET);
                if (row.state.equals("RESERVED") && outcome != Outcome.CANCELLED) throw new GatewayFailure(STALE_TICKET);
                long charge = row.state.equals("RESERVED") ? 0 : outcome == Outcome.SUCCESS && actualUnits != null && actualUnits >= 0 && actualUnits <= row.units ? actualUnits : row.units;
                finish(row, outcome.name(), actualUnits, charge, now);
                circuit(row, control, outcome, now);
            });
            return null;
        });
    }

    @Override public int expire(Instant now, int limit) {
        return safe(() -> {
            if (now == null || limit < 1 || limit > 1000) throw new GatewayFailure(INVALID_REQUEST);
            return transactions.execute(tx -> {
                Control control = lock();
                List<UUID> ids = sql.query("select id from td_ai_tickets where state<>'TERMINAL' and expires_at<=? order by expires_at,id limit ?", (rs,n) -> rs.getObject(1,UUID.class), ts(now), limit);
                for (UUID id : ids) {
                    Row row = row(id);
                    finish(row, "EXPIRED", null, row.state.equals("RESERVED") ? 0 : row.units, now);
                    if (row.probe && row.circuit == control.revision) sql.update("update td_ai_control set probe_active=false where singleton=true");
                }
                return ids.size();
            });
        });
    }

    private void admit(Date day, UUID scope, long units, long budget) {
        sql.update("insert into td_ai_daily_units(window_day,scope_id) values(?,?) on conflict do nothing", day, scope);
        int changed = sql.update("update td_ai_daily_units set reserved_units=reserved_units+? where window_day=? and scope_id=? and charged_units+reserved_units<=?-?", units, day, scope, budget, units);
        if (changed != 1) throw new GatewayFailure(AI_QUOTA_EXCEEDED);
    }

    private void finish(Row row, String outcome, Long actual, long charge, Instant now) {
        sql.update("update td_ai_daily_units set reserved_units=reserved_units-?,charged_units=charged_units+? where window_day=? and scope_id in (?,?)", row.units, charge, row.day, GLOBAL, row.actor);
        sql.update("update td_ai_tickets set state='TERMINAL',revision=revision+1,outcome=?,actual_units=?,charged_units=?,settled_at=? where id=?", outcome, actual, charge, ts(now), row.id);
        event(row.id, row.revision+1, outcome, now, charge);
    }

    private void circuit(Row row, Control c, Outcome outcome, Instant now) {
        if (row.circuit != c.revision || row.state.equals("RESERVED")) {
            if (row.probe && row.circuit == c.revision) sql.update("update td_ai_control set probe_active=false where singleton=true");
            return;
        }
        if (outcome == Outcome.FAILURE) {
            int failures = Math.min(3, c.failures+1);
            if (row.probe || failures == 3) sql.update("update td_ai_control set failures=3,open_until=?,probe_active=false,circuit_revision=circuit_revision+1 where singleton=true", ts(now.plusSeconds(30)));
            else sql.update("update td_ai_control set failures=? where singleton=true", failures);
        } else if (outcome == Outcome.SUCCESS) {
            sql.update("update td_ai_control set failures=0,open_until=null,probe_active=false where singleton=true");
        } else if (row.probe) sql.update("update td_ai_control set probe_active=false where singleton=true");
    }

    private Control lock() {
        return sql.queryForObject("select * from td_ai_control where singleton=true for update", (rs,n) -> new Control(rs.getLong("policy_revision"),rs.getLong("metadata_revision"),rs.getLong("funding_revision"),rs.getLong("circuit_revision"),rs.getInt("failures"),rs.getTimestamp("open_until")==null?null:rs.getTimestamp("open_until").toInstant(),rs.getBoolean("probe_active"),rs.getString("policy_fingerprint"),rs.getString("model"),rs.getString("metadata_valid_until")));
    }
    private static String fingerprint(Policy p) {
        return p.enabled()+":"+p.globalDailyUnits()+":"+p.actorDailyUnits()+":"+p.globalConcurrency()+":"+p.actorConcurrency()+":"+p.reservationTtl().toNanos();
    }
    private void fence(Policy p, Metadata m, Control c) {
        if (p.revision()<c.policy || (p.revision()==c.policy && !fingerprint(p).equals(c.fingerprint))) throw new GatewayFailure(AI_UNAVAILABLE);
        if (m != null && (m.revision()<c.metadata || m.fundingRevision()<c.funding
                || (m.revision()==c.metadata && (!m.model().equals(c.model) || m.fundingRevision()!=c.funding
                    || !m.validUntil().toString().equals(c.metadataValidUntil))))) throw new GatewayFailure(AI_UNAVAILABLE);
    }
    private Row row(UUID id) {
        List<Row> rows=sql.query("select * from td_ai_tickets where id=?", (rs,n) -> new Row(rs.getObject("id",UUID.class),rs.getObject("actor_id",UUID.class),rs.getDate("window_day"),rs.getString("model"),rs.getLong("policy_revision"),rs.getLong("metadata_revision"),rs.getLong("funding_revision"),rs.getLong("circuit_revision"),rs.getBoolean("probe"),rs.getLong("reserved_units"),rs.getLong("revision"),rs.getString("state"),rs.getTimestamp("expires_at").toInstant(),rs.getString("outcome"),(Long)rs.getObject("actual_units")),id);
        if(rows.size()!=1) throw new GatewayFailure(STALE_TICKET);
        return rows.get(0);
    }
    private void matching(Ticket t, Row r) { matchingFields(t,r); if(t.revision()!=r.revision) throw new GatewayFailure(STALE_TICKET); }
    private void matchingFields(Ticket t, Row r) {
        if(t.circuitRevision()!=r.circuit || t.reservedUnits()!=r.units || !t.expiresAt().equals(r.expires)) throw new GatewayFailure(STALE_TICKET);
    }
    private void validateTicket(Ticket t) {
        if(t==null || t.id()==null || t.revision()<1 || t.revision()>2 || t.circuitRevision()<1 || t.reservedUnits()<1 || t.reservedUnits()>9200 || t.expiresAt()==null) throw new GatewayFailure(STALE_TICKET);
    }
    private void event(UUID id,long revision,String code,Instant now,long charge) { sql.update("insert into td_ai_accounting_events(ticket_id,revision,event_code,occurred_at,charged_units) values(?,?,?,?,?)",id,revision,code,ts(now),charge); }
    private static Timestamp ts(Instant value) { return Timestamp.from(value); }
    private <T> T safe(Supplier<T> work) {
        try { return work.get(); }
        catch(GatewayFailure failure) { throw failure; }
        catch(RuntimeException failure) { throw new GatewayFailure(AI_UNAVAILABLE); }
    }
    private record Control(long policy,long metadata,long funding,long revision,int failures,Instant openUntil,boolean probeActive,String fingerprint,String model,String metadataValidUntil) { }
    private record Row(UUID id,UUID actor,Date day,String model,long policy,long metadata,long funding,long circuit,boolean probe,long units,long revision,String state,Instant expires,String outcome,Long actual) { }
}
