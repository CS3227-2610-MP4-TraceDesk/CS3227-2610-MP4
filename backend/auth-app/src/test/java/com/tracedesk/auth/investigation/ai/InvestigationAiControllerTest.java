package com.tracedesk.auth.investigation.ai;

import com.tracedesk.ai.gateway.SecuredAiGateway;
import com.tracedesk.auth.api.ApiFailureHandler;
import com.tracedesk.auth.investigation.api.InvestigationAiController;
import com.tracedesk.auth.persistence.AuthStore;
import com.tracedesk.auth.investigation.application.InvestigationRepository.Note;
import com.tracedesk.auth.reports.application.ReportRepository;
import com.tracedesk.auth.reports.domain.ReportData;
import com.tracedesk.auth.security.InvestigationBodyLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.tracedesk.ai.gateway.GatewayPorts.Provider;
import static com.tracedesk.ai.gateway.GatewayTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class InvestigationAiControllerTest {
    private static final String CSRF = "known-token";

    static final class Fixture {
        final UUID project = UUID.randomUUID(), report = UUID.randomUUID(), developer = UUID.randomUUID(), note = UUID.randomUUID();
        final AtomicInteger contextReads = new AtomicInteger(), calls = new AtomicInteger();
        final Map<UUID, Note> notes = new LinkedHashMap<>();
        final com.tracedesk.ai.gateway.GatewayPorts.Ledger ledger = mock(com.tracedesk.ai.gateway.GatewayPorts.Ledger.class);
        final InvestigationAiSource source;
        Policy policy = new Policy(true, 1, 100000, 50000, 4, 1, Duration.ofSeconds(60));
        boolean member = true;
        String role = "DEVELOPER", status = "IN_PROGRESS", description = "description";
        int reportVersion = 1;
        Fixture() {
            insertNote(note, report);
            source = new InvestigationAiSource() {
                public Optional<ReportRepository.Membership> membership(UUID actor, UUID projectId) {
                    return member && developer.equals(actor) && project.equals(projectId)
                            ? Optional.of(new ReportRepository.Membership(project, "Synthetic", true, true, true, role)) : Optional.empty();
                }
                public Optional<ReportRepository.Report> report(UUID projectId, UUID reportId) {
                    if (!project.equals(projectId) || !report.equals(reportId)) return Optional.empty();
                    return Optional.of(new ReportRepository.Report(report, project, UUID.randomUUID(),
                            new ReportData("title", description, "environment", "steps", "expected", "actual", "logs"),
                            status, developer, InvestigationAiAccessTest.NOW, InvestigationAiAccessTest.NOW, reportVersion));
                }
                public Optional<Note> note(UUID projectId, UUID reportId, UUID noteId) {
                    if (!project.equals(projectId) || !report.equals(reportId)) return Optional.empty();
                    return Optional.ofNullable(notes.get(noteId));
                }
            };
            var ticket = new Ticket(UUID.randomUUID(), 1, 1, 2000, InvestigationAiAccessTest.NOW.plusSeconds(60));
            when(ledger.reserve(any(), any(), any(), any(), anyLong(), any())).thenReturn(ticket);
            when(ledger.dispatch(any(), any(), any(), any())).thenReturn(new Ticket(ticket.id(), 2, 1, 2000, ticket.expiresAt()));
        }
        void insertNote(UUID id, UUID reportId) {
            notes.put(id, new Note(id, reportId, developer, null, "synthetic note", InvestigationAiAccessTest.NOW,
                    InvestigationAiAccessTest.NOW, 1));
        }
        InvestigationAiAccess service() {
            Provider provider = new Provider() {
                public Metadata metadata() { return new Metadata("synthetic-model", policy.revision(), InvestigationAiAccessTest.NOW.plusSeconds(900), 1); }
                public Completion complete(Context context) { calls.incrementAndGet(); return new Completion(InvestigationAiAccessTest.OUTPUT, new Usage(20, 20)); }
            };
            return new InvestigationAiAccess(source, InvestigationAiAccessTest.CLOCK,
                    (actors, authorizer, contexts) -> new SecuredAiGateway(actors, authorizer,
                            (actor, request) -> { contextReads.incrementAndGet(); return contexts.build(actor, request); },
                            provider, ledger, policy, InvestigationAiAccessTest.CLOCK));
        }
    }

    @Test void successHasExactServerOwnedResponseAndNoMutation() throws Exception {
        var f = new Fixture(); try (var access = f.service()) {
            var api = api(f, access, sessions(f.developer));
            String body = body(1, List.of("title", "textLogs"), List.of(note(f.note, 1)));
            String raw = api.mvc.perform(request(f, api.session, body)).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.capability").value("INVESTIGATION"))
                    .andExpect(jsonPath("$.promptVersion").value("investigation-suggestion-v2"))
                    .andExpect(jsonPath("$.reportId").value(f.report.toString()))
                    .andExpect(jsonPath("$.reportVersion").value(1))
                    .andExpect(jsonPath("$.reportFields[0]").value("title"))
                    .andExpect(jsonPath("$.reportFields[1]").value("textLogs"))
                    .andExpect(jsonPath("$.sourceNotes[0].ref").value("note1"))
                    .andExpect(jsonPath("$.sourceNotes[0].id").value(f.note.toString()))
                    .andExpect(jsonPath("$.sourceNotes[0].version").value(1))
                    .andExpect(jsonPath("$.hypotheses").isArray()).andExpect(jsonPath("$.nextSteps").isArray())
                    .andReturn().getResponse().getContentAsString();
            assertEquals(Set.of("capability", "promptVersion", "reportId", "reportVersion", "reportFields",
                    "sourceNotes", "hypotheses", "nextSteps"), InvestigationAiContextTest.JSON.readTree(raw).propertyNames());
            assertEquals(1, f.calls.get());
            assertEquals("IN_PROGRESS", f.status);
            assertEquals(1, f.reportVersion);
            assertEquals(1, f.notes.size());
        }
    }

    @Test void strictRequestShapeAndEveryRequestLimit() throws Exception {
        var f = new Fixture(); try (var access = f.service()) {
            var api = api(f, access, sessions(f.developer));
            for (String valid : List.of(
                    body(1, List.of("title"), List.of()),
                    body(Integer.MAX_VALUE, List.of("title", "description", "environment", "reproductionSteps",
                            "expectedBehavior", "actualBehavior", "textLogs"), List.of()),
                    body(1, List.of("title"), List.of(note(f.note, Integer.MAX_VALUE))),
                    body(1, List.of("title"), fiveNotes(f)))) {
                // The maximum int is structurally valid; the stale value proves it reached authorization.
                int expected = valid.contains(String.valueOf(Integer.MAX_VALUE)) ? 409 : 200;
                api.mvc.perform(request(f, api.session, valid)).andExpect(status().is(expected));
            }
            String base = body(1, List.of("title"), List.of());
            int bytes = base.getBytes(StandardCharsets.UTF_8).length;
            api.mvc.perform(request(f, api.session, base + " ".repeat(8192 - bytes))).andExpect(status().isOk());
            api.mvc.perform(request(f, api.session, base + " ".repeat(8193 - bytes)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

            List<String> invalid = List.of(
                    "null", "{}", "[]", "{\"expectedVersion\":1,\"reportFields\":[\"title\"],\"notes\":[],\"actor\":\"forged\"}",
                    "{\"expectedVersion\":1,\"expectedVersion\":2,\"reportFields\":[\"title\"],\"notes\":[]}",
                    "{\"expectedVersion\":null,\"reportFields\":[\"title\"],\"notes\":[]}",
                    body(0, List.of("title"), List.of()),
                    "{\"expectedVersion\":2147483648,\"reportFields\":[\"title\"],\"notes\":[]}",
                    body(1, List.of(), List.of()),
                    body(1, List.of("title", "title"), List.of()),
                    body(1, List.of("title", "description", "environment", "reproductionSteps", "expectedBehavior", "actualBehavior", "textLogs", "prompt"), List.of()),
                    body(1, List.of("forged"), List.of()),
                    "{\"expectedVersion\":1,\"reportFields\":null,\"notes\":[]}",
                    "{\"expectedVersion\":1,\"reportFields\":[\"title\"],\"notes\":null}",
                    body(1, List.of("title"), sixNotes(f)),
                    body(1, List.of("title"), List.of(note(f.note, 1), note(f.note, 1))),
                    "{\"expectedVersion\":1,\"reportFields\":[\"title\"],\"notes\":[{\"id\":null,\"expectedVersion\":1}]}",
                    "{\"expectedVersion\":1,\"reportFields\":[\"title\"],\"notes\":[{\"id\":\"" + f.note + "\",\"expectedVersion\":0}]}",
                    "{\"expectedVersion\":1,\"reportFields\":[\"title\"],\"notes\":[{\"id\":\"" + f.note + "\",\"expectedVersion\":2147483648}]}",
                    "{\"expectedVersion\":1,\"reportFields\":[\"title\"],\"notes\":[{\"id\":\"" + f.note + "\",\"expectedVersion\":1,\"role\":\"DEVELOPER\"}]}",
                    base + " {}"
            );
            int callsBefore = f.calls.get();
            for (String raw : invalid) api.mvc.perform(request(f, api.session, raw))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
            api.mvc.perform(post(route(f).replace(f.project.toString(), f.project.toString().toUpperCase()))
                            .session(api.session).header("X-CSRF-TOKEN", CSRF).contentType("application/json").content(base))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
            api.mvc.perform(request(f, api.session, base).queryParam("actor", f.developer.toString()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
            api.mvc.perform(post(route(f)).session(api.session).header("X-CSRF-TOKEN", CSRF)
                            .contentType("text/plain").content(base))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
            assertEquals(callsBefore, f.calls.get(), "invalid requests must not call the provider");
        }
    }

    @Test void everyErrorCodeUsesSpecifiedStatusAndAuthorizationPrecedesAiAvailability() throws Exception {
        var f = new Fixture(); {
            var sessions = sessions(f.developer);
            String valid = body(1, List.of("title"), List.of());

            try (var access = f.service()) {
                var api = api(f, access, sessions);
                api.mvc.perform(request(f, api.session, "{}"))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
                api.mvc.perform(post(route(f)).session(api.session).contentType("application/json").content(valid))
                        .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
                when(sessions.findById(anyString())).thenReturn(null);
                api.mvc.perform(request(f, api.session, valid)).andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
                when(sessions.findById(anyString())).thenReturn(persisted(f.developer));
                f.member = false;
                api.mvc.perform(request(f, api.session, valid)).andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
                f.member = true; f.role = "REPORTER";
                api.mvc.perform(request(f, api.session, valid)).andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
                f.role = "DEVELOPER"; f.status = "OPEN";
                api.mvc.perform(request(f, api.session, valid)).andExpect(status().isConflict())
                        .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
                f.status = "IN_PROGRESS";
                api.mvc.perform(request(f, api.session, body(2, List.of("title"), List.of())))
                        .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("STALE_VERSION"));
                assertEquals(0, f.contextReads.get(), "initial denials must happen before context selection");
                assertEquals(0, f.calls.get(), "initial denials must happen before provider dispatch");
                f.description = "x".repeat(8000);
                api.mvc.perform(request(f, api.session, body(1, List.of("description"), List.of())))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("AI_CONTEXT_TOO_LARGE"));
                assertEquals(1, f.contextReads.get()); assertEquals(0, f.calls.get());
            }

            f.description = "description";
            f.policy = Policy.disabled();
            try (var disabled = f.service()) {
                var api = api(f, disabled, sessions);
                int contextBefore = f.contextReads.get(), callsBefore = f.calls.get();
                f.role = "REPORTER";
                api.mvc.perform(request(f, api.session, valid)).andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
                assertEquals(contextBefore, f.contextReads.get()); assertEquals(callsBefore, f.calls.get());
                f.role = "DEVELOPER";
                api.mvc.perform(request(f, api.session, valid)).andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.error.code").value("AI_UNAVAILABLE"));
                assertEquals(contextBefore, f.contextReads.get()); assertEquals(callsBefore, f.calls.get());
            }

            f.policy = new Policy(true, 20, 100000, 50000, 4, 1, java.time.Duration.ofSeconds(60));
            Provider invalidProvider = new Provider() {
                public Metadata metadata() { return new Metadata("synthetic-model", 20, InvestigationAiAccessTest.NOW.plusSeconds(900), 1); }
                public Completion complete(Context context) { return new Completion("{\"hypotheses\":[],\"nextSteps\":[],\"tool_calls\":[]}", new Usage(1, 1)); }
            };
            try (var invalid = new InvestigationAiAccess(f.source, InvestigationAiAccessTest.CLOCK,
                    (actors, authorizer, contexts) -> new SecuredAiGateway(actors, authorizer, contexts,
                            invalidProvider, f.ledger, f.policy, InvestigationAiAccessTest.CLOCK))) {
                var api = api(f, invalid, sessions);
                api.mvc.perform(request(f, api.session, valid)).andExpect(status().isBadGateway())
                        .andExpect(jsonPath("$.error.code").value("AI_INVALID_OUTPUT"));
            }

            @SuppressWarnings("unchecked") SessionRepository<Session> broken = mock(SessionRepository.class);
            when(broken.findById(anyString())).thenThrow(new IllegalStateException("synthetic session failure"));
            try (var access = f.service()) {
                var api = api(f, access, broken);
                api.mvc.perform(request(f, api.session, valid)).andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE"));
            }
        }
    }

    @Test void controllerWiringHasNoGatewayOrTransportDependency() {
        Set<String> forbidden = Set.of("com.tracedesk.ai.gateway", "com.tracedesk.ai.internal", "com.tracedesk.ai.transport");
        for (var field : InvestigationAiController.class.getDeclaredFields())
            assertTrue(forbidden.stream().noneMatch(field.getType().getName()::startsWith), field.toString());
        for (var constructor : InvestigationAiController.class.getDeclaredConstructors())
            for (var type : constructor.getParameterTypes())
                assertTrue(forbidden.stream().noneMatch(type.getName()::startsWith), constructor.toString());
        assertTrue(List.of(InvestigationAiController.class.getDeclaredFields()).stream()
                .anyMatch(field -> field.getType().equals(InvestigationAiAccess.class)));
    }

    private record Api(MockMvc mvc, MockHttpSession session) { }
    private static Api api(Fixture f, InvestigationAiAccess access,
                           SessionRepository<? extends Session> sessions) {
        AuthStore events = mock(AuthStore.class);
        MockMvc mvc = standaloneSetup(new InvestigationAiController(access, InvestigationAiAccessTest.CLOCK, events, sessions))
                .setControllerAdvice(new ApiFailureHandler(events, InvestigationAiAccessTest.CLOCK))
                .addFilters(new InvestigationBodyLimitFilter(events)).build();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("td.actor", f.developer); session.setAttribute("td.authAt", InvestigationAiAccessTest.CLOCK.millis());
        session.setAttribute("td.csrf", CSRF);
        return new Api(mvc, session);
    }
    @SuppressWarnings("unchecked") private static SessionRepository<Session> sessions(UUID actor) {
        SessionRepository<Session> sessions = mock(SessionRepository.class);
        when(sessions.findById(anyString())).thenReturn(persisted(actor));
        return sessions;
    }
    private static Session persisted(UUID actor) {
        MapSession session = new MapSession(); session.setAttribute("td.actor", actor);
        session.setAttribute("td.authAt", InvestigationAiAccessTest.CLOCK.millis()); return session;
    }
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
            Fixture f, MockHttpSession session, String body) {
        return post(route(f)).session(session).header("X-CSRF-TOKEN", CSRF).contentType("application/json").content(body);
    }
    private static String route(Fixture f) {
        return "/api/projects/" + f.project + "/reports/" + f.report + "/investigation/suggestions";
    }
    private static String note(UUID id, int version) { return "{\"id\":\"" + id + "\",\"expectedVersion\":" + version + "}"; }
    private static String body(int version, List<String> fields, List<String> notes) {
        return "{\"expectedVersion\":" + version + ",\"reportFields\":[" + fields.stream().map(v -> "\"" + v + "\"").collect(java.util.stream.Collectors.joining(","))
                + "],\"notes\":[" + String.join(",", notes) + "]}";
    }
    private static List<String> fiveNotes(Fixture f) {
        var ids = new java.util.ArrayList<UUID>(); ids.add(f.note);
        for (int i = 1; i < 5; i++) { UUID id = UUID.randomUUID(); f.insertNote(id, f.report); ids.add(id); }
        return ids.stream().map(id -> note(id, 1)).toList();
    }
    private static List<String> sixNotes(Fixture f) {
        var notes = new java.util.ArrayList<>(fiveNotes(f)); notes.add(note(UUID.randomUUID(), 1)); return notes;
    }
}
