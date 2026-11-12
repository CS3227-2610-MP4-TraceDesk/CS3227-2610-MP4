package com.tracedesk.auth.investigation.api;

import com.tracedesk.auth.investigation.ai.InvestigationAiAccess;
import com.tracedesk.auth.investigation.ai.InvestigationAiSelection;
import com.tracedesk.auth.persistence.AuthStore;
import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.security.TrustedSessionActor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;

@RestController
public final class InvestigationAiController {
    static final String ROUTE = "/api/projects/{projectId}/reports/{reportId}/investigation/suggestions";
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final Set<String> FIELDS = Set.of("title", "description", "environment",
            "reproductionSteps", "expectedBehavior", "actualBehavior", "textLogs");

    private final InvestigationAiAccess access;
    private final Clock clock;
    private final AuthStore events;
    private final SessionRepository<? extends Session> sessions;

    public InvestigationAiController(InvestigationAiAccess access, Clock clock, AuthStore events,
                                     SessionRepository<? extends Session> sessions) {
        this.access = access; this.clock = clock; this.events = events; this.sessions = sessions;
    }

    @PostMapping(ROUTE)
    public ResponseEntity<?> suggest(@PathVariable String projectId, @PathVariable String reportId,
                                     @RequestBody String raw, HttpServletRequest request) {
        try {
            UUID project = uuid(projectId), report = uuid(reportId);
            if (!request.getParameterMap().isEmpty() || !jsonContentType(request.getContentType())) throw invalid();
            Parsed parsed = parse(raw);
            if (!csrf(request)) throw new ReportService.Failure("CSRF_INVALID", 403);
            var result = access.suggestWithProvenance(project, report, parsed.expectedVersion(), parsed.selection(),
                    () -> freshActor(request));
            Map<String,Object> response = new LinkedHashMap<>();
            response.put("capability", result.capability());
            response.put("promptVersion", result.promptVersion());
            response.put("reportId", result.reportId());
            response.put("reportVersion", result.reportVersion());
            response.put("reportFields", result.reportFields());
            response.put("sourceNotes", result.sourceNotes());
            response.put("hypotheses", result.hypotheses());
            response.put("nextSteps", result.nextSteps());
            return ResponseEntity.ok().header("Cache-Control", "no-store").body(response);
        } catch (ReportService.Failure failure) {
            return failure(failure, request);
        }
    }

    private record Parsed(int expectedVersion, InvestigationAiSelection selection) { }
    private static Parsed parse(String raw) {
        try {
            JsonNode body = JSON.readTree(raw);
            if (body == null || !body.isObject() || body.size() != 3
                    || !body.has("expectedVersion") || !body.has("reportFields") || !body.has("notes")) throw invalid();
            JsonNode version = body.get("expectedVersion"), fields = body.get("reportFields"), notes = body.get("notes");
            if (!positiveInt(version) || !fields.isArray() || fields.size() == 0 || fields.size() > 7
                    || !notes.isArray() || notes.size() > 5) throw invalid();
            var selectedFields = new ArrayList<String>();
            var uniqueFields = new HashSet<String>();
            for (JsonNode field : fields) {
                if (!field.isString() || !FIELDS.contains(field.asString()) || !uniqueFields.add(field.asString())) throw invalid();
                selectedFields.add(field.asString());
            }
            var selectedNotes = new ArrayList<InvestigationAiSelection.SelectedNote>();
            var uniqueNotes = new HashSet<UUID>();
            for (JsonNode note : notes) {
                if (!note.isObject() || note.size() != 2 || !note.has("id") || !note.has("expectedVersion")
                        || !note.get("id").isString() || !positiveInt(note.get("expectedVersion"))) throw invalid();
                UUID id = uuid(note.get("id").asString());
                if (!uniqueNotes.add(id)) throw invalid();
                selectedNotes.add(new InvestigationAiSelection.SelectedNote(id, note.get("expectedVersion").asInt()));
            }
            return new Parsed(version.asInt(), new InvestigationAiSelection(List.copyOf(selectedFields), List.copyOf(selectedNotes)));
        } catch (ReportService.Failure failure) { throw failure; }
        catch (RuntimeException malformed) { throw invalid(); }
    }

    private ResponseEntity<?> failure(ReportService.Failure failure, HttpServletRequest request) {
        String event = switch (failure.code()) {
            case "CSRF_INVALID", "UNAUTHENTICATED" -> failure.code();
            case "NOT_FOUND", "FORBIDDEN" -> "DEVELOPER_DENIED";
            case "INVALID_REQUEST" -> "DEVELOPER_INVALID_REQUEST";
            default -> null;
        };
        if (event != null) {
            try { events.event(event, ROUTE, failure.status(), TrustedSessionActor.forEvent(request, clock), null); }
            catch (RuntimeException unavailable) { failure = new ReportService.Failure("SERVICE_UNAVAILABLE", 503); }
        }
        String message = switch (failure.code()) {
            case "UNAUTHENTICATED" -> "Authentication required";
            case "CSRF_INVALID", "FORBIDDEN" -> "Forbidden";
            case "NOT_FOUND" -> "Not found";
            case "STALE_VERSION" -> "Stale version";
            case "INVALID_STATE" -> "Invalid state";
            case "AI_CONTEXT_TOO_LARGE" -> "Selected context is too large";
            case "AI_UNAVAILABLE" -> "AI suggestions are unavailable";
            case "AI_INVALID_OUTPUT" -> "AI returned an invalid suggestion";
            case "SERVICE_UNAVAILABLE" -> "Service unavailable";
            default -> "Invalid request";
        };
        return ResponseEntity.status(failure.status()).header("Cache-Control", "no-store")
                .body(Map.of("error", Map.of("code", failure.code(), "message", message)));
    }

    private static boolean positiveInt(JsonNode node) {
        return node != null && node.isIntegralNumber() && node.canConvertToInt() && node.asInt() > 0;
    }
    private static boolean jsonContentType(String value) {
        try { MediaType type = MediaType.parseMediaType(value); return "application".equals(type.getType()) && "json".equals(type.getSubtype()); }
        catch (RuntimeException invalid) { return false; }
    }
    private static boolean csrf(HttpServletRequest request) {
        var session = request.getSession(false);
        Object token = session == null ? null : session.getAttribute("td.csrf");
        String supplied = request.getHeader("X-CSRF-TOKEN");
        return token instanceof String expected && supplied != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }
    /** The request wrapper caches HttpSession, so re-read the persisted session at every gateway check. */
    private UUID freshActor(HttpServletRequest request) {
        try {
            UUID cached = TrustedSessionActor.resolve(request, clock);
            var cachedSession = request.getSession(false);
            if (cached == null || cachedSession == null) return null;
            Session persisted = sessions.findById(cachedSession.getId());
            if (persisted == null || persisted.isExpired()) return null;
            Object actor = persisted.getAttribute("td.actor"), at = persisted.getAttribute("td.authAt");
            if (!cached.equals(actor) || !(at instanceof Long authenticatedAt)) return null;
            long age = clock.millis() - authenticatedAt;
            return age >= 0 && age < 8L * 60 * 60 * 1000 ? cached : null;
        } catch (RuntimeException failure) { throw new ReportService.Failure("SERVICE_UNAVAILABLE", 503); }
    }
    private static UUID uuid(String text) {
        try { UUID id = UUID.fromString(text); if (!id.toString().equals(text)) throw invalid(); return id; }
        catch (ReportService.Failure failure) { throw failure; }
        catch (RuntimeException failure) { throw invalid(); }
    }
    private static ReportService.Failure invalid() { return new ReportService.Failure("INVALID_REQUEST", 400); }
}
