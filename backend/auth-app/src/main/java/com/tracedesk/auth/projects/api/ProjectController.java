package com.tracedesk.auth.projects.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tracedesk.auth.persistence.AuthStore;
import com.tracedesk.auth.projects.application.ProjectRepository;
import com.tracedesk.auth.projects.application.ProjectService;
import com.tracedesk.auth.security.TrustedSessionActor;
import com.tracedesk.auth.security.ProjectJoinBodyProbeFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class ProjectController {
    private static final String DIRECTORY="/api/projects", JOIN="/api/projects/{projectId}/join";
    private final ProjectService projects; private final AuthStore store; private final Clock clock;
    public ProjectController(ProjectService projects,AuthStore store,Clock clock){this.projects=projects;this.store=store;this.clock=clock;}
    public static final class CreateRequest {
        private String name; private boolean invalid; private boolean nameSeen;
        @JsonProperty("name") public void name(Object value){if(nameSeen)invalid=true;nameSeen=true;if(value instanceof String s)name=s;else invalid=true;}
        @com.fasterxml.jackson.annotation.JsonAnySetter public void unknown(String key,Object value){invalid=true;}
    }
    record Item(UUID id,String name,String membership) { }
    record Directory(List<Item> items,boolean hasMore,String nextCursor) { }
    record Created(UUID id,String name) { }
    record JoinReply(Created project,String membership) { }
    private ResponseEntity<?> error(HttpStatus status,String code,String message){return ResponseEntity.status(status).body(Map.of("error",Map.of("code",code,"message",message)));}
    private UUID actor(HttpServletRequest req,String route,int statusOnFailure){
        UUID actor=TrustedSessionActor.resolve(req,clock);
        if(actor==null||store.accountRole(actor).isEmpty()){
            denied("UNAUTHENTICATED",route,401,null,null); return null;
        }
        return actor;
    }
    private void denied(String code,String route,int status,UUID actor,UUID project){store.event(code,route,status,actor,project);}
    private boolean csrf(HttpServletRequest req){
        var session=req.getSession(false); String expected=session==null?null:(String)session.getAttribute("td.csrf");
        String actual=req.getHeader("X-CSRF-TOKEN");
        return expected!=null&&actual!=null&&java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    @GetMapping("/api/projects") public ResponseEntity<?> directory(@RequestParam(required=false) String limit,@RequestParam(required=false) String cursor,HttpServletRequest req){
        UUID actor=actor(req,DIRECTORY,401);if(actor==null)return error(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","Authentication required");
        Integer parsed=null;
        if(limit!=null)try{if(!limit.matches("[0-9]{1,3}"))throw new NumberFormatException();parsed=Integer.parseInt(limit);}catch(NumberFormatException e){return invalid(req,DIRECTORY,actor,null);}
        try {
            var page=projects.directory(actor,parsed,cursor);
            return ResponseEntity.ok(new Directory(page.items().stream().map(i->new Item(i.id(),i.name(),i.membership())).toList(),page.hasMore(),page.nextCursor()));
        }catch(ProjectService.Failure failure){return failure(failure,DIRECTORY,actor,null);}
    }
    @PostMapping("/api/projects") public ResponseEntity<?> create(@RequestBody(required=false) CreateRequest body,HttpServletRequest req){
        UUID actor=actor(req,DIRECTORY,401);if(actor==null)return error(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","Authentication required");
        if(!csrf(req)){denied("CSRF_INVALID",DIRECTORY,403,actor,null);return error(HttpStatus.FORBIDDEN,"CSRF_INVALID","Invalid request");}
        UUID key=canonical(req.getHeader("Idempotency-Key"));
        if(body==null||body.invalid||body.name==null||key==null)return invalid(req,DIRECTORY,actor,null);
        try {
            var created=projects.create(actor,body.name,key,UUID.randomUUID());
            return ResponseEntity.status(HttpStatus.CREATED).body(new Created(created.id(),created.name()));
        }catch(ProjectService.Failure failure){return failure(failure,DIRECTORY,actor,null);}
    }
    @PostMapping("/api/projects/{projectId}/join") public ResponseEntity<?> join(@PathVariable String projectId,HttpServletRequest req){
        UUID actor=actor(req,JOIN,401);if(actor==null)return error(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","Authentication required");
        UUID project=canonical(projectId);
        if(!csrf(req)){denied("CSRF_INVALID",JOIN,403,actor,project);return error(HttpStatus.FORBIDDEN,"CSRF_INVALID","Invalid request");}
        if(project==null)return invalid(req,JOIN,actor,null);
        if(Boolean.TRUE.equals(req.getAttribute(ProjectJoinBodyProbeFilter.BODY_PRESENT)))return invalid(req,JOIN,actor,project);
        try {
            var joined=projects.join(actor,project,UUID.randomUUID());
            return ResponseEntity.ok(new JoinReply(new Created(joined.id(),joined.name()),"ACTIVE"));
        }catch(ProjectService.Failure failure){return failure(failure,JOIN,actor,project);}
    }
    private ResponseEntity<?> failure(ProjectService.Failure failure,String route,UUID actor,UUID project){
        int status=failure.status();String code=failure.code();
        if("INVALID_REQUEST".equals(code))denied("PROJECT_INVALID_REQUEST",route,status,actor,project);
        else denied("PROJECT_MUTATION_DENIED",route,status,actor,project);
        String message=switch(code){case "UNAUTHENTICATED"->"Authentication required";case "NOT_FOUND"->"Not found";default->"Invalid request";};
        return error(HttpStatus.valueOf(status),code,message);
    }
    private ResponseEntity<?> invalid(HttpServletRequest req,String route,UUID actor,UUID project){
        denied("PROJECT_INVALID_REQUEST",route,400,actor,project);return error(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Invalid request");
    }
    private static UUID canonical(String value){try{if(value==null)return null;UUID id=UUID.fromString(value);return id.toString().equals(value)?id:null;}catch(IllegalArgumentException e){return null;}}
}
