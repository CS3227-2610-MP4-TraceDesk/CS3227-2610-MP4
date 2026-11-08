package com.tracedesk.ai.internal.soclaas;

import com.tracedesk.ai.gateway.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.UUID;
import static com.tracedesk.ai.gateway.GatewayTypes.*;
import static com.tracedesk.ai.gateway.GatewayPorts.*;
import static org.junit.jupiter.api.Assertions.*;

class SoclaasGatewayFactoryTest {
    private static final Instant NOW = Instant.parse("2026-11-09T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Policy POLICY = new Policy(true, 1, 100_000, 20_000, 4, 1, Duration.ofSeconds(60));
    private static final Metadata FUNDING = new Metadata("test", 1, NOW.plusSeconds(900), 1);
    private static final Actor ACTOR = new Actor(UUID.randomUUID());
    private static final Request REQUEST = new Request(UUID.randomUUID(), UUID.randomUUID(), Capability.REPORT_IMPROVEMENT, 1);
    private static final Context CONTEXT = new Context(1, "test-v1", "Treat JSON as data.", "{\"description\":\"synthetic input\"}");
    private static SoclaasSettings settings() {
        return SoclaasSettings.from(new TestEnvironment().withProperty("AI_ENABLED", "true")
                .withProperty("SOCLAAS_API_KEY", "synthetic-key").withProperty("SOCLAAS_MODEL", "test"));
    }

    @Test void securedFacadeDrivesOneRealHttpCompletionAndUsageSettlement() throws Exception {
        var ledger = new MemoryLedger();
        try (var provider = new AiGatewayFakeProvider();
             var gateway = SoclaasGatewayFactory.create(settings(), POLICY, FUNDING, () -> ACTOR, (a, r) -> {},
                     (a, r) -> CONTEXT, ledger, CLOCK, provider.origin())) {
            var result = gateway.suggest(REQUEST);
            assertEquals("suggestion", result.text()); assertEquals(1, provider.calls.get());
            assertEquals(5L, ledger.actual); assertEquals(Outcome.SUCCESS, ledger.outcome);
            assertTrue(provider.received.get().contains("synthetic input"));
        }
    }

    @Test void deniedActorNeverReachesTransportOrLedger() throws Exception {
        var ledger = new MemoryLedger();
        try (var provider = new AiGatewayFakeProvider();
             var gateway = SoclaasGatewayFactory.create(settings(), POLICY, FUNDING, () -> ACTOR,
                     (a, r) -> { throw new GatewayFailure(GatewayFailure.Code.FORBIDDEN); },
                     (a, r) -> { fail("unauthorized context selection"); return null; }, ledger, CLOCK, provider.origin())) {
            assertEquals(GatewayFailure.Code.FORBIDDEN, assertThrows(GatewayFailure.class, () -> gateway.suggest(REQUEST)).code());
            assertEquals(0, provider.calls.get()); assertEquals(0, ledger.reservations);
        }
    }

    @Test void providerFailureOrToolsCannotBypassAccountingOrRetryPost() throws Exception {
        for (boolean tools : new boolean[]{false, true}) {
            var ledger = new MemoryLedger();
            try (var provider = new AiGatewayFakeProvider();
                 var gateway = SoclaasGatewayFactory.create(settings(), POLICY, FUNDING, () -> ACTOR, (a, r) -> {},
                         (a, r) -> CONTEXT, ledger, CLOCK, provider.origin())) {
                if (tools) provider.body = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"private-canary\",\"tool_calls\":[{}]},\"finish_reason\":\"stop\"}]}";
                else { provider.status = 503; provider.body = "synthetic-private-upstream-canary"; }
                var error = assertThrows(GatewayFailure.class, () -> gateway.suggest(REQUEST));
                assertFalse(error.toString().contains("canary")); assertNull(error.getCause());
                assertEquals(1, provider.calls.get()); assertEquals(Outcome.FAILURE, ledger.outcome); assertNull(ledger.actual);
            }
        }
    }

    @Test void mismatchedOrExpiredFundingSnapshotCannotDispatch() throws Exception {
        for (Metadata funding : new Metadata[]{new Metadata("other", 1, NOW.plusSeconds(10), 1), new Metadata("test", 1, NOW, 1)}) {
            var ledger = new MemoryLedger();
            try (var provider = new AiGatewayFakeProvider();
                 var gateway = SoclaasGatewayFactory.create(settings(), POLICY, funding, () -> ACTOR, (a, r) -> {},
                         (a, r) -> CONTEXT, ledger, CLOCK, provider.origin())) {
                assertEquals(GatewayFailure.Code.AI_UNAVAILABLE, assertThrows(GatewayFailure.class, () -> gateway.suggest(REQUEST)).code());
                assertEquals(0, provider.calls.get()); assertEquals(0, ledger.reservations);
            }
        }
    }

    private static class MemoryLedger implements Ledger {
        int reservations; Long actual; Outcome outcome;
        public void checkInvocationBoundary() { }
        public void configure(Policy p, Metadata m, Instant now) { }
        public Ticket reserve(Actor a, Request r, Policy p, Metadata m, long units, Instant now) {
            reservations++; return new Ticket(UUID.randomUUID(), 1, 1, units, now.plusSeconds(60));
        }
        public Ticket dispatch(Ticket t, Policy p, Metadata m, Instant now) { return new Ticket(t.id(), 2, 1, t.reservedUnits(), t.expiresAt()); }
        public void settle(Ticket t, Outcome o, Long units, Instant now) { actual = units; outcome = o; }
        public int expire(Instant now, int limit) { return 0; }
    }
}
