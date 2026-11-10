package com.tracedesk.auth.investigation.ai;

import com.tracedesk.ai.gateway.SecuredAiGateway;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;
import static com.tracedesk.ai.gateway.GatewayPorts.*;
import static com.tracedesk.ai.gateway.GatewayTypes.*;

/** Experiment signature only. Factory is trusted backend wiring, like the Reporter adapter. */
public final class InvestigationAiAccess implements Authorizer, AutoCloseable {
    @FunctionalInterface public interface Factory {
        SecuredAiGateway create(ActorResolver actors, Authorizer authorizer, ContextBuilder contexts);
    }
    public InvestigationAiAccess(InvestigationAiSource source, Clock clock, Factory factory) {
        throw new UnsupportedOperationException("not implemented");
    }
    public InvestigationAiOutput.Result suggest(UUID project, UUID report, int expectedVersion,
            InvestigationAiSelection selection, Supplier<UUID> liveActor) {
        throw new UnsupportedOperationException("not implemented");
    }
    @Override public void authorize(Actor actor, Request request) {
        throw new UnsupportedOperationException("not implemented");
    }
    @Override public void close() {
        throw new UnsupportedOperationException("not implemented");
    }
}
