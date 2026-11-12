package com.tracedesk.auth.investigation.api;

import com.tracedesk.auth.investigation.application.InvestigationRepository;
import com.tracedesk.auth.investigation.application.InvestigationService;
import com.tracedesk.auth.investigation.domain.InvestigationNoteData;
import com.tracedesk.auth.investigation.domain.ProposedResolutionData;
import com.tracedesk.auth.persistence.AuthStore;
import com.tracedesk.auth.reports.application.ReportRepository;
import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.security.TrustedSessionActor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;

@RestController
public class InvestigationController {
    private static final String CONTEXT = "/api/projects/{projectId}/developer-context";
    private static final String START = "/api/projects/{projectId}/reports/{reportId}/investigation/start";
    private static final String NOTES = "/api/projects/{projectId}/reports/{reportId}/notes";
    private static final String NOTE = "/api/projects/{projectId}/reports/{reportId}/notes/{noteId}";
    private static final String RESOLUTIONS = "/api/projects/{projectId}/reports/{reportId}/resolutions";
    private static final String CSRF = "td.csrf";
    private final InvestigationService service;
    private final AuthStore events;
    private final Clock clock;

    public InvestigationController(InvestigationService service, AuthStore events, Clock clock) {
        this.service = service; this.events = events; this.clock = clock;
    }

    @GetMapping(CONTEXT)
    public ResponseEntity<?> developerContext(@PathVariable String projectId, HttpServletRequest request) {
        UUID project = parseUuid(projectId), actor = TrustedSessionActor.resolve(request, clock);
        if (actor == null) return denied("UNAUTHENTICATED", CONTEXT, 401, null, project, "UNAUTHENTICATED");
        try {
            var membership = service.developerContext(actor, project);
            return ResponseEntity.ok(Map.of("project", Map.of("id", membership.projectId(), "name", membership.projectName()),
                    "role", "DEVELOPER"));
        } catch (ReportService.Failure failure) { return failure(failure, CONTEXT, actor, project); }
    }

    @PostMapping(START)
    public ResponseEntity<?> start(@PathVariable String projectId, @PathVariable String reportId,
                                   @RequestBody JsonNode body, HttpServletRequest request) {
        UUID project = parseUuid(projectId), report = parseUuid(reportId), requestId = UUID.randomUUID();
        int expectedVersion = exactPositiveInt(body, Set.of("expectedVersion"), "expectedVersion");
        if (!csrf(request)) return denied("CSRF_INVALID", START, 403, trustedActor(request), project, "CSRF_INVALID");
        UUID actor = TrustedSessionActor.resolve(request, clock);
        if (actor == null) return denied("UNAUTHENTICATED", START, 401, null, project, "UNAUTHENTICATED");
        try { return ResponseEntity.ok(reportView(service.start(actor, project, report, expectedVersion, requestId))); }
        catch (ReportService.Failure failure) { return failure(failure, START, actor, project); }
    }

    @GetMapping(NOTES)
    public ResponseEntity<?> notes(@PathVariable String projectId, @PathVariable String reportId,
                                   @RequestParam(required=false) String limit, @RequestParam(required=false) String cursor,
                                   HttpServletRequest request) {
        UUID project = parseUuid(projectId), report = parseUuid(reportId);
        validateQuery(request, Set.of("limit", "cursor"));
        Integer parsedLimit = limit == null ? null : parsePositiveInt(limit);
        UUID actor = TrustedSessionActor.resolve(request, clock);
        if (actor == null) return denied("UNAUTHENTICATED", NOTES, 401, null, project, "UNAUTHENTICATED");
        try {
            var page = service.notes(actor, project, report, parsedLimit, cursor);
            Map<String,Object> response = new LinkedHashMap<>();
            response.put("items", page.items().stream().map(this::noteView).toList());
            response.put("nextCursor", page.nextCursor());
            return ResponseEntity.ok(response);
        } catch (ReportService.Failure failure) { return failure(failure, NOTES, actor, project); }
    }

    @PostMapping(NOTES)
    public ResponseEntity<?> createNote(@PathVariable String projectId, @PathVariable String reportId,
                                        @RequestHeader(value="Idempotency-Key", required=false) String key,
                                        @RequestBody JsonNode body, HttpServletRequest request) {
        UUID project = parseUuid(projectId), report = parseUuid(reportId), idempotencyKey = parseUuid(key);
        UUID requestId = UUID.randomUUID(); InvestigationNoteData data = noteCreateBody(body);
        if (!csrf(request)) return denied("CSRF_INVALID", NOTES, 403, trustedActor(request), project, "CSRF_INVALID");
        UUID actor = TrustedSessionActor.resolve(request, clock);
        if (actor == null) return denied("UNAUTHENTICATED", NOTES, 401, null, project, "UNAUTHENTICATED");
        try { return ResponseEntity.status(HttpStatus.CREATED).body(noteView(
                service.createNote(actor, project, report, idempotencyKey, data, requestId))); }
        catch (ReportService.Failure failure) { return failure(failure, NOTES, actor, project); }
    }

    @PatchMapping(NOTE)
    public ResponseEntity<?> updateNote(@PathVariable String projectId, @PathVariable String reportId,
                                        @PathVariable String noteId, @RequestBody JsonNode body,
                                        HttpServletRequest request) {
        UUID project = parseUuid(projectId), report = parseUuid(reportId), note = parseUuid(noteId), requestId = UUID.randomUUID();
        if (body == null || !body.isObject() || !set(body).equals(Set.of("body", "expectedVersion")) ||
                !body.path("body").isTextual() || !body.path("expectedVersion").isIntegralNumber() ||
                !body.path("expectedVersion").canConvertToInt()) throw fail("INVALID_REQUEST", 400);
        int expectedVersion = body.path("expectedVersion").intValue();
        InvestigationNoteData data = new InvestigationNoteData(body.path("body").textValue());
        if (!csrf(request)) return denied("CSRF_INVALID", NOTE, 403, trustedActor(request), project, "CSRF_INVALID");
        UUID actor = TrustedSessionActor.resolve(request, clock);
        if (actor == null) return denied("UNAUTHENTICATED", NOTE, 401, null, project, "UNAUTHENTICATED");
        try { return ResponseEntity.ok(noteView(service.updateNote(actor, project, report, note, expectedVersion, data, requestId))); }
        catch (ReportService.Failure failure) { return failure(failure, NOTE, actor, project); }
    }

    @DeleteMapping(NOTE)
    public ResponseEntity<?> deleteNote(@PathVariable String projectId, @PathVariable String reportId,
                                        @PathVariable String noteId,
                                        @RequestParam(required=false) String expectedVersion,
                                        HttpServletRequest request) {
        UUID project = parseUuid(projectId), report = parseUuid(reportId), note = parseUuid(noteId), requestId = UUID.randomUUID();
        validateQuery(request, Set.of("expectedVersion")); int version = parsePositiveInt(expectedVersion);
        if (!csrf(request)) return denied("CSRF_INVALID", NOTE, 403, trustedActor(request), project, "CSRF_INVALID");
        UUID actor = TrustedSessionActor.resolve(request, clock);
        if (actor == null) return denied("UNAUTHENTICATED", NOTE, 401, null, project, "UNAUTHENTICATED");
        try { service.deleteNote(actor, project, report, note, version, requestId); return ResponseEntity.noContent().build(); }
        catch (ReportService.Failure failure) { return failure(failure, NOTE, actor, project); }
    }

    @PostMapping(RESOLUTIONS)
    public ResponseEntity<?> propose(@PathVariable String projectId, @PathVariable String reportId,
                                     @RequestBody JsonNode body, HttpServletRequest request) {
        UUID project = parseUuid(projectId), report = parseUuid(reportId), requestId = UUID.randomUUID();
        ProposedResolutionData data = proposalData(body);
        int expectedVersion = body.path("expectedVersion").intValue();
        if (!csrf(request)) return denied("CSRF_INVALID", RESOLUTIONS, 403, trustedActor(request), project, "CSRF_INVALID");
        UUID actor = TrustedSessionActor.resolve(request, clock);
        if (actor == null) return denied("UNAUTHENTICATED", RESOLUTIONS, 401, null, project, "UNAUTHENTICATED");
        try {
            var proposal = service.propose(actor, project, report, data, expectedVersion, requestId);
            Map<String,Object> response = new LinkedHashMap<>();
            response.put("report", reportView(proposal.report()));
            response.put("resolution", resolutionView(proposal.resolution()));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (ReportService.Failure failure) { return failure(failure, RESOLUTIONS, actor, project); }
    }

    @GetMapping(RESOLUTIONS)
    public ResponseEntity<?> resolutions(@PathVariable String projectId, @PathVariable String reportId,
                                         @RequestParam(required=false) String limit, @RequestParam(required=false) String cursor,
                                         HttpServletRequest request) {
        UUID project = parseUuid(projectId), report = parseUuid(reportId);
        validateQuery(request, Set.of("limit", "cursor"));
        Integer parsedLimit = limit == null ? null : parsePositiveInt(limit);
        UUID actor = TrustedSessionActor.resolve(request, clock);
        if (actor == null) return denied("UNAUTHENTICATED", RESOLUTIONS, 401, null, project, "UNAUTHENTICATED");
        try {
            var page = service.resolutions(actor, project, report, parsedLimit, cursor);
            Map<String,Object> response = new LinkedHashMap<>();
            response.put("items", page.items().stream().map(this::resolutionView).toList());
            response.put("nextCursor", page.nextCursor());
            return ResponseEntity.ok(response);
        } catch (ReportService.Failure failure) { return failure(failure, RESOLUTIONS, actor, project); }
    }

    private ProposedResolutionData proposalData(JsonNode body) {
        Set<String> required = Set.of("rootCause", "changeSummary", "verificationSteps", "expectedVersion");
        if (body == null || !body.isObject()) throw fail("INVALID_REQUEST", 400);
        Set<String> supplied = new HashSet<>(set(body));
        supplied.remove("fixReference");
        if (!supplied.equals(required) || !body.path("rootCause").isTextual() || !body.path("changeSummary").isTextual() ||
                !body.path("verificationSteps").isTextual() || (body.has("fixReference") && !body.path("fixReference").isTextual()) ||
                !body.path("expectedVersion").isIntegralNumber() || !body.path("expectedVersion").canConvertToInt())
            throw fail("INVALID_REQUEST", 400);
        return new ProposedResolutionData(body.path("rootCause").textValue(), body.path("changeSummary").textValue(),
                body.path("verificationSteps").textValue(), body.has("fixReference") ? body.path("fixReference").textValue() : "");
    }

    private InvestigationNoteData noteCreateBody(JsonNode body) {
        if (body == null || !body.isObject() || !set(body).equals(Set.of("body")) || !body.path("body").isTextual())
            throw fail("INVALID_REQUEST", 400);
        return new InvestigationNoteData(body.path("body").textValue());
    }
    private int exactPositiveInt(JsonNode body, Set<String> fields, String name) {
        if (body == null || !body.isObject() || !set(body).equals(fields) || !body.path(name).isIntegralNumber() ||
                !body.path(name).canConvertToInt()) throw fail("INVALID_REQUEST", 400);
        return body.path(name).intValue();
    }
    private static Set<String> set(JsonNode body) { return new HashSet<>(body.propertyNames()); }
    private static int parsePositiveInt(String value) {
        try {
            if (value == null || !value.matches("[0-9]{1,10}")) throw new NumberFormatException();
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) { throw fail("INVALID_REQUEST", 400); }
    }
    private boolean csrf(HttpServletRequest request) {
        var session = request.getSession(false); String expected = session == null ? null : (String) session.getAttribute(CSRF);
        String supplied = request.getHeader("X-CSRF-TOKEN");
        return expected != null && supplied != null && java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }
    private UUID trustedActor(HttpServletRequest request) { return TrustedSessionActor.forEvent(request, clock); }

    private ResponseEntity<?> failure(ReportService.Failure failure, String route, UUID actor, UUID project) {
        if (Set.of("NOT_FOUND", "FORBIDDEN").contains(failure.code()))
            return denied("DEVELOPER_DENIED", route, failure.status(), actor, project, failure.code());
        if ("INVALID_CURSOR".equals(failure.code()))
            return denied("DEVELOPER_CURSOR_INVALID", route, failure.status(), actor, project, failure.code());
        if (Set.of("INVALID_REQUEST", "VALIDATION_ERROR").contains(failure.code()))
            return denied("DEVELOPER_INVALID_REQUEST", route, failure.status(), actor, project, failure.code());
        return error(failure.status(), failure.code());
    }
    private ResponseEntity<?> denied(String event, String route, int status, UUID actor, UUID project, String code) {
        events.event(event, route, status, actor, project); return error(status, code);
    }
    private ResponseEntity<?> error(int status, String code) {
        String message = switch (code) {
            case "UNAUTHENTICATED" -> "Authentication required";
            case "NOT_FOUND" -> "Not found";
            case "FORBIDDEN" -> "Forbidden";
            case "STALE_VERSION" -> "Stale version";
            case "INVALID_STATE" -> "Invalid state";
            case "NOTE_LIMIT" -> "Note limit reached";
            case "RESOLUTION_LIMIT" -> "Resolution limit reached";
            case "IDEMPOTENCY_CONFLICT" -> "Idempotency conflict";
            case "SERVICE_UNAVAILABLE" -> "Service unavailable";
            default -> "Invalid request";
        };
        return ResponseEntity.status(status).body(Map.of("error", Map.of("code", code, "message", message)));
    }
    private Map<String,Object> noteView(InvestigationRepository.Note note) {
        Map<String,Object> out = new LinkedHashMap<>(); out.put("id", note.id()); out.put("reportId", note.reportId());
        out.put("authorUserId", note.authorUserId()); out.put("lastEditedByUserId", note.lastEditedByUserId());
        out.put("body", note.body()); out.put("createdAt", note.createdAt()); out.put("updatedAt", note.updatedAt());
        out.put("version", note.version()); return out;
    }
    private Map<String,Object> resolutionView(InvestigationRepository.Resolution resolution) {
        Map<String,Object> out = new LinkedHashMap<>(); out.put("id", resolution.id()); out.put("reportId", resolution.reportId());
        out.put("authorUserId", resolution.authorUserId()); out.put("revision", resolution.revision());
        out.put("rootCause", resolution.rootCause()); out.put("changeSummary", resolution.changeSummary());
        out.put("verificationSteps", resolution.verificationSteps()); out.put("fixReference", resolution.fixReference());
        out.put("createdAt", resolution.createdAt());
        var rejection = resolution.rejection();
        out.put("rejection", rejection == null ? null : Map.of(
                "reason", rejection.reason(), "reviewerUserId", rejection.reviewerUserId(), "createdAt", rejection.createdAt()));
        return out;
    }
    private Map<String,Object> reportView(ReportRepository.Report report) {
        Map<String,Object> out = new LinkedHashMap<>(); out.put("id", report.id()); out.put("projectId", report.projectId());
        out.put("reporterUserId", report.reporterUserId()); out.put("title", report.fields().title());
        out.put("description", report.fields().description()); out.put("environment", report.fields().environment());
        out.put("reproductionSteps", report.fields().reproductionSteps()); out.put("expectedBehavior", report.fields().expectedBehavior());
        out.put("actualBehavior", report.fields().actualBehavior()); out.put("textLogs", report.fields().textLogs());
        out.put("status", report.status()); out.put("assignedDeveloperId", report.assignedDeveloperId());
        out.put("createdAt", report.createdAt()); out.put("updatedAt", report.updatedAt()); out.put("version", report.version()); return out;
    }
    private static UUID parseUuid(String value) {
        try { UUID id = UUID.fromString(value); if (!id.toString().equals(value)) throw new IllegalArgumentException(); return id; }
        catch (RuntimeException failure) { throw fail("INVALID_REQUEST", 400); }
    }
    private static void validateQuery(HttpServletRequest request, Set<String> allowed) {
        if (!allowed.containsAll(request.getParameterMap().keySet())) throw fail("INVALID_REQUEST", 400);
        for (var entry : request.getParameterMap().entrySet()) if (entry.getValue().length != 1) throw fail("INVALID_REQUEST", 400);
    }
    private static ReportService.Failure fail(String code, int status) { return new ReportService.Failure(code, status); }
}
