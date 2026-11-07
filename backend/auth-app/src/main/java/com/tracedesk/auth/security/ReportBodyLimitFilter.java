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

/** Caps the report create JSON wire body, including chunked requests. */
public final class ReportBodyLimitFilter extends OncePerRequestFilter {
    private static final int LIMIT = 256 * 1024;
    private final AuthStore store;
    public ReportBodyLimitFilter(AuthStore store) { this.store = store; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !request.getRequestURI().matches("/api/projects/[^/]+/reports");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        byte[] body = request.getInputStream().readNBytes(LIMIT + 1);
        if (body.length > LIMIT) {
            try {
                store.event("REPORT_INVALID_REQUEST", "/api/projects/{projectId}/reports", 400, null, null);
                writeError(response, 400, "INVALID_REQUEST");
            } catch (DataAccessException failure) { writeError(response, 503, "SERVICE_UNAVAILABLE"); }
            return;
        }
        chain.doFilter(new CachedRequest(request, body), response);
    }
    private static void writeError(HttpServletResponse response, int status, String code) throws IOException {
        response.setHeader("Cache-Control", "no-store"); response.setStatus(status); response.setCharacterEncoding("UTF-8"); response.setContentType("application/json");
        response.getWriter().write("{\"error\":{\"code\":\""+code+"\",\"message\":\""+(status == 400 ? "Invalid request" : "Service unavailable")+"\"}}");
    }
    private static final class CachedRequest extends HttpServletRequestWrapper {
        private final byte[] body; CachedRequest(HttpServletRequest r, byte[] b) { super(r); body=b; }
        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() { public int read(){return in.read();} public boolean isFinished(){return in.available()==0;} public boolean isReady(){return true;} public void setReadListener(ReadListener l){throw new UnsupportedOperationException();} };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
    }
}
