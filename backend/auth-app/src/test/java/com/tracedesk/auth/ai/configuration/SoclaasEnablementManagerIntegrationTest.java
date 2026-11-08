package com.tracedesk.auth.ai.configuration;

import com.tracedesk.ai.internal.soclaas.SoclaasSettings;
import com.tracedesk.auth.ai.persistence.JdbcAiLedger;
import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SoclaasEnablementManagerIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-09T03:00:00Z");

    @Test void disabledAndExpiredStartupNeverCallCatalog() throws Exception {
        try (var db = IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
            var sql = new JdbcTemplate(db);
            var disabledProbeCalls = new AtomicInteger();
            try (var manager = new SoclaasEnablementManager(SoclaasSettings.from(Map.of()), new JdbcAiLedger(sql),
                    Clock.fixed(NOW, ZoneOffset.UTC), disabledProbeCalls::incrementAndGet, false)) {
                assertEquals(SoclaasEnablementManager.Status.DISABLED, manager.status());
                manager.refreshNow();
                assertEquals(0, disabledProbeCalls.get());
            }

            var expiredProbeCalls = new AtomicInteger();
            try (var manager = new SoclaasEnablementManager(settings(NOW.minusSeconds(1)), new JdbcAiLedger(sql),
                    Clock.fixed(NOW, ZoneOffset.UTC), expiredProbeCalls::incrementAndGet, false)) {
                manager.refreshNow();
                assertEquals(SoclaasEnablementManager.Status.UNAVAILABLE, manager.status());
                assertEquals(0, expiredProbeCalls.get());
            }
        }
    }

    @Test void instancesAdoptSharedRefreshFailureRecoveryAndFixedApprovalExpiry() throws Exception {
        try (var db = IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
            var sql = new JdbcTemplate(db);
            var time = new AtomicReference<>(NOW);
            Clock clock = new Clock() {
                @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(java.time.ZoneId zone) { return this; }
                @Override public Instant instant() { return time.get(); }
            };
            var settings = settings(NOW.plusSeconds(400));
            var probeA = new Probe(); var probeB = new Probe();
            try (var first = new SoclaasEnablementManager(settings, new JdbcAiLedger(sql), clock, probeA, false);
                 var second = new SoclaasEnablementManager(settings, new JdbcAiLedger(sql), clock, probeB, false)) {
                assertEquals(SoclaasEnablementManager.Status.UNAVAILABLE, first.status());
                assertEquals(SoclaasEnablementManager.Status.UNAVAILABLE, second.status());
                first.refreshNow();
                assertEquals(SoclaasEnablementManager.Status.AVAILABLE, second.status(), "second process adopts published snapshot");
                assertEquals(1, probeA.calls.get());
                assertEquals(0, probeB.calls.get());

                probeA.fail.set(true);
                first.refreshNow();
                assertEquals(SoclaasEnablementManager.Status.UNAVAILABLE, first.status());
                assertEquals(SoclaasEnablementManager.Status.UNAVAILABLE, second.status(), "failed refresh revokes shared availability");

                probeB.fail.set(false);
                second.refreshNow();
                assertEquals(SoclaasEnablementManager.Status.AVAILABLE, first.status());
                assertEquals(SoclaasEnablementManager.Status.AVAILABLE, second.status());
                String storedExpiry = sql.queryForObject("select approval_valid_until from td_ai_control where singleton=true", String.class);
                assertEquals(NOW.plusSeconds(400).toString(), storedExpiry);

                time.set(NOW.plusSeconds(401));
                assertEquals(SoclaasEnablementManager.Status.UNAVAILABLE, first.status());
                assertEquals(SoclaasEnablementManager.Status.UNAVAILABLE, second.status());
                assertEquals(NOW.plusSeconds(400).toString(), sql.queryForObject(
                        "select approval_valid_until from td_ai_control where singleton=true", String.class));
            }
        }
    }

    private static SoclaasSettings settings(Instant approvalExpiry) {
        Map<String,String> env = new HashMap<>();
        env.put("AI_ENABLED", "true"); env.put("SOCLAAS_API_KEY", "synthetic-key"); env.put("SOCLAAS_MODEL", "synthetic-model");
        env.put("AI_GLOBAL_DAILY_UNITS", "100000"); env.put("AI_ACTOR_DAILY_UNITS", "50000");
        env.put("AI_SPONSORED_ACCESS_ACKNOWLEDGED", "true"); env.put("AI_SPONSORED_APPROVAL_REFERENCE", "synthetic-operator-approval");
        env.put("AI_SPONSORED_APPROVAL_EXPIRES_AT", approvalExpiry.toString());
        return SoclaasSettings.from(env);
    }

    private static final class Probe implements SoclaasEnablementManager.CatalogProbe {
        final AtomicInteger calls = new AtomicInteger(); final AtomicReference<Boolean> fail = new AtomicReference<>(false);
        @Override public void validateConfiguredModel() {
            calls.incrementAndGet();
            if (fail.get()) throw new IllegalStateException("synthetic catalog failure");
        }
    }
}
