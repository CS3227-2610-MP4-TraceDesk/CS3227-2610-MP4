package com.tracedesk.auth.api;

import com.tracedesk.auth.persistence.AuthStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiFailureHandler {
    private final AuthStore store;
    public ApiFailureHandler(AuthStore store) { this.store=store; }
    private ResponseEntity<?> failure(HttpStatus status,String code,String message) {
        return ResponseEntity.status(status).body(java.util.Map.of("error",java.util.Map.of("code",code,"message",message)));
    }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> unavailable(DataAccessException ignored) { return failure(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE","Service unavailable"); }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<?> malformed(HttpMessageNotReadableException ignored,HttpServletRequest request) {
        if("/api/session/login".equals(request.getRequestURI())) { try { store.event("LOGIN_INVALID_REQUEST","/api/session/login",400,null,null); } catch(DataAccessException eventFailure) { return failure(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE","Service unavailable"); } }
        return failure(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Invalid request");
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception ignored) { return failure(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_UNAVAILABLE","Service unavailable"); }
}
