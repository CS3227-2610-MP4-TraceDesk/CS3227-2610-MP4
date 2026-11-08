package com.tracedesk.auth.ai.configuration;

import com.tracedesk.ai.gateway.GatewayPorts.ActorResolver;
import com.tracedesk.ai.gateway.GatewayPorts.Authorizer;
import com.tracedesk.ai.gateway.GatewayPorts.ContextBuilder;
import com.tracedesk.ai.gateway.GatewayTypes.Metadata;
import com.tracedesk.ai.gateway.SecuredAiGateway;
import com.tracedesk.ai.internal.soclaas.SoclaasGatewayFactory;
import com.tracedesk.ai.internal.soclaas.SoclaasSettings;
import com.tracedesk.auth.ai.persistence.JdbcAiLedger;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Shared backend-only enablement, catalog refresh and secured-gateway factory. */
public final class SoclaasEnablementManager implements AutoCloseable {
    public enum Status { DISABLED, UNAVAILABLE, AVAILABLE }

    private final SoclaasSettings settings;
    private final JdbcAiLedger ledger;
    private final Clock clock;
    private final CatalogProbe catalog;
    private final ScheduledExecutorService refresh;

    /** Construction publishes the startup policy only; it performs no provider HTTP call. */
    public SoclaasEnablementManager(SoclaasSettings settings, JdbcAiLedger ledger, Clock clock) {
        this(settings, ledger, clock, productionCatalog(settings), true);
    }

    interface CatalogProbe extends AutoCloseable {
        void validateConfiguredModel();
        @Override default void close() { }
    }

    SoclaasEnablementManager(SoclaasSettings settings, JdbcAiLedger ledger, Clock clock,
                             CatalogProbe catalog, boolean schedule) {
        this.settings = Objects.requireNonNull(settings);
        this.ledger = Objects.requireNonNull(ledger);
        this.clock = Objects.requireNonNull(clock);
        this.catalog = Objects.requireNonNull(catalog);
        try {
            ledger.publishPolicy(settings.policy(),clock.instant());
            if (settings.enabled()) ledger.prepareEnablement(settings.policy(),settings.model(),settings.modelRevision(),
                    settings.approvalRevision(),settings.approvalFingerprint(),settings.approvalValidUntil(),clock.instant());
        } catch (RuntimeException unavailable) { /* status is derived from the shared publication */ }
        this.refresh = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("ai-metadata-refresh").factory());
        if (schedule && settings.enabled()) refresh.scheduleWithFixedDelay(this::refreshQuietly, 10_000, 600_000, TimeUnit.MILLISECONDS);
    }

    private static CatalogProbe productionCatalog(SoclaasSettings settings) {
        var validator = SoclaasGatewayFactory.catalogValidator(settings);
        return new CatalogProbe() {
            @Override public void validateConfiguredModel() { validator.validateConfiguredModel(); }
            @Override public void close() { validator.close(); }
        };
    }

    /** Shared read-only publication check; never calls the provider or writes to the ledger. */
    public Status status() {
        if (!settings.enabled()) return ledger.isCurrentPolicy(settings.policy()) ? Status.DISABLED : Status.UNAVAILABLE;
        return currentMetadata() == null ? Status.UNAVAILABLE : Status.AVAILABLE;
    }

    /** Operator/configuration refresh: validates only the fixed-origin catalog, never a completion. */
    public synchronized void refreshNow() {
        if (!settings.enabled() || settings.approvalValidUntil() == null
                || !settings.approvalValidUntil().isAfter(clock.instant())) {
            return;
        }
        Instant now = clock.instant();
        try {
            ledger.publishPolicy(settings.policy(),now);
            ledger.prepareEnablement(settings.policy(),settings.model(),settings.modelRevision(),settings.approvalRevision(),
                    settings.approvalFingerprint(),settings.approvalValidUntil(),now);
            // Revoke the prior snapshot before refresh starts. Failure therefore remains fail-closed.
            long revision = ledger.nextMetadataRevision(settings.policy(),settings.model(),settings.modelRevision(),
                    settings.approvalRevision(),settings.approvalFingerprint(),settings.approvalValidUntil(),now);
            catalog.validateConfiguredModel();
            Instant until = now.plusSeconds(900);
            if (until.isAfter(settings.approvalValidUntil())) until = settings.approvalValidUntil();
            var published = new Metadata(settings.model(), revision, until, settings.approvalRevision(),
                    settings.approvalFingerprint(), settings.approvalValidUntil());
            ledger.configure(settings.policy(), published, now);
        } catch (RuntimeException unavailable) {
            // The revision allocator revoked the previous snapshot before validation.
        }
    }

    /** Returns only the secured invocation facade; the provider client remains owned by the transport. */
    public SecuredAiGateway createGateway(ActorResolver actors, Authorizer authorizer, ContextBuilder contexts) {
        return SoclaasGatewayFactory.createRefreshing(settings, settings.policy(), this::currentMetadata,
                candidate -> candidate.equals(currentMetadata()), actors, authorizer, contexts, ledger, clock);
    }

    private Metadata currentMetadata() {
        return ledger.readCurrentMetadata(settings.policy(),settings.model(),settings.modelRevision(),settings.approvalRevision(),
                settings.approvalFingerprint(),settings.approvalValidUntil(),clock.instant());
    }

    private void refreshQuietly() { try { refreshNow(); } catch (RuntimeException ignored) { /* shared state remains fail-closed */ } }

    @Override public void close() {
        refresh.shutdownNow();
        try { catalog.close(); } catch (Exception ignored) { }
    }
}
