package com.tracedesk.ai.internal.soclaas;

import com.tracedesk.ai.gateway.GatewayFailure;
import com.tracedesk.ai.gateway.SecuredAiGateway;
import java.time.Clock;
import java.net.URI;
import java.util.function.Supplier;
import java.util.function.Predicate;
import static com.tracedesk.ai.gateway.GatewayPorts.*;
import static com.tracedesk.ai.gateway.GatewayTypes.*;

/** Backend wiring creates only a secured facade; no public provider/client accessor exists. */
public final class SoclaasGatewayFactory {
    private SoclaasGatewayFactory() { }

    /**
     * Funding snapshot is a server-validated, expiring resource-unit authorization, never a price guess.
     * Role slices must supply fresh session/object/version authorization and allowlisted context adapters.
     */
    static SecuredAiGateway create(SoclaasSettings settings, Policy policy, Metadata funding,
                                         ActorResolver actors, Authorizer authorizer, ContextBuilder contexts,
                                         Ledger ledger, Clock clock) {
        return create(settings, policy, funding, actors, authorizer, contexts, ledger, clock, SoclaasSettings.ORIGIN);
    }

    /** Dynamic metadata seam for a trusted enablement manager; provider access remains encapsulated. */
    public static SecuredAiGateway createRefreshing(SoclaasSettings settings, Policy policy, Supplier<Metadata> funding,
                                                    Predicate<Metadata> snapshotCurrent, ActorResolver actors,
                                                    Authorizer authorizer, ContextBuilder contexts, Ledger ledger, Clock clock) {
        requireConfiguredPolicy(settings,policy);
        return new SecuredAiGateway(actors, authorizer, contexts,
                new Adapter(settings, funding, snapshotCurrent, false, clock, SoclaasSettings.ORIGIN), ledger, policy, clock);
    }

    private static void requireConfiguredPolicy(SoclaasSettings settings, Policy policy) {
        if (settings == null || policy == null || !settings.policy().equals(policy))
            throw new IllegalArgumentException("AI policy must match trusted deployment settings");
    }

    /** Creates only a catalog validator; it cannot complete requests or expose provider data or credentials. */
    public static CatalogValidator catalogValidator(SoclaasSettings settings) {
        return new CatalogValidator(settings);
    }

    public static final class CatalogValidator implements AutoCloseable {
        private final SoclaasClient client;
        private CatalogValidator(SoclaasSettings settings) { this.client = new SoclaasClient(settings); }
        public void validateConfiguredModel() { client.validateModel(); }
        @Override public void close() { client.close(); }
    }

    // Isolated fake HTTP seam, unavailable to application consumers.
    static SecuredAiGateway create(SoclaasSettings settings, Policy policy, Metadata funding,
                                  ActorResolver actors, Authorizer authorizer, ContextBuilder contexts,
                                  Ledger ledger, Clock clock, URI origin) {
        return new SecuredAiGateway(actors, authorizer, contexts,
                new Adapter(settings, funding, clock, origin), ledger, policy, clock);
    }

    private static final class Adapter implements Provider {
        private final SoclaasClient client;
        private final SoclaasSettings settings;
        private final Supplier<Metadata> funding;
        private final Clock clock;
        private boolean catalogValidated;
        Adapter(SoclaasSettings settings, Metadata funding, Clock clock, URI origin) {
            this(settings, () -> funding, ignored -> false, true, clock, origin);
        }
        Adapter(SoclaasSettings settings, Supplier<Metadata> funding, Predicate<Metadata> snapshotCurrent, boolean validateOnAccess, Clock clock, URI origin) {
            this.settings = settings; this.funding = funding; this.snapshotCurrent = snapshotCurrent; this.validateOnAccess = validateOnAccess; this.clock = clock;
            this.client = new SoclaasClient(settings, origin);
        }
        private final Predicate<Metadata> snapshotCurrent;
        private final boolean validateOnAccess;
        @Override public synchronized Metadata metadata() {
            Metadata funding = this.funding.get();
            if (funding == null) throw new GatewayFailure(GatewayFailure.Code.AI_UNAVAILABLE);
            validateMetadata(funding, clock.instant());
            if (!settings.enabled() || !settings.model().equals(funding.model()) || !approved(funding))
                throw new GatewayFailure(GatewayFailure.Code.AI_UNAVAILABLE);
            try {
                if (validateOnAccess && !this.catalogValidated) {
                    client.validateModel();
                    this.catalogValidated = true;
                }
                return funding;
            } catch (SoclaasException safe) { throw mapped(safe); }
        }
        @Override public Completion complete(Context context) {
            return complete(context, funding.get());
        }
        @Override public Completion complete(Context context, Metadata funding) {
            validateMetadata(funding, clock.instant());
            if (!approved(funding) || !settings.model().equals(funding.model())) throw new GatewayFailure(GatewayFailure.Code.AI_UNAVAILABLE);
            try {
                if (!validateOnAccess) {
                    if (!snapshotCurrent.test(funding)) throw new GatewayFailure(GatewayFailure.Code.AI_UNAVAILABLE);
                    client.markModelValidated();
                }
                var completion = client.complete(context.systemInstruction(), context.dataJson());
                var usage = completion.usage();
                return new Completion(completion.text(), usage == null ? null : new Usage(usage.promptTokens(), usage.completionTokens()));
            } catch (SoclaasException safe) { throw mapped(safe); }
        }
        @Override public void close() { client.close(); }
        private boolean approved(Metadata metadata) {
            return metadata.fundingRevision() == settings.approvalRevision()
                    && settings.approvalFingerprint() != null
                    && settings.approvalFingerprint().equals(metadata.approvalFingerprint())
                    && settings.approvalValidUntil().equals(metadata.approvalValidUntil());
        }
        private static GatewayFailure mapped(SoclaasException failure) {
            return new GatewayFailure(switch (failure.category()) {
                case AI_TIMEOUT -> GatewayFailure.Code.AI_TIMEOUT;
                case AI_INVALID_RESPONSE -> GatewayFailure.Code.AI_INVALID_RESPONSE;
                case AI_OUTPUT_TOO_LARGE -> GatewayFailure.Code.AI_OUTPUT_TOO_LARGE;
                case AI_REJECTED -> GatewayFailure.Code.AI_REJECTED;
                case AI_RATE_LIMITED -> GatewayFailure.Code.AI_RATE_LIMITED;
                default -> GatewayFailure.Code.AI_UNAVAILABLE;
            });
        }
    }
}
