package com.tracedesk.ai.gateway;

import java.time.Instant;
import static com.tracedesk.ai.gateway.GatewayTypes.*;

/** Trusted backend wiring only; none of these collaborators are browser inputs. */
public final class GatewayPorts {
    private GatewayPorts() { }
    @FunctionalInterface public interface ActorResolver { Actor currentActor(); }
    /** Checks current session, membership, object policy AND expected source version on each call. */
    @FunctionalInterface public interface Authorizer { void authorize(Actor actor, Request request); }
    @FunctionalInterface public interface ContextBuilder { Context build(Actor actor, Request request); }
    public interface Provider extends AutoCloseable {
        Metadata metadata();
        Completion complete(Context context);
        /** The gateway passes the exact immutable snapshot that was admitted. */
        default Completion complete(Context context, Metadata metadata) { return complete(context); }
        @Override default void close() { }
    }
    public interface Ledger {
        /** Fail before any provider/catalog call if an enclosing domain transaction is active. */
        void checkInvocationBoundary();
        /** Publish trusted immutable revisions independently of admission; null metadata only when disabled. */
        void configure(Policy policy, Metadata metadata, Instant now);
        /** Confirm the shared publication still matches this attempt before dispatch and delivery. */
        default void assertCurrent(Policy policy, Metadata metadata, Instant now) { }
        Ticket reserve(Actor actor, Request request, Policy policy, Metadata metadata, long units, Instant now);
        Ticket dispatch(Ticket ticket, Policy policy, Metadata metadata, Instant now);
        void settle(Ticket ticket, Outcome outcome, Long actualUnits, Instant now);
        int expire(Instant now, int limit);
    }
}
