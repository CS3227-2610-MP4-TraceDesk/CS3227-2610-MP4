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
        else if (isReportRoute(request.getRequestURI())) { try { store.event("REPORT_INVALID_REQUEST",reportRoute(request.getRequestURI()),400,TrustedSessionActor.forEvent(request,clock),null); } catch(DataAccessException eventFailure) { return failure(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE","Service unavailable"); } }
        return failure(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Invalid request");
    }
    @ExceptionHandler(ReportService.Failure.class)
    ResponseEntity<?> reportFailure(ReportService.Failure problem,HttpServletRequest request) {
        if ("INVALID_REQUEST".equals(problem.code()) && isReportRoute(request.getRequestURI())) {
            try { store.event("REPORT_INVALID_REQUEST",reportRoute(request.getRequestURI()),problem.status(),TrustedSessionActor.forEvent(request,clock),null); }
            catch(DataAccessException eventFailure) { return failure(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE","Service unavailable"); }
        }
        HttpStatus status=HttpStatus.valueOf(problem.status());
        String message=switch(problem.code()){case "UNAUTHENTICATED"->"Authentication required";case "NOT_FOUND"->"Not found";case "FORBIDDEN"->"Forbidden";default->"Invalid request";};
        return failure(status,problem.code(),message);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception ignored) { return failure(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE","Service unavailable"); }

    private static boolean isReportRoute(String path) { return path.matches("/api/projects/[^/]+/(reports(?:/[^/]+)?|reporter-context)"); }
    private static String reportRoute(String path) {
        if(path.endsWith("/reporter-context")) return "/api/projects/{projectId}/reporter-context";
        return path.matches(".*/reports/[^/]+$") ? "/api/projects/{projectId}/reports/{reportId}" : "/api/projects/{projectId}/reports";
    }
}
