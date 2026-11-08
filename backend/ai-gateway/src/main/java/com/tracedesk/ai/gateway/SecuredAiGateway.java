package com.tracedesk.ai.gateway;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.*;
import static com.tracedesk.ai.gateway.GatewayTypes.*;
import static com.tracedesk.ai.gateway.GatewayPorts.*;
import static com.tracedesk.ai.gateway.GatewayFailure.Code.*;

/** The sole invocation facade. All collaborators are installed by trusted backend wiring. */
public final class SecuredAiGateway implements AutoCloseable {
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final ActorResolver actors;
    private final Authorizer authorizer;
    private final ContextBuilder contexts;
    private final Provider provider;
    private final Ledger ledger;
    private final Policy policy;
    private final Clock clock;
    private final Semaphore active;
    // No unbounded worker queue, including before shared admission/catalog validation.
    private final ExecutorService workers = new ThreadPoolExecutor(0, 4, 60, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Thread.ofVirtual().name("ai-gateway-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());

    public SecuredAiGateway(ActorResolver actors, Authorizer authorizer, ContextBuilder contexts,
                           Provider provider, Ledger ledger, Policy policy, Clock clock) {
        this.actors = Objects.requireNonNull(actors); this.authorizer = Objects.requireNonNull(authorizer);
        this.contexts = Objects.requireNonNull(contexts); this.provider = Objects.requireNonNull(provider);
        this.ledger = Objects.requireNonNull(ledger); this.policy = Objects.requireNonNull(policy);
        this.clock = Objects.requireNonNull(clock);
        this.active = new Semaphore(4);
    }

    public Suggestion suggest(Request request) {
        try { return invoke(request); }
        catch (GatewayFailure safe) { throw safe; }
        catch (RuntimeException unsafe) { throw new GatewayFailure(AI_UNAVAILABLE); }
    }

    private Suggestion invoke(Request request) {
        if (request == null || request.projectId() == null || request.sourceId() == null
                || request.capability() == null || request.expectedSourceVersion() < 1)
            throw new GatewayFailure(INVALID_REQUEST);
        Actor actor = actors.currentActor();
        if (actor == null || actor.id() == null) throw new GatewayFailure(FORBIDDEN);
        authorizer.authorize(actor, request);
        ledger.checkInvocationBoundary();
        validatePolicyShape(policy);
        if (!policy.enabled()) {
            ledger.configure(policy, null, clock.instant());
            throw new GatewayFailure(AI_DISABLED);
        }
        validatePolicy(policy);
        if (Thread.currentThread().isInterrupted()) throw new GatewayFailure(AI_UNAVAILABLE);
        if (!active.tryAcquire()) throw new GatewayFailure(AI_CAPACITY_EXCEEDED);
        try {
            Context context = contexts.build(actor, request);
            long inputUnits = validateContext(context, request);
            ledger.expire(clock.instant(), 100);
            Metadata metadata = bounded(provider::metadata);
            validateMetadata(metadata, clock.instant());
            ledger.configure(policy, metadata, clock.instant());
            Ticket ticket = ledger.reserve(actor, request, policy, metadata, inputUnits + 1200, clock.instant());
            Ticket dispatched;
            try {
                // Authorization can change during catalog/admission; do not dispatch after a known revocation.
                authorizer.authorize(actor, request);
                if (Thread.currentThread().isInterrupted()) throw new GatewayFailure(AI_UNAVAILABLE);
                dispatched = ledger.dispatch(ticket, policy, metadata, clock.instant());
            } catch (RuntimeException failure) {
                ledger.settle(ticket, Outcome.CANCELLED, null, clock.instant());
                throw failure;
            }
            Completion completion;
            try {
                completion = bounded(() -> provider.complete(context));
                validateCompletion(completion, inputUnits);
            } catch (RuntimeException failure) {
                Outcome outcome = Thread.currentThread().isInterrupted() ? Outcome.CANCELLED
                        : qualifies(failure) ? Outcome.FAILURE : Outcome.UNKNOWN;
                ledger.settle(dispatched, outcome, null, clock.instant());
                throw failure;
            }
            Long units = actualUnits(completion.usage(), inputUnits);
            ledger.settle(dispatched, Outcome.SUCCESS, units, clock.instant());
            validateMetadata(metadata, clock.instant());
            // This port must re-read the live session/membership/object/source version, not a cached decision.
            authorizer.authorize(actor, request);
            if (Thread.currentThread().isInterrupted()) throw new GatewayFailure(AI_UNAVAILABLE);
            return new Suggestion(completion.text(), request.capability(), context.promptVersion(), context.sourceVersion());
        } finally { active.release(); }
    }

    private static long validateContext(Context context, Request request) {
        if (context == null || context.sourceVersion() != request.expectedSourceVersion())
            throw new GatewayFailure(STALE_SOURCE);
        if (context.promptVersion() == null || !context.promptVersion().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
                || !validText(context.systemInstruction()) || context.dataJson() == null
                || bytes(context.systemInstruction()) > 8000 || bytes(context.dataJson()) > 16384)
            throw new GatewayFailure(INVALID_REQUEST);
        long estimate = (long) bytes(context.systemInstruction()) + bytes(context.dataJson()) + 256;
        if (estimate > 8000) throw new GatewayFailure(INVALID_REQUEST);
        try {
            var data = JSON.readTree(context.dataJson());
            if (data == null || !data.isObject()) throw new GatewayFailure(INVALID_REQUEST);
        } catch (RuntimeException invalid) { throw new GatewayFailure(INVALID_REQUEST); }
        return estimate;
    }

    private static void validateCompletion(Completion completion, long inputUnits) {
        if (completion == null || !validText(completion.text())) throw new GatewayFailure(AI_INVALID_RESPONSE);
        if (bytes(completion.text()) > 16384 || (actualUnits(completion.usage(), inputUnits) == null && bytes(completion.text()) > 1200)
                || (completion.usage() != null && completion.usage().outputTokens() > 1200))
            throw new GatewayFailure(AI_OUTPUT_TOO_LARGE);
    }

    private static Long actualUnits(Usage usage, long inputEstimate) {
        if (usage == null || usage.inputTokens() <= 0 || usage.outputTokens() <= 0
                || usage.inputTokens() > inputEstimate || usage.outputTokens() > 1200) return null;
        return usage.inputTokens() + usage.outputTokens();
    }

    private static boolean validText(String value) {
        if (value == null || value.isBlank()) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) return false;
            } else if (Character.isLowSurrogate(c) || (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) return false;
        }
        return true;
    }

    private <T> T bounded(Callable<T> action) {
        Future<T> pending;
        try { pending = workers.submit(action); }
        catch (RejectedExecutionException unavailable) { throw new GatewayFailure(AI_CAPACITY_EXCEEDED); }
        try { return pending.get(30, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) {
            pending.cancel(true); Thread.currentThread().interrupt(); throw new GatewayFailure(AI_UNAVAILABLE);
        } catch (TimeoutException timeout) {
            pending.cancel(true); throw new GatewayFailure(AI_TIMEOUT);
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof GatewayFailure safe) throw safe;
            throw new GatewayFailure(AI_UNAVAILABLE);
        }
    }
    private static boolean qualifies(RuntimeException failure) {
        if (!(failure instanceof GatewayFailure safe)) return true;
        return switch (safe.code()) {
            case AI_UNAVAILABLE, AI_TIMEOUT, AI_INVALID_RESPONSE, AI_OUTPUT_TOO_LARGE -> true;
            default -> false;
        };
    }
    private static int bytes(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }
    @Override public void close() {
        workers.shutdownNow();
        try { provider.close(); }
        catch (RuntimeException unsafe) { throw new GatewayFailure(AI_UNAVAILABLE); }
    }
}
