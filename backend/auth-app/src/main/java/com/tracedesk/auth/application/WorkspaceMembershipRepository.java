package com.tracedesk.auth.application;

import java.util.List;
import java.util.UUID;

/** Port for bounded active workspace discovery for a trusted actor. */
public interface WorkspaceMembershipRepository {
    record Membership(UUID projectId, String projectName, String role) { }
    record Page(List<Membership> items, boolean hasMore) { }

    Page activeMemberships(UUID actorId, int limit);
}
