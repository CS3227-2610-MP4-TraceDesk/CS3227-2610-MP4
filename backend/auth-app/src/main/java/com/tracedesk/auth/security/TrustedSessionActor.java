package com.tracedesk.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.util.UUID;

/** Servlet adapter for the authenticated UUID in the existing server-side session. */
public final class TrustedSessionActor {
    private TrustedSessionActor() { }
    public static UUID resolve(HttpServletRequest request, Clock clock) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object id = session.getAttribute("td.actor"), authenticatedAt = session.getAttribute("td.authAt");
        if (!(id instanceof UUID actor) || !(authenticatedAt instanceof Long at)) return null;
        long age = clock.millis() - at;
        if (age < 0 || age >= 8L * 60 * 60 * 1000) { session.invalidate(); return null; }
        return actor;
    }

    /** Reads the trusted actor only for a denial event without changing session state. */
    public static UUID forEvent(HttpServletRequest request, Clock clock) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object id = session.getAttribute("td.actor"), authenticatedAt = session.getAttribute("td.authAt");
        if (!(id instanceof UUID actor) || !(authenticatedAt instanceof Long at)) return null;
        long age = clock.millis() - at;
        return age >= 0 && age < 8L * 60 * 60 * 1000 ? actor : null;
    }
}
