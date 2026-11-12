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
@RequestMapping("/api/projects/{projectId}/qa-templates")
public class TemplateController {
    private static final int MAX_BODY_BYTES = 768 * 1024;
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final TemplateStore store;
    private final Clock clock;

    public TemplateController(TemplateStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable String projectId, HttpServletRequest request) {
        UUID actor = actor(request, false);
        if (!request.getParameterMap().isEmpty()) throw VerificationCommand.invalid();
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(store.list(actor, uuid(projectId)));
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable String projectId, HttpServletRequest request) {
        UUID actor = actor(request, true);
        return ResponseEntity.status(201).header("Cache-Control", "no-store")
                .body(store.create(actor, uuid(projectId), input(body(request), false)));
    }

    @PatchMapping("/{templateId}")
    public ResponseEntity<?> update(@PathVariable String projectId, @PathVariable String templateId,
                                    HttpServletRequest request) {
        UUID actor = actor(request, true);
        var node = body(request);
        if (!node.path("expectedVersion").isInt() || node.path("expectedVersion").intValue() < 1)
            throw VerificationCommand.invalid();
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(store.update(actor, uuid(projectId),
                uuid(templateId), node.path("expectedVersion").intValue(), input(node, true)));
    }

    @DeleteMapping("/{templateId}")
    public ResponseEntity<?> delete(@PathVariable String projectId, @PathVariable String templateId,
                                    HttpServletRequest request) {
        UUID actor = actor(request, true);
        var params = request.getParameterMap();
        if (params.size() != 1 || !params.containsKey("expectedVersion") || params.get("expectedVersion").length != 1)
            throw VerificationCommand.invalid();
        var value = request.getParameter("expectedVersion");
        int version;
        try {
            version = Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw VerificationCommand.invalid();
        }
        if (version < 1 || !Integer.toString(version).equals(value)) throw VerificationCommand.invalid();
        store.delete(actor, uuid(projectId), uuid(templateId), version);
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    private static TemplateStore.Input input(JsonNode node, boolean update) {
        var cases = node.path("testCases");
        if (node.size() != (update ? 5 : 4) || !cases.isArray() || cases.isEmpty() || cases.size() > 30)
            throw VerificationCommand.invalid();
        var items = new java.util.ArrayList<TemplateStore.Case>();
        for (var item : cases) {
            if (!item.isObject() || item.size() != 3) throw VerificationCommand.invalid();
            items.add(new TemplateStore.Case(field(item, "name"), field(item, "steps"), field(item, "expectedResult")));
        }
        return new TemplateStore.Input(field(node, "name"), field(node, "whenToUse"), field(node, "preconditions"), items);
    }

    private static String field(JsonNode node, String name) {
        if (!node.path(name).isString()) throw VerificationCommand.invalid();
        return node.path(name).stringValue();
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
