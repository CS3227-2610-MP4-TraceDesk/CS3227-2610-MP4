package com.tracedesk.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.sql.SQLException;

/** Converts persistence failures raised outside MVC (notably Spring Session) to a safe API error. */
public final class PersistenceFailureFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (RuntimeException failure) {
            if (!isPersistenceFailure(failure)) throw failure;
            unavailable(request, response);
        } catch (ServletException failure) {
            if (!isPersistenceFailure(failure)) throw failure;
            unavailable(request, response);
        }
    }

    private static boolean isPersistenceFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null && cause.getCause() != cause; cause = cause.getCause()) {
            if (cause instanceof DataAccessException || cause instanceof SQLException) return true;
        }
        return false;
    }

    private static void unavailable(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (response.isCommitted()) return;
        response.resetBuffer();
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'; base-uri 'self'");
        if (request.isSecure()) response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":{\"code\":\"SERVICE_UNAVAILABLE\",\"message\":\"Service unavailable\"}}");
    }
}
