package com.tracedesk.auth.verification;

import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.security.TrustedSessionActor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/reports/{reportId}/qa-comments")
public class DiscussionController {
    private static final int MAX_BODY_BYTES = 16 * 1024;
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final DiscussionStore store;
    private final Clock clock;

    public DiscussionController(DiscussionStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable String projectId, @PathVariable String reportId, HttpServletRequest request) {
        UUID actor = actor(request, false);
        var params = request.getParameterMap();
        if (!params.isEmpty() && (params.size() != 1 || !params.containsKey("before") || params.get("before").length != 1))
            throw VerificationCommand.invalid();
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(store.list(actor, uuid(projectId), uuid(reportId), cursor(request.getParameter("before"))));
    }

    @PostMapping
    public ResponseEntity<?> post(@PathVariable String projectId, @PathVariable String reportId, HttpServletRequest request) {
        UUID actor = actor(request, true);
        var node = body(request);
        if (node.size() != 1 || !node.path("body").isString()) throw VerificationCommand.invalid();
        return ResponseEntity.status(201).header("Cache-Control", "no-store")
                .body(store.post(actor, uuid(projectId), uuid(reportId), node.path("body").stringValue()));
    }

    private static Long cursor(String value) {
        if (value == null) return null;
        try {
            long cursor = Long.parseLong(value);
            if (cursor > 0 && Long.toString(cursor).equals(value)) return cursor;
        } catch (NumberFormatException ignored) { }
        throw VerificationCommand.invalid();
    }

    private UUID actor(HttpServletRequest request, boolean write) {
        UUID actor = TrustedSessionActor.resolve(request, clock);
        if (actor == null) throw new ReportService.Failure("UNAUTHENTICATED", 401);
        if (write) {
            Object token = request.getSession(false).getAttribute("td.csrf");
            String supplied = request.getHeader("X-CSRF-TOKEN");
            if (!(token instanceof String expected) || supplied == null || !MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
                throw new ReportService.Failure("CSRF_INVALID", 403);
        }
        return actor;
    }

    private static JsonNode body(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) throw VerificationCommand.invalid();
        try {
            byte[] bytes = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) throw VerificationCommand.invalid();
            var node = JSON.readTree(bytes);
            if (node == null || !node.isObject()) throw VerificationCommand.invalid();
            return node;
        } catch (IOException | tools.jackson.core.JacksonException ex) {
            throw VerificationCommand.invalid();
        }
    }

    private static UUID uuid(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (id.toString().equals(value)) return id;
        } catch (IllegalArgumentException ignored) { }
        throw VerificationCommand.invalid();
    }
}
