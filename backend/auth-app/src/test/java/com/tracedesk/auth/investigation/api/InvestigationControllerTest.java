package com.tracedesk.auth.investigation.api;

import com.tracedesk.auth.api.ApiFailureHandler;
import com.tracedesk.auth.investigation.application.InvestigationRepository;
import com.tracedesk.auth.investigation.application.InvestigationService;
import com.tracedesk.auth.investigation.domain.InvestigationNoteData;
import com.tracedesk.auth.persistence.AuthStore;
import com.tracedesk.auth.reports.application.ReportRepository;
import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.reports.domain.ReportCursorSigner;
import com.tracedesk.auth.reports.domain.ReportData;
import com.tracedesk.auth.security.InvestigationBodyLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class InvestigationControllerTest {
    private static final Instant NOW = Instant.parse("2026-11-08T02:03:04Z");

    @Test void trustedDeveloperContextStartAndPrivateNoteRepresentationsAreAllowlisted() throws Exception {
        UUID actor=UUID.randomUUID(), project=UUID.randomUUID(), report=UUID.randomUUID(), note=UUID.randomUUID();
        InvestigationRepository repository=mock(InvestigationRepository.class); AuthStore events=mock(AuthStore.class);
        when(repository.membership(actor,project)).thenReturn(Optional.of(membership(project,"DEVELOPER")));
        var started=report(report,project,actor,"IN_PROGRESS",2);
        when(repository.start(eq(actor),eq(project),eq(report),eq(1),any(),eq(NOW))).thenReturn(started);
        var created=new InvestigationRepository.Note(note,report,actor,null,"private canary",NOW,NOW,1);
        when(repository.createNote(eq(actor),eq(project),eq(report),any(),any(InvestigationNoteData.class),any(),eq(NOW))).thenReturn(created);
        when(repository.notes(actor,project,report,25,null,null)).thenReturn(new InvestigationRepository.NotePage(List.of(created),false));
        MockMvc mvc=mvc(repository,events);
        MockHttpSession session=session(actor);

        mvc.perform(get("/api/projects/{projectId}/developer-context",project).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("DEVELOPER"))
                .andExpect(jsonPath("$.project.id").value(project.toString())).andExpect(jsonPath("$.userId").doesNotExist());
        mvc.perform(post("/api/projects/{projectId}/reports/{reportId}/investigation/start",project,report)
                        .session(session).header("X-CSRF-TOKEN","known-token").contentType("application/json")
                        .content("{\"expectedVersion\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.notes").doesNotExist());
        mvc.perform(post("/api/projects/{projectId}/reports/{reportId}/notes",project,report)
                        .session(session).header("X-CSRF-TOKEN","known-token").header("Idempotency-Key",UUID.randomUUID())
                        .contentType("application/json").content("{\"body\":\"private canary\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.body").value("private canary"))
                .andExpect(jsonPath("$.authorUserId").value(actor.toString()))
                .andExpect(jsonPath("$.projectId").doesNotExist()).andExpect(jsonPath("$.status").doesNotExist());
        mvc.perform(get("/api/projects/{projectId}/reports/{reportId}/notes",project,report).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(note.toString()))
                .andExpect(jsonPath("$.items[0].body").value("private canary"));
    }

    @Test void shapeCsrfSessionRoleAndAssignmentFailuresFollowPrecedence() throws Exception {
        UUID actor=UUID.randomUUID(), project=UUID.randomUUID(), report=UUID.randomUUID();
        InvestigationRepository repository=mock(InvestigationRepository.class); AuthStore events=mock(AuthStore.class);
        when(repository.membership(actor,project)).thenReturn(Optional.of(membership(project,"REPORTER")));
        MockMvc mvc=mvc(repository,events); MockHttpSession session=session(actor);

        mvc.perform(post("/api/projects/{projectId}/reports/{reportId}/investigation/start",project,report)
                        .session(session).contentType("application/json").content("{\"expectedVersion\":1,\"actorId\":\""+actor+"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/projects/{projectId}/reports/{reportId}/investigation/start",project,report)
                        .session(session).contentType("application/json").content("{\"expectedVersion\":1}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/projects/{projectId}/reports/{reportId}/notes",project,report)
                        .session(session).header("X-CSRF-TOKEN","known-token").header("Idempotency-Key",UUID.randomUUID())
                        .contentType("application/json").content("{\"body\":\" \"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        verify(repository,never()).createNote(any(),any(),any(),any(),any(),any(),any());

        when(repository.membership(actor,project)).thenReturn(Optional.of(membership(project,"DEVELOPER")));
        when(repository.start(eq(actor),eq(project),eq(report),eq(1),any(),eq(NOW)))
                .thenThrow(new ReportService.Failure("FORBIDDEN",403));
        when(repository.start(eq(actor),eq(project),eq(report),eq(0),any(),eq(NOW)))
                .thenThrow(new ReportService.Failure("FORBIDDEN",403));
        when(repository.createNote(eq(actor),eq(project),eq(report),any(),any(),any(),eq(NOW)))
                .thenThrow(new ReportService.Failure("FORBIDDEN",403));
        when(repository.updateNote(eq(actor),eq(project),eq(report),any(),anyInt(),any(),any(),eq(NOW)))
                .thenThrow(new ReportService.Failure("FORBIDDEN",403));
        mvc.perform(post("/api/projects/{projectId}/reports/{reportId}/investigation/start",project,report)
                        .session(session).header("X-CSRF-TOKEN","known-token").contentType("application/json")
                        .content("{\"expectedVersion\":1}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(post("/api/projects/{projectId}/reports/{reportId}/investigation/start",project,report)
                        .session(session).header("X-CSRF-TOKEN","known-token").contentType("application/json")
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        for (String body : List.of(" ", "valid but unassigned")) {
            mvc.perform(post("/api/projects/{projectId}/reports/{reportId}/notes",project,report)
                            .session(session).header("X-CSRF-TOKEN","known-token").header("Idempotency-Key",UUID.randomUUID())
                            .contentType("application/json").content("{\"body\":\""+body+"\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
        for (String patchBody : List.of("{\"body\":\" \",\"expectedVersion\":1}",
                "{\"body\":\"valid but unassigned\",\"expectedVersion\":0}")) {
            mvc.perform(patch("/api/projects/{projectId}/reports/{reportId}/notes/{noteId}",project,report,UUID.randomUUID())
                            .session(session).header("X-CSRF-TOKEN","known-token").contentType("application/json").content(patchBody))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
        verify(events,times(2)).event("DEVELOPER_DENIED","/api/projects/{projectId}/reports/{reportId}/investigation/start",403,actor,project);
        verify(events,times(3)).event("DEVELOPER_DENIED","/api/projects/{projectId}/reports/{reportId}/notes",403,actor,project);
        verify(events,times(2)).event("DEVELOPER_DENIED","/api/projects/{projectId}/reports/{reportId}/notes/{noteId}",403,actor,project);
    }

    @Test void deleteWithMissingOrMalformedExpectedVersionIsInvalidRequestNotServiceUnavailable() throws Exception {
        UUID actor=UUID.randomUUID(), project=UUID.randomUUID(), report=UUID.randomUUID(), note=UUID.randomUUID();
        InvestigationRepository repository=mock(InvestigationRepository.class); AuthStore events=mock(AuthStore.class);
        when(repository.membership(actor,project)).thenReturn(Optional.of(membership(project,"DEVELOPER")));
        MockMvc mvc=mvc(repository,events); MockHttpSession session=session(actor);
        String route="/api/projects/{projectId}/reports/{reportId}/notes/{noteId}";
        for (String query : List.of("", "?expectedVersion=", "?expectedVersion=abc", "?expectedVersion=-1")) {
            mvc.perform(delete("/api/projects/"+project+"/reports/"+report+"/notes/"+note+query)
                            .session(session).header("X-CSRF-TOKEN","known-token"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        }
        verify(events,times(4)).event("DEVELOPER_INVALID_REQUEST",route,400,actor,null);
        verify(repository,never()).deleteNote(any(),any(),any(),any(),anyInt(),any(),any());
    }

    @Test void bodyLimitFailsClosedBeforeController() throws Exception {
        InvestigationRepository repository=mock(InvestigationRepository.class); AuthStore events=mock(AuthStore.class);
        MockMvc mvc=standaloneSetup(controller(repository,events)).setControllerAdvice(new ApiFailureHandler(events))
                .addFilters(new InvestigationBodyLimitFilter(events)).build();
        mvc.perform(post("/api/projects/{projectId}/reports/{reportId}/notes",UUID.randomUUID(),UUID.randomUUID())
                        .contentType("application/json").content("{\"body\":\""+"x".repeat(70_000)+"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verify(events).event(eq("DEVELOPER_INVALID_REQUEST"),eq("/api/projects/{projectId}/reports/{reportId}/notes"),eq(400),isNull(),isNull());

        mvc.perform(delete("/api/projects/{projectId}/reports/{reportId}/notes/{noteId}?expectedVersion=1",
                        UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verify(events).event(eq("DEVELOPER_INVALID_REQUEST"),eq("/api/projects/{projectId}/reports/{reportId}/notes/{noteId}"),eq(400),isNull(),isNull());
    }

    private static MockMvc mvc(InvestigationRepository repository,AuthStore events) {
        return standaloneSetup(controller(repository,events)).setControllerAdvice(new ApiFailureHandler(events)).build();
    }
    private static InvestigationController controller(InvestigationRepository repository,AuthStore events) {
        Clock clock=Clock.fixed(NOW, ZoneOffset.UTC);
        return new InvestigationController(new InvestigationService(repository,new ReportCursorSigner(),clock),events,clock);
    }
    private static ReportRepository.Membership membership(UUID project,String role) {
        return new ReportRepository.Membership(project,"Synthetic Project",true,true,true,role);
    }
    private static ReportRepository.Report report(UUID id,UUID project,UUID developer,String status,int version) {
        return new ReportRepository.Report(id,project,UUID.randomUUID(),new ReportData("title","description","environment","steps","expected","actual",""),
                status,developer,NOW,NOW,version);
    }
    private static MockHttpSession session(UUID actor) {
        MockHttpSession session=new MockHttpSession(); session.setAttribute("td.actor",actor);
        session.setAttribute("td.authAt",NOW.toEpochMilli()); session.setAttribute("td.csrf","known-token"); return session;
    }
}
