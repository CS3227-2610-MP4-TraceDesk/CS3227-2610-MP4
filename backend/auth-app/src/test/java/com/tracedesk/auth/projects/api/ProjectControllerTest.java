package com.tracedesk.auth.projects.api;

import com.tracedesk.auth.api.ApiFailureHandler;
import com.tracedesk.auth.persistence.AuthStore;
import com.tracedesk.auth.projects.application.ProjectRepository;
import com.tracedesk.auth.projects.application.ProjectService;
import com.tracedesk.auth.projects.domain.ProjectDirectoryCursor;
import com.tracedesk.auth.security.ProjectJoinBodyProbeFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ProjectControllerTest {
    private static final Clock CLOCK=Clock.fixed(Instant.parse("2026-11-11T00:00:00Z"),ZoneOffset.UTC);
    private static final String SECRET="synthetic-project-cursor-secret-for-tests-0001";
    private record Fixture(org.springframework.test.web.servlet.MockMvc mvc,AuthStore auth,ProjectRepository repo,UUID actor) { }

    @Test void anonymousAndCsrfDenialsAreRecordedBeforeBusinessCalls() throws Exception {
        Fixture f=fixture();
        f.mvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        verify(f.auth).event("UNAUTHENTICATED","/api/projects",401,null,null);
        f.mvc.perform(post("/api/projects").session(session(f.actor,false)).contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key",UUID.randomUUID().toString()).content("{\"name\":\"Safe\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
        verify(f.auth).event("CSRF_INVALID","/api/projects",403,f.actor,null);
        verifyNoInteractions(f.repo);
    }

    @Test void duplicateNullAndWrongTypeNameKeysAreRejectedByApi() throws Exception {
        Fixture f=fixture();
        for(String body:List.of("{\"name\":\"Safe\",\"name\":null}","{\"name\":7,\"name\":\"Safe\"}")) {
            f.mvc.perform(post("/api/projects").session(session(f.actor,true)).contentType(MediaType.APPLICATION_JSON)
                            .header("X-CSRF-TOKEN","synthetic-csrf-token")
                            .header("Idempotency-Key",UUID.randomUUID().toString()).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        }
        verify(f.auth,times(2)).event("PROJECT_INVALID_REQUEST","/api/projects",400,f.actor,null);
        verify(f.repo,never()).create(any(),anyString(),any(),any());
    }

    @Test void malformedAndEmptyCreateBodiesAreRejectedAndAudited() throws Exception {
        Fixture f=fixture();
        f.mvc.perform(post("/api/projects").session(session(f.actor,true)).contentType(MediaType.APPLICATION_JSON)
                        .header("X-CSRF-TOKEN","synthetic-csrf-token")
                        .header("Idempotency-Key",UUID.randomUUID().toString()).content("{\"name\":"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        f.mvc.perform(post("/api/projects").session(session(f.actor,true)).contentType(MediaType.APPLICATION_JSON)
                        .header("X-CSRF-TOKEN","synthetic-csrf-token")
                        .header("Idempotency-Key",UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verify(f.auth,times(2)).event("PROJECT_INVALID_REQUEST","/api/projects",400,f.actor,null);
        verify(f.repo,never()).create(any(),anyString(),any(),any());
    }

    @Test void joinRejectsNonemptyUnknownLengthBodyAndAllowsEmptyBody() throws Exception {
        Fixture f=fixture(); UUID project=UUID.randomUUID();
        var wrappedUnknownLength=(jakarta.servlet.Filter)(request,response,chain)->{
            HttpServletRequest wrapped=new HttpServletRequestWrapper((HttpServletRequest)request){
                @Override public long getContentLengthLong(){return -1;}
            };
            chain.doFilter(wrapped,response);
        };
        var mvc=standaloneSetup(new ProjectController(new ProjectService(f.repo,new ProjectDirectoryCursor(SECRET)),f.auth,CLOCK))
                .setControllerAdvice(new ApiFailureHandler(f.auth,CLOCK))
                .addFilters(wrappedUnknownLength,new ProjectJoinBodyProbeFilter()).build();
        mvc.perform(post("/api/projects/{id}/join",project).session(session(f.actor,true)).header("X-CSRF-TOKEN","synthetic-csrf-token").content("x"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verify(f.auth).event("PROJECT_INVALID_REQUEST","/api/projects/{projectId}/join",400,f.actor,project);
        when(f.repo.join(eq(f.actor),eq(project),any())).thenReturn(new ProjectRepository.Created(project,"Public directory name"));
        mvc.perform(post("/api/projects/{id}/join",project).session(session(f.actor,true)).header("X-CSRF-TOKEN","synthetic-csrf-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.membership").value("ACTIVE"));
        verify(f.repo).join(eq(f.actor),eq(project),any());
    }

    @Test void directoryRejectsBadBoundsAndCursorAndReturnsOnlySafeFields() throws Exception {
        Fixture f=fixture(); UUID project=UUID.randomUUID();
        when(f.repo.directory(eq(f.actor),eq(50),isNull(),isNull())).thenReturn(
                new ProjectRepository.Page(List.of(new ProjectRepository.Summary(project,"Café Project","NONE","café project")),false,null));
        f.mvc.perform(get("/api/projects").session(session(f.actor,true)).param("limit","0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        f.mvc.perform(get("/api/projects").session(session(f.actor,true)).param("cursor","forged"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        f.mvc.perform(get("/api/projects").session(session(f.actor,true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(project.toString()))
                .andExpect(jsonPath("$.items[0].name").value("Café Project"))
                .andExpect(jsonPath("$.items[0].membership").value("NONE"))
                .andExpect(jsonPath("$.items[0].role").doesNotExist())
                .andExpect(jsonPath("$.items[0].members").doesNotExist());
        verify(f.auth,times(2)).event("PROJECT_INVALID_REQUEST","/api/projects",400,f.actor,null);
    }

    @Test void eventSinkFailureFailsClosed() throws Exception {
        Fixture f=fixture();
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic"))
                .when(f.auth).event("PROJECT_INVALID_REQUEST","/api/projects",400,f.actor,null);
        f.mvc.perform(get("/api/projects").session(session(f.actor,true)).param("limit","0"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE"));
        verify(f.repo,never()).directory(any(),anyInt(),any(),any());
    }

    private static Fixture fixture(){
        AuthStore auth=mock(AuthStore.class);ProjectRepository repo=mock(ProjectRepository.class);UUID actor=UUID.randomUUID();
        when(auth.accountRole(actor)).thenReturn(Optional.of("REPORTER"));
        ProjectService projects=new ProjectService(repo,new ProjectDirectoryCursor(SECRET));
        var mvc=standaloneSetup(new ProjectController(projects,auth,CLOCK)).setControllerAdvice(new ApiFailureHandler(auth,CLOCK))
                .addFilters(new ProjectJoinBodyProbeFilter()).build();
        return new Fixture(mvc,auth,repo,actor);
    }
    private static MockHttpSession session(UUID actor,boolean csrf){
        MockHttpSession session=new MockHttpSession();session.setAttribute("td.actor",actor);session.setAttribute("td.authAt",CLOCK.millis());
        if(csrf)session.setAttribute("td.csrf","synthetic-csrf-token");return session;
    }
}
