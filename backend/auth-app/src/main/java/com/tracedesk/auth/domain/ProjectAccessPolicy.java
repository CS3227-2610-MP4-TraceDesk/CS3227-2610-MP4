package com.tracedesk.auth.domain;

/** Framework-independent decision rule for reading a project. */
public final class ProjectAccessPolicy {
    private ProjectAccessPolicy() { }

    public record Facts(boolean userEnabled, boolean projectEnabled,
                        boolean membershipActive) { }

    public static boolean mayRead(Facts facts) {
        return facts != null && facts.userEnabled() && facts.projectEnabled()
                && facts.membershipActive();
    }
}
