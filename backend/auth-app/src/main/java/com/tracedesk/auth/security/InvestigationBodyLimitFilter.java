package com.tracedesk.auth.security;

import com.tracedesk.auth.persistence.AuthStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Caps Developer mutation JSON bodies, including chunked requests. */
public final class InvestigationBodyLimitFilter extends OncePerRequestFilter {
    private static final int LIMIT = 64 * 1024;
    private final AuthStore store;
    public InvestigationBodyLimitFilter(AuthStore store) { this.store = store; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!("POST".equals(request.getMethod()) || "PATCH".equals(request.getMethod()) || "DELETE".equals(request.getMethod()))) return true;
        return !request.getRequestURI().matches("/api/projects/[^/]+/reports/[^/]+/(investigation/start|resolutions|notes(?:/[^/]+)?)");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] body = request.getInputStream().readNBytes(LIMIT + 1);
        if (body.length > LIMIT || ("DELETE".equals(request.getMethod()) && body.length > 0)) {
            try {
                store.event("DEVELOPER_INVALID_REQUEST", route(request.getRequestURI()), 400, null, null);
                writeError(response, 400, "INVALID_REQUEST");
            } catch (DataAccessException failure) { writeError(response, 503, "SERVICE_UNAVAILABLE"); }
            return;
        }
        chain.doFilter(new CachedRequest(request, body), response);
    }
    private static String route(String path) {
        if (path.endsWith("/investigation/start")) return "/api/projects/{projectId}/reports/{reportId}/investigation/start";
        if (path.matches(".*/reports/[^/]+/resolutions$")) return "/api/projects/{projectId}/reports/{reportId}/resolutions";
        if (path.matches(".*/notes/[^/]+$")) return "/api/projects/{projectId}/reports/{reportId}/notes/{noteId}";
        return "/api/projects/{projectId}/reports/{reportId}/notes";
    }
    private static void writeError(HttpServletResponse response, int status, String code) throws IOException {
        response.setHeader("Cache-Control", "no-store"); response.setStatus(status); response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":{\"code\":\"" + code + "\",\"message\":\"" +
                (status == 400 ? "Invalid request" : "Service unavailable") + "\"}}");
    }
    private static final class CachedRequest extends HttpServletRequestWrapper {
        private final byte[] body; CachedRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                public int read() { return input.read(); } public boolean isFinished() { return input.available() == 0; }
                public boolean isReady() { return true; } public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
            };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
    }
}
