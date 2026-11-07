package com.tracedesk.auth.reports.domain;

import java.util.UUID;

/** Role and ownership rules, independent of HTTP or persistence. */
public final class ReportAccessPolicy {
    private ReportAccessPolicy() { }
    public record Facts(boolean userEnabled, boolean projectEnabled, boolean activeMembership,
                        String role, UUID reporterId) { }
    public static boolean activeMember(Facts f) { return f != null && f.userEnabled() && f.projectEnabled() && f.activeMembership(); }
    public static boolean mayCreate(Facts f) { return activeMember(f) && "REPORTER".equals(f.role()); }
    public static boolean mayList(Facts f) { return activeMember(f) && ListRole.isAllowed(f.role()); }
    public static boolean mayRead(Facts f, UUID actor) {
        return activeMember(f) && ("DEVELOPER".equals(f.role()) || "QA_REVIEWER".equals(f.role()) ||
                ("REPORTER".equals(f.role()) && actor != null && actor.equals(f.reporterId())));
    }
    private static final class ListRole {
        static boolean isAllowed(String role) { return "REPORTER".equals(role) || "DEVELOPER".equals(role) || "QA_REVIEWER".equals(role); }
    }
}
