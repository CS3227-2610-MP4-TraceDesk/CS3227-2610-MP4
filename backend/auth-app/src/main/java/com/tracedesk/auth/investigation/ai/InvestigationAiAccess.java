package com.tracedesk.auth.investigation.ai;

import com.tracedesk.ai.gateway.SecuredAiGateway;
import com.tracedesk.ai.gateway.GatewayFailure;
import com.tracedesk.auth.reports.application.ReportService.Failure;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import static com.tracedesk.ai.gateway.GatewayPorts.*;
import static com.tracedesk.ai.gateway.GatewayTypes.*;

/** Request-local role policy around the shared secured gateway. */
public final class InvestigationAiAccess implements Authorizer, AutoCloseable {
    private final InvestigationAiSource source;
    private final SecuredAiGateway gateway;
    private final ThreadLocal<Invocation> current = new ThreadLocal<>();

    private static final class Invocation {
        final Request request;
        final InvestigationAiSelection selection;
        final InvestigationAiContext context;
        final Supplier<UUID> liveActor;
        Failure failure;

        Invocation(Request request, InvestigationAiSelection selection,
                   InvestigationAiContext context, Supplier<UUID> liveActor) {
            this.request = request;
            this.selection = selection;
            this.context = context;
            this.liveActor = liveActor;
        }
    }

    @FunctionalInterface public interface Factory {
        SecuredAiGateway create(ActorResolver actors, Authorizer authorizer, ContextBuilder contexts);
    }
    public InvestigationAiAccess(InvestigationAiSource source, Clock clock, Factory factory) {
        this.source = Objects.requireNonNull(source);
        Objects.requireNonNull(clock);
        this.gateway = Objects.requireNonNull(Objects.requireNonNull(factory).create(
                () -> guarded(() -> {
                    UUID id = invocation().liveActor.get();
                    if (id == null) throw new Failure("UNAUTHENTICATED", 401);
                    return new Actor(id);
                }), this,
                (actor, request) -> guarded(() -> {
                    var invocation = invocation();
                    if (!invocation.request.equals(request)) throw new Failure("INVALID_REQUEST", 400);
                    return invocation.context.build(actor, request);
                })));
    }
    public InvestigationAiOutput.Result suggest(UUID project, UUID report, int expectedVersion,
            InvestigationAiSelection selection, Supplier<UUID> liveActor) {
        if (project == null || report == null || expectedVersion < 1 || liveActor == null
                || selection == null || selection.reportFields() == null || selection.notes() == null)
            throw new Failure("INVALID_REQUEST", 400);
        // Snapshot caller-owned collections before retaining them for later gateway checks.
        var snapshot = new InvestigationAiSelection(new ArrayList<>(selection.reportFields()),
                new ArrayList<>(selection.notes()));
        var context = new InvestigationAiContext(source, snapshot);
        snapshot = new InvestigationAiSelection(List.copyOf(snapshot.reportFields()), List.copyOf(snapshot.notes()));
        var request = new Request(project, report, Capability.INVESTIGATION, expectedVersion);
        if (current.get() != null) throw new Failure("SERVICE_UNAVAILABLE", 503);
        var invocation = new Invocation(request, snapshot, context, liveActor);
        current.set(invocation);
        try {
            var suggestion = gateway.suggest(request);
            var labels = new HashSet<>(snapshot.reportFields());
            for (int i = 1; i <= snapshot.notes().size(); i++) labels.add("note" + i);
            return InvestigationAiOutput.parse(suggestion.text(), labels);
        } catch (GatewayFailure failure) {
            if (invocation.failure != null) throw invocation.failure;
            throw translate(failure);
        } finally {
            current.remove();
        }
    }
    @Override public void authorize(Actor actor, Request request) {
        guarded(() -> {
            var invocation = invocation();
            if (request == null || !invocation.request.equals(request))
                throw new Failure("INVALID_REQUEST", 400);
            UUID live = invocation.liveActor.get();
            if (actor == null || actor.id() == null || !actor.id().equals(live))
                throw new Failure("UNAUTHENTICATED", 401);
            var membership = source.membership(actor.id(), request.projectId())
                    .filter(value -> request.projectId().equals(value.projectId())
                            && value.userEnabled() && value.projectEnabled() && value.active())
                    .orElseThrow(() -> new Failure("NOT_FOUND", 404));
            if (!"DEVELOPER".equals(membership.role())) throw new Failure("FORBIDDEN", 403);
            var report = source.report(request.projectId(), request.sourceId())
                    .filter(value -> request.projectId().equals(value.projectId())
                            && request.sourceId().equals(value.id()))
                    .orElseThrow(() -> new Failure("NOT_FOUND", 404));
            if (!actor.id().equals(report.assignedDeveloperId())) throw new Failure("FORBIDDEN", 403);

            // All existence checks precede lifecycle and version errors, including later notes.
            boolean staleNote = false;
            for (var selected : invocation.selection.notes()) {
                var note = source.note(request.projectId(), request.sourceId(), selected.id())
                        .filter(value -> selected.id().equals(value.id())
                                && request.sourceId().equals(value.reportId()))
                        .orElseThrow(() -> new Failure("NOT_FOUND", 404));
                staleNote |= note.version() != selected.expectedVersion();
            }
            if (!"IN_PROGRESS".equals(report.status())) throw new Failure("INVALID_STATE", 409);
            if (report.version() != request.expectedSourceVersion() || staleNote)
                throw new Failure("STALE_VERSION", 409);
            return null;
        });
    }
    @Override public void close() {
        try { gateway.close(); }
        catch (GatewayFailure failure) { throw translate(failure); }
    }

    private Invocation invocation() {
        var invocation = current.get();
        if (invocation == null) throw new Failure("SERVICE_UNAVAILABLE", 503);
        return invocation;
    }

    /** Preserve fixed role errors through the gateway's safe exception boundary. */
    private <T> T guarded(Supplier<T> action) {
        try { return action.get(); }
        catch (RuntimeException failure) {
            var invocation = current.get();
            if (invocation != null) invocation.failure = failure instanceof Failure safe
                    ? safe : new Failure("SERVICE_UNAVAILABLE", 503);
            throw new GatewayFailure(GatewayFailure.Code.FORBIDDEN);
        }
    }

    private static Failure translate(GatewayFailure failure) {
        return switch (failure.code()) {
            case INVALID_REQUEST -> new Failure("INVALID_REQUEST", 400);
            case FORBIDDEN -> new Failure("FORBIDDEN", 403);
            case STALE_SOURCE -> new Failure("STALE_VERSION", 409);
            case AI_INVALID_RESPONSE, AI_OUTPUT_TOO_LARGE -> new Failure("AI_INVALID_OUTPUT", 502);
            default -> new Failure("AI_UNAVAILABLE", 503);
        };
    }
}
