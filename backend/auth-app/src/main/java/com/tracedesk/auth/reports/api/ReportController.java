package com.tracedesk.auth.reports.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JsonFactory;
import tools.jackson.core.StreamReadFeature;
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
    private static final String DRAFTS = "/api/projects/{projectId}/drafts";
    private static final String DRAFT = "/api/projects/{projectId}/drafts/{draftId}";
    private static final String SUBMIT_DRAFT = "/api/projects/{projectId}/drafts/{draftId}/submit";
    private static final String CSRF = "td.csrf";
    private final ReportService service; private final AuthStore auth; private final Clock clock;
    private static final ObjectMapper STRICT_JSON = new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    public ReportController(ReportService service, AuthStore auth, Clock clock) { this.service=service; this.auth=auth; this.clock=clock; }

    @PostMapping(REPORTS)
    public ResponseEntity<?> create(@PathVariable String projectId, @RequestHeader(value="Idempotency-Key", required=false) String key,
                                    @RequestBody String rawBody, HttpServletRequest request) {
        UUID project = parseUuid(projectId), requestId = UUID.randomUUID();
        JsonNode body = strictBody(rawBody);
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

    @PostMapping(DRAFTS)
    public ResponseEntity<?> createDraft(@PathVariable String projectId,@RequestHeader(value="Idempotency-Key",required=false) String key,
                                         @RequestBody String rawBody,HttpServletRequest request) {
        UUID project=parseUuid(projectId),requestId=UUID.randomUUID(); JsonNode body=strictBody(rawBody);
        ReportData fields=draftFields(body); UUID idem=parseUuid(key);
        if(!csrf(request)) return denied("CSRF_INVALID",DRAFTS,403,trustedActor(request),project,"CSRF_INVALID");
        UUID actor=TrustedSessionActor.resolve(request,clock); if(actor==null) return denied("UNAUTHENTICATED",DRAFTS,401,null,project,"UNAUTHENTICATED");
        try { return ResponseEntity.status(HttpStatus.CREATED).body(draftView(service.createDraft(actor,project,idem,fields,requestId))); }
        catch(ReportService.Failure f) { return failure(f,DRAFTS,actor,project); }
    }

    @GetMapping(DRAFTS)
    public ResponseEntity<?> listDrafts(@PathVariable String projectId,@RequestParam(required=false) String limit,
                                        @RequestParam(required=false) String cursor,HttpServletRequest request) {
        UUID project=parseUuid(projectId); validateQuery(request,Set.of("limit","cursor"));
        UUID actor=TrustedSessionActor.resolve(request,clock); if(actor==null) return denied("UNAUTHENTICATED",DRAFTS,401,null,project,"UNAUTHENTICATED");
        Integer parsed=null; if(limit!=null) try { if(!limit.matches("[0-9]{1,3}")) throw new NumberFormatException(); parsed=Integer.valueOf(limit); } catch(NumberFormatException e) { throw new ReportService.Failure("INVALID_REQUEST",400); }
        try {
            var page=service.listDrafts(actor,project,parsed,cursor); Map<String,Object> out=new LinkedHashMap<>();
            out.put("items",page.items().stream().map(this::draftSummary).toList());out.put("nextCursor",page.nextCursor());return ResponseEntity.ok(out);
        } catch(ReportService.Failure f) { return failure(f,DRAFTS,actor,project); }
    }

    @GetMapping(DRAFT)
    public ResponseEntity<?> getDraft(@PathVariable String projectId,@PathVariable String draftId,HttpServletRequest request) {
        UUID project=parseUuid(projectId),id=parseUuid(draftId),actor=TrustedSessionActor.resolve(request,clock);
        if(actor==null) return denied("UNAUTHENTICATED",DRAFT,401,null,project,"UNAUTHENTICATED");
        try { return ResponseEntity.ok(draftView(service.draft(actor,project,id))); }
        catch(ReportService.Failure f) { return failure(f,DRAFT,actor,project); }
    }

    @PatchMapping(DRAFT)
    public ResponseEntity<?> updateDraft(@PathVariable String projectId,@PathVariable String draftId,@RequestBody String rawBody,HttpServletRequest request) {
        UUID project=parseUuid(projectId),id=parseUuid(draftId),requestId=UUID.randomUUID(); JsonNode body=strictBody(rawBody);
        int version=expectedVersion(body); ReportData fields=versionedFields(body);
        if(!csrf(request)) return denied("CSRF_INVALID",DRAFT,403,trustedActor(request),project,"CSRF_INVALID");
        UUID actor=TrustedSessionActor.resolve(request,clock);if(actor==null) return denied("UNAUTHENTICATED",DRAFT,401,null,project,"UNAUTHENTICATED");
        try { return ResponseEntity.ok(draftView(service.updateDraft(actor,project,id,version,fields,requestId))); }
        catch(ReportService.Failure f) { return failure(f,DRAFT,actor,project); }
    }

    @DeleteMapping(DRAFT)
    public ResponseEntity<?> deleteDraft(@PathVariable String projectId,@PathVariable String draftId,@RequestParam String expectedVersion,HttpServletRequest request) {
        UUID project=parseUuid(projectId),id=parseUuid(draftId),requestId=UUID.randomUUID();validateQuery(request,Set.of("expectedVersion"));
        int version=parseVersion(expectedVersion);
        if(!csrf(request)) return denied("CSRF_INVALID",DRAFT,403,trustedActor(request),project,"CSRF_INVALID");
        UUID actor=TrustedSessionActor.resolve(request,clock);if(actor==null) return denied("UNAUTHENTICATED",DRAFT,401,null,project,"UNAUTHENTICATED");
        try { service.deleteDraft(actor,project,id,version,requestId);return ResponseEntity.noContent().build(); }
        catch(ReportService.Failure f) { return failure(f,DRAFT,actor,project); }
    }

    @PostMapping(SUBMIT_DRAFT)
    public ResponseEntity<?> submitDraft(@PathVariable String projectId,@PathVariable String draftId,@RequestHeader(value="Idempotency-Key",required=false) String key,
                                         @RequestBody String rawBody,HttpServletRequest request) {
        UUID project=parseUuid(projectId),id=parseUuid(draftId),requestId=UUID.randomUUID();JsonNode body=strictBody(rawBody);
        if(body==null||!body.isObject()||!Set.of("expectedVersion").equals(new HashSet<>(body.propertyNames()))) throw new ReportService.Failure("INVALID_REQUEST",400);
        int version=expectedVersion(body);UUID idem=parseUuid(key);
        if(!csrf(request)) return denied("CSRF_INVALID",SUBMIT_DRAFT,403,trustedActor(request),project,"CSRF_INVALID");
        UUID actor=TrustedSessionActor.resolve(request,clock);if(actor==null) return denied("UNAUTHENTICATED",SUBMIT_DRAFT,401,null,project,"UNAUTHENTICATED");
        try { Map<String,Object> out=detailView(service.submitDraft(actor,project,id,version,idem,requestId));out.put("submittedDraftId",id);return ResponseEntity.status(HttpStatus.CREATED).body(out); }
        catch(ReportService.Failure f) { return failure(f,SUBMIT_DRAFT,actor,project); }
    }

    @PatchMapping(DETAIL)
    public ResponseEntity<?> updateReport(@PathVariable String projectId,@PathVariable String reportId,@RequestBody String rawBody,HttpServletRequest request) {
        UUID project=parseUuid(projectId),id=parseUuid(reportId),requestId=UUID.randomUUID();JsonNode body=strictBody(rawBody);
        int version=expectedVersion(body);ReportData fields=versionedFields(body);
        if(!csrf(request)) return denied("CSRF_INVALID",DETAIL,403,trustedActor(request),project,"CSRF_INVALID");
        UUID actor=TrustedSessionActor.resolve(request,clock);if(actor==null) return denied("UNAUTHENTICATED",DETAIL,401,null,project,"UNAUTHENTICATED");
        try { return ResponseEntity.ok(detailView(service.updateReport(actor,project,id,version,fields,requestId))); }
        catch(ReportService.Failure f) { return failure(f,DETAIL,actor,project); }
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
    private Map<String,Object> draftSummary(ReportRepository.DraftSummary d) {
        Map<String,Object> out=new LinkedHashMap<>();out.put("id",d.id());out.put("projectId",d.projectId());out.put("title",d.title());
        out.put("createdAt",d.createdAt());out.put("updatedAt",d.updatedAt());out.put("expiresAt",d.expiresAt());out.put("version",d.version());out.put("submittedReportId",d.submittedReportId());return out;
    }
    private Map<String,Object> draftView(ReportRepository.Draft d) {
        Map<String,Object> out=new LinkedHashMap<>();out.put("id",d.id());out.put("projectId",d.projectId());out.put("ownerUserId",d.ownerId());
        out.put("title",d.fields().title());out.put("description",d.fields().description());out.put("environment",d.fields().environment());
        out.put("reproductionSteps",d.fields().reproductionSteps());out.put("expectedBehavior",d.fields().expectedBehavior());out.put("actualBehavior",d.fields().actualBehavior());out.put("textLogs",d.fields().textLogs());
        out.put("createdAt",d.createdAt());out.put("updatedAt",d.updatedAt());out.put("expiresAt",d.expiresAt());out.put("version",d.version());out.put("submittedReportId",d.submittedReportId());return out;
    }
    private ReportData fields(JsonNode body, boolean required) {
        if (body==null || !body.isObject()) throw new ReportService.Failure("INVALID_REQUEST",400);
        Set<String> allowed=new LinkedHashSet<>(ReportData.FIELD_NAMES); Set<String> actual=new HashSet<>(body.propertyNames());
        if(!actual.equals(allowed)) throw new ReportService.Failure("INVALID_REQUEST",400);
        String[] v=new String[7]; for(int i=0;i<v.length;i++){JsonNode n=body.get(ReportData.FIELD_NAMES.get(i)); if(n==null||!n.isTextual()) throw new ReportService.Failure("INVALID_REQUEST",400);v[i]=n.textValue();}
        return new ReportData(v[0],v[1],v[2],v[3],v[4],v[5],v[6]);
    }
    private ReportData draftFields(JsonNode body) {
        if(body==null||!body.isObject()) throw new ReportService.Failure("INVALID_REQUEST",400);
        Set<String> allowed=new HashSet<>(ReportData.FIELD_NAMES);
        if(!allowed.containsAll(body.propertyNames())) throw new ReportService.Failure("INVALID_REQUEST",400);
        String[] v=new String[7]; for(int i=0;i<v.length;i++) { JsonNode n=body.get(ReportData.FIELD_NAMES.get(i)); if(n==null) v[i]=""; else if(!n.isTextual()) throw new ReportService.Failure("INVALID_REQUEST",400); else v[i]=n.textValue(); }
        return new ReportData(v[0],v[1],v[2],v[3],v[4],v[5],v[6]);
    }
    private ReportData versionedFields(JsonNode body) {
        if(body==null||!body.isObject()) throw new ReportService.Failure("INVALID_REQUEST",400);
        Set<String> expected=new HashSet<>(ReportData.FIELD_NAMES);expected.add("expectedVersion");
        if(!expected.equals(new HashSet<>(body.propertyNames()))) throw new ReportService.Failure("INVALID_REQUEST",400);
        String[] v=new String[7];for(int i=0;i<v.length;i++){JsonNode n=body.get(ReportData.FIELD_NAMES.get(i));if(n==null||!n.isTextual())throw new ReportService.Failure("INVALID_REQUEST",400);v[i]=n.textValue();}
        return new ReportData(v[0],v[1],v[2],v[3],v[4],v[5],v[6]);
    }
    private int expectedVersion(JsonNode body) {
        if(body==null||!body.isObject()) throw new ReportService.Failure("INVALID_REQUEST",400);
        JsonNode value=body.get("expectedVersion");if(value==null||!value.isIntegralNumber()||!value.canConvertToInt()||value.intValue()<1) throw new ReportService.Failure("INVALID_REQUEST",400);
        return value.intValue();
    }
    private static int parseVersion(String value) {
        try { if(value==null||!value.matches("[1-9][0-9]{0,8}")) throw new NumberFormatException();return Integer.parseInt(value); }
        catch(NumberFormatException e) { throw new ReportService.Failure("INVALID_REQUEST",400); }
    }
    private static JsonNode strictBody(String raw) {
        try { return STRICT_JSON.readTree(raw); }
        catch(Exception e) { throw new ReportService.Failure("INVALID_REQUEST",400); }
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
        if(f.currentVersion()!=null) return ResponseEntity.status(f.status()).body(Map.of("error",Map.of("code",f.code(),"message","A newer version is available"),"currentVersion",f.currentVersion()));
        return error(f.status(),f.code());
    }
    private ResponseEntity<?> denied(String event,String route,int status,UUID actor,UUID project,String code) {
        auth.event(event,route,status,actor,project); return error(status,code);
    }
    private ResponseEntity<?> error(int status,String code) {
        String message=switch(code){case "UNAUTHENTICATED"->"Authentication required";case "NOT_FOUND"->"Not found";case "FORBIDDEN"->"Forbidden";case "DRAFT_EXPIRED"->"Draft expired";case "SERVICE_UNAVAILABLE"->"Service unavailable";case "INVALID_STATE"->"The report or draft can no longer be changed";case "STALE_VERSION"->"A newer version is available";case "DRAFT_LIMIT"->"Draft limit reached";case "DRAFT_ALREADY_SUBMITTED"->"Draft already submitted";case "IDEMPOTENCY_CONFLICT"->"Request key was already used";default->"Invalid request";};
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
