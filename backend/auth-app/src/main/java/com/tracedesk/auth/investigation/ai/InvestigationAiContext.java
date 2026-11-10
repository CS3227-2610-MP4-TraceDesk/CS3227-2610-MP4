package com.tracedesk.auth.investigation.ai;

import com.tracedesk.ai.gateway.GatewayPorts.ContextBuilder;
import static com.tracedesk.ai.gateway.GatewayTypes.*;

/** Experiment signature only; checks versions on values read through the source port. */
public final class InvestigationAiContext implements ContextBuilder {
    public InvestigationAiContext(InvestigationAiSource source, InvestigationAiSelection selection) {
        throw new UnsupportedOperationException("not implemented");
    }
    @Override public Context build(Actor actor, Request request) {
        throw new UnsupportedOperationException("not implemented");
    }
}
