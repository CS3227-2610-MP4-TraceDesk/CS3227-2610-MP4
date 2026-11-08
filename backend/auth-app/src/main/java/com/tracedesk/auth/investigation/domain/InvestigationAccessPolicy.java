package com.tracedesk.auth.investigation.domain;

import java.util.UUID;

/** Developer investigation rules independent of HTTP and persistence. */
public final class InvestigationAccessPolicy {
    private InvestigationAccessPolicy() { }
    public record Facts(boolean userEnabled, boolean projectEnabled, boolean activeMembership,
                        String role, UUID actorId, UUID assignedDeveloperId, String reportStatus) { }
    public static boolean activeDeveloper(Facts f) {
        return f != null && f.userEnabled() && f.projectEnabled() && f.activeMembership() && "DEVELOPER".equals(f.role());
    }
    public static boolean assigned(Facts f) {
        return activeDeveloper(f) && f.actorId() != null && f.actorId().equals(f.assignedDeveloperId());
    }
    public static boolean mayStart(Facts f) { return assigned(f) && "OPEN".equals(f.reportStatus()); }
    public static boolean mayMutateNotes(Facts f) {
        return assigned(f) && ("IN_PROGRESS".equals(f.reportStatus()) || "READY_FOR_QA".equals(f.reportStatus()));
    }
    public static boolean mayReadNotes(Facts f) { return assigned(f); }
}
