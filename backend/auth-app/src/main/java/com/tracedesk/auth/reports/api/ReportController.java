package com.tracedesk.auth.reports.api;

import tools.jackson.databind.JsonNode;
import com.tracedesk.auth.persistence.AuthStore;
import com.tracedesk.auth.reports.application.ReportRepository;
import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.reports.domain.ReportData;
import com.tracedesk.auth.security.TrustedSessionActor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import java.util.*;

@RestController
public class ReportController {
    private static final String REPORTS = "/api/projects/{projectId}/reports";
    private static final String DETAIL = "/api/projects/{projectId}/reports/{reportId}";
    private static final String CONTEXT = "/api/projects/{projectId}/reporter-context";
    private static final String CSRF = "td.csrf";
    private final ReportService service; private final AuthStore auth; private final Clock clock;
    public ReportController(ReportService service, AuthStore auth, Clock clock) { this.service=service; this.auth=auth; this.clock=clock; }

    @PostMapping(REPORTS)
    public ResponseEntity<?> create(@PathVariable String projectId, @RequestHeader(value="Idempotency-Key", required=false) String key,
                                    @RequestBody JsonNode body, HttpServletRequest request) {
        UUID project = parseUuid(projectId), requestId = UUID.randomUUID();
        ReportData fields = fields(body, true); UUID idem = parseUuid(key);
        if (!csrf(request)) return denied("CSRF_INVALID", REPORTS, 403, trustedActor(request), null, "CSRF_INVALID");
        UUID actor = TrustedSessionActor.resolve(request, clock);
        if (actor == null) return denied("UNAUTHENTICATED", REPORTS, 401, null, null, "UNAUTHENTICATED");
        try { return ResponseEntity.status(HttpStatus.CREATED).body(detailView(service.create(actor, project, idem, fields, requestId))); }
        catch (ReportService.Failure f) { return failure(f, REPORTS, actor, null); }
    }

    @GetMapping(REPORTS)
    public ResponseEntity<?> list(@PathVariable String projectId, @RequestParam(required=false) String limit,
                                  @RequestParam(required=false) String cursor, @RequestParam(required=false) String status,
                                  @RequestParam(required=false) String assignedToMe, HttpServletRequest request) {
        UUID project = parseUuid(projectId); validateQuery(request, Set.of("limit","cursor","status","assignedToMe"));
        UUID actor = TrustedSessionActor.resolve(request, clock);
        if (actor == null) return denied("UNAUTHENTICATED", REPORTS, 401, null, null, "UNAUTHENTICATED");
        Integer parsedLimit = null; if (limit != null) try { if (!limit.matches("[0-9]{1,3}")) throw new NumberFormatException(); parsedLimit=Integer.valueOf(limit); } catch (NumberFormatException e) { throw new ReportService.Failure("INVALID_REQUEST",400); }
        Boolean assigned = null; if (assignedToMe != null) { if (!assignedToMe.equals("true") && !assignedToMe.equals("false")) throw new ReportService.Failure("INVALID_REQUEST",400); assigned=Boolean.valueOf(assignedToMe); }
        try {
            var page=service.list(actor, project, parsedLimit, cursor, status, assigned);
            Map<String,Object> reply = new LinkedHashMap<>(); reply.put("items", page.items().stream().map(this::summary).toList()); reply.put("nextCursor", page.nextCursor());
            return ResponseEntity.ok(reply);
        } catch (ReportService.Failure f) { return failure(f, REPORTS, actor, null); }
    }

    @GetMapping(DETAIL)
    public ResponseEntity<?> detail(@PathVariable String projectId, @PathVariable String reportId, HttpServletRequest request) {
        UUID project=parseUuid(projectId), report=parseUuid(reportId); UUID actor=TrustedSessionActor.resolve(request,clock);
        if (actor==null) return denied("UNAUTHENTICATED", DETAIL,401,null,null,"UNAUTHENTICATED");
        try { return ResponseEntity.ok(detailView(service.detail(actor,project,report))); }
        catch (ReportService.Failure f) { return failure(f,DETAIL,actor,null); }
    }

    @GetMapping(CONTEXT)
    public ResponseEntity<?> reporterContext(@PathVariable String projectId, HttpServletRequest request) {
        UUID project=parseUuid(projectId); UUID actor=TrustedSessionActor.resolve(request,clock);
        if(actor==null) return denied("UNAUTHENTICATED",CONTEXT,401,null,null,"UNAUTHENTICATED");
        try {
            var m=service.reporterContext(actor,project);
            return ResponseEntity.ok(Map.of("project",Map.of("id",m.projectId(),"name",m.projectName()),"role","REPORTER"));
        } catch(ReportService.Failure f) { return failure(f,CONTEXT,actor,null); }
    }

    private Map<String,Object> summary(ReportRepository.Summary r) {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("id",r.id());out.put("projectId",r.projectId());out.put("reporterUserId",r.reporterUserId());out.put("title",r.title());
        out.put("status",r.status());out.put("assignedDeveloperId",r.assignedDeveloperId());out.put("createdAt",r.createdAt());out.put("updatedAt",r.updatedAt());out.put("version",r.version());return out;
    }
    private Map<String,Object> detailView(ReportRepository.Report r) {
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("id",r.id());out.put("projectId",r.projectId());out.put("reporterUserId",r.reporterUserId());
        out.put("title",r.fields().title());out.put("description",r.fields().description());out.put("environment",r.fields().environment());
        out.put("reproductionSteps",r.fields().reproductionSteps());out.put("expectedBehavior",r.fields().expectedBehavior());
        out.put("actualBehavior",r.fields().actualBehavior());out.put("textLogs",r.fields().textLogs());out.put("status",r.status());
        out.put("assignedDeveloperId",r.assignedDeveloperId());out.put("createdAt",r.createdAt());out.put("updatedAt",r.updatedAt());out.put("version",r.version());return out;
    }
    private ReportData fields(JsonNode body, boolean required) {
        if (body==null || !body.isObject()) throw new ReportService.Failure("INVALID_REQUEST",400);
        Set<String> allowed=new LinkedHashSet<>(ReportData.FIELD_NAMES); Set<String> actual=new HashSet<>(body.propertyNames());
        if(!actual.equals(allowed)) throw new ReportService.Failure("INVALID_REQUEST",400);
        String[] v=new String[7]; for(int i=0;i<v.length;i++){JsonNode n=body.get(ReportData.FIELD_NAMES.get(i)); if(n==null||!n.isTextual()) throw new ReportService.Failure("INVALID_REQUEST",400);v[i]=n.textValue();}
        return new ReportData(v[0],v[1],v[2],v[3],v[4],v[5],v[6]);
    }
    private boolean csrf(HttpServletRequest request) {
        var session=request.getSession(false); String expected=session==null?null:(String)session.getAttribute(CSRF); String supplied=request.getHeader("X-CSRF-TOKEN");
        return expected!=null&&supplied!=null&&java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private UUID trustedActor(HttpServletRequest request) { return TrustedSessionActor.forEvent(request,clock); }
    private ResponseEntity<?> failure(ReportService.Failure f,String route,UUID actor,UUID project) {
        if (f.code().equals("NOT_FOUND") || f.code().equals("FORBIDDEN") || f.code().equals("INVALID_CURSOR")) {
            String ev=f.code().equals("INVALID_CURSOR")?"REPORT_CURSOR_INVALID":"REPORT_DENIED";
            return denied(ev,route,f.status(),actor,null,f.code());
        }
        return error(f.status(),f.code());
    }
    private ResponseEntity<?> denied(String event,String route,int status,UUID actor,UUID project,String code) {
        auth.event(event,route,status,actor,project); return error(status,code);
    }
    private ResponseEntity<?> error(int status,String code) {
        String message=switch(code){case "UNAUTHENTICATED"->"Authentication required";case "NOT_FOUND"->"Not found";case "FORBIDDEN"->"Forbidden";default->"Invalid request";};
        return ResponseEntity.status(status).body(Map.of("error",Map.of("code",code,"message",message)));
    }
    private static UUID parseUuid(String value) {
        try { UUID parsed=UUID.fromString(value); if(!parsed.toString().equals(value)) throw new IllegalArgumentException(); return parsed; }
        catch (RuntimeException e) { throw new ReportService.Failure("INVALID_REQUEST",400); }
    }
    private static void validateQuery(HttpServletRequest request, Set<String> allowed) {
        if(!allowed.containsAll(request.getParameterMap().keySet())) throw new ReportService.Failure("INVALID_REQUEST",400);
        for(var e:request.getParameterMap().entrySet()) if(e.getValue().length!=1) throw new ReportService.Failure("INVALID_REQUEST",400);
    }
}
