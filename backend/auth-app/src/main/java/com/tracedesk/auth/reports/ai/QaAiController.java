package com.tracedesk.auth.reports.ai;

import com.tracedesk.auth.persistence.AuthStore;
import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.security.TrustedSessionActor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Map;

@RestController
public final class QaAiController {
    private static final String ROUTE = "/api/projects/{projectId}/reports/{reportId}/qa-suggestions";
    private final QaAiService service;
    private final Clock clock;
    private final AuthStore events;
    private final SessionRepository<? extends Session> sessions;

    public QaAiController(QaAiService service, Clock clock, AuthStore events, SessionRepository<? extends Session> sessions) {
        this.service = service;
        this.clock = clock;
        this.events = events;
        this.sessions = sessions;
    }

    @PostMapping(ROUTE)
    public ResponseEntity<?> review(@PathVariable String projectId, @PathVariable String reportId,
                                    @RequestBody String body, HttpServletRequest request) {
        try {
            var project = ReportAiController.uuid(projectId);
            var report = ReportAiController.uuid(reportId);
            int version = version(body, request);
            var session = request.getSession(false);
            Object token = session == null ? null : session.getAttribute("td.csrf");
            String supplied = request.getHeader("X-CSRF-TOKEN");
            if (!(token instanceof String expected) || supplied == null || !MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
                throw new ReportService.Failure("CSRF_INVALID", 403);
            return ResponseEntity.ok().header("Cache-Control", "no-store").body(
                    service.review(project, report, version, () -> ReportAiController.freshActor(request, clock, sessions)));
        } catch (ReportService.Failure failure) {
            String event = switch (failure.code()) {
                case "UNAUTHENTICATED", "CSRF_INVALID" -> failure.code();
                case "NOT_FOUND", "FORBIDDEN" -> "REPORT_DENIED";
                default -> null;
            };
            if (event != null) {
                try { events.event(event, "/api/projects/{projectId}/reports/{reportId}",
                        failure.status(), TrustedSessionActor.forEvent(request, clock), null); }
                catch (RuntimeException unavailable) { return error(503, "SERVICE_UNAVAILABLE"); }
            }
            return error(failure.status(), failure.code());
        }
    }

    private static int version(String raw, HttpServletRequest request) {
        try {
            var body = ReportAiOutput.JSON.readTree(raw);
            if (body == null || !body.isObject() || body.size() != 1 || !request.getParameterMap().isEmpty())
                throw ReportAiController.invalid();
            var value = body.path("expectedVersion");
            if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 1)
                throw ReportAiController.invalid();
            return value.asInt();
        } catch (RuntimeException failure) { throw ReportAiController.invalid(); }
    }

    private static ResponseEntity<?> error(int status, String code) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store")
                .body(Map.of("error", Map.of("code", code, "message", "QA review could not be generated")));
    }
}
