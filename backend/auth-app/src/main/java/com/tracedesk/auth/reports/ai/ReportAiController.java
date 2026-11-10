package com.tracedesk.auth.reports.ai;

import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.persistence.AuthStore;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import com.tracedesk.auth.security.TrustedSessionActor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;

@RestController
public final class ReportAiController {
    private final ReportAiService service; private final Clock clock;
    private final AuthStore events; private final SessionRepository<? extends Session> sessions;
    public ReportAiController(ReportAiService service,Clock clock,AuthStore events,SessionRepository<? extends Session> sessions){
        this.service=service;this.clock=clock;this.events=events;this.sessions=sessions;
    }
    @PostMapping("/api/projects/{projectId}/reports/{sourceId}/suggestions")
    public ResponseEntity<?> report(@PathVariable String projectId,@PathVariable String sourceId,@RequestBody String body,HttpServletRequest request) {
        return suggest(ReportAiService.SourceKind.REPORT,projectId,sourceId,body,request);
    }
    @PostMapping("/api/projects/{projectId}/drafts/{sourceId}/suggestions")
    public ResponseEntity<?> draft(@PathVariable String projectId,@PathVariable String sourceId,@RequestBody String body,HttpServletRequest request) {
        return suggest(ReportAiService.SourceKind.DRAFT,projectId,sourceId,body,request);
    }
    private ResponseEntity<?> suggest(ReportAiService.SourceKind kind,String projectId,String sourceId,String raw,HttpServletRequest request) {
        try {
            UUID project=uuid(projectId),source=uuid(sourceId);int version;
            try {
                var body=ReportAiOutput.JSON.readTree(raw);
                if(body==null||!body.isObject()||body.size()!=1||!body.has("expectedVersion")) throw invalid();
                var v=body.get("expectedVersion");
                if(!v.isIntegralNumber()||!v.canConvertToInt()||v.asInt()<1) throw invalid();version=v.asInt();
                if(!request.getParameterMap().isEmpty()) throw invalid();
            } catch(RuntimeException failure){throw invalid();}
            var session=request.getSession(false);Object token=session==null?null:session.getAttribute("td.csrf");String supplied=request.getHeader("X-CSRF-TOKEN");
            if(!(token instanceof String expected)||supplied==null||!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))
                throw new ReportService.Failure("CSRF_INVALID",403);
            return ResponseEntity.ok().header("Cache-Control","no-store").body(service.suggest(kind,project,source,version,()->freshActor(request,clock,sessions)));
        } catch(ReportService.Failure failure) {
            if(Set.of("FORBIDDEN","NOT_FOUND","UNAUTHENTICATED","CSRF_INVALID").contains(failure.code())) {
                String event=switch(failure.code()){case "UNAUTHENTICATED","CSRF_INVALID"->failure.code();default->"REPORT_DENIED";};
                String route=kind==ReportAiService.SourceKind.DRAFT?"/api/projects/{projectId}/drafts/{draftId}":"/api/projects/{projectId}/reports/{reportId}";
                try {events.event(event,route,failure.status(),TrustedSessionActor.forEvent(request,clock),null);}
                catch(RuntimeException unavailable){failure=new ReportService.Failure("SERVICE_UNAVAILABLE",503);}
            }
            String message=switch(failure.code()) {
                case "UNAUTHENTICATED" -> "Authentication required";
                case "FORBIDDEN", "CSRF_INVALID" -> "Forbidden";
                case "NOT_FOUND" -> "Not found";
                case "STALE_VERSION" -> "A newer version is available";
                case "AI_CONTEXT_TOO_LARGE" -> "Shorten the saved report before requesting AI review";
                case "SERVICE_UNAVAILABLE" -> "Service unavailable";
                case "AI_UNAVAILABLE" -> "AI review is unavailable";
                case "AI_INVALID_OUTPUT" -> "AI returned an invalid suggestion";
                case "DRAFT_EXPIRED" -> "Draft expired";
                case "INVALID_STATE" -> "The report or draft can no longer be changed";
                default -> "Invalid request";
            };
            return ResponseEntity.status(failure.status()).header("Cache-Control","no-store").body(Map.of("error",Map.of("code",failure.code(),"message",message)));
        }
    }
    /** Spring Session's request wrapper caches its HttpSession: always re-read persisted authority. */
    static UUID freshActor(HttpServletRequest request, Clock clock, SessionRepository<? extends Session> sessions) {
        try {
            UUID cached=TrustedSessionActor.resolve(request,clock);
            var cachedSession=request.getSession(false);
            if(cached==null||cachedSession==null) return null;
            Session persisted=sessions.findById(cachedSession.getId());
            if(persisted==null||persisted.isExpired()) return null;
            Object actor=persisted.getAttribute("td.actor"),at=persisted.getAttribute("td.authAt");
            if(!cached.equals(actor)||!(at instanceof Long authenticatedAt)) return null;
            long age=clock.millis()-authenticatedAt;
            return age>=0&&age<8L*60*60*1000?cached:null;
        } catch(RuntimeException failure){throw new ReportService.Failure("SERVICE_UNAVAILABLE",503);}
    }
    static UUID uuid(String text){try{UUID id=UUID.fromString(text);if(!id.toString().equals(text))throw invalid();return id;}catch(RuntimeException failure){throw invalid();}}
    static ReportService.Failure invalid(){return new ReportService.Failure("INVALID_REQUEST",400);}
}
