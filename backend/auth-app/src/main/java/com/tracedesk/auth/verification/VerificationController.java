package com.tracedesk.auth.verification;

import com.tracedesk.auth.persistence.AuthStore;
import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.security.TrustedSessionActor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/reports/{reportId}/qa-verification")
public class VerificationController {
    private static final String AUDIT_ROUTE = "/api/projects/{projectId}/reports/{reportId}";
    private static final int MAX_BODY_BYTES = 128 * 1024;
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(tools.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).build();
    private final VerificationStore store;
    private final AuthStore events;
    private final Clock clock;

    public VerificationController(VerificationStore store, AuthStore events, Clock clock) {
        this.store = store;
        this.events = events;
        this.clock = clock;
    }

    @GetMapping
    public ResponseEntity<?> read(@PathVariable String projectId, @PathVariable String reportId, HttpServletRequest request) {
        UUID actor = actor(request);
        return review(request, actor, () -> store.read(actor, uuid(projectId), uuid(reportId)));
    }

    @PostMapping
    public ResponseEntity<?> write(@PathVariable String projectId, @PathVariable String reportId, HttpServletRequest request) {
        UUID actor = actor(request);
        return review(request, actor, () -> {
            Object token = request.getSession(false).getAttribute("td.csrf");
            String supplied = request.getHeader("X-CSRF-TOKEN");
            if (!(token instanceof String expected) || supplied == null || !MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
                throw new ReportService.Failure("CSRF_INVALID", 403);
            return store.write(actor, uuid(projectId), uuid(reportId), command(request));
        });
    }

    private ResponseEntity<?> review(HttpServletRequest request, UUID actor,
                                     java.util.function.Supplier<VerificationStore.Review> action) {
        try {
            if (actor == null) throw new ReportService.Failure("UNAUTHENTICATED", 401);
            if (!request.getParameterMap().isEmpty()) throw VerificationCommand.invalid();
            return ResponseEntity.ok().header("Cache-Control", "no-store").body(action.get());
        } catch (ReportService.Failure failure) {
            String event = switch (failure.code()) {
                case "FORBIDDEN", "NOT_FOUND" -> "REPORT_DENIED";
                case "CSRF_INVALID", "UNAUTHENTICATED" -> failure.code();
                case "INVALID_REQUEST" -> "REPORT_INVALID_REQUEST";
                default -> null;
            };
            if (event != null) events.event(event, AUDIT_ROUTE, failure.status(), actor, null);
            throw failure;
        }
    }

    private UUID actor(HttpServletRequest request) { return TrustedSessionActor.resolve(request, clock); }

    private static VerificationCommand command(HttpServletRequest request) {
        try {
            byte[] bytes = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) throw VerificationCommand.invalid();
            var node = JSON.readTree(bytes);
            if (node == null || !node.isObject() || node.size() != 5 || !node.path("expectedVersion").isInt()
                    || !node.path("decision").isString() || !node.path("notes").isString() || !node.path("reason").isString()
                    || !node.path("checks").isArray()) throw VerificationCommand.invalid();
            for (var check : node.path("checks")) {
                if (!check.isObject() || check.size() != 3 || !check.path("title").isString()
                        || !check.path("result").isString() || !check.path("notes").isString()) throw VerificationCommand.invalid();
            }
            return JSON.treeToValue(node, VerificationCommand.class);
        } catch (IOException | RuntimeException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof ReportService.Failure problem) throw problem;
            }
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
