package com.tracedesk.ai.internal.soclaas;

import com.tracedesk.ai.gateway.GatewayFailure;
import com.tracedesk.ai.gateway.SecuredAiGateway;
import java.time.Clock;
import java.net.URI;
import static com.tracedesk.ai.gateway.GatewayPorts.*;
import static com.tracedesk.ai.gateway.GatewayTypes.*;

/** Backend wiring creates only a secured facade; no public provider/client accessor exists. */
public final class SoclaasGatewayFactory {
    private SoclaasGatewayFactory() { }

    /**
     * Funding snapshot is a server-validated, expiring resource-unit authorization, never a price guess.
     * Role slices must supply fresh session/object/version authorization and allowlisted context adapters.
     */
    public static SecuredAiGateway create(SoclaasSettings settings, Policy policy, Metadata funding,
                                         ActorResolver actors, Authorizer authorizer, ContextBuilder contexts,
                                         Ledger ledger, Clock clock) {
        return create(settings, policy, funding, actors, authorizer, contexts, ledger, clock, SoclaasSettings.ORIGIN);
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
        private final Metadata funding;
        private final Clock clock;
        private boolean catalogValidated;
        Adapter(SoclaasSettings settings, Metadata funding, Clock clock, URI origin) {
            this.settings = settings; this.funding = funding; this.clock = clock;
            this.client = new SoclaasClient(settings, origin);
        }
        @Override public synchronized Metadata metadata() {
            validateMetadata(funding, clock.instant());
            if (!settings.enabled() || !settings.model().equals(funding.model()))
                throw new GatewayFailure(GatewayFailure.Code.AI_UNAVAILABLE);
            try {
                if (!catalogValidated) {
                    client.validateModel();
                    catalogValidated = true;
                }
                return funding;
            } catch (SoclaasException safe) { throw mapped(safe); }
        }
        @Override public Completion complete(Context context) {
            validateMetadata(funding, clock.instant());
            try {
                var completion = client.complete(context.systemInstruction(), context.dataJson());
                var usage = completion.usage();
                return new Completion(completion.text(), usage == null ? null : new Usage(usage.promptTokens(), usage.completionTokens()));
            } catch (SoclaasException safe) { throw mapped(safe); }
        }
        @Override public void close() { client.close(); }
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
