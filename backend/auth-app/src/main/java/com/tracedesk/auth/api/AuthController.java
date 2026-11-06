package com.tracedesk.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tracedesk.auth.domain.LoginPolicy;
import com.tracedesk.auth.persistence.AuthStore;
import com.tracedesk.auth.application.ProjectAuthorization;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

@RestController
public class AuthController {
    static final String USER = "td.actor", AUTH_AT = "td.authAt", CSRF = "td.csrf";
    private final AuthStore store;
    private final ProjectAuthorization authorization;
    private final PasswordEncoder encoder;
    private final String dummyHash;
    private final Clock clock;
    public AuthController(AuthStore store, ProjectAuthorization authorization, PasswordEncoder encoder, Clock clock) { this.store=store; this.authorization=authorization; this.encoder=encoder; this.clock=clock; this.dummyHash=encoder.encode("dummy-password-never-valid"); }
    public static final class LoginRequest {
        public String login;
        public String password;
        private boolean unknownField;
        @com.fasterxml.jackson.annotation.JsonAnySetter public void unknown(String name, Object value) { unknownField = true; }
        String login() { return login; }
        String password() { return password; }
        boolean hasUnknownField() { return unknownField; }
    }
    record LoginReply(UUID userId) { }
    record SessionReply(UUID userId) { }
    record CsrfReply(String token) { }
    record ProjectReply(UUID id, String name) { }
    record ErrorBody(Error error) { }
    record Error(String code, String message) { }
    ResponseEntity<ErrorBody> error(HttpStatus status, String code) {
        return ResponseEntity.status(status).body(new ErrorBody(new Error(code, switch(code) {
            case "INVALID_CREDENTIALS" -> "Invalid credentials"; case "UNAUTHENTICATED" -> "Authentication required";
            case "NOT_FOUND" -> "Not found"; case "CSRF_INVALID" -> "Invalid request";
            case "INVALID_REQUEST" -> "Invalid request"; default -> "Service unavailable";
        })));
    }
    boolean csrf(HttpServletRequest req) {
        HttpSession s=req.getSession(false); String expected=s==null?null:(String)s.getAttribute(CSRF);
        String actual=req.getHeader("X-CSRF-TOKEN"); return expected!=null && actual!=null && java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8), actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    void denied(String code,String route,int status,UUID actor,UUID project) { store.event(code,route,status,actor,project); }
    @GetMapping("/api/csrf") public CsrfReply csrfToken(HttpServletRequest req) {
        HttpSession s=req.getSession(true); String token=(String)s.getAttribute(CSRF); if(token==null){token=UUID.randomUUID()+UUID.randomUUID().toString();s.setAttribute(CSRF,token);} return new CsrfReply(token);
    }
    @PostMapping("/api/session/login") public ResponseEntity<?> login(@RequestBody LoginRequest body,HttpServletRequest req,HttpServletResponse res) {
        if(body==null||body.hasUnknownField()||body.login()==null||!LoginPolicy.validPassword(body.password())) {
            denied("LOGIN_INVALID_REQUEST","/api/session/login",400,null,null); return error(HttpStatus.BAD_REQUEST,"INVALID_REQUEST");
        }
        if(!csrf(req)){denied("CSRF_INVALID","/api/session/login",403,null,null);return error(HttpStatus.FORBIDDEN,"CSRF_INVALID");}
        String normalized=LoginPolicy.normalize(body.login());
        var user=normalized==null?java.util.Optional.<AuthStore.User>empty():store.user(normalized);
        String hash=user.map(AuthStore.User::hash).orElse(dummyHash);
        boolean matches=encoder.matches(body.password(),hash);
        if(user.isEmpty()||!user.get().enabled()||!matches){invalidateAndExpire(req,res);denied("LOGIN_DENIED","/api/session/login",401,null,null);return error(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS");}
        HttpSession session=req.getSession(true); req.changeSessionId(); session=req.getSession(false);
        session.setAttribute(USER,user.get().id()); session.setAttribute(org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,user.get().id().toString()); session.setAttribute(AUTH_AT,clock.millis()); session.setAttribute(CSRF,UUID.randomUUID().toString()+UUID.randomUUID());
        return ResponseEntity.ok(new LoginReply(user.get().id()));
    }
    private void invalidateAndExpire(HttpServletRequest req,HttpServletResponse res) {
        HttpSession existing=req.getSession(false); if(existing!=null) existing.invalidate();
        jakarta.servlet.http.Cookie cookie=new jakarta.servlet.http.Cookie("TDSESSION",""); cookie.setPath("/"); cookie.setHttpOnly(true); cookie.setSecure(true); cookie.setAttribute("SameSite","Lax"); cookie.setMaxAge(0); res.addCookie(cookie);
    }
    @GetMapping("/api/session") public ResponseEntity<?> session(HttpServletRequest req) {
        UUID actor=actor(req); if(actor==null)return error(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED");
        if(!store.enabled(actor)){req.getSession(false).invalidate(); return error(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED");}
        return ResponseEntity.ok(new SessionReply(actor));
    }
    private UUID actor(HttpServletRequest req) {
        HttpSession s=req.getSession(false); if(s==null)return null;
        Object id=s.getAttribute(USER), at=s.getAttribute(AUTH_AT); if(!(id instanceof UUID actor)||!(at instanceof Long authAt))return null;
        long now=clock.millis(); if(now-authAt>=8L*60*60*1000){s.invalidate();return null;} return actor;
    }
    @PostMapping("/api/session/logout") public ResponseEntity<?> logout(HttpServletRequest req,HttpServletResponse res) {
        UUID actor=actor(req); if(actor==null)return error(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED");
        if(!csrf(req)){denied("CSRF_INVALID","/api/session/logout",403,actor,null);return error(HttpStatus.FORBIDDEN,"CSRF_INVALID");}
        req.getSession(false).invalidate(); jakarta.servlet.http.Cookie cookie=new jakarta.servlet.http.Cookie("TDSESSION","");cookie.setPath("/");cookie.setHttpOnly(true);cookie.setSecure(true);cookie.setAttribute("SameSite","Lax");cookie.setMaxAge(0);res.addCookie(cookie);return ResponseEntity.noContent().build();
    }
    @GetMapping("/api/projects/{projectId}") public ResponseEntity<?> project(@PathVariable String projectId,HttpServletRequest req) {
        UUID actor=actor(req); if(actor==null){denied("UNAUTHENTICATED","/api/projects/{projectId}",401,null,null);return error(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED");}
        UUID id; try { id=UUID.fromString(projectId); if(!id.toString().equals(projectId))throw new IllegalArgumentException(); }
        catch(IllegalArgumentException ex){denied("PROJECT_INVALID_ID","/api/projects/{projectId}",400,actor,null);return error(HttpStatus.BAD_REQUEST,"INVALID_REQUEST");}
        var project=authorization.readableProject(actor,id);
        if(project.isEmpty()){denied("PROJECT_DENIED","/api/projects/{projectId}",404,actor,id);return error(HttpStatus.NOT_FOUND,"NOT_FOUND");}
        return ResponseEntity.ok(new ProjectReply(project.get().projectId(),project.get().projectName()));
    }
}
