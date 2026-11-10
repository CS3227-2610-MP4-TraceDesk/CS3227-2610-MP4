package com.tracedesk.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Detects even chunked join bodies without binding or retaining their contents. */
public final class ProjectJoinBodyProbeFilter extends OncePerRequestFilter {
    public static final String BODY_PRESENT="td.projectJoinBodyPresent";
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        if("POST".equals(request.getMethod())&&request.getRequestURI().matches("/api/projects/[^/]+/join")) {
            boolean present=request.getContentLengthLong()>0;
            if(request.getContentLengthLong()<0) present=request.getInputStream().read()!=-1;
            request.setAttribute(BODY_PRESENT,present);
        }
        chain.doFilter(request,response);
    }
}
