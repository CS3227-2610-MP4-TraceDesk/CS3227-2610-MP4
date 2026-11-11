package com.tracedesk.auth.api;

import com.tracedesk.auth.persistence.AuthStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.beans.factory.annotation.Autowired;
import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.security.TrustedSessionActor;
import java.time.Clock;
import java.util.UUID;

@RestControllerAdvice
public class ApiFailureHandler {
    private final AuthStore store;
    private final Clock clock;
    @Autowired public ApiFailureHandler(AuthStore store, Clock clock) { this.store=store; this.clock=clock; }
    public ApiFailureHandler(AuthStore store) { this(store, Clock.systemUTC()); }
    private ResponseEntity<?> failure(HttpStatus status,String code,String message) {
        return ResponseEntity.status(status).body(java.util.Map.of("error",java.util.Map.of("code",code,"message",message)));
    }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> unavailable(DataAccessException ignored) { return failure(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE","Service unavailable"); }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<?> malformed(HttpMessageNotReadableException ignored,HttpServletRequest request) {
        if("/api/session/login".equals(request.getRequestURI())) { try { store.event("LOGIN_INVALID_REQUEST","/api/session/login",400,null,null); } catch(DataAccessException eventFailure) { return failure(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE","Service unavailable"); } }
        else if("/api/session/register".equals(request.getRequestURI())) { try { store.event("REGISTRATION_INVALID_REQUEST","/api/session/register",400,null,null); } catch(DataAccessException eventFailure) { return failure(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE","Service unavailable"); } }
        else if ("/api/projects".equals(request.getRequestURI())) { try { store.event("PROJECT_INVALID_REQUEST","/api/projects",400,TrustedSessionActor.forEvent(request,clock),null); } catch(DataAccessException eventFailure) { return failure(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE","Service unavailable"); } }
        else if (request.getRequestURI().matches("/api/projects/[^/]+/join")) { try { store.event("PROJECT_INVALID_REQUEST","/api/projects/{projectId}/join",400,TrustedSessionActor.forEvent(request,clock),canonicalProject(request.getRequestURI())); } catch(DataAccessException eventFailure) { return failure(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE","Service unavailable"); } }
        else if (isReportRoute(request.getRequestURI())) { try { store.event("REPORT_INVALID_REQUEST",reportRoute(request.getRequestURI()),400,TrustedSessionActor.forEvent(request,clock),null); } catch(DataAccessException eventFailure) { return failure(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE","Service unavailable"); } }
        else if (isInvestigationRoute(request.getRequestURI())) { try { store.event("DEVELOPER_INVALID_REQUEST",investigationRoute(request.getRequestURI()),400,TrustedSessionActor.forEvent(request,clock),null); } catch(DataAccessException eventFailure) { return failure(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE","Service unavailable"); } }
        return failure(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Invalid request");
    }
    @ExceptionHandler(ReportService.Failure.class)
    ResponseEntity<?> reportFailure(ReportService.Failure problem,HttpServletRequest request) {
        if ("INVALID_REQUEST".equals(problem.code()) && isReportRoute(request.getRequestURI())) {
            try { store.event("REPORT_INVALID_REQUEST",reportRoute(request.getRequestURI()),problem.status(),TrustedSessionActor.forEvent(request,clock),null); }
            catch(DataAccessException eventFailure) { return failure(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE","Service unavailable"); }
        }
        else if ("INVALID_REQUEST".equals(problem.code()) && isInvestigationRoute(request.getRequestURI())) {
            try { store.event("DEVELOPER_INVALID_REQUEST",investigationRoute(request.getRequestURI()),problem.status(),TrustedSessionActor.forEvent(request,clock),null); }
            catch(DataAccessException eventFailure) { return failure(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE","Service unavailable"); }
        }
        HttpStatus status=HttpStatus.valueOf(problem.status());
        String message=switch(problem.code()){case "UNAUTHENTICATED"->"Authentication required";case "NOT_FOUND"->"Not found";case "FORBIDDEN"->"Forbidden";default->"Invalid request";};
        return failure(status,problem.code(),message);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception ignored) { return failure(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE","Service unavailable"); }

    private static boolean isReportRoute(String path) { return path.matches("/api/projects/[^/]+/(reports(?:/[^/]+(?:/assignment)?)?|reporter-context)"); }
    private static boolean isInvestigationRoute(String path) { return path.matches("/api/projects/[^/]+/(developer-context|reports/[^/]+/(investigation/start|resolutions|notes(?:/[^/]+)?))"); }
    private static String investigationRoute(String path) {
        if(path.endsWith("/developer-context")) return "/api/projects/{projectId}/developer-context";
        if(path.endsWith("/investigation/start")) return "/api/projects/{projectId}/reports/{reportId}/investigation/start";
        if(path.matches(".*/reports/[^/]+/resolutions$")) return "/api/projects/{projectId}/reports/{reportId}/resolutions";
        if(path.matches(".*/notes/[^/]+$")) return "/api/projects/{projectId}/reports/{reportId}/notes/{noteId}";
        return "/api/projects/{projectId}/reports/{reportId}/notes";
    }
    private static String reportRoute(String path) {
        if(path.endsWith("/reporter-context")) return "/api/projects/{projectId}/reporter-context";
        return path.matches(".*/reports/[^/]+(?:/assignment)?$") ? "/api/projects/{projectId}/reports/{reportId}" : "/api/projects/{projectId}/reports";
    }
    private static UUID canonicalProject(String path) {
        try { String raw=path.substring("/api/projects/".length(),path.length()-"/join".length()); UUID id=UUID.fromString(raw); return id.toString().equals(raw)?id:null; }
        catch(RuntimeException ignored){return null;}
    }
}
